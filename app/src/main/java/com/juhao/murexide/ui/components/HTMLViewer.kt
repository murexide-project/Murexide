package com.juhao.murexide.ui.components

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.juhao.murexide.network.NetworkClient

@Composable
fun UnifiedHtmlWebView(
    htmlContent: String,
    modifier: Modifier = Modifier,
    bgColor: Color = MaterialTheme.colorScheme.surface,
    onImageClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = bgColor.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    val processedHtml = remember(htmlContent) {
        wrapImagesWithClickableLink(htmlContent)
    }

    val styledHtml = remember(
        processedHtml,
        backgroundColor,
        textColor,
        linkColor,
        codeBackgroundColor,
        isDarkTheme
    ) {
        generateStyledHtml(
            processedHtml,
            backgroundColor,
            textColor,
            linkColor,
            codeBackgroundColor,
            isDarkTheme
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webViewRef?.onPause()
                    webViewRef?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webViewRef?.onResume()
                    webViewRef?.resumeTimers()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    webViewRef?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                        destroy()
                    }
                    webViewRef = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            WebView(ctx).apply {
                setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)

                webViewClient = object : WebViewClient() {

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        url: String?
                    ): Boolean {
                        if (url == null) return true

                        if (url.startsWith("imageclick://")) {
                            val imageUrl = url.removePrefix("imageclick://")
                            onImageClick?.invoke(imageUrl)
                            return true
                        }

                        if (url.startsWith("yunhu://")) {
                            com.juhao.murexide.utils.UrlSchemeHandler.handle(context, url)
                            return true
                        }

                        try {
                            val finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                "https://$url"
                            } else {
                                url
                            }
                            val intent = Intent(Intent.ACTION_VIEW, finalUrl.toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // 无可用浏览器
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString()
                            ?: return super.shouldInterceptRequest(view, request)

                        if (url.startsWith("https://chat-img.jwznb.com")) {
                            try {
                                val req = okhttp3.Request.Builder()
                                    .url(url)
                                    .header("Referer", "https://myapp.jwznb.com")
                                    .build()
                                val resp = NetworkClient.okHttpClient.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val contentType =
                                        (resp.header("Content-Type") ?: "image/*")
                                            .substringBefore(';').trim()
                                    return WebResourceResponse(
                                        contentType,
                                        null,
                                        resp.body.byteStream()
                                    )
                                }
                                resp.close()
                            } catch (_: Exception) {
                                // fallback to default
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadsImagesAutomatically = true
                    blockNetworkImage = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    offscreenPreRaster = false
                }
                setBackgroundColor(backgroundColor)

                webViewRef = this
            }
        },
        update = { webView ->
            val tagKey = 0x7f0a0001
            val lastLoadedHtml = webView.getTag(tagKey) as? String
            if (lastLoadedHtml != styledHtml) {
                webView.setTag(tagKey, styledHtml)
                webView.loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun wrapImagesWithClickableLink(html: String): String {
    val imgRegex = Regex(
        """<img[^>]*src\s*=\s*["']([^"']+)["'][^>]*/?>""",
        RegexOption.IGNORE_CASE
    )
    return imgRegex.replace(html) { match ->
        val src = match.groupValues[1]
        """<a href="imageclick://$src"><img src="$src" style="cursor:pointer;" /></a>"""
    }
}

private fun generateStyledHtml(
    htmlContent: String,
    backgroundColor: Int,
    textColor: Int,
    linkColor: Int,
    codeBackgroundColor: Int,
    isDark: Boolean
): String {
    val bgHex = String.format("#%06X", backgroundColor and 0xFFFFFF)
    val textHex = String.format("#%06X", textColor and 0xFFFFFF)
    val linkHex = String.format("#%06X", linkColor and 0xFFFFFF)
    val codeBgHex = String.format("#%06X", codeBackgroundColor and 0xFFFFFF)

    return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            body {
                background-color: $bgHex;
                color: $textHex;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 14px;
                line-height: 1.5;
                padding: 4px;
                word-wrap: break-word;
                overflow-wrap: break-word;
                -webkit-tap-highlight-color: transparent;
            }
            p, div, span, h1, h2, h3, h4, h5, h6, li, blockquote {
                color: $textHex;
            }
            a {
                color: $linkHex;
                text-decoration: none;
                word-break: break-all;
            }
            a:hover, a:active {
                text-decoration: underline;
            }
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 8px 0;
                border-radius: 8px;
                cursor: pointer;
            }
            pre {
                background-color: $codeBgHex;
                color: $textHex;
                padding: 12px 16px;
                border-radius: 8px;
                overflow-x: auto;
                font-family: 'Courier New', monospace;
                font-size: 14px;
                line-height: 1.5;
                margin: 8px 0;
                border: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
            }
            code {
                background-color: $codeBgHex;
                color: $textHex;
                padding: 2px 6px;
                border-radius: 4px;
                font-family: 'Courier New', monospace;
                font-size: 0.9em;
            }
            pre code {
                background-color: transparent;
                padding: 0;
            }
            blockquote {
                border-left: 4px solid $linkHex;
                margin: 8px 0;
                padding: 8px 12px;
                background-color: ${if (isDark) "#2a2a2a" else "#f5f5f5"};
                border-radius: 0 4px 4px 0;
                color: $textHex;
            }
            table {
                border-collapse: collapse;
                width: 100%;
                margin: 8px 0;
                font-size: 14px;
            }
            th, td {
                border: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
                padding: 8px 12px;
                text-align: left;
                color: $textHex;
            }
            th {
                background-color: $codeBgHex;
                font-weight: 600;
            }
            ul, ol {
                padding-left: 24px;
                margin: 4px 0;
            }
            li {
                margin: 2px 0;
            }
            h1, h2, h3, h4, h5, h6 {
                margin: 16px 0 8px 0;
                font-weight: 600;
                line-height: 1.3;
                color: $textHex;
            }
            h1 { font-size: 24px; }
            h2 { font-size: 20px; }
            h3 { font-size: 18px; }
            h4 { font-size: 16px; }
            h5, h6 { font-size: 14px; }
            hr {
                border: none;
                border-top: 1px solid ${if (isDark) "#3a3a3a" else "#e0e0e0"};
                margin: 12px 0;
            }
            ::selection {
                background-color: ${if (isDark) "#4a4a4a" else "#cce0ff"};
                color: $textHex;
            }
        </style>
    </head>
    <body>
        $processedHtml
    </body>
    </html>
    """.trimIndent()
}