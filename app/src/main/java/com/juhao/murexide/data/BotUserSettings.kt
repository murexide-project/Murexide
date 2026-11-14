package com.juhao.murexide.data

import kotlinx.serialization.json.JsonObject

/**
 * 群聊内机器人设置表单项（来自 /v1/bot/get-user-settings-json 的 settingsJson 数组）。
 *
 * 服务端结构（文档 /api/v1/bot#获取群聊内机器人设置）：
 * ```
 * {
 *   "id": "pqkyru",            // 表单项 ID
 *   "key": 0,                  // 第几个项
 *   "type": "radio",           // radio | input | switch | checkbox | textarea | select
 *   "title": "Radio 单选框",    // 该项标题
 *   "props": [{ "name": "标签", "type": "label", "value": "" }, ...],   // 蓝图（含默认值）
 *   "propsValue": { "label": "...", "options": "...", "defaultValue": "..." }  // 成品（实际配置）
 * }
 * ```
 * 具体解释文档见 /api/v1/instruction#自定义输入指令部分解释（与 instruction 的 customJson 同构）。
 */
data class BotUserSettingsItem(
    val id: String,
    val type: String,          // radio | input | switch | checkbox | textarea | select
    val title: String,
    val options: List<String>, // radio/checkbox/select 的候选项（"#" 分隔）
    val defaultValue: String,  // 当前值（字符串化；switch 为 "1"/"0"）
    val raw: JsonObject        // 原始项，保存时原样回传并注入 value
)

/** 表单控件类型。 */
object BotSettingsFieldType {
    const val RADIO = "radio"
    const val INPUT = "input"
    const val SWITCH = "switch"
    const val CHECKBOX = "checkbox"
    const val TEXTAREA = "textarea"
    const val SELECT = "select"

    val ALL = setOf(RADIO, INPUT, SWITCH, CHECKBOX, TEXTAREA, SELECT)
}