package com.deivelawink.customtvplayer.streamlink

import com.deivelawink.customtvplayer.data.model.ChannelTv
import org.json.JSONObject

data class ResolvedStream(
    val inputUrl: String,
    val playbackUrl: String,
    val pluginId: String? = null,
    val qualities: List<String> = listOf("best"),
    val selectedQuality: String = "best",
    val headers: Map<String, String> = emptyMap(),
    val isHls: Boolean = false,
    val diagnostics: String = ""
)

sealed interface ResolutionResult {
    data class Success(val stream: ResolvedStream) : ResolutionResult
    data class Failure(val message: String) : ResolutionResult
}

class StreamResolver(
    private val pythonBridge: PythonBridge,
    private val pluginRepository: PluginRepository
) {
    fun resolve(channel: ChannelTv): ResolutionResult {
        if (channel.inputUrl.isNullOrBlank()) {
            return ResolutionResult.Success(
                ResolvedStream(
                    channel.streamUrl,
                    channel.streamUrl,
                    isHls = channel.streamUrl.startsWith("hls:", ignoreCase = true) ||
                        channel.streamUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                )
            )
        }
        return runCatching {
            val result = pythonBridge.resolve(
                channel.inputUrl,
                pluginRepository.directoryPath,
                channel.preferredQuality ?: "best"
            )
            if (!result.optBoolean("ok")) {
                val code = result.optString("errorCode", "STREAMLINK_RESOLUTION_FAILED")
                val detail = result.optString("error", "No se pudo resolver el stream")
                ResolutionResult.Failure("[$code] $detail")
            } else {
                val playbackUrl = result.optString("url").trim()
                if (playbackUrl.isBlank()) {
                    return@runCatching ResolutionResult.Failure(
                        "[STREAMLINK_EMPTY_URL] Streamlink no devolvió una URL reproducible"
                    )
                }
                ResolutionResult.Success(
                    ResolvedStream(
                        inputUrl = channel.inputUrl,
                        playbackUrl = playbackUrl,
                        pluginId = channel.pluginId,
                        qualities = result.optJSONArray("qualities").toStringList(),
                        selectedQuality = result.optString("quality", "best"),
                        headers = result.optJSONObject("headers").toStringMap(),
                        isHls = result.optBoolean("isHls"),
                        diagnostics = result.optString("diagnostics")
                    )
                )
            }
        }.getOrElse {
            ResolutionResult.Failure("${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONObject?.toStringMap(): Map<String, String> =
        if (this == null) emptyMap() else keys().asSequence().associateWith { getString(it) }
}
