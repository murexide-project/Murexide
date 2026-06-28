package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String,
    val platform: String = "android"
)

@Serializable
data class LoginResponse(
    val code: Int,
    val data: LoginData? = null,
    val msg: String
)

@Serializable
data class LoginData(
    val token: String
)

// ---------- 手机号登录 ----------

/** 通用响应（仅含状态码与消息） */
@Serializable
data class BaseResponse(
    val code: Int,
    val msg: String = ""
)

/** 人机验证图片响应 */
@Serializable
data class CaptchaResponse(
    val code: Int,
    val data: CaptchaData? = null,
    val msg: String = ""
)

@Serializable
data class CaptchaData(
    val b64s: String,
    val id: String
)

/** 获取短信验证码请求 */
@Serializable
data class VerificationCodeRequest(
    val mobile: String,
    val code: String,      // 人机验证校验码
    val id: String,        // 人机验证ID
    val platform: String = "android"
)

/** 短信验证码登录请求 */
@Serializable
data class PhoneLoginRequest(
    val mobile: String,
    val captcha: String,   // 短信验证码
    val deviceId: String,
    val platform: String = "android"
)