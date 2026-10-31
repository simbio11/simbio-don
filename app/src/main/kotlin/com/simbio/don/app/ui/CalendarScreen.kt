package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 캘린더 보기: 한 달 · 일정 목록 */
enum class CalView(val label: String) { MONTH("월"), LIST("목록") }

/** "19:00 – 21:00" · "하루 종일" · "10/27 ~ 10/29" */
fun eventTimeRange(e: CalEvent): String = when {
    e.multiDay && e.allDay -> "${shortDate(e.startDate)} ~ ${shortDate(e.endDate)}"
    e.multiDay -> "${shortDate(e.startDate)} ${timeText(e.minuteOfDay!!)} ~ ${shortDate(e.endDate)} ${timeText(e.endMinute ?: e.minuteOfDay)}"
    e.allDay -> "하루 종일"
    e.endMinute != null && e.endMinute != e.minuteOfDay -> "${timeText(e.minuteOfDay!!)} – ${timeText(e.endMinute)}"
    else -> timeText(e.minuteOfDay!!)
}

/** 일정 예산 한 줄: "예산 5만" · "실제 6.2만 · 1.2만 넘음" */
fun budgetLine(p: PlanEntity): Pair<String, Color> = when {
    p.canceled -> "안 썼어요" to DonColors.Good
    p.done -> {
        val actual = p.doneAmount ?: 0
        if (actual <= p.amount) "실제 ${compactWon(actual)} · 지킴" to DonColors.Good
        else "실제 ${compactWon(actual)} · ${compactWon(actual - p.amount)} 넘음" to DonColors.Warn
    }
    else -> "예산 ${compactWon(p.amount)}" to DonColors.Real
}

/**
 * 캘린더 탭: 다른 캘린더 앱처럼 한 달 칸마다 일정 막대(여러 날 일정은 이어서), 날을 고르면 그날 일정과 가계부.
 * 가계부를 켜 두면 칸마다 그날 쓴 돈도 (가계부 연동). 좌우로 밀어 달을 넘긴다.
 */
@Composable
fun CalendarScreen(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    today: LocalDate,
    events: Map<LocalDate, List<CalEvent>>,
    entries: List<EntryEntity>,
    plans: List<PlanEntity>,
    phoneOn: Boolean,
    onConnect: () -> Unit,
    showMoney: Boolean,
    onShowMoney: (Boolean) -> Unit,
    view: CalView,
    onView: (CalView) -> Unit,
    onOpenEvent: (CalEvent) -> Unit,
    onAddEvent: (LocalDate) -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    accountNames: Map<Long, String> = emptyMap(),
) {
    val es = remember(entries) { entries.mapNotNull { runCatching { it.toEntry() }.getOrNull() } }
    val spent = remember(es) { Stats.daily(es) }
    val income = remember(es) { Stats.dailyIncome(es) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(UiIcon.CHEVRON_LEFT, "지난달", true) { onMonth(month.minusMonths(1)) }
                Text(
                    "${month.year}년 ${month.monthValue}월",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DonColors.Ink,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                RoundIconButton(UiIcon.CHEVRON_RIGHT, "다음 달", true) { onMonth(month.plusMonths(1)) }
                Spacer(Modifier.weight(1f))
                if (YearMonth.from(today) != month || selected != today) {
                    TextButton(onClick = { onMonth(YearMonth.from(today)); onSelect(today) }) { Text("오늘", fontSize = 13.sp) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CalView.entries.forEach { v -> FilterChip(view == v, { onView(v) }, { Text(v.label) }) }
                Spacer(Modifier.weight(1f))
                FilterChip(showMoney, { onShowMoney(!showMoney) }, { Text(if (showMoney) "가계부 보기 ✓" else "가계부 보기") })
            }
            if (!phoneOn) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(DonColors.RealSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("구글 캘린더 등 폰 캘린더와 이으면 일정이 같이 보여요. 지금 일정은 이 앱 안에만 있어요.", fontSize = 12.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                    TextButton(onClick = onConnect) { Text("잇기", fontSize = 13.sp) }
                }
            }
        }
        when (view) {
            CalView.MONTH -> {
                item {
                    MonthGrid(month, selected, today, events, if (showMoney) spent else emptyMap(), if (showMoney) income else emptyMap(), onSelect, onMonth)
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "day$selected") {
                    DayAgenda(selected, today, events[selected].orEmpty(), plans, onOpenEvent, onAddEvent)
                    Spacer(Modifier.height(10.dp))
                }
                if (showMoney) item(key = "money$selected") {
                    DayMoney(selected, entries.filter { it.epochDay == selected.toEpochDay() }, accountNames, onOpenEntry, onAddEntry)
                }
            }
            CalView.LIST -> {
                val from = if (YearMonth.from(today) == month) today else month.atDay(1)
                val days = (0L until 45L).map { from.plusDays(it) }.filter { d ->
                    events[d].orEmpty().isNotEmpty() || (showMoney && ((spent[d] ?: 0) != 0L || (income[d] ?: 0) > 0))
                }
                if (days.isEmpty()) item {
                    Text("${shortDate(from)}부터 45일 동안 일정이 없어요.", fontSize = 14.sp, color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 24.dp))
                }
                items(days, key = { "list$it" }) { d ->
                    AgendaDay(d, today, events[d].orEmpty(), plans, if (showMoney) spent[d] else null, if (showMoney) income[d] else null, onOpenEvent) {
                        onSelect(d)
                        onView(CalView.MONTH)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ── 한 달 칸 ─────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    events: Map<LocalDate, List<CalEvent>>,
    spent: Map<LocalDate, Long>,
    income: Map<LocalDate, Long>,
    onSelect: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val rows = (first.dayOfWeek.value % 7 + month.lengthOfMonth() + 6) / 7
    Column(
        Modifier.fillMaxWidth().card().padding(horizontal = 4.dp, vertical = 10.dp)
            // 좌우로 밀어 달 넘기기
            .pointerInput(month) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 120) onMonth(month.minusMonths(1)) else if (drag < -120) onMonth(month.plusMonths(1))
                    },
                    onHorizontalDrag = { _, d -> drag += d },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, n ->
                Text(
                    n, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = when (i) { 0 -> DonColors.Expense; 6 -> DonColors.Income; else -> DonColors.InkMuted },
                )
            }
        }
        (0 until rows).forEach { r ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { c ->
                    val d = start.plusDays((r * 7 + c).toLong())
                    DayCell(d, month, d == selected, d == today, events[d].orEmpty(), spent[d], income[d], Modifier.weight(1f)) { onSelect(d) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    d: LocalDate,
    month: YearMonth,
    selected: Boolean,
    today: Boolean,
    events: List<CalEvent>,
    spent: Long?,
    income: Long?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val inside = YearMonth.from(d) == month
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier.height(if (spent != null || income != null) 84.dp else 76.dp).padding(1.dp).clip(shape)
            .background(if (selected) DonColors.RealSurface else Color.Transparent)
            .let { if (selected) it.border(1.5.dp, DonColors.Real, shape) else it }
            .clickable(onClick = onClick)
            .semantics { contentDescription = shortDate(d) }
            .padding(top = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (today) DonColors.Ink else Color.Transparent), contentAlignment = Alignment.Center) {
            Text(
                d.dayOfMonth.toString(),
                fontSize = 11.sp,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    today -> DonColors.OnInk
                    !inside -> DonColors.InkFaint
                    d.dayOfWeek == DayOfWeek.SUNDAY -> DonColors.Expense
                    d.dayOfWeek == DayOfWeek.SATURDAY -> DonColors.Income
                    else -> DonColors.Ink
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        events.take(3).forEach { e -> EventBar(e, faded = !inside) }
        if (events.size > 3) Text("+${events.size - 3}", fontSize = 8.sp, lineHeight = 10.sp, color = DonColors.InkMuted)
        Spacer(Modifier.weight(1f))
        if (inside) {
            income?.takeIf { it > 0 }?.let { Text("+" + compactWon(it), fontSize = 8.sp, lineHeight = 10.sp, color = DonColors.Income, maxLines = 1) }
            spent?.takeIf { it != 0L }?.let { Text("−" + compactWon(it), fontSize = 8.sp, lineHeight = 10.sp, color = DonColors.Expense, maxLines = 1) }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** 칸 안 일정 막대: 하루 종일·여러 날은 색을 채우고 (이어지는 날은 글자 없이), 시각 있는 일정은 옅게 */
@Composable
private fun EventBar(e: CalEvent, faded: Boolean) {
    val color = Color(e.color or 0xFF000000.toInt())
    val filled = e.allDay || e.multiDay
    val leftOpen = e.multiDay && e.date != e.startDate
    val rightOpen = e.multiDay && e.date != e.endDate
    val shape = RoundedCornerShape(topStart = if (leftOpen) 0.dp else 3.dp, bottomStart = if (leftOpen) 0.dp else 3.dp, topEnd = if (rightOpen) 0.dp else 3.dp, bottomEnd = if (rightOpen) 0.dp else 3.dp)
    Text(
        if (e.labelHere) e.title else " ",
        fontSize = 8.sp,
        lineHeight = 10.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        color = when {
            faded -> DonColors.InkFaint
            filled -> Color.White
            else -> DonColors.Ink
        },
        modifier = Modifier.fillMaxWidth().padding(top = 1.dp, start = if (leftOpen) 0.dp else 1.dp, end = if (rightOpen) 0.dp else 1.dp)
            .clip(shape)
            .background(if (filled) color.copy(alpha = if (faded) 0.35f else 0.9f) else color.copy(alpha = if (faded) 0.1f else 0.2f))
            .padding(horizontal = 2.dp),
    )
}

// ── 고른 날 ─────────────────────────────────────────────────

@Composable
private fun DayAgenda(
    d: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    plans: List<PlanEntity>,
    onOpenEvent: (CalEvent) -> Unit,
    onAddEvent: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${d.monthValue}월 ${d.dayOfMonth}일 ${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.KOREAN)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DonColors.Ink,
            )
            if (d == today) {
                Spacer(Modifier.width(6.dp))
                Badge("오늘", DonColors.Real)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onAddEvent(d) }) { Text("+ 일정") }
        }
        if (events.isEmpty()) Text("일정이 없어요.", fontSize = 13.sp, color = DonColors.InkMuted)
        events.forEach { e -> EventRow(e, planOf(e, plans)) { onOpenEvent(e) } }
    }
}

@Composable
fun EventRow(e: CalEvent, plan: PlanEntity?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(eventTimeRange(e), e.location?.takeIf { it.isNotBlank() }, e.repeat.takeIf { it != com.simbio.don.cal.Repeat.NONE }?.label)
                    .joinToString(" · "),
                fontSize = 12.sp,
                color = DonColors.InkMuted,
                maxLines = 1,
            )
        }
        plan?.let { p ->
            val (text, color) = budgetLine(p)
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
        Icon(UiIcon.CHEVRON_RIGHT, 18.dp, DonColors.InkFaint)
    }
}

@Composable
private fun DayMoney(
    d: LocalDate,
    list: List<EntryEntity>,
    accountNames: Map<Long, String>,
    onOpenEntry: (EntryEntity) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
) {
    val es = list.mapNotNull { runCatching { it.toEntry() }.getOrNull() }
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("가계부", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Spacer(Modifier.width(8.dp))
            val out = Stats.total(es)
            val inc = Stats.income(es)
            Text(
                listOfNotNull(if (out != 0L) "−${won(out)}" else null, if (inc > 0) "+${won(inc)}" else null).joinToString(" · ").ifEmpty { "쓴 돈 없음" },
                fontSize = 13.sp,
                color = if (out > 0) DonColors.Expense else DonColors.InkMuted,
                modifier = Modifier.weight(1f),
            )
            SmallButton(onClick = { onAddEntry(d) }, containerColor = DonColors.SurfaceAlt, contentColor = DonColors.Real) { Text("+ 가계부 쓰기") }
        }
        list.sortedWith(compareByDescending<EntryEntity> { it.minuteOfDay ?: -1 }.thenByDescending { it.id })
            .forEach { e -> LedgerRow(e, accountNames, onOpenEntry) }
    }
}

// ── 목록 ────────────────────────────────────────────────────

@Composable
private fun AgendaDay(
    d: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    plans: List<PlanEntity>,
    spent: Long?,
    income: Long?,
    onOpenEvent: (CalEvent) -> Unit,
    onDay: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).card().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onDay), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shortDate(d),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    d == today -> DonColors.Real
                    d.dayOfWeek == DayOfWeek.SUNDAY -> DonColors.Expense
                    d.dayOfWeek == DayOfWeek.SATURDAY -> DonColors.Income
                    else -> DonColors.Ink
                },
            )
            if (d == today) {
                Spacer(Modifier.width(6.dp))
                Badge("오늘", DonColors.Real)
            }
            Spacer(Modifier.weight(1f))
            income?.takeIf { it > 0 }?.let { Text("+${won(it)}", fontSize = 12.sp, color = DonColors.Income) }
            spent?.takeIf { it != 0L }?.let {
                Spacer(Modifier.width(6.dp))
                Text("−${won(it)}", fontSize = 12.sp, color = DonColors.Expense)
            }
        }
        events.forEach { e -> EventRow(e, planOf(e, plans)) { onOpenEvent(e) } }
    }
}
