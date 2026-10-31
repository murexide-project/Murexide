package com.juhao.murexide.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: @Composable (() -> Unit),
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    containerColor: Color = Color.Transparent,
) {
    MediumFlexibleTopAppBar(
        title = title,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledScaffold(
    title: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable (RowScope.() -> Unit) = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    topBarScrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    snackbarHost: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = rememberHazeState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val supportsBlur = Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2

    val blurStyle = remember(surfaceColor) {
        HazeBlurStyle {
            blurRadius(16.dp)
            backgroundColor(surfaceColor)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(surfaceColor)
                )
            )
            noiseFactor(0.02f)
            progressive(
                HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
            )
        }
    }

    val fallbackBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceColor,
                surfaceColor.copy(alpha = 0.8f),
                surfaceColor.copy(alpha = 0.6f),
                Color.Transparent
            )
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (supportsBlur) {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = blurStyle
                            )
                        } else {
                            Modifier.background(fallbackBrush)
                        }
                    )
            ) {
                TopBar(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = topBarScrollBehavior
                )
            }
        },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        snackbarHost = {
            if (snackbarHost != null) {
                snackbarHost()
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
                .hazeSource(state = hazeState)
        ) {
            content(padding)
        }
    }
}