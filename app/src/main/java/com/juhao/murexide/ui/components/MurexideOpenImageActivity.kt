package com.juhao.murexide.ui.components

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.flyjingfish.openimagelib.OpenImageFragmentStateAdapter
import com.flyjingfish.openimagelib.StandardOpenImageActivity
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.MessageRepository
import com.juhao.murexide.ui.chat.ChatMediaGalleryEntry
import com.juhao.murexide.ui.chat.ChatMediaKind
import com.juhao.murexide.ui.chat.buildEarlierChatMediaPage
import com.juhao.murexide.utils.LegacyStoragePermissionRequester
import com.juhao.murexide.utils.requiresLegacyWritePermission
import kotlinx.coroutines.launch

class MurexideOpenImageActivity : StandardOpenImageActivity(), LegacyStoragePermissionRequester {
    private var pendingStoragePermissionResult: ((Boolean) -> Unit)? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingStoragePermissionResult?.let { callback ->
            pendingStoragePermissionResult = null
            callback(granted)
        }
    }

    private val viewerOptions by lazy(LazyThreadSafetyMode.NONE) {
        intent.getBundleExtra(EXTRA_VIEWER_OPTIONS)
    }
    private val media by lazy(LazyThreadSafetyMode.NONE) {
        val urls = viewerOptions?.getStringArrayList(EXTRA_MEDIA_URLS).orEmpty()
        val messageIds = viewerOptions?.getStringArrayList(EXTRA_MEDIA_MESSAGE_IDS).orEmpty()
        urls.mapIndexed { index, url ->
            ViewerMedia(
                messageId = messageIds.getOrNull(index)?.takeIf { it.isNotBlank() },
                url = url
            )
        }.toMutableList()
    }
    private val chatId by lazy(LazyThreadSafetyMode.NONE) {
        viewerOptions?.getString(EXTRA_CHAT_ID).orEmpty()
    }
    private val chatType by lazy(LazyThreadSafetyMode.NONE) {
        viewerOptions?.getInt(EXTRA_CHAT_TYPE, 0) ?: 0
    }
    private val knownMessageIds by lazy(LazyThreadSafetyMode.NONE) {
        media.mapNotNullTo(mutableSetOf()) { it.messageId }
    }
    private val accountStorage by lazy(LazyThreadSafetyMode.NONE) {
        AccountStorage.getInstance(applicationContext)
    }
    private val messageRepository = MessageRepository()
    private val preloadedUrls = mutableSetOf<String>()
    private var earlierAnchorMessageId: String? = null
    private var isLoadingEarlierMedia = false
    private var hasEarlierMedia = true
    private var callbackRegistered = false
    private var shareTransitionFinished = false
    private val shareTransitionFallback = Runnable { onShareTransitionEnd() }

    private val isLowRamDevice: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            preloadAround(position)
            maybeLoadEarlier(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_IDLE) {
                maybeLoadEarlier(viewPager2.currentItem)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canFragmentBack()) {
                    close(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        earlierAnchorMessageId = media.firstNotNullOfOrNull(ViewerMedia::messageId)

        viewPager2.registerOnPageChangeCallback(pageChangeCallback)
        callbackRegistered = true
        preloadAround(viewPager2.currentItem)
        viewPager2.post { maybeLoadEarlier(viewPager2.currentItem) }
        if (!shareTransitionFinished) {
            viewPager2.postDelayed(shareTransitionFallback, TRANSITION_FALLBACK_DELAY_MS)
        }
    }

    override fun requestLegacyStoragePermission(onResult: (Boolean) -> Unit) {
        if (!requiresLegacyWritePermission(this)) {
            onResult(true)
            return
        }
        pendingStoragePermissionResult?.invoke(false)
        pendingStoragePermissionResult = onResult
        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shareTransitionFinished && !isFinishing) {
            configureSystemBars()
        }
    }

    override fun onShareTransitionEnd() {
        if (shareTransitionFinished) return
        shareTransitionFinished = true
        viewPager2.removeCallbacks(shareTransitionFallback)
        super.onShareTransitionEnd()
        configureSystemBars()
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.statusBars())
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun preloadAround(position: Int) {
        if (isLowRamDevice) return
        for (index in (position - PRELOAD_RADIUS)..(position + PRELOAD_RADIUS)) {
            val url = media.getOrNull(index)?.url ?: continue
            if (preloadedUrls.add(url)) {
                preloadOpenImage(applicationContext, url)
            }
        }
    }

    private fun maybeLoadEarlier(position: Int) {
        if (!paginationEnabled || media.isEmpty()) return
        if (viewPager2.scrollState != ViewPager2.SCROLL_STATE_IDLE) return
        if (position <= LOAD_THRESHOLD && hasEarlierMedia && !isLoadingEarlierMedia) {
            loadEarlierPage()
        }
    }

    private fun loadEarlierPage() {
        val anchorId = earlierAnchorMessageId ?: run {
            hasEarlierMedia = false
            return
        }

        isLoadingEarlierMedia = true
        lifecycleScope.launch {
            var continuePaging = false
            try {
                val token = accountStorage.getCurrentToken()
                if (token == null) {
                    hasEarlierMedia = false
                    return@launch
                }
                messageRepository.getMessageList(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    msgId = anchorId
                ).onSuccess { page ->
                    if (page.isEmpty()) {
                        hasEarlierMedia = false
                        return@onSuccess
                    }

                    val mediaPage = buildEarlierChatMediaPage(
                        messages = page,
                        knownMessageIds = knownMessageIds,
                        currentAnchorMessageId = anchorId,
                        pageSize = PAGE_SIZE
                    )
                    earlierAnchorMessageId = mediaPage.nextAnchorMessageId
                    val addedCount = addEarlierPage(mediaPage.entries)
                    hasEarlierMedia = mediaPage.hasMoreMessages
                    continuePaging = addedCount == 0 && mediaPage.shouldContinueLoading
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load earlier media messages", error)
                }
            } finally {
                isLoadingEarlierMedia = false
                if (continuePaging && !isFinishing) {
                    viewPager2.postDelayed(
                        { maybeLoadEarlier(viewPager2.currentItem) },
                        EMPTY_MEDIA_PAGE_DELAY_MS
                    )
                }
            }
        }
    }

    private fun addEarlierPage(newEntries: List<ChatMediaGalleryEntry>): Int {
        val adapter = viewPager2.adapter as? OpenImageFragmentStateAdapter ?: return -1
        if (newEntries.isEmpty()) {
            return 0
        }

        val viewerMedia = newEntries.map { entry ->
            ViewerMedia(
                messageId = entry.messageId,
                url = entry.url
            )
        }
        val openImageItems = newEntries.map { entry ->
            when (entry.kind) {
                ChatMediaKind.IMAGE -> imageMessagePreviewItem(
                    url = entry.url,
                    messageId = entry.messageId,
                    imageId = entry.sequence
                )
                ChatMediaKind.VIDEO -> videoMessagePreviewItem(
                    url = entry.url,
                    messageId = entry.messageId,
                    sequence = entry.sequence
                )
            }
        }
        knownMessageIds.addAll(newEntries.map { it.messageId })
        media.addAll(0, viewerMedia)
        adapter.addFrontData(openImageItems)
        return newEntries.size
    }

    override fun onDestroy() {
        pendingStoragePermissionResult = null
        if (callbackRegistered) {
            viewPager2.unregisterOnPageChangeCallback(pageChangeCallback)
        }
        viewPager2.removeCallbacks(shareTransitionFallback)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MurexideMediaViewer"

        const val EXTRA_VIEWER_OPTIONS = "murexide_open_image_options"
        const val EXTRA_MEDIA_URLS = "murexide_open_image_urls"
        const val EXTRA_MEDIA_MESSAGE_IDS = "murexide_open_image_message_ids"
        const val EXTRA_CHAT_ID = "murexide_open_image_chat_id"
        const val EXTRA_CHAT_TYPE = "murexide_open_image_chat_type"

        private const val PRELOAD_RADIUS = 1
        private const val LOAD_THRESHOLD = 1
        private const val PAGE_SIZE = 20
        private const val EMPTY_MEDIA_PAGE_DELAY_MS = 150L
        private const val TRANSITION_FALLBACK_DELAY_MS = 600L
    }

    private val paginationEnabled: Boolean
        get() = chatId.isNotBlank() && chatType in 1..3 && earlierAnchorMessageId != null

    private data class ViewerMedia(
        val messageId: String?,
        val url: String
    )
}