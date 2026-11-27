package com.juhao.murexide.plugin

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.juhao.murexide.ui.lock.GateActivity

class PluginActivityCallbacks : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is GateActivity) return
        dispatch(activity, "created")
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is GateActivity) return
        dispatch(activity, "started")
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is GateActivity) return
        dispatch(activity, "resumed")
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity is GateActivity) return
        dispatch(activity, "paused")
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is GateActivity) return
        dispatch(activity, "stopped")
    }

    override fun onActivityDestroyed(activity: Activity) {
        PluginFloatingViews.clearActivity(activity)
        if (activity is GateActivity) return
        dispatch(activity, "destroyed")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        if (activity is GateActivity) return
        dispatch(activity, "saveInstanceState")
    }

    private fun dispatch(activity: Activity, event: String) {
        try {
            PluginDispatcher.dispatchActivityEvent(activity, event)
        } catch (t: Throwable) {
            PluginLogger.error(activity, "plugin", "page event $event failed: ${t.message}")
        }
    }
}