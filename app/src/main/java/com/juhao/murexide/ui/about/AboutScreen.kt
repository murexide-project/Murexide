package com.juhao.murexide.ui.about

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.R
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.utils.getAppVersionInfo
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val LogoDragLimit = 12.dp
private val LogoDragDamping = 0.3f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val versionInfo = remember { context.getAppVersionInfo() }

    var logoBursting by remember { mutableStateOf(false) }
    val logoScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }

    val dragLimitPx = with(LocalDensity.current) { LogoDragLimit.toPx() }

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val settleX = remember { Animatable(0f) }
    val settleY = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    val offsetX = if (dragging) dragX else settleX.value
    val offsetY = if (dragging) dragY else settleY.value

    fun triggerBurst() {
        if (logoBursting) return
        logoBursting = true
        scope.launch {
            logoScale.animateTo(0.82f, tween(90))
            logoScale.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, tween(520, easing = LinearEasing))
            logoBursting = false
        }
    }

    fun settle() {
        val startX = dragX
        val startY = dragY
        dragging = false
        scope.launch {
            launch {
                settleX.snapTo(startX)
                settleX.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                settleY.snapTo(startY)
                settleY.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    StyledScaffold(
        title = { Text("关于") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { triggerBurst() })
                            }
                            .pointerInput(dragLimitPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = true
                                        dragX = settleX.value
                                        dragY = settleY.value
                                    },
                                    onDragEnd = { settle() },
                                    onDragCancel = { settle() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        var nx = dragX + dragAmount.x * LogoDragDamping
                                        var ny = dragY + dragAmount.y * LogoDragDamping
                                        val dist = hypot(nx, ny)
                                        if (dist > dragLimitPx) {
                                            val f = dragLimitPx / dist
                                            nx *= f
                                            ny *= f
                                        }
                                        dragX = nx
                                        dragY = ny
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val transition = rememberInfiniteTransition(label = "logo_animation")
                        val rotation by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 36000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation"
                        )
                        val surfaceAlpha by transition.animateFloat(
                            initialValue = 0.55f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "surface_alpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .graphicsLayer {
                                    scaleX = logoScale.value
                                    scaleY = logoScale.value
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    rotationZ = rotation
                                    scaleX = logoScale.value
                                    scaleY = logoScale.value
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = surfaceAlpha),
                                    shape = MaterialShapes.Cookie12Sided.toShape()
                                )
                        ) {
                            Box(Modifier.fillMaxSize())
                        }

                        Icon(
                            imageVector = AppFilledIcons.ChatBubble,
                            contentDescription = null,
                            modifier = Modifier
                                .requiredSize(38.dp)
                                .graphicsLayer {
                                    scaleX = logoScale.value
                                    scaleY = logoScale.value
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (burstProgress.value > 0f) {
                            BurstOverlay(
                                progress = burstProgress.value,
                                color = MaterialTheme.colorScheme.primary,
                                offsetX = offsetX,
                                offsetY = offsetY
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = versionInfo.versionName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "为云湖而生的原生体验",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "versionCode ${versionInfo.versionCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsGroup(title = "贡献者") {
                SettingsItem(
                    icon = AppIcons.Person,
                    title = "贡献者",
                    subtitle = "查看对此项目做出贡献的用户",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/murexide-project/Murexide/graphs/contributors".toUri()
                            )
                        )
                    }
                )

                SettingsItemCell(
                    icon = AppIcons.Code,
                    title = "编程语言",
                    subtitle = "Kotlin, Jetpack Compose",
                    onClick = { }
                )
            }

            SettingsGroup(title = "许可证") {
                SettingsItemCell(
                    icon = AppIcons.Book,
                    title = "许可证",
                    subtitle = "GNU General Public License v3.0",
                    onClick = { }
                )
            }

            SettingsGroup(title = "相关链接") {
                SettingsItem(
                    icon = AppIcons.Code,
                    title = "GitHub",
                    subtitle = "访问项目源码",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/murexide-project/Murexide".toUri()
                            )
                        )
                    }
                )
                SettingsItem(
                    icon = AppIcons.BugReport,
                    title = "问题反馈",
                    subtitle = "提交 Issue",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/murexide-project/Murexide/issues".toUri()
                            )
                        )
                    }
                )
                SettingsItem(
                    icon = AppIcons.People,
                    title = "交流群",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "yunhu://chat-add?id=476660997&type=group".toUri()
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BurstOverlay(
    progress: Float,
    color: Color,
    offsetX: Float,
    offsetY: Float
) {
    val particleCount = 10
    Canvas(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        if (progress <= 0f || progress >= 1f) return@Canvas
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val alpha = (1f - progress).coerceIn(0f, 1f)
        val radius = 3.dp.toPx() * (1f - progress * 0.5f)
        val maxDistance = 64.dp.toPx() * progress
        for (i in 0 until particleCount) {
            val angle = (i.toFloat() / particleCount) * 2f * Math.PI.toFloat()
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(
                    centerX + cos(angle) * maxDistance,
                    centerY + sin(angle) * maxDistance
                )
            )
        }
    }
}