package com.yansproject.app.ui.invoice

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yansproject.app.data.CustomProject
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.Invoice
import com.yansproject.app.ui.DualInvoiceManagerViewModel
import com.yansproject.app.ui.components.*
import com.yansproject.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualInvoiceDashboardScreen(
    viewModel: DualInvoiceManagerViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToEditor: (isCustom: Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Pill Filter state
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Unpaid", "Paid", "Partially Paid")

    // Modal state
    var showActionHubByInvoiceId by remember { mutableStateOf<String?>(null) }
    var isActionHubCustom by remember { mutableStateOf(false) }
    var showPaymentRecordByInvoiceId by remember { mutableStateOf<String?>(null) }
    var isPaymentCustom by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isScrollingUp = remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                true
            } else {
                listState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    // Filter logic
    val filteredInvoices = remember(state.ajibqobulInvoices, selectedFilter) {
        state.ajibqobulInvoices.filter { inv ->
            val remaining = inv.totalAmount - inv.paidAmount
            when (selectedFilter) {
                "All" -> true
                "Unpaid" -> remaining > 0 && inv.paidAmount == 0.0
                "Overdue" -> remaining > 0 && inv.dueDate < System.currentTimeMillis()
                "Paid" -> remaining <= 0.0
                "Partially Paid" -> remaining > 0 && inv.paidAmount > 0.0
                else -> true
            }
        }
    }

    val filteredCustomProjects = remember(state.customProjectInvoices, selectedFilter) {
        state.customProjectInvoices.filter { proj ->
            val remaining = proj.remainingBalance
            when (selectedFilter) {
                "All" -> true
                "Unpaid" -> remaining > 0 && proj.paidAmount == 0.0
                "Overdue" -> remaining > 0 && proj.issueDate + (86400000 * 14) < System.currentTimeMillis() // assume 14 days due
                "Paid" -> remaining <= 0.0
                "Partially Paid" -> remaining > 0 && proj.paidAmount > 0.0
                else -> true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            YansTopAppBar(
                title = "YANSPROJECT.ID INVOICE HUB",
                subtitle = "Manajemen Faktur & Pelacakan Pembayaran Multi-Channel",
                navigationIcon = { YansBackButton(onClick = onNavigateBack) }
            )
        },
        floatingActionButton = {
            // Perfect FAB: Icon-Only, scale animated on scroll
            AnimatedVisibility(
                visible = isScrollingUp.value || !listState.isScrollInProgress,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // FAB for Ajibqobul Sales
                    FloatingActionButton(
                        onClick = { onNavigateToEditor(false) },
                        containerColor = PrimaryDarkTeal,
                        contentColor = AccentAgedGold,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("add_ajib_invoice_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Invoice Stock/Ajibqobul",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // FAB for Custom Projects
                    FloatingActionButton(
                        onClick = { onNavigateToEditor(true) },
                        containerColor = AccentAgedGold,
                        contentColor = SecondaryShadowBlackTeal,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.testTag("add_custom_invoice_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Project Custom",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Enterprise 3-Tier Financial Indicator Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DividerDarkCyanGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "METRIK KEUANGAN DUAL-STREAM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = HighlightSoftCyan,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Ditagih", color = TextNonActive, fontSize = 11.sp)
                            Text(
                                text = IdrAccountingEngine.formatRupiahNoCents(state.summary.totalInvoicedAmount),
                                color = TextOnCarbon,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total Terbayar", color = StatusSuccessCyan, fontSize = 11.sp)
                            Text(
                                text = IdrAccountingEngine.formatRupiahNoCents(state.summary.totalPaidAmount),
                                color = HighlightSoftCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sisa Piutang", color = AccentAgedGold, fontSize = 11.sp)
                            Text(
                                text = IdrAccountingEngine.formatRupiahNoCents(state.summary.totalRemainingBalance),
                                color = AccentAgedGold,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Pill-Tabs Filter using LazyRow
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    val bgCol by animateColorAsState(
                        targetValue = if (isSelected) PrimaryDarkTeal else SurfaceDarkTealSurface,
                        animationSpec = tween(durationMillis = 200)
                    )
                    val textCol by animateColorAsState(
                        targetValue = if (isSelected) AccentAgedGold else TextIsiSoftGray,
                        animationSpec = tween(durationMillis = 200)
                    )

                    // Calculate Count dynamically
                    val count = when (filter) {
                        "All" -> state.ajibqobulInvoices.size + state.customProjectInvoices.size
                        "Unpaid" -> state.ajibqobulInvoices.count { (it.totalAmount - it.paidAmount) > 0 && it.paidAmount == 0.0 } +
                                state.customProjectInvoices.count { it.remainingBalance > 0 && it.paidAmount == 0.0 }
                        "Overdue" -> state.ajibqobulInvoices.count { (it.totalAmount - it.paidAmount) > 0 && it.dueDate < System.currentTimeMillis() } +
                                state.customProjectInvoices.count { it.remainingBalance > 0 && it.issueDate + 1209600000 < System.currentTimeMillis() }
                        "Paid" -> state.ajibqobulInvoices.count { (it.totalAmount - it.paidAmount) <= 0.0 } +
                                state.customProjectInvoices.count { it.remainingBalance <= 0.0 }
                        "Partially Paid" -> state.ajibqobulInvoices.count { (it.totalAmount - it.paidAmount) > 0 && it.paidAmount > 0.0 } +
                                state.customProjectInvoices.count { it.remainingBalance > 0 && it.paidAmount > 0.0 }
                        else -> 0
                    }

                    Surface(
                        color = bgCol,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = if (isSelected) HighlightSoftCyan else DividerDarkCyanGray,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable { selectedFilter = filter }
                            .testTag("filter_pill_$filter")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = textCol,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            // Circle badge with item counts
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        color = if (isSelected) HighlightSoftCyan else DividerDarkCyanGray,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (isSelected) SecondaryShadowBlackTeal else TextIsiSoftGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Dual List Representation
            if (filteredInvoices.isEmpty() && filteredCustomProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Empty",
                            tint = TextNonActive,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tidak Ada Invoice Ditemukan",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextIsiSoftGray)
                        )
                        Text(
                            text = "Tidak ada data transaksi invoice.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextNonActive)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                ) {
                    // Custom Project Invoices Section
                    if (filteredCustomProjects.isNotEmpty()) {
                        item {
                            Text(
                                text = "CUSTOM PROJECTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentAgedGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredCustomProjects) { project ->
                            InvoiceCard(
                                title = project.projectName,
                                idNumber = project.id,
                                clientName = project.clientName,
                                dateLong = project.issueDate,
                                totalAmount = project.grandTotal,
                                paidAmount = project.paidAmount,
                                isCustomProject = true,
                                onActionClicked = {
                                    showActionHubByInvoiceId = project.id
                                    isActionHubCustom = true
                                },
                                onPaymentClicked = {
                                    showPaymentRecordByInvoiceId = project.id
                                    isPaymentCustom = true
                                }
                            )
                        }
                    }

                    // Ajibqobul Standard Invoices Section
                    if (filteredInvoices.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "AJIBQOBUL STANDARD INVOICES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = HighlightSoftCyan,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(filteredInvoices) { invoice ->
                            InvoiceCard(
                                title = "Penjualan Toko",
                                idNumber = invoice.invoiceNumber,
                                clientName = invoice.clientName,
                                dateLong = invoice.issueDate,
                                totalAmount = invoice.totalAmount,
                                paidAmount = invoice.paidAmount,
                                isCustomProject = false,
                                onActionClicked = {
                                    showActionHubByInvoiceId = invoice.invoiceNumber
                                    isActionHubCustom = false
                                },
                                onPaymentClicked = {
                                    showPaymentRecordByInvoiceId = invoice.invoiceNumber
                                    isPaymentCustom = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Action Hub Trigger Menu
    val actionHubId = showActionHubByInvoiceId
    if (actionHubId != null) {
        ActionHubBottomSheet(
            invoiceNumber = actionHubId,
            isCustomProject = isActionHubCustom,
            onDismiss = { showActionHubByInvoiceId = null },
            viewModel = viewModel
        )
    }

    // Record Payment Bottom Sheet Trigger
    val paymentRecordId = showPaymentRecordByInvoiceId
    if (paymentRecordId != null) {
        val currentId = paymentRecordId
        val outstandingAmount = if (isPaymentCustom) {
            val proj = state.customProjectInvoices.find { it.id == currentId }
            proj?.remainingBalance ?: 0.0
        } else {
            val inv = state.ajibqobulInvoices.find { it.invoiceNumber == currentId }
            inv?.remainingPayment ?: 0.0
        }

        PaymentRecordBottomSheet(
            invoiceNumber = currentId,
            isCustomProject = isPaymentCustom,
            remainingBalance = outstandingAmount,
            onDismiss = { showPaymentRecordByInvoiceId = null },
            onPaymentRecorded = { amount, method, triggerWa ->
                viewModel.receivePaymentOnInvoice(currentId, isPaymentCustom, amount, method)
                if (triggerWa) {
                    Toast.makeText(context, "Resi WhatsApp dipicu asinkron via n8n webhook!", Toast.LENGTH_LONG).show()
                }
                showPaymentRecordByInvoiceId = null
            }
        )
    }
}

@Composable
fun InvoiceCard(
    title: String,
    idNumber: String,
    clientName: String,
    dateLong: Long,
    totalAmount: Double,
    paidAmount: Double,
    isCustomProject: Boolean,
    onActionClicked: () -> Unit,
    onPaymentClicked: () -> Unit
) {
    val balanceDue = (totalAmount - paidAmount).coerceAtLeast(0.0)
    val formattedDate = remember(dateLong) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        sdf.format(Date(dateLong))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClicked() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDarkCard),
        border = BorderStroke(1.dp, if (balanceDue <= 0.0) HighlightSoftCyan.copy(alpha = 0.4f) else DividerDarkCyanGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Badge & Action Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Dynamic AssistChip Badge
                    if (isCustomProject) {
                        Surface(
                            color = SecondaryShadowBlackTeal,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HighlightSoftCyan)
                        ) {
                            Text(
                                text = "CUSTOM PROJECT",
                                color = HighlightSoftCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = SecondaryShadowBlackTeal,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, AccentAgedGold)
                        ) {
                            Text(
                                text = "AJIBQOBUL",
                                color = AccentAgedGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = idNumber,
                        color = TextIsiSoftGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onActionClicked,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Opsi Aksi",
                        tint = AccentAgedGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Row: Financial balance & detail summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Left Column: Large Display Balance Due
                Column {
                    Text(
                        text = "SISA TAGIHAN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextNonActive,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = IdrAccountingEngine.formatRupiahNoCents(balanceDue),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (balanceDue <= 0) HighlightSoftCyan else AccentAgedGold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                // Right Column: Client & Date Details
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = clientName,
                        color = TextOnCarbon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedDate,
                        color = TextNonActive,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status indicator bar or payment button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info regarding total and paid
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Total Tagihan", color = TextNonActive, fontSize = 9.sp)
                        Text(
                            text = IdrAccountingEngine.formatRupiahNoCents(totalAmount),
                            color = TextOnCarbon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Telah Dibayar", color = TextNonActive, fontSize = 9.sp)
                        Text(
                            text = IdrAccountingEngine.formatRupiahNoCents(paidAmount),
                            color = HighlightSoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Conditional CTA Button
                if (balanceDue > 0.0) {
                    Button(
                        onClick = onPaymentClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryDarkTeal,
                            contentColor = AccentAgedGold
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Rekam Bayar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Lunas",
                            tint = HighlightSoftCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "LUNAS",
                            color = HighlightSoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
