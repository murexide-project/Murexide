package com.juhao.murexide.plugin

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PluginOverlay(pageId: String) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()
    val pageRules = manifests.filter { it.enabled }.flatMap { it.rules }
        .filter {
            (it.page == pageId || it.page == "*") &&
                    it.event == "created" &&
                    it.action == "decorate" &&
                    it.content.type != "toast" &&
                    it.content.type != "action"
        }
    if (pageRules.isEmpty()) return

    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }

    Box(Modifier.fillMaxSize()) {
        pageRules.forEach { rule ->
            val alignment = when (rule.position) {
                "top" -> Alignment.TopCenter
                "bottom" -> Alignment.BottomCenter
                else -> Alignment.BottomEnd
            }
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(if (rule.position == "floating") 16.dp else 0.dp)
            ) {
                PluginContentRenderer(
                    content = rule.content,
                    onShowDialog = { dialogContent = it },
                    onAction = { action ->
                        PluginActions.execute(context, action)
                    }
                )
            }
        }
    }

    dialogContent?.let { content ->
        AlertDialog(
            onDismissRequest = { dialogContent = null },
            title = { Text(content.title ?: "插件弹窗") },
            text = { Text(content.message ?: content.value ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    content.onClick?.let { PluginActions.execute(context, it) }
                    dialogContent = null
                }) {
                    Text(content.confirmText ?: "确定")
                }
            }
        )
    }
}

@Composable
fun PluginReplaceContent(
    pageId: String,
    content: PluginContent
) {
    val context = LocalContext.current
    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        PluginContentRenderer(
            content = content,
            onShowDialog = { dialogContent = it },
            onAction = { action -> PluginActions.execute(context, action) }
        )
    }

    dialogContent?.let { c ->
        AlertDialog(
            onDismissRequest = { dialogContent = null },
            title = { Text(c.title ?: "插件弹窗") },
            text = { Text(c.message ?: c.value ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    c.onClick?.let { PluginActions.execute(context, it) }
                    dialogContent = null
                }) {
                    Text(c.confirmText ?: "确定")
                }
            }
        )
    }
}

@Composable
fun PluginContentRenderer(
    content: PluginContent,
    onShowDialog: (PluginContent) -> Unit,
    onAction: (PluginAction) -> Unit
) {
    val tint = content.color?.let {
        runCatching { Color(AndroidColor.parseColor(it)) }.getOrNull()
    }

    when (content.type) {
        "text" -> Text(
            text = content.value ?: "",
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        "button" -> Button(
            onClick = {
                content.onClick?.let(onAction) ?: onShowDialog(content)
            },
            colors = tint?.let { ButtonDefaults.buttonColors(containerColor = it) }
                ?: ButtonDefaults.buttonColors()
        ) {
            Text(content.value ?: "按钮")
        }
        "dialog" -> Button(
            onClick = { onShowDialog(content) },
            colors = tint?.let { ButtonDefaults.buttonColors(containerColor = it) }
                ?: ButtonDefaults.buttonColors()
        ) {
            Text(content.value ?: "打开弹窗")
        }
        "column" -> Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content.children?.forEach {
                PluginContentRenderer(
                    content = it,
                    onShowDialog = onShowDialog,
                    onAction = onAction
                )
            }
        }
        "action" -> Unit
    }
}