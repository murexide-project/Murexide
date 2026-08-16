package com.juhao.murexide.ui.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationViewModelStateTest {
    @Test
    fun `creation kinds expose the API chat type and screen title`() {
        assertEquals(2, CreationKind.GROUP.chatType)
        assertEquals("创建群聊", CreationKind.GROUP.title)
        assertEquals(3, CreationKind.BOT.chatType)
        assertEquals("创建机器人", CreationKind.BOT.title)
    }

    @Test
    fun `blank names remain available for validation without starting async operations`() {
        val blank = CreationUiState(name = "")
        val whitespace = CreationUiState(name = " \t\n")

        assertEquals(true, blank.name.isBlank())
        assertEquals(true, whitespace.name.isBlank())
        assertFalse(blank.isCreating)
        assertFalse(whitespace.isCreating)
    }

    @Test
    fun `name and introduction updates clear a previous validation error`() {
        val viewModel = viewModel()
        viewModel.create()
        assertEquals("请输入名称", viewModel.uiState.value.error)

        viewModel.updateName("New group")
        assertEquals("New group", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.error)

        viewModel.updateIntroduction("A short introduction")
        assertEquals("A short introduction", viewModel.uiState.value.introduction)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `privacy toggle changes only privacy state`() {
        val viewModel = viewModel()
        viewModel.updateName("Group")
        viewModel.updateIntroduction("Intro")
        viewModel.updatePrivate(true)

        assertEquals("Group", viewModel.uiState.value.name)
        assertEquals("Intro", viewModel.uiState.value.introduction)
        assertTrue(viewModel.uiState.value.isPrivate)
        assertFalse(viewModel.uiState.value.isCreating)
        assertFalse(viewModel.uiState.value.isUploadingAvatar)
    }

    @Test
    fun `creation UI state copies editable fields without activating async operations`() {
        val edited = CreationUiState().copy(
            name = "New group",
            introduction = "A short introduction",
            isPrivate = true
        )

        assertEquals("New group", edited.name)
        assertEquals("A short introduction", edited.introduction)
        assertTrue(edited.isPrivate)
        assertFalse(edited.isUploadingAvatar)
        assertFalse(edited.isCreating)
        assertEquals(0f, edited.uploadProgress)
    }

    @Test
    fun `a validation error is represented until a reducer clears it`() {
        val invalid = CreationUiState(error = "请输入名称")
        val corrected = invalid.copy(name = "New group", error = null)

        assertEquals("请输入名称", invalid.error)
        assertEquals("New group", corrected.name)
        assertNull(corrected.error)
    }

    private fun viewModel() = CreationViewModel(
        token = "token",
        kind = CreationKind.GROUP
    )
}