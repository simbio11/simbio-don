package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.CycleSummary
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.MonthView
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 가계부 보기 */
enum class LedgerMode(val label: String) { DAILY("일일"), CALENDAR("달력"), WEEKLY("주별"), MONTHLY("월별") }

// ── 금액 표시 ──────────────────────────────────────────────

/** 기록 금액: 수입·환불 +파랑, 지출 −빨강, 이체 회색 */
fun entryAmountText(e: EntryEntity): String = when {
    e.settleFor != null -> "+${won(e.amount)}"
    else -> when (e.type) {
        EntryType.INCOME.name, EntryType.REFUND.name -> "+${won(e.amount)}"
        EntryType.TRANSFER.name -> won(e.amount)
        else -> "−${won(e.amount)}"
    }
}

fun entryAmountColor(e: EntryEntity): Color = when (e.type) {
    EntryType.INCOME.name, EntryType.REFUND.name -> DonColors.Income
    EntryType.TRANSFER.name -> DonColors.Transfer
    else -> DonColors.Expense
}

/** "14:30" */
fun timeText(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** 가계부 한 줄: 분류 · 내용(자산·시각) · 금액 */
@Composable
fun LedgerRow(e: EntryEntity, accountNames: Map<Long, String>, onOpen: (EntryEntity) -> Unit) {
    val type = enumOr(e.type, EntryType.SPEND)
    val c = Category.of(e.category)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(e) }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (type == EntryType.TRANSFER) IconBubble(UiIcon.TRANSFER, DonColors.Transfer, 36.dp) else CategoryIcon(c, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink, maxLines = 1)
            Text(
                buildList {
                    add(
                        when {
                            e.settleFor != null -> "정산 받음" + (e.toAccountId?.let(accountNames::get)?.let { " → $it" } ?: "")
                            else -> when (type) {
                            EntryType.TRANSFER -> listOfNotNull(e.accountId?.let(accountNames::get), e.toAccountId?.let(accountNames::get))
                                .joinToString(" → ").ifEmpty { "이체" }
                            EntryType.FIXED -> "고정 · ${c.label}"
                            EntryType.EXCEPTION -> "예외 · ${c.label}"
                            EntryType.REFUND -> "환불 · ${c.label}"
                            else -> c.label
                        }
                        } + (e.sub?.let { " > $it" } ?: ""),
                    )
                    e.share?.let { add("나눠 냄 · 내 몫 ${won(it)}") }
                    if (e.owe) add("보낼 돈")
                    e.installmentNo?.let { add("할부 $it") }
                    if (type != EntryType.TRANSFER) e.accountId?.let(accountNames::get)?.let(::add)
                    e.minuteOfDay?.let { add(timeText(it)) }
                    when (e.source) {
                        EntrySource.NOTIFICATION.name -> add("알림")
                        EntrySource.RECURRING.name -> add("자동")
                    }
                    if (e.memo.isNotBlank()) add(e.memo)
                }.joinToString(" · "),
                fontSize = 12.sp,
                color = DonColors.InkMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(entryAmountText(e), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = entryAmountColor(e), maxLines = 1)
    }
}

/** 하루 묶음 머리: 큰 날짜 · 요일 · 수입/지출 */
@Composable
fun DayHeader(d: LocalDate, income: Long, expense: Long) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(d.dayOfMonth.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Spacer(Modifier.width(8.dp))
        val dow = d.dayOfWeek
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(
                when (dow) {
                    DayOfWeek.SUNDAY -> DonColors.Expense
                    DayOfWeek.SATURDAY -> DonColors.Income
                    else -> DonColors.InkMuted
                },
            ).padding(horizontal = 6.dp, vertical = 2.dp),
        ) { Text(dayName(dow), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(6.dp))
        Text("${d.year}.${"%02d".format(d.monthValue)}", fontSize = 12.sp, color = DonColors.InkMuted)
        Spacer(Modifier.weight(1f))
        if (income > 0) Text("+${won(income)}", fontSize = 13.sp, color = DonColors.Income, fontWeight = FontWeight.SemiBold)
        if (income > 0 && expense != 0L) Spacer(Modifier.width(10.dp))
        if (expense != 0L) Text("−${won(expense)}", fontSize = 13.sp, color = DonColors.Expense, fontWeight = FontWeight.SemiBold)
    }
}

/** 달 넘기기: ‹ 10월 › (월급날 기준 기간) */
@Composable
fun LedgerMonthBar(view: MonthView, hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onSearch: () -> Unit) {
    val ym = YearMonth.parse(view.nominal)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(UiIcon.CHEVRON_LEFT, "이전 달", hasPrev, onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${ym.year}년 ${ym.monthValue}월", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text("${shortDate(view.cycle.start)} ~ ${shortDate(view.cycle.end)} · 월급날 기준", fontSize = 11.sp, color = DonColors.InkMuted)
        }
        RoundIconButton(UiIcon.CHEVRON_RIGHT, "다음 달", hasNext, onNext)
        Spacer(Modifier.width(4.dp))
        RoundIconButton(UiIcon.SEARCH, "검색", true, onSearch)
    }
}

/** 보기 고르기 탭 (밑줄) */
@Composable
fun LedgerTabs(mode: LedgerMode, onMode: (LedgerMode) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        LedgerMode.entries.forEach { m ->
            val on = m == mode
            Column(
                Modifier.weight(1f).clickable(role = Role.Tab) { onMode(m) }.padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(m.label, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) DonColors.Ink else DonColors.InkMuted)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (on) DonColors.Ink else DonColors.Line))
            }
        }
    }
}

@Composable
fun LedgerScreen(
    view: MonthView,
    summaries: List<CycleSummary>,
    accountNames: Map<Long, String>,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickMonth: (String) -> Unit,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    category: Category?,
    onCategory: (Category?) -> Unit,
    onOpen: (EntryEntity) -> Unit,
    onSearch: () -> Unit,
    mode: LedgerMode,
    onMode: (LedgerMode) -> Unit,
    /** 돈 나갈 계획 (실제 기록과 따로, 점선으로) */
    plans: List<com.simbio.don.app.data.PlanEntity> = emptyList(),
    onPlans: () -> Unit = {},
) {
    val monthPlans = plans.filter { it.open && LocalDate.ofEpochDay(it.epochDay) in view.cycle }
    val income = Stats.income(view.entries)
    val expense = Stats.total(view.entries)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { LedgerMonthBar(view, hasPrev, hasNext, onPrev, onNext, onSearch) }
        item {
            MonthSummaryRow(income, expense)
            Spacer(Modifier.height(4.dp))
            LedgerTabs(mode, onMode)
            Spacer(Modifier.height(12.dp))
        }
        when (mode) {
            LedgerMode.DAILY -> {
                if (monthPlans.isNotEmpty() && selectedDate == null && category == null) item {
                    SectionCard("돈 나갈 계획 · 아직 안 씀", trailing = { com.simbio.don.app.ui.kit.MoreButton("계획", onPlans) }) {
                        Text("계획 ${won(monthPlans.sumOf { it.amount })} · 실제 기록과 따로 세요", fontSize = 12.sp, color = DonColors.InkMuted)
                        monthPlans.sortedBy { it.epochDay }.take(5).forEach { PlanRow(it, view.today, onPlans) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                dailyItems(view, accountNames, selectedDate, onSelectDate, category, onCategory, onOpen)
            }
            LedgerMode.CALENDAR -> calendarItems(view, accountNames, selectedDate ?: view.today.takeIf { it in view.cycle } ?: view.cycle.start, onSelectDate, onOpen, monthPlans, onPlans)
            LedgerMode.WEEKLY -> weeklyItems(view)
            LedgerMode.MONTHLY -> monthlyItems(summaries, view.nominal) { n -> onPickMonth(n); onMode(LedgerMode.DAILY) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ── 일일 ──────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.dailyItems(
    view: MonthView,
    accountNames: Map<Long, String>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate?) -> Unit,
    category: Category?,
    onCategory: (Category?) -> Unit,
    onOpen: (EntryEntity) -> Unit,
) {
    val present = view.entities.map { Category.of(it.category) }.toSet()
    val filtered = view.entities.filter { e ->
        (selectedDate == null || e.epochDay == selectedDate.toEpochDay()) && (category == null || e.category == category.name)
    }
    if (present.size > 1 || selectedDate != null) item {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = category == null && selectedDate == null, onClick = { onCategory(null); onSelectDate(null) }, label = { Text("전체") })
            selectedDate?.let { d -> FilterChip(selected = true, onClick = { onSelectDate(null) }, label = { Text("${shortDate(d)} ×") }) }
            Category.entries.filter { it in present }.forEach { c ->
                FilterChip(selected = c == category, onClick = { onCategory(if (c == category) null else c) }, label = { Text(c.label) })
            }
        }
    }
    if (filtered.isEmpty()) item {
        Text(
            if (view.entities.isEmpty()) "아직 기록이 없어요. 결제하면 알림으로 자동 기록되고, + 버튼으로 직접 적을 수도 있어요." else "기록이 없어요.",
            modifier = Modifier.padding(vertical = 24.dp),
            color = DonColors.InkMuted,
            fontSize = 14.sp,
        )
    }
    val groups = filtered.groupBy { it.epochDay }.toSortedMap(compareByDescending { it })
    groups.forEach { (day, list) ->
        item(key = "d$day") {
            val d = LocalDate.ofEpochDay(day)
            val es = list.map { it.toEntryOrNull() }.filterNotNull()
            Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
                DayHeader(d, Stats.income(es), Stats.total(es))
                list.sortedWith(compareByDescending<EntryEntity> { it.minuteOfDay ?: -1 }.thenByDescending { it.id })
                    .forEach { e -> LedgerRow(e, accountNames, onOpen) }
            }
        }
    }
}

private fun EntryEntity.toEntryOrNull() = runCatching { toEntry() }.getOrNull()

// ── 달력 ──────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.calendarItems(
    view: MonthView,
    accountNames: Map<Long, String>,
    selected: LocalDate,
    onSelectDate: (LocalDate?) -> Unit,
    onOpen: (EntryEntity) -> Unit,
    plans: List<com.simbio.don.app.data.PlanEntity> = emptyList(),
    onPlans: () -> Unit = {},
) {
    item {
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 8.dp, vertical = 12.dp)) {
            LedgerCalendar(
                view.cycle.start, view.cycle.end,
                Stats.dailyIncome(view.entries), Stats.daily(view.entries),
                view.today, selected,
                planned = plans.groupBy { LocalDate.ofEpochDay(it.epochDay) }.mapValues { (_, l) -> l.sumOf { it.amount } },
            ) { onSelectDate(it) }
        }
        Spacer(Modifier.height(10.dp))
    }
    item {
        val list = view.entities.filter { it.epochDay == selected.toEpochDay() }
        val es = list.mapNotNull { it.toEntryOrNull() }
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp)) {
            DayHeader(selected, Stats.income(es), Stats.total(es))
            val dayPlans = plans.filter { it.epochDay == selected.toEpochDay() }
            if (list.isEmpty() && dayPlans.isEmpty()) Text("이날은 기록이 없어요.", fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 8.dp))
            list.sortedWith(compareByDescending<EntryEntity> { it.minuteOfDay ?: -1 }.thenByDescending { it.id })
                .forEach { e -> LedgerRow(e, accountNames, onOpen) }
            dayPlans.forEach { PlanRow(it, view.today, onPlans) }
        }
    }
}

/**
 * 가계부 달력 (일요일부터). 칸마다 수입(파랑)·지출(빨강)을 줄여서 적는다.
 * 이번 달(월급날 기준) 밖 날짜는 흐리게.
 */
@Composable
fun LedgerCalendar(
    start: LocalDate,
    end: LocalDate,
    income: Map<LocalDate, Long>,
    expense: Map<LocalDate, Long>,
    today: LocalDate,
    selected: LocalDate?,
    planned: Map<LocalDate, Long> = emptyMap(),
    onSelect: (LocalDate) -> Unit,
) {
    val first = start.minusDays((start.dayOfWeek.value % 7).toLong())
    val last = end.plusDays((6 - end.dayOfWeek.value % 7).toLong())
    val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, n ->
                Text(
                    n, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = when (i) { 0 -> DonColors.Expense; 6 -> DonColors.Income; else -> DonColors.InkMuted },
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val inside = d in start..end
                    val on = d == selected
                    val shape = RoundedCornerShape(8.dp)
                    Column(
                        Modifier.weight(1f).aspectRatio(0.78f).padding(1.dp).clip(shape)
                            .background(if (on) DonColors.RealSurface else Color.Transparent)
                            .let { if (on) it.border(1.5.dp, DonColors.Real, shape) else it }
                            .clickable(enabled = inside) { onSelect(d) }
                            .semantics { contentDescription = shortDate(d) }
                            .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val isToday = d == today
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (isToday) DonColors.Ink else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                d.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                fontWeight = if (isToday || on) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isToday -> DonColors.OnInk
                                    !inside -> DonColors.InkFaint
                                    d.dayOfWeek == DayOfWeek.SUNDAY -> DonColors.Expense
                                    d.dayOfWeek == DayOfWeek.SATURDAY -> DonColors.Income
                                    else -> DonColors.Ink
                                },
                            )
                        }
                        if (inside) {
                            income[d]?.takeIf { it > 0 }?.let { Text(compactWon(it), fontSize = 9.sp, color = DonColors.Income, maxLines = 1) }
                            expense[d]?.takeIf { it != 0L }?.let { Text(compactWon(it), fontSize = 9.sp, color = DonColors.Expense, maxLines = 1) }
                            // 계획은 회색 (아직 안 쓴 돈)
                            planned[d]?.let { Text("예정 " + compactWon(it), fontSize = 8.sp, color = DonColors.InkFaint, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

// ── 주별 ──────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.weeklyItems(view: MonthView) {
    item {
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp)) {
            view.results.forEachIndexed { i, r ->
                val inWeek = view.entries.filter { it.date in r.week }
                val inc = Stats.income(inWeek)
                val exp = Stats.total(inWeek)
                val now = view.today in r.week
                if (i > 0) ListDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}주 · ${shortDate(r.week.start)} ~ ${shortDate(r.week.end)}",
                            fontSize = 14.sp,
                            fontWeight = if (now) FontWeight.Bold else FontWeight.Medium,
                            color = DonColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        if (now) com.simbio.don.app.ui.kit.Badge("이번 주")
                    }
                    Spacer(Modifier.height(4.dp))
                    if (r.week.start > view.today) {
                        Text("주 예산 ${won(r.budget)}", fontSize = 12.sp, color = DonColors.InkMuted)
                    } else Row {
                        if (inc > 0) {
                            Text("수입 ", fontSize = 12.sp, color = DonColors.InkMuted)
                            Text("+${won(inc)}", fontSize = 13.sp, color = DonColors.Income, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text("지출 ", fontSize = 12.sp, color = DonColors.InkMuted)
                        Text("−${won(exp)}", fontSize = 13.sp, color = DonColors.Expense, fontWeight = FontWeight.SemiBold)
                    }
                    if (r.week.start <= view.today) {
                        Spacer(Modifier.height(6.dp))
                        Meter(r.spent, r.budget)
                        Text(
                            "생활비 ${won(r.spent)} / 주 예산 ${won(r.budget)}" + if (r.overspent > 0) " · ${won(r.overspent)} 넘음" else "",
                            fontSize = 11.sp,
                            color = if (r.overspent > 0) DonColors.Warn else DonColors.InkMuted,
                        )
                    }
                }
            }
        }
    }
}

// ── 월별 ──────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.monthlyItems(summaries: List<CycleSummary>, current: String, onPick: (String) -> Unit) {
    item {
        Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (summaries.isEmpty()) Text("아직 기록이 없어요.", color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 12.dp))
            summaries.forEachIndexed { i, m ->
                val ym = YearMonth.parse(m.nominal)
                if (i > 0) ListDivider()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(m.nominal) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${ym.year}년 ${ym.monthValue}월",
                            fontSize = 15.sp,
                            fontWeight = if (m.nominal == current) FontWeight.Bold else FontWeight.Medium,
                            color = DonColors.Ink,
                        )
                        Text("${shortDate(m.cycle.start)} ~ ${shortDate(m.cycle.end)}", fontSize = 11.sp, color = DonColors.InkMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("+${won(m.income)}", fontSize = 13.sp, color = DonColors.Income, fontWeight = FontWeight.SemiBold)
                        Text("−${won(m.expense)}", fontSize = 13.sp, color = DonColors.Expense, fontWeight = FontWeight.SemiBold)
                        val net = m.income - m.expense
                        Text((if (net >= 0) "+" else "−") + won(kotlin.math.abs(net)), fontSize = 12.sp, color = DonColors.InkSoft)
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(UiIcon.CHEVRON_RIGHT, 18.dp, DonColors.InkFaint)
                }
            }
        }
    }
}
