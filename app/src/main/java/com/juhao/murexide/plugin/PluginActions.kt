package com.juhao.murexide.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.network.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PluginActions {

    private val scope = CoroutineScope(Dispatchers.Main)

    fun execute(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        if (action.action == "if") return executeIf(context, ctx, action)
        if (!checkPermission(context, ctx.pluginId, action.action)) {
            PluginLogger.warn(context, ctx.pluginId, "no permission for ${action.action}")
            return false
        }
        return runCatching {
            when (action.action) {
                "toast" -> doToast(context, ctx, action)
                "copy" -> doCopy(context, ctx, action)
                "openUrl" -> doOpenUrl(context, ctx, action)
                "startActivity", "navigate" -> doStartActivity(context, ctx, action)
                "restartApp" -> doRestart(context)
                "log" -> doLog(context, ctx, action)
                "set" -> doSet(context, ctx, action)
                "delay" -> { doDelay(action); true }
                "http" -> doHttp(context, ctx, action)
                "notify" -> doNotify(context, ctx, action)
                "vibrate" -> doVibrate(context, action)
                "share" -> doShare(context, ctx, action)
                "openApp" -> doOpenApp(context, ctx, action)
                "openChat" -> doOpenChat(context, ctx, action)
                "readSetting" -> doReadSetting(context, ctx, action)
                "getAccount" -> doGetAccount(context, ctx, action)
                "showDialog" -> true
                else -> {
                    PluginLogger.warn(context, ctx.pluginId, "unknown action: ${action.action}")
                    false
                }
            }
        }.getOrDefault(false)
    }

    fun executeList(context: Context, ctx: PluginContext, actions: List<PluginAction>?) {
        actions?.forEach { execute(context, ctx, it) }
    }

    private fun executeIf(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val ok = WhenEvaluator.evaluate(action.condition, ctx)
        val list = if (ok) action.then else action.elseActions
        list?.forEach { execute(context, ctx, it) }
        return true
    }

    private fun checkPermission(context: Context, pluginId: String, action: String): Boolean {
        val manifest = PluginManager.manifests.value.firstOrNull { it.id == pluginId } ?: return false
        val required = permissionFor(action) ?: return true
        return manifest.permissions.contains(required) || manifest.permissions.contains(PluginPermissions.ALL)
    }

    private fun permissionFor(action: String): String? = when (action) {
        "copy" -> PluginPermissions.CLIPBOARD
        "openUrl" -> PluginPermissions.OPEN_URL
        "startActivity", "navigate" -> PluginPermissions.NAVIGATE
        "restartApp" -> PluginPermissions.RESTART
        "log" -> PluginPermissions.LOG
        "http" -> PluginPermissions.NETWORK
        "notify" -> PluginPermissions.NOTIFICATION
        "vibrate" -> PluginPermissions.VIBRATE
        "share" -> PluginPermissions.SHARE
        "openApp" -> PluginPermissions.OPEN_URL
        "openChat" -> PluginPermissions.OPEN_CHAT
        "readSetting" -> PluginPermissions.READ_SETTINGS
        "getAccount" -> PluginPermissions.READ_ACCOUNT
        "set" -> PluginPermissions.STORAGE
        else -> null
    }

    private fun doToast(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val text = ctx.interpolate(action.text ?: action.target) ?: return false
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun doCopy(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val text = ctx.interpolate(action.text ?: action.target) ?: return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("plugin", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun doOpenUrl(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val url = ctx.interpolate(action.target ?: action.url) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun doStartActivity(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val cls = ctx.interpolate(action.target) ?: return false
        val intent = Intent().apply {
            setClassName(context.packageName, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action.extras?.forEach { (k, v) ->
                when (v) {
                    is Int -> putExtra(k, v)
                    is Long -> putExtra(k, v)
                    is Boolean -> putExtra(k, v)
                    is Float -> putExtra(k, v)
                    else -> putExtra(k, ctx.interpolate(v?.toString() ?: "") ?: "")
                }
            }
            action.flags?.forEach { f ->
                when (f) {
                    "NEW_TASK" -> addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    "CLEAR_TOP" -> addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    "CLEAR_TASK" -> addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    "SINGLE_TOP" -> addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    "NO_HISTORY" -> addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
            }
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun doRestart(context: Context): Boolean {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)
        Runtime.getRuntime().exit(0)
        return true
    }

    private fun doLog(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val text = ctx.interpolate(action.text) ?: return false
        PluginLogger.info(context, ctx.pluginId, text)
        return true
    }

    private fun doSet(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val key = action.key ?: return false
        val v = action.value
        val interpolated = if (v is String) ctx.interpolate(v) else v
        ctx.setKv(key, interpolated, action.persist == true)
        return true
    }

    private fun doDelay(action: PluginAction) {
        val d = action.duration ?: return
        scope.launch { delay(d) }
    }

    private fun doHttp(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val url = ctx.interpolate(action.url ?: action.target) ?: return false
        val method = (action.method ?: "GET").uppercase()
        val bodyStr = ctx.interpolate(action.body) ?: ""
        val headers = action.headers?.mapValues { ctx.interpolate(it.value) ?: "" } ?: emptyMap()
        val saveTo = action.saveTo
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = if (method == "GET" || method == "DELETE" || method == "HEAD") null
                    else bodyStr.toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url(url).method(method, body)
                    headers.forEach { (k, v) -> req.addHeader(k, v) }
                    val client = NetworkClient.okHttpClient.newBuilder()
                        .callTimeout(30, TimeUnit.SECONDS).build()
                    client.newCall(req.build()).execute().use { resp ->
                        val text = resp.body?.string() ?: ""
                        saveTo?.let { ctx.setVar(it, text) }
                        PluginLogger.info(context, ctx.pluginId, "http $method $url -> ${resp.code}")
                    }
                }
            }.onFailure {
                PluginLogger.error(context, ctx.pluginId, "http failed: ${it.message}")
            }
        }
        return true
    }

    private fun doNotify(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val title = ctx.interpolate(action.notifyTitle ?: action.title) ?: "插件通知"
        val text = ctx.interpolate(action.text ?: action.message) ?: return false
        PluginNotification.post(context, ctx.pluginId, title, text)
        return true
    }

    private fun doVibrate(context: Context, action: PluginAction): Boolean {
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return false
        val ms = action.duration ?: 100L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(ms)
        }
        return true
    }

    private fun doShare(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val text = ctx.interpolate(action.text) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = action.mimeType ?: "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
    }

    private fun doOpenApp(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val pkg = ctx.interpolate(action.packageName)
        val scheme = ctx.interpolate(action.scheme ?: action.target)
        val intent = when {
            !scheme.isNullOrBlank() -> Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
            !pkg.isNullOrBlank() -> context.packageManager.getLaunchIntentForPackage(pkg)
            else -> null
        } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun doOpenChat(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val chatId = ctx.interpolate(action.chatId) ?: return false
        val chatType = action.chatType ?: 1
        val chatName = ctx.interpolate(action.title ?: action.text) ?: ""
        val intent = Intent().apply {
            setClassName(context.packageName, "com.juhao.murexide.ui.chat.ChatActivity")
            putExtra("chat_id", chatId)
            putExtra("chat_type", chatType)
            putExtra("chat_name", chatName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    private fun doReadSetting(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val key = action.readKey ?: action.key ?: return false
        val storage = SettingsStorage(context)
        scope.launch {
            val value = runCatching {
                when (key) {
                    "themeMode" -> storage.getThemeMode()
                    "themeColor" -> storage.getThemeColor()
                    "notificationEnabled" -> storage.getNotificationEnabled()
                    "squareAvatar" -> storage.getSquareAvatar()
                    "bigScreen" -> storage.getBigScreen()
                    "updateChannel" -> storage.getUpdateChannel()
                    "showSticky" -> storage.getShowSticky()
                    "mergeSticky" -> storage.getMergeSticky()
                    "floatBottomBar" -> storage.getFloatBottomBar()
                    "floatingTopBar" -> storage.getFloatingTopBar()
                    "showMsgTags" -> storage.getShowMsgTags()
                    "bubbleCornerRadius" -> storage.getBubbleCornerRadius()
                    "bubbleOpacity" -> storage.getBubbleOpacity()
                    "showBackground" -> storage.getShowBackground()
                    "backgroundOpacity" -> storage.getBackgroundOpacity()
                    "showMyBubbleAvatar" -> storage.getShowMyBubbleAvatar()
                    "liquidGlassEnabled" -> storage.getLiquidGlassEnabled()
                    "liquidGlassBlur" -> storage.getLiquidGlassBlur()
                    "screenshotHideSenderInfo" -> storage.getScreenshotHideSenderInfo()
                    "screenshotHideMyInfo" -> storage.getScreenshotHideMyInfo()
                    "screenshotHideSessionInfo" -> storage.getScreenshotHideSessionInfo()
                    "screenshotHideImages" -> storage.getScreenshotHideImages()
                    "cacheEnabled" -> storage.getCacheEnabled()
                    "wsEnabled" -> storage.getWsEnabled()
                    "userBotEnabled" -> storage.getUserBotEnabled()
                    "appLockEnabled" -> storage.getAppLockEnabled()
                    "appLockLockOnBackground" -> storage.getAppLockLockOnBackground()
                    "enterKeyToSend" -> storage.getEnterKeyToSend()
                    else -> null
                }
            }.getOrNull()
            val target = action.saveTo ?: action.key
            if (target != null && value != null) ctx.setVar(target, value)
        }
        return true
    }

    private fun doGetAccount(context: Context, ctx: PluginContext, action: PluginAction): Boolean {
        val target = action.saveTo ?: return false
        scope.launch {
            val acc = runCatching {
                AccountStorage.getInstance(context).currentAccountFlow.first()
            }.getOrNull() ?: return@launch
            ctx.setVar(target, JSONObject().apply {
                put("id", acc.id)
                put("username", acc.username)
                put("avatar", acc.avatar)
            })
        }
        return true
    }
}