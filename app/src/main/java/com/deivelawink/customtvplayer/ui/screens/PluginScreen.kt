package com.deivelawink.customtvplayer.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.deivelawink.customtvplayer.streamlink.PluginMetadata
import com.deivelawink.customtvplayer.streamlink.PluginRepository

@Composable
fun PluginScreen(repository: PluginRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf(emptyList<PluginMetadata>()) }
    var warning by remember { mutableStateOf(false) }
    var sourceToImport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selected by remember { mutableStateOf<PluginMetadata?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) {
        plugins = withContext(Dispatchers.IO) { repository.list() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "plugin.py"
            val source = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (source != null) sourceToImport = name to source
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                val result: Result<Pair<Int, List<PluginMetadata>>> = runCatching {
                    withContext(Dispatchers.IO) {
                        val files = DocumentFile.fromTreeUri(context, uri)?.listFiles().orEmpty()
                            .filter { it.isFile && it.name?.endsWith(".py", true) == true }
                        var installed = 0
                        files.forEach { file ->
                            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
                                repository.import(file.name ?: "plugin.py", reader.readText())
                                installed++
                            }
                        }
                        installed to repository.list()
                    }
                }
                result.onSuccess { pair ->
                    plugins = pair.second
                    status = "${pair.first} plugin(s) instalado(s) desde la carpeta."
                }.onFailure { error -> status = "No se pudo importar la carpeta: ${error.message}" }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Plugins Streamlink", style = MaterialTheme.typography.headlineSmall)
        Text("Plugins instalados en el almacenamiento privado de la aplicación:")
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/streamlink/streamlink/tree/master/src/streamlink/plugins")))
        }) { Text("Consultar guía y plugins disponibles de Streamlink") }
        Text(
            "Los plugins Python ejecutan código dentro de la aplicación y no están aislados. " +
                "Importa solo archivos de fuentes en las que confíes.",
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = { warning = true }) { Text("Importar plugin .py") }
        OutlinedButton(onClick = { folderPicker.launch(null) }) {
            Text("Importar carpeta de plugins")
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(plugins, key = { it.id }) { plugin ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plugin.fileName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (plugin.domains.isEmpty()) "Dominio no detectado"
                            else plugin.domains.joinToString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        plugin.loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row {
                            TextButton(onClick = { selected = plugin }) { Text("Ver código") }
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.delete(plugin)
                                        repository.list()
                                    }.also { plugins = it }
                                }
                            }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            title = { Text("Código Python externo") },
            text = { Text("Un plugin puede acceder a red, archivos y datos del proceso. No existe aislamiento automático. ¿Quieres seleccionar un archivo de confianza?") },
            confirmButton = {
                TextButton(onClick = { warning = false; picker.launch(arrayOf("text/x-python", "text/plain", "*/*")) }) {
                    Text("Continuar")
                }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text("Cancelar") } }
        )
    }
    sourceToImport?.let { (name, source) ->
        AlertDialog(
            onDismissRequest = { sourceToImport = null },
            title = { Text("Validar plugin") },
            text = { Text("Se analizará el código antes de guardarlo y no se ejecutará como plugin Streamlink si no contiene una clase válida.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                repository.import(name, source)
                                repository.list()
                            }
                        }
                        result.onSuccess {
                            plugins = it
                            status = "Plugin instalado correctamente."
                            sourceToImport = null
                        }.onFailure {
                            status = "No se pudo instalar: ${it.message}"
                        }
                    }
                }) { Text("Instalar") }
            },
            dismissButton = { TextButton(onClick = { sourceToImport = null }) { Text("Cancelar") } }
        )
    }
    selected?.let { plugin ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(plugin.fileName) },
            text = { Text(plugin.source.take(8000)) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } }
        )
    }
}
