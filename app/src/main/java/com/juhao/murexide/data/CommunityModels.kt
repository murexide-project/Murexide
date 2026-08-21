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
    val memberNum: Int = 0,
    val postNum: Int = 0,
    val groupNum: Int = 0,
    val isFollowed: String? = null
)

@Serializable
data class BaCreateListResponse(
    val code: Int,
    val data: BaCreateListData?,
    val msg: String
)

@Serializable
data class BaCreateListData(
    val ba: List<BaItem> = emptyList()
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

// ==================== 帖子详情 ====================

@Serializable
data class PostDetailResponse(
    val code: Int,
    val data: PostDetailData?,
    val msg: String
)

@Serializable
data class PostDetailData(
    val ba: BaDetail? = null,
    val isAdmin: Int = 0,
    val post: PostDetail
)

@Serializable
data class BaDetail(
    val id: Int,
    val name: String,
    val avatar: String,
    val memberNum: Int = 0,
    val postNum: Int = 0,
    val groupNum: Int = 0,
    val createTimeText: String = "",
    val isFollowed: String? = null
)

@Serializable
data class PostDetail(
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
    val isLiked: Int = 0,
    val isCollected: Int = 0,
    val isReward: Int = 0,
    val isVip: Int = 0,
    val group: GroupInfo? = null
)

// ==================== 评论 ====================

@Serializable
data class CommentListResponse(
    val code: Int,
    val data: CommentListData?,
    val msg: String
)

@Serializable
data class CommentListData(
    val comments: List<CommentItem> = emptyList(),
    val isAdmin: Int = 0,
    val total: Int = 0
)

@Serializable
data class CommentItem(
    val id: Int,
    val postId: Int,
    val parentId: Int = 0,
    val senderId: String,
    val content: String,
    val createTimeText: String,
    val likeNum: Int = 0,
    val repliesNum: Int = 0,
    val amountNum: Int = 0,
    val senderNickname: String,
    val senderAvatar: String,
    val isLiked: String = "0",
    val isReward: Int = 0,
    val isVip: Int = 0
)

// ==================== 分区详情 ====================

@Serializable
data class BaInfoResponse(
    val code: Int,
    val data: BaInfoData?,
    val msg: String
)

@Serializable
data class BaInfoData(
    val ba: BaDetail
)

// ==================== 发布文章 ====================

@Serializable
data class CreatePostResponse(
    val code: Int,
    val data: CreatePostData? = null,
    val msg: String
)

@Serializable
data class CreatePostData(
    val audioUrl: Int? = null
)

@Serializable
data class EditPostResponse(
    val code: Int,
    val msg: String
)