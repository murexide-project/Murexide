package com.juhao.murexide.ui.conversation

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    accountAvatar: String,
    innerPadding: PaddingValues,
    bigScreenMode: Boolean,
    onConversationClick: (ConversationItem) -> Unit,
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
    ),
    searchViewModel: HomeSearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>) =
            HomeSearchViewModel(token) as T
    })
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
    
    val searchState by searchViewModel.uiState.collectAsState()
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {
                    searchViewModel.updateQuery(it)
                },
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "搜索群、用户、机器人")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (textFieldState.text.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    textFieldState.clearText()
                                    searchViewModel.updateQuery("")
                                }
                            ) {
                                Icon(AppIcons.Close, contentDescription = null)
                            }
                        }
                        if (searchBarState.targetValue == SearchBarValue.Collapsed) {
                            Avatar(url = accountAvatar, size = 36.dp, alwaysCircle = true, modifier = Modifier.padding(end = 2.dp))
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

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            Box {
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
                SearchBar(
                    modifier = Modifier.statusBarsPadding().padding(12.dp),
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
                        onClick = { viewModel.refresh() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = "刷新")
                    }
                    Spacer(Modifier.height(12.dp))
                    Box {
                        FloatingActionButton(
                            onClick = { showCreateMenu = true },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(AppIcons.Add, contentDescription = "添加")
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
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(listContainerColor)
                .hazeSource(hazeState)
        ) {
            val state = uiState
            if (state is ConversationUiState.Success) {
                val (stickyConvs, normalConvs) = state.conversations.partition { 
                    stickyIds.contains(it.chatId) 
                }

                val allowCollapseSticky = stickyConvs.size > 5
                val isStickyCollapsed = !isStickyExpanded && allowCollapseSticky

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
                    state = listState,
                    contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = innerPadding.calculateBottomPadding())
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

                        item(key = "divider2") {
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
            
            if (isRefreshing) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(contentPadding)) }
        }
    }
    
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        when {
            searchState.isLoading && searchState.results.isEmpty() -> LoadingScreen(Modifier)
            searchState.error != null -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(searchState.error!!)
                Spacer(Modifier.size(12.dp))
                TextButton(onClick = searchViewModel::retry) { Text("重试") }
            }
            searchState.results.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    AppIcons.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (searchState.hasSearched) "未找到相关结果" else "暂无搜索内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    
    ListItem(
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
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                listItemColor
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
                Text(
                    text = conversation.chatContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
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
fun LoadingScreen(modifier: Modifier = Modifier) = Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) { CircularProgressIndicator() }

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
