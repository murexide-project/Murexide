package com.juhao.murexide.ui.chat.components

import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.A2uiAudioPlayerRenderer
import androidx.compose.material3.a2ui.catalog.A2uiImageRenderer
import androidx.compose.material3.a2ui.catalog.A2uiVideoRenderer
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.juhao.murexide.data.A2uiFunctionRuntime
import com.juhao.murexide.data.MessageButton
import com.juhao.murexide.data.parseA2uiMessages
import com.juhao.murexide.data.toA2uiMessageButton
import com.juhao.murexide.data.toMessageButton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun A2uiContent(
    content: String,
    hideImages: Boolean,
    isSelectionMode: Boolean,
    onSelectionClick: () -> Unit,
    onButtonClick: (MessageButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentButtonClick by rememberUpdatedState(onButtonClick)
    val catalog = remember(context, hideImages) {
        materialA2uiBasicCatalogV1(
            image = MaterialA2uiBasicCatalogV1Defaults.image(
                A2uiImageRenderer { url, description, scale, imageModifier, onError ->
                    if (hideImages) {
                        Text("图片已隐藏", modifier = imageModifier)
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = description,
                            contentScale = scale,
                            modifier = imageModifier,
                            onError = { onError(it.result.throwable) }
                        )
                    }
                }
            ),
            video = MaterialA2uiBasicCatalogV1Defaults.video(
                A2uiVideoRenderer { url, mediaModifier, onError ->
                    if (hideImages) {
                        Text("视频已隐藏", modifier = mediaModifier)
                    } else {
                        A2uiMediaPlayer(url, true, mediaModifier, onError)
                    }
                }
            ),
            audioPlayer = MaterialA2uiBasicCatalogV1Defaults.audioPlayer(
                A2uiAudioPlayerRenderer { url, description, mediaModifier, onError ->
                    A2uiMediaPlayer(
                        url = url,
                        isVideo = false,
                        modifier = mediaModifier.semantics {
                            contentDescription = description ?: "音频播放器"
                        },
                        onError = onError
                    )
                }
            ),
            functions = A2uiBasicCatalogV1.Functions(context),
            list = ChatA2uiList
        )
    }
    key(content, catalog) {
        val processor = remember { A2uiMessageProcessor(catalogs = listOf(catalog)) }
        val functionRuntime = remember {
            A2uiFunctionRuntime(onOutbound = { message ->
                currentButtonClick(message.toA2uiMessageButton("A2UI 函数响应"))
            })
        }
        var error by remember { mutableStateOf<String?>(null) }
        var warning by remember { mutableStateOf<String?>(null) }
        val surfaces by processor.activeSurfaces.collectAsState()

        LaunchedEffect(processor) {
            launch(Dispatchers.Default) {
                try {
                    processor.collectMessages()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "A2UI 卡片处理失败"
                }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                processor.outboundEvents.collect { event ->
                    when (event) {
                        is A2uiClientEventMessage -> {
                            event.clientDataModel?.let { functionRuntime.replaceDataModels(it.surfaces) }
                            currentButtonClick(event.toMessageButton())
                        }
                        is A2uiClientErrorMessage -> warning = "部分 A2UI 内容无法显示"
                    }
                }
            }
            try {
                val messages = withContext(Dispatchers.Default) {
                    parseA2uiMessages(content).map { Json.parseToJsonElement(it) as JsonObject }
                }
                val parser = A2uiMessageParser()
                messages.forEach { message ->
                    if (!functionRuntime.process(message)) {
                        functionRuntime.observe(message)
                        val parsed = withContext(Dispatchers.Default) {
                            parser.parse(message.toString())
                        }
                        processor.processMessage(parsed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is IllegalArgumentException) {
                    e.message ?: "A2UI 1.0 消息格式无效"
                } else {
                    "A2UI 1.0 消息解析失败"
                }
            }
        }

        Box(modifier = modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            val errorText = error
            if (errorText != null) {
                Text(text = errorText, style = MaterialTheme.typography.bodyMedium)
            } else if (surfaces.isEmpty()) {
                Text(
                    text = warning ?: "A2UI 卡片暂无可显示内容",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    surfaces.forEach { surface ->
                        key(surface.id) {
                            A2uiSurface(
                                surfaceModel = surface,
                                modifier = Modifier.fillMaxWidth(),
                                loadingContent = {
                                    Text("A2UI 卡片等待完整数据", style = MaterialTheme.typography.bodyMedium)
                                },
                                errorContent = {
                                    Text("A2UI 组件无法显示", style = MaterialTheme.typography.bodyMedium)
                                },
                                transitionSpec = null
                            )
                        }
                    }
                    warning?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (isSelectionMode) {
                Box(Modifier.matchParentSize().clickable(onClick = onSelectionClick))
            }
        }
    }
}

private object ChatA2uiList : A2uiBasicCatalogV1.List {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier
    ) {
        with(MaterialA2uiBasicCatalogV1Defaults.list) {
            TypedContent(
                children = children,
                direction = direction,
                align = align,
                accessibility = accessibility,
                modifier = modifier.heightIn(max = 400.dp).widthIn(max = 600.dp)
            )
        }
    }
}

@Composable
private fun A2uiMediaPlayer(
    url: String,
    isVideo: Boolean,
    modifier: Modifier,
    onError: (Throwable?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(context, url) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player, url) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error)
            }
        }
        player.addListener(listener)
        try {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        } catch (e: Exception) {
            currentOnError(e)
        }
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                controllerAutoShow = true
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxWidth().height(if (isVideo) 180.dp else 64.dp)
    )
}
