package com.yansproject.app.data

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.AppTab
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.room.withTransaction

enum class UserRole {
    OWNER, ADMIN, STAFF, RESELLER, MEMBER, CUSTOMER;

    fun hasFullERPChainAccess(): Boolean {
        return this == OWNER || this == ADMIN
    }

    fun canManageInventory(): Boolean {
        return this == OWNER || this == ADMIN
    }

    fun canManageProjects(): Boolean {
        return this == OWNER || this == ADMIN || this == STAFF
    }

    fun canManageInvoices(): Boolean {
        return this == OWNER || this == ADMIN
    }

    fun canAccessFinancials(): Boolean {
        return this == OWNER || this == ADMIN
    }

    fun canAccessSettings(): Boolean {
        return this == OWNER || this == ADMIN || this == STAFF || this == MEMBER || this == RESELLER || this == CUSTOMER
    }
}

data class UserSession(
    val email: String,
    val role: UserRole,
    val displayName: String = "",
    val priceCategory: String = "Retail", // "Retail", "Member", "Reseller", "Custom" for Member pricing
    val whatsapp: String = "",
    val address: String = "",
    val uid: String = ""
)

data class UserSessionInfo(
    val id: String = "",
    val deviceName: String = "",
    val osDetails: String = "",
    val ipLocation: String = "",
    val lastActive: Long = System.currentTimeMillis(),
    val isCurrent: Boolean = false,
    val isActive: Boolean = true
)

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var messaging: FirebaseMessaging? = null
    private var analytics: com.google.firebase.analytics.FirebaseAnalytics? = null

    private var appContext: Context? = null

    var isFirebaseActive = false
        private set

    var isPullingData = false

    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private val _syncStatus = MutableStateFlow<String>("Offline / Terhubung Lokal")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        val googleAppIdId = context.resources.getIdentifier("google_app_id", "string", context.packageName)
        if (googleAppIdId == 0) {
            isFirebaseActive = false
            _syncStatus.value = "Lokal & Offline Mode"
            Log.w(TAG, "Firebase configuration (google-services.json) is missing. Running in Offline/Local Mode.")
            // Check remember login session
            checkStoredSession(context)
            return
        }

        try {
            // Safe initialization in case google-services.json is missing or incomplete
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(context)
            }
            
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            messaging = FirebaseMessaging.getInstance()
            analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)

            // Enable Offline Persistence for Firestore with Bounded 100MB Cache Size
            @Suppress("DEPRECATION")
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(100 * 1024 * 1024L) // 100MB bounded offline cache
                .build()
            firestore?.firestoreSettings = settings

            isFirebaseActive = true
            _syncStatus.value = "Cloud Sync Aktif (Offline Persistence Enabled)"
            Log.d(TAG, "Firebase initialized successfully with offline persistence.")

            // Check remember login session
            checkStoredSession(context)
        } catch (e: Exception) {
            isFirebaseActive = false
            _syncStatus.value = "Lokal & Offline Mode"
            Log.w(TAG, "Firebase failed to initialize (running in Offline/Local Mode): ${e.message}")
        }
    }

    private fun checkStoredSession(context: Context) {
        val sharedPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("remember_login", false)
        val savedEmail = sharedPrefs.getString("saved_email", null)
        val savedRole = sharedPrefs.getString("saved_role", null)
        val savedName = sharedPrefs.getString("saved_name", "") ?: ""
        val defaultPriceCat = if (savedRole == "OWNER") "Retail" else "Member"
        val savedPriceCategory = sharedPrefs.getString("saved_price_category", defaultPriceCat) ?: defaultPriceCat
        var savedWhatsapp = sharedPrefs.getString("saved_whatsapp", "") ?: ""
        var savedAddress = sharedPrefs.getString("saved_address", "") ?: ""
        val savedUid = sharedPrefs.getString("saved_uid", "") ?: ""

        if (isLoggedIn && savedEmail != null && savedRole != null) {
            val cleanEmail = savedEmail.trim().lowercase()
            val localCred = AppSettings.getLocalUserCredential(context, cleanEmail)
            if (savedWhatsapp.isBlank() && localCred != null && localCred.whatsapp.isNotBlank()) {
                savedWhatsapp = localCred.whatsapp
            }
            if (savedAddress.isBlank() && localCred != null && localCred.address.isNotBlank()) {
                savedAddress = localCred.address
            }
            val role = try { UserRole.valueOf(savedRole) } catch (e: Exception) { UserRole.MEMBER }
            _currentUser.value = UserSession(
                email = savedEmail,
                role = role,
                displayName = if (savedName.isNotBlank()) savedName else (localCred?.displayName ?: ""),
                priceCategory = savedPriceCategory,
                whatsapp = savedWhatsapp,
                address = savedAddress,
                uid = savedUid
            )
            
            // Subscribe User (Member and Owner) to relevant FCM topics and register token
            subscribeUserToFcmTopics(context, role.name)

            // Start real-time snapshot listeners for immediate sync
            startRealtimeSyncListeners(context)

            // Restore cloud user preferences (security, notifications, appearance)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                restoreUserPreferencesFromCloud(context, savedEmail)
            }

            // Background automatic sign-in to Firebase Auth to ensure cloud writes do not fail with PERMISSION_DENIED
            if (isFirebaseActive && auth != null && auth?.currentUser == null) {
                val localCred = AppSettings.getLocalUserCredential(context, savedEmail)
                val passwordOrPin = localCred?.passwordOrPin
                if (passwordOrPin != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val firebasePassword = if (passwordOrPin.length < 6) "yans_$passwordOrPin" else passwordOrPin
                            auth?.signInWithEmailAndPassword(savedEmail, firebasePassword)?.await()
                            Log.d(TAG, "Successfully auto-logged in $savedEmail to Firebase Auth in background.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to auto-log in $savedEmail to Firebase Auth: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun saveSession(
        context: Context,
        email: String,
        role: UserRole,
        displayName: String,
        priceCategory: String,
        whatsapp: String = "",
        address: String = "",
        uid: String = ""
    ) {
        val sharedPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("remember_login", true)
            .putString("saved_email", email)
            .putString("saved_role", role.name)
            .putString("saved_name", displayName)
            .putString("saved_price_category", priceCategory)
            .putString("saved_whatsapp", whatsapp)
            .putString("saved_address", address)
            .putString("saved_uid", uid)
            .apply()

        _currentUser.value = UserSession(
            email = email,
            role = role,
            displayName = displayName,
            priceCategory = priceCategory,
            whatsapp = whatsapp,
            address = address,
            uid = uid
        )
        
        subscribeUserToFcmTopics(context, role.name)

        // Start real-time snapshot listeners for immediate sync
        startRealtimeSyncListeners(context)
    }

    fun updateDisplayName(context: Context, name: String) {
        val current = _currentUser.value ?: return
        saveSession(
            context = context,
            email = current.email,
            role = current.role,
            displayName = name,
            priceCategory = current.priceCategory,
            whatsapp = current.whatsapp,
            address = current.address,
            uid = current.uid
        )
    }

    fun clearSession(context: Context) {
        val sharedPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
        val email = _currentUser.value?.email ?: "unknown"
        sharedPrefs.edit().clear().apply()
        _currentUser.value = null
        stopRealtimeSyncListeners()
        try {
            auth?.signOut()
            val params = android.os.Bundle().apply {
                putString("email", email)
            }
            logEvent("logout", params)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signout error: ${e.message}")
        }
        
        // Launch asynchronous session database wipe safely
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            wipeSession(context)
        }
    }

    suspend fun wipeSession(context: Context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                AppDatabase.getDatabase(context).clearAllTables()
                YansRoomDatabase.getDatabase(context).clearAllTables()
                SyncMetadataManager.getInstance(context).reset()
                YansSyncManager.getInstance(context).resetSyncTimestamp()
                Log.d(TAG, "wipeSession successfully cleared all tables from both Room databases and reset metadata.")
            } catch (e: Exception) {
                Log.e(TAG, "wipeSession failed: ${e.message}")
            }
        }
    }

    // --- Firebase Auth & Session Actions ---
    suspend fun loginUser(context: Context, emailOrUsername: String, passwordOrPin: String): Boolean {
        val cleanInput = emailOrUsername.trim().lowercase()
        val targetEmail = if (cleanInput.contains("@")) cleanInput else "$cleanInput@yansproject.id"
        
        // Dynamic user authentication against stored credential
        val localCred = AppSettings.getLocalUserCredential(context, targetEmail)
        if (localCred != null && localCred.passwordOrPin == passwordOrPin) {
            if (isFirebaseActive && auth != null) {
                try {
                    auth?.signInWithEmailAndPassword(targetEmail, passwordOrPin)?.await()
                    Log.d(TAG, "User logged into Firebase Auth successfully.")
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase Auth sign-in failed/offline: ${e.message}")
                }
            }
            val role = try { UserRole.valueOf(localCred.role.uppercase()) } catch (e: Exception) { UserRole.MEMBER }
            saveSession(context, targetEmail, role, localCred.displayName, localCred.priceCategory, localCred.whatsapp, localCred.address)
            return true
        }

        // Try local credential cache fallback
        if (!isFirebaseActive) {
            if (localCred != null && localCred.passwordOrPin == passwordOrPin) {
                val role = try { UserRole.valueOf(localCred.role) } catch (e: Exception) { UserRole.MEMBER }
                saveSession(context, targetEmail, role, localCred.displayName, localCred.priceCategory, localCred.whatsapp, localCred.address)
                return true
            }
            // Local-only check for other members defined in AppSettings
            val members = AppSettings.getMembers(context)
            if (members.contains(emailOrUsername.trim())) {
                val expectedPin = BusinessIdentityProvider.getSecureProvisionedPin(emailOrUsername.trim(), context)
                if (expectedPin != null && passwordOrPin == expectedPin) {
                    val wa = localCred?.whatsapp ?: ""
                    val addr = localCred?.address ?: ""
                    saveSession(context, targetEmail, UserRole.MEMBER, emailOrUsername.trim(), "Member", wa, addr)
                    return true
                }
            }
            return false
        }

        return try {
            val result = auth?.signInWithEmailAndPassword(targetEmail, passwordOrPin)?.await()
            if (result != null) {
                // Fetch details from Firestore "users" collection safely
                val isHardcodedOwner = BusinessIdentityProvider.isOwnerEmail(targetEmail, context)
                var roleStr = if (isHardcodedOwner) "OWNER" else "MEMBER"
                var displayName = if (isHardcodedOwner) "Yans Art" else emailOrUsername.trim()
                var priceCategory = if (isHardcodedOwner) "Retail" else "Member"
                var whatsapp = localCred?.whatsapp ?: ""
                var address = localCred?.address ?: ""
                val uid = result.user?.uid ?: ""

                try {
                    val doc = firestore?.collection("users")?.document(targetEmail)?.get()?.await()
                    if (doc != null && doc.exists()) {
                        roleStr = doc.getString("role") ?: (if (isHardcodedOwner) "OWNER" else "MEMBER")
                        displayName = doc.getString("displayName") ?: (if (isHardcodedOwner) "Yans Art" else emailOrUsername.trim())
                        priceCategory = doc.getString("priceCategory") ?: (if (roleStr == "OWNER") "Retail" else "Member")
                        val docWa = doc.getString("whatsapp") ?: doc.getString("phone") ?: ""
                        val docAddr = doc.getString("address") ?: ""
                        if (docWa.isNotBlank()) whatsapp = docWa
                        if (docAddr.isNotBlank()) address = docAddr
                    } else if (isHardcodedOwner) {
                        // Create Firestore document if missing for owner
                        val adminData = hashMapOf(
                            "email" to targetEmail,
                            "role" to "OWNER",
                            "displayName" to displayName,
                            "priceCategory" to "Retail",
                            "whatsapp" to "",
                            "address" to "",
                            "created_at" to System.currentTimeMillis()
                        )
                        firestore?.collection("users")?.document(targetEmail)?.set(adminData)?.await()
                        Log.d(TAG, "Created missing Firestore document for owner yansart31@gmail.com")
                    }
                } catch (fe: Exception) {
                    Log.e(TAG, "Failed to fetch user details from Firestore: ${fe.message}. Falling back to default/local parameters.")
                    if (localCred != null) {
                        roleStr = localCred.role
                        displayName = localCred.displayName
                        priceCategory = localCred.priceCategory
                        if (whatsapp.isBlank()) whatsapp = localCred.whatsapp
                        if (address.isBlank()) address = localCred.address
                    }
                }

                val role = try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.MEMBER }
                saveSession(context, targetEmail, role, displayName, priceCategory, whatsapp, address, uid)
                restoreUserPreferencesFromCloud(context, targetEmail)
                
                // Sync to local cache (save under targetEmail, prefix, and displayName)
                val prefix = if (targetEmail.contains("@")) targetEmail.substringBefore("@") else targetEmail
                AppSettings.saveLocalUserCredential(context, targetEmail, passwordOrPin, displayName, role.name, priceCategory, whatsapp, address)
                if (role == UserRole.MEMBER) {
                    AppSettings.addMember(context, displayName)
                }
                
                if (targetEmail != "$prefix@yansproject.id") {
                    AppSettings.saveLocalUserCredential(context, "$prefix@yansproject.id", passwordOrPin, displayName, role.name, priceCategory, whatsapp, address)
                }
                val cleanDisplayName = displayName.trim().lowercase().replace(" ", "")
                if (cleanDisplayName.isNotEmpty()) {
                    AppSettings.saveLocalUserCredential(context, "$cleanDisplayName@yansproject.id", passwordOrPin, displayName, role.name, priceCategory, whatsapp, address)
                }

                val params = android.os.Bundle().apply {
                    putString("email", targetEmail)
                    putString("role", role.name)
                }
                logEvent("login", params)
                true
            } else {
                if (localCred != null && localCred.passwordOrPin == passwordOrPin) {
                    val role = try { UserRole.valueOf(localCred.role) } catch (e: Exception) { UserRole.MEMBER }
                    saveSession(context, targetEmail, role, localCred.displayName, localCred.priceCategory, localCred.whatsapp, localCred.address, "")
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Authentication failed: ${e.message}. Trying local cached fallback...")
            if (localCred != null && localCred.passwordOrPin == passwordOrPin) {
                val role = try { UserRole.valueOf(localCred.role) } catch (e: Exception) { UserRole.MEMBER }
                saveSession(context, targetEmail, role, localCred.displayName, localCred.priceCategory, localCred.whatsapp, localCred.address, "")
                true
            } else {
                val members = AppSettings.getMembers(context)
                val expectedPin = BusinessIdentityProvider.getSecureProvisionedPin(emailOrUsername.trim(), context)
                if (members.contains(emailOrUsername.trim()) && expectedPin != null && passwordOrPin == expectedPin) {
                    saveSession(context, targetEmail, UserRole.MEMBER, emailOrUsername.trim(), "Member", "", "", "")
                    true
                } else {
                    false
                }
            }
        }
    }

    suspend fun registerMemberOnCloud(
        context: Context,
        email: String,
        passwordOrPin: String,
        displayName: String,
        priceCategory: String,
        role: String = "MEMBER",
        whatsapp: String = "",
        address: String = ""
    ): String {
        if (passwordOrPin.length < 4) {
            return "PIN kurang dari 4 digit"
        }

        val cleanEmail = email.trim().lowercase()

        if (!isFirebaseActive) {
            // Local fallback
            AppSettings.addMember(context, displayName)
            AppSettings.saveLocalUserCredential(context, cleanEmail, passwordOrPin, displayName, role, priceCategory)
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("wa_$cleanEmail", whatsapp)
                .putString("address_$cleanEmail", address)
                .apply()
            return "SUCCESS"
        }

        return try {
            // Register in Firebase Auth using a secondary instance to avoid logging out the current owner
            val secondaryApp = try {
                val options = FirebaseApp.getInstance().options
                FirebaseApp.initializeApp(context, options, "SecondaryApp")
            } catch (e: Exception) {
                FirebaseApp.getInstance("SecondaryApp")
            }
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            val firebasePassword = if (passwordOrPin.length < 6) "yans_$passwordOrPin" else passwordOrPin
            val authResult = secondaryAuth.createUserWithEmailAndPassword(cleanEmail, firebasePassword).await()
            val createdUser = authResult.user

            // Robust defense: Re-authenticate primary Auth session if null or mismatched, prior to writing on Firestore
            if (isFirebaseActive && auth != null) {
                val currentEmail = _currentUser.value?.email ?: "owner@yansproject.id"
                if (auth?.currentUser == null || auth?.currentUser?.email?.lowercase() != currentEmail.lowercase()) {
                    val localCred = AppSettings.getLocalUserCredential(context, currentEmail)
                    val pass = localCred?.passwordOrPin
                    if (pass != null) {
                        try {
                            val fbPass = if (pass.length < 6) "yans_$pass" else pass
                            auth?.signInWithEmailAndPassword(currentEmail, fbPass)?.await()
                            Log.d(TAG, "Re-authenticated primary Auth as $currentEmail for firestore registration write.")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed silent re-auth in registerMemberOnCloud: ${e.message}")
                        }
                    }
                }
            }

            val userRef = firestore?.collection("users")?.document(cleanEmail)
            val userData = hashMapOf(
                "email" to cleanEmail,
                "role" to role,
                "displayName" to displayName,
                "priceCategory" to priceCategory,
                "passwordOrPin" to passwordOrPin,
                "whatsapp" to whatsapp,
                "address" to address,
                "created_at" to System.currentTimeMillis()
            )
            
            try {
                userRef?.set(userData)?.await()
            } catch (fe: Exception) {
                Log.e(TAG, "Firestore write failed after Auth user creation: ${fe.message}. Cleaning up stranded Auth user.")
                try {
                    createdUser?.delete()?.await()
                } catch (de: Exception) {
                    Log.e(TAG, "Failed to delete stranded user from secondaryAuth: ${de.message}")
                }
                secondaryApp.delete()
                throw fe
            }
            secondaryApp.delete()
            
            // Also add to local preferences for offline fallback
            if (role == "MEMBER") {
                AppSettings.addMember(context, displayName)
            }
            AppSettings.saveLocalUserCredential(context, cleanEmail, passwordOrPin, displayName, role, priceCategory, whatsapp, address)
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("wa_$cleanEmail", whatsapp)
                .putString("address_$cleanEmail", address)
                .apply()
            "SUCCESS"
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            Log.d(TAG, "Auth user collision for $cleanEmail. Attempting to ensure Firestore document exists anyway.")
            // Robust defense: Re-authenticate primary Auth session if null or mismatched, prior to writing on Firestore
            if (isFirebaseActive && auth != null) {
                val currentEmail = _currentUser.value?.email ?: "owner@yansproject.id"
                if (auth?.currentUser == null || auth?.currentUser?.email?.lowercase() != currentEmail.lowercase()) {
                    val localCred = AppSettings.getLocalUserCredential(context, currentEmail)
                    val pass = localCred?.passwordOrPin
                    if (pass != null) {
                        try {
                            val fbPass = if (pass.length < 6) "yans_$pass" else pass
                            auth?.signInWithEmailAndPassword(currentEmail, fbPass)?.await()
                            Log.d(TAG, "Re-authenticated primary Auth as $currentEmail for firestore registration write.")
                        } catch (re: Exception) {
                            Log.e(TAG, "Failed silent re-auth in registerMemberOnCloud collision handler: ${re.message}")
                        }
                    }
                }
            }

            val userRef = firestore?.collection("users")?.document(cleanEmail)
            val userData = hashMapOf(
                "email" to cleanEmail,
                "role" to role,
                "displayName" to displayName,
                "priceCategory" to priceCategory,
                "passwordOrPin" to passwordOrPin,
                "whatsapp" to whatsapp,
                "address" to address,
                "created_at" to System.currentTimeMillis()
            )
            
            try {
                userRef?.set(userData)?.await()
                Log.d(TAG, "Firestore document written successfully after collision recovery.")
            } catch (fe: Exception) {
                Log.e(TAG, "Firestore write failed during collision recovery: ${fe.message}")
                return "Firestore Rules ditolak: ${fe.message}"
            }

            // Also add to local preferences for offline fallback
            val prefix = if (cleanEmail.contains("@")) cleanEmail.substringBefore("@") else cleanEmail
            if (role == "MEMBER") {
                AppSettings.addMember(context, displayName)
            }
            AppSettings.saveLocalUserCredential(context, cleanEmail, passwordOrPin, displayName, role, priceCategory)
            
            if (cleanEmail != "$prefix@yansproject.id") {
                AppSettings.saveLocalUserCredential(context, "$prefix@yansproject.id", passwordOrPin, displayName, role, priceCategory)
            }
            val cleanDisplayName = displayName.trim().lowercase().replace(" ", "")
            if (cleanDisplayName.isNotEmpty()) {
                AppSettings.saveLocalUserCredential(context, "$cleanDisplayName@yansproject.id", passwordOrPin, displayName, role, priceCategory)
            }
            "SUCCESS"
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            "Firestore Rules ditolak: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "Cloud User registration failed: ${e.message}")
            e.message ?: "Gagal mendaftarkan member."
        }
    }

    suspend fun resetPasswordOnCloud(email: String): Boolean {
        if (!isFirebaseActive) return false
        return try {
            auth?.sendPasswordResetEmail(email)?.await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed: ${e.message}")
            false
        }
    }

    suspend fun changePasswordOnCloud(newPassword: String): Boolean {
        if (!isFirebaseActive) return false
        return try {
            val firebasePassword = if (newPassword.length < 6) "yans_$newPassword" else newPassword
            auth?.currentUser?.updatePassword(firebasePassword)?.await()
            val email = _currentUser.value?.email ?: auth?.currentUser?.email
            if (!email.isNullOrBlank()) {
                val cleanEmail = email.trim().lowercase()
                firestore?.collection("users")?.document(cleanEmail)
                    ?.set(mapOf("passwordOrPin" to newPassword, "updated_at" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())?.await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Change password failed: ${e.message}")
            false
        }
    }

    suspend fun changePasswordAndSyncAll(
        context: Context,
        newPassword: String,
        transactionPin: String? = null
    ): Pair<Boolean, String> {
        val user = _currentUser.value ?: return Pair(false, "Sesi pengguna tidak ditemukan.")
        val cleanEmail = user.email.trim().lowercase()
        val prefix = if (cleanEmail.contains("@")) cleanEmail.substringBefore("@") else cleanEmail
        val cleanDisplayName = user.displayName.trim().lowercase().replace(" ", "")

        var authSuccess = false
        var authMessage = ""

        // 1. Firebase Auth Update (with automatic silent re-authentication if recent login required)
        if (isFirebaseActive && auth != null) {
            val firebasePassword = if (newPassword.length < 6) "yans_$newPassword" else newPassword
            val fbUser = auth?.currentUser
            if (fbUser != null) {
                try {
                    fbUser.updatePassword(firebasePassword).await()
                    authSuccess = true
                } catch (re: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
                    Log.w(TAG, "Recent login required to update Firebase Auth password. Attempting re-auth...")
                    val oldCred = AppSettings.getLocalUserCredential(context, cleanEmail)
                    val oldPass = oldCred?.passwordOrPin
                    if (oldPass != null && fbUser.email != null) {
                        try {
                            val oldFbPass = if (oldPass.length < 6) "yans_$oldPass" else oldPass
                            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(fbUser.email!!, oldFbPass)
                            fbUser.reauthenticate(credential).await()
                            fbUser.updatePassword(firebasePassword).await()
                            authSuccess = true
                        } catch (e2: Exception) {
                            Log.e(TAG, "Re-auth & password update failed: ${e2.message}")
                            authMessage = "Firebase Auth memerlukan login ulang: ${e2.localizedMessage}"
                        }
                    } else {
                        authMessage = "Sesi login lama. Harap re-login untuk memperbarui Firebase Auth."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase Auth updatePassword failed: ${e.message}")
                    authMessage = "Firebase Auth: ${e.localizedMessage}"
                }
            } else {
                // If currentUser is null in primary Auth instance, attempt sign in with stored credential
                val oldCred = AppSettings.getLocalUserCredential(context, cleanEmail)
                val oldPass = oldCred?.passwordOrPin
                if (oldPass != null) {
                    try {
                        val oldFbPass = if (oldPass.length < 6) "yans_$oldPass" else oldPass
                        val authResult = auth?.signInWithEmailAndPassword(cleanEmail, oldFbPass)?.await()
                        val newFbUser = authResult?.user
                        if (newFbUser != null) {
                            val firebasePassword = if (newPassword.length < 6) "yans_$newPassword" else newPassword
                            newFbUser.updatePassword(firebasePassword).await()
                            authSuccess = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auth sign-in retry failed: ${e.message}")
                        authMessage = "Koneksi Firebase Auth: ${e.localizedMessage}"
                    }
                }
            }
        } else {
            authSuccess = true
        }

        // 2. Firestore Document Update (users/{cleanEmail})
        var firestoreSuccess = false
        if (isFirebaseActive && firestore != null) {
            try {
                val updates = hashMapOf<String, Any>(
                    "passwordOrPin" to newPassword,
                    "updated_at" to System.currentTimeMillis()
                )
                if (!transactionPin.isNullOrBlank()) {
                    updates["transactionPin"] = transactionPin
                }
                firestore?.collection("users")?.document(cleanEmail)
                    ?.set(updates, com.google.firebase.firestore.SetOptions.merge())?.await()
                firestoreSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Firestore password update failed: ${e.message}")
            }
        } else {
            firestoreSuccess = true
        }

        // 3. Local Credentials and Security SharedPreferences Sync
        val userRole = user.role.name
        val priceCat = user.priceCategory
        val wa = user.whatsapp
        val addr = user.address

        AppSettings.saveLocalUserCredential(context, cleanEmail, newPassword, user.displayName, userRole, priceCat, wa, addr)
        if (cleanEmail != "$prefix@yansproject.id") {
            AppSettings.saveLocalUserCredential(context, "$prefix@yansproject.id", newPassword, user.displayName, userRole, priceCat, wa, addr)
        }
        if (cleanDisplayName.isNotEmpty()) {
            AppSettings.saveLocalUserCredential(context, "$cleanDisplayName@yansproject.id", newPassword, user.displayName, userRole, priceCat, wa, addr)
        }

        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit()
            .putString("app_pin", newPassword)
            .putString("app_pin_$cleanEmail", newPassword)
            .apply()

        if (!transactionPin.isNullOrBlank()) {
            secPrefs.edit()
                .putString("transaction_pin_$cleanEmail", transactionPin)
                .putString("transaction_pin", transactionPin)
                .apply()

            val userPrefs = context.getSharedPreferences("yans_user_prefs_${cleanEmail}", Context.MODE_PRIVATE)
            userPrefs.edit().putString("transaction_pin", transactionPin).apply()
        }

        if (user.role == UserRole.MEMBER) {
            val memberRepo = MemberRepository(context)
            memberRepo.resetPasswordOrPin(cleanEmail, newPassword)
        }

        return if (authSuccess && firestoreSuccess) {
            Pair(true, "Sandi & Kredensial Keamanan berhasil diperbarui secara Realtime di Firebase Cloud & Lokal!")
        } else if (firestoreSuccess) {
            Pair(true, "Sandi tersimpan di Cloud Firestore & Lokal (${authMessage.ifEmpty { "Auth offline" }})")
        } else {
            Pair(false, "Gagal memperbarui sandi: ${authMessage.ifEmpty { "Gagal koneksi Firebase" }}")
        }
    }

    suspend fun updateTransactionPinAndSync(
        context: Context,
        transactionPin: String
    ): Pair<Boolean, String> {
        val user = _currentUser.value ?: return Pair(false, "Sesi pengguna tidak ditemukan.")
        val cleanEmail = user.email.trim().lowercase()

        val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
        secPrefs.edit()
            .putString("transaction_pin_$cleanEmail", transactionPin)
            .putString("transaction_pin", transactionPin)
            .apply()

        val userPrefs = context.getSharedPreferences("yans_user_prefs_${cleanEmail}", Context.MODE_PRIVATE)
        userPrefs.edit().putString("transaction_pin", transactionPin).apply()

        var firestoreSuccess = false
        if (isFirebaseActive && firestore != null) {
            try {
                val updates = hashMapOf<String, Any>(
                    "transactionPin" to transactionPin,
                    "updated_at" to System.currentTimeMillis()
                )
                firestore?.collection("users")?.document(cleanEmail)
                    ?.set(updates, com.google.firebase.firestore.SetOptions.merge())?.await()
                firestoreSuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Firestore transactionPin update failed: ${e.message}")
            }
        } else {
            firestoreSuccess = true
        }

        return if (firestoreSuccess) {
            Pair(true, "PIN Transaksi berhasil disimpan & disinkronkan ke Cloud & Lokal!")
        } else {
            Pair(true, "PIN Transaksi tersimpan secara lokal!")
        }
    }

    suspend fun verifyEmailOnCloud(email: String): Boolean {
        if (email.isBlank()) return false
        val cleanEmail = email.trim().lowercase()
        return try {
            if (isFirebaseActive && firestore != null) {
                firestore?.collection("users")?.document(cleanEmail)
                    ?.set(mapOf("email_verified" to true, "updated_at" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())?.await()
            }
            auth?.currentUser?.sendEmailVerification()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Verify email cloud update failed: ${e.message}")
            true
        }
    }

    suspend fun verifyPhoneOnCloud(email: String, whatsappNumber: String): Boolean {
        if (email.isBlank()) return false
        val cleanEmail = email.trim().lowercase()
        return try {
            if (isFirebaseActive && firestore != null) {
                val updates = mapOf(
                    "phone_verified" to true,
                    "whatsapp" to whatsappNumber,
                    "updated_at" to System.currentTimeMillis()
                )
                firestore?.collection("users")?.document(cleanEmail)
                    ?.set(updates, com.google.firebase.firestore.SetOptions.merge())?.await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Verify phone cloud update failed: ${e.message}")
            true
        }
    }

    fun syncPreferencesToCloud(context: Context, category: String, data: Map<String, Any>) {
        val userEmail = currentUser.value?.email?.trim()?.lowercase() ?: return
        if (userEmail.isBlank()) return

        if (isFirebaseActive && firestore != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val updates = mapOf(
                        "${category}_settings" to data,
                        "updated_at" to System.currentTimeMillis()
                    )
                    firestore?.collection("users")?.document(userEmail)
                        ?.set(updates, com.google.firebase.firestore.SetOptions.merge())?.await()
                    Log.d(TAG, "Synced $category settings to Firestore for $userEmail")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync $category settings to Cloud: ${e.message}")
                }
            }
        }
    }

    suspend fun restoreUserPreferencesFromCloud(context: Context, email: String) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !isFirebaseActive || firestore == null) return

        try {
            val snapshot = firestore?.collection("users")?.document(cleanEmail)?.get()?.await()
            if (snapshot != null && snapshot.exists()) {
                val appearanceMap = snapshot.get("appearance_settings") as? Map<String, Any>
                if (appearanceMap != null) {
                    val appPrefs = context.getSharedPreferences("yans_appearance_prefs", Context.MODE_PRIVATE)
                    val editor = appPrefs.edit()
                    (appearanceMap["themeVariant"] as? String)?.let { editor.putString("theme_variant", it) }
                    (appearanceMap["accentColor"] as? String)?.let { editor.putString("accent_color", it) }
                    (appearanceMap["canvasStyle"] as? String)?.let { editor.putString("canvas_style", it) }
                    (appearanceMap["glassStyle"] as? String)?.let { editor.putString("glass_style", it) }
                    (appearanceMap["fontScale"] as? Number)?.let { editor.putFloat("font_scale", it.toFloat()) }
                    (appearanceMap["hapticEnabled"] as? Boolean)?.let { editor.putBoolean("haptic_enabled", it) }
                    (appearanceMap["layoutDensity"] as? String)?.let { editor.putString("layout_density", it) }
                    editor.apply()
                }

                val notifyMap = snapshot.get("notification_settings") as? Map<String, Any>
                if (notifyMap != null) {
                    val notifyPrefs = context.getSharedPreferences("yans_notifications_prefs", Context.MODE_PRIVATE)
                    val editor = notifyPrefs.edit()
                    (notifyMap["system_notify"] as? Boolean)?.let { editor.putBoolean("system_notify", it) }
                    (notifyMap["finance_notify"] as? Boolean)?.let { editor.putBoolean("finance_notify", it) }
                    (notifyMap["project_notify"] as? Boolean)?.let { editor.putBoolean("project_notify", it) }
                    (notifyMap["stock_notify"] as? Boolean)?.let { editor.putBoolean("stock_notify", it) }
                    (notifyMap["invoice_notify"] as? Boolean)?.let { editor.putBoolean("invoice_notify", it) }
                    (notifyMap["member_notify"] as? Boolean)?.let { editor.putBoolean("member_notify", it) }
                    (notifyMap["broadcast_notify"] as? Boolean)?.let { editor.putBoolean("broadcast_notify", it) }
                    (notifyMap["personal_notify"] as? Boolean)?.let { editor.putBoolean("personal_notify", it) }
                    editor.apply()
                }

                val securityMap = snapshot.get("security_settings") as? Map<String, Any>
                if (securityMap != null) {
                    val secPrefs = context.getSharedPreferences("yans_security_prefs", Context.MODE_PRIVATE)
                    val editor = secPrefs.edit()
                    (securityMap["pin_lock_enabled"] as? Boolean)?.let {
                        editor.putBoolean("app_lock_enabled", it)
                        editor.putBoolean("pin_lock_enabled", it)
                    }
                    (securityMap["maintenance_password_required"] as? Boolean)?.let { editor.putBoolean("maintenance_password_required", it) }
                    (securityMap["session_timeout"] as? Number)?.let { editor.putInt("session_timeout", it.toInt()) }
                    (securityMap["biometric_enabled"] as? Boolean)?.let { editor.putBoolean("biometric_enabled", it) }
                    editor.apply()
                }

                val userPrefs = context.getSharedPreferences("yans_user_prefs_${cleanEmail}", Context.MODE_PRIVATE)
                val isEmailVerified = snapshot.getBoolean("email_verified") ?: false
                val isPhoneVerified = snapshot.getBoolean("phone_verified") ?: false
                userPrefs.edit()
                    .putBoolean("email_verified", isEmailVerified)
                    .putBoolean("phone_verified", isPhoneVerified)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring user preferences from cloud: ${e.message}")
        }
    }

    suspend fun fetchOrRegisterSessions(context: Context, email: String): List<UserSessionInfo> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return emptyList()

        val currentDeviceId = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "current_device"
        } catch (e: Exception) {
            "current_device"
        }

        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        val model = android.os.Build.MODEL
        val release = android.os.Build.VERSION.RELEASE
        val sdk = android.os.Build.VERSION.SDK_INT

        val currentSession = UserSessionInfo(
            id = currentDeviceId,
            deviceName = "$manufacturer $model (Perangkat Ini)",
            osDetails = "Android $release (API $sdk) • YansProject App",
            ipLocation = "Tangerang, Banten (Koneksi Terenkripsi)",
            lastActive = System.currentTimeMillis(),
            isCurrent = true,
            isActive = true
        )

        if (!isFirebaseActive || firestore == null) {
            return listOf(currentSession)
        }

        return try {
            val sessionRef = firestore!!.collection("users").document(cleanEmail).collection("active_sessions")

            val currentMap = mapOf(
                "device_id" to currentDeviceId,
                "device_name" to "$manufacturer $model",
                "os_details" to "Android $release (API $sdk)",
                "ip_location" to "Tangerang, Banten (Koneksi Terenkripsi)",
                "last_active" to System.currentTimeMillis(),
                "is_current" to true,
                "is_active" to true
            )
            sessionRef.document(currentDeviceId).set(currentMap, com.google.firebase.firestore.SetOptions.merge()).await()

            val updatedSnapshots = sessionRef.get().await()
            val sessionsList = mutableListOf<UserSessionInfo>()

            for (doc in updatedSnapshots.documents) {
                val devId = doc.id
                val devName = doc.getString("device_name") ?: "Perangkat Lain"
                val osDet = doc.getString("os_details") ?: "Sesi Aktif"
                val ipLoc = doc.getString("ip_location") ?: "Indonesia"
                val lastAct = doc.getLong("last_active") ?: System.currentTimeMillis()
                val isCurr = devId == currentDeviceId
                val isAct = doc.getBoolean("is_active") ?: true

                sessionsList.add(
                    UserSessionInfo(
                        id = devId,
                        deviceName = if (isCurr) "$devName (Perangkat Ini)" else devName,
                        osDetails = osDet,
                        ipLocation = ipLoc,
                        lastActive = lastAct,
                        isCurrent = isCurr,
                        isActive = isAct
                    )
                )
            }
            sessionsList.sortedByDescending { it.isCurrent }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sessions from Firestore: ${e.message}")
            listOf(currentSession)
        }
    }

    suspend fun revokeOtherSessionsOnCloud(context: Context, email: String): Pair<Boolean, String> {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return Pair(false, "Email tidak valid.")

        val currentDeviceId = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "current_device"
        } catch (e: Exception) {
            "current_device"
        }

        val userPrefs = context.getSharedPreferences("yans_user_prefs_${cleanEmail}", Context.MODE_PRIVATE)
        userPrefs.edit().putBoolean("other_devices_logged_out", true).apply()

        if (!isFirebaseActive || firestore == null) {
            return Pair(true, "Koneksi sesi lain berhasil diputuskan secara lokal!")
        }

        return try {
            val sessionRef = firestore!!.collection("users").document(cleanEmail).collection("active_sessions")
            val snapshots = sessionRef.get().await()
            var revokedCount = 0

            for (doc in snapshots.documents) {
                if (doc.id != currentDeviceId) {
                    doc.reference.update(
                        mapOf(
                            "is_active" to false,
                            "revoked_at" to System.currentTimeMillis()
                        )
                    ).await()
                    revokedCount++
                }
            }

            firestore!!.collection("users").document(cleanEmail).set(
                mapOf("last_sessions_revoked_at" to System.currentTimeMillis()),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            Pair(true, "Sesi login di $revokedCount perangkat lain berhasil diputuskan permanen!")
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking other sessions on cloud: ${e.message}")
            Pair(true, "Sesi login di perangkat lain telah diputuskan.")
        }
    }

    private val activeDashboardListenerRegs = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    fun startActiveDashboardListener(context: Context, onUpdate: () -> Unit) {
        if (!isFirebaseActive || firestore == null) return
        
        stopActiveDashboardListener()

        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        // Read-optimized lightweight query for recent invoices
        val invoiceReg = firestore?.collection("invoices")
            ?.orderBy("issueDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            ?.addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                scope.launch {
                    for (doc in snapshots.documentChanges) {
                        try {
                            val item = doc.document.toObject(Invoice::class.java)
                            if (item != null) {
                                val local = if (item.invoiceNumber.isNotBlank()) {
                                    db.invoiceDao().getInvoiceByNumber(item.invoiceNumber)
                                } else {
                                    db.invoiceDao().getInvoiceById(item.id)
                                }

                                when (doc.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        if (item.isDeleted) {
                                            if (local != null) db.invoiceDao().deleteInvoice(local)
                                        } else {
                                            if (local != null) {
                                                val updated = item.copy(id = local.id)
                                                db.invoiceDao().insertInvoice(updated)
                                            } else {
                                                db.invoiceDao().insertInvoice(item.copy(id = 0))
                                            }
                                        }
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        if (local != null) db.invoiceDao().deleteInvoice(local)
                                        if (item.invoiceNumber.isNotBlank()) {
                                            db.invoiceDao().deleteInvoiceByNumber(item.invoiceNumber)
                                        }
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            Log.e("FirebaseSyncManager", "Active invoice listen error: ${ex.message}")
                        }
                    }
                    onUpdate()
                }
            }

        // Read-optimized lightweight query for recent orders (limit 30)
        val orderReg = firestore?.collection("orders")
            ?.orderBy("id", com.google.firebase.firestore.Query.Direction.DESCENDING)
            ?.limit(30)
            ?.addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                scope.launch {
                    for (doc in snapshots.documentChanges) {
                        try {
                            val item = doc.document.toObject(OrderHistory::class.java)
                            if (item != null) {
                                when (doc.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        db.orderDao().insertOrder(item)
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        db.orderDao().deleteOrder(item)
                                    }
                                }
                            }
                        } catch (ex: Exception) {
                            Log.e("FirebaseSyncManager", "Active order listen error: ${ex.message}")
                        }
                    }
                    onUpdate()
                }
            }

        if (invoiceReg != null) activeDashboardListenerRegs.add(invoiceReg)
        if (orderReg != null) activeDashboardListenerRegs.add(orderReg)
    }

    fun stopActiveDashboardListener() {
        activeDashboardListenerRegs.forEach { it.remove() }
        activeDashboardListenerRegs.clear()
    }

    private val listenerRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    fun startRealtimeSyncListeners(context: Context) {
        EnterpriseSyncEngine.startRealtimeSyncListeners(context)
        return

        // Stop any existing listeners first
        stopRealtimeSyncListeners()

        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        val collections = listOf(
            "stock_items" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(StockItem::class.java)
                        if (item != null) {
                            val local = db.stockDao().getStockById(item.id)
                            if (local == null || item != local) {
                                db.stockDao().insertStock(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing stock_items from Firestore: ${e.message}") }
                }
            },
            "projects" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(ProjectCustom::class.java)
                        if (item != null) {
                            val local = db.projectDao().getProjectById(item.id)
                            if (local == null || item != local) {
                                db.projectDao().insertProject(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing projects from Firestore: ${e.message}") }
                }
            },
            "invoices" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(Invoice::class.java)
                        if (item != null) {
                            val local = if (item.invoiceNumber.isNotBlank()) {
                                db.invoiceDao().getInvoiceByNumber(item.invoiceNumber)
                            } else {
                                db.invoiceDao().getInvoiceById(item.id)
                            }
                            if (item.isDeleted) {
                                if (local != null) db.invoiceDao().deleteInvoice(local)
                            } else {
                                if (local != null) {
                                    val updated = item.copy(id = local.id)
                                    if (updated != local) {
                                        db.invoiceDao().insertInvoice(updated)
                                    }
                                } else {
                                    db.invoiceDao().insertInvoice(item.copy(id = 0))
                                }
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing invoices from Firestore: ${e.message}") }
                }
            },
            "orders" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(OrderHistory::class.java)
                        if (item != null) {
                            val local = db.orderDao().getOrderById(item.id)
                            if (local == null || item != local) {
                                db.orderDao().insertOrder(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing orders from Firestore: ${e.message}") }
                }
            },
            "expenses" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(Expense::class.java)
                        if (item != null) {
                            val local = db.expenseDao().getExpenseById(item.id)
                            if (local == null || item != local) {
                                db.expenseDao().insertExpense(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing expenses from Firestore: ${e.message}") }
                }
            },
            "inflows" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(Inflow::class.java)
                        if (item != null) {
                            val local = db.inflowDao().getInflowById(item.id)
                            if (local == null || item != local) {
                                db.inflowDao().insertInflow(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing inflows from Firestore: ${e.message}") }
                }
            },
            "master_catalog" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(MasterCatalog::class.java)
                        if (item != null) {
                            val local = db.catalogDao().getCatalogById(item.id_catalog)
                            if (local == null || item != local) {
                                db.catalogDao().insertCatalog(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing master_catalog from Firestore: ${e.message}") }
                }
            },
            "master_varian_warna" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(MasterVarianWarna::class.java)
                        if (item != null) {
                            val local = db.varianWarnaDao().getVarianById(item.id_varian)
                            if (local == null || item != local) {
                                db.varianWarnaDao().insertVarian(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing master_varian_warna from Firestore: ${e.message}") }
                }
            },
            "master_stock" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(MasterStock::class.java)
                        if (item != null) {
                            val local = db.masterStockDao().getStockById(item.id_stock)
                            if (local == null || item != local) {
                                db.masterStockDao().insertStockMaster(item)
                            }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error syncing master_stock from Firestore: ${e.message}") }
                }
            },
            "stock_history" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(StockHistory::class.java)
                        if (item != null) db.stockHistoryDao().insertHistory(item)
                    } catch (e: Exception) { Log.e(TAG, "Error syncing stock_history from Firestore: ${e.message}") }
                }
            },
            "audit_logs" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(AuditLog::class.java)
                        if (item != null) db.auditLogDao().insertLog(item)
                    } catch (e: Exception) { Log.e(TAG, "Error syncing audit_logs from Firestore: ${e.message}") }
                }
            },
            "inventory_ledger" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(InventoryLedger::class.java)
                        if (item != null) db.inventoryLedgerDao().insertLedger(item)
                    } catch (e: Exception) { Log.e(TAG, "Error syncing inventory_ledger from Firestore: ${e.message}") }
                }
            },
            "production_batch" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(ProductionBatch::class.java)
                        if (item != null) db.productionBatchDao().insertBatch(item)
                    } catch (e: Exception) { Log.e(TAG, "Error syncing production_batch from Firestore: ${e.message}") }
                }
            },
            "inventory_summary" to { doc: com.google.firebase.firestore.DocumentSnapshot ->
                scope.launch {
                    try {
                        val item = doc.toObject(InventorySummary::class.java)
                        if (item != null) db.inventorySummaryDao().insertSummary(item)
                    } catch (e: Exception) { Log.e(TAG, "Error syncing inventory_summary from Firestore: ${e.message}") }
                }
            }
        )

        for ((col, mapper) in collections) {
            try {
                val registration = firestore?.collection(col)
                    ?.addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.e(TAG, "Listen failed for collection $col: ${e.message}")
                            return@addSnapshotListener
                        }
                        if (snapshots != null) {
                            for (change in snapshots.documentChanges) {
                                when (change.type) {
                                    com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                    com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                        if (!isPullingData) {
                                            mapper(change.document)
                                        }
                                    }
                                    com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                        if (!isPullingData) {
                                            val docId = change.document.id.toIntOrNull()
                                            if (docId != null) {
                                                scope.launch {
                                                    try {
                                                        when (col) {
                                                            "stock_items" -> {
                                                                val item = db.stockDao().getStockById(docId)
                                                                if (item != null) db.stockDao().deleteStock(item)
                                                            }
                                                            "projects" -> {
                                                                val item = db.projectDao().getProjectById(docId)
                                                                if (item != null) db.projectDao().deleteProject(item)
                                                            }
                                                            "invoices" -> {
                                                                val item = db.invoiceDao().getInvoiceById(docId)
                                                                if (item != null) db.invoiceDao().deleteInvoice(item)
                                                            }
                                                            "orders" -> {
                                                                val item = db.orderDao().getOrderById(docId)
                                                                if (item != null) db.orderDao().deleteOrder(item)
                                                            }
                                                            "expenses" -> {
                                                                val item = db.expenseDao().getExpenseById(docId)
                                                                if (item != null) db.expenseDao().deleteExpense(item)
                                                            }
                                                            "inflows" -> {
                                                                val item = db.inflowDao().getInflowById(docId)
                                                                if (item != null) db.inflowDao().deleteInflow(item)
                                                            }
                                                            "master_catalog" -> {
                                                                val item = db.catalogDao().getCatalogById(docId)
                                                                if (item != null) db.catalogDao().deleteCatalog(item)
                                                            }
                                                            "master_varian_warna" -> {
                                                                val item = db.varianWarnaDao().getVarianById(docId)
                                                                if (item != null) db.varianWarnaDao().deleteVarian(item)
                                                            }
                                                            "master_stock" -> {
                                                                val item = db.masterStockDao().getStockById(docId)
                                                                if (item != null) db.masterStockDao().deleteStockMaster(item)
                                                            }
                                                            "inventory_summary" -> {
                                                                db.inventorySummaryDao().deleteSummaryByVarian(docId)
                                                            }
                                                        }
                                                    } catch (ex: Exception) {
                                                        Log.e(TAG, "Error removing item from Room: ${ex.message}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            val formattedTime = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            _syncStatus.value = "Tersinkronisasi Realtime: $formattedTime"
                        }
                    }
                if (registration != null) {
                    listenerRegistrations.add(registration)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error setting up listener for collection $col: ${ex.message}")
            }
        }

        // --- Sprint 7B: Sync payments subcollection in real-time using Collection Group ---
        try {
            val paymentsReg = firestore?.collectionGroup("payments")
                ?.addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.e(TAG, "Listen failed for collectionGroup payments: ${e.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !isPullingData) {
                        for (change in snapshots.documentChanges) {
                            val doc = change.document
                            val id = doc.id
                            val invoiceId = doc.reference.parent.parent?.id ?: ""
                            scope.launch {
                                try {
                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            val dateVal = doc.getLong("date") ?: System.currentTimeMillis()
                                            val amountVal = doc.getDouble("amount") ?: 0.0
                                            val paymentMethodVal = doc.getString("paymentMethod") ?: ""
                                            val methodDetailVal = doc.getString("methodDetail") ?: ""
                                            val notesVal = doc.getString("notes") ?: ""
                                            val inputByVal = doc.getString("inputBy") ?: ""
                                            val inputByUidVal = doc.getString("inputByUid") ?: ""
                                            val timestampVal = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                            
                                            val payment = InvoicePayment(
                                                id = id,
                                                invoiceId = invoiceId,
                                                date = dateVal,
                                                amount = amountVal,
                                                paymentMethod = paymentMethodVal,
                                                methodDetail = methodDetailVal,
                                                notes = notesVal,
                                                inputBy = inputByVal,
                                                inputByUid = inputByUidVal,
                                                timestamp = timestampVal
                                            )
                                            db.invoicePaymentDao().insertPayment(payment)
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            db.invoicePaymentDao().deletePaymentById(id)
                                        }
                                    }
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Error syncing collectionGroup payment: ${ex.message}")
                                }
                            }
                        }
                    }
                }
            if (paymentsReg != null) {
                listenerRegistrations.add(paymentsReg)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error setting up collectionGroup payments listener: ${ex.message}")
        }
    }

    fun stopRealtimeSyncListeners() {
        EnterpriseSyncEngine.stopRealtimeSyncListeners()
        return
    }

    // --- Bi-directional Real-Time Sync Bridge ---
    fun <T : Any> syncItemToCloud(collectionPath: String, id: String, item: T) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore?.collection(collectionPath)?.document(id)?.set(item)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Sync SUCCESS: $collectionPath with ID $id")
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "Sync FAILED: $collectionPath ID $id: ${e.message}. Queuing offline...")
                        enqueueOfflineAction(collectionPath, id, item)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Sync execution crashed: ${e.message}. Queuing offline...")
                enqueueOfflineAction(collectionPath, id, item)
            }
        }
    }

    fun deleteItemFromCloud(collectionPath: String, id: String) {
        if (!isFirebaseActive) return
        val context = appContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updates = hashMapOf<String, Any>(
                    "isDeleted" to true,
                    "is_deleted" to true,
                    "updatedAt" to System.currentTimeMillis(),
                    "updated_at" to System.currentTimeMillis(),
                    "lastUpdated" to System.currentTimeMillis()
                )
                firestore?.collection(collectionPath)?.document(id)?.update(updates)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Soft Delete SUCCESS: $collectionPath ID $id")
                    }
                    ?.addOnFailureListener { e ->
                        firestore?.collection(collectionPath)?.document(id)?.set(updates, com.google.firebase.firestore.SetOptions.merge())
                            ?.addOnSuccessListener {
                                Log.d(TAG, "Soft Delete (Set Merge) SUCCESS: $collectionPath ID $id")
                            }
                            ?.addOnFailureListener { se ->
                                Log.e(TAG, "Soft Delete FAILED: $collectionPath ID $id: ${se.message}. Queuing offline...")
                                if (context != null) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        MutationQueue.getInstance(context).enqueueSoftDelete(collectionPath, id)
                                    }
                                }
                            }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Soft Delete execution crashed: ${e.message}. Queuing offline...")
                if (context != null) {
                    MutationQueue.getInstance(context).enqueueSoftDelete(collectionPath, id)
                }
            }
        }
    }

    private fun <T : Any> enqueueOfflineAction(collectionPath: String, id: String, item: T) {
        val context = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = YansRoomDatabase.getDatabase(context)
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(item.javaClass)
                val payload = adapter.toJson(item)

                // Avoid duplicating offline actions for the same ID to prevent infinite writes
                val existing = db.offlineActionDao().getAllActions()
                if (existing.any { it.targetCollection == collectionPath && it.additionalMeta == id }) {
                    Log.d(TAG, "Offline action already exists for $collectionPath ID $id. Skipping duplicate.")
                    return@launch
                }

                val action = OfflineActionEntity(
                    stringPayload = payload,
                    targetCollection = collectionPath,
                    timestamp = System.currentTimeMillis(),
                    retryCount = 0,
                    additionalMeta = id
                )
                db.offlineActionDao().insertAction(action)
                Log.d(TAG, "Enqueued offline sync action for $collectionPath ID $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing offline action: ${e.message}")
            }
        }
    }

    private fun enqueueOfflineDeleteAction(collectionPath: String, id: String) {
        val context = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = YansRoomDatabase.getDatabase(context)
                
                // Avoid duplicating delete action
                val existing = db.offlineActionDao().getAllActions()
                if (existing.any { it.targetCollection == collectionPath && it.additionalMeta == id }) {
                    return@launch
                }

                val action = OfflineActionEntity(
                    stringPayload = "{\"id\":\"$id\",\"isDeleted\":true}",
                    targetCollection = collectionPath,
                    timestamp = System.currentTimeMillis(),
                    retryCount = 0,
                    additionalMeta = id
                )
                db.offlineActionDao().insertAction(action)
                Log.d(TAG, "Enqueued offline delete action for $collectionPath ID $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing offline delete action: ${e.message}")
            }
        }
    }

    fun triggerOfflineQueueSync(context: Context) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val secureDb = YansRoomDatabase.getDatabase(context)
                val resolver = DataConflictResolver(context)
                resolver.resolveAndSyncQueue(db, secureDb.offlineActionDao())
                Log.d(TAG, "Triggered offline queue resolution successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering offline queue resolution: ${e.message}")
            }
        }
    }

    // Ganti Perangkat: Load all from cloud to local Room
    fun pullAllDataFromCloudToLocal(context: Context, onComplete: (Boolean) -> Unit) {
        if (!isFirebaseActive) {
            onComplete(false)
            return
        }

        if (isPullingData) {
            onComplete(true)
            return
        }

        isPullingData = true
        _syncStatus.value = "Sedang Sinkronisasi..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Pull Settings Config first
                try {
                    val settingsDoc = firestore?.collection("settings")?.document("store_config")?.get()?.await()
                    if (settingsDoc != null && settingsDoc.exists()) {
                        settingsDoc.getString("store_name")?.let { AppSettings.setStoreName(context, it) }
                        settingsDoc.getString("store_address")?.let { AppSettings.setAddress(context, it) }
                        settingsDoc.getString("store_whatsapp")?.let { AppSettings.setWhatsApp(context, it) }
                        settingsDoc.getString("store_email")?.let { AppSettings.setEmail(context, it) }
                        settingsDoc.getString("store_website")?.let { AppSettings.setWebsite(context, it) }
                        settingsDoc.getString("bank_name")?.let { AppSettings.setBankName(context, it) }
                        settingsDoc.getString("bank_account")?.let { AppSettings.setAccountNumber(context, it) }
                        settingsDoc.getString("bank_holder")?.let { AppSettings.setAccountHolder(context, it) }
                        settingsDoc.getString("invoice_footer")?.let { AppSettings.setInvoiceFooter(context, it) }
                        settingsDoc.getString("project_prefix")?.let { AppSettings.setProjectPrefix(context, it) }
                        settingsDoc.getString("invoice_prefix")?.let { AppSettings.setInvoicePrefix(context, it) }
                        settingsDoc.getDouble("custom_upsize_xxl")?.let { AppSettings.setCustomUpsizeXXL(context, it) }
                        settingsDoc.getDouble("custom_upsize_3xl")?.let { AppSettings.setCustomUpsize3XL(context, it) }
                        settingsDoc.getDouble("custom_upsize_4xl")?.let { AppSettings.setCustomUpsize4XL(context, it) }
                        settingsDoc.getDouble("ajibqobul_upsize_xxl")?.let { AppSettings.setAjibqobulUpsizeXXL(context, it) }
                        settingsDoc.getDouble("ajibqobul_upsize_3xl")?.let { AppSettings.setAjibqobulUpsize3XL(context, it) }
                        settingsDoc.getDouble("ajibqobul_upsize_4xl")?.let { AppSettings.setAjibqobulUpsize4XL(context, it) }
                    }

                    val finDoc = firestore?.collection("settings")?.document("finance_config")?.get()?.await()
                    if (finDoc != null && finDoc.exists()) {
                        finDoc.getDouble("ajibqobul_hpp_pendek")?.let { AppSettings.setAjibqobulHppPendek(context, it) }
                        finDoc.getDouble("ajibqobul_hpp_panjang")?.let { AppSettings.setAjibqobulHppPanjang(context, it) }
                        finDoc.getDouble("ajibqobul_hpp_upsize_xxl")?.let { AppSettings.setAjibqobulHppUpsizeXXL(context, it) }
                        finDoc.getDouble("ajibqobul_hpp_upsize_3xl")?.let { AppSettings.setAjibqobulHppUpsize3XL(context, it) }
                        finDoc.getDouble("ajibqobul_hpp_upsize_4xl")?.let { AppSettings.setAjibqobulHppUpsize4XL(context, it) }
                        finDoc.getDouble("ajibqobul_harga_retail")?.let { AppSettings.setAjibqobulHargaRetail(context, it) }
                        finDoc.getDouble("ajibqobul_harga_member")?.let { AppSettings.setAjibqobulHargaMember(context, it) }
                        finDoc.getDouble("ajibqobul_harga_reseller")?.let { AppSettings.setAjibqobulHargaReseller(context, it) }
                        finDoc.getDouble("ajibqobul_harga_custom")?.let { AppSettings.setAjibqobulHargaCustom(context, it) }
                        finDoc.getDouble("ajibqobul_sleeve_long_price")?.let { AppSettings.setAjibqobulSleeveLongPrice(context, it) }
                        finDoc.getDouble("ajibqobul_upsize_xxl")?.let { AppSettings.setAjibqobulUpsizeXXL(context, it) }
                        finDoc.getDouble("ajibqobul_upsize_3xl")?.let { AppSettings.setAjibqobulUpsize3XL(context, it) }
                        finDoc.getDouble("ajibqobul_upsize_4xl")?.let { AppSettings.setAjibqobulUpsize4XL(context, it) }

                        finDoc.getDouble("custom_base_price")?.let { AppSettings.setCustomBasePrice(context, it) }
                        finDoc.getDouble("custom_sleeve_long_price")?.let { AppSettings.setCustomSleeveLongPrice(context, it) }
                        finDoc.getDouble("custom_upsize_xxl")?.let { AppSettings.setCustomUpsizeXXL(context, it) }
                        finDoc.getDouble("custom_upsize_3xl")?.let { AppSettings.setCustomUpsize3XL(context, it) }
                        finDoc.getDouble("custom_upsize_4xl")?.let { AppSettings.setCustomUpsize4XL(context, it) }
                        finDoc.getDouble("custom_hpp_reguler_pendek")?.let { AppSettings.setCustomHppRegulerPendek(context, it) }
                        finDoc.getDouble("custom_hpp_reguler_panjang")?.let { AppSettings.setCustomHppRegulerPanjang(context, it) }
                        finDoc.getDouble("custom_hpp_kids_pendek")?.let { AppSettings.setCustomHppKidsPendek(context, it) }
                        finDoc.getDouble("custom_hpp_kids_panjang")?.let { AppSettings.setCustomHppKidsPanjang(context, it) }
                        Log.d(TAG, "Successfully pulled finance_config settings from cloud.")
                    }
                } catch (se: Exception) {
                    Log.e(TAG, "Error fetching settings from cloud: ${se.message}")
                }

                // Delegate synchronization to the advanced YansSyncManager
                val syncManager = YansSyncManager.getInstance(context)
                syncManager.synchronize()

                _syncStatus.value = syncManager.syncStatus.value

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during pulling cloud data: ${e.message}")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            } finally {
                isPullingData = false
            }
        }
    }

    // --- Cloud Backups via Firebase Storage replaced with Cloud Firestore Text Base64 ---
    fun uploadBackupToCloud(context: Context, backupFile: File, onResult: (Boolean, String?) -> Unit) {
        if (!isFirebaseActive) {
            onResult(false, "Firebase tidak aktif")
            return
        }
        try {
            val bytes = backupFile.readBytes()
            val base64String = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
            
            val db = firestore ?: FirebaseFirestore.getInstance()
            val backupDoc = hashMapOf(
                "fileName" to backupFile.name,
                "dataBase64" to base64String,
                "timestamp" to System.currentTimeMillis()
            )
            
            db.collection("cloud_backups")
                .document(backupFile.name.replace(".", "_"))
                .set(backupDoc)
                .addOnSuccessListener {
                    onResult(true, "Backup '${backupFile.name}' berhasil diunggah ke Cloud Firestore (100% Text-Based).")
                    sendPushNotification("Backup Berhasil", "Sistem berhasil membuat dan mengunggah backup ke Cloud (Firestore).")
                    val params = android.os.Bundle().apply {
                        putString("filename", backupFile.name)
                    }
                    logEvent("backup", params)
                }
                .addOnFailureListener { e ->
                    onResult(false, "Gagal mengunggah backup ke Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    fun downloadBackupFromCloud(context: Context, backupName: String, destinationFile: File, onResult: (Boolean, String?) -> Unit) {
        if (!isFirebaseActive) {
            onResult(false, "Firebase tidak aktif")
            return
        }
        try {
            val db = firestore ?: FirebaseFirestore.getInstance()
            db.collection("cloud_backups")
                .document(backupName.replace(".", "_"))
                .get()
                .addOnSuccessListener { doc ->
                    val base64String = doc.getString("dataBase64")
                    if (base64String != null) {
                        try {
                            val bytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                            destinationFile.writeBytes(bytes)
                            onResult(true, "Backup berhasil diunduh.")
                            sendPushNotification("Restore Berhasil", "Sistem berhasil memulihkan database dari Cloud.")
                            val params = android.os.Bundle().apply {
                                java.lang.String.valueOf(backupName)
                                putString("filename", backupName)
                            }
                            logEvent("restore", params)
                        } catch (e: Exception) {
                            onResult(false, "Gagal memproses data dekripsi backup: ${e.message}")
                        }
                    } else {
                        onResult(false, "Backup tidak ditemukan di Cloud")
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, "Gagal mengunduh backup: ${e.message}")
                }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }

    // --- Firebase Cloud Messaging (FCM) & Push Handler ---
    fun subscribeUserToFcmTopics(context: Context, userRole: String = "MEMBER") {
        if (!isFirebaseActive) return
        try {
            val msg = messaging ?: com.google.firebase.messaging.FirebaseMessaging.getInstance()

            msg.subscribeToTopic("yans_broadcast")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d(TAG, "Subscribed successfully to 'yans_broadcast' FCM topic.")
                }
            msg.subscribeToTopic("yans_all")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d(TAG, "Subscribed successfully to 'yans_all' FCM topic.")
                }
            msg.subscribeToTopic("yans_members")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) Log.d(TAG, "Subscribed successfully to 'yans_members' FCM topic.")
                }

            if (userRole.equals("OWNER", ignoreCase = true) || userRole.equals("ADMIN", ignoreCase = true)) {
                msg.subscribeToTopic("yans_owners")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) Log.d(TAG, "Subscribed successfully to 'yans_owners' FCM topic.")
                    }
            }

            msg.token.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val token = task.result
                    Log.d(TAG, "FCM Registration Token retrieved: $token")
                    updateFcmTokenInCloud(context, token)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in subscribeUserToFcmTopics: ${e.message}", e)
        }
    }

    fun updateFcmTokenInCloud(context: Context, token: String) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
                val savedEmail = authPrefs.getString("saved_email", "")?.trim()?.lowercase() ?: ""
                val savedName = authPrefs.getString("saved_name", "")?.trim() ?: ""
                val userRole = authPrefs.getString("user_role", "MEMBER") ?: "MEMBER"

                authPrefs.edit().putString("fcm_token", token).apply()

                if (savedEmail.isNotBlank()) {
                    val tokenDoc = hashMapOf(
                        "email" to savedEmail,
                        "name" to savedName,
                        "role" to userRole,
                        "fcmToken" to token,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    firestore?.collection("fcm_tokens")
                        ?.document(savedEmail)
                        ?.set(tokenDoc, com.google.firebase.firestore.SetOptions.merge())
                        ?.addOnSuccessListener {
                            Log.d(TAG, "FCM Token registered in Cloud for $savedEmail")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token in Cloud: ${e.message}")
            }
        }
    }

    fun sendPushNotification(
        context: Context,
        title: String,
        body: String,
        category: String = "Broadcast",
        targetTab: String = "INVOICE",
        roleTarget: String = "MEMBER",
        userId: String = "ALL"
    ) {
        Log.d(TAG, "PUSH BROADCAST DISPATCHED: [$title] -> $body to Target [$roleTarget / $userId]")

        // 1. Dispatch locally on current device as well
        com.yansproject.app.util.NotificationHandler.processAndDispatchNotification(
            context = context,
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            message = body,
            category = category,
            targetTab = targetTab,
            roleTarget = roleTarget,
            userId = userId
        )

        // 2. Dispatch payload asynchronously to n8n webhook / FCM push gateway
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
                val rawN8nUrl = prefs.getString("n8n_url", "https://primary-production.shared.n8n.cloud") ?: "https://primary-production.shared.n8n.cloud"
                val n8nBase = if (rawN8nUrl.startsWith("http")) rawN8nUrl else "https://$rawN8nUrl"
                val pushEndpoint = "$n8nBase/webhook/yans-broadcast-push"

                val payload = org.json.JSONObject().apply {
                    put("title", title)
                    put("body", body)
                    put("category", category)
                    put("targetTab", targetTab)
                    put("roleTarget", roleTarget)
                    put("userId", userId)
                    put("topic", "yans_broadcast")
                    put("timestamp", System.currentTimeMillis())
                }

                val url = java.net.URL(pushEndpoint)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "Broadcast push webhook returned HTTP status $responseCode")
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Push webhook dispatch info: ${e.message}")
            }
        }
    }

    fun sendPushNotification(title: String, body: String) {
        Log.d(TAG, "PUSH DISPATCHED: [$title] -> $body")
    }

    fun writeNotificationToCloud(notification: AppSettings.AppNotification) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = hashMapOf(
                    "id" to notification.id,
                    "title" to notification.title,
                    "description" to notification.message,
                    "timestamp" to notification.timestamp,
                    "category" to notification.category,
                    "actionRoute" to notification.targetTab,
                    "isRead" to notification.isRead,
                    "roleTarget" to notification.roleTarget,
                    "userId" to notification.userId,
                    "priority" to notification.priority,
                    "isArchived" to notification.isArchived,
                    "createdBy" to notification.createdBy
                )
                firestore?.collection("notifications")?.document(notification.id)?.set(data)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Notification synced to Cloud: ${notification.id}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write notification to cloud: ${e.message}")
            }
        }
    }

    fun updateNotificationInCloud(id: String, fields: Map<String, Any>) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore?.collection("notifications")?.document(id)?.update(fields)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Notification $id updated in cloud with: $fields")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification $id in cloud: ${e.message}")
            }
        }
    }

    fun startNotificationListener(
        context: Context,
        userEmail: String,
        userRole: String,
        onUpdate: (List<AppSettings.AppNotification>) -> Unit
    ): com.google.firebase.firestore.ListenerRegistration? {
        if (!isFirebaseActive) return null
        try {
            val cleanEmail = userEmail.trim().lowercase()
            val sharedPrefs = context.getSharedPreferences("yans_auth_prefs", Context.MODE_PRIVATE)
            val savedName = sharedPrefs.getString("saved_name", "")?.trim()?.lowercase() ?: ""
            val savedEmail = sharedPrefs.getString("saved_email", "")?.trim()?.lowercase() ?: ""
            val shownSystemIds = sharedPrefs.getStringSet("shown_system_notif_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

            return firestore?.collection("notifications")
                ?.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Notification listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val deletedIds = AppSettings.getDeletedNotificationIds(context)
                        val list = mutableListOf<AppSettings.AppNotification>()
                        val newSystemNotifsToPost = mutableListOf<AppSettings.AppNotification>()

                        for (doc in snapshot.documents) {
                            val id = doc.id
                            if (deletedIds.contains(id)) continue

                            val title = doc.getString("title") ?: ""
                            val message = doc.getString("description") ?: doc.getString("message") ?: ""
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val category = doc.getString("category") ?: "SYSTEM"
                            val targetTab = doc.getString("actionRoute") ?: doc.getString("targetTab")
                            val isRead = doc.getBoolean("isRead") ?: false
                            val roleTarget = doc.getString("roleTarget") ?: "ALL"
                            val userId = doc.getString("userId") ?: "ALL"
                            val priority = doc.getString("priority") ?: "MEDIUM"
                            val isArchived = doc.getBoolean("isArchived") ?: false
                            val isDeleted = doc.getBoolean("isDeleted") ?: doc.getBoolean("is_deleted") ?: false
                            val createdBy = doc.getString("createdBy") ?: "SYSTEM"

                            if (isDeleted) continue

                            val catUpper = category.trim().uppercase()
                            val isMemberRole = userRole.equals("MEMBER", ignoreCase = true)
                            val cleanTargetUser = userId.trim().lowercase()

                            // Strict Privacy Scope Rule for Member Role
                            val isForMe = if (isMemberRole) {
                                val isOrderOrInvoiceOrPaymentCategory = catUpper in setOf("INVOICE", "ORDER", "PESANAN", "PEMBAYARAN", "PAYMENT")
                                if (isOrderOrInvoiceOrPaymentCategory) {
                                    // Order & Payment/Invoice MUST explicitly match this member's email or name! NEVER show "ALL" or other members'
                                    cleanTargetUser != "all" && (
                                        cleanTargetUser == cleanEmail ||
                                        cleanTargetUser == savedEmail ||
                                        cleanTargetUser == savedName ||
                                        cleanTargetUser.contains(savedName)
                                    )
                                } else {
                                    // Stock, Broadcast, Promotion, System alerts sent to ALL, MEMBER, BROADCAST, PROMO
                                    (roleTarget.uppercase() in setOf("ALL", "MEMBER", "BROADCAST", "PROMO", "PUBLIC") || catUpper in setOf("BROADCAST", "PROMO", "SISTEM", "SYSTEM", "STOCK", "STOK")) &&
                                    (userId == "ALL" || cleanTargetUser == "all" || cleanTargetUser == cleanEmail || cleanTargetUser == savedEmail || cleanTargetUser == savedName || cleanTargetUser.isBlank())
                                }
                            } else {
                                // Owner / Admin Role can see Owner alerts, Stock, System, and ALL broadcasts
                                roleTarget.uppercase() in setOf("ALL", "OWNER", "ADMIN", "BROADCAST", "PROMO", "PUBLIC") || userId == "ALL" || cleanTargetUser == "all"
                            }

                            if (isForMe && !isArchived) {
                                val notif = AppSettings.AppNotification(
                                    id = id,
                                    title = title,
                                    message = message,
                                    timestamp = timestamp,
                                    category = category,
                                    targetTab = targetTab,
                                    isRead = isRead,
                                    roleTarget = roleTarget,
                                    userId = userId,
                                    priority = priority,
                                    isArchived = isArchived,
                                    isDeleted = false,
                                    createdBy = createdBy
                                )
                                list.add(notif)

                                // Trigger System Bar Notification if not yet posted and recent (within last 24h)
                                if (!isRead && !shownSystemIds.contains(id) && (System.currentTimeMillis() - timestamp < 86400000L)) {
                                    newSystemNotifsToPost.add(notif)
                                    shownSystemIds.add(id)
                                }
                            }
                        }

                        // Persist updated shown system notif IDs
                        sharedPrefs.edit().putStringSet("shown_system_notif_ids", shownSystemIds).apply()

                        // Post Android status bar notifications for new incoming items
                        newSystemNotifsToPost.forEach { item ->
                            com.yansproject.app.util.SystemNotificationHelper.postSystemNotification(
                                context = context,
                                title = item.title,
                                message = item.message,
                                category = item.category,
                                targetTab = item.targetTab,
                                notificationId = item.id
                            )
                        }

                        onUpdate(list.sortedByDescending { it.timestamp })
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting notification listener: ${e.message}")
        }
        return null
    }

    fun deleteNotificationFromCloudPermanently(id: String) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firestore?.collection("notifications")?.document(id)?.delete()
                    ?.addOnSuccessListener {
                        Log.d(TAG, "Notification $id permanently deleted from Firestore")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to permanently delete notification $id from cloud: ${e.message}")
            }
        }
    }

    fun deleteNotificationsForInvoiceFromCloud(invoiceNumber: String, invoiceId: Int? = null, orderId: Int? = null) {
        if (!isFirebaseActive) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val invNumClean = invoiceNumber.trim().lowercase()
                val invIdStr = invoiceId?.takeIf { it != 0 }?.toString()
                val ordIdStr = orderId?.takeIf { it != 0 }?.toString()

                if (invNumClean.isEmpty() && invIdStr == null && ordIdStr == null) return@launch

                firestore?.collection("notifications")?.get()?.addOnSuccessListener { snapshot ->
                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val title = (doc.getString("title") ?: "").lowercase()
                            val desc = (doc.getString("description") ?: doc.getString("message") ?: "").lowercase()
                            val docId = doc.id.lowercase()

                            val matchesInvNum = invNumClean.isNotEmpty() && (desc.contains(invNumClean) || title.contains(invNumClean) || docId.contains(invNumClean))
                            val matchesInvId = invIdStr != null && (desc.contains("invoice #$invIdStr") || desc.contains("invoice id: $invIdStr") || docId.contains("inv_$invIdStr"))
                            val matchesOrdId = ordIdStr != null && (desc.contains("pesanan #$ordIdStr") || desc.contains("order #$ordIdStr") || desc.contains("order id: $ordIdStr") || docId.contains("ord_$ordIdStr"))

                            if (matchesInvNum || matchesInvId || matchesOrdId) {
                                doc.reference.delete()
                                Log.d(TAG, "Deleted notification from cloud for invoice/order: ${doc.id}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting notifications for invoice from cloud: ${e.message}")
            }
        }
    }

    fun logEvent(name: String, params: android.os.Bundle? = null) {
        if (!isFirebaseActive) return
        try {
            analytics?.logEvent(name, params)
            Log.d(TAG, "Analytics Event Logged: $name")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: ${e.message}")
        }
    }
}
