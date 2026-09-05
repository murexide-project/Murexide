package com.juhao.murexide.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.juhao.murexide.datastore.AccountStorage
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.AuthRepository
import com.juhao.murexide.ui.login.LoginActivity
import com.juhao.murexide.ui.theme.MurexideTheme
import com.juhao.murexide.ui.theme.UiCache
import com.juhao.murexide.utils.DeviceIdProvider
import com.juhao.murexide.utils.NotificationHelper
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val accountStorage = AccountStorage.getInstance(this)
        val authRepository = AuthRepository()
        
        val page = intent.getIntExtra("page", 0)

        setContent {
            MurexideTheme {
                SettingsScreen(
                    onBack = { finish() },
                    page = page,
                    onLogout = {
                        lifecycleScope.launch {
                            val token = accountStorage.getCurrentToken()
                            val remoteLogoutError = token
                                ?.let {
                                    authRepository.logout(
                                        token = it,
                                        deviceId = DeviceIdProvider.get(this@SettingsActivity)
                                    ).exceptionOrNull()
                                }

                            val localLogoutResult = runCatching {
                                accountStorage.removeCurrentUser()
                            }
                            WebSocketManager.getInstance().disconnect()
                            NotificationHelper.clearAllNotifications(this@SettingsActivity)
                            UiCache.clearAccountData()

                            if (localLogoutResult.isFailure) {
                                Toast.makeText(
                                    this@SettingsActivity,
                                    localLogoutResult.exceptionOrNull()?.message ?: "退出登录失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }

                            Toast.makeText(
                                this@SettingsActivity,
                                if (remoteLogoutError == null) {
                                    "已登出"
                                } else {
                                    "已在本机退出，服务器会话退出失败"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                )
            }
        }
    }
}
