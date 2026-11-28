package com.juhao.murexide.plugin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.juhao.murexide.ui.theme.MurexideTheme

class PluginDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)
        if (pluginId.isNullOrBlank()) {
            finish()
            return
        }
        PluginManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MurexideTheme {
                val manifests by PluginManager.manifests.collectAsState()
                val manifest = manifests.firstOrNull { it.id == pluginId }
                if (manifest == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    PluginDetailScreen(manifest, onDismiss = { finish() })
                }
                PluginDialogHost()
            }
        }
    }

    companion object {
        private const val EXTRA_PLUGIN_ID = "plugin_id"

        fun start(context: Context, pluginId: String) {
            context.startActivity(Intent(context, PluginDetailActivity::class.java).apply {
                putExtra(EXTRA_PLUGIN_ID, pluginId)
            })
        }
    }
}
