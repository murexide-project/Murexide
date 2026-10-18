package com.juhao.murexide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

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
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val controlSize = 48.dp
    val buttonShape = CircleShape
    val topBarColor = MaterialTheme.colorScheme.surfaceContainer
    val topBarStyle = HazeMaterials.thin().then {
        blurRadius(32.dp)
        noiseFactor(0f)
    }
    val useGlassBar = liquidGlassEnabled && liquidBackdrop != null
    val glassSurfaceColor = topBarColor.copy(alpha = 0.8f)

    fun Modifier.glassControl(shape: Shape): Modifier =
        if (useGlassBar) {
            drawBackdrop(
                backdrop = liquidBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(1.dp.toPx() * liquidGlassBlur)
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(glassSurfaceColor)
                },
            )
        } else {
            shadow(2.dp, shape)
                .clip(shape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = topBarStyle
                )
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(controlSize)
                    .glassControl(RoundedCornerShape(24.dp))
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
        }
    }
}