package com.juhao.murexide.ui.chat.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import com.juhao.murexide.data.DefaultEmoji
import com.juhao.murexide.data.DefaultEmojiBitmapCache
import com.juhao.murexide.data.DefaultEmojiParser
import com.juhao.murexide.data.DefaultEmojiMatch
import com.juhao.murexide.data.MentionToken
import com.juhao.murexide.utils.MentionUtils
import kotlin.math.roundToInt

private class DefaultEmojiSpan(
    val emojiName: String,
    private val bitmap: Bitmap?,
    private val placeholderWidth: Int,
    private val placeholderHeight: Int
) : ReplacementSpan() {
    private var cachedWidth: Int = -1
    private var cachedHeight: Int = -1
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val width: Int
        get() {
            if (cachedWidth < 0) cachedWidth = bitmap?.width ?: placeholderWidth
            return cachedWidth
        }

    private val height: Int
        get() {
            if (cachedHeight < 0) cachedHeight = bitmap?.height ?: placeholderHeight
            return cachedHeight
        }

    val isPlaceholder: Boolean
        get() = bitmap == null

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = width

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val drawTop = top + (bottom - top - height) / 2f
        val image = bitmap
        if (image != null && !image.isRecycled) {
            canvas.drawBitmap(image, x, drawTop, paint)
        } else {
            placeholderPaint.color = paint.color and 0x22FFFFFF
            canvas.drawRoundRect(
                x,
                drawTop,
                x + width,
                drawTop + height,
                height / 4f,
                height / 4f,
                placeholderPaint
            )
        }
    }
}

internal class DefaultEmojiEditText(context: Context) : AppCompatEditText(context) {
    private var ready = false
    private var internalChange = false
    private var textChangeInProgress = false
    private var pendingTextEdit: MentionUtils.TextEdit? = null
    private var previousValue = TextFieldValue("")
    private var currentMentions: List<MentionToken> = emptyList()
    private var currentEmojis: List<DefaultEmoji> = emptyList()
    private var focused: () -> Unit = {}
    private var valueChanged: (
        value: TextFieldValue,
        mentions: List<MentionToken>,
        insertedText: String,
        insertPosition: Int
    ) -> Unit = { _, _, _, _ -> }

    private var cachedEmojiHeight = 24
    private var currentEmojiMatches: List<DefaultEmojiMatch> = emptyList()
    private var currentProtectedRanges: List<TextRange> = emptyList()
    private var currentEmojisByName: Map<String, DefaultEmoji> = emptyMap()
    private val pendingEmojiLoads = HashMap<String, kotlinx.coroutines.Deferred<Bitmap?>>()
    private var refreshPosted = false
    private var spansInitialized = false
    private var lastEmojiHeight = -1
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

                val oldText = previousValue.text
                val rawText = editable.toString()
                val rawValue = TextFieldValue(
                    text = rawText,
                    selection = currentSelection(editable.length)
                )
                val result = MentionUtils.processEdit(
                    old = previousValue,
                    new = rawValue,
                    mentions = currentMentions,
                    protectedRanges = currentProtectedRanges,
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
                if (textWasCorrected) {
                    currentEmojiMatches = DefaultEmojiParser.findMatches(
                        result.value.text,
                        currentEmojis
                    )
                    refreshProtectedRanges()
                    rebuildEmojiSpans(editable, currentEmojiMatches)
                    spansInitialized = true
                } else {
                    updateEmojiSpans(editable, oldText, result.value.text)
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

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(target, false) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                return deleteSingleDefaultEmoji(beforeLength, afterLength) ||
                    super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                return deleteSingleDefaultEmoji(beforeLength, afterLength) ||
                    super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val deletedEmoji = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> deleteSingleDefaultEmoji(beforeLength = 1, afterLength = 0)
            KeyEvent.KEYCODE_FORWARD_DEL ->
                deleteSingleDefaultEmoji(beforeLength = 0, afterLength = 1)
            else -> false
        }
        return deletedEmoji || super.onKeyDown(keyCode, event)
    }

    private fun deleteSingleDefaultEmoji(beforeLength: Int, afterLength: Int): Boolean {
        if (beforeLength <= 0 && afterLength <= 0) return false
        val cursor = selectionStart
        if (cursor < 0 || cursor != selectionEnd) return false

        val range = when {
            beforeLength > 0 -> currentProtectedRanges.lastOrNull { it.end == cursor }
            afterLength > 0 -> currentProtectedRanges.firstOrNull { it.start == cursor }
            else -> null
        } ?: return false
        editableText.delete(range.start, range.end)
        return true
    }

    fun bind(
        value: TextFieldValue,
        mentions: List<MentionToken>,
        emojis: List<DefaultEmoji>,
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
        val emojisChanged = currentEmojis !== emojis
        currentEmojis = emojis
        if (emojisChanged) currentEmojisByName = emojis.associateBy(DefaultEmoji::name)
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

        cachedEmojiHeight = (paint.textSize * 1.2f).roundToInt().coerceAtLeast(1)

        val currentText = text?.toString().orEmpty()
        val textChanged = currentText != value.text
        val heightChanged = lastEmojiHeight != cachedEmojiHeight
        internalChange = true
        if (textChanged) {
            replaceTextPreservingSpans(
                editable = editableText,
                oldText = currentText,
                newText = value.text
            )
        }
        if (emojisChanged || textChanged || heightChanged || !spansInitialized) {
            if (emojisChanged || heightChanged || !spansInitialized) {
                currentEmojiMatches = DefaultEmojiParser.findMatches(value.text, currentEmojis)
                refreshProtectedRanges()
                rebuildEmojiSpans(editableText, currentEmojiMatches)
            } else {
                updateEmojiSpans(editableText, currentText, value.text)
            }
            spansInitialized = true
            lastEmojiHeight = cachedEmojiHeight
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
            mentions = oldMentions,
            protectedRanges = currentProtectedRanges
        )

        if (result.value.selection.start != selectionStart || result.value.selection.end != selectionEnd) {
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

    private fun rebuildEmojiSpans(
        editable: Editable?,
        matches: List<DefaultEmojiMatch>
    ) {
        if (editable == null) return
        editable.getSpans(0, editable.length, DefaultEmojiSpan::class.java)
            .forEach(editable::removeSpan)
        matches.forEach { match -> addEmojiSpan(editable, match) }
    }

    private fun updateEmojiSpans(
        editable: Editable?,
        oldText: String,
        newText: String
    ) {
        if (editable == null) return
        if (oldText == newText && spansInitialized) return

        val oldMatches = currentEmojiMatches
        val window = DefaultEmojiParser.editWindow(oldText, newText)
        currentEmojiMatches = DefaultEmojiParser.updateMatchesIncrementally(
            oldText = oldText,
            newText = newText,
            oldMatches = oldMatches,
            emojis = currentEmojis
        )
        refreshProtectedRanges()

        val existing = editable
            .getSpans(0, editable.length, DefaultEmojiSpan::class.java)
            .toList()
        existing.forEach { span ->
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (
                end <= start ||
                (start < window.newEndExclusive && end > window.newStart)
            ) {
                editable.removeSpan(span)
            }
        }

        currentEmojiMatches.forEach { match ->
            if (match.start >= window.newStart && match.start < window.newEndExclusive) {
                addEmojiSpan(editable, match)
            }
        }
        spansInitialized = true
    }

    private fun addEmojiSpan(editable: Editable, match: DefaultEmojiMatch) {
        if (match.start < 0 || match.endExclusive > editable.length || match.start >= match.endExclusive) {
            return
        }
        val bitmap = DefaultEmojiBitmapCache.get(match.emoji, cachedEmojiHeight)
        editable.setSpan(
            DefaultEmojiSpan(
                emojiName = match.emoji.name,
                bitmap = bitmap,
                placeholderWidth = cachedEmojiHeight,
                placeholderHeight = cachedEmojiHeight
            ),
            match.start,
            match.endExclusive,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (bitmap == null) requestEmojiBitmap(match.emoji)
    }

    private fun requestEmojiBitmap(emoji: DefaultEmoji) {
        val key = DefaultEmojiBitmapCache.cacheKey(emoji.assetPath, cachedEmojiHeight)
        if (pendingEmojiLoads.containsKey(key)) return
        val deferred = DefaultEmojiBitmapCache.requestAsync(
            context.assets,
            emoji,
            cachedEmojiHeight
        )
        pendingEmojiLoads[key] = deferred
        deferred.invokeOnCompletion {
            post {
                pendingEmojiLoads.remove(key)
                scheduleReadySpanRefresh()
            }
        }
    }

    private fun scheduleReadySpanRefresh() {
        if (refreshPosted) return
        refreshPosted = true
        post {
            refreshPosted = false
            val editable = editableText ?: return@post
            val placeholders = editable
                .getSpans(0, editable.length, DefaultEmojiSpan::class.java)
                .filter(DefaultEmojiSpan::isPlaceholder)
                .toList()
            var replacedAny = false
            placeholders.forEach { span ->
                val start = editable.getSpanStart(span)
                val end = editable.getSpanEnd(span)
                val emoji = currentEmojisByName[span.emojiName]
                val bitmap = emoji?.let { DefaultEmojiBitmapCache.get(it, cachedEmojiHeight) }
                if (start in 0..<end && bitmap != null) {
                    editable.removeSpan(span)
                    editable.setSpan(
                        DefaultEmojiSpan(
                            emojiName = span.emojiName,
                            bitmap = bitmap,
                            placeholderWidth = cachedEmojiHeight,
                            placeholderHeight = cachedEmojiHeight
                        ),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    replacedAny = true
                }
            }
            if (replacedAny) {
                requestLayout()
                invalidate()
            }
        }
    }

    private fun refreshProtectedRanges() {
        currentProtectedRanges = currentEmojiMatches.map { match ->
            TextRange(match.start, match.endExclusive)
        }
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
internal fun DefaultEmojiTextField(
    value: TextFieldValue,
    mentions: List<MentionToken>,
    emojis: List<DefaultEmoji>,
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
    val editorHolder = remember { arrayOfNulls<DefaultEmojiEditText>(1) }

    Box(
        modifier = modifier,
        propagateMinConstraints = true
    ) {
        AndroidView(
            factory = { context ->
                DefaultEmojiEditText(context).apply {
                    hint = "输入消息..."
                    editorHolder[0] = this
                }
            },
            update = { editor ->
                editor.bind(
                    value = value,
                    mentions = mentions,
                    emojis = emojis,
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