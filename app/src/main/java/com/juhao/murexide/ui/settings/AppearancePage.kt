package com.juhao.murexide.ui.settings

import android.os.Build
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSlider
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.data.MessageTag
import com.juhao.murexide.ui.chat.components.MessageBubble
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        backgroundOpacity = settingsStorage.getBackgroundOpacity()
        liquidGlassBlur = settingsStorage.getLiquidGlassBlur()
    }

    // 主题设置
    SettingsGroup(title = "主题") {
        SettingsDropdownItem(
            icon = AppIcons.WbSunny,
            title = "主题模式",
            subtitle = when (themeMode) {
                "dark" -> "深色模式"
                "light" -> "浅色模式"
                "oled" -> "纯黑模式（禁用动态取色）"
                else -> "跟随系统"
            },
            options = listOf(
                "system" to "跟随系统",
                "light" to "浅色模式",
                "dark" to "深色模式",
                "oled" to "纯黑模式（禁用动态取色）"
            ),
            selectedValue = themeMode,
            onOptionSelected = { selected ->
                UiState.themeMode.value = selected
                scope.launch {
                    settingsStorage.setThemeMode(selected)
                }
            }
        )
        ThemeColorPicker(
            selectedColor = themeColor,
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

private data class ThemeColorOption(
    val value: String,
    val label: String,
    val color: Color
)

private val themeColorOptions = listOf(
    ThemeColorOption("DYNAMIC", "动态取色", Color(0xFFD946EF)),
    ThemeColorOption("WHITE", "白色", Color.White),
    ThemeColorOption("PURPLE", "紫色", Color(0xFF7C4DFF)),
    ThemeColorOption("BLUE", "蓝色", Color(0xFF008CFF)),
    ThemeColorOption("GREEN", "绿色", Color(0xFF00A63E)),
    ThemeColorOption("ORANGE", "橙色", Color(0xFFFF6D00))
)

@Composable
private fun ThemeColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    CustomItemCell {
        Icon(
            imageVector = AppIcons.Draw,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "主题颜色",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                themeColorOptions.forEach { option ->
                    val selected = selectedColor == option.value
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.16f else 1f,
                        animationSpec = spring(),
                        label = "themeColorScale"
                    )
                    val borderWidth by animateDpAsState(
                        targetValue = if (selected) 3.dp else 1.dp,
                        label = "themeColorBorderWidth"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        label = "themeColorBorder"
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (option.value == "DYNAMIC") {
                            Color.Transparent
                        } else {
                            option.color
                        },
                        border = BorderStroke(
                            width = borderWidth,
                            color = borderColor
                        ),
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .semantics { contentDescription = "${option.label}主题" }
                            .selectable(
                                selected = selected,
                                onClick = { onColorSelected(option.value) },
                                role = Role.RadioButton
                            )
                    ) {
                        if (option.value == "DYNAMIC") {
                            Icon(
                                imageVector = AppIcons.Colorize,
                                contentDescription = null,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
