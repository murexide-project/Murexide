package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.juhao.murexide.ui.theme.liquidglass.LiquidGlassSelectionContainer

@Composable
internal fun MessageText(
    text: String,
    timestampText: String,
    bodyStyle: TextStyle,
    timestampStyle: TextStyle,
    modifier: Modifier = Modifier,
    enableSelection: Boolean = false,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val annotatedString = remember(text, timestampText, timestampStyle, linkColor) {
        buildAnnotatedString {
            appendWithLinks(text, linkColor)
            if (timestampText.isNotEmpty()) {
                append(' ')
                withStyle(timestampStyle.toSpanStyle()) {
                    append(timestampText)
                }
            }
        }
    }

    val basicText = @Composable {
        BasicText(
            text = annotatedString,
            style = bodyStyle,
            modifier = Modifier
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