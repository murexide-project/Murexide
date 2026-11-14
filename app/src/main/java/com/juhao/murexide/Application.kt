package com.juhao.murexide

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.data.local.CacheSyncCoordinator
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.plugin.PluginActivityCallbacks
import com.juhao.murexide.plugin.PluginManager
import com.juhao.murexide.repository.ConversationRepository
import com.juhao.murexide.ui.lock.AppLockState
import com.juhao.murexide.ui.lock.GateActivity
import com.juhao.murexide.ui.settings.userbot.UserBotManager
import com.juhao.murexide.ui.theme.UiCache
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.utils.AppForegroundState
import com.juhao.murexide.utils.NotificationHelper
import com.juhao.murexide.utils.isYunhuImageUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class MyApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val accountStorage by lazy { AccountStorage.getInstance(this) }
    private val conversationRepository by lazy { ConversationRepository() }
    private val settingsStorage by lazy { SettingsStorage(this) }

    private val wsDeviceId: String = UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var appLockEnabled: Boolean = false

    @Volatile
    private var appLockLockOnBackground: Boolean = true

    @Volatile
    private var wasInBackground: Boolean = false

    private val activityLifecycleCallbacks = object : ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (!appLockEnabled) return
            if (AppLockState.unlocked) return
            if (activity is GateActivity) return
            if (AppLockState.gateVisible) return

            val forwarded = cloneIntent(activity.intent)
            AppLockState.gateVisible = true
            activity.startActivity(GateActivity.intentFor(activity, forwarded))
        }

        override fun onActivityStarted(activity: Activity) {
            if (!appLockEnabled || !appLockLockOnBackground) {
                wasInBackground = false
                return
            }
            if (activity is GateActivity) {
                wasInBackground = false
                return
            }
            if (wasInBackground && !AppLockState.unlocked && !AppLockState.gateVisible) {
                wasInBackground = false
                AppLockState.gateVisible = true
                activity.startActivity(GateActivity.intentFor(activity, null))
            }
        }

        override fun onActivityResumed(activity: Activity) { }

        override fun onActivityPaused(activity: Activity) { }

        override fun onActivityStopped(activity: Activity) {
            if (!AppForegroundState.isInForeground) {
                if (appLockEnabled && appLockLockOnBackground) {
                    AppLockState.reset()
                }
                wasInBackground = true
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }

        override fun onActivityDestroyed(activity: Activity) { }
    }

    private fun cloneIntent(intent: Intent?): Intent? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        ) {
            return null
        }
        return runCatching { Intent(intent) }.getOrNull()
    }

    override fun onCreate() {
        super.onCreate()

        runBlocking {
            appLockEnabled = settingsStorage.getAppLockEnabled()
            appLockLockOnBackground = settingsStorage.getAppLockLockOnBackground()
        }

        LocalCache.initialize(this)
        NotificationHelper.createNotificationChannel(this)
        AppForegroundState.init(this)
        UserBotManager.start(this)
        observeAvatarSetting()
        initWebSocket()
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        PluginManager.init(this)
        registerActivityLifecycleCallbacks(PluginActivityCallbacks())
        CacheSyncCoordinator(
            dismissConversationNotification = { chatId ->
                val account = accountStorage.getCurrentUserInfo()
                if (account != null && account.token.isNotEmpty()) {
                    conversationRepository.dismissNotification(account.token, chatId)
                        .onFailure { error ->
                            Log.w("MyApplication", "Failed to mark $chatId as read", error)
                        }
                }
            }
        ).start(applicationScope)
        observeNetworkStatus()
        observeMessages(this)
    }

    fun refreshAppLockState() {
        applicationScope.launch {
            appLockEnabled = settingsStorage.getAppLockEnabled()
            appLockLockOnBackground = settingsStorage.getAppLockLockOnBackground()
        }
    }

    private fun observeMessages(context: Context) {
        applicationScope.launch {
            WebSocketManager.getInstance().messageFlow.collect { event ->
                when (event) {
                    is WebSocketManager.WsEvent.NewMessage -> {
                        if (AppForegroundState.isInForeground) {
                            Log.d("MyApplication", "App in foreground, skip notification")
                            return@collect
                        }

                        val msg = event.message
                        if (msg.isRecalled || msg.isMine) {
                            return@collect
                        }

                        val notificationAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            true
                        } else {
                            settingsStorage.notificationEnabledFlow.first()
                        }

                        if (!notificationAllowed) {
                            Log.d("MyApplication", "Notification disabled, skip")
                            return@collect
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val permission = Manifest.permission.POST_NOTIFICATIONS
                            if (ContextCompat.checkSelfPermission(
                                    this@MyApplication,
                                    permission
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                Log.d("MyApplication", "Notification permission denied, skip sending")
                                return@collect
                            }
                        }

                        val currentConversation = UiCache.conversation.value.firstOrNull { it.chatId == msg.chatId && it.chatType == msg.chatType }

                        if (currentConversation?.doNotDisturb == 1) {
                            return@collect
                        }

                        val content = msg.getDisplayContent()
                        val chatName = currentConversation?.displayName ?: "会话"
                        val chatAvatarBitmap = loadAvatarBitmap(context, currentConversation?.avatarUrl)

                        NotificationHelper.sendNotification(
                            context = this@MyApplication,
                            chatId = msg.chatId,
                            chatType = msg.chatType,
                            chatName = chatName,
                            chatAvatar = currentConversation?.avatarUrl,
                            avatarBitmap = chatAvatarBitmap,
                            content = content
                        )
                    }
                    else -> { }
                }
            }
        }
    }

    private suspend fun loadAvatarBitmap(context: Context, avatarUrl: String?): Bitmap? {
        if (avatarUrl.isNullOrEmpty()) {
            return null
        }

        return try {
            val imageLoader = coil.Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(avatarUrl)
                .addHeader("Referer", "https://myapp.jwznb.com")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                .allowHardware(false)
                .size(128, 128)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun observeNetworkStatus() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("MyApplication", "Network onAvailable, checking WS toggle")
                applicationScope.launch {
                    if (settingsStorage.getWsEnabled()) {
                        Log.d("MyApplication", "WebSocket enabled, triggering reconnect")
                        WebSocketManager.getInstance().manualReconnect()
                    } else {
                        Log.d("MyApplication", "WebSocket disabled, skip reconnect")
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.d("MyApplication", "Network onLost")
                WebSocketManager.getInstance().notifyNetworkLost()
            }
        })
    }

    private fun initWebSocket() {
        applicationScope.launch {
            accountStorage.currentAccountFlow.collect { account ->
                LocalCache.setActiveAccount(account?.id)
                if (account != null && account.token.isNotEmpty()) {
                    if (settingsStorage.getWsEnabled()) {
                        Log.d("MyApplication", "Account found, connecting WS")
                        WebSocketManager.getInstance().connect(
                            userId = account.id,
                            token = account.token,
                            deviceId = wsDeviceId
                        )
                    } else {
                        Log.d("MyApplication", "WebSocket disabled by user, skip connect")
                        WebSocketManager.getInstance().disconnect()
                    }
                } else {
                    Log.d("MyApplication", "No account, disconnecting WS")
                    WebSocketManager.getInstance().disconnect()
                }
            }
        }
    }

    private fun observeAvatarSetting() {
        applicationScope.launch {
            settingsStorage.squareAvatarFlow.collect { squareAvatar ->
                UiState.squareAvatar.value = squareAvatar
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                NetworkClient.okHttpClient.newBuilder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        val imageRequest = if (isYunhuImageUrl(request.url.toString())) {
                            request.newBuilder()
                                .header("Referer", "https://myapp.jwznb.com")
                                .build()
                        } else {
                            request
                        }
                        chain.proceed(imageRequest)
                    }
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
}