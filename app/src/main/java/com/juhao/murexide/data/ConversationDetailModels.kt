package com.juhao.murexide.data

import kotlinx.serialization.Serializable

/**
 * 会话详情统一数据模型（用户 / 群聊 / 机器人）
 */
@Serializable
data class ConversationDetail(
    val chatId: String,
    val chatType: Int,
    val name: String,
    val avatarUrl: String,
    val introduction: String = "",
    // 群聊
    val groupId: String? = null,
    val memberCount: Long? = null,
    val ownerId: String? = null,
    val groupCode: String? = null,
    val categoryName: String? = null,
    val categoryId: Long? = null,
    val myGroupNickname: String? = null,
    val isPrivate: Boolean = false,
    val isGag: Boolean = false,
    val doNotDisturb: Boolean = false,
    // 群聊设置（权限与开关）
    val permissionLevel: Int = 0,      // 群主 100 / 管理员 2 / 普通 0
    val directJoin: Boolean = false,   // 进群免审核
    val historyMsg: Boolean = false,   // 新成员可见历史消息
    val hideGroupMembers: Boolean = false, // 隐藏群成员
    // 用户
    val nameId: Long? = null,
    val registerTime: String? = null,
    val lastActiveTime: String? = null,
    val onlineDay: Int? = null,
    val continuousOnlineDay: Int? = null,
    val ipGeo: String? = null,
    val isVip: Boolean = false,
    val gender: Int = 3,
    // 机器人
    val createBy: String? = null,
    val createTime: Long? = null,
    val usageCount: Long? = null,
    val groupLimit: Boolean = false,
    val isStop: Boolean = false
)

/** The conversation list is the local source of truth after an in-app mute change. */
internal fun ConversationDetail.withCachedMuteState(muted: Boolean?): ConversationDetail =
    if (muted == null) this else copy(doNotDisturb = muted)

data class ConversationDetailUiState(
    val isLoading: Boolean = true,
    val detail: ConversationDetail? = null,
    val error: String? = null,
    // 添加/进入聊天
    val isAdded: Boolean? = null,   // null 表示尚未查询完成
    val isAdding: Boolean = false,
    val message: String? = null,
    val selectedTab: Int = 0,
    val members: List<GroupMember> = emptyList(),
    val isLoadingMembers: Boolean = false,
    val isLoadingMoreMembers: Boolean = false,
    val membersPage: Int = 1,
    val hasMoreMembers: Boolean = true,
    val groupBots: List<BotItem> = emptyList(),
    val isLoadingGroupBots: Boolean = false,
    val hasLoadedGroupBots: Boolean = false,
    val mediaMessages: List<MessageItem> = emptyList(),
    val createdBoards: List<BaItem> = emptyList(),
    val isLoadingCreatedBoards: Boolean = false,
    val hasLoadedCreatedBoards: Boolean = false,
    val fileMessages: List<MessageItem> = emptyList(),
    val historyAnchorMessageId: String? = null,
    val mediaImageAnchor: Long = 0L,
    val isLoadingHistory: Boolean = false,
    val hasMoreHistory: Boolean = true,
    val isChangingMute: Boolean = false,
    val kickTarget: GroupMember? = null,
    val gagTarget: GroupMember? = null,
    val adminTarget: GroupMember? = null,
    val showKickConfirm: Boolean = false,
    val showGagDialog: Boolean = false,
    val showAdminConfirm: Boolean = false,
    val isLeaving: Boolean = false,
    val hasLeft: Boolean = false
)
