package com.juhao.murexide.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UncoveredDataModelsWorker1Test {
    @Test
    fun `conversation derived fields prefer a nonblank remark and send timestamp`() {
        val withRemark = conversation(
            remark = "  Nickname  ",
            timestamp = 10L,
            sendTimestamp = 20L,
            unread = 1,
            at = 1
        )
        val withoutRemark = conversation(remark = " ", timestamp = 30L, sendTimestamp = 0L)

        assertEquals("  Nickname  ", withRemark.displayName)
        assertEquals(20L, withRemark.latestMessageTimestamp)
        assertTrue(withRemark.hasUnread)
        assertTrue(withRemark.isAtMentioned)
        assertEquals("chat", withoutRemark.displayName)
        assertEquals(30L, withoutRemark.latestMessageTimestamp)
        assertFalse(withoutRemark.hasUnread)
        assertFalse(withoutRemark.isAtMentioned)
    }

    @Test
    fun `unread total clamps negative values and integer overflow`() {
        val conversations = listOf(
            conversation(chatId = "negative", unread = -10),
            conversation(chatId = "large", unread = Int.MAX_VALUE),
            conversation(chatId = "small", unread = 4),
            conversation(chatId = "muted", unread = 100, doNotDisturb = 1)
        )

        assertEquals(Int.MAX_VALUE, conversations.unreadTotal())
        assertEquals(4, conversations.unreadTotal(ConversationKey("large", 1)))
        assertEquals(Int.MAX_VALUE, conversations.unreadTotal(ConversationKey("large", 2)))
    }

    @Test
    fun `forward target exposes a stable key and conversation conversion`() {
        val target = ForwardTarget(
            chatId = "group-1",
            chatType = 2,
            displayName = "Group",
            avatarUrl = "group.png"
        )

        assertEquals(2 to "group-1", target.key)
        assertEquals(
            ConversationItem(
                chatId = "group-1",
                chatType = 2,
                name = "Group",
                chatContent = "",
                timestampMs = 0L,
                avatarUrl = "group.png"
            ),
            target.toConversationItem()
        )
    }

    @Test
    fun `instruction web item conversion maps custom form and bot name`() {
        val source = InstructionWebListItem(
            id = 7L,
            botId = "bot-1",
            name = "weather",
            desc = "Get weather",
            instructionType = 5,
            hintText = "City",
            defaultText = "Beijing",
            customJson = "[{\"id\":\"city\"}]"
        )

        assertEquals(
            InstructionItem(
                id = 7L,
                botId = "bot-1",
                name = "weather",
                desc = "Get weather",
                type = 5,
                hintText = "City",
                defaultText = "Beijing",
                form = "[{\"id\":\"city\"}]",
                botName = "Weather bot"
            ),
            source.toInstructionItem("Weather bot")
        )
    }

    @Test
    fun `chat background response decodes nested list and keeps defaults`() {
        val response = Json.decodeFromString<ChatBackgroundListResponse>(
            """{"code":1,"msg":"ok","data":{"list":[{"id":9,"userId":"user","chatId":"all","imgUrl":"https://img.example/bg.jpg","createTime":10,"updateTime":11}]}}"""
        )

        assertEquals(1, response.code)
        assertEquals("ok", response.msg)
        assertEquals(1, response.data?.list?.size)
        assertEquals("all", response.data?.list?.single()?.chatId)
        assertEquals("https://img.example/bg.jpg", response.data?.list?.single()?.imgUrl)
        assertEquals(ChatBackgroundItem(), ChatBackgroundItem())
        assertEquals(emptyList<ChatBackgroundItem>(), ChatBackgroundListData().list)
    }

    @Test
    fun `community detail model applies optional group and counter defaults`() {
        val response = Json.decodeFromString<PostDetailResponse>(
            """{"code":1,"msg":"success","data":{"post":{"id":3,"baId":4,"senderId":"user","senderNickname":"User","senderAvatar":"avatar","title":"Title","content":"Body","contentType":1,"createTimeText":"now","likeNum":0,"commentNum":0,"collectNum":0}}}"""
        )
        val post = response.data!!.post

        assertEquals(3, post.id)
        assertEquals("Body", post.content)
        assertNull(post.group)
        assertEquals(0, post.isLiked)
        assertEquals(0, post.isReward)
        assertFalse(response.data!!.isAdmin != 0)
    }

    @Test
    fun `small data models retain identifiers and default group member state`() {
        val image = ConversationImage("message", "image.jpg", sequence = 4L, timestamp = 5L)
        val member = GroupMember(userId = "user", name = "User")
        val background = ChatBackgroundItem()

        assertEquals("message", image.messageId)
        assertEquals(4L, image.sequence)
        assertEquals("", member.avatarUrl)
        assertEquals(0, member.permissionLevel)
        assertFalse(member.isVip)
        assertFalse(member.isGag)
        assertEquals(0L, member.gagTime)
        assertEquals("", background.imgUrl)
        assertEquals(ConversationKey("chat", 1), ConversationKey("chat", 1))
    }

    @Test
    fun `login request defaults to android and home search defaults optional text`() {
        val request = LoginRequest("user@example.com", "secret", "installation")
        val result = HomeSearchResult(chatId = "chat", chatType = 1, name = "Name")

        assertEquals("android", request.platform)
        assertEquals("", result.avatarUrl)
        assertEquals("", result.introduction)
        assertEquals(CreatedChat("chat", 1, "Name", ""), CreatedChat("chat", 1, "Name", ""))
    }

    private fun conversation(
        chatId: String = "chat",
        remark: String? = null,
        timestamp: Long = 1L,
        sendTimestamp: Long = timestamp,
        unread: Int = 0,
        at: Int = 0,
        doNotDisturb: Int = 0
    ) = ConversationItem(
        chatId = chatId,
        chatType = 1,
        name = chatId,
        remark = remark,
        chatContent = "content",
        timestampMs = timestamp,
        sendTimestamp = sendTimestamp,
        unreadMessage = unread,
        at = at,
        avatarUrl = "",
        doNotDisturb = doNotDisturb
    )
}
