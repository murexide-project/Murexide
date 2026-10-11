package com.juhao.murexide.data

import kotlinx.serialization.Serializable

private const val STICKER_IMAGE_BASE_URL = "https://chat-img.jwznb.com"

fun resolveStickerUrl(url: String): String {
    val value = url.trim()
    return when {
        value.isEmpty() -> value
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "$STICKER_IMAGE_BASE_URL/${value.trimStart('/')}"
    }
}

fun resolveStickerMessageUrl(imageUrl: String?, stickerUrl: String?): String? {
    return sequenceOf(imageUrl, stickerUrl).firstNotNullOfOrNull { it?.takeIf(String::isNotBlank) }
        ?.let(::resolveStickerUrl)
}

@Serializable
data class StickerItem(
    val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val stickerPackId: Long = 0,
    val createBy: String = "",
    val createTime: Long = 0,
    val delFlag: Int = 0
)

@Serializable
data class StickerPack(
    val id: Long = 0,
    val name: String = "",
    val createBy: String = "",
    val createTime: Long = 0,
    val delFlag: Int = 0,
    val userCount: Long = 0,
    val hot: Long = 0,
    val uuid: String = "",
    val updateTime: Long = 0,
    val sort: Long = 0,
    val stickerItems: List<StickerItem> = emptyList()
)

@Serializable
data class StickerListResponse(
    val code: Int = 0,
    val data: StickerListData? = null,
    val msg: String = ""
)

@Serializable
data class StickerListData(
    val stickerPacks: List<StickerPack> = emptyList()
)

@Serializable
data class ExpressionItem(
    val id: Long = 0,
    val url: String = "",
    val urlOriginal: String = "",
    val delFlag: Int = 0,
    val createTime: Long = 0,
    val createBy: String = ""
)

@Serializable
data class ExpressionListResponse(
    val code: Int = 0,
    val data: ExpressionListData? = null,
    val msg: String = ""
)

@Serializable
data class ExpressionListData(
    val expression: List<ExpressionItem> = emptyList()
)

@Serializable
data class StickerDetailResponse(
    val code: Int = 0,
    val data: StickerDetailData? = null,
    val msg: String = ""
)

@Serializable
data class StickerDetailData(
    val stickerPack: StickerPack? = null,
    val user: StickerCreator? = null
)

@Serializable
data class StickerCreator(
    val id: Long = 0,
    val user_id: String = "",
    val nickname: String = "",
    val avatar_url: String = ""
)

@Serializable
data class StickerCreatePackResponse(
    val code: Int = 0,
    val data: StickerCreatePackData? = null,
    val msg: String = ""
)

@Serializable
data class StickerCreatePackData(
    val id: Long = 0
)