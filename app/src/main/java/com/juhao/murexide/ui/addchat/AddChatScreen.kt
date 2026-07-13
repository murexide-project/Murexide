package com.juhao.murexide.ui.addchat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.StyledIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChatScreen(
    onBackClick: () -> Unit,
    onEnterChat: (ConversationDetail) -> Unit,
    viewModel: AddChatViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message, uiState.error) {
        val msg = uiState.message ?: uiState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val typeLabel = when (uiState.chatType) {
        2 -> "群聊"
        3 -> "机器人"
        else -> "用户"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("添加$typeLabel") },
                navigationIcon = {
                    StyledIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                uiState.isLoading && uiState.detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val detail = uiState.detail!!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Avatar(url = detail.avatarUrl, size = 88.dp)

                        Spacer(Modifier.height(16.dp))

                        Text(
                            detail.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            "ID: ${detail.chatId}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (detail.introduction.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                detail.introduction,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        detail.memberCount?.let { count ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$count 人",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        if (uiState.isAdded) {
                            Button(
                                onClick = { onEnterChat(detail) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Chat, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("进入聊天")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.addChat() },
                                enabled = !uiState.isAdding,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isAdding) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("添加")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
