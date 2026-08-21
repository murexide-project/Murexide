package com.juhao.murexide.ui.community.ba

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.components.StyledIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    baName: String,
    onClose: () -> Unit,
    onPublished: () -> Unit,
    viewModel: CreatePostViewModel,
    postId: Int? = null,
    title: String,
    content: String,
    contentType: Int
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isEditMode = postId != null

    LaunchedEffect(Unit) {
        viewModel.onTitleChange(title)
        viewModel.onContentChange(content)
        viewModel.onContentTypeChange(contentType)
        viewModel.onEditModeChange(isEditMode)
        if (isEditMode) {
            viewModel.onPostIdChange(postId)
        }
    }

    LaunchedEffect(uiState.published) {
        if (uiState.published) onPublished()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isEditMode -> "编辑文章"
                            baName.isNotEmpty() -> "发布到 $baName"
                            else -> "发布文章"
                        }
                    )
                },
                navigationIcon = {
                    StyledIconButton(onClick = onClose) {
                        Icon(AppIcons.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    StyledIconButton(
                        onClick = { viewModel.publish() },
                        enabled = !uiState.isPublishing
                    ) {
                        if (uiState.isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(AppIcons.Check, contentDescription = "确定")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // 内容类型切换
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("内容格式", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = uiState.contentType == 1,
                    onClick = { viewModel.onContentTypeChange(1) },
                    label = { Text("文本") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.contentType == 2,
                    onClick = { viewModel.onContentTypeChange(2) },
                    label = { Text("Markdown") }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.content,
                onValueChange = viewModel::onContentChange,
                label = { Text(if (uiState.contentType == 2) "内容（支持 Markdown）" else "内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
            )
        }
    }
}
