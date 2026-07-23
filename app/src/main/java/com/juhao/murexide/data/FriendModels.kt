package com.juhao.murexide.data

import kotlinx.serialization.Serializable

data class ContactItem(
    val chatId: String,
    val chatType: Int,
    val remark: String?,
    val avatarUrl: String,
    val permissionLevel: Int,
    val noDisturb: Boolean,
    val name: String
)

data class ContactGroup(
    val groupName: String,
    val chatType: Int,
    val contacts: List<ContactItem>
)

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

    val displayName: String
        get() = requesterName.ifBlank {
            botName.ifBlank {
                groupName.ifBlank {
                    receiverName.ifBlank { "未知联系人" }
                }
            }
        }

    val displayAvatarUrl: String
        get() = requesterAvatarUrl.ifBlank {
            botAvatarUrl.ifBlank {
                groupAvatarUrl.ifBlank { receiverAvatarUrl }
            }
        }

    val typeLabel: String
        get() = when {
            sourceType == 2 || targetType == 2 || groupName.isNotBlank() -> "群聊申请 / 邀请"
            sourceType == 3 || targetType == 3 || botName.isNotBlank() -> "机器人申请 / 邀请"
            else -> "好友申请"
        }

    val contextName: String?
        get() = sequenceOf(groupName, botName, receiverName)
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
