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

    fun isFinancialRoute(route: String?): Boolean {
        if (route == null) return false
        return FINANCIAL_SENSITIVE_ROUTES.contains(route) || route.contains("ledger") || route.contains("keuangan")
    }

    fun isUserAuthorizedForFinancials(role: UserRole?): Boolean {
        if (role == null) return false
        return role.canAccessFinancials() || role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Async verification of Firebase Auth custom claims & local session role
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
 * GuardedFinancialRoute Component
 * Wraps sensitive screens or financial widgets to ensure only OWNER/ADMIN accounts can access.
 */
@Composable
fun GuardedFinancialRoute(
    userRole: UserRole?,
    onNavigateBack: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var accessState by remember(userRole) { mutableStateOf<RouteAccessResult>(RouteAccessResult.Checking) }

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
        is RouteAccessResult.Granted -> {
            content()
        }
        is RouteAccessResult.Denied -> {
            AccessDeniedScreen(
                reason = state.reason,
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
fun AccessDeniedScreen(
    reason: String,
    onNavigateBack: () -> Unit
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
                    text = "AKSES KEUANGAN DIBATASI",
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
                            text = "Modul ini dilindungi oleh Route Guard & Firebase Auth Custom Claims YANSPROJECT.ID.",
                            fontSize = 11.sp,
                            color = AgedGold,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryDarkTeal,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kembali ke Dashboard Utama", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
