package com.juhao.murexide.data

import kotlinx.serialization.Serializable

@Serializable
enum class UserBotMatchType {
    CONTAINS,
    EXACT,
    REGEX
}

@Serializable
data class UserBotRule(
    val keyword: String = "",
    val reply: String = "",
    val matchType: UserBotMatchType = UserBotMatchType.CONTAINS
)

@Serializable
data class UserBotRulesWrapper(
    val rules: List<UserBotRule> = emptyList()
)