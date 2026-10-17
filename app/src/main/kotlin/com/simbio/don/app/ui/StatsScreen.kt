package com.simbio.don.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.simbio.don.app.ui.kit.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.MonthView
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
) {
    val spent = Stats.total(view.entries)
    val prevSpent = Stats.total(view.previousEntries)
    val pace = Stats.pace(view.entries, view.cycle.start, view.cycle.end, view.plan.disposable, view.today)
    val byCategory = Stats.byCategory(view.entries)
    val prevByCategory = Stats.byCategory(view.previousEntries).associate { it.category to it.amount }
    val dailyAvg = if (pace.elapsedDays == 0) 0 else spent / pace.elapsedDays
    var paceTable by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSwitcher(view, hasPrev, hasNext, onPrev, onNext)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "쓴 돈",
                won(spent),
                if (view.previousEntries.isNotEmpty()) "지난달 ${won(prevSpent)}" else null,
                Modifier.weight(1f),
            )
            StatTile("하루 평균", won(dailyAvg), "${pace.elapsedDays}일 기준", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("생활비 예산", won(view.plan.disposable), null, Modifier.weight(1f))
            StatTile("남은 생활비", won(view.plan.disposable - spent), null, Modifier.weight(1f))
        }

        SectionCard("지출 속도") {
            Text(
                when {
                    !view.isCurrent -> if (spent <= view.plan.disposable) "예산보다 ${won(view.plan.disposable - spent)} 덜 썼어요" else "예산보다 ${won(spent - view.plan.disposable)} 더 썼어요"
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

        SectionCard("카테고리별") {
            if (byCategory.isEmpty()) Text("아직 기록이 없어요.", color = ChartColors.InkSecondary)
            CategoryBars(byCategory, prevByCategory, onClick = onCategory)
            if (byCategory.isNotEmpty()) Text("누르면 그 카테고리 내역을 봐요.", fontSize = 11.sp, color = ChartColors.InkMuted)
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
