package com.yansproject.app.data

import com.yansproject.app.data.UserRole

object RoleAccessManager {

    /**
     * Checks if the user is authorized to view global financial ledgers, cash mutations, and profit reports.
     */
    fun canAccessFinancials(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

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
     * Checks if the user can view all member directory and detailed member data.
     */
    fun canViewMemberDirectory(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Checks if the user can perform general stock production adjustments, batch creation, and price editing.
     */
    fun canManageStock(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Checks if the user can create and update custom projects progress.
     */
    fun canManageProjects(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }

    /**
     * Checks if the user can create and register arbitrary invoices for all clients.
     */
    fun canCreateInvoices(role: UserRole): Boolean {
        return role == UserRole.OWNER || role == UserRole.ADMIN
    }
}

