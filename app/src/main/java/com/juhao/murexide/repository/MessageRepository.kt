package com.juhao.murexide.repository

import com.juhao.murexide.data.*
import com.juhao.murexide.network.NetworkClient
import com.juhao.murexide.proto.list_message
import com.juhao.murexide.proto.list_message_send
import com.juhao.murexide.proto.pic_list_message_by_mid_seq
import com.juhao.murexide.proto.pic_list_message_by_mid_seq_send
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class MessageRepository {
    private val client = NetworkClient.okHttpClient
    private val baseUrl = NetworkClient.BASE_URL

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
                                MessageItem(
                                    msgId = msg.msg_id,
                                    senderId = msg.sender?.chat_id ?: "",
                                    senderName = msg.sender?.name ?: "",
                                    senderAvatar = msg.sender?.avatar_url ?: "",
                                    senderType = msg.sender?.chat_type ?: 1,
                                    chatId = chatId,
                                    chatType = chatType,
                                    content = msg.content?.text ?: "",
                                    contentType = msg.content_type,
                                    timestamp = msg.send_time,
                                    msgSeq = msg.msg_seq,
                                    direction = msg.direction,
                                    isRecalled = msg.msg_delete_time > 0,
                                    deleteTime = msg.msg_delete_time,
                                    isEdited = msg.edit_time > 0,
                                    quoteMsgId = msg.quote_msg_id.takeIf { it.isNotEmpty() },
                                    quoteMsgText = msg.content?.quote_msg_text?.takeIf { it.isNotEmpty() },
                                    quoteImageUrl = msg.content?.quote_image_url?.takeIf { it.isNotEmpty() },
                                    stickerUrl = msg.content?.sticker_url?.takeIf { it.isNotEmpty() },
                                    imageUrl = msg.content?.image_url?.takeIf { it.isNotEmpty() },
                                    imageWidth = msg.content?.width?.takeIf { it > 0 },
                                    imageHeight = msg.content?.height?.takeIf { it > 0 },
                                    audioTime = if ((msg.content?.audio_time ?: 0) > 0) msg.content?.audio_time else null,
                                    videoUrl = msg.content?.video_url?.takeIf { it.isNotEmpty() },
                                    fileUrl = msg.content?.file_url?.takeIf { it.isNotEmpty() },
                                    fileName = msg.content?.file_name?.takeIf { it.isNotEmpty() },
                                    fileSize = if ((msg.content?.file_size ?: 0) > 0) msg.content?.file_size else null,
                                    cmdName = msg.cmd?.name?.takeIf { it.isNotEmpty() },
                                    cmdId = msg.cmd?.type?.toLong(),
                                    cmdType = msg.cmd?.type,
                                    postId = msg.content?.post_id,
                                    postTitle = msg.content?.post_title,
                                    postContent = msg.content?.post_content,
                                    postContentType = msg.content?.post_content_type?.toIntOrNull(),
                                    buttons = parseMessageButtons(msg.content?.buttons),
                                    tags = msg.sender?.tag?.map { tag ->
                                        MessageTag(
                                            id = tag.id,
                                            text = tag.text,
                                            color = tag.color
                                        )
                                    } ?: emptyList()
                                )
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

    suspend fun getImageMessageList(
        token: String,
        chatId: String,
        chatType: Int,
        imageId: Long,
        earlierQuantities: Int,
        latestQuantities: Int
    ): Result<List<ConversationImage>> {
        return withContext(Dispatchers.IO) {
            try {
                if (imageId == 0L) {
                    return@withContext Result.failure(
                        IllegalArgumentException("图片消息缺少有效的 image_id")
                    )
                }
                val requestBody = createImageMessageListRequestBody(
                    imageId = imageId,
                    chatId = chatId,
                    chatType = chatType,
                    earlierQuantities = earlierQuantities,
                    latestQuantities = latestQuantities
                )
                val httpRequest = Request.Builder()
                    .url("$baseUrl/v1/msg/pic-list-message-by-mid-seq")
                    .post(requestBody)
                    .header("token", token)
                    .build()

                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure<List<ConversationImage>>(
                            Exception("HTTP error: ${response.code}")
                        )
                    }

                    val result = pic_list_message_by_mid_seq.ADAPTER.decode(response.body.bytes())
                    if (result.status?.code == 1) {
                        Result.success(result.toConversationImages())
                    } else {
                        Result.failure(Exception(result.status?.msg ?: "获取图片列表失败"))
                    }
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
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val msgId = UUID.randomUUID().toString().replace("-", "")

                // 构建 ProtoBuf 请求
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

                val requestProto = send_message_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType.toLong(),
                    content = contentProto,
                    content_type = contentType.toLong(),
                    quote_msg_id = quoteMsgId ?: "",
                    command_id = commandId ?: 0L
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
                            Result.success(true)
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
                // 构建 ProtoBuf 请求
                val requestProto = recall_msg_send(
                    msg_id = msgId,
                    chat_id = chatId,
                    chat_type = chatType.toLong()
                )
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

internal fun createImageMessageListRequestBody(
    imageId: Long,
    chatId: String,
    chatType: Int,
    earlierQuantities: Int,
    latestQuantities: Int
) = pic_list_message_by_mid_seq_send(
    image_id = imageId,
    chat_type = chatType.toLong(),
    chat_id = chatId,
    earlier_quantities = earlierQuantities.coerceAtLeast(0).toLong(),
    latest_quantities = latestQuantities.coerceAtLeast(0).toLong()
).encode().toRequestBody("application/octet-stream".toMediaType())

internal fun pic_list_message_by_mid_seq.toConversationImages(): List<ConversationImage> {
    return msg.asSequence()
        .filter { message ->
            message.content_type == MessageItem.CONTENT_TYPE_IMAGE &&
                message.msg_delete_time == 0L &&
                message.msg_seq != 0L
        }
        .mapNotNull { message ->
            val url = message.content?.image_url?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            ConversationImage(
                messageId = message.msg_id,
                url = url,
                sequence = message.msg_seq,
                timestamp = message.send_time
            )
        }
        .sortedWith(compareBy<ConversationImage> { it.timestamp }.thenBy { it.sequence })
        .toList()
}
