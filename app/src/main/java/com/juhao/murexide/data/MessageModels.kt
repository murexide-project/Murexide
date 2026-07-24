package com.juhao.murexide.data

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class MessageDisplayItem(
    val message: MessageItem,
    val isFirstFromSender: Boolean,
    val isLastFromSender: Boolean,
    val roleLabel: String?
)

@Serializable
data class MessageItem(
    val msgId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String,
    val senderType: Int = 1,
    val chatId: String = "",
    val chatType: Int = 1,
    val content: String = "",
    val contentType: Int,
    val timestamp: Long,
    val deleteTime: Long = 0,
    val msgSeq: Long = 0,
    val direction: String,
    val isRecalled: Boolean = false,
    val isEdited: Boolean = false,
    val quoteMsgId: String? = null,
    val quoteMsgText: String? = null,
    val quoteImageUrl: String? = null,
    val stickerUrl: String? = null,
    val imageUrl: String? = null,
    val imageWidth: Long? = null,
    val imageHeight: Long? = null,
    val audioUrl: String? = null,
    val audioTime: Int? = null,
    val videoUrl: String? = null,
    val videoTime: Int? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val cmdName: String? = null,
    val cmdId: Long? = null,
    val cmdType: Int? = null,
    val postId: String? = null,
    val postTitle: String? = null,
    val postContent: String? = null,
    val postContentType: Int? = null,
    val buttons: List<List<MessageButton>> = emptyList(),
    val tags: List<MessageTag> = emptyList()
) {
    val isMine: Boolean
        get() = direction == "right"

    fun getDisplayContent(): String {
        return when (contentType) {
            CONTENT_TYPE_IMAGE -> "[图片消息]"
            CONTENT_TYPE_FILE -> "[文件消息]"
            CONTENT_TYPE_STICKER -> "[表情消息]"
            CONTENT_TYPE_VIDEO -> "[视频消息]"
            CONTENT_TYPE_AUDIO -> "[语音消息]"
            CONTENT_TYPE_MARKDOWN -> "[Markdown消息]"
            CONTENT_TYPE_HTML -> "[HTML消息]"
            CONTENT_TYPE_POST -> "[文章]"
            else -> content.takeIf { it.isNotEmpty() } ?: "[消息]"
        }
    }

    companion object {
        const val CONTENT_TYPE_TEXT = 1
        const val CONTENT_TYPE_IMAGE = 2
        const val CONTENT_TYPE_MARKDOWN = 3
        const val CONTENT_TYPE_FILE = 4
        const val CONTENT_TYPE_POST = 6
        const val CONTENT_TYPE_STICKER = 7
        const val CONTENT_TYPE_HTML = 8
        const val CONTENT_TYPE_TIP = 9
        const val CONTENT_TYPE_VIDEO = 10
        const val CONTENT_TYPE_AUDIO = 11
    }
}

@Serializable
data class MessageButton(
    val text: String = "",
    val actionType: Int = 0, // 1-跳转URL，2-复制，3-上报点击事件
    val url: String? = null,
    val value: String? = null
) {
    companion object {
        const val ACTION_JUMP = 1
        const val ACTION_COPY = 2
        const val ACTION_REPORT = 3
    }
}

private val buttonsJson = Json { ignoreUnknownKeys = true }


fun parseMessageButtons(raw: String?): List<List<MessageButton>> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        buttonsJson.decodeFromString<List<List<MessageButton>>>(raw)
    } catch (e: Exception) {
        Log.w("MessageButtons", "解析按钮失败: $raw", e)
        emptyList()
    }
}

@Serializable
data class MessageTag(
    val id: Long,
    val text: String,
    val color: String
)

@Serializable
data class MessageContent(
    val text: String = "",
    val image: String? = null,
    val quoteMsgText: String? = null,
    val quoteImageUrl: String? = null,
    val quoteImageName: String? = null,
    val fileKey: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val audio: String? = null,
    val audioTime: Int? = null,
    val video: String? = null,
    val postType: String? = null,
    val expressionId: String? = null,
    val stickerItemId: Long? = null,
    val stickerPackId: Long? = null,
    val mentionedId: List<String> = emptyList(),
    val commandId: Long? = null,
    val form: String? = null
)

data class ChatUiState(
    val messages: List<MessageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val replyTo: MessageItem? = null,
    val sendType: String = "text",
    val hasMore: Boolean = true,
    val chatName: String = "",
    val chatAvatar: String = "",
    val isSending: Boolean = false,
    val backgroundUrl: String? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadImagePath: String? = null,
    val selectionMode: Boolean = false,
    val selectedMessages: Set<MessageItem> = emptySet(),
    val downloadedFiles: Set<String> = emptySet(),
    val instructionPanel: InstructionPanelState = InstructionPanelState(),
    val pendingCommandId: Long? = null,
    val pendingCommandName: String? = null,
    val pendingCommandHint: String? = null,
    val editingMessage: MessageItem? = null,
    val boardPanel: BoardPanelState = BoardPanelState(),
    
    // -----群聊专属-----
    val isAdmin: Boolean = false,
    val memberCount: Long? = null,
    val myGroupNickname: String? = null,
    val ownerId: String? = null,
    val adminIds: Set<String> = emptySet(),
    val permissionLevel: Int = 0,      // 群主 100 / 管理员 2 / 普通 0
    val groupMembers: GroupMembersState = GroupMembersState(),
    val mentionPicker: MentionPickerState = MentionPickerState(),
    val mentions: Map<String, String> = emptyMap(), // @名称 -> userId
    
    // -----机器人专属-----
    val usageCount: Long? = null
)
