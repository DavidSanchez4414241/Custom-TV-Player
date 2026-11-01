package com.deivelawink.customtvplayer.streamlink

import android.content.Context
import java.io.File
import java.util.UUID

class PluginRepository(private val context: Context) {
    private val directory get() = File(context.filesDir, "streamlink-plugins").also { it.mkdirs() }
    val directoryPath: String get() = directory.absolutePath

    fun list(): List<PluginMetadata> = directory.listFiles { file ->
        file.extension.equals("py", ignoreCase = true)
    }?.mapNotNull { file ->
        runCatching {
            val source = file.readText()
            val inspection = PythonBridge(context).inspectPlugin(source)
            PluginMetadata(
                file.nameWithoutExtension, file.name, source,
                inspection.domains, inspection.classes, loadError = inspection.error
            )
        }.getOrNull()
    }.orEmpty()

    fun import(fileName: String, source: String): PluginMetadata {
        val inspection = PythonBridge(context).inspectPlugin(source)
        require(inspection.valid) { inspection.error ?: "El archivo no contiene una clase Streamlink Plugin válida." }
        val safeName = fileName.substringAfterLast('/').ifBlank { "plugin-${UUID.randomUUID()}.py" }
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        File(directory, safeName).writeText(source)
        return PluginMetadata(safeName.substringBeforeLast('.'), safeName, source, inspection.domains, inspection.classes)
    }

    fun delete(plugin: PluginMetadata) { File(directory, plugin.fileName).delete() }
}
