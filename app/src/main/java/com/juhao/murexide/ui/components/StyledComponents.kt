package com.juhao.murexide.ui.components

import com.juhao.murexide.ui.icons.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.juhao.murexide.ui.theme.liquidglass.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun StyledButton(
    onClick: () -> Unit,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color(0xFF0088FF),
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    if (backdrop == null) {
        Button(
            content = content,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        val animationScope = rememberCoroutineScope()

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope
            )
        }

        val interactive = enabled && isInteractive

        val effectiveTint = if (enabled) tint else Color.Gray

        Row(
            modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = if (interactive) {
                        {
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
                        }
                    } else {
                        null
                    },
                    onDrawSurface = {
                        if (effectiveTint.isSpecified) {
                            drawRect(effectiveTint, blendMode = BlendMode.Hue)
                            drawRect(effectiveTint.copy(alpha = 0.75f))
                        }
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    }
                )
                .clickable(
                    interactionSource = null,
                    indication = if (interactive) null else LocalIndication.current,
                    role = Role.Button,
                    enabled = enabled,
                    onClick = onClick
                )
                .then(
                    if (interactive) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .height(48f.dp)
                .padding(horizontal = 16f.dp),
            horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
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
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTopBar(
    title: @Composable (() -> Unit),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable (RowScope.() -> Unit) = {}
) {
    LargeTopAppBar(
        title = title,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions
    )
}

@Composable
fun ExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MenuDefaults.standaloneGroupShape,
        content = content
    )
}
