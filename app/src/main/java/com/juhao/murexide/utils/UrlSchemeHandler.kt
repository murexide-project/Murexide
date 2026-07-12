package com.juhao.murexide.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.juhao.murexide.ui.chat.ChatActivity
import com.juhao.murexide.ui.community.detail.PostDetailActivity

/**
 * 统一处理 yunhu:// 协议链接（见 api 文档 url_scheme）。
 *
 * 支持：
 * - yunhu://post-detail?id=xxx     跳转文章详情
 * - yunhu://chat-add?id=xxx&type=  打开聊天（user/group/bot）
 * - yunhu://alley-detail?id=xxx    跳转文章分区（暂未实现独立页面）
 * - yunhu://ad?id=xxx              观看广告（不支持）
 *
 * 供 markdown 链接、HTML WebView、消息点击等各处复用。
 */
object UrlSchemeHandler {

    private const val TAG = "UrlSchemeHandler"

    /** 是否为需要本处理器接管的 yunhu:// 链接 */
    fun isYunhuScheme(url: String): Boolean = url.startsWith("yunhu://")

    /**
     * 处理链接。返回 true 表示已消费（无论成功与否），调用方不应再继续默认处理。
     */
    fun handle(context: Context, url: String): Boolean {
        if (!isYunhuScheme(url)) return false

        return try {
            val uri = url.toUri()
            when (uri.host) {
                "post-detail" -> {
                    val id = uri.getQueryParameter("id")?.toIntOrNull()
                    if (id != null && id > 0) {
                        PostDetailActivity.start(context, id)
                    } else {
                        toast(context, "无效的文章链接")
                    }
                    true
                }

                "chat-add" -> {
                    val id = uri.getQueryParameter("id")
                    val type = uri.getQueryParameter("type")
                    if (!id.isNullOrEmpty()) {
                        ChatActivity.start(
                            context = context,
                            chatId = id,
                            chatType = chatTypeOf(type),
                            chatName = "",
                            chatAvatar = ""
                        )
                    } else {
                        toast(context, "无效的聊天链接")
                    }
                    true
                }

                "alley-detail" -> {
                    // TODO: 分区详情页尚未实现
                    toast(context, "暂不支持打开文章分区")
                    true
                }

                "ad" -> {
                    toast(context, "不支持观看广告")
                    true
                }

                else -> {
                    // 未知的 yunhu 链接，尝试交给系统
                    tryStartView(context, uri.toString())
                    true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "处理 yunhu 链接失败: $url", e)
            true
        }
    }

    /** type: user-1, group-2, bot-3 */
    private fun chatTypeOf(type: String?): Int = when (type) {
        "user" -> 1
        "group" -> 2
        "bot" -> 3
        else -> 1
    }

    private fun tryStartView(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
