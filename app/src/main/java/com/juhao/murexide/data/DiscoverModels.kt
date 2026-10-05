package com.juhao.murexide.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class BotBannerResponse(
    val code: Int,
    val data: BotBannerData? = null,
    val msg: String = ""
)

@Serializable
data class BotBannerData(
    val banners: List<BotBanner> = emptyList()
)

@Serializable
data class BotBanner(
    val id: Int = 0,
    val title: String = "",
    val introduction: String = "",
    val targetId: String = "",
    val targetUrl: String = "",
    val imageUrl: String = "",
    val sort: Int = 0,
    val typ: Int = 0
)

@Serializable
data class BotStoreResponse(
    val code: Int,
    val data: BotStoreData? = null,
    val msg: String = ""
)

@Serializable
data class BotStoreData(
    val bots: List<BotStoreItem> = emptyList()
)

@Serializable
data class BotStoreItem(
    val chatId: String = "",
    val chatType: JsonPrimitive? = null,
    val headcount: JsonPrimitive? = null,
    val nickname: String = "",
    val introduction: String = "",
    val instructions: String = "",
    val avatarUrl: String = ""
) {
    val chatTypeValue: Int
        get() = chatType?.content?.toIntOrNull() ?: 3

    val userCount: Int
        get() = headcount?.content?.toIntOrNull() ?: 0
}

@Serializable
data class GroupCategoryResponse(
    val code: Int,
    val data: GroupCategoryData? = null,
    val msg: String = ""
)

@Serializable
data class GroupCategoryData(
    val category: List<GroupCategoryItem> = emptyList()
)

@Serializable
data class GroupCategoryItem(
    val id: Int = 0,
    val name: String = "",
    val parent_id: Int = 0,
    val subItems: List<GroupCategoryItem>? = null
)

@Serializable
data class GroupRecommendResponse(
    val code: Int,
    val data: GroupRecommendData? = null,
    val msg: String = ""
)

@Serializable
data class GroupRecommendData(
    val groups: List<GroupRecommendItem> = emptyList()
)

@Serializable
data class GroupRecommendItem(
    val id: Int = 0,
    val groupId: String = "",
    val name: String = "",
    val introduction: String = "",
    val createBy: String = "",
    val createTime: Long = 0,
    val avatarId: Long = 0,
    val avatarUrl: String = "",
    val headcount: Int = 0,
    val readHistory: Int = 0,
    val alwaysAgree: Int = 0,
    val categoryId: Int = 0,
    val category: String = "",
    val gag: Int = 0
)
