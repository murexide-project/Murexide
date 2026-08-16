package com.juhao.murexide.utils

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.juhao.murexide.data.MentionToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionUtilsWorker1Test {
    @Test
    fun `valid mentions are sorted and overlapping or stale ranges are rejected`() {
        val text = "@Bob @Ann @Bob"
        val validBob = MentionToken("bob-1", "Bob", 0, 4)
        val validAnn = MentionToken("ann", "Ann", 5, 9)
        val secondBob = MentionToken("bob-2", "Bob", 10, 14)
        val stale = MentionToken("wrong", "Bob", 10, 13)
        val overlapping = MentionToken("overlap", "Bob", 1, 5)

        val mentions = MentionUtils.validMentions(
            text,
            listOf(secondBob, stale, overlapping, validAnn, validBob)
        )

        assertEquals(listOf(validBob, validAnn, secondBob), mentions)
        assertEquals(listOf("bob-1", "ann", "bob-2"), MentionUtils.mentionedUserIds(text, mentions))
    }

    @Test
    fun `duplicate mention ids are returned once in text order`() {
        val text = "@Bob @Bob"
        val mentions = listOf(
            MentionToken("same-user", "Bob", 0, 4),
            MentionToken("same-user", "Bob", 5, 9)
        )

        assertEquals(listOf("same-user"), MentionUtils.mentionedUserIds(text, mentions))
    }

    @Test
    fun `inserting at a trigger replaces only the trigger and shifts later mentions`() {
        val oldText = "@ hello @Bob "
        val bob = MentionToken("bob", "Bob", start = 8, endExclusive = 12)

        val result = MentionUtils.insertMention(
            text = oldText,
            mentions = listOf(bob),
            userId = "ann",
            displayName = "Ann",
            triggerPos = 0
        )

        assertEquals("@Ann  hello @Bob ", result.text)
        assertEquals(
            listOf(
                MentionToken("ann", "Ann", 0, 4),
                MentionToken("bob", "Bob", 12, 16)
            ),
            result.mentions
        )
        assertEquals(TextRange(5), result.selection)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inserting an empty display name is rejected`() {
        MentionUtils.insertMention(
            text = "",
            mentions = emptyList(),
            userId = "user",
            displayName = ""
        )
    }

    @Test
    fun `same text edit clamps selection to the nearest mention boundary`() {
        val text = "@Ann message"
        val mention = MentionToken("ann", "Ann", 0, 4)

        val result = MentionUtils.processEdit(
            old = TextFieldValue(text, TextRange(2)),
            new = TextFieldValue(text, TextRange(2)),
            mentions = listOf(mention)
        )

        assertEquals(TextRange(4), result.value.selection)
        assertEquals(listOf(mention), result.mentions)
        assertEquals("", result.insertedText)
        assertEquals(-1, result.insertPos)
    }

    @Test
    fun `insertion inside a mention is moved after the atomic token`() {
        val oldText = "@Ann"
        val mention = MentionToken("ann", "Ann", 0, 4)

        val result = MentionUtils.processEdit(
            old = TextFieldValue(oldText, TextRange(2)),
            new = TextFieldValue("@AXnn", TextRange(3)),
            mentions = listOf(mention)
        )

        assertEquals("@AnnX", result.value.text)
        assertEquals("X", result.insertedText)
        assertEquals(4, result.insertPos)
        assertEquals(TextRange(5), result.value.selection)
        assertEquals(listOf(mention), result.mentions)
    }

    @Test
    fun `invalid edit metadata falls back to the actual changed window`() {
        val result = MentionUtils.processEdit(
            old = TextFieldValue("Hello world", TextRange(6)),
            new = TextFieldValue("Hello brave world", TextRange(12)),
            mentions = emptyList(),
            textEdit = MentionUtils.TextEdit(start = 100, beforeCount = 1, afterCount = 0)
        )

        assertEquals("Hello brave world", result.value.text)
        assertEquals("brave ", result.insertedText)
        assertEquals(6, result.insertPos)
        assertEquals(TextRange(12), result.value.selection)
    }

    @Test
    fun `replace range clamps reversed and out of bounds selection`() {
        val result = MentionUtils.replaceRange(
            text = "abcdef",
            mentions = emptyList(),
            selection = TextRange(20, 2),
            replacement = "X"
        )

        assertEquals("abX", result.text)
        assertEquals(TextRange(3), result.selection)
        assertTrue(result.mentions.isEmpty())
    }
}
