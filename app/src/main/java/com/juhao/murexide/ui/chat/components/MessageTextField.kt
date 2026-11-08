package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.juhao.murexide.data.MentionToken
import com.juhao.murexide.utils.MentionUtils

@Composable
internal fun MessageTextField(
    value: TextFieldValue,
    mentions: List<MentionToken>,
    enabled: Boolean,
    textColor: Color,
    hintColor: Color,
    textSizeSp: Float,
    focusRequester: FocusRequester,
    onValueChange: (
        value: TextFieldValue,
        mentions: List<MentionToken>,
        insertedText: String,
        insertPosition: Int
    ) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cursorColor = MaterialTheme.colorScheme.primary
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)

    var displayValue by remember { mutableStateOf(value) }
    var committedValue by remember { mutableStateOf(value) }
    var committedMentions by remember { mutableStateOf(mentions) }

    LaunchedEffect(value) {
        if (committedValue.text != value.text ||
            committedValue.selection != value.selection ||
            committedMentions != mentions
        ) {
            displayValue = value
            committedValue = value
            committedMentions = mentions
        }
    }

    val selectionColors = remember(selectionColor) {
        TextSelectionColors(
            handleColor = selectionColor,
            backgroundColor = selectionColor
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = displayValue,
            onValueChange = { newValue ->
                val textEdit = computeTextEdit(committedValue, newValue)
                val result = if (textEdit != null) {
                    MentionUtils.processEdit(
                        old = committedValue,
                        new = newValue,
                        mentions = committedMentions,
                        textEdit = textEdit
                    )
                } else {
                    MentionUtils.processEdit(
                        old = committedValue,
                        new = newValue,
                        mentions = committedMentions
                    )
                }
            
                displayValue = result.value
                committedValue = result.value
                committedMentions = result.mentions
                onValueChange(
                    result.value,
                    result.mentions,
                    result.insertedText,
                    result.insertPos
                )
            },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) onFocused()
                },
            enabled = enabled,
            textStyle = TextStyle(
                color = textColor,
                fontSize = textSizeSp.sp,
                lineHeight = (textSizeSp * 1.45).sp
            ),
            cursorBrush = SolidColor(cursorColor),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.None,
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions.Default,
            singleLine = false,
            minLines = 1,
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (displayValue.text.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = "输入消息...",
                            color = hintColor,
                            fontSize = textSizeSp.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

private fun computeTextEdit(
    old: TextFieldValue,
    new: TextFieldValue
): MentionUtils.TextEdit? {
    val oldText = old.text
    val newText = new.text
    if (oldText == newText) return null

    val prefixLength = oldText.commonPrefixWith(newText).length
    var suffixLength = 0
    val maxSuffixLength = minOf(
        oldText.length - prefixLength,
        newText.length - prefixLength
    )
    while (
        suffixLength < maxSuffixLength &&
        oldText[oldText.lastIndex - suffixLength] == newText[newText.lastIndex - suffixLength]
    ) {
        suffixLength++
    }

    return MentionUtils.TextEdit(
        start = prefixLength,
        beforeCount = oldText.length - prefixLength - suffixLength,
        afterCount = newText.length - prefixLength - suffixLength
    )
}