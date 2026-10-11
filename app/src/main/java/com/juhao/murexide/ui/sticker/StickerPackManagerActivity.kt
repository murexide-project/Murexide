package com.juhao.murexide.ui.sticker

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.theme.MurexideTheme

class StickerPackManagerActivity : androidx.activity.ComponentActivity() {
    companion object {
        const val EXTRA_TOKEN = "token"

        fun start(context: Context, token: String) {
            context.startActivity(
                Intent(context, StickerPackManagerActivity::class.java).apply {
                    putExtra(EXTRA_TOKEN, token)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                val vm: StickerPackManagerViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return StickerPackManagerViewModel(token) as T
                        }
                    }
                )
                StickerPackManagerScreen(
                    viewModel = vm,
                    onBack = { finish() }
                )
            }
        }
    }
}