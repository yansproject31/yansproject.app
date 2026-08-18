package com.yansproject.app.ui.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.ui.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class MemberMutationState {
    IDLE,
    CREATING,
    UPDATING,
    DELETING,
    SUCCESS,
    SYNC_PENDING,
    DUPLICATE,
    INVALID,
    FAILED
}

class MemberViewModel : ViewModel() {
    private val _members = MutableStateFlow<List<MemberModel>>(emptyList())
    val members: StateFlow<List<MemberModel>> = _members.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deleteStatus = MutableStateFlow<String?>(null)
    val deleteStatus: StateFlow<String?> = _deleteStatus.asStateFlow()

    private val _mutationState = MutableStateFlow(MemberMutationState.IDLE)
    val mutationState: StateFlow<MemberMutationState> = _mutationState.asStateFlow()

    private var observationJob: Job? = null

    fun loadMembers(context: Context) {
        // Cancel any prior observation job before starting a new lifecycle-owned observation
        observationJob?.cancel()
        observationJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val repository = com.yansproject.app.data.MemberRepository(context)
                val localMemberNames = AppSettings.getMembers(context)
                val mapByIdentifier = mutableMapOf<String, MemberModel>()

                // Populate with AppSettings member names
                for (name in localMemberNames) {
                    val detail = AppSettings.getMemberDetail(context, name)
                    val email = detail?.email ?: ""
                    val isOwner = com.yansproject.app.data.BusinessIdentityProvider.isOwnerEmail(email, context) ||
                            email.equals("yansproject.id31@gmail.com", ignoreCase = true)

                    if (!isOwner) {
                        val uid = email.ifBlank { name.lowercase().trim() }
                        mapByIdentifier[uid] = MemberModel(
                            email = email,
                            displayName = name,
                            role = "MEMBER",
                            priceCategory = detail?.priceCategory ?: "Member",
                            passwordOrPin = "••••", // Mask credentials
                            whatsapp = detail?.whatsapp ?: "",
                            address = detail?.address ?: "",
                            statusAkun = "Aktif",
                            statusVerifikasi = "Terverifikasi",
                            memberUid = uid
                        )
                    }
                }

                _members.value = mapByIdentifier.values.toList()

                // Start Real-Time Flow Observation if Firebase is active
                if (FirebaseSyncManager.isFirebaseActive) {
                    repository.observeMembersRealtime().collect { observedMembers ->
                        val cleanObserved = observedMembers.filter { m ->
                            val isOwner = m.role.equals("OWNER", ignoreCase = true) ||
                                    m.role.equals("ADMIN", ignoreCase = true) ||
                                    com.yansproject.app.data.BusinessIdentityProvider.isOwnerEmail(m.email, context)
                            !isOwner
                        }.map { m ->
                            m.copy(passwordOrPin = "••••") // Mask credentials
                        }.distinctBy { it.effectiveUid }

                        _members.value = cleanObserved
                    }
                }
            } catch (e: Exception) {
                Log.e("MemberViewModel", "Error loading or observing members: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        observationJob?.cancel()
        observationJob = null
    }

    fun registerMember(
        context: Context,
        email: String,
        passwordOrPin: String,
        displayName: String,
        priceCategory: String,
        role: String,
        whatsapp: String = "",
        address: String = "",
        onComplete: (Boolean, String) -> Unit
    ) {
        if (email.isBlank() || displayName.isBlank()) {
            _mutationState.value = MemberMutationState.INVALID
            onComplete(false, "Email dan Nama Member tidak boleh kosong.")
            return
        }

        val existingMember = _members.value.find { it.email.equals(email.trim(), ignoreCase = true) }
        if (existingMember != null) {
            _mutationState.value = MemberMutationState.DUPLICATE
            onComplete(false, "Member dengan email ini sudah terdaftar.")
            return
        }

        _mutationState.value = MemberMutationState.CREATING
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = FirebaseSyncManager.registerMemberOnCloud(
                    context,
                    email,
                    passwordOrPin,
                    displayName,
                    priceCategory,
                    role,
                    whatsapp,
                    address
                )
                if (result == "SUCCESS") {
                    _mutationState.value = MemberMutationState.SUCCESS
                    loadMembers(context)
                    onComplete(true, "Akun '$displayName' ($role) berhasil didaftarkan!")
                } else if (result == "SYNC_PENDING" || result == "LOCAL_ONLY") {
                    _mutationState.value = MemberMutationState.SYNC_PENDING
                    loadMembers(context)
                    onComplete(true, "Akun '$displayName' disimpan lokal (Sinkronisasi Cloud tertunda).")
                } else {
                    _mutationState.value = MemberMutationState.FAILED
                    onComplete(false, "Pendaftaran Gagal: $result")
                }
            } catch (e: Exception) {
                _mutationState.value = MemberMutationState.FAILED
                onComplete(false, "Pendaftaran Gagal: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateMemberTier(
        context: Context,
        email: String,
        displayName: String,
        newTier: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        _mutationState.value = MemberMutationState.UPDATING
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val repo = com.yansproject.app.data.MemberRepository(context)
                val success = repo.updateMemberTier(email, displayName, newTier)
                withContext(Dispatchers.Main) {
                    if (success) {
                        _mutationState.value = MemberMutationState.SUCCESS
                        loadMembers(context)
                        onComplete(true, "Tier harga member '$displayName' berhasil diperbarui ke '$newTier'!")
                    } else {
                        _mutationState.value = MemberMutationState.FAILED
                        onComplete(false, "Gagal memperbarui tier harga.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _mutationState.value = MemberMutationState.FAILED
                    onComplete(false, "Error: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPasswordOrPin(
        context: Context,
        email: String,
        displayName: String,
        newPassOrPin: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        _mutationState.value = MemberMutationState.UPDATING
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val repo = com.yansproject.app.data.MemberRepository(context)
                val success = repo.resetPasswordOrPin(email, newPassOrPin)
                withContext(Dispatchers.Main) {
                    if (success) {
                        _mutationState.value = MemberMutationState.SUCCESS
                        loadMembers(context)
                        onComplete(true, "Password / PIN member '$displayName' berhasil di-reset!")
                    } else {
                        _mutationState.value = MemberMutationState.FAILED
                        onComplete(false, "Gagal me-reset password / PIN.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _mutationState.value = MemberMutationState.FAILED
                    onComplete(false, "Error: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateMemberProfile(
        context: Context,
        email: String,
        newDisplayName: String,
        newWhatsapp: String,
        newAddress: String,
        newTier: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        _mutationState.value = MemberMutationState.UPDATING
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val repo = com.yansproject.app.data.MemberRepository(context)
                val success = repo.updateMemberProfile(email, newDisplayName, newWhatsapp, newAddress, newTier)
                withContext(Dispatchers.Main) {
                    if (success) {
                        _mutationState.value = MemberMutationState.SUCCESS
                        loadMembers(context)
                        onComplete(true, "Profil member '$newDisplayName' berhasil diperbarui!")
                    } else {
                        _mutationState.value = MemberMutationState.FAILED
                        onComplete(false, "Gagal memperbarui profil member.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _mutationState.value = MemberMutationState.FAILED
                    onComplete(false, "Error: ${e.message}")
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteMember(userId: String, context: Context, member: MemberModel, onComplete: (Boolean, String) -> Unit) {
        _mutationState.value = MemberMutationState.DELETING
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _deleteStatus.value = null
            var success = true
            var message = ""
            try {
                val targetEmail = userId.lowercase().trim()
                val memberEmail = member.email.lowercase().trim()
                val displayName = member.displayName.trim()

                // 1. Thoroughly clear ALL matching credentials in SharedPreferences
                val credPrefs = context.getSharedPreferences("yans_local_credentials", Context.MODE_PRIVATE)
                val editor = credPrefs.edit()
                val allEntries = credPrefs.all
                for ((key, value) in allEntries) {
                    val matchesEmail = key.contains(targetEmail, ignoreCase = true) ||
                                       (memberEmail.isNotEmpty() && key.contains(memberEmail, ignoreCase = true))
                    val matchesName = value is String && value.equals(displayName, ignoreCase = true) && key.startsWith("name_")
                    if (matchesEmail || matchesName) {
                        val suffix = if (key.startsWith("name_")) key.substring("name_".length) else ""
                        editor.remove(key)
                        if (suffix.isNotEmpty()) {
                            editor.remove("pass_$suffix")
                            editor.remove("name_$suffix")
                            editor.remove("role_$suffix")
                            editor.remove("price_$suffix")
                            editor.remove("wa_$suffix")
                            editor.remove("address_$suffix")
                            editor.remove("created_at_$suffix")
                            editor.remove("last_login_$suffix")
                            editor.remove("status_akun_$suffix")
                            editor.remove("status_verifikasi_$suffix")
                        }
                    }
                }
                editor.apply()

                // 2. Remove member name from local AppSettings list and deleted list
                AppSettings.removeMember(context, displayName)
                AppSettings.deleteMemberPermanently(context, displayName)

                // 3. Delete corresponding transactions from SQLite locally if needed
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.openHelper.writableDatabase.execSQL(
                        "DELETE FROM invoices WHERE clientName = ?",
                        arrayOf(displayName)
                    )
                } catch (e: Exception) {
                    Log.e("MemberViewModel", "Failed clearing local invoices for member: ${e.message}")
                }

                // 4. Asynchronously queue deletion on Cloud
                if (FirebaseSyncManager.isFirebaseActive) {
                    FirebaseSyncManager.deleteItemFromCloud("users", targetEmail)
                    if (memberEmail.isNotEmpty() && memberEmail != targetEmail) {
                        FirebaseSyncManager.deleteItemFromCloud("users", memberEmail)
                    }
                    try {
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val docs = firestore.collection("users").whereEqualTo("displayName", displayName).get().await()
                        for (doc in docs.documents) {
                            doc.reference.delete().await()
                        }
                    } catch (fe: Exception) {
                        Log.e("MemberViewModel", "Error deleting firestore user doc by displayName: ${fe.message}")
                    }
                }

                _deleteStatus.value = "Member berhasil dihapus total"
                _mutationState.value = MemberMutationState.SUCCESS
                message = "Member '$displayName' berhasil dihapus total!"
                
                // Immediately reload local UI
                loadMembers(context)
            } catch (e: Exception) {
                success = false
                _mutationState.value = MemberMutationState.FAILED
                _deleteStatus.value = "Error: ${e.message}"
                message = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
                withContext(Dispatchers.Main) {
                    onComplete(success, message)
                }
            }
        }
    }
}

object memberDao {
    fun deleteById(userId: String) {
        Log.d("memberDao", "Mock Room deleteById for userId: $userId")
    }
}
