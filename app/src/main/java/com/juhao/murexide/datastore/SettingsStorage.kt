package com.juhao.murexide.datastore

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

internal const val MAX_RECENT_DEFAULT_EMOJIS = 16
private const val RECENT_DEFAULT_EMOJI_SEPARATOR = "\u0000"

internal fun updateRecentDefaultEmojiNames(
    recentNames: Iterable<String>,
    usedName: String,
    limit: Int = MAX_RECENT_DEFAULT_EMOJIS
): List<String> {
    if (limit <= 0) return emptyList()

    return buildList {
        if (usedName.isNotBlank()) add(usedName)
        recentNames.forEach { name ->
            if (name.isNotBlank() && name != usedName) add(name)
        }
    }.distinct().take(limit)
}

internal fun encodeRecentDefaultEmojiNames(names: Iterable<String>): String {
    return updateRecentDefaultEmojiNames(
        recentNames = names,
        usedName = ""
    ).joinToString(RECENT_DEFAULT_EMOJI_SEPARATOR)
}

internal fun decodeRecentDefaultEmojiNames(value: String?): List<String> {
    if (value.isNullOrEmpty()) return emptyList()
    return updateRecentDefaultEmojiNames(
        recentNames = value.split(RECENT_DEFAULT_EMOJI_SEPARATOR),
        usedName = ""
    )
}

class SettingsStorage(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color")

        private val NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("notification_enabled")
        
        private val SQUARE_AVATAR_KEY = booleanPreferencesKey("square_avatar")
        private val AVATAR_FOLLOW_KEY = booleanPreferencesKey("avatar_follow")
        private val BIG_SCREEN_KEY = booleanPreferencesKey("big_screen")
        private val UPDATE_CHANNEL_KEY = stringPreferencesKey("update_channel")
        
        private val FLOAT_BOTTOM_BAR_KEY = booleanPreferencesKey("float_bottom_bar")
        private val SHOW_STICKY_KEY = booleanPreferencesKey("show_sticky")
        
        private val MSG_SHOW_TAGS_KEY = booleanPreferencesKey("msg_show_tags")
        private val BUBBLE_CORNER_RADIUS_KEY = floatPreferencesKey("bubble_corner_radius")
        private val BUBBLE_OPACITY_KEY = floatPreferencesKey("bubble_opacity")
        
        private val SHOW_BACKGROUND_KEY = booleanPreferencesKey("show_background")
        private val BACKGROUND_OPACITY_KEY = floatPreferencesKey("background_opacity")
        
        private val SHOW_MY_BUBBLE_AVATAR_KEY = booleanPreferencesKey("show_my_bubble_avatar")
        private val LIQUID_GLASS_ENABLED_KEY = booleanPreferencesKey("liquid_glass_enabled")
        private val LIQUID_GLASS_BLUR_KEY = floatPreferencesKey("liquid_glass_blur")
        private val RECENT_DEFAULT_EMOJI_NAMES_KEY =
            stringPreferencesKey("recent_default_emoji_names")

        // ====== 截图隐私设置 ======
        private val SCREENSHOT_HIDE_SENDER_INFO_KEY = booleanPreferencesKey("screenshot_hide_sender_info")
        private val SCREENSHOT_HIDE_MY_INFO_KEY = booleanPreferencesKey("screenshot_hide_my_info")
        private val SCREENSHOT_HIDE_SESSION_INFO_KEY = booleanPreferencesKey("screenshot_hide_session_info")
        private val SCREENSHOT_HIDE_IMAGES_KEY = booleanPreferencesKey("screenshot_hide_images")
    }

    // 主题模式
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun getThemeMode(): String {
        return themeModeFlow.first()
    }
    
    // 主题颜色
    val themeColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_COLOR_KEY] ?: "DYNAMIC"
    }

    suspend fun setThemeColor(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = mode
        }
    }

    suspend fun getThemeColor(): String {
        return themeColorFlow.first()
    }

    // 圆角正方形头像
    val squareAvatarFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SQUARE_AVATAR_KEY] ?: false
    }

    suspend fun setSquareAvatar(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SQUARE_AVATAR_KEY] = enabled
        }
    }

    suspend fun getSquareAvatar(): Boolean {
        return squareAvatarFlow.first()
    }

    // 通知
    val notificationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_ENABLED_KEY] ?: true  // 默认开启
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun getNotificationEnabled(): Boolean {
        return notificationEnabledFlow.first()
    }
    
    // 头像跟随
    val avatarFollowFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AVATAR_FOLLOW_KEY] ?: true
    }

    suspend fun setAvatarFollow(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVATAR_FOLLOW_KEY] = enabled
        }
    }

    suspend fun getAvatarFollow(): Boolean {
        return avatarFollowFlow.first()
    }

    // 大屏模式
    val bigScreenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIG_SCREEN_KEY] ?: true
    }

    suspend fun setBigScreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIG_SCREEN_KEY] = enabled
        }
    }

    suspend fun getBigScreen(): Boolean {
        return bigScreenFlow.first()
    }
    
    // 更新频道
    val updateChannelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_CHANNEL_KEY] ?: "stable"
    }

    suspend fun setUpdateChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_CHANNEL_KEY] = channel
        }
    }

    suspend fun getUpdateChannel(): String {
        return updateChannelFlow.first()
    }
    
    // 展开置顶
    val showStickyFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_STICKY_KEY] ?: true
    }

    suspend fun setShowSticky(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_STICKY_KEY] = value
        }
    }
    
    // 悬浮底栏
    val isFloatBottomBarEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FLOAT_BOTTOM_BAR_KEY] ?: true
    }

    suspend fun setFloatBottomBar(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FLOAT_BOTTOM_BAR_KEY] = value
        }
    }
    
    // 显示标签
    val showMsgTagsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MSG_SHOW_TAGS_KEY] ?: true
    }

    suspend fun setShowMsgTags(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MSG_SHOW_TAGS_KEY] = enabled
        }
    }

    suspend fun getShowMsgTags(): Boolean {
        return showMsgTagsFlow.first()
    }
    
    // ====== 气泡圆角 ======
    val bubbleCornerRadiusFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUBBLE_CORNER_RADIUS_KEY] ?: 18f
    }

    suspend fun setBubbleCornerRadius(radius: Float) {
        context.dataStore.edit { preferences ->
            preferences[BUBBLE_CORNER_RADIUS_KEY] = radius
        }
    }

    suspend fun getBubbleCornerRadius(): Float {
        return bubbleCornerRadiusFlow.first()
    }

    // ====== 气泡不透明度 ======
    val bubbleOpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BUBBLE_OPACITY_KEY] ?: 0.9f
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BUBBLE_OPACITY_KEY] = opacity
        }
    }

    suspend fun getBubbleOpacity(): Float {
        return bubbleOpacityFlow.first()
    }
    
    // ====== 显示背景 ======
    val showBackgroundFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_BACKGROUND_KEY] ?: true
    }

    suspend fun setShowBackground(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_BACKGROUND_KEY] = value
        }
    }

    suspend fun getShowBackground(): Boolean {
        return showBackgroundFlow.first()
    }
    
    // ====== 背景不透明度 ======
    val backgroundOpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BACKGROUND_OPACITY_KEY] ?: 0.4f
    }

    suspend fun setBackgroundOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_OPACITY_KEY] = opacity
        }
    }

    suspend fun getBackgroundOpacity(): Float {
        return backgroundOpacityFlow.first()
    }

    // ====== 显示气泡（我）头像 ======
    val showMyBubbleAvatarFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHOW_MY_BUBBLE_AVATAR_KEY] ?: true
    }

    suspend fun setShowMyBubbleAvatar(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_MY_BUBBLE_AVATAR_KEY] = show
        }
    }

    // ====== 液态玻璃效果 ======
    val liquidGlassEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        (preferences[LIQUID_GLASS_ENABLED_KEY] == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LIQUID_GLASS_ENABLED_KEY] = enabled
        }
    }

    suspend fun getLiquidGlassEnabled(): Boolean {
        return liquidGlassEnabledFlow.first()
    }

    // ====== 液态玻璃模糊强度 ======
    val liquidGlassBlurFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_BLUR_KEY]?.coerceIn(0f, 4f) ?: 3f
    }

    suspend fun setLiquidGlassBlur(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[LIQUID_GLASS_BLUR_KEY] = value.coerceIn(0f, 4f)
        }
    }

    suspend fun getLiquidGlassBlur(): Float {
        return liquidGlassBlurFlow.first()
    }

    // ====== 最近使用的默认表情 ======
    val recentDefaultEmojiNamesFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        decodeRecentDefaultEmojiNames(preferences[RECENT_DEFAULT_EMOJI_NAMES_KEY])
    }

    suspend fun recordRecentDefaultEmoji(name: String) {
        if (name.isBlank()) return

        context.dataStore.edit { preferences ->
            val recentNames = decodeRecentDefaultEmojiNames(
                preferences[RECENT_DEFAULT_EMOJI_NAMES_KEY]
            )
            preferences[RECENT_DEFAULT_EMOJI_NAMES_KEY] = encodeRecentDefaultEmojiNames(
                updateRecentDefaultEmojiNames(recentNames, name)
            )
        }
    }

    // ====== 截图隐私设置 ======

    // 隐藏发送者信息（发送者名称、头像）
    val screenshotHideSenderInfoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_HIDE_SENDER_INFO_KEY] ?: false
    }

    suspend fun setScreenshotHideSenderInfo(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_HIDE_SENDER_INFO_KEY] = enabled
        }
    }

    suspend fun getScreenshotHideSenderInfo(): Boolean {
        return screenshotHideSenderInfoFlow.first()
    }

    // 隐藏我的信息（我的名称、头像也显示为对方）
    val screenshotHideMyInfoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_HIDE_MY_INFO_KEY] ?: false
    }

    suspend fun setScreenshotHideMyInfo(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_HIDE_MY_INFO_KEY] = enabled
        }
    }

    suspend fun getScreenshotHideMyInfo(): Boolean {
        return screenshotHideMyInfoFlow.first()
    }

    // 隐藏会话信息（会话名称、会话头像）
    val screenshotHideSessionInfoFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_HIDE_SESSION_INFO_KEY] ?: false
    }

    suspend fun setScreenshotHideSessionInfo(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_HIDE_SESSION_INFO_KEY] = enabled
        }
    }

    suspend fun getScreenshotHideSessionInfo(): Boolean {
        return screenshotHideSessionInfoFlow.first()
    }

    // 隐藏图片及表情包
    val screenshotHideImagesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SCREENSHOT_HIDE_IMAGES_KEY] ?: false
    }

    suspend fun setScreenshotHideImages(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREENSHOT_HIDE_IMAGES_KEY] = enabled
        }
    }

    suspend fun getScreenshotHideImages(): Boolean {
        return screenshotHideImagesFlow.first()
    }
}
