package com.juhao.murexide.plugin

data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val enabled: Boolean,
    val description: String?,
    val rules: List<PluginRule>,
    val onInstalled: List<PluginRule>,
    val onUninstalled: List<PluginRule>
)

data class PluginRule(
    val page: String,
    val event: String,
    val action: String,
    val position: String,
    val content: PluginContent
)

data class PluginContent(
    val type: String,
    val value: String? = null,
    val color: String? = null,
    val title: String? = null,
    val message: String? = null,
    val confirmText: String? = null,
    val duration: String? = null,
    val onClick: PluginAction? = null,
    val execute: PluginAction? = null,
    val children: List<PluginContent>? = null
)

data class PluginAction(
    val action: String,
    val target: String? = null,
    val text: String? = null
)