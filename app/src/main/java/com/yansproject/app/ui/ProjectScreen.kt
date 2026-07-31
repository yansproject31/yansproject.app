package com.yansproject.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.yansproject.app.data.ProjectCustom
import com.yansproject.app.ui.theme.*
import androidx.navigation.NavController

@Composable
fun ProjectScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsState()
    val currentUser by com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
    val isOwner = currentUser?.role == com.yansproject.app.data.UserRole.OWNER
    var showAddDialog by remember { mutableStateOf(false) }
    val searchQuery by viewModel.projectSearchQuery.collectAsState()
    val selectedStatusFilter by viewModel.projectStatusFilter.collectAsState()
    val selectedDateFilter by viewModel.projectDateFilter.collectAsState()
    var projectToDelete by remember { mutableStateOf<ProjectCustom?>(null) }
    
    var selectedDeadlineFilter by remember { mutableStateOf("All") }
    var selectedProjectForDetail by remember { mutableStateOf<ProjectCustom?>(null) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.projectScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.projectScrollOffset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.projectScrollIndex = index
                viewModel.projectScrollOffset = offset
            }
    }

    val calendarNow = remember { java.util.Calendar.getInstance() }

    BackHandler(enabled = showAddDialog || projectToDelete != null || selectedProjectForDetail != null) {
        if (showAddDialog) {
            showAddDialog = false
        } else if (projectToDelete != null) {
            projectToDelete = null
        } else if (selectedProjectForDetail != null) {
            selectedProjectForDetail = null
        }
    }

    fun isToday(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
               calendarNow.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun isThisWeek(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
               calendarNow.get(java.util.Calendar.WEEK_OF_YEAR) == cal.get(java.util.Calendar.WEEK_OF_YEAR)
    }

    fun isThisMonth(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
               calendarNow.get(java.util.Calendar.MONTH) == cal.get(java.util.Calendar.MONTH)
    }

    fun isThisYear(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
    }

    fun isDeadlineToday(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return calendarNow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
               calendarNow.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun isDeadlineTomorrow(timestamp: Long): Boolean {
        val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val res = tomorrow.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
               tomorrow.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
        // Reset calendarNow to current instant
        calendarNow.timeInMillis = System.currentTimeMillis()
        return res
    }

    fun isDeadlineOverdue(timestamp: Long, isClosed: Boolean): Boolean {
        return !isClosed && timestamp < System.currentTimeMillis() && !isDeadlineToday(timestamp)
    }

    val filteredProjects = projects.filter {
        val matchesStatus = when (selectedStatusFilter) {
            "All" -> true
            "Belum DP" -> it.paidAmount == 0.0 || it.paymentStatus == "Belum Bayar"
            "Desain" -> it.currentStage == "Desain" || it.currentStage == "ACC Desain"
            "Open PO" -> it.currentStage == "Open PO"
            "Produksi" -> it.currentStage == "Produksi"
            "QC" -> it.currentStage == "QC"
            "Packing" -> it.currentStage == "Packing"
            "Pengiriman" -> it.currentStage == "Pengiriman"
            "Selesai" -> it.currentStage == "Project Closed" || it.status == "Completed"
            else -> it.status == selectedStatusFilter
        }
        
        val matchesSearch = if (searchQuery.trim().isEmpty()) true else {
            it.projectName.contains(searchQuery, ignoreCase = true) || 
            it.clientName.contains(searchQuery, ignoreCase = true) ||
            it.clientPhone.contains(searchQuery, ignoreCase = true) ||
            it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
            it.pic.contains(searchQuery, ignoreCase = true)
        }
        
        val matchesDate = when (selectedDateFilter) {
            "Semua Waktu" -> true
            "Hari Ini" -> isToday(it.startDate)
            "Minggu Ini" -> isThisWeek(it.startDate)
            "Bulan Ini" -> isThisMonth(it.startDate)
            "Tahun Ini" -> isThisYear(it.startDate)
            else -> true
        }

        val matchesDeadline = when (selectedDeadlineFilter) {
            "All" -> true
            "Today" -> isDeadlineToday(it.endDate)
            "Tomorrow" -> isDeadlineTomorrow(it.endDate)
            "Overdue" -> isDeadlineOverdue(it.endDate, it.currentStage == "Project Closed" || it.status == "Completed")
            else -> true
        }
        
        matchesStatus && matchesSearch && matchesDate && matchesDeadline
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (isOwner) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.testTag("add_project_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Project")
                }
            }
        }
    ) { innerPadding ->
        val isSyncing by viewModel.isSyncing.collectAsState()
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = {
                viewModel.refreshData(context) { success, error ->
                    if (success) {
                        viewModel.showGlobalSnackbar("Data berhasil diperbarui.")
                    } else {
                        viewModel.showGlobalSnackbar("Sinkronisasi gagal: $error")
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Spacer(modifier = Modifier.height(4.dp))
            // --- Header ---
            Column {
                Text(
                    text = "MANAJEMEN PROYEK",
                    fontSize = 12.sp,
                    color = AgedGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Project Custom",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // --- Deadline Interactive Metric Badges ---
            val activeProjs = projects.filter { !it.isDeleted && it.currentStage != "Project Closed" && it.status != "Completed" && it.status != "Cancelled" }
            val todayDeadlinesCount = activeProjs.count { isDeadlineToday(it.endDate) }
            val tomorrowDeadlinesCount = activeProjs.count { isDeadlineTomorrow(it.endDate) }
            val overdueDeadlinesCount = projects.count { isDeadlineOverdue(it.endDate, it.currentStage == "Project Closed" || it.status == "Completed") && !it.isDeleted }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Today Deadline Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedDeadlineFilter == "Today") AgedGold.copy(alpha = 0.2f) else CardGrey
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedDeadlineFilter = if (selectedDeadlineFilter == "Today") "All" else "Today" },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selectedDeadlineFilter == "Today") AgedGold else BorderGrey
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Deadline Hari Ini", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$todayDeadlinesCount", fontSize = 16.sp, color = AlertOrange, fontWeight = FontWeight.Bold)
                    }
                }

                // Tomorrow Deadline Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedDeadlineFilter == "Tomorrow") HighlightSoftCyan.copy(alpha = 0.2f) else CardGrey
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedDeadlineFilter = if (selectedDeadlineFilter == "Tomorrow") "All" else "Tomorrow" },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selectedDeadlineFilter == "Tomorrow") HighlightSoftCyan else BorderGrey
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Deadline Besok", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$tomorrowDeadlinesCount", fontSize = 16.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                    }
                }

                // Overdue Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedDeadlineFilter == "Overdue") AlertRed.copy(alpha = 0.2f) else CardGrey
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedDeadlineFilter = if (selectedDeadlineFilter == "Overdue") "All" else "Overdue" },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selectedDeadlineFilter == "Overdue") AlertRed else BorderGrey
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Terlambat / Overdue", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$overdueDeadlinesCount", fontSize = 16.sp, color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- Search & Filter ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.projectSearchQuery.value = it },
                    placeholder = { Text("Cari klien, invoice, PIC...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("project_search"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AgedGold,
                        unfocusedBorderColor = CardGrey
                    )
                )

                // Expanded status filters dropdown
                var showFilterMenu by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showFilterMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = TextLight),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.FilterList, contentDescription = "Filter")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = selectedStatusFilter, fontSize = 13.sp)
                    }
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        modifier = Modifier.background(CardGrey)
                    ) {
                        listOf(
                            "All", "Planning", "In Progress", "Completed", "Cancelled", 
                            "Belum DP", "Desain", "Open PO", "Produksi", "QC", "Packing", "Pengiriman", "Selesai"
                        ).forEach { status ->
                            DropdownMenuItem(
                                text = { Text(text = status, color = TextLight) },
                                onClick = {
                                    viewModel.projectStatusFilter.value = status
                                    showFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // --- Date Status Filters (Horizontal Scrollable) ---
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf("Semua Waktu", "Hari Ini", "Minggu Ini", "Bulan Ini", "Tahun Ini")) { filter ->
                    val isSelected = filter == selectedDateFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AgedGold else CardGrey)
                            .border(1.dp, if (isSelected) AgedGold else BorderGrey, RoundedCornerShape(20.dp))
                            .clickable { viewModel.projectDateFilter.value = filter }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) ShadowBlack else TextLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- Projects List ---
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        icon = Icons.Outlined.WorkOff,
                        title = "Belum Ada Project Custom",
                        description = "Seluruh pengerjaan pesanan kustom, data ukuran pelanggan, sisa tagihan, dan progress produksi akan tercatat rapi di sini saat Anda membuat project baru."
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProjects) { project ->
                        ProjectItemCard(
                            project = project,
                            isOwner = isOwner,
                            onUpdateStatus = { newStatus ->
                                viewModel.updateProject(project.copy(status = newStatus))
                            },
                            onDelete = {
                                projectToDelete = project
                            },
                            onOpenDetail = {
                                selectedProjectForDetail = project
                            },
                            navController = navController
                        )
                    }
                }
            }
        }
    }

        // --- Dialog Add Project ---
        if (showAddDialog) {
            AddProjectDialog(
                onDismiss = { showAddDialog = false },
                onAddProject = { name, client, phone, desc, cost, paid, status, pType, sType, qXS, qS, qM, qL, qXL, qXXL, q3XL, q4XL, cInst, cAddr, cNotes, cPic ->
                    val isDuplicate = projects.any { it.projectName.equals(name, ignoreCase = true) }
                    if (isDuplicate) {
                        Toast.makeText(context, "Tolak: Nama Project '$name' sudah digunakan!", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.addProject(
                            projectName = name,
                            clientName = client,
                            clientPhone = phone,
                            description = desc,
                            totalCost = cost,
                            paidAmount = paid,
                            status = status,
                            startDate = System.currentTimeMillis(),
                            endDate = System.currentTimeMillis() + (86400000L * 30L), // Pre-set duration: 30 days
                            productType = pType,
                            sleeveType = sType,
                            qtyXS = qXS,
                            qtyS = qS,
                            qtyM = qM,
                            qtyL = qL,
                            qtyXL = qXL,
                            qtyXXL = qXXL,
                            qty3XL = q3XL,
                            qty4XL = q4XL,
                            clientInstitution = cInst,
                            clientAddress = cAddr,
                            clientNotes = cNotes,
                            pic = cPic
                        )
                        showAddDialog = false
                    }
                }
            )
        }

        // --- Dialog Konfirmasi Hapus ---
        if (projectToDelete != null) {
            YansConfirmDialog(
                title = "Konfirmasi Hapus Project",
                message = "Apakah Anda yakin ingin memindahkan project '${projectToDelete?.projectName}' ini ke Trash?",
                onConfirm = {
                    projectToDelete?.let { viewModel.deleteProject(it) }
                    projectToDelete = null
                },
                onDismiss = { projectToDelete = null }
            )
        }

        // --- Dialog Detail & Alur Kerja Project ---
        if (selectedProjectForDetail != null) {
            ProjectDetailDialog(
                project = selectedProjectForDetail!!,
                viewModel = viewModel,
                onDismiss = { selectedProjectForDetail = null }
            )
        }
    }
}

@Composable
fun ProjectItemCard(
    project: ProjectCustom,
    isOwner: Boolean,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
    navController: NavController? = null
) {
    val statusColor = when (project.status) {
        "In Progress", "Sedang Berjalan" -> CyanPulse
        "Completed", "Selesai" -> AgedGold
        else -> AlertBlue
    }

    SharedPremiumCard(
        onClick = {
            onOpenDetail()
            try {
                navController?.navigate("custom_project_detail/${project.id}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        borderGlowColor = statusColor.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth().testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Nomor Project & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nomor Project (e.g., PRJ-005)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ShadowBlack)
                        .border(1.dp, AgedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PRJ-${String.format("%03d", project.id)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgedGold
                    )
                }
                
                // Status Badge (Pill-shaped)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(statusColor)
                        )
                        Text(
                            text = if (project.status == "In Progress") "Sedang Berjalan" else if (project.status == "Completed") "Selesai" else project.status,
                            fontSize = 10.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nama Project (Headline)
            Text(
                text = project.projectName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nama Customer / Klien (Tebal, TextWhite)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = AgedGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = project.clientName,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (project.clientPhone.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${project.clientPhone})",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description & Multi-item Breakdown (Priority 3)
            val displayDesc = com.yansproject.app.ui.ProjectItemParser.getProjectDescription(project.description)
            val pItems = com.yansproject.app.ui.ProjectItemParser.getProjectItems(project.description)
            if (displayDesc.isNotEmpty()) {
                Text(
                    text = displayDesc,
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (pItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Rincian Item Pakaian:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                    pItems.forEach { item ->
                        Text(
                            text = "• ${item.productType} (${item.sleeveType}) - ${item.size}: ${item.qty} pcs @ ${FormatUtils.formatRupiah(item.price)}",
                            fontSize = 11.sp,
                            color = TextLight
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Specs Row: Total Qty & Deadline (Muted font for Deadline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Total Qty: ${project.totalQty} Pcs",
                        fontSize = 12.sp,
                        color = TextLight,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Deadline: ${FormatUtils.formatDate(project.endDate)}",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Pricing details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Total Harga", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = FormatUtils.formatRupiah(project.totalCost),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgedGold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Telah Dibayar", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = FormatUtils.formatRupiah(project.paidAmount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Sisa Tagihan", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = FormatUtils.formatRupiah(project.remainingPayment),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (project.remainingPayment > 0) AlertOrange else AlertGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            com.yansproject.app.ui.YansBluetoothPrinter.printProject(context, project)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .background(ShadowBlack, RoundedCornerShape(6.dp))
                            .border(1.dp, AgedGold, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Print,
                            contentDescription = "Cetak SPK",
                            tint = AgedGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Fast status update buttons
                    if (isOwner) {
                        if (project.status != "Completed" && project.status != "Cancelled") {
                            Button(
                                onClick = { onUpdateStatus("Completed") },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextLight),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Selesai", fontSize = 11.sp)
                            }
                        }
                        if (project.status == "Planning") {
                            Button(
                                onClick = { onUpdateStatus("In Progress") },
                                colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = AgedGold),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp).border(1.dp, AgedGold, RoundedCornerShape(6.dp))
                            ) {
                                Text("Mulai", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (isOwner) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Hapus Project", tint = AlertRed.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectDialog(
    onDismiss: () -> Unit,
    onAddProject: (
        name: String, client: String, phone: String, desc: String, cost: Double, paid: Double, status: String, 
        productType: String, sleeveType: String, qtyXS: Int, qtyS: Int, qtyM: Int, qtyL: Int, qtyXL: Int, qtyXXL: Int, qty3XL: Int, qty4XL: Int,
        clientInstitution: String, clientAddress: String, clientNotes: String, pic: String
    ) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("Planning") }
    
    var clientInstitution by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }
    var clientNotes by remember { mutableStateOf("") }
    var pic by remember { mutableStateOf("Owner") }

    // Multi-item builder state (Priority 3 & 4)
    val addedItems = remember { mutableStateListOf<ProjectItem>() }
    var itemProductType by remember { mutableStateOf("T-Shirt Reguler") }
    var itemSleeveType by remember { mutableStateOf("Pendek") }
    var itemSize by remember { mutableStateOf("S") }
    var itemQtyStr by remember { mutableStateOf("1") }

    // Manual input price states (No default prices)
    var priceRegulerPendekStr by remember { mutableStateOf("") }
    var priceRegulerPanjangStr by remember { mutableStateOf("") }
    var priceKidsPendekStr by remember { mutableStateOf("") }
    var priceKidsPanjangStr by remember { mutableStateOf("") }

    // Prefilled Production Template (Priority 11)
    var desc by remember {
        mutableStateOf(
            "--- TEMPLATE PRODUKSI ---\n" +
            "Metode: Sablon / Bordir\n" +
            "Desain: [Warna & Posisi]\n" +
            "Bahan Kaos: Cotton Combed 30s\n" +
            "Finishing: Plastisol / Discharge\n" +
            "QC Status: Pending"
        )
    }

    // Live calculation of item prices (Priority 5 & 6)
    fun calculateItemPrice(product: String, sleeve: String, size: String): Double {
        val base = when (product) {
            "T-Shirt Reguler" -> {
                if (sleeve == "Pendek") {
                    priceRegulerPendekStr.toDoubleOrNull() ?: 0.0
                } else {
                    priceRegulerPanjangStr.toDoubleOrNull() ?: 0.0
                }
            }
            "T-Shirt Kids" -> {
                val kidsBase = priceKidsPendekStr.toDoubleOrNull() ?: 0.0
                if (sleeve == "Pendek") {
                    kidsBase
                } else {
                    kidsBase + 5000.0 // (+) 5000 fixed default setting for Kids long sleeve
                }
            }
            else -> 0.0
        }
        val upsize = if (product == "T-Shirt Reguler") {
            when (size.uppercase()) {
                "XXL" -> AppSettings.getCustomUpsizeXXL(context)
                "3XL" -> AppSettings.getCustomUpsize3XL(context)
                "4XL" -> AppSettings.getCustomUpsize4XL(context)
                else -> 0.0
            }
        } else {
            0.0
        }
        return base + upsize
    }

    val currentItemPrice = calculateItemPrice(itemProductType, itemSleeveType, itemSize)
    val currentQty = itemQtyStr.toIntOrNull() ?: 0
    val currentItemSubtotal = currentItemPrice * currentQty

    // Totals calculations
    val totalQty = addedItems.sumOf { it.qty }
    val grandTotalCost = addedItems.sumOf { it.subtotal }

    var paidStr by remember { mutableStateOf("") }
    val paid = paidStr.toDoubleOrNull() ?: 0.0

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TAMBAH PROJECT CUSTOM", color = AgedGold, fontWeight = FontWeight.Black, fontSize = 16.sp)
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                item {
                    Text(
                        text = "Pencatatan Order Project Custom (PO)",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                // Customer Information
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Project / Event") },
                        placeholder = { Text("Contoh: PO Reuni SMA") },
                        modifier = Modifier.fillMaxWidth().testTag("add_project_name"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }
                item {
                    OutlinedTextField(
                        value = client,
                        onValueChange = { client = it },
                        label = { Text("Nama Klien") },
                        modifier = Modifier.fillMaxWidth().testTag("add_project_client"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { char -> char.isDigit() || char == '+' } },
                        label = { Text("No. HP Klien (WhatsApp - Opsional)") },
                        placeholder = { Text("Contoh: 08123456789") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("add_project_phone"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }

                item {
                    OutlinedTextField(
                        value = clientAddress,
                        onValueChange = { clientAddress = it },
                        label = { Text("Alamat Pengiriman / Customer") },
                        modifier = Modifier.fillMaxWidth().testTag("add_project_address"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }

                // BUILDER SECTION FOR A NEW ITEM
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SecondaryShadowBlackTeal),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "CONSTRUCT ITEM BARU", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            
                            // Product Selection (T-Shirt Reguler, T-Shirt Kids only - Priority 4)
                            Text(text = "Jenis Produk:", fontSize = 11.sp, color = TextLight)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("T-Shirt Reguler", "T-Shirt Kids").forEach { type ->
                                    val isSel = itemProductType == type
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) DarkTeal else CardGrey)
                                            .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                            .clickable { 
                                                itemProductType = type
                                                if (type == "T-Shirt Kids" && (itemSize == "3XL" || itemSize == "4XL")) {
                                                    itemSize = "S"
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) AgedGold else TextLight)
                                    }
                                }
                            }

                            // Sleeve Selection
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Jenis Lengan:", fontSize = 11.sp, color = TextLight, modifier = Modifier.weight(1f))
                                listOf("Pendek", "Panjang").forEach { sleeve ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { itemSleeveType = sleeve }
                                            .padding(end = 12.dp)
                                    ) {
                                        RadioButton(selected = itemSleeveType == sleeve, onClick = { itemSleeveType = sleeve })
                                        Text(text = sleeve, fontSize = 12.sp, color = TextLight)
                                    }
                                }
                            }

                            // Price Inputs (Manual Input, No Default)
                            if (itemProductType == "T-Shirt Reguler") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Harga Dasar Reguler:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = priceRegulerPendekStr,
                                            onValueChange = { priceRegulerPendekStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("Lengan Pendek (Rp)", fontSize = 10.sp) },
                                            placeholder = { Text("Contoh: 85000", fontSize = 10.sp) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("price_reguler_pendek"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                        )
                                        OutlinedTextField(
                                            value = priceRegulerPanjangStr,
                                            onValueChange = { priceRegulerPanjangStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("Lengan Panjang (Rp)", fontSize = 10.sp) },
                                            placeholder = { Text("Contoh: 95000", fontSize = 10.sp) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("price_reguler_panjang"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                        )
                                    }
                                }
                            } else if (itemProductType == "T-Shirt Kids") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = "Harga Dasar Kids:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = priceKidsPendekStr,
                                            onValueChange = { priceKidsPendekStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("Lengan Pendek (Rp)", fontSize = 10.sp) },
                                            placeholder = { Text("Contoh: 75000", fontSize = 10.sp) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("price_kids_pendek"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                        )
                                        OutlinedTextField(
                                            value = priceKidsPanjangStr,
                                            onValueChange = { priceKidsPanjangStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("Lengan Panjang (Rp)", fontSize = 10.sp) },
                                            placeholder = { Text("Contoh: 85000", fontSize = 10.sp) },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            modifier = Modifier.weight(1f).testTag("price_kids_panjang"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                                        )
                                    }
                                }
                            }

                            // Size Selection Scroll
                            val sizeOptions = if (itemProductType == "T-Shirt Kids") {
                                listOf("XS", "S", "M", "L", "XL", "XXL")
                             } else {
                                listOf("XS", "S", "M", "L", "XL", "XXL", "3XL", "4XL")
                             }
                            Text(text = "Ukuran:", fontSize = 11.sp, color = TextLight)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(sizeOptions) { sz ->
                                    val isSel = itemSize == sz
                                    val calculatedPriceLabel = calculateItemPrice(itemProductType, itemSleeveType, sz)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) DarkTeal else CardGrey)
                                            .border(1.dp, if (isSel) AgedGold else BorderGrey, RoundedCornerShape(6.dp))
                                            .clickable { itemSize = sz }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = sz, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) AgedGold else Color.White)
                                            Text(text = FormatUtils.formatRupiah(calculatedPriceLabel).replace("Rp ", ""), fontSize = 8.sp, color = TextMuted)
                                        }
                                    }
                                }
                            }

                            // Qty input + Live Preview
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = itemQtyStr,
                                    onValueChange = { itemQtyStr = it.filter { char -> char.isDigit() } },
                                    label = { Text("Jumlah (Pcs)") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    modifier = Modifier.width(100.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Harga/Pcs: ${FormatUtils.formatRupiah(currentItemPrice)}", fontSize = 11.sp, color = TextLight)
                                    Text(text = "Subtotal: ${FormatUtils.formatRupiah(currentItemSubtotal)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HighlightSoftCyan)
                                }
                            }

                            Button(
                                onClick = {
                                    val qtyVal = itemQtyStr.toIntOrNull() ?: 0
                                    if (qtyVal <= 0) {
                                        Toast.makeText(context, "Jumlah item harus lebih dari 0!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    
                                    // Check manual prices
                                    if (itemProductType == "T-Shirt Reguler") {
                                        val pShort = priceRegulerPendekStr.toDoubleOrNull() ?: 0.0
                                        val pLong = priceRegulerPanjangStr.toDoubleOrNull() ?: 0.0
                                        if (pShort <= 0.0 || pLong <= 0.0) {
                                            Toast.makeText(context, "Silakan input harga dasar Reguler Pendek & Panjang terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                    } else if (itemProductType == "T-Shirt Kids") {
                                        val pShort = priceKidsPendekStr.toDoubleOrNull() ?: 0.0
                                        val pLong = priceKidsPanjangStr.toDoubleOrNull() ?: 0.0
                                        if (pShort <= 0.0 || pLong <= 0.0) {
                                            Toast.makeText(context, "Silakan input harga dasar Kids Pendek & Panjang terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                    }

                                    // Add to list or aggregate if same configuration
                                    val existingIdx = addedItems.indexOfFirst {
                                        it.productType == itemProductType && it.sleeveType == itemSleeveType && it.size == itemSize
                                    }
                                    if (existingIdx != -1) {
                                        val prev = addedItems[existingIdx]
                                        val newQty = prev.qty + qtyVal
                                        addedItems[existingIdx] = prev.copy(
                                            qty = newQty,
                                            subtotal = prev.price * newQty
                                        )
                                    } else {
                                        addedItems.add(
                                            ProjectItem(
                                                productType = itemProductType,
                                                sleeveType = itemSleeveType,
                                                size = itemSize,
                                                qty = qtyVal,
                                                price = currentItemPrice,
                                                subtotal = currentItemSubtotal
                                            )
                                        )
                                    }
                                    itemQtyStr = "1"
                                    Toast.makeText(context, "Item ditambahkan ke project!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambahkan Item Pakaian", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // LIST OF ADDED ITEMS DISPLAY
                if (addedItems.isNotEmpty()) {
                    item {
                        Text(text = "DAFTAR ITEM PROJECT (${addedItems.size} Jenis):", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                    }

                    items(addedItems.toList()) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.productType, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    text = "Lengan ${item.sleeveType} • Size ${item.size} • ${item.qty} pcs",
                                    fontSize = 11.sp,
                                    color = TextLight
                                )
                                Text(
                                    text = "@ ${FormatUtils.formatRupiah(item.price)} | Subtotal: ${FormatUtils.formatRupiah(item.subtotal)}",
                                    fontSize = 11.sp,
                                    color = HighlightSoftCyan
                                )
                            }

                            IconButton(onClick = { addedItems.remove(item) }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Hapus", tint = AlertRed.copy(alpha = 0.8f))
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardGrey)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Belum ada rincian item pakaian. Tambahkan item di atas terlebih dahulu!", fontSize = 11.sp, color = AlertOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // TOTAL PRICE SUMMARY (AUTOMATED)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShadowBlack)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Kuantitas:", fontSize = 11.sp, color = TextMuted)
                            Text(text = "$totalQty Pcs", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Grand Total Nilai Project (Otomatis):", fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                            Text(text = FormatUtils.formatRupiah(grandTotalCost), fontSize = 13.sp, color = AgedGold, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                // DEPOSIT & NOTES INPUTS
                item {
                    OutlinedTextField(
                        value = paidStr,
                        onValueChange = { paidStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Uang Muka / Deposit (Rp)") },
                        placeholder = { Text("Contoh: 500000") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("add_project_paid"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }

                item {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Deskripsi Kerja / Detail Produksi") },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Status Awal:", fontSize = 13.sp, color = TextMuted)
                        listOf("Planning", "In Progress").forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { selectedStatus = s }
                            ) {
                                RadioButton(selected = selectedStatus == s, onClick = { selectedStatus = s })
                                Text(text = s, fontSize = 13.sp, color = TextLight, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                    Text("Batal")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            Toast.makeText(context, "Nama Project tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                        } else if (client.trim().isEmpty()) {
                            Toast.makeText(context, "Nama Klien tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                        } else if (phone.trim().isNotEmpty() && phone.length < 9) {
                            Toast.makeText(context, "Nomor WhatsApp Klien tidak valid (minimal 9 karakter)!", Toast.LENGTH_SHORT).show()
                        } else if (addedItems.isEmpty()) {
                            Toast.makeText(context, "Wajib menambahkan minimal 1 item pakaian ke project!", Toast.LENGTH_SHORT).show()
                        } else if (paid < 0.0) {
                            Toast.makeText(context, "Uang Muka tidak boleh negatif!", Toast.LENGTH_SHORT).show()
                        } else if (paid > grandTotalCost) {
                            Toast.makeText(context, "Uang Muka tidak boleh melebihi Nilai Project!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Aggregate sizes for legacy compatibility
                            val qtyXS = addedItems.filter { it.size == "XS" }.sumOf { it.qty }
                            val qtyS = addedItems.filter { it.size == "S" }.sumOf { it.qty }
                            val qtyM = addedItems.filter { it.size == "M" }.sumOf { it.qty }
                            val qtyL = addedItems.filter { it.size == "L" }.sumOf { it.qty }
                            val qtyXL = addedItems.filter { it.size == "XL" }.sumOf { it.qty }
                            val qtyXXL = addedItems.filter { it.size == "XXL" }.sumOf { it.qty }
                            val qty3XL = addedItems.filter { it.size == "3XL" }.sumOf { it.qty }
                            val qty4XL = addedItems.filter { it.size == "4XL" }.sumOf { it.qty }

                            // Serialize items inside description field (Priority 3)
                            val itemsSerialized = ProjectItemParser.serialize(addedItems)
                            val combinedDescription = desc.trim() + " ===ITEMS_DATA=== " + itemsSerialized

                            onAddProject(
                                name.trim(),
                                client.trim(),
                                phone.trim(),
                                combinedDescription,
                                grandTotalCost,
                                paid,
                                selectedStatus,
                                "Multi", // multi product types supported
                                "Multi", // multi sleeve types supported
                                qtyXS, qtyS, qtyM, qtyL, qtyXL, qtyXXL, qty3XL, qty4XL,
                                clientInstitution.trim(),
                                clientAddress.trim(),
                                clientNotes.trim(),
                                pic.trim()
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun YansDropdownSelector(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth().background(CardGrey)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(text = opt, color = TextLight) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailDialog(
    project: ProjectCustom,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by com.yansproject.app.data.FirebaseSyncManager.currentUser.collectAsState()
    val isOwner = currentUser?.role == com.yansproject.app.data.UserRole.OWNER

    var activeTab by remember { mutableStateOf("Workflow") } // "Workflow", "Klien & Items", "Timeline"

    // Form inputs initialized with current project properties
    var selectedStage by remember { mutableStateOf(project.currentStage) }
    var selectedDesignStatus by remember { mutableStateOf(project.designStatus) }
    var selectedOpenPoStatus by remember { mutableStateOf(project.openPoStatus) }
    var poTargetQtyStr by remember { mutableStateOf(project.poTargetQty.toString()) }
    var poReceivedQtyStr by remember { mutableStateOf(project.poReceivedQty.toString()) }
    var selectedProductionStatus by remember { mutableStateOf(project.productionStatus) }
    var selectedQcStatus by remember { mutableStateOf(project.qcStatus) }
    var selectedPaymentStatus by remember { mutableStateOf(project.paymentStatus) }
    var selectedShippingStatus by remember { mutableStateOf(project.shippingStatus) }
    var shippingReceiptNumber by remember { mutableStateOf(project.shippingReceiptNumber) }
    var shippingCarrier by remember { mutableStateOf(project.shippingCarrier) }
    
    // Change notes
    var changeNote by remember { mutableStateOf("") }

    PremiumBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRJ-${String.format("%03d", project.id)} | INV: ${project.invoiceNumber.ifEmpty { "Belum Dibuat" }}",
                        fontSize = 11.sp,
                        color = AgedGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = project.projectName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Tutup", tint = TextMuted)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // TABS Navigation
                Row(
                    modifier = Modifier.fillMaxWidth().background(ShadowBlack, RoundedCornerShape(8.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Workflow", "Klien & Items", "Timeline").forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) DarkTeal else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) AgedGold else TextMuted
                            )
                        }
                    }
                }

                Divider(color = BorderGrey, thickness = 1.dp)

                // TAB CONTENT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                ) {
                    when (activeTab) {
                        "Workflow" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Progress Indicator
                                val calculatedProgress = project.calculatedProgress
                                Column(modifier = Modifier.fillMaxWidth().background(ShadowBlack, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Progress Alur Kerja", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                        Text("${(calculatedProgress * 100).toInt()}% Selesai", fontSize = 11.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { calculatedProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = HighlightSoftCyan,
                                        trackColor = BorderGrey
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tahap Saat Ini: $selectedStage", fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                }

                                // Quick Next Stage Action
                                val stagesList = listOf(
                                    "Customer Datang", "Project Dibuat", "Invoice", "DP Awal", "Desain", 
                                    "ACC Desain", "Open PO", "DP Produksi", "Produksi", "QC", 
                                    "Pelunasan", "Packing", "Pengiriman", "Project Closed"
                                )
                                val currentIndex = stagesList.indexOf(selectedStage)
                                if (currentIndex < stagesList.size - 1) {
                                    val nextStageName = stagesList[currentIndex + 1]
                                    Button(
                                        onClick = {
                                            selectedStage = nextStageName
                                            changeNote = "Maju otomatis ke tahap: $nextStageName"
                                            // Auto-update related status drops to align
                                            if (nextStageName == "Desain") selectedDesignStatus = "Draft"
                                            if (nextStageName == "ACC Desain") selectedDesignStatus = "ACC"
                                            if (nextStageName == "Open PO") selectedOpenPoStatus = "Open PO"
                                            if (nextStageName == "Produksi") selectedProductionStatus = "Produksi"
                                            if (nextStageName == "QC") selectedQcStatus = "QC"
                                            if (nextStageName == "Packing") selectedShippingStatus = "Packing"
                                            if (nextStageName == "Pengiriman") selectedShippingStatus = "Dikirim"
                                            if (nextStageName == "Project Closed") selectedShippingStatus = "Selesai"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkTeal, contentColor = TextLight),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Langkah Selanjutnya: $nextStageName", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text("Perbarui Dimensi Status:", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)

                                // 1. Stage Utama Dropdown
                                YansDropdownSelector(
                                    label = "Stage Utama",
                                    selectedValue = selectedStage,
                                    options = stagesList,
                                    onSelect = { selectedStage = it }
                                )

                                // 2. Status Desain Dropdown
                                YansDropdownSelector(
                                    label = "Status Desain",
                                    selectedValue = selectedDesignStatus,
                                    options = listOf("Draft", "Revisi", "ACC"),
                                    onSelect = { selectedDesignStatus = it }
                                )

                                // 3. Status Open PO
                                YansDropdownSelector(
                                    label = "Status Open PO",
                                    selectedValue = selectedOpenPoStatus,
                                    options = listOf("Belum Dibuka", "Open PO", "Closed PO"),
                                    onSelect = { selectedOpenPoStatus = it }
                                )

                                if (selectedOpenPoStatus != "Belum Dibuka") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = poTargetQtyStr,
                                            onValueChange = { poTargetQtyStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("PO Target Qty", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                        )
                                        OutlinedTextField(
                                            value = poReceivedQtyStr,
                                            onValueChange = { poReceivedQtyStr = it.filter { char -> char.isDigit() } },
                                            label = { Text("PO Received Qty", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                        )
                                    }
                                }

                                // 4. Status Produksi
                                YansDropdownSelector(
                                    label = "Status Produksi",
                                    selectedValue = selectedProductionStatus,
                                    options = listOf("Menunggu Produksi", "Produksi", "Selesai Produksi"),
                                    onSelect = { selectedProductionStatus = it }
                                )

                                // 5. Status QC
                                YansDropdownSelector(
                                    label = "Status QC",
                                    selectedValue = selectedQcStatus,
                                    options = listOf("Belum QC", "QC", "Rework", "QC Lulus"),
                                    onSelect = { selectedQcStatus = it }
                                )

                                // 6. Status Pembayaran
                                YansDropdownSelector(
                                    label = "Status Keuangan & Pembayaran",
                                    selectedValue = selectedPaymentStatus,
                                    options = listOf("Belum Bayar", "DP Awal", "DP Produksi", "Lunas"),
                                    onSelect = { selectedPaymentStatus = it }
                                )

                                // 7. Status Pengiriman
                                YansDropdownSelector(
                                    label = "Status Pengiriman / Packing",
                                    selectedValue = selectedShippingStatus,
                                    options = listOf("Belum Packing", "Packing", "Siap Kirim", "Dikirim", "Selesai"),
                                    onSelect = { selectedShippingStatus = it }
                                )

                                if (selectedShippingStatus == "Dikirim" || selectedShippingStatus == "Selesai") {
                                    OutlinedTextField(
                                        value = shippingCarrier,
                                        onValueChange = { shippingCarrier = it },
                                        label = { Text("Ekspedisi / Kurir") },
                                        placeholder = { Text("Contoh: JNE, J&T, Sicepat") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                    )
                                    OutlinedTextField(
                                        value = shippingReceiptNumber,
                                        onValueChange = { shippingReceiptNumber = it },
                                        label = { Text("Nomor Resi Pengiriman") },
                                        placeholder = { Text("Masukkan nomor resi") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Divider(color = BorderGrey, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(4.dp))

                                // Custom Note input
                                OutlinedTextField(
                                    value = changeNote,
                                    onValueChange = { changeNote = it },
                                    label = { Text("Catatan Perubahan Alur Kerja (Opsional)") },
                                    placeholder = { Text("Contoh: Klien setuju dengan desain saku baru, bahan mulai dikirim") },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AgedGold, unfocusedBorderColor = BorderGrey)
                                )
                            }
                        }

                        "Klien & Items" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Client metadata card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Data Identitas Klien", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                        
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Nama Klien:", fontSize = 12.sp, color = TextMuted)
                                            Text(project.clientName, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("No. WhatsApp:", fontSize = 12.sp, color = TextMuted)
                                            if (project.clientPhone.isNotEmpty()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                                    val message = "Halo ${project.clientName}, kami ingin menginfokan bahwa proyek Anda '${project.projectName}' saat ini berada pada tahap: ${project.currentStage}."
                                                    var formatted = project.clientPhone.replace("+", "").replace(" ", "").replace("-", "")
                                                    if (formatted.startsWith("0")) {
                                                        formatted = "62" + formatted.substring(1)
                                                    }
                                                    val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${android.net.Uri.encode(message)}")
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(imageVector = Icons.Outlined.Chat, contentDescription = null, tint = HighlightSoftCyan, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(project.clientPhone, fontSize = 12.sp, color = HighlightSoftCyan, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Text("-", fontSize = 12.sp, color = TextLight)
                                            }
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Instansi / Perusahaan:", fontSize = 12.sp, color = TextMuted)
                                            Text(project.clientInstitution.ifEmpty { "-" }, fontSize = 12.sp, color = Color.White)
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("PIC Lapangan:", fontSize = 12.sp, color = TextMuted)
                                            Text(project.pic.ifEmpty { "Owner" }, fontSize = 12.sp, color = Color.White)
                                        }

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text("Alamat Pengiriman:", fontSize = 11.sp, color = TextMuted)
                                            Text(project.clientAddress.ifEmpty { "Tidak ditentukan" }, fontSize = 12.sp, color = TextLight)
                                        }

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text("Catatan Khusus Klien:", fontSize = 11.sp, color = TextMuted)
                                            Text(project.clientNotes.ifEmpty { "Tidak ada catatan khusus" }, fontSize = 12.sp, color = TextLight)
                                        }
                                    }
                                }

                                // Items list display
                                val pItems = com.yansproject.app.ui.ProjectItemParser.getProjectItems(project.description)
                                val displayDesc = com.yansproject.app.ui.ProjectItemParser.getProjectDescription(project.description)
                                
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ShadowBlack),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Spesifikasi & Rincian Pesanan", fontSize = 11.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                        
                                        if (displayDesc.isNotEmpty()) {
                                            Text(displayDesc, fontSize = 12.sp, color = TextLight)
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }

                                        if (pItems.isNotEmpty()) {
                                            pItems.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        "• ${item.productType} (${item.sleeveType}) - ${item.size}",
                                                        fontSize = 11.sp,
                                                        color = TextLight,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        "${item.qty} pcs @ ${FormatUtils.formatRupiah(item.price)}",
                                                        fontSize = 11.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Text("Tidak ada item pakaian terinci.", fontSize = 11.sp, color = TextMuted)
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Divider(color = BorderGrey, thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Total Nilai Project:", fontSize = 12.sp, color = TextMuted)
                                            Text(FormatUtils.formatRupiah(project.totalCost), fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Uang Muka / DP Dibayar:", fontSize = 12.sp, color = TextMuted)
                                            Text(FormatUtils.formatRupiah(project.paidAmount), fontSize = 12.sp, color = AlertGreen, fontWeight = FontWeight.Bold)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Sisa Sisa Tagihan:", fontSize = 12.sp, color = TextMuted)
                                            Text(
                                                FormatUtils.formatRupiah(project.remainingPayment), 
                                                fontSize = 12.sp, 
                                                color = if (project.remainingPayment > 0.0) AlertOrange else AlertGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Printing and sharing Actions row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            com.yansproject.app.ui.YansBluetoothPrinter.printProject(context, project)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CardGrey, contentColor = AgedGold),
                                        modifier = Modifier.weight(1f).border(1.dp, AgedGold, RoundedCornerShape(8.dp)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Cetak SPK", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val summaryText = "🚀 *INVOICE PROJECT CUSTOM - YANSPROJECT.ID*\n\n" +
                                                    "No. Invoice: ${project.invoiceNumber.ifEmpty { "INV-${project.id}" }}\n" +
                                                    "Project: ${project.projectName}\n" +
                                                    "Klien: ${project.clientName}\n" +
                                                    "Tahap Proyek: ${project.currentStage}\n" +
                                                    "Total Tagihan: ${FormatUtils.formatRupiah(project.totalCost)}\n" +
                                                    "Sudah Dibayar: ${FormatUtils.formatRupiah(project.paidAmount)}\n" +
                                                    "Sisa Pembayaran: ${FormatUtils.formatRupiah(project.remainingPayment)}\n\n" +
                                                    "Terima kasih atas kepercayaan Anda memesan produk custom di YANSPROJECT.ID!"
                                            var formatted = project.clientPhone.replace("+", "").replace(" ", "").replace("-", "")
                                            if (formatted.startsWith("0")) {
                                                formatted = "62" + formatted.substring(1)
                                            }
                                            val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$formatted&text=${android.net.Uri.encode(summaryText)}")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HighlightSoftCyan, contentColor = ShadowBlack),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kirim WA", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        "Timeline" -> {
                            val converters = com.yansproject.app.data.AppTypeConverters()
                            val timelineList = converters.toTimelineEntryList(project.timelineJson)

                            if (timelineList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Belum ada riwayat aktivitas log timeline.", fontSize = 12.sp, color = TextMuted)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(timelineList.reversed()) { entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ShadowBlack, RoundedCornerShape(8.dp))
                                                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .align(Alignment.Top)
                                                    .padding(top = 4.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(HighlightSoftCyan)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(entry.statusText, fontSize = 12.sp, color = AgedGold, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        FormatUtils.formatDate(entry.timestamp) + " " +
                                                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp)),
                                                        fontSize = 9.sp,
                                                        color = TextMuted
                                                    )
                                                }
                                                if (entry.note.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(entry.note, fontSize = 11.sp, color = TextLight)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                    Text("Tutup")
                }
                if (isOwner && activeTab == "Workflow") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val poTarget = poTargetQtyStr.toIntOrNull() ?: 0
                            val poReceived = poReceivedQtyStr.toIntOrNull() ?: 0
                            
                            // Create combined timeline entry if there is a note or change in status
                            val noteText = changeNote.trim().ifEmpty { "Update status project manual." }
                            
                            val updated = project.copy(
                                currentStage = selectedStage,
                                designStatus = selectedDesignStatus,
                                openPoStatus = selectedOpenPoStatus,
                                poTargetQty = poTarget,
                                poReceivedQty = poReceived,
                                productionStatus = selectedProductionStatus,
                                qcStatus = selectedQcStatus,
                                paymentStatus = selectedPaymentStatus,
                                shippingStatus = selectedShippingStatus,
                                shippingReceiptNumber = shippingReceiptNumber.trim(),
                                shippingCarrier = shippingCarrier.trim(),
                                status = if (selectedStage == "Project Closed" || selectedShippingStatus == "Selesai") "Completed" else project.status
                            ).withTimelineEntry(selectedStage, noteText)

                            viewModel.updateProject(updated)
                            Toast.makeText(context, "Status Alur Kerja berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgedGold, contentColor = ShadowBlack)
                    ) {
                        Text("Simpan Status", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

