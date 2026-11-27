package com.juhao.murexide.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

object PluginSession {
    data class PendingDialog(val pluginId: String, val action: PluginAction.Alert)

    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()
    private val _dialogs = MutableSharedFlow<PendingDialog>(extraBufferCapacity = 16)
    val dialogs = _dialogs.asSharedFlow()
    private val _pageTick = MutableStateFlow(0)
    val pageTick: StateFlow<Int> = _pageTick.asStateFlow()
    private val globalVars = ConcurrentHashMap<String, Map<String, Any?>>()
    private val pendingDialogs = ConcurrentHashMap<String, PluginAction.Alert>()
    private val dialogCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private val dialogDisposals = ConcurrentHashMap<String, () -> Unit>()

    fun bumpPage() { _pageTick.update { it + 1 } }

    fun update(pluginList: List<Plugin>) {
        _plugins.value = pluginList
        _revision.update { it + 1 }
        bumpPage()
    }

    fun allPlugins(): List<Plugin> = _plugins.value

    fun setVar(pluginId: String, key: String, value: Any?) {
        globalVars.compute(pluginId) { _, previous ->
            (previous.orEmpty() + (key to value)).filterValues { it != null }
        }
        bumpPage()
    }

    fun getVar(pluginId: String, key: String): Any? = globalVars[pluginId]?.get(key)
    fun varsFor(pluginId: String): Map<String, Any?> = globalVars[pluginId].orEmpty()
    fun findPlugin(pluginId: String): Plugin? = _plugins.value.firstOrNull { it.manifest.id == pluginId }

    fun clearPlugin(pluginId: String) {
        globalVars.remove(pluginId)
        dismissPendingDialog(pluginId)
        bumpPage()
    }

    @Synchronized
    fun emitDialog(pluginId: String, action: PluginAction.Alert, onConfirm: (() -> Unit)? = null, onDispose: (() -> Unit)? = null) {
        dialogDisposals.remove(pluginId)?.invoke()
        if (onDispose != null) dialogDisposals[pluginId] = onDispose
        pendingDialogs[pluginId] = action
        if (onConfirm == null) dialogCallbacks.remove(pluginId) else dialogCallbacks[pluginId] = onConfirm
        _dialogs.tryEmit(PendingDialog(pluginId, action))
    }

    @Synchronized
    fun confirmPendingDialog(context: Context, pluginId: String): Boolean {
        val action = pendingDialogs.remove(pluginId) ?: return false
        val callback = dialogCallbacks.remove(pluginId)
        val dispose = dialogDisposals.remove(pluginId)
        if (callback != null) callback() else PluginScripts.enqueue(context, pluginId) {
            PluginExecutor.execute(context, pluginId, action.onConfirm)
        }
        dispose?.invoke()
        return true
    }

    @Synchronized
    fun dismissPendingDialog(pluginId: String) {
        pendingDialogs.remove(pluginId)
        dialogCallbacks.remove(pluginId)
        dialogDisposals.remove(pluginId)?.invoke()
    }
}
