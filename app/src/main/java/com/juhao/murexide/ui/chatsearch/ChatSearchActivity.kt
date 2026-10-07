package com.juhao.murexide.ui.chatsearch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class ChatSearchActivity : ComponentActivity() {

    private val accountState = mutableStateOf<UserAccount?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val chatType = intent.getIntExtra(EXTRA_CHAT_TYPE, 1)
        val chatName = intent.getStringExtra(EXTRA_CHAT_NAME).orEmpty()

        if (chatId.isBlank()) {
            Toast.makeText(this, "会话参数缺失", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MurexideTheme {
                val account = accountState.value
                if (account == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    ChatSearchScreen(
                        chatName = chatName,
                        onBack = { finish() },
                        onOpenMessage = { msgId, msgSeq ->
                            ChatActivity.startWithSearchTarget(
                                context = this,
                                chatId = chatId,
                                chatType = chatType,
                                chatName = chatName,
                                chatAvatar = "",
                                targetMsgId = msgId,
                                targetMsgSeq = msgSeq
                            )
                        },
                        viewModel = viewModel(
                            key = "chat_search_${chatId}_$chatType",
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ChatSearchViewModel(
                                        token = account.token,
                                        chatId = chatId,
                                        chatType = chatType
                                    ) as T
                                }
                            }
                        )
                    )
                }
            }
        }

        lifecycleScope.launch {
            val account = runCatching {
                AccountStorage.getInstance(this@ChatSearchActivity).getCurrentUserInfo()
            }.getOrNull()
        
            if (account?.token.isNullOrEmpty()) {
                Toast.makeText(this@ChatSearchActivity, "请先登录", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            accountState.value = account
        }
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_CHAT_TYPE = "chat_type"
        private const val EXTRA_CHAT_NAME = "chat_name"

        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String
        ) {
            val intent = Intent(context, ChatSearchActivity::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_CHAT_TYPE, chatType)
                putExtra(EXTRA_CHAT_NAME, chatName)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}