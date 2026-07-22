package com.juhao.murexide.ui.mine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.juhao.murexide.ui.theme.MurexideTheme

class EditProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
        
        setContent {
            MurexideTheme {
                EditProfileScreen(
                    token = token,
                    onBackClick = { finish() },
                    onProfileSaved = {
                        setResult(RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TOKEN = "extra_token"

        fun createIntent(context: Context, token: String): Intent {
            return Intent(context, EditProfileActivity::class.java).apply {
                putExtra(EXTRA_TOKEN, token)
            }
        }

        fun start(context: Context, token: String) {
            context.startActivity(createIntent(context, token))
        }
    }
}
