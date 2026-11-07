package com.juhao.murexide.ui.components

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.ui.components.litehtml.LiteHtmlView
import com.juhao.murexide.ui.components.litehtml.HtmlRenderPolicy
import com.juhao.murexide.utils.UrlSchemeHandler

/** Renders a static YunHu HTML message with litehtml and no browser runtime. */
@Composable
fun LiteHtmlContent(
    htmlContent: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onImageClick: ((String) -> Unit)? = null,
    onLinkClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val darkTheme = isSystemInDarkTheme()
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val fontSize = (14f * density.fontScale).coerceAtLeast(12f)

    val css = remember(
        backgroundColor,
        textColor,
        linkColor,
        codeColor,
        borderColor,
        fontSize,
        darkTheme
    ) {
        buildMessageCss(
            backgroundColor = backgroundColor,
            textColor = textColor,
            linkColor = linkColor,
            codeColor = codeColor,
            borderColor = borderColor,
            fontSize = fontSize,
            darkTheme = darkTheme
        )
    }
    val resolvedLinkClick = onLinkClick ?: remember(context) {
        { url: String -> handleStaticHtmlLink(context, url) }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        factory = { ctx ->
            LiteHtmlView(ctx).also {
                it.updateContent(
                    html = htmlContent,
                    css = css,
                    defaultFontSizeCssPx = fontSize.toInt(),
                    onImageClick = onImageClick,
                    onLinkClick = resolvedLinkClick
                )
            }
        },
        onReset = {
            it.updateCallbacks(onImageClick = null, onLinkClick = null)
        },
        onRelease = LiteHtmlView::release,
        update = {
            it.updateContent(
                html = htmlContent,
                css = css,
                defaultFontSizeCssPx = fontSize.toInt(),
                onImageClick = onImageClick,
                onLinkClick = resolvedLinkClick
            )
        }
    )
}

internal fun handleStaticHtmlLink(context: Context, url: String) {
    if (HtmlRenderPolicy.isInternalDeepLink(url)) {
        UrlSchemeHandler.handle(context, url)
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

internal fun buildMessageCss(
    backgroundColor: Color,
    textColor: Color,
    linkColor: Color,
    codeColor: Color,
    borderColor: Color,
    fontSize: Float,
    darkTheme: Boolean
): String = """
    * { box-sizing: border-box; }
    html, body { margin: 0; padding: 0; width: 100%; }
    body {
        background: ${backgroundColor.toCssHex()};
        color: ${textColor.toCssHex()};
        font-family: sans-serif;
        font-size: ${fontSize}px;
        line-height: 1.4;
        overflow-wrap: break-word;
        word-wrap: break-word;
        padding: 4px;
    }
    p, div, span, h1, h2, h3, h4, h5, h6, li, blockquote { color: ${textColor.toCssHex()}; }
    p { margin: 4px 0; }
    a { color: ${linkColor.toCssHex()}; text-decoration: none; overflow-wrap: anywhere; }
    img { max-width: 100%; height: auto; margin: 8px 0; border-radius: 8px; }
    pre {
        display: block;
        background: ${codeColor.toCssHex()};
        color: ${textColor.toCssHex()};
        padding: 12px 16px;
        border-radius: 8px;
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        font-family: monospace;
        margin: 8px 0;
        border: 1px solid ${borderColor.toCssHex()};
    }
    code {
        background: ${codeColor.toCssHex()};
        color: ${textColor.toCssHex()};
        padding: 2px 6px;
        border-radius: 4px;
        font-family: monospace;
        font-size: 0.9em;
    }
    pre code { padding: 0; background: transparent; }
    blockquote {
        border-left: 4px solid ${linkColor.toCssHex()};
        margin: 8px 0;
        padding: 8px 12px;
        background: ${if (darkTheme) "#2A2A2A" else "#F5F5F5"};
    }
    table { border-collapse: collapse; max-width: 100%; margin: 8px 0; }
    th, td { border: 1px solid ${borderColor.toCssHex()}; padding: 8px 12px; text-align: left; }
    th { background: ${codeColor.toCssHex()}; font-weight: 600; }
    ul, ol { padding-left: 24px; margin: 4px 0; }
    li { margin: 2px 0; }
    details { display: block; margin: 4px 0; }
    details > summary {
        display: block;
        cursor: pointer;
        font-weight: 600;
    }
    details > .murexide-details-content {
        display: block;
        height: 0;
        overflow: hidden;
    }
    h1, h2, h3, h4, h5, h6 { margin: 12px 0 6px; line-height: 1.3; font-weight: 600; }
    h1 { font-size: 1.7em; } h2 { font-size: 1.45em; } h3 { font-size: 1.25em; }
    h4 { font-size: 1.1em; } h5, h6 { font-size: 1em; }
    hr { border: 0; border-top: 1px solid ${borderColor.toCssHex()}; margin: 12px 0; }
""".trimIndent()

private fun Color.toCssHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
