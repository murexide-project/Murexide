package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.juhao.murexide.ui.about.AboutActivity
import com.juhao.murexide.utils.hasLegacyWritePermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    page: Int = 0,
    onLogout: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()
        
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    val title = when (page) {
        1 -> "消息气泡设置"
        2 -> "外观设置"
        3 -> "行为设置"
        4 -> "更新"
        5 -> "账号设置"
        6 -> "截图设置"
        else -> "设置"
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            StyledTopBar(
                title = { Text(title) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StyledIconButton(onClick = onBack) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (page == 0) {
                        IconButton(onClick = {
                            val intent = Intent(context, AboutActivity::class.java)
                            context.startActivity(intent)
                        }) {
                            Icon(AppIcons.Info, contentDescription = "关于")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            when (page) {
                1 -> BubblePage()
                2 -> AppearancePage()
                3 -> ActionPage()
                4 -> UpdatePage()
                5 -> AccountPage(onLogout = onLogout)
                6 -> ScreenshotPage()
                else -> MainScreen()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    
    SettingsGroup() {
        SettingsItem(
            icon = AppIcons.ChatBubble,
            title = "消息气泡",
            subtitle = "自定义气泡透明度、圆角大小、控件显示等",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 1)
                }
                context.startActivity(intent)
            }
        )
        SettingsItem(
            icon = AppIcons.Style,
            title = "应用外观",
            subtitle = "主题模式、主题颜色、液态玻璃等",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 2)
                }
                context.startActivity(intent)
            }
        )
        SettingsItem(
            icon = AppIcons.DragClick,
            title = "行为",
            subtitle = "头像跟随、大屏模式、截图、通知等",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 3)
                }
                context.startActivity(intent)
            }
        )
        SettingsItem(
            icon = AppIcons.Update,
            title = "更新",
            subtitle = "检查更新、更新频道",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 4)
                }
                context.startActivity(intent)
            }
        )
        SettingsItem(
            icon = AppIcons.Person,
            title = "账号",
            subtitle = "切换账号、提取 Token、退出登录",
            onClick = {
                val intent = Intent(context, SettingsActivity::class.java).apply{
                    putExtra("page", 5)
                }
                context.startActivity(intent)
            }
        )
    }
}