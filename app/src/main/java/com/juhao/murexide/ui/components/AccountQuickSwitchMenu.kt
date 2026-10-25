package com.juhao.murexide.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.juhao.murexide.MainActivity
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.theme.ProvideLiquidGlassContentColor
import com.juhao.murexide.ui.theme.liquidglass.glassControl
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.launch

@Composable
fun AccountQuickSwitchMenu(
    expanded: Boolean,
    accounts: List<UserAccount>,
    hazeState: HazeState,
    currentAccountId: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountStorage = AccountStorage.getInstance(context.applicationContext)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeMaterials.thin().then {
                blurRadius(28.dp)
                noiseFactor(0f)
            }
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "添加账号",
                    style = MaterialTheme.typography.titleSmall
                )
            },
            onClick = {
                onDismissRequest()
                LoginActivity.start(context, isAddMode = true)
            },
            leadingIcon = {
                Icon(AppIcons.Add, contentDescription = null)
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .padding(vertical = 4.dp)
                .verticalScroll(rememberScrollState())
        ) {
            accounts.forEach { account ->
                val isCurrentAccount = account.id == currentAccountId
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuickSwitchAvatar(
                                account = account,
                                isCurrentAccount = isCurrentAccount
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ID: ${account.id}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    onClick = {
                        onDismissRequest()
                        if (!isCurrentAccount) {
                            scope.launch {
                                accountStorage.switchAccount(account.id)
                                context.startActivity(
                                    Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickSwitchAvatar(account: UserAccount, isCurrentAccount: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (isCurrentAccount) {
                    Modifier
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (account.avatar.isNotBlank()) {
            Avatar(url = account.avatar, size = if (isCurrentAccount) 36.dp else 40.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(if (isCurrentAccount) 36.dp else 40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountQuickSwitchGlassMenu(
    expanded: Boolean,
    accounts: List<UserAccount>,
    currentAccountId: String,
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cardAlignment: Alignment = Alignment.BottomEnd,
    cardPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountStorage = AccountStorage.getInstance(context.applicationContext)
    val hazeState = remember { HazeState() }
    val shape = RoundedCornerShape(28.dp)

    BackHandler(enabled = expanded, onBack = onDismissRequest)

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(120)),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
            )

            Column(
                modifier = Modifier
                    .align(cardAlignment)
                    .padding(cardPadding)
                    .width(IntrinsicSize.Max)
                    .widthIn(max = 320.dp)
                    .glassControl(
                        shape = shape,
                        liquidBackdrop = backdrop,
                        hazeState = hazeState,
                        useGlassBar = backdrop != null,
                        isInteractive = true,
                    )
                    .padding(8.dp),
            ) {
                val glassColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
                ProvideLiquidGlassContentColor(
                    glassColor = glassColor,
                    preferredColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    GlassMenuItemRow(
                        leadingIcon = { Icon(AppIcons.Add, contentDescription = null) },
                        text = "添加账号",
                        onClick = {
                            onDismissRequest()
                            LoginActivity.start(context, isAddMode = true)
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .padding(vertical = 4.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        accounts.forEach { account ->
                            val isCurrentAccount = account.id == currentAccountId
                            GlassAccountRow(
                                account = account,
                                isCurrentAccount = isCurrentAccount,
                                onClick = {
                                    onDismissRequest()
                                    if (!isCurrentAccount) {
                                        scope.launch {
                                            accountStorage.switchAccount(account.id)
                                            context.startActivity(
                                                Intent(context, MainActivity::class.java).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                }
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassMenuItemRow(
    leadingIcon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon()
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun GlassAccountRow(
    account: UserAccount,
    isCurrentAccount: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickSwitchAvatar(account = account, isCurrentAccount = isCurrentAccount)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.wrapContentWidth()) {
            Text(
                text = account.username,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ID: ${account.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}