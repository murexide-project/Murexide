package com.juhao.murexide.plugin

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PluginLogger {
    private const val MAX_LOG_SIZE = 256 * 1024L

    fun log(context: Context, pluginId: String, level: String, message: String) {
        runCatching {
            val dir = File(context.filesDir, "lua_plugins/$pluginId")
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
        val file = File(context.filesDir, "lua_plugins/$pluginId/logs.txt")
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context, pluginId: String) {
        File(context.filesDir, "lua_plugins/$pluginId/logs.txt").delete()
    }
}