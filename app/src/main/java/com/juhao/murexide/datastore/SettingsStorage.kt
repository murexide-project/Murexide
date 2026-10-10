package com.juhao.murexide.datastore

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class SettingsStorage(private val context: Context) {

    private val dataStore: DataStore<Preferences>
        get() = context.settingsDataStore

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_COLOR_KEY = stringPreferencesKey("theme_color")

        private val NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("notification_enabled")

        private val SQUARE_AVATAR_KEY = booleanPreferencesKey("square_avatar")
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

        private val SCREENSHOT_HIDE_SENDER_INFO_KEY = booleanPreferencesKey("screenshot_hide_sender_info")
        private val SCREENSHOT_HIDE_MY_INFO_KEY = booleanPreferencesKey("screenshot_hide_my_info")
        private val SCREENSHOT_HIDE_SESSION_INFO_KEY = booleanPreferencesKey("screenshot_hide_session_info")
        private val SCREENSHOT_HIDE_IMAGES_KEY = booleanPreferencesKey("screenshot_hide_images")
    }

    private fun <T> preferenceFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.map { it[key] ?: default }

    private suspend fun <T> getPreference(key: Preferences.Key<T>, default: T): T =
        dataStore.data.first()[key] ?: default

    private suspend fun <T> setPreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    val themeModeFlow = preferenceFlow(THEME_MODE_KEY, "system")
    suspend fun getThemeMode() = getPreference(THEME_MODE_KEY, "system")
    suspend fun setThemeMode(mode: String) = setPreference(THEME_MODE_KEY, mode)

    val themeColorFlow = preferenceFlow(THEME_COLOR_KEY, "DYNAMIC")
    suspend fun getThemeColor() = getPreference(THEME_COLOR_KEY, "DYNAMIC")
    suspend fun setThemeColor(mode: String) = setPreference(THEME_COLOR_KEY, mode)

    val squareAvatarFlow = preferenceFlow(SQUARE_AVATAR_KEY, false)
    suspend fun getSquareAvatar() = getPreference(SQUARE_AVATAR_KEY, false)
    suspend fun setSquareAvatar(enabled: Boolean) = setPreference(SQUARE_AVATAR_KEY, enabled)

    val notificationEnabledFlow = preferenceFlow(NOTIFICATION_ENABLED_KEY, true)
    suspend fun getNotificationEnabled() = getPreference(NOTIFICATION_ENABLED_KEY, true)
    suspend fun setNotificationEnabled(enabled: Boolean) =
        setPreference(NOTIFICATION_ENABLED_KEY, enabled)

    val bigScreenFlow = preferenceFlow(BIG_SCREEN_KEY, true)
    suspend fun getBigScreen() = getPreference(BIG_SCREEN_KEY, true)
    suspend fun setBigScreen(enabled: Boolean) = setPreference(BIG_SCREEN_KEY, enabled)

    val updateChannelFlow = preferenceFlow(UPDATE_CHANNEL_KEY, "stable")
    suspend fun getUpdateChannel() = getPreference(UPDATE_CHANNEL_KEY, "stable")
    suspend fun setUpdateChannel(channel: String) = setPreference(UPDATE_CHANNEL_KEY, channel)

    val showStickyFlow = preferenceFlow(SHOW_STICKY_KEY, true)
    suspend fun getShowSticky() = getPreference(SHOW_STICKY_KEY, true)
    suspend fun setShowSticky(value: Boolean) = setPreference(SHOW_STICKY_KEY, value)

    val isFloatBottomBarEnabledFlow = preferenceFlow(FLOAT_BOTTOM_BAR_KEY, true)
    suspend fun getFloatBottomBar() = getPreference(FLOAT_BOTTOM_BAR_KEY, true)
    suspend fun setFloatBottomBar(value: Boolean) = setPreference(FLOAT_BOTTOM_BAR_KEY, value)

    val showMsgTagsFlow = preferenceFlow(MSG_SHOW_TAGS_KEY, true)
    suspend fun getShowMsgTags() = getPreference(MSG_SHOW_TAGS_KEY, true)
    suspend fun setShowMsgTags(enabled: Boolean) = setPreference(MSG_SHOW_TAGS_KEY, enabled)

    val bubbleCornerRadiusFlow = preferenceFlow(BUBBLE_CORNER_RADIUS_KEY, 18f)
    suspend fun getBubbleCornerRadius() = getPreference(BUBBLE_CORNER_RADIUS_KEY, 18f)
    suspend fun setBubbleCornerRadius(radius: Float) =
        setPreference(BUBBLE_CORNER_RADIUS_KEY, radius)

    val bubbleOpacityFlow = preferenceFlow(BUBBLE_OPACITY_KEY, 0.9f)
    suspend fun getBubbleOpacity() = getPreference(BUBBLE_OPACITY_KEY, 0.9f)
    suspend fun setBubbleOpacity(opacity: Float) = setPreference(BUBBLE_OPACITY_KEY, opacity)

    val showBackgroundFlow = preferenceFlow(SHOW_BACKGROUND_KEY, true)
    suspend fun getShowBackground() = getPreference(SHOW_BACKGROUND_KEY, true)
    suspend fun setShowBackground(value: Boolean) = setPreference(SHOW_BACKGROUND_KEY, value)

    val backgroundOpacityFlow = preferenceFlow(BACKGROUND_OPACITY_KEY, 0.4f)
    suspend fun getBackgroundOpacity() = getPreference(BACKGROUND_OPACITY_KEY, 0.4f)
    suspend fun setBackgroundOpacity(opacity: Float) =
        setPreference(BACKGROUND_OPACITY_KEY, opacity)

    val showMyBubbleAvatarFlow = preferenceFlow(SHOW_MY_BUBBLE_AVATAR_KEY, true)
    suspend fun getShowMyBubbleAvatar() = getPreference(SHOW_MY_BUBBLE_AVATAR_KEY, true)
    suspend fun setShowMyBubbleAvatar(show: Boolean) =
        setPreference(SHOW_MY_BUBBLE_AVATAR_KEY, show)

    val liquidGlassEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_ENABLED_KEY] == true &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    suspend fun getLiquidGlassEnabled() = liquidGlassEnabledFlow.first()
    suspend fun setLiquidGlassEnabled(enabled: Boolean) =
        setPreference(LIQUID_GLASS_ENABLED_KEY, enabled)

    val liquidGlassBlurFlow: Flow<Float> = dataStore.data.map { preferences ->
        preferences[LIQUID_GLASS_BLUR_KEY]?.coerceIn(0f, 4f) ?: 3f
    }

    suspend fun getLiquidGlassBlur() = liquidGlassBlurFlow.first()
    suspend fun setLiquidGlassBlur(value: Float) =
        setPreference(LIQUID_GLASS_BLUR_KEY, value.coerceIn(0f, 4f))

    val screenshotHideSenderInfoFlow = preferenceFlow(SCREENSHOT_HIDE_SENDER_INFO_KEY, false)
    suspend fun getScreenshotHideSenderInfo() = getPreference(SCREENSHOT_HIDE_SENDER_INFO_KEY, false)
    suspend fun setScreenshotHideSenderInfo(enabled: Boolean) =
        setPreference(SCREENSHOT_HIDE_SENDER_INFO_KEY, enabled)

    val screenshotHideMyInfoFlow = preferenceFlow(SCREENSHOT_HIDE_MY_INFO_KEY, false)
    suspend fun getScreenshotHideMyInfo() = getPreference(SCREENSHOT_HIDE_MY_INFO_KEY, false)
    suspend fun setScreenshotHideMyInfo(enabled: Boolean) =
        setPreference(SCREENSHOT_HIDE_MY_INFO_KEY, enabled)

    val screenshotHideSessionInfoFlow = preferenceFlow(SCREENSHOT_HIDE_SESSION_INFO_KEY, false)
    suspend fun getScreenshotHideSessionInfo() =
        getPreference(SCREENSHOT_HIDE_SESSION_INFO_KEY, false)

    suspend fun setScreenshotHideSessionInfo(enabled: Boolean) =
        setPreference(SCREENSHOT_HIDE_SESSION_INFO_KEY, enabled)

    val screenshotHideImagesFlow = preferenceFlow(SCREENSHOT_HIDE_IMAGES_KEY, false)
    suspend fun getScreenshotHideImages() = getPreference(SCREENSHOT_HIDE_IMAGES_KEY, false)
    suspend fun setScreenshotHideImages(enabled: Boolean) =
        setPreference(SCREENSHOT_HIDE_IMAGES_KEY, enabled)
}