package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.popIn
import java.time.LocalDate

/** 일정에 이은 예산 (없으면 null). 반복 일정은 날마다 따로라 날짜도 본다 */
fun planOf(e: CalEvent, plans: List<PlanEntity>): PlanEntity? =
    plans.firstOrNull { it.eventId == e.eventId && it.epochDay == e.startDate.toEpochDay() }

/** 가계부·홈 달력이 쓰는 폰 캘린더 상태와 동작 */
class CalendarHooks(
    /** 연동을 켰는지 (권한까지) */
    val on: Boolean = false,
    /** 새 일정을 넣을 캘린더가 있는지 */
    val canWrite: Boolean = false,
    /** 폰 캘린더(구글 등)를 이었는지 */
    val phoneOn: Boolean = false,
    val events: Map<LocalDate, List<CalEvent>> = emptyMap(),
    val onBudget: (CalEvent, Long) -> Unit = { _, _ -> },
    val onAdd: (LocalDate, String, Int?, Long?) -> Unit = { _, _, _, _ -> },
    val onSpent: (PlanEntity) -> Unit = {},
    val onConnect: () -> Unit = {},
    /** 저절로 이은 결제가 아니면 풀기 */
    val onUnlink: (PlanEntity) -> Unit = {},
) {
    companion object {
        val Off = CalendarHooks()
    }
}

/** 가계부 달력 칸 안 일정 이름 (두 개까지, 캘린더 색 바탕) */
@Composable
fun EventLabels(events: List<CalEvent>?, faded: Boolean = false, max: Int = 2) {
    if (events.isNullOrEmpty()) return
    events.take(max).forEach { e ->
        Text(
            e.title,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            color = if (faded) DonColors.InkFaint else DonColors.Ink,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp).clip(RoundedCornerShape(3.dp))
                .background(Color(e.color or 0xFF000000.toInt()).copy(alpha = if (faded) 0.1f else 0.22f)).padding(horizontal = 2.dp),
        )
    }
    if (events.size > max) Text("+${events.size - max}", fontSize = 8.sp, lineHeight = 10.sp, color = DonColors.InkMuted)
}

/** 달력 칸 아래 일정 점 (세 개까지, 캘린더 색) */
@Composable
fun EventDots(events: List<CalEvent>?, size: androidx.compose.ui.unit.Dp = 4.dp) {
    if (events.isNullOrEmpty()) return
    Row(Modifier.padding(vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        events.take(3).forEach { e -> Box(Modifier.size(size).clip(CircleShape).background(Color(e.color or 0xFF000000.toInt()))) }
    }
}

/** 일정 시각 "14:00" / "하루 종일" */
fun eventTime(e: CalEvent): String = e.minuteOfDay?.let(::timeText) ?: "하루 종일"

/**
 * 그날 일정과 일정마다 예산. 예산을 정하면 "돈 나갈 계획"이 되어 이번 주 오늘 몫에서 미리 빠지고,
 * 쓴 뒤에는 예산과 실제를 견준다 (지킴 · 넘음).
 */
@Composable
fun DayEventsCard(
    date: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    plans: List<PlanEntity>,
    calendarOn: Boolean,
    canWrite: Boolean,
    onBudget: (CalEvent, Long) -> Unit,
    onAdd: (title: String, minuteOfDay: Int?, budget: Long?) -> Unit,
    onSpent: (PlanEntity) -> Unit,
    onConnect: () -> Unit,
    onUnlink: (PlanEntity) -> Unit = {},
) {
    var adding by remember(date) { mutableStateOf(false) }
    var budgeting by remember(date) { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("일정", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            if (calendarOn && canWrite) TextButton(onClick = { adding = !adding }) { Text(if (adding) "닫기" else "+ 일정 추가") }
        }
        if (!calendarOn) {
            Text(
                "폰 캘린더(구글 캘린더 등)를 이으면 일정이 여기 보이고, 일정마다 쓸 돈을 미리 정해 지켰는지 볼 수 있어요.",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
            SmallButton(onClick = onConnect) { Text("캘린더 연동하기") }
            return@Column
        }
        if (events.isEmpty() && !adding) Text("이날은 일정이 없어요.", fontSize = 13.sp, color = DonColors.InkMuted)
        events.forEach { e ->
            val p = planOf(e, plans)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(e.color or 0xFF000000.toInt())))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
                    Text(listOfNotNull(eventTime(e), e.location?.takeIf { it.isNotBlank() }).joinToString(" · "), fontSize = 12.sp, color = DonColors.InkMuted, maxLines = 1)
                }
                EventBudget(p, date, today, onSpent, onUnlink) { budgeting = if (budgeting == e.eventId) null else e.eventId }
            }
            if (budgeting == e.eventId) {
                BudgetPanel(e.title, p?.amount, { amount -> onBudget(e, amount); budgeting = null }) { budgeting = null }
            }
        }
        if (adding) AddEventPanel(date, { t, m, b -> onAdd(t, m, b); adding = false }) { adding = false }
    }
}

/** 일정 오른쪽: 예산 없음 → 정하기 / 예산 → 썼어요 / 쓴 뒤 → 예산과 실제 */
@Composable
private fun EventBudget(p: PlanEntity?, date: LocalDate, today: LocalDate, onSpent: (PlanEntity) -> Unit, onUnlink: (PlanEntity) -> Unit, onEdit: () -> Unit) {
    when {
        p == null -> SmallButton(onClick = onEdit, containerColor = DonColors.SurfaceAlt, contentColor = DonColors.Real) { Text("예산 정하기") }
        p.canceled -> Text("안 썼어요", fontSize = 12.sp, color = DonColors.Good)
        p.done -> {
            val actual = p.doneAmount ?: 0
            val kept = actual <= p.amount
            Column(horizontalAlignment = Alignment.End) {
                Text("예산 ${won(p.amount)}", fontSize = 11.sp, color = DonColors.InkMuted)
                Text(
                    "실제 ${won(actual)} · " + if (kept) "지킴" else "${won(actual - p.amount)} 넘음",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (kept) DonColors.Good else DonColors.Warn,
                )
                Row {
                    // 들어온 결제와 저절로 이었으면 아니라고 풀 수 있게
                    if (p.autoLinked) TextButton(onClick = { onUnlink(p) }) { Text("이 결제 아니에요", fontSize = 12.sp) }
                    TextButton(onClick = { onSpent(p) }) { Text("더 썼어요", fontSize = 12.sp) }
                }
            }
        }
        else -> Column(horizontalAlignment = Alignment.End) {
            Text("예산 ${won(p.amount)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Real)
            Row {
                TextButton(onClick = onEdit) { Text("고치기", fontSize = 12.sp) }
                if (!date.isAfter(today)) TextButton(onClick = { onSpent(p) }) { Text("썼어요", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun BudgetPanel(title: String, current: Long?, onSave: (Long) -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableStateOf(current?.toString() ?: "") }
    val v = amount.toLongOrNull() ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.RealSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("'$title'에 쓸 돈", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("정해 두면 이번 주 남은 날 오늘 몫에서 미리 빼 두고, 쓴 뒤에 지켰는지 보여 줘요.", fontSize = 12.sp, color = DonColors.InkMuted)
        MoneyField("예산", amount, { amount = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(10_000L, 30_000L, 50_000L, 100_000L).forEach { n -> FilterChip(false, { amount = n.toString() }, { Text(compactWon(n)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onSave(v) }, enabled = v > 0, modifier = Modifier.weight(2f)) { Text("예산 정하기") }
        }
    }
}

/** 시각 고르기: 하루 종일 · 아침 · 점심 · 저녁 · 밤 */
private val EVENT_TIMES = listOf<Pair<String, Int?>>("하루 종일" to null, "아침 9시" to 9 * 60, "점심 12시" to 12 * 60, "저녁 7시" to 19 * 60, "밤 9시" to 21 * 60)

@Composable
private fun AddEventPanel(date: LocalDate, onAdd: (String, Int?, Long?) -> Unit, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf<Int?>(null) }
    var budget by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.RealSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${shortDate(date)} 일정 추가", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        OutlinedTextField(title, { title = it.take(40) }, label = { Text("일정 (예: 친구 생일 모임)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            EVENT_TIMES.forEach { (label, m) -> FilterChip(minute == m, { minute = m }, { Text(label) }) }
        }
        MoneyField("쓸 돈 (선택)", budget, { budget = it })
        Text("폰 캘린더에 넣어요 (구글 캘린더면 다른 기기에도 보여요). 쓸 돈을 적으면 예산도 같이 정해요.", fontSize = 12.sp, color = DonColors.InkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onAdd(title.trim(), minute, budget.toLongOrNull()?.takeIf { it > 0 }) }, enabled = title.isNotBlank(), modifier = Modifier.weight(2f)) {
                Text("캘린더에 넣기")
            }
        }
    }
}
