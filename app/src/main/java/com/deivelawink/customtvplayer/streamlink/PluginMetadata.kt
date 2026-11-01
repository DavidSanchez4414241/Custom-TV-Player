package com.deivelawink.customtvplayer.streamlink

data class PluginMetadata(
    val id: String,
    val fileName: String,
    val source: String,
    val domains: List<String>,
    val classNames: List<String>,
    val enabled: Boolean = true,
    val loadError: String? = null
)
