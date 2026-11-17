package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val title: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class AccentPalette(val title: String, val primaryLight: Color, val primaryDark: Color) {
    ORANGE(
        title = "Warm Orange",
        primaryLight = Color(0xFFE45B24),
        primaryDark = Color(0xFFFF895B)
    ),
    BLUE(
        title = "Ocean Blue",
        primaryLight = Color(0xFF1D4ED8),
        primaryDark = Color(0xFF60A5FA)
    ),
    GREEN(
        title = "Forest Emerald",
        primaryLight = Color(0xFF047857),
        primaryDark = Color(0xFF34D399)
    ),
    PURPLE(
        title = "Royal Violet",
        primaryLight = Color(0xFF7C3AED),
        primaryDark = Color(0xFFA78BFA)
    ),
    RED(
        title = "Ruby Crimson",
        primaryLight = Color(0xFFBE123C),
        primaryDark = Color(0xFFFB7185)
    )
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentPalette: AccentPalette = AccentPalette.ORANGE,
    val openInAppBrowser: Boolean = true,
    val showUnreadOnly: Boolean = false,
    val pullToRefreshEnabled: Boolean = true
)

object SettingsStorage {
    private const val PREFS_NAME = "cleanfeed_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_ACCENT_PALETTE = "accent_palette"
    private const val KEY_IN_APP_BROWSER = "in_app_browser"
    private const val KEY_UNREAD_ONLY = "unread_only"
    private const val KEY_PTR_ENABLED = "ptr_enabled"

    fun loadSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(modeStr) } catch (_: Exception) { ThemeMode.SYSTEM }

        val paletteStr = prefs.getString(KEY_ACCENT_PALETTE, AccentPalette.ORANGE.name) ?: AccentPalette.ORANGE.name
        val accentPalette = try { AccentPalette.valueOf(paletteStr) } catch (_: Exception) { AccentPalette.ORANGE }

        return AppSettings(
            themeMode = themeMode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
            accentPalette = accentPalette,
            openInAppBrowser = prefs.getBoolean(KEY_IN_APP_BROWSER, true),
            showUnreadOnly = prefs.getBoolean(KEY_UNREAD_ONLY, false),
            pullToRefreshEnabled = prefs.getBoolean(KEY_PTR_ENABLED, true)
        )
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
            .putString(KEY_ACCENT_PALETTE, settings.accentPalette.name)
            .putBoolean(KEY_IN_APP_BROWSER, settings.openInAppBrowser)
            .putBoolean(KEY_UNREAD_ONLY, settings.showUnreadOnly)
            .putBoolean(KEY_PTR_ENABLED, settings.pullToRefreshEnabled)
            .apply()
    }
}
