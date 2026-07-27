package com.yansproject.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.yansproject.app.ui.settings.MemberModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MemberRepository(private val context: Context) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e("MemberRepository", "FirebaseFirestore unavailable: ${e.message}")
            null
        }
    }

    private fun getLocalMembers(): List<MemberModel> {
        val membersList = mutableListOf<MemberModel>()
        try {
            val memberNames = com.yansproject.app.ui.AppSettings.getMembers(context)
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            for (name in memberNames) {
                if (name.isBlank() || name.equals("Owner", ignoreCase = true) || name.contains("Admin", ignoreCase = true)) continue
                val email = prefs.getString("email_for_$name", "$name@yansproject.id") ?: "$name@yansproject.id"
                val normalizedEmail = email.lowercase().trim()
                val wa = prefs.getString("wa_$normalizedEmail", "") ?: ""
                val address = prefs.getString("address_$normalizedEmail", "") ?: ""
                val priceCategory = com.yansproject.app.ui.AppSettings.getMemberPriceCategory(context, name)
                val createdAt = prefs.getLong("created_at_$normalizedEmail", System.currentTimeMillis())
                val statusVerifikasi = prefs.getString("status_verifikasi_$normalizedEmail", "Terverifikasi") ?: "Terverifikasi"
                val passwordOrPin = prefs.getString("pass_$normalizedEmail", "1234") ?: "1234"

                if (membersList.none { it.displayName.equals(name.trim(), ignoreCase = true) }) {
                    membersList.add(
                        MemberModel(
                            email = email,
                            displayName = name,
                            role = "MEMBER",
                            priceCategory = priceCategory,
                            passwordOrPin = passwordOrPin,
                            whatsapp = wa,
                            address = address,
                            createdAt = createdAt,
                            lastLogin = System.currentTimeMillis(),
                            statusAkun = "Aktif",
                            statusVerifikasi = statusVerifikasi
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MemberRepository", "Error loading local members safely: ${e.message}")
        }
        return membersList
    }

    fun observeMembersRealtime(): Flow<List<MemberModel>> = callbackFlow {
        val dbInstance = firestore
        if (!FirebaseSyncManager.isFirebaseActive || dbInstance == null) {
            trySend(getLocalMembers())
            close()
            return@callbackFlow
        }

        Log.d("MemberRepository", "Registering real-time Firestore listener for 'users' collection")
        val listenerRegistration = try {
            dbInstance.collection("users")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.w("MemberRepository", "Error observing members or snapshot null: ${error?.message}")
                        trySend(getLocalMembers())
                        return@addSnapshotListener
                    }

                    try {
                        val membersList = mutableListOf<MemberModel>()
                        val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
                        val edit = prefs.edit()

                        for (doc in snapshot.documents) {
                            val email = doc.getString("email") ?: doc.id
                            val displayName = doc.getString("displayName") ?: ""
                            val role = doc.getString("role") ?: "MEMBER"
                            val priceCategory = doc.getString("priceCategory") ?: "Member"
                            val passwordOrPin = doc.getString("passwordOrPin") ?: ""
                            val whatsapp = doc.getString("whatsapp") ?: doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""
                            val address = doc.getString("address") ?: ""
                            val createdAt = doc.getLong("created_at") ?: doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val lastLogin = doc.getLong("lastLogin") ?: doc.getLong("last_login") ?: doc.getLong("lastActive") ?: System.currentTimeMillis()
                            val statusAkun = doc.getString("statusAkun") ?: doc.getString("status") ?: "Aktif"
                            val statusVerifikasi = doc.getString("statusVerifikasi") ?: doc.getString("status_verifikasi") ?: "Terverifikasi"

                            val isOwner = role.equals("OWNER", ignoreCase = true) ||
                                    role.equals("ADMIN", ignoreCase = true) ||
                                    displayName.contains("Owner", ignoreCase = true) ||
                                    displayName.equals("YANSPROJECT.ID", ignoreCase = true) ||
                                    email.equals("admin@yansproject.id", ignoreCase = true) ||
                                    email.equals("yansart31@gmail.com", ignoreCase = true)

                            if (!isOwner && displayName.isNotBlank() && (role.equals("MEMBER", ignoreCase = true) || role.isBlank())) {
                                val normalizedEmail = email.lowercase().trim()
                                
                                try {
                                    com.yansproject.app.ui.AppSettings.addMember(context, displayName)
                                    com.yansproject.app.ui.AppSettings.saveLocalUserCredential(
                                        context, email, passwordOrPin, displayName, "MEMBER", priceCategory
                                    )
                                    com.yansproject.app.ui.AppSettings.saveMemberPriceCategory(context, displayName, priceCategory)
                                } catch (ex: Exception) {}

                                edit.putString("wa_$normalizedEmail", whatsapp)
                                    .putString("address_$normalizedEmail", address)
                                    .putLong("created_at_$normalizedEmail", createdAt)
                                    .putLong("last_login_$normalizedEmail", lastLogin)
                                    .putString("status_akun_$normalizedEmail", statusAkun)
                                    .putString("status_verifikasi_$normalizedEmail", statusVerifikasi)
                                    .putString("email_for_${displayName.trim()}", email)

                                val finalModel = MemberModel(
                                    email = email,
                                    displayName = displayName,
                                    role = "MEMBER",
                                    priceCategory = priceCategory,
                                    passwordOrPin = passwordOrPin,
                                    whatsapp = whatsapp,
                                    address = address,
                                    createdAt = createdAt,
                                    lastLogin = lastLogin,
                                    statusAkun = "Aktif",
                                    statusVerifikasi = statusVerifikasi
                                )
                                if (membersList.none { it.displayName.equals(displayName.trim(), ignoreCase = true) }) {
                                    membersList.add(finalModel)
                                }
                            }
                        }
                        edit.apply()
                        if (membersList.isNotEmpty()) {
                            trySend(membersList)
                        } else {
                            trySend(getLocalMembers())
                        }
                    } catch (e: Exception) {
                        Log.e("MemberRepository", "Error processing user snapshot: ${e.message}")
                        trySend(getLocalMembers())
                    }
                }
        } catch (e: Exception) {
            Log.e("MemberRepository", "Failed attaching snapshot listener: ${e.message}")
            trySend(getLocalMembers())
            null
        }

        awaitClose {
            Log.d("MemberRepository", "Removing real-time Firestore listener for 'users' collection")
            try {
                listenerRegistration?.remove()
            } catch (e: Exception) {}
        }
    }

    suspend fun updateMemberTier(email: String, displayName: String, newTier: String): Boolean {
        return try {
            val targetEmail = email.lowercase().trim()
            val dbInstance = firestore
            if (FirebaseSyncManager.isFirebaseActive && dbInstance != null) {
                try {
                    dbInstance.collection("users").document(targetEmail)
                        .update("priceCategory", newTier)
                        .await()
                } catch (ex: Exception) {}
            }
            com.yansproject.app.ui.AppSettings.saveMemberPriceCategory(context, displayName, newTier)
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            prefs.edit().putString("price_$targetEmail", newTier).apply()
            true
        } catch (e: Exception) {
            Log.e("MemberRepository", "Failed updating member tier: ${e.message}")
            false
        }
    }

    suspend fun resetPasswordOrPin(email: String, newPassOrPin: String): Boolean {
        return try {
            val targetEmail = email.lowercase().trim()
            val dbInstance = firestore
            if (FirebaseSyncManager.isFirebaseActive && dbInstance != null) {
                try {
                    dbInstance.collection("users").document(targetEmail)
                        .update("passwordOrPin", newPassOrPin)
                        .await()
                } catch (ex: Exception) {}
            }
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            prefs.edit().putString("pass_$targetEmail", newPassOrPin).apply()
            true
        } catch (e: Exception) {
            Log.e("MemberRepository", "Failed resetting password or PIN: ${e.message}")
            false
        }
    }

    suspend fun updateMemberProfile(
        email: String,
        newDisplayName: String,
        newWhatsapp: String,
        newAddress: String,
        newTier: String
    ): Boolean {
        return try {
            val targetEmail = email.lowercase().trim()
            val dbInstance = firestore
            if (FirebaseSyncManager.isFirebaseActive && dbInstance != null) {
                try {
                    dbInstance.collection("users").document(targetEmail)
                        .update(
                            mapOf(
                                "displayName" to newDisplayName,
                                "whatsapp" to newWhatsapp,
                                "address" to newAddress,
                                "priceCategory" to newTier
                            )
                        )
                        .await()
                } catch (ex: Exception) {}
            }
            com.yansproject.app.ui.AppSettings.saveMemberPriceCategory(context, newDisplayName, newTier)
            com.yansproject.app.ui.AppSettings.addMember(context, newDisplayName)
            val prefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("name_$targetEmail", newDisplayName)
                .putString("wa_$targetEmail", newWhatsapp)
                .putString("address_$targetEmail", newAddress)
                .putString("price_$targetEmail", newTier)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("MemberRepository", "Failed updating member profile: ${e.message}")
            false
        }
    }
}