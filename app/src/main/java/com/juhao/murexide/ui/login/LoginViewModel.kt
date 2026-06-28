package com.juhao.murexide.ui.login

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val token: String) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/** 手机号登录相关状态（人机验证 + 短信验证码） */
data class PhoneLoginState(
    val captchaImage: String? = null,   // 非空表示展示人机验证弹窗（base64 图片）
    val captchaId: String = "",
    val isCaptchaLoading: Boolean = false,
    val isSendingSms: Boolean = false,
    val countdown: Int = 0,             // 重新获取验证码的倒计时（秒）
    val isLoggingIn: Boolean = false,
    val error: String? = null
)

class LoginViewModel(
    application: Application,
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {
    
    private val tokenStorage = TokenStorage(application)
    
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val _phoneState = MutableStateFlow(PhoneLoginState())
    val phoneState: StateFlow<PhoneLoginState> = _phoneState

    private var countdownJob: kotlinx.coroutines.Job? = null

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            
            val deviceId = getDeviceId()
            
            repository.login(email, password, deviceId).onSuccess { token ->
                tokenStorage.saveToken(token)
                _uiState.value = LoginUiState.Success(token)
            }.onFailure { error ->
                _uiState.value = LoginUiState.Error(error.message ?: "登录失败")
            }
        }
    }

    /** 请求人机验证图片，并打开验证弹窗 */
    fun requestCaptcha() {
        _phoneState.update { it.copy(isCaptchaLoading = true, error = null) }
        viewModelScope.launch {
            repository.getCaptcha().onSuccess { captcha ->
                _phoneState.update {
                    it.copy(
                        captchaImage = captcha.b64s,
                        captchaId = captcha.id,
                        isCaptchaLoading = false
                    )
                }
            }.onFailure { error ->
                _phoneState.update {
                    it.copy(isCaptchaLoading = false, error = error.message ?: "获取验证码图片失败")
                }
            }
        }
    }

    /** 关闭人机验证弹窗 */
    fun dismissCaptcha() {
        _phoneState.update { it.copy(captchaImage = null) }
    }

    /** 提交人机验证码并发送短信验证码，成功后关闭弹窗并开始倒计时 */
    fun submitCaptchaAndSendSms(mobile: String, captchaCode: String) {
        val captchaId = _phoneState.value.captchaId
        _phoneState.update { it.copy(isSendingSms = true, error = null) }
        viewModelScope.launch {
            repository.sendSmsCode(mobile, captchaCode, captchaId).onSuccess {
                _phoneState.update {
                    it.copy(isSendingSms = false, captchaImage = null)
                }
                startCountdown()
            }.onFailure { error ->
                // 人机验证码通常一次性，失败后刷新图片便于重试
                _phoneState.update {
                    it.copy(isSendingSms = false, error = error.message ?: "发送验证码失败")
                }
                requestCaptcha()
            }
        }
    }

    /** 短信验证码登录 */
    fun phoneLogin(mobile: String, smsCode: String) {
        viewModelScope.launch {
            _phoneState.update { it.copy(isLoggingIn = true, error = null) }
            val deviceId = getDeviceId()
            repository.phoneLogin(mobile, smsCode, deviceId).onSuccess { token ->
                tokenStorage.saveToken(token)
                _phoneState.update { it.copy(isLoggingIn = false) }
                _uiState.value = LoginUiState.Success(token)
            }.onFailure { error ->
                _phoneState.update {
                    it.copy(isLoggingIn = false, error = error.message ?: "登录失败")
                }
            }
        }
    }

    fun clearPhoneError() {
        _phoneState.update { it.copy(error = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in 60 downTo 1) {
                _phoneState.update { it.copy(countdown = seconds) }
                delay(1000)
            }
            _phoneState.update { it.copy(countdown = 0) }
        }
    }

    private fun getDeviceId(): String {
        return "android_device_${System.currentTimeMillis()}"
    }
}
