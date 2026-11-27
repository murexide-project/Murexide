package com.juhao.murexide.plugin

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.juhao.murexide.network.WebSocketManager.WsEvent
import com.juhao.murexide.ui.lock.GateActivity
import java.lang.ref.WeakReference

object PluginDispatcher {
    fun dispatchMessageEvent(context: Context, event: WsEvent) {
        val (type, message) = when (event) {
            is WsEvent.NewMessage -> "new" to event.message
            is WsEvent.EditMessage -> "edit" to event.message
            is WsEvent.MessageDeleted -> "recall" to event.message
            else -> return
        }
        dispatchAppEvent(context, "ws.message", mapOf(
            "type" to type,
            "msgId" to message.msgId,
            "chatId" to message.chatId,
            "chatType" to message.chatType,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "content" to message.content,
            "contentType" to message.contentType,
            "timestamp" to message.timestamp
        ))
    }

    fun dispatchAppEvent(context: Context, event: String, extras: Map<String, Any?> = emptyMap()) {
        val activity = PluginActivityTracker.currentActivity()
        val state = when (event) {
            "ws.connected" -> mapOf("connected" to true, "ws.connected" to true)
            "ws.disconnected" -> mapOf("connected" to false, "ws.connected" to false)
            "network.online" -> mapOf("online" to true, "network.online" to true)
            "network.offline" -> mapOf("online" to false, "network.online" to false)
            else -> emptyMap()
        }
        dispatch(context, activity?.javaClass?.name, event, state + extras, activity)
    }

    fun dispatchActivityEvent(activity: Activity, event: String, extras: Map<String, Any?> = emptyMap()) {
        dispatch(activity, activity.javaClass.name, event, extras, activity)
    }

    fun refreshActivityPlugins(pluginIds: Set<String>) {
        if (pluginIds.isEmpty()) return
        val reference = WeakReference(PluginActivityTracker.currentActivity() ?: return)
        Handler(Looper.getMainLooper()).post {
            val activity = reference.get() ?: return@post
            if (activity is GateActivity || activity.isFinishing || activity.isDestroyed) return@post
            if (PluginActivityTracker.currentActivity() !== activity) return@post
            if (activity is LifecycleOwner && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@post
            dispatch(activity, activity.javaClass.name, "resumed", emptyMap(), activity, pluginIds)
        }
    }

    private fun dispatch(context: Context, pageClass: String?, event: String, extras: Map<String, Any?>, activity: Activity?, pluginIds: Set<String>? = null) {
        val fileEvent = when (event) {
            "created" -> "onCreated"
            "started" -> "onStarted"
            "resumed" -> "onResumed"
            "paused" -> "onPaused"
            "stopped" -> "onStopped"
            "destroyed" -> "onDestroyed"
            "saveInstanceState" -> "onSaveInstanceState"
            "ws.message" -> "onWSMsg"
            "ws.connected", "ws.disconnected" -> "onWSStateChanged"
            "network.online", "network.offline" -> "onNetworkChanged"
            "account.changed" -> "onAccountChanged"
            "app.foreground" -> "onAppForeground"
            else -> event.takeIf { it.matches(Regex("on[A-Za-z][A-Za-z0-9_]*")) } ?: return
        }
        val appContext = context.applicationContext
        val activityReference = activity?.let { WeakReference(it) }
        for (plugin in PluginSession.allPlugins()) {
            if (pluginIds != null && plugin.manifest.id !in pluginIds) continue
            val scripts = plugin.events.filter { it.event == fileEvent && (it.page == "regular" || it.page == pageClass) }
                .sortedBy { if (it.page == "regular") 0 else 1 }
            if (scripts.isEmpty()) continue
            PluginScripts.enqueue(appContext, plugin.manifest.id) {
                if (PluginSession.findPlugin(plugin.manifest.id) !== plugin) return@enqueue
                scripts.forEach { script ->
                    PluginScripts.executeEvent(appContext, plugin, script, pageClass, event, extras, activityReference?.get())
                }
            }
        }
        if (activity != null) PluginScripts.refreshActivity(activity)
    }
}
