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
    /** 앱 달력처럼: 날짜별 수입(파랑)·지출(빨강, 고정 지출 포함) */
    val income: Map<LocalDate, Long> = emptyMap(),
    val expense: Map<LocalDate, Long> = spent,
) {
    /** 지난 날·오늘(쓴 날)의 잔디 단계 — 칸 바탕을 흐리게 칠한다. 앞날·이번 달 밖은 null */
    fun level(day: LocalDate): GrassLevel? {
        if (day !in start..end || day.isAfter(today)) return null
        val s = spent[day] ?: 0
        if (day == today && s <= 0) return null
        return if (day in noSpend) GrassLevel.NO_SPEND else com.simbio.don.budget.Grasses.level(s, dailyPlan)
    }


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

    // 잔디 여백 (dp): 위 달 이름, 아래 범례
    private const val TOP = 14f
    private const val LEGEND = 15f

    /** 이 너비부터 달력 옆에 작은 잔디를 같이 그린다 */
    const val CALENDAR_MIN_WIDTH = 230f

    /** 잔디 한 칸 간격 (높이에 일곱 칸) */
    private fun grassPitch(heightDp: Float) = ((heightDp - TOP - LEGEND) / 7f).coerceIn(9f, 20f)

    /** 가로를 채울 잔디 주 수 (달력을 끄고 잔디만 그릴 때) */
    fun grassWeeks(widthDp: Float, heightDp: Float): Int =
        floor(widthDp / grassPitch(heightDp)).toInt().coerceIn(4, 53)

    /** 잔디만 (달력을 껐을 때): 요일 글자 없이, 위에는 달 이름만, 아래 범례 */
    fun grass(g: Grass, widthDp: Float, heightDp: Float, density: Float, st: WidgetStyle = WidgetStyle.Light): Bitmap {
        val d = density
        val w = (widthDp * d).roundToInt().coerceAtLeast(1)
        val h = (heightDp * d).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val label = text(9.5f * d, st.inkMuted)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * d; color = st.ink }
        val rect = RectF()
        val y0 = TOP * d
        val gridH = h - (TOP + LEGEND) * d

        val left = 0f

        // 최근 몇 주 잔디 (남는 자리는 가운데로)
        val weeks = g.weeks.size
        val pitch = min(gridH / 7f, (w - left) / weeks)
        val gap = (pitch * 0.16f).coerceAtLeast(1.5f * d)
        val size = pitch - gap
        val x0 = left + ((w - left) - weeks * pitch) / 2 + gap / 2
        var labelEnd = -1f
        g.weeks.forEachIndexed { i, week ->
            val first = week.filterNotNull().firstOrNull { it.date.dayOfMonth == 1 }?.date
                ?: if (i == 0) week.firstNotNullOfOrNull { it }?.date else null
            if (first != null) {
                val x = x0 + i * pitch
                val name = "${first.monthValue}월"
                if (x > labelEnd) {
                    c.drawText(name, x, y0 - 4f * d, label)
                    labelEnd = x + label.measureText(name) + 4 * d
                }
            }
        }
        val r = (size * 0.22f).coerceAtMost(3f * d)
        g.weeks.forEachIndexed { i, week ->
            week.forEachIndexed { row, day ->
                if (day == null) return@forEachIndexed
                rect.set(x0 + i * pitch, y0 + row * pitch, x0 + i * pitch + size, y0 + row * pitch + size)
                fill.color = color(day.level, st)
                c.drawRoundRect(rect, r, r, fill)
                if (day.date == g.today) c.drawRoundRect(rect, r, r, ring)
            }
        }

        // 범례: 많이 씀 ■■■■ 무지출
        val ly = h - 3f * d
        val ls = 9f * d
        label.textAlign = Paint.Align.RIGHT
        var x = w.toFloat()
        c.drawText("무지출", x, ly, label)
        x -= label.measureText("무지출") + 4 * d
        listOf(GrassLevel.OVER, GrassLevel.OK, GrassLevel.LOW, GrassLevel.NO_SPEND).asReversed().forEach { lv ->
            rect.set(x - ls, ly - ls + 1 * d, x, ly + 1 * d)
            fill.color = color(lv, st)
            c.drawRoundRect(rect, 2 * d, 2 * d, fill)
            x -= ls + 2.5f * d
        }
        c.drawText("많이 씀", x - 1.5f * d, ly, label)
        return bmp
    }

    private const val HEAD = 15f

    /** 달력 한 장 (앱 가계부 달력과 같게) */
    fun calendar(cal: CalendarCells, widthDp: Float, heightDp: Float, density: Float, st: WidgetStyle = WidgetStyle.Light): Bitmap {
        val w = (widthDp * density).roundToInt().coerceAtLeast(1)
        val h = (heightDp * density).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCalendar(Canvas(bmp), cal, RectF(0f, 0f, w.toFloat(), h.toFloat()), density, st)
        return bmp
    }

    /**
     * [area] 안에 달력을 그린다: 위에 요일(일 빨강·토 파랑), 칸마다 날짜와 수입(파랑)·지출(빨강),
     * 지난 날은 잔디 단계 색을 흐리게 깔고, 오늘은 테두리, 앞으로 잡힌 계획은 점선과 "예정".
     * 칸이 낮으면 줄 수를 줄인다 (지출 → 수입 차례로 남긴다).
     */
    private fun drawCalendar(c: Canvas, cal: CalendarCells, area: RectF, d: Float, st: WidgetStyle) {
        val days = cal.days
        val rows = days.size / 7
        val cw = area.width() / 7f
        // 낮은 위젯(4×2)은 요일 줄을 줄이고 칸 모서리에 날짜·금액을 나눠 적는다
        val compact = (area.height() - HEAD * d) / rows < 22 * d
        val headH = (if (compact) 12f else HEAD) * d
        val ch = (area.height() - headH) / rows
        val head = text((if (compact) 8.5f else 10f) * d, st.inkMuted).apply { textAlign = Paint.Align.CENTER }
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, n ->
            head.color = if (st.clear) st.inkMuted else when (i) { 0 -> st.expense; 6 -> st.real; else -> st.inkMuted }
            c.drawText(n, area.left + cw * i + cw / 2, area.top + headH - 3f * d, head)
        }
        val big = ch >= 34 * d
        val num = text((if (big) 11f else if (ch >= 24 * d) 10f else 9f) * d, st.ink).apply { textAlign = Paint.Align.CENTER }
        val small = text((if (big) 8.5f else 7.5f) * d, st.inkMuted).apply { textAlign = Paint.Align.CENTER }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val rect = RectF()
        val r = (min(cw, ch) * 0.2f).coerceAtMost(6f * d)
        val inset = 1.2f * d
        days.forEachIndexed { k, day ->
            val col = k % 7
            val row = k / 7
            rect.set(
                area.left + cw * col + inset, area.top + headH + ch * row + inset,
                area.left + cw * (col + 1) - inset, area.top + headH + ch * (row + 1) - inset,
            )
            val inside = day in cal.start..cal.end
            val isToday = day == cal.today
            val plan = cal.planned[day]?.takeIf { inside && day.isAfter(cal.today) }
            cal.level(day)?.let { lv ->
                fill.color = st.grassFaint(lv)
                c.drawRoundRect(rect, r, r, fill)
            }
            if (isToday) {
                stroke.color = st.real
                stroke.strokeWidth = 1.5f * d
                stroke.pathEffect = null
                c.drawRoundRect(rect, r, r, stroke)
            } else if (plan != null) {
                stroke.color = st.inkFaint
                stroke.strokeWidth = 1f * d
                stroke.pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
                c.drawRoundRect(rect, r, r, stroke)
            }
            num.color = when {
                !inside -> st.inkFaint
                isToday -> st.real
                // 바탕이 비치면(배경화면 위) 주말 색 대신 기본 글자색으로 — 읽히게
                st.clear -> st.ink
                day.dayOfWeek == java.time.DayOfWeek.SUNDAY -> st.expense
                day.dayOfWeek == java.time.DayOfWeek.SATURDAY -> st.real
                else -> st.ink
            }
            num.isFakeBoldText = isToday
            if (compact) {
                // 날짜는 왼쪽 위, 금액(지출, 없으면 수입)은 오른쪽 아래
                num.textSize = 7.5f * d
                num.textAlign = Paint.Align.LEFT
                c.drawText(day.dayOfMonth.toString(), rect.left + 2.5f * d, rect.top + 7.5f * d, num)
                if (!inside) return@forEachIndexed
                val amount = cal.expense[day]?.takeIf { it != 0L }?.let { compactWon(it) to st.expense }
                    ?: cal.income[day]?.takeIf { it > 0 }?.let { compactWon(it) to st.real }
                    ?: plan?.let { compactWon(it) to st.inkFaint }
                amount?.let { (t, col2) ->
                    small.textSize = 7.5f * d
                    small.textAlign = Paint.Align.RIGHT
                    small.color = col2
                    c.drawText(t, rect.right - 2f * d, rect.bottom - 2f * d, small)
                }
                return@forEachIndexed
            }
            // 날짜는 위에, 금액은 그 아래 줄마다
            val numY = rect.top + 2.5f * d + num.textSize * 0.82f
            c.drawText(day.dayOfMonth.toString(), rect.centerX(), numY, num)
            if (!inside) return@forEachIndexed
            val lineH = small.textSize + 1.5f * d
            val room = ((rect.bottom - numY - 1.5f * d) / lineH).toInt()
            if (room <= 0) return@forEachIndexed
            val lines = buildList {
                cal.expense[day]?.takeIf { it != 0L }?.let { add(compactWon(it) to st.expense) }
                cal.income[day]?.takeIf { it > 0 }?.let { add(compactWon(it) to st.real) }
                plan?.let { add("예정 " + compactWon(it) to st.inkFaint) }
            }.take(room)
            lines.forEachIndexed { i, (t, col2) ->
                small.color = col2
                c.drawText(t, rect.centerX(), numY + lineH * (i + 1), small)
            }
        }
    }

    /** 오른쪽 아래 작은 잔디 칸 간격 (예전 잔디의 70%) */
    private fun miniPitch(heightDp: Float) = ((heightDp - TOP - LEGEND) / 7f * 0.7f).coerceIn(6.5f, 11f)

    /** 오른쪽 칸 너비 (이번 달 합계 글자가 들어갈 만큼은) */
    private const val SIDE_MIN = 74f

    /** 달력 옆 작은 잔디에 넣을 주 수: 고른 기간(0이면 10주), 오른쪽이 너비의 38%를 넘지 않게 */
    fun miniWeeks(widthDp: Float, heightDp: Float, chosen: Int): Int {
        val fit = floor(widthDp * 0.38f / miniPitch(heightDp)).toInt()
        return (if (chosen > 0) chosen else 10).coerceAtMost(fit).coerceAtLeast(4)
    }

    /**
     * 큰 달력(왼쪽) + 오른쪽 위에 이번 달 수입·지출 합계, 오른쪽 아래에 최근 몇 주 잔디를 작게.
     */
    fun calendarWithGrass(
        cal: CalendarCells,
        g: Grass,
        totalIncome: Long,
        totalExpense: Long,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        st: WidgetStyle = WidgetStyle.Light,
    ): Bitmap {
        val d = density
        val w = (widthDp * d).roundToInt().coerceAtLeast(1)
        val h = (heightDp * d).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pitch = miniPitch(heightDp) * d
        val weeks = g.weeks.size
        val side = maxOf(weeks * pitch, SIDE_MIN * d)
        drawCalendar(c, cal, RectF(0f, 0f, w - side - 10 * d, h.toFloat()), d, st)

        // 오른쪽 위: 이번 달 합계
        val x = w - side
        val label = text(9f * d, st.inkMuted)
        val value = text(11.5f * d, st.ink).apply { isFakeBoldText = true }
        var y = 11f * d
        c.drawText("이번 달 수입", x, y, label)
        y += 14f * d
        c.drawText(if (totalIncome > 0) "+" + compactWon(totalIncome) else "0", x, y, value.apply { color = st.real })
        y += 15f * d
        c.drawText("이번 달 지출", x, y, label)
        y += 14f * d
        c.drawText(if (totalExpense > 0) "−" + compactWon(totalExpense) else "0", x, y, value.apply { color = st.expense })

        // 오른쪽 아래: 작은 잔디 (요일·달 글자 없이)
        val gap = (pitch * 0.18f).coerceAtLeast(1f * d)
        val size = pitch - gap
        val gx = w - weeks * pitch + gap / 2
        val gy = h - 7 * pitch + gap / 2
        if (gy - 4 * d > y + 4 * d) {
            c.drawText("최근 ${weeks}주", w - weeks * pitch, gy - 4f * d, text(8.5f * d, st.inkFaint))
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f * d; color = st.ink }
        val rr = (size * 0.22f).coerceAtMost(2f * d)
        val rect = RectF()
        g.weeks.forEachIndexed { i, week ->
            week.forEachIndexed { row, day ->
                if (day == null) return@forEachIndexed
                rect.set(gx + i * pitch, gy + row * pitch, gx + i * pitch + size, gy + row * pitch + size)
                fill.color = color(day.level, st)
                c.drawRoundRect(rect, rr, rr, fill)
                if (day.date == g.today) c.drawRoundRect(rect, rr, rr, ring)
            }
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

    /** 색 밝기 (0~1) */
    private fun luminance(c: Int): Float {
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun text(size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
    }
}
