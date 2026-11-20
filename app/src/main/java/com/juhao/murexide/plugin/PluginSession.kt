package com.juhao.murexide.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object PluginSession {

    data class PendingDialog(val pluginId: String, val action: PluginAction.Alert)
    data class ActiveDecorate(
        val pluginId: String,
        val action: PluginAction.Decorate,
        val env: Map<String, Any?>
    )

    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins.asStateFlow()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _dialogs = MutableSharedFlow<PendingDialog>(extraBufferCapacity = 16)
    val dialogs = _dialogs.asSharedFlow()

    private val _decorateRules = MutableStateFlow<List<Pair<String, PluginAction.Decorate>>>(emptyList())
    val decorateRules = _decorateRules.asStateFlow()

    private val _activeDecorates = MutableStateFlow<List<ActiveDecorate>>(emptyList())
    val activeDecorates = _activeDecorates.asStateFlow()

    private val globalVars = ConcurrentHashMap<String, Any?>()
    private val pendingDialogs = ConcurrentHashMap<String, PluginAction.Alert>()

    fun update(pluginList: List<Plugin>) {
        _plugins.value = pluginList
        _revision.value++
        val allDecorates = mutableListOf<Pair<String, PluginAction.Decorate>>()
        for (p in pluginList) {
            for (d in p.decorates) allDecorates += p.manifest.id to d
        }
        _decorateRules.value = allDecorates
    }

    fun updateActiveDecorates(list: List<ActiveDecorate>) {
        _activeDecorates.value = list
    }

    fun allPlugins(): List<Plugin> = _plugins.value

    fun rulesFor(pageClass: String?, event: String): List<Pair<String, PluginRule>> {
        val out = mutableListOf<Pair<String, PluginRule>>()
        for (p in _plugins.value) {
            for (r in p.rules) {
                if (r.event == event && (r.pages.isEmpty() || pageClass != null && r.pages.any { it == "*" || it == pageClass })) {
                    out += p.manifest.id to r
                }
            }
        }
        return out
    }

    fun appRulesFor(event: String): List<Pair<String, PluginRule>> {
        val out = mutableListOf<Pair<String, PluginRule>>()
        for (p in _plugins.value) {
            for (r in p.global) {
                if (r.event == event) out += p.manifest.id to r
            }
        }
        return out
    }

    fun setVar(pluginId: String, key: String, value: Any?) {
        globalVars["$pluginId.$key"] = value
    }

    fun getVar(pluginId: String, key: String): Any? = globalVars["$pluginId.$key"]

    fun anyVar(key: String): Any? = globalVars[key]

    fun findPlugin(pluginId: String): Plugin? =
        _plugins.value.firstOrNull { it.manifest.id == pluginId }

    fun emitDialog(pluginId: String, action: PluginAction.Alert) {
        pendingDialogs[pluginId] = action
        _dialogs.tryEmit(PendingDialog(pluginId, action))
    }

    fun confirmPendingDialog(context: Context, pluginId: String): Boolean {
        val action = pendingDialogs.remove(pluginId) ?: return false
        PluginExecutor.execute(context, pluginId, action.onConfirm)
        return true
    }

    fun dismissPendingDialog(pluginId: String) {
        pendingDialogs.remove(pluginId)
    }
}