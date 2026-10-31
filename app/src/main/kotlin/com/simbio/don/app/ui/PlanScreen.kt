package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category
import com.simbio.don.budget.PlanSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 계획(아직 안 쓴 돈)은 점선 테두리 + 회색으로, 실제 기록과 한눈에 다르게 */
fun Modifier.dashedBorder(color: Color, radius: Dp = 14.dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
    )
}

/** 계획 날짜 글자: "오늘", "내일", "3일 뒤", "2일 지남" */
fun planDday(date: LocalDate, today: LocalDate): String {
    val d = ChronoUnit.DAYS.between(today, date)
    return when {
        d == 0L -> "오늘"
        d == 1L -> "내일"
        d > 1 -> "${d}일 뒤"
        else -> "${-d}일 지남"
    }
}

/** 계획 한 줄 (가계부·홈에서도): 점선, "계획" 표, 회색 "예정" 금액 */
@Composable
fun PlanRow(p: PlanEntity, today: LocalDate, onClick: (() -> Unit)? = null) {
    val c = Category.of(p.category)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).dashedBorder(DonColors.InkFaint, 12.dp).clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.pressable(role = null, onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(c, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge("계획", DonColors.InkSoft)
                Text(p.label, fontSize = 14.sp, color = DonColors.InkSoft, maxLines = 1)
            }
            Text("${shortDate(LocalDate.ofEpochDay(p.epochDay))} · ${planDday(LocalDate.ofEpochDay(p.epochDay), today)} · ${c.label}", fontSize = 11.sp, color = DonColors.InkMuted)
        }
        Text("예정 ${won(p.amount)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.InkMuted)
    }
}

/**
 * 돈 나갈 계획: 앞으로 쓸 돈(생일 선물·병원·여행…)을 미리 적어 둔다. 실제 기록과 따로 보이고,
 * 쓰면 "썼어요"로 실제 기록과 이어 계획과 견준다. 안 쓰기로 하면 그만큼 아낀 돈.
 * 홈 "이대로 쓰면" 예측과 "일주일 안에"에도 들어간다.
 */
@Composable
fun PlanScreen(
    plans: List<PlanEntity>,
    matches: Map<Long, EntryEntity>,
    summary: PlanSummary?,
    today: LocalDate,
    /** 계획 추가 (두 번째 값: 폰 캘린더에도 넣기) */
    onAdd: (PlanEntity, Boolean) -> Unit,
    onSpent: (PlanEntity) -> Unit,
    onLink: (PlanEntity, EntryEntity) -> Unit,
    onCancel: (PlanEntity, Boolean) -> Unit,
    onDelete: (PlanEntity) -> Unit,
    onBack: () -> Unit,
    /** 폰 캘린더를 이었고 넣을 곳이 있으면 "캘린더에도 넣기"를 보인다 */
    calendarOn: Boolean = false,
    /** 저절로 이은 결제가 아니면 풀기 */
    onUnlink: (PlanEntity) -> Unit = {},
) {
    var date by remember { mutableStateOf(today.plusDays(1)) }
    var toCalendar by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.EVENT) }

    val open = plans.filter { it.open }
    val upcoming = open.filter { !LocalDate.ofEpochDay(it.epochDay).isBefore(today) }
    val overdue = open.filter { LocalDate.ofEpochDay(it.epochDay).isBefore(today) }
    val done = plans.filter { it.done }.sortedByDescending { it.doneEpochDay ?: it.epochDay }.take(20)
    val canceled = plans.filter { it.canceled }.sortedByDescending { it.epochDay }.take(10)

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("돈 나갈 계획", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (summary != null) {
                RealCard("이번 달 계획") {
                    Text(won(summary.planned), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text("계획한 돈은 실제 쓴 돈과 따로 보여요. 홈의 \"이대로 쓰면\" 예측에도 들어가요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    Spacer(Modifier.height(4.dp))
                    Line("아직 안 쓴 계획 ${summary.openCount}건", won(summary.openAmount))
                    if (summary.doneCount > 0) {
                        Line("쓴 계획 ${summary.doneCount}건 (계획 → 실제)", "${won(summary.donePlanned)} → ${won(summary.doneActual)}")
                        Text(
                            if (summary.doneDiff >= 0) "계획보다 ${won(summary.doneDiff)} 덜 썼어요" else "계획보다 ${won(-summary.doneDiff)} 더 썼어요",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (summary.doneDiff >= 0) DonColors.Good else DonColors.Warn,
                        )
                    }
                    if (summary.canceledAmount > 0) Line("안 쓰기로 해서 아낀 돈", won(summary.canceledAmount))
                }
            }

            FormCard("돈 나갈 일 적기") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    listOf("오늘" to today, "내일" to today.plusDays(1), "이번 토요일" to sat, "다음 주 월요일" to nextMon).forEach { (n, d) ->
                        FilterChip(selected = date == d, onClick = { date = d }, label = { Text(n) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(UiIcon.CHEVRON_LEFT, "하루 앞으로", date.isAfter(today)) { date = date.minusDays(1) }
                    Text(
                        "${shortDate(date)} · ${planDday(date, today)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DonColors.Ink,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    RoundIconButton(UiIcon.CHEVRON_RIGHT, "하루 뒤로", true) { date = date.plusDays(1) }
                }
                MoneyField("얼마쯤", amount, { amount = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(10_000L, 30_000L, 50_000L, 100_000L).forEach { n ->
                        FilterChip(selected = false, onClick = { amount = ((amount.toLongOrNull() ?: 0) + n).toString() }, label = { Text("+${compactWon(n)}") })
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    label = { Text("무엇에? (예: 친구 생일 선물)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Category.spending.filter { it != Category.BILLS }.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                if (calendarOn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("폰 캘린더에도 넣기", fontSize = 14.sp, color = DonColors.Ink)
                            Text("하루 종일 일정으로 들어가요. 계획을 지우면 그 일정도 지워요.", fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                        androidx.compose.material3.Switch(checked = toCalendar, onCheckedChange = { toCalendar = it })
                    }
                }
                Button(
                    onClick = {
                        onAdd(PlanEntity(epochDay = date.toEpochDay(), amount = amount.toLong(), label = label, category = category.name), calendarOn && toCalendar)
                        amount = ""
                        label = ""
                    },
                    enabled = (amount.toLongOrNull() ?: 0) > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("계획 추가") }
            }

            if (upcoming.isNotEmpty()) PlanSection("다가오는 계획", upcoming, matches, today, onSpent, onLink, onCancel, onDelete)
            if (overdue.isNotEmpty()) PlanSection("날짜가 지난 계획", overdue, matches, today, onSpent, onLink, onCancel, onDelete)
            if (plans.isEmpty()) {
                Text("아직 계획이 없어요. 생일·병원·여행처럼 쓸 일이 정해진 돈을 미리 적어 두면, 예산이 갑자기 모자라지 않아요.", fontSize = 13.sp, color = DonColors.InkMuted)
            } else if (open.isEmpty()) {
                Text("다가오는 계획이 없어요.", fontSize = 13.sp, color = DonColors.InkMuted)
            }
            if (done.isNotEmpty()) {
                SectionCard("쓴 계획 (계획 → 실제)") {
                    done.forEach { p ->
                        val diff = p.amount - (p.doneAmount ?: 0)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryIcon(Category.of(p.category), 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                                Text("${won(p.amount)} → ${won(p.doneAmount ?: 0)}", fontSize = 12.sp, color = DonColors.InkMuted)
                                if (p.autoLinked) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("들어온 결제와 저절로 이었어요", fontSize = 11.sp, color = DonColors.Real)
                                        TextButton(onClick = { onUnlink(p) }) { Text("아니에요", fontSize = 11.sp) }
                                    }
                                }
                            }
                            Text(
                                when {
                                    diff > 0 -> "${won(diff)} 덜"
                                    diff < 0 -> "${won(-diff)} 더"
                                    else -> "딱 맞게"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (diff >= 0) DonColors.Good else DonColors.Warn,
                            )
                        }
                    }
                }
            }
            if (canceled.isNotEmpty()) {
                SectionCard("안 쓰기로 한 계획") {
                    canceled.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, fontSize = 14.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                            Text(won(p.amount), fontSize = 13.sp, color = DonColors.Good, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { onCancel(p, false) }) { Text("되돌리기") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlanSection(
    title: String,
    list: List<PlanEntity>,
    matches: Map<Long, EntryEntity>,
    today: LocalDate,
    onSpent: (PlanEntity) -> Unit,
    onLink: (PlanEntity, EntryEntity) -> Unit,
    onCancel: (PlanEntity, Boolean) -> Unit,
    onDelete: (PlanEntity) -> Unit,
) {
    SectionCard(title) {
        list.forEach { p ->
            PlanRow(p, today)
            matches[p.id]?.let { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DonColors.RealSurface).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(UiIcon.CHECK, 16.dp, DonColors.Real)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "이 지출인가요? ${e.label} ${won(e.amount)} · ${shortDate(LocalDate.ofEpochDay(e.epochDay))}",
                        fontSize = 12.sp,
                        color = DonColors.Ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    SmallButton(onClick = { onLink(p, e) }) { Text("맞아요") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                SmallButton(onClick = { onSpent(p) }) { Text("썼어요") }
                TextButton(onClick = { onCancel(p, true) }) { Text("안 써요") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDelete(p) }, color = DonColors.InkMuted) { Text("지우기") }
            }
        }
    }
}
