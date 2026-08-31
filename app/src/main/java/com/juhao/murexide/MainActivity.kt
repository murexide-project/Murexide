package com.juhao.murexide

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AppFilledIcons
import com.juhao.murexide.ui.icons.AnimatedNavigationSymbol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.contact.ContactListScreen
import com.juhao.murexide.ui.conversation.ConversationListScreen
import com.juhao.murexide.ui.conversation.CreationActivity
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.mine.MineScreen
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.ui.theme.UiState
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.data.ConversationItem
import com.juhao.murexide.data.ConversationKey
import com.juhao.murexide.data.unreadTotal
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.ui.chat.ChatScreen
import com.juhao.murexide.ui.chat.ChatViewModel
import com.juhao.murexide.ui.components.UnreadCountBadge
import com.juhao.murexide.ui.components.AccountQuickSwitchMenu
import com.juhao.murexide.ui.components.AccountQuickSwitchGlassMenu
import com.juhao.murexide.ui.community.CommunityScreen
import com.juhao.murexide.ui.settings.SettingsActivity
import com.juhao.murexide.utils.getAppVersionInfo
import androidx.compose.foundation.combinedClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.liquidGlass
import com.juhao.murexide.ui.theme.liquidGlassHighlightEnabled
import com.juhao.murexide.ui.theme.liquidGlassContentColor
import com.juhao.murexide.ui.theme.liquidglass.LiquidBottomTabs

private data class NavItem(
    val route: String,
    val title: String,
    val outlineIcon: ImageVector,
    val filledIcon: ImageVector,
)

private val navItems = listOf(
    NavItem("conversations", "消息", AppIcons.ChatBubble, AppFilledIcons.ChatBubble),
    NavItem("contacts", "通讯录", AppIcons.Contacts, AppFilledIcons.Contacts),
    NavItem("community", "社区", AppIcons.Group, AppFilledIcons.Group),
    NavItem("discover", "发现", AppIcons.Explore, AppFilledIcons.Explore),
    NavItem("mine", "我的", AppIcons.Person, AppFilledIcons.Person),
)

private const val TAB_TRANSITION_DURATION_MILLIS = 300

@Composable
private fun HomeNavigationIcon(
    item: NavItem,
    selected: Boolean,
    unreadCount: Int
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val inheritedGlassContentColor = LocalContentColor.current
    val icon: @Composable () -> Unit = {
        AnimatedNavigationSymbol(
            outlineIcon = item.outlineIcon,
            filledIcon = item.filledIcon,
            selected = selected,
            contentDescription = item.title,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else if (liquidGlassEnabled) {
                inheritedGlassContentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    when (item.route) {
        "conversations" -> {
            BadgedBox(badge = { UnreadCountBadge(unreadCount) }) {
                icon()
            }
        }

        else -> icon()
    }
}

@Composable
private fun HomeNavigationLabel(
    item: NavItem,
    selected: Boolean,
    compact: Boolean = false,
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val inheritedGlassContentColor = LocalContentColor.current
    AnimatedVisibility(
        visible = compact || selected,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            text = item.title,
            style = if (compact) {
                MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
            } else {
                LocalTextStyle.current
            },
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else if (liquidGlassEnabled) {
                inheritedGlassContentColor
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
private fun TelegramFloatingNavigationBar(
    currentRoute: String?,
    unreadCount: Int,
    showAccountMenu: Boolean,
    accounts: List<UserAccount>,
    currentAccountId: String,
    selectedIndicatorColor: Color,
    hazeState: HazeState,
    liquidBackdrop: Backdrop?,
    onNavigate: (String) -> Unit,
    onShowAccountMenu: () -> Unit,
    onDismissAccountMenu: () -> Unit,
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val showGlassHighlight = liquidGlassHighlightEnabled()
    val shape = RoundedCornerShape(32.dp)
    val navigationBarHeight = 64.dp
    val indicatorInset = 5.dp
    val indicatorHeight = navigationBarHeight - indicatorInset * 2f
    val indicatorShape = RoundedCornerShape(26.dp)
    val navigationSurfaceColor = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = if (liquidGlassEnabled && liquidBackdrop != null) 0.56f else 0.72f
    )
    val navigationContentColor = liquidGlassContentColor(
        preferredColor = MaterialTheme.colorScheme.onSurfaceVariant,
        glassColor = navigationSurfaceColor,
        backgroundColor = MaterialTheme.colorScheme.background,
    )

    val containerModifier = if (liquidGlassEnabled && liquidBackdrop != null) {
        Modifier.liquidGlass(
            enabled = true,
            backdrop = liquidBackdrop,
            shape = shape,
            surfaceColor = navigationSurfaceColor,
            blurRadius = 16.dp * liquidGlassBlur,
            lensHeight = 12.dp,
            lensAmount = 22.dp,
            showHighlight = showGlassHighlight
        )
    } else {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(
                    containerColor = navigationSurfaceColor
                ).copy(
                    blurRadius = 28.dp,
                    noiseFactor = 0f
                ),
                block = null
            )
    }

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 10.dp)
            .shadow(elevation = 2.dp, shape = shape, clip = false)
            .then(containerModifier)
            .height(navigationBarHeight),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val contentInset = 12.dp
            val itemWidth = (maxWidth - contentInset * 2f) / navItems.size
            val selectionWidth = itemWidth + (contentInset - indicatorInset) * 2f

            navItems.forEachIndexed { index, item ->
                val selected = currentRoute == item.route
                val indicatorProgress by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (selected) 220 else 160,
                        easing = if (selected) {
                            LinearOutSlowInEasing
                        } else {
                            FastOutLinearInEasing
                        }
                    ),
                    label = "${item.route} bottom navigation indicator"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = itemWidth * index.toFloat() + indicatorInset)
                        .width(selectionWidth)
                        .height(indicatorHeight)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = indicatorProgress
                            scaleY = indicatorProgress
                        }
                        .then(
                            if (liquidGlassEnabled && liquidBackdrop != null) {
                                Modifier.liquidGlass(
                                    enabled = true,
                                    backdrop = liquidBackdrop,
                                    shape = indicatorShape,
                                    surfaceColor = selectedIndicatorColor.copy(alpha = 0.48f),
                                    blurRadius = 6.dp * liquidGlassBlur,
                                    lensHeight = 6.dp,
                                    lensAmount = 10.dp,
                                    showHighlight = showGlassHighlight
                                )
                            } else {
                                Modifier
                                    .clip(indicatorShape)
                                    .background(selectedIndicatorColor)
                            }
                        )
                )
            }

            CompositionLocalProvider(LocalContentColor provides navigationContentColor) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = contentInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEach { item ->
                        val selected = currentRoute == item.route
                        val interactionSource = remember(item.route) { MutableInteractionSource() }
                        val interactionModifier = if (item.route == "mine") {
                            Modifier.combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onNavigate(item.route) },
                                onLongClick = onShowAccountMenu,
                                onLongClickLabel = "打开账号菜单"
                            )
                        } else {
                            Modifier.selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onNavigate(item.route) },
                                role = Role.Tab
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(interactionModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HomeNavigationIcon(
                                    item = item,
                                    selected = selected,
                                    unreadCount = unreadCount
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                HomeNavigationLabel(item, selected, compact = true)
                            }
                            if (item.route == "mine") {
                                AccountQuickSwitchMenu(
                                    expanded = showAccountMenu,
                                    accounts = accounts,
                                    currentAccountId = currentAccountId,
                                    onDismissRequest = onDismissAccountMenu
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideDirection():
        AnimatedContentTransitionScope.SlideDirection {
    val initialIndex = navItems.indexOfFirst { it.route == initialState.destination.route }
    val targetIndex = navItems.indexOfFirst { it.route == targetState.destination.route }
    return if (targetIndex > initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val accountStorage = AccountStorage.getInstance(this)

        lifecycleScope.launch {
            val account = accountStorage.getCurrentUserInfo()
            if (account == null || account.token.isEmpty()) {
                LoginActivity.start(this@MainActivity)
                finish()
                return@launch
            }

            // Main content and its ViewModels may be created before the Application-level
            // DataStore collector emits. Bind the already validated account synchronously.
            LocalCache.setActiveAccount(account.id)

            setContent {
                MurexideTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        MainScreen(account)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MainScreen(account: UserAccount) {
    val themeColor by UiState.themeColor
    val token = account.token
    val navController = rememberNavController()
    val hazeState = remember { HazeState() }
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidBackdrop = if (liquidGlassEnabled) rememberLayerBackdrop() else null
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val settingsStorage = remember { SettingsStorage(context) }
    val bigScreenEnabled by settingsStorage.bigScreenFlow.collectAsState(initial = true)
    val floatBottomBarEnabled by settingsStorage.isFloatBottomBarEnabledFlow.collectAsState(initial = true)

    val isBigScreen = LocalConfiguration.current.screenWidthDp >= 600

    var currentConversation by remember { mutableStateOf<ConversationItem?>(null) }
    val cachedConversations by LocalCache.observeConversations(account.id)
        .collectAsState(initial = emptyList())
    val unreadCount = cachedConversations.unreadTotal(
        currentConversation?.takeIf { isBigScreen && bigScreenEnabled }
            ?.let { ConversationKey(it.chatId, it.chatType) }
    )

    var isContactNewMessagesVisible by remember { mutableStateOf(false) }

    var showAccountMenu by remember { mutableStateOf(false) }
    val accountStorage = remember(context.applicationContext) {
        AccountStorage.getInstance(context.applicationContext)
    }
    val loggedInAccounts by accountStorage.userAccountsFlow.collectAsState(initial = emptyList())

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val useNavigationRail = bigScreenEnabled && isBigScreen
    val hideMobileNavigation = currentRoute == "contacts" && isContactNewMessagesVisible
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    NavigationSuiteScaffold(
        layoutType = if (useNavigationRail) {
            NavigationSuiteType.NavigationRail
        } else if (!floatBottomBarEnabled) {
            NavigationSuiteType.NavigationBar
        } else {
            NavigationSuiteType.None
        },
        navigationSuiteColors = if (themeColor == "WHITE" || liquidGlassEnabled) {
            NavigationSuiteDefaults.colors(
                navigationBarContainerColor = if (liquidGlassEnabled) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                navigationRailContainerColor = if (liquidGlassEnabled) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            )
        } else {
            NavigationSuiteDefaults.colors()
        },
        navigationSuiteItems = {
            navItems.forEach { item ->
                val selected = currentRoute == item.route
                item(
                    icon = {
                        when (item.route) {
                            "mine" -> {
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { navigateTo(item.route) },
                                        onLongClick = { showAccountMenu = true }
                                    )
                                ) {
                                    AnimatedNavigationSymbol(
                                        outlineIcon = item.outlineIcon,
                                        filledIcon = item.filledIcon,
                                        selected = selected,
                                        contentDescription = item.title,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    AccountQuickSwitchMenu(
                                        expanded = showAccountMenu && !liquidGlassEnabled,
                                        accounts = loggedInAccounts,
                                        currentAccountId = account.id,
                                        onDismissRequest = { showAccountMenu = false }
                                    )
                                }
                            }

                            "conversations" -> {
                                BadgedBox(badge = { UnreadCountBadge(unreadCount) }) {
                                    AnimatedNavigationSymbol(
                                        outlineIcon = item.outlineIcon,
                                        filledIcon = item.filledIcon,
                                        selected = selected,
                                        contentDescription = item.title,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            else -> {
                                AnimatedNavigationSymbol(
                                    outlineIcon = item.outlineIcon,
                                    filledIcon = item.filledIcon,
                                    selected = selected,
                                    contentDescription = item.title,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    label = { HomeNavigationLabel(item, selected) },
                    selected = selected,
                    onClick = { navigateTo(item.route) }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!useNavigationRail && !hideMobileNavigation && floatBottomBarEnabled) {
                    if (liquidGlassEnabled && liquidBackdrop != null) {
                        LiquidBottomTabs(
                            selectedTabIndex = navItems.indexOfFirst { it.route == currentRoute }
                                .coerceAtLeast(0),
                            onTabSelected = { index -> navigateTo(navItems[index].route) },
                            backdrop = liquidBackdrop,
                            tabsCount = navItems.size,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 10.dp),
                            onTabLongClick = { index ->
                                if (navItems[index].route == "mine") showAccountMenu = true
                            },
                            onTabLongClickLabel = { index ->
                                if (navItems[index].route == "mine") "打开账号菜单" else null
                            },
                        ) { index, selected, overlayPass ->
                            val item = navItems[index]
                            if (item.route == "mine") {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(
                                            2.dp,
                                            Alignment.CenterVertically
                                        )
                                    ) {
                                        HomeNavigationIcon(
                                            item = item,
                                            selected = selected,
                                            unreadCount = unreadCount
                                        )
                                        HomeNavigationLabel(item, selected, compact = true)
                                    }
                                    if (!overlayPass) {
                                        AccountQuickSwitchMenu(
                                            expanded = showAccountMenu && !liquidGlassEnabled,
                                            accounts = loggedInAccounts,
                                            currentAccountId = account.id,
                                            onDismissRequest = { showAccountMenu = false }
                                        )
                                    }
                                }
                            } else {
                                HomeNavigationIcon(
                                    item = item,
                                    selected = selected,
                                    unreadCount = unreadCount
                                )
                                HomeNavigationLabel(item, selected, compact = true)
                            }
                        }
                    } else {
                        TelegramFloatingNavigationBar(
                            currentRoute = currentRoute,
                            unreadCount = unreadCount,
                            showAccountMenu = showAccountMenu,
                            accounts = loggedInAccounts,
                            currentAccountId = account.id,
                            selectedIndicatorColor = if (themeColor == "WHITE") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            hazeState = hazeState,
                            liquidBackdrop = liquidBackdrop,
                            onNavigate = navigateTo,
                            onShowAccountMenu = { showAccountMenu = true },
                            onDismissAccountMenu = { showAccountMenu = false },
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "conversations",
                    modifier = Modifier
                        .then(
                            if (useNavigationRail && liquidGlassEnabled) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .hazeSource(hazeState)
                        .then(
                            if (liquidBackdrop != null) {
                                Modifier.layerBackdrop(liquidBackdrop)
                            } else {
                                Modifier
                            }
                        ),
                    enterTransition = {
                        slideIntoContainer(
                            towards = tabSlideDirection(),
                            animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = tabSlideDirection(),
                            animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                        )
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = tabSlideDirection(),
                            animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                        )
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = tabSlideDirection(),
                            animationSpec = tween(TAB_TRANSITION_DURATION_MILLIS)
                        )
                    }
                ) {
                    composable("conversations") {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ConversationListScreen(
                                modifier = Modifier
                                    .weight(if (isBigScreen && bigScreenEnabled) 0.4f else 1f)
                                    .fillMaxHeight(),
                                token = token,
                                accountId = account.id,
                                accountAvatar = account.avatar,
                                innerPadding = innerPadding,
                                bigScreenMode = isBigScreen && bigScreenEnabled,
                                currentConversation = if (isBigScreen && bigScreenEnabled) currentConversation else null,
                                onConversationClick = { conversation ->
                                    if (isBigScreen && bigScreenEnabled) {
                                        currentConversation = conversation
                                    } else {
                                        ChatActivity.start(
                                            context = context,
                                            chatId = conversation.chatId,
                                            chatType = conversation.chatType,
                                            chatName = conversation.displayName,
                                            chatAvatar = conversation.avatarUrl,
                                        )
                                    }
                                },
                                onCreateClick = { kind -> CreationActivity.start(context, kind) }
                            )

                            if (isBigScreen && bigScreenEnabled) {
                                if (currentConversation != null) {
                                    BackHandler {
                                        currentConversation = null
                                    }
                                    key(currentConversation!!.chatId) {
                                        ChatScreen(
                                            modifier = Modifier
                                                .weight(0.7f)
                                                .fillMaxHeight(),
                                            chatAvatar = currentConversation!!.avatarUrl,
                                            chatName = currentConversation!!.name,
                                            chatType = currentConversation!!.chatType,
                                            chatId = currentConversation!!.chatId,
                                            onBackClick = { currentConversation = null },
                                            onOpenConversation = { target ->
                                                currentConversation = target.toConversationItem()
                                            },
                                            bigScreenMode = true,
                                            backUnreadCount = unreadCount,
                                            viewModel = viewModel(
                                                key = "chat_" + currentConversation!!.chatId,
                                                factory = object : ViewModelProvider.Factory {
                                                    @Suppress("UNCHECKED_CAST")
                                                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                                        return ChatViewModel(
                                                            token = token,
                                                            chatId = currentConversation!!.chatId,
                                                            chatType = currentConversation!!.chatType,
                                                            currentUserId = account.id,
                                                            currentUserName = account.username,
                                                            currentUserAvatar = account.avatar
                                                        ) as T
                                                    }
                                                }
                                            )
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.ChatBubble,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .alpha(0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    composable("contacts") {
                        ContactListScreen(
                            token = token,
                            innerPadding = innerPadding,
                            onNewMessagesVisibilityChanged = { isVisible ->
                                isContactNewMessagesVisible = isVisible
                            },
                            onContactClick = { contact ->
                                ChatActivity.start(
                                    context = context,
                                    chatId = contact.chatId,
                                    chatType = contact.chatType,
                                    chatName = contact.remark ?: contact.name,
                                    chatAvatar = contact.avatarUrl
                                )
                            }
                        )
                    }

                    composable("community") {
                        CommunityScreen(
                            token = token,
                            innerPadding = innerPadding
                        )
                    }

                    composable("discover") {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("发现") },
                                    actions = {
                                        IconButton(onClick = { /* TODO: 搜索 */ }) {
                                            Icon(AppIcons.Search, contentDescription = "搜索")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(top = 16.dp),
                            ) {
                                Text("发现")
                            }
                        }
                    }

                    composable("mine") {
                        MineScreen(
                            token = token,
                            innerPadding = innerPadding,
                            onSettingsClick = {
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
        if (liquidGlassEnabled && liquidBackdrop != null) {
            AccountQuickSwitchGlassMenu(
                expanded = showAccountMenu,
                accounts = loggedInAccounts,
                currentAccountId = account.id,
                backdrop = liquidBackdrop,
                onDismissRequest = { showAccountMenu = false },
                cardAlignment = if (useNavigationRail) Alignment.BottomStart else Alignment.BottomEnd,
                cardPadding = if (useNavigationRail) {
                    PaddingValues(start = 8.dp, bottom = navigationBarInset + 12.dp)
                } else {
                    PaddingValues(end = 20.dp, bottom = navigationBarInset + 82.dp)
                }
            )
        }
    }
}
