package com.example.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 5 Professional VIP Color Theme Presets for BDX Auto Sheet.
 * Supports instant local switching for testing AND future remote Admin Control Panel override.
 */
enum class AppThemePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeIcon: String,
    // Hex strings for Android View-based Floating Bubbles & Overlay Windows
    val bgHex: String,
    val cardHex: String,
    val elevatedHex: String,
    val headerStartHex: String,
    val headerEndHex: String,
    val cardBorderHex: String,
    val accentHex: String,
    val accentBrightHex: String,
    val accentSoftBgHex: String,
    val accentLightTextHex: String,
    val textSecondaryHex: String
) {
    OBSIDIAN_CRIMSON(
        id = "obsidian_crimson",
        title = "1. Obsidian Crimson",
        subtitle = "Carbon Glass & Ruby Red",
        badgeIcon = "🔴",
        bgHex = "#08080C",
        cardHex = "#111218",
        elevatedHex = "#181922",
        headerStartHex = "#1D1116",
        headerEndHex = "#111218",
        cardBorderHex = "#252632",
        accentHex = "#E11D48",
        accentBrightHex = "#F43F5E",
        accentSoftBgHex = "#281118",
        accentLightTextHex = "#FDA4AF",
        textSecondaryHex = "#A1A1AA"
    ),

    CYBER_EMERALD(
        id = "cyber_emerald",
        title = "2. Cyber Matrix",
        subtitle = "Obsidian Slate & Neon Green",
        badgeIcon = "🟢",
        bgHex = "#060B0E",
        cardHex = "#0D151C",
        elevatedHex = "#13202B",
        headerStartHex = "#0D241E",
        headerEndHex = "#0D151C",
        cardBorderHex = "#1D303D",
        accentHex = "#059669",
        accentBrightHex = "#10B981",
        accentSoftBgHex = "#0B2920",
        accentLightTextHex = "#6EE7B7",
        textSecondaryHex = "#94A3B8"
    ),

    MIDNIGHT_SAPPHIRE(
        id = "midnight_sapphire",
        title = "3. Midnight Sapphire",
        subtitle = "Deep Ocean & Electric Cyan",
        badgeIcon = "🔵",
        bgHex = "#070B14",
        cardHex = "#0F1626",
        elevatedHex = "#162138",
        headerStartHex = "#112240",
        headerEndHex = "#0F1626",
        cardBorderHex = "#1E2E4A",
        accentHex = "#2563EB",
        accentBrightHex = "#38BDF8",
        accentSoftBgHex = "#102342",
        accentLightTextHex = "#93C5FD",
        textSecondaryHex = "#94A3B8"
    ),

    TITANIUM_GOLD(
        id = "titanium_gold",
        title = "4. Titanium & Gold",
        subtitle = "Brushed Metal & VIP Gold",
        badgeIcon = "🟡",
        bgHex = "#0A0A0D",
        cardHex = "#131318",
        elevatedHex = "#1C1C24",
        headerStartHex = "#241D11",
        headerEndHex = "#131318",
        cardBorderHex = "#2C2923",
        accentHex = "#D97706",
        accentBrightHex = "#F59E0B",
        accentSoftBgHex = "#291F0E",
        accentLightTextHex = "#FDE68A",
        textSecondaryHex = "#A8A29E"
    ),

    AMETHYST_CYBER(
        id = "amethyst_cyber",
        title = "5. Amethyst Cyber",
        subtitle = "Cosmic Violet & Neon Purple",
        badgeIcon = "🟣",
        bgHex = "#090710",
        cardHex = "#130F21",
        elevatedHex = "#1C1630",
        headerStartHex = "#211438",
        headerEndHex = "#130F21",
        cardBorderHex = "#2B2246",
        accentHex = "#7C3AED",
        accentBrightHex = "#A855F7",
        accentSoftBgHex = "#23143B",
        accentLightTextHex = "#DDD6FE",
        textSecondaryHex = "#A1A1AA"
    );

    // Parsed Android Graphics Int colors for Floating Windows
    val bgInt: Int get() = android.graphics.Color.parseColor(bgHex)
    val cardInt: Int get() = android.graphics.Color.parseColor(cardHex)
    val elevatedInt: Int get() = android.graphics.Color.parseColor(elevatedHex)
    val headerStartInt: Int get() = android.graphics.Color.parseColor(headerStartHex)
    val headerEndInt: Int get() = android.graphics.Color.parseColor(headerEndHex)
    val cardBorderInt: Int get() = android.graphics.Color.parseColor(cardBorderHex)
    val accentInt: Int get() = android.graphics.Color.parseColor(accentHex)
    val accentBrightInt: Int get() = android.graphics.Color.parseColor(accentBrightHex)
    val accentSoftBgInt: Int get() = android.graphics.Color.parseColor(accentSoftBgHex)
    val accentLightTextInt: Int get() = android.graphics.Color.parseColor(accentLightTextHex)
    val textSecondaryInt: Int get() = android.graphics.Color.parseColor(textSecondaryHex)

    // Compose Color properties for Jetpack Compose Screens
    val bgCompose: Color get() = Color(bgInt)
    val cardCompose: Color get() = Color(cardInt)
    val elevatedCompose: Color get() = Color(elevatedInt)
    val headerStartCompose: Color get() = Color(headerStartInt)
    val cardBorderCompose: Color get() = Color(cardBorderInt)
    val accentCompose: Color get() = Color(accentInt)
    val accentBrightCompose: Color get() = Color(accentBrightInt)
    val accentSoftBgCompose: Color get() = Color(accentSoftBgInt)
    val accentLightTextCompose: Color get() = Color(accentLightTextInt)
    val textSecondaryCompose: Color get() = Color(textSecondaryInt)

    companion object {
        fun fromId(id: String?): AppThemePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MIDNIGHT_SAPPHIRE
        }
    }
}

/**
 * 3 Selectable UI Structure / Layout Presets for BDX Auto Sheet.
 * Allows switching between the new Bento Grid Pro layout, Cyber Tactical HUD layout,
 * and the Classic Stack layout anytime, or controlling it remotely via Admin Panel.
 */
enum class AppStructurePreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val usesGlassDockOverlay: Boolean
) {
    BENTO_COMMAND_PRO(
        id = "bento_command_pro",
        title = "1. Bento Command Pro",
        subtitle = "Bento Grid + Power Hub + Glass Dock",
        badgeText = "PRO NEW",
        usesGlassDockOverlay = true
    ),

    CYBER_TACTICAL_HUD(
        id = "cyber_tactical_hud",
        title = "2. Cyber Tactical HUD",
        subtitle = "Telemetry Rings + Segmented Deck",
        badgeText = "TACTICAL",
        usesGlassDockOverlay = true
    ),

    CLASSIC_STACK(
        id = "classic_stack",
        title = "3. Classic Stack",
        subtitle = "Original Vertical Card Layout",
        badgeText = "CLASSIC",
        usesGlassDockOverlay = false
    );

    companion object {
        fun fromId(id: String?): AppStructurePreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BENTO_COMMAND_PRO
        }
    }
}

/**
 * Centralized Singleton Manager for active Color Theme and UI Structure Mode.
 * Persists selections locally and exposes reactive StateFlows so both
 * Jetpack Compose screens AND background Floating Overlay Windows update immediately.
 */
object AppThemeManager {
    private const val PREFS_NAME = "bdx_theme_prefs"
    private const val KEY_ACTIVE_THEME_ID = "active_theme_id_v3_fixed"
    private const val KEY_ACTIVE_STRUCTURE_ID = "active_structure_id_v3_fixed"

    private val _currentTheme = MutableStateFlow(AppThemePreset.MIDNIGHT_SAPPHIRE)
    val currentTheme: StateFlow<AppThemePreset> = _currentTheme.asStateFlow()

    private val _currentStructure = MutableStateFlow(AppStructurePreset.BENTO_COMMAND_PRO)
    val currentStructure: StateFlow<AppStructurePreset> = _currentStructure.asStateFlow()

    val current: AppThemePreset
        get() = _currentTheme.value

    val currentStructurePreset: AppStructurePreset
        get() = _currentStructure.value

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedThemeId = prefs.getString(KEY_ACTIVE_THEME_ID, AppThemePreset.MIDNIGHT_SAPPHIRE.id)
        val savedStructureId = prefs.getString(KEY_ACTIVE_STRUCTURE_ID, AppStructurePreset.BENTO_COMMAND_PRO.id)
        _currentTheme.value = AppThemePreset.fromId(savedThemeId)
        _currentStructure.value = AppStructurePreset.fromId(savedStructureId)
        isInitialized = true
    }

    fun selectTheme(context: Context, preset: AppThemePreset) {
        _currentTheme.value = preset
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_THEME_ID, preset.id)
            .apply()
    }

    fun selectStructure(context: Context, preset: AppStructurePreset) {
        _currentStructure.value = preset
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_STRUCTURE_ID, preset.id)
            .apply()
    }

    /**
     * Hooks for future Server / Admin Control Panel remote theme & structure override.
     */
    fun applyRemoteAdminTheme(context: Context, themeId: String) {
        val preset = AppThemePreset.fromId(themeId)
        selectTheme(context, preset)
    }

    fun applyRemoteAdminStructure(context: Context, structureId: String) {
        val preset = AppStructurePreset.fromId(structureId)
        selectStructure(context, preset)
    }
}
