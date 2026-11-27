package com.juhao.murexide.plugin

import android.content.Context
import com.juhao.murexide.datastore.AccountStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PluginContext(
    private val context: Context,
    private val pluginId: String,
    private val page: String? = null,
    private val event: String? = null,
    private val extras: Map<String, Any?> = emptyMap()
) {
    fun toEnv(): LinkedHashMap<String, Any?> {
        val env = LinkedHashMap<String, Any?>()
        env["event"] = event
        env["page"] = page
        env["plugin.id"] = pluginId
        PluginManager.manifestById(pluginId)?.let {
            env["plugin.name"] = it.name
            env["plugin.author"] = it.author
            env["plugin.version"] = it.version
        }
        env["network.online"] = PluginSystem.isNetworkOnline(context)
        env["ws.connected"] = PluginSystem.isWsConnected()

        val permissions = PluginSession.findPlugin(pluginId)?.manifest?.permissions.orEmpty()
        if (PluginPermissions.ALL in permissions || PluginPermissions.READ_ACCOUNT in permissions) {
            val account = runCatching {
                runBlocking { AccountStorage.getInstance(context).currentAccountFlow.first() }
            }.getOrNull()
            env["account.loggedIn"] = account != null
            if (account != null) {
                env["account.id"] = account.id
                env["account.username"] = account.username
                env["account.avatar"] = account.avatar
            }
        }

        val now = Date()
        env["time.now"] = now.time
        env["time.date"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        env["time.time"] = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
        env["time.datetime"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)

        runCatching {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            env["app.version"] = pkg.versionName
            env["app.versionCode"] = pkg.longVersionCode
            env["app.package"] = context.packageName
        }

        extras.forEach { (key, value) ->
            env[key] = value
            env["extra.$key"] = value
        }
        PluginSession.varsFor(pluginId).forEach { (key, value) -> env["var.$key"] = value }
        return env
    }
}
