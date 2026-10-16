package com.juhao.murexide.utils

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.juhao.murexide.R
import com.juhao.murexide.ui.chat.ChatActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    private const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "聊天消息"
    private const val NOTIFICATION_GROUP_PREFIX = "chat_group_"

    private val notificationIdCounter = AtomicInteger(1000)
    private val chatNotificationIds = ConcurrentHashMap<String, MutableSet<Int>>()
    private val messageCounts = ConcurrentHashMap<String, Int>()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到新的聊天消息"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun sendNotification(
        context: Context,
        chatId: String,
        chatType: Int,
        chatName: String,
        chatAvatar: String?,
        avatarBitmap: Bitmap?,
        content: String
    ) {
        val count = (messageCounts[chatId] ?: 0) + 1
        messageCounts[chatId] = count

        val notificationId = notificationIdCounter.incrementAndGet()
        chatNotificationIds.getOrPut(chatId) { java.util.Collections.synchronizedSet(mutableSetOf()) }
            .add(notificationId)

        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("chat_id", chatId)
            putExtra("chat_type", chatType)
            putExtra("chat_name", chatName)
            putExtra("chat_avatar", chatAvatar)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(chatName)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP_PREFIX + chatId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_SOUND)

        if (avatarBitmap != null) {
            builder.setLargeIcon(avatarBitmap)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun clearNotification(context: Context, chatId: String) {
        messageCounts.remove(chatId)
        val ids = chatNotificationIds.remove(chatId) ?: return
        val manager = NotificationManagerCompat.from(context)
        synchronized(ids) {
            ids.forEach { manager.cancel(it) }
        }
    }

    fun clearAllNotifications(context: Context) {
        messageCounts.clear()
        chatNotificationIds.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }
}

object AppForegroundState {
    private val activityCount = AtomicInteger(0)
    private val _returnedToForeground = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasEnteredForeground = false

    val returnedToForeground: SharedFlow<Unit> = _returnedToForeground.asSharedFlow()

    val isInForeground: Boolean
        get() = activityCount.get() > 0

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val count = activityCount.incrementAndGet()
                if (count == 1) {
                    if (hasEnteredForeground) {
                        _returnedToForeground.tryEmit(Unit)
                    } else {
                        hasEnteredForeground = true
                    }
                    onForegroundChanged(true)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                val count = activityCount.decrementAndGet()
                if (count == 0) {
                    onForegroundChanged(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun onForegroundChanged(foreground: Boolean) {
        android.util.Log.d("AppForegroundState", if (foreground) "进入前台" else "进入后台")
    }
}