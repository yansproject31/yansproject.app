package com.yansproject.app.ui.navigation

/**
 * Centered route registry for YANSPROJECT.ID ERP.
 * Zero hardcoded strings inside individual composables.
 */
object Routes {
    const val Startup = "startup"
    const val Dashboard = "dashboard"
    const val Project = "project"
    const val Stock = "stock"
    const val Invoice = "invoice"
    const val History = "riwayat"
    const val KitabDigital = "kitab_digital"
    const val LuxuryCart = "luxury_cart"
    
    // Core ERP forms / updates
    const val AddInvoice = "invoice_create_route"
    const val AddProject = "project_create_route"
    const val AddStock = "stock_update_route"
    const val CustomProjectMain = "custom_project_main"
    const val CustomProjectCreate = "project_create_route"
    const val CustomProjectDetail = "custom_project_detail/{projectId}"
    const val InstantCheckout = "instant_checkout"
    const val AjibReturn = "ajib_return_adjustment"
    
    // Configuration Sub-Modules
    const val Settings = "settings"
    const val SettingsMain = "settings_main"
    const val SettingsIdentitas = "settings_identitas"
    const val SettingsKeuangan = "settings_keuangan"
    const val SettingsDokumen = "settings_dokumen"
    const val SettingsMember = "settings_member"
    const val SettingsBackup = "settings_backup"
    
    // New Consolidated 17 Categories Routes
    const val SettingsAccount = "settings_account"
    const val SettingsOwnerCenter = "settings_owner_center"
    const val SettingsMemberCenter = "settings_member_center"
    const val SettingsRoleManagement = "settings_role_management"
    const val SettingsSecurity = "settings_security"
    const val SettingsBiometric = "settings_biometric"
    const val SettingsErpConfig = "settings_erp_config"
    const val SettingsNotifications = "settings_notifications"
    const val SettingsDbSync = "settings_db_sync"
    const val SettingsStorage = "settings_storage"
    const val SettingsAppearance = "settings_appearance"
    const val SettingsAppInfo = "settings_app_info"
    const val SettingsMaintenance = "settings_maintenance"
    const val SettingsDevDiag = "settings_dev_diag"
    
    // Administration / System Health
    const val AdminProfile = "admin_profile"
    const val AppSettings = "app_settings"
    const val AppInfo = "app_info"
    const val SystemHealth = "system_health"
    const val Telemetry = "telemetry"
    const val SecurityLog = "security_log"
    
    // Financial Ledger Routes
    const val GlobalLedger = "global_ledger_route"
    const val IncomeLedger = "income_ledger_route"
    const val ExpenseLedger = "expense_ledger_route"
}
