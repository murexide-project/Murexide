package com.juhao.murexide.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juhao.murexide.ui.theme.UiState

@Composable
fun CapsuleTabBar(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val selectedIndex = selectedTabIndex.coerceIn(tabs.indices)
    val selectedIndicatorColor = if (UiState.themeColor.value == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
            ) {
                tabs.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val backgroundColor by animateColorAsState(
                        targetValue = if (selected) selectedIndicatorColor else Color.Transparent,
                        animationSpec = tween(durationMillis = 180),
                        label = "capsule tab background"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 180),
                        label = "capsule tab text color"
                    )
    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(backgroundColor)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onTabSelected(index) }
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}