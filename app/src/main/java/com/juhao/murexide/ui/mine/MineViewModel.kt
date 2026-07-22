package com.juhao.murexide.ui.mine

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.repository.ConversationDetailRepository
import com.juhao.murexide.repository.UserInfo
import com.juhao.murexide.data.SaveUserDataRequest
import com.juhao.murexide.data.UserProfileData
import com.juhao.murexide.utils.QiniuUploader
import android.content.Context
import android.net.Uri
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.datastore.UserAccount
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class MineUiState {
    object Loading : MineUiState()
    data class Success(
        val userInfo: UserInfo,
        val onlineDay: Int? = null,
        val continuousOnlineDay: Int? = null,
        val introduction: String = "",
        val userProfile: UserProfileData? = null,
        val isUploadingAvatar: Boolean = false,
        val uploadProgress: Float = 0f,
        val isSavingProfile: Boolean = false
    ) : MineUiState()
    data class Error(val message: String) : MineUiState()
}

class MineViewModel(
    application: Application,
    private val token: String,
    private val repository: AuthRepository = AuthRepository(),
    private val detailRepository: ConversationDetailRepository = ConversationDetailRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MineUiState>(MineUiState.Loading)
    val uiState: StateFlow<MineUiState> = _uiState

    private val _eventFlow = MutableSharedFlow<MineEvent>()
    val eventFlow: SharedFlow<MineEvent> = _eventFlow

    private val accountStorage = AccountStorage(application)

    sealed class MineEvent {
        data class ShowToast(val message: String) : MineEvent()
        object ProfileUpdated : MineEvent()
    }

    init {
        loadUserInfo()
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            _uiState.value = MineUiState.Loading

            repository.getUserInfo(token).onSuccess { userInfo ->
                _uiState.value = MineUiState.Success(userInfo)

                detailRepository.getDetail(token, userInfo.id, 1).onSuccess { detail ->
                    val current = _uiState.value
                    accountStorage.validateAccount(
                        UserAccount(
                            username = detail.name,
                            avatar = detail.avatarUrl,
                            id = detail.chatId
                        )
                    )
                    if (current is MineUiState.Success) {
                        _uiState.value = current.copy(
                            onlineDay = detail.onlineDay,
                            continuousOnlineDay = detail.continuousOnlineDay,
                            introduction = detail.introduction
                        )
                    }
                }

                repository.getUserData(token).onSuccess { profile ->
                    val current = _uiState.value
                    if (current is MineUiState.Success) {
                        _uiState.value = current.copy(userProfile = profile)
                    }
                }
            }.onFailure { error ->
                _uiState.value = MineUiState.Error(error.message ?: "获取用户信息失败")
            }
        }
    }

    /** 上传并修改头像 */
    fun uploadAndChangeAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is MineUiState.Success) {
                _uiState.value = currentState.copy(isUploadingAvatar = true, uploadProgress = 0f)
            }

            val uploader = QiniuUploader(
                context = context,
                userToken = token,
                enableWebp = true
            )
            
            uploader.uploadFromUri(
                context = context, 
                uri = uri,
                onProgress = { percent ->
                    val state = _uiState.value
                    if (state is MineUiState.Success) {
                        _uiState.value = state.copy(uploadProgress = percent)
                    }
                }
            ).onSuccess { response ->
                val avatarUrl = "https://chat-img.jwznb.com/${response.key}"
                repository.editAvatar(token, avatarUrl).onSuccess {
                    _eventFlow.emit(MineEvent.ShowToast("头像修改成功"))
                    loadUserInfo()
                }.onFailure { error ->
                    _eventFlow.emit(MineEvent.ShowToast(error.message ?: "修改头像失败"))
                    val state = _uiState.value
                    if (state is MineUiState.Success) {
                        _uiState.value = state.copy(isUploadingAvatar = false)
                    }
                }
            }.onFailure { error ->
                _eventFlow.emit(MineEvent.ShowToast("上传失败: ${error.message}"))
                val state = _uiState.value
                if (state is MineUiState.Success) {
                    _uiState.value = state.copy(isUploadingAvatar = false)
                }
            }
        }
    }

    /** 按 API 约束依次保存昵称和个人资料。 */
    fun saveProfile(
        nickname: String,
        introduction: String,
        gender: Int,
        birthday: Long,
        province: String,
        city: String,
        district: String,
        locationCode: String
    ) {
        val normalizedNickname = nickname.trim()
        val normalizedProvince = province.trim()
        val normalizedCity = city.trim()
        val normalizedDistrict = district.trim()
        val normalizedLocationCode = locationCode.trim()
        val validationError = validateProfile(
            nickname = normalizedNickname,
            gender = gender,
            birthday = birthday,
            province = normalizedProvince,
            city = normalizedCity,
            district = normalizedDistrict,
            locationCode = normalizedLocationCode
        )

        if (validationError != null) {
            viewModelScope.launch {
                _eventFlow.emit(MineEvent.ShowToast(validationError))
            }
            return
        }

        viewModelScope.launch {
            val current = _uiState.value as? MineUiState.Success ?: return@launch
            if (current.isSavingProfile) return@launch
            _uiState.value = current.copy(isSavingProfile = true)

            if (normalizedNickname != current.userInfo.name) {
                val nicknameResult = repository.editNickname(token, normalizedNickname)
                val nicknameError = nicknameResult.exceptionOrNull()
                if (nicknameError != null) {
                    setProfileSaving(false)
                    _eventFlow.emit(
                        MineEvent.ShowToast(nicknameError.message ?: "昵称保存失败")
                    )
                    return@launch
                }

                val savingState = _uiState.value
                if (savingState is MineUiState.Success) {
                    _uiState.value = savingState.copy(
                        userInfo = savingState.userInfo.copy(name = normalizedNickname)
                    )
                }
            }

            val request = SaveUserDataRequest(
                introduction = introduction.trim(),
                gender = gender,
                birthday = birthday,
                province = normalizedProvince,
                city = normalizedCity,
                district = normalizedDistrict,
                locationCode = normalizedLocationCode
            )

            repository.saveUserData(token, request).onSuccess {
                setProfileSaving(false)
                _eventFlow.emit(MineEvent.ShowToast("个人资料修改成功"))
                _eventFlow.emit(MineEvent.ProfileUpdated)
            }.onFailure { error ->
                setProfileSaving(false)
                _eventFlow.emit(MineEvent.ShowToast(error.message ?: "修改个人资料失败"))
            }
        }
    }

    private fun setProfileSaving(isSaving: Boolean) {
        val current = _uiState.value
        if (current is MineUiState.Success) {
            _uiState.value = current.copy(isSavingProfile = isSaving)
        }
    }

    private fun validateProfile(
        nickname: String,
        gender: Int,
        birthday: Long,
        province: String,
        city: String,
        district: String,
        locationCode: String
    ): String? {
        if (nickname.isBlank()) return "昵称不能为空"
        if (gender !in 1..3) return "请选择性别"
        if (birthday <= 0L) return "请选择生日"
        if (birthday > System.currentTimeMillis() / 1000L) return "生日不能晚于今天"
        if (province.isBlank() || city.isBlank() || district.isBlank()) {
            return "请完整填写省份、城市和区县"
        }
        if (!locationCode.matches(Regex("\\d{6}"))) {
            return "地区代码应为 6 位数字"
        }
        return null
    }
}
