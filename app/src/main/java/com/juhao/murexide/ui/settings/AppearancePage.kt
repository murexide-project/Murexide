package com.juhao.murexide.ui.settings

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val CUSTOM_PREFIX = "CUSTOM_"

private fun parseCustomThemeColor(value: String): Color? {
    if (!value.startsWith(CUSTOM_PREFIX)) return null
    val argb = value.removePrefix(CUSTOM_PREFIX).toLongOrNull() ?: return null
    return Color(argb.toInt())
}

private fun encodeCustomThemeColor(color: Color): String {
    val r = (color.red * 255f).roundToInt()
    val g = (color.green * 255f).roundToInt()
    val b = (color.blue * 255f).roundToInt()
    val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    return "$CUSTOM_PREFIX$argb"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearancePage() {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }
    val scope = rememberCoroutineScope()

    val themeMode by UiState.themeMode
    val themeColor by UiState.themeColor

    val squareAvatar by UiState.squareAvatar

    val liquidGlassEnabled by settingsStorage.liquidGlassEnabledFlow.collectAsState(initial = false)
    var liquidGlassBlur by remember { mutableFloatStateOf(1f) }
    val showBackground by settingsStorage.showBackgroundFlow.collectAsState(initial = true)
    var backgroundOpacity by remember { mutableFloatStateOf(0.5f) }

    val floatBottomBarEnabled by settingsStorage.isFloatBottomBarEnabledFlow.collectAsState(initial = true)
    val mergeStickyEnabled by settingsStorage.mergeStickyFlow.collectAsState(initial = false)

    val isDark = usesDarkTheme(themeMode, isSystemInDarkTheme())

    var showCustomColorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        backgroundOpacity = settingsStorage.getBackgroundOpacity()
        liquidGlassBlur = settingsStorage.getLiquidGlassBlur()
    }

    if (showCustomColorDialog) {
        CustomThemeColorDialog(
            initialColor = parseCustomThemeColor(themeColor),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { color ->
                val value = encodeCustomThemeColor(color)
                UiState.themeColor.value = value
                scope.launch { settingsStorage.setThemeColor(value) }
                showCustomColorDialog = false
            }
        )
    }

    SettingsGroup {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ConversationListPreview(squareAvatar = squareAvatar, mergeSticky = mergeStickyEnabled)
        }
    }

    SettingsGroup(title = "主题") {
        CustomItemCell {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.WbSunny,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "主题模式",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ThemeModeButtonGroup(
                    selectedMode = themeMode,
                    onModeSelected = { value ->
                        UiState.themeMode.value = value
                        scope.launch { settingsStorage.setThemeMode(value) }
                    }
                )
            }
        }
        if (isDark) {
            SettingsSwitchItem(
                icon = AppIcons.Contrast,
                title = "纯黑模式",
                subtitle = "使用纯黑背景，适合 OLED 屏幕",
                checked = themeMode == "oled",
                onCheckedChange = { enabled ->
                    val value = if (enabled) "oled" else "dark"
                    UiState.themeMode.value = value
                    scope.launch { settingsStorage.setThemeMode(value) }
                }
            )
        }
        ThemeColorPicker(
            selectedColor = themeColor,
            isDark = isDark,
            onColorSelected = { selected ->
                UiState.themeColor.value = selected
                scope.launch { settingsStorage.setThemeColor(selected) }
            },
            onCustomClick = { showCustomColorDialog = true }
        )

        SettingsSwitchItem(
            icon = AppIcons.Opacity,
            title = "启用液态玻璃效果",
            subtitle = "使用液态玻璃风格显示界面元素，仅在 Android 13 及以上效果最佳",
            checked = liquidGlassEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    settingsStorage.setLiquidGlassEnabled(enabled)
                }
            }
        )
        if (liquidGlassEnabled) {
            CustomItemCell {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Opacity,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "液态玻璃模糊",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${
                                    "%.1f".format(
                                        java.util.Locale.US,
                                        liquidGlassBlur
                                    )
                                }x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 2.dp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = liquidGlassBlur,
                        onValueChange = { liquidGlassBlur = it },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStorage.setLiquidGlassBlur(
                                    liquidGlassBlur
                                )
                            }
                        },
                        valueRange = 0f..4f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "4",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    SettingsGroup(title = "界面") {
        SettingsSwitchItem(
            icon = AppIcons.Image,
            title = "显示会话背景",
            subtitle = "在会话页面显示设定的背景",
            checked = showBackground,
            onCheckedChange = { checked ->
                scope.launch {
                    settingsStorage.setShowBackground(checked)
                }
            }
        )

        if (showBackground) {
            CustomItemCell {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "背景不透明度",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${(backgroundOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = backgroundOpacity,
                        onValueChange = { backgroundOpacity = it },
                        onValueChangeFinished = {
                            scope.launch {
                                settingsStorage.setBackgroundOpacity(backgroundOpacity)
                            }
                        },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "20%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SettingsSwitchItem(
            icon = AppIcons.People,
            title = "圆角矩形头像",
            subtitle = "将好友和群组头像显示为圆角矩形",
            checked = squareAvatar,
            onCheckedChange = { checked ->
                UiState.squareAvatar.value = checked
                scope.launch {
                    settingsStorage.setSquareAvatar(checked)
                }
            }
        )
        SettingsSwitchItem(
            icon = AppIcons.Settings,
            title = "主页悬浮底栏",
            subtitle = "开启悬浮底栏可同时显示玻璃效果",
            checked = floatBottomBarEnabled,
            onCheckedChange = { checked ->
                scope.launch {
                    settingsStorage.setFloatBottomBar(checked)
                }
            }
        )
        SettingsSwitchItem(
            icon = AppIcons.Keep,
            title = "合并置顶会话",
            subtitle = "开启后置顶会话将合并至会话列表中而不是单独显示置顶栏",
            checked = mergeStickyEnabled,
            onCheckedChange = { checked ->
                scope.launch {
                    settingsStorage.setMergeSticky(checked)
                }
            }
        )
    }
}

private fun themeColorScheme(value: String, isDark: Boolean): ColorScheme {
    parseCustomThemeColor(value)?.let { color ->
        return buildCustomColorScheme(
            base = if (isDark) neutralBaseForCustom else dayNeutralBaseForCustom,
            customColor = color,
            isDay = !isDark
        )
    }
    return when (value) {
        "DYNAMIC" -> if (isDark) DynamicDarkColorScheme else DynamicLightColorScheme
        "WHITE" -> if (isDark) WhiteDarkColorScheme else WhiteLightColorScheme
        "PURPLE" -> if (isDark) PurpleDarkColorScheme else PurpleLightColorScheme
        "BLUE" -> if (isDark) BlueDarkColorScheme else BlueLightColorScheme
        "GREEN" -> if (isDark) GreenDarkColorScheme else GreenLightColorScheme
        "ORANGE" -> if (isDark) OrangeDarkColorScheme else OrangeLightColorScheme
        "RED" -> if (isDark) RedDarkColorScheme else RedLightColorScheme
        "TEAL" -> if (isDark) TealDarkColorScheme else TealLightColorScheme
        "PINK" -> if (isDark) PinkDarkColorScheme else PinkLightColorScheme
        "INDIGO" -> if (isDark) IndigoDarkColorScheme else IndigoLightColorScheme
        "BROWN" -> if (isDark) BrownDarkColorScheme else BrownLightColorScheme
        "GRAY" -> if (isDark) GrayDarkColorScheme else GrayLightColorScheme
        else -> if (isDark) PurpleDarkColorScheme else PurpleLightColorScheme
    }
}

private data class ThemeModeOption(
    val value: String,
    val label: String
)

private val themeModeOptions = listOf(
    ThemeModeOption("system", "系统"),
    ThemeModeOption("light", "日间"),
    ThemeModeOption("dark", "夜间")
)

@Composable
private fun ThemeModeButtonGroup(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val selectedIndex = themeModeOptions
        .indexOfFirst { it.value == selectedMode }
        .takeIf { it >= 0 }
        ?: themeModeOptions.lastIndex

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        themeModeOptions.forEachIndexed { index, option ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onModeSelected(option.value) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = themeModeOptions.size
                ),
                label = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

private data class ThemeColorOption(
    val value: String,
    val label: String
)

private val themeColorOptions = listOf(
    ThemeColorOption("DYNAMIC", "动态取色"),
    ThemeColorOption("WHITE", "白色"),
    ThemeColorOption("PURPLE", "紫色"),
    ThemeColorOption("BLUE", "蓝色"),
    ThemeColorOption("GREEN", "绿色"),
    ThemeColorOption("ORANGE", "橙色"),
    ThemeColorOption("RED", "红色"),
    ThemeColorOption("TEAL", "青色"),
    ThemeColorOption("PINK", "粉色"),
    ThemeColorOption("INDIGO", "靛蓝"),
    ThemeColorOption("BROWN", "棕色"),
    ThemeColorOption("GRAY", "灰色")
)

@Composable
private fun ThemeColorPicker(
    selectedColor: String,
    isDark: Boolean,
    onColorSelected: (String) -> Unit,
    onCustomClick: () -> Unit
) {
    CustomItemCell {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.Draw,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "主题颜色",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(themeColorOptions) { option ->
                    ThemeColorBall(
                        option = option,
                        selected = selectedColor == option.value,
                        isDark = isDark,
                        onClick = { onColorSelected(option.value) }
                    )
                }
                item {
                    CustomThemeColorBall(
                        selected = parseCustomThemeColor(selectedColor) != null,
                        customColor = parseCustomThemeColor(selectedColor),
                        isDark = isDark,
                        onClick = onCustomClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorBall(
    option: ThemeColorOption,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val scheme = remember(option.value, isDark) {
        themeColorScheme(option.value, isDark)
    }

    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        label = "themeBallBorderWidth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = "${option.label}主题" }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            border = if (selected) {
                BorderStroke(borderWidth, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(48.dp)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawArc(
                            color = scheme.secondaryContainer,
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            size = size
                        )

                        drawArc(
                            color = scheme.tertiaryContainer,
                            startAngle = 90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            size = size
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .align(Alignment.Center)
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun CustomThemeColorBall(
    selected: Boolean,
    customColor: Color?,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val previewColor = customColor ?: Color(0xFF9BD7FF)
    val scheme = remember(previewColor, isDark) {
        buildCustomColorScheme(
            base = if (isDark) neutralBaseForCustom else dayNeutralBaseForCustom,
            customColor = previewColor,
            isDay = !isDark
        )
    }

    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        label = "customThemeBallBorderWidth"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = "自定义主题" }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            border = if (selected) {
                BorderStroke(borderWidth, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialColor: Color?,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val start = initialColor ?: Color(0xFF9BD7FF)
    var red by remember { mutableFloatStateOf(start.red) }
    var green by remember { mutableFloatStateOf(start.green) }
    var blue by remember { mutableFloatStateOf(start.blue) }

    val currentColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("自定义主题色")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(currentColor)
                )

                Spacer(modifier = Modifier.height(20.dp))

                ColorChannelSlider(
                    label = "红",
                    value = red,
                    tint = Color(0xFFFF5252),
                    onValueChange = { red = it }
                )
                ColorChannelSlider(
                    label = "绿",
                    value = green,
                    tint = Color(0xFF69F0AE),
                    onValueChange = { green = it }
                )
                ColorChannelSlider(
                    label = "蓝",
                    value = blue,
                    tint = Color(0xFF40C4FF),
                    onValueChange = { blue = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ColorChannelSlider(
    label: String,
    value: Float,
    tint: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint
            )
        )
        Text(
            text = "${(value * 255f).roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun ConversationListPreview(squareAvatar: Boolean, mergeSticky: Boolean) {
    val scheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.width(240.dp),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
            colors = CardDefaults.cardColors(containerColor = scheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "消息",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(scheme.primaryContainer)
                    )
                }

                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.5f)
                )

                if (!mergeSticky) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConversationPreviewAvatar(
                            name = "设计团队",
                            avatarColor = scheme.primaryContainer,
                            avatarContentColor = scheme.onPrimaryContainer,
                            squareAvatar = squareAvatar
                        )
                        ConversationPreviewAvatar(
                            name = "项目组",
                            avatarColor = scheme.tertiaryContainer,
                            avatarContentColor = scheme.onTertiaryContainer,
                            squareAvatar = squareAvatar
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                ConversationPreviewItem(
                    name = "设计团队",
                    message = "明天上午十点开会",
                    time = "10:24",
                    unread = 3,
                    avatarColor = scheme.primaryContainer,
                    avatarContentColor = scheme.onPrimaryContainer,
                    squareAvatar = squareAvatar,
                    isSticky = mergeSticky
                )
                ConversationPreviewItem(
                    name = "项目组",
                    message = "[图片]",
                    time = "昨天",
                    unread = 12,
                    avatarColor = scheme.tertiaryContainer,
                    avatarContentColor = scheme.onTertiaryContainer,
                    squareAvatar = squareAvatar,
                    isSticky = mergeSticky
                )

                ConversationPreviewItem(
                    name = "Alex",
                    message = "好的，收到 👍",
                    time = "09:15",
                    unread = 0,
                    avatarColor = scheme.secondaryContainer,
                    avatarContentColor = scheme.onSecondaryContainer,
                    squareAvatar = squareAvatar,
                    isSticky = false
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationPreviewAvatar(
    name: String,
    avatarColor: Color,
    avatarContentColor: Color,
    squareAvatar: Boolean
) {
    val avatarShape = if (squareAvatar) RoundedCornerShape(8.dp) else CircleShape
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(avatarShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Person,
                contentDescription = null,
                tint = avatarContentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ConversationPreviewItem(
    name: String,
    message: String,
    time: String,
    unread: Int,
    avatarColor: Color,
    avatarContentColor: Color,
    squareAvatar: Boolean,
    isSticky: Boolean
) {
    val themeColor by UiState.themeColor
    val scheme = MaterialTheme.colorScheme
    val avatarShape = if (squareAvatar) RoundedCornerShape(8.dp) else CircleShape

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if ((isSticky && themeColor != "WHITE") || (!isSticky && themeColor == "WHITE")) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(avatarShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Person,
                contentDescription = null,
                tint = avatarContentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
            if (unread > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape)
                        .background(scheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unread > 99) "99+" else unread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onError,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}