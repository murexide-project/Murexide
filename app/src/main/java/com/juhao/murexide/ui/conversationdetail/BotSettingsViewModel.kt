package com.juhao.murexide.ui.conversationdetail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.repository.BotRepository
import com.juhao.murexide.repository.ManagedBot
import com.juhao.murexide.utils.QiniuUploader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

data class BotSettingsUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val original: ManagedBot? = null,
    val name: String = "",
    val introduction: String = "",
    val avatarUrl: String = "",
    val isPrivate: Boolean = false,
    val link: String = "",
    val subscribeType: Int = 0,
    val settingJson: String = "",
    val botToken: String = "",
    val tokenVisible: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val uploadProgress: Float = 0f,
    val isSaving: Boolean = false,
    val isResettingToken: Boolean = false,
    val showResetConfirm: Boolean = false
) {
    val infoChanged: Boolean
        get() = original != null && (
            name.trim() != original.nickname ||
                introduction.trim() != original.introduction ||
                avatarUrl.trim() != original.avatarUrl ||
                isPrivate != original.isPrivate
            )
    val linkChanged: Boolean
        get() = original != null &&
            (link.trim() != original.link || subscribeType != original.subscribeType)
    val settingChanged: Boolean
        get() = original != null && settingJson.trim() != original.settingJson.trim()
    val hasChanges: Boolean get() = infoChanged || linkChanged || settingChanged
}

class BotSettingsViewModel(
    private val token: String,
    private val botId: String,
    fallbackName: String = "",
    fallbackAvatar: String = "",
    private val repository: BotRepository = BotRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BotSettingsUiState(name = fallbackName, avatarUrl = fallbackAvatar)
    )
    val uiState: StateFlow<BotSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BotSettingsEvent>()
    val events: SharedFlow<BotSettingsEvent> = _events.asSharedFlow()

    sealed class BotSettingsEvent {
        data class ShowToast(val message: String) : BotSettingsEvent()
        object Saved : BotSettingsEvent()
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            repository.getMyBot(token, botId)
                .onSuccess { bot ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            original = bot,
                            name = bot.nickname,
                            introduction = bot.introduction,
                            avatarUrl = bot.avatarUrl,
                            isPrivate = bot.isPrivate,
                            link = bot.link,
                            subscribeType = bot.subscribeType,
                            settingJson = bot.settingJson,
                            botToken = bot.token
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "加载机器人信息失败") }
                }
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun updateIntroduction(value: String) = _uiState.update { it.copy(introduction = value) }
    fun togglePrivate(value: Boolean) = _uiState.update { it.copy(isPrivate = value) }
    fun updateLink(value: String) = _uiState.update { it.copy(link = value) }
    fun updateSubscribeType(value: Int) = _uiState.update { it.copy(subscribeType = value) }
    fun updateSettingJson(value: String) = _uiState.update { it.copy(settingJson = value) }
    fun toggleTokenVisible() = _uiState.update { it.copy(tokenVisible = !it.tokenVisible) }
    fun showResetConfirm(show: Boolean) = _uiState.update { it.copy(showResetConfirm = show) }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true, uploadProgress = 0f) }
            val uploader = QiniuUploader(context = context, userToken = token, enableWebp = true)
            uploader.uploadFromUri(
                context = context,
                uri = uri,
                onProgress = { percent -> _uiState.update { it.copy(uploadProgress = percent) } }
            ).onSuccess { response ->
                val avatarUrl = "https://chat-img.jwznb.com/${response.key}"
                _uiState.update { it.copy(isUploadingAvatar = false, avatarUrl = avatarUrl) }
                _events.emit(BotSettingsEvent.ShowToast("头像已上传，保存后生效"))
            }.onFailure { e ->
                _uiState.update { it.copy(isUploadingAvatar = false) }
                _events.emit(BotSettingsEvent.ShowToast("头像上传失败: ${e.message}"))
            }
        }
    }

    /** 只提交有改动的部分，每部分对应一个接口。 */
    fun save() {
        val state = _uiState.value
        val original = state.original ?: return
        if (state.isSaving || state.isUploadingAvatar) return
        if (!state.hasChanges) {
            viewModelScope.launch { _events.emit(BotSettingsEvent.ShowToast("没有需要保存的修改")) }
            return
        }
        validate(state)?.let { error ->
            viewModelScope.launch { _events.emit(BotSettingsEvent.ShowToast(error)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val errors = mutableListOf<String>()
            var updated = original
            val name = state.name.trim()
            val introduction = state.introduction.trim()
            val avatarUrl = state.avatarUrl.trim()
            val link = state.link.trim()
            val settingJson = state.settingJson.trim()

            if (state.infoChanged) {
                repository.editBot(token, botId, name, introduction, avatarUrl, state.isPrivate)
                    .onSuccess {
                        updated = updated.copy(
                            nickname = name,
                            introduction = introduction,
                            avatarUrl = avatarUrl,
                            isPrivate = state.isPrivate
                        )
                    }
                    .onFailure { errors += "基本信息：${it.message}" }
            }
            if (state.linkChanged) {
                repository.editSubscribedLink(token, botId, link, state.subscribeType)
                    .onSuccess { updated = updated.copy(link = link, subscribeType = state.subscribeType) }
                    .onFailure { errors += "订阅地址：${it.message}" }
            }
            if (state.settingChanged) {
                repository.editSettingJson(token, botId, settingJson)
                    .onSuccess { updated = updated.copy(settingJson = settingJson) }
                    .onFailure { errors += "设置 JSON：${it.message}" }
            }

            _uiState.update { it.copy(isSaving = false, original = updated) }
            if (errors.isEmpty()) {
                _events.emit(BotSettingsEvent.ShowToast("机器人设置已保存"))
                _events.emit(BotSettingsEvent.Saved)
            } else {
                _events.emit(BotSettingsEvent.ShowToast(errors.joinToString("\n")))
            }
        }
    }

    fun resetToken() {
        if (_uiState.value.isResettingToken) return
        viewModelScope.launch {
            _uiState.update { it.copy(showResetConfirm = false, isResettingToken = true) }
            repository.resetToken(token, botId)
                .onSuccess { newToken ->
                    _uiState.update {
                        it.copy(
                            isResettingToken = false,
                            botToken = newToken,
                            tokenVisible = true,
                            original = it.original?.copy(token = newToken)
                        )
                    }
                    _events.emit(BotSettingsEvent.ShowToast("Token 已重置，旧 Token 已失效"))
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isResettingToken = false) }
                    _events.emit(BotSettingsEvent.ShowToast("重置失败: ${e.message}"))
                }
        }
    }

    private fun validate(s: BotSettingsUiState): String? {
        if (s.name.isBlank()) return "机器人名称不能为空"
        val link = s.link.trim()
        if (link.isNotEmpty()) {
            val ok = if (s.subscribeType == 1) {
                link.startsWith("ws://") || link.startsWith("wss://")
            } else {
                link.startsWith("http://") || link.startsWith("https://")
            }
            if (!ok) {
                return if (s.subscribeType == 1) "WSS 订阅地址需以 ws:// 或 wss:// 开头"
                else "URL 订阅地址需以 http:// 或 https:// 开头"
            }
        }
        val setting = s.settingJson.trim()
        if (setting.isNotEmpty() && s.settingChanged) {
            val isArray = runCatching { Json.parseToJsonElement(setting) is JsonArray }.getOrDefault(false)
            if (!isArray) return "设置 JSON 必须是 JSON 数组，例如 []"
        }
        return null
    }
}
