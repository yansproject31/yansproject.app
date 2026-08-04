package com.yansproject.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncQueueWorker"
        private const val MAX_RETRY_LIMIT = 5
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = YansRoomDatabase.getDatabase(applicationContext)
        val actionDao = database.offlineActionDao()
        val actions = actionDao.getAllActions()

        if (actions.isEmpty()) {
            Log.d(TAG, "Offline actions queue is clean. Sync completed successfully.")
            return@withContext Result.success()
        }

        Log.i(TAG, "Found ${actions.size} offline pending actions to synchronize.")

        val prefs = applicationContext.getSharedPreferences("api_health_prefs", Context.MODE_PRIVATE)
        val rawN8nUrl = prefs.getString("n8n_url", "https://primary-production.shared.n8n.cloud") ?: "https://primary-production.shared.n8n.cloud"
        val n8nUrlStr = if (rawN8nUrl.startsWith("http")) rawN8nUrl else "https://$rawN8nUrl"

        var hasTemporaryFailure = false

        for (action in actions) {
            try {
                val syncResponse = sendToN8nDetailed(n8nUrlStr, action)
                when (syncResponse) {
                    is SyncResponse.Success -> {
                        actionDao.deleteAction(action)
                        Log.i(TAG, "Successfully synced action ID ${action.id} [${action.targetCollection}]. Deleted from queue.")
                    }
                    is SyncResponse.PermanentError -> {
                        actionDao.deleteAction(action)
                        Log.e(TAG, "Permanent Error (${syncResponse.code} - ${syncResponse.message}) for action ID ${action.id}. Purging action from queue.")
                    }
                    is SyncResponse.TemporaryError -> {
                        hasTemporaryFailure = true
                        val newRetryCount = action.retryCount + 1
                        if (newRetryCount >= MAX_RETRY_LIMIT) {
                            Log.e(TAG, "Max retry limit ($MAX_RETRY_LIMIT) reached for action ID ${action.id}. Dropping action to prevent queue starvation.")
                            actionDao.deleteAction(action)
                        } else {
                            val updatedAction = action.copy(retryCount = newRetryCount)
                            actionDao.updateAction(updatedAction)
                            Log.w(TAG, "Temporary failure (${syncResponse.message}) for action ID ${action.id} (Attempt $newRetryCount/$MAX_RETRY_LIMIT). Retrying later.")
                        }
                    }
                }
            } catch (e: Exception) {
                hasTemporaryFailure = true
                val newRetryCount = action.retryCount + 1
                if (newRetryCount >= MAX_RETRY_LIMIT) {
                    Log.e(TAG, "Max retry limit reached after exception for action ID ${action.id}. Dropping action.", e)
                    actionDao.deleteAction(action)
                } else {
                    val updatedAction = action.copy(retryCount = newRetryCount)
                    actionDao.updateAction(updatedAction)
                    Log.e(TAG, "Exception during sync for action ID ${action.id} (Attempt $newRetryCount/$MAX_RETRY_LIMIT): ${e.message}", e)
                }
            }
        }

        if (hasTemporaryFailure) {
            Log.w(TAG, "One or more queue items encountered temporary sync errors. Scheduling worker retry.")
            Result.retry()
        } else {
            Log.i(TAG, "All offline queue actions processed and synchronized successfully.")
            Result.success()
        }
    }

    private sealed class SyncResponse {
        object Success : SyncResponse()
        data class PermanentError(val code: Int, val message: String) : SyncResponse()
        data class TemporaryError(val message: String) : SyncResponse()
    }

    private fun sendToN8nDetailed(baseUrl: String, action: OfflineActionEntity): SyncResponse {
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
            Log.d(TAG, "Webhook connection returned response status $responseCode for action ID ${action.id}")
            
            when (responseCode) {
                in 200..299 -> SyncResponse.Success
                in 400..499 -> SyncResponse.PermanentError(responseCode, "Client HTTP $responseCode")
                else -> SyncResponse.TemporaryError("Server HTTP $responseCode")
            }
        } catch (e: java.net.SocketTimeoutException) {
            SyncResponse.TemporaryError("Connection Timeout: ${e.message}")
        } catch (e: java.io.IOException) {
            SyncResponse.TemporaryError("Network I/O Failure: ${e.message}")
        } catch (e: Exception) {
            SyncResponse.PermanentError(-1, e.message ?: "Unknown Transport Error")
        } finally {
            connection?.disconnect()
        }
    }
}
