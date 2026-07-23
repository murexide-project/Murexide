package com.juhao.murexide.ui.theme

import androidx.compose.runtime.mutableStateOf
import com.juhao.murexide.data.ConversationItem

object UiState {
    var themeMode = mutableStateOf("system")
    var themeColor = mutableStateOf("DYNAMIC")
    var squareAvatar = mutableStateOf(false)
}

object UiCache {
    var conversation = mutableStateOf<List<ConversationItem>>(emptyList())
}