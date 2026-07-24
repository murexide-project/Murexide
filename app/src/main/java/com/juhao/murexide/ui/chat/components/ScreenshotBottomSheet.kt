package com.juhao.murexide.ui.chat.components

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.LocalImageLoader
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.data.MessageItem
import com.juhao.murexide.ui.components.Avatar
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.createBitmap
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.settings.ScreenshotSettingsActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotBottomSheet(
    messages: List<MessageItem>,
    chatName: String,
    chatAvatar: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val scope = rememberCoroutineScope()
    var screenshotView by remember { mutableStateOf<View?>(null) }
    val settingsStorage = remember { SettingsStorage(context) }

    val hideSenderInfo by settingsStorage.screenshotHideSenderInfoFlow.collectAsState(initial = false)
    val hideMyInfo by settingsStorage.screenshotHideMyInfoFlow.collectAsState(initial = false)
    val hideSessionInfo by settingsStorage.screenshotHideSessionInfoFlow.collectAsState(initial = false)
    val hideImages by settingsStorage.screenshotHideImagesFlow.collectAsState(initial = false)

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val screenshotImageLoader = remember {
        ImageLoader.Builder(context)
            .allowHardware(false)
            .build()
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { _ ->
                    ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(
                                LocalImageLoader provides screenshotImageLoader
                            ) {
                                MurexideTheme {
                                    ScreenshotContent(
                                        messages = messages,
                                        chatName = chatName,
                                        chatAvatar = chatAvatar,
                                        hideSenderInfo = hideSenderInfo,
                                        hideMyInfo = hideMyInfo,
                                        hideSessionInfo = hideSessionInfo,
                                        hideImages = hideImages
                                    )
                                }
                            }
                        }
                        screenshotView = this
                    }
                },
                modifier = Modifier.wrapContentSize()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScreenshotActionCard(
                    icon = Icons.Rounded.SaveAlt,
                    label = "保存图片",
                    onClick = {
                        scope.launch {
                            val view = screenshotView ?: return@launch
                            withContext(Dispatchers.Main) {
                                val bitmap = createBitmap(view.width, view.height)
                                val canvas = Canvas(bitmap)
                                view.draw(canvas)
                                saveBitmapToGallery(context, bitmap)
                                onDismiss()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(25.dp))
                ScreenshotActionCard(
                    icon = Icons.Rounded.Share,
                    label = "分享",
                    onClick = {
                        scope.launch {
                            val view = screenshotView ?: return@launch
                            val bitmap = withContext(Dispatchers.Main) {
                                val bmp = createBitmap(view.width, view.height)
                                val canvas = Canvas(bmp)
                                view.draw(canvas)
                                bmp
                            }
                            saveAndShareBitmap(context, bitmap)
                            onDismiss()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(25.dp))
                ScreenshotActionCard(
                    icon = Icons.Rounded.Settings,
                    label = "截图设置",
                    onClick = {
                        val intent = Intent(context, ScreenshotSettingsActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotContent(
    messages: List<MessageItem>,
    chatName: String,
    chatAvatar: String,
    hideSenderInfo: Boolean = false,
    hideMyInfo: Boolean = false,
    hideSessionInfo: Boolean = false,
    hideImages: Boolean = false
) {
    val anonymousCache = remember { mutableMapOf<String, String>() }
    var counter by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 会话头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (hideSessionInfo) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    Avatar(url = chatAvatar, size = 36.dp)
                }
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = if (hideSessionInfo) "隐藏会话" else chatName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                messages.forEachIndexed { index, message ->
                    val olderMessage = messages.getOrNull(index - 1)
                    val newerMessage = messages.getOrNull(index + 1)

                    val isFirstFromSender = newerMessage == null ||
                            newerMessage.contentType == MessageItem.CONTENT_TYPE_TIP ||
                            newerMessage.senderId != message.senderId

                    val isLastFromSender = olderMessage == null ||
                            olderMessage.contentType == MessageItem.CONTENT_TYPE_TIP ||
                            olderMessage.senderId != message.senderId

                    MessageBubble(
                        message = message,
                        isLastFromSender = isLastFromSender,
                        isFirstFromSender = isFirstFromSender,
                        showAvatar = isFirstFromSender,
                        hideSenderInfo = hideSenderInfo,
                        hideMyInfo = hideMyInfo,
                        hideImages = hideImages,
                        anonymousNameProvider = { senderId ->
                            anonymousCache.getOrPut(senderId) {
                                counter++
                                "用户$counter"
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.6f)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "由 Murexide 生成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ScreenshotActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = false
) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(22.dp),
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private suspend fun saveBitmapToMediaStore(
    context: Context,
    bitmap: Bitmap,
    filenamePrefix: String,
    onSuccess: suspend (Uri) -> Unit = {}
) {
    withContext(Dispatchers.IO) {
        val filename = "${filenamePrefix}_${System.currentTimeMillis()}.webp"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                out.flush()
            }
            onSuccess(it)
        } ?: withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    saveBitmapToMediaStore(context, bitmap, "chat_screenshot") {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "截图已保存到相册", Toast.LENGTH_SHORT).show()
        }
    }
}

suspend fun saveAndShareBitmap(context: Context, bitmap: Bitmap) {
    saveBitmapToMediaStore(context, bitmap, "chat_share") { uri ->
        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/webp"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享截图"))
        }
    }
}
