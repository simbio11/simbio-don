package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.popIn
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.MonthReport
import com.simbio.don.budget.PurchaseCheck
import com.simbio.don.budget.PurchaseImpact
import com.simbio.don.budget.PurchaseVerdict
import java.time.temporal.ChronoUnit

/** 이번 주 남은 날 (오늘 포함) */
private fun weekDaysLeft(home: HomeState): Int =
    home.currentWeek?.let { (ChronoUnit.DAYS.between(home.today, it.week.end) + 1).toInt() } ?: 1

/** 산다면 어떻게 되는지 (가격만 바꿔 가며 바로 본다) */
fun purchaseImpact(home: HomeState, price: Long): PurchaseImpact = PurchaseCheck.of(
    price = price,
    weekLeft = home.currentWeek?.available ?: 0,
    weekDaysLeft = weekDaysLeft(home),
    monthLeft = home.insights.forecast?.leftover ?: (home.plan.disposable - home.monthLiving),
    salary = home.salary,
)

/** "5시간 56분" */
private fun workText(minutes: Long): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0L -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

/**
 * 이거 사도 될까? — 가격을 넣으면 이번 주 남은 돈·하루 몫·월말 예측·일한 시간·목표로 보여 준다.
 * 사면 그대로 가계부에 적고, 참으면 그냥 닫는다 (아무것도 저장하지 않음).
 */
@Composable
fun CanBuyScreen(home: HomeState, onBought: (EntryDraft) -> Unit, onSkip: (Long) -> Unit, onBack: () -> Unit) {
    var price by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val v = price.toLongOrNull() ?: 0
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("이거 사도 될까?", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                Text("사기 전에 가격만 넣어 보세요. 이번 주·월말·목표가 어떻게 되는지 바로 보여요.", fontSize = 12.sp, color = DonColors.InkMuted)
                MoneyField("가격", price, { price = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(10_000L, 30_000L, 50_000L, 100_000L, 300_000L).forEach { n ->
                        FilterChip(false, { price = (v + n).toString() }, { Text("+${compactWon(n)}") })
                    }
                }
                OutlinedTextField(label, { label = it.take(30) }, label = { Text("뭘 사나요 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (v > 0) {
                val r = purchaseImpact(home, v)
                ImpactCard(home, r)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubButton(onClick = { onSkip(v) }, modifier = Modifier.weight(1f)) { Text("참을래요") }
                    Button(
                        onClick = { onBought(EntryDraft(home.today, v, label.trim(), EntryType.SPEND, Category.ETC, "", false)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("샀어요 · 적기") }
                }
            }
        }
    }
}

@Composable
private fun ImpactCard(home: HomeState, r: PurchaseImpact) {
    val color = when (r.verdict) {
        PurchaseVerdict.OK -> DonColors.Good
        PurchaseVerdict.WEEK_OVER, PurchaseVerdict.MONTH_SHORT -> DonColors.Highlight
        PurchaseVerdict.NO -> DonColors.Warn
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DonColors.Surface).padding(16.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(r.verdict.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(r.verdict.body, fontSize = 13.sp, color = DonColors.InkSoft)
        ImpactRow("이번 주 남은 돈", r.weekLeftBefore, r.weekLeftAfter)
        ImpactRow("남은 날 하루 몫", r.perDayBefore, r.perDayAfter)
        ImpactRow("월말에 남을 돈 (예측)", r.monthLeftBefore, r.monthLeftAfter)
        r.workMinutes?.let { m ->
            Text("월급으로 치면 ${workText(m)} 일한 돈이에요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
        }
        home.goals.firstOrNull { !it.done }?.let { g ->
            val left = (g.goal.target - g.filled).coerceAtLeast(1)
            Text(
                "'${g.goal.name}' 목표에 넣으면 남은 ${won(left)}의 ${(r.price * 100 / left).coerceAtMost(100)}%를 채워요",
                fontSize = 13.sp,
                color = DonColors.Real,
            )
        }
    }
}

@Composable
private fun ImpactRow(label: String, before: Long, after: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.weight(1f))
        Text(won(before), fontSize = 13.sp, color = DonColors.InkFaint)
        Text("  →  ", fontSize = 13.sp, color = DonColors.InkFaint)
        Text(won(after), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (after < 0) DonColors.Warn else DonColors.Ink)
    }
}

/** 홈 카드: 이거 사도 될까? */
@Composable
fun CanBuyHomeCard(onOpen: () -> Unit) {
    SectionCard("이거 사도 될까?", trailing = { TextButton(onClick = onOpen) { Text("따져 보기") } }) {
        Text("가격을 넣으면 이번 주 남은 돈·월말 예측·일한 시간으로 바로 보여 줘요.", fontSize = 12.sp, color = DonColors.InkMuted)
    }
}

/** 지난달 성적표 (새 달 첫 두 주 동안 홈에) */
@Composable
fun ReportCard(r: MonthReport) {
    SectionCard("지난달 성적표") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val gradeColor = when (r.grade.first()) {
                'A' -> DonColors.Good
                'B' -> DonColors.Real
                'C' -> DonColors.Highlight
                else -> DonColors.Warn
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(gradeColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text(r.grade, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = gradeColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${shortDate(r.start)} ~ ${shortDate(r.end)}", fontSize = 12.sp, color = DonColors.InkMuted)
                Text("생활비 ${won(r.spent)} / ${won(r.budget)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                Text(
                    "예산의 ${r.usedPercent}% · " + if (r.leftover >= 0) "${won(r.leftover)} 남김" else "${won(-r.leftover)} 넘김",
                    fontSize = 13.sp,
                    color = if (r.leftover >= 0) DonColors.Good else DonColors.Warn,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ReportStat("지킨 주", "${r.weeksKept}/${r.weeks}", Modifier.weight(1f))
            ReportStat("무지출", "${r.noSpendDays}일", Modifier.weight(1f))
            ReportStat("가장 많이", r.top?.category?.label ?: "-", Modifier.weight(1f))
        }
        r.bestCut?.let { (c, d) -> ReportLine("그 전 달보다 가장 줄인 곳", "${c.label} −${won(-d)}", DonColors.Good) }
        r.worstRise?.let { (c, d) -> ReportLine("가장 늘어난 곳", "${c.label} +${won(d)}", DonColors.Warn) }
    }
}

@Composable
private fun ReportStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = DonColors.InkMuted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, maxLines = 1)
    }
}

@Composable
private fun ReportLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, fontSize = 12.sp, color = DonColors.InkMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}
