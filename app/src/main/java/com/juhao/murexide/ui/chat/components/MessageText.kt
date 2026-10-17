package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Composable
internal fun MessageText(
    text: String,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    trailingReserveWidthPx: Int? = null
) {
    val density = LocalDensity.current
    val reserveId = "trailing_reserve"
    val hasReserve = trailingReserveWidthPx != null && trailingReserveWidthPx > 0

    val annotatedString = remember(text, linkColor, trailingReserveWidthPx) {
        buildAnnotatedString {
            appendWithLinks(text, linkColor)
            if (hasReserve) {
                appendInlineContent(reserveId, "\u00A0")
            }
        }
    }

    val inlineContent = remember(trailingReserveWidthPx, density) {
        if (hasReserve) {
            mapOf(
                reserveId to InlineTextContent(
                    Placeholder(
                        width = (trailingReserveWidthPx!! / density.density).sp,
                        height = 1.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom
                    )
                ) { }
            )
        } else {
            emptyMap()
        }
    }

    Box(modifier = modifier) {
        BasicText(
            text = annotatedString,
            style = bodyStyle,
            modifier = Modifier,
            inlineContent = inlineContent
        )
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