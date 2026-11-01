package com.deivelawink.customtvplayer.ui.screens

import android.net.Uri
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.deivelawink.customtvplayer.data.local.*
import com.deivelawink.customtvplayer.data.model.*
import androidx.media3.ui.PlayerView
import com.deivelawink.customtvplayer.player.PlayerManager
import com.deivelawink.customtvplayer.player.PlaybackNotificationManager
import kotlinx.coroutines.launch
import com.deivelawink.customtvplayer.streamlink.ResolutionResult
import com.deivelawink.customtvplayer.streamlink.StreamResolver
import com.deivelawink.customtvplayer.streamlink.PythonBridge
import com.deivelawink.customtvplayer.streamlink.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    channels: List<ChannelTv>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onPlay: (ChannelTv) -> Unit,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (channels.isEmpty()) {
            Text("Añade un canal o importa una lista M3U/M3U8.")
            Button(onAdd, Modifier.fillMaxWidth()) { Text("Añadir canal") }
            OutlinedButton(onImport, Modifier.fillMaxWidth()) { Text("Importar lista") }
        } else {
            Text("${channels.size} canales disponibles")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                channels.groupBy { it.countryName }.forEach { (country, countryChannels) ->
                    val countryKey = "country:$country"
                    item {
                        Row(Modifier.fillMaxWidth()) {
                            Text(country, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                collapsed = if (countryKey in collapsed) collapsed - countryKey else collapsed + countryKey
                            }) { Text(if (countryKey in collapsed) "↓" else "↑") }
                        }
                    }
                    if (countryKey !in collapsed) countryChannels.groupBy { it.category ?: "Nacionales" }.forEach { (category, categoryChannels) ->
                        val categoryKey = "$countryKey:$category"
                        item {
                            Row(Modifier.fillMaxWidth()) {
                                Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    collapsed = if (categoryKey in collapsed) collapsed - categoryKey else collapsed + categoryKey
                                }) { Text(if (categoryKey in collapsed) "↓" else "↑") }
                            }
                        }
                        if (categoryKey !in collapsed) {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.heightIn(max = 430.dp)
                            ) {
                                items(categoryChannels, key = { it.id }) { channel ->
                                    Card(onClick = { onPlay(channel) }, shape = RoundedCornerShape(16.dp)) {
                                        Column {
                                            if (channel.imageUrl.isNullOrBlank()) {
                                                Box(
                                                    Modifier.fillMaxWidth().height(84.dp),
                                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                                ) { Text("TV", style = MaterialTheme.typography.headlineMedium) }
                                            } else {
                                                AsyncImage(
                                                    model = channel.imageUrl,
                                                    contentDescription = channel.name,
                                                    imageLoader = imageLoader,
                                                    modifier = Modifier.fillMaxWidth().height(84.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                            Text(channel.name, Modifier.padding(8.dp), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun ChannelsScreen(channels: List<ChannelTv>, countries: List<Country>, onAdd: () -> Unit, onEdit: (ChannelTv) -> Unit, onDelete: (ChannelTv) -> Unit, onDeleteAll: () -> Unit, onPlay: (ChannelTv) -> Unit, onImport: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = channels.filter { it.name.contains(query, true) }
    Column(Modifier.padding(16.dp)) {
        Row {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), label = { Text("Buscar canales") })
            Button(onAdd) { Text("Añadir") }
        }
        OutlinedButton(onImport) { Text("Importar / exportar listas") }
        var confirmDeleteAll by remember { mutableStateOf(false) }
        Button(
            onClick = { confirmDeleteAll = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("Borrar todos los canales") }
        if (confirmDeleteAll) {
            AlertDialog(
                onDismissRequest = { confirmDeleteAll = false },
                title = { Text("Borrar todos los canales") },
                text = { Text("Esta acción eliminará todos los canales guardados y no se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = { confirmDeleteAll = false; onDeleteAll() }) {
                        Text("Borrar definitivamente", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton({ confirmDeleteAll = false }) { Text("Cancelar") } }
            )
        }
        LazyColumn {
            items(list, key = { it.id }) { channel ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(channel.name) }, supportingContent = { Text(channel.countryName) }, trailingContent = {
                        Row {
                            Button({ onPlay(channel) }) { Text("Reproducir") }
                            TextButton({ onEdit(channel) }) { Text("Editar") }
                            TextButton({ onDelete(channel) }) { Text("Borrar") }
                        }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChannelEditorScreen(channel: ChannelTv?, countries: List<Country>, onCancel: () -> Unit, onSave: (ChannelTv) -> Unit) {
    var name by remember { mutableStateOf(channel?.name.orEmpty()) }
    var url by remember { mutableStateOf(channel?.streamUrl.orEmpty()) }
    var websiteMode by remember { mutableStateOf(channel?.resolverType == "streamlink") }
    var inputUrl by remember { mutableStateOf(channel?.inputUrl.orEmpty()) }
    var imageUrl by remember { mutableStateOf(channel?.imageUrl.orEmpty()) }
    var country by remember { mutableStateOf(countries.firstOrNull { it.code == channel?.countryCode } ?: countries.firstOrNull() ?: Country("ES", "España", DefaultCategories.spain())) }
    var expanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(channel?.category) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (channel == null) "Añadir canal" else "Editar canal", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre *") })
        OutlinedTextField(imageUrl, { imageUrl = it }, Modifier.fillMaxWidth(), label = { Text("Logo (URL SVG, PNG o JPG, opcional)") })
        Text("Ejemplo: https://servidor.example/logo.png", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!websiteMode, { websiteMode = false }, label = { Text("Stream directo") })
            FilterChip(websiteMode, { websiteMode = true }, label = { Text("Web / Streamlink") })
        }
        if (websiteMode) {
            OutlinedTextField(inputUrl, { inputUrl = it }, Modifier.fillMaxWidth(), label = { Text("URL de la web *") })
            Text("Ejemplo: https://sitio.example/directo", style = MaterialTheme.typography.bodySmall)
            Text("El plugin se resolverá al reproducir. Puedes guardar aunque el sitio esté temporalmente caído.", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL del stream *") })
            Text("Ejemplos: https://servidor/canal.m3u8 o http://servidor/live", style = MaterialTheme.typography.bodySmall)
        }
        ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
            OutlinedTextField(country.name, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("País") })
            ExposedDropdownMenu(expanded, { expanded = false }) {
                countries.forEach { item -> DropdownMenuItem({ Text(item.name) }, { country = item; expanded = false }) }
            }
        }
        ExposedDropdownMenuBox(categoryExpanded, { categoryExpanded = !categoryExpanded }) {
            OutlinedTextField(category ?: "Nacionales", {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Categoría") })
            ExposedDropdownMenu(categoryExpanded, { categoryExpanded = false }) {
                DropdownMenuItem({ Text("Sin categoría") }, { category = null; categoryExpanded = false })
                country.categories.filter { it.parentId == null }.forEach { parent ->
                    DropdownMenuItem({ Text(parent.name) }, { category = parent.name; categoryExpanded = false })
                    country.categories.filter { it.parentId == parent.id }.forEach { child ->
                        DropdownMenuItem({ Text("${parent.name} / ${child.name}") }, {
                            category = "${parent.name} / ${child.name}"
                            categoryExpanded = false
                        })
                    }
                }
            }
        }
        Text("Importa archivos M3U/M3U8 desde Importar listas.", style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row {
            OutlinedButton(onCancel) { Text("Cancelar") }
            Button({
                when {
                    name.isBlank() -> error = "El nombre es obligatorio"
                    !TvRepository.isValidUrl(if (websiteMode) inputUrl else url) -> error = "URL no válida"
                    else -> onSave(
                        (channel ?: ChannelTv(name = name, streamUrl = if (websiteMode) inputUrl else url)).copy(
                            name = name.trim(),
                            imageUrl = imageUrl.trim().takeIf { it.isNotBlank() },
                            streamUrl = if (websiteMode) inputUrl.trim() else url.trim(),
                            inputUrl = inputUrl.trim().takeIf { websiteMode },
                            resolverType = "streamlink".takeIf { websiteMode },
                            countryCode = country.code,
                            countryName = country.name,
                            category = category
                        )
                    )
                }
            }) { Text("Guardar") }
        }
    }
}

@Composable fun ImportScreen(repository: TvRepository, database: TvDatabase, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { ExternalPlaylistImporter.parse(it.readText()) } }
                .onSuccess { repository.merge(it); onRefresh(); message = "Detectados ${it.channels.size} canales" }
                .onFailure { message = "Error: ${it.message}" }
        }
    }
    val exportConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use {
                    it.write(repository.exportJson())
                }
            }.onSuccess { message = "Configuración exportada correctamente" }
                .onFailure { message = "Error exportando configuración: ${it.message}" }
        }
    }
    val importConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
                    repository.parseJson(it.readText())
                }
            }.onSuccess {
                repository.replace(it)
                onRefresh()
                message = "Configuración restaurada: ${it.channels.size} canales"
            }.onFailure { message = "Error importando configuración: ${it.message}" }
        }
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Importar M3U / M3U8", style = MaterialTheme.typography.headlineSmall)
        Text("Selecciona una lista local mediante el selector de archivos.")
        Button({ picker.launch(arrayOf("*/*")) }) { Text("Seleccionar archivo") }
        Text("${database.channels.size} canales guardados")
        HorizontalDivider()
        Text("Configuración personal", style = MaterialTheme.typography.headlineSmall)
        Text("Incluye canales, países y categorías. Puedes editar el JSON fuera de la aplicación.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ exportConfig.launch("tv-personalizada-configuracion.json") }) {
                Text("Exportar configuración")
            }
            OutlinedButton({ importConfig.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                Text("Importar configuración")
            }
        }
        Text(message)
    }
}

@Composable fun PlayerScreen(channel: ChannelTv, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var resolvedStream by remember { mutableStateOf<com.deivelawink.customtvplayer.streamlink.ResolvedStream?>(null) }
    var retryGeneration by remember { mutableIntStateOf(0) }
    val resolver = remember {
        StreamResolver(
            PythonBridge(context.applicationContext),
            PluginRepository(context.applicationContext)
        )
    }
    DisposableEffect(Unit) {
        activity?.window?.apply {
            statusBarColor = Color.Black.toArgb()
            navigationBarColor = Color.Black.toArgb()
            decorView.setBackgroundColor(android.graphics.Color.BLACK)
        }
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.apply {
                statusBarColor = android.graphics.Color.TRANSPARENT
                navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    DisposableEffect(fullscreen) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                if (fullscreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler(onBack = onBack)
    DisposableEffect(Unit) {
        onDispose {
            PlayerManager.release()
            PlaybackNotificationManager.stop(context)
        }
    }
    LaunchedEffect(channel, retryGeneration) {
        resolving = true
        resolvedStream = null
        error = null
        runCatching {
            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(30_000L) { resolver.resolve(channel) }
                    ?: ResolutionResult.Failure(
                        "[STREAMLINK_TIMEOUT] Streamlink agotó el tiempo de espera al resolver " +
                            (channel.inputUrl ?: channel.streamUrl)
                    )
            }
            when (result) {
                is ResolutionResult.Success -> {
                    resolvedStream = result.stream
                    resolving = false
                }
                is ResolutionResult.Failure -> error = result.message
            }
        }.onFailure {
            if (it !is kotlinx.coroutines.CancellationException) {
                error = "No se pudo iniciar la reproducción: ${it.message ?: it.javaClass.simpleName}"
            }
        }.also {
            if (error != null) resolving = false
        }
    }
    if (resolving) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else if (error == null && resolvedStream != null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply {
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    PlayerManager.prepare(context, resolvedStream!!, this)
                    PlaybackNotificationManager.show(context, channel.name)
                } },
                update = { },
                onRelease = { PlayerManager.stop() },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                Modifier.align(androidx.compose.ui.Alignment.TopStart).statusBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!fullscreen) {
                    TextButton(onClick = onBack) { Text("← Inicio", color = Color.White) }
                }
                if (!fullscreen) {
                    TextButton(onClick = { PlayerManager.togglePause() }) {
                        Text("⏯", color = Color.White)
                    }
                }
                TextButton(onClick = { fullscreen = !fullscreen }) {
                    Text(if (fullscreen) "◲" else "⛶", color = Color.White)
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(error ?: "No se pudo iniciar la reproducción", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                error = null
                retryGeneration++
            }) {
                Text("Reintentar")
            }
            TextButton(onClick = onBack) {
                Text("Volver al inicio")
            }
        }
    }
}
