package com.yansproject.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.yansproject.app.ui.*
import com.yansproject.app.ui.navigation.Routes
import com.yansproject.app.ui.invoice.DualInvoiceEditorScreen as ActionHubAndPdfModule
import com.yansproject.app.ui.inventory.MatrixScreen as OmniverseMatrixModule

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yansproject.app.data.FirebaseSyncManager

@Composable
fun YansNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { navController.popBackStack() }
) {
    val currentUser by FirebaseSyncManager.currentUser.collectAsStateWithLifecycle()
    val userRole = currentUser?.role

    NavHost(
        navController = navController,
        startDestination = Routes.Startup,
        modifier = modifier
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
            RiwayatScreen(viewModel = viewModel)
        }
        composable(Routes.KitabDigital) {
            KitabDigitalScreen(viewModel = viewModel, onBack = {
                if (!navController.popBackStack()) {
                    viewModel.setTab(AppTab.DASHBOARD)
                }
            })
        }
        
        navigation(startDestination = Routes.SettingsMain, route = Routes.Settings) {
            composable(Routes.SettingsMain) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = null)
            }
            composable(Routes.SettingsIdentitas) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "identitas")
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
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "dokumen")
            }
            composable(Routes.SettingsMember) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "member")
            }
            composable(Routes.SettingsBackup) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "backup")
            }
            composable(Routes.SettingsAccount) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "akun")
            }
            composable(Routes.SettingsOwnerCenter) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "owner_center")
            }
            composable(Routes.SettingsMemberCenter) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "member_center")
            }
            composable(Routes.SettingsRoleManagement) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "role_management")
            }
            composable(Routes.SettingsSecurity) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "security")
            }
            composable(Routes.SettingsBiometric) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "biometric")
            }
            composable(Routes.SettingsErpConfig) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "erp_config")
            }
            composable(Routes.SettingsNotifications) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "notifications")
            }
            composable(Routes.SettingsDbSync) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "db_sync")
            }
            composable(Routes.SettingsStorage) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "storage")
            }
            composable(Routes.SettingsAppearance) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "appearance")
            }
            composable(Routes.SettingsAppInfo) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "info")
            }
            composable(Routes.SettingsMaintenance) {
                SettingsScreen(viewModel = viewModel, navController = navController, subScreen = "maintenance")
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
                RiwayatTransaksiScreen(
                    viewModel = viewModel,
                    type = "ALL",
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
