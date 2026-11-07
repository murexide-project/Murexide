package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.runBlocking

class DiskActivity : ComponentActivity() {
    private var token: String = ""
    private var chatId: String = ""
    private var canManage: Boolean = false

    private val viewModel: DiskViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DiskViewModel(token, chatId) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        canManage = intent.getBooleanExtra(EXTRA_CAN_MANAGE, false)
        token = runBlocking { AccountStorage.getInstance(this@DiskActivity).getCurrentToken() }.orEmpty()
        setContent {
            MurexideTheme {
                DiskScreen(
                    viewModel = viewModel,
                    token = token,
                    canManage = canManage,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CHAT_ID = "chat_id"
        private const val EXTRA_CAN_MANAGE = "can_manage"

        fun start(context: Context, chatId: String, canManage: Boolean) {
            context.startActivity(
                Intent(context, DiskActivity::class.java)
                    .putExtra(EXTRA_CHAT_ID, chatId)
                    .putExtra(EXTRA_CAN_MANAGE, canManage)
            )
        }
    }
}