package com.juhao.murexide.plugin

import android.content.Context
import com.juhao.murexide.datastore.AccountStorage
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PluginContext(
    val context: Context,
    val pluginId: String,
    val uri: String? = null,
    val event: String? = null,
    extras: Map<String, Any?> = emptyMap()
) {
    private val vars = ConcurrentHashMap<String, Any?>()
    private val extraMap = ConcurrentHashMap<String, Any?>(extras)

    private val storage by lazy { LuaPluginDataStorage.getInstance(context) }

    fun putLua(table: LuaValue.Table) {
        for ((k, v) in table.toMap()) {
            val key = (k as? LuaValue.Str)?.value ?: k.toString()
            vars[key] = luaToKotlin(v)
        }
    }

    fun luaToKotlin(v: LuaValue?): Any? = when (v) {
        null -> null
        is LuaValue.Str -> v.value
        is LuaValue.Integer -> v.value
        is LuaValue.Number -> v.value
        else -> v.toString()
    }

    fun setVar(key: String, value: Any?) {
        vars[key] = value
    }

    fun getVar(key: String): Any? = vars[key] ?: extraMap[key]

    fun setKv(key: String, value: String) {
        storage.set(pluginId, key, value)
    }

    fun getKv(key: String): String? = storage.get(pluginId, key)

    fun resolve(path: String): Any? {
        return when {
            path.startsWith("page.") -> when (path.removePrefix("page.")) {
                "id" -> uri
                else -> null
            }
            path == "event" -> event
            path.startsWith("plugin.") -> when (path.removePrefix("plugin.")) {
                "id" -> pluginId
                "name" -> PluginManager.manifestById(pluginId)?.name
                "version" -> PluginManager.manifestById(pluginId)?.version
                else -> null
            }
            path.startsWith("account.") -> accountValue(path.removePrefix("account."))
            path.startsWith("time.") -> timeValue(path.removePrefix("time."))
            path.startsWith("app.") -> appValue(path.removePrefix("app."))
            path.startsWith("network.") -> when (path.removePrefix("network.")) {
                "online" -> PluginSystem.isNetworkOnline(context)
                else -> null
            }
            path.startsWith("ws.") -> when (path.removePrefix("ws.")) {
                "connected" -> PluginSystem.isWsConnected()
                else -> null
            }
            path.startsWith("var.") -> vars[path.removePrefix("var.")]
                    ?: PluginSession.getVar(pluginId, path.removePrefix("var."))
            path.startsWith("kv.") -> getKv(path.removePrefix("kv."))
            path.startsWith("extra.") -> extraMap[path.removePrefix("extra.")]
            else -> extraMap[path] ?: vars[path]
        }
    }

    private fun accountValue(key: String): Any? {
        val acc = account()
        return when (key) {
            "id" -> acc?.id
            "username" -> acc?.username
            "avatar" -> acc?.avatar
            "loggedIn" -> acc != null
            else -> null
        }
    }

    private fun account() = runCatching {
        runBlocking { AccountStorage.getInstance(context).currentAccountFlow.first() }
    }.getOrNull()

    private fun timeValue(key: String): Any? {
        val now = Date()
        return when (key) {
            "now" -> System.currentTimeMillis()
            "date" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
            "time" -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            "datetime" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
            else -> null
        }
    }

    private fun appValue(key: String): Any? = when (key) {
        "version" -> runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        "versionCode" -> runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrNull()
        "package" -> context.packageName
        else -> null
    }

    fun toEnv(extra: Map<String, Any?> = emptyMap()): LinkedHashMap<String, Any?> {
        val env = LinkedHashMap<String, Any?>()
        env["event"] = event
        env["page"] = uri
        env["plugin.id"] = pluginId
        PluginManager.manifestById(pluginId)?.let {
            env["plugin.name"] = it.name
            env["plugin.author"] = it.author
            env["plugin.version"] = it.version
        }
        env["network.online"] = PluginSystem.isNetworkOnline(context)
        env["ws.connected"] = PluginSystem.isWsConnected()

        val acc = account()
        if (acc != null) {
            env["account.id"] = acc.id
            env["account.username"] = acc.username
            env["account.avatar"] = acc.avatar
            env["account.loggedIn"] = true
        } else {
            env["account.loggedIn"] = false
        }

        val now = Date()
        env["time.now"] = System.currentTimeMillis()
        env["time.date"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        env["time.time"] = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
        env["time.datetime"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)

        runCatching {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            env["app.version"] = pkg.versionName
            env["app.versionCode"] = pkg.longVersionCode
            env["app.package"] = context.packageName
        }

        extra.forEach { (k, v) -> env["$k"] = v }
        extra.forEach { (k, v) -> env["extra.$k"] = v }
        vars.forEach { (k, v) -> env["var.$k"] = v }
        PluginSession.varsFor(pluginId).forEach { (k, v) -> env["var.$k"] = v }
        return env
    }
}