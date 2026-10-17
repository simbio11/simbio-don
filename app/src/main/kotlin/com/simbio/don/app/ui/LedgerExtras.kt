package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.CycleSummary
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.budget.Category

// ── 카테고리 예산 ───────────────────────────────────────────

/** 카테고리 예산 대비 쓴 돈. 예산을 정한 카테고리만, 많이 쓴 비율 순. */
@Composable
fun BudgetBars(budgets: Map<Category, Long>, spent: Map<Category, Long>, limit: Int = Int.MAX_VALUE, onClick: ((Category) -> Unit)? = null) {
    val rows = budgets.filterValues { it > 0 }.entries
        .sortedByDescending { (c, b) -> (spent[c] ?: 0).toDouble() / b }
        .take(limit)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { (c, budget) ->
            val used = spent[c] ?: 0
            val over = used > budget
            val ratio = (used.toFloat() / budget).coerceIn(0f, 1f)
            Row(
                Modifier.fillMaxWidth().let { m -> if (onClick != null) m.clip(RoundedCornerShape(10.dp)).then(Modifier.padding(0.dp)) else m },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(c, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink, modifier = Modifier.weight(1f))
                        Text(
                            if (over) "${won(used - budget)} 넘음" else "${won(budget - used)} 남음",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (over) DonColors.Warn else if (ratio >= 0.8f) DonColors.Highlight else DonColors.Ink,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ChartColors.Track)) {
                        Box(
                            Modifier.fillMaxWidth(ratio.coerceAtLeast(0.01f)).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(if (over) ChartColors.Critical else ChartColors.Series),
                        )
                    }
                    Text("${won(used)} / ${won(budget)} · ${(used * 100 / budget).coerceAtMost(999)}%", fontSize = 11.sp, color = DonColors.InkMuted)
                }
            }
        }
    }
}

/** 카테고리별 한 달 예산 정하기 */
@Composable
fun BudgetsScreen(
    current: Map<Category, Long>,
    lastMonth: Map<Category, Long>,
    disposable: Long,
    onSave: (Map<Category, Long>) -> Unit,
    onBack: () -> Unit,
) {
    val values = remember { mutableStateMapOf<Category, String>().apply { current.forEach { (c, a) -> put(c, a.toString()) } } }
    val total = values.values.sumOf { it.toLongOrNull() ?: 0 }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("카테고리 예산", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("한 달(월급날 기준)에 카테고리마다 쓸 만큼 정해요. 넘을 것 같으면 홈에서 알려줘요. 비워두면 정하지 않아요.", fontSize = 13.sp, color = DonColors.InkSoft)
            FormCard {
                Text(
                    "생활비 ${won(disposable)} 중 ${won(total)}을 나눴어요",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (total > disposable) DonColors.Warn else DonColors.Ink,
                )
                if (total > disposable) Text("카테고리 예산 합이 생활비보다 많아요.", fontSize = 12.sp, color = DonColors.Warn)
            }
            FormCard {
                Category.spending.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(c, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.width(84.dp)) {
                            Text(c.label, fontSize = 14.sp, color = DonColors.Ink, maxLines = 1)
                            lastMonth[c]?.takeIf { it > 0 }?.let { Text("지난달 ${compactWon(it)}", fontSize = 11.sp, color = DonColors.InkMuted) }
                        }
                        Spacer(Modifier.width(8.dp))
                        MoneyField("예산", values[c].orEmpty(), { v -> if (v.isEmpty()) values.remove(c) else values[c] = v }, Modifier.weight(1f))
                    }
                }
            }
        }
        BottomBar {
            Button(
                onClick = {
                    onSave(values.mapNotNull { (c, v) -> v.toLongOrNull()?.takeIf { it > 0 }?.let { c to it } }.toMap())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }
}

// ── 월별 추이 ───────────────────────────────────────────────

/** 최근 몇 달 쓴 돈 막대 (이번 달 진하게) + 그 달 생활비 예산 눈금 */
@Composable
fun MonthTrendChart(summaries: List<CycleSummary>, months: Int = 6) {
    val shown = summaries.take(months).asReversed()
    if (shown.isEmpty()) return
    val max = shown.maxOf { maxOf(it.spent, it.disposable) }.coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(160.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { m ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(
                        compactWon(m.spent),
                        fontSize = 11.sp,
                        fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (m.isCurrent) DonColors.Ink else DonColors.InkMuted,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    val frac = m.spent.toFloat() / max
                    Box(
                        Modifier.fillMaxWidth(0.7f).fillMaxHeight(frac.coerceIn(0.02f, 0.85f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (m.isCurrent) ChartColors.Series else ChartColors.Ramp[1]),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ChartColors.Baseline))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { m ->
                Text(
                    if (m.isCurrent) "이번 달" else "${m.cycle.start.monthValue}/${m.cycle.start.dayOfMonth}~",
                    fontSize = 11.sp,
                    color = if (m.isCurrent) DonColors.Ink else DonColors.InkMuted,
                    fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (shown.size >= 2) {
            val now = shown.last()
            val prev = shown[shown.size - 2]
            val diff = now.spent - prev.spent
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    diff > 0 -> "지난달보다 ${won(diff)} 더 썼어요"
                    diff < 0 -> "지난달보다 ${won(-diff)} 덜 썼어요"
                    else -> "지난달과 같아요"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (diff > 0) DonColors.Warn else DonColors.Good,
            )
            Text("이번 달은 오늘까지 쓴 돈이에요.", fontSize = 11.sp, color = DonColors.InkMuted)
        }
    }
}
