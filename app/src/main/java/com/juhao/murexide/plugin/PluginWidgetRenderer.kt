package com.juhao.murexide.plugin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.juhao.murexide.ui.icons.AppIcons

@Composable
fun PluginWidgetRenderer(
    widget: PluginWidget,
    pluginId: String,
    env: Map<String, Any?> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val base = modifier.applyLayout(widget)

    when (widget.type) {
        "column" -> Column(
            modifier = base,
            horizontalAlignment = when (widget.align) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val bg = widget.background?.let { safeColor(it) } ?: Color.Transparent
            if (bg != Color.Transparent) {
                Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        widget.children.forEach { child ->
                            PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child))
                        }
                    }
                }
            } else {
                widget.children.forEach { child ->
                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child))
                }
            }
        }
        "row" -> Row(
            modifier = base,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (widget.align) {
                "center" -> Arrangement.Center
                "end" -> Arrangement.End
                "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround
                else -> Arrangement.Start
            }
        ) {
            widget.children.forEach { child ->
                PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child))
            }
        }
        "text" -> Text(
            text = PluginExecutor.interpolate(widget.content ?: "", env, pluginId),
            color = widget.color?.let { safeColor(it) } ?: MaterialTheme.colorScheme.onSurface,
            modifier = base
        )
        "textfield" -> {
            val model = widget.model ?: ""
            val key = if (model.startsWith("var.")) model else "var.$model"
            var text by remember {
                mutableStateOf(env[key]?.toString() ?: widget.content.orEmpty())
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    if (model.isNotBlank()) {
                        PluginSession.setVar(pluginId, model.removePrefix("var."), new)
                        PluginSession.bumpPage()
                    }
                },
                placeholder = widget.placeholder?.let { ph ->
                    { Text(PluginExecutor.interpolate(ph, env, pluginId)) }
                },
                singleLine = true,
                modifier = base.fillMaxWidth()
            )
        }
        "dropdown" -> {
            val model = widget.model ?: ""
            val key = if (model.startsWith("var.")) model else "var.$model"
            val options = widget.options
            var selected by remember {
                mutableStateOf(env[key]?.toString() ?: options.firstOrNull().orEmpty())
            }
            var expanded by remember { mutableStateOf(false) }
            Column(modifier = base.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selected.isBlank()) {
                            "请选择"
                        } else {
                            PluginExecutor.interpolate(selected, env, pluginId)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        AppIcons.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(PluginExecutor.interpolate(opt, env, pluginId)) },
                            onClick = {
                                selected = opt
                                expanded = false
                                if (model.isNotBlank()) {
                                    PluginSession.setVar(pluginId, model.removePrefix("var."), opt)
                                    PluginSession.bumpPage()
                                }
                            }
                        )
                    }
                }
            }
        }
        "slider" -> {
            val model = widget.model ?: ""
            val key = if (model.startsWith("var.")) model else "var.$model"
            val min = widget.min ?: 0f
            val max = (widget.max ?: 100f).coerceAtLeast(min + 1f)
            var value by remember {
                mutableStateOf(env[key]?.toString()?.toFloatOrNull() ?: min)
            }
            Column(modifier = base.fillMaxWidth()) {
                Slider(
                    value = value.coerceIn(min, max),
                    onValueChange = { v ->
                        val stepped = widget.step?.let { s -> (v / s).toInt() * s } ?: v
                        value = stepped.coerceIn(min, max)
                        if (model.isNotBlank()) {
                            PluginSession.setVar(pluginId, model.removePrefix("var."), value.toString())
                            PluginSession.bumpPage()
                        }
                    },
                    valueRange = min..max
                )
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        "button" -> Button(
            onClick = { PluginExecutor.execute(context, pluginId, widget.onClick, env) },
            modifier = base
        ) {
            Text(PluginExecutor.interpolate(widget.content ?: "", env, pluginId))
        }
        "spacer" -> Spacer(modifier = base.height((widget.size ?: 8f).dp))
        "divider" -> HorizontalDivider(modifier = base)
        "image" -> PluginWidgetImage(widget, pluginId, base)
        "icon" -> Icon(
            imageVector = iconOf(widget.icon),
            contentDescription = null,
            tint = widget.color?.let { safeColor(it) } ?: MaterialTheme.colorScheme.onSurface,
            modifier = base.size((widget.size ?: 20f).dp)
        )
        "card" -> Card(
            modifier = base,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                widget.children.forEach { child ->
                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child))
                }
            }
        }
        "scroll" -> Column(
            modifier = modifier.verticalScroll(rememberScrollState()).applyLayout(widget),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            widget.children.forEach { child ->
                PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child))
            }
        }
        else -> Text("[未知类型: ${widget.type}]")
    }
}

private fun Modifier.applyLayout(widget: PluginWidget): Modifier {
    var m = this
    when (widget.width?.lowercase()) {
        "match", "fill", "fillmaxwidth" -> m = m.fillMaxWidth()
        "wrap", "wrapcontent" -> m = m.wrapContentWidth()
        null -> {}
        else -> widget.width.toFloatOrNull()?.let { m = m.width(it.dp) }
    }
    when (widget.height?.lowercase()) {
        "match", "fill", "fillmaxheight" -> m = m.fillMaxHeight()
        "wrap", "wrapcontent" -> m = m.wrapContentHeight()
        null -> {}
        else -> widget.height.toFloatOrNull()?.let { m = m.height(it.dp) }
    }
    widget.padding?.let { m = m.padding(it.dp) }
    widget.paddingH?.let { m = m.padding(horizontal = it.dp) }
    widget.paddingV?.let { m = m.padding(vertical = it.dp) }
    return m
}

private fun Modifier.applyChildAlign(child: PluginWidget): Modifier {
    var m = this
    when (child.align?.lowercase()) {
        "center" -> m = m.wrapContentSize(Alignment.Center)
        "end" -> m = m.wrapContentSize(Alignment.CenterEnd)
        "start" -> m = m.wrapContentSize(Alignment.CenterStart)
        "top" -> m = m.wrapContentSize(Alignment.TopCenter)
        "bottom" -> m = m.wrapContentSize(Alignment.BottomCenter)
    }
    return m
}

private fun iconOf(name: String?): ImageVector = when (name) {
    "Info" -> AppIcons.Info
    "SmartToy" -> AppIcons.SmartToy
    "Person" -> AppIcons.Person
    "Link" -> AppIcons.Link
    "Image" -> AppIcons.Image
    "Settings" -> AppIcons.Settings
    "Code" -> AppIcons.Code
    "Warning" -> AppIcons.Warning
    "People" -> AppIcons.People
    "Build" -> AppIcons.Build
    else -> AppIcons.Build
}

@Composable
private fun PluginWidgetImage(widget: PluginWidget, pluginId: String, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val src = widget.content
    if (src.isNullOrBlank()) return
    LaunchedEffect(src) {
        bitmap = withContext(Dispatchers.IO) {
            when {
                src.startsWith("assets/") -> {
                    val f = File(context.filesDir, "lua_plugins/$pluginId/$src")
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
                src.startsWith("http://") || src.startsWith("https://") -> {
                    val req = ImageRequest.Builder(context).data(src).allowHardware(false).build()
                    val r = Coil.imageLoader(context).execute(req)
                    if (r is SuccessResult) (r.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap else null
                }
                else -> null
            }
        }
    }
    val size = (widget.size ?: 48f).dp
    val shape = RoundedCornerShape(8.dp)
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop
        )
    } ?: Spacer(modifier.size(size))
}

private fun safeColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.Magenta)