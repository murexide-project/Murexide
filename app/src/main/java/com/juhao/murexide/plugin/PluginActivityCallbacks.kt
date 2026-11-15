package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.juhao.murexide.ui.lock.GateActivity

class PluginActivityCallbacks : Application.ActivityLifecycleCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is GateActivity) return
        val pageId = activity.javaClass.name
        val pairs = PluginManager.rulesForPage(pageId, "created")

        pairs.forEach { (manifest, rule) ->
            if (rule.content.type == "toast") {
                val ctx = PluginContext(activity, manifest.id, pageId, "created")
                if (!WhenEvaluator.evaluate(rule.whenExpr, ctx)) return@forEach
                val text = ctx.interpolate(
                    rule.content.value?.toString() ?: rule.content.message
                ) ?: return@forEach
                val duration = if (rule.content.duration == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(activity, text, duration).show()
            } else if (rule.content.type == "action") {
                val ctx = PluginContext(activity, manifest.id, pageId, "created")
                if (WhenEvaluator.evaluate(rule.whenExpr, ctx)) {
                    PluginActions.executeList(activity, ctx, rule.content.execute)
                }
            }
        }

        val replacePair = pairs.firstOrNull {
            it.second.action == "replace" && it.second.content.type != "toast"
        }
        if (replacePair != null) {
            scheduleInject(activity) {
                injectReplace(activity, pageId, replacePair.first, replacePair.second.content)
            }
            return
        }

        if (pairs.any {
                it.second.action == "decorate" &&
                        it.second.content.type != "toast" &&
                        it.second.content.type != "action"
            }) {
            scheduleInject(activity) {
                injectOverlay(activity, pageId)
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToastForEvent(activity, "started")
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToastForEvent(activity, "resumed")
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToastForEvent(activity, "paused")
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToastForEvent(activity, "stopped")
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity is GateActivity) return
        dispatchToastForEvent(activity, "destroyed")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    private fun scheduleInject(activity: Activity, block: () -> Unit) {
        if (activity !is ComponentActivity) return
        attemptInject(activity, block, 0)
    }

    private fun attemptInject(activity: Activity, block: () -> Unit, attempt: Int) {
        if (attempt >= 20) return
        if (activity.isFinishing || activity.isDestroyed) return
        val decor = activity.window?.decorView
        if (decor == null || !decor.isAttachedToWindow) {
            mainHandler.postDelayed({ attemptInject(activity, block, attempt + 1) }, 50L)
            return
        }
        mainHandler.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val decor2 = activity.window?.decorView ?: return@postDelayed
            if (!decor2.isAttachedToWindow) {
                attemptInject(activity, block, attempt + 1)
                return@postDelayed
            }
            block()
        }, 50L)
    }

    private fun dispatchToastForEvent(activity: Activity, event: String) {
        val pageId = activity.javaClass.name
        val pairs = PluginManager.rulesForPage(pageId, event)
        pairs.forEach { (manifest, rule) ->
            if (rule.content.type != "toast") return@forEach
            val ctx = PluginContext(activity, manifest.id, pageId, event)
            if (!WhenEvaluator.evaluate(rule.whenExpr, ctx)) return@forEach
            val text = ctx.interpolate(
                rule.content.value?.toString() ?: rule.content.message
            ) ?: return@forEach
            val duration = if (rule.content.duration == "long") Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(activity, text, duration).show()
        }
    }

    private fun injectOverlay(activity: Activity, pageId: String) {
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        if (decorView.findViewWithTag<View>(TAG_OVERLAY) != null) return

        runCatching {
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
        }.onFailure {
            PluginLogger.error(activity, "plugin", "overlay failed: ${it.message}")
        }
    }

    private fun injectReplace(
        activity: Activity,
        pageId: String,
        manifest: PluginManifest,
        content: PluginContent
    ) {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (contentView.findViewWithTag<View>(TAG_REPLACE) != null) return

        runCatching {
            val originalChildren = (0 until contentView.childCount).map { contentView.getChildAt(it) }
            originalChildren.forEach { it.visibility = View.GONE }

            val composeView = ComposeView(activity).apply {
                tag = TAG_REPLACE
                setContent {
                    PluginReplaceContent(
                        pageId = pageId,
                        pluginId = manifest.id,
                        content = content
                    )
                }
            }
            contentView.addView(
                composeView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }.onFailure {
            PluginLogger.error(activity, "plugin", "replace failed: ${it.message}")
        }
    }

    companion object {
        private const val TAG_OVERLAY = "plugin_overlay"
        private const val TAG_REPLACE = "plugin_replace"
    }
}