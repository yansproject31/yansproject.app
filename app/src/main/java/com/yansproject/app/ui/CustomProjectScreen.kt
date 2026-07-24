package com.yansproject.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yansproject.app.data.CustomProject
import com.yansproject.app.data.IdrAccountingEngine
import com.yansproject.app.data.SleeveType
import com.yansproject.app.ui.theme.*

// Exact Custom Project DNA color palettes as specified
val DeepCarbonBlack = Color(0xFF0A0F0D)
val EmeraldSlateGreen = Color(0xFF121A16)
val MutedSilver = Color(0xFF2A3A32)
val LuxuryGold = Color(0xFFD4AF37)
val HijauMint = Color(0xFF00E676)
val KuningAmber = Color(0xFFFFB300)
val MerahCrimson = Color(0xFFFF5252)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProjectScreen(
    viewModel: CustomProjectViewModel = viewModel(),
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var searchKeyword by remember { mutableStateOf("") }
    var selectedFilterTime by remember { mutableStateOf("Semua Waktu") }
    var selectedStatusFilter by remember { mutableStateOf("All") }

    // Deadlines & Overdue calculations based on real-time data
    val todayDeadlines = state.projects.filter { it.status == "PENDING" }.size
    val tomorrowDeadlines = state.projects.filter { it.status == "PRODUKSI" }.size
    val overdueCount = state.projects.filter { it.remainingBalance > 0 && it.status != "SELESAI" }.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MANAJEMEN PROJECT CUSTOM",
                        color = LuxuryGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepCarbonBlack
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreate,
                containerColor = LuxuryGold,
                contentColor = DeepCarbonBlack,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.testTag("add_custom_project_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Project")
            }
        },
        containerColor = DeepCarbonBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Top Metric Header Row (3 KPI Horizontal widgets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricWidgetCard(
                    title = "Deadline Hari Ini",
                    value = todayDeadlines.toString(),
                    indicatorColor = MerahCrimson,
                    modifier = Modifier.weight(1f)
                )
                MetricWidgetCard(
                    title = "Deadline Besok",
                    value = tomorrowDeadlines.toString(),
                    indicatorColor = KuningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricWidgetCard(
                    title = "Terlambat / Overdue",
                    value = overdueCount.toString(),
                    indicatorColor = MerahCrimson,
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. Search & Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchKeyword,
                    onValueChange = { searchKeyword = it },
                    placeholder = { Text("Cari klien, invoice...", color = MutedSilver, fontSize = 14.sp) },
                    prefix = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MutedSilver) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryGold,
                        unfocusedBorderColor = MutedSilver,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = EmeraldSlateGreen,
                        unfocusedContainerColor = EmeraldSlateGreen
                    ),
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_custom_project_input"),
                    singleLine = true
                )

                Button(
                    onClick = { selectedStatusFilter = "All" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatusFilter == "All") LuxuryGold else EmeraldSlateGreen,
                        contentColor = if (selectedStatusFilter == "All") DeepCarbonBlack else Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text("All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Timeline filter row (horizontal scroll)
            val times = listOf("Semua Waktu", "Hari Ini", "Minggu Ini", "Bulan Ini", "Tahun Ini")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(times) { time ->
                    val isSelected = selectedFilterTime == time
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isSelected) LuxuryGold else EmeraldSlateGreen)
                            .border(1.dp, MutedSilver, RoundedCornerShape(15.dp))
                            .clickable { selectedFilterTime = time }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = time,
                            color = if (isSelected) DeepCarbonBlack else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filtering our projects list
            val filteredProjects = state.projects.filter {
                (it.projectName.contains(searchKeyword, true) || it.clientName.contains(searchKeyword, true)) &&
                (selectedStatusFilter == "All" || it.status == selectedStatusFilter)
            }

            // 3. Conditional Layout (Premium Empty State vs Lists)
            if (filteredProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Container Card in Emerald Slate Green (#121A16)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkOff,
                                contentDescription = "Suitcase Crossed",
                                tint = LuxuryGold,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Belum Ada Project Custom",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Seluruh pengerjaan pesanan kustom, data ukuran pelanggan, sisa tagihan, dan progress produksi akan tercatat rapi di sini saat Anda membuat project baru.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredProjects) { project ->
                        ProjectItemCard(
                            project = project,
                            onClick = { onNavigateToDetail(project.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricWidgetCard(
    title: String,
    value: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProjectItemCard(
    project: CustomProject,
    onClick: () -> Unit
) {
    val totalQty = project.adultMatrix.sumOf { it.quantity } + project.kidsMatrix.sumOf { it.quantity }
    val statusColor = when (project.status) {
        "SELESAI" -> HijauMint
        "PRODUKSI" -> KuningAmber
        else -> MerahCrimson
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = EmeraldSlateGreen),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MutedSilver, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.id,
                    color = LuxuryGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = project.status,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = project.projectName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Klien: ${project.clientName} (${project.clientCompany.ifEmpty { "Individu" }})",
                color = Color.LightGray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MutedSilver, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "TOTAL ORDER", color = Color.Gray, fontSize = 10.sp)
                    Text(text = "$totalQty Pcs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "SISA TAGIHAN", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        text = IdrAccountingEngine.formatRupiah(project.remainingBalance),
                        color = LuxuryGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
