package com.simbio.don.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.CategoryTotal
import com.simbio.don.budget.SpendingPace
import com.simbio.don.budget.WeekResult
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 차트 색. 한 가지 파랑(크기)과 회색(비교 기준)만 쓴다 — 카테고리는 11개라 색 대신 아이콘·이름으로 구분.
 * 상태색(초과)은 아이콘·문구와 함께만 쓴다.
 */
object ChartColors {
    val Series = DonColors.Real
    val Reference = Color(0xFFB0B8C1)
    val Grid = Color(0xFFF0F2F4)
    val Baseline = Color(0xFFD1D6DB)
    val Track = Color(0xFFF0F2F4)
    val Surface = DonColors.Surface
    val InkPrimary = DonColors.Ink
    val InkSecondary = DonColors.InkSoft
    val InkMuted = DonColors.InkMuted
    val Critical = Color(0xFFD03B3B)

    /** 달력 칸: 하루 지출 크기 (한 색, 옅음 → 진함) */
    val Ramp = listOf(Color(0xFFDCE6FF), Color(0xFFA9C1FF), Color(0xFF6A93FF), Color(0xFF2F5FE6))
}

/** 10,000원 이상은 "1.8만"처럼 줄여 쓴다 (축 눈금·달력 칸). */
fun compactWon(amount: Long): String {
    val a = kotlin.math.abs(amount)
    val sign = if (amount < 0) "−" else ""
    return when {
        a >= 100_000_000 -> sign + trim(a / 100_000_000.0) + "억"
        a >= 10_000 -> sign + trim(a / 10_000.0) + "만"
        else -> sign + "%,d".format(a)
    }
}

private fun trim(v: Double): String =
    if (v >= 100) v.roundToInt().toString() else "%.1f".format(v).removeSuffix(".0")

/** 눈금을 깔끔한 수로 (0 / 50만 / 100만) */
private fun niceMax(max: Long): Pair<Long, Long> {
    if (max <= 0) return 10_000L to 5_000L
    val raw = max / 3.0
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }.toLong()
    val top = ((max + step - 1) / step) * step
    return top to step
}

@Composable
fun LineKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = ChartColors.InkSecondary)
    }
}

/**
 * 한 달 지출 속도: 실제 누적 지출(파랑) vs 예산을 고르게 썼을 때(회색).
 * 끌거나 눌러서 날짜별 값을 본다.
 */
@Composable
fun PaceChart(pace: SpendingPace, modifier: Modifier = Modifier) {
    val days = pace.cumulative.size
    if (days == 0) return
    val shown = pace.elapsedDays.coerceIn(0, days)
    val maxValue = maxOf(pace.pace.last(), pace.cumulative.take(shown).maxOrNull() ?: 0)
    val (top, step) = niceMax(maxValue)
    var selected by remember(pace) { mutableStateOf<Int?>(null) }
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = ChartColors.InkMuted)

    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LineKey(ChartColors.Series, "실제 쓴 돈 (누적)")
            LineKey(ChartColors.Reference, "예산을 고르게 쓸 때")
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp)) {
            val leftPad = 40.dp
            val bottomPad = 18.dp
            Canvas(
                Modifier.fillMaxWidth().height(200.dp)
                    .pointerInput(pace) {
                        detectTapGestures { o -> selected = indexAt(o.x, leftPad.toPx(), size.width.toFloat(), days) }
                    }
                    .pointerInput(pace) {
                        detectHorizontalDragGestures(
                            onDragEnd = {},
                        ) { change, _ -> selected = indexAt(change.position.x, leftPad.toPx(), size.width.toFloat(), days) }
                    },
            ) {
                val l = leftPad.toPx()
                val b = size.height - bottomPad.toPx()
                val w = size.width - l
                fun x(i: Int) = l + if (days == 1) 0f else w * i / (days - 1)
                fun y(v: Long) = b - (b - 8f) * (v.toFloat() / top)

                // 눈금선 (가는 실선, 뒤로 물러나게)
                var t = 0L
                while (t <= top) {
                    val yy = y(t)
                    drawLine(if (t == 0L) ChartColors.Baseline else ChartColors.Grid, Offset(l, yy), Offset(size.width, yy), 1f)
                    val label = tm.measure(compactWon(t), axisStyle)
                    drawText(label, topLeft = Offset(l - label.size.width - 6f, yy - label.size.height / 2))
                    t += step
                }
                // x축: 시작·끝 날짜
                val startLabel = tm.measure(shortDate(pace.start), axisStyle)
                drawText(startLabel, topLeft = Offset(l, b + 4f))
                val endLabel = tm.measure(shortDate(pace.dateAt(days - 1)), axisStyle)
                drawText(endLabel, topLeft = Offset(size.width - endLabel.size.width, b + 4f))

                fun line(values: List<Long>, color: Color, width: androidx.compose.ui.unit.Dp) {
                    if (values.isEmpty()) return
                    val path = Path().apply {
                        moveTo(x(0), y(values[0]))
                        values.drop(1).forEachIndexed { i, v -> lineTo(x(i + 1), y(v)) }
                    }
                    drawPath(path, color, style = Stroke(width = width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                line(pace.pace, ChartColors.Reference, 2.dp)
                val actual = pace.cumulative.take(shown)
                // 실제 지출 아래를 옅게 채워 면으로 읽히게
                if (actual.size > 1) {
                    val area = Path().apply {
                        moveTo(x(0), b)
                        actual.forEachIndexed { i, v -> lineTo(x(i), y(v)) }
                        lineTo(x(actual.lastIndex), b)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(ChartColors.Series.copy(alpha = 0.18f), ChartColors.Series.copy(alpha = 0f)), startY = 0f, endY = b))
                }
                line(actual, ChartColors.Series, 2.5.dp)
                if (actual.isNotEmpty()) {
                    val end = Offset(x(actual.lastIndex), y(actual.last()))
                    drawCircle(ChartColors.Surface, 6.dp.toPx(), end)
                    drawCircle(ChartColors.Series, 4.dp.toPx(), end)
                }
                selected?.let { i ->
                    drawLine(ChartColors.Baseline, Offset(x(i), 0f), Offset(x(i), b), 1f)
                    if (i < shown) {
                        drawCircle(ChartColors.Surface, 6.dp.toPx(), Offset(x(i), y(pace.cumulative[i])))
                        drawCircle(ChartColors.Series, 4.dp.toPx(), Offset(x(i), y(pace.cumulative[i])))
                    }
                }
            }
            selected?.let { i ->
                val frac = if (days == 1) 0f else i.toFloat() / (days - 1)
                val boxW = 150.dp
                val xPos = leftPad + (maxWidth - leftPad) * frac
                val offsetX = (xPos - boxW / 2).coerceIn(0.dp, maxWidth - boxW)
                Column(
                    Modifier.offset { IntOffset(offsetX.roundToPx(), 0) }.width(boxW)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .card(radius = 12.dp)
                        .padding(10.dp),
                ) {
                    Text(shortDate(pace.dateAt(i)), fontSize = 11.sp, color = ChartColors.InkSecondary)
                    if (i < shown) TooltipRow(ChartColors.Series, won(pace.cumulative[i]), "쓴 돈")
                    TooltipRow(ChartColors.Reference, won(pace.pace[i]), "예산 페이스")
                }
            }
        }
    }
}

private fun indexAt(px: Float, left: Float, width: Float, days: Int): Int =
    (((px - left) / (width - left)) * (days - 1)).roundToInt().coerceIn(0, days - 1)

@Composable
private fun TooltipRow(color: Color, value: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ChartColors.InkPrimary)
        Spacer(Modifier.width(4.dp))
        Text(name, fontSize = 11.sp, color = ChartColors.InkSecondary)
    }
}

/** 카테고리별 지출 — 큰 순서 가로 막대 (한 색). 누르면 그 카테고리 내역으로. */
@Composable
fun CategoryBars(
    totals: List<CategoryTotal>,
    previous: Map<com.simbio.don.budget.Category, Long> = emptyMap(),
    limit: Int = totals.size,
    onClick: ((com.simbio.don.budget.Category) -> Unit)? = null,
) {
    val max = totals.maxOfOrNull { it.amount } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        totals.take(limit).forEach { t ->
            Row(
                Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick(t.category) } else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(t.category, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.category.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink)
                    Spacer(Modifier.width(6.dp))
                    Text("${t.shareBps / 100}%", fontSize = 12.sp, color = ChartColors.InkMuted, modifier = Modifier.weight(1f))
                    Text(won(t.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ChartColors.Track)) {
                    val frac = if (max == 0L) 0f else t.amount.toFloat() / max
                    Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ChartColors.Series))
                }
                previous[t.category]?.let { p ->
                    val diff = t.amount - p
                    if (diff != 0L) {
                        Text(
                            "지난달보다 ${won(kotlin.math.abs(diff))} ${if (diff > 0) "더 씀" else "덜 씀"}",
                            fontSize = 11.sp,
                            color = ChartColors.InkSecondary,
                        )
                    }
                }
                }
            }
        }
    }
}

/** 예산 대비 사용 막대 (트랙 = 예산). 넘으면 상태색 + 경고 문구. */
@Composable
fun Meter(spent: Long, budget: Long, modifier: Modifier = Modifier) {
    val over = budget in 1..<spent
    val frac = if (budget <= 0) (if (spent > 0) 1f else 0f) else (spent.toFloat() / budget).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(ChartColors.Track)) {
        if (frac > 0f) {
            Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (over) ChartColors.Critical else ChartColors.Series))
        }
    }
}

/** 주별 예산 대비 지출. */
@Composable
fun WeekMeters(results: List<WeekResult>, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        results.forEach { r ->
            val future = today.isBefore(r.week.start)
            val now = today in r.week
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${r.week.index + 1}주 ${shortDate(r.week.start)}~${shortDate(r.week.end)}",
                        fontSize = 13.sp,
                        fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            future -> "예정 · ${won(r.budget)}"
                            else -> "${won(r.spent)} / ${won(r.budget)}"
                        },
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Meter(if (future) 0 else r.spent, r.budget)
                when {
                    r.overspent > 0 && !future -> WarnLine("${won(r.overspent)} 초과", fontSize = 11)
                    now -> Text("진행 중", fontSize = 11.sp, color = ChartColors.InkMuted)
                    !future -> Text("${r.savedBps / 100}% 아낌", fontSize = 11.sp, color = ChartColors.InkSecondary)
                }
            }
        }
    }
}

/**
 * 주기 달력. 칸 색은 그날 지출 크기(한 색 4단계), 금액은 줄여서 표시.
 * 이번 주기 밖 날짜는 비워 둔다.
 */
@Composable
fun MonthCalendar(
    start: LocalDate,
    end: LocalDate,
    weekStart: DayOfWeek,
    daily: Map<LocalDate, Long>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val first = start.minusDays(((start.dayOfWeek.value - weekStart.value + 7) % 7).toLong())
    val max = daily.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val rows = days.chunked(7)
    Column {
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                Text(
                    dayName(weekStart.plus(i.toLong())),
                    fontSize = 11.sp,
                    color = ChartColors.InkMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { i ->
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(0.9f).padding(1.5.dp)) {
                        if (d != null && !d.isBefore(start)) DayCell(d, daily[d] ?: 0, max, d == today, d == selected) { onSelect(d) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, amount: Long, max: Long, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val bin = when {
        amount <= 0 -> -1
        else -> ((amount.toDouble() / max) * 4).toInt().coerceIn(0, 3)
    }
    val bg = if (bin < 0) Color.Transparent else ChartColors.Ramp[bin]
    val ink = if (bin >= 2) Color.White else ChartColors.InkPrimary
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().aspectRatio(0.9f)
            .clip(shape)
            .background(bg)
            .let { if (isSelected) it.border(2.dp, DonColors.Ink, shape) else it }
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (d.dayOfMonth == 1) "${d.monthValue}/1" else d.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (bin < 0) ChartColors.InkSecondary else ink,
        )
        if (amount != 0L) Text(compactWon(amount), fontSize = 9.sp, color = if (bin < 0) ChartColors.InkSecondary else ink, maxLines = 1)
        if (isToday) Box(Modifier.size(4.dp).clip(CircleShape).background(if (bin >= 2) Color.White else DonColors.Real))
    }
}

/** 숫자 하나가 주인공인 칸. */
@Composable
fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().card().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 12.sp, color = ChartColors.InkMuted, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChartColors.InkPrimary, maxLines = 1)
        sub?.let { Text(it, fontSize = 11.sp, color = ChartColors.InkMuted, maxLines = 1) }
    }
}
