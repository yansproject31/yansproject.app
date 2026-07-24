package com.yansproject.app.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
fun BusinessIdentityModule(
    onSaveSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Initialize states with Offline-First defaults if local storage is empty
    val dataStoreName = try { AppSettings.getStoreName(context).ifBlank { null } } catch (e: Exception) { null }
    val storeNameDefaultValue = "YANSPROJECT.ID"
    var storeName by remember { mutableStateOf(dataStoreName ?: storeNameDefaultValue) }

    val dataAddress = try { AppSettings.getAddress(context).ifBlank { null } } catch (e: Exception) { null }
    val addressDefaultValue = "Tangerang, Banten"
    var address by remember { mutableStateOf(dataAddress ?: addressDefaultValue) }

    val dataWhatsApp = try { AppSettings.getWhatsApp(context).ifBlank { null } } catch (e: Exception) { null }
    val whatsappDefaultValue = "087777 3988 13"
    var whatsapp by remember { mutableStateOf(dataWhatsApp ?: whatsappDefaultValue) }

    val dataEmail = try { AppSettings.getEmail(context).ifBlank { null } } catch (e: Exception) { null }
    val emailDefaultValue = "yansart31@gmail.com"
    var email by remember { mutableStateOf(dataEmail ?: emailDefaultValue) }

    val dataWebsite = try { AppSettings.getWebsite(context).ifBlank { null } } catch (e: Exception) { null }
    val websiteDefaultValue = ""
    var website by remember { mutableStateOf(dataWebsite ?: websiteDefaultValue) }

    // Persist defaults back to AppSettings if they weren't already stored
    LaunchedEffect(Unit) {
        try {
            if (AppSettings.getStoreName(context).isBlank()) {
                AppSettings.setStoreName(context, "YANSPROJECT.ID")
            }
            if (AppSettings.getAddress(context).isBlank()) {
                AppSettings.setAddress(context, "Tangerang, Banten")
            }
            if (AppSettings.getWhatsApp(context).isBlank()) {
                AppSettings.setWhatsApp(context, "087777 3988 13")
            }
            if (AppSettings.getEmail(context).isBlank()) {
                AppSettings.setEmail(context, "yansart31@gmail.com")
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
                imageVector = Icons.Default.Business,
                contentDescription = null,
                tint = AccentAgedGold,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "IDENTITAS BISNIS (OFFLINE-FIRST)",
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

        // Card Form Group
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
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Nama Toko / Instansi", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
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
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat Lengkap", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    )
                )

                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("Nomor WhatsApp Aktif", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Resmi", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextIsiSoftGray,
                        focusedBorderColor = AccentAgedGold,
                        unfocusedBorderColor = DividerDarkCyanGray,
                        cursorColor = HighlightSoftCyan
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )

                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website Resmi (Opsional)", color = TextNonActive) },
                    modifier = Modifier.fillMaxWidth(),
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

        // Logo Active Preview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, DividerDarkCyanGray.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "LOGO RESMI INSTANSI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAgedGold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryShadowBlackTeal)
                        .border(1.dp, DividerDarkCyanGray, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = com.yansproject.app.R.drawable.ic_logo),
                        contentDescription = "Logo YANSPROJECT.ID",
                        tint = AccentAgedGold,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Text(
                    text = "Logo YANSPROJECT.ID Aktif",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Logo digunakan secara konsisten pada seluruh dokumen invoice, slip proyek, cetak thermal, dan berkas branding resmi.",
                    fontSize = 10.sp,
                    color = TextNonActive,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }

        // Save Button
        Button(
            onClick = {
                if (storeName.isBlank() || address.isBlank() || whatsapp.isBlank() || email.isBlank()) {
                    com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Harap isi seluruh field wajib!")
                    return@Button
                }

                val cleanWA = whatsapp.replace(" ", "").replace("-", "")
                val isValidWA = (cleanWA.startsWith("08") || cleanWA.startsWith("+62") || cleanWA.startsWith("62")) && cleanWA.all { it.isDigit() || it == '+' } && cleanWA.length in 10..15
                if (!isValidWA) {
                    com.yansproject.app.ui.util.FeedbackManager.triggerWarning(context, "Format nomor WhatsApp tidak valid! Gunakan format Indonesia (contoh: 0812xxxxxxxx atau +62812xxxxxxxx).")
                    return@Button
                }

                isSaving = true
                coroutineScope.launch {
                    try {
                        // 1. Save locally to AppSettings
                        AppSettings.setStoreName(context, storeName.trim())
                        AppSettings.setAddress(context, address.trim())
                        AppSettings.setWhatsApp(context, whatsapp.trim())
                        AppSettings.setEmail(context, email.trim())
                        AppSettings.setWebsite(context, website.trim())

                        // 2. Add local SQLite Audit Log entry
                        withContext(Dispatchers.IO) {
                            val auditLog = com.yansproject.app.data.AuditLog(
                                activity = "Update Identitas Bisnis",
                                details = "Mengubah nama toko ke '${storeName.trim()}', WA: '${whatsapp.trim()}'.",
                                adminName = "Owner"
                            )
                            com.yansproject.app.data.AppDatabase.getDatabase(context).auditLogDao().insertLog(auditLog)
                        }

                        // 3. Async save to Firebase Cloud (Firestore)
                        val firestoreData = mapOf(
                            "store_name" to storeName.trim(),
                            "store_address" to address.trim(),
                            "store_whatsapp" to whatsapp.trim(),
                            "store_email" to email.trim(),
                            "store_website" to website.trim(),
                            "updated_at" to System.currentTimeMillis()
                        )

                        try {
                            withContext(Dispatchers.IO) {
                                FirebaseFirestore.getInstance()
                                    .collection("settings")
                                    .document("business_identity")
                                    .set(firestoreData)
                            }
                            com.yansproject.app.ui.util.FeedbackManager.triggerSuccess(context, "Identitas Bisnis berhasil disimpan ke Lokal & Cloud!")
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
