/*
 * Adapted from the AndroidLiquidGlass catalog application's LiquidToggle.
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Modified for Murexide with Material fallback, disabled state, and accessibility.
 */
package com.juhao.murexide.ui.theme.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.LocalLiquidGlassBackdrop
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
fun LiquidGlassToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backdrop = LocalLiquidGlassBackdrop.current
    val blurScale = LocalLiquidGlassBlur.current
    if (!LocalLiquidGlassEnabled.current || backdrop == null) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            thumbContent = {
                if (checked) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
        )
        return
    }

    LiquidToggleContent(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        backdrop = backdrop,
        blurScale = blurScale,
        modifier = modifier,
    )
}

@Composable
private fun LiquidToggleContent(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    backdrop: Backdrop,
    blurScale: Float,
    modifier: Modifier,
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val boxColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
    val trackColor = MaterialTheme.colorScheme.secondary
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val dragAnimation = remember(animationScope, dragWidth, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = { didDrag = false },
            onDragStopped = {
                val onChange = currentOnCheckedChange ?: return@DampedDragAnimation
                fraction = if (didDrag) {
                    if (targetValue >= 0.5f) 1f else 0f
                } else {
                    if (currentChecked) 0f else 1f
                }
                didDrag = false
                animateToValue(fraction)
                onChange(fraction == 1f)
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f
                val delta = dragAmount.x / dragWidth
                fraction = if (isLtr) {
                    (targetValue + delta).fastCoerceIn(0f, 1f)
                } else {
                    (targetValue - delta).fastCoerceIn(0f, 1f)
                }
                dragToValue(fraction)
            },
        )
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        fraction = target
        if (!dragAnimation.isDragging && dragAnimation.targetValue != target) {
            dragAnimation.animateToValue(target)
        }
    }

    val interactive = enabled && onCheckedChange != null
    val trackBackdrop = rememberLayerBackdrop()
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .size(width = 48.dp, height = 48.dp)
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
                if (interactive) {
                    onClick {
                        currentOnCheckedChange?.invoke(!currentChecked)
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    drawRect(lerp(trackColor, accentColor, dragAnimation.value))
                }
                .size(width = 48.dp, height = 28.dp),
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) {
                        lerp(padding, padding + dragWidth, dragAnimation.value)
                    } else {
                        lerp(-padding, -(padding + dragWidth), dragAnimation.value)
                    }
                }
                .then(if (interactive) dragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dragAnimation.pressProgress
                            scale(
                                lerp(2f / 3f, 0.75f, progress),
                                lerp(0f, 0.75f, progress),
                            ) {
                                drawBackdrop()
                            }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        blur(4.dp.toPx() * blurScale * (1f - progress))
                        lens(
                            5.dp.toPx() * progress,
                            10.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = if (isLightTheme) {
                        {
                            val progress = dragAnimation.pressProgress
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress,
                            )
                        }
                    } else {
                        null
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(radius = 4.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(boxColor.copy(alpha = 1f - dragAnimation.pressProgress))
                    },
                )
                .size(24.dp),
        )
    }
}

private fun Color.luminance(): Float =
    (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
