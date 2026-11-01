package com.juhao.murexide.ui.settings.chatbackground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.repository.ChatBackgroundRepository
import com.juhao.murexide.ui.theme.MurexideTheme

class ChatBackgroundActivity : ComponentActivity() {
    private val viewModel: ChatBackgroundViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatBackgroundViewModel(
                    application = application,
                    repository = ChatBackgroundRepository()
                ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MurexideTheme {
                ChatBackgroundScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}