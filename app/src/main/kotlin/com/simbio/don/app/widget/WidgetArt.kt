package com.simbio.don.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.simbio.don.app.ui.compactWon
import com.simbio.don.budget.Grass
import com.simbio.don.budget.GrassLevel
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** 위젯 달력 한 달 (월급날부터 다음 월급날 전날까지) */
data class CalendarCells(
    val start: LocalDate,
    val end: LocalDate,
    val today: LocalDate,
    /** 날짜별 생활비 */
    val spent: Map<LocalDate, Long>,
    val noSpend: Set<LocalDate>,
    /** 아직 안 쓴 계획 */
    val planned: Map<LocalDate, Long>,
    val dailyPlan: Long,
) {
    /** 일요일부터 토요일까지 꽉 채운 칸들 */
    val days: List<LocalDate>
        get() {
            val first = start.minusDays((start.dayOfWeek.value % 7).toLong())
            val last = end.plusDays((6 - end.dayOfWeek.value % 7).toLong())
            return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        }
}

/**
 * 위젯 그림 (RemoteViews에는 Compose를 못 쓰므로 Canvas로 그려 ImageView에 넣는다).
 * 크기는 dp로 받고 [density]를 곱해 픽셀로 그린다.
 */
object WidgetArt {
    /** 깃허브 잔디 색: 무지출이 가장 진하다 */
    fun color(level: GrassLevel, st: WidgetStyle = WidgetStyle.Light): Int = st.grass(level)

    // 잔디 여백 (dp): 위 달 이름, 왼쪽 요일, 아래 범례
    private const val TOP = 13f
    private const val LEFT = 15f
    private const val LEGEND = 15f

    /** 높이에 일곱 칸이 들어가는 크기로 가로를 채울 주 수 */
    fun grassWeeks(widthDp: Float, heightDp: Float): Int {
        val pitch = ((heightDp - TOP - LEGEND) / 7f).coerceIn(9f, 20f)
        return floor((widthDp - LEFT) / pitch).toInt().coerceIn(4, 53)
    }

    fun grass(g: Grass, widthDp: Float, heightDp: Float, density: Float, st: WidgetStyle = WidgetStyle.Light): Bitmap {
        val d = density
        val w = (widthDp * d).roundToInt().coerceAtLeast(1)
        val h = (heightDp * d).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val weeks = g.weeks.size
        val gridH = h - (TOP + LEGEND) * d
        val pitch = min(gridH / 7f, (w - LEFT * d) / weeks)
        val gap = (pitch * 0.16f).coerceAtLeast(1.5f * d)
        val size = pitch - gap
        val x0 = w - weeks * pitch + gap / 2
        val y0 = TOP * d
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = text(9f * d, st.inkMuted)

        // 요일 (일요일부터면 월·수·금 줄)
        listOf(1 to "월", 3 to "수", 5 to "금").forEach { (row, name) ->
            c.drawText(name, x0 - gap - 2 * d, y0 + row * pitch + size / 2 + text.textSize * 0.36f, text.apply { textAlign = Paint.Align.RIGHT })
        }
        text.textAlign = Paint.Align.LEFT
        // 달 이름: 그 달 1일이 든 주 위에
        var labelEnd = -1f
        g.weeks.forEachIndexed { i, week ->
            val first = week.filterNotNull().firstOrNull { it.date.dayOfMonth == 1 }?.date
                ?: if (i == 0) week.firstNotNullOfOrNull { it }?.date else null
            if (first != null) {
                val x = x0 + i * pitch
                val label = "${first.monthValue}월"
                if (x > labelEnd) {
                    c.drawText(label, x, y0 - 3.5f * d, text)
                    labelEnd = x + text.measureText(label) + 4 * d
                }
            }
        }
        // 칸
        val r = (size * 0.22f).coerceAtMost(3f * d)
        val rect = RectF()
        g.weeks.forEachIndexed { i, week ->
            week.forEachIndexed { row, day ->
                if (day == null) return@forEachIndexed
                rect.set(x0 + i * pitch, y0 + row * pitch, x0 + i * pitch + size, y0 + row * pitch + size)
                fill.color = color(day.level, st)
                c.drawRoundRect(rect, r, r, fill)
                if (day.date == g.today) {
                    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * d; color = st.ink }
                    c.drawRoundRect(rect, r, r, ring)
                }
            }
        }
        // 범례: 많이 ■■■■ 무지출
        val ly = h - 4f * d
        val ls = 9f * d
        val legend = listOf(GrassLevel.OVER, GrassLevel.OK, GrassLevel.LOW, GrassLevel.NO_SPEND)
        text.textAlign = Paint.Align.RIGHT
        var x = w.toFloat()
        c.drawText("무지출", x, ly, text)
        x -= text.measureText("무지출") + 4 * d
        legend.asReversed().forEach { lv ->
            rect.set(x - ls, ly - ls + 1 * d, x, ly + 1 * d)
            fill.color = color(lv, st)
            c.drawRoundRect(rect, 2 * d, 2 * d, fill)
            x -= ls + 2.5f * d
        }
        c.drawText("많이 씀", x - 1.5f * d, ly, text)
        text.textAlign = Paint.Align.LEFT
        c.drawText("하루 몫과 견준 생활비", x0 - gap / 2, ly, text.apply { color = st.inkFaint })
        return bmp
    }

    private const val HEAD = 15f

    fun calendar(cal: CalendarCells, widthDp: Float, heightDp: Float, density: Float, st: WidgetStyle = WidgetStyle.Light): Bitmap {
        val d = density
        val w = (widthDp * d).roundToInt().coerceAtLeast(1)
        val h = (heightDp * d).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val days = cal.days
        val rows = days.size / 7
        val cw = w / 7f
        val ch = (h - HEAD * d) / rows
        val head = text(10f * d, st.inkMuted).apply { textAlign = Paint.Align.CENTER }
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, n ->
            head.color = when (i) { 0 -> st.expense; 6 -> st.real; else -> st.inkMuted }
            c.drawText(n, cw * i + cw / 2, 11f * d, head)
        }
        val num = text((if (ch < 26 * d) 9.5f else 11f) * d, st.ink).apply { textAlign = Paint.Align.CENTER }
        val small = text((if (ch < 26 * d) 7.5f else 8.5f) * d, st.inkMuted).apply { textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val rect = RectF()
        val r = 6f * d
        days.forEachIndexed { k, day ->
            val col = k % 7
            val row = k / 7
            rect.set(cw * col + 1.5f * d, HEAD * d + ch * row + 1.5f * d, cw * (col + 1) - 1.5f * d, HEAD * d + ch * (row + 1) - 1.5f * d)
            val inside = day in cal.start..cal.end
            val past = day.isBefore(cal.today)
            val isToday = day == cal.today
            val spent = cal.spent[day] ?: 0
            val stamp = inside && day in cal.noSpend
            val plan = cal.planned[day]
            if (stamp) {
                fill.color = st.goodSurface
                c.drawRoundRect(rect, r, r, fill)
            }
            if (isToday) {
                stroke.color = st.real
                stroke.strokeWidth = 1.5f * d
                stroke.pathEffect = null
                c.drawRoundRect(rect, r, r, stroke)
            } else if (inside && !past && plan != null) {
                stroke.color = st.inkFaint
                stroke.strokeWidth = 1f * d
                stroke.pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
                c.drawRoundRect(rect, r, r, stroke)
            }
            // 날짜와 금액을 한 덩어리로 칸 가운데에 (칸이 낮으면 날짜만)
            val oneLine = rect.height() < num.textSize + small.textSize + 6 * d
            val block = if (oneLine) num.textSize else num.textSize + 3 * d + small.textSize
            val top = rect.centerY() - block / 2
            val numY = top + num.textSize * 0.85f
            num.color = when {
                !inside -> st.inkFaint
                isToday -> st.real
                else -> st.ink
            }
            num.isFakeBoldText = isToday
            c.drawText(day.dayOfMonth.toString(), rect.centerX(), numY, num)
            if (!inside || oneLine) return@forEachIndexed
            val subY = top + num.textSize + 3 * d + small.textSize * 0.85f
            when {
                !past && plan != null && spent <= 0 -> c.drawText(compactWon(plan), rect.centerX(), subY, small.apply { color = st.inkFaint })
                stamp -> check(c, rect.centerX(), subY - small.textSize * 0.35f, small.textSize * 0.55f, d, st.good)
                spent > 0 -> c.drawText(
                    compactWon(spent),
                    rect.centerX(),
                    subY,
                    small.apply {
                        color = if (spent > cal.dailyPlan) st.expense else st.inkMuted
                        isFakeBoldText = spent > cal.dailyPlan
                    },
                )
            }
            small.isFakeBoldText = false
        }
        return bmp
    }

    /** 무지출 도장 (초록 체크) */
    private fun check(c: Canvas, cx: Float, cy: Float, s: Float, d: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        c.drawPath(Path().apply { moveTo(cx - s, cy); lineTo(cx - s * 0.3f, cy + s * 0.7f); lineTo(cx + s, cy - s * 0.7f) }, p)
    }

    /** 도트 그림을 정수 배로 키운다 (흐려지지 않게) */
    fun pixels(px: IntArray, w: Int, h: Int, scale: Int): Bitmap {
        val src = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(src, w * scale, h * scale, false)
    }

    private fun text(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
    }
}
