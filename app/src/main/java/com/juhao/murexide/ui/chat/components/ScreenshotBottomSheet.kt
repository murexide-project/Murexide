package com.juhao.murexide.ui.chat.components

import com.juhao.murexide.ui.icons.AppIcons

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.settings.ScreenshotSettingsActivity

private const val SCREENSHOT_TAG = "ScreenshotBottomSheet"

private enum class ScreenshotAction {
    SAVE,
    SHARE
}

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
    var pendingLegacyAction by remember { mutableStateOf<ScreenshotAction?>(null) }
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

    val performScreenshotAction: (ScreenshotAction) -> Unit = { action ->
        scope.launch {
            val view = screenshotView ?: return@launch
            val bitmap = try {
                withContext(Dispatchers.Main) {
                    check(view.width > 0 && view.height > 0) { "截图视图尚未完成布局" }
                    createBitmap(view.width, view.height).also { captured ->
                        view.draw(Canvas(captured))
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(SCREENSHOT_TAG, "Failed to render screenshot", error)
                Toast.makeText(context, "截图生成失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val succeeded = try {
                when (action) {
                    ScreenshotAction.SAVE -> saveBitmapToGallery(context, bitmap)
                    ScreenshotAction.SHARE -> saveAndShareBitmap(context, bitmap)
                }
            } finally {
                bitmap.recycle()
            }
            if (succeeded) {
                onDismiss()
            }
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingLegacyAction
        pendingLegacyAction = null
        if (granted && action != null) {
            performScreenshotAction(action)
        } else if (action != null) {
            Toast.makeText(context, "需要存储权限才能保存截图", Toast.LENGTH_SHORT).show()
        }
    }

    val requestOrPerformScreenshotAction: (ScreenshotAction) -> Unit = { action ->
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (action == ScreenshotAction.SAVE &&
            requiresLegacyWritePermission(Build.VERSION.SDK_INT, permissionGranted)
        ) {
            pendingLegacyAction = action
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            performScreenshotAction(action)
        }
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
                    icon = AppIcons.SaveAlt,
                    label = "保存图片",
                    onClick = { requestOrPerformScreenshotAction(ScreenshotAction.SAVE) }
                )
                Spacer(modifier = Modifier.width(25.dp))
                ScreenshotActionCard(
                    icon = AppIcons.Share,
                    label = "分享",
                    onClick = { requestOrPerformScreenshotAction(ScreenshotAction.SHARE) }
                )
                Spacer(modifier = Modifier.width(25.dp))
                ScreenshotActionCard(
                    icon = AppIcons.Settings,
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
    val configuration = LocalConfiguration.current
    @Suppress("DEPRECATION")
    val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        configuration.locale
    }

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
                            Icon(AppIcons.VisibilityOff, contentDescription = null, modifier = Modifier.size(24.dp))
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
                        text = SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date()),
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

internal fun requiresLegacyWritePermission(sdkInt: Int, permissionGranted: Boolean): Boolean {
    return sdkInt <= Build.VERSION_CODES.P && !permissionGranted
}

private suspend fun saveBitmapToMediaStore(
    context: Context,
    bitmap: Bitmap,
    filenamePrefix: String
): Result<Uri> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var insertedUri: Uri? = null
    try {
        val filename = "${filenamePrefix}_${System.currentTimeMillis()}.webp"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ) ?: throw IOException("MediaStore insert returned null")
        insertedUri = uri

        val output = resolver.openOutputStream(uri)
            ?: throw IOException("MediaStore output stream is unavailable")
        output.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 90, stream)) {
                throw IOException("Bitmap compression failed")
            }
            stream.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            if (resolver.update(uri, published, null, null) <= 0) {
                throw IOException("Failed to publish MediaStore image")
            }
        }
        Result.success(uri)
    } catch (error: Exception) {
        insertedUri?.let { uri ->
            runCatching { resolver.delete(uri, null, null) }
                .onFailure { cleanupError ->
                    Log.w(SCREENSHOT_TAG, "Failed to remove incomplete MediaStore image", cleanupError)
                }
        }
        if (error is CancellationException) throw error
        Log.e(SCREENSHOT_TAG, "Failed to save screenshot to MediaStore", error)
        Result.failure(error)
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val result = saveBitmapToMediaStore(context, bitmap, "chat_screenshot")
    return withContext(Dispatchers.Main) {
        if (result.isSuccess) {
            Toast.makeText(context, "截图已保存到相册", Toast.LENGTH_SHORT).show()
            true
        } else {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

private data class SharedBitmap(val uri: Uri, val file: File)

private suspend fun saveBitmapToShareCache(
    context: Context,
    bitmap: Bitmap
): Result<SharedBitmap> = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "shared_screenshots")
    val file = File(directory, "chat_share_${System.currentTimeMillis()}.webp")
    try {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create screenshot cache directory")
        }
        file.outputStream().use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.WEBP, 90, stream)) {
                throw IOException("Bitmap compression failed")
            }
            stream.flush()
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        Result.success(SharedBitmap(uri, file))
    } catch (error: Exception) {
        if (file.exists() && !file.delete()) {
            Log.w(SCREENSHOT_TAG, "Failed to remove incomplete shared screenshot")
        }
        if (error is CancellationException) throw error
        Log.e(SCREENSHOT_TAG, "Failed to save screenshot to cache", error)
        Result.failure(error)
    }
}

suspend fun saveAndShareBitmap(context: Context, bitmap: Bitmap): Boolean {
    val sharedBitmap = saveBitmapToShareCache(context, bitmap).getOrElse {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    return withContext(Dispatchers.Main) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/webp"
                putExtra(Intent.EXTRA_STREAM, sharedBitmap.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "分享截图").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!sharedBitmap.file.delete()) {
                Log.w(SCREENSHOT_TAG, "Failed to remove unshared cached screenshot")
            }
            Log.e(SCREENSHOT_TAG, "Failed to launch screenshot share sheet", error)
            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
