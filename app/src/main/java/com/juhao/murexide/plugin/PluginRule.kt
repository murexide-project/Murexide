package com.juhao.murexide.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.juhao.murexide.network.NetworkClient
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

sealed class PluginAction {
    data class Toast(val text: String, val type: String) : PluginAction()
    data class Alert(
        val title: String,
        val message: String,
        val confirmText: String,
        val cancelText: String,
        val onConfirm: List<PluginAction>
    ) : PluginAction()
    data class Notify(val title: String, val message: String) : PluginAction()
    data class ReadData(val key: String, val target: String) : PluginAction()
    data class WriteData(val key: String, val value: String) : PluginAction()
    data class Copy(val text: String) : PluginAction()
    data class OpenUrl(val url: String) : PluginAction()
    data class Http(
        val method: String,
        val url: String,
        val body: String,
        val headers: Map<String, String>,
        val saveTo: String
    ) : PluginAction()
    data class If(
        val field: String,
        val isEquals: String,
        val thenActions: List<PluginAction>,
        val elseActions: List<PluginAction>
    ) : PluginAction()
    data class Decorate(
        val page: String,
        val position: String,
        val whenField: String?,
        val whenEquals: String?,
        val widget: PluginWidget
    ) : PluginAction()
}

data class PluginWidget(
    val type: String,
    val content: String? = null,
    val icon: String? = null,
    val size: Float? = null,
    val color: String? = null,
    val background: String? = null,
    val width: String? = null,
    val height: String? = null,
    val padding: Float? = null,
    val paddingH: Float? = null,
    val paddingV: Float? = null,
    val align: String? = null,
    val children: List<PluginWidget> = emptyList(),
    val onClick: List<PluginAction> = emptyList()
)

data class PluginPage(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String? = null,
    val page: PluginWidget
)

data class PluginRule(
    val event: String,
    val pages: List<String>,
    val actions: List<PluginAction>
)

class PluginManifest(
    val id: String,
    val name: String,
    val author: String?,
    val version: Int,
    val enabled: Boolean,
    val permissions: Set<String> = emptySet()
)

class Plugin(
    val manifest: PluginManifest,
    val rules: List<PluginRule>,
    val pages: List<PluginPage>,
    val global: List<PluginRule>,
    val decorates: List<PluginAction.Decorate>
)

object PluginExecutor {

    fun execute(
        context: Context,
        pluginId: String,
        actions: List<PluginAction>,
        env: Map<String, Any?> = emptyMap()
    ): Boolean = runBlocking(Dispatchers.IO) {
        var ok = true
        for (a in actions) {
            if (!execSuspend(context, pluginId, a, env)) ok = false
        }
        ok
    }

    private fun requiredPermission(action: PluginAction): String? = when (action) {
        is PluginAction.Notify -> PluginPermissions.NOTIFICATION
        is PluginAction.Copy -> PluginPermissions.CLIPBOARD
        is PluginAction.OpenUrl -> PluginPermissions.OPEN_URL
        is PluginAction.Http -> PluginPermissions.NETWORK
        is PluginAction.WriteData, is PluginAction.ReadData -> PluginPermissions.STORAGE
        else -> null
    }

    private fun checkPermission(context: Context, pluginId: String, action: PluginAction): Boolean {
        val perms = PluginSession.findPlugin(pluginId)?.manifest?.permissions ?: emptySet()
        if (perms.contains(PluginPermissions.ALL)) return true
        val need = requiredPermission(action) ?: return true
        if (perms.contains(need)) return true
        PluginLogger.warn(context, pluginId, "permission denied: $need for ${action::class.simpleName}")
        return false
    }

    private suspend fun execSuspend(
        context: Context,
        pluginId: String,
        action: PluginAction,
        env: Map<String, Any?>
    ): Boolean {
        if (!checkPermission(context, pluginId, action)) return false
        return try {
            when (action) {
                is PluginAction.Toast -> {
                    Toast.makeText(
                        context,
                        interpolate(action.text, env, pluginId),
                        if (action.type == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                is PluginAction.Alert -> {
                    PluginSession.emitDialog(pluginId, action)
                    true
                }
                is PluginAction.Notify -> {
                    PluginNotification.post(
                        context, pluginId,
                        interpolate(action.title, env, pluginId),
                        interpolate(action.message, env, pluginId)
                    )
                    true
                }
                is PluginAction.ReadData -> {
                    val v = LuaPluginDataStorage.getInstance(context).get(pluginId, action.key).first()
                    val target = action.target.removePrefix("var.")
                    PluginSession.setVar(pluginId, target, v)
                    true
                }
                is PluginAction.WriteData -> {
                    LuaPluginDataStorage.getInstance(context).set(pluginId, action.key, interpolate(action.value, env, pluginId))
                    true
                }
                is PluginAction.Copy -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("plugin", interpolate(action.text, env, pluginId)))
                    true
                }
                is PluginAction.OpenUrl -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(interpolate(action.url, env, pluginId))).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }.isSuccess
                }
                is PluginAction.Http -> {
                    doHttp(context, pluginId, action, env)
                    true
                }
                is PluginAction.If -> {
                    val fromEnv = env[action.field]?.toString()
                    val fromVar = if (action.field.startsWith("var.")) {
                        PluginSession.getVar(pluginId, action.field.removePrefix("var."))?.toString()
                    } else {
                        null
                    }
                    val f = fromEnv ?: fromVar ?: ""
                    val m = interpolate(action.isEquals, env, pluginId)
                    val chosen = if (f == m) action.thenActions else action.elseActions
                    for (a in chosen) execSuspend(context, pluginId, a, env)
                    true
                }
                is PluginAction.Decorate -> true
            }
        } catch (t: Throwable) {
            PluginLogger.error(context, pluginId, "action failed: ${t.message}")
            false
        }
    }

    private fun doHttp(
        context: Context,
        pluginId: String,
        action: PluginAction.Http,
        env: Map<String, Any?>
    ) {
        val url = interpolate(action.url, env, pluginId)
        val bodyStr = interpolate(action.body, env, pluginId)
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                try {
                    val body: RequestBody? =
                        if (action.method.uppercase() == "GET") null
                        else bodyStr.toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url(url)
                        .method(action.method.uppercase(), body)
                        .apply { action.headers.forEach { (k, v) -> addHeader(k, interpolate(v, env, pluginId)) } }
                        .build()
                    NetworkClient.okHttpClient.newCall(req).execute().use { resp ->
                        val text = resp.body.string()
                        if (action.saveTo.isNotBlank()) PluginSession.setVar(pluginId, action.saveTo.removePrefix("var."), text)
                    }
                } catch (t: Throwable) {
                    PluginLogger.error(context, pluginId, "http failed: ${t.message}")
                }
            }
        }
    }

    fun interpolate(text: String, env: Map<String, Any?>, pluginId: String): String =
        REGEX.replace(text) { m ->
            val key = m.groupValues[1]
            when {
                key.startsWith("var.") -> {
                    env[key]?.toString()
                        ?: PluginSession.getVar(pluginId, key.removePrefix("var."))?.toString()
                        ?: m.value
                }
                else -> env[key]?.toString() ?: m.value
            }
        }

    private val REGEX = Regex("\\{\\{\\s*([\\w.]+)\\s*\\}\\}")

    fun str(v: Any?, def: String = ""): String = (v as? LuaValue.Str)?.value ?: def
    fun text(t: LuaValue): String = (t as? LuaValue.Str)?.value ?: ""
}