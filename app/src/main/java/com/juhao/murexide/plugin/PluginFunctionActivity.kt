package com.juhao.murexide.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.StyledScaffold
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme

class PluginFunctionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID).orEmpty()
        setContent {
            MurexideTheme {
                PluginFunctionScreen(entryId = entryId, onBack = { finish() })
                PluginDialogHost()
            }
        }
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "entry_id"

        fun start(context: Context, entryId: String) {
            context.startActivity(
                Intent(context, PluginFunctionActivity::class.java)
                    .putExtra(EXTRA_ENTRY_ID, entryId)
            )
        }
    }
}

@Composable
private fun PluginFunctionScreen(entryId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val pluginId = PluginManager.manifestForEntry(entryId)
    val entry = PluginManager.entryById(entryId)
    val revision by PluginSession.revision.collectAsState()
    val pageTick by PluginSession.pageTick.collectAsState()

    if (entry == null || pluginId == null) {
        StyledScaffold(
            title = { Text("插件页面") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("入口不存在或插件已停用")
            }
        }
        return
    }

    StyledScaffold(
        title = { Text(entry.title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val env = remember(revision, pageTick, pluginId) {
                PluginContext(context, pluginId).toEnv()
            }
            PluginWidgetRenderer(entry.page, pluginId, env)
        }
    }
}

@Composable
fun PluginDialogHost() {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PluginSession.PendingDialog?>(null) }
    LaunchedEffect(Unit) {
        PluginSession.dialogs.collect { pending = it }
    }
    pending?.let { d ->
        AlertDialog(
            onDismissRequest = {
                PluginSession.dismissPendingDialog(d.pluginId)
                pending = null
            },
            title = { Text(d.action.title.ifBlank { "提示" }) },
            text = { Text(d.action.message) },
            confirmButton = {
                TextButton(onClick = {
                    PluginSession.confirmPendingDialog(context, d.pluginId)
                    pending = null
                }) { Text(d.action.confirmText.ifBlank { "确定" }) }
            },
            dismissButton = {
                TextButton(onClick = {
                    PluginSession.dismissPendingDialog(d.pluginId)
                    pending = null
                }) { Text(d.action.cancelText.ifBlank { "取消" }) }
            }
        )
    }
}