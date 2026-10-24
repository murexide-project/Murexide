package com.juhao.murexide.ui.settings.userbot

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.data.UserBotMatchType
import com.juhao.murexide.data.UserBotRule
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.components.SettingsGroup
import com.juhao.murexide.ui.components.SettingsItemCell
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserBotPage(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val recallSenderIds = remember { mutableStateListOf<String>() }
    val rules = remember { mutableStateListOf<UserBotRule>() }
    var loading by remember { mutableStateOf(true) }

    var editingIndex by remember { mutableIntStateOf(-1) }
    var editKeyword by remember { mutableStateOf("") }
    var editReply by remember { mutableStateOf("") }
    var editMatchType by remember { mutableStateOf(UserBotMatchType.CONTAINS) }
    var regexError by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    var showRecallDialog by remember { mutableStateOf(false) }
    var editRecallIds by remember { mutableStateOf("") }
    var editingRecallId by remember { mutableStateOf<String?>(null) }

    var showTip by remember { mutableStateOf(false) }
    var tipCountdown by remember { mutableIntStateOf(15) }

    val tabs = listOf("自动回复", "自动撤回")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    fun persistRecallIds() {
        scope.launch {
            settingsStorage.setUserBotRecallSenderIds(recallSenderIds.joinToString(","))
        }
    }

    LaunchedEffect(Unit) {
        recallSenderIds.clear()
        recallSenderIds.addAll(
            settingsStorage.getUserBotRecallSenderIds()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        )
        rules.clear()
        rules.addAll(UserBotManager.parseRules(settingsStorage.getUserBotRules()))
        loading = false

        if (!settingsStorage.getUserBotTipShown()) {
            showTip = true
            settingsStorage.setUserBotTipShown(true)
        }
    }

    LaunchedEffect(showTip) {
        if (!showTip) return@LaunchedEffect
        tipCountdown = 15
        while (tipCountdown > 0) {
            delay(1000)
            tipCountdown -= 1
        }
    }

    if (showTip) {
        AlertDialog(
            onDismissRequest = { if (tipCountdown <= 0) showTip = false },
            icon = { Icon(AppIcons.Info, contentDescription = null) },
            title = { Text("用户 Bot 使用提示") },
            text = {
                Column {
                    Text("用户 Bot 功能会通过 WebSocket 收到相应事件后根据你的设置进行响应。")
                    Spacer(Modifier.height(8.dp))
                    Text("请勿用于攻击、阻碍他人正常交流，违者后果自负。")
                    Spacer(Modifier.height(8.dp))
                    Text("软件不会做出除你设置以外的事情，如果你不放心可前往关于查看源代码，否则不要使用。")
                    Spacer(Modifier.height(8.dp))
                    Text("此功能仅在 App 运行且 WebSocket 已连接时生效。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTip = false },
                    enabled = tipCountdown <= 0
                ) {
                    Text(if (tipCountdown > 0) "请阅读（$tipCountdown s）" else "我知道了")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> AutoReplyTab(
                    rules = rules,
                    loading = loading,
                    onEdit = { index, rule ->
                        editingIndex = index
                        editKeyword = rule.keyword
                        editReply = rule.reply
                        editMatchType = rule.matchType
                        regexError = null
                        showEditor = true
                    },
                    onAdd = {
                        editingIndex = -1
                        editKeyword = ""
                        editReply = ""
                        editMatchType = UserBotMatchType.CONTAINS
                        regexError = null
                        showEditor = true
                    }
                )
                1 -> AutoRecallTab(
                    senderIds = recallSenderIds,
                    onAdd = {
                        editingRecallId = null
                        editRecallIds = ""
                        showRecallDialog = true
                    },
                    onEdit = { id ->
                        editingRecallId = id
                        editRecallIds = id
                        showRecallDialog = true
                    }
                )
            }
        }
    }

    if (showRecallDialog) {
        val isEditing = editingRecallId != null
        AlertDialog(
            onDismissRequest = { showRecallDialog = false },
            title = { Text(if (isEditing) "编辑撤回目标" else "添加撤回目标") },
            text = {
                Column {
                    Text(
                        if (isEditing) {
                            "修改这个发送者 ID。"
                        } else {
                            "收到这些用户发的消息时自动撤回，支持一次输入多个，用英文逗号分隔。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editRecallIds,
                        onValueChange = { editRecallIds = it },
                        label = { Text("发送者 ID") },
                        placeholder = { Text(if (isEditing) "10001" else "10001,10002") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isEditing) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val oldId = editingRecallId
                                if (oldId != null) {
                                    recallSenderIds.remove(oldId)
                                    persistRecallIds()
                                }
                                showRecallDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(AppIcons.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("删除此目标")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newIds = editRecallIds
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        if (isEditing) {
                            val oldId = editingRecallId
                            if (oldId != null) {
                                recallSenderIds.remove(oldId)
                            }
                            newIds.forEach { id ->
                                if (id !in recallSenderIds) recallSenderIds.add(id)
                            }
                        } else {
                            newIds.forEach { id ->
                                if (id !in recallSenderIds) recallSenderIds.add(id)
                            }
                        }
                        persistRecallIds()
                        showRecallDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecallDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingIndex >= 0) "编辑规则" else "添加规则") },
            text = {
                Column {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserBotMatchType.entries.forEachIndexed { index, type ->
                            val label = when (type) {
                                UserBotMatchType.CONTAINS -> "包含"
                                UserBotMatchType.EXACT -> "完全"
                                UserBotMatchType.REGEX -> "正则"
                            }
                            SegmentedButton(
                                selected = editMatchType == type,
                                onClick = {
                                    editMatchType = type
                                    regexError = if (type == UserBotMatchType.REGEX) {
                                        UserBotManager.validateRegex(editKeyword)
                                    } else null
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = UserBotMatchType.entries.size
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editKeyword,
                        onValueChange = {
                            editKeyword = it
                            if (editMatchType == UserBotMatchType.REGEX) {
                                regexError = UserBotManager.validateRegex(it)
                            }
                        },
                        label = {
                            Text(
                                when (editMatchType) {
                                    UserBotMatchType.CONTAINS -> "关键词（包含即触发）"
                                    UserBotMatchType.EXACT -> "关键词（完全等于才触发）"
                                    UserBotMatchType.REGEX -> "正则表达式"
                                }
                            )
                        },
                        singleLine = true,
                        isError = regexError != null,
                        supportingText = regexError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editReply,
                        onValueChange = { editReply = it },
                        label = { Text("回复内容") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editingIndex >= 0) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                rules.removeAt(editingIndex)
                                scope.launch {
                                    settingsStorage.setUserBotRules(
                                        UserBotManager.encodeRules(rules.toList())
                                    )
                                }
                                showEditor = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(AppIcons.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("删除此规则")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editKeyword.isBlank() || editReply.isBlank()) return@TextButton
                        if (editMatchType == UserBotMatchType.REGEX) {
                            val error = UserBotManager.validateRegex(editKeyword)
                            if (error != null) {
                                regexError = error
                                return@TextButton
                            }
                        }
                        val newRule = UserBotRule(
                            keyword = editKeyword.trim(),
                            reply = editReply.trim(),
                            matchType = editMatchType
                        )
                        if (editingIndex >= 0) {
                            rules[editingIndex] = newRule
                        } else {
                            rules.add(newRule)
                        }
                        scope.launch {
                            settingsStorage.setUserBotRules(
                                UserBotManager.encodeRules(rules.toList())
                            )
                        }
                        showEditor = false
                    },
                    enabled = editKeyword.isNotBlank() && editReply.isNotBlank() && regexError == null
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun AutoReplyTab(
    rules: List<UserBotRule>,
    loading: Boolean,
    onEdit: (Int, UserBotRule) -> Unit,
    onAdd: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SettingsGroup(title = "自动回复规则") {
                if (loading) {
                    SettingsItemCell(
                        icon = AppIcons.Info,
                        title = "加载中…"
                    )
                } else if (rules.isEmpty()) {
                    SettingsItemCell(
                        icon = AppIcons.Info,
                        title = "暂无规则",
                        subtitle = "点击下方按钮添加"
                    )
                } else {
                    rules.forEachIndexed { index, rule ->
                        val currentIndex = index
                        val modeLabel = when (rule.matchType) {
                            UserBotMatchType.EXACT -> "完全匹配"
                            UserBotMatchType.REGEX -> "正则匹配"
                            UserBotMatchType.CONTAINS -> "包含匹配"
                        }
                        SettingsItemCell(
                            icon = AppIcons.ChatBubble,
                            title = rule.keyword,
                            subtitle = "回复：${rule.reply} · $modeLabel",
                            endIcon = AppIcons.Edit,
                            onClick = { onEdit(currentIndex, rule) }
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup {
                SettingsItemCell(
                    icon = AppIcons.Add,
                    title = "添加规则",
                    subtitle = "设置关键词和自动回复内容",
                    onClick = onAdd
                )
            }
        }
    }
}

@Composable
private fun AutoRecallTab(
    senderIds: List<String>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            SettingsGroup(title = "要撤回的发送者") {
                if (senderIds.isEmpty()) {
                    SettingsItemCell(
                        icon = AppIcons.Info,
                        title = "暂无撤回目标",
                        subtitle = "点击下方按钮添加发送者 ID"
                    )
                } else {
                    senderIds.forEach { id ->
                        SettingsItemCell(
                            icon = AppIcons.Person,
                            title = id,
                            subtitle = "收到该用户的消息时自动撤回",
                            endIcon = AppIcons.Edit,
                            onClick = { onEdit(id) }
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup {
                SettingsItemCell(
                    icon = AppIcons.Add,
                    title = "添加撤回目标",
                    subtitle = "支持一次输入多个 ID，逗号分隔",
                    onClick = onAdd
                )
            }
        }
    }
}