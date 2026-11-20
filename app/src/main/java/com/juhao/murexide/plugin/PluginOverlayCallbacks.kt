package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.juhao.murexide.ui.lock.GateActivity
import java.util.HashMap

class PluginOverlayCallbacks : Application.ActivityLifecycleCallbacks {

    private val overlays = HashMap<Activity, PluginOverlayView>()

    override fun onActivityResumed(activity: Activity) {
        if (activity is GateActivity) return
        if (activity !is ComponentActivity) return
        if (overlays.containsKey(activity)) return

        val decor = activity.window.decorView as? ViewGroup ?: return

        decor.setViewTreeLifecycleOwner(activity)
        decor.setViewTreeViewModelStoreOwner(activity)
        decor.setViewTreeSavedStateRegistryOwner(activity)

        val overlay = PluginOverlayView(activity)
        overlay.bind(activity)
        decor.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlays[activity] = overlay
    }

    override fun onActivityDestroyed(activity: Activity) {
        val overlay = overlays.remove(activity) ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}

private class PluginOverlayView(context: Context) : FrameLayout(context) {

    private val composeView = ComposeView(context)

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        visibility = View.GONE
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun bind(activity: ComponentActivity) {
        composeView.setViewTreeLifecycleOwner(activity)
        composeView.setViewTreeViewModelStoreOwner(activity)
        composeView.setViewTreeSavedStateRegistryOwner(activity)

        composeView.setContent {
            val active by PluginSession.activeDecorates.collectAsState()
            LaunchedEffect(active.isEmpty()) {
                visibility = if (active.isEmpty()) View.GONE else View.VISIBLE
            }
            if (active.isEmpty()) return@setContent
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                active.forEach { ad ->
                    val align =
                        if (ad.action.position == "top") Alignment.TopCenter
                        else Alignment.BottomCenter
                    Box(Modifier.fillMaxWidth().align(align)) {
                        PluginWidgetRenderer(
                            widget = ad.action.widget,
                            pluginId = ad.pluginId,
                            env = ad.env,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}