package com.simbio.don.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonPalette
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.39: 버튼·글자 색이 바탕과 겹쳐 안 보이지 않게 — 두 모드 모두 글자 대비 4.5:1 이상 (WCAG AA) */
class PaletteContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun check(p: DonPalette, what: String, fg: Color, bg: Color, min: Double = 4.5) {
        val r = ratio(fg, bg)
        assertTrue("${if (p.dark) "어둡게" else "밝게"} $what: ${"%.2f".format(r)}:1 (${min}:1 넘어야)", r >= min)
    }

    @After
    fun reset() {
        DonColors.palette = DonPalette.Light
    }

    @Test
    fun `흰 글자 버튼 바탕과 글자색이 두 모드 모두 잘 읽힌다`() {
        for (p in listOf(DonPalette.Light, DonPalette.Dark)) {
            check(p, "파란 버튼", Color.White, p.realFill)
            check(p, "보라 버튼", Color.White, p.gameFill)
            check(p, "초록 버튼", Color.White, p.goodFill)
            check(p, "빨간 버튼", Color.White, p.warnFill)
            check(p, "고른 칩 (Ink 바탕)", p.onInk, p.ink)
            check(p, "보조 버튼", p.inkSoft, p.surfaceAlt)
            check(p, "꺼진 버튼", p.inkSoft, p.line)
            check(p, "카드 위 본문", p.ink, p.surface)
            check(p, "카드 위 흐린 글자", p.inkMuted, p.surface)
            check(p, "카드 위 경고 글자", p.warn, p.surface)
            check(p, "카드 위 초록 글자", p.good, p.surface)
            check(p, "초록 바탕 위 초록 글자", p.good, p.goodSurface)
            check(p, "회색 바탕 위 파란 글자 버튼", p.realDeep, p.surfaceAlt)
        }
    }

    @Test
    fun `강조색을 버튼 바탕으로 주면 흰 글자용 진한 색으로 바뀐다`() {
        DonColors.palette = DonPalette.Dark
        assertEquals(DonPalette.Dark.realFill, DonColors.fillFor(DonColors.Real))
        assertEquals(DonPalette.Dark.goodFill, DonColors.fillFor(DonColors.Good))
        assertEquals(DonPalette.Dark.warnFill, DonColors.fillFor(DonColors.Warn))
        assertEquals(DonPalette.Dark.gameFill, DonColors.fillFor(DonColors.Game))
        assertEquals(DonColors.SurfaceAlt, DonColors.fillFor(DonColors.SurfaceAlt))
    }
}
