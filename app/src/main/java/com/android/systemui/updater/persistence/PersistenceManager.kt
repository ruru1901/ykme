package com.android.systemui.updater.persistence

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PersistenceManager {

    private const val WORK_NAME = "service_restart_worker"

    fun enqueuePeriodicWork(context: Context) {
        val workRequest = PeriodicWorkRequest.Builder(
            ServiceRestartWorker::class.java,
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}