package com.yansproject.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.AppDatabase
import com.yansproject.app.data.AppTypeConverters
import com.yansproject.app.data.Invoice
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.ui.AppSettings
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CustomerSuggestion(
    val name: String,
    val phone: String,
    val address: String = "",
    val isMember: Boolean = false,
    val memberTier: String = "",
    val totalOrders: Int = 1,
    val lastInvoiceNumber: String = ""
)

object CustomerSuggestionHelper {

    suspend fun getCustomerSuggestions(
        context: android.content.Context,
        invoicesList: List<Invoice>? = null,
        projectsList: List<ProjectCustom>? = null
    ): List<CustomerSuggestion> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CustomerSuggestion>()
        val registeredMemberNames = AppSettings.getMembers(context).map { it.trim() }.toSet()
        val memberMap = mutableMapOf<String, CustomerSuggestion>()

        // 1. Collect Registered Members
        for (memberName in registeredMemberNames) {
            val detail = AppSettings.getMemberDetail(context, memberName)
            val phone = detail?.whatsapp?.trim() ?: ""
            val address = detail?.address?.trim() ?: ""
            val tier = detail?.priceCategory?.ifBlank { "Member" } ?: "Member"
            val item = CustomerSuggestion(
                name = memberName,
                phone = phone,
                address = address,
                isMember = true,
                memberTier = tier,
                totalOrders = 0,
                lastInvoiceNumber = ""
            )
            memberMap[memberName.lowercase()] = item
            result.add(item)
        }

        // 2. Collect Invoices
        val db = AppDatabase.getDatabase(context)
        val invoices = invoicesList ?: try { db.invoiceDao().getInvoicesList() } catch (e: Exception) { emptyList() }
        val projects = projectsList ?: try { db.projectDao().getAllProjectsList() } catch (e: Exception) { emptyList() }
        val converters = AppTypeConverters()

        val nonMemberMap = mutableMapOf<String, CustomerSuggestion>()

        for (inv in invoices) {
            val rawName = inv.clientName.trim()
            if (rawName.isBlank() || isGenericName(rawName)) continue

            val lowerName = rawName.lowercase()
            if (memberMap.containsKey(lowerName)) {
                val existing = memberMap[lowerName]!!
                memberMap[lowerName] = existing.copy(
                    totalOrders = existing.totalOrders + 1,
                    lastInvoiceNumber = if (inv.invoiceNumber.isNotBlank()) inv.invoiceNumber else existing.lastInvoiceNumber
                )
                continue
            }

            val phone = inv.clientPhone.trim()
            val items = try { converters.toInvoiceItemList(inv.itemsJson) } catch (e: Exception) { emptyList() }
            val address = items.find { it.description.startsWith("__ADDRESS__:") }?.description?.removePrefix("__ADDRESS__:")?.trim() ?: ""

            val existing = nonMemberMap[lowerName]
            if (existing == null) {
                nonMemberMap[lowerName] = CustomerSuggestion(
                    name = rawName,
                    phone = phone,
                    address = address,
                    isMember = false,
                    totalOrders = 1,
                    lastInvoiceNumber = inv.invoiceNumber
                )
            } else {
                nonMemberMap[lowerName] = existing.copy(
                    phone = phone.ifBlank { existing.phone },
                    address = address.ifBlank { existing.address },
                    totalOrders = existing.totalOrders + 1,
                    lastInvoiceNumber = if (inv.invoiceNumber.isNotBlank()) inv.invoiceNumber else existing.lastInvoiceNumber
                )
            }
        }

        // 3. Collect Custom Projects
        for (proj in projects) {
            val rawName = proj.clientName.trim()
            if (rawName.isBlank() || isGenericName(rawName)) continue
            val lowerName = rawName.lowercase()
            if (memberMap.containsKey(lowerName)) continue

            val phone = proj.clientPhone.trim()
            val address = proj.clientAddress.trim()

            val existing = nonMemberMap[lowerName]
            if (existing == null) {
                nonMemberMap[lowerName] = CustomerSuggestion(
                    name = rawName,
                    phone = phone,
                    address = address,
                    isMember = false,
                    totalOrders = 1,
                    lastInvoiceNumber = if (proj.invoiceNumber.isNotBlank()) proj.invoiceNumber else "PROJ-${proj.id}"
                )
            } else {
                nonMemberMap[lowerName] = existing.copy(
                    phone = phone.ifBlank { existing.phone },
                    address = address.ifBlank { existing.address },
                    totalOrders = existing.totalOrders + 1
                )
            }
        }

        val sortedNonMembers = nonMemberMap.values.sortedByDescending { it.totalOrders }
        result.addAll(sortedNonMembers)

        result
    }

    private fun isGenericName(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "pelanggan umum" || lower == "customer umum" || lower == "umum" || lower == "cash" || lower == "klien umum" || lower == "non member" || lower == "non-member"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSelectionSection(
    clientName: String,
    onClientNameChange: (String) -> Unit,
    clientPhone: String,
    onClientPhoneChange: (String) -> Unit,
    clientAddress: String,
    onClientAddressChange: (String) -> Unit,
    onPriceTypeSelect: ((String) -> Unit)? = null,
    invoicesList: List<Invoice>? = null,
    projectsList: List<ProjectCustom>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var suggestions by remember { mutableStateOf(listOf<CustomerSuggestion>()) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        suggestions = CustomerSuggestionHelper.getCustomerSuggestions(context, invoicesList, projectsList)
    }

    val members = remember(suggestions) { suggestions.filter { it.isMember } }
    val nonMembers = remember(suggestions) { suggestions.filter { !it.isMember } }

    val filteredSuggestions = remember(suggestions, clientName) {
        if (clientName.isBlank()) emptyList()
        else {
            val query = clientName.trim().lowercase()
            suggestions.filter {
                it.name.lowercase().contains(query) || it.phone.contains(query)
            }.take(6)
        }
    }

    val matchedMember = remember(suggestions, clientName) {
        if (clientName.isBlank()) null
        else members.find { it.name.equals(clientName.trim(), ignoreCase = true) }
    }

    val matchedNonMember = remember(suggestions, clientName, matchedMember) {
        if (matchedMember != null || clientName.isBlank()) null
        else nonMembers.find { it.name.equals(clientName.trim(), ignoreCase = true) }
    }

    // Auto-fill price type for members if callback provided
    LaunchedEffect(matchedMember) {
        if (matchedMember != null && onPriceTypeSelect != null) {
            onPriceTypeSelect(matchedMember.memberTier)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x2A0F3D3E)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Section Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(AgedGold, RoundedCornerShape(2.dp))
                )
                Text(
                    text = "1. DATA PELANGGAN / MEMBER",
                    fontSize = 11.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }

            // Customer Name Field
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = clientName,
                    onValueChange = {
                        onClientNameChange(it)
                        isDropdownExpanded = it.isNotBlank()
                    },
                    label = { Text("Nama Customer / Member", color = Color(0xFFA0AEC0)) },
                    placeholder = { Text("Ketik nama atau pilih dari saran...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sale_client_name"),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        if (clientName.isNotBlank()) {
                            IconButton(onClick = {
                                onClientNameChange("")
                                onClientPhoneChange("")
                                onClientAddressChange("")
                                isDropdownExpanded = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Icon(Icons.Outlined.PersonSearch, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = Color(0x44319795)
                    )
                )
            }

            // Live Autocomplete Suggestions Dropdown Box
            AnimatedVisibility(
                visible = isDropdownExpanded && filteredSuggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xFF0C2223),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Saran Data Pelanggan Ditemukan:", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { isDropdownExpanded = false }
                            )
                        }

                        Divider(color = Color(0x33319795), thickness = 0.8.dp)

                        filteredSuggestions.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onClientNameChange(item.name)
                                        if (item.phone.isNotBlank()) onClientPhoneChange(item.phone)
                                        if (item.address.isNotBlank()) onClientAddressChange(item.address)
                                        if (item.isMember && onPriceTypeSelect != null) {
                                            onPriceTypeSelect(item.memberTier)
                                        }
                                        isDropdownExpanded = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            if (item.isMember) AgedGold.copy(alpha = 0.2f) else HighlightSoftCyan.copy(alpha = 0.15f),
                                            CircleShape
                                        )
                                        .border(
                                            0.8.dp,
                                            if (item.isMember) AgedGold else HighlightSoftCyan,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isMember) Icons.Default.Star else Icons.Outlined.Autorenew,
                                        contentDescription = null,
                                        tint = if (item.isMember) AgedGold else HighlightSoftCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.name,
                                            fontSize = 12.sp,
                                            color = TextLight,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = if (item.isMember) AlertGreen.copy(alpha = 0.2f) else HighlightSoftCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(0.5.dp, if (item.isMember) AlertGreen else HighlightSoftCyan.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = if (item.isMember) "MEMBER • ${item.memberTier}" else "NON-MEMBER (${item.totalOrders}x Trx)",
                                                fontSize = 9.sp,
                                                color = if (item.isMember) AlertGreen else HighlightSoftCyan,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    if (item.phone.isNotBlank() || item.address.isNotBlank()) {
                                        Text(
                                            text = listOfNotNull(
                                                item.phone.takeIf { it.isNotBlank() }?.let { "WA: $it" },
                                                item.address.takeIf { it.isNotBlank() }?.let { "Alamat: $it" }
                                            ).joinToString(" • "),
                                            fontSize = 10.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Horizontal Chips Quick Selector (Registered Members & Non-Member Repeat Order)
            if (members.isNotEmpty() || nonMembers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Pilih Cepat Pelanggan Terdaftar / Repeat Order:",
                        color = AgedGold.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        // Section 1: Members
                        items(members) { item ->
                            val isSelected = clientName.equals(item.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onClientNameChange("")
                                        onClientPhoneChange("")
                                        onClientAddressChange("")
                                    } else {
                                        onClientNameChange(item.name)
                                        if (item.phone.isNotBlank()) onClientPhoneChange(item.phone)
                                        if (item.address.isNotBlank()) onClientAddressChange(item.address)
                                        if (onPriceTypeSelect != null) onPriceTypeSelect(item.memberTier)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isSelected) ShadowBlack else AgedGold,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "${item.name} [Member]",
                                        fontSize = 11.sp,
                                        color = if (isSelected) ShadowBlack else TextLight,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AgedGold,
                                    containerColor = PrimaryDarkTeal
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BorderGrey,
                                    selectedBorderColor = AgedGold
                                )
                            )
                        }

                        // Section 2: Non-Member Repeat Customers
                        items(nonMembers) { item ->
                            val isSelected = clientName.equals(item.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onClientNameChange("")
                                        onClientPhoneChange("")
                                        onClientAddressChange("")
                                    } else {
                                        onClientNameChange(item.name)
                                        if (item.phone.isNotBlank()) onClientPhoneChange(item.phone)
                                        if (item.address.isNotBlank()) onClientAddressChange(item.address)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Autorenew,
                                        contentDescription = null,
                                        tint = if (isSelected) ShadowBlack else HighlightSoftCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "${item.name} (${item.totalOrders}x Order)",
                                        fontSize = 11.sp,
                                        color = if (isSelected) ShadowBlack else TextLight,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HighlightSoftCyan,
                                    containerColor = Color(0xFF102D2E)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = HighlightSoftCyan.copy(alpha = 0.5f),
                                    selectedBorderColor = HighlightSoftCyan
                                )
                            )
                        }
                    }
                }
            }

            // Status Banner: Registered Member Matched
            if (matchedMember != null) {
                Surface(
                    color = AlertGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AlertGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AlertGreen, modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                text = "AKUN MEMBER TERDAFTAR • Otomatis Tier: ${matchedMember.memberTier}",
                                color = AlertGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Data WhatsApp & Alamat Pengiriman terisi otomatis dari sistem member.",
                                color = TextLight,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Status Banner: Non-Member Repeat Order Customer Matched
            if (matchedNonMember != null) {
                Surface(
                    color = HighlightSoftCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, HighlightSoftCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(18.dp))
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "PELANGGAN NON-MEMBER (REPEAT ORDER)",
                                    color = HighlightSoftCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Surface(
                                    color = HighlightSoftCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${matchedNonMember.totalOrders}x Trx Selesai",
                                        color = HighlightSoftCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Data WA (${matchedNonMember.phone.ifBlank { "-" }}) & Alamat terisi otomatis dari riwayat invoice. Status tetap Non-Member.",
                                color = TextLight,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // WhatsApp Phone Field
            OutlinedTextField(
                value = clientPhone,
                onValueChange = { onClientPhoneChange(it.filter { char -> char.isDigit() || char == '+' }) },
                label = { Text("No. WhatsApp (Opsional)", color = Color(0xFFA0AEC0)) },
                placeholder = { Text("Contoh: 08123456789", color = TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                leadingIcon = {
                    Icon(Icons.Outlined.Phone, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sale_client_phone"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey
                )
            )

            // Delivery Address Field
            OutlinedTextField(
                value = clientAddress,
                onValueChange = { onClientAddressChange(it) },
                label = { Text("Alamat Pengiriman / Customer (Opsional)", color = Color(0xFFA0AEC0)) },
                placeholder = { Text("Masukkan alamat lengkap customer...", color = TextMuted) },
                leadingIcon = {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = AgedGold, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sale_client_address"),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgedGold,
                    unfocusedBorderColor = BorderGrey
                )
            )
        }
    }
}
