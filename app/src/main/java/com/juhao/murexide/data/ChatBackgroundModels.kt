package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class ChatBackgroundItem(
    val id: Long = 0,
    val userId: String = "",
    val chatId: String = "",
    val imgUrl: String = "",
    val createTime: Long = 0,
    val updateTime: Long = 0
)

@Serializable
data class ChatBackgroundListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: ChatBackgroundListData? = null
)

@Serializable
data class ChatBackgroundListData(
    val list: List<ChatBackgroundItem> = emptyList()
)

@Serializable
data class ChatBackgroundEditBody(
    val userId: String,
    val chatId: String,
    val url: String
)

@Serializable
data class ChatBackgroundEditResponse(
    val code: Int = 0,
    val msg: String = ""
)