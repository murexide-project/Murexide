package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class BaListResponse(
    val code: Int,
    val data: BaListData?,
    val msg: String
)

@Serializable
data class BaListData(
    val ba: List<BaItem>,
    val total: Int
)

@Serializable
data class BaItem(
    val id: Int,
    val name: String,
    val avatar: String,
    val memberNum: Int,
    val postNum: Int,
    val groupNum: Int,
    val isFollowed: String? = null
)

@Serializable
data class PostListResponse(
    val code: Int,
    val data: PostListData?,
    val msg: String
)

@Serializable
data class PostListData(
    val posts: List<PostItem>,
    val total: Int
)

@Serializable
data class PostItem(
    val id: Int,
    val baId: Int,
    val senderId: String,
    val senderNickname: String,
    val senderAvatar: String,
    val title: String,
    val content: String,
    val contentType: Int,
    val createTimeText: String,
    val likeNum: Int,
    val commentNum: Int,
    val collectNum: Int,
    val amountNum: Int,
    val isLiked: String,  // "0" 或 "1"
    val isCollected: Int, // 0 或 1
    val isReward: Int,
    val group: GroupInfo? = null
)

@Serializable
data class GroupInfo(
    val groupId: String,
    val name: String,
    val avatarUrl: String,
    val headcount: Int
)