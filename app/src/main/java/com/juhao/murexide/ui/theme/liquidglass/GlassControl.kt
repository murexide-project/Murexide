package com.juhao.murexide.ui.theme.liquidglass

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.material3.MaterialTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import com.juhao.murexide.ui.theme.liquidglass.InteractiveHighlight

@Composable
fun Modifier.glassControl(
    shape: Shape,
    liquidBackdrop: Backdrop? = null,
    hazeState: HazeState,
    useGlassBar: Boolean,
    isInteractive: Boolean = true
): Modifier =
    if (useGlassBar && liquidBackdrop != null) {
        val liquidGlassBlur = LocalLiquidGlassBlur.current
        
        val color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
        
        val animationScope = rememberCoroutineScope()
    
        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope
            )
        }
    
        drawBackdrop(
            backdrop = liquidBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(1.dp.toPx() * liquidGlassBlur)
                lens(16.dp.toPx(), 32.dp.toPx())
            },
            layerBlock = {
                val width = size.width
                val height = size.height
    
                val progress = interactiveHighlight.pressProgress
                val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
    
                val maxOffset = size.minDimension
                val initialDerivative = 0.05f
                val offset = interactiveHighlight.offset
                translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
    
                val maxDragScale = 4f.dp.toPx() / size.height
                val offsetAngle = atan2(offset.y, offset.x)
                scaleX =
                    scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                scaleY =
                    scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
            },
            onDrawSurface = {
                drawRect(color)
            },
        )
        .then(
            if (isInteractive) {
                Modifier
                    .then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier)
            } else {
                Modifier
            }
        )
    } else {
        val style = HazeMaterials.thin().then {
            blurRadius(32.dp)
            noiseFactor(0f)
        }
        
        shadow(2.dp, shape)
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = style
            )
    }