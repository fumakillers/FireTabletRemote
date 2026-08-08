package com.fumakillers.fireremoteserver.preview

import android.graphics.Bitmap
import java.util.concurrent.Executor

sealed interface ScreenshotCaptureResult {
    data class Success(val bitmap: Bitmap) : ScreenshotCaptureResult
    data class Error(val message: String) : ScreenshotCaptureResult
}

fun interface ScreenshotGateway {
    fun capture(executor: Executor, callback: (ScreenshotCaptureResult) -> Unit)
}
