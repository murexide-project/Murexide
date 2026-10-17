package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

/** 机器人编辑页（仅创建者可用）。 */
class BotSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val botId = intent.getStringExtra(EXTRA_BOT_ID) ?: return finish()
        val botName = intent.getStringExtra(EXTRA_BOT_NAME) ?: ""
        val botAvatar = intent.getStringExtra(EXTRA_BOT_AVATAR) ?: ""

        val accountStorage = AccountStorage.getInstance(this)
        val tokenState = mutableStateOf<String?>(null)

        setContent {
            MurexideTheme {
                val token = tokenState.value
                if (token == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    BotSettingsScreen(
                        onBack = { finish() },
                        onSaved = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        viewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return BotSettingsViewModel(
                                        token = token,
                                        botId = botId,
                                        fallbackName = botName,
                                        fallbackAvatar = botAvatar
                                    ) as T
                                }
                            }
                        )
                    )
                }
            }
        }

        lifecycleScope.launch {
            val token = runCatching { accountStorage.getCurrentToken() }.getOrNull()
            if (token == null) {
                finish()
                return@launch
            }
            tokenState.value = token
        }
    }

    companion object {
        private const val EXTRA_BOT_ID = "bot_id"
        private const val EXTRA_BOT_NAME = "bot_name"
        private const val EXTRA_BOT_AVATAR = "bot_avatar"

        fun intent(
            context: Context,
            botId: String,
            botName: String,
            botAvatar: String
        ): Intent = Intent(context, BotSettingsActivity::class.java).apply {
            putExtra(EXTRA_BOT_ID, botId)
            putExtra(EXTRA_BOT_NAME, botName)
            putExtra(EXTRA_BOT_AVATAR, botAvatar)
        }

        fun start(
            context: Context,
            botId: String,
            botName: String,
            botAvatar: String
        ) {
            context.startActivity(intent(context, botId, botName, botAvatar))
        }
    }
}
