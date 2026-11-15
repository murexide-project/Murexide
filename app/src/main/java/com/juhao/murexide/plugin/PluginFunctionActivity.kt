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
    val functions by PluginManager.functions.collectAsState()
    val manifests by PluginManager.manifests.collectAsState()

    val pair = remember(entryId, functions, manifests) {
        manifests.filter { it.enabled }.firstNotNullOfOrNull { m ->
            m.functions.firstOrNull { it.id == entryId }?.let { m to it }
        }
    }

    if (pair == null) {
        StyledScaffold(
            title = { Text("插件页面") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { Text("入口不存在或插件已停用") }
        }
        return
    }

    val (manifest, entry) = pair
    val ctx = remember(entryId) { PluginContext(context, manifest.id, null, "function") }
    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }

    StyledScaffold(
        title = { Text(entry.title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, "返回") } }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!WhenEvaluator.evaluate(null, ctx)) return@Column
            PluginContentRenderer(
                content = entry.content,
                ctx = ctx,
                onShowDialog = { c, _ -> dialogContent = c },
                onAction = { actions -> PluginActions.executeList(context, ctx, actions) }
            )
        }
    }

    dialogContent?.let { c ->
        PluginDialog(content = c, ctx = ctx, onDismiss = { dialogContent = null })
    }
}