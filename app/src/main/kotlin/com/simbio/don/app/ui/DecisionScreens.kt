package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
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
fun CanBuyScreen(
    home: HomeState,
    onBought: (EntryDraft) -> Unit,
    onSkip: (Long) -> Unit,
    onBack: () -> Unit,
    /** 7일 참아 보기 (이름, 가격) */
    onWait: (String, Long) -> Unit = { _, _ -> },
) {
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
                // 부자들의 소비법: 바로 사지 말고 7일 참아 본다. 그래도 사고 싶으면 그때 산다
                Button(onClick = { onWait(label.trim(), v) }, modifier = Modifier.fillMaxWidth()) { Text("7일 참아 보기 (7일 뒤에 다시 물어봐요)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubButton(onClick = { onSkip(v) }, modifier = Modifier.weight(1f)) { Text("안 살래요") }
                    SubButton(
                        onClick = { onBought(EntryDraft(home.today, v, label.trim(), EntryType.SPEND, Category.ETC, "", false, want = 1)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("지금 샀어요 · 적기") }
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
        // 빠른 보상 → 긴 보상: 최저시급으로 몇 시간, 참으면 1년에 얼마
        val year = home.today.year
        Text(
            "최저시급(${won(com.simbio.don.budget.MinWage.hourly(year))})으로는 ${com.simbio.don.budget.MinWage.text(com.simbio.don.budget.MinWage.minutes(r.price, year))} 일해야 버는 돈",
            fontSize = 13.sp,
            color = DonColors.InkSoft,
        )
        val yearly = com.simbio.don.budget.MinWage.yearIfSkippedMonthly(r.price)
        Text(
            "한 달에 한 번 이만큼 참으면 1년에 ${won(yearly)}" + (com.simbio.don.budget.Worths.of(yearly, home.today.dayOfYear.toLong())?.let { " — ${it.big}" } ?: ""),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DonColors.Good,
        )
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
fun CanBuyHomeCard(onOpen: () -> Unit, wishes: List<com.simbio.don.app.data.WishEntity> = emptyList(), today: java.time.LocalDate = java.time.LocalDate.now(), onWishes: () -> Unit = {}) {
    SectionCard("이거 사도 될까?", trailing = { TextButton(onClick = onOpen) { Text("따져 보기") } }) {
        Text("사고 싶은 게 생기면 가격을 넣고 7일 참아 보세요. 7일 뒤에도 사고 싶으면 그때 사요.", fontSize = 12.sp, color = DonColors.InkMuted)
        val waiting = wishes.filter { it.status == "WAITING" }
        val due = waiting.filter { com.simbio.don.budget.WaitRule.due(java.time.LocalDate.ofEpochDay(it.remindEpochDay), today) }
        if (waiting.isNotEmpty()) {
            Text(
                if (due.isNotEmpty()) "7일 참은 것 ${due.size}개 — 아직도 사고 싶나요?" else "참는 중 ${waiting.size}개 · 가장 빠른 것 D-${waiting.minOf { com.simbio.don.budget.WaitRule.daysLeft(java.time.LocalDate.ofEpochDay(it.remindEpochDay), today) }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (due.isNotEmpty()) DonColors.Real else DonColors.Ink,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(DonColors.RealSurface).clickable(onClick = onWishes).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else if (wishes.isNotEmpty()) {
            TextButton(onClick = onWishes) { Text("사고 싶은 것 보기", fontSize = 12.sp) }
        }
    }
}

/**
 * 사고 싶은 것: 7일 참는 중 · 7일이 지나 물어보는 것 · 끝난 것. 안 산 것은 "참아서 아낀 돈"으로 모인다.
 */
@Composable
fun WishScreen(
    wishes: List<com.simbio.don.app.data.WishEntity>,
    today: java.time.LocalDate,
    onBuy: (com.simbio.don.app.data.WishEntity) -> Unit,
    onSkip: (com.simbio.don.app.data.WishEntity) -> Unit,
    onDelete: (com.simbio.don.app.data.WishEntity) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val waiting = wishes.filter { it.status == "WAITING" }
    val (due, notYet) = waiting.partition { com.simbio.don.budget.WaitRule.due(java.time.LocalDate.ofEpochDay(it.remindEpochDay), today) }
    val skipped = wishes.filter { it.status == "SKIPPED" }
    val bought = wishes.filter { it.status == "BOUGHT" }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("사고 싶은 것 · 7일 참기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val saved = skipped.sumOf { it.price }
            FormCard {
                Text("참아서 아낀 돈", fontSize = 13.sp, color = DonColors.InkMuted)
                Text(won(saved), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.Good)
                if (saved > 0) {
                    Text(
                        "최저시급으로 ${com.simbio.don.budget.MinWage.text(com.simbio.don.budget.MinWage.minutes(saved, today.year))} 일한 돈" + (com.simbio.don.budget.Worths.of(saved, today.dayOfYear.toLong())?.let { " · ${it.big}" } ?: ""),
                        fontSize = 12.sp,
                        color = DonColors.InkSoft,
                    )
                }
                Text("부자들은 사고 싶은 걸 바로 사지 않아요. 7일 기다리면 진짜 필요한지 보여요.", fontSize = 12.sp, color = DonColors.InkMuted)
            }
            if (due.isNotEmpty()) {
                FormCard("7일 참았어요 — 아직도 사고 싶나요?") {
                    due.forEach { w ->
                        WishRow(w, "${(today.toEpochDay() - w.createdEpochDay)}일 참음")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSkip(w) }, modifier = Modifier.weight(1f)) { Text("안 살래요 (아낀 돈)") }
                            SubButton(onClick = { onBuy(w) }, modifier = Modifier.weight(1f)) { Text("그래도 살래요") }
                        }
                    }
                }
            }
            if (notYet.isNotEmpty()) {
                FormCard("참는 중") {
                    notYet.forEach { w ->
                        val left = com.simbio.don.budget.WaitRule.daysLeft(java.time.LocalDate.ofEpochDay(w.remindEpochDay), today)
                        WishRow(w, "D-$left")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubButton(onClick = { onSkip(w) }, modifier = Modifier.weight(1f)) { Text("벌써 안 사고 싶어요") }
                            TextButton(onClick = { onDelete(w) }) { Text("지우기", fontSize = 12.sp) }
                        }
                    }
                }
            }
            if (skipped.isNotEmpty() || bought.isNotEmpty()) {
                FormCard("끝난 것") {
                    skipped.forEach { WishRow(it, "참음") }
                    bought.forEach { WishRow(it, "샀음") }
                }
            }
            if (wishes.isEmpty()) Text("아직 없어요. '이거 사도 될까?'에서 가격을 넣고 '7일 참아 보기'를 눌러 보세요.", fontSize = 13.sp, color = DonColors.InkMuted)
            TextButton(onClick = onAdd) { Text("+ 사고 싶은 것 담기") }
        }
    }
}

@Composable
private fun WishRow(w: com.simbio.don.app.data.WishEntity, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        Text(tag, fontSize = 12.sp, color = DonColors.InkMuted)
        Spacer(Modifier.width(8.dp))
        Text(won(w.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
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
