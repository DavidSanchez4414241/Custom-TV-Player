package com.deivelawink.customtvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.deivelawink.customtvplayer.data.local.TvRepository
import com.deivelawink.customtvplayer.data.model.ChannelTv
import com.deivelawink.customtvplayer.ui.screens.*
import com.deivelawink.customtvplayer.ui.theme.CustomTVPlayerTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import java.util.Locale
import android.app.PictureInPictureParams
import android.util.Rational
import com.deivelawink.customtvplayer.player.PlayerManager

enum class Destination(val title: String, val glyph: String) {
    HOME("Inicio", "⌂"),
    CHANNELS("Mis canales", "▣"),
    IMPORT("Importar / exportar", "⇄"),
    REGIONS("Categorías", "◎"),
    GUIDE("Guía de uso", "?"),
    LEGAL("Aviso legal", "§"),
    SETTINGS("Ajustes", "⚙")
    ,PLUGINS("Plugins Python", "⌘")
}

class MainActivity : ComponentActivity() {
    internal var playerScreenVisible = false

    fun updatePictureInPicture(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(enabled)
                    .build()
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playerScreenVisible &&
            PlayerManager.isPlaying() &&
            !isInPictureInPictureMode
        ) {
            enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
        setContent {
            val preferences = remember { getSharedPreferences("settings", MODE_PRIVATE) }
            var theme by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
            var language by remember { mutableStateOf(preferences.getString("language", "es") ?: "es") }
            CustomTVPlayerTheme(
                darkTheme = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
            ) {
                TvApp(theme, language, { selected ->
                    theme = selected
                    preferences.edit().putString("theme", selected).apply()
                }, { selected ->
                    language = selected
                    preferences.edit().putString("language", selected).apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        getSystemService(LocaleManager::class.java).applicationLocales =
                            LocaleList.forLanguageTags(selected)
                    } else {
                        Locale.setDefault(Locale.forLanguageTag(selected))
                    }
                })
            }
        }

    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvApp(theme: String, language: String, onThemeChanged: (String) -> Unit, onLanguageChanged: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { TvRepository(context.applicationContext) }
    val pluginRepository = remember { com.deivelawink.customtvplayer.streamlink.PluginRepository(context.applicationContext) }
    var database by remember { mutableStateOf(repository.snapshot()) }
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var editor by remember { mutableStateOf<ChannelTv?>(null) }
    var creating by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<ChannelTv?>(null) }
    val hostActivity = context as? MainActivity
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val refresh = { database = repository.snapshot() }
    LaunchedEffect(playing) {
        hostActivity?.playerScreenVisible = playing != null
        hostActivity?.updatePictureInPicture(playing != null)
    }

    if (playing != null) {
        PlayerScreen(playing!!) { playing = null }
        return
    }
    if (creating || editor != null) {
        ChannelEditorScreen(
            channel = if (creating) null else editor,
            countries = database.countries,
            onCancel = { creating = false; editor = null },
            onSave = { channel ->
                repository.saveChannels(database.channels.filterNot { it.id == channel.id } + channel)
                refresh()
                creating = false
                editor = null
            }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 24.dp)) {
                    Text("TV Personalizada", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                    Text("Tu reproductor, tus fuentes", modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))
                    Destination.entries.filter { it != Destination.IMPORT && it != Destination.LEGAL }.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Text(item.glyph) },
                            label = { Text(item.title) },
                            selected = item == destination,
                            onClick = {
                                destination = item
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(destination.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (destination) {
                    Destination.HOME -> HomeScreen(
                        channels = database.channels,
                        onAdd = { creating = true },
                        onImport = { destination = Destination.IMPORT },
                        onPlay = { playing = it }
                    )
                    Destination.CHANNELS -> ChannelsScreen(
                        channels = database.channels,
                        countries = database.countries,
                        onAdd = { creating = true },
                        onEdit = { editor = it },
                        onDelete = { repository.saveChannels(database.channels - it); refresh() },
                        onDeleteAll = { repository.saveChannels(emptyList()); refresh() },
                        onPlay = { playing = it },
                        onImport = { destination = Destination.IMPORT }
                    )
                    Destination.IMPORT -> ImportScreen(repository, database, refresh)
                    Destination.REGIONS -> RegionsScreen(database.countries) { countries ->
                        val validCountries = countries.map { it.code }.toSet()
                        val validCategories = countries.associate { country ->
                            country.code to country.categories.map { category ->
                                val parent = country.categories.firstOrNull { it.id == category.parentId }
                                if (parent == null) category.name else "${parent.name} / ${category.name}"
                            }.toSet()
                        }
                        repository.saveChannels(database.channels.filter { channel ->
                            channel.countryCode in validCountries &&
                                (channel.category == null || channel.category in (validCategories[channel.countryCode] ?: emptySet()))
                        })
                        repository.saveCountries(countries)
                        refresh()
                    }
                    Destination.GUIDE -> GuideScreen()
                    Destination.LEGAL -> LegalScreen()
                    Destination.SETTINGS -> SettingsScreen(
                        theme,
                        language,
                        onThemeChanged,
                        onLanguageChanged,
                        onLegal = { destination = Destination.LEGAL }
                    )
                    Destination.PLUGINS -> PluginScreen(pluginRepository)
                }
            }
        }
    }
}
