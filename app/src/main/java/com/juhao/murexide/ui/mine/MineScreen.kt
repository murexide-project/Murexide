package com.juhao.murexide.ui.mine

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.juhao.murexide.repository.UserInfo
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.ThemeState
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    token: String,
    onSettingsClick: () -> Unit,
    viewModel: MineViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MineViewModel(token) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val uiState by viewModel.uiState.collectAsState()
    
    val themeStyle by ThemeState.themeStyle
    
    val scrollBehavior = if (themeStyle == "md3") TopAppBarDefaults.pinnedScrollBehavior()
        else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.uploadAndChangeAvatar(context, it)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (themeStyle == "md3") {
                TopAppBar(
                    title = { Text("我的") },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        StyledIconButton(onClick = onSettingsClick) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    }
                )
            } else {
                LargeTopAppBar(
                    title = { Text("我的") },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        StyledIconButton(onClick = onSettingsClick) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    }
                )
            }
        }
    ) {
        when (val state = uiState) {
            is MineUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = it.calculateTopPadding(),
                            end = it.calculateRightPadding(LayoutDirection.Ltr)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is MineUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = it.calculateTopPadding(),
                            end = it.calculateRightPadding(LayoutDirection.Ltr)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadUserInfo() }) {
                            Text("重试")
                        }
                    }
                }
            }

            is MineUiState.Success -> {
                MineContent(
                    userInfo = state.userInfo,
                    onlineDay = state.onlineDay,
                    continuousOnlineDay = state.continuousOnlineDay,
                    scrollState = scrollState,
                    paddingValues = it,
                    introduction = state.introduction,
                    onEditProfileClick = {
                        EditProfileActivity.start(context, token)
                    },
                    onAvatarEditClick = {
                        avatarLauncher.launch("image/*")
                    }
                )

                if (state.isUploadingAvatar) {
                    UploadProgressDialog(progress = state.uploadProgress)
                }
            }
        }
    }
}

@Composable
fun UploadProgressDialog(progress: Float) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.width(280.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "正在上传头像",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MineContent(
    userInfo: UserInfo,
    onlineDay: Int?,
    continuousOnlineDay: Int?,
    introduction: String,
    scrollState: ScrollState,
    paddingValues: PaddingValues,
    onEditProfileClick: () -> Unit,
    onAvatarEditClick: () -> Unit
) {
    var isPhoneVisible by remember { mutableStateOf(false) }
    var isEmailVisible by remember { mutableStateOf(false) }

    val displayPhone = if (isPhoneVisible) {
        userInfo.phone.ifEmpty { "未绑定" }
    } else {
        maskPhoneNumber(userInfo.phone)
    }

    val displayEmail = if (isEmailVisible) {
        userInfo.email.ifEmpty { "未设置" }
    } else {
        maskEmail(userInfo.email)
    }

    val visibilityIcon = if (isPhoneVisible) {
        Icons.Rounded.VisibilityOff
    } else {
        Icons.Rounded.Visibility
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(scrollState)
    ) {
        SettingsGroup {
            CustomItemCell(onClick = onEditProfileClick) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        url = userInfo.avatarUrl,
                        size = 64.dp,
                        modifier = Modifier.clickable {
                            onAvatarEditClick()
                        }
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = 4.dp, y = 4.dp)
                            .clickable {
                                onAvatarEditClick()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "修改头像",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = userInfo.name.ifEmpty { "未知" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "ID: ${userInfo.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (introduction.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = introduction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        SettingsGroup(title = "账号信息") {
            InfoItem(
                icon = Icons.Rounded.Verified,
                title = "用户等级",
                value = if (userInfo.isVip) "会员" else "普通用户"
            )

            InfoItem(
                icon = Icons.Rounded.MonetizationOn,
                title = "金币",
                value = "${userInfo.coin}"
            )

            InfoItem(
                icon = Icons.Rounded.Phone,
                title = "手机号",
                value = displayPhone,
                endIcon = visibilityIcon,
                onClick = {
                    isPhoneVisible = !isPhoneVisible
                }
            )

            InfoItem(
                icon = Icons.Rounded.Email,
                title = "邮箱",
                value = displayEmail,
                endIcon = visibilityIcon,
                onClick = {
                    isEmailVisible = !isEmailVisible
                }
            )

            InfoItem(
                icon = Icons.Rounded.CardGiftcard,
                title = "邀请码",
                value = userInfo.invitationCode.ifEmpty { "未设置" }
            )
        }

        SettingsGroup(title = "活跃度") {
            onlineDay?.let {
                InfoItem(
                    icon = Icons.Rounded.AccessTime,
                    title = "在线天数",
                    value = "$it 天"
                )
            }
            continuousOnlineDay?.let {
                InfoItem(
                    icon = Icons.Rounded.LocalFireDepartment,
                    title = "连续在线",
                    value = "$it 天"
                )
            }
            if (onlineDay == null && continuousOnlineDay == null) {
                InfoItem(
                    icon = Icons.Rounded.Schedule,
                    title = "在线天数",
                    value = "加载中…"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun maskPhoneNumber(phone: String): String {
    return if (phone.isEmpty()) {
        "未绑定"
    } else if (phone.length >= 2) {
        phone.first() + "*".repeat(phone.length - 2) + phone.last()
    } else {
        phone
    }
}

fun maskEmail(email: String): String {
    return if (email.isEmpty()) {
        "未设置"
    } else {
        val atIndex = email.indexOf('@')
        if (atIndex > 0) {
            "*".repeat(atIndex) + email.substring(atIndex)
        } else {
            "*".repeat(email.length)
        }
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector,
    title: String,
    value: String,
    endIcon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    SettingsItemCell(
        icon = icon,
        endIcon = endIcon,
        title = title,
        subtitle = value,
        onClick = onClick
    )
}