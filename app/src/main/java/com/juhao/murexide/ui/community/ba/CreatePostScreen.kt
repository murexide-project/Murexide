package com.juhao.murexide.ui.community.ba

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.launch

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var uploading by remember { mutableStateOf(false) }

    var contentFieldValue by remember {
        mutableStateOf(TextFieldValue(content, TextRange(content.length)))
    }
    var contentInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onTitleChange(title)
        viewModel.onContentChange(content)
        viewModel.onContentTypeChange(contentType)
        viewModel.onEditModeChange(isEditMode)
        if (isEditMode) {
            viewModel.onPostIdChange(postId)
        }
    }

    LaunchedEffect(Unit) {
        if (!contentInitialized) {
            contentFieldValue = TextFieldValue(content, TextRange(content.length))
            contentInitialized = true
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

    fun insertAtCursor(text: String) {
        val start = contentFieldValue.selection.start.coerceIn(0, contentFieldValue.text.length)
        val end = contentFieldValue.selection.end.coerceIn(0, contentFieldValue.text.length)
        val newText = contentFieldValue.text.replaceRange(start, end, text)
        contentFieldValue = TextFieldValue(newText, TextRange(start + text.length))
        viewModel.onContentChange(newText)
        focusRequester.requestFocus()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (viewModel.token.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("请先登录") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            uploading = true
            QiniuUploader(
                context = context,
                userToken = viewModel.token,
                uploadType = 1
            ).uploadFromUri(context, uri)
                .onSuccess { response ->
                    insertAtCursor("![](https://chat-img.jwznb.com/${response.key})")
                }
                .onFailure { error ->
                    snackbarHostState.showSnackbar(error.message ?: "图片上传失败")
                }
            uploading = false
        }
    }

    StyledScaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            IconButton(onClick = onClose) {
                Icon(AppIcons.Close, contentDescription = "关闭")
            }
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    if (uiState.contentType == 2) {
                        IconButton(onClick = { insertAtCursor("# ") }) {
                            Icon(AppIcons.Tag, contentDescription = "标题")
                        }
                        IconButton(onClick = { insertAtCursor("**粗体**") }) {
                            Icon(AppIcons.FormatBold, contentDescription = "粗体")
                        }
                        IconButton(onClick = { insertAtCursor("[]()") }) {
                            Icon(AppIcons.Link, contentDescription = "链接")
                        }
                        IconButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !uploading
                        ) {
                            if (uploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(AppIcons.Image, contentDescription = "插入图片")
                            }
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { if (!uiState.isPublishing) viewModel.publish() },
                        containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
                    ) {
                        if (uiState.isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(AppIcons.Check, contentDescription = "发布")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
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
                value = contentFieldValue,
                onValueChange = { newValue ->
                    contentFieldValue = newValue
                    viewModel.onContentChange(newValue.text)
                },
                label = { Text(if (uiState.contentType == 2) "内容（支持 Markdown）" else "内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .focusRequester(focusRequester)
            )
        }
    }
}