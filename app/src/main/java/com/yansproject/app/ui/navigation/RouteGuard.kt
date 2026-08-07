package com.yansproject.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.yansproject.app.data.FirebaseSyncManager
import com.yansproject.app.data.UserRole
import com.yansproject.app.ui.theme.*
import kotlinx.coroutines.tasks.await

sealed class RouteAccessResult {
    object Granted : RouteAccessResult()
    data class Denied(val reason: String) : RouteAccessResult()
    object Checking : RouteAccessResult()
}

/**
 * RouteGuard Utility for YANSPROJECT.ID ERP
 * Validates user permissions against UserRole and Firebase Auth Custom Claims.
 * Protects financial dashboard metrics, ledgers, and sensitive settings.
 */
object RouteGuard {

    private val FINANCIAL_SENSITIVE_ROUTES = setOf(
        Routes.SettingsKeuangan,
        Routes.GlobalLedger,
        Routes.IncomeLedger,
        Routes.ExpenseLedger,
        Routes.SettingsOwnerCenter,
        Routes.SettingsRoleManagement,
        Routes.SettingsBackup
    )

    private val INVOICE_MANAGEMENT_ROUTES = setOf(
        Routes.Invoice,
        Routes.AddInvoice
    )

    fun isFinancialRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        val baseRoute = route.split("?", "{")[0].trim()
        return FINANCIAL_SENSITIVE_ROUTES.contains(baseRoute)
    }

    fun isInvoiceRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        val baseRoute = route.split("?", "{")[0].trim()
        return INVOICE_MANAGEMENT_ROUTES.contains(baseRoute)
    }

    fun isUserAuthorizedForFinancials(role: UserRole?): Boolean {
        if (role == null) return false
        return role.canAccessFinancials() || role == UserRole.OWNER || role == UserRole.ADMIN
    }

    fun isUserAuthorizedForInvoices(role: UserRole?): Boolean {
        if (role == null) return false
        return role.canManageInvoices()
    }

    /**
     * Async verification of Firebase Auth custom claims & local session role for Invoices
     */
    suspend fun verifyInvoiceAccessWithCustomClaims(fallbackRole: UserRole?): RouteAccessResult {
        if (!isUserAuthorizedForInvoices(fallbackRole)) {
            return RouteAccessResult.Denied("Peran Pengguna (${fallbackRole?.name ?: "MEMBER"}) tidak memiliki izin untuk mengelola atau mengakses Manajemen Invoice ERP YANSPROJECT.ID.")
        }

        val firebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

        if (firebaseUser != null) {
            try {
                val idTokenResult = firebaseUser.getIdToken(false).await()
                val claims = idTokenResult.claims
                val claimRole = claims["role"] as? String
                val isOwnerClaim = (claims["isOwner"] as? Boolean) ?: (claims["owner"] as? Boolean) ?: false
                val isAdminClaim = (claims["isAdmin"] as? Boolean) ?: (claims["admin"] as? Boolean) ?: false

                if (isOwnerClaim || isAdminClaim || claimRole.equals("OWNER", ignoreCase = true) || claimRole.equals("ADMIN", ignoreCase = true)) {
                    return RouteAccessResult.Granted
                } else if (claimRole != null && !claimRole.equals("OWNER", ignoreCase = true) && !claimRole.equals("ADMIN", ignoreCase = true)) {
                    return RouteAccessResult.Denied("Custom Claim Firebase Auth ('$claimRole') membatasi akses Manajemen Invoice ERP.")
                }
            } catch (e: Exception) {
                if (fallbackRole == UserRole.OWNER || fallbackRole == UserRole.ADMIN) {
                    return RouteAccessResult.Granted
                }
            }
        }

        return if (isUserAuthorizedForInvoices(fallbackRole)) RouteAccessResult.Granted
        else RouteAccessResult.Denied("Akses ditolak oleh kebijakan otorisasi YANSPROJECT.ID.")
    }

    /**
     * Async verification of Firebase Auth custom claims & local session role for Financials
     */
    suspend fun verifyFinancialAccessWithCustomClaims(fallbackRole: UserRole?): RouteAccessResult {
        // 1. Check local session role first
        if (!isUserAuthorizedForFinancials(fallbackRole)) {
            return RouteAccessResult.Denied("Peran Pengguna (${fallbackRole?.name ?: "MEMBER"}) tidak memiliki izin akses data keuangan.")
        }

        // 2. Inspect Firebase Auth Custom Claims if active
        val firebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

        if (firebaseUser != null) {
            try {
                val idTokenResult = firebaseUser.getIdToken(false).await()
                val claims = idTokenResult.claims
                val claimRole = claims["role"] as? String
                val isOwnerClaim = (claims["isOwner"] as? Boolean) ?: (claims["owner"] as? Boolean) ?: false
                val isAdminClaim = (claims["isAdmin"] as? Boolean) ?: (claims["admin"] as? Boolean) ?: false

                if (isOwnerClaim || isAdminClaim || claimRole.equals("OWNER", ignoreCase = true) || claimRole.equals("ADMIN", ignoreCase = true)) {
                    return RouteAccessResult.Granted
                } else if (claimRole != null && !claimRole.equals("OWNER", ignoreCase = true) && !claimRole.equals("ADMIN", ignoreCase = true)) {
                    return RouteAccessResult.Denied("Custom Claim Firebase Auth ('$claimRole') membatasi akses keuangan ERP.")
                }
            } catch (e: Exception) {
                // Network error or offline mode: Fallback safely to local session role
                if (fallbackRole == UserRole.OWNER || fallbackRole == UserRole.ADMIN) {
                    return RouteAccessResult.Granted
                }
            }
        }

        return if (isUserAuthorizedForFinancials(fallbackRole)) RouteAccessResult.Granted
        else RouteAccessResult.Denied("Akses ditolak oleh kebijakan keamanan YANSPROJECT.ID.")
    }
}

/**
 * GuardedInvoiceRoute Component
 * Wraps Invoice management screens to enforce Owner/Admin authorization.
 */
@Composable
fun GuardedInvoiceRoute(
    userRole: UserRole?,
    onNavigateBack: () -> Unit = {},
    onNavigateToHistory: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var accessState by remember(userRole) {
        mutableStateOf<RouteAccessResult>(
            if (RouteGuard.isUserAuthorizedForInvoices(userRole)) RouteAccessResult.Granted
            else RouteAccessResult.Checking
        )
    }

    LaunchedEffect(userRole) {
        if (!RouteGuard.isUserAuthorizedForInvoices(userRole)) {
            accessState = RouteGuard.verifyInvoiceAccessWithCustomClaims(userRole)
        }
    }

    when (val state = accessState) {
        is RouteAccessResult.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundShadowBlack),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AgedGold)
            }
        }
        is RouteAccessResult.Granted -> {
            content()
        }
        is RouteAccessResult.Denied -> {
            AccessDeniedScreen(
                title = "AKSES MANAJEMEN INVOICE DIBATASI",
                reason = state.reason,
                onNavigateBack = onNavigateBack,
                onNavigateToHistory = onNavigateToHistory
            )
        }
    }
}

/**
 * GuardedFinancialRoute Component
 * Wraps sensitive screens or financial widgets to ensure only OWNER/ADMIN accounts can access.
 */
@Composable
fun GuardedFinancialRoute(
    userRole: UserRole?,
    onNavigateBack: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var accessState by remember(userRole) {
        mutableStateOf<RouteAccessResult>(
            if (RouteGuard.isUserAuthorizedForFinancials(userRole)) RouteAccessResult.Granted
            else RouteAccessResult.Checking
        )
    }

    LaunchedEffect(userRole) {
        if (!RouteGuard.isUserAuthorizedForFinancials(userRole)) {
            accessState = RouteGuard.verifyFinancialAccessWithCustomClaims(userRole)
        }
    }

    when (val state = accessState) {
        is RouteAccessResult.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundShadowBlack),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AgedGold)
            }
        }
        is RouteAccessResult.Granted -> {
            content()
        }
        is RouteAccessResult.Denied -> {
            AccessDeniedScreen(
                title = "AKSES KEUANGAN DIBATASI",
                reason = state.reason,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
fun AccessDeniedScreen(
    title: String = "AKSES KEUANGAN DIBATASI",
    reason: String,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundShadowBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, AlertRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
            color = CardDarkCard,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AlertRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Akses Dibatasi",
                        tint = AlertRed,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = AlertRed,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = reason,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDarkTeal)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = AgedGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Modul ini dilindungi oleh Route Guard & Otorisasi Peran YANSPROJECT.ID.",
                            fontSize = 11.sp,
                            color = AgedGold,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onNavigateToHistory != null) {
                        Button(
                            onClick = onNavigateToHistory,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryDarkTeal,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Buka Riwayat Transaksi Saya", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryDarkTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kembali ke Dashboard Utama", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
