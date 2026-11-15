package com.juhao.murexide.plugin

import android.content.Context
import android.os.Build
import com.juhao.murexide.datastore.AccountStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class PluginContext(
    val context: Context,
    val pluginId: String,
    val pageId: String? = null,
    val event: String? = null,
    extras: Map<String, Any?> = emptyMap()
) {
    private val vars = ConcurrentHashMap<String, Any?>()
    private val extraMap = ConcurrentHashMap<String, Any?>(extras)
    private val storage = PluginStorage(context, pluginId)

    private data class AccountSnapshot(
        val id: String? = null,
        val username: String? = null,
        val avatar: String? = null,
        val loggedIn: Boolean = false
    )

    @Volatile
    private var accountSnapshot: AccountSnapshot = AccountSnapshot()

    @Volatile
    private var accountLoaded: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            runCatching {
                val acc = AccountStorage.getInstance(context).currentAccountFlow.first()
                if (acc != null) {
                    accountSnapshot = AccountSnapshot(acc.id, acc.username, acc.avatar, true)
                }
                accountLoaded = true
            }
        }
    }

    private fun ensureAccountLoaded() {
        if (accountLoaded) return
        runCatching {
            val acc = runBlocking {
                AccountStorage.getInstance(context).currentAccountFlow.first()
            }
            if (acc != null) {
                accountSnapshot = AccountSnapshot(acc.id, acc.username, acc.avatar, true)
            }
            accountLoaded = true
        }
    }

    fun setVar(key: String, value: Any?) {
        vars[key] = value
    }

    fun getVar(key: String): Any? = vars[key] ?: extraMap[key]

    fun setKv(key: String, value: Any?, persist: Boolean) {
        if (persist) storage.put(key, value) else vars["kv.$key"] = value
    }

    fun getKv(key: String): Any? = storage.get(key) ?: vars["kv.$key"]

    fun resolve(path: String): Any? {
        if (path.startsWith("account.")) {
            ensureAccountLoaded()
        }
        return when {
            path.startsWith("page.") -> when (path.removePrefix("page.")) {
                "id" -> pageId
                "name" -> pageId?.substringAfterLast('.')
                else -> null
            }
            path == "event" -> event
            path.startsWith("plugin.") -> when (path.removePrefix("plugin.")) {
                "id" -> pluginId
                "dir" -> context.filesDir.resolve("plugins/$pluginId").absolutePath
                "version" -> PluginManager.manifestById(pluginId)?.version
                "name" -> PluginManager.manifestById(pluginId)?.name
                "author" -> PluginManager.manifestById(pluginId)?.author
                "description" -> PluginManager.manifestById(pluginId)?.description
                else -> null
            }
            path.startsWith("account.") -> when (path.removePrefix("account.")) {
                "id" -> accountSnapshot.id
                "username" -> accountSnapshot.username
                "avatar" -> accountSnapshot.avatar
                "loggedIn" -> accountSnapshot.loggedIn
                "token" -> null
                else -> null
            }
            path.startsWith("time.") -> when (path.removePrefix("time.")) {
                "now" -> System.currentTimeMillis()
                "date" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                "time" -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                "datetime" -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                else -> null
            }
            path.startsWith("app.") -> when (path.removePrefix("app.")) {
                "version" -> appVersionName()
                "versionCode" -> appVersionCode()
                "sdk" -> Build.VERSION.SDK_INT
                "package" -> context.packageName
                else -> null
            }
            path.startsWith("network.") -> when (path.removePrefix("network.")) {
                "online" -> PluginSystem.isNetworkOnline(context)
                else -> null
            }
            path.startsWith("ws.") -> when (path.removePrefix("ws.")) {
                "connected" -> PluginSystem.isWsConnected()
                else -> null
            }
            path.startsWith("var.") -> vars[path.removePrefix("var.")]
            path.startsWith("kv.") -> storage.get(path.removePrefix("kv.")) ?: vars["kv.${path.removePrefix("kv.")}"]
            path.startsWith("extra.") -> extraMap[path.removePrefix("extra.")]
            else -> vars[path] ?: extraMap[path]
        }
    }

    private fun appVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    private fun appVersionCode(): Int? = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }.getOrNull()

    fun interpolate(text: String?): String? {
        if (text == null) return null
        return INTERPOLATE_REGEX.replace(text) { m ->
            val key = m.groupValues[1]
            (resolve(key) ?: m.value).toString()
        }
    }

    companion object {
        private val INTERPOLATE_REGEX = Regex("\\{\\{\\s*([\\w.]+)\\s*\\}\\}")
    }
}