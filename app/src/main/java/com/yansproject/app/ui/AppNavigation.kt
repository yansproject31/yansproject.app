package com.yansproject.app.ui

import android.util.Log
import androidx.navigation.NavHostController
import com.yansproject.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
 * Navigation error event hub for global diagnostics
 */
object NavigationDiagnostics {
    private const val TAG = "NavigationDiagnostics"
    private val _navigationErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val navigationErrorEvents: SharedFlow<String> = _navigationErrorEvents.asSharedFlow()

    fun reportNavigationFailure(route: String, reason: String, cause: Throwable? = null) {
        val message = "Navigasi Gagal ke '$route': $reason"
        Log.e(TAG, message, cause)
        _navigationErrorEvents.tryEmit(message)
    }
}

/**
 * Safe navigation extension utility to prevent crashes with structured error reporting
 */
fun NavHostController.safeNavigate(
    route: String,
    onError: ((String) -> Unit)? = null
) {
    if (route.isBlank()) {
        val errMsg = "Rute target navigasi tidak boleh kosong"
        NavigationDiagnostics.reportNavigationFailure(route, errMsg)
        onError?.invoke(errMsg)
        return
    }

    try {
        navigate(route) {
            launchSingleTop = true
        }
    } catch (e: Exception) {
        Log.w("AppNavigation", "SingleTop navigation attempt to '$route' failed: ${e.message}. Retrying fallback navigation dispatch.")
        try {
            navigate(route)
        } catch (e2: Exception) {
            val errMsg = e2.localizedMessage ?: e2.message ?: "Unknown navigation exception"
            NavigationDiagnostics.reportNavigationFailure(route, errMsg, e2)
            onError?.invoke(errMsg)
        }
    }
}
