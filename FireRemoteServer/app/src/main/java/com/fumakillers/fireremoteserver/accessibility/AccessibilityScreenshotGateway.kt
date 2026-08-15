package com.fumakillers.fireremoteserver.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import com.fumakillers.fireremoteserver.preview.ScreenshotCaptureResult
import com.fumakillers.fireremoteserver.preview.ScreenshotGateway
import com.fumakillers.fireremoteserver.logging.RemoteLogger
import java.util.concurrent.Executor

object AccessibilityScreenshotGateway : ScreenshotGateway {
    private var service: FireRemoteAccessibilityService? = null

    @Synchronized
    fun connect(connectedService: FireRemoteAccessibilityService) {
        service = connectedService
    }

    @Synchronized
    fun disconnect(disconnectedService: FireRemoteAccessibilityService) {
        if (service === disconnectedService) {
            service = null
        }
    }

    override fun capture(
        executor: Executor,
        callback: (ScreenshotCaptureResult) -> Unit,
    ) {
        val connectedService = synchronized(this) { service }
        if (connectedService == null) {
            callback(ScreenshotCaptureResult.Error("Accessibility service is not connected"))
            return
        }

        try {
            connectedService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val result = try {
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                hardwareBuffer,
                                screenshot.colorSpace,
                            )
                            if (hardwareBitmap == null) {
                                ScreenshotCaptureResult.Error("Could not read screenshot buffer")
                            } else {
                                val softwareBitmap = try {
                                    hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                } finally {
                                    hardwareBitmap.recycle()
                                }

                                if (softwareBitmap == null) {
                                    ScreenshotCaptureResult.Error("Could not copy screenshot bitmap")
                                } else {
                                    ScreenshotCaptureResult.Success(softwareBitmap)
                                }
                            }
                        } catch (error: RuntimeException) {
                            RemoteLogger.error(TAG, "Screenshot buffer conversion failed", error)
                            ScreenshotCaptureResult.Error("Screenshot buffer conversion failed")
                        } finally {
                            hardwareBuffer.close()
                        }
                        callback(result)
                    }

                    override fun onFailure(errorCode: Int) {
                        callback(ScreenshotCaptureResult.Error(screenshotErrorMessage(errorCode)))
                    }
                },
            )
        } catch (error: RuntimeException) {
            RemoteLogger.error(TAG, "Screenshot request failed", error)
            callback(ScreenshotCaptureResult.Error("Screenshot request failed"))
        }
    }

    private fun screenshotErrorMessage(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Screenshot failed: internal error"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "Screenshot failed: no accessibility access"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "Screenshot failed: requests are too frequent"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "Screenshot failed: invalid display"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
            "Screenshot failed: invalid window"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "Screenshot failed: secure window"
        else -> "Screenshot failed: error code $errorCode"
    }

    private const val TAG = "FireRemotePreview"
}
