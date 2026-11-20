package com.juhao.murexide.plugin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LuaPluginDataStorage private constructor(private val context: Context) {

    private fun key(pluginId: String, name: String) =
        stringPreferencesKey("${sanitize(pluginId)}:$name")

    suspend fun get(pluginId: String, name: String): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            prefs[key(pluginId, name)]
        }
    }

    suspend fun set(pluginId: String, name: String, value: String) {
        context.dataStore.edit { prefs ->
            prefs[key(pluginId, name)] = value
        }
    }

    suspend fun clear(pluginId: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(key(pluginId, name))
        }
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("[^\\w.-]"), "_")

    companion object {
        @Volatile private var instance: LuaPluginDataStorage? = null
        fun getInstance(context: Context): LuaPluginDataStorage =
            instance ?: synchronized(this) {
                instance ?: LuaPluginDataStorage(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}

private val Context.dataStore by preferencesDataStore(name = "lua_plugin_data")