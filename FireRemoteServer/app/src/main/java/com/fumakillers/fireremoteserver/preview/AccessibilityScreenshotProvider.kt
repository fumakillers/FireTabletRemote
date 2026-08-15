package com.fumakillers.fireremoteserver.preview

import android.graphics.Bitmap
import com.fumakillers.fireremoteserver.logging.RemoteLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class AccessibilityScreenshotProvider(
    private val screenshotGateway: ScreenshotGateway,
) : PreviewProvider, AutoCloseable {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureInProgress = AtomicBoolean(false)

    override fun capture(callback: (PreviewResult) -> Unit) {
        if (!captureInProgress.compareAndSet(false, true)) {
            callback(PreviewResult.Error("Preview capture is already in progress"))
            return
        }

        try {
            screenshotGateway.capture(worker) { screenshotResult ->
                try {
                    when (screenshotResult) {
                        is ScreenshotCaptureResult.Success -> encode(screenshotResult.bitmap, callback)
                        is ScreenshotCaptureResult.Error -> {
                            RemoteLogger.warn(TAG, screenshotResult.message)
                            callback(PreviewResult.Error(screenshotResult.message))
                        }
                    }
                } catch (error: RuntimeException) {
                    RemoteLogger.error(TAG, "Preview encode failed", error)
                    callback(PreviewResult.Error("Preview encode failed"))
                } finally {
                    captureInProgress.set(false)
                }
            }
        } catch (error: RuntimeException) {
            captureInProgress.set(false)
            RemoteLogger.error(TAG, "Screenshot request failed", error)
            callback(PreviewResult.Error("Screenshot request failed"))
        }
    }

    private fun encode(source: Bitmap, callback: (PreviewResult) -> Unit) {
        try {
            val sourceWidth = source.width
            val sourceHeight = source.height
            val largestDimension = maxOf(source.width, source.height)
            val scale = minOf(1.0, MAX_DIMENSION.toDouble() / largestDimension)
            val outputWidth = maxOf(1, (source.width * scale).roundToInt())
            val outputHeight = maxOf(1, (source.height * scale).roundToInt())
            val output = if (outputWidth == source.width && outputHeight == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, outputWidth, outputHeight, true)
            }

            try {
                val jpegBytes = ByteArrayOutputStream().use { stream ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                        RemoteLogger.warn(TAG, "JPEG encode was rejected")
                        callback(PreviewResult.Error("JPEG encode failed"))
                        return
                    }
                    stream.toByteArray()
                }
                RemoteLogger.debug(TAG, "Screenshot encoded: ${output.width}x${output.height}, ${jpegBytes.size} bytes")
                callback(
                    PreviewResult.Frame(
                        width = output.width,
                        height = output.height,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        jpegBytes = jpegBytes,
                    ),
                )
            } finally {
                if (output !== source) {
                    output.recycle()
                }
            }
        } finally {
            source.recycle()
        }
    }

    override fun close() {
        worker.shutdownNow()
    }

    companion object {
        const val MAX_DIMENSION = 640
        const val JPEG_QUALITY = 55
        private const val TAG = "FireRemotePreview"
    }
}
