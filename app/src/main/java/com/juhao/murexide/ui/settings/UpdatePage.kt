package com.juhao.murexide.ui.settings

import android.os.Build
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.widget.Toast
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    var showUpdateDialog by remember { mutableStateOf(false) }
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
            if (info.shouldUpdate) {
                showUpdateDialog = true
            } else {
                Toast.makeText(
                    context,
                    "已是最新版本",
                    Toast.LENGTH_SHORT
                ).show()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        updateChannel = settingsStorage.getUpdateChannel()
        check()
    }
    
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            currentVersion = context.getAppVersionInfo().versionName,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                val intent = Intent(Intent.ACTION_VIEW, updateInfo?.releaseUrl?.toUri())
                context.startActivity(intent)
                showUpdateDialog = false
            }
        )
    }
    
    val newestVersion = updateInfo?.version ?: "null"
    val currentVersion = context.getAppVersionInfo().versionName
    val shouldUpdate = updateInfo?.shouldUpdate == true
    
    val (cardBgColor, cardTextColor) = if (shouldUpdate)
        Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    else
        Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    
    SettingsGroup() {
        ListItem(
            onClick = { check() },
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = {
                if (loading) ContainedLoadingIndicator(Modifier.size(24.dp)) else
                Icon(
                    imageVector = when {
                        updateInfo?.shouldUpdate == true -> AppIcons.Info
                        else -> AppIcons.Check
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                if (!loading) {
                    Icon(
                        AppIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = cardBgColor,
                headlineColor = cardTextColor,
                supportingColor = cardTextColor,
                leadingIconColor = cardTextColor,
                trailingIconColor = cardTextColor
            )
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = when {
                        loading -> "正在检测更新"
                        shouldUpdate -> "更新可用"
                        else -> "已是最新版本"
                    },
                    style = MaterialTheme.typography.bodyLarge                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "最新版本: ${newestVersion}\n当前版本: ${currentVersion}",
                    style = MaterialTheme.typography.bodySmall                )
            }
        }
    }

    SettingsGroup() {
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


@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isPreRelease = updateInfo.isPreRelease
    val versionType = if (isPreRelease) "预发布版" else "正式版"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPreRelease) {
                    "发现新预发布版"
                } else {
                    "发现新正式版"
                }
            )
        },
        text = {
            Column {
                Text(
                    text = "$currentVersion  →  ${updateInfo.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "版本类型：$versionType",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}