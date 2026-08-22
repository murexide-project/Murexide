package com.juhao.murexide.ui.conversation

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.juhao.murexide.R
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.UiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    token: String,
    accountId: String,
    bigScreenMode: Boolean,
    onConversationClick: (ConversationItem) -> Unit,
    onSearchClick: (IntOffset) -> Unit = {},
    onCreateClick: (CreationKind) -> Unit = {},
    currentConversation: ConversationItem? = null,
    viewModel: ConversationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation_$accountId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConversationViewModel(token, accountId) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()
    val isWsConnected by viewModel.isWsConnected.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    val themeColor by UiState.themeColor
    val listContainerColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val listState = rememberLazyListState()
    val stickyIds by viewModel.stickyIds.collectAsState()
    
    LaunchedEffect(uiState) {
        if (uiState is ConversationUiState.Success) {
            val conversations = (uiState as ConversationUiState.Success).conversations
            if (conversations.isNotEmpty()) {
                val firstVisibleIndex = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset
                
                if (firstVisibleIndex <= 1 && firstVisibleOffset < 200) {
                    listState.animateScrollToItem(0)
                }
            }
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setForegroundSyncEnabled(true)
        onDispose { viewModel.setForegroundSyncEnabled(false) }
    }

    var showCreateMenu by remember { mutableStateOf(false) }
    var searchButtonCenter by remember { mutableStateOf<IntOffset?>(null) }
    
    val scope = rememberCoroutineScope()
    
    val settingsStorage = remember { SettingsStorage(context) }
    val isStickyExpanded by settingsStorage.showStickyFlow.collectAsState(initial = true)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.thin().copy(
                                blurRadius = 32.dp,
                                noiseFactor = 0f,
                            ),
                            block = null,
                        )
                )
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.app_name),
                                maxLines = 1
                            )
                            if (!isWsConnected) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.error
                                ) {}
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    actions = {
                        IconButton(
                            onClick = { onSearchClick(searchButtonCenter ?: IntOffset.Zero) },
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInWindow()
                                searchButtonCenter = IntOffset(
                                    x = (position.x + coordinates.size.width / 2f).roundToInt(),
                                    y = (position.y + coordinates.size.height / 2f).roundToInt()
                                )
                            }
                        ) {
                            Icon(AppIcons.Search, contentDescription = "搜索")
                        }
                        Box {
                            StyledIconButton(onClick = { showCreateMenu = true }) {
                                Icon(AppIcons.Add, contentDescription = "创建")
                            }
                            DropdownMenu(
                                expanded = showCreateMenu,
                                onDismissRequest = { showCreateMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("创建群聊") },
                                    onClick = {
                                        showCreateMenu = false; onCreateClick(CreationKind.GROUP)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            AppIcons.Group,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("创建机器人") },
                                    onClick = {
                                        showCreateMenu = false; onCreateClick(CreationKind.BOT)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            AppIcons.SmartToy,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(listContainerColor)
                .hazeSource(hazeState)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                val state = uiState
                if (state is ConversationUiState.Success) {
                    val (stickyConvs, normalConvs) = state.conversations.partition { 
                        stickyIds.contains(it.chatId) 
                    }

                    val allowCollapseSticky = stickyConvs.size >= 5
                    val isStickyCollapsed = !isStickyExpanded && allowCollapseSticky

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        val totalItems = stickyConvs.size + normalConvs.size

                        if (totalItems == 0) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无会话")
                                }
                            }
                        } else {
                            if (allowCollapseSticky) {
                                stickyHeader(key = "collapseStickyButton") {
                                    val bkgolor = if (themeColor != "WHITE") {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                    ListItem(
                                        onClick = { 
                                            scope.launch {
                                                settingsStorage.setShowSticky(!isStickyExpanded)
                                            }
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = if (isStickyCollapsed) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowUp,
                                                contentDescription = null
                                            )
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = bkgolor
                                        )
                                    ) {
                                        Text(
                                            if (isStickyCollapsed) "${stickyConvs.size}个置顶会话" else "折叠置顶会话",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            items(
                                items = stickyConvs,
                                key = { item -> "sticky_${item.chatType}:${item.chatId}" }
                            ) { conversation ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                ) {
                                    if (isStickyCollapsed) return@Box
                                    ConversationItem(
                                        conversation = conversation,
                                        isSelected = currentConversation?.chatId == conversation.chatId &&
                                                currentConversation.chatType == conversation.chatType &&
                                                bigScreenMode,
                                        isSticky = true,
                                        onClick = {
                                            viewModel.clearUnread(
                                                conversation.chatId,
                                                conversation.chatType
                                            )
                                            onConversationClick(conversation)
                                        }
                                    )
                                }
                            }

                            stickyHeader(key = "divider") {
                                HorizontalDivider()
                            }

                            items(
                                items = normalConvs,
                                key = { item -> "normal_${item.chatType}:${item.chatId}" }
                            ) { conversation ->
                                ConversationItem(
                                    conversation = conversation,
                                    isSelected = currentConversation?.chatId == conversation.chatId &&
                                            currentConversation.chatType == conversation.chatType &&
                                            bigScreenMode,
                                    isSticky = false,
                                    onClick = {
                                        viewModel.clearUnread(
                                            conversation.chatId,
                                            conversation.chatType
                                        )
                                        onConversationClick(conversation)
                                    }
                                )
                            }
                        }
                    }
                } else if (state is ConversationUiState.Error) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("加载失败: ${state.message}")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationItem,
    isSelected: Boolean = false,
    isSticky: Boolean = false,
    onClick: () -> Unit
) {
    val themeColor by UiState.themeColor
    val listItemColor = if ((isSticky && themeColor != "WHITE") || (!isSticky && themeColor == "WHITE")) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    listItemColor
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = conversation.avatarUrl,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatTime(conversation.timestampMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.chatContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (conversation.doNotDisturb == 1) {
                    Icon(
                        imageVector = AppIcons.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (conversation.hasUnread || conversation.isAtMentioned) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Badges(
                        doNotDisturb = conversation.doNotDisturb == 1,
                        hasUnread = conversation.hasUnread,
                        isAtMentioned = conversation.isAtMentioned,
                        unreadCount = conversation.unreadMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun Badges(
    doNotDisturb: Boolean,
    hasUnread: Boolean,
    isAtMentioned: Boolean,
    unreadCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isAtMentioned) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text("@", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (hasUnread) {
            Badge(
                containerColor = if (doNotDisturb) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (doNotDisturb) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ) {
                AnimatedContent(
                    targetState = unreadCount,
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
                    label = "unread_count"
                ) { count ->
                    Text("$count", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0) return ""

    val date = Date(timestampMs)
    val now = Date()

    val todayCalendar = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val dateCalendar = Calendar.getInstance().apply {
        time = date
    }

    return when {
        date.after(todayCalendar.time) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }

        dateCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(date)
        }

        else -> {
            SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
        }
    }
}
