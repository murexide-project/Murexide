package com.juhao.murexide.ui.contact

import com.juhao.murexide.data.ContactItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactIndexBoundaryTest {
    @Test
    fun `display name falls back from blank remark to name and then chat id`() {
        assertEquals(
            "Remark",
            contactDisplayName(contact("1", "Name", remark = "Remark"))
        )
        assertEquals(
            "Name",
            contactDisplayName(contact("2", "Name", remark = " \t"))
        )
        assertEquals(
            "3",
            contactDisplayName(contact("3", "", remark = null))
        )
    }

    @Test
    fun `sections remove duplicate keys but retain the same id in another chat type`() {
        val sections = buildContactSections(
            listOf(
                contact("same", "Alice", type = 1),
                contact("same", "Alice duplicate", type = 1),
                contact("same", "Alice bot", type = 3)
            )
        )

        assertEquals(2, sections.single().contacts.size)
        assertEquals(setOf(1, 3), sections.single().contacts.map { it.chatType }.toSet())
    }

    @Test
    fun `accented latin names use their latin initial`() {
        assertEquals('E', contactInitial(" Émile"))
        assertEquals('A', contactInitial("Åsa"))
    }

    @Test
    fun `closest initial reports no answer when no sections exist`() {
        assertNull(closestAvailableInitial('A', emptyList()))
        assertEquals('#', closestAvailableInitial('#', listOf('#')))
    }

    @Test
    fun `empty contacts produce no sections`() {
        assertEquals(emptyList<ContactSection>(), buildContactSections(emptyList()))
    }

    private fun contact(
        id: String,
        name: String,
        type: Int = 1,
        remark: String? = null
    ) = ContactItem(
        chatId = id,
        chatType = type,
        remark = remark,
        avatarUrl = "",
        permissionLevel = 0,
        noDisturb = false,
        name = name
    )
}
