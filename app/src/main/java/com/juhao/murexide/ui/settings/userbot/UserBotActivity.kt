package com.juhao.murexide.ui.settings.userbot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class UserBotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                UserBotScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, UserBotActivity::class.java).apply {
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserBotScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = settingsStorage.getUserBotEnabled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户 Bot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Switch(
                        checked = enabled,
                        modifier = Modifier.padding(end = 8.dp),
                        onCheckedChange = { value ->
                            enabled = value
                            scope.launch { settingsStorage.setUserBotEnabled(value) }
                        }
                    )
                }
            )
        }
    ) { padding ->
        UserBotPage(
            modifier = Modifier.padding(padding),
            enabled = enabled,
            onEnabledChange = { value ->
                enabled = value
                scope.launch { settingsStorage.setUserBotEnabled(value) }
            }
        )
    }
}