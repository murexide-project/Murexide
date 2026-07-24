package com.juhao.murexide.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import coil.Coil
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.flyjingfish.openimagelib.beans.OpenImageUrl
import com.flyjingfish.openimagelib.enums.MediaType
import com.flyjingfish.openimagelib.listener.BigImageHelper
import com.flyjingfish.openimagelib.listener.DownloadMediaHelper
import com.flyjingfish.openimagelib.listener.OnDownloadMediaListener
import com.flyjingfish.openimagelib.listener.OnLoadBigImageListener
import com.flyjingfish.openimagelib.utils.SaveImageUtils

class MurexideBigImageHelper : BigImageHelper {
    @OptIn(ExperimentalCoilApi::class)
    override fun loadImage(
        context: Context,
        imageUrl: String?,
        listener: OnLoadBigImageListener
    ) {
        if (imageUrl.isNullOrBlank()) {
            listener.onLoadImageFailed()
            return
        }

        val imageLoader = Coil.imageLoader(context)
        val request = openImageRequest(context, imageUrl)
            .target(
                onError = { listener.onLoadImageFailed() },
                onSuccess = { drawable ->
                    listener.onLoadImageSuccess(
                        drawable,
                        imageLoader.cachedFilePath(imageUrl)
                    )
                }
            )
            .build()
        imageLoader.enqueue(request)
    }
}

/**
 * Warms Coil's memory/disk cache for a page before OpenImage displays it.
 * ViewPager2's off-screen page limit only creates adjacent fragments; it does
 * not reliably keep their full-image requests alive on every Android build.
 */
internal fun preloadOpenImage(context: Context, imageUrl: String) {
    if (imageUrl.isBlank()) return
    val request = openImageRequest(context.applicationContext, imageUrl).build()
    Coil.imageLoader(context).enqueue(request)
}

private fun openImageRequest(context: Context, imageUrl: String): ImageRequest.Builder {
    val displayMetrics = context.resources.displayMetrics
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .size(
            displayMetrics.widthPixels.coerceAtLeast(1),
            displayMetrics.heightPixels.coerceAtLeast(1)
        )
        .allowHardware(false)
}

class MurexideDownloadMediaHelper : DownloadMediaHelper {
    @OptIn(ExperimentalCoilApi::class)
    override fun download(
        activity: FragmentActivity,
        lifecycleOwner: LifecycleOwner,
        openImageUrl: OpenImageUrl,
        listener: OnDownloadMediaListener
    ) {
        val isVideo = openImageUrl.type == MediaType.VIDEO
        val downloadUrl = if (isVideo) openImageUrl.videoUrl else openImageUrl.imageUrl
        if (downloadUrl.isNullOrBlank()) {
            listener.onDownloadFailed()
            return
        }

        listener.onDownloadStart(false)
        val imageLoader = Coil.imageLoader(activity)
        val request = ImageRequest.Builder(activity)
            .data(downloadUrl)
            .lifecycle(lifecycleOwner)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .decoderFactory { result: SourceResult, _: coil.request.Options, _: ImageLoader ->
                Decoder {
                    SaveImageUtils.INSTANCE.saveFile(
                        activity,
                        result.source.file().toFile(),
                        isVideo
                    ) { savedPath ->
                        if (savedPath.isNullOrEmpty()) {
                            listener.onDownloadFailed()
                        } else {
                            listener.onDownloadSuccess(savedPath)
                        }
                    }
                    DecodeResult(ColorDrawable(Color.TRANSPARENT), false)
                }
            }
            .listener(object : ImageRequest.Listener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    listener.onDownloadFailed()
                }
            })
            .build()
        imageLoader.enqueue(request)
    }
}

@OptIn(ExperimentalCoilApi::class)
private fun ImageLoader.cachedFilePath(cacheKey: String): String? {
    val snapshot = runCatching { diskCache?.openSnapshot(cacheKey) }.getOrNull() ?: return null
    return try {
        snapshot.data.toFile().absolutePath
    } finally {
        snapshot.close()
    }
}
