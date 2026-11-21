package com.juhao.murexide.plugin

import android.content.Context
import org.json.JSONObject
import java.io.File

class LuaPluginDataStorage private constructor(private val root: File) {

    private fun dataFile(pluginId: String): File = File(root, "$pluginId/data.json")

    @Synchronized
    fun get(pluginId: String, name: String): String? {
        val obj = read(dataFile(pluginId)) ?: return null
        return when (val v = obj.opt(name)) {
            null -> null
            JSONObject.NULL -> null
            else -> v.toString()
        }
    }

    @Synchronized
    fun set(pluginId: String, name: String, value: String) {
        val file = dataFile(pluginId)
        file.parentFile?.mkdirs()
        val obj = read(file) ?: JSONObject()
        obj.put(name, value)
        write(file, obj)
    }

    @Synchronized
    fun clear(pluginId: String, name: String) {
        val file = dataFile(pluginId)
        val obj = read(file) ?: return
        if (!obj.has(name)) return
        obj.remove(name)
        write(file, obj)
    }

    fun sizeOf(pluginId: String): Long {
        val file = dataFile(pluginId)
        return if (file.exists()) file.length() else 0L
    }

    private fun read(file: File): JSONObject? {
        if (!file.exists()) return null
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun write(file: File, obj: JSONObject) {
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(obj.toString(2))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        @Volatile private var instance: LuaPluginDataStorage? = null
        fun getInstance(context: Context): LuaPluginDataStorage =
            instance ?: synchronized(this) {
                instance ?: LuaPluginDataStorage(
                    File(context.filesDir, "lua_plugins")
                ).also { instance = it }
            }
    }
}