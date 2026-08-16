package com.juhao.murexide.ui.chat

import com.juhao.murexide.data.ForwardTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardUiStateTest {
    @Test
    fun `pending source count is zero at and beyond the completed cursor`() {
        val sourceIds = listOf("first", "second")

        assertEquals(
            2,
            ForwardUiState(sourceMsgIds = sourceIds, nextSourceIndex = 0).pendingSourceCount
        )
        assertEquals(
            0,
            ForwardUiState(sourceMsgIds = sourceIds, nextSourceIndex = sourceIds.size)
                .pendingSourceCount
        )
        assertEquals(
            0,
            ForwardUiState(sourceMsgIds = sourceIds, nextSourceIndex = sourceIds.size + 1)
                .pendingSourceCount
        )
    }

    @Test
    fun `selected targets keep target order while filtering uses normalized query`() {
        val targets = listOf(
            target("one", "Alice", "Alice original"),
            target("two", "Bob", "Bob original")
        )
        val state = ForwardUiState(
            targets = targets,
            query = " alice ",
            selectedKeys = setOf(targets[1].key, targets[0].key)
        )

        assertEquals(listOf("one"), state.filteredTargets.map { it.chatId })
        assertEquals(listOf("one", "two"), state.selectedTargets.map { it.chatId })
    }

    @Test
    fun `can send only when a selected target and a pending source are available`() {
        val target = target("one", "Alice", "Alice")
        val ready = ForwardUiState(
            targets = listOf(target),
            selectedKeys = setOf(target.key),
            sourceMsgIds = listOf("message")
        )

        assertTrue(ready.canSend)
        assertFalse(ready.copy(selectedKeys = emptySet()).canSend)
        assertFalse(ready.copy(nextSourceIndex = 1).canSend)
        assertFalse(ready.copy(isSending = true).canSend)
        assertFalse(ready.copy(isCompleted = true).canSend)
    }

    @Test
    fun `blank query exposes all targets including an empty target list`() {
        val targets = listOf(target("one", "Alice", "Alice"))

        assertEquals(targets, ForwardUiState(targets = targets, query = " \t").filteredTargets)
        assertTrue(ForwardUiState(query = "anything").filteredTargets.isEmpty())
    }

    private fun target(id: String, name: String, searchText: String) = ForwardTarget(
        chatId = id,
        chatType = 1,
        displayName = name,
        avatarUrl = "",
        searchText = searchText
    )
}
