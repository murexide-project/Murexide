package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class CommonResponse(
    val code: Int,
    val msg: String
)