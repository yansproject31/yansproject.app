package com.yansproject.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.theme.AlertOrange
import com.yansproject.app.ui.theme.TextMuted
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.yansproject.app.ui.*
import com.yansproject.app.ui.analytics.AnalisisKeuanganGlobalScreen
import com.yansproject.app.ui.navigation.Routes
import com.yansproject.app.ui.invoice.DualInvoiceEditorScreen as ActionHubAndPdfModule
import com.yansproject.app.ui.inventory.MatrixScreen as OmniverseMatrixModule

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.FirebaseSyncManager

private val LuxuryMotionEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Composable
fun YansNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { navController.popBackStack() }
) {
    val currentUser by FirebaseSyncManager.currentUser.collectAsStateWithLifecycle()
    val userRole = currentUser?.role

    val context = androidx.compose.ui.platform.LocalContext.current
    val isAlreadyBootstrapped = androidx.compose.runtime.remember {
        com.yansproject.app.data.SyncMetadataManager.getInstance(context).getState() == com.yansproject.app.data.BootstrapState.FINISHED
    }
    val initialRoute = if (isAlreadyBootstrapped) Routes.Dashboard else Routes.Startup

    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(320, easing = LuxuryMotionEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = LuxuryMotionEasing)) +
            slideInHorizontally(animationSpec = tween(320, easing = LuxuryMotionEasing)) { it / 12 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(240, easing = LuxuryMotionEasing)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = LuxuryMotionEasing)) +
            slideOutHorizontally(animationSpec = tween(240, easing = LuxuryMotionEasing)) { -it / 12 }
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(320, easing = LuxuryMotionEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(320, easing = LuxuryMotionEasing)) +
            slideInHorizontally(animationSpec = tween(320, easing = LuxuryMotionEasing)) { -it / 12 }
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(240, easing = LuxuryMotionEasing)) +
            scaleOut(targetScale = 0.96f, animationSpec = tween(240, easing = LuxuryMotionEasing)) +
            slideOutHorizontally(animationSpec = tween(240, easing = LuxuryMotionEasing)) { it / 12 }
        }
    ) {
        composable(Routes.Startup) {
            StartupScreen(onFinished = {
                navController.navigate(Routes.Dashboard) {
                    popUpTo(Routes.Startup) { inclusive = true }
                }
            })
        }
        composable(Routes.Dashboard) {
            DashboardScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.Project) {
            ProjectScreen(viewModel = viewModel)
        }
        composable(Routes.Stock) {
            StockScreen(viewModel = viewModel)
        }
        composable(Routes.Invoice) {
            InvoiceScreen(viewModel = viewModel)
        }
        composable(Routes.History) {
            if (userRole == UserRole.OWNER || userRole == UserRole.ADMIN) {
                RiwayatScreen(viewModel = viewModel)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = AlertOrange,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Akses Terbatas",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Halaman Riwayat hanya dapat diakses oleh Owner / Admin.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        composable(Routes.KitabDigital) {
            KitabDigitalScreen(viewModel = viewModel, onBack = {
                viewModel.setTab(AppTab.DASHBOARD)
            })
        }
        
        navigation(startDestination = Routes.SettingsMain, route = Routes.Settings) {
            composable(Routes.SettingsMain) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = null)
            }
            composable(Routes.SettingsIdentitas) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "identitas")
                }
            }
            composable(Routes.SettingsKeuangan) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "keuangan")
                }
            }
            composable(Routes.SettingsDokumen) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "dokumen")
                }
            }
            composable(Routes.SettingsMember) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "member")
                }
            }
            composable(Routes.SettingsBackup) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "backup")
                }
            }
            composable(Routes.SettingsAccount) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "akun")
            }
            composable(Routes.SettingsOwnerCenter) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "owner_center")
                }
            }
            composable(Routes.SettingsMemberCenter) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "member_center")
            }
            composable(Routes.SettingsRoleManagement) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "role_management")
                }
            }
            composable(Routes.SettingsSecurity) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "security")
            }
            composable(Routes.SettingsBiometric) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "biometric")
            }
            composable(Routes.SettingsErpConfig) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "erp_config")
                }
            }
            composable(Routes.SettingsNotifications) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "notifications")
            }
            composable(Routes.SettingsDbSync) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "db_sync")
                }
            }
            composable(Routes.SettingsStorage) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "storage")
                }
            }
            composable(Routes.SettingsAppearance) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "appearance")
            }
            composable(Routes.SettingsAppInfo) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "info")
            }
            composable(Routes.SettingsMaintenance) {
                GuardedFinancialRoute(
                    userRole = userRole,
                    onNavigateBack = { navController.popBackStack() }
                ) {
                    SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "maintenance")
                }
            }
            composable(Routes.SettingsDevDiag) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "dev_diag")
            }
            composable(Routes.AdminProfile) {
                AdminProfileScreen(navController = navController, viewModel = viewModel)
            }
            composable(Routes.AppSettings) {
                AppSettingsScreen(navController = navController, viewModel = viewModel)
            }
            composable(Routes.AppInfo) {
                AppInfoScreen(navController = navController)
            }
            composable(Routes.SystemHealth) {
                SystemHealthScreen(navController = navController, viewModel = viewModel)
            }
            composable(Routes.Telemetry) {
                PerformanceTelemetryScreen(onBack = { navController.popBackStack() }, viewModel = viewModel)
            }
            composable(Routes.SecurityLog) {
                ActivityLogScreen(navController = navController, viewModel = viewModel)
            }
        }
        
        // Unified Action & Core ERP Forms
        composable(Routes.AddInvoice) {
            ActionHubAndPdfModule(isCustomProject = false, onNavigateBack = { navController.popBackStack() })
        }
        
        composable(Routes.AddProject) {
            CustomProjectFormScreen(onNavigateBack = { navController.popBackStack() })
        }
        
        composable(Routes.AddStock) {
            OmniverseMatrixModule()
        }
        
        composable(Routes.CustomProjectMain) {
            CustomProjectScreen(
                onNavigateToCreate = { navController.navigate(Routes.AddProject) },
                onNavigateToDetail = { id -> navController.navigate("custom_project_detail/$id") }
            )
        }
        
        composable(Routes.CustomProjectDetail) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("projectId") ?: ""
            ProfessionalInvoiceDetailScreen(
                projectId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.InstantCheckout) {
            InstantCheckoutScreen(
                onCheckoutSuccess = { navController.popBackStack() }
            )
        }
        
        composable(Routes.LuxuryCart) {
            com.yansproject.app.ui.member.LuxuryCartScreen(
                viewModel = viewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
        
        composable(Routes.AjibReturn) {
            AjibqobulReturnAdjustmentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Ledger Routes (Global, Income, Expense)
        composable(Routes.GlobalLedger) {
            GuardedFinancialRoute(
                userRole = userRole,
                onNavigateBack = { navController.popBackStack() }
            ) {
                AnalisisKeuanganGlobalScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.IncomeLedger) {
            GuardedFinancialRoute(
                userRole = userRole,
                onNavigateBack = { navController.popBackStack() }
            ) {
                RiwayatPemasukanScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.ExpenseLedger) {
            GuardedFinancialRoute(
                userRole = userRole,
                onNavigateBack = { navController.popBackStack() }
            ) {
                RiwayatPengeluaranScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
