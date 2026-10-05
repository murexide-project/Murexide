package com.juhao.murexide.ui.discover

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.BotBanner
import com.juhao.murexide.data.BotStoreItem
import com.juhao.murexide.data.GroupCategoryItem
import com.juhao.murexide.data.GroupRecommendItem
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.conversationdetail.ConversationDetailActivity
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.utils.UrlSchemeHandler
import com.juhao.murexide.utils.isYunhuImageUrl
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    token: String,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val viewModel: DiscoverViewModel = viewModel(
        key = "discover_$token",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoverViewModel(token) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val tabs = listOf(
        DiscoverTab.BOTS to "机器人广场",
        DiscoverTab.GROUPS to "群聊推荐"
    )
    val selectedIndex = tabs.indexOfFirst { it.first == uiState.currentTab }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("发现") })
                PrimaryTabRow(selectedTabIndex = selectedIndex) {
                    tabs.forEachIndexed { index, (tab, label) ->
                        Tab(
                            selected = selectedIndex == index,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { contentPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            when (uiState.currentTab) {
                DiscoverTab.BOTS -> BotsTabContent(
                    banners = uiState.banners,
                    bots = uiState.bots,
                    isLoading = uiState.isLoadingBots,
                    isRefreshing = uiState.isRefreshingBots,
                    innerPadding = innerPadding,
                    onRefresh = viewModel::refreshBots,
                    onBannerClick = { banner -> openBanner(context, banner) },
                    onBotClick = { bot ->
                        ChatActivity.start(
                            context = context,
                            chatId = bot.chatId,
                            chatType = bot.chatTypeValue,
                            chatName = bot.nickname,
                            chatAvatar = bot.avatarUrl
                        )
                    }
                )

                DiscoverTab.GROUPS -> GroupsTabContent(
                    categories = uiState.categories,
                    selectedParent = uiState.selectedParent,
                    selectedCategoryId = uiState.selectedCategoryId,
                    keyword = uiState.keyword,
                    groups = uiState.groups,
                    isLoading = uiState.isLoadingGroups,
                    isRefreshing = uiState.isRefreshingGroups,
                    innerPadding = innerPadding,
                    onKeywordChange = viewModel::onKeywordChange,
                    onClearKeyword = viewModel::clearKeyword,
                    onParentSelected = viewModel::selectParent,
                    onCategorySelected = viewModel::selectCategory,
                    onRefresh = viewModel::refreshGroups,
                    onGroupClick = { group ->
                        ConversationDetailActivity.start(
                            context = context,
                            chatId = group.groupId,
                            chatType = 2,
                            chatName = group.name,
                            chatAvatar = group.avatarUrl
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotsTabContent(
    banners: List<BotBanner>,
    bots: List<BotStoreItem>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    innerPadding: PaddingValues,
    onRefresh: () -> Unit,
    onBannerClick: (BotBanner) -> Unit,
    onBotClick: (BotStoreItem) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && bots.isEmpty() && banners.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            bots.isEmpty() && banners.isEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(
                            Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 12.dp
                    )
                ) {
                    if (banners.isNotEmpty()) {
                        item {
                            BotBannerPager(banners = banners, onBannerClick = onBannerClick)
                        }
                    }

                    if (bots.isNotEmpty()) {
                        item {
                            Text(
                                text = "推荐机器人",
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 20.dp,
                                    bottom = 8.dp
                                ),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(bots, key = { it.chatId }) { bot ->
                            BotRow(bot = bot, onClick = { onBotClick(bot) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotBannerPager(
    banners: List<BotBanner>,
    onBannerClick: (BotBanner) -> Unit
) {
    val pagerState = rememberPagerState { banners.size }
    val context = LocalContext.current

    LaunchedEffect(pagerState, banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4000)
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.animateScrollToPage(next)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onBannerClick(banner) }
            ) {
                AsyncImage(
                    model = discoverImageRequest(context, banner.imageUrl),
                    contentDescription = banner.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (banner.title.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                )
                            )
                    )
                    Text(
                        text = banner.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                    )
                }
            }
        }

        if (banners.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(banners.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (selected) 0.95f else 0.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun BotRow(
    bot: BotStoreItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = bot.avatarUrl, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bot.nickname,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bot.introduction.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bot.introduction,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (bot.userCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${bot.userCount} 人使用",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsTabContent(
    categories: List<GroupCategoryItem>,
    selectedParent: GroupCategoryItem?,
    selectedCategoryId: Int,
    keyword: String,
    groups: List<GroupRecommendItem>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    innerPadding: PaddingValues,
    onKeywordChange: (String) -> Unit,
    onClearKeyword: () -> Unit,
    onParentSelected: (GroupCategoryItem?) -> Unit,
    onCategorySelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onGroupClick: (GroupRecommendItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索群聊") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = onClearKeyword) {
                        Icon(AppIcons.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedParent == null,
                        onClick = { onParentSelected(null) },
                        label = { Text("全部") }
                    )
                }
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedParent?.id == category.id,
                        onClick = { onParentSelected(category) },
                        label = { Text(category.name) }
                    )
                }
            }

            val parent = selectedParent
            val subItems = parent?.subItems
            if (parent != null && !subItems.isNullOrEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == parent.id,
                            onClick = { onCategorySelected(parent.id) },
                            label = { Text("全部") }
                        )
                    }
                    items(subItems, key = { it.id }) { sub ->
                        FilterChip(
                            selected = selectedCategoryId == sub.id,
                            onClick = { onCategorySelected(sub.id) },
                            label = { Text(sub.name) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && groups.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                groups.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(
                                Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "没有找到相关群聊",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = innerPadding.calculateBottomPadding() + 12.dp
                        )
                    ) {
                        items(groups, key = { it.groupId }) { group ->
                            GroupRow(group = group, onClick = { onGroupClick(group) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupRecommendItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = group.avatarUrl, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.introduction.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = group.introduction,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = groupMeta(group)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun groupMeta(group: GroupRecommendItem): String {
    val parts = mutableListOf<String>()
    if (group.headcount > 0) parts.add("${group.headcount} 位成员")
    if (group.category.isNotBlank()) parts.add(group.category)
    return parts.joinToString(" · ")
}

private fun discoverImageRequest(context: Context, url: String): ImageRequest {
    return ImageRequest.Builder(context)
        .data(url)
        .apply {
            if (isYunhuImageUrl(url)) {
                setHeader("Referer", "https://myapp.jwznb.com")
            }
        }
        .build()
}

private fun openBanner(context: Context, banner: BotBanner) {
    val url = banner.targetUrl
    if (url.isBlank()) return
    if (UrlSchemeHandler.handle(context, url)) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
