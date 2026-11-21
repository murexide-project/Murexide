package com.juhao.murexide.plugin

import android.app.Activity
import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

object PluginDispatcher {

    private val pageRefreshListeners =
        CopyOnWriteArrayList<(Activity, String, Map<String, Any?>) -> Unit>()

    fun addPageRefreshListener(l: (Activity, String, Map<String, Any?>) -> Unit) {
        if (!pageRefreshListeners.contains(l)) pageRefreshListeners += l
    }

    fun removePageRefreshListener(l: (Activity, String, Map<String, Any?>) -> Unit) {
        pageRefreshListeners -= l
    }

    fun dispatchAppEvent(context: Context, event: String, extras: Map<String, Any?> = emptyMap()) {
        for ((pluginId, rule) in PluginSession.appRulesFor(event)) {
            val pluginContext = PluginContext(context, pluginId, event = event, extras = extras)
            val env = pluginContext.toEnv(extras)
            PluginExecutor.execute(context, pluginId, rule.actions, env)
        }
        PluginSession.update(PluginSession.allPlugins())
        evaluateDecorates(context, null, event, extras)
        val act = PluginActivityTracker.currentActivity()
        if (act != null) {
            for (l in pageRefreshListeners) {
                runCatching { l(act, event, extras) }
            }
        }
    }

    fun dispatchPageEvent(context: Context, pageClass: String, event: String) {
        for ((pluginId, rule) in PluginSession.rulesFor(pageClass, event)) {
            val pluginContext = PluginContext(context, pluginId, uri = pageClass, event = event)
            val env = pluginContext.toEnv()
            PluginExecutor.execute(context, pluginId, rule.actions, env)
        }
        PluginSession.update(PluginSession.allPlugins())
        evaluateDecorates(context, pageClass, event, emptyMap())
    }

    fun dispatchActivityEvent(activity: Activity, event: String, extras: Map<String, Any?> = emptyMap()) {
        val pageClass = activity.javaClass.name
        for ((pluginId, rule) in PluginSession.rulesFor(pageClass, event)) {
            val pluginContext = PluginContext(activity, pluginId, uri = pageClass, event = event, extras = extras)
            val env = pluginContext.toEnv(extras)
            PluginExecutor.execute(activity, pluginId, rule.actions, env)
        }
        PluginSession.update(PluginSession.allPlugins())
        evaluateDecorates(activity, pageClass, event, extras)
    }

    private fun evaluateDecorates(
        context: Context,
        pageClass: String?,
        event: String,
        extras: Map<String, Any?>
    ) {
        val active = mutableListOf<PluginSession.ActiveDecorate>()
        for ((pluginId, rule) in PluginSession.decorateRules.value) {
            if (pageClass != null && rule.page != "*" && rule.page != pageClass) continue
            if (pageClass == null && rule.page != "*") continue

            val ctx = PluginContext(context, pluginId, uri = pageClass, event = event, extras = extras)
            val env = ctx.toEnv(extras)

            val fieldVal = if (rule.whenField != null) {
                env[rule.whenField]?.toString()
                    ?: PluginSession.getVar(pluginId, rule.whenField.removePrefix("var."))?.toString()
            } else null

            val match = when {
                rule.whenField == null -> true
                fieldVal == null -> false
                rule.whenEquals == null -> fieldVal.isNotBlank()
                else -> fieldVal == rule.whenEquals
            }
            if (match) {
                active += PluginSession.ActiveDecorate(pluginId, rule, env)
            }
        }
        PluginSession.updateActiveDecorates(active)
    }
}