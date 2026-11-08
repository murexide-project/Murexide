package com.juhao.murexide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(
    @SerialName("chatId")
    val chatId: String,
    @SerialName("chatType")
    val chatType: Int,
    @SerialName("chatName")
    val chatName: String,
    @SerialName("content")
    val content: String,
    @SerialName("url")
    val url: String = "",
    @SerialName("reason")
    val reason: String
)

@Serializable
data class ReportResponse(
    @SerialName("code")
    val code: Int = 0,
    @SerialName("msg")
    val msg: String = ""
)