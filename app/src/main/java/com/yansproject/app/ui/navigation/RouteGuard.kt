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
    object Checking : RouteAccessResult()
    object VerifiedSuperAdmin : RouteAccessResult()
    data class OfflineSessionValid(val role: UserRole) : RouteAccessResult()
    object AuthRequired : RouteAccessResult()
    data class Denied(val reason: String) : RouteAccessResult()
    data class AuthCheckFailed(val reason: String) : RouteAccessResult()

    val isGranted: Boolean
        get() = this is VerifiedSuperAdmin || this is OfflineSessionValid
}

/**
 * RouteGuard Utility for YANSPROJECT.ID ERP
 * Validates user permissions against UserRole and Firebase Auth Custom Claims.
 * Protects financial dashboard metrics, ledgers, and sensitive settings.
 * Business Policy: OWNER + ADMIN = ONE SUPER_ADMIN.
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

    // Session-scoped Verified Claims Cache
    private var cachedUid: String? = null
    private var cachedToken: String? = null
    private var cachedResult: RouteAccessResult? = null
    private var lastClaimCheckTime: Long = 0L
    private const val CACHE_TTL_MS = 300_000L // 5 minutes cache

    fun invalidateClaimCache() {
        synchronized(this) {
            cachedUid = null
            cachedToken = null
            cachedResult = null
            lastClaimCheckTime = 0L
        }
    }

    fun isFinancialRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        val baseRoute = route.split("?", "{").firstOrNull()?.trim() ?: route.trim()
        return FINANCIAL_SENSITIVE_ROUTES.contains(baseRoute)
    }

    fun isInvoiceRoute(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        val baseRoute = route.split("?", "{").firstOrNull()?.trim() ?: route.trim()
        return INVOICE_MANAGEMENT_ROUTES.contains(baseRoute)
    }

    /**
     * Checks if local role is authorized (OWNER or ADMIN treated as SUPER_ADMIN).
     */
    fun isUserAuthorizedForFinancials(role: UserRole?): Boolean {
        if (role == null) return false
        return role == UserRole.OWNER || role == UserRole.ADMIN || role.canAccessFinancials()
    }

    fun isUserAuthorizedForInvoices(role: UserRole?): Boolean {
        if (role == null) return false
        return role == UserRole.OWNER || role == UserRole.ADMIN || role.canManageInvoices()
    }

    /**
     * Async verification of Firebase Auth custom claims & local session role for Invoices
     */
    suspend fun verifyInvoiceAccessWithCustomClaims(fallbackRole: UserRole?, forceRefresh: Boolean = false): RouteAccessResult {
        return verifyAccessInternal(fallbackRole, isInvoiceModule = true, forceRefresh = forceRefresh)
    }

    /**
     * Async verification of Firebase Auth custom claims & local session role for Financials
     */
    suspend fun verifyFinancialAccessWithCustomClaims(fallbackRole: UserRole?, forceRefresh: Boolean = false): RouteAccessResult {
        return verifyAccessInternal(fallbackRole, isInvoiceModule = false, forceRefresh = forceRefresh)
    }

    private suspend fun verifyAccessInternal(
        fallbackRole: UserRole?,
        isInvoiceModule: Boolean,
        forceRefresh: Boolean
    ): RouteAccessResult {
        // 1. Validate local session role authorization
        val isLocallySuperAdmin = if (isInvoiceModule) isUserAuthorizedForInvoices(fallbackRole) else isUserAuthorizedForFinancials(fallbackRole)
        if (!isLocallySuperAdmin) {
            return RouteAccessResult.Denied("Peran Pengguna (${fallbackRole?.name ?: "MEMBER"}) tidak memiliki otorisasi SUPER_ADMIN untuk modul ini.")
        }

        val firebaseUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }

        if (firebaseUser == null) {
            // Unauthenticated
            if (fallbackRole == UserRole.OWNER || fallbackRole == UserRole.ADMIN) {
                return RouteAccessResult.OfflineSessionValid(fallbackRole)
            }
            return RouteAccessResult.AuthRequired
        }

        val now = System.currentTimeMillis()
        synchronized(this) {
            val result = cachedResult
            if (!forceRefresh &&
                cachedUid == firebaseUser.uid &&
                result != null &&
                (now - lastClaimCheckTime) < CACHE_TTL_MS
            ) {
                return result
            }
        }

        // 2. Fetch remote claims
        return try {
            val idTokenResult = firebaseUser.getIdToken(forceRefresh).await()
            val tokenString = idTokenResult.token
            val claims = idTokenResult.claims
            val claimRole = (claims["role"] as? String)?.uppercase()
            val isOwnerClaim = (claims["isOwner"] as? Boolean) ?: (claims["owner"] as? Boolean) ?: false
            val isAdminClaim = (claims["isAdmin"] as? Boolean) ?: (claims["admin"] as? Boolean) ?: false

            val isSuperAdminByClaim = isOwnerClaim || isAdminClaim || claimRole == "OWNER" || claimRole == "ADMIN" || claimRole == "SUPER_ADMIN"

            val result = if (isSuperAdminByClaim) {
                RouteAccessResult.VerifiedSuperAdmin
            } else if (claimRole != null && claimRole != "OWNER" && claimRole != "ADMIN" && claimRole != "SUPER_ADMIN") {
                RouteAccessResult.Denied("Custom Claim Firebase Auth ('$claimRole') membatasi akses SUPER_ADMIN.")
            } else if (fallbackRole == UserRole.OWNER || fallbackRole == UserRole.ADMIN) {
                RouteAccessResult.VerifiedSuperAdmin
            } else {
                RouteAccessResult.Denied("Otorisasi tidak terpenuhi.")
            }

            synchronized(this) {
                cachedUid = firebaseUser.uid
                cachedToken = tokenString
                cachedResult = result
                lastClaimCheckTime = now
            }

            result
        } catch (e: Exception) {
            // Network error / Offline claim verification failure:
            // Permit ONLY previously verified offline session behavior without elevating authority beyond verified local role
            if (fallbackRole == UserRole.OWNER || fallbackRole == UserRole.ADMIN) {
                val result = RouteAccessResult.OfflineSessionValid(fallbackRole)
                synchronized(this) {
                    cachedUid = firebaseUser.uid
                    cachedResult = result
                    lastClaimCheckTime = now
                }
                result
            } else {
                RouteAccessResult.AuthCheckFailed("Gagal memverifikasi klaim otorisasi: ${e.localizedMessage}")
            }
        }
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
        mutableStateOf<RouteAccessResult>(RouteAccessResult.Checking)
    }

    LaunchedEffect(userRole) {
        accessState = RouteGuard.verifyInvoiceAccessWithCustomClaims(userRole)
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
        is RouteAccessResult.VerifiedSuperAdmin, is RouteAccessResult.OfflineSessionValid -> {
            content()
        }
        is RouteAccessResult.AuthRequired -> {
            AccessDeniedScreen(
                title = "OTENTIKASI DIPERLUKAN",
                reason = "Sesi Anda telah berakhir atau belum terotentikasi. Silakan login kembali.",
                onNavigateBack = onNavigateBack,
                onNavigateToHistory = onNavigateToHistory
            )
        }
        is RouteAccessResult.AuthCheckFailed -> {
            AccessDeniedScreen(
                title = "GAGAL VERIFIKASI OTORISASI",
                reason = state.reason,
                onNavigateBack = onNavigateBack,
                onNavigateToHistory = onNavigateToHistory
            )
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
        mutableStateOf<RouteAccessResult>(RouteAccessResult.Checking)
    }

    LaunchedEffect(userRole) {
        accessState = RouteGuard.verifyFinancialAccessWithCustomClaims(userRole)
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
        is RouteAccessResult.VerifiedSuperAdmin, is RouteAccessResult.OfflineSessionValid -> {
            content()
        }
        is RouteAccessResult.AuthRequired -> {
            AccessDeniedScreen(
                title = "OTENTIKASI DIPERLUKAN",
                reason = "Sesi Anda telah berakhir atau belum terotentikasi. Silakan login kembali.",
                onNavigateBack = onNavigateBack
            )
        }
        is RouteAccessResult.AuthCheckFailed -> {
            AccessDeniedScreen(
                title = "GAGAL VERIFIKASI OTORISASI",
                reason = state.reason,
                onNavigateBack = onNavigateBack
            )
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
