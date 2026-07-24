package com.juhao.murexide.ui.components

import android.os.Bundle
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.flyjingfish.openimagelib.OpenImageFragmentStateAdapter
import com.flyjingfish.openimagelib.StandardOpenImageActivity
import com.juhao.murexide.data.ConversationImage
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.repository.MessageRepository
import kotlinx.coroutines.launch

/** OpenImage activity with chat image pagination and immersive system bars. */
class MurexideOpenImageActivity : StandardOpenImageActivity() {
    private val viewerOptions by lazy(LazyThreadSafetyMode.NONE) {
        intent.getBundleExtra(EXTRA_VIEWER_OPTIONS)
    }
    private val images by lazy(LazyThreadSafetyMode.NONE) {
        val urls = viewerOptions?.getStringArrayList(EXTRA_IMAGE_URLS).orEmpty()
        val messageIds = viewerOptions?.getStringArrayList(EXTRA_IMAGE_MESSAGE_IDS).orEmpty()
        val imageIds = viewerOptions?.getLongArray(EXTRA_IMAGE_IDS) ?: longArrayOf()
        urls.mapIndexed { index, url ->
            ViewerImage(
                messageId = messageIds.getOrNull(index)?.takeIf { it.isNotBlank() },
                imageId = imageIds.getOrNull(index)?.takeIf { it != 0L },
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
        images.mapNotNullTo(mutableSetOf()) { it.messageId }
    }
    private val accountStorage by lazy(LazyThreadSafetyMode.NONE) {
        AccountStorage(applicationContext)
    }
    private val messageRepository = MessageRepository()
    private val preloadedUrls = mutableSetOf<String>()
    private var isLoadingEarlierImages = false
    private var isLoadingLatestImages = false
    private var hasEarlierImages = true
    private var hasLatestImages = true
    private var callbackRegistered = false

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            preloadAround(position)
            maybeLoadMore(position)
        }

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager2.SCROLL_STATE_IDLE) {
                maybeLoadMore(viewPager2.currentItem)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return

        enterImmersiveMode()

        viewPager2.registerOnPageChangeCallback(pageChangeCallback)
        callbackRegistered = true
        preloadAround(viewPager2.currentItem)
        viewPager2.post { maybeLoadMore(viewPager2.currentItem) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isFinishing) {
            enterImmersiveMode()
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun preloadAround(position: Int) {
        for (index in (position - PRELOAD_RADIUS)..(position + PRELOAD_RADIUS)) {
            val url = images.getOrNull(index)?.url ?: continue
            if (preloadedUrls.add(url)) {
                preloadOpenImage(applicationContext, url)
            }
        }
    }

    private fun maybeLoadMore(position: Int) {
        if (!paginationEnabled || images.isEmpty()) return
        if (position <= LOAD_THRESHOLD && hasEarlierImages && !isLoadingEarlierImages) {
            loadPage(PageDirection.EARLIER)
        }
        if (
            position >= images.lastIndex - LOAD_THRESHOLD &&
            hasLatestImages &&
            !isLoadingLatestImages
        ) {
            loadPage(PageDirection.LATEST)
        }
    }

    private fun loadPage(direction: PageDirection) {
        val anchor = when (direction) {
            PageDirection.EARLIER -> images.first()
            PageDirection.LATEST -> images.last()
        }
        val anchorId = anchor.imageId ?: run {
            markComplete(direction)
            return
        }

        setLoading(direction, true)
        lifecycleScope.launch {
            try {
                val token = accountStorage.getCurrentToken() ?: return@launch
                messageRepository.getImageMessageList(
                    token = token,
                    chatId = chatId,
                    chatType = chatType,
                    imageId = anchorId,
                    earlierQuantities = if (direction == PageDirection.EARLIER) PAGE_SIZE else 0,
                    latestQuantities = if (direction == PageDirection.LATEST) PAGE_SIZE else 0
                ).onSuccess { page ->
                    addPage(direction, page)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load $direction image page", error)
                }
            } finally {
                setLoading(direction, false)
                viewPager2.post { maybeLoadMore(viewPager2.currentItem) }
            }
        }
    }

    private fun addPage(direction: PageDirection, page: List<ConversationImage>) {
        val adapter = viewPager2.adapter as? OpenImageFragmentStateAdapter ?: return
        val newImages = page.filter { image ->
            image.messageId.isNotBlank() && image.messageId !in knownMessageIds
        }
        if (newImages.isEmpty()) {
            markComplete(direction)
            return
        }

        val viewerImages = newImages.map { image ->
            ViewerImage(
                messageId = image.messageId,
                imageId = image.sequence,
                url = image.url
            )
        }
        val openImageItems = newImages.map { image ->
            imageMessagePreviewItem(
                url = image.url,
                messageId = image.messageId,
                imageId = image.sequence
            )
        }
        knownMessageIds.addAll(newImages.map { it.messageId })

        when (direction) {
            PageDirection.EARLIER -> {
                images.addAll(0, viewerImages)
                adapter.addFrontData(openImageItems)
            }
            PageDirection.LATEST -> {
                images.addAll(viewerImages)
                adapter.addData(openImageItems)
            }
        }
    }

    private fun markComplete(direction: PageDirection) {
        when (direction) {
            PageDirection.EARLIER -> hasEarlierImages = false
            PageDirection.LATEST -> hasLatestImages = false
        }
    }

    private fun setLoading(direction: PageDirection, isLoading: Boolean) {
        when (direction) {
            PageDirection.EARLIER -> isLoadingEarlierImages = isLoading
            PageDirection.LATEST -> isLoadingLatestImages = isLoading
        }
    }

    override fun onDestroy() {
        if (callbackRegistered) {
            viewPager2.unregisterOnPageChangeCallback(pageChangeCallback)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MurexideImageViewer"

        const val EXTRA_VIEWER_OPTIONS = "murexide_open_image_options"
        const val EXTRA_IMAGE_URLS = "murexide_open_image_urls"
        const val EXTRA_IMAGE_MESSAGE_IDS = "murexide_open_image_message_ids"
        const val EXTRA_IMAGE_IDS = "murexide_open_image_ids"
        const val EXTRA_CHAT_ID = "murexide_open_image_chat_id"
        const val EXTRA_CHAT_TYPE = "murexide_open_image_chat_type"

        private const val PRELOAD_RADIUS = 1
        private const val LOAD_THRESHOLD = 1
        private const val PAGE_SIZE = 20
    }

    private val paginationEnabled: Boolean
        get() = chatId.isNotBlank() && chatType in 1..3 && images.any { it.imageId != null }

    private data class ViewerImage(
        val messageId: String?,
        val imageId: Long?,
        val url: String
    )

    private enum class PageDirection {
        EARLIER,
        LATEST
    }
}
