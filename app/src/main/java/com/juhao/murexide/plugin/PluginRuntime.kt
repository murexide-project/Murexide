package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.juhao.murexide.R
import com.juhao.murexide.network.WebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object PluginPermissions {
    const val NETWORK = "network"
    const val CLIPBOARD = "clipboard"
    const val NOTIFICATION = "notification"
    const val VIBRATE = "vibrate"
    const val READ_SETTINGS = "readSettings"
    const val READ_ACCOUNT = "readAccount"
    const val OPEN_CHAT = "openChat"
    const val NAVIGATE = "navigate"
    const val RESTART = "restartApp"
    const val OPEN_URL = "openUrl"
    const val STORAGE = "storage"
    const val LOG = "log"
    const val SHARE = "share"
    const val ALL = "all"

    val KNOWN = setOf(
        NETWORK, CLIPBOARD, NOTIFICATION, VIBRATE, READ_SETTINGS,
        READ_ACCOUNT, OPEN_CHAT, NAVIGATE, RESTART, OPEN_URL,
        STORAGE, LOG, SHARE, ALL
    )

    fun label(p: String): String = when (p) {
        NETWORK -> "网络请求"
        CLIPBOARD -> "读写剪贴板"
        NOTIFICATION -> "发送通知"
        VIBRATE -> "震动"
        READ_SETTINGS -> "读取应用设置"
        READ_ACCOUNT -> "读取当前账号"
        OPEN_CHAT -> "打开会话"
        NAVIGATE -> "跳转页面"
        RESTART -> "重启应用"
        OPEN_URL -> "打开外部链接"
        STORAGE -> "插件存储"
        LOG -> "写入日志"
        SHARE -> "系统分享"
        ALL -> "全部权限"
        else -> p
    }
}

object PluginNotification {
    private const val CHANNEL_ID = "plugin_notifications"
    private const val CHANNEL_NAME = "插件通知"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun post(context: Context, pluginId: String, title: String, content: String) {
        ensureChannel(context)
        val id = (pluginId.hashCode() xor title.hashCode())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }
}

object PluginSystem {
    private val _networkOnline = MutableStateFlow(true)
    val networkOnline: StateFlow<Boolean> = _networkOnline.asStateFlow()

    @Volatile var wsConnected: Boolean = false

    fun setNetworkOnline(online: Boolean) {
        _networkOnline.value = online
    }

    fun isNetworkOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWsConnected(): Boolean = runCatching {
        WebSocketManager.getInstance().connectionState.value
    }.getOrDefault(wsConnected)
}

object PluginEventBus {
    private val listeners = CopyOnWriteArrayList<(PluginEvent) -> Unit>()

    data class PluginEvent(
        val name: String,
        val context: Context,
        val activity: Activity? = null,
        val extras: Map<String, Any?> = emptyMap()
    )

    fun addListener(listener: (PluginEvent) -> Unit) {
        if (!listeners.contains(listener)) listeners += listener
    }

    fun removeListener(listener: (PluginEvent) -> Unit) {
        listeners -= listener
    }

    fun emit(event: PluginEvent) {
        listeners.forEach { runCatching { it(event) } }
    }
}

object PluginActivityTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var current: WeakReference<Activity>? = null

    fun currentActivity(): Activity? = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) { }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { }
    override fun onActivityStarted(activity: Activity) { }
    override fun onActivityStopped(activity: Activity) { }
    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }
}