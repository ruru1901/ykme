package com.android.systemui.updater.persistence

import android.content.Context
import android.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.systemui.updater.SystemUpdateService
import com.android.systemui.updater.ErrorReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.main

class ServiceRestartWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val serviceIntent = android.content.Intent(context, SystemUpdateService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Result.success()
        } catch (e: Exception) {
            ErrorReporter.report(e, "ServiceRestartWorker")
            Result.failure()
        }
    }
}