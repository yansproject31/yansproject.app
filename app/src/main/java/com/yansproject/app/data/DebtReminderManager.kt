package com.yansproject.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Trigger payload for automated WhatsApp reminder webhook.
 */
data class DebtReminderPayload(
    val transactionToken: String,
    val invoiceNumber: String,
    val clientName: String,
    val clientPhone: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val remainingBalance: Double,
    val dueDate: Long,
    val reminderText: String
)

/**
 * Controller for triggering asynchrounous Debt Reminders (WhatsApp/Email Webhooks via n8n).
 */
class DebtReminderManager(private val context: Context) {

    private val TAG = "DebtReminderManager"
    private val appDb = AppDatabase.getDatabase(context)

    /**
     * Compiles secure payload and triggers a debt collector message for partially paid/unpaid invoices.
     */
    suspend fun triggerInvoiceReminder(invoice: Invoice): Boolean = withContext(Dispatchers.IO) {
        val remaining = invoice.remainingPayment
        if (remaining <= 0.0) {
            Log.w(TAG, "Cannot trigger reminder for fully paid invoice: ${invoice.invoiceNumber}")
            return@withContext false
        }

        // 1. Compile clean secure payload
        val token = "TXN-SEC-${UUID.randomUUID().toString().uppercase().take(8)}"
        
        val reminderMessage = "Halo ${invoice.clientName},\n\n" +
                "Kami dari YANSPROJECT.ID ingin menginfokan mengenai Invoice No: *${invoice.invoiceNumber}* " +
                "dengan sisa tagihan sebesar *${IdrAccountingEngine.formatRupiah(remaining)}*. " +
                "Mohon melakukan pelunasan invoice.\n\n" +
                "Terima kasih atas kepercayaan Anda bermitra dengan YANSPROJECT.ID!"

        val payload = DebtReminderPayload(
            transactionToken = token,
            invoiceNumber = invoice.invoiceNumber,
            clientName = invoice.clientName,
            clientPhone = invoice.clientPhone,
            totalAmount = invoice.totalAmount,
            paidAmount = invoice.paidAmount,
            remainingBalance = remaining,
            dueDate = invoice.dueDate,
            reminderText = reminderMessage
        )

        // 2. Load Endpoint URL
        val prefs = context.getSharedPreferences("api_health_prefs", Context.MODE_PRIVATE)
        val rawN8nUrl = prefs.getString("n8n_url", "https://primary-production.shared.n8n.cloud")?.trim() ?: "https://primary-production.shared.n8n.cloud"
        if (rawN8nUrl.isBlank()) {
            val failureDetail = "Configuration failure: Empty n8n webhook URL setting."
            Log.e(TAG, failureDetail)
            appDb.auditLogDao().insertLog(
                AuditLog(
                    activity = "DEBT_COLLECTOR_FAILED",
                    details = "Failed triggering WhatsApp reminder for Invoice ${invoice.invoiceNumber}. $failureDetail"
                )
            )
            return@withContext false
        }

        val n8nBase = if (rawN8nUrl.startsWith("http://") || rawN8nUrl.startsWith("https://")) rawN8nUrl else "https://$rawN8nUrl"
        val reminderWebhookUrl = "$n8nBase/webhook/yans-debt-reminder"

        Log.d(TAG, "Triggering WhatsApp Reminder Webhook -> $reminderWebhookUrl")

        // 3. Post asynchronously to n8n Webhook
        var connection: HttpURLConnection? = null
        var failureReason: String? = null
        val success = try {
            val url = URL(reminderWebhookUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Secure-Trigger", "YANSPROJECT-DEBT-COLLECTOR")

            val jsonBody = JSONObject().apply {
                put("transactionToken", payload.transactionToken)
                put("invoiceNumber", payload.invoiceNumber)
                put("clientName", payload.clientName)
                put("clientPhone", payload.clientPhone)
                put("totalAmount", payload.totalAmount)
                put("paidAmount", payload.paidAmount)
                put("remainingBalance", payload.remainingBalance)
                put("dueDate", payload.dueDate)
                put("reminderText", payload.reminderText)
                put("timestamp", System.currentTimeMillis())
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                Log.d(TAG, "Debt Reminder trigger succeeded with HTTP Code: $code")
                true
            } else {
                failureReason = "HTTP failure with response code $code"
                Log.e(TAG, "Debt Reminder trigger failed: $failureReason")
                false
            }
        } catch (e: java.net.MalformedURLException) {
            failureReason = "Configuration failure: Malformed URL $reminderWebhookUrl (${e.message})"
            Log.e(TAG, failureReason, e)
            false
        } catch (e: java.io.IOException) {
            failureReason = "Transport/Network failure connecting to $reminderWebhookUrl (${e.message})"
            Log.e(TAG, failureReason, e)
            false
        } catch (e: Exception) {
            failureReason = "Unexpected failure (${e.javaClass.simpleName}): ${e.message}"
            Log.e(TAG, failureReason, e)
            false
        } finally {
            connection?.disconnect()
        }

        // 4. Trace in local AuditLog
        if (success) {
            appDb.auditLogDao().insertLog(
                AuditLog(
                    activity = "DEBT_COLLECTOR_TRIGGERED",
                    details = "WhatsApp debt reminder successfully sent for Invoice ${invoice.invoiceNumber} to client ${invoice.clientName} (${invoice.clientPhone}). Sisa: ${IdrAccountingEngine.formatRupiah(remaining)}."
                )
            )
        } else {
            appDb.auditLogDao().insertLog(
                AuditLog(
                    activity = "DEBT_COLLECTOR_FAILED",
                    details = "Failed triggering WhatsApp reminder for Invoice ${invoice.invoiceNumber}. Reason: ${failureReason ?: "Unknown"}"
                )
            )
        }

        return@withContext success
    }
}
