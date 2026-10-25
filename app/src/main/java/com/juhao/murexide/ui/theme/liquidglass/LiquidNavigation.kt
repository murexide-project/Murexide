/*
 * Adapted from the AndroidLiquidGlass catalog application's LiquidBottomTabs.
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * Modified for Murexide with five tabs, long-click support, and a vertical rail.
 */
package com.juhao.murexide.ui.theme.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.liquidGlassContentColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val LocalLiquidNavigationScale = compositionLocalOf { { 1f } }

internal fun snapNavigationIndex(value: Float, tabsCount: Int): Int {
    require(tabsCount > 0) { "tabsCount must be positive" }
    return value.roundToInt().coerceIn(0, tabsCount - 1)
}

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    onTabLongClick: ((Int) -> Unit)? = null,
    onTabLongClickLabel: ((Int) -> String?)? = null,
    content: @Composable (index: Int, selected: Boolean, overlayPass: Boolean) -> Unit,
) {
    val blurScale = LocalLiquidGlassBlur.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.6f)
    }
    val navigationContentColor = liquidGlassContentColor(
        preferredColor = MaterialTheme.colorScheme.onSurfaceVariant,
        glassColor = containerColor,
        backgroundColor = MaterialTheme.colorScheme.background,
    )
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth.coerceAtLeast(1))
                    .fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex) }
        val dragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = snapNavigationIndex(targetValue, tabsCount)
                    currentIndex = target
                    animateToValue(target.toFloat())
                    onTabSelected(target)
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabIndex) {
            if (selectedTabIndex != currentIndex) {
                currentIndex = selectedTabIndex
                dragAnimation.animateToValue(selectedTabIndex.toFloat())
            }
        }
        val interactiveHighlight = remember(animationScope, isLtr, tabWidth) {
            InteractiveHighlight(animationScope) { size, _ ->
                Offset(
                    if (isLtr) (dragAnimation.value + 0.5f) * tabWidth + panelOffset
                    else size.width - (dragAnimation.value + 0.5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(1.dp.toPx() * blurScale)
                        lens(24.dp.toPx(), 32.dp.toPx())
                    },
                    layerBlock = {
                        val scale = lerp(
                            1f,
                            1f + 16.dp.toPx() / size.width,
                            dragAnimation.pressProgress,
                        )
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabsCount) { index ->
                LiquidBottomTab(
                    selected = currentIndex == index,
                    contentColor = navigationContentColor,
                    onClick = {
                        currentIndex = index
                        dragAnimation.animateToValue(index.toFloat())
                        onTabSelected(index)
                    },
                    onLongClick = onTabLongClick?.let { callback -> { callback(index) } },
                    onLongClickLabel = onTabLongClickLabel?.invoke(index),
                ) {
                    Box(Modifier.alpha(0f)) {
                        content(index, currentIndex == index, true)
                    }
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 4.dp)
                .height(56.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(tabsCount) { index ->
                LiquidBottomTab(
                    selected = currentIndex == index,
                    contentColor = navigationContentColor,
                    onClick = {},
                    interactive = false,
                ) {
                    content(index, currentIndex == index, false)
                }
            }
        }

        CompositionLocalProvider(LocalLiquidNavigationScale provides {
            lerp(1f, 1.2f, dragAnimation.pressProgress)
        }) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dragAnimation.pressProgress
                            vibrancy()
                            blur(5.dp.toPx() * blurScale)
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = dragAnimation.pressProgress) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(tabsCount) { index ->
                    LiquidBottomTab(
                        selected = currentIndex == index,
                        contentColor = navigationContentColor,
                        onClick = {},
                        interactive = false,
                    ) {
                        content(index, currentIndex == index, true)
                    }
                }
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        dragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = dragAnimation.pressProgress) },
                    shadow = { Shadow(alpha = dragAnimation.pressProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * dragAnimation.pressProgress,
                            alpha = dragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.1f)
                            else Color.White.copy(alpha = 0.1f),
                            alpha = 1f - dragAnimation.pressProgress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * dragAnimation.pressProgress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LiquidBottomTab(
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    interactive: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scale = LocalLiquidNavigationScale.current
    Column(
        Modifier
            .then(if (interactive) Modifier.clip(Capsule()) else Modifier)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onLongClickLabel = onLongClickLabel,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (interactive) {
                    Modifier.semantics {
                        this.selected = selected
                        role = Role.Tab
                    }
                } else {
                    Modifier
                },
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

private fun Color.luminance(): Float =
    (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)