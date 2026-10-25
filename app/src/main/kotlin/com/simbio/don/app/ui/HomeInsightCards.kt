package com.simbio.don.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.budget.Insight
import com.simbio.don.budget.MonthForecast
import com.simbio.don.budget.Stats
import com.simbio.don.budget.Subscription
import com.simbio.don.budget.Upcoming
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ── 이번 달 예측 ────────────────────────────────────────────

/**
 * 생활비 쓰는 속도로 본 월말: 남을지 모자랄지, 남은 날 하루 얼마씩이면 딱 맞는지.
 * 막대: 지금까지 쓴 돈(진하게) · 이대로 더 쓸 돈(옅게) · 생활비(눈금).
 */
@Composable
fun ForecastCard(f: MonthForecast, end: LocalDate) {
    val over = f.leftover < 0
    RealCard("이대로 쓰면", icon = { Icon(UiIcon.TREND_UP, 18.dp, if (over) DonColors.Warn else DonColors.Real) }) {
        Text(
            if (over) "${shortDate(end)}에 약 ${won(roundTo(-f.leftover, 1_000))} 모자라요" else "${shortDate(end)}에 약 ${won(roundTo(f.leftover, 1_000))} 남아요",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (over) DonColors.Warn else DonColors.Ink,
        )
        ForecastBar(f)
        Row(Modifier.fillMaxWidth()) {
            Text("쓴 돈 ${won(f.spent)}", fontSize = 12.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
            Text("생활비 ${won(f.budget)}", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        if (f.daysLeft > 0) {
            Text(
                if (f.perDayToFit > 0) "남은 ${f.daysLeft}일 동안 하루 ${won(f.perDayToFit / 100 * 100)}씩 쓰면 딱 맞아요" else "생활비를 다 썼어요. 남은 ${f.daysLeft}일은 아껴 봐요",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (f.perDayToFit > 0) DonColors.Real else DonColors.Warn,
            )
            if (f.plannedAhead > 0) Text("남은 날 계획한 돈 ${won(f.plannedAhead)}도 넣어서 봤어요", fontSize = 12.sp, color = DonColors.InkSoft)
            Text(
                "요즘 평일 하루 ${won(roundTo(f.weekdayAvg, 100))} · 주말 하루 ${won(roundTo(f.weekendAvg, 100))}" + (f.runOut?.let { " · 이대로면 ${shortDate(it)}에 바닥나요" } ?: ""),
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
        }
    }
}

/** 예측은 어림값이라 [unit] 단위로 반올림해 보여준다 */
private fun roundTo(v: Long, unit: Long): Long = (v + unit / 2) / unit * unit

@Composable
private fun ForecastBar(f: MonthForecast) {
    val max = maxOf(f.budget, f.projected).coerceAtLeast(1)
    val over = f.projected > f.budget
    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(ChartColors.Track)) {
        Box(
            Modifier.fillMaxWidth((f.projected.toFloat() / max).coerceIn(0f, 1f)).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background((if (over) DonColors.Warn else DonColors.Real).copy(alpha = 0.25f)),
        )
        Box(
            Modifier.fillMaxWidth((f.spent.toFloat() / max).coerceIn(0f, 1f)).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(if (f.spent > f.budget) DonColors.Warn else DonColors.Real),
        )
        if (over) {
            // 생활비 눈금
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.fillMaxWidth((f.budget.toFloat() / max).coerceIn(0f, 1f)))
                Box(Modifier.width(2.dp).height(14.dp).background(DonColors.Ink))
            }
        }
    }
}

// ── 이번 달 달력 ────────────────────────────────────────────

/**
 * 이번 달(월급날 기준) 생활비 달력. 날마다 쓴 돈, 한 푼도 안 쓴 날은 초록 도장,
 * 하루 몫(생활비 ÷ 날 수)을 넘긴 날은 빨강. 누르면 가계부의 그날로.
 */
@Composable
fun HomeCalendarCard(home: HomeState, onDay: (LocalDate) -> Unit) {
    val start = home.cycle.start
    val end = home.cycle.end
    val perDay = Stats.daily(home.entries.map { it.toEntry() }, livingOnly = true)
    val noSpend = home.insights.noSpend
    val plan = home.plan.disposable / home.cycle.days.coerceAtLeast(1)
    // 아직 안 쓴 계획 (회색 점선)
    val planned = home.plans.filter { it.open }.groupBy { LocalDate.ofEpochDay(it.epochDay) }.mapValues { (_, l) -> l.sumOf { it.amount } }
    RealCard(
        "이번 달 달력",
        icon = { Icon(UiIcon.CALENDAR, 18.dp, DonColors.Real) },
        trailing = {
            if (noSpend != null && noSpend.days.isNotEmpty()) Badge("무지출 ${noSpend.days.size}일", DonColors.Good)
        },
    ) {
        if (noSpend != null && noSpend.streak >= 2) {
            Text("${noSpend.streak}일 연속 무지출! 오늘도 이어 가 볼까요?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Good)
        }
        val first = start.minusDays((start.dayOfWeek.value % 7).toLong())
        val last = end.plusDays((6 - end.dayOfWeek.value % 7).toLong())
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, n ->
                Text(
                    n, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    color = when (i) { 0 -> DonColors.Expense; 6 -> DonColors.Income; else -> DonColors.InkMuted },
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val inside = d in start..end
                    val spent = perDay[d] ?: 0
                    val past = d.isBefore(home.today)
                    val isToday = d == home.today
                    val stamp = inside && noSpend != null && d in noSpend.days
                    Column(
                        Modifier.weight(1f).aspectRatio(0.82f).padding(1.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (stamp) DonColors.GoodSurface else Color.Transparent)
                            .let { if (isToday) it.border(1.5.dp, DonColors.Real, RoundedCornerShape(8.dp)) else it }
                            .let { if (!isToday && inside && planned[d] != null && !past) it.dashedBorder(DonColors.InkFaint, 8.dp, 1.dp) else it }
                            .let { if (inside) it.pressable(role = null) { onDay(d) } else it }
                            .semantics { contentDescription = "달력 ${shortDate(d)}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                !inside -> DonColors.InkFaint
                                isToday -> DonColors.Real
                                else -> DonColors.Ink
                            },
                        )
                        when {
                            !inside -> Unit
                            !past && planned[d] != null && spent <= 0 -> Text(compactWon(planned.getValue(d)), fontSize = 9.sp, maxLines = 1, color = DonColors.InkFaint)
                            stamp -> Icon(UiIcon.CHECK, 14.dp, DonColors.Good)
                            spent > 0 -> Text(
                                compactWon(spent),
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = if (spent > plan) DonColors.Expense else DonColors.InkSoft,
                                fontWeight = if (spent > plan) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            past || isToday -> Unit
                            else -> Box(Modifier.size(4.dp).clip(CircleShape).background(DonColors.Line))
                        }
                    }
                }
            }
        }
        Text("하루 몫 ${won(plan)} · 넘긴 날은 빨강, 안 쓴 날은 도장, 점선은 계획", fontSize = 11.sp, color = DonColors.InkMuted)
    }
}

// ── 다가오는 결제 ───────────────────────────────────────────

@Composable
fun UpcomingCard(list: List<Upcoming>, today: LocalDate, onPlans: () -> Unit = {}) {
    SectionCard("일주일 안에", trailing = { com.simbio.don.app.ui.kit.MoreButton("계획 +", onPlans) }) {
        if (list.isEmpty()) {
            Text("일주일 안에 나갈 돈이 없어요. 생일·병원처럼 쓸 일이 정해진 돈은 계획으로 적어 두면 여기와 예측에 보여요.", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        list.take(5).forEach { u ->
            val d = ChronoUnit.DAYS.between(today, u.date)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .let { if (u.planned) it.dashedBorder(DonColors.InkFaint, 12.dp).padding(horizontal = 8.dp, vertical = 4.dp) else it.padding(vertical = 2.dp) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble(
                    when {
                        u.income -> UiIcon.PAYMENTS
                        u.planned -> UiIcon.FLAG
                        else -> UiIcon.CALENDAR
                    },
                    if (u.income) DonColors.Income else DonColors.InkSoft,
                    34.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (u.planned) Badge("계획", DonColors.InkSoft)
                        Text(u.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (u.planned) DonColors.InkSoft else DonColors.Ink, maxLines = 1)
                    }
                    Text("${shortDate(u.date)} · ${if (d == 0L) "오늘" else "${d}일 뒤"}", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                Text(
                    when {
                        u.income -> "+" + won(u.amount)
                        u.planned -> "예정 " + won(u.amount)
                        else -> "−" + won(u.amount)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        u.income -> DonColors.Income
                        u.planned -> DonColors.InkMuted
                        else -> DonColors.Ink
                    },
                )
            }
        }
    }
}

// ── 정기 결제 발견 ──────────────────────────────────────────

/** 숨긴 정기 결제 (이 폰에만) */
private class HiddenSubscriptions(context: Context) {
    private val prefs = context.getSharedPreferences("don_prefs", Context.MODE_PRIVATE)
    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()
    fun hide(label: String) = prefs.edit().putStringSet(KEY, all() + label).apply()

    companion object {
        const val KEY = "hidden_subscriptions"
    }
}

/**
 * 매달 비슷한 날 비슷한 금액이 나가는 곳을 찾아 알려준다 (구독·정기 결제).
 * 고정비로 넣으면 다음 달부터 월급에서 먼저 빼고 그날 저절로 적는다.
 */
@Composable
fun SubscriptionCard(subs: List<Subscription>, onAddFixed: (Subscription) -> Unit) {
    val context = LocalContext.current
    val hidden = remember { HiddenSubscriptions(context) }
    var gone by remember { mutableStateOf(hidden.all()) }
    val shown = subs.filter { it.label !in gone }
    if (shown.isEmpty()) return
    SectionCard("정기 결제 같아요") {
        Text("매달 비슷한 날 같은 금액이 나가요. 고정비로 넣으면 다음 달부터 생활비에서 미리 빼 둬요.", fontSize = 12.sp, color = DonColors.InkMuted)
        if (shown.size > 1) {
            Text(
                "모두 합치면 한 달 ${won(shown.sumOf { it.amount })} · 1년이면 ${won(shown.sumOf { it.amount } * 12)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonColors.Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        shown.take(3).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
                    Text("${won(s.amount)} · 매달 ${s.day}일쯤 · ${s.months}달째 · 1년이면 ${compactWon(s.amount * 12)}원", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                TextButton(onClick = { hidden.hide(s.label); gone = hidden.all() }) { Text("아니에요") }
                SmallButton(onClick = { onAddFixed(s); hidden.hide(s.label); gone = hidden.all() }) { Text("고정비로") }
            }
        }
    }
}

// ── 한 줄 분석 ─────────────────────────────────────────────

@Composable
fun InsightCard(list: List<Insight>) {
    if (list.isEmpty()) return
    SectionCard("이번 주 한 줄 분석") {
        list.take(3).forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                IconBubble(if (i.good) UiIcon.CHECK else UiIcon.BOLT, if (i.good) DonColors.Good else DonColors.Highlight, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(i.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                    Text(i.body, fontSize = 12.sp, color = DonColors.InkSoft)
                }
            }
        }
    }
}

/** 요일 이름 (월~일) */
fun DayOfWeek.short(): String = com.simbio.don.budget.Insights.dowLabel(this)
