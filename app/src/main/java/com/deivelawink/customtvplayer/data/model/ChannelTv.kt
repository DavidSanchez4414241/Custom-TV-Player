package com.deivelawink.customtvplayer.data.model

import java.util.UUID

data class ChannelTv(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val streamUrl: String,
    val imageUrl: String? = null,
    val countryCode: String = "ES",
    val countryName: String = "España",
    /** A category path such as "Nacionales" or "Autonómicos / Andalucía". */
    val category: String? = null,
    val inputUrl: String? = null,
    val resolverType: String? = null,
    val pluginId: String? = null,
    val preferredQuality: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val countryCode: String,
    val builtIn: Boolean = false
)

data class Country(
    val code: String,
    val name: String,
    val categories: List<Category> = emptyList(),
    val builtIn: Boolean = false
)

data class TvDatabase(
    val version: Int = 2,
    val channels: List<ChannelTv> = emptyList(),
    val countries: List<Country> = emptyList()
)

object DefaultCategories {
    private val national = listOf("Nacionales", "Informativos", "Deportivos", "Infantiles", "Eventuales Streaming")
    private val autonomous = listOf(
        "Andalucía", "Aragón", "Asturias", "Illes Balears", "Canarias",
        "Cantabria", "Castilla-La Mancha", "Castilla y León", "Cataluña",
        "Comunitat Valenciana", "Extremadura", "Galicia", "Comunidad de Madrid",
        "Región de Murcia", "Comunidad Foral de Navarra", "País Vasco",
        "La Rioja", "Ceuta", "Melilla"
    )

    fun spain(): List<Category> {
        val roots = national.map { Category(slug(it), it, countryCode = "ES", builtIn = true) } +
            Category("autonomicos", "Autonómicos", countryCode = "ES", builtIn = true)
        val children = autonomous.map {
            Category(slug("autonomicos-$it"), it, "autonomicos", "ES", true)
        }
        return roots + children
    }

    fun forCountry(code: String): List<Category> =
        if (code.equals("ES", true)) spain() else emptyList()

    fun slug(value: String) = value.lowercase()
        .replace(Regex("[^a-z0-9áéíóúüñ]+"), "-")
        .trim('-')
}
