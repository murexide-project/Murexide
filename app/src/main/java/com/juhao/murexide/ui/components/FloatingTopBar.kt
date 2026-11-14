package com.juhao.murexide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.kyant.backdrop.Backdrop
import com.juhao.murexide.ui.theme.liquidglass.glassControl
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloatingTopBar(
    hazeState: HazeState,
    liquidBackdrop: Backdrop?,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    onTitleClick: () -> Unit = {},
    extraButton: (@Composable () -> Unit)? = null,
    onExtraButtonClick: () -> Unit = {},
    showOverlay: Boolean = true
) {
    // 悬浮顶栏开关在组件内部读取设置（默认开）
    val context = LocalContext.current
    val floatingEnabled by remember { SettingsStorage(context.applicationContext) }
        .floatingTopBarFlow
        .collectAsState(initial = true)
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val controlSize = 48.dp
    val buttonShape = CircleShape
    val topBarColor = MaterialTheme.colorScheme.surface
    val topBarStyle = HazeMaterials.thin().then {
        blurRadius(32.dp)
        noiseFactor(0f)
    }
    val useGlassBar = liquidGlassEnabled && liquidBackdrop != null

    @Composable
    fun Modifier.glassControl(
        shape: Shape,
        isInteractive: Boolean = true
    ): Modifier = glassControl(shape, liquidBackdrop, hazeState, useGlassBar, isInteractive)

    if (!floatingEnabled) {
        // 关闭悬浮顶栏：普通 TopAppBar，不做悬浮/玻璃，背景用 haze 毛玻璃
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlSize)
                        .clickable(onClick = onTitleClick)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (avatar != null) {
                        avatar()
                    }
                    Spacer(Modifier.width(if (avatar == null) 8.dp else 4.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        title()
                    }
                    if (extraButton != null) {
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onExtraButtonClick)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            extraButton()
                        }
                    }
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier.size(controlSize),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                }
            },
            actions = {
                if (actions != null) {
                    Row(
                        modifier = Modifier.height(controlSize),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                    }
                }
            },
            modifier = Modifier.hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = topBarStyle
            ),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified
            )
        )
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showOverlay) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                topBarColor.copy(alpha = 0.8f),
                                topBarColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        TopAppBar(
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlSize)
                        .glassControl(RoundedCornerShape(24.dp), true)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClick = onTitleClick)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (avatar != null) {
                        avatar()
                    }
                    Spacer(Modifier.width(if (avatar == null) 8.dp else 4.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        title()
                    }
                    if (extraButton != null) {
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onExtraButtonClick)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            extraButton()
                        }
                    }
                }
            },
            navigationIcon = {
                if (navigationIcon != null) {
                    Box(
                        modifier = Modifier
                            .size(controlSize)
                            .glassControl(buttonShape),
                        contentAlignment = Alignment.Center
                    ) {
                        navigationIcon()
                    }
                }
            },
            actions = {
                if (actions != null) {
                    Row(
                        modifier = Modifier
                            .height(controlSize)
                            .glassControl(buttonShape),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions()
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified
            )
        )
    }
}