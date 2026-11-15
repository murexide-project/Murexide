package com.juhao.murexide.plugin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PluginLogger {
    private const val MAX_LOG_SIZE = 256 * 1024L

    fun log(context: Context, pluginId: String, level: String, message: String) {
        runCatching {
            val dir = File(context.filesDir, "plugins/$pluginId")
            if (!dir.exists()) return
            val file = File(dir, "logs.txt")
            if (file.exists() && file.length() > MAX_LOG_SIZE) file.writeText("")
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            file.appendText("[$time][$level] $message\n")
        }
    }

    fun info(context: Context, pluginId: String, message: String) = log(context, pluginId, "I", message)
    fun warn(context: Context, pluginId: String, message: String) = log(context, pluginId, "W", message)
    fun error(context: Context, pluginId: String, message: String) = log(context, pluginId, "E", message)

    fun read(context: Context, pluginId: String): String {
        val file = File(context.filesDir, "plugins/$pluginId/logs.txt")
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context, pluginId: String) {
        File(context.filesDir, "plugins/$pluginId/logs.txt").delete()
    }
}

class PluginStorage(private val context: Context, private val pluginId: String) {
    private val dir: File by lazy {
        File(context.filesDir, "plugins/$pluginId/data").apply { mkdirs() }
    }
    private val file: File by lazy { File(dir, "storage.json") }

    @Synchronized
    private fun load(): JSONObject = runCatching {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrDefault(JSONObject())

    @Synchronized
    private fun save(obj: JSONObject) {
        runCatching { file.writeText(obj.toString(2)) }
    }

    fun put(key: String, value: Any?) {
        val obj = load()
        when (value) {
            null -> obj.remove(key)
            is JSONObject -> obj.put(key, value)
            is JSONArray -> obj.put(key, value)
            is Map<*, *> -> obj.put(key, JSONObject(value as Map<*, *>))
            is List<*> -> obj.put(key, JSONArray(value))
            else -> obj.put(key, value)
        }
        save(obj)
    }

    fun get(key: String): Any? {
        val obj = load()
        return if (obj.has(key)) obj.get(key) else null
    }

    fun remove(key: String) {
        val obj = load()
        obj.remove(key)
        save(obj)
    }

    fun clear() {
        runCatching { file.delete() }
    }

    fun size(): Long = if (file.exists()) file.length() else 0L
}