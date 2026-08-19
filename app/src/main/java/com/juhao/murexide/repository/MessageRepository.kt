package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.data.local.LocalCache
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.Msg
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import com.juhao.murexide.proto.send_message_send
import com.juhao.murexide.proto.send_message
import com.juhao.murexide.proto.edit_message_send
import com.juhao.murexide.proto.edit_message
import com.juhao.murexide.proto.recall_msg_send
import com.juhao.murexide.proto.recall_msg
import com.juhao.murexide.proto.button_report_send
import com.juhao.murexide.proto.button_report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable
data class EditHistoryResponse(
    val code: Int,
    val msg: String,
    val data: EditHistoryData
)

@Serializable
data class EditHistoryData(
    val total: Int,
    val list: List<EditHistoryItem>
)

@Serializable
data class EditHistoryItem(
    val id: Int,
    val msgId: String,
    val contentType: Int,
    val contentOld: String,
    val createTime: Long,
    val msgTime: Long
) {
    fun getOldText(): String {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val content = json.decodeFromString<EditContentOld>(contentOld)
            content.text
        } catch (_: Exception) {
            contentOld
        }
    }
}

@Serializable
data class EditContentOld(
    val text: String = ""
)

@Serializable
data class ForwardReceiveRequest(
    val chatId: String,
    val chatType: Int
)

@Serializable
internal data class ForwardMessageRequest(
    val msgId: String,
    val chatType: Int,
    val receive: List<ForwardReceiveRequest>
)

@Serializable
private data class ForwardStatusResponse(
    val code: Int = 0,
    val msg: String = ""
)

private val forwardJson = Json { ignoreUnknownKeys = true }

internal fun createForwardMessageJson(
    msgId: String,
    chatType: Int,
    recipients: List<ForwardReceiveRequest>
): String = forwardJson.encodeToString(
    ForwardMessageRequest(
        msgId = msgId,
        chatType = chatType,
        receive = recipients
    )
)

internal fun createRecallMessageRequest(
    msgId: String,
    chatId: String,
    chatType: Int
) = recall_msg_send(
    msg_id = listOf(msgId),
    chat_id = chatId,
    chat_type = chatType.toLong()
)

internal fun createSendMessageRequest(
    msgId: String,
    chatId: String,
    chatType: Int,
    content: MessageContent,
    contentType: Int,
    quoteMsgId: String?,
    commandId: Long?
): send_message_send {
    val contentProto = send_message_send.Content(
        text = content.text.takeIf { it.isNotEmpty() } ?: "",
        image = content.image ?: "",
        quote_msg_text = content.quoteMsgText ?: "",
        quote_image_url = content.quoteImageUrl ?: "",
        quote_image_name = content.quoteImageName ?: "",
        file_name = content.fileName ?: "",
        file_key = content.fileKey ?: "",
        file_size = content.fileSize ?: 0L,
        audio = content.audio ?: "",
        audio_time = content.audioTime?.toLong() ?: 0L,
        video = content.video ?: "",
        post_type = content.postType ?: "",
        expression_id = content.expressionId ?: "",
        sticker_item_id = content.stickerItemId ?: 0L,
        sticker_pack_id = content.stickerPackId ?: 0L,
        mentioned_id = content.mentionedId,
        form = content.form ?: ""
    )
    val mediaProto = content.media?.let { media ->
        send_message_send.Media(
            file_key = media.fileKey,
            file_hash = media.fileHash,
            file_type = media.fileType,
            image_height = media.height ?: 0L,
            image_width = media.width ?: 0L,
            file_size = media.fileSize,
            file_key2 = media.fileKey,
            file_suffix = media.fileSuffix
        )
    }

    return send_message_send(
        msg_id = msgId,
        chat_id = chatId,
        chat_type = chatType.toLong(),
        content = contentProto,
        content_type = contentType.toLong(),
        quote_msg_id = quoteMsgId ?: "",
        command_id = commandId ?: 0L,
        media = mediaProto
    )
}

class MessageRepository(
    private val client: OkHttpClient = NetworkClient.okHttpClient,
    private val baseUrl: String = NetworkClient.BASE_URL
) {
    suspend fun getMessageList(
        token: String,
        chatId: String,
        chatType: Int,
        msgId: String? = null,
    ): Result<List<MessageItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = list_message_send(
                    msg_count = 20.toLong(),
                    msg_id = msgId ?: "",
                    chat_type = chatType.toLong(),
                    chat_id = chatId
                ).encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/list-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val messageList = list_message.ADAPTER.decode(responseBody)

                        if (messageList.status?.code == 1) {
                            val messages = messageList.msg.map { msg ->
                                msg.toMessageItem(chatId = chatId, chatType = chatType)
                            }
                            LocalCache.currentAccountId()?.let { accountId ->
                                LocalCache.cacheMessages(accountId, messages)
                            }
                            Result.success(messages)
                        } else {
                            Result.failure(Exception(messageList.status?.msg ?: "获取消息失败"))
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

    suspend fun getMessageEditHistory(
        token: String,
        msgId: String,
        page: Int = 1,
        size: Int = 20
    ): Result<List<EditHistoryItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = buildJsonObject {
                    put("msgId", msgId)
                    put("size", size)
                    put("page", page)
                }
                val requestBody = forwardJson.encodeToString(params).toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/list-message-edit-record")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            Exception("HTTP error: ${response.code}")
                        )
                    }

                    val responseBody = response.body.string()
                    val editList = forwardJson.decodeFromString<EditHistoryResponse>(responseBody)

                    Result.success(editList.data.list)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        chatType: Int,
        content: MessageContent,
        contentType: Int,
        quoteMsgId: String? = null,
        commandId: Long? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val msgId = UUID.randomUUID().toString().replace("-", "")

                val requestProto = createSendMessageRequest(
                    msgId = msgId,
                    chatId = chatId,
                    chatType = chatType,
                    content = content,
                    contentType = contentType,
                    quoteMsgId = quoteMsgId,
                    commandId = commandId
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/send-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val sendResult = send_message.ADAPTER.decode(responseBody)

                        if (sendResult.status?.code == 1) {
                            Result.success(msgId)
                        } else {
                            Result.failure(Exception(sendResult.status?.msg ?: "发送失败"))
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

    suspend fun forwardMessage(
        token: String,
        msgId: String,
        sourceChatType: Int,
        recipients: List<ForwardReceiveRequest>
    ): Result<Boolean> {
        if (msgId.isBlank()) return Result.failure(IllegalArgumentException("消息 ID 不能为空"))
        if (recipients.isEmpty()) return Result.failure(IllegalArgumentException("至少选择一个会话"))

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = createForwardMessageJson(
                    msgId = msgId,
                    chatType = sourceChatType,
                    recipients = recipients
                ).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/v1/msg/msg-forward")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(
                            Exception("HTTP error: ${response.code}")
                        )
                    }
                    val body = response.body.string()
                    if (body.isBlank()) {
                        return@use Result.failure(Exception("转发响应为空"))
                    }
                    val result = forwardJson.decodeFromString<ForwardStatusResponse>(body)
                    if (result.code == 1) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(result.msg.ifBlank { "转发失败" }))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun editMessage(
        token: String,
        msgId: String,
        chatId: String,
        chatType: Int,
        content: MessageContent,
        contentType: Int,
        quoteMsgId: String? = null
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建 ProtoBuf 请求
                val contentProto = edit_message_send.Content(
                    text = content.text.takeIf { it.isNotEmpty() } ?: "",
                    quote_msg_text = content.quoteMsgText ?: ""
                )

                val requestProto = edit_message_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType,
                    content = contentProto,
                    content_type = contentType.toLong(),
                    quote_msg_id = quoteMsgId ?: ""
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/edit-message")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val editResult = edit_message.ADAPTER.decode(responseBody)

                        if (editResult.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(editResult.status?.msg ?: "编辑失败"))
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

    /** 上报消息气泡按钮的点击事件 (actionType=3) */
    suspend fun reportButtonClick(
        token: String,
        msgId: String,
        chatId: String,
        chatType: Int,
        userId: String,
        buttonValue: String
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val requestProto = button_report_send(
                    msg_id = msgId,
                    chat_type = chatType.toLong(),
                    chat_id = chatId,
                    user_id = userId,
                    button_value = buttonValue
                )
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/button-report")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val result = button_report.ADAPTER.decode(responseBody)

                        if (result.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(result.status?.msg ?: "按钮上报失败"))
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

    suspend fun recallMessage(
        token: String,
        msgId: String,
        chatId: String,
        chatType: Int
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val requestProto = createRecallMessageRequest(msgId, chatId, chatType)
                val requestBody = requestProto.encode().toRequestBody("application/octet-stream".toMediaType())

                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/recall-msg")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body.bytes()
                        val recallResult = recall_msg.ADAPTER.decode(responseBody)

                        if (recallResult.status?.code == 1) {
                            Result.success(true)
                        } else {
                            Result.failure(Exception(recallResult.status?.msg ?: "撤回失败"))
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

internal fun Msg.toMessageItem(chatId: String, chatType: Int): MessageItem {
    return MessageItem(
        msgId = msg_id,
        senderId = sender?.chat_id ?: "",
        senderName = sender?.name ?: "",
        senderAvatar = sender?.avatar_url ?: "",
        senderType = sender?.chat_type ?: 1,
        chatId = chatId,
        chatType = chatType,
        content = content?.text ?: "",
        contentType = content_type,
        timestamp = send_time,
        msgSeq = msg_seq,
        direction = direction,
        isRecalled = msg_delete_time > 0,
        deleteTime = msg_delete_time,
        isEdited = edit_time > 0,
        quoteMsgId = quote_msg_id.takeIf { it.isNotEmpty() },
        quoteMsgText = content?.quote_msg_text?.takeIf { it.isNotEmpty() },
        quoteImageUrl = content?.quote_image_url?.takeIf { it.isNotEmpty() },
        stickerUrl = content?.sticker_url?.takeIf { it.isNotEmpty() },
        imageUrl = content?.image_url?.takeIf { it.isNotEmpty() },
        imageWidth = content?.width?.takeIf { it > 0 },
        imageHeight = content?.height?.takeIf { it > 0 },
        audioUrl = content?.audio_url?.takeIf { it.isNotEmpty() },
        audioTime = content?.audio_time?.takeIf { it > 0 },
        videoUrl = content?.video_url?.takeIf { it.isNotEmpty() },
        videoTime = content?.video_time?.takeIf { it > 0 },
        fileUrl = content?.file_url?.takeIf { it.isNotEmpty() },
        fileName = content?.file_name?.takeIf { it.isNotEmpty() },
        fileSize = content?.file_size?.takeIf { it > 0 },
        cmdName = cmd?.name?.takeIf { it.isNotEmpty() },
        cmdId = cmd?.type?.toLong(),
        cmdType = cmd?.type,
        postId = content?.post_id,
        postTitle = content?.post_title,
        postContent = content?.post_content,
        postContentType = content?.post_content_type?.toIntOrNull(),
        buttons = parseMessageButtons(content?.buttons),
        tags = sender?.tag?.map { tag ->
            MessageTag(
                id = tag.id,
                text = tag.text,
                color = tag.color
            )
        } ?: emptyList(),
        updateTimestamp = maxOf(send_time, edit_time, msg_delete_time)
    )
}