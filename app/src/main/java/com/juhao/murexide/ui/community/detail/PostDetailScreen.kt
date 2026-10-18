package com.juhao.murexide.ui.community.detail

import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.icons.AutoMirroredIcon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.CommentItem
import com.juhao.murexide.data.PostDetail
import com.juhao.murexide.data.GroupInfo
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.community.ba.CreatePostActivity
import com.juhao.murexide.ui.components.*
import com.juhao.murexide.ui.theme.LocalLiquidGlassBlur
import com.juhao.murexide.ui.theme.LocalLiquidGlassEnabled
import com.juhao.murexide.ui.theme.UiState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostDetailScreen(
    onBackClick: () -> Unit,
    viewModel: PostDetailViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInputSheet by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMoreComments && !uiState.isLoadingComments) {
            viewModel.loadMoreComments()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onBackClick()
        }
    }

    val hazeState = remember { HazeState() }
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val themeColor by UiState.themeColor
    val topBarBackgroundColor = if (themeColor == "WHITE") {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val liquidBackdrop = if (liquidGlassEnabled) {
        rememberLayerBackdrop {
            drawRect(topBarBackgroundColor)
            drawContent()
        }
    } else {
        null
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除此文章吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePost()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showInputSheet) {
        CommentInputSheet(
            onDismiss = { showInputSheet = false },
            onSend = { text ->
                viewModel.submitComment(text) { success ->
                    if (success) {
                        showInputSheet = false
                        Toast.makeText(context, "评论成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "评论失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            FloatingTopBar(
                hazeState = hazeState,
                liquidBackdrop = liquidBackdrop,
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        AutoMirroredIcon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                avatar = {
                    uiState.post?.let { post ->
                        Avatar(url = post.senderAvatar, alwaysCircle = true, size = 40.dp)
                    }
                },
                title = {
                    uiState.post?.let { post ->
                        Column {
                            Text(
                                post.senderNickname,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                post.createTimeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        ExpressiveDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制原文") },
                                onClick = {
                                    showMenu = false
                                    val content = uiState.post?.content ?: ""
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("post", content))
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(AppIcons.ContentCopy, contentDescription = null)
                                }
                            )
                            if (uiState.isManageEnabled) {
                                DropdownMenuItem(
                                    text = { Text("编辑文章") },
                                    onClick = {
                                        showMenu = false
                                        CreatePostActivity.startEdit(
                                            context = context,
                                            content = uiState.post?.content ?: "",
                                            contentType = uiState.post?.contentType ?: 1,
                                            postId = uiState.post?.id ?: return@DropdownMenuItem,
                                            title = uiState.post?.title ?: ""
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(AppIcons.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除文章", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            AppIcons.Delete,
                                            tint = MaterialTheme.colorScheme.error,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { showMenu = true }) {
                            AutoMirroredIcon(AppIcons.MoreVert, contentDescription = "更多")
                        }
                    }
                }
            )
        },
        bottomBar = {
            CommentBottomBar(
                hazeState = hazeState,
                liquidBackdrop = liquidBackdrop,
                enabled = uiState.post != null,
                isLiked = uiState.post?.isLiked == 1,
                isCollected = uiState.post?.isCollected == 1,
                likeCount = uiState.post?.likeNum ?: 0,
                commentCount = uiState.post?.commentNum ?: 0,
                collectCount = uiState.post?.collectNum ?: 0,
                onInputClick = { showInputSheet = true },
                onLikeClick = { viewModel.toggleLike() },
                onCommentClick = {
                    scope.launch { listState.animateScrollToItem(2) }
                },
                onCollectClick = { viewModel.toggleCollect() }
            )
        }
    ) { innerPadding ->
        val post = uiState.post
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (liquidBackdrop != null) {
                        Modifier
                            .layerBackdrop(liquidBackdrop)
                            .hazeSource(hazeState)
                    } else {
                        Modifier.hazeSource(hazeState)
                    }
                )
        ) {
            when {
                uiState.isLoadingDetail && post == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                post == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            uiState.error ?: "加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "post_header_${post.id}") {
                            PostHeader(post = post, viewModel = viewModel)
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "评论 ${uiState.commentTotal}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (uiState.comments.isEmpty() && !uiState.isLoadingComments) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无评论", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        items(uiState.comments, key = { it.id }) { comment ->
                            CommentRow(comment)
                        }

                        if (uiState.isLoadingComments) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ContainedLoadingIndicator(
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentBottomBar(
    hazeState: HazeState,
    liquidBackdrop: Backdrop?,
    enabled: Boolean,
    isLiked: Boolean,
    isCollected: Boolean,
    likeCount: Int,
    commentCount: Int,
    collectCount: Int,
    onInputClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCollectClick: () -> Unit
) {
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val liquidGlassBlur = LocalLiquidGlassBlur.current
    val controlSize = 48.dp
    val buttonShape = CircleShape
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val barStyle = HazeMaterials.thin().then {
        blurRadius(32.dp)
        noiseFactor(0f)
    }
    val useGlassBar = liquidGlassEnabled && liquidBackdrop != null
    val glassSurfaceColor = barColor.copy(alpha = 0.8f)

    fun Modifier.glassControl(shape: Shape): Modifier =
        if (useGlassBar) {
            drawBackdrop(
                backdrop = liquidBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(1.dp.toPx() * liquidGlassBlur)
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(glassSurfaceColor)
                },
            )
        } else {
            shadow(2.dp, shape)
                .clip(shape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = barStyle
                )
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            barColor.copy(alpha = 0.6f),
                            barColor.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(controlSize)
                    .glassControl(RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = enabled, onClick = onInputClick)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    AppIcons.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "说点什么…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .height(controlSize)
                    .glassControl(buttonShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarAction(
                    icon = if (isLiked) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    count = likeCount,
                    tint = if (isLiked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = enabled,
                    onClick = onLikeClick
                )
                BottomBarAction(
                    icon = AppIcons.ChatBubble,
                    count = commentCount,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = enabled,
                    onClick = onCommentClick
                )
                BottomBarAction(
                    icon = if (isCollected) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                    count = collectCount,
                    tint = if (isCollected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = enabled,
                    onClick = onCollectClick
                )
            }
        }
    }
}

@Composable
private fun BottomBarAction(
    icon: ImageVector,
    count: Int,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentInputSheet(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "说点什么…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )

            Button(
                onClick = {
                    if (canSend) {
                        onSend(text.trim())
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(8.dp)
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun PostHeader(post: PostDetail, viewModel: PostDetailViewModel) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        Text(post.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)

        Spacer(Modifier.height(12.dp))

        if (post.contentType == 2) {
            MarkdownText(markdown = post.content, modifier = Modifier.fillMaxWidth())
        } else {
            SelectionContainer {
                Text(post.content, style = MaterialTheme.typography.bodyMedium)
            }
        }

        post.group?.takeIf { it.groupId.isNotEmpty() }?.let { group ->
            Spacer(Modifier.height(12.dp))
            GroupCard(group)
        }
    }
}

@Composable
private fun GroupCard(group: GroupInfo) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                ChatActivity.start(
                    context = context,
                    chatId = group.groupId,
                    chatType = 2,
                    chatName = group.name,
                    chatAvatar = group.avatarUrl
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(url = group.avatarUrl, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    "${group.headcount} 人",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentRow(comment: CommentItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Avatar(url = comment.senderAvatar, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                comment.senderNickname,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                comment.createTimeText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
        }
        if (comment.likeNum > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (comment.isLiked == "1") AppIcons.Favorite else AppIcons.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (comment.isLiked == "1") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    comment.likeNum.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}