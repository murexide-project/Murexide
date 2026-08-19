package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiMatch
import com.juhao.murexide.data.DefaultEmojiParser
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSelectionContainer
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun BatchedDefaultEmojiText(
    text: String,
    timestampText: String,
    emojis: List<DefaultEmoji>,
    bodyStyle: TextStyle,
    timestampStyle: TextStyle,
    modifier: Modifier = Modifier,
    enableSelection: Boolean = false,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val matches = remember(text, emojis) {
        DefaultEmojiParser.findMatches(text, emojis)
    }
    val targetHeightPx = with(density) {
        val fontSize = bodyStyle.fontSize.takeIf { it.type == TextUnitType.Sp } ?: 14.sp
        (fontSize.toPx() * 1.2f).roundToInt().coerceAtLeast(1)
    }
    val bitmaps = rememberDefaultEmojiBitmaps(context, matches, targetHeightPx)
    val imageBitmaps = remember(bitmaps) {
        bitmaps.mapValues { (_, bitmap) -> bitmap.asImageBitmap() }
    }

    val annotatedString = remember(text, timestampText, matches, timestampStyle, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0
            matches.forEachIndexed { index, match ->
                if (match.start > lastIndex) {
                    appendWithLinks(text.substring(lastIndex, match.start), linkColor)
                }
                appendInlineContent("emoji_$index", match.emoji.assetPath)
                lastIndex = match.endExclusive
            }
            if (lastIndex < text.length) {
                appendWithLinks(text.substring(lastIndex), linkColor)
            }
            if (timestampText.isNotEmpty()) {
                append(' ')
                withStyle(timestampStyle.toSpanStyle()) {
                    append(timestampText)
                }
            }
        }
    }

    val inlineContent = remember(matches) {
        List(matches.size) { index ->
            "emoji_$index" to InlineTextContent(
                Placeholder(
                    width = 1.2.em,
                    height = 1.2.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }.toMap()
    }

    val layoutState = remember { BatchedEmojiLayoutState() }

    val basicText = @Composable {
        BasicText(
            text = annotatedString,
            style = bodyStyle,
            inlineContent = inlineContent,
            onTextLayout = { layoutResult ->
                layoutState.layoutResult = layoutResult
                layoutState.matches = matches
            },
            modifier = Modifier.drawWithContent {
                drawContent()
                val layoutResult = layoutState.layoutResult ?: return@drawWithContent
                layoutResult.placeholderRects.forEachIndexed { index, rect ->
                    val safeRect = rect ?: return@forEachIndexed
                    val match = layoutState.matches.getOrNull(index) ?: return@forEachIndexed
                    val image = imageBitmaps[match.emoji.assetPath] ?: return@forEachIndexed
                    val scale = min(
                        safeRect.width / image.width.coerceAtLeast(1),
                        safeRect.height / image.height.coerceAtLeast(1)
                    )
                    val imageWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
                    val imageHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(
                            (safeRect.left + (safeRect.width - imageWidth) / 2f).roundToInt(),
                            (safeRect.top + (safeRect.height - imageHeight) / 2f).roundToInt()
                        ),
                        dstSize = IntSize(imageWidth, imageHeight),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        )
    }

    if (enableSelection) {
        LiquidGlassSelectionContainer(
            modifier = modifier
        ) {
            basicText()
        }
    } else {
        Box(modifier = modifier) {
            basicText()
        }
    }
}

private val URL_PATTERN = Regex(
    pattern = "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|www\\.[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
    option = RegexOption.IGNORE_CASE
)

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithLinks(
    text: String,
    linkColor: Color
) {
    var lastIndex = 0
    URL_PATTERN.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }

        val url = match.value
        val fullUrl = if (url.startsWith("www.")) "https://$url" else url

        withLink(
            LinkAnnotation.Url(
                url = fullUrl,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            append(url)
        }

        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

private class BatchedEmojiLayoutState {
    var layoutResult: TextLayoutResult? = null
    var matches: List<DefaultEmojiMatch> = emptyList()
}