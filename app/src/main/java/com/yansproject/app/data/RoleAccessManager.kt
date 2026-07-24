package com.yansproject.app.data

import com.yansproject.app.data.UserRole

object RoleAccessManager {

    /**
     * Checks if the user is authorized to delete global cash mutation logs.
     */
    fun canDeleteCashMutation(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Checks if the user can view the complete profit margin reports.
     */
    fun canViewProfitMargins(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Checks if the user can access the Developer Portal (13-tap).
     */
    fun canAccessDevPortal(role: UserRole): Boolean {
        return role == UserRole.OWNER
    }

    /**
     * Checks if the user can perform general stock production adjustments.
     */
    fun canManageStock(role: UserRole): Boolean {
        // Members are explicitly permitted to input production updates
        return role == UserRole.OWNER || role == UserRole.ADMIN || role == UserRole.MEMBER
    }

    /**
     * Checks if the user can update the custom projects progress.
     */
    fun canManageProjects(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN || role == UserRole.MEMBER
    }

    /**
     * Checks if the user can create and register new invoices.
     */
    fun canCreateInvoices(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN || role == UserRole.MEMBER
    }
}
