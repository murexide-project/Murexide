package com.juhao.murexide.ui.conversationdetail

import android.content.ClipData
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.juhao.murexide.data.BaItem
import com.juhao.murexide.data.BotItem
import com.juhao.murexide.data.ConversationDetail
import com.juhao.murexide.data.GroupMember
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.CapsuleTabBar
import com.juhao.murexide.ui.components.ExpressiveDropdownMenu
import com.juhao.murexide.ui.components.ExpressiveOverflowIconButton
import com.juhao.murexide.ui.components.MediaViewerPagination
import com.juhao.murexide.ui.components.imageMessagePreviewItem
import com.juhao.murexide.ui.components.showImageViewer
import com.juhao.murexide.ui.components.videoMessagePreviewItem
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon
import com.juhao.murexide.ui.chat.components.formatVideoDuration
import com.juhao.murexide.ui.theme.LiquidGlassSurface
import com.juhao.murexide.ui.theme.LocalLiquidGlassBackdrop
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.juhao.murexide.ui.theme.liquidGlass
import com.juhao.murexide.ui.theme.liquidGlassHighlightEnabled
import com.juhao.murexide.ui.theme.liquidGlassContentColor
import com.juhao.murexide.ui.theme.resolvedLiquidGlassContentColor
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSlider
import kotlinx.coroutines.launch

@Composable
private fun detailGlassColor(color: Color): Color = color.copy(
    alpha = if (LocalLiquidGlassEnabled.current) 0.64f else color.alpha
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDetailScreen(
    viewModel: ConversationDetailViewModel,
    onBack: () -> Unit,
    onEnterChat: (ConversationDetail) -> Unit,
    onEditGroup: (ConversationDetail) -> Unit = {},
    onOpenMember: (GroupMember) -> Unit = {},
    onOpenBoard: (BaItem) -> Unit = {},
    onInviteBotToGroup: (ConversationDetail) -> Unit = {},
    onLeaveGroup: () -> Unit = {},
    currentUserId: String
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val showGlassHighlight = liquidGlassHighlightEnabled()
    val liquidBackdrop = LocalLiquidGlassBackdrop.current
    val snackbars = remember { SnackbarHostState() }
    var showMore by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val groupListState = rememberLazyListState()
    val userListState = rememberLazyListState()
    val groupNameBottomOffset = with(LocalDensity.current) { 130.dp.roundToPx() }
    val showGroupTitleInAppBar by remember(groupNameBottomOffset) {
        derivedStateOf {
            groupListState.firstVisibleItemIndex > 0 ||
                groupListState.firstVisibleItemScrollOffset >= groupNameBottomOffset
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.hasLeft) {
        if (state.hasLeft) onLeaveGroup()
    }

    val topAppBarGlassColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f)
    val topAppBarGlassContentColor = liquidGlassContentColor(
        preferredColor = MaterialTheme.colorScheme.onSurface,
        glassColor = topAppBarGlassColor,
        backgroundColor = MaterialTheme.colorScheme.background,
    )
    val topAppBarGlassSecondaryContentColor = liquidGlassContentColor(
        preferredColor = MaterialTheme.colorScheme.onSurfaceVariant,
        glassColor = topAppBarGlassColor,
        backgroundColor = MaterialTheme.colorScheme.background,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                modifier = if (liquidGlassEnabled) {
                    Modifier.liquidGlass(
                        enabled = true,
                        backdrop = liquidBackdrop,
                        shape = RectangleShape,
                        surfaceColor = topAppBarGlassColor,
                        blurRadius = 6.dp,
                        showHighlight = showGlassHighlight
                    )
                } else {
                    Modifier
                },
                colors = if (liquidGlassEnabled) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = topAppBarGlassContentColor,
                        titleContentColor = topAppBarGlassContentColor,
                        actionIconContentColor = topAppBarGlassContentColor,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
                title = {
                    val group = state.detail?.takeIf { it.chatType == 2 }
                    if (group != null) {
                        AnimatedVisibility(
                            visible = showGroupTitleInAppBar,
                            enter = fadeIn(animationSpec = tween(180)) +
                                slideInVertically(
                                    initialOffsetY = { height -> -height / 2 },
                                    animationSpec = tween(180)
                                ),
                            exit = fadeOut(animationSpec = tween(140)) +
                                slideOutVertically(
                                    targetOffsetY = { height -> -height / 2 },
                                    animationSpec = tween(140)
                                )
                        ) {
                            Column {
                                Text(
                                    text = group.name.ifBlank { "未知群聊" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${group.memberCount ?: 0} 位成员",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (liquidGlassEnabled) {
                                        topAppBarGlassSecondaryContentColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { AutoMirroredIcon(AppIcons.ArrowBack, "返回") }
                },
                actions = {
                    val group = state.detail?.takeIf { it.chatType == 2 }
                    if ((group?.permissionLevel ?: 0) >= 2) {
                        IconButton(onClick = { onEditGroup(group!!) }) {
                            Icon(AppIcons.Edit, "编辑群聊")
                        }
                    }
                    Box {
                        ExpressiveOverflowIconButton(
                            expanded = showMore,
                            onClick = { showMore = true },
                            contentDescription = "更多"
                        )
                        ExpressiveDropdownMenu(
                            expanded = showMore,
                            onDismissRequest = { showMore = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                leadingIcon = { Icon(AppIcons.Refresh, null) },
                                onClick = {
                                    showMore = false
                                    viewModel.loadDetail()
                                    if (group != null) {
                                        viewModel.loadMembers(refresh = true)
                                        viewModel.loadGroupBots(refresh = true)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides liquidBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val detail = state.detail
                when {
                    detail == null && state.isLoading -> Box(
                        Modifier.fillMaxSize(),
                        Alignment.Center
                    ) { CircularProgressIndicator() }

                    detail == null -> ErrorContent(state.error ?: "加载失败", viewModel::loadDetail)
                    detail.chatType == 2 -> GroupConversationDetail(
                        modifier = Modifier.padding(padding),
                        detail = detail,
                        listState = groupListState,
                        selectedTab = state.selectedTab,
                        members = state.members,
                        bots = state.groupBots,
                        media = state.mediaMessages,
                        onAdd = viewModel::addChat,
                        isAdded = state.isAdded,
                        isAdding = state.isAdding,
                        isLoadingMembers = state.isLoadingMembers,
                        isLoadingMoreMembers = state.isLoadingMoreMembers,
                        hasMoreMembers = state.hasMoreMembers,
                        isLoadingBots = state.isLoadingGroupBots,
                        hasLoadedBots = state.hasLoadedGroupBots,
                        isLoadingHistory = state.isLoadingHistory,
                        hasMoreHistory = state.hasMoreHistory,
                        isChangingMute = state.isChangingMute,
                        isLeaving = state.isLeaving,
                        onMessage = { onEnterChat(detail) },
                        onMute = viewModel::toggleMute,
                        onLeave = { showLeaveConfirm = true },
                        onTabSelected = viewModel::selectTab,
                        onLoadMembers = { viewModel.loadMembers() },
                        onLoadHistory = viewModel::loadMoreHistory,
                        onOpenMember = onOpenMember,
                        canManageMembers = detail.permissionLevel >= 2,
                        isGroupOwner = detail.permissionLevel == 100,
                        onKickMember = viewModel::requestKickMember,
                        onGagMember = viewModel::requestGagMember,
                        onAdminToggle = viewModel::requestAdminToggle,
                        onOpenBot = { bot ->
                            ConversationDetailActivity.start(
                                context = context,
                                chatId = bot.id,
                                chatType = 3,
                                chatName = bot.name,
                                chatAvatar = bot.avatarUrl
                            )
                        }
                    )

                    detail.chatType == 1 -> UserConversationDetail(
                        modifier = Modifier.padding(padding),
                        detail = detail,
                        listState = userListState,
                        isCurrentUser = detail.chatId == currentUserId,
                        isAdded = state.isAdded,
                        isAdding = state.isAdding,
                        onAdd = viewModel::addChat,
                        onMessage = { onEnterChat(detail) },
                        onMute = viewModel::toggleMute,
                        isChangingMute = state.isChangingMute,
                        media = state.mediaMessages,
                        isLoadingHistory = state.isLoadingHistory,
                        hasMoreHistory = state.hasMoreHistory,
                        onLoadHistory = viewModel::loadMoreHistory,
                        createdBoards = state.createdBoards,
                        isLoadingCreatedBoards = state.isLoadingCreatedBoards,
                        hasLoadedCreatedBoards = state.hasLoadedCreatedBoards,
                        onOpenBoard = onOpenBoard
                    )

                    detail.chatType == 3 -> BotConversationDetail(
                        modifier = Modifier.padding(padding),
                        detail = detail,
                        isAdded = state.isAdded,
                        isAdding = state.isAdding,
                        onAdd = viewModel::addChat,
                        onMessage = { onEnterChat(detail) },
                        onMute = viewModel::toggleMute,
                        isChangingMute = state.isChangingMute,
                        onInviteToGroup = { onInviteBotToGroup(detail) },
                        media = state.mediaMessages,
                        isLoadingHistory = state.isLoadingHistory,
                        hasMoreHistory = state.hasMoreHistory,
                        onLoadHistory = viewModel::loadMoreHistory
                    )

                    else -> LegacyDetail(
                        modifier = Modifier.padding(padding),
                        detail = detail,
                        isAdded = state.isAdded,
                        isAdding = state.isAdding,
                        onAdd = viewModel::addChat,
                        onMessage = { onEnterChat(detail) }
                    )
                }
            }
        }
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { if (!state.isLeaving) showLeaveConfirm = false },
            icon = { Icon(AppIcons.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("退出群聊") },
            text = { Text("确定要退出该群聊吗？") },
            confirmButton = {
                TextButton(
                    enabled = !state.isLeaving,
                    onClick = viewModel::leaveGroup
                ) {
                    if (state.isLeaving) CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    else Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isLeaving,
                    onClick = { showLeaveConfirm = false }) { Text("取消") }
            }
        )
    }

    MemberManagementDialogs(
        kickTarget = state.kickTarget,
        gagTarget = state.gagTarget,
        adminTarget = state.adminTarget,
        showKickConfirm = state.showKickConfirm,
        showGagDialog = state.showGagDialog,
        showAdminConfirm = state.showAdminConfirm,
        onDismiss = viewModel::dismissMemberActionDialogs,
        onConfirmKick = viewModel::confirmKickMember,
        onConfirmGag = viewModel::confirmGagMember,
        onConfirmAdminToggle = viewModel::confirmAdminToggle
    )

}

@Composable
private fun GroupConversationDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    listState: LazyListState,
    selectedTab: Int,
    members: List<GroupMember>,
    bots: List<BotItem>,
    media: List<MessageItem>,
    onAdd: () -> Unit,
    isAdded: Boolean?,
    isAdding: Boolean,
    isLoadingMembers: Boolean,
    isLoadingMoreMembers: Boolean,
    hasMoreMembers: Boolean,
    isLoadingBots: Boolean,
    hasLoadedBots: Boolean,
    isLoadingHistory: Boolean,
    hasMoreHistory: Boolean,
    isChangingMute: Boolean,
    isLeaving: Boolean,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onLoadMembers: () -> Unit,
    onLoadHistory: () -> Unit,
    onOpenMember: (GroupMember) -> Unit,
    canManageMembers: Boolean,
    isGroupOwner: Boolean,
    onKickMember: (GroupMember) -> Unit,
    onGagMember: (GroupMember) -> Unit,
    onAdminToggle: (GroupMember) -> Unit,
    onOpenBot: (BotItem) -> Unit
) {
    var introductionExpanded by remember(detail.introduction) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val mediaRows = media.chunked(3)
    LaunchedEffect(
        listState,
        selectedTab,
        members.size,
        hasMoreMembers,
        isLoadingMembers,
        isLoadingMoreMembers,
        mediaRows.size,
        hasMoreHistory,
        isLoadingHistory
    ) {
        if (isAdded != true) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItem to layoutInfo.totalItemsCount
        }.collect { (lastVisibleItem, totalItems) ->
            if (
                selectedTab == 0 &&
                hasMoreMembers &&
                !isLoadingMembers &&
                !isLoadingMoreMembers &&
                totalItems > 0 &&
                lastVisibleItem >= totalItems - 3
            ) {
                onLoadMembers()
            }
            if (
                selectedTab == 2 &&
                hasMoreHistory &&
                !isLoadingHistory &&
                totalItems > 0 &&
                lastVisibleItem >= totalItems - 2
            ) {
                onLoadHistory()
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "header") {
            GroupHeader(
                detail = detail,
                onMessage = onMessage,
                onMute = onMute,
                onLeave = onLeave,
                isAdded = isAdded,
                isAdding = isAdding,
                isChangingMute = isChangingMute,
                isLeaving = isLeaving,
                introductionExpanded = introductionExpanded,
                onAdd = onAdd,
                onIntroductionClick = {
                    introductionExpanded = true
                }
            )
        }
        if (isAdded == true) {
            item(key = "tabs") {
                DetailCardSegment(cardColor = cardColor, isTop = true) {
                    val labels = listOf("成员", "机器人", "媒体", "群云盘")
                    CapsuleTabBar(
                        tabs = labels,
                        selectedTabIndex = selectedTab,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            when (selectedTab) {
                0 -> when {
                    isLoadingMembers && members.isEmpty() -> item(key = "members-loading") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { LoadingContent() }
                    }

                    members.isEmpty() -> item(key = "members-empty") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { EmptyContent("暂无可展示的成员") }
                    }

                    else -> {
                        items(members, key = GroupMember::userId) { member ->
                            val isLast = member == members.last() && !hasMoreMembers
                            DetailCardSegment(cardColor, isBottom = isLast) {
                                MemberRow(
                                    member = member,
                                    canManage = canManageMembers && member.permissionLevel != 100,
                                    canSetAdmin = isGroupOwner && member.permissionLevel != 100,
                                    onClick = { onOpenMember(member) },
                                    onKick = { onKickMember(member) },
                                    onGag = { onGagMember(member) },
                                    onAdminToggle = { onAdminToggle(member) }
                                )
                            }
                        }
                        if (hasMoreMembers) item(key = "members-load-more") {
                            DetailCardSegment(cardColor, isBottom = true) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isLoadingMoreMembers) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> when {
                    isLoadingBots && bots.isEmpty() -> item(key = "bots-loading") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { LoadingContent() }
                    }

                    bots.isEmpty() && hasLoadedBots -> item(key = "bots-empty") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { EmptyContent("暂无机器人") }
                    }

                    else -> {
                        items(bots, key = BotItem::id) { bot ->
                            val isLast = bot == bots.last()
                            DetailCardSegment(cardColor, isBottom = isLast) {
                                BotRow(bot, onClick = { onOpenBot(bot) })
                            }
                        }
                    }
                }

                2 -> when {
                    isLoadingHistory && media.isEmpty() -> item(key = "media-loading") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { LoadingContent() }
                    }

                    media.isEmpty() && !hasMoreHistory -> item(key = "media-empty") {
                        DetailCardSegment(
                            cardColor,
                            isBottom = true,
                            minHeight = 180.dp
                        ) { EmptyContent("暂无媒体") }
                    }

                    else -> {
                        items(
                            mediaRows,
                            key = { row -> row.joinToString(separator = ":") { it.msgId } }) { row ->
                            DetailCardSegment(
                                cardColor,
                                isBottom = row == mediaRows.last() && !hasMoreHistory
                            ) {
                                MediaRow(
                                    row,
                                    media,
                                    detail,
                                    hasBottomSpacing = row != mediaRows.last()
                                )
                            }
                        }
                        if (hasMoreHistory) item(key = "media-load-more") {
                            DetailCardSegment(cardColor, isBottom = true) {
                                AutoLoadingRow(isLoadingHistory)
                            }
                        }
                    }
                }

                else -> item(key = "cloud-drive") {
                    DetailCardSegment(
                        cardColor,
                        isBottom = true,
                        minHeight = 180.dp
                    ) { EmptyContent("群云盘功能即将推出") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserConversationDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    listState: LazyListState,
    isCurrentUser: Boolean,
    isAdded: Boolean?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    isChangingMute: Boolean,
    media: List<MessageItem>,
    isLoadingHistory: Boolean,
    hasMoreHistory: Boolean,
    onLoadHistory: () -> Unit,
    createdBoards: List<BaItem>,
    isLoadingCreatedBoards: Boolean,
    hasLoadedCreatedBoards: Boolean,
    onOpenBoard: (BaItem) -> Unit
) {
    var introductionExpanded by remember(detail.introduction) { mutableStateOf(false) }
    var createdBoardsExpanded by remember(detail.chatId) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val mediaRows = media.chunked(3)
    LaunchedEffect(listState, isLoadingHistory, hasMoreHistory, mediaRows.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItem to layoutInfo.totalItemsCount
        }.collect { (lastVisibleItem, totalItems) ->
            if (
                hasMoreHistory &&
                !isLoadingHistory &&
                totalItems > 0 &&
                lastVisibleItem >= totalItems - 2
            ) {
                onLoadHistory()
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "user-header") {
            UserHeader(
                detail = detail,
                isCurrentUser = isCurrentUser,
                isAdded = isAdded,
                isAdding = isAdding,
                onAdd = onAdd,
                onMessage = onMessage,
                onMute = onMute,
                isChangingMute = isChangingMute,
                introductionExpanded = introductionExpanded,
                onIntroductionClick = { introductionExpanded = true }
            )
        }
        item(key = "user-info") {
            DetailCardSegment(cardColor = cardColor, isTop = true) {
                UserInfoContent(
                    detail = detail,
                    createdBoards = createdBoards,
                    createdBoardsExpanded = createdBoardsExpanded,
                    isLoadingCreatedBoards = isLoadingCreatedBoards,
                    hasLoadedCreatedBoards = hasLoadedCreatedBoards,
                    onOpenBoard = onOpenBoard,
                    onCreatedBoardsClick = {
                        createdBoardsExpanded = !createdBoardsExpanded
                    }
                )
                HorizontalDivider()
                Text(
                    text = "媒体",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
        when {
            isLoadingHistory && media.isEmpty() -> item(key = "user-media-loading") {
                DetailCardSegment(
                    cardColor,
                    isBottom = true,
                    minHeight = 180.dp
                ) { LoadingContent() }
            }

            media.isEmpty() && !hasMoreHistory -> item(key = "user-media-empty") {
                DetailCardSegment(
                    cardColor,
                    isBottom = true,
                    minHeight = 180.dp
                ) { EmptyContent("暂无媒体") }
            }

            else -> {
                items(
                    mediaRows,
                    key = { row -> row.joinToString(separator = ":") { it.msgId } }) { row ->
                    DetailCardSegment(
                        cardColor,
                        isBottom = row == mediaRows.last() && !hasMoreHistory
                    ) {
                        MediaRow(row, media, detail, hasBottomSpacing = row != mediaRows.last())
                    }
                }
                if (hasMoreHistory) item(key = "user-media-load-more") {
                    DetailCardSegment(cardColor, isBottom = true) {
                        AutoLoadingRow(isLoadingHistory)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BotConversationDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    isAdded: Boolean?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    isChangingMute: Boolean,
    onInviteToGroup: () -> Unit,
    media: List<MessageItem>,
    isLoadingHistory: Boolean,
    hasMoreHistory: Boolean,
    onLoadHistory: () -> Unit
) {
    var introductionExpanded by remember(detail.introduction) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val mediaRows = media.chunked(3)
    val listState = rememberLazyListState()
    LaunchedEffect(listState, isLoadingHistory, hasMoreHistory, mediaRows.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleItem to layoutInfo.totalItemsCount
        }.collect { (lastVisibleItem, totalItems) ->
            if (
                hasMoreHistory &&
                !isLoadingHistory &&
                totalItems > 0 &&
                lastVisibleItem >= totalItems - 2
            ) {
                onLoadHistory()
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "bot-header") {
            UserHeader(
                detail = detail,
                isAdded = isAdded,
                isAdding = isAdding,
                onAdd = onAdd,
                onMessage = onMessage,
                onMute = onMute,
                isChangingMute = isChangingMute,
                introductionExpanded = introductionExpanded,
                onIntroductionClick = { introductionExpanded = true },
                isBot = true,
                onInviteToGroup = onInviteToGroup
            )
        }
        item(key = "bot-info") {
            DetailCardSegment(cardColor = cardColor, isTop = true) {
                BotInfoContent(detail)
                HorizontalDivider()
                Text(
                    text = "媒体",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
        when {
            isLoadingHistory && media.isEmpty() -> item(key = "bot-media-loading") {
                DetailCardSegment(
                    cardColor,
                    isBottom = true,
                    minHeight = 180.dp
                ) { LoadingContent() }
            }

            media.isEmpty() && !hasMoreHistory -> item(key = "bot-media-empty") {
                DetailCardSegment(
                    cardColor,
                    isBottom = true,
                    minHeight = 180.dp
                ) { EmptyContent("暂无媒体") }
            }

            else -> {
                items(
                    mediaRows,
                    key = { row -> row.joinToString(separator = ":") { it.msgId } }) { row ->
                    DetailCardSegment(
                        cardColor,
                        isBottom = row == mediaRows.last() && !hasMoreHistory
                    ) {
                        MediaRow(row, media, detail, hasBottomSpacing = row != mediaRows.last())
                    }
                }
                if (hasMoreHistory) item(key = "bot-media-load-more") {
                    DetailCardSegment(cardColor, isBottom = true) {
                        AutoLoadingRow(isLoadingHistory)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserHeader(
    detail: ConversationDetail,
    isCurrentUser: Boolean = false,
    isAdded: Boolean?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    isChangingMute: Boolean,
    introductionExpanded: Boolean,
    onIntroductionClick: () -> Unit,
    isBot: Boolean = false,
    onInviteToGroup: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        Avatar(url = detail.avatarUrl, size = 88.dp, canView = true)
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                detail.name.ifBlank { "未知用户" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isBot) {
                val (genderIcon, genderColor, genderDescription) = when (detail.gender) {
                    1 -> Triple(AppIcons.Boy, Color(0xFF2196F3), "男")
                    2 -> Triple(AppIcons.Girl, Color(0xFFFF6B9A), "女")
                    else -> Triple(
                        AppFilledIcons.Person,
                        resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        "未知性别"
                    )
                }
                Icon(
                    genderIcon,
                    genderDescription,
                    tint = genderColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isBot) {
                    "${detail.usageCount ?: 0} 人使用"
                } else {
                    "ID: ${detail.chatId}"
                },
                color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
                style = MaterialTheme.typography.bodyMedium
            )
            detail.ipGeo?.takeIf { !isBot }?.let { ip ->
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "IP:$ip",
                    color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (isCurrentUser) {
            TelegramAction(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                icon = AppIcons.ChatBubbleOutline,
                label = "消息",
                onClick = onMessage
            )
        } else if (isAdded == true) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelegramAction(Modifier.weight(1f), AppIcons.ChatBubbleOutline, "消息", onMessage)
                TelegramAction(
                    Modifier.weight(1f),
                    if (detail.doNotDisturb) AppIcons.NotificationsOff else AppIcons.Notifications,
                    if (detail.doNotDisturb) "取消静音" else "静音",
                    onMute,
                    isChangingMute
                )
                TelegramAction(
                    Modifier.weight(1f),
                    if (isBot) AppIcons.Group else AppIcons.Phone,
                    if (isBot) "添加到群" else "通话",
                    onClick = onInviteToGroup ?: {},
                    enabled = isBot && onInviteToGroup != null
                )
            }
        } else {
            TelegramAction(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                icon = AppIcons.PersonAdd,
                label = "添加好友",
                onClick = onAdd,
                loading = isAdding
            )
        }
        if (detail.introduction.isNotBlank()) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(onClick = onIntroductionClick),
                shape = RoundedCornerShape(18.dp),
                color = detailGlassColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                blurRadius = 6.dp,
                lensHeight = 6.dp,
                lensAmount = 12.dp
            ) {
                IntroductionContent(
                    introduction = detail.introduction,
                    expanded = introductionExpanded,
                    onExpand = onIntroductionClick
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun BotInfoContent(detail: ConversationDetail) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "详情信息",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
        UserInfoRow("机器人 ID", detail.chatId)
        UserInfoRow("创建者 ID", detail.createBy ?: "未知")
        UserInfoRow("创建时间", detail.createTime?.let(::formatBotCreateTime) ?: "未知")
        UserInfoRow("限制进群", if (detail.groupLimit) "是" else "否")
    }
}

private fun formatBotCreateTime(timestampSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestampSeconds * 1000L))

@Composable
private fun UserInfoContent(
    detail: ConversationDetail,
    createdBoards: List<BaItem>,
    createdBoardsExpanded: Boolean,
    isLoadingCreatedBoards: Boolean,
    hasLoadedCreatedBoards: Boolean,
    onOpenBoard: (BaItem) -> Unit,
    onCreatedBoardsClick: () -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "详情信息",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
        UserInfoRow("在线天数", detail.onlineDay?.let { "$it 天" } ?: "未知")
        UserInfoRow("连续在线", detail.continuousOnlineDay?.let { "$it 天" } ?: "未知")
        UserInfoRow("注册时间", detail.registerTime ?: "未知")
        UserInfoRow(
            label = "创建的板块",
            value = when {
                isLoadingCreatedBoards -> "加载中"
                createdBoards.isEmpty() && hasLoadedCreatedBoards -> "无"
                hasLoadedCreatedBoards -> "${createdBoards.size} 个"
                else -> "加载失败"
            },
            onClick = onCreatedBoardsClick.takeIf { createdBoards.isNotEmpty() },
            trailingIcon = if (createdBoards.isNotEmpty()) {
                if (createdBoardsExpanded) AppIcons.KeyboardArrowUp else AppIcons.KeyboardArrowDown
            } else {
                null
            }
        )
        AnimatedVisibility(
            visible = createdBoardsExpanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(animationSpec = tween(140))
        ) {
            when {
                isLoadingCreatedBoards -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                createdBoards.isEmpty() -> Text(
                    text = if (hasLoadedCreatedBoards) "暂无创建的板块" else "加载失败，点击重试",
                    color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                )

                else -> Column {
                    createdBoards.forEach { board ->
                        CreatedBoardRow(board, onClick = { onOpenBoard(board) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        trailingIcon?.let {
            Spacer(Modifier.width(4.dp))
            Icon(
                it,
                contentDescription = null,
                tint = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun CreatedBoardRow(board: BaItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = board.avatar, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = board.name.ifBlank { "未命名板块" },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    detail: ConversationDetail,
    onMessage: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    onAdd: () -> Unit,
    isAdded: Boolean?,
    isAdding: Boolean,
    isChangingMute: Boolean,
    isLeaving: Boolean,
    introductionExpanded: Boolean,
    onIntroductionClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(4.dp))
        Avatar(url = detail.avatarUrl, size = 88.dp, canView = true)
        Spacer(Modifier.height(10.dp))
        Text(
            detail.name.ifBlank { "未知群聊" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${detail.memberCount ?: 0} 位成员",
            color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAdded == true) {
                TelegramAction(Modifier.weight(1f), AppIcons.ChatBubbleOutline, "消息", onMessage)
                TelegramAction(
                    Modifier.weight(1f),
                    if (detail.doNotDisturb) AppIcons.NotificationsOff else AppIcons.Notifications,
                    if (detail.doNotDisturb) "取消静音" else "静音",
                    onMute,
                    isChangingMute
                )
                TelegramAction(
                    Modifier.weight(1f),
                    AppIcons.Logout,
                    "退出",
                    onLeave,
                    isLeaving,
                    isDanger = true
                )
            } else {
                TelegramAction(
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcons.PersonAdd,
                    label = "加入群聊",
                    onClick = onAdd,
                    loading = isAdding
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        GroupIdentityInfo(detail)
        if (detail.introduction.isNotBlank()) {
            LiquidGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(onClick = onIntroductionClick),
                shape = RoundedCornerShape(18.dp),
                color = detailGlassColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                blurRadius = 6.dp,
                lensHeight = 6.dp,
                lensAmount = 12.dp
            ) {
                IntroductionContent(
                    introduction = detail.introduction,
                    expanded = introductionExpanded,
                    onExpand = onIntroductionClick
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupIdentityInfo(detail: ConversationDetail) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyValue: (String, String) -> Unit = { label, value ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            Toast.makeText(context, "${label}已复制", Toast.LENGTH_SHORT).show()
        }
    }

    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = detailGlassColor(MaterialTheme.colorScheme.surfaceContainerHigh),
        blurRadius = 6.dp,
        lensHeight = 6.dp,
        lensAmount = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupIdentityColumn(
                label = "群 ID",
                value = detail.chatId,
                onLongClick = { copyValue("群 ID", detail.chatId) },
                modifier = Modifier.weight(1f)
            )
            detail.groupCode?.takeIf { it.isNotBlank() }?.let { groupCode ->
                VerticalDivider(
                    modifier = Modifier.height(44.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                GroupIdentityColumn(
                    label = "群口令",
                    value = groupCode,
                    onLongClick = { copyValue("群口令", groupCode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupIdentityColumn(
    label: String,
    value: String,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        GroupIdentityText(
            value = value,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupIdentityText(
    value: String,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun IntroductionContent(
    introduction: String,
    expanded: Boolean,
    onExpand: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    var hasOverflow by remember(introduction) { mutableStateOf(false) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.padding(14.dp)) {
        Box(
            modifier = Modifier.animateContentSize()
        ) {
            SelectionContainer {
                Text(
                    text = introduction,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { hasOverflow = it.hasVisualOverflow }
                )
            }
            if (!expanded && hasOverflow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    cardColor.copy(alpha = 0.8f),
                                    cardColor
                                )
                            )
                        )
                        .clickable(onClick = onExpand),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        "更多",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "简介",
                style = MaterialTheme.typography.labelMedium,
                color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                modifier = Modifier.size(16.dp),
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText(
                                    "简介",
                                    introduction
                                )
                            )
                        )
                        Toast.makeText(context, "简介已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(AppIcons.ContentCopy, contentDescription = null)
            }
        }
    }
}

@Composable
private fun TelegramAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    loading: Boolean = false,
    isDanger: Boolean = false,
    enabled: Boolean = true
) {
    val actionColor = when {
        !enabled -> resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
        isDanger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    LiquidGlassSurface(
        modifier = modifier
            .height(72.dp)
            .clickable(
                enabled = enabled && !loading,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = detailGlassColor(if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }),
        blurRadius = 6.dp,
        lensHeight = 8.dp,
        lensAmount = 14.dp
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = actionColor.copy(alpha = if (enabled) 0.16f else 0.08f),
                    contentColor = actionColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled && !isDanger) {
                    resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurface)
                } else {
                    actionColor
                }
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    canManage: Boolean,
    canSetAdmin: Boolean,
    onClick: () -> Unit,
    onKick: () -> Unit,
    onGag: () -> Unit,
    onAdminToggle: () -> Unit
) {
    var expanded by remember(member.userId) { mutableStateOf(false) }
    
    ListItem(
        onClick = onClick,
        leadingContent = {
            Avatar(url = member.avatarUrl, size = 46.dp)
        },
        trailingContent = {
            if (canManage) {
                Box {
                    ExpressiveOverflowIconButton(
                        expanded = expanded,
                        onClick = { expanded = !expanded },
                        contentDescription = "管理成员"
                    )
                    ExpressiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (canSetAdmin) {
                            DropdownMenuItem(
                                text = { Text(if (member.permissionLevel == 2) "取消管理员" else "设置管理员") },
                                onClick = { expanded = false; onAdminToggle() },
                                leadingIcon = { Icon(AppIcons.AdminPanelSettings, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (member.isGag) "取消禁言" else "禁言") },
                            onClick = { expanded = false; onGag() },
                            leadingIcon = { Icon(AppIcons.MicOff, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("踢出群聊", color = MaterialTheme.colorScheme.error) },
                            onClick = { expanded = false; onKick() },
                            leadingIcon = { Icon(AppIcons.PersonRemove, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    member.name.ifBlank { "未知用户" },
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when (member.permissionLevel) {
                    100 -> MemberRoleBadge("群主")
                    2 -> MemberRoleBadge("管理员")
                }
            }
            Text(
                when {
                    member.permissionLevel == 100 -> "群主"
                    member.permissionLevel >= 2 -> "管理员"
                    member.isGag -> "已禁言"
                    else -> "成员"
                },
                style = MaterialTheme.typography.bodySmall,
                color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}

@Composable
private fun BotRow(bot: BotItem, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Avatar(url = bot.avatarUrl, size = 46.dp)
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                bot.name.ifBlank { "未知机器人" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bot.introduction.isNotBlank()) {
                Text(
                    bot.introduction,
                    style = MaterialTheme.typography.bodySmall,
                    color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemberManagementDialogs(
    kickTarget: GroupMember?,
    gagTarget: GroupMember?,
    adminTarget: GroupMember?,
    showKickConfirm: Boolean,
    showGagDialog: Boolean,
    showAdminConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirmKick: () -> Unit,
    onConfirmGag: (Int) -> Unit,
    onConfirmAdminToggle: () -> Unit
) {
    val gagOptions = listOf(
        600 to "10分钟",
        3600 to "1小时",
        21600 to "6小时",
        43200 to "12小时",
        -1 to "永久"
    )
    var selectedGagIndex by remember(gagTarget?.userId) { mutableIntStateOf(0) }
    if (showKickConfirm) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("踢出成员") },
            text = { Text("确定要踢出 ${kickTarget?.name ?: "该成员"} 吗？") },
            confirmButton = { TextButton(onClick = onConfirmKick) { Text("踢出", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    }
    if (showGagDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (gagTarget?.isGag == true) "取消禁言" else "禁言 ${gagTarget?.name ?: ""}") },
            text = {
                if (gagTarget?.isGag == true) {
                    Text("确定要取消 ${gagTarget.name} 的禁言吗？")
                } else {
                    Column {
                        Text("禁言时长：${gagOptions[selectedGagIndex].second}")
                        LiquidGlassSlider(
                            value = selectedGagIndex.toFloat(),
                            onValueChange = { selectedGagIndex = it.toInt() },
                            valueRange = 0f..gagOptions.lastIndex.toFloat(),
                            steps = gagOptions.lastIndex - 1
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("10分钟", style = MaterialTheme.typography.labelSmall)
                            Text("永久", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onConfirmGag(if (gagTarget?.isGag == true) 0 else gagOptions[selectedGagIndex].first) }
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    }
    if (showAdminConfirm) {
        val target = adminTarget ?: return
        val isAdmin = target.permissionLevel == 2
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (isAdmin) "取消管理员" else "设置管理员") },
            text = {
                Text(
                    if (isAdmin) {
                        "确定要取消 ${target.name.ifBlank { "该成员" }} 的管理员权限吗？"
                    } else {
                        "确定要设置 ${target.name.ifBlank { "该成员" }} 为管理员吗？"
                    }
                )
            },
            confirmButton = { TextButton(onClick = onConfirmAdminToggle) { Text("确定") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )
    }
}

@Composable
private fun MemberRoleBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MediaRow(
    row: List<MessageItem>,
    media: List<MessageItem>,
    detail: ConversationDetail,
    hasBottomSpacing: Boolean
) {
    val context = LocalContext.current
    val spacing = with(LocalDensity.current) { 5.toDp() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = spacing,
                end = spacing,
                bottom = if (hasBottomSpacing) spacing else 0.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        row.forEach { message ->
            val url =
                if (message.contentType == MessageItem.CONTENT_TYPE_VIDEO) message.videoUrl else message.imageUrl
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clickable {
                        val previews = media.map { item ->
                            if (item.contentType == MessageItem.CONTENT_TYPE_VIDEO) {
                                videoMessagePreviewItem(
                                    item.videoUrl.orEmpty(),
                                    item.msgId,
                                    item.msgSeq
                                )
                            } else imageMessagePreviewItem(
                                item.imageUrl.orEmpty(),
                                item.msgId,
                                item.msgSeq
                            )
                        }
                        showImageViewer(
                            context, previews, media.indexOfFirst { it.msgId == message.msgId },
                            MediaViewerPagination(detail.chatId, detail.chatType)
                        )
                    }
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "媒体",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (message.contentType == MessageItem.CONTENT_TYPE_VIDEO) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.68f), RectangleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "视频",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        formatVideoDuration(message.videoTime)?.let { duration ->
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        repeat(3 - row.size) { Spacer(Modifier
            .weight(1f)
            .aspectRatio(1f)) }
    }
}

@Composable
private fun DetailCardSegment(
    cardColor: Color,
    isTop: Boolean = false,
    isBottom: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit
) {
    val shape = when {
        isTop -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        isBottom -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RectangleShape
    }
    LiquidGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = shape,
        color = detailGlassColor(cardColor),
        blurRadius = 6.dp
    ) {
        if (minHeight == null) {
            Column { content() }
        } else {
            Box(
                modifier = Modifier.height(minHeight),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AutoLoadingRow(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(
                "继续下滑加载更多",
                color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun LegacyDetail(
    modifier: Modifier,
    detail: ConversationDetail,
    isAdded: Boolean?,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onMessage: () -> Unit
) {
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(url = detail.avatarUrl, size = 88.dp, canView = true)
        Spacer(Modifier.height(14.dp))
        Text(
            detail.name.ifBlank { "未知" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (detail.introduction.isNotBlank()) Text(
            detail.introduction,
            textAlign = TextAlign.Center,
            color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = if (isAdded == false) onAdd else onMessage,
            enabled = !isAdding,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAdded == false) "添加" else "进入聊天")
        }
    }
}

@Composable
private fun LoadingContent() =
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun EmptyContent(text: String) = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text(
        text,
        color = resolvedLiquidGlassContentColor(MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

@Composable
private fun ErrorContent(message: String, retry: () -> Unit) = Column(
    Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
) {
    Text(message, color = MaterialTheme.colorScheme.error)
    TextButton(onClick = retry) { Text("重试") }
}
