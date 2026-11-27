package com.juhao.murexide.plugin

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

class LuaPluginDataStorage private constructor(private val root: File) {
    private fun dataFile(pluginId: String): File {
        require(pluginId.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]*"))) { "非法插件 id" }
        return File(root, "$pluginId/data.json")
    }

    @Synchronized
    fun get(pluginId: String, name: String): String? = readValue(pluginId, name)?.toString()

    @Synchronized
    fun set(pluginId: String, name: String, value: String) = writeValue(pluginId, name, value)

    @Synchronized
    fun readValue(pluginId: String, name: String): Any? = decode(read(dataFile(pluginId)).opt(name))

    @Synchronized
    fun values(pluginId: String): Map<String, Any?> {
        val obj = read(dataFile(pluginId))
        return obj.keys().asSequence().associateWith { decode(obj.opt(it)) }
    }

    @Synchronized
    fun writeValue(pluginId: String, name: String, value: Any?) {
        val file = dataFile(pluginId)
        file.parentFile?.mkdirs()
        val obj = read(file)
        if (value == null) obj.remove(name) else obj.put(name, encode(value))
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(obj.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun clear(pluginId: String, name: String) = writeValue(pluginId, name, null)

    fun sizeOf(pluginId: String): Long = dataFile(pluginId).let { if (it.exists()) it.length() else 0L }

    private fun read(file: File): JSONObject {
        val atomic = AtomicFile(file)
        val stream = try {
            atomic.openRead()
        } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) throw error
            return JSONObject()
        }
        return JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun encode(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is List<*> -> JSONArray().also { array -> value.forEach { array.put(encode(it)) } }
        is Map<*, *> -> JSONObject().also { obj -> value.forEach { (key, item) -> obj.put(key.toString(), encode(item)) } }
        is String, is Boolean, is Int, is Long, is Float, is Double -> value
        else -> error("不支持的数据类型: ${value::class.simpleName}")
    }

    private fun decode(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONArray -> (0 until value.length()).map { decode(value.opt(it)) }
        is JSONObject -> value.keys().asSequence().associateWith { decode(value.opt(it)) }
        else -> value
    }

    companion object {
        @Volatile private var instance: LuaPluginDataStorage? = null
        fun getInstance(context: Context): LuaPluginDataStorage = instance ?: synchronized(this) {
            instance ?: LuaPluginDataStorage(File(context.filesDir, "lua_plugins")).also { instance = it }
        }
    }
}
