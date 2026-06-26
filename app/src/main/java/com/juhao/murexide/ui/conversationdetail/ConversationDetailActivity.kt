package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.runBlocking

class ConversationDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId = intent.getStringExtra("chat_id") ?: return finish()
        val chatType = intent.getIntExtra("chat_type", 1)
        val chatName = intent.getStringExtra("chat_name") ?: ""
        val chatAvatar = intent.getStringExtra("chat_avatar") ?: ""

        val tokenStorage = TokenStorage(this)
        val token = runBlocking { tokenStorage.getToken() } ?: return finish()

        setContent {
            MurexideTheme {
                ConversationDetailScreen(
                    onBack = { finish() },
                    viewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ConversationDetailViewModel(
                                    token = token,
                                    chatId = chatId,
                                    chatType = chatType,
                                    fallbackName = chatName,
                                    fallbackAvatar = chatAvatar
                                ) as T
                            }
                        }
                    )
                )
            }
        }
    }

    companion object {
        fun start(
            context: Context,
            chatId: String,
            chatType: Int,
            chatName: String,
            chatAvatar: String
        ) {
            val intent = Intent(context, ConversationDetailActivity::class.java).apply {
                putExtra("chat_id", chatId)
                putExtra("chat_type", chatType)
                putExtra("chat_name", chatName)
                putExtra("chat_avatar", chatAvatar)
            }
            context.startActivity(intent)
        }
    }
}
