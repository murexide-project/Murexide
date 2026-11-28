package com.juhao.murexide.plugin

import android.content.Context
import com.juhao.murexide.data.MessageContent
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.network.RecallActor
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.MessageRepository
import com.seanproctor.lua.LuaState
import com.seanproctor.lua.LuaValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal object PluginMessageFunctions {
    fun register(
        state: LuaState,
        context: Context,
        pluginId: String,
        beforeCall: () -> Unit = {},
        resolve: (LuaValue) -> String = { value ->
            when (value) {
                is LuaValue.Str -> value.value
                is LuaValue.Integer -> value.value.toString()
                else -> error("消息参数必须是字符串或整数")
            }
        }
    ) {
        fun field(args: List<LuaValue>, name: String, position: Int): String {
            val table = args.firstOrNull() as? LuaValue.Table
            val value = if (table != null) table[LuaValue.Str(name)] else args.getOrNull(position)
            return value?.takeUnless { it == LuaValue.Nil }?.let(resolve).orEmpty()
        }
        for (sending in listOf(true, false)) {
            val name = if (sending) "sendMessage" else "recallMessage"
            state.registerTraced(context, pluginId, name) { args ->
                beforeCall()
                val plugin = checkNotNull(PluginSession.findPlugin(pluginId)) { "插件已停用" }
                val permissions = plugin.manifest.permissions
                check(PluginPermissions.USER_BOT in permissions || PluginPermissions.ALL in permissions) {
                    "permission denied: ${PluginPermissions.USER_BOT}"
                }
                val result = runCatching {
                    val chatId = field(args, "chatId", 0)
                    val chatType = field(args, "chatType", 1).toIntOrNull()
                    require(chatId.isNotBlank()) { "chatId 不能为空" }
                    require(chatType != null && chatType in 1..3) { "chatType 必须为 1（私聊）、2（群聊）或 3（机器人）" }
                    val content = if (sending) field(args, "content", 2) else ""
                    val msgId = if (sending) "" else field(args, "msgId", 2)
                    val quoteMsgId = if (sending) field(args, "quoteMsgId", 3).takeIf { it.isNotBlank() } else null
                    require(if (sending) content.isNotBlank() else msgId.isNotBlank()) {
                        if (sending) "content 不能为空" else "msgId 不能为空"
                    }
                    runBlocking(Dispatchers.IO) {
                        val account = checkNotNull(AccountStorage.getInstance(context).getCurrentAccount()) { "请先登录账号" }
                        check(account.token.isNotBlank()) { "当前账号未登录" }
                        check(PluginSession.findPlugin(pluginId) === plugin) { "插件已停用或重载" }
                        val repository = MessageRepository()
                        val completedId = if (sending) {
                            repository.sendMessage(
                                token = account.token,
                                chatId = chatId,
                                chatType = requireNotNull(chatType),
                                content = MessageContent(text = content),
                                contentType = MessageItem.CONTENT_TYPE_TEXT,
                                quoteMsgId = quoteMsgId
                            ).getOrThrow()
                        } else {
                            check(repository.recallMessage(account.token, msgId, chatId, requireNotNull(chatType)).getOrThrow()) { "撤回失败" }
                            msgId
                        }
                        val currentAccount = runCatching {
                            AccountStorage.getInstance(context).getCurrentAccount()
                        }.getOrNull()
                        if (currentAccount != null && currentAccount.id == account.id && currentAccount.token == account.token) {
                            val message = MessageItem(
                                msgId = completedId,
                                senderId = if (sending) account.id else "",
                                senderName = if (sending) account.username else "",
                                senderAvatar = if (sending) account.avatar else "",
                                chatId = chatId,
                                chatType = requireNotNull(chatType),
                                content = content,
                                contentType = MessageItem.CONTENT_TYPE_TEXT,
                                timestamp = if (sending) System.currentTimeMillis() else 0L,
                                direction = if (sending) "right" else "left",
                                hasReliableSender = sending,
                                quoteMsgId = quoteMsgId
                            )
                            val ws = WebSocketManager.getInstance()
                            if (sending) ws.publishLocalMessageSent(message)
                            else ws.publishLocalMessageRecalled(message, RecallActor(id = account.id, name = account.username))
                        }
                        completedId
                    }
                }
                val table = state.eval("return {}").single() as LuaValue.Table
                table[LuaValue.Str("successful")] = LuaValue.Bool(result.isSuccess)
                result.onSuccess { table[LuaValue.Str("msgId")] = LuaValue.Str(it) }
                    .onFailure { error ->
                        val message = error.message ?: "消息操作失败"
                        table[LuaValue.Str("error")] = LuaValue.Str(message)
                        PluginLogger.warn(context, pluginId, "$name failed: $message")
                    }
                listOf(table)
            }
        }
    }
}
