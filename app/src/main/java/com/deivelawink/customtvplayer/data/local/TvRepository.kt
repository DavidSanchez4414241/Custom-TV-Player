package com.deivelawink.customtvplayer.data.local

import android.content.Context
import com.deivelawink.customtvplayer.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TvRepository(private val context: Context) {
    private val fileName = "canalesTv.json"
    private var database = load()

    fun snapshot() = database
    fun saveChannels(value: List<ChannelTv>) {
        database = database.copy(channels = value)
        persist()
    }

    fun saveCountries(value: List<Country>) {
        database = database.copy(countries = value)
        persist()
    }

    fun merge(imported: TvDatabase): Int {
        val old = database.channels.toMutableList()
        var added = 0
        imported.channels.forEach { channel ->
            if (old.none { it.id == channel.id || it.streamUrl.equals(channel.streamUrl, true) }) {
                old += channel
                added++
            }
        }
        val importedCategories = imported.channels.groupBy { it.countryCode }.map { (code, channels) ->
            Country(
                code = code,
                name = channels.firstOrNull()?.countryName ?: code,
                categories = channels.mapNotNull { channel ->
                    channel.category?.let {
                        Category(DefaultCategories.slug("$code-$it"), it, countryCode = code)
                    }
                }.distinctBy { it.id }
            )
        }
        val countries = (database.countries + imported.countries + importedCategories).mergeCountries()
        database = database.copy(channels = old, countries = countries)
        persist()
        return added
    }

    fun replace(value: TvDatabase) {
        database = value
        persist()
    }

    fun exportJson() = encode(database).toString(2)

    fun parseJson(text: String): TvDatabase {
        val root = JSONObject(text)
        return TvDatabase(
            version = 2,
            channels = root.optJSONArray("channels").channels(),
            countries = root.optJSONArray("countries").countries()
        )
    }

    private fun load() = runCatching {
        if (!context.getFileStreamPath(fileName).exists()) emptyDb()
        else parseJson(context.openFileInput(fileName).bufferedReader().use { it.readText() })
    }.getOrElse { emptyDb() }

    private fun emptyDb() = TvDatabase(
        countries = listOf(Country("ES", "España", DefaultCategories.spain(), true))
    )

    private fun persist() {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(encode(database).toString().toByteArray())
        }
    }

    private fun encode(db: TvDatabase) = JSONObject().apply {
        put("version", 2)
        put("channels", JSONArray().apply {
            db.channels.forEach { channel ->
                put(JSONObject().apply {
                    put("id", channel.id); put("name", channel.name)
                    put("streamUrl", channel.streamUrl); put("imageUrl", channel.imageUrl)
                    put("countryCode", channel.countryCode); put("countryName", channel.countryName)
                    put("category", channel.category); put("inputUrl", channel.inputUrl)
                    put("resolverType", channel.resolverType); put("pluginId", channel.pluginId)
                    put("preferredQuality", channel.preferredQuality)
                    put("createdAt", channel.createdAt); put("updatedAt", channel.updatedAt)
                })
            }
        })
        put("countries", JSONArray().apply {
            db.countries.forEach { country ->
                put(JSONObject().apply {
                    put("code", country.code); put("name", country.name)
                    put("builtIn", country.builtIn)
                    put("categories", JSONArray().apply {
                        country.categories.forEach { category ->
                            put(JSONObject().apply {
                                put("id", category.id); put("name", category.name)
                                put("parentId", category.parentId)
                                put("countryCode", category.countryCode)
                                put("builtIn", category.builtIn)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun JSONArray?.channels() = if (this == null) emptyList() else
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                val name = item.optString("name").trim()
                val url = item.optString("streamUrl", item.optString("url")).trim()
                if (name.isBlank() || !isValidUrl(url)) null else {
                    val oldRegion = item.optString("regionName").takeIf { it.isNotBlank() }
                    ChannelTv(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name, streamUrl = url,
                        imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                        countryCode = item.optString("countryCode", "ES"),
                        countryName = item.optString("countryName", "España"),
                        category = item.optString("category").takeIf { it.isNotBlank() }
                            ?: oldRegion?.let { "Autonómicos / $it" },
                        inputUrl = item.optString("inputUrl").takeIf { it.isNotBlank() },
                        resolverType = item.optString("resolverType").takeIf { it.isNotBlank() },
                        pluginId = item.optString("pluginId").takeIf { it.isNotBlank() },
                        preferredQuality = item.optString("preferredQuality").takeIf { it.isNotBlank() },
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", System.currentTimeMillis())
                    )
                }
            }
        }

    private fun JSONArray?.countries() = if (this == null) emptyList() else
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                val code = item.optString("code")
                val categories = item.optJSONArray("categories").categories(code)
                val legacyRegions = item.optJSONArray("regions").legacyCategories(code)
                Country(
                    code = code, name = item.optString("name"),
                    categories = (categories + legacyRegions).distinctBy { it.id },
                    builtIn = item.optBoolean("builtIn")
                )
            }
        }

    private fun JSONArray?.categories(countryCode: String) = if (this == null) emptyList() else
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let { item ->
                Category(
                    item.optString("id"), item.optString("name"),
                    item.optString("parentId").takeIf { it.isNotBlank() },
                    item.optString("countryCode", countryCode), item.optBoolean("builtIn")
                )
            }
        }

    private fun JSONArray?.legacyCategories(countryCode: String) = if (this == null) emptyList() else
        (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let {
                val name = it.optString("name")
                if (name.isBlank()) null else Category(
                    DefaultCategories.slug("autonomicos-$name"), name, "autonomicos",
                    countryCode, it.optBoolean("builtIn")
                )
            }
        }

    private fun List<Country>.mergeCountries() = groupBy { it.code }.map { (_, values) ->
        values.first().copy(categories = values.flatMap { it.categories }.distinctBy { it.id })
    }

    companion object {
        fun isValidUrl(value: String) = value.startsWith("http://") || value.startsWith("https://")
    }
}
