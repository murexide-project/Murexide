package com.juhao.murexide.data

import kotlinx.serialization.Serializable

/** 群成员 */
@Serializable
data class GroupMember(
    val userId: String,
    val name: String,
    val avatarUrl: String = "",
    val permissionLevel: Int = 0, // 0-普通, 2-管理员, 100-群主
    val isVip: Boolean = false,
    val isGag: Boolean = false,
    val gagTime: Long = 0
)

/** 群成员列表状态（menu 入口的成员列表 & @选择弹窗共用数据源） */
data class GroupMembersState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val members: List<GroupMember> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val keywords: String = ""
)

/** @成员选择弹窗状态 */
data class MentionPickerState(
    val isVisible: Boolean = false,
    val triggerPos: Int = -1,
    val keyword: String = ""
)