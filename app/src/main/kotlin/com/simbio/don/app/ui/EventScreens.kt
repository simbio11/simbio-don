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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.calendar.EventDraft
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.cal.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** 알림 고르기: 없음 · 정각 · 10분 · 30분 · 1시간 · 하루 전 */
val REMINDERS = listOf<Pair<Int?, String>>(null to "알림 없음", 0 to "정각", 10 to "10분 전", 30 to "30분 전", 60 to "1시간 전", 24 * 60 to "하루 전")

fun reminderLabel(m: Int?): String = REMINDERS.firstOrNull { it.first == m }?.second ?: "${m}분 전"

/** "2026년 10월 27일 (화)" */
fun longDate(d: LocalDate): String = "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

// ── 일정 보기 ────────────────────────────────────────────────

/**
 * 일정 한 개: 날짜·시각·반복·알림·장소·메모·캘린더와 그 일정에 쓸 돈 (예산 → 썼어요 → 지킴/넘음, 이은 결제).
 */
@Composable
fun EventDetailScreen(
    e: CalEvent,
    calendarName: String,
    plan: PlanEntity?,
    linked: EntryEntity?,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBudget: (Long) -> Unit,
    onSpent: (PlanEntity) -> Unit,
    onUnlink: (PlanEntity) -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("일정", onBack, trailing = {
            TextButton(onClick = onEdit) { Text("고치기") }
            TextButton(onClick = { confirm = true }, color = DonColors.Warn) { Text("지우기") }
        })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(Color(e.color or 0xFF000000.toInt())))
                    Spacer(Modifier.width(10.dp))
                    Text(e.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                }
                DetailLine(UiIcon.CLOCK, if (e.multiDay) "${longDate(e.startDate)} ~ ${longDate(e.endDate)}" else longDate(e.startDate), eventTimeRange(e).takeIf { !e.multiDay })
                if (e.repeat != Repeat.NONE) DetailLine(UiIcon.REPEAT, e.repeat.label)
                if (e.reminder != null) DetailLine(UiIcon.BELL, reminderLabel(e.reminder))
                e.location?.takeIf { it.isNotBlank() }?.let { DetailLine(UiIcon.FLAG, it) }
                e.memo?.takeIf { it.isNotBlank() }?.let { DetailLine(UiIcon.NOTE, it) }
                DetailLine(UiIcon.CALENDAR, calendarName)
            }
            EventMoneyCard(e, plan, linked, today, onBudget, onSpent, onUnlink, onOpenEntry)
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("일정을 지울까요?") },
            text = { Text(if (e.repeat != Repeat.NONE) "반복 일정은 모든 회차가 지워져요. 아직 안 쓴 예산도 같이 지워요." else "아직 안 쓴 예산도 같이 지워요.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }, color = DonColors.Warn) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("그대로") } },
        )
    }
}

@Composable
private fun DetailLine(icon: UiIcon, text: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, 18.dp, DonColors.InkMuted)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(text, fontSize = 14.sp, color = DonColors.Ink)
            sub?.let { Text(it, fontSize = 13.sp, color = DonColors.InkSoft) }
        }
    }
}

/** 일정에 쓸 돈: 정하기 · 썼어요 · 예산 vs 실제 · 이은 결제 */
@Composable
private fun EventMoneyCard(
    e: CalEvent,
    plan: PlanEntity?,
    linked: EntryEntity?,
    today: LocalDate,
    onBudget: (Long) -> Unit,
    onSpent: (PlanEntity) -> Unit,
    onUnlink: (PlanEntity) -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
) {
    var editing by remember(plan?.id) { mutableStateOf(plan == null) }
    var amount by remember(plan?.id) { mutableStateOf(plan?.amount?.toString() ?: "") }
    Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("이 일정에 쓸 돈", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        if (plan != null && !editing) {
            val (line, color) = budgetLine(plan)
            Text("예산 ${won(plan.amount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Real)
            if (plan.done || plan.canceled) Text(line, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
            if (plan.autoLinked) Text("들어온 결제와 저절로 이었어요.", fontSize = 12.sp, color = DonColors.InkMuted)
            linked?.let { LedgerRow(it, emptyMap(), onOpenEntry) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SubButton(onClick = { editing = true }, containerColor = DonColors.SurfaceAlt) { Text("예산 고치기") }
                if (!e.startDate.isAfter(today) && !plan.canceled) {
                    SmallButton(onClick = { onSpent(plan) }) { Text(if (plan.done) "더 썼어요" else "썼어요") }
                }
                if (plan.autoLinked) SubButton(onClick = { onUnlink(plan) }, containerColor = DonColors.SurfaceAlt) { Text("이 결제 아니에요") }
            }
        } else {
            Text(
                "정해 두면 그 주 오늘 몫에서 미리 빼 두고, 결제 알림이 오면 저절로 이어 예산과 실제를 견줘요.",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
            MoneyField("예산", amount, { amount = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(10_000L, 30_000L, 50_000L, 100_000L).forEach { n -> FilterChip(false, { amount = n.toString() }, { Text(compactWon(n)) }) }
            }
            val v = amount.toLongOrNull() ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan != null) SubButton(onClick = { editing = false }, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("닫기") }
                Button(onClick = { onBudget(v); editing = false }, enabled = v > 0, modifier = Modifier.weight(2f)) { Text("예산 정하기") }
            }
        }
    }
}

// ── 일정 만들기·고치기 ───────────────────────────────────────

/** 일정을 넣을 캘린더 하나 (id, 이름, 색) */
data class CalendarChoice(val id: Long, val name: String, val color: Int)

/**
 * 일정 만들기·고치기: 제목 · 하루 종일 · 시작/끝(날짜·시각) · 반복 · 알림 · 장소 · 메모 · 캘린더(새 일정만) · 쓸 돈.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    initial: EventDraft,
    isNew: Boolean,
    choices: List<CalendarChoice>,
    target: Long,
    budget: Long?,
    onSave: (EventDraft, Long, Long?) -> Unit,
    onBack: () -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var startDate by remember { mutableStateOf(initial.startDate) }
    var startMin by remember { mutableStateOf(initial.startMinute ?: 19 * 60) }
    var endDate by remember { mutableStateOf(initial.endDate) }
    var endMin by remember { mutableStateOf(initial.endMinute ?: ((initial.startMinute ?: 19 * 60) + 60).coerceAtMost(23 * 60 + 59)) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var reminder by remember { mutableStateOf(initial.reminder) }
    var location by remember { mutableStateOf(initial.location) }
    var memo by remember { mutableStateOf(initial.memo) }
    var calendar by remember { mutableStateOf(target) }
    var money by remember { mutableStateOf(budget?.toString() ?: "") }
    var pickDate by remember { mutableStateOf<Int?>(null) }
    var pickTime by remember { mutableStateOf<Int?>(null) }

    // 시작을 옮기면 끝도 같이 (길이 그대로)
    fun moveStart(d: LocalDate) {
        val len = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
        startDate = d
        endDate = d.plusDays(len.coerceAtLeast(0))
    }
    val endOk = !endDate.isBefore(startDate) && (allDay || endDate.isAfter(startDate) || endMin >= startMin)

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(if (isNew) "일정 추가" else "일정 고치기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("하루 종일", fontSize = 15.sp, color = DonColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                DateTimeRow("시작", startDate, startMin.takeIf { !allDay }, { moveStart(it) }, { startMin = it; if (endDate == startDate && endMin < it) endMin = (it + 60).coerceAtMost(23 * 60 + 59) }, { pickDate = 0 }, { pickTime = 0 })
                DateTimeRow("끝", endDate, endMin.takeIf { !allDay }, { endDate = it }, { endMin = it }, { pickDate = 1 }, { pickTime = 1 })
                if (!endOk) Text("끝이 시작보다 앞이에요.", fontSize = 12.sp, color = DonColors.Warn)
            }
            FormCard("반복 · 알림") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Repeat.entries.forEach { r -> FilterChip(repeat == r, { repeat = r }, { Text(r.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    REMINDERS.forEach { (m, label) -> FilterChip(reminder == m, { reminder = m }, { Text(label) }) }
                }
                if (allDay && reminder != null) Text("하루 종일 일정은 그날 오전 9시 기준으로 알려요.", fontSize = 11.sp, color = DonColors.InkMuted)
            }
            FormCard {
                OutlinedTextField(location, { location = it.take(60) }, label = { Text("장소 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(memo, { memo = it.take(500) }, label = { Text("메모 (선택)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            if (isNew && choices.size > 1) {
                FormCard("캘린더") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        choices.forEach { c -> FilterChip(calendar == c.id, { calendar = c.id }, { Text(c.name) }) }
                    }
                }
            }
            FormCard("이 일정에 쓸 돈 (선택)") {
                MoneyField("예산", money, { money = it })
                Text("정하면 그 주 오늘 몫에서 미리 빼 두고, 결제가 오면 이어서 지켰는지 보여 줘요.", fontSize = 12.sp, color = DonColors.InkMuted)
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    val draft = EventDraft(
                        title.trim(), startDate, startMin.takeIf { !allDay }, endDate, endMin.takeIf { !allDay },
                        location.trim(), memo.trim(), repeat, reminder,
                    )
                    onSave(draft, calendar, money.toLongOrNull()?.takeIf { it > 0 })
                },
                enabled = title.isNotBlank() && endOk,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }

    pickDate?.let { which ->
        val state = rememberDatePickerState(initialSelectedDateMillis = (if (which == 0) startDate else endDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == 0) moveStart(d) else endDate = d
                    }
                    pickDate = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickDate = null }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
    pickTime?.let { which ->
        val m = if (which == 0) startMin else endMin
        val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            confirmButton = {
                TextButton(onClick = {
                    val v = state.hour * 60 + state.minute
                    if (which == 0) {
                        startMin = v
                        if (endDate == startDate && endMin < v) endMin = (v + 60).coerceAtMost(23 * 60 + 59)
                    } else endMin = v
                    pickTime = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("취소") } },
            text = { TimePicker(state = state) },
        )
    }
}

/** "시작  ‹ 10/27(화) ›  ‹ 19:00 ›" — 가운데를 누르면 달력·시계로 고른다 */
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    minute: Int?,
    onDate: (LocalDate) -> Unit,
    onMinute: (Int) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.width(32.dp))
        Stepper(shortDate(date), "$label 날짜", { onDate(date.minusDays(1)) }, { onDate(date.plusDays(1)) }, onPickDate)
        if (minute != null) {
            Stepper(timeText(minute), "$label 시각", { onMinute((minute - 30).coerceAtLeast(0)) }, { onMinute((minute + 30).coerceAtMost(23 * 60 + 30)) }, onPickTime)
        }
    }
}

@Composable
private fun Stepper(text: String, what: String, onMinus: () -> Unit, onPlus: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(DonColors.SurfaceAlt), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = "$what 앞으로" }) { Icon(UiIcon.CHEVRON_LEFT, 16.dp, DonColors.InkMuted) }
        TextButton(onClick = onPick, color = DonColors.Ink) { Text(text, fontSize = 14.sp) }
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = "$what 뒤로" }) { Icon(UiIcon.CHEVRON_RIGHT, 16.dp, DonColors.InkMuted) }
    }
}
