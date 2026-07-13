package com.juhao.murexide.ui.addchat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.theme.MurexideTheme

/**
 * 添加会话页面。由 UrlSchemeHandler 处理 yunhu://chat-add 时进入。
 */
class AddChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = resolveChatId()
        val chatType = resolveChatType()
        if (chatId.isNullOrEmpty()) {
            Toast.makeText(this, "无效的会话链接", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val viewModel: AddChatViewModel by viewModels {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddChatViewModel(
                        tokenStorage = TokenStorage(this@AddChatActivity),
                        chatId = chatId,
                        chatType = chatType
                    ) as T
                }
            }
        }

        setContent {
            MurexideTheme {
                AddChatScreen(
                    onBackClick = { finish() },
                    onEnterChat = { detail ->
                        ChatActivity.start(
                            context = this,
                            chatId = detail.chatId,
                            chatType = detail.chatType,
                            chatName = detail.name,
                            chatAvatar = detail.avatarUrl
                        )
                        finish()
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    private fun resolveChatId(): String? {
        intent.getStringExtra(EXTRA_CHAT_ID)?.takeIf { it.isNotEmpty() }?.let { return it }
        // yunhu://chat-add?id=xxx&type=user
        return intent.data?.getQueryParameter("id")?.takeIf { it.isNotEmpty() }
    }

    private fun resolveChatType(): Int {
        if (intent.hasExtra(EXTRA_CHAT_TYPE)) {
            val t = intent.getIntExtra(EXTRA_CHAT_TYPE, 1)
            if (t in 1..3) return t
        }
        // scheme 里的 type 为 user/group/bot 文本
        return when (intent.data?.getQueryParameter("type")) {
            "group" -> 2
            "bot" -> 3
            "user" -> 1
            else -> 1
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_TYPE = "chat_type"

        /** @param chatType 1-用户，2-群聊，3-机器人 */
        fun start(context: Context, chatId: String, chatType: Int) {
            val intent = Intent(context, AddChatActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CHAT_TYPE, chatType)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}
