package com.juhao.murexide.ui.settings

import android.os.Build
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.ui.theme.usesDarkTheme
import com.juhao.murexide.ui.theme.DynamicDarkColorScheme
import com.juhao.murexide.ui.theme.WhiteDarkColorScheme
import com.juhao.murexide.ui.theme.PurpleDarkColorScheme
import com.juhao.murexide.ui.theme.BlueDarkColorScheme
import com.juhao.murexide.ui.theme.GreenDarkColorScheme
import com.juhao.murexide.ui.theme.OrangeDarkColorScheme
import com.juhao.murexide.ui.theme.RedDarkColorScheme
import com.juhao.murexide.ui.theme.TealDarkColorScheme
import com.juhao.murexide.ui.theme.PinkDarkColorScheme
import com.juhao.murexide.ui.theme.IndigoDarkColorScheme
import com.juhao.murexide.ui.theme.BrownDarkColorScheme
import com.juhao.murexide.ui.theme.GrayDarkColorScheme
import com.juhao.murexide.ui.theme.DynamicLightColorScheme
import com.juhao.murexide.ui.theme.WhiteLightColorScheme
import com.juhao.murexide.ui.theme.PurpleLightColorScheme
import com.juhao.murexide.ui.theme.BlueLightColorScheme
import com.juhao.murexide.ui.theme.GreenLightColorScheme
import com.juhao.murexide.ui.theme.OrangeLightColorScheme
import com.juhao.murexide.ui.theme.RedLightColorScheme
import com.juhao.murexide.ui.theme.TealLightColorScheme
import com.juhao.murexide.ui.theme.PinkLightColorScheme
import com.juhao.murexide.ui.theme.IndigoLightColorScheme
import com.juhao.murexide.ui.theme.BrownLightColorScheme
import com.juhao.murexide.ui.theme.GrayLightColorScheme
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSlider
import kotlinx.coroutines.launch

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

    val isDark = usesDarkTheme(themeMode, isSystemInDarkTheme())

    LaunchedEffect(Unit) {
        backgroundOpacity = settingsStorage.getBackgroundOpacity()
        liquidGlassBlur = settingsStorage.getLiquidGlassBlur()
    }

    SettingsGroup {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeCard()
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ThemeModeButtonGroup(
                        selectedMode = themeMode,
                        onModeSelected = { value ->
                            UiState.themeMode.value = value
                            scope.launch { settingsStorage.setThemeMode(value) }
                        }
                    )
                }
            }
        }
        ThemeColorPicker(
            selectedColor = themeColor,
            isDark = isDark,
            onColorSelected = { selected ->
                UiState.themeColor.value = selected
                scope.launch { settingsStorage.setThemeColor(selected) }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsSwitchItem(
                icon = AppIcons.Opacity,
                title = "启用液态玻璃效果",
                subtitle = "使用液态玻璃风格显示界面元素",
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
                        LiquidGlassSlider(
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

                    LiquidGlassSlider(
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
    }
}

private fun themeColorScheme(value: String, isDark: Boolean): ColorScheme = when (value) {
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

private data class ThemeModeOption(
    val value: String,
    val label: String,
    val checkedIcon: ImageVector,
    val unCheckedIcon: ImageVector
)

private val themeModeOptions = listOf(
    ThemeModeOption(
        value = "system",
        label = "系统",
        checkedIcon = AppFilledIcons.BrightnessAuto,
        unCheckedIcon = AppIcons.BrightnessAuto
    ),
    ThemeModeOption(
        value = "light",
        label = "浅色",
        checkedIcon = AppFilledIcons.LightMode,
        unCheckedIcon = AppIcons.LightMode
    ),
    ThemeModeOption(
        value = "dark",
        label = "深色",
        checkedIcon = AppFilledIcons.DarkMode,
        unCheckedIcon = AppIcons.DarkMode
    ),
    ThemeModeOption(
        value = "oled",
        label = "纯黑",
        checkedIcon = AppFilledIcons.Contrast,
        unCheckedIcon = AppIcons.Contrast
    )
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeButtonGroup(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val interactionSources = remember {
        List(themeModeOptions.size) { MutableInteractionSource() }
    }
    val layoutDirection = LocalLayoutDirection.current

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        expandedRatio = 1f
    ) {
        themeModeOptions.forEachIndexed { index, option ->
            customItem(
                buttonGroupContent = {
                    val contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ToggleButton(
                        checked = selectedMode == option.value,
                        onCheckedChange = {
                            onModeSelected(option.value)
                        },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            themeModeOptions.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = contentPadding,
                        interactionSource = interactionSources[index],
                        modifier = Modifier.animateWidth(
                            interactionSource = interactionSources[index],
                            compressionLimit =
                                contentPadding.calculateEndPadding(layoutDirection)
                        )
                    ) {
                        Icon(
                            imageVector = if (selectedMode == option.value) {
                                option.checkedIcon
                            } else {
                                option.unCheckedIcon
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        Text(
                            text = option.label,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Visible
                        )
                    }
                },
                menuContent = {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = option.checkedIcon,
                                contentDescription = null
                            )
                        },
                        text = { Text(option.label) },
                        onClick = { onModeSelected(option.value) },
                        interactionSource = interactionSources[index]
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
    onColorSelected: (String) -> Unit
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
private fun ThemeCard() {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .width(128.dp)
                .height(256.dp),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, scheme.primary),
            colors = CardDefaults.cardColors(containerColor = scheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 12.dp)
                        .size(width = 48.dp, height = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.onSurfaceVariant)
                )

                Box(
                    modifier = Modifier
                        .padding(start = 12.dp, top = 36.dp)
                        .size(width = 56.dp, height = 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.secondaryContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(width = 16.dp, height = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(scheme.secondary)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(scheme.surfaceContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                }
            }
        }
    }
}