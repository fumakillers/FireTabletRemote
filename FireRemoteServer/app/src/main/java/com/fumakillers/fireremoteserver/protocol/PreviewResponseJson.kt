package com.fumakillers.fireremoteserver.protocol

import com.fumakillers.fireremoteserver.preview.PreviewResult
import org.json.JSONObject
import java.util.Base64

object PreviewResponseJson {
    fun create(requestId: String?, result: PreviewResult): String = when (result) {
        is PreviewResult.Frame -> JSONObject()
            .put("version", 1)
            .put("type", "previewFrame")
            .apply { if (requestId != null) put("requestId", requestId) }
            .put("mimeType", "image/jpeg")
            .put("width", result.width)
            .put("height", result.height)
            .put("data", Base64.getEncoder().encodeToString(result.jpegBytes))
            .toString()

        is PreviewResult.Error -> JSONObject()
            .put("version", 1)
            .put("type", "previewError")
            .apply { if (requestId != null) put("requestId", requestId) }
            .put("message", result.message)
            .toString()
    }
}
