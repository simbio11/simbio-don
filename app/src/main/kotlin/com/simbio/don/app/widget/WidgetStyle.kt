package com.simbio.don.app.widget

import android.content.Context
import com.simbio.don.app.UiPrefs
import com.simbio.don.app.ui.ThemeMode
import com.simbio.don.budget.GrassLevel

/**
 * 위젯 색: 밝게·어둡게와 바탕 투명도(0 = 투명, 100 = 꽉 참).
 * 투명하게 두면 배경화면이 비치므로, 어두운 배경화면이면 "어둡게"(밝은 글자)를 고른다.
 */
data class WidgetStyle(val dark: Boolean, val opacity: Int = 100) {
    val bg: Int get() = if (dark) 0xFF1C1E23.toInt() else 0xFFFFFFFF.toInt()

    /** 내 캐릭터 위젯 바탕 (옅은 보라) */
    val buddyBg: Int get() = if (dark) 0xFF221E36.toInt() else 0xFFF4F1FF.toInt()
    val bgAlpha: Int get() = opacity.coerceIn(0, 100) * 255 / 100

    /** 바탕이 반 넘게 비치면 배경화면 위에서도 읽히게 흐린 글자를 한 단계씩 진하게 */
    val clear: Boolean get() = opacity < 50

    /** 비치는 바탕에서 글자 둘레에 두르는 테두리 색 (글자와 반대 밝기) */
    val halo: Int get() = if (dark) 0xE60E1116.toInt() else 0xEBFFFFFF.toInt()

    /** 비치는 바탕에서 달력 칸 뒤에 까는 반투명 바탕 (밖의 날은 더 옅게) */
    val scrim: Int get() = if (dark) 0xD9181B21.toInt() else 0xD9FFFFFF.toInt()
    val scrimFaint: Int get() = if (dark) 0x8C181B21.toInt() else 0x8CFFFFFF.toInt()

    val ink: Int get() = if (dark) 0xFFECEEF1.toInt() else 0xFF191F28.toInt()
    val inkSoft: Int get() = if (clear) ink else if (dark) 0xFFB9C0C9.toInt() else 0xFF4E5968.toInt()
    val inkMuted: Int get() = if (clear) (if (dark) 0xFFD5DAE0.toInt() else 0xFF333B46.toInt()) else if (dark) 0xFF9AA3AD.toInt() else 0xFF8B95A1.toInt()
    val inkFaint: Int get() = if (clear) (if (dark) 0xFFAEB6C0.toInt() else 0xFF5B6573.toInt()) else if (dark) 0xFF66707B.toInt() else 0xFFB0B8C1.toInt()
    val real: Int get() = if (dark) 0xFF6E97FF.toInt() else 0xFF346AFF.toInt()
    val expense: Int get() = if (dark) 0xFFFF6B74.toInt() else 0xFFE5484D.toInt()
    val good: Int get() = if (dark) 0xFF34C98C.toInt() else 0xFF14A06A.toInt()
    val goodSurface: Int get() = if (dark) 0xFF17352C.toInt() else 0xFFE8F7F0.toInt()
    val game: Int get() = if (dark) 0xFFBBAEFF.toInt() else 0xFF5A3FE0.toInt()

    /** 달력 칸 바탕: 그날 잔디 색을 흐리게 (덜 쓸수록 조금 더 진한 초록, 넘긴 날은 옅은 빨강) */
    fun grassFaint(level: GrassLevel): Int = if (dark) {
        when (level) {
            GrassLevel.EMPTY -> 0xFF262B32.toInt()
            GrassLevel.OK -> 0xFF1B2E24.toInt()
            GrassLevel.LOW -> 0xFF1D3D2A.toInt()
            GrassLevel.NO_SPEND -> 0xFF1F4D31.toInt()
            GrassLevel.OVER -> 0xFF4A2A2A.toInt()
        }
    } else {
        when (level) {
            GrassLevel.EMPTY -> 0xFFF2F4F6.toInt()
            GrassLevel.OK -> 0xFFEDF9EF.toInt()
            GrassLevel.LOW -> 0xFFDDF4E3.toInt()
            GrassLevel.NO_SPEND -> 0xFFC9EDD4.toInt()
            GrassLevel.OVER -> 0xFFFFE5E1.toInt()
        }
    }

    /** 깃허브 잔디 색: 무지출이 가장 진하다 (어둡게는 가장 밝다) */
    fun grass(level: GrassLevel): Int = if (dark) {
        when (level) {
            GrassLevel.EMPTY -> 0xFF2A2F37.toInt()
            GrassLevel.OK -> 0xFF0E4429.toInt()
            GrassLevel.LOW -> 0xFF26A641.toInt()
            GrassLevel.NO_SPEND -> 0xFF39D353.toInt()
            GrassLevel.OVER -> 0xFFA8413B.toInt()
        }
    } else {
        when (level) {
            GrassLevel.EMPTY -> 0xFFEBEDF0.toInt()
            GrassLevel.OK -> 0xFF9BE9A8.toInt()
            GrassLevel.LOW -> 0xFF40C463.toInt()
            GrassLevel.NO_SPEND -> 0xFF216E39.toInt()
            GrassLevel.OVER -> 0xFFFF9C8F.toInt()
        }
    }

    companion object {
        val Light = WidgetStyle(false)
        val Dark = WidgetStyle(true)

        /** 바탕 투명도 고르기 (설정) */
        val OPACITIES = listOf(100, 75, 50, 25, 0)

        fun of(context: Context): WidgetStyle {
            val dark = when (UiPrefs.widgetTheme(context)) {
                ThemeMode.SYSTEM -> UiPrefs.systemDark(context)
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            return WidgetStyle(dark, UiPrefs.widgetOpacity(context))
        }
    }
}
