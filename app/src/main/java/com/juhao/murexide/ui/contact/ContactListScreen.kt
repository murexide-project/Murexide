package com.juhao.murexide.ui.contact

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.ContactGroup
import com.juhao.murexide.data.ContactItem
import com.juhao.murexide.data.ContactRequestItem
import com.juhao.murexide.ui.components.Avatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class ContactTab(
    val title: String,
    val chatType: Int,
    val icon: ImageVector,
    val emptyTitle: String,
    val emptyDescription: String
)

private val ContactTabs = listOf(
    ContactTab("好友", 1, Icons.Rounded.People, "还没有好友", "添加好友后会显示在这里"),
    ContactTab("我加入的群聊", 2, Icons.Rounded.Groups, "还没有加入群聊", "加入的群聊会显示在这里"),
    ContactTab("机器人", 3, Icons.Rounded.SmartToy, "还没有机器人", "添加的机器人会显示在这里")
)

private val ContactIndexCellSize = 14.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    token: String,
    onContactClick: (ContactItem) -> Unit,
    onNewMessagesVisibilityChanged: (Boolean) -> Unit = {},
    viewModel: ContactViewModel = remember(token) { ContactViewModel(token) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNewMessages by rememberSaveable { mutableStateOf(false) }

    val setNewMessagesVisible: (Boolean) -> Unit = { visible ->
        showNewMessages = visible
        onNewMessagesVisibilityChanged(visible)
    }

    BackHandler(enabled = showNewMessages) {
        setNewMessagesVisible(false)
    }

    LaunchedEffect(showNewMessages) {
        onNewMessagesVisibilityChanged(showNewMessages)
    }

    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearUserMessage()
    }

    AnimatedContent(
        targetState = showNewMessages,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = 80)
                )) togetherWith (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    targetOffsetX = { fullWidth -> -fullWidth / 4 }
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 160)
                ))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    initialOffsetX = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, delayMillis = 80)
                )) togetherWith (slideOutHorizontally(
                    animationSpec = tween(durationMillis = 300),
                    targetOffsetX = { fullWidth -> fullWidth }
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 160)
                ))
            }
        },
        label = "contact_page_transition"
    ) { newMessagesVisible ->
        if (newMessagesVisible) {
            NewMessagesScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onBack = { setNewMessagesVisible(false) },
                onRefresh = { viewModel.loadRequests() },
                onRespond = viewModel::respondToRequest
            )
        } else {
            ContactDirectoryScreen(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                onRefresh = viewModel::refreshAll,
                onOpenNewMessages = {
                    setNewMessagesVisible(true)
                    if (!uiState.isRequestsLoading) {
                        viewModel.loadRequests(showLoading = uiState.requests.isEmpty())
                    }
                },
                onContactClick = onContactClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDirectoryScreen(
    uiState: ContactUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenNewMessages: () -> Unit,
    onContactClick: (ContactItem) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val friendListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val botListState = rememberLazyListState()
    val currentTab = ContactTabs[selectedTabIndex]
    val listState = when (selectedTabIndex) {
        0 -> friendListState
        1 -> groupListState
        else -> botListState
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "通讯录",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "刷新通讯录")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )

                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    ContactTabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = tab.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        ContactTabContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            groups = uiState.contactGroups,
            tab = currentTab,
            listState = listState,
            isLoading = uiState.isLoading,
            error = uiState.error,
            pendingRequestCount = uiState.pendingRequestCount,
            isRequestsLoading = uiState.isRequestsLoading,
            onRetry = onRefresh,
            onOpenNewMessages = onOpenNewMessages,
            onContactClick = onContactClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactTabContent(
    modifier: Modifier,
    groups: List<ContactGroup>,
    tab: ContactTab,
    listState: LazyListState,
    isLoading: Boolean,
    error: String?,
    pendingRequestCount: Int,
    isRequestsLoading: Boolean,
    onRetry: () -> Unit,
    onOpenNewMessages: () -> Unit,
    onContactClick: (ContactItem) -> Unit
) {
    val contacts = remember(groups, tab.chatType) {
        groups.asSequence()
            .filter { it.chatType == tab.chatType }
            .flatMap { it.contacts.asSequence() }
            .toList()
    }
    val sections = remember(contacts) { buildContactSections(contacts) }
    val showNewMessageShortcut = tab.chatType == 1
    val headerIndices = remember(sections, showNewMessageShortcut) {
        var itemIndex = if (showNewMessageShortcut) 1 else 0
        buildMap {
            sections.forEach { section ->
                put(section.initial, itemIndex)
                itemIndex += section.contacts.size + 1
            }
        }
    }
    val currentSection by remember(listState, headerIndices) {
        derivedStateOf {
            headerIndices
                .filterValues { it <= listState.firstVisibleItemIndex }
                .maxByOrNull { it.value }
                ?.key
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showNewMessageShortcut) {
                item(key = "new_messages", contentType = "shortcut") {
                    NewMessageShortcut(
                        pendingCount = pendingRequestCount,
                        isLoading = isRequestsLoading,
                        onClick = onOpenNewMessages
                    )
                }
            }

            if (sections.isEmpty()) {
                item(key = "contact_state", contentType = "state") {
                    when {
                        isLoading -> LoadingState(
                            modifier = Modifier.fillParentMaxHeight(
                                if (showNewMessageShortcut) 0.7f else 1f
                            )
                        )

                        error != null -> ErrorState(
                            modifier = Modifier.fillParentMaxHeight(
                                if (showNewMessageShortcut) 0.7f else 1f
                            ),
                            message = error,
                            onRetry = onRetry
                        )

                        else -> EmptyState(
                            modifier = Modifier.fillParentMaxHeight(
                                if (showNewMessageShortcut) 0.7f else 1f
                            ),
                            icon = tab.icon,
                            title = tab.emptyTitle,
                            description = tab.emptyDescription
                        )
                    }
                }
            } else {
                sections.forEach { section ->
                    stickyHeader(key = "section_${tab.chatType}_${section.initial}") {
                        ContactSectionHeader(section.initial)
                    }
                    items(
                        items = section.contacts,
                        key = { "${it.chatType}:${it.chatId}" },
                        contentType = { "contact" }
                    ) { contact ->
                        ContactItemRow(
                            contact = contact,
                            onClick = { onContactClick(contact) }
                        )
                    }
                }
            }
        }

        if (sections.isNotEmpty()) {
            AlphabetFastScroller(
                modifier = Modifier.fillMaxSize(),
                headerIndices = headerIndices,
                listState = listState,
                currentSection = currentSection
            )
        }
    }
}

@Composable
private fun NewMessageShortcut(
    pendingCount: Int,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                contentDescription = "新消息"
                stateDescription = if (pendingCount > 0) {
                    "有 $pendingCount 条待处理申请或邀请"
                } else {
                    "没有待处理申请或邀请"
                }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgedBox(
                badge = {
                    if (pendingCount > 0) {
                        Badge()
                    }
                }
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "新消息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        pendingCount > 0 -> "$pendingCount 条申请或邀请待处理"
                        isLoading -> "正在检查新消息…"
                        else -> "好友与群聊申请、邀请"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactSectionHeader(initial: Char) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = initial.toString(),
            modifier = Modifier.padding(start = 18.dp, end = 36.dp, top = 6.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ContactItemRow(
    contact: ContactItem,
    onClick: () -> Unit
) {
    val displayName = contactDisplayName(contact)
    val supportingText = when {
        !contact.remark.isNullOrBlank() && contact.name.isNotBlank() && contact.remark != contact.name ->
            contact.name
        contact.chatType == 2 -> "群聊 · ${contact.chatId}"
        contact.chatType == 3 -> "机器人 · ${contact.chatId}"
        else -> "ID: ${contact.chatId}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 36.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(url = contact.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp, end = 32.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun AlphabetFastScroller(
    modifier: Modifier,
    headerIndices: Map<Char, Int>,
    listState: LazyListState,
    currentSection: Char?
) {
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val selectLetter: (Char) -> Unit = { requested ->
        closestAvailableInitial(requested, headerIndices.keys)?.let { target ->
            activeLetter = target
            headerIndices[target]?.let { itemIndex ->
                scrollJob?.cancel()
                scrollJob = scope.launch { listState.scrollToItem(itemIndex) }
            }
        }
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = activeLetter != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeLetter?.toString().orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(ContactIndexCellSize * ContactIndexLetters.size.toFloat())
                .width(30.dp)
                .pointerInput(headerIndices) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun selectAt(y: Float) {
                            val index = ((y / size.height.coerceAtLeast(1)) * ContactIndexLetters.size)
                                .toInt()
                                .coerceIn(ContactIndexLetters.indices)
                            selectLetter(ContactIndexLetters[index])
                        }

                        selectAt(down.position.y)
                        down.consume()
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            pressed = change.pressed
                            if (pressed) {
                                selectAt(change.position.y)
                                change.consume()
                            }
                        }
                        activeLetter = null
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ContactIndexLetters.forEach { letter ->
                val isSelected = (activeLetter ?: currentSection) == letter
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "$letter 索引"
                            onClick(label = "跳转到 $letter") {
                                selectLetter(letter)
                                activeLetter = null
                                true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(ContactIndexCellSize)
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter.toString(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMessagesScreen(
    uiState: ContactUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRespond: (Int, Boolean) -> Unit
) {
    val requests = remember(uiState.requests) {
        uiState.requests.sortedWith(
            compareByDescending<ContactRequestItem> { it.isPending }
                .thenByDescending { it.invitedAt }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("新消息", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回通讯录")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !uiState.isRequestsLoading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新新消息")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isRequestsLoading && requests.isEmpty() -> LoadingState(Modifier.fillMaxSize())
                uiState.requestsError != null && requests.isEmpty() -> ErrorState(
                    modifier = Modifier.fillMaxSize(),
                    message = uiState.requestsError,
                    onRetry = onRefresh
                )
                requests.isEmpty() -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Rounded.Inbox,
                    title = "暂无新消息",
                    description = "好友和群聊申请、邀请会显示在这里"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isRequestsLoading) {
                        item(key = "request_loading") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                    if (uiState.requestsError != null) {
                        item(key = "request_error") {
                            InlineError(message = uiState.requestsError, onRetry = onRefresh)
                        }
                    }
                    items(
                        items = requests,
                        key = { it.requestId },
                        contentType = { "request" }
                    ) { request ->
                        RequestSurface(
                            request = request,
                            isProcessing = request.requestId in uiState.processingRequestIds,
                            onRespond = { accept -> onRespond(request.requestId, accept) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestSurface(
    request: ContactRequestItem,
    isProcessing: Boolean,
    onRespond: (Boolean) -> Unit
) {
    val description = buildList {
        request.contextName?.let(::add)
        add(request.typeLabel)
    }.joinToString(" · ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                RequestAvatar(
                    url = request.displayAvatarUrl,
                    type = when {
                        request.sourceType == 2 || request.targetType == 2 -> 2
                        request.sourceType == 3 || request.targetType == 3 -> 3
                        else -> 1
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = request.displayName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!request.isPending) {
                            RequestStatusPill(request.resultLabel)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (request.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = request.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (request.invitedAtText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = request.invitedAtText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (request.isPending) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "处理中…",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(onClick = { onRespond(false) }) {
                            Text("拒绝")
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = { onRespond(true) }) {
                            Text("同意")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestAvatar(url: String, type: Int) {
    if (url.isNotBlank()) {
        Avatar(url = url, size = 52.dp)
        return
    }

    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (type) {
                    2 -> Icons.Rounded.Groups
                    3 -> Icons.Rounded.SmartToy
                    else -> Icons.Rounded.People
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun RequestStatusPill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
