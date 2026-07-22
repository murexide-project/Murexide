package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.user.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL
    private val json = Json { ignoreUnknownKeys = true }

    private fun httpError(response: Response, fallback: String): Exception {
        val responseBody = response.body.string().trim()
        val apiMessage = runCatching {
            json.parseToJsonElement(responseBody)
                .jsonObject["msg"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val plainMessage = responseBody.takeIf {
            it.isNotBlank() &&
                it.length <= 200 &&
                !it.startsWith("{") &&
                !it.startsWith("<")
        }

        return Exception(apiMessage ?: plainMessage ?: "$fallback（HTTP ${response.code}）")
    }

    suspend fun login(email: String, password: String, deviceId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val loginRequest = LoginRequest(
                    email = email,
                    password = password,
                    deviceId = deviceId,
                    platform = "android"
                )
                
                val jsonBody = json.encodeToString(LoginRequest.serializer(), loginRequest)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/email-login")
                    .post(requestBody)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.string()
                        val loginResponse = json.decodeFromString(LoginResponse.serializer(),
                            responseBody
                        )
                        
                        if (loginResponse.code == 1 && loginResponse.data != null) {
                            Result.success(loginResponse.data.token)
                        } else {
                            Result.failure(Exception(loginResponse.msg))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 获取人机验证图片 */
    suspend fun getCaptcha(): Result<CaptchaData> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/captcha")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val captchaResponse = json.decodeFromString(
                            CaptchaResponse.serializer(),
                            response.body.string()
                        )
                        if (captchaResponse.code == 1 && captchaResponse.data != null) {
                            Result.success(captchaResponse.data)
                        } else {
                            Result.failure(Exception(captchaResponse.msg.ifBlank { "获取验证码图片失败" }))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 获取短信验证码（需先通过人机验证） */
    suspend fun sendSmsCode(
        mobile: String,
        captchaCode: String,
        captchaId: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val verificationRequest = VerificationCodeRequest(
                    mobile = mobile,
                    code = captchaCode,
                    id = captchaId,
                    platform = "android"
                )

                val jsonBody = json.encodeToString(
                    VerificationCodeRequest.serializer(),
                    verificationRequest
                )
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/v1/verification/get-verification-code")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val baseResponse = json.decodeFromString(
                            BaseResponse.serializer(),
                            response.body.string()
                        )
                        if (baseResponse.code == 1) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(baseResponse.msg.ifBlank { "发送验证码失败" }))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 短信验证码登录 */
    suspend fun phoneLogin(
        mobile: String,
        smsCode: String,
        deviceId: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val phoneLoginRequest = PhoneLoginRequest(
                    mobile = mobile,
                    captcha = smsCode,
                    deviceId = deviceId,
                    platform = "android"
                )

                val jsonBody = json.encodeToString(
                    PhoneLoginRequest.serializer(),
                    phoneLoginRequest
                )
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/v1/user/verification-login")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val loginResponse = json.decodeFromString(
                            LoginResponse.serializer(),
                            response.body.string()
                        )
                        if (loginResponse.code == 1 && loginResponse.data != null) {
                            Result.success(loginResponse.data.token)
                        } else {
                            Result.failure(Exception(loginResponse.msg))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getUserInfo(token: String): Result<UserInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/info")
                    .get()
                    .header("token", token)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val userInfo = info.ADAPTER.decode(responseBody)
                        
                        if (userInfo.status?.code == 1 && userInfo.data_ != null) {
                            val data = userInfo.data_
                            Result.success(
                                UserInfo(
                                    id = data.id,
                                    name = data.name,
                                    avatarUrl = data.avatar_url,
                                    phone = data.phone,
                                    email = data.email,
                                    coin = data.coin,
                                    isVip = data.is_vip == 1,
                                    invitationCode = data.invitation_code
                                )
                            )
                        } else {
                            Result.failure(Exception(userInfo.status?.msg ?: "获取用户信息失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 修改昵称 (ProtoBuf) */
    suspend fun editNickname(token: String, nickname: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val protoRequest = edit_nickname_send(name = nickname)
                val requestBody = protoRequest.encode().toRequestBody("application/octet-stream".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/v1/user/edit-nickname")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val protoResponse = edit_nickname.ADAPTER.decode(response.body.bytes())
                        if (protoResponse.status?.code == 1) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(protoResponse.status?.msg ?: "修改昵称失败"))
                        }
                    } else {
                        Result.failure(httpError(response, "修改昵称失败"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 修改头像 (ProtoBuf) */
    suspend fun editAvatar(token: String, avatarUrl: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val protoRequest = edit_avatar_send(url = avatarUrl)
                val requestBody = protoRequest.encode().toRequestBody("application/octet-stream".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/v1/user/edit-avatar")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val protoResponse = edit_avatar.ADAPTER.decode(response.body.bytes())
                        if (protoResponse.status?.code == 1) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(protoResponse.status?.msg ?: "修改头像失败"))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 修改个人资料 (JSON) */
    suspend fun saveUserData(token: String, profile: SaveUserDataRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonBody = json.encodeToString(SaveUserDataRequest.serializer(), profile)
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/v1/user/save-user-data")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val baseResponse = json.decodeFromString(BaseResponse.serializer(), response.body.string())
                        if (baseResponse.code == 1) {
                            Result.success(Unit)
                        } else {
                            Result.failure(Exception(baseResponse.msg.ifBlank { "修改个人资料失败" }))
                        }
                    } else {
                        Result.failure(httpError(response, "修改个人资料失败"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 获取个人资料 (JSON) */
    suspend fun getUserData(token: String): Result<UserProfileData> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/v1/user/get-user-data")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val dataResponse = json.decodeFromString(UserDataResponse.serializer(), response.body.string())
                        if (dataResponse.code == 1 && dataResponse.data != null) {
                            Result.success(dataResponse.data.data)
                        } else {
                            Result.failure(Exception(dataResponse.msg.ifBlank { "获取个人资料失败" }))
                        }
                    } else {
                        Result.failure(Exception("HTTP error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

data class UserInfo(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val phone: String,
    val email: String,
    val coin: Double,
    val isVip: Boolean,
    val invitationCode: String
)
