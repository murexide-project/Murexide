package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juhao.murexide.data.ExpressionItem
import com.juhao.murexide.data.StickerItem
import com.juhao.murexide.data.StickerPack
import com.juhao.murexide.data.resolveStickerUrl
import kotlinx.coroutines.launch

private const val DEFAULT_EMOJI_COLUMNS = 8

@Composable
fun EmojiPanel(
    expressions: List<ExpressionItem>,
    stickerPacks: List<StickerPack>,
    isLoading: Boolean,
    onExpressionClick: (ExpressionItem) -> Unit,
    onStickerItemClick: (StickerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabTitles = remember(stickerPacks) {
        buildList {
            add("收藏")
            stickerPacks.forEach { add(it.name) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { tabTitles.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 4.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title,
                            maxLines = 1,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (isLoading && page == 0 && expressions.isEmpty() && stickerPacks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (page == 0) {
                ExpressionGridPage(
                    expressions = expressions,
                    onItemClick = onExpressionClick
                )
            } else {
                val packIndex = page - 1
                if (packIndex in stickerPacks.indices) {
                    StickerPackGridPage(
                        items = stickerPacks[packIndex].stickerItems,
                        onItemClick = onStickerItemClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressionGridPage(
    expressions: List<ExpressionItem>,
    onItemClick: (ExpressionItem) -> Unit
) {
    if (expressions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无收藏表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = expressions.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = expressions[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            onClick = { onItemClick(item) }
        )
    }
}

@Composable
private fun StickerPackGridPage(
    items: List<StickerItem>,
    onItemClick: (StickerItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无表情",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        return
    }
    EmojiGrid(
        count = items.size,
        contentPadding = PaddingValues(8.dp)
    ) { index ->
        val item = items[index]
        EmojiGridItem(
            url = resolveStickerUrl(item.url),
            name = item.name,
            onClick = { onItemClick(item) }
        )
    }
}

/** 通用表情网格 */
@Composable
private fun EmojiGrid(
    count: Int,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (index: Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = count,
            key = { index -> "remote_emoji_$index" },
            contentType = { "remote_emoji" }
        ) { index ->
            content(index)
        }
    }
}

@Composable
private fun EmojiGridItem(
    url: String?,
    name: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url?.contains("jwznb.com") == true) {
                    setHeader("Referer", "https://myapp.jwznb.com")
                }
            }
            .crossfade(true)
            .build()
    }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Fit
        )
        name?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
