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

    // 잔디 여백 (dp): 위 달 이름, 아래 범례
    private const val TOP = 14f
    private const val LEGEND = 15f

    /** 달력과 잔디 사이 */
    private const val GAP = 14f

    /** 이 너비부터 잔디 옆에 이번 달 달력을 같이 그린다 */
    const val CALENDAR_MIN_WIDTH = 230f

    /** 잔디 한 칸 간격 (높이에 일곱 칸) */
    private fun grassPitch(heightDp: Float) = ((heightDp - TOP - LEGEND) / 7f).coerceIn(9f, 20f)

    /** 이번 달 달력이 몇 줄인지 (일요일부터) */
    fun monthRows(today: LocalDate, firstDay: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY): Int {
        val first = today.withDayOfMonth(1)
        val lead = (first.dayOfWeek.value - firstDay.value + 7) % 7
        return (lead + today.lengthOfMonth() + 6) / 7
    }

    /** 달력 칸 간격 (숫자가 들어가게 잔디보다 크게, 22dp까지) */
    private fun calendarPitch(heightDp: Float, rows: Int) = ((heightDp - TOP - LEGEND) / rows).coerceIn(10f, 22f)

    fun calendarWidth(heightDp: Float, rows: Int) = calendarPitch(heightDp, rows) * 7

    /** 가로를 채울 잔디 주 수. [calendarRows]가 있으면 달력 자리를 빼고 */
    fun grassWeeks(widthDp: Float, heightDp: Float, calendarRows: Int? = null): Int {
        val room = if (calendarRows != null) widthDp - calendarWidth(heightDp, calendarRows) - GAP else widthDp
        return floor(room / grassPitch(heightDp)).toInt().coerceIn(4, 53)
    }

    /**
     * 잔디 (오른쪽, 최근 몇 주) + [month]가 있으면 왼쪽에 이번 달 달력 (날짜 숫자, 같은 색).
     * 요일 글자는 없이, 위에는 달 이름만.
     */
    fun grass(g: Grass, widthDp: Float, heightDp: Float, density: Float, st: WidgetStyle = WidgetStyle.Light, month: Grass? = null): Bitmap {
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

        // 왼쪽: 이번 달 달력
        var left = 0f
        if (month != null) {
            val today = g.today
            val rows = monthRows(today, g.firstDay)
            val pitch = calendarPitch(heightDp, rows) * d
            val gap = (pitch * 0.12f).coerceAtLeast(1.5f * d)
            val size = pitch - gap
            val r = (size * 0.24f).coerceAtMost(4f * d)
            val levels = month.days.associate { it.date to it.level }
            val first = today.withDayOfMonth(1)
            val lead = (first.dayOfWeek.value - g.firstDay.value + 7) % 7
            c.drawText("${today.monthValue}월", 0f, y0 - 4f * d, text(10f * d, st.ink).apply { isFakeBoldText = true })
            val num = text((size * 0.5f).coerceAtMost(10f * d), st.ink).apply { textAlign = Paint.Align.CENTER }
            for (day in 1..today.lengthOfMonth()) {
                val date = first.withDayOfMonth(day)
                val i = lead + day - 1
                val x = (i % 7) * pitch
                val y = y0 + (i / 7) * pitch
                rect.set(x, y, x + size, y + size)
                val lv = if (date.isAfter(today)) null else levels[date] ?: GrassLevel.EMPTY
                fill.color = if (lv == null) st.grass(GrassLevel.EMPTY) and 0x00FFFFFF or 0x66000000 else color(lv, st)
                c.drawRoundRect(rect, r, r, fill)
                if (date == today) c.drawRoundRect(rect, r, r, ring)
                // 칸 색이 어두우면 흰 글자, 밝으면 검은 글자
                num.color = when {
                    lv == null -> st.inkFaint
                    lv == GrassLevel.EMPTY -> st.inkMuted
                    luminance(fill.color) > 0.42f -> 0xFF1B1F24.toInt()
                    else -> 0xFFFFFFFF.toInt()
                }
                c.drawText(day.toString(), x + size / 2, y + size / 2 + num.textSize * 0.36f, num)
            }
            left = 7 * pitch + GAP * d
        }

        // 오른쪽: 최근 몇 주 잔디 (남는 자리는 가운데로)
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
