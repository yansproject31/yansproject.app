package com.yansproject.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.yansproject.app.ui.theme.glassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFormatModule(
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Load or initialize default values with robust fallback
    val dataInvoiceFooter = try { AppSettings.getInvoiceFooter(context).ifBlank { null } } catch (e: Exception) { null }
    var invoiceFooter by remember { mutableStateOf(dataInvoiceFooter ?: "Hatur Tengkyu") }

    val dataProjectPrefix = try { AppSettings.getProjectPrefix(context).ifBlank { null } } catch (e: Exception) { null }
    var projectPrefix by remember { mutableStateOf(dataProjectPrefix ?: "YP") }

    val dataInvoicePrefix = try { AppSettings.getInvoicePrefix(context).ifBlank { null } } catch (e: Exception) { null }
    var invoicePrefix by remember { mutableStateOf(dataInvoicePrefix ?: "INV") }

    // Persist default values if initially empty
    LaunchedEffect(Unit) {
        try {
            if (AppSettings.getInvoiceFooter(context).isBlank()) {
                AppSettings.setInvoiceFooter(context, "Hatur Tengkyu")
            }
            if (AppSettings.getProjectPrefix(context).isBlank()) {
                AppSettings.setProjectPrefix(context, "YP")
            }
            if (AppSettings.getInvoicePrefix(context).isBlank()) {
                AppSettings.setInvoicePrefix(context, "INV")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = AccentAgedGold,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "FORMAT DOKUMEN (OFFLINE-FIRST)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = AccentAgedGold
            )
        }

        Text(
            text = "Seluruh isian dimuat langsung dari basis data lokal secara offline. Perubahan hanya disinkronisasi ke Cloud (Firebase) ketika Anda menekan tombol Simpan.",
            fontSize = 11.sp,
            color = TextNonActive,
            lineHeight = 16.sp
        )

        // Card Form 1: Footer Note
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CATATAN KAKI DOKUMEN (FOOTER)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold
                )

                OutlinedTextField(
                    value = invoiceFooter,
                    onValueChange = { invoiceFooter = it },
                    label = { Text("Catatan Kaki Invoice (Footer Note)", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    singleLine = false,
                    maxLines = 3
                )

                Text(
                    text = "Catatan kaki ini akan tertera otomatis di bagian bawah cetak PDF invoice fisik / digital untuk syarat & ketentuan pembayaran.",
                    fontSize = 10.sp,
                    color = TextNonActive,
                    lineHeight = 14.sp
                )
            }
        }

        // Card Form 2: Prefix
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "FORMAT NOMOR INVOICE & PROJECT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = projectPrefix,
                        onValueChange = { projectPrefix = it.trim().uppercase() },
                        label = { Text("Prefix Project", color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = TextIsiSoftGray,
                            focusedBorderColor = AccentAgedGold,
                            unfocusedBorderColor = DividerDarkCyanGray,
                            cursorColor = HighlightSoftCyan
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = invoicePrefix,
                        onValueChange = { invoicePrefix = it.trim().uppercase() },
                        label = { Text("Prefix Invoice", color = TextNonActive) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = TextIsiSoftGray,
                            focusedBorderColor = AccentAgedGold,
                            unfocusedBorderColor = DividerDarkCyanGray,
                            cursorColor = HighlightSoftCyan
                        ),
                        singleLine = true
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                if (invoiceFooter.isBlank() || projectPrefix.isBlank() || invoicePrefix.isBlank()) {
                    com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi seluruh field wajib!")
                    return@Button
                }

                isSaving = true
                coroutineScope.launch {
                    try {
                        // 1. Save to AppSettings (Local DB / SharedPreferences)
                        AppSettings.setInvoiceFooter(context, invoiceFooter.trim())
                        AppSettings.setProjectPrefix(context, projectPrefix.trim().uppercase())
                        AppSettings.setInvoicePrefix(context, invoicePrefix.trim().uppercase())

                        // 2. Asynchronously save to Firebase Cloud (Firestore)
                        val firestoreData = mapOf(
                            "invoice_footer" to invoiceFooter.trim(),
                            "project_prefix" to projectPrefix.trim().uppercase(),
                            "invoice_prefix" to invoicePrefix.trim().uppercase(),
                            "updated_at" to System.currentTimeMillis()
                        )

                        try {
                            withContext(Dispatchers.IO) {
                                FirebaseFirestore.getInstance()
                                    .collection("settings")
                                    .document("document_format")
                                    .set(firestoreData)
                            }
                            com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Format Dokumen berhasil disimpan ke Lokal & Cloud!")
                            onSaveSuccess()
                        } catch (e: Exception) {
                            com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Disimpan Lokal. Sinkronisasi Cloud tertunda (Offline)")
                            onSaveSuccess()
                        }
                    } catch (e: Exception) {
                        com.yansproject.app.ui.util.FeedbackManager.triggerError(context, "Gagal menyimpan: ${e.localizedMessage}")
                    } finally {
                        isSaving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = HighlightSoftCyan,
                contentColor = SecondaryShadowBlackTeal
            ),
            shape = RoundedCornerShape(10.dp),
            enabled = !isSaving
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SecondaryShadowBlackTeal,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = if (isSaving) "MENYIMPAN..." else "SIMPAN PERUBAHAN",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
