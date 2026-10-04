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