package com.juhao.murexide.ui.settings.userbot

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.juhao.murexide.data.MessageContent
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.UserBotMatchType
import com.juhao.murexide.data.UserBotRule
import com.juhao.murexide.data.UserBotRulesWrapper
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.network.RecallActor
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object UserBotManager {
    private const val TAG = "UserBotManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = MessageRepository()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val appContext = context.applicationContext

        scope.launch {
            val settingsStorage = SettingsStorage(appContext)
            val accountStorage = AccountStorage.getInstance(appContext)

            WebSocketManager.getInstance().messageFlow.collect { event ->
                if (event !is WebSocketManager.WsEvent.NewMessage) return@collect
                
                val enabled = settingsStorage.getUserBotEnabled()
                if (!enabled) return@collect

                val msg = event.message
                if (msg.isRecalled || msg.isMine) return@collect

                val recallSenderIds = settingsStorage.getUserBotRecallSenderIds()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                if (msg.senderId in recallSenderIds) {
                    val account = accountStorage.getCurrentAccount() ?: return@collect
                    if (account.token.isBlank()) return@collect
                    Log.d(TAG, "Recall message from ${msg.senderId}: ${msg.msgId}")
                    repository.recallMessage(
                        token = account.token,
                        msgId = msg.msgId,
                        chatId = msg.chatId,
                        chatType = msg.chatType
                    ).onSuccess {
                        Log.d(TAG, "Recall success: ${msg.msgId}")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(appContext, "已撤回用户「${msg.senderId}」的消息", Toast.LENGTH_SHORT).show()
                        }
                        WebSocketManager.getInstance().publishLocalMessageRecalled(
                            msg,
                            RecallActor(id = account.id, name = account.username)
                        )
                    }.onFailure { error ->
                        Log.e(TAG, "Recall failed: ${msg.msgId}", error)
                    }
                    return@collect
                }

                val rulesJson = settingsStorage.getUserBotRules()
                val rules = parseRules(rulesJson)
                if (rules.isEmpty()) return@collect

                val text = msg.content.trim()
                if (text.isEmpty()) return@collect

                val matched = rules.firstOrNull { rule ->
                    matchRule(rule, text)
                } ?: return@collect

                val account = accountStorage.getCurrentAccount() ?: return@collect
                if (account.token.isBlank()) return@collect

                Log.d(TAG, "Auto reply to ${msg.chatId} (type=${msg.chatType}): ${matched.reply}")
                repository.sendMessage(
                    token = account.token,
                    chatId = msg.chatId,
                    chatType = msg.chatType,
                    content = MessageContent(text = matched.reply),
                    contentType = MessageItem.CONTENT_TYPE_TEXT
                ).onSuccess { msgId ->
                    Log.d(TAG, "Auto reply sent: $msgId")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(appContext, "已自动回复「${matched.keyword}」", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Auto reply failed", error)
                }
            }
        }
    }

    fun matchRule(rule: UserBotRule, text: String): Boolean {
        if (rule.keyword.isBlank()) return false
        return when (rule.matchType) {
            UserBotMatchType.EXACT -> text.equals(rule.keyword, ignoreCase = true)
            UserBotMatchType.REGEX -> try {
                Regex(rule.keyword).containsMatchIn(text)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid regex: ${rule.keyword}", e)
                false
            }
            UserBotMatchType.CONTAINS -> text.contains(rule.keyword, ignoreCase = true)
        }
    }

    fun validateRegex(pattern: String): String? {
        if (pattern.isBlank()) return "正则不能为空"
        return try {
            Regex(pattern)
            null
        } catch (e: Exception) {
            e.message ?: "正则表达式不合法"
        }
    }

    fun parseRules(jsonStr: String): List<UserBotRule> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<UserBotRulesWrapper>(jsonStr).rules
                .filter { it.keyword.isNotBlank() && it.reply.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun encodeRules(rules: List<UserBotRule>): String {
        return runCatching {
            json.encodeToString(UserBotRulesWrapper.serializer(), UserBotRulesWrapper(rules))
        }.getOrDefault("")
    }
}