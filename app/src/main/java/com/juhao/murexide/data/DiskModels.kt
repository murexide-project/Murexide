package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class DiskFileItem(
    val id: Long = 0,
    val name: String = "",
    val fileSize: Long = 0,
    val objectType: Int = 2,
    val uploadTime: Long = 0,
    val uploadBy: String = "",
    val uploadByName: String = "",
    val qiniuKey: String = ""
) {
    val isFolder: Boolean get() = objectType == 1
}

@Serializable
data class DiskListResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: DiskListData? = null
)

@Serializable
data class DiskListData(
    val list: List<DiskFileItem> = emptyList()
)

@Serializable
data class DiskSizeResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: DiskSizeData? = null
)

@Serializable
data class DiskSizeData(
    val totalSize: Long = 0
)