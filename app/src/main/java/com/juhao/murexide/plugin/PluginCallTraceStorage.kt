package com.juhao.murexide.plugin

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue

internal data class PluginCallTrace(
    val id: Long,
    val function: String,
    val timestamp: Long
)

internal class PluginCallTraceStorage private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "plugin_call_traces.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE calls (id INTEGER PRIMARY KEY AUTOINCREMENT, plugin_id TEXT NOT NULL, function_name TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX calls_plugin_id ON calls(plugin_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun record(pluginId: String, function: String) {
        val timestamp = System.currentTimeMillis() / 1000L
        runCatching {
            writableDatabase.insertOrThrow("calls", null, ContentValues().apply {
                put("plugin_id", pluginId)
                put("function_name", function)
                put("timestamp", timestamp)
            })
        }.onFailure { Log.e("PluginCallTrace", "Failed to record $function for $pluginId", it) }
    }

    fun read(pluginId: String, beforeId: Long = Long.MAX_VALUE, limit: Int = 100): List<PluginCallTrace> {
        return readableDatabase.query(
            "calls",
            arrayOf("id", "function_name", "timestamp"),
            "plugin_id = ? AND id < ?",
            arrayOf(pluginId, beforeId.toString()),
            null,
            null,
            "id DESC",
            limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(PluginCallTrace(cursor.getLong(0), cursor.getString(1), cursor.getLong(2)))
                }
            }
        }
    }

    fun clear(pluginId: String) {
        writableDatabase.delete("calls", "plugin_id = ?", arrayOf(pluginId))
    }

    companion object {
        @Volatile private var instance: PluginCallTraceStorage? = null

        fun getInstance(context: Context): PluginCallTraceStorage = instance ?: synchronized(this) {
            instance ?: PluginCallTraceStorage(context).also { instance = it }
        }
    }
}

internal fun LuaState.registerTraced(
    context: Context,
    pluginId: String,
    name: String,
    function: (List<LuaValue>) -> List<LuaValue>
) {
    register(name) { args ->
        PluginCallTraceStorage.getInstance(context).record(pluginId, name)
        function(args)
    }
}
