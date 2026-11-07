package com.juhao.murexide.ui.about

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.juhao.murexide.R
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.utils.getAppVersionInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transition = rememberInfiniteTransition(label = "icon_shape_rotate")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 36000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )
                
                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                rotationZ = rotation
                            },
                        shape = MaterialShapes.Cookie12Sided.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(Modifier.fillMaxSize())
                    }

                    Icon(
                        imageVector = AppFilledIcons.ChatBubble,
                        contentDescription = null,
                        modifier = Modifier.requiredSize(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    AssistChip(
                        label = {
                            Text(
                                context.getAppVersionInfo().versionName,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        onClick = { }
                    )
                    Text(
                        text = "一个云湖第三方客户端",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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