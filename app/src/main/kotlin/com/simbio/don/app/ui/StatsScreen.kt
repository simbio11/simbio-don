package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.MonthView
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.budget.Category
import com.simbio.don.budget.Stats

@Composable
fun StatsScreen(
    view: MonthView,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCategory: (Category) -> Unit,
    summaries: List<com.simbio.don.app.data.CycleSummary> = emptyList(),
    onBudgets: () -> Unit = {},
    year: com.simbio.don.app.data.YearView? = null,
    onPickYear: (Int) -> Unit = {},
) {
    var yearly by rememberSaveable { mutableStateOf(false) }
    if (yearly && year != null) {
        YearStats(year, onPickYear, onMonthly = { yearly = false })
        return
    }
    val spent = Stats.total(view.entries)
    val living = Stats.livingTotal(view.entries)
    val income = Stats.income(view.entries)
    val prevSpent = Stats.total(view.previousEntries)
    val incomeByCategory = Stats.incomeByCategory(view.entries)
    var showIncome by remember { mutableStateOf(false) }
    val pace = Stats.pace(view.entries, view.cycle.start, view.cycle.end, view.plan.disposable, view.today)
    val byCategory = Stats.byCategory(view.entries)
    val prevByCategory = Stats.byCategory(view.previousEntries).associate { it.category to it.amount }
    val dailyAvg = if (pace.elapsedDays == 0) 0 else living / pace.elapsedDays
    var paceTable by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSwitcher(view, hasPrev, hasNext, onPrev, onNext)
        SegmentedToggle(listOf("월간", "연간"), 0) { if (it == 1) yearly = true }

        RealCard("이번 달 가계부") { MonthSummaryRow(income, spent) }

        SectionCard("카테고리별") {
            SegmentedToggle(listOf("지출", "수입"), if (showIncome) 1 else 0) { showIncome = it == 1 }
            val totals = if (showIncome) incomeByCategory else byCategory
            if (totals.isEmpty()) {
                Text("아직 기록이 없어요.", color = ChartColors.InkSecondary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                DonutChart(donutParts(totals), if (showIncome) "수입" else "지출")
                DonutLegend(totals, onClick = onCategory, subs = { c -> Stats.bySub(view.entries, c) })
                Text("도넛을 누르면 그 조각 금액이, 목록을 누르면 그 카테고리 내역이 보여요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            }
        }

        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "쓴 돈",
                won(spent),
                if (view.previousEntries.isNotEmpty()) "지난달 ${won(prevSpent)}" else null,
                Modifier.weight(1f),
            )
            StatTile("하루 평균 생활비", won(dailyAvg), "${pace.elapsedDays}일 기준", Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("생활비 예산", won(view.plan.disposable), "고정 지출 ${won(Stats.fixedTotal(view.entries))} 별도", Modifier.weight(1f))
            StatTile("남은 생활비", won(view.plan.disposable - living), null, Modifier.weight(1f))
        }

        SectionCard("생활비 쓰는 속도") {
            Text(
                when {
                    !view.isCurrent -> if (living <= view.plan.disposable) "예산보다 ${won(view.plan.disposable - living)} 덜 썼어요" else "예산보다 ${won(living - view.plan.disposable)} 더 썼어요"
                    pace.projectedLeft >= 0 -> "이 속도면 월말에 ${won(pace.projectedLeft)} 남아요"
                    else -> "이 속도면 월말에 ${won(-pace.projectedLeft)} 모자라요"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (view.isCurrent && pace.projectedLeft < 0) ChartColors.Critical else ChartColors.InkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            PaceChart(pace)
            Text("그래프를 누르거나 끌면 날짜별 금액이 보여요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            TextButton(onClick = { paceTable = !paceTable }) { Text(if (paceTable) "표 닫기" else "표로 보기") }
            if (paceTable) {
                view.results.forEach { r ->
                    val i = (r.week.end.toEpochDay() - view.cycle.start.toEpochDay()).toInt()
                    val shown = i < pace.elapsedDays
                    Line(
                        "${shortDate(r.week.end)}까지",
                        if (shown) "${won(pace.cumulative[i])} / 페이스 ${won(pace.pace[i])}" else "페이스 ${won(pace.pace[i])}",
                    )
                }
            }
        }

        if (view.isCurrent && summaries.size >= 2) {
            SectionCard("월별 추이") { MonthTrendChart(summaries) }
        }

        SectionCard(
            "카테고리 예산",
            trailing = { com.simbio.don.app.ui.kit.MoreButton(if (view.categoryBudgets.isEmpty()) "정하기" else "고치기", onBudgets) },
        ) {
            if (view.categoryBudgets.isEmpty()) {
                Text("카페·배달처럼 자주 새는 카테고리에 한 달 예산을 정해 두면 넘기 전에 알려줘요.", fontSize = 13.sp, color = DonColors.InkSoft)
            } else {
                // 카테고리 예산은 생활비용: 고정 지출은 빼고 (홈과 같은 기준)
                val living = Stats.byCategory(view.entries.filter { it.type != com.simbio.don.budget.EntryType.FIXED })
                BudgetBars(view.categoryBudgets, living.associate { it.category to it.amount })
            }
        }

        if (byCategory.isNotEmpty() && view.previousEntries.isNotEmpty()) {
            SectionCard("지난달과 비교") {
                CategoryBars(byCategory, prevByCategory, onClick = onCategory)
            }
        }

        SectionCard("주별 예산") { WeekMeters(view.results, view.today) }

        val places = Stats.topPlaces(view.entries)
        if (places.isNotEmpty()) {
            SectionCard("많이 쓴 곳") {
                places.forEachIndexed { i, p ->
                    Line("${i + 1}. ${p.label} (${p.count}번)", won(p.amount))
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** 연간 통계: 그 해의 달마다 수입·지출 막대, 한 해 합계·저축률, 카테고리 도넛 */
@Composable
private fun YearStats(year: com.simbio.don.app.data.YearView, onPickYear: (Int) -> Unit, onMonthly: () -> Unit) {
    var showIncome by remember { mutableStateOf(false) }
    val byCategory = if (showIncome) Stats.incomeByCategory(year.entries) else Stats.byCategory(year.entries)
    val i = year.years.indexOf(year.year)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(UiIcon.CHEVRON_LEFT, "지난해", enabled = i in 0 until year.years.lastIndex) { onPickYear(year.years[i + 1]) }
            Text("${year.year}년", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            RoundIconButton(UiIcon.CHEVRON_RIGHT, "다음 해", enabled = i > 0) { onPickYear(year.years[i - 1]) }
        }
        SegmentedToggle(listOf("월간", "연간"), 1) { if (it == 0) onMonthly() }

        RealCard("${year.year}년 가계부") {
            MonthSummaryRow(year.income, year.expense)
            if (year.income > 0) {
                val rate = (year.income - year.expense) * 100 / year.income
                Text(
                    if (rate >= 0) "번 돈의 ${rate}%를 남겼어요" else "번 돈보다 ${won(year.expense - year.income)} 더 썼어요",
                    fontSize = 13.sp,
                    color = if (rate >= 0) DonColors.Good else DonColors.Warn,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        SectionCard("달마다") {
            if (year.months.isEmpty()) {
                Text("이 해에는 기록이 없어요.", color = ChartColors.InkSecondary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                YearBars(year.months)
                year.months.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${m.nominal.monthValue}월", fontSize = 14.sp, color = DonColors.Ink, modifier = Modifier.width(44.dp))
                        Text("+${won(m.income)}", fontSize = 13.sp, color = DonColors.Income, modifier = Modifier.weight(1f))
                        Text("−${won(m.expense)}", fontSize = 13.sp, color = DonColors.Expense, modifier = Modifier.weight(1f))
                        Text(won(m.net), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        SectionCard("한 해 카테고리") {
            SegmentedToggle(listOf("지출", "수입"), if (showIncome) 1 else 0) { showIncome = it == 1 }
            if (byCategory.isEmpty()) {
                Text("아직 기록이 없어요.", color = ChartColors.InkSecondary, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                DonutChart(donutParts(byCategory), if (showIncome) "수입" else "지출")
                DonutLegend(byCategory, onClick = null, subs = { c -> Stats.bySub(year.entries, c) })
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** 12달 막대: 달마다 수입(파랑)·지출(빨강) 두 줄. 가장 큰 값에 맞춘다 */
@Composable
private fun YearBars(months: List<com.simbio.don.budget.MonthTotal>) {
    val max = months.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1)
    val byMonth = months.associateBy { it.nominal.monthValue }
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        (1..12).forEach { mo ->
            val m = byMonth[mo]
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    listOf((m?.income ?: 0) to DonColors.Income, (m?.expense ?: 0) to DonColors.Expense).forEach { (v, color) ->
                        Box(
                            Modifier.width(6.dp).fillMaxHeight((v.toFloat() / max).coerceIn(0f, 1f)).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(color),
                        )
                    }
                }
                Text("$mo", fontSize = 10.sp, color = ChartColors.InkMuted)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(DonColors.Income, "수입")
        Legend(DonColors.Expense, "지출")
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = ChartColors.InkSecondary)
    }
}
