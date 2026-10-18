package com.juhao.murexide.ui.conversation

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.juhao.murexide.R
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.HomeSearchResult
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.UiState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity

@Composable
fun ConversationListScreen(
    modifier: Modifier = Modifier,
    token: String,
    accountId: String,
    accountAvatar: String,
    innerPadding: PaddingValues,
    bigScreenMode: Boolean,
    onConversationClick: (ConversationItem) -> Unit,
    onCreateClick: (CreationKind) -> Unit = {},
    currentConversation: ConversationItem? = null,
    searchViewModel: HomeSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            HomeSearchViewModel(token) as T
    })
) {
    val context = LocalContext.current
    val viewModel: ConversationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation_$accountId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val storage = AccountStorage.getInstance(context)
                return ConversationViewModel(token, accountId, storage) as T
            }
        }
    )

    val drafts by viewModel.drafts.collectAsState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()
    val isWsConnected by viewModel.isWsConnected.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val filter by viewModel.filter.collectAsState()

    val themeColor by UiState.themeColor
    val listContainerColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val listState = rememberLazyListState()
    val stickyIds by viewModel.stickyIds.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

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

    val searchState by searchViewModel.uiState.collectAsState()
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchBarExpanded = searchBarState.targetValue != SearchBarValue.Collapsed
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            enabled = searchBarExpanded,
            colors = TextFieldDefaults.colors(
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            onSearch = {
                searchViewModel.updateQuery(it)
            },
            placeholder = {
                Text(modifier = Modifier.clearAndSetSemantics {}, text = "搜索&发现")
            },
            leadingIcon = {
                if (searchBarState.targetValue != SearchBarValue.Collapsed) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                searchBarState.animateToCollapsed()
                            }
                        }
                    ) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = null)
                    }
                } else {
                    AutoMirroredIcon(AppIcons.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (searchBarState.targetValue == SearchBarValue.Collapsed) {
                    Avatar(url = accountAvatar, size = 36.dp, alwaysCircle = true, modifier = Modifier.padding(end = 2.dp))
                } else if (textFieldState.text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            textFieldState.clearText()
                            searchViewModel.updateQuery("")
                        }
                    ) {
                        Icon(AppIcons.Close, contentDescription = null)
                    }
                }
            }
        )
    }

    val hideFloatingButton by remember {
        derivedStateOf {
            val isScrollInProgress = listState.isScrollInProgress

            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val isAtBottom = visibleItems.isNotEmpty() && !listState.canScrollForward
            isScrollInProgress || isAtBottom
        }
    }

    val topBarColor = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = topBarColor,
        topBar = {
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    topBarColor,
                                    topBarColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                SearchBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp
                        )
                        .fillMaxWidth(),
                    state = searchBarState,
                    inputField = inputField
                )
            }
        },
        floatingActionButton = {
            Crossfade(targetState = hideFloatingButton) { hide ->
                if (hide) return@Crossfade
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                    FloatingActionButton(
                        onClick = { showCreateMenu = true }
                    ) {
                        Icon(AppIcons.Add, contentDescription = "添加")
                    }
                    DropdownMenu(
                        expanded = showCreateMenu,
                        onDismissRequest = { showCreateMenu = false },
                        shape = MenuDefaults.standaloneGroupShape
                    ) {
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
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(listContainerColor)
        ) {
            val state = uiState
            if (state is ConversationUiState.Success) {
                val filteredConversations = state.conversations.filter { conversation ->
                    when (filter) {
                        ConversationFilter.ALL -> true
                        ConversationFilter.UNREAD -> conversation.hasUnread || conversation.isAtMentioned
                        ConversationFilter.GROUP -> conversation.chatType == 2
                        ConversationFilter.USER -> conversation.chatType == 1
                        ConversationFilter.BOT -> conversation.chatType == 3
                    }
                }

                val (stickyConvs, normalConvs) = filteredConversations.partition {
                    stickyIds.contains(it.chatId)
                }

                val allowCollapseSticky = stickyConvs.size > 5
                val isStickyCollapsed = !isStickyExpanded && allowCollapseSticky

                val tabs = listOf("全部", "未读", "群聊", "用户", "机器人")
                val selectedTabIndex = when (filter) {
                    ConversationFilter.ALL -> 0
                    ConversationFilter.UNREAD -> 1
                    ConversationFilter.GROUP -> 2
                    ConversationFilter.USER -> 3
                    ConversationFilter.BOT -> 4
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = contentPadding.calculateTopPadding() + 4.dp),
                            isRefreshing = isRefreshing,
                            state = pullToRefreshState
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                    ) {
                        item(key = "tabBar") {
                            CapsuleTabBar(
                                tabs = tabs,
                                selectedTabIndex = selectedTabIndex,
                                scrollable = true,
                                onTabSelected = { index ->
                                    val newFilter = when (index) {
                                        0 -> ConversationFilter.ALL
                                        1 -> ConversationFilter.UNREAD
                                        2 -> ConversationFilter.GROUP
                                        3 -> ConversationFilter.USER
                                        4 -> ConversationFilter.BOT
                                        else -> ConversationFilter.ALL
                                    }
                                    viewModel.setFilter(newFilter)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        val totalItems = stickyConvs.size + normalConvs.size

                        if (totalItems == 0) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(top = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyPlaceholder(
                                        icon = AppIcons.ChatBubble,
                                        title = when (filter) {
                                            ConversationFilter.ALL -> "还没有聊天"
                                            ConversationFilter.UNREAD -> "没有未读消息"
                                            ConversationFilter.GROUP -> "还没有群聊"
                                            ConversationFilter.USER -> "还没有好友会话"
                                            ConversationFilter.BOT -> "还没有机器人"
                                        },
                                        subtitle = when (filter) {
                                            ConversationFilter.ALL -> "点右下角按钮开始创建群聊或机器人"
                                            ConversationFilter.UNREAD -> "全部消息都已读完"
                                            ConversationFilter.GROUP -> "点右下角按钮创建一个群聊"
                                            ConversationFilter.USER -> "去联系人页面开始一段对话吧"
                                            ConversationFilter.BOT -> "点右下角按钮创建一个机器人"
                                        }
                                    )
                                }
                            }
                        } else {
                            item(key = "divider") {
                                HorizontalDivider()
                            }

                            if (allowCollapseSticky) {
                                item(key = "collapseStickyButton") {
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
                                if (isStickyCollapsed) return@items
                                SwipeableConversationItem(
                                    modifier = Modifier.animateItem(),
                                    conversation = conversation,
                                    draft = drafts["${conversation.chatType}_${conversation.chatId}"],
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
                                    },
                                    onPin = { viewModel.pinConversation(conversation) },
                                    onUnpin = { viewModel.unpinConversation(conversation) },
                                    onDelete = { viewModel.removeConversation(conversation) }
                                )
                            }

                            item(key = "divider2") {
                                HorizontalDivider()
                            }

                            items(
                                items = normalConvs,
                                key = { item -> "normal_${item.chatType}:${item.chatId}" }
                            ) { conversation ->
                                SwipeableConversationItem(
                                    modifier = Modifier.animateItem(),
                                    conversation = conversation,
                                    draft = drafts["${conversation.chatType}_${conversation.chatId}"],
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
                                    },
                                    onPin = { viewModel.pinConversation(conversation) },
                                    onUnpin = { viewModel.unpinConversation(conversation) },
                                    onDelete = { viewModel.removeConversation(conversation) }
                                )
                            }
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
                    EmptyPlaceholder(
                        icon = AppIcons.Warning,
                        title = "加载失败",
                        subtitle = state.message,
                        primaryButton = {
                            Button(onClick = { viewModel.refresh() }) {
                                Text("重试")
                            }
                        }
                    )
                }
            }
        }
    }

    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        when {
            searchState.isLoading && searchState.results.isEmpty() -> LoadingScreen(Modifier)
            searchState.error != null -> EmptyPlaceholder(
                icon = AppIcons.Warning,
                title = "搜索失败",
                subtitle = searchState.error,
                primaryButton = {
                    TextButton(onClick = searchViewModel::retry) {
                        Text("重试")
                    }
                }
            )
            searchState.results.isEmpty() -> EmptyPlaceholder(
                icon = if (searchState.hasSearched) AppIcons.Search else AppIcons.Inbox,
                title = if (searchState.hasSearched) "未找到相关结果" else "暂无搜索内容",
                subtitle = if (searchState.hasSearched) "换个关键词试试" else "输入关键词开始搜索"
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                listOf(1 to "用户", 2 to "群聊", 3 to "机器人").forEach { (type, title) ->
                    val items = searchState.resultsFor(type)
                    if (items.isNotEmpty()) {
                        item {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp)
                            )
                        }
                        items(items, key = { "${it.chatType}:${it.chatId}" }) { result ->
                            SearchRow(result, { result ->
                                ConversationDetailActivity.start(
                                    context,
                                    result.chatId,
                                    result.chatType,
                                    result.name,
                                    result.avatarUrl
                                )
                            })
                        }
                    }
                }
            }
        }
    }
}

private enum class SwipeState {
    Closed,
    Open
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableConversationItem(
    modifier: Modifier = Modifier,
    conversation: ConversationItem,
    draft: String? = null,
    isSelected: Boolean = false,
    isSticky: Boolean = false,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val buttonWidth = 80.dp
    val totalWidth = buttonWidth * 2
    val totalWidthPx = with(density) { totalWidth.toPx() }

    val state = remember {
        AnchoredDraggableState(
            initialValue = SwipeState.Closed,
            anchors = DraggableAnchors {
                SwipeState.Closed at 0f
                SwipeState.Open at -totalWidthPx
            }
        )
    }

    LaunchedEffect(conversation.chatId, conversation.chatType, isSticky) {
        if (state.currentValue == SwipeState.Open) {
            state.animateTo(SwipeState.Closed)
        }
    }
    
    val themeColor by UiState.themeColor
    val listItemColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else if ((isSticky && themeColor != "WHITE") || (!isSticky && themeColor == "WHITE")) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    listItemColor
            )
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Horizontal
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .width(totalWidth)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        scope.launch { state.animateTo(SwipeState.Closed) }
                        if (isSticky) onUnpin() else onPin()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AppIcons.Keep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isSticky) "取消置顶" else "置顶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        scope.launch { state.animateTo(SwipeState.Closed) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
        ) {
            ConversationItem(
                modifier = Modifier,
                conversation = conversation,
                draft = draft,
                listItemColor = listItemColor,
                isSelected = isSelected,
                isSticky = isSticky,
                onClick = {
                    if (state.currentValue == SwipeState.Open) {
                        scope.launch { state.animateTo(SwipeState.Closed) }
                    } else {
                        onClick()
                    }
                }
            )
        }
    }
}

@Composable
fun ConversationItem(
    modifier: Modifier = Modifier,
    conversation: ConversationItem,
    draft: String? = null,
    listItemColor: Color,
    isSelected: Boolean = false,
    isSticky: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            Box{
                Avatar(
                    url = conversation.avatarUrl,
                    size = 52.dp
                )
                if (conversation.certificationLevel >= 1) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = listItemColor,
                                shape = CircleShape
                            )
                            .align(Alignment.BottomEnd)
                    ) {
                        Text(if (conversation.certificationLevel == 1) "官" else "城")
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = listItemColor
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                verticalAlignment = Alignment.Top
            ) {
                if (!draft.isNullOrBlank()) {
                    Row(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "[草稿]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = draft,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = conversation.chatContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) = Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) { ContainedLoadingIndicator() }

@Composable
private fun SearchRow(result: HomeSearchResult, onClick: (HomeSearchResult) -> Unit) {
    ListItem(
        onClick = { onClick(result) },
        leadingContent = {
            Avatar(url = result.avatarUrl, size = 44.dp)
        },
        trailingContent = {
            Icon(
                if (result.chatType == 2) AppIcons.Group else if (result.chatType == 3) AppIcons.SmartToy else AppIcons.Person,
                modifier = Modifier.size(18.dp),
                contentDescription = null
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(result.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.introduction.isNotBlank()) {
                Text(
                    result.introduction,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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