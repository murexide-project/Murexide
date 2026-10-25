package com.juhao.murexide.data

import kotlinx.serialization.Serializable

/** 群组标签 */
@Serializable
data class GroupTag(
    val id: Long = 0L,
    val groupId: String = "",
    val tag: String = "",
    val color: String = "",
    val desc: String = "",
    val sort: Int = 0,
    val createTime: Long = 0L
)

/** 群组标签绑定的成员分页数据 */
data class GroupTagMemberPage(
    val members: List<GroupMember> = emptyList(),
    val total: Long = 0L
)
