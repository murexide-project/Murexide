@file:Suppress("AssignedValueIsNeverRead")

package com.juhao.murexide.ui.login

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.R

private enum class LoginMode { PHONE, EMAIL }

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onTokenLogin: () -> Unit,
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val phoneState by viewModel.phoneState.collectAsState()

    var loginMode by remember { mutableStateOf(LoginMode.PHONE) }
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess((uiState as LoginUiState.Success).token)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "欢迎登录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            SegmentedButton(
                selected = loginMode == LoginMode.PHONE,
                onClick = { loginMode = LoginMode.PHONE },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("手机号登录") }
            SegmentedButton(
                selected = loginMode == LoginMode.EMAIL,
                onClick = { loginMode = LoginMode.EMAIL },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("邮箱登录") }
        }

        when (loginMode) {
            LoginMode.PHONE -> PhoneLoginContent(
                phone = phone,
                smsCode = smsCode,
                phoneState = phoneState,
                onPhoneChange = { phone = it; viewModel.clearPhoneError() },
                onSmsCodeChange = { smsCode = it; viewModel.clearPhoneError() },
                onSendCode = { viewModel.requestCaptcha() },
                onLogin = { viewModel.phoneLogin(phone.trim(), smsCode.trim()) }
            )
            LoginMode.EMAIL -> EmailLoginContent(
                uiState = uiState,
                onLogin = { email, password -> viewModel.login(email, password) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onTokenLogin) {
                Text("Token登录")
            }
        }
    }

    phoneState.captchaImage?.let { image ->
        CaptchaDialog(
            imageBase64 = image,
            isSending = phoneState.isSendingSms,
            onRefresh = { viewModel.requestCaptcha() },
            onDismiss = { viewModel.dismissCaptcha() },
            onConfirm = { captchaCode ->
                viewModel.submitCaptchaAndSendSms(phone.trim(), captchaCode)
            }
        )
    }
}

@Composable
private fun PhoneLoginContent(
    phone: String,
    smsCode: String,
    phoneState: PhoneLoginState,
    onPhoneChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onLogin: () -> Unit
) {
    val phoneValid = phone.trim().length == 11 && phone.trim().all { it.isDigit() }

    // 手机号
    OutlinedTextField(
        value = phone,
        onValueChange = { if (it.length <= 11) onPhoneChange(it.filter { c -> c.isDigit() }) },
        label = { Text("手机号") },
        placeholder = { Text("请输入手机号") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp)
    )

    // 验证码 + 获取验证码
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = smsCode,
            onValueChange = { if (it.length <= 6) onSmsCodeChange(it.filter { c -> c.isDigit() }) },
            label = { Text("验证码") },
            placeholder = { Text("短信验证码") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        val canSend = phoneValid && phoneState.countdown == 0 &&
                !phoneState.isCaptchaLoading && !phoneState.isSendingSms
        OutlinedButton(
            onClick = onSendCode,
            enabled = canSend,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (phoneState.isCaptchaLoading || phoneState.isSendingSms) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (phoneState.countdown > 0) "${phoneState.countdown}s" else "获取验证码"
                )
            }
        }
    }

    phoneState.error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Button(
        onClick = onLogin,
        enabled = !phoneState.isLoggingIn && phoneValid && smsCode.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (phoneState.isLoggingIn) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = "登录", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmailLoginContent(
    uiState: LoginUiState,
    onLogin: (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = email,
        onValueChange = { email = it; emailError = null },
        label = { Text("邮箱") },
        placeholder = { Text("请输入邮箱地址") },
        isError = emailError != null,
        supportingText = emailError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp)
    )

    OutlinedTextField(
        value = password,
        onValueChange = { password = it; passwordError = null },
        label = { Text("密码") },
        placeholder = { Text("请输入密码") },
        visualTransformation = PasswordVisualTransformation(),
        isError = passwordError != null,
        supportingText = passwordError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(12.dp)
    )

    if (uiState is LoginUiState.Error) {
        Text(
            text = uiState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Button(
        onClick = {
            var hasError = false
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailError = "邮箱格式不正确"; hasError = true
            }
            if (password.length < 6) {
                passwordError = "密码长度不能少于6位"; hasError = true
            }
            if (!hasError) onLogin(email, password)
        },
        enabled = uiState !is LoginUiState.Loading && email.isNotBlank() && password.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (uiState is LoginUiState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = "登录", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CaptchaDialog(
    imageBase64: String,
    isSending: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var captchaCode by remember { mutableStateOf("") }

    val bitmap = remember(imageBase64) {
        runCatching {
            val pure = if (imageBase64.contains(",")) imageBase64.substringAfterLast(",") else imageBase64
            val bytes = Base64.decode(pure, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("人机验证") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "验证码图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        "验证码图片加载失败",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                TextButton(onClick = onRefresh) { Text("看不清，换一张") }

                OutlinedTextField(
                    value = captchaCode,
                    onValueChange = { captchaCode = it },
                    placeholder = { Text("请输入图片中的字符") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(captchaCode.trim()) },
                enabled = captchaCode.isNotBlank() && !isSending
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("确定")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}