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

class GroupTagActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val groupId = intent.getStringExtra("group_id") ?: return finish()
        val groupName = intent.getStringExtra("group_name").orEmpty()
        val isAdmin = intent.getBooleanExtra("is_admin", false)

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
                    GroupTagScreen(
                        onBack = { finish() },
                        isAdmin = isAdmin,
                        viewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    return GroupTagViewModel(
                                        token = token,
                                        groupId = groupId,
                                        groupName = groupName
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
        fun start(
            context: Context,
            groupId: String,
            groupName: String,
            isAdmin: Boolean
        ) {
            val intent = Intent(context, GroupTagActivity::class.java).apply {
                putExtra("group_id", groupId)
                putExtra("group_name", groupName)
                putExtra("is_admin", isAdmin)
            }
            context.startActivity(intent)
        }
    }
}
