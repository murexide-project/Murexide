package com.juhao.murexide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShareInfoRequest(
    @SerialName("key")
    val key: String,
    @SerialName("ts")
    val ts: String
)

@Serializable
data class ShareInfoResponse(
    @SerialName("code")
    val code: Int = 0,
    @SerialName("msg")
    val msg: String = "",
    @SerialName("data")
    val data: ShareInfoWrapper? = null
)

@Serializable
data class ShareInfoWrapper(
    @SerialName("share")
    val share: ShareInfo? = null
)

@Serializable
data class ShareInfo(
    @SerialName("chat_id")
    val chatId: String = "",
    @SerialName("chat_type")
    val chatType: Int = 0,
    @SerialName("chat_name")
    val chatName: String = "",
    @SerialName("imageUrl")
    val imageUrl: String = ""
)

@Serializable
data class ShareCreateRequest(
    @SerialName("chatId")
    val chatId: String,
    @SerialName("chatType")
    val chatType: Int,
    @SerialName("chatName")
    val chatName: String
)

@Serializable
data class ShareCreateResponse(
    @SerialName("code")
    val code: Int = 0,
    @SerialName("msg")
    val msg: String = "",
    @SerialName("data")
    val data: ShareCreated? = null
)

@Serializable
data class ShareCreated(
    @SerialName("imageKey")
    val imageKey: String = "",
    @SerialName("key")
    val key: String = "",
    @SerialName("shareUrl")
    val shareUrl: String = "",
    @SerialName("ts")
    val ts: Long = 0L
)