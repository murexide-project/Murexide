package com.juhao.murexide

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flyjingfish.openimagelib.OpenImageConfig
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.ui.theme.UiCache
import com.juhao.murexide.ui.components.MurexideBigImageHelper
import com.juhao.murexide.ui.components.MurexideDownloadMediaHelper
import com.juhao.murexide.utils.AppForegroundState
import com.juhao.murexide.utils.NotificationHelper
import com.juhao.murexide.utils.isYunhuImageUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val settingsStorage by lazy { SettingsStorage(this) }

    override fun onCreate() {
        super.onCreate()
        OpenImageConfig.getInstance().apply {
            bigImageHelper = MurexideBigImageHelper()
            downloadMediaHelper = MurexideDownloadMediaHelper()
        }
        NotificationHelper.createNotificationChannel(this)
        AppForegroundState.init(this)
        initWebSocket()
        observeNetworkStatus()
        observeMessages(this)
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
                            true // 系统禁止无需判断
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
                    else -> { /* 忽略其他事件 */ }
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
                Log.d("MyApplication", "Network onAvailable, triggering WS reconnect")
                WebSocketManager.getInstance().manualReconnect()
            }

            override fun onLost(network: Network) {
                Log.d("MyApplication", "Network onLost")
                WebSocketManager.getInstance().notifyNetworkLost()
            }
        })
    }

    private fun initWebSocket() {
        val accountStorage = AccountStorage(this)
        val authRepository = AuthRepository()
        
        applicationScope.launch {
            accountStorage.currentTokenFlow.collect { token ->
                if (token != null) {
                    Log.d("MyApplication", "Token found, fetching user info for WS")
                    authRepository.getUserInfo(token).onSuccess { userInfo ->
                        val deviceId = "android_${Build.MODEL}_${Build.ID}"
                        WebSocketManager.getInstance().connect(
                            userId = userInfo.id,
                            token = token,
                            deviceId = deviceId
                        )
                    }.onFailure { e ->
                        Log.e("MyApplication", "Failed to get user info for WS", e)
                    }
                } else {
                    Log.d("MyApplication", "No token, disconnecting WS")
                    WebSocketManager.getInstance().disconnect()
                }
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
