package com.juhao.murexide.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRequestModelsWorker1Test {
    @Test
    fun `request status labels cover known and unknown results`() {
        val labels = mapOf(
            0 to "待处理",
            1 to "已同意",
            2 to "已拒绝",
            3 to "已过期",
            4 to "群聊已解散",
            9 to "待处理"
        )

        labels.forEach { (result, label) ->
            val request = request(result = result)
            assertEquals(label, request.resultLabel)
            assertEquals(result == 0, request.isPending)
        }
    }

    @Test
    fun `display name and avatar use the documented fallback order`() {
        val request = request(
            requesterName = "",
            botName = "Bot",
            groupName = "Group",
            receiverName = "Receiver",
            requesterAvatarUrl = "",
            botAvatarUrl = "bot.png",
            groupAvatarUrl = "group.png",
            receiverAvatarUrl = "receiver.png"
        )

        assertEquals("Bot", request.displayName)
        assertEquals("bot.png", request.displayAvatarUrl)
        assertEquals("Group", request.contextName)

        val unknown = request(
            requesterName = "",
            botName = "",
            groupName = "",
            receiverName = "",
            requesterAvatarUrl = "",
            botAvatarUrl = "",
            groupAvatarUrl = "",
            receiverAvatarUrl = "fallback.png"
        )
        assertEquals("未知联系人", unknown.displayName)
        assertEquals("fallback.png", unknown.displayAvatarUrl)
    }

    @Test
    fun `group and bot applications distinguish invitations`() {
        val groupApplication = request(sourceType = 2)
        val groupInvitation = request(sourceType = 2, inviterId = "inviter")
        val botApplication = request(targetType = 3)
        val botInvitation = request(targetType = 3, inviterId = "inviter")

        assertEquals("群聊申请", groupApplication.typeLabel)
        assertTrue(groupApplication.usesGroupAgreeInvite)
        assertEquals("群聊邀请", groupInvitation.typeLabel)
        assertFalse(groupInvitation.usesGroupAgreeInvite)
        assertEquals("机器人申请", botApplication.typeLabel)
        assertTrue(botApplication.usesGroupAgreeInvite)
        assertEquals("机器人邀请", botInvitation.typeLabel)
        assertFalse(botInvitation.usesGroupAgreeInvite)
    }

    @Test
    fun `plain request is a friend application and context skips display name`() {
        val request = request(
            requesterName = "Alice",
            receiverName = "Context",
            groupName = "",
            botName = ""
        )

        assertEquals("好友申请", request.typeLabel)
        assertFalse(request.usesGroupAgreeInvite)
        assertEquals("Context", request.contextName)
    }

    private fun request(
        result: Int = 0,
        requesterName: String = "Requester",
        requesterAvatarUrl: String = "requester.png",
        receiverName: String = "Receiver",
        receiverAvatarUrl: String = "receiver.png",
        groupName: String = "",
        groupAvatarUrl: String = "group.png",
        botName: String = "",
        botAvatarUrl: String = "bot.png",
        inviterId: String = "",
        sourceType: Int = 1,
        targetType: Int = 1
    ) = ContactRequestItem(
        requestId = 1,
        requesterName = requesterName,
        requesterAvatarUrl = requesterAvatarUrl,
        receiverName = receiverName,
        receiverAvatarUrl = receiverAvatarUrl,
        groupName = groupName,
        groupAvatarUrl = groupAvatarUrl,
        botName = botName,
        botAvatarUrl = botAvatarUrl,
        inviterId = inviterId,
        sourceType = sourceType,
        targetType = targetType,
        targetId = "target",
        receiverId = "receiver",
        result = result,
        processedAt = 0L,
        invitedAt = 0L,
        invitedAtText = "",
        processorName = "",
        note = ""
    )
}
