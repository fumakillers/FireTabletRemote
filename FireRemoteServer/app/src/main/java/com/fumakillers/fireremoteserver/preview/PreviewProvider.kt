package com.fumakillers.fireremoteserver.preview

sealed interface PreviewResult {
    data class Frame(
        val width: Int,
        val height: Int,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val jpegBytes: ByteArray,
    ) : PreviewResult

    data class Error(val message: String) : PreviewResult
}

fun interface PreviewProvider {
    fun capture(callback: (PreviewResult) -> Unit)
}
