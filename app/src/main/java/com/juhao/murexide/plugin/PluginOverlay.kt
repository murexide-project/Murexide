package com.juhao.murexide.plugin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PluginOverlay(pageId: String) {
    val context = LocalContext.current
    val manifests by PluginManager.manifests.collectAsState()

    var wsConnected by remember { mutableStateOf(PluginSystem.isWsConnected()) }
    var networkOnline by remember { mutableStateOf(PluginSystem.networkOnline.value) }

    LaunchedEffect(Unit) {
        WebSocketManager.getInstance().connectionState.collect { wsConnected = it }
    }
    LaunchedEffect(Unit) {
        PluginSystem.networkOnline.collect { networkOnline = it }
    }

    val list = remember(pageId, manifests) {
        manifests.filter { it.enabled }.flatMap { m ->
            m.rules.filter {
                (it.page == pageId || it.page == "*") &&
                        it.event == "created" &&
                        it.action == "decorate" &&
                        it.content.type != "toast" &&
                        it.content.type != "action"
            }.map { m to it }
        }
    }
    if (list.isEmpty()) return

    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }
    var dialogPluginId by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        list.forEach { (manifest, rule) ->
            key(manifest.id, wsConnected, networkOnline) {
                val ctx = PluginContext(context, manifest.id, pageId, "created")
                if (WhenEvaluator.evaluate(rule.whenExpr, ctx)) {
                    val alignment = alignmentFor(rule)
                    val pad = offsetPadding(rule)
                    Box(modifier = Modifier.align(alignment).padding(pad)) {
                        PluginContentRenderer(
                            content = rule.content,
                            ctx = ctx,
                            onShowDialog = { c, pid -> dialogContent = c; dialogPluginId = pid },
                            onAction = { actions -> PluginActions.executeList(context, ctx, actions) }
                        )
                    }
                }
            }
        }
    }

    dialogContent?.let { content ->
        PluginDialog(
            content = content,
            ctx = PluginContext(context, dialogPluginId, pageId, "created"),
            onDismiss = { dialogContent = null }
        )
    }
}

@Composable
fun PluginSingleOverlay(rule: PluginRule, pluginId: String, pageId: String) {
    val context = LocalContext.current
    var wsConnected by remember { mutableStateOf(PluginSystem.isWsConnected()) }
    var networkOnline by remember { mutableStateOf(PluginSystem.networkOnline.value) }
    LaunchedEffect(Unit) {
        WebSocketManager.getInstance().connectionState.collect { wsConnected = it }
    }
    LaunchedEffect(Unit) {
        PluginSystem.networkOnline.collect { networkOnline = it }
    }

    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }

    key(wsConnected, networkOnline) {
        val ctx = PluginContext(context, pluginId, pageId, "ws.disconnected")
        if (WhenEvaluator.evaluate(rule.whenExpr, ctx)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Box(modifier = Modifier.align(alignmentFor(rule)).padding(offsetPadding(rule))) {
                    PluginContentRenderer(
                        content = rule.content,
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
    }
}

@Composable
fun PluginReplaceContent(pageId: String, pluginId: String, content: PluginContent) {
    val context = LocalContext.current
    var dialogContent by remember { mutableStateOf<PluginContent?>(null) }
    val ctx = remember(pluginId, pageId) {
        PluginContext(context, pluginId, pageId, "created")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        PluginContentRenderer(
            content = content,
            ctx = ctx,
            onShowDialog = { c, _ -> dialogContent = c },
            onAction = { actions -> PluginActions.executeList(context, ctx, actions) }
        )
    }

    dialogContent?.let { c ->
        PluginDialog(content = c, ctx = ctx, onDismiss = { dialogContent = null })
    }
}

@Composable
fun PluginDialog(content: PluginContent, ctx: PluginContext, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ctx.interpolate(content.title ?: "提示") ?: "") },
        text = { Text(ctx.interpolate(content.message ?: content.value?.toString() ?: "") ?: "") },
        confirmButton = {
            TextButton(onClick = {
                PluginActions.executeList(ctx.context, ctx, content.onClick)
                onDismiss()
            }) { Text(content.confirmText ?: "确定") }
        },
        dismissButton = content.cancelText?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        }
    )
}

@Composable
fun PluginContentRenderer(
    content: PluginContent,
    ctx: PluginContext,
    onShowDialog: (PluginContent, String) -> Unit,
    onAction: (List<PluginAction>?) -> Unit
) {
    val mod = buildModifier(content)
    val tint = content.color?.let {
        runCatching { Color(AndroidColor.parseColor(ctx.interpolate(it) ?: it)) }.getOrNull()
    }

    when (content.type) {
        "text" -> {
            val hasOwnBg = content.background != null
            val bgModifier = if (hasOwnBg) {
                Modifier.background(
                    tint ?: MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                    RoundedCornerShape((content.radius ?: 8f).dp)
                )
            } else {
                Modifier
            }
            Text(
                text = ctx.interpolate(content.value?.toString() ?: "") ?: "",
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                fontSize = (content.fontSize ?: 14f).sp,
                modifier = mod
                    .then(bgModifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        "row" -> Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
            content.children?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
        }
        "column" -> Column(
            modifier = mod,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = when (content.align) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }
        ) {
            content.children?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
        }
        "scroll" -> Column(modifier = mod.verticalScroll(rememberScrollState())) {
            content.children?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
        }
        "hscroll" -> Row(modifier = mod.horizontalScroll(rememberScrollState())) {
            content.children?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
        }
        "card" -> Card(modifier = mod, shape = RoundedCornerShape((content.radius ?: 12f).dp)) {
            Column(Modifier.padding(12.dp)) {
                content.children?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
            }
        }
        "spacer" -> Spacer(modifier = mod.height(((content.size ?: "8").toFloatOrNull() ?: 8f).dp))
        "divider" -> HorizontalDivider(modifier = mod)
        "image" -> {
            val src = ctx.interpolate(content.src ?: content.value?.toString() ?: "") ?: ""
            val sizeDp = (content.size ?: "96").toFloatOrNull() ?: 96f
            PluginImage(src = src, ctx = ctx, modifier = mod, radius = content.radius ?: 0f, sizeDp = sizeDp)
        }
        "icon" -> {
            val name = content.icon ?: content.value?.toString()
            Icon(
                imageVector = resolvePluginIcon(name),
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurface,
                modifier = mod.size(((content.size ?: "24").toFloatOrNull() ?: 24f).dp)
            )
        }
        "button" -> Button(
            onClick = {
                if (content.onClick != null) onAction(content.onClick)
                else onShowDialog(content, ctx.pluginId)
            },
            modifier = mod,
            colors = tint?.let { ButtonDefaults.buttonColors(containerColor = it) }
                ?: ButtonDefaults.buttonColors()
        ) {
            Text(
                text = ctx.interpolate(content.value?.toString() ?: "按钮") ?: "按钮",
                fontSize = (content.fontSize ?: 14f).sp
            )
        }
        "dialog" -> Button(
            onClick = { onShowDialog(content, ctx.pluginId) },
            modifier = mod,
            colors = tint?.let { ButtonDefaults.buttonColors(containerColor = it) }
                ?: ButtonDefaults.buttonColors()
        ) {
            Text(
                text = ctx.interpolate(content.value?.toString() ?: "打开") ?: "打开",
                fontSize = (content.fontSize ?: 14f).sp
            )
        }
        "switch" -> {
            val checked = (content.value as? Boolean)
                ?: (content.value?.toString()?.toBoolean())
                ?: false
            var state by remember { mutableStateOf(checked) }
            Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.interpolate(content.title ?: "") ?: "")
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state,
                    onCheckedChange = {
                        state = it
                        ctx.setVar(content.bind ?: "", it)
                        onAction(content.onChange)
                    }
                )
            }
        }
        "textField" -> {
            val initial = ctx.interpolate(content.value?.toString() ?: "") ?: ""
            var text by remember { mutableStateOf(initial) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    ctx.setVar(content.bind ?: "", it)
                    onAction(content.onChange)
                },
                placeholder = { Text(ctx.interpolate(content.hint ?: "") ?: "") },
                modifier = mod
            )
        }
        "slider" -> {
            var value by remember {
                mutableStateOf((content.value as? Number)?.toFloat() ?: content.min ?: 0f)
            }
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    ctx.setVar(content.bind ?: "", it)
                },
                onValueChangeFinished = { onAction(content.onChange) },
                valueRange = (content.min ?: 0f)..(content.max ?: 100f),
                steps = if (content.step != null && content.step > 0) {
                    (((content.max ?: 100f) - (content.min ?: 0f)) / content.step - 1)
                        .toInt().coerceAtLeast(0)
                } else 0,
                modifier = mod
            )
        }
        "progress" -> {
            if (content.indeterminate == true) {
                CircularProgressIndicator(modifier = mod)
            } else {
                LinearProgressIndicator(
                    progress = { (content.value as? Number)?.toFloat() ?: 0f },
                    modifier = mod
                )
            }
        }
        "list" -> Column(modifier = mod) {
            content.items?.forEach { PluginContentRenderer(it, ctx, onShowDialog, onAction) }
        }
        "action" -> Unit
        else -> Text(
            text = "[未知类型: ${content.type}]",
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PluginImage(
    src: String,
    ctx: PluginContext,
    modifier: Modifier,
    radius: Float,
    sizeDp: Float
) {
    var bitmap by remember(src) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(src) { bitmap = loadPluginBitmap(ctx, src) }
    val size = sizeDp.dp
    val shape = RoundedCornerShape(radius.dp)
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop
        )
    } ?: Box(modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, shape))
}

private suspend fun loadPluginBitmap(ctx: PluginContext, src: String): Bitmap? =
    withContext(Dispatchers.IO) {
        if (src.isBlank()) return@withContext null
        runCatching {
            when {
                src.startsWith("assets/") -> {
                    val file = File(ctx.context.filesDir, "plugins/${ctx.pluginId}/$src")
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                src.startsWith("file://") -> {
                    val path = src.removePrefix("file://")
                    if (File(path).exists()) BitmapFactory.decodeFile(path) else null
                }
                src.startsWith("http://") || src.startsWith("https://") -> {
                    val req = ImageRequest.Builder(ctx.context)
                        .data(src)
                        .allowHardware(false)
                        .build()
                    val result = Coil.imageLoader(ctx.context).execute(req)
                    if (result is SuccessResult) {
                        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    } else null
                }
                else -> null
            }
        }.getOrNull()
    }

private fun resolvePluginIcon(name: String?): ImageVector = when (name) {
    "Info" -> AppIcons.Info
    "SmartToy" -> AppIcons.SmartToy
    "Person" -> AppIcons.Person
    "Link" -> AppIcons.Link
    "Image" -> AppIcons.Image
    "Settings" -> AppIcons.Settings
    "Code" -> AppIcons.Code
    "Warning" -> AppIcons.Warning
    "People" -> AppIcons.People
    else -> AppIcons.Build
}

private fun buildModifier(content: PluginContent): Modifier {
    var m: Modifier = Modifier
    content.padding?.let { p ->
        val all = p.all?.dp
        val h = p.horizontal?.dp
        val v = p.vertical?.dp
        val startDp: Dp = when {
            p.start > 0 -> p.start.dp
            h != null -> h
            all != null -> all
            else -> 0.dp
        }
        val endDp: Dp = when {
            p.end > 0 -> p.end.dp
            h != null -> h
            all != null -> all
            else -> 0.dp
        }
        val topDp: Dp = when {
            p.top > 0 -> p.top.dp
            v != null -> v
            all != null -> all
            else -> 0.dp
        }
        val bottomDp: Dp = when {
            p.bottom > 0 -> p.bottom.dp
            v != null -> v
            all != null -> all
            else -> 0.dp
        }
        m = m.padding(start = startDp, end = endDp, top = topDp, bottom = bottomDp)
    }
    content.width?.let { w ->
        when {
            w == "match" -> m = m.fillMaxWidth()
            w.endsWith("dp") -> w.removeSuffix("dp").toFloatOrNull()?.let { m = m.width(it.dp) }
        }
    }
    content.height?.let { h ->
        when {
            h == "match" -> m = m.fillMaxHeight()
            h.endsWith("dp") -> h.removeSuffix("dp").toFloatOrNull()?.let { m = m.height(it.dp) }
        }
    }
    content.background?.let { bg ->
        val color = runCatching { Color(AndroidColor.parseColor(bg)) }.getOrNull()
        if (color != null) {
            m = m.background(color, RoundedCornerShape((content.radius ?: 0f).dp))
        }
    }
    return m
}

private fun alignmentFor(rule: PluginRule): Alignment {
    rule.positionData?.let { pd ->
        return when (pd.anchor) {
            "top" -> Alignment.TopCenter
            "bottom" -> Alignment.BottomCenter
            "topStart" -> Alignment.TopStart
            "topEnd" -> Alignment.TopEnd
            "bottomStart" -> Alignment.BottomStart
            "bottomEnd" -> Alignment.BottomEnd
            "center" -> Alignment.Center
            "centerStart" -> Alignment.CenterStart
            "centerEnd" -> Alignment.CenterEnd
            else -> Alignment.BottomEnd
        }
    }
    return when (rule.position) {
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        "floating" -> Alignment.BottomEnd
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        "center" -> Alignment.Center
        else -> Alignment.BottomEnd
    }
}

private fun offsetPadding(rule: PluginRule): PaddingValues {
    val pd = rule.positionData
    if (pd != null) {
        val p = pd.padding
        val baseStart = p?.start ?: 0
        val baseEnd = p?.end ?: 0
        val baseTop = p?.top ?: 0
        val baseBottom = p?.bottom ?: 0
        return PaddingValues(
            start = (baseStart + pd.offsetX).coerceAtLeast(0).dp,
            end = (baseEnd + pd.offsetX).coerceAtLeast(0).dp,
            top = (baseTop + pd.offsetY).coerceAtLeast(0).dp,
            bottom = (baseBottom + pd.offsetY).coerceAtLeast(0).dp
        )
    }
    return PaddingValues(0.dp)
}