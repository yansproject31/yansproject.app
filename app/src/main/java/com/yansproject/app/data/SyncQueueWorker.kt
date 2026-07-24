package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.yansproject.app.data.YansRoomDatabase
import com.yansproject.app.data.OfflineActionEntity
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = YansRoomDatabase.getDatabase(applicationContext)
        val actionDao = database.offlineActionDao()
        val actions = actionDao.getAllActions()

        if (actions.isEmpty()) {
            Log.d("SyncQueueWorker", "Offline actions queue is empty. Sync completed successfully.")
            return@withContext Result.success()
        }

        Log.d("SyncQueueWorker", "Found ${actions.size} offline actions to sync.")

        val prefs = applicationContext.getSharedPreferences("api_health_prefs", Context.MODE_PRIVATE)
        val rawN8nUrl = prefs.getString("n8n_url", "https://primary-production.shared.n8n.cloud") ?: "https://primary-production.shared.n8n.cloud"
        // Ensure valid endpoint URL
        val n8nUrlStr = if (rawN8nUrl.startsWith("http")) rawN8nUrl else "https://$rawN8nUrl"

        var hasFailure = false

        for (action in actions) {
            try {
                val success = sendToN8n(n8nUrlStr, action)
                if (success) {
                    actionDao.deleteAction(action)
                    Log.d("SyncQueueWorker", "Successfully synced and deleted action ID: ${action.id}")
                } else {
                    hasFailure = true
                    // Increment retry count
                    val updatedAction = action.copy(retryCount = action.retryCount + 1)
                    actionDao.updateAction(updatedAction)
                    Log.w("SyncQueueWorker", "Failed to sync action ID: ${action.id}, will retry later.")
                }
            } catch (e: Exception) {
                hasFailure = true
                val updatedAction = action.copy(retryCount = action.retryCount + 1)
                actionDao.updateAction(updatedAction)
                Log.e("SyncQueueWorker", "Exception syncing action ID: ${action.id}", e)
            }
        }

        if (hasFailure) {
            Log.w("SyncQueueWorker", "Some actions failed to sync. Scheduling retry.")
            Result.retry()
        } else {
            Log.d("SyncQueueWorker", "All offline actions synced successfully.")
            Result.success()
        }
    }

    private fun sendToN8n(baseUrl: String, action: OfflineActionEntity): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(baseUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Yans-Target", action.targetCollection)
            connection.setRequestProperty("X-Yans-Timestamp", action.timestamp.toString())

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(action.stringPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d("SyncQueueWorker", "Webhook connection completed with code: $responseCode")
            
            // Accept standard 2xx successful responses
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e("SyncQueueWorker", "HTTP transport failed for URL: $baseUrl", e)
            false
        } finally {
            connection?.disconnect()
        }
    }
}
