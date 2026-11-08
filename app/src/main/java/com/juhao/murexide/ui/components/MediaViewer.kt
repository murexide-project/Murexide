package com.juhao.murexide.ui.components

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import coil.compose.rememberAsyncImagePainter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.NullRequestDataException
import coil.size.Precision
import coil.size.Scale
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.ui.chat.ChatMediaGalleryEntry
import com.juhao.murexide.ui.chat.ChatMediaKind
import com.juhao.murexide.ui.chat.buildEarlierChatMediaPage
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.utils.downloadedMediaFileName
import com.juhao.murexide.utils.requestLegacyStoragePermission
import com.juhao.murexide.utils.requiresLegacyWritePermission
import com.juhao.murexide.utils.saveMediaFileToDownloads
import com.juhao.murexide.utils.LegacyStoragePermissionRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import okhttp3.Request
import java.io.File
import java.util.Collections

enum class MediaViewerKind { IMAGE, VIDEO }

@Parcelize
data class MediaViewerItem(
    val url: String,
    val messageId: String? = null,
    val sequence: Long? = null,
    val kind: MediaViewerKind = MediaViewerKind.IMAGE
) : Parcelable

data class MediaViewerPagination(val chatId: String, val chatType: Int)

fun imageMediaItem(url: String, messageId: String? = null, sequence: Long? = null) =
    MediaViewerItem(url = url, messageId = messageId, sequence = sequence, kind = MediaViewerKind.IMAGE)

fun videoMediaItem(url: String, messageId: String? = null, sequence: Long? = null) =
    MediaViewerItem(url = url, messageId = messageId, sequence = sequence, kind = MediaViewerKind.VIDEO)

fun showMediaViewer(
    context: Context,
    items: List<MediaViewerItem>,
    initialIndex: Int = 0,
    pagination: MediaViewerPagination? = null
): Boolean {
    val activity = context.findActivity() ?: return false
    if (items.isEmpty()) return false
    val intent = Intent(activity, MediaViewerActivity::class.java).apply {
        putParcelableArrayListExtra(MediaViewerActivity.EXTRA_ITEMS, ArrayList(items))
        putExtra(MediaViewerActivity.EXTRA_INITIAL_INDEX, initialIndex.coerceIn(items.indices))
        pagination?.let {
            putExtra(MediaViewerActivity.EXTRA_CHAT_ID, it.chatId)
            putExtra(MediaViewerActivity.EXTRA_CHAT_TYPE, it.chatType)
        }
    }
    activity.startActivity(intent)
    return true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MediaViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_ITEMS, MediaViewerItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_ITEMS)
        }.orEmpty()
        val initialIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0)
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val chatType = intent.getIntExtra(EXTRA_CHAT_TYPE, 0)
        if (items.isEmpty()) {
            finish()
            return
        }
        setContent {
            MurexideTheme {
                MediaViewerScreen(
                    initialItems = items,
                    initialIndex = initialIndex,
                    pagination = if (chatId != null && chatType in 1..3)
                        MediaViewerPagination(chatId, chatType) else null,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_ITEMS = "viewer_items"
        const val EXTRA_INITIAL_INDEX = "viewer_initial_index"
        const val EXTRA_CHAT_ID = "viewer_chat_id"
        const val EXTRA_CHAT_TYPE = "viewer_chat_type"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewerScreen(
    initialItems: List<MediaViewerItem>,
    initialIndex: Int,
    pagination: MediaViewerPagination?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLowRam = remember {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }
    val items = remember { mutableStateListOf<MediaViewerItem>().apply { addAll(initialItems) } }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    var controlsVisible by remember { mutableStateOf(true) }
    val currentItem = items.getOrNull(pagerState.currentPage)
    val zoomStates = remember { mutableStateMapOf<Int, Boolean>() }
    val isZoomed = zoomStates[pagerState.currentPage] == true
    var loadingEarlier by remember { mutableStateOf(false) }
    var hasEarlier by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage, pagination, hasEarlier) {
        if (pagination == null || items.isEmpty() || loadingEarlier || !hasEarlier) return@LaunchedEffect
        if (pagerState.currentPage > 1) return@LaunchedEffect
        loadingEarlier = true
        try {
            val anchorId = items.firstOrNull()?.messageId
            if (anchorId == null) {
                hasEarlier = false
                return@LaunchedEffect
            }
            val token = AccountStorage.getInstance(context).getCurrentToken()
            if (token == null) {
                hasEarlier = false
                return@LaunchedEffect
            }
            val knownIds = items.mapNotNullTo(mutableSetOf()) { it.messageId }
            MessageRepository().getMessageList(
                token = token,
                chatId = pagination.chatId,
                chatType = pagination.chatType,
                msgId = anchorId
            ).onSuccess { page ->
                var currentAnchor: String = anchorId
                var continuePaging = true
                while (continuePaging && hasEarlier) {
                    val mediaPage = buildEarlierChatMediaPage(
                        messages = page,
                        knownMessageIds = knownIds,
                        currentAnchorMessageId = currentAnchor,
                        pageSize = 20
                    )
                    val nextAnchor = mediaPage.nextAnchorMessageId
                    if (nextAnchor.isNullOrBlank()) {
                        hasEarlier = false
                        break
                    }
                    currentAnchor = nextAnchor
                    val newItems = mediaPage.entries.map { entry ->
                        when (entry.kind) {
                            ChatMediaKind.IMAGE -> imageMediaItem(entry.url, entry.messageId, entry.sequence)
                            ChatMediaKind.VIDEO -> videoMediaItem(entry.url, entry.messageId, entry.sequence)
                        }
                    }
                    if (newItems.isNotEmpty()) {
                        knownIds.addAll(newItems.mapNotNull { it.messageId })
                        items.addAll(0, newItems)
                        pagerState.scrollToPage(pagerState.currentPage + newItems.size)
                        break
                    }
                    continuePaging = mediaPage.shouldContinueLoading
                    hasEarlier = mediaPage.hasMoreMessages
                    if (!continuePaging || !hasEarlier) break
                }
                if (items.size == initialItems.size) hasEarlier = false
            }.onFailure { error ->
                Log.w("MediaViewer", "load earlier failed", error)
            }
        } finally {
            loadingEarlier = false
        }
    }

    if (!isLowRam) {
        LaunchedEffect(pagerState.currentPage, items.size) {
            val start = (pagerState.currentPage - 1).coerceAtLeast(0)
            val end = (pagerState.currentPage + 1).coerceAtMost(items.lastIndex)
            for (i in start..end) {
                val item = items.getOrNull(i) ?: continue
                if (item.kind == MediaViewerKind.IMAGE && i != pagerState.currentPage) {
                    context.preloadImage(item.url)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize(),
            key = { page -> items.getOrNull(page)?.url ?: page.toString() }
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            when (item.kind) {
                MediaViewerKind.IMAGE -> ZoomableImagePage(
                    item = item,
                    onZoomChange = { zoomStates[page] = it },
                    onTap = { controlsVisible = !controlsVisible },
                    onLongPress = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", item.url))
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                MediaViewerKind.VIDEO -> VideoPage(
                    item = item,
                    isCurrentPage = page == pagerState.currentPage,
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(AppIcons.Close, contentDescription = "关闭", tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                Row {
                    IconButton(onClick = {
                        currentItem?.let { item ->
                            scope.launch { downloadMedia(context, item) }
                        }
                    }) {
                        Icon(AppIcons.Download, contentDescription = "保存", tint = Color.White)
                    }
                    IconButton(onClick = {
                        currentItem?.let { item ->
                            scope.launch { shareMedia(context, item) }
                        }
                    }) {
                        Icon(AppIcons.Share, contentDescription = "分享", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    item: MediaViewerItem,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }

    var scale by remember(item.url) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.url) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.url) { mutableFloatStateOf(0f) }
    var containerSize by remember(item.url) { mutableStateOf(Size.Zero) }
    var fittedSize by remember(item.url) { mutableStateOf(Size.Zero) }

    val qualityTier = remember(scale) {
        when {
            scale <= 1.3f -> 1
            scale <= 2.8f -> 2
            else -> 3
        }
    }
    val tierMultiplier = when (qualityTier) {
        1 -> 1
        2 -> 3
        else -> 5
    }

    val requestWidth = (screenWidthPx * tierMultiplier).coerceAtLeast(1)
    val requestHeight = (screenHeightPx * tierMultiplier).coerceAtLeast(1)

    val imageRequest = remember(context, item.url, requestWidth, requestHeight) {
        item.url.toImageRequest(context, requestWidth, requestHeight)
    }

    val painter = rememberAsyncImagePainter(model = imageRequest)
    val intrinsicSize = painter.intrinsicSize
    val hasIntrinsic = intrinsicSize.width > 0f && intrinsicSize.height > 0f

    LaunchedEffect(hasIntrinsic, intrinsicSize, containerSize) {
        if (!hasIntrinsic || containerSize.width <= 0f || containerSize.height <= 0f) {
            fittedSize = containerSize
            return@LaunchedEffect
        }
        val imageAspect = intrinsicSize.width / intrinsicSize.height
        val containerAspect = containerSize.width / containerSize.height
        fittedSize = if (imageAspect > containerAspect) {
            Size(containerSize.width, containerSize.width / imageAspect)
        } else {
            Size(containerSize.height * imageAspect, containerSize.height)
        }
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        if (newScale <= 1f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            return@rememberTransformableState
        }
        val bounded = boundImageOffset(
            fittedSize = fittedSize,
            scale = newScale,
            desiredOffset = Offset(offsetX + panChange.x, offsetY + panChange.y)
        )
        scale = newScale
        offsetX = bounded.x
        offsetY = bounded.y
    }

    LaunchedEffect(scale) { onZoomChange(scale > 1.02f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .transformable(state = transformState, canPan = { scale > 1.02f })
            .pointerInput(item.url, qualityTier) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val targetScale = 2.5f
                            val cx = tapOffset.x - containerSize.width / 2f
                            val cy = tapOffset.y - containerSize.height / 2f
                            val bounded = boundImageOffset(
                                fittedSize = fittedSize,
                                scale = targetScale,
                                desiredOffset = Offset(
                                    -cx * (targetScale - 1f),
                                    -cy * (targetScale - 1f)
                                )
                            )
                            scale = targetScale
                            offsetX = bounded.x
                            offsetY = bounded.y
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = item.url,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            else -> {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun VideoPage(
    item: MediaViewerItem,
    isCurrentPage: Boolean,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var playbackPosition by remember(item.url) { mutableLongStateOf(0L) }
    val player = remember(item.url) {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(
                Collections.singletonMap("Referer", "http://myapp.jwznb.com")
            )
        val dataSource = DefaultDataSource.Factory(context, httpDataSource)
        val mediaSource = DefaultMediaSourceFactory(dataSource)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSource)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(item.url))
                prepare()
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            if (playbackPosition > 0L) player.seekTo(playbackPosition)
            player.play()
        } else {
            playbackPosition = player.currentPosition
            player.pause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3_000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun String.toImageRequest(
    context: Context,
    width: Int,
    height: Int
): ImageRequest =
    ImageRequest.Builder(context)
        .data(this)
        .size(width, height)
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        .apply {
            if (contains("jwznb.com")) {
                setHeader("Referer", "https://myapp.jwznb.com")
                setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
            }
        }
        .crossfade(true)
        .build()

private fun Context.preloadImage(url: String) {
    if (url.isBlank()) return
    val configuration = resources.configuration
    val density = resources.displayMetrics.density
    val width = (configuration.screenWidthDp * density).toInt().coerceAtLeast(1)
    val height = (configuration.screenHeightDp * density).toInt().coerceAtLeast(1)
    coil.Coil.imageLoader(this).enqueue(url.toImageRequest(applicationContext, width, height))
}

private fun boundImageOffset(
    fittedSize: Size,
    scale: Float,
    desiredOffset: Offset
): Offset {
    val width = if (fittedSize.width > 0f) fittedSize.width else return Offset.Zero
    val height = if (fittedSize.height > 0f) fittedSize.height else return Offset.Zero
    val maxX = ((width * scale) - width) / 2f
    val maxY = ((height * scale) - height) / 2f
    return Offset(
        x = desiredOffset.x.coerceIn(-maxX, maxX),
        y = desiredOffset.y.coerceIn(-maxY, maxY)
    )
}

private suspend fun downloadMedia(context: Context, item: MediaViewerItem) {
    val isVideo = item.kind == MediaViewerKind.VIDEO
    withContext(Dispatchers.IO) {
        try {
            if (requiresLegacyWritePermission(context)) {
                val granted = suspendCoroutineResult<Boolean> { callback ->
                    val activity = context as? Activity
                    if (activity == null) {
                        callback(false)
                        return@suspendCoroutineResult
                    }
                    requestLegacyStoragePermission(activity) { callback(it) }
                }
                if (!granted) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "需要存储权限才能保存", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }
            }
            val request = Request.Builder()
                .url(item.url)
                .apply {
                    if (item.url.contains("jwznb.com")) header("Referer", "https://myapp.jwznb.com")
                }
                .build()
            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val mimeType = response.body.contentType()?.toString()
                val ext = item.url.substringAfterLast('.', if (isVideo) "mp4" else "jpg").substringBefore('?')
                val cacheFile = File(context.cacheDir, "download_${System.currentTimeMillis()}.$ext")
                response.body.byteStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                val savedPath = runCatching {
                    saveMediaFileToDownloads(
                        context = context,
                        sourceFile = cacheFile,
                        displayName = downloadedMediaFileName(
                            sourceFile = cacheFile,
                            isVideo = isVideo,
                            mimeType = mimeType
                        ),
                        mimeType = mimeType
                    )
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (savedPath.isNullOrEmpty()) {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun shareMedia(context: Context, item: MediaViewerItem) {
    val isVideo = item.kind == MediaViewerKind.VIDEO
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(item.url)
                .apply {
                    if (item.url.contains("jwznb.com")) header("Referer", "https://myapp.jwznb.com")
                }
                .build()
            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val ext = item.url.substringAfterLast('.', if (isVideo) "mp4" else "jpg").substringBefore('?')
                val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.$ext")
                response.body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = if (isVideo) "video/*" else "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private suspend fun <T> suspendCoroutineResult(block: (callback: (T) -> Unit) -> Unit): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        block { value -> if (cont.isActive) cont.resume(value) { _, _, _ -> } }
    }