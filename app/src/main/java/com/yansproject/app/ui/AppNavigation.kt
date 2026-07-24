package com.yansproject.app.ui

import androidx.navigation.NavHostController
import com.yansproject.app.ui.navigation.Routes

/**
 * Screen: Unified Material Design Navigation Compatibility Bridge.
 * Bridges legacy screen references to the new central [Routes] registry.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen(Routes.Dashboard)
    object Project : Screen(Routes.Project)
    object Stock : Screen(Routes.Stock)
    object Invoice : Screen(Routes.Invoice)
    object Riwayat : Screen(Routes.History)
    object Kitab : Screen(Routes.KitabDigital)
    object KitabDigital : Screen(Routes.KitabDigital)
    object AddInvoice : Screen(Routes.AddInvoice)
    object AddProject : Screen(Routes.AddProject)
    object AddStock : Screen(Routes.AddStock)
    object CustomProjectMain : Screen(Routes.CustomProjectMain)
    object CustomProjectCreate : Screen(Routes.CustomProjectCreate)
    object CustomProjectDetail : Screen(Routes.CustomProjectDetail)
    object InstantCheckout : Screen(Routes.InstantCheckout)
    object AjibReturn : Screen(Routes.AjibReturn)
}

/**
 * Safe navigation extension utility to prevent crashes (launchSingleTop, restoreState, try-catch)
 */
fun NavHostController.safeNavigate(route: String) {
    try {
        navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
