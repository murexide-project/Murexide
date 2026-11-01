package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class BotItem(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val introduction: String = ""
)

/** 服务端返回的单个机器人权限项。 */
@Serializable
data class GroupBotPermissionItem(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = false
)

/** 群机器人权限（/v1/bot/group-permission-get|edit）。 */
@Serializable
data class GroupBotPermission(
    val permissions: List<GroupBotPermissionItem> = emptyList()
) {
    /** 服务端未返回列表时回退到内置默认项。 */
    val items: List<GroupBotPermissionItem>
        get() = permissions.ifEmpty { defaultItems }

    fun isEnabled(key: String): Boolean =
        items.firstOrNull { it.key == key }?.enabled ?: false

    fun withEnabled(key: String, enabled: Boolean): GroupBotPermission =
        copy(permissions = items.map { if (it.key == key) it.copy(enabled = enabled) else it })

    /** 提交给接口的 permissions 对象（key -> enabled）。 */
    fun toPermissionMap(): Map<String, Boolean> = items.associate { it.key to it.enabled }

    companion object {
        val defaultItems = listOf(
            GroupBotPermissionItem(
                key = "allowEditGroupInfo",
                name = "允许修改群信息",
                description = "允许机器人修改群信息及消息类型限制"
            ),
            GroupBotPermissionItem(
                key = "allowGagMember",
                name = "允许禁言用户",
                description = "允许机器人禁言或解除成员禁言"
            ),
            GroupBotPermissionItem(
                key = "allowRemoveMember",
                name = "允许移除群成员",
                description = "允许机器人将成员移出本群"
            ),
            GroupBotPermissionItem(
                key = "allowGroupTagManage",
                name = "允许控制标签组",
                description = "允许机器人创建、修改群标签及成员标签关联"
            ),
            GroupBotPermissionItem(
                key = "allowGetGroupMembers",
                name = "允许获取群成员",
                description = "允许机器人获取群成员列表"
            )
        )
    }
}

@Serializable
data class InstructionItem(
    val id: Long,
    val botId: String,
    val name: String,
    val desc: String = "",
    val type: Int, // 1-普通指令, 2-直发指令, 5-自定义输入指令
    val hintText: String = "",
    val defaultText: String = "",
    val form: String = "",
    val botName: String = ""
)

data class InstructionPanelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val bots: List<BotItem> = emptyList(),
    val instructions: List<InstructionItem> = emptyList()
)

// ---------- 机器人私聊指令 (instruction/web-list, JSON) ----------

@Serializable
data class InstructionWebListResponse(
    val code: Int = 0,
    val data: InstructionWebListData? = null,
    val msg: String = ""
)

@Serializable
data class InstructionWebListData(
    val list: List<InstructionWebListItem> = emptyList()
)

@Serializable
data class InstructionWebListItem(
    val id: Long = 0,
    val botId: String = "",
    val name: String = "",
    val desc: String = "",
    val instructionType: Int = 1,
    val hintText: String = "",
    val defaultText: String = "",
    val customJson: String = ""
)

fun InstructionWebListItem.toInstructionItem(botName: String = ""): InstructionItem = InstructionItem(
    id = id,
    botId = botId,
    name = name,
    desc = desc,
    type = instructionType,
    hintText = hintText,
    defaultText = defaultText,
    form = customJson,
    botName = botName
)
