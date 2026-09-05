package com.juhao.murexide.ui.chat

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.Manifest
import android.content.ClipData
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.WindowInsets as AndroidWindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.ExpressiveDropdownMenu
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.ui.components.fullImagePreviewItem
import com.juhao.murexide.ui.components.imageMessagePreviewItem
import com.juhao.murexide.ui.components.videoMessagePreviewItem
import com.juhao.murexide.ui.components.MediaViewerPagination
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
import com.juhao.murexide.data.ForwardTarget
import com.juhao.murexide.data.resolveStickerMessageUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.ui.chat.components.EditHistoryDialog
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity
import com.juhao.murexide.ui.components.handleStaticHtmlLink
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.utils.NotificationHelper
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.liquidGlass
import com.juhao.murexide.ui.theme.liquidGlassHighlightEnabled
import com.juhao.murexide.ui.theme.ProvideLiquidGlassContentColor
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassMagnifierHost
import com.juhao.murexide.utils.requiresLegacyWritePermission
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private enum class ChatInputPanel {
    Emoji,
    Instruction
}

private val DefaultInputPanelHeight = 280.dp

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun FloatingTopBar(
    hazeState: HazeState,
    liquidBackdrop: Backdrop?,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    title: @Composable () -> Unit,
    showOverlay: Boolean = true
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val controlSize = 48.dp
    val buttonShape = CircleShape
    val topBarColor = MaterialTheme.colorScheme.surface
    val buttonHazeStyle = HazeMaterials.thin().copy(
        blurRadius = 32.dp,
        noiseFactor = 0f
    )
    val useGlassBar = liquidGlassEnabled && liquidBackdrop != null
    val glassSurfaceColor = topBarColor.copy(alpha = 0.75f)

    fun Modifier.glassControl(shape: androidx.compose.ui.graphics.Shape): Modifier =
        if (useGlassBar) {
            drawBackdrop(
                backdrop = liquidBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(1.dp.toPx() * liquidGlassBlur)
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(glassSurfaceColor)
                },
            )
        } else {
            shadow(2.dp, shape)
                .clip(shape)
                .hazeEffect(
                    state = hazeState,
                    style = buttonHazeStyle,
                    block = null
                )
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showOverlay) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                topBarColor.copy(alpha = 0.8f),
                                topBarColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (navigationIcon != null) {
                Box(
                    modifier = Modifier
                        .size(controlSize)
                        .glassControl(buttonShape),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(controlSize)
                    .glassControl(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                title()
            }

            if (actions != null) {
                Row(
                    modifier = Modifier
                        .height(controlSize)
                        .glassControl(buttonShape),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
    }
}

private suspend fun View.measureShownImeHeight(): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

    return suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }

        val controller = windowInsetsController
        if (controller == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        // Animation control exposes the fully shown bounds without making the hidden IME visible.
        controller.controlWindowInsetsAnimation(
            AndroidWindowInsets.Type.ime(),
            -1L,
            null,
            cancellationSignal,
            object : WindowInsetsAnimationControlListener {
                override fun onReady(
                    animationController: WindowInsetsAnimationController,
                    types: Int
                ) {
                    val height = animationController.shownStateInsets.bottom.takeIf { it > 0 }
                    animationController.finish(false)
                    if (continuation.isActive) continuation.resume(height)
                }

                override fun onFinished(
                    animationController: WindowInsetsAnimationController
                ) = Unit

                override fun onCancelled(
                    animationController: WindowInsetsAnimationController?
                ) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        )
    }
}

@Composable
private fun ChatComposer(
    viewModel: ChatViewModel,
    chatType: Int,
    isSending: Boolean,
    isEmojiPanelVisible: Boolean,
    onEmojiClick: () -> Unit,
    hasInstructions: Boolean,
    isInstructionPanelVisible: Boolean,
    onInstructionClick: () -> Unit,
    onAddAlbumClick: () -> Unit,
    onAddFileClick: () -> Unit,
    focusRequester: FocusRequester,
    onInputFocused: () -> Unit
) {
    val composerState by viewModel.composerState.collectAsState()
    MessageInput(
        inputText = composerState.text,
        inputSelectionStart = composerState.selectionStart,
        inputSelectionEnd = composerState.selectionEnd,
        isSending = isSending,
        onTextChange = { text, mentions, selectionStart, selectionEnd ->
            viewModel.updateInputText(
                text = text,
                mentions = mentions,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd
            )
        },
        onSendClick = { viewModel.sendMessage() },
        onSendWithType = { type -> viewModel.sendMessage(type) },
        onAddAlbumClick = onAddAlbumClick,
        onAddFileClick = onAddFileClick,
        isEmojiPanelVisible = isEmojiPanelVisible,
        onEmojiClick = onEmojiClick,
        hasInstructions = hasInstructions,
        isInstructionPanelVisible = isInstructionPanelVisible,
        onInstructionClick = onInstructionClick,
        mentions = composerState.mentions,
        onMentionTriggered = { position ->
            if (chatType == 2) viewModel.showMentionPicker(position)
        },
        focusRequester = focusRequester,
        onInputFocused = onInputFocused
    )
}

@OptIn(
    ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class, ExperimentalHazeMaterialsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    chatType: Int,
    chatName: String,
    chatAvatar: String,
    chatId: String,
    onBackClick: () -> Unit = {},
    onOpenConversation: (ForwardTarget) -> Unit = {},
    bigScreenMode: Boolean = false,
    backUnreadCount: Int = 0,
    viewModel: ChatViewModel
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val uiState by viewModel.screenState.collectAsState()
    val themeColor by UiState.themeColor
    val chatBackgroundColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val forwardViewModel: ForwardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "forward_${chatType}_$chatId",
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ForwardViewModel(token = viewModel.token) as T
            }
        }
    )
    val forwardState by forwardViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val forwardSendingState = rememberUpdatedState(forwardState.isSending)
    val forwardSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !forwardSendingState.value
        }
    )
    val expressions by viewModel.stickerPanel.collectAsState()
    val instructionPanel = uiState.instructionPanel
    val instructionForm by viewModel.instructionForm.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    val composeView = LocalView.current

    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    var pendingInputPanel by remember { mutableStateOf<ChatInputPanel?>(null) }
    var isMeasuringIme by remember { mutableStateOf(false) }
    var isReturningToKeyboard by remember { mutableStateOf(false) }
    var inputPanelHeightPx by remember { mutableIntStateOf(0) }

    var currentMsgHistoryToShow by remember { mutableStateOf<String?>(null) }
    currentMsgHistoryToShow?.let {
        EditHistoryDialog(
            token = viewModel.token,
            msgId = it,
            onDismiss = {
                currentMsgHistoryToShow = null
            }
        )
    }

    val inputPanelHeight = if (inputPanelHeightPx > 0) {
        with(density) { inputPanelHeightPx.toDp() }
    } else {
        DefaultInputPanelHeight
    }

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
        isMeasuringIme = false
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

        isReturningToKeyboard = false
        pendingInputPanel = panel

        val keyboardHeightPx = maxOf(imeBottomPx, imeTargetBottomPx)
        if (keyboardHeightPx > 0) {
            inputPanelHeightPx = keyboardHeightPx
            isMeasuringIme = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        } else if (inputPanelHeightPx == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isMeasuringIme = true
        } else {
            isMeasuringIme = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(isMeasuringIme) {
        if (!isMeasuringIme) return@LaunchedEffect

        inputFocusRequester.requestFocus()
        withFrameNanos { }
        val measuredHeightPx = withTimeoutOrNull(500.milliseconds) {
            composeView.measureShownImeHeight()
        }
        if (!isMeasuringIme || pendingInputPanel == null) return@LaunchedEffect

        if (measuredHeightPx != null) {
            inputPanelHeightPx = measuredHeightPx
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isMeasuringIme = false
    }

    LaunchedEffect(imeTargetBottomPx) {
        if (imeTargetBottomPx > 0) {
            inputPanelHeightPx = imeTargetBottomPx
        }
    }

    LaunchedEffect(
        pendingInputPanel,
        isMeasuringIme,
        imeBottomPx,
        imeTargetBottomPx,
        expressions.isVisible,
        instructionPanel.isVisible
    ) {
        val panel = pendingInputPanel ?: return@LaunchedEffect
        if (isMeasuringIme) return@LaunchedEffect
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
        delay(1_000.milliseconds)
        isReturningToKeyboard = false
    }

    LaunchedEffect(isReturningToKeyboard, imeBottomPx, imeTargetBottomPx) {
        if (
            isReturningToKeyboard &&
            imeTargetBottomPx > 0 &&
            imeBottomPx >= imeTargetBottomPx
        ) {
            inputPanelHeightPx = imeTargetBottomPx
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
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    var highlightRequest by remember { mutableIntStateOf(0) }
    var quoteJumpJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(highlightedMessageId, highlightRequest) {
        val highlightedId = highlightedMessageId ?: return@LaunchedEffect
        val request = highlightRequest
        delay(1_600.milliseconds)
        if (highlightedMessageId == highlightedId && highlightRequest == request) {
            highlightedMessageId = null
        }
    }

    val downloadingFiles by viewModel.downloadingFiles.collectAsState()

    val settingsStorage = remember { SettingsStorage(context) }
    val avatarFollowEnabled by settingsStorage.avatarFollowFlow.collectAsState(initial = false)
    val bubbleCornerRadius by settingsStorage.bubbleCornerRadiusFlow.collectAsState(initial = 18f)
    val bubbleOpacity by settingsStorage.bubbleOpacityFlow.collectAsState(initial = 0.9f)
    val showMyBubbleAvatarSetting by settingsStorage.showMyBubbleAvatarFlow.collectAsState(initial = true)
    val showMsgTagsSetting by settingsStorage.showMsgTagsFlow.collectAsState(initial = false)
    
    val showBackground by settingsStorage.showBackgroundFlow.collectAsState(initial = true)
    val backgroundOpacity by settingsStorage.backgroundOpacityFlow.collectAsState(initial = 0.5f)
    
    val hazeState = remember { HazeState() }
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val showGlassHighlight = liquidGlassHighlightEnabled()
    val liquidBackdrop = if (liquidGlassEnabled) {
        rememberLayerBackdrop {
            drawRect(chatBackgroundColor)
            drawContent()
        }
    } else {
        null
    }

    val albumPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAndSendMedia(uris.take(9), context)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadAndSendFile(it, context) }
    }

    var pendingDownloadMessage by remember { mutableStateOf<MessageItem?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val message = pendingDownloadMessage
        pendingDownloadMessage = null
        if (granted && message != null) {
            viewModel.startDownload(message, context)
        } else if (message != null) {
            Toast.makeText(context, "需要存储权限才能下载文件", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAlbumPicker() {
        albumPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    fun openFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    val selectionMode = uiState.selectionMode
    val selectedMessages = uiState.selectedMessages

    fun startDownload(message: MessageItem) {
        if (requiresLegacyWritePermission(context)) {
            pendingDownloadMessage = message
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.startDownload(message, context)
        }
    }

    fun openForward(messages: List<MessageItem>) {
        val validMessages = messages.filter {
            it.msgId.isNotBlank() &&
                    !it.isRecalled &&
                    it.contentType != MessageItem.CONTENT_TYPE_TIP
        }
        if (validMessages.size != messages.size) {
            Toast.makeText(context, "已撤回或提示消息不可转发", Toast.LENGTH_SHORT).show()
            return
        }
        if (validMessages.isEmpty()) return
        forwardViewModel.open(
            sourceChatType = chatType,
            sourceMsgIds = validMessages.map { it.msgId }.distinct()
        )
    }

    fun dismissForward() {
        scope.launch {
            if (forwardSheetState.isVisible) forwardSheetState.hide()
            forwardViewModel.close()
        }
    }

    LaunchedEffect(forwardState.isOpen) {
        if (forwardState.isOpen && !forwardSheetState.isVisible) {
            forwardSheetState.show()
        } else if (!forwardState.isOpen && forwardSheetState.isVisible) {
            forwardSheetState.hide()
        }
    }

    LaunchedEffect(forwardViewModel) {
        forwardViewModel.events.collect { event ->
            when (event) {
                is ForwardEvent.SourceForwarded -> viewModel.removeForwardedMessage(event.msgId)
                is ForwardEvent.Completed -> {
                    viewModel.exitSelectionMode()
                    if (forwardSheetState.isVisible) forwardSheetState.hide()
                    forwardViewModel.close()
                    viewModel.refresh()

                    val target = event.recipients.singleOrNull()
                    val result = snackbarHostState.showSnackbar(
                        message = if (target == null) {
                            "消息已转发到${event.recipients.size}个对话当中"
                        } else {
                            "消息已转发到${target.displayName}中"
                        },
                        actionLabel = target?.let { "查看" },
                        duration = if (target == null) SnackbarDuration.Short else SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed && target != null) {
                        onOpenConversation(target)
                    }
                }
            }
        }
    }

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

    val topVisibleMessage by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isNotEmpty()) {
                val topIndex = visibleItems.minByOrNull { it.index }?.index
                topIndex?.let { uiState.messages.getOrNull(it) }
            } else {
                null
            }
        }
    }

    val topVisibleMessageId = topVisibleMessage?.msgId

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
                    handleStaticHtmlLink(context, event.url)
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
    LaunchedEffect(
        listState,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.isRefreshing
    ) {
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
                !listState.canScrollBackward
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

                val isAtBottom = !listState.canScrollBackward

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

    if (forwardState.isOpen) {
        ForwardBottomSheet(
            state = forwardState,
            sheetState = forwardSheetState,
            onDismiss = ::dismissForward,
            onQueryChange = forwardViewModel::updateQuery,
            onRetry = forwardViewModel::retryLoad,
            onTargetClick = forwardViewModel::toggleTarget,
            onSend = forwardViewModel::send
        )
    }

    LiquidGlassMagnifierHost(
        modifier = modifier.fillMaxSize(),
        enabled = liquidGlassEnabled
    ) {
        Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = chatBackgroundColor,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                FloatingTopBar(
                    hazeState = hazeState,
                    liquidBackdrop = liquidBackdrop,
                    navigationIcon = if (selectionMode || !bigScreenMode) {
                        {
                            Crossfade(targetState = selectionMode) { isSelectionMode ->
                                if (isSelectionMode) {
                                    IconButton(
                                        onClick = { viewModel.exitSelectionMode() },
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Icon(
                                            AppIcons.Close,
                                            contentDescription = "退出多选",
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Box {
                                        IconButton(
                                            modifier = Modifier.size(46.dp),
                                            onClick = onBackClick
                                        ) {
                                            AutoMirroredIcon(
                                                imageVector = AppIcons.ArrowBack,
                                                contentDescription = "返回",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        if (backUnreadCount > 0) {
                                            Badge(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp),
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ) {
                                                Text(
                                                    text = if (backUnreadCount > 99) "99+" else backUnreadCount.toString(),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
                    title = {
                        Crossfade(targetState = selectionMode) { isSelectionMode ->
                            if (isSelectionMode) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .height(46.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        "已选中",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    Text(
                                        "条",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
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
                                            alwaysCircle = true,
                                            size = 40.dp
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
                                                Text(
                                                    text = "${uiState.memberCount} 位成员",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            if (chatType == 3 && uiState.usageCount != null) {
                                                Text(
                                                    text = "${uiState.usageCount} 人使用",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            if (chatType == 1 && uiState.continuousOnlineDay != null) {
                                                Text(
                                                    text = "连续在线 ${uiState.continuousOnlineDay} 天",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (uiState.boardPanel.boards.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .height(46.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable { viewModel.toggleBoard() }
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (uiState.boardPanel.isExpanded) {
                                                    AppIcons.KeyboardArrowUp
                                                } else {
                                                    AppIcons.KeyboardArrowDown
                                                },
                                                contentDescription = if (uiState.boardPanel.isExpanded) "收起看板" else "展开看板",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier.animateContentSize()
                        ) {
                            if (selectionMode) {
                                IconButton(
                                    onClick = { viewModel.recallSelectedMessages() },
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    AutoMirroredIcon(
                                        AppIcons.Undo,
                                        contentDescription = "撤回",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                if (selectedMessages.size == 1) {
                                    val message = selectedMessages.firstOrNull()
                                    message?.let {
                                        if (it.content.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        clipboardManager.setClipEntry(
                                                            ClipEntry(
                                                                ClipData.newPlainText(
                                                                    "msg",
                                                                    it.content
                                                                )
                                                            )
                                                        )
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        "复制成功",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    viewModel.exitSelectionMode()
                                                },
                                                modifier = Modifier.size(46.dp)
                                            ) {
                                                Icon(
                                                    AppIcons.ContentCopy,
                                                    contentDescription = "复制",
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { showScreenshotSheet = true },
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Icon(
                                        AppIcons.Screenshot,
                                        contentDescription = "截图",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .clickable { showMoreMenu = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ExpressiveDropdownMenu(
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
                                                    AppIcons.Refresh,
                                                    contentDescription = null
                                                )
                                            }
                                        )
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
                                                    AppIcons.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        )
                                    }
                                    Icon(
                                        imageVector = AppIcons.MoreVert,
                                        contentDescription = "更多",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = !selectionMode && uiState.boardPanel.isExpanded && uiState.boardPanel.boards.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val boardShape = RoundedCornerShape(28.dp)
                    val boardGlassColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .shadow(4.dp, boardShape)
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.liquidGlass(
                                        enabled = true,
                                        backdrop = liquidBackdrop,
                                        shape = boardShape,
                                        surfaceColor = boardGlassColor,
                                        blurRadius = 5.dp * liquidGlassBlur,
                                        lensHeight = 6.dp,
                                        lensAmount = 12.dp,
                                        showHighlight = showGlassHighlight
                                    )
                                } else {
                                    Modifier
                                        .clip(boardShape)
                                        .hazeEffect(
                                            state = hazeState,
                                            style = HazeMaterials.thin(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ).copy(
                                                blurRadius = 32.dp,
                                                noiseFactor = 0f
                                            ),
                                            block = null
                                        )
                                }
                            )
                    ) {
                        ProvideLiquidGlassContentColor(
                            glassColor = boardGlassColor,
                            preferredColor = MaterialTheme.colorScheme.onSurface,
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
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular().copy(
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
                                    Icon(AppIcons.FormatQuote, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("引用")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            TextButton(
                                onClick = {
                                    openForward(
                                        uiState.messages.asReversed()
                                            .filter { it in selectedMessages })
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    AutoMirroredIcon(AppIcons.Redo, contentDescription = null)
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
                                            AppIcons.Close,
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
                                        AppIcons.Edit,
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
                                            AppIcons.Close,
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
                                        AppIcons.Code,
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
                                            AppIcons.Close,
                                            contentDescription = "取消指令",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            ChatComposer(
                                viewModel = viewModel,
                                chatType = chatType,
                                isSending = uiState.isSending,
                                onAddAlbumClick = { openAlbumPicker() },
                                onAddFileClick = { openFilePicker() },
                                isEmojiPanelVisible =
                                    expressions.isVisible ||
                                            pendingInputPanel == ChatInputPanel.Emoji,
                                onEmojiClick = { requestInputPanel(ChatInputPanel.Emoji) },
                                hasInstructions = instructionPanel.instructions.isNotEmpty(),
                                isInstructionPanelVisible =
                                    instructionPanel.isVisible ||
                                            pendingInputPanel == ChatInputPanel.Instruction,
                                onInstructionClick = {
                                    requestInputPanel(ChatInputPanel.Instruction)
                                },
                                focusRequester = inputFocusRequester,
                                onInputFocused = {
                                    if (
                                        !isMeasuringIme &&
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
                                isMeasuringIme = false
                                pendingInputPanel = null
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                viewModel.hideStickerPanel()
                                viewModel.hideInstructionPanel()
                            }
                            
                            AnimatedVisibility(
                                visible = expressions.isVisible,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
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
            
                            AnimatedVisibility(
                                visible = instructionPanel.isVisible,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (liquidBackdrop != null) {
                        Modifier.layerBackdrop(liquidBackdrop).hazeSource(hazeState)
                    } else {
                        Modifier.hazeSource(hazeState)
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(chatBackgroundColor)
            ) {
                uiState.backgroundUrl?.takeIf { showBackground && it.isNotEmpty() }?.let { bgUrl ->
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
            }

            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding() + 24.dp)
                ) {
                    ContainedLoadingIndicator(
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
                        AppIcons.Warning,
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
                        Icon(AppIcons.Refresh, contentDescription = null)
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
                        key = { it.message.msgId },
                        contentType = { it.message.contentType }
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
                            onForward = { openForward(listOf(message)) },
                            onQuoteClick = { quotedMessage ->
                                val quoteMsgId = quotedMessage.quoteMsgId
                                if (!quoteMsgId.isNullOrBlank()) {
                                    showMenuMsgId = null
                                    quoteJumpJob?.cancel()
                                    quoteJumpJob = scope.launch {
                                        if (viewModel.loadQuotedMessage(quoteMsgId)) {
                                            val targetIndex =
                                                withTimeoutOrNull(2_000.milliseconds) {
                                                    snapshotFlow {
                                                        displayItems.indexOfFirst {
                                                            it.message.msgId == quoteMsgId
                                                        }
                                                    }.first { it >= 0 }
                                                }
                                            if (targetIndex != null) {
                                                listState.animateScrollToCenteredItem(targetIndex)
                                                highlightedMessageId = quoteMsgId
                                                highlightRequest++
                                            }
                                        }
                                    }
                                }
                            },
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
                            onImageClick = { msg, sourceBounds ->
                                if (!selectionMode) {
                                    when (msg.contentType) {
                                        MessageItem.CONTENT_TYPE_IMAGE,
                                        MessageItem.CONTENT_TYPE_VIDEO -> {
                                            buildChatMediaGallery(
                                                messages = uiState.messages,
                                                selectedMessageId = msg.msgId
                                            )?.let { gallery ->
                                                showImageViewer(
                                                    context = context,
                                                    images = gallery.entries.map { entry ->
                                                        when (entry.kind) {
                                                            ChatMediaKind.IMAGE -> imageMessagePreviewItem(
                                                                url = entry.url,
                                                                messageId = entry.messageId,
                                                                imageId = entry.sequence
                                                            )

                                                            ChatMediaKind.VIDEO -> videoMessagePreviewItem(
                                                                url = entry.url,
                                                                messageId = entry.messageId,
                                                                sequence = entry.sequence
                                                            )
                                                        }
                                                    },
                                                    initialIndex = gallery.initialIndex,
                                                    pagination = MediaViewerPagination(
                                                        chatId = chatId,
                                                        chatType = chatType
                                                    ),
                                                    sourceBounds = sourceBounds
                                                )
                                            }
                                        }

                                        MessageItem.CONTENT_TYPE_STICKER -> {
                                            resolveStickerMessageUrl(
                                                imageUrl = msg.imageUrl,
                                                stickerUrl = msg.stickerUrl
                                            )
                                                ?.let { url ->
                                                    showImageViewer(
                                                        context = context,
                                                        images = listOf(fullImagePreviewItem(url)),
                                                        sourceBounds = sourceBounds
                                                    )
                                                }
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
                                    startDownload(msg)
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
                            onEditIconClick = { msgId ->
                                currentMsgHistoryToShow = msgId
                            },
                            isHighlighted = highlightedMessageId == message.msgId,
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
                                ContainedLoadingIndicator()
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
            onDismissRequest = {
                if (!recallDialog.isSubmitting) viewModel.hideRecallDialog()
            },
            title = { Text("撤回消息") },
            text = { Text("确定要撤回这条消息吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.recallMessage() },
                    enabled = !recallDialog.isSubmitting
                ) {
                    Text(if (recallDialog.isSubmitting) "撤回中…" else "确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.hideRecallDialog() },
                    enabled = !recallDialog.isSubmitting
                ) {
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
            icon = { Icon(AppIcons.DriveFileRenameOutline, contentDescription = null) },
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
            icon = {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
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
                    imageVector = AppIcons.KeyboardArrowDown,
                    contentDescription = "滚动到底部",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
