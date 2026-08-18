package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Keep
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "MEMBER", // "SUPER_ADMIN" or "MEMBER"
    val priceCategory: String = "Member",
    val whatsapp: String = "",
    val address: String = "",
    val isVerified: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isSuperAdmin: Boolean get() = role == "SUPER_ADMIN" || role == "OWNER" || role == "ADMIN"
}

/**
 * ProfileRepository: Centralized identity and profile security manager.
 * Invariant: Credentials (passwords, PINs, auth secrets) are NEVER persisted in SharedPreferences or local cache.
 * Firebase Authentication owns credentials. Profile state is derived from UID and Firestore/Room cache.
 * Role authority is strictly centralized: OWNER + ADMIN = SUPER_ADMIN vs MEMBER.
 */
class ProfileRepository private constructor(private val context: Context) {

    private val TAG = "ProfileRepository"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private val _currentProfile = MutableStateFlow(UserProfile())
    val currentProfile: StateFlow<UserProfile> = _currentProfile.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: ProfileRepository? = null

        fun getInstance(context: Context): ProfileRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ProfileRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                loadProfileForUser(user.uid, user.email ?: "")
            } else {
                _currentProfile.value = UserProfile()
                AuthoritativeSessionManager.clearSession()
            }
        }
    }

    fun loadProfileForUser(uid: String, email: String) {
        if (uid.isBlank()) return
        scope.launch {
            try {
                val cleanEmail = email.trim().lowercase()
                val isHardcodedOwner = cleanEmail == "yansproject.id31@gmail.com" || cleanEmail == "admin@yansproject.id"
                val baseRole = if (isHardcodedOwner) "SUPER_ADMIN" else "MEMBER"
                
                // Read profile attributes (name, wa, address, tier) from Room/local cache WITHOUT storing passwords
                val cred = com.yansproject.app.ui.AppSettings.getLocalUserCredential(context, cleanEmail)
                val resolvedRole = if (isHardcodedOwner || cred?.role == "OWNER" || cred?.role == "ADMIN" || cred?.role == "SUPER_ADMIN") {
                    "SUPER_ADMIN"
                } else {
                    "MEMBER"
                }

                val profile = UserProfile(
                    uid = uid,
                    email = cleanEmail,
                    displayName = cred?.displayName ?: (if (isHardcodedOwner) "Super Admin" else "Member"),
                    role = resolvedRole,
                    priceCategory = cred?.priceCategory ?: (if (resolvedRole == "SUPER_ADMIN") "Retail" else "Member"),
                    whatsapp = cred?.whatsapp ?: "",
                    address = cred?.address ?: "",
                    isVerified = true,
                    lastUpdated = System.currentTimeMillis()
                )

                _currentProfile.value = profile
                AuthoritativeSessionManager.updateSession(
                    uid = uid,
                    role = resolvedRole,
                    isAuthenticated = true
                )
                Log.i(TAG, "Profile loaded for UID: $uid [Role: $resolvedRole]")
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading profile for user: ${e.message}", e)
            }
        }
    }

    suspend fun updateProfile(
        displayName: String,
        whatsapp: String,
        address: String,
        priceCategory: String? = null
    ): Boolean {
        val current = _currentProfile.value
        if (current.uid.isBlank()) return false

        val newProfile = current.copy(
            displayName = displayName.trim(),
            whatsapp = whatsapp.trim(),
            address = address.trim(),
            priceCategory = priceCategory ?: current.priceCategory,
            lastUpdated = System.currentTimeMillis()
        )

        _currentProfile.value = newProfile

        // Persist non-credential attributes locally
        com.yansproject.app.ui.AppSettings.saveLocalUserCredential(
            context = context,
            email = current.email,
            passwordOrPin = "", // Never persist passwords!
            displayName = newProfile.displayName,
            role = newProfile.role,
            priceCategory = newProfile.priceCategory,
            whatsapp = newProfile.whatsapp,
            address = newProfile.address
        )

        // Sync non-credential profile to Firebase
        return try {
            FirebaseSyncManager.syncUserProfileToCloud(
                uid = current.uid,
                email = current.email,
                displayName = newProfile.displayName,
                role = newProfile.role,
                whatsapp = newProfile.whatsapp,
                address = newProfile.address,
                priceCategory = newProfile.priceCategory
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing updated profile to cloud: ${e.message}", e)
            false
        }
    }
}
