package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class NewConversationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MurexideTheme {
                NewConversationScreen(
                    onBack = { finish() },
                    onConfirm = { chatId, chatType ->
                        ConversationDetailActivity.start(
                            context = this,
                            chatId = chatId,
                            chatType = chatType
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NewConversationActivity::class.java).apply {
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}

private data class ChatTypeOption(
    val type: Int,
    val label: String,
    val idLabel: String,
    val placeholder: String
)

private val chatTypeOptions = listOf(
    ChatTypeOption(1, "用户", "输入用户 ID", "请输入用户 ID"),
    ChatTypeOption(2, "群聊", "输入群 ID", "请输入群 ID"),
    ChatTypeOption(3, "机器人", "输入机器人 ID", "请输入机器人 ID")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationScreen(
    onBack: () -> Unit,
    onConfirm: (chatId: String, chatType: Int) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(chatTypeOptions.first().type) }
    var chatId by remember { mutableStateOf("") }

    val currentOption = chatTypeOptions.firstOrNull { it.type == selectedType } ?: chatTypeOptions.first()
    val trimmedId = chatId.trim()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("添加会话") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "选择会话类型",
                style = MaterialTheme.typography.titleMedium
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                chatTypeOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = selectedType == option.type,
                        onClick = { selectedType = option.type },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = chatTypeOptions.size
                        )
                    ) {
                        Text(option.label)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = currentOption.idLabel,
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(currentOption.placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (trimmedId.isBlank()) return@Button
                        onConfirm(trimmedId, selectedType)
                },
                enabled = trimmedId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("添加", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}