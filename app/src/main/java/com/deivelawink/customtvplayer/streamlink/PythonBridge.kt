package com.deivelawink.customtvplayer.streamlink

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

data class PythonInspection(
    val valid: Boolean,
    val classes: List<String>,
    val domains: List<String>,
    val error: String? = null
)

class PythonBridge(private val context: Context) {
    private fun python(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance()
    }

    fun inspectPlugin(source: String): PythonInspection = runCatching {
        val json = python().getModule("streamlink_bridge")
            .callAttr("inspect_plugin", source).toJava(String::class.java)
        val values = JSONObject(json)
        PythonInspection(
            valid = values.optBoolean("valid"),
            classes = values.optJSONArray("classes").toStringList(),
            domains = values.optJSONArray("domains").toStringList()
        )
    }.getOrElse { PythonInspection(false, emptyList(), emptyList(), it.message ?: "Error de Python") }

    fun runtimeStatus(): String = runCatching {
        val json = python().getModule("streamlink_bridge")
            .callAttr("runtime_status").toJava(String::class.java)
        val values = JSONObject(json)
        if (values.optBoolean("available")) "Streamlink disponible"
        else "Streamlink no disponible: ${values.optString("error")}"
    }.getOrElse { "Python no disponible: ${it.message}" }

    fun resolve(inputUrl: String, pluginDirectory: String, quality: String): JSONObject {
        val json = python().getModule("streamlink_bridge")
            .callAttr("resolve", inputUrl, pluginDirectory, quality)
            .toJava(String::class.java)
        return JSONObject(json)
    }

    private fun org.json.JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
