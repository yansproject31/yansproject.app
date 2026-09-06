package com.yansproject.app.data

import com.yansproject.app.data.UserRole

object RoleAccessManager {

    /**
     * Enterprise Security Context representing caller identity, role, and custom claim attributes.
     */
    data class SecurityContext(
        val role: UserRole,
        val email: String = "",
        val claims: Map<String, Any> = emptyMap()
    )

    /**
     * Helper to verify whether a role or security context represents an owner or administrative authority.
     */
    fun isAdministrative(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    fun isAdministrative(context: SecurityContext): Boolean {
        return isAdministrative(context.role) || (context.claims["is_admin"] as? Boolean == true)
    }

    /**
     * Checks if the user is authorized to view global financial ledgers, cash mutations, and profit reports.
     */
    fun canAccessFinancials(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canAccessFinancials(context: SecurityContext): Boolean {
        return canAccessFinancials(context.role) || (context.claims["financial_access"] as? Boolean == true)
    }

    /**
     * Checks if the user is authorized to delete global cash mutation logs.
     */
    fun canDeleteCashMutation(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canDeleteCashMutation(context: SecurityContext): Boolean {
        return canDeleteCashMutation(context.role)
    }

    /**
     * Checks if the user can view the complete profit margin reports.
     */
    fun canViewProfitMargins(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canViewProfitMargins(context: SecurityContext): Boolean {
        return canViewProfitMargins(context.role)
    }

    /**
     * Checks if the user can access the Developer Portal (13-tap).
     */
    fun canAccessDevPortal(role: UserRole): Boolean {
        return role == UserRole.OWNER
    }

    fun canAccessDevPortal(context: SecurityContext): Boolean {
        return canAccessDevPortal(context.role)
    }

    /**
     * Checks if the user can view all member directory and detailed member data.
     */
    fun canViewMemberDirectory(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canViewMemberDirectory(context: SecurityContext): Boolean {
        return canViewMemberDirectory(context.role)
    }

    /**
     * Checks if the user can perform general stock production adjustments, batch creation, and price editing.
     */
    fun canManageStock(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canManageStock(context: SecurityContext): Boolean {
        return canManageStock(context.role)
    }

    /**
     * Checks if the user can create and update custom projects progress.
     */
    fun canManageProjects(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canManageProjects(context: SecurityContext): Boolean {
        return canManageProjects(context.role)
    }

    /**
     * Checks if the user can create and register arbitrary invoices for all clients.
     */
    fun canCreateInvoices(role: UserRole): Boolean {
        return isAdministrative(role)
    }

    fun canCreateInvoices(context: SecurityContext): Boolean {
        return canCreateInvoices(context.role)
    }
}

