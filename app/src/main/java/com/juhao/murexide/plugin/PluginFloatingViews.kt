package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.juhao.murexide.ui.theme.MurexideTheme
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlinx.coroutines.flow.StateFlow

internal data class PluginFloatingPosition(
    val gravity: Int = Gravity.TOP or Gravity.END,
    val x: Float? = null,
    val y: Float? = null
)

internal fun parseFloatingGravity(value: String): Int {
    val normalized = value.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
        .replace(Regex("center[\\s_-]+horizontal"), "centerhorizontal")
        .replace(Regex("center[\\s_-]+vertical"), "centervertical")
    val tokens = normalized.split(Regex("[|,\\s_-]+")).filter { it.isNotEmpty() }
    require(tokens.isNotEmpty()) { "浮窗位置不能为空" }
    var horizontal = Gravity.CENTER_HORIZONTAL
    var vertical = Gravity.CENTER_VERTICAL
    tokens.forEach { token ->
        when (token) {
            "top" -> vertical = Gravity.TOP
            "bottom" -> vertical = Gravity.BOTTOM
            "start" -> horizontal = Gravity.START
            "end" -> horizontal = Gravity.END
            "left" -> horizontal = Gravity.LEFT
            "right" -> horizontal = Gravity.RIGHT
            "center" -> Unit
            "centerhorizontal" -> horizontal = Gravity.CENTER_HORIZONTAL
            "centervertical" -> vertical = Gravity.CENTER_VERTICAL
            else -> error("不支持的浮窗位置: $token")
        }
    }
    return horizontal or vertical
}

object PluginFloatingViews {
    private data class EntryKey(val pluginId: String, val id: String)

    private class Entry(
        val key: EntryKey,
        val session: PluginScriptSession,
        val content: StateFlow<PluginRenderedPage>,
        val position: PluginFloatingPosition
    ) {
        var released = false
        var windowRegistered = false
        var window = WeakReference<ViewGroup>(null)
        var composition = WeakReference<ComposeView>(null)
        var unregisterBack: (() -> Unit)? = null
    }

    private class Host(val context: Context, activity: Activity) {
        val entries = mutableListOf<Entry>()
        val decor = WeakReference(activity.window.decorView)
        val windows = activity.windowManager
        var layoutListener: View.OnLayoutChangeListener? = null
        var disposed = false
    }

    // Let content wrap naturally; only the available Activity viewport limits its size.
    private class FloatingContent(context: Context) : FrameLayout(context) {
        var maxWidth = Int.MAX_VALUE
        var maxHeight = Int.MAX_VALUE
        var onBack: (() -> Unit)? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), maxWidth), MeasureSpec.getMode(widthMeasureSpec)),
                MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(heightMeasureSpec), maxHeight), MeasureSpec.getMode(heightMeasureSpec))
            )
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && onBack != null) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack?.invoke()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val hosts = WeakHashMap<Activity, Host>()
    private val applications = WeakHashMap<Application, Boolean>()
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) { clearActivity(activity) }
    }

    internal fun add(
        activity: Activity,
        pluginId: String,
        id: String,
        session: PluginScriptSession,
        content: StateFlow<PluginRenderedPage>,
        position: PluginFloatingPosition = PluginFloatingPosition()
    ) {
        val reference = WeakReference(activity)
        val context = activity.applicationContext
        val entry = Entry(EntryKey(pluginId, id), session, content, position)
        post(context, pluginId, { release(context, entry) }) {
            val owner = reference.get()
            if (owner == null || !isValid(owner, entry)) {
                release(context, entry)
                return@post
            }
            var host = hosts[owner]
            try {
                val previous = host?.entries?.firstOrNull { it.key == entry.key }
                if (previous != null) {
                    host.entries.remove(previous)
                    unmount(host, previous)
                    if (previous.content === content) previous.released = true
                    else release(context, previous)
                }
                if (host == null || host.disposed) {
                    host = Host(context, owner)
                    hosts[owner] = host
                    observe(owner, host)
                }
                host.entries.add(entry)
                mountEntry(owner, host, entry)
            } catch (error: Throwable) {
                log(context, pluginId, error)
                host?.let(::disposeHost)
                release(context, entry)
            }
        }
    }

    fun remove(activity: Activity, pluginId: String, id: String) {
        val reference = WeakReference(activity)
        val context = activity.applicationContext
        post(context, pluginId) {
            val host = reference.get()?.let { hosts[it] } ?: return@post
            host.entries.firstOrNull { it.key == EntryKey(pluginId, id) }?.let { removeEntry(host, it) }
        }
    }

    fun removeSession(session: PluginScriptSession) {
        post(null, "") {
            hosts.values.toList().forEach { host ->
                host.entries.toList().filter { it.session === session }.forEach { removeEntry(host, it) }
            }
        }
    }

    fun clearActivity(activity: Activity) {
        val reference = WeakReference(activity)
        post(activity.applicationContext, "") {
            reference.get()?.let { hosts[it] }?.let(::disposeHost)
        }
    }

    private fun isValid(activity: Activity, entry: Entry): Boolean =
        !activity.isFinishing && !activity.isDestroyed && entry.session.isCurrent &&
            PluginSession.findPlugin(entry.key.pluginId) != null

    private fun observe(activity: Activity, host: Host) {
        val application = activity.application
        if (applications[application] != true) {
            application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            applications[application] = true
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            host.entries.toList().forEach { entry ->
                runCatching { updateWindow(host, entry) }.onFailure { log(host.context, entry.key.pluginId, it) }
            }
        }
        host.layoutListener = listener
        host.decor.get()?.addOnLayoutChangeListener(listener)
    }

    private fun mountEntry(activity: Activity, host: Host, entry: Entry) {
        val decor = checkNotNull(host.decor.get())
        val root = FloatingContent(activity).apply {
            id = View.generateViewId()
            isClickable = false
            setViewTreeLifecycleOwner(decor.findViewTreeLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(decor.findViewTreeSavedStateRegistryOwner())
            setViewTreeViewModelStoreOwner(decor.findViewTreeViewModelStoreOwner())
            onBack = {
                (hosts.entries.firstOrNull { it.value === host }?.key as? ComponentActivity)
                    ?.onBackPressedDispatcher?.onBackPressed()
            }
        }
        val composition = ComposeView(activity)
        entry.window = WeakReference(root)
        entry.composition = WeakReference(composition)
        composition.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composition.setContent {
            MurexideTheme(windowBackdrop = false) { FloatingEntry(host, entry) }
        }
        root.addView(composition, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = checkNotNull(decor.windowToken) { "当前页面尚未附着，无法创建浮窗" }
            setTitle("plugin:${entry.key.pluginId}:${entry.key.id}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or WindowInsets.Type.ime()
                )
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                configureLegacyInsets(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        configureWindow(host, entry, root, params)
        host.windows.addView(root, params)
        entry.windowRegistered = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            root.findOnBackInvokedDispatcher()?.let { dispatcher ->
                val callback = OnBackInvokedCallback { root.onBack?.invoke() }
                dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                entry.unregisterBack = { dispatcher.unregisterOnBackInvokedCallback(callback) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureLegacyInsets(params: WindowManager.LayoutParams) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private fun configureWindow(host: Host, entry: Entry, root: FloatingContent, params: WindowManager.LayoutParams) {
        val decor = host.decor.get() ?: return
        val display = decor.resources.displayMetrics
        val density = display.density
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) host.windows.currentWindowMetrics.bounds else null
        val width = bounds?.width() ?: (decor.width.takeIf { it > 0 } ?: display.widthPixels)
        val height = bounds?.height() ?: (decor.height.takeIf { it > 0 } ?: display.heightPixels)
        root.maxWidth = width.coerceAtLeast(1)
        root.maxHeight = height.coerceAtLeast(1)
        val rendered = entry.content.value
        val contentWidth = rendered.widget.width?.let {
            PluginExecutor.interpolate(it, rendered.env, entry.key.pluginId)
        }?.lowercase()
        params.width = when (contentWidth) {
            "match", "fill", "fillmaxwidth" -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> ViewGroup.LayoutParams.WRAP_CONTENT
        }
        var gravity = Gravity.getAbsoluteGravity(entry.position.gravity, decor.layoutDirection)
        // Explicit coordinates override the corresponding alignment axis, from the top-left.
        if (entry.position.x != null) gravity = (gravity and Gravity.HORIZONTAL_GRAVITY_MASK.inv()) or Gravity.LEFT
        if (entry.position.y != null) gravity = (gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or Gravity.TOP
        params.gravity = gravity
        params.x = ((entry.position.x ?: 0f) * density).toInt()
        params.y = ((entry.position.y ?: 0f) * density).toInt()
    }

    private fun updateWindow(host: Host, entry: Entry) {
        val root = entry.window.get() as? FloatingContent ?: return
        if (!root.isAttachedToWindow) return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        configureWindow(host, entry, root, params)
        root.requestLayout()
        host.windows.updateViewLayout(root, params)
    }

    @Composable
    private fun FloatingEntry(host: Host, entry: Entry) {
        val rendered by entry.content.collectAsState()
        val pageTick by PluginSession.pageTick.collectAsState()
        val plugins by PluginSession.plugins.collectAsState()
        val enabled = entry.session.isCurrent && plugins.any { it.manifest.id == entry.key.pluginId }
        LaunchedEffect(entry, pageTick, enabled) {
            post(host.context, entry.key.pluginId, { removeEntry(host, entry) }) {
                if (host.disposed || entry !in host.entries) return@post
                val activity = hosts.entries.firstOrNull { it.value === host }?.key
                if (activity == null || !isValid(activity, entry)) removeEntry(host, entry)
                else entry.session.refresh()
            }
        }
        LaunchedEffect(entry, rendered) {
            post(host.context, entry.key.pluginId) {
                if (!host.disposed && entry in host.entries) updateWindow(host, entry)
            }
        }
        if (enabled) PluginWidgetRenderer(rendered.widget, entry.key.pluginId, rendered.env)
    }

    private fun unmount(host: Host, entry: Entry) {
        val composition = entry.composition.get()
        val root = entry.window.get()
        val unregisterBack = entry.unregisterBack
        entry.unregisterBack = null
        (root as? FloatingContent)?.onBack = null
        runCatching { unregisterBack?.invoke() }
            .onFailure { log(host.context, entry.key.pluginId, it) }
        try {
            root?.visibility = View.GONE
            if (root != null && entry.windowRegistered) {
                host.windows.removeViewImmediate(root)
                entry.windowRegistered = false
            }
        } finally {
            try {
                composition?.disposeComposition()
            } finally {
                entry.composition.clear()
                entry.window.clear()
            }
        }
    }

    private fun removeEntry(host: Host, entry: Entry) {
        if (!host.entries.remove(entry)) return
        runCatching { unmount(host, entry) }.onFailure { log(host.context, entry.key.pluginId, it) }
        release(host.context, entry)
        if (host.entries.isEmpty()) disposeHost(host)
    }

    private fun disposeHost(host: Host) {
        if (host.disposed) return
        host.disposed = true
        val entries = host.entries.toList()
        host.entries.clear()
        host.layoutListener?.let { host.decor.get()?.removeOnLayoutChangeListener(it) }
        host.layoutListener = null
        host.decor.clear()
        val iterator = hosts.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value === host) iterator.remove()
        }
        entries.forEach { entry ->
            runCatching { unmount(host, entry) }.onFailure { log(host.context, entry.key.pluginId, it) }
            release(host.context, entry)
        }
    }

    private fun release(context: Context, entry: Entry) {
        if (entry.released) return
        entry.released = true
        runCatching { entry.session.releaseFloatingView(entry.key.id, entry.content) }
            .onFailure { log(context, entry.key.pluginId, it) }
    }

    private fun post(context: Context?, pluginId: String, onFailure: () -> Unit = {}, block: () -> Unit) {
        val task = Runnable {
            try { block() } catch (error: Throwable) {
                log(context, pluginId, error)
                runCatching(onFailure).onFailure { log(context, pluginId, it) }
            }
        }
        try {
            check(handler.post(task)) { "无法将浮窗操作提交到主线程" }
        } catch (error: Throwable) {
            log(context, pluginId, error)
            runCatching(onFailure).onFailure { log(context, pluginId, it) }
        }
    }

    private fun log(context: Context?, pluginId: String, error: Throwable) {
        val target = context ?: hosts.values.firstOrNull()?.context ?: return
        runCatching { PluginLogger.error(target, pluginId, "浮窗: ${error.message ?: error.javaClass.simpleName}") }
    }
}
