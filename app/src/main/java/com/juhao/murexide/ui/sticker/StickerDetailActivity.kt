package com.juhao.murexide.ui.sticker

import android.content.Context
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.theme.MurexideTheme

class StickerDetailActivity : androidx.activity.ComponentActivity() {
    companion object {
        const val EXTRA_PACK_ID = "pack_id"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_CURRENT_USER_ID = "current_user_id"
        const val EXTRA_STICKER_URL = "sticker_url"

        fun start(
            context: Context,
            packId: Long,
            token: String,
            currentUserId: String,
            stickerUrl: String? = null
        ) {
            context.startActivity(
                Intent(context, StickerDetailActivity::class.java).apply {
                    putExtra(EXTRA_PACK_ID, packId)
                    putExtra(EXTRA_TOKEN, token)
                    putExtra(EXTRA_CURRENT_USER_ID, currentUserId)
                    putExtra(EXTRA_STICKER_URL, stickerUrl)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val packId = intent.getLongExtra(EXTRA_PACK_ID, 0L)
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val currentUserId = intent.getStringExtra(EXTRA_CURRENT_USER_ID).orEmpty()
        val stickerUrl = intent.getStringExtra(EXTRA_STICKER_URL)
        
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                val vm: StickerDetailViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return StickerDetailViewModel(token, packId, currentUserId) as T
                        }
                    }
                )
                StickerDetailScreen(
                    viewModel = vm,
                    previewStickerUrl = stickerUrl,
                    onBack = { finish() }
                )
            }
        }
    }
}