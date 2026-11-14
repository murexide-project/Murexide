package com.juhao.murexide.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object PluginActions {

    private val ALLOWED = setOf("toast", "copy", "openUrl", "startActivity", "restartApp")

    fun execute(context: Context, action: PluginAction): Boolean {
        if (action.action !in ALLOWED) return false
        return runCatching {
            when (action.action) {
                "toast" -> {
                    val text = action.text ?: action.target ?: return false
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
                "copy" -> {
                    val text = action.text ?: action.target ?: return false
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("plugin", text))
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
                "openUrl" -> {
                    val url = action.target ?: return false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                "startActivity" -> {
                    val cls = action.target ?: return false
                    val intent = Intent().apply {
                        setClassName(context.packageName, cls)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                "restartApp" -> {
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(context.packageName)
                        ?: return false
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    context.startActivity(launchIntent)
                    Runtime.getRuntime().exit(0)
                }
            }
            true
        }.getOrDefault(false)
    }
}