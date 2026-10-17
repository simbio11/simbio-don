package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.card
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.simbio.don.app.ui.kit.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.simbio.don.app.ui.kit.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import com.simbio.don.app.ui.kit.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.data.QuickPick
import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryGuesser
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.PayCycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 저장할 값 묶음 */
data class EntryDraft(
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val type: EntryType,
    val category: Category,
    val memo: String,
    val applyToSameLabel: Boolean,
)

/**
 * 지출 입력·수정. [existing]이 있으면 수정.
 * 가게 이름을 적는 동안 카테고리를 추측하고, 사용자가 고르면 그대로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    cycle: PayCycle,
    today: LocalDate,
    exceptionCount: Int,
    canAddException: Boolean,
    learned: Map<String, Category>,
    existing: EntryEntity?,
    picks: List<QuickPick> = emptyList(),
    onSave: (EntryDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var type by remember { mutableStateOf(existing?.let { enumOr(it.type, EntryType.SPEND) } ?: EntryType.SPEND) }
    var picked by remember { mutableStateOf(existing?.let { Category.of(it.category) }) }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var date by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.epochDay) } ?: today.coerceIn(cycle.start, cycle.end)) }
    var applySame by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val category = picked ?: CategoryGuesser.guess(label, learned)
    val wasException = existing?.type == EntryType.EXCEPTION.name
    val exceptionAllowed = canAddException || wasException
    val recent = (0L..6L).map { today.minusDays(it) }.filter { it in cycle }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(if (existing == null) "지출 입력" else "기록 고치기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                if (existing?.source == EntrySource.NOTIFICATION.name) {
                    Text("결제 알림에서 자동으로 기록됐어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                MoneyField("금액", amount, { amount = it })
                AmountQuickAdd(amount) { amount = it }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("어디에 썼나요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 자주 쓰는 곳: 누르면 이름·금액·카테고리를 한 번에
                val shown = if (existing != null) emptyList() else picks.filter { p ->
                    label.isBlank() || (p.label.contains(label.trim(), ignoreCase = true) && !p.label.equals(label.trim(), ignoreCase = true))
                }.take(6)
                if (shown.isNotEmpty()) {
                    Text("자주 쓰는 곳", fontSize = 12.sp, color = DonColors.InkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        shown.forEach { p ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    label = p.label
                                    if (amount.isBlank()) amount = p.amount.toString()
                                    picked = p.category
                                },
                                label = { Text("${p.label} · ${won(p.amount)}") },
                            )
                        }
                    }
                }
            }

            FormCard("카테고리") {
                CategoryGrid(category) { picked = it }
                if (existing != null && picked != null && picked!!.name != existing.category) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applySame, onCheckedChange = { applySame = it })
                        Text("'${label.ifBlank { existing.label }}'의 다른 기록도 바꾸기", fontSize = 13.sp)
                    }
                }
            }

            FormCard("날짜") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (if (date in recent) recent else listOf(date) + recent).forEach { d ->
                        FilterChip(selected = d == date, onClick = { date = d }, label = { Text(shortDate(d)) })
                    }
                    FilterChip(selected = false, onClick = { showPicker = true }, label = { Text("달력에서 고르기") })
                }
            }

            FormCard("종류") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = type == EntryType.SPEND, onClick = { type = EntryType.SPEND }, label = { Text("일반 지출") })
                    FilterChip(selected = type == EntryType.REFUND, onClick = { type = EntryType.REFUND }, label = { Text("환불") })
                    FilterChip(
                        selected = type == EntryType.EXCEPTION,
                        onClick = { if (exceptionAllowed) type = EntryType.EXCEPTION },
                        enabled = exceptionAllowed,
                        label = { Text("예외 지출 $exceptionCount/2") },
                    )
                }
                if (type == EntryType.EXCEPTION) {
                    Text(
                        "병원비·경조사비처럼 피할 수 없는 큰 지출이에요. 이번 주 결산에는 영향이 없고, 남은 주 예산을 줄여서 다시 나눠요.",
                        fontSize = 13.sp,
                        color = DonColors.Warn,
                    )
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(100) },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (onDelete != null) {
                if (confirmDelete) {
                    TextButton(onClick = { onDelete(); onBack() }, color = DonColors.Warn) { Text("정말 지우기") }
                } else {
                    TextButton(onClick = { confirmDelete = true }, color = DonColors.InkMuted) { Text("이 기록 지우기") }
                }
            }
        }
        BottomBar {
            Button(
                onClick = {
                    onSave(EntryDraft(date, amount.toLong(), label.trim(), type, category, memo.trim(), applySame))
                    onBack()
                },
                enabled = (amount.toLongOrNull() ?: 0) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return d in cycle && !d.isAfter(today)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showPicker = false
                }) { Text("고르기") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }
}

/** 흰 카드 (+ 선택 소제목) */
@Composable
fun FormCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DonColors.Ink) }
        content()
    }
}

/** 카테고리 고르기: 동그란 아이콘 + 이름, 한 줄에 4개 */
@Composable
fun CategoryGrid(selected: Category, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Category.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    val on = c == selected
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) DonColors.RealSurface else Color.Transparent)
                            .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(c) })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryIcon(c, 40.dp)
                        Text(
                            c.label,
                            fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) DonColors.RealDeep else DonColors.InkSoft,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun LocalDate.coerceIn(a: LocalDate, b: LocalDate): LocalDate = if (isBefore(a)) a else if (isAfter(b)) b else this
