package com.juhao.murexide.ui.contact

import com.juhao.murexide.data.ContactItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactIndexTest {

    @Test
    fun `contact initial supports latin chinese and symbols`() {
        assertEquals('A', contactInitial("alice"))
        assertEquals('Z', contactInitial("张三"))
        assertEquals('L', contactInitial("李四"))
        assertEquals('#', contactInitial("  123"))
        assertEquals('#', contactInitial(""))
    }

    @Test
    fun `sections are ordered by initial and keep symbols last`() {
        val sections = buildContactSections(
            listOf(
                contact(id = "4", name = "_服务号"),
                contact(id = "3", name = "张三"),
                contact(id = "2", name = "李四"),
                contact(id = "1", name = "Alice")
            )
        )

        assertEquals(listOf('A', 'L', 'Z', '#'), sections.map { it.initial })
    }

    @Test
    fun `fast index resolves missing letter to the next section`() {
        val available = listOf('A', 'L', 'Z', '#')

        assertEquals('L', closestAvailableInitial('B', available))
        assertEquals('Z', closestAvailableInitial('Y', available))
        assertEquals('#', closestAvailableInitial('#', available))
    }

    private fun contact(id: String, name: String) = ContactItem(
        chatId = id,
        chatType = 1,
        remark = null,
        avatarUrl = "",
        permissionLevel = 0,
        noDisturb = false,
        name = name
    )
}
