package com.juhao.murexide.ui.chat

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.components.Avatar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.ui.components.fullImagePreviewItem
import com.juhao.murexide.ui.components.imageMessagePreviewItem
import com.juhao.murexide.ui.components.showImageViewer
import com.juhao.murexide.ui.chat.components.MessageBubble
import com.juhao.murexide.ui.chat.components.BoardPanel
import com.juhao.murexide.ui.chat.components.MessageInput
import com.juhao.murexide.ui.chat.components.EmojiPanel
import com.juhao.murexide.ui.chat.components.InstructionPanel
import com.juhao.murexide.ui.chat.components.InstructionFormDialog
import com.juhao.murexide.ui.chat.components.UploadProgressBar
import com.juhao.murexide.ui.chat.components.ScreenshotBottomSheet
import com.juhao.murexide.ui.chat.components.GroupMemberSheet
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.MessageItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.draw.clip
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity
import com.juhao.murexide.ui.conversationdetail.GroupSettingsActivity
import com.juhao.murexide.ui.conversationdetail.groupmember.GroupMemberActivity
import com.juhao.murexide.ui.webview.WebViewActivity
import com.juhao.murexide.utils.NotificationHelper
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

private enum class ChatInputPanel {
    Emoji,
    Instruction
}

private val DefaultInputPanelHeight = 280.dp

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class, ExperimentalHazeMaterialsApi::class
)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatType: Int,
    chatName: String,
    chatAvatar: String,
    chatId: String,
    onBackClick: () -> Unit = {},
    bigScreenMode: Boolean = false,
    viewModel: ChatViewModel
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val uiState by viewModel.uiState.collectAsState()
    val expressions by viewModel.stickerPanel.collectAsState()
    val instructionPanel = uiState.instructionPanel
    val instructionForm by viewModel.instructionForm.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    var pendingInputPanel by remember { mutableStateOf<ChatInputPanel?>(null) }
    var isReturningToKeyboard by remember { mutableStateOf(false) }
    var inputPanelHeight by remember { mutableStateOf(DefaultInputPanelHeight) }

    fun showInputPanel(panel: ChatInputPanel) {
        when (panel) {
            ChatInputPanel.Emoji -> {
                if (!expressions.isVisible) viewModel.toggleStickerPanel()
            }
            ChatInputPanel.Instruction -> {
                if (!instructionPanel.isVisible) viewModel.toggleInstructionPanel()
            }
        }
    }

    fun returnToKeyboard() {
        pendingInputPanel = null
        isReturningToKeyboard = true
        viewModel.hideStickerPanel()
        viewModel.hideInstructionPanel()
    }

    fun requestInputPanel(panel: ChatInputPanel) {
        val isCurrentPanel = when (panel) {
            ChatInputPanel.Emoji -> expressions.isVisible
            ChatInputPanel.Instruction -> instructionPanel.isVisible
        }

        if (isCurrentPanel || pendingInputPanel == panel) {
            returnToKeyboard()
            return
        }

        val keyboardHeightPx = maxOf(imeBottomPx, imeTargetBottomPx)
        if (keyboardHeightPx > 0) {
            inputPanelHeight = with(density) { keyboardHeightPx.toDp() }
        }

        isReturningToKeyboard = false
        pendingInputPanel = panel
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(
        pendingInputPanel,
        imeBottomPx,
        imeTargetBottomPx,
        expressions.isVisible,
        instructionPanel.isVisible
    ) {
        val panel = pendingInputPanel ?: return@LaunchedEffect
        if (imeBottomPx != 0 || imeTargetBottomPx != 0) return@LaunchedEffect

        val isPanelVisible = when (panel) {
            ChatInputPanel.Emoji -> expressions.isVisible
            ChatInputPanel.Instruction -> instructionPanel.isVisible
        }
        if (isPanelVisible) {
            pendingInputPanel = null
        } else {
            showInputPanel(panel)
        }
    }

    LaunchedEffect(isReturningToKeyboard) {
        if (!isReturningToKeyboard) return@LaunchedEffect
        inputFocusRequester.requestFocus()
        keyboardController?.show()
        delay(1_000)
        isReturningToKeyboard = false
    }

    LaunchedEffect(isReturningToKeyboard, imeBottomPx, imeTargetBottomPx) {
        if (
            isReturningToKeyboard &&
            imeTargetBottomPx > 0 &&
            imeBottomPx >= imeTargetBottomPx
        ) {
            inputPanelHeight = with(density) { imeTargetBottomPx.toDp() }
            isReturningToKeyboard = false
        }
    }

    var showMenuMsgId by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showEditNickNameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val recallDialog by viewModel.recallDialog.collectAsState()

    val listState = rememberLazyListState()
    var showScrollToBottom by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableIntStateOf(0) }
    var firstMessageId by remember { mutableStateOf<String?>(null) }

    val downloadingFiles by viewModel.downloadingFiles.collectAsState()

    val settingsStorage = remember { SettingsStorage(context) }
    val avatarFollowEnabled by settingsStorage.avatarFollowFlow.collectAsState(initial = false)
    val bubbleCornerRadius by settingsStorage.bubbleCornerRadiusFlow.collectAsState(initial = 18f)
    val bubbleOpacity by settingsStorage.bubbleOpacityFlow.collectAsState(initial = 0.9f)
    val showMyBubbleAvatarSetting by settingsStorage.showMyBubbleAvatarFlow.collectAsState(initial = true)
    val showMsgTagsSetting by settingsStorage.showMsgTagsFlow.collectAsState(initial = false)
    val backgroundOpacity by settingsStorage.backgroundOpacityFlow.collectAsState(initial = 0.5f)
    
    val hazeState = remember { HazeState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAndSendImage(it, context) }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAndSendVideo(it, context) }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAndSendFile(it, context) }
    }
    
    fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }
    
    fun openVideoPicker() {
        videoPickerLauncher.launch("video/*")
    }
    
    fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    val selectionMode = uiState.selectionMode
    val selectedMessages = uiState.selectedMessages
    
    BackHandler(enabled = selectionMode) {
        viewModel.exitSelectionMode()
    }

    BackHandler(enabled = !selectionMode && showMenuMsgId != null) {
        showMenuMsgId = null
    }
    
    val displayItems by remember {
        derivedStateOf {
            computeDisplayItems(
                messages = uiState.messages,
                chatType = chatType,
                ownerId = uiState.ownerId,
                adminIds = uiState.adminIds
            )
        }
    }

    val floatingAvatarState by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || displayItems.isEmpty() || !avatarFollowEnabled) {
                Triple(false, "", false)
            } else {
                val topVisibleIndex = visibleItems.first().index
                val displayItem = displayItems.getOrNull(topVisibleIndex) ?: return@derivedStateOf Triple(false, "", false)
                val message = displayItem.message
    
                val itemHeightDp = with(density) { visibleItems.first().size.toDp() }.value
                val visibleHeightDp = with(density) {
                    (visibleItems.first().size + visibleItems.first().offset.coerceAtMost(0)).toDp()
                }.value
    
                val hasEnoughSpace = visibleHeightDp >= 44 && itemHeightDp >= 44
    
                if (hasEnoughSpace) {
                    Triple(true, message.senderAvatar, message.isMine)
                } else if (!displayItem.isLastFromSender) {
                    Triple(true, message.senderAvatar, message.isMine)
                } else {
                    Triple(false, "", false)
                }
            }
        }
    }

    val showFloatingAvatar = floatingAvatarState.first
    val floatingAvatarUrl = floatingAvatarState.second
    val floatingAvatarIsMine = floatingAvatarState.third

    val topVisibleMessage by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.let { displayItems.getOrNull(it.index)?.message }
        }
    }

    val topVisibleMessageId = topVisibleMessage?.msgId

    LaunchedEffect(Unit) {
        NotificationHelper.clearNotification(context, chatId)
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.buttonEvent.collect { event ->
            when (event) {
                is ButtonEvent.OpenUrl -> {
                    val intent = Intent(context, WebViewActivity::class.java).apply {
                        putExtra(WebViewActivity.EXTRA_URL, event.url)
                    }
                    context.startActivity(intent)
                }
                is ButtonEvent.CopyText -> {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("button", event.text))
                    )
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            val shouldLoadMore = if (visibleItems.isNotEmpty()) {
                val lastVisibleIndex = visibleItems.last().index
                val totalItems = layoutInfo.totalItemsCount
                lastVisibleIndex >= totalItems - 5 && uiState.hasMore && !uiState.isLoadingMore && !uiState.isRefreshing
            } else {
                false
            }

            val atBottom = if (visibleItems.isNotEmpty()) {
                val firstVisibleIndex = visibleItems.first().index
                firstVisibleIndex == 0
            } else {
                true
            }

            Pair(shouldLoadMore, atBottom)
        }
            .collect { (shouldLoadMore, atBottom) ->
                if (shouldLoadMore) {
                    viewModel.loadMore()
                }

                showScrollToBottom = !atBottom
                if (atBottom) {
                    unreadCount = 0
                }
            }
    }

    LaunchedEffect(Unit) {
        var lastMsgId: String? = null
        var pendingCount = 0

        snapshotFlow { uiState.messages.firstOrNull() }
            .collect { message: MessageItem? ->
                val msgId = message?.msgId
                if (message == null) return@collect
                if (msgId == lastMsgId) return@collect
                lastMsgId = msgId

                pendingCount++

                if (firstMessageId == null) {
                    firstMessageId = msgId
                    pendingCount = 0
                    return@collect
                }

                if (msgId == firstMessageId) {
                    pendingCount = 0
                    return@collect
                }

                val isAtBottom = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.index == 0

                firstMessageId = msgId

                if (isAtBottom && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(0)
                    unreadCount = 0
                    pendingCount = 0
                } else {
                    if (!message.isMine) {
                        unreadCount += pendingCount
                    }
                    pendingCount = 0
                }
            }
    }

    val scrollToBottom: () -> Unit = {
        scope.launch {
            listState.animateScrollToItem(0)
            unreadCount = 0
            if (uiState.messages.isNotEmpty()) {
                firstMessageId = uiState.messages.first().msgId
            }
        }
    }
    
    var showScreenshotSheet by remember { mutableStateOf(false) }
    
    if (showScreenshotSheet) {
        val orderedSelected = uiState.messages
            .filter { it in selectedMessages }
            .reversed()
            
        ScreenshotBottomSheet(
            messages = orderedSelected,
            chatName = chatName,
            chatAvatar = chatAvatar,
            onDismiss = { showScreenshotSheet = false }
        )
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin().copy(
                                noiseFactor = 0f
                            ),
                            block = null
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )

                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(200))
                    },
                    label = "top_bar_transition"
                ) { isSelectionMode ->
                    if (isSelectionMode) {
                        TopAppBar(
                            title = {
                                AnimatedContent(
                                    targetState = selectedMessages.size,
                                    transitionSpec = {
                                        if (targetState < initialState) {
                                            slideInVertically(
                                                initialOffsetY = { fullHeight -> fullHeight },
                                                animationSpec = tween(200)
                                            ) togetherWith slideOutVertically(
                                                targetOffsetY = { fullHeight -> -fullHeight },
                                                animationSpec = tween(200)
                                            )
                                        } else {
                                            slideInVertically(
                                                initialOffsetY = { fullHeight -> -fullHeight },
                                                animationSpec = tween(200)
                                            ) togetherWith slideOutVertically(
                                                targetOffsetY = { fullHeight -> fullHeight },
                                                animationSpec = tween(200)
                                            )
                                        }
                                    },
                                    label = "selected_count"
                                ) { count ->
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { viewModel.exitSelectionMode() }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            ),
                            actions = {
                                IconButton(onClick = { viewModel.recallSelectedMessages() }) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤回")
                                }
                                if (selectedMessages.size == 1) {
                                    val message = selectedMessages.firstOrNull()
                                    message?.let { 
                                        if (it.content.isNotBlank()) {
                                            IconButton(onClick = { 
                                                scope.launch {
                                                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("msg", it.content)))
                                                }
                                                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                                                viewModel.exitSelectionMode()
                                            }) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                                            }
                                        }
                                    }
                                }
                                IconButton(onClick = { showScreenshotSheet = true }) {
                                    Icon(Icons.Rounded.Screenshot, contentDescription = "截图")
                                }
                            }
                        )
                    } else {
                        Column {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .clickable {
                                                ConversationDetailActivity.start(
                                                    context = context,
                                                    chatId = viewModel.chatId,
                                                    chatType = chatType,
                                                    chatName = chatName,
                                                    chatAvatar = chatAvatar
                                                )
                                            }
                                    ) {
                                        Avatar(
                                            url = chatAvatar,
                                            size = 36.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = chatName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (chatType == 2 && uiState.memberCount != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${uiState.memberCount} 位成员",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            if (chatType == 3 && uiState.usageCount != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${uiState.usageCount} 人使用",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent
                                ),
                                actions = {
                                    if (uiState.boardPanel.boards.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.toggleBoard() }) {
                                            Icon(
                                                imageVector = if (uiState.boardPanel.isExpanded) {
                                                    Icons.Rounded.KeyboardArrowUp
                                                } else {
                                                    Icons.Rounded.KeyboardArrowDown
                                                },
                                                contentDescription = if (uiState.boardPanel.isExpanded) "收起看板" else "展开看板"
                                            )
                                        }
                                    }
                                    Box {
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("刷新") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    viewModel.refresh()
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Refresh,
                                                        contentDescription = null
                                                    )
                                                }
                                            )
                                            if (chatType == 2 && uiState.permissionLevel >= 2) {
                                                DropdownMenuItem(
                                                    text = { Text("群聊设置") },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        GroupSettingsActivity.start(
                                                            context = context,
                                                            groupId = chatId,
                                                            groupName = chatName,
                                                            groupAvatar = chatAvatar
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.Settings,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (chatType == 2) {
                                                DropdownMenuItem(
                                                    text = { Text("群成员列表") },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        val intent = Intent(context, GroupMemberActivity::class.java).apply {
                                                            putExtra("group_id", chatId)
                                                            putExtra("my_permission", uiState.permissionLevel)
                                                        }
                                                        context.startActivity(intent)
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.Group,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("我的群名称") },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        showEditNickNameDialog = true
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.Edit,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("会话详情") },
                                                onClick = {
                                                    showMoreMenu = false
                                                    ConversationDetailActivity.start(
                                                        context = context,
                                                        chatId = viewModel.chatId,
                                                        chatType = chatType,
                                                        chatName = chatName,
                                                        chatAvatar = chatAvatar
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Info,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            )

                                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = when (chatType) {
                                                            1 -> "删除好友"
                                                            2 -> "退出群聊"
                                                            else -> "删除机器人"
                                                        },
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                },
                                                onClick = {
                                                    showMoreMenu = false
                                                    showDeleteConfirm = true
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.Logout,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            )
                                        }

                                        IconButton(onClick = {
                                            showMoreMenu = true
                                        }) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = "更多"
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    if (!bigScreenMode) {
                                        IconButton(onClick = onBackClick) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.ArrowBack,
                                                contentDescription = "返回"
                                            )
                                        }
                                    }
                                }
                            )

                            AnimatedVisibility(
                                visible = uiState.boardPanel.isExpanded && uiState.boardPanel.boards.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                BoardPanel(
                                    boards = uiState.boardPanel.boards,
                                    onImageClick = { url ->
                                        showImageViewer(
                                            context = context,
                                            images = listOf(fullImagePreviewItem(url))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin().copy(
                                noiseFactor = 0f
                            ),
                            block = null
                        )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(200))
                    },
                    label = "bottom_bar_transition"
                ) { isSelectionMode ->
                    if (isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .navigationBarsPadding(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val message = selectedMessages.firstOrNull()
                            message?.let { 
                                Button(
                                    onClick = { 
                                        viewModel.setReplyTo(it)
                                        viewModel.exitSelectionMode()
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = selectedMessages.size == 1 && !it.isRecalled
                                ) {
                                    Icon(Icons.Rounded.FormatQuote, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("引用")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            TextButton(
                                onClick = { /* 转发选中消息 */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("转发")
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isUploading) {
                                UploadProgressBar(
                                    progress = uiState.uploadProgress,
                                    imagePath = uiState.uploadImagePath ?: "",
                                    onCancel = { viewModel.cancelUpload() }
                                )
                            }

                            // 引用
                            AnimatedVisibility(
                                visible = uiState.replyTo != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(32.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = uiState.replyTo?.senderName ?: "用户",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = uiState.replyTo?.getDisplayContent() ?: "消息",
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearReplyTo() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "取消引用",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // 编辑
                            AnimatedVisibility(
                                visible = uiState.editingMessage != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "编辑中……",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = uiState.editingMessage?.getDisplayContent() ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.cancelEdit() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "取消编辑",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // 指令
                            AnimatedVisibility(
                                visible = uiState.pendingCommandId != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "指令: ${uiState.pendingCommandName ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearPendingCommand() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "取消指令",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            MessageInput(
                                inputText = uiState.inputText,
                                sendType = uiState.sendType,
                                isSending = uiState.isSending,
                                onTextChange = { viewModel.updateInputText(it) },
                                onSendClick = { viewModel.sendMessage() },
                                onSendWithType = { type -> viewModel.sendMessage(type) },
                                onAddImageClick = { openImagePicker() },
                                onAddVideoClick = { openVideoPicker() },
                                onAddFileClick = { openFilePicker() },
                                isEmojiPanelVisible =
                                    expressions.isVisible ||
                                        pendingInputPanel == ChatInputPanel.Emoji,
                                onEmojiClick = { requestInputPanel(ChatInputPanel.Emoji) },
                                isInstructionPanelVisible =
                                    instructionPanel.isVisible ||
                                        pendingInputPanel == ChatInputPanel.Instruction,
                                onInstructionClick = {
                                    requestInputPanel(ChatInputPanel.Instruction)
                                },
                                mentionNames = uiState.mentions.keys,
                                onMentionTriggered = { pos ->
                                    if (chatType == 2) {
                                        viewModel.showMentionPicker(pos)
                                    }
                                },
                                focusRequester = inputFocusRequester,
                                onInputFocused = {
                                    if (
                                        !isReturningToKeyboard &&
                                        (pendingInputPanel != null ||
                                            expressions.isVisible ||
                                            instructionPanel.isVisible)
                                    ) {
                                        returnToKeyboard()
                                    }
                                }
                            )

                            BackHandler(
                                enabled = pendingInputPanel != null ||
                                    expressions.isVisible ||
                                    instructionPanel.isVisible
                            ) {
                                pendingInputPanel = null
                                viewModel.hideStickerPanel()
                                viewModel.hideInstructionPanel()
                            }

                            when {
                                pendingInputPanel != null || isReturningToKeyboard -> {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(inputPanelHeight)
                                    )
                                }
                                expressions.isVisible -> {
                                    EmojiPanel(
                                        expressions = expressions.expressions,
                                        isLoading = expressions.isLoading,
                                        onExpressionClick = { expression ->
                                            viewModel.sendExpression(expression)
                                        },
                                        onStickerItemClick = { stickerItem ->
                                            viewModel.sendStickerItem(stickerItem)
                                        },
                                        stickerPacks = expressions.stickerPacks,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(inputPanelHeight)
                                    )
                                }
                                instructionPanel.isVisible -> {
                                    InstructionPanel(
                                        bots = instructionPanel.bots,
                                        instructions = instructionPanel.instructions,
                                        isLoading = instructionPanel.isLoading,
                                        onInstructionClick = { viewModel.onInstructionClick(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(inputPanelHeight)
                                    )
                                }
                                else -> {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsBottomHeight(
                                                WindowInsets.navigationBars.union(WindowInsets.ime)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            uiState.backgroundUrl?.takeIf { it.isNotEmpty() }?.let { bgUrl ->
                val bgRequest = remember(bgUrl) {
                    ImageRequest.Builder(context)
                        .data(bgUrl)
                        .apply {
                            if (bgUrl.contains("jwznb.com")) {
                                setHeader("Referer", "https://myapp.jwznb.com")
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = bgRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(backgroundOpacity),
                    contentScale = ContentScale.Crop
                )
            }

            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding() + 24.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp)
                    )
                }
            } else if (uiState.error != null && uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "加载失败",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.error ?: "未知错误",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重试")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = displayItems,
                        key = { it.message.msgId }
                    ) { item ->
                        val message = item.message
                        
                        val isTopVisibleItem = message.msgId == topVisibleMessageId

                        val shouldShowItemAvatar = if (isTopVisibleItem) {
                            !showFloatingAvatar && ((item.isLastFromSender && avatarFollowEnabled) || item.isFirstFromSender)
                        } else {
                            item.isFirstFromSender
                        }

                        val avatarAlignment =
                            if (isTopVisibleItem && shouldShowItemAvatar && avatarFollowEnabled) {
                                if (item.isLastFromSender) Alignment.Top else Alignment.Bottom
                            } else {
                                Alignment.Bottom
                            }

                        MessageBubble(
                            message = message,
                            roleLabel = item.roleLabel,
                            onRecall = { viewModel.showRecallDialog(message.msgId) },
                            onEdit = { viewModel.startEditMessage(message) },
                            onReply = { viewModel.setReplyTo(message) },
                            isAdmin = uiState.isAdmin,
                            isLastFromSender = item.isLastFromSender,
                            isFirstFromSender = item.isFirstFromSender,
                            showAvatar = shouldShowItemAvatar,
                            showTags = showMsgTagsSetting,
                            showMyBubbleAvatarSetting = showMyBubbleAvatarSetting,
                            bubbleOpacity = bubbleOpacity,
                            bubbleCornerRadius = bubbleCornerRadius,
                            avatarAlignment = avatarAlignment,
                            isSelectionMode = selectionMode,
                            isSelected = message in selectedMessages,
                            onLongPress = { msg -> viewModel.enterSelectionMode(msg) },
                            onClickInSelectionMode = { msg -> viewModel.toggleMessageSelection(msg) },
                            showMenu = showMenuMsgId == message.msgId && !selectionMode,
                            showMenuMsgId = showMenuMsgId,
                            showMenuChanged = { msgId ->
                                if (!selectionMode) {
                                    showMenuMsgId = msgId
                                }
                            },
                            onImageClick = { msg ->
                                if (!selectionMode) {
                                    msg.imageUrl?.let { url ->
                                        val allImages = uiState.messages
                                            .filter { !it.isRecalled }
                                            .mapNotNull { imageMessage ->
                                                imageMessage.imageUrl
                                                    ?.takeIf { it.isNotEmpty() }
                                                    ?.let { imageUrl ->
                                                        if (imageMessage.contentType == MessageItem.CONTENT_TYPE_IMAGE) {
                                                            imageMessagePreviewItem(imageUrl)
                                                        } else {
                                                            fullImagePreviewItem(imageUrl)
                                                        }
                                                    }
                                            }
                                            .reversed()
                            
                                        if (allImages.isNotEmpty()) {
                                            val index = allImages.indexOfFirst { it.originalUrl == url }
                                            showImageViewer(
                                                context = context,
                                                images = allImages,
                                                initialIndex = index.coerceAtLeast(0)
                                            )
                                        }
                                    }
                                } else {
                                    viewModel.toggleMessageSelection(msg)
                                }
                            },
                            onMarkdownImageClick = { url ->
                                showImageViewer(
                                    context = context,
                                    images = listOf(fullImagePreviewItem(url))
                                )
                            },
                            onAvatarClick = {
                                ConversationDetailActivity.start(
                                    context = context,
                                    chatId = message.senderId,
                                    chatType = message.senderType,
                                    chatName = message.senderName,
                                    chatAvatar = message.senderAvatar
                                )
                            },
                            onAvatarLongClick = {
                                if (chatType == 2 && !message.isMine) {
                                    viewModel.mentionUser(message.senderId, message.senderName)
                                }
                            },
                            downloadProgress = downloadingFiles[message.msgId],
                            isDownloaded = message.msgId in uiState.downloadedFiles,
                            onDownloadClick = { msg ->
                                if (!selectionMode) {
                                    viewModel.startDownload(msg, context)
                                } else {
                                    viewModel.toggleMessageSelection(msg)
                                }
                            },
                            onButtonClick = { msg, button ->
                                if (!selectionMode) {
                                    viewModel.onButtonClick(msg, button)
                                } else {
                                    viewModel.toggleMessageSelection(msg)
                                }
                            },
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                    }
                }

                AnimatedScrollToBottomButton(
                    visible = showScrollToBottom,
                    unreadCount = unreadCount,
                    onClick = scrollToBottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .padding(12.dp)
                )

                val targetAlpha = when {
                    showMenuMsgId != null && topVisibleMessageId != showMenuMsgId -> 0.5f
                    topVisibleMessage?.isRecalled == true -> 0.6f
                    else -> 1f
                }
                
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 300),
                    label = "floating_avatar_alpha"
                )

                if (showFloatingAvatar && (!floatingAvatarIsMine || showMyBubbleAvatarSetting)) {
                    Column(
                        modifier = Modifier
                            .alpha(animatedAlpha)
                            .align(if (floatingAvatarIsMine) Alignment.BottomEnd else Alignment.BottomStart)
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Avatar(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        ConversationDetailActivity.start(
                                            context = context,
                                            chatId = topVisibleMessage?.senderId ?: "0",
                                            chatType = topVisibleMessage?.senderType ?: 0,
                                            chatName = topVisibleMessage?.senderName ?: "",
                                            chatAvatar = topVisibleMessage?.senderAvatar ?: ""
                                        )
                                    },
                                    onLongClick = {
                                        topVisibleMessage?.let { message ->
                                            if (chatType == 2 && !message.isMine) {
                                                viewModel.mentionUser(
                                                    message.senderId,
                                                    message.senderName
                                                )
                                            }
                                        }
                                    }
                                ),
                            url = floatingAvatarUrl,
                            size = 36.dp
                        )
                    }
                }
            }
        }
    }

    if (uiState.mentionPicker.isVisible) {
        GroupMemberSheet(
            title = "选择要@的成员",
            members = uiState.groupMembers.members,
            isLoading = uiState.groupMembers.isLoading,
            hasMore = uiState.groupMembers.hasMore,
            onLoadMore = { viewModel.loadGroupMembers() },
            onMemberClick = { member -> viewModel.selectMention(member) },
            onDismiss = { viewModel.hideMentionPicker() }
        )
    }

    if (recallDialog.isOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.hideRecallDialog() },
            title = { Text("撤回消息") },
            text = { Text("确定要撤回这条消息吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.recallMessage() }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideRecallDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    instructionForm?.let { item ->
        InstructionFormDialog(
            item = item,
            onDismiss = { viewModel.dismissInstructionForm() },
            onSubmit = { formJson -> viewModel.submitInstructionForm(item, formJson) }
        )
    }

    val conversationDetailRepository = ConversationDetailRepository()

    if (showEditNickNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNickNameDialog = false },
            icon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
            title = { Text("我的群名称") },
            text = {
                OutlinedTextField(
                    value = uiState.myGroupNickname ?: "",
                    onValueChange = viewModel::updateNickName,
                    label = { Text("群名称（为空复原）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditNickNameDialog = false
                    scope.launch {
                        conversationDetailRepository.editMyGroupNickname(
                            viewModel.token,
                            chatId,
                            uiState.myGroupNickname ?: ""
                        ).onSuccess {
                            Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "修改失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNickNameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val actionText = when (chatType) {
            1 -> "删除该好友"
            2 -> "退出该群聊"
            else -> "删除该机器人"
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(actionText) },
            text = {
                Text("确定要${actionText}吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteFriend(onSuccess = { onBackClick() })
                }) {
                    Text(actionText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun AnimatedScrollToBottomButton(
    visible: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_alpha"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "scroll_button_scale"
    )

    Box(
        modifier = modifier
            .wrapContentSize()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            }
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        ) {
            SmallFloatingActionButton(
                onClick = onClick,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "滚动到底部",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
