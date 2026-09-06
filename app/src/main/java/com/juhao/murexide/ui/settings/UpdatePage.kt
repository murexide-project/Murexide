package com.juhao.murexide.ui.settings

import android.content.Intent
import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.utils.UpdateInfo
import com.juhao.murexide.utils.checkForUpdateWithDetails
import com.juhao.murexide.utils.getAppVersionInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatePage() {        
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val updateEnabled = context.getAppVersionInfo().commitHash != "dev"
    var loading by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    var updateChannel by remember { mutableStateOf("stable") }
    
    fun check() {
        loading = true
        scope.launch {
            val includePreRelease = updateChannel == "preRelease"
            
            val info = checkForUpdateWithDetails(
                context = context,
                includePreRelease = includePreRelease
            )
            updateInfo = info
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        updateChannel = settingsStorage.getUpdateChannel()
        if (updateEnabled) {       
            check()
        }
    }
    
    val newestVersion = updateInfo?.version ?: "null"
    val currentVersion = context.getAppVersionInfo().versionName
    val shouldUpdate = updateInfo?.shouldUpdate == true
    
    val (cardBgColor, cardTextColor) = if (shouldUpdate)
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    else
        Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        
    val subtitle = buildString {
        appendLine("最新版本: $newestVersion")
        append("当前版本: $currentVersion")
        if (shouldUpdate) {
            append("\n")
            val type = if (updateInfo?.isPreRelease == true) {
                "预发布版"
            } else {
                "正式版"
            }
            append("新版本类型：$type")
        }
    }
    
    SettingsGroup {
        if (updateEnabled) {
            Surface(
                color = cardBgColor,
                contentColor = cardTextColor,
                onClick = {
                    if (shouldUpdate) {
                        val intent = Intent(Intent.ACTION_VIEW, updateInfo?.releaseUrl?.toUri())
                        context.startActivity(intent)
                    } else {
                        check()
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    if (loading) {
                        ContainedLoadingIndicator(Modifier.size(64.dp))
                    } else {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialShapes.Pill.toShape(),
                            color = if (shouldUpdate)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary
                        ) {
                            Icon(
                                imageVector = when {
                                    shouldUpdate -> AppIcons.Download
                                    else -> AppIcons.Check
                                },
                                contentDescription = null,
                                modifier = Modifier.requiredSize(32.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            loading -> "正在检测更新"
                            shouldUpdate -> "更新可用"
                            else -> "已是最新版本"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!loading && !shouldUpdate) {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            SettingsItemCell(
                icon = AppIcons.Info,
                title = "开发版本，更新不可用",
                subtitle = "获取更新请自行关注通知"
            )
        }
    }

    SettingsGroup {
        SettingsDropdownItem(
            icon = AppIcons.List,
            title = "更新频道",
            subtitle = if (updateChannel == "stable")
                "仅检查正式版本"
            else
                "检查预发布版本",
            options = listOf(
                "stable" to "仅正式版",
                "preRelease" to "正式版 + 预发布版"
            ),
            selectedValue = updateChannel,
            onOptionSelected = { selected ->
                updateChannel = selected
                scope.launch {
                    settingsStorage.setUpdateChannel(selected)
                }
            }
        )
    }
}