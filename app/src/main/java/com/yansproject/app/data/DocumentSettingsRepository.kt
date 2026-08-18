package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.SettingsSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Keep
enum class DocumentSyncStatus {
    LOCAL_SAVED,
    SYNC_PENDING,
    SYNCED,
    SYNC_FAILED
}

@Keep
data class DocumentSettingsData(
    val storeName: String = BusinessIdentityProvider.DEFAULT_COMPANY_NAME,
    val storeLogo: String = "",
    val address: String = BusinessIdentityProvider.DEFAULT_STORE_ADDRESS,
    val whatsapp: String = BusinessIdentityProvider.DEFAULT_SUPPORT_WHATSAPP,
    val email: String = BusinessIdentityProvider.DEFAULT_SUPPORT_EMAIL,
    val website: String = "",
    val bankAccount: String = "",
    val bankName: String = "",
    val bankHolder: String = "",
    val invoiceFooter: String = "",
    val projectPrefix: String = "YP",
    val invoicePrefix: String = "INV",
    val defaultMargin: Double = 35.0,
    val defaultTax: Double = 11.0,
    val syncStatus: DocumentSyncStatus = DocumentSyncStatus.LOCAL_SAVED,
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)

/**
 * DocumentSettingsRepository: Authoritative repository for ERP Document & Business Settings.
 * Architecture Flow: UI -> Room/local source -> outbox -> Firebase -> sync confirmation.
 * Guarantees: Settings are observable via StateFlow. Never reports SYNCED solely on local save.
 */
class DocumentSettingsRepository private constructor(private val context: Context) {

    private val TAG = "DocumentSettingsRepo"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _settingsState = MutableStateFlow(loadSettingsFromLocal())
    val settingsState: StateFlow<DocumentSettingsData> = _settingsState.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: DocumentSettingsRepository? = null

        fun getInstance(context: Context): DocumentSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DocumentSettingsRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getSettings(): DocumentSettingsData = _settingsState.value

    private fun loadSettingsFromLocal(): DocumentSettingsData {
        val syncEnum = when (AppSettings.getSyncState(context)) {
            SettingsSyncState.LOCAL_SAVED -> DocumentSyncStatus.LOCAL_SAVED
            SettingsSyncState.SYNC_PENDING -> DocumentSyncStatus.SYNC_PENDING
            SettingsSyncState.SYNCED -> DocumentSyncStatus.SYNCED
            SettingsSyncState.SYNC_FAILED -> DocumentSyncStatus.SYNC_FAILED
        }

        return DocumentSettingsData(
            storeName = AppSettings.getStoreName(context),
            storeLogo = AppSettings.getStoreLogo(context),
            address = AppSettings.getAddress(context),
            whatsapp = AppSettings.getWhatsApp(context),
            email = AppSettings.getEmail(context),
            website = AppSettings.getWebsite(context),
            bankAccount = AppSettings.getAccountNumber(context),
            bankName = AppSettings.getBankName(context),
            bankHolder = AppSettings.getAccountHolder(context),
            invoiceFooter = AppSettings.getInvoiceFooter(context),
            projectPrefix = AppSettings.getProjectPrefix(context),
            invoicePrefix = AppSettings.getInvoicePrefix(context),
            defaultMargin = AppSettings.getDefaultMargin(context),
            defaultTax = AppSettings.getDefaultTax(context),
            syncStatus = syncEnum,
            lastModifiedTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Updates document settings:
     * 1. Saves locally (state becomes LOCAL_SAVED).
     * 2. Sets state to SYNC_PENDING and queues cloud synchronization.
     * 3. Upon cloud confirmation sets state to SYNCED (or SYNC_FAILED on error).
     */
    fun saveSettings(
        updatedData: DocumentSettingsData,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            try {
                // Step 1: Local Save
                AppSettings.setStoreName(context, updatedData.storeName)
                AppSettings.setStoreLogo(context, updatedData.storeLogo)
                AppSettings.setAddress(context, updatedData.address)
                AppSettings.setWhatsApp(context, updatedData.whatsapp)
                AppSettings.setEmail(context, updatedData.email)
                AppSettings.setWebsite(context, updatedData.website)
                AppSettings.setAccountNumber(context, updatedData.bankAccount)
                AppSettings.setBankName(context, updatedData.bankName)
                AppSettings.setAccountHolder(context, updatedData.bankHolder)
                AppSettings.setInvoiceFooter(context, updatedData.invoiceFooter)
                AppSettings.setProjectPrefix(context, updatedData.projectPrefix)
                AppSettings.setInvoicePrefix(context, updatedData.invoicePrefix)
                AppSettings.setDefaultMargin(context, updatedData.defaultMargin)
                AppSettings.setDefaultTax(context, updatedData.defaultTax)

                // Marked locally saved & sync pending
                AppSettings.setSyncState(context, SettingsSyncState.SYNC_PENDING)
                _settingsState.value = updatedData.copy(
                    syncStatus = DocumentSyncStatus.SYNC_PENDING,
                    lastModifiedTimestamp = System.currentTimeMillis()
                )

                // Step 2: Push to Firebase Cloud Outbox
                val syncSuccess = FirebaseSyncManager.syncBusinessProfileToCloud(
                    storeName = updatedData.storeName,
                    address = updatedData.address,
                    whatsapp = updatedData.whatsapp,
                    email = updatedData.email,
                    bankAccount = updatedData.bankAccount,
                    bankName = updatedData.bankName,
                    bankHolder = updatedData.bankHolder
                )

                if (syncSuccess) {
                    AppSettings.setSyncState(context, SettingsSyncState.SYNCED)
                    _settingsState.value = _settingsState.value.copy(
                        syncStatus = DocumentSyncStatus.SYNCED
                    )
                    Log.i(TAG, "Document settings synced to cloud successfully.")
                    onComplete?.invoke(true)
                } else {
                    AppSettings.setSyncState(context, SettingsSyncState.SYNC_FAILED)
                    _settingsState.value = _settingsState.value.copy(
                        syncStatus = DocumentSyncStatus.SYNC_FAILED
                    )
                    Log.w(TAG, "Document settings sync pending/failed. Retaining LOCAL_SAVED status.")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving document settings: ${e.message}", e)
                AppSettings.setSyncState(context, SettingsSyncState.SYNC_FAILED)
                _settingsState.value = _settingsState.value.copy(
                    syncStatus = DocumentSyncStatus.SYNC_FAILED
                )
                onComplete?.invoke(false)
            }
        }
    }

    fun refreshFromCloud() {
        scope.launch {
            try {
                FirebaseSyncManager.pullBusinessProfileFromCloud(context)
                _settingsState.value = loadSettingsFromLocal()
            } catch (e: Exception) {
                Log.e(TAG, "Failed refreshing settings from cloud: ${e.message}", e)
            }
        }
    }
}
