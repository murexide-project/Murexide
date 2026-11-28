package com.juhao.murexide.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.juhao.murexide.ui.theme.liquidglass.glassControl
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledScaffold(
    title: @Composable (() -> Unit),
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    snackbarHost: (@Composable () -> Unit)? = null,
    extraContent: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val floatingEnabled by remember { SettingsStorage(context.applicationContext) }
        .floatingTopBarFlow
        .collectAsState(initial = true)
    val hazeState = rememberHazeState()
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidBackdrop = if (liquidGlassEnabled) rememberLayerBackdrop() else null
    val useGlassBar = liquidGlassEnabled && liquidBackdrop != null
    val controlSize = 48.dp
    val buttonShape = CircleShape
    val surfaceColor = MaterialTheme.colorScheme.surface
    val supportsBlur = Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2
    val blurStyle = remember(surfaceColor) {
        HazeBlurStyle {
            blurRadius(16.dp)
            backgroundColor(surfaceColor)
            colorEffects(listOf(HazeColorEffect.tint(surfaceColor)))
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
                surfaceColor.copy(alpha = 0.5f),
                Color.Transparent
            )
        )
    }
    val titleText: @Composable () -> Unit = {
        if (subtitle == null) {
            title()
        } else {
            Column {
                title()
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall
                ) {
                    subtitle()
                }
            }
        }
    }
    val appBarTitleContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(controlSize)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.titleLarge
                ) {
                    titleText()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (floatingEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            surfaceColor.copy(alpha = 0.8f),
                                            surfaceColor.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .safeDrawingPadding()
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 8.dp)
                                .zIndex(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (navigationIcon != null) {
                                Box(
                                    modifier = Modifier
                                        .size(controlSize)
                                        .glassControl(
                                            shape = buttonShape,
                                            liquidBackdrop = liquidBackdrop,
                                            hazeState = hazeState,
                                            useGlassBar = useGlassBar
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    navigationIcon()
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(controlSize),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Spacer(Modifier.width(4.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    CompositionLocalProvider(
                                        LocalTextStyle provides MaterialTheme.typography.titleLarge
                                    ) {
                                        titleText()
                                    }
                                }
                            }
                            if (actions != null) {
                                Spacer(Modifier.width(4.dp))
                                Row(
                                    modifier = Modifier
                                        .height(controlSize)
                                        .glassControl(
                                            shape = buttonShape,
                                            liquidBackdrop = liquidBackdrop,
                                            hazeState = hazeState,
                                            useGlassBar = useGlassBar
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = actions
                                )
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = appBarTitleContent,
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
                        modifier = if (supportsBlur) {
                            Modifier.hazeBlur(
                                input = HazeInput.Sources(hazeState),
                                style = blurStyle
                            )
                        } else {
                            Modifier.background(fallbackBrush)
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
                extraContent()
            }
        },
        bottomBar = bottomBar,
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
                .then(
                    if (liquidBackdrop != null) {
                        Modifier
                            .layerBackdrop(liquidBackdrop)
                            .hazeSource(state = hazeState)
                    } else {
                        Modifier.hazeSource(state = hazeState)
                    }
                )
        ) {
            content(padding)
        }
    }
}
