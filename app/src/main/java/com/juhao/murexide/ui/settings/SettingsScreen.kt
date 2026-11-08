package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.ui.about.AboutActivity
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.settings.chatbackground.ChatBackgroundActivity
import com.juhao.murexide.ui.settings.userbot.UserBotActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    page: Int = 0,
    highlightId: String? = null,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchScope = rememberCoroutineScope()

    BackHandler(enabled = searchBarState.currentValue == SearchBarValue.Collapsed) {
        onBack()
    }

    if (page == 0) {
        SettingsHomeScreen(
            onBack = onBack,
            onOpenPage = { targetPage ->
                context.startActivity(
                    Intent(context, SettingsActivity::class.java).putExtra("page", targetPage)
                )
            },
            onOpenSearch = {
                searchScope.launch {
                    textFieldState.clearText()
                    searchBarState.animateToExpanded()
                }
            },
            onOpenAbout = {
                context.startActivity(Intent(context, AboutActivity::class.java))
            }
        )
    } else {
        StyledScaffold(
            title = { Text(SettingsPageTitles[page] ?: "设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = {
                    searchScope.launch {
                        textFieldState.clearText()
                        searchBarState.animateToExpanded()
                    }
                }) {
                    Icon(AppIcons.Search, contentDescription = "搜索")
                }
            }
        ) { padding ->
            when (page) {
                1 -> AppearanceControl(highlightId, contentPadding = padding)
                2 -> BubbleControl(highlightId, contentPadding = padding)
                3 -> ActionControl(highlightId, contentPadding = padding)
                4 -> UpdateControl(highlightId, contentPadding = padding)
                5 -> WebSocketControl(highlightId, contentPadding = padding)
                6 -> ScreenshotControl(highlightId, contentPadding = padding)
                7 -> AccountControl(highlightId, contentPadding = padding, onLogout = onLogout)
            }
        }
    }

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = {
                    searchScope.launch { searchBarState.animateToCollapsed() }
                },
                placeholder = { Text("搜索设置") },
                leadingIcon = {
                    if (searchBarState.targetValue != SearchBarValue.Collapsed) {
                        IconButton(onClick = {
                            searchScope.launch { searchBarState.animateToCollapsed() }
                        }) {
                            AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        Icon(AppIcons.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = { textFieldState.clearText() }) {
                            Icon(AppIcons.Close, contentDescription = "清除")
                        }
                    }
                }
            )
        }
    ) {
        val query = textFieldState.text.toString()
        val results = remember(query) {
            if (query.isBlank()) AllSettingsIndex
            else AllSettingsIndex.filter {
                it.title.contains(query, ignoreCase = true)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (results.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                AppIcons.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "未找到相关设置",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val grouped = results.groupBy { it.group }
                grouped.forEach { (group, items) ->
                    item(key = "header_$group") {
                        SettingsGroupHeader(group)
                    }
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        SettingsGroupSurface(index = index, count = items.size) {
                            SettingsItem(
                                icon = item.icon,
                                title = item.title,
                                onClick = {
                                    searchScope.launch {
                                        searchBarState.animateToCollapsed()
                                    }
                                    context.startActivity(
                                        Intent(context, SettingsActivity::class.java)
                                            .putExtra("page", item.page)
                                            .putExtra("highlight_id", item.id)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }
    val wsManager = remember { WebSocketManager.getInstance() }

    val connected by wsManager.connectionState.collectAsState()
    var currentAccount by remember { mutableStateOf<UserAccount?>(null) }

    LaunchedEffect(Unit) { currentAccount = accountStorage.getCurrentAccount() }

    StyledScaffold(
        title = { Text("设置") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(AppIcons.Search, contentDescription = "搜索")
            }
            IconButton(onClick = onOpenAbout) {
                Icon(AppIcons.Info, contentDescription = "关于")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding
        ) {
            currentAccount?.let { account ->
                item(key = "account_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(url = account.avatar, size = 64.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account.username,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "ID: ${account.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            FilledTonalButton(onClick = { onOpenPage(7) }) {
                                Icon(AppIcons.People, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("账号")
                            }
                        }
                    }
                }
            }

            item { SettingsGroupHeader("个性化") }
            item {
                SettingsGroupSurface(index = 0, count = 2) {
                    SettingsItem(
                        icon = AppIcons.Style,
                        title = "应用外观",
                        subtitle = "主题模式、主题颜色、液态玻璃等",
                        onClick = { onOpenPage(1) }
                    )
                }
            }
            item {
                SettingsGroupSurface(index = 1, count = 2) {
                    SettingsItem(
                        icon = AppIcons.ChatBubble,
                        title = "消息气泡",
                        subtitle = "自定义气泡透明度、圆角大小、控件显示等",
                        onClick = { onOpenPage(2) }
                    )
                }
            }

            item { SettingsGroupHeader("功能") }
            item {
                SettingsGroupSurface(index = 0, count = 4) {
                    SettingsItem(
                        icon = AppIcons.DragClick,
                        title = "行为",
                        subtitle = "大屏模式、截图、通知等",
                        onClick = { onOpenPage(3) }
                    )
                }
            }
            item {
                SettingsGroupSurface(index = 1, count = 4) {
                    SettingsItem(
                        icon = AppIcons.Link,
                        title = "WebSocket",
                        subtitle = "连接状态、自动重连开关",
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (connected) Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                        },
                        onClick = { onOpenPage(5) }
                    )
                }
            }
            item {
                SettingsGroupSurface(index = 2, count = 4) {
                    SettingsItem(
                        icon = AppIcons.SmartToy,
                        title = "用户 Bot",
                        subtitle = "关键词自动回复、自动撤回",
                        onClick = { UserBotActivity.start(context) }
                    )
                }
            }
            item {
                SettingsGroupSurface(index = 3, count = 4) {
                    SettingsItem(
                        icon = AppIcons.Image,
                        title = "聊天背景",
                        subtitle = "为聊天设置背景图",
                        onClick = {
                            context.startActivity(Intent(context, ChatBackgroundActivity::class.java))
                        }
                    )
                }
            }

            item { SettingsGroupHeader("应用") }
            item {
                SettingsGroupSurface(index = 0, count = 2) {
                    SettingsItem(
                        icon = AppIcons.Update,
                        title = "更新",
                        subtitle = "检查更新、更新频道",
                        onClick = { onOpenPage(4) }
                    )
                }
            }
            item {
                SettingsGroupSurface(index = 1, count = 2) {
                    SettingsItem(
                        icon = AppIcons.Person,
                        title = "账号",
                        subtitle = "切换账号、提取 Token、退出登录",
                        onClick = { onOpenPage(7) }
                    )
                }
            }
        }
    }
}