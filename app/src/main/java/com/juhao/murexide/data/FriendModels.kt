package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class ContactItem(
    val chatId: String,
    val chatType: Int,
    val remark: String?,
    val avatarUrl: String,
    val permissionLevel: Int,
    val noDisturb: Boolean,
    val name: String
)

@Serializable
data class ContactGroup(
    val groupName: String,
    val chatType: Int,
    val contacts: List<ContactItem>
)

@Serializable
data class ContactRequestItem(
    val requestId: Int,
    val requesterName: String,
    val requesterAvatarUrl: String,
    val receiverName: String,
    val receiverAvatarUrl: String,
    val groupName: String,
    val groupAvatarUrl: String,
    val botName: String,
    val botAvatarUrl: String,
    val inviterId: String,
    val sourceType: Int,
    val targetType: Int,
    val targetId: String,
    val receiverId: String,
    val result: Int,
    val processedAt: Long,
    val invitedAt: Long,
    val invitedAtText: String,
    val processorName: String,
    val note: String
) {
    val isPending: Boolean
        get() = result == 0

    /**
     * 群主处理群聊/机器人的申请走 /group/agree-invite；有邀请人的记录是用户收到的
     * 邀请，应由 /friend/agree-apply 处理。
     */
    private val isGroupRelated: Boolean
        get() = sourceType == 2 || targetType == 2 || groupName.isNotBlank()

    private val isBotRelated: Boolean
        get() = sourceType == 3 || targetType == 3 || botName.isNotBlank()

    private val isInvitation: Boolean
        get() = inviterId.isNotBlank()

    val usesGroupAgreeInvite: Boolean
        get() = isInvitation && (isGroupRelated || isBotRelated)

    val displayName: String
        get() = groupName.ifBlank {
            botName.ifBlank {
                requesterName.ifBlank {
                    receiverName.ifBlank { "未知联系人" }
                }
            }
        }

    val displayAvatarUrl: String
        get() = groupAvatarUrl.ifBlank {
            botAvatarUrl.ifBlank {
                requesterAvatarUrl.ifBlank {
                    receiverAvatarUrl
                }
            }
        }

    val typeLabel: String
        get() = when {
            isGroupRelated -> if (isInvitation) "群聊邀请" else "群聊申请"
            isBotRelated -> if (isInvitation) "机器人邀请" else "机器人申请"
            else -> "好友申请"
        }

    val contextName: String?
        get() = sequenceOf(groupName, botName, requesterName)
            .firstOrNull { it.isNotBlank() && it != displayName }

    val resultLabel: String
        get() = when (result) {
            1 -> "已同意"
            2 -> "已拒绝"
            3 -> "已过期"
            4 -> "群聊已解散"
            else -> "待处理"
        }
}

@Serializable
data class ContactRequestList(
    val requests: List<ContactRequestItem>,
    val total: Int,
    val pending: Int
)

@Serializable
data class DeleteFriendResponse(
    val code: Int,
    val msg: String
)
