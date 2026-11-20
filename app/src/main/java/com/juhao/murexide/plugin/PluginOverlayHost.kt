package com.juhao.murexide.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PluginOverlayHost() {
    val active by PluginSession.activeDecorates.collectAsState()
    if (active.isEmpty()) return
    Box(Modifier.fillMaxSize()) {
        active.forEach { ad ->
            val align = if (ad.action.position == "top") Alignment.TopCenter else Alignment.BottomCenter
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(align)
            ) {
                PluginWidgetRenderer(ad.action.widget, ad.pluginId, ad.env)
            }
        }
    }
}