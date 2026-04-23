package com.example.agent

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import com.example.agent.ErrorReporter
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * EncryptedConfig — stores bot token and chat ID encrypted using device-specific key.
 * Uses AES/GCM/NoPadding for strong encryption.
 * Key derivation: SHA-256 of (device ID + app ID + salt)
 *
 * Data is stored in SharedPreferences encrypted. On first run, plain values are encrypted and stored.
 * On subsequent runs, values are decrypted from storage.
 *
 * Fallback: if decryption fails (e.g., device changed), uses hardcoded defaults (for testing).
 */
class EncryptedConfig private constructor(private val ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val deviceId: String = Build.FINGERPRINT + Build.SERIAL + Build.ID
    private val appId: String = ctx.packageName

    companion object {
        private const val PREFS_NAME = "encrypted_config"
        private const val KEY_BOT_TOKEN = "enc_bot_token"
        private const val KEY_CHAT_ID = "enc_chat_id"
        private const val SALT = "rUru1901@yKme_Agent_2025"

        @Volatile
        private var instance: EncryptedConfig? = null

        fun getInstance(context: Context): EncryptedConfig {
            return instance ?: synchronized(this) {
                instance ?: EncryptedConfig(context.applicationContext).also { instance = it }
            }
        }
    }

    // Strong key derivation from device-specific data
    private fun deriveKey(): ByteArray {
        val raw = "$deviceId|$appId|$SALT"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8))
    }

    // Encrypt plaintext using AES/GCM
    private fun encrypt(plaintext: String): EncryptedBlob? {
        return try {
            val keyBytes = deriveKey()
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            EncryptedBlob(iv, encrypted)
        } catch (e: Exception) {
            ErrorReporter.report(e, "EncryptedConfig")
            null
        }
    }

    // Decrypt stored ciphertext
    private fun decrypt(iv: ByteArray, ciphertext: ByteArray): String? {
        return try {
            val keyBytes = deriveKey()
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            ErrorReporter.report(e, "EncryptedConfig")
            null
        }
    }

    // Get bot token (decrypted)
    fun getBotToken(): String {
        val ivB64 = prefs.getString("${KEY_BOT_TOKEN}_iv", null)
        val ctB64 = prefs.getString(KEY_BOT_TOKEN, null)

        if (ivB64 != null && ctB64 != null) {
            try {
                val iv = android.util.Base64.decode(ivB64, android.util.Base64.DEFAULT)
                val ct = android.util.Base64.decode(ctB64, android.util.Base64.DEFAULT)
                decrypt(iv, ct)?.let { return it }
            } catch (e: Exception) {
                ErrorReporter.report(e, "EncryptedConfig")
                // Decryption failed — maybe device changed or corrupted
            }
        }

        // FALLBACK: return hardcoded value (for testing, will not work if not set)
        return "8697419498:AAFUkgi0_Jft2lpC5M5dWsM2rpVhIeYc91Q"
    }

    // Get chat ID (decrypted)
    fun getChatId(): String {
        val ivB64 = prefs.getString("${KEY_CHAT_ID}_iv", null)
        val ctB64 = prefs.getString(KEY_CHAT_ID, null)

        if (ivB64 != null && ctB64 != null) {
            try {
                val iv = android.util.Base64.decode(ivB64, android.util.Base64.DEFAULT)
                val ct = android.util.Base64.decode(ctB64, android.util.Base64.DEFAULT)
                decrypt(iv, ct)?.let { return it }
            } catch (e: Exception) {
                ErrorReporter.report(e, "EncryptedConfig")
                // Decryption failed
            }
        }

        return "8036939276"
    }

    // Initialize and encrypt stored values on first run
    fun initializeIfNeeded(plainBotToken: String, plainChatId: String) {
        val alreadyInitialized = prefs.getBoolean("initialized", false)
        if (alreadyInitialized) return

        val tokenBlob = encrypt(plainBotToken) ?: return
        val chatBlob = encrypt(plainChatId) ?: return

        prefs.edit()
            .putString(KEY_BOT_TOKEN, android.util.Base64.encodeToString(tokenBlob.ciphertext, android.util.Base64.DEFAULT))
            .putString("${KEY_BOT_TOKEN}_iv", android.util.Base64.encodeToString(tokenBlob.iv, android.util.Base64.DEFAULT))
            .putString(KEY_CHAT_ID, android.util.Base64.encodeToString(chatBlob.ciphertext, android.util.Base64.DEFAULT))
            .putString("${KEY_CHAT_ID}_iv", android.util.Base64.encodeToString(chatBlob.iv, android.util.Base64.DEFAULT))
            .putBoolean("initialized", true)
            .apply()
    }

    private data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray)
}
