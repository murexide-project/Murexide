package com.juhao.murexide.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.data.ConversationKey
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.data.unreadTotal
import com.juhao.murexide.repository.ConversationRepository
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class ChatActivity : ComponentActivity() {

    private var searchTargetMsgId by mutableStateOf<String?>(null)
    private var searchTargetMsgSeq by mutableStateOf<Long?>(null)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = intent.getStringExtra("chat_id") ?: return finish()
        val chatType = intent.getIntExtra("chat_type", 1)
        val chatName = intent.getStringExtra("chat_name") ?: ""
        val chatAvatar = intent.getStringExtra("chat_avatar") ?: ""
        readSearchTarget(intent)

        val accountStorage = AccountStorage.getInstance(this)
        val accountState = mutableStateOf<UserAccount?>(null)

        setContent {
            MurexideTheme {
                val account = accountState.value
                if (account == null) {
                    Surface {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ContainedLoadingIndicator()
                                Spacer(Modifier.height(6.dp))
                                Text("马上就好...")
                            }
                        }
                    }
                } else {
                    val conversations by LocalCache.observeConversations(account.id)
                        .collectAsState(initial = emptyList())

                    val chatViewModel: ChatViewModel = viewModel(
                        key = "chat_${chatId}_$chatType",
                        factory = remember(account.id, account.token, chatId, chatType) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ChatViewModel(
                                        token = account.token,
                                        chatId = chatId,
                                        chatType = chatType,
                                        accountStorage = accountStorage,
                                        currentUserId = account.id,
                                        currentUserName = account.username,
                                        currentUserAvatar = account.avatar
                                    ) as T
                                }
                            }
                        }
                    )

                    ChatScreen(
                        chatType = chatType,
                        chatName = chatName,
                        chatId = chatId,
                        chatAvatar = chatAvatar,
                        onBackClick = { finish() },
                        onOpenConversation = { target ->
                            if (target.chatId != chatId || target.chatType != chatType) {
                                start(
                                    context = this@ChatActivity,
                                    chatId = target.chatId,
                                    chatType = target.chatType,
                                    chatName = target.displayName,
                                    chatAvatar = target.avatarUrl
                                )
                            }
                        },
                        backUnreadCount = conversations.unreadTotal(ConversationKey(chatId, chatType)),
                        searchTargetMsgId = searchTargetMsgId,
                        searchTargetMsgSeq = searchTargetMsgSeq,
                        viewModel = chatViewModel
                    )
                }
            }
        }

        lifecycleScope.launch {
            val account = runCatching { accountStorage.getCurrentUserInfo() }.getOrNull()
            if (account == null || account.token.isEmpty()) {
                finish()
                return@launch
            }
            accountState.value = account
            LocalCache.clearUnread(account.id, chatId, chatType)
            ConversationRepository().dismissNotification(account.token, chatId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSearchTarget(intent)
    }

    private fun readSearchTarget(intent: Intent) {
        searchTargetMsgId = intent.getStringExtra("search_target_msg_id")
        searchTargetMsgSeq = intent.getLongExtra("search_target_msg_seq", -1L)
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            chatAvatar: String
        ) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
            }
            context.startActivity(intent)
        }

        fun startWithSearchTarget(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            chatAvatar: String = "",
            targetMsgId: String,
            targetMsgSeq: Long
        ) {
            val intent = Intent(context, ChatActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
                putExtra("search_target_msg_id", targetMsgId)
                putExtra("search_target_msg_seq", targetMsgSeq)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
}