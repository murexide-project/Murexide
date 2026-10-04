package com.juhao.murexide.ui.conversationdetail

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
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.MainActivity
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.ShareRepository
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.community.ba.BaDetailActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class ConversationDetailActivity : ComponentActivity() {

    private val accountState =
        mutableStateOf<com.juhao.murexide.datastore.UserAccount?>(null)

    private val detailState =
        mutableStateOf<DetailState>(DetailState.Resolving)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                val account = accountState.value
                val state = detailState.value
                when {
                    account == null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state is DetailState.Resolving -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state is DetailState.Error -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(state.message) }

                    state is DetailState.Ready -> ConversationDetailScreen(
                        onBack = { finish() },
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
                        onEditGroup = { detail ->
                            GroupSettingsActivity.start(
                                context = this,
                                groupId = detail.chatId,
                                groupName = detail.name,
                                groupAvatar = detail.avatarUrl
                            )
                        },
                        onOpenMember = { member ->
                            start(
                                context = this,
                                chatId = member.userId,
                                chatType = 1,
                                chatName = member.name,
                                chatAvatar = member.avatarUrl
                            )
                        },
                        onOpenBoard = { board ->
                            BaDetailActivity.start(this, board.id)
                        },
                        onInviteBotToGroup = { detail ->
                            InviteBotToGroupActivity.start(
                                context = this,
                                botId = detail.chatId,
                                botName = detail.name
                            )
                        },
                        onLeaveGroup = {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            })
                            finish()
                        },
                        currentUserId = account.id,
                        viewModel = viewModel(
                            key = "${state.chatId}_${state.chatType}",
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return ConversationDetailViewModel(
                                        token = account.token,
                                        chatId = state.chatId,
                                        chatType = state.chatType
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
                AccountStorage.getInstance(this@ConversationDetailActivity)
                    .getCurrentAccount()
            }.getOrNull()

            if (account?.token.isNullOrEmpty()) {
                Toast.makeText(
                    this@ConversationDetailActivity,
                    "请先登录",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            accountState.value = account

            resolveDetail(account.token)
        }
    }

    private suspend fun resolveDetail(token: String) {
        val direct = readDirectChat()
        if (direct != null) {
            detailState.value = DetailState.Ready(
                chatId = direct.chatId,
                chatType = direct.chatType
            )
            return
        }

        val share = readShareParams()
        if (share == null) {
            detailState.value = DetailState.Error("无效的会话链接")
            Toast.makeText(this, "无效的会话链接", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ShareRepository()
            .getShareInfo(token, share.first, share.second)
            .onSuccess { info ->
                detailState.value = DetailState.Ready(
                    chatId = info.chatId,
                    chatType = info.chatType
                )
            }
            .onFailure { error ->
                detailState.value = DetailState.Error(
                    error.message ?: "分享链接解析失败"
                )
            }
    }

    private data class DirectChat(
        val chatId: String,
        val chatType: Int
    )

    private fun readDirectChat(): DirectChat? {
        val chatId = intent.getStringExtra("chat_id")
            ?.takeIf { it.isNotEmpty() }
            ?: intent.data?.getQueryParameter("id")?.takeIf { it.isNotEmpty() }
            ?: return null

        val chatType = when {
            intent.hasExtra("chat_type") -> {
                val t = intent.getIntExtra("chat_type", 1)
                if (t in 1..3) t else 1
            }
            else -> when (intent.data?.getQueryParameter("type")) {
                "group" -> 2
                "bot" -> 3
                else -> 1
            }
        }

        return DirectChat(chatId, chatType)
    }

    private fun readShareParams(): Pair<String, String>? {
        val uri = intent.data
        val key = intent.getStringExtra("share_key")
            ?: uri?.getQueryParameter("key")
            ?: uri?.getQueryParameter("share_key")
        val ts = intent.getStringExtra("share_ts")
            ?: uri?.getQueryParameter("ts")
            ?: uri?.getQueryParameter("share_ts")
        if (key.isNullOrEmpty() || ts.isNullOrEmpty()) return null
        return key to ts
    }

    sealed interface DetailState {
        data object Resolving : DetailState
        data class Ready(
            val chatId: String,
            val chatType: Int
        ) : DetailState
        data class Error(val message: String) : DetailState
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String = "",
            chatAvatar: String = ""
        ) {
            val intent = Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}