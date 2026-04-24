package com.android.systemui.updater.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Utility for managing runtime permissions.
 */
object PermissionManager {

    /**
     * Checks if a permission is granted.
     * @param context The context
     * @param permission The permission to check (e.g., Manifest.permission.SEND_SMS)
     * @return true if granted, false otherwise
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if multiple permissions are granted.
     * @param context The context
     * @param permissions The permissions to check
     * @return true if all permissions are granted, false otherwise
     */
    fun arePermissionsGranted(context: Context, vararg permissions: String): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

    /**
     * Requests a permission (if not already granted).
     * Note: This is a placeholder. In a real app, you would use ActivityCompat.requestPermissions
     * from an Activity or Fragment.
     * @param context The context
     * @param permission The permission to request
     */
    fun requestPermission(context: Context, permission: String) {
        // This method is a placeholder. Actual permission requesting must be done from an Activity.
        // For example: ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
        // Since we don't have an activity context here, we cannot perform the request.
        // In a real implementation, you might callback to an Activity or use a different approach.
    }
}