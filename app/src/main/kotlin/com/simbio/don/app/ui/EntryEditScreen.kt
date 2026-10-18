package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.QuickPick
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryGuesser
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val minuteOfDay: Int? = null,
)

/** 입력 화면 맨 위 탭 */
private enum class Kind(val label: String) { INCOME("수입"), SPEND("지출"), TRANSFER("이체") }

private fun kindOf(t: EntryType) = when (t) {
    EntryType.INCOME -> Kind.INCOME
    EntryType.TRANSFER -> Kind.TRANSFER
    else -> Kind.SPEND
}

/**
 * 수입·지출·이체 입력과 수정. [existing]이 있으면 수정.
 * 가게 이름을 적는 동안 카테고리를 추측하고, 사용자가 고르면 그대로 둔다.
 * "계속"을 누르면 저장하고 날짜·자산은 둔 채 다음 것을 적는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    minDate: LocalDate,
    maxDate: LocalDate,
    today: LocalDate,
    exceptionCount: Int,
    canAddException: Boolean,
    learned: Map<String, Category>,
    existing: EntryEntity?,
    accounts: List<AccountEntity> = emptyList(),
    picks: List<QuickPick> = emptyList(),
    initial: EntryDraft? = null,
    onSave: (EntryDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val start = existing?.let { enumOr(it.type, EntryType.SPEND) } ?: initial?.type ?: EntryType.SPEND
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: initial?.label ?: "") }
    var type by remember { mutableStateOf(start) }
    var picked by remember { mutableStateOf(existing?.let { Category.of(it.category) }) }
    var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var date by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.epochDay) } ?: initial?.date ?: today.coerceIn(minDate, maxDate)) }
    var minute by remember {
        mutableStateOf(existing?.minuteOfDay ?: if (existing == null) initial?.minuteOfDay ?: LocalTime.now().let { it.hour * 60 + it.minute } else null)
    }
    var accountId by remember { mutableStateOf(existing?.accountId ?: initial?.accountId) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId ?: initial?.toAccountId) }
    var applySame by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }

    val kind = kindOf(type)
    val isIncome = kind == Kind.INCOME
    // 지출↔수입을 바꾸면 고른 카테고리가 맞지 않을 수 있어 다시 추측
    val category = picked?.takeIf { it.income == isIncome }
        ?: if (isIncome) Category.guessIncome(label, amount.toLongOrNull() ?: 0, 0) else CategoryGuesser.guess(label, learned)
    val wasException = existing?.type == EntryType.EXCEPTION.name
    val exceptionAllowed = canAddException || wasException
    val recent = (0L..6L).map { today.minusDays(it) }.filter { it in minDate..maxDate }
    val shownAccounts = accounts.filter { !it.hidden || it.id == accountId || it.id == toAccountId }
    val transferOk = kind != Kind.TRANSFER || (accountId != null && toAccountId != null && accountId != toAccountId)
    val canSave = (amount.toLongOrNull() ?: 0) > 0 && transferOk

    fun draft() = EntryDraft(date, amount.toLong(), label.trim(), type, category, memo.trim(), applySame, accountId, toAccountId, minute)

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(if (existing == null) "가계부 쓰기" else "기록 고치기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KindTabs(kind) { k ->
                type = when (k) {
                    Kind.INCOME -> EntryType.INCOME
                    Kind.TRANSFER -> EntryType.TRANSFER
                    Kind.SPEND -> if (type == EntryType.INCOME || type == EntryType.TRANSFER) EntryType.SPEND else type
                }
            }
            if (savedCount > 0) Text("${savedCount}건 저장했어요. 이어서 적어요.", fontSize = 13.sp, color = DonColors.Good, fontWeight = FontWeight.SemiBold)

            FormCard {
                when (existing?.source) {
                    EntrySource.NOTIFICATION.name -> Text("결제 알림에서 자동으로 기록됐어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    EntrySource.RECURRING.name -> Text("고정 지출이라 정한 날에 자동으로 적었어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
                FormRow("날짜") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = true, onClick = { showDate = true }, label = { Text(shortDate(date)) })
                        FilterChip(selected = minute != null, onClick = { showTime = true }, label = { Text(minute?.let(::timeText) ?: "시각") })
                        recent.filter { it != date }.take(3).forEach { d ->
                            FilterChip(selected = false, onClick = { date = d }, label = { Text(if (d == today) "오늘" else if (d == today.minusDays(1)) "어제" else shortDate(d)) })
                        }
                    }
                }
                MoneyField("금액", amount, { amount = it })
                AmountQuickAdd(amount) { amount = it }
            }

            if (kind == Kind.TRANSFER) {
                FormCard("어디서 어디로") {
                    if (shownAccounts.size < 2) {
                        Text("이체는 자산이 두 개 이상 있어야 해요. 자산 탭에서 통장·현금·카드를 추가하세요.", fontSize = 13.sp, color = DonColors.Warn)
                    }
                    FormRow("보낸 곳") { AccountChips(shownAccounts, accountId, allowNone = false) { accountId = it } }
                    FormRow("받은 곳") { AccountChips(shownAccounts.filter { it.id != accountId }, toAccountId, allowNone = false) { toAccountId = it } }
                    Text("통장에서 현금을 뽑거나 카드값을 내는 것처럼 내 돈끼리 옮긴 거예요. 수입·지출에는 들어가지 않아요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
            } else {
                FormCard("분류") {
                    CategoryGrid(category, if (isIncome) Category.incomes else Category.spending) { picked = it }
                    if (existing != null && picked != null && picked!!.name != existing.category && !isIncome) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = applySame, onCheckedChange = { applySame = it })
                            Text("'${label.ifBlank { existing.label }}'의 다른 기록도 바꾸기", fontSize = 13.sp)
                        }
                    }
                }
                if (shownAccounts.isNotEmpty()) {
                    FormCard(if (isIncome) "들어온 곳" else "결제 수단") { AccountChips(shownAccounts, accountId, allowNone = true) { accountId = it } }
                }
            }

            FormCard {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text(when (kind) { Kind.INCOME -> "내용 (어디서 들어왔나요)"; Kind.TRANSFER -> "내용 (예: 카드값)"; Kind.SPEND -> "내용 (어디에 썼나요)" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // 자주 쓰는 곳: 누르면 이름·금액·카테고리를 한 번에
                val shown = if (existing != null || kind != Kind.SPEND) emptyList() else picks.filter { p ->
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
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it.take(100) },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (kind == Kind.SPEND) {
                FormCard("지출 종류") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = type == EntryType.SPEND, onClick = { type = EntryType.SPEND }, label = { Text("생활비") })
                        FilterChip(selected = type == EntryType.FIXED, onClick = { type = EntryType.FIXED }, label = { Text("고정 지출") })
                        FilterChip(
                            selected = type == EntryType.EXCEPTION,
                            onClick = { if (exceptionAllowed) type = EntryType.EXCEPTION },
                            enabled = exceptionAllowed,
                            label = { Text("예외 지출 $exceptionCount/2") },
                        )
                        FilterChip(selected = type == EntryType.REFUND, onClick = { type = EntryType.REFUND }, label = { Text("환불") })
                    }
                    Text(
                        when (type) {
                            EntryType.FIXED -> "월세·통신비처럼 월급에서 미리 뺀 돈이에요. 가계부 지출에는 들어가고 주 예산은 깎지 않아요."
                            EntryType.EXCEPTION -> "병원비·경조사비처럼 피할 수 없는 큰 지출이에요. 이번 주 결산에는 영향이 없고, 남은 주 예산을 줄여서 다시 나눠요."
                            EntryType.REFUND -> "취소·환불로 돌려받은 돈이에요. 그날 지출에서 빠져요."
                            else -> "이번 주 예산에서 빠지는 보통 지출이에요."
                        },
                        fontSize = 12.sp,
                        color = if (type == EntryType.EXCEPTION) DonColors.Warn else DonColors.InkMuted,
                    )
                }
            }

            if (onDelete != null) {
                TextButton(onClick = { confirmDelete = true }, color = DonColors.InkMuted) { Text("이 기록 지우기") }
            }
        }
        BottomBar {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) {
                    SubButton(
                        onClick = {
                            onSave(draft())
                            savedCount++
                            amount = ""; label = ""; memo = ""; picked = null
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f),
                    ) { Text("계속") }
                }
                Button(
                    onClick = { onSave(draft()); onBack() },
                    enabled = canSave,
                    modifier = Modifier.weight(if (existing == null) 2f else 1f),
                ) { Text("저장") }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 기록을 지울까요?") },
            text = { Text("지운 뒤 잠깐 동안은 되돌릴 수 있어요.") },
            confirmButton = { TextButton(onClick = { onDelete(); onBack() }, color = DonColors.Warn) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return d in minDate..maxDate
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("고르기") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val m = minute ?: LocalTime.now().let { it.hour * 60 + it.minute }
        val state = rememberTimePickerState(initialHour = m / 60, initialMinute = m % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("시각") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { minute = state.hour * 60 + state.minute; showTime = false }) { Text("고르기") } },
            dismissButton = { TextButton(onClick = { minute = null; showTime = false }) { Text("시각 없이") } },
        )
    }
}

/** 수입 · 지출 · 이체 (가계부 관례 색) */
@Composable
private fun KindTabs(selected: Kind, onSelect: (Kind) -> Unit) {
    Row(Modifier.fillMaxWidth().card(DonColors.Line.copy(alpha = 0.6f), 12.dp).padding(4.dp)) {
        Kind.entries.forEach { k ->
            val on = k == selected
            val color = when (k) { Kind.INCOME -> DonColors.Income; Kind.SPEND -> DonColors.Expense; Kind.TRANSFER -> DonColors.InkSoft }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) DonColors.Surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(k) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(k.label, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) color else DonColors.InkMuted)
            }
        }
    }
}

/** 왼쪽 이름표 + 오른쪽 값 */
@Composable
fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 자산 고르기 칩 */
@Composable
fun AccountChips(accounts: List<AccountEntity>, selected: Long?, allowNone: Boolean, onSelect: (Long?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (allowNone) FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("선택 안 함") })
        accounts.forEach { a -> FilterChip(selected = a.id == selected, onClick = { onSelect(a.id) }, label = { Text(a.name) }) }
    }
}

/** 두세 칸 중 하나 고르기 */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().card(DonColors.Line.copy(alpha = 0.6f), 12.dp).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (on) DonColors.Surface else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) DonColors.Ink else DonColors.InkMuted,
                )
            }
        }
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
fun CategoryGrid(selected: Category, options: List<Category> = Category.spending, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.chunked(4).forEach { row ->
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
