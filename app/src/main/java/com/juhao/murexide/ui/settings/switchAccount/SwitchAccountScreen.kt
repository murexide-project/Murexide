package com.juhao.murexide.ui.settings.switchAccount

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.MainActivity
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.components.StyledIconButton
import com.juhao.murexide.ui.components.StyledTopBar
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.theme.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(
    onBack: () -> Unit,
    isChooseMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val accountStorage = AccountStorage(context)

    val accounts = accountStorage.userAccountsFlow.collectAsState(initial = emptyList()).value
    val currentAccount = accountStorage.currentTokenFlow.collectAsState(initial = "").value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            StyledTopBar(
                title = { Text("切换账号") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    StyledIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn (
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isChooseMode) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LoginActivity.start(context, true)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            modifier = Modifier.size(48.dp).padding(8.dp),
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("添加账号", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "暂无账号", modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }

            items(accounts) { account ->
                AccountRow(
                    account = account,
                    isCurrentAccount = account.token == currentAccount,
                    isChooseMode = isChooseMode,
                    onSwitchAccount = {
                        scope.launch {
                            accountStorage.switchAccount(it.id)
                            Toast.makeText(context, "切换成功", Toast.LENGTH_LONG).show()
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                        }
                    },
                    onRemoveAccount = {
                        scope.launch {
                            accountStorage.removeAccount(it.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AccountRow(
    account: UserAccount,
    isCurrentAccount: Boolean,
    isChooseMode: Boolean,
    onSwitchAccount: (UserAccount) -> Unit,
    onRemoveAccount: (UserAccount) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isChooseMode) {
                        showMenu = true
                    } else {
                        onSwitchAccount(account)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.avatar.isNotBlank()) {
                Avatar(url = account.avatar, size = 48.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(account.username, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "ID: ${account.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    if (isCurrentAccount && !isChooseMode) {
                        Text(
                            "当前",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    if (!account.isValidated) {
                        Text(
                            "账号未验证，切换至此账号验证",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showMenu && !isCurrentAccount,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("切换至此账号") },
                onClick = {
                    onSwitchAccount(account)
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.SwitchAccount,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("移除账号", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showDeleteDialog = true
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("删除账号") },
            text = {
                Text("确定要删除此账号吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onRemoveAccount(account)
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}