package com.deivelawink.customtvplayer.data.local

import com.deivelawink.customtvplayer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ExternalPlaylistImporter {
    suspend fun download(url: String): TvDatabase = withContext(Dispatchers.IO) {
        require(TvRepository.isValidUrl(url)) { "La URL debe usar HTTP o HTTPS." }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        try {
            connection.inputStream.bufferedReader().use { parse(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    fun parse(text: String): TvDatabase {
        if (text.trimStart().startsWith("#EXTM3U")) return parseM3u(text)
        val root = JSONObject(text)
        val array = root.optJSONArray("channels") ?: root.optJSONArray("items")
            ?: root.optJSONArray("data") ?: return TvDatabase()
        return TvDatabase(channels = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name", item.optString("title")).trim()
            val url = item.optString("url", item.optString("streamUrl")).trim()
            if (name.isBlank() || !TvRepository.isValidUrl(url)) null else ChannelTv(
                name = name,
                streamUrl = url,
                imageUrl = item.optString("logo").takeIf { it.isNotBlank() },
                countryCode = item.optString("countryCode", "ES"),
                countryName = item.optString("country", "España"),
                category = item.optString("category").takeIf { it.isNotBlank() }
            )
        })
    }

    fun parseM3u(text: String): TvDatabase {
        var metadata = emptyMap<String, String>()
        val channels = mutableListOf<ChannelTv>()
        text.lineSequence().map(String::trim).forEach { line ->
            when {
                line.startsWith("#EXTINF", true) -> {
                    metadata = Regex("""([\w-]+)="([^"]*)"""")
                        .findAll(line)
                        .associate { it.groupValues[1] to it.groupValues[2] }
                        .plus("name" to line.substringAfter(',', "Canal").trim())
                }
                line.isNotBlank() && !line.startsWith('#') &&
                    TvRepository.isValidUrl(line) -> {
                    val group = metadata["group-title"].orEmpty().trim()
                    channels += ChannelTv(
                        name = metadata["name"].orEmpty().ifBlank { "Canal" },
                        streamUrl = line,
                        imageUrl = metadata["tvg-logo"]?.takeIf(String::isNotBlank),
                        category = group.takeIf(String::isNotBlank)
                    )
                    metadata = emptyMap()
                }
            }
        }
        return TvDatabase(channels = channels)
    }
}
