package com.simbio.don.app

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import com.simbio.don.app.ui.DonPalette
import com.simbio.don.app.ui.ThemeMode

/** 화면 설정 (이 폰에만): 밝게·어둡게, 화면 효과, 누를 때 진동 */
object UiPrefs {
    private const val PREFS = "ui"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(c: Context): ThemeMode =
        prefs(c).getString("theme", null)?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.SYSTEM

    fun setThemeMode(c: Context, m: ThemeMode) = prefs(c).edit().putString("theme", m.name).apply()

    /** 폰이 어둡게 모드인지 */
    fun systemDark(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun dark(c: Context, mode: ThemeMode = themeMode(c)): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark(c)
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    fun palette(c: Context): DonPalette = if (dark(c)) DonPalette.Dark else DonPalette.Light

    /** 폰 설정에서 애니메이션을 껐는지 ("애니메이션 효과 제거"·개발자 옵션 배율 0) */
    fun systemAnimationsOff(c: Context): Boolean =
        runCatching { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    /** 폰에서 애니메이션을 꺼도 이 앱의 화면 효과는 보이게 */
    fun effectsAlways(c: Context): Boolean = prefs(c).getBoolean("effects_always", false)

    fun setEffectsAlways(c: Context, on: Boolean) = prefs(c).edit().putBoolean("effects_always", on).apply()

    /** 누를 때 짧은 진동 */
    fun haptics(c: Context): Boolean = prefs(c).getBoolean("haptics", true)

    fun setHaptics(c: Context, on: Boolean) = prefs(c).edit().putBoolean("haptics", on).apply()

    /** 위젯 색 (폰 설정 따라 · 밝게 · 어둡게) */
    fun widgetTheme(c: Context): ThemeMode =
        prefs(c).getString("widget_theme", null)?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: ThemeMode.SYSTEM

    fun setWidgetTheme(c: Context, m: ThemeMode) = prefs(c).edit().putString("widget_theme", m.name).apply()

    /** 위젯 바탕 불투명도 (0 = 투명 ~ 100) */
    fun widgetOpacity(c: Context): Int = prefs(c).getInt("widget_opacity", 100).coerceIn(0, 100)

    fun setWidgetOpacity(c: Context, v: Int) = prefs(c).edit().putInt("widget_opacity", v.coerceIn(0, 100)).apply()

    /** 잔디 위젯에 보일 주 수 (0 = 위젯 크기에 맞춰 꽉 채움) */
    fun grassWeeks(c: Context): Int = prefs(c).getInt("grass_weeks", 0).let { if (it in GRASS_PERIODS) it else 0 }

    fun setGrassWeeks(c: Context, weeks: Int) = prefs(c).edit().putInt("grass_weeks", weeks).apply()

    /** 고를 수 있는 잔디 기간: 한 달·두 달·석 달·반년·크기에 맞춤 */
    val GRASS_PERIODS = listOf(4, 8, 12, 26, 0)

    fun grassPeriodLabel(weeks: Int) = when (weeks) {
        0 -> "크기에 맞춤"
        4 -> "4주"
        8 -> "8주"
        12 -> "12주"
        26 -> "반년"
        else -> "${weeks}주"
    }
}
