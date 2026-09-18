package com.juhao.murexide.ui.chat.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.data.MentionToken
import com.juhao.murexide.utils.MentionUtils

internal class MentionEditText(context: Context) : AppCompatEditText(context) {
    private var ready = false
    private var internalChange = false
    private var textChangeInProgress = false
    private var pendingTextEdit: MentionUtils.TextEdit? = null
    private var previousValue = TextFieldValue("")
    private var currentMentions: List<MentionToken> = emptyList()
    private var focused: () -> Unit = {}
    private var valueChanged: (
        value: TextFieldValue,
        mentions: List<MentionToken>,
        insertedText: String,
        insertPosition: Int
    ) -> Unit = { _, _, _, _ -> }

    private var boundTextColor: Int? = null
    private var boundHintColor: Int? = null
    private var boundTextSizeSp = Float.NaN
    private var boundEnabled: Boolean? = null

    init {
        background = null
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        minLines = 1
        maxLines = 5
        isSingleLine = false
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        showSoftInputOnFocus = true
        isVerticalScrollBarEnabled = false
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_NONE

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                if (internalChange) return
                textChangeInProgress = true
                pendingTextEdit = MentionUtils.TextEdit(
                    start = start,
                    beforeCount = count,
                    afterCount = after
                )
                previousValue = TextFieldValue(
                    text = text?.toString().orEmpty(),
                    selection = currentSelection(text?.length ?: 0)
                )
            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(editable: Editable?) {
                if (internalChange || editable == null) return
                val textEdit = pendingTextEdit
                pendingTextEdit = null

                val rawText = editable.toString()
                val rawValue = TextFieldValue(
                    text = rawText,
                    selection = currentSelection(editable.length)
                )
                val result = MentionUtils.processEdit(
                    old = previousValue,
                    new = rawValue,
                    mentions = currentMentions,
                    textEdit = textEdit
                )

                val textWasCorrected = rawText != result.value.text
                internalChange = true
                if (textWasCorrected) {
                    replaceTextPreservingSpans(
                        editable = editable,
                        oldText = rawText,
                        newText = result.value.text
                    )
                }
                setSelectionSafely(result.value.selection, editable.length)
                internalChange = false
                textChangeInProgress = false

                currentMentions = result.mentions
                previousValue = result.value
                valueChanged(
                    result.value,
                    result.mentions,
                    result.insertedText,
                    result.insertPos
                )
            }
        })

        ready = true
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (ready && focused) this.focused()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            requestEditorFocus()
        }
        return handled
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        requestEditorFocus()
        return handled
    }

    fun requestEditorFocus() {
        post {
            if (!hasFocus()) requestFocus()
            if (hasFocus()) {
                val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
                inputMethodManager?.showSoftInput(this, 0)
            }
        }
    }

    fun bind(
        value: TextFieldValue,
        mentions: List<MentionToken>,
        enabled: Boolean,
        textColor: Color,
        cursorColor: Color,
        selectionColor: Color,
        hintColor: Color,
        textSizeSp: Float,
        onValueChanged: (
            value: TextFieldValue,
            mentions: List<MentionToken>,
            insertedText: String,
            insertPosition: Int
        ) -> Unit,
        onFocused: () -> Unit
    ) {
        currentMentions = mentions
        valueChanged = onValueChanged
        focused = onFocused

        if (boundEnabled != enabled) {
            isEnabled = enabled
            boundEnabled = enabled
        }
        val textColorArgb = textColor.toArgb()
        if (boundTextColor != textColorArgb) {
            setTextColor(textColorArgb)
            boundTextColor = textColorArgb
        }
        highlightColor = selectionColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = textCursorDrawable?.mutate()?.apply {
                setTint(cursorColor.toArgb())
            }
        }
        val hintColorArgb = hintColor.toArgb()
        if (boundHintColor != hintColorArgb) {
            setHintTextColor(hintColorArgb)
            boundHintColor = hintColorArgb
        }
        if (boundTextSizeSp != textSizeSp) {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            boundTextSizeSp = textSizeSp
        }

        val currentText = text?.toString().orEmpty()
        val textChanged = currentText != value.text
        internalChange = true
        if (textChanged) {
            replaceTextPreservingSpans(
                editable = editableText,
                oldText = currentText,
                newText = value.text
            )
        }
        setSelectionSafely(value.selection, value.text.length)
        internalChange = false
        textChangeInProgress = false
        pendingTextEdit = null
        previousValue = value
    }

    private fun replaceTextPreservingSpans(
        editable: Editable,
        oldText: String,
        newText: String
    ) {
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

        editable.replace(
            prefixLength,
            oldText.length - suffixLength,
            newText,
            prefixLength,
            newText.length - suffixLength
        )
    }

    override fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        super.onSelectionChanged(selectionStart, selectionEnd)
        if (
            !ready || internalChange || textChangeInProgress ||
            selectionStart < 0 || selectionEnd < 0
        ) return

        val currentText = text?.toString().orEmpty()

        val oldMentions = currentMentions
        val result = MentionUtils.processEdit(
            old = previousValue.copy(text = currentText),
            new = TextFieldValue(currentText, TextRange(selectionStart, selectionEnd)),
            mentions = oldMentions
        )

        if (result.value.selection.start != selectionStart ||
            result.value.selection.end != selectionEnd
        ) {
            internalChange = true
            setSelectionSafely(result.value.selection, currentText.length)
            internalChange = false
        }
        val changed = result.value.selection != previousValue.selection ||
            result.mentions != oldMentions
        previousValue = result.value
        currentMentions = result.mentions
        if (changed) valueChanged(result.value, result.mentions, "", -1)
    }

    private fun currentSelection(textLength: Int): TextRange {
        val safeStart = selectionStart.takeIf { it >= 0 }?.coerceIn(0, textLength) ?: textLength
        val safeEnd = selectionEnd.takeIf { it >= 0 }?.coerceIn(0, textLength) ?: safeStart
        return TextRange(safeStart, safeEnd)
    }

    private fun setSelectionSafely(selection: TextRange, textLength: Int) {
        val start = selection.start.coerceIn(0, textLength)
        val end = selection.end.coerceIn(0, textLength)
        if (this.selectionStart != start || this.selectionEnd != end) {
            setSelection(start, end)
        }
    }
}

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
    val editorHolder = remember { arrayOfNulls<MentionEditText>(1) }

    Box(
        modifier = modifier,
        propagateMinConstraints = true
    ) {
        AndroidView(
            factory = { context ->
                MentionEditText(context).apply {
                    hint = "输入消息..."
                    editorHolder[0] = this
                }
            },
            update = { editor ->
                editor.bind(
                    value = value,
                    mentions = mentions,
                    enabled = enabled,
                    textColor = textColor,
                    hintColor = hintColor,
                    cursorColor = cursorColor,
                    selectionColor = selectionColor,
                    textSizeSp = textSizeSp,
                    onValueChanged = onValueChange,
                    onFocused = onFocused
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.hasFocus) editorHolder[0]?.requestEditorFocus()
                }
        )
    }
}