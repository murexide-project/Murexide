package com.juhao.murexide.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
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
    
    var currentMentions by remember { mutableStateOf(mentions) }
    var currentProtectedRanges by remember { mutableStateOf<List<TextRange>>(emptyList()) }
    
    LaunchedEffect(mentions) {
        currentMentions = mentions
    }
    
    Box(
        modifier = modifier,
        propagateMinConstraints = true
    ) {
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val textEdit = MentionUtils.TextEdit(
                    start = minOf(newValue.selection.start, value.selection.start),
                    beforeCount = maxOf(0, value.text.length - newValue.text.length),
                    afterCount = maxOf(0, newValue.text.length - value.text.length)
                )
                
                val result = MentionUtils.processEdit(
                    old = value,
                    new = newValue,
                    mentions = currentMentions,
                    protectedRanges = currentProtectedRanges,
                    textEdit = textEdit
                )
                
                currentMentions = result.mentions
                
                onValueChange(
                    result.value,
                    result.mentions,
                    result.insertedText,
                    result.insertPos
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.hasFocus) {
                        onFocused()
                    }
                },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontSize = textSizeSp.sp
            ),
            cursorBrush = SolidColor(cursorColor),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "输入消息...",
                            color = hintColor,
                            fontSize = textSizeSp.sp,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}