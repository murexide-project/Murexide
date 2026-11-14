package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import com.juhao.murexide.ui.lock.GateActivity

class PluginActivityCallbacks : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is GateActivity) return
        val pageId = activity.javaClass.name
        val rules = PluginManager.rulesFor(pageId, "created")
        dispatchToast(activity, rules)

        val replaceRule = rules.firstOrNull {
            it.action == "replace" && it.content.type != "toast"
        }
        if (replaceRule != null) {
            activity.window.decorView.post { injectReplace(activity, pageId, replaceRule.content) }
            return
        }

        val hasOverlay = rules.any { it.action == "decorate" && it.content.type != "toast" }
        if (hasOverlay) {
            activity.window.decorView.post { injectOverlay(activity, pageId) }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToast(activity, PluginManager.rulesFor(activity.javaClass.name, "started"))
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToast(activity, PluginManager.rulesFor(activity.javaClass.name, "resumed"))
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToast(activity, PluginManager.rulesFor(activity.javaClass.name, "paused"))
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToast(activity, PluginManager.rulesFor(activity.javaClass.name, "stopped"))
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToast(activity, PluginManager.rulesFor(activity.javaClass.name, "destroyed"))
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    private fun dispatchToast(activity: Activity, rules: List<PluginRule>) {
        rules.forEach { rule ->
            if (rule.content.type == "toast") {
                val text = rule.content.value ?: rule.content.message ?: return@forEach
                val duration = when (rule.content.duration) {
                    "long" -> Toast.LENGTH_LONG
                    else -> Toast.LENGTH_SHORT
                }
                Toast.makeText(activity, text, duration).show()
            }
        }
    }

    private fun injectOverlay(activity: Activity, pageId: String) {
        val decorView = activity.window.decorView as? ViewGroup ?: return
        if (decorView.findViewWithTag<View>(TAG_OVERLAY) != null) return

        val overlay = ComposeView(activity).apply {
            tag = TAG_OVERLAY
            isClickable = false
            isFocusable = false
            setContent { PluginOverlay(pageId = pageId) }
        }
        decorView.addView(
            overlay,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun injectReplace(activity: Activity, pageId: String, content: PluginContent) {
        val contentParent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (contentParent.findViewWithTag<View>(TAG_REPLACE) != null) return
        contentParent.removeAllViews()

        val replacement = ComposeView(activity).apply {
            tag = TAG_REPLACE
            setContent { PluginReplaceContent(pageId = pageId, content = content) }
        }
        contentParent.addView(
            replacement,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        private const val TAG_OVERLAY = "plugin_overlay"
        private const val TAG_REPLACE = "plugin_replace"
    }
}