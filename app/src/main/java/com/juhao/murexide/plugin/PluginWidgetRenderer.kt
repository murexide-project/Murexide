package com.juhao.murexide.plugin

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.juhao.murexide.ui.components.SettingsGroupSurface
import com.juhao.murexide.ui.components.SettingsItem
import com.juhao.murexide.ui.icons.AppIcons
import java.io.File
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Share an ancestor's viewport to avoid measuring nested scrollers with infinite constraints.
internal val LocalPluginScrollManagedByHost = staticCompositionLocalOf { false }
private val LocalPluginHorizontalScrollManagedByParent = staticCompositionLocalOf { false }

@Composable
fun PluginWidgetRenderer(
    widget: PluginWidget,
    pluginId: String,
    env: Map<String, Any?> = emptyMap(),
    modifier: Modifier = Modifier,
    parentEnabled: Boolean = true
) {
    val type = widget.type.lowercase().replace("_", "").replace("-", "")
    val enabled = parentEnabled && bindingBoolean(PluginBindings.resolve(widget.enabled, env, pluginId))
    val scrollable = bindingBoolean(PluginBindings.resolve(widget.scrollable, env, pluginId))
    val verticalScrollManaged = LocalPluginScrollManagedByHost.current
    val horizontalScrollManaged = LocalPluginHorizontalScrollManagedByParent.current
    val color = widget.color?.let {
        safeColor(PluginExecutor.interpolate(it, env, pluginId), MaterialTheme.colorScheme)
    }
    val background = widget.background?.let {
        safeColor(PluginExecutor.interpolate(it, env, pluginId), MaterialTheme.colorScheme)
    }
    val base = modifier
        .then(if (background != null && type != "card" && type != "button") Modifier.background(background) else Modifier)
        .applyLayout(widget, pluginId, env)
    val hasClick = widget.onClickCallback != null
    val onEvent: () -> Unit = {
        if (enabled) widget.onClickCallback?.invoke()
    }

    when (type) {
        "column" -> Column(
            modifier = if (scrollable && !verticalScrollManaged) base.verticalScroll(rememberScrollState()) else base,
            horizontalAlignment = when (widget.align) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompositionLocalProvider(LocalPluginScrollManagedByHost provides (verticalScrollManaged || scrollable)) {
                widget.children.forEach { child ->
                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child), enabled)
                }
            }
        }
        "row" -> Row(
            modifier = if (scrollable && !horizontalScrollManaged) base.horizontalScroll(rememberScrollState()) else base,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = when (widget.align) {
                "center" -> Arrangement.Center
                "end" -> Arrangement.End
                "spaceBetween" -> Arrangement.SpaceBetween
                "spaceAround" -> Arrangement.SpaceAround
                else -> Arrangement.Start
            }
        ) {
            CompositionLocalProvider(LocalPluginHorizontalScrollManagedByParent provides (horizontalScrollManaged || scrollable)) {
                widget.children.forEach { child ->
                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child), enabled)
                }
            }
        }
        "text" -> Text(
            text = PluginExecutor.interpolate(widget.content.orEmpty(), env, pluginId),
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontSize = widget.size?.takeIf { it.isFinite() && it > 0f }?.sp ?: TextUnit.Unspecified,
            modifier = base
        )
        "textfield" -> {
            val external = modelValue(widget, pluginId, env)
            val fallback = PluginExecutor.interpolate(widget.content.orEmpty(), env, pluginId)
            val source = external ?: fallback
            var text by remember(pluginId, widget.model) { mutableStateOf(source.toString()) }
            var observedSource by remember(pluginId, widget.model) { mutableStateOf(source) }
            LaunchedEffect(source) {
                if (source != observedSource) {
                    text = source.toString()
                    observedSource = source
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (enabled) {
                        text = new
                        val value = modelInput(new, external)
                        if (value != null) {
                            observedSource = value
                            updateModel(widget, pluginId, value)
                        }
                        onEvent()
                    }
                },
                enabled = enabled,
                placeholder = widget.placeholder?.let { ph ->
                    { Text(PluginExecutor.interpolate(ph, env, pluginId)) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = color ?: Color.Unspecified,
                    unfocusedTextColor = color ?: Color.Unspecified
                ),
                singleLine = true,
                modifier = base.fillMaxWidth()
            )
        }
        "dropdown" -> {
            val external = modelValue(widget, pluginId, env)
            val options = widget.options.map { PluginExecutor.interpolate(it, env, pluginId) }
            var selected by remember(pluginId, widget.model, external, options) {
                mutableStateOf(external?.toString() ?: options.firstOrNull().orEmpty())
            }
            var expanded by remember(pluginId, widget.model) { mutableStateOf(false) }
            LaunchedEffect(enabled) {
                if (!enabled) expanded = false
            }
            Column(modifier = base.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selected.ifBlank { "请选择" },
                        color = if (enabled) color ?: Color.Unspecified else Color.Unspecified,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        AppIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            enabled = enabled,
                            onClick = {
                                if (enabled) {
                                    selected = option
                                    expanded = false
                                    val value = modelInput(option, external)
                                    if (value != null) updateModel(widget, pluginId, value)
                                    onEvent()
                                }
                            }
                        )
                    }
                }
            }
        }
        "slider" -> {
            val external = modelValue(widget, pluginId, env)
            val min = widget.min?.takeIf { it.isFinite() } ?: 0f
            val max = widget.max?.takeIf { it.isFinite() && it > min }
                ?: (min + 100f).takeIf { it.isFinite() && it > min }
                ?: Float.MAX_VALUE
            val step = widget.step?.takeIf { it.isFinite() && it > 0f }
            var value by remember(pluginId, widget.model, external, min, max) {
                mutableStateOf((numberValue(external) ?: min).coerceIn(min, max))
            }
            Column(modifier = base.fillMaxWidth()) {
                Slider(
                    value = value.coerceIn(min, max),
                    enabled = enabled && max > min,
                    onValueChange = { new ->
                        if (enabled) {
                            val stepped = if (step != null) {
                                (min.toDouble() + round((new.toDouble() - min) / step) * step).toFloat()
                            } else new
                            value = stepped.coerceIn(min, max)
                            updateModel(widget, pluginId, sliderModelValue(value, external))
                            onEvent()
                        }
                    },
                    valueRange = min..max,
                    colors = SliderDefaults.colors(
                        thumbColor = color ?: MaterialTheme.colorScheme.primary,
                        activeTrackColor = color ?: MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        "button" -> Button(
            onClick = onEvent,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = background ?: MaterialTheme.colorScheme.primary,
                contentColor = color ?: MaterialTheme.colorScheme.onPrimary
            ),
            modifier = base
        ) {
            Text(PluginExecutor.interpolate(widget.content.orEmpty(), env, pluginId))
        }
        "switch", "checkbox" -> {
            val external = modelValue(widget, pluginId, env)
            val fallback = PluginBindings.resolve(widget.content ?: "false", env, pluginId)
            var checked by remember(pluginId, widget.model, external, fallback) {
                mutableStateOf(bindingBoolean(external ?: fallback))
            }
            val onCheckedChange: (Boolean) -> Unit = { new ->
                if (enabled) {
                    checked = new
                    updateModel(widget, pluginId, new)
                    onEvent()
                }
            }
            if (type == "switch") {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = base,
                    colors = SwitchDefaults.colors(checkedTrackColor = color ?: MaterialTheme.colorScheme.primary)
                )
            } else {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = base,
                    colors = CheckboxDefaults.colors(checkedColor = color ?: MaterialTheme.colorScheme.primary)
                )
            }
        }
        "iconbutton" -> IconButton(onClick = onEvent, enabled = enabled, modifier = base) {
            Icon(
                imageVector = iconOf(widget.icon),
                contentDescription = (widget.title ?: widget.content)?.let {
                    PluginExecutor.interpolate(it, env, pluginId)
                },
                tint = if (enabled) color ?: LocalContentColor.current else LocalContentColor.current,
                modifier = Modifier.size((widget.size ?: 24f).dp)
            )
        }
        "spacer" -> Spacer(modifier = base.height((widget.size ?: 8f).dp))
        "divider" -> HorizontalDivider(modifier = base, color = color ?: DividerDefaults.color)
        "image" -> PluginWidgetImage(
            widget,
            pluginId,
            PluginExecutor.interpolate(widget.content.orEmpty(), env, pluginId),
            base
        )
        "icon" -> Icon(
            imageVector = iconOf(widget.icon),
            contentDescription = null,
            tint = color ?: MaterialTheme.colorScheme.onSurface,
            modifier = base.size((widget.size ?: 20f).dp)
        )
        "card" -> {
            val content: @Composable ColumnScope.() -> Unit = {
                Column(Modifier.padding(12.dp)) {
                    widget.children.forEach { child ->
                        PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child), enabled)
                    }
                }
            }
            val colors = CardDefaults.cardColors(
                containerColor = background ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = color ?: contentColorFor(background ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            if (hasClick) {
                Card(
                    onClick = onEvent,
                    enabled = enabled,
                    modifier = base,
                    shape = RoundedCornerShape(16.dp),
                    colors = colors,
                    content = content
                )
            } else {
                Card(modifier = base, shape = RoundedCornerShape(16.dp), colors = colors, content = content)
            }
        }
        "settingscard" -> Box(
            modifier = base.then(if (!enabled) Modifier.semantics { disabled() } else Modifier)
        ) {
            val scheme = MaterialTheme.colorScheme
            MaterialTheme(
                colorScheme = scheme.copy(
                    surfaceContainerHigh = background ?: scheme.surfaceContainerHigh,
                    onSurface = color ?: scheme.onSurface,
                    onSurfaceVariant = color ?: scheme.onSurfaceVariant
                )
            ) {
                SettingsGroupSurface(isTop = true, isBottom = true, onClick = onEvent) {
                    Column {
                        SettingsItem(
                            title = PluginExecutor.interpolate(widget.title ?: widget.content.orEmpty(), env, pluginId),
                            subtitle = widget.subtitle?.let { PluginExecutor.interpolate(it, env, pluginId) },
                            icon = widget.icon?.let(::iconOf),
                            isEnabled = enabled,
                            onClick = onEvent
                        )
                        if (widget.children.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(12.dp).alpha(if (enabled) 1f else 0.38f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                widget.children.forEach { child ->
                                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child), enabled)
                                }
                            }
                        }
                    }
                }
            }
        }
        "scroll" -> Column(
            modifier = if (verticalScrollManaged) base else base.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompositionLocalProvider(LocalPluginScrollManagedByHost provides true) {
                widget.children.forEach { child ->
                    PluginWidgetRenderer(child, pluginId, env, Modifier.applyChildAlign(child), enabled)
                }
            }
        }
        else -> Text("[未知类型: ${widget.type}]")
    }
}

private fun bindingBoolean(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toDouble().let { it.isFinite() && it != 0.0 }
    is String -> value.trim().let { it.equals("true", ignoreCase = true) || it.toDoubleOrNull()?.let { number -> number.isFinite() && number != 0.0 } == true }
    else -> false
}

private fun modelValue(widget: PluginWidget, pluginId: String, env: Map<String, Any?>): Any? {
    val model = widget.model?.removePrefix("var.")?.takeIf { it.isNotBlank() } ?: return null
    return PluginSession.getVar(pluginId, model) ?: env["var.$model"] ?: env[model]
}

private fun updateModel(widget: PluginWidget, pluginId: String, value: Any) {
    val model = widget.model?.removePrefix("var.")?.takeIf { it.isNotBlank() } ?: return
    PluginSession.setVar(pluginId, model, value)
}

private fun modelInput(value: String, previous: Any?): Any? = when (previous) {
    is Number -> value.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { number ->
        when (previous) {
            is Int -> value.toIntOrNull() ?: number
            is Long -> value.toLongOrNull() ?: number
            is Float -> number.toFloat().takeIf { it.isFinite() }
            else -> number
        }
    }
    is Boolean -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
    }
    else -> value
}

private fun numberValue(value: Any?): Float? = when (value) {
    is Number -> value.toFloat()
    is String -> value.toFloatOrNull()
    else -> null
}?.takeIf { it.isFinite() }

private fun sliderModelValue(value: Float, previous: Any?): Number = when (previous) {
    is Int -> if (value.toDouble() == value.toInt().toDouble()) value.toInt() else value
    is Long -> if (value.toDouble() == value.toLong().toDouble()) value.toLong() else value
    is Double -> value.toDouble()
    else -> value
}

private fun Modifier.applyLayout(widget: PluginWidget, pluginId: String, env: Map<String, Any?>): Modifier {
    var m = this
    val width = widget.width?.let { PluginExecutor.interpolate(it, env, pluginId) }
    val height = widget.height?.let { PluginExecutor.interpolate(it, env, pluginId) }
    when (width?.lowercase()) {
        "match", "fill", "fillmaxwidth" -> m = m.fillMaxWidth()
        "wrap", "wrapcontent" -> m = m.wrapContentWidth()
        null -> {}
        else -> width.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.let { m = m.width(it.dp) }
    }
    when (height?.lowercase()) {
        "match", "fill", "fillmaxheight" -> m = m.fillMaxHeight()
        "wrap", "wrapcontent" -> m = m.wrapContentHeight()
        null -> {}
        else -> height.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.let { m = m.height(it.dp) }
    }
    widget.padding?.takeIf { it.isFinite() && it >= 0f }?.let { m = m.padding(it.dp) }
    widget.paddingH?.takeIf { it.isFinite() && it >= 0f }?.let { m = m.padding(horizontal = it.dp) }
    widget.paddingV?.takeIf { it.isFinite() && it >= 0f }?.let { m = m.padding(vertical = it.dp) }
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
private fun PluginWidgetImage(widget: PluginWidget, pluginId: String, src: String, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(pluginId, src) { mutableStateOf<Bitmap?>(null) }
    if (src.isBlank()) return
    LaunchedEffect(pluginId, src, context) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val data: Any = when {
                    src.startsWith("assets/") -> {
                        val pluginsDir = File(context.filesDir, "lua_plugins").canonicalFile
                        val pluginDir = File(pluginsDir, pluginId).canonicalFile
                        val assetsDir = File(pluginDir, "assets").canonicalFile
                        val file = File(pluginDir, src).canonicalFile
                        if (pluginDir.parentFile != pluginsDir ||
                            assetsDir.parentFile != pluginDir ||
                            !file.path.startsWith(assetsDir.path + File.separator) ||
                            !file.isFile
                        ) return@withContext null
                        file
                    }
                    src.startsWith("http://") || src.startsWith("https://") -> src
                    else -> return@withContext null
                }
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(1024)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                if (result is SuccessResult) (result.drawable as? BitmapDrawable)?.bitmap else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    val size = (widget.size?.takeIf { it.isFinite() && it >= 0f } ?: 48f).dp
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } ?: Spacer(modifier.size(size))
}

private fun safeColor(value: String, scheme: ColorScheme): Color {
    val name = value.trim()
    if (name.startsWith("MaterialTheme.colorScheme.")) {
        return when (name.removePrefix("MaterialTheme.colorScheme.")) {
            "primary" -> scheme.primary
            "onPrimary" -> scheme.onPrimary
            "primaryContainer" -> scheme.primaryContainer
            "onPrimaryContainer" -> scheme.onPrimaryContainer
            "inversePrimary" -> scheme.inversePrimary
            "secondary" -> scheme.secondary
            "onSecondary" -> scheme.onSecondary
            "secondaryContainer" -> scheme.secondaryContainer
            "onSecondaryContainer" -> scheme.onSecondaryContainer
            "tertiary" -> scheme.tertiary
            "onTertiary" -> scheme.onTertiary
            "tertiaryContainer" -> scheme.tertiaryContainer
            "onTertiaryContainer" -> scheme.onTertiaryContainer
            "background" -> scheme.background
            "onBackground" -> scheme.onBackground
            "surface" -> scheme.surface
            "onSurface" -> scheme.onSurface
            "surfaceVariant" -> scheme.surfaceVariant
            "onSurfaceVariant" -> scheme.onSurfaceVariant
            "surfaceTint" -> scheme.surfaceTint
            "inverseSurface" -> scheme.inverseSurface
            "inverseOnSurface" -> scheme.inverseOnSurface
            "error" -> scheme.error
            "onError" -> scheme.onError
            "errorContainer" -> scheme.errorContainer
            "onErrorContainer" -> scheme.onErrorContainer
            "outline" -> scheme.outline
            "outlineVariant" -> scheme.outlineVariant
            "scrim" -> scheme.scrim
            "surfaceBright" -> scheme.surfaceBright
            "surfaceDim" -> scheme.surfaceDim
            "surfaceContainer" -> scheme.surfaceContainer
            "surfaceContainerHigh" -> scheme.surfaceContainerHigh
            "surfaceContainerHighest" -> scheme.surfaceContainerHighest
            "surfaceContainerLow" -> scheme.surfaceContainerLow
            "surfaceContainerLowest" -> scheme.surfaceContainerLowest
            "primaryFixed" -> scheme.primaryFixed
            "primaryFixedDim" -> scheme.primaryFixedDim
            "onPrimaryFixed" -> scheme.onPrimaryFixed
            "onPrimaryFixedVariant" -> scheme.onPrimaryFixedVariant
            "secondaryFixed" -> scheme.secondaryFixed
            "secondaryFixedDim" -> scheme.secondaryFixedDim
            "onSecondaryFixed" -> scheme.onSecondaryFixed
            "onSecondaryFixedVariant" -> scheme.onSecondaryFixedVariant
            "tertiaryFixed" -> scheme.tertiaryFixed
            "tertiaryFixedDim" -> scheme.tertiaryFixedDim
            "onTertiaryFixed" -> scheme.onTertiaryFixed
            "onTertiaryFixedVariant" -> scheme.onTertiaryFixedVariant
            else -> Color.Magenta
        }
    }
    return runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrDefault(Color.Magenta)
}
