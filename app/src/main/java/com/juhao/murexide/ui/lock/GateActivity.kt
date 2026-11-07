package com.juhao.murexide.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juhao.murexide.datastore.SettingsStorage
import com.juhao.murexide.ui.icons.AppIcons
import com.juhao.murexide.ui.theme.MurexideTheme
import kotlinx.coroutines.launch

class GateActivity : ComponentActivity() {

    private lateinit var storage: SettingsStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = SettingsStorage(this)
        AppLockState.gateVisible = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!AppLockState.unlocked) {
                    moveTaskToBack(true)
                }
            }
        })

        val target = intent.getParcelableExtra<Intent>(EXTRA_TARGET)

        setContent {
            MurexideTheme {
                GateContent(
                    storage = storage,
                    onPass = {
                        AppLockState.unlocked = true
                        passTo(target)
                    }
                )
            }
        }
    }

    private fun passTo(target: Intent?) {
        if (target != null) {
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(target) }
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLockState.gateVisible = false
    }

    companion object {
        const val EXTRA_TARGET = "gate_target"

        fun intentFor(context: Context, target: Intent?): Intent {
            val intent = Intent(context, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (target != null) {
                intent.putExtra(EXTRA_TARGET, target)
            }
            return intent
        }
    }
}

@Composable
private fun GateContent(
    storage: SettingsStorage,
    onPass: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lockEnabled = storage.getAppLockEnabled()
        enabled = lockEnabled
        if (!lockEnabled || AppLockState.unlocked) {
            onPass()
        }
    }

    val ready = enabled
    if (ready == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }
    if (!ready) return

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "应用已锁定",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "请输入应用锁密码以继续",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(12)
                        error = null
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (pin.length < 4 || checking) return@Button
                        checking = true
                        scope.launch {
                            val ok = storage.verifyAppLockPin(pin)
                            checking = false
                            if (ok) {
                                AppLockState.unlocked = true
                                onPass()
                            } else {
                                error = "密码错误"
                                pin = ""
                            }
                        }
                    },
                    enabled = pin.length >= 4 && !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checking) "验证中…" else "解锁")
                }
            }
        }
    }
}