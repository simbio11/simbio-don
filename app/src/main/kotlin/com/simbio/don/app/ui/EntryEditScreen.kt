package com.simbio.don.app.ui

import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.budget.Installments
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
    /** 소분류 */
    val sub: String? = null,
    /** 할부 개월 (새 지출만, 1이면 일시불) */
    val months: Int = 1,
    /** 돈 나갈 계획에서 "썼어요"로 연 것이면 그 계획 (저장하면 계획과 이어진다) */
    val planId: Long? = null,
    /** 더치페이 — 내가 다 냈으면 내 몫과 몇 명이서 */
    val share: Long? = null,
    val people: Int? = null,
    /** 남이 내서 내가 보낼 돈 (금액 = 내 몫) */
    val owe: Boolean = false,
)

/** 나눠 내기: 혼자 냄 · 내가 다 냄 · 남이 냄 */
private enum class SplitMode(val label: String) { NONE("혼자 냄"), I_PAID("내가 다 냄"), THEY_PAID("남이 냄") }

/** 할부 고르기 칩 */
private val INSTALLMENT_CHOICES = listOf(1, 2, 3, 6, 10, 12, 24)

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
    /** 카테고리 이름 → 소분류 */
    subcategories: Map<String, List<String>> = emptyMap(),
    onAddSub: (Category, String) -> Unit = { _, _ -> },
    /** 이 기록이 할부면: "n/N" 표시와 할부 전체 지우기 */
    onDeleteInstallment: (() -> Unit)? = null,
    /** 정산으로 받은 돈이면: 정산 풀기 (다시 수입으로) */
    onUnsettle: (() -> Unit)? = null,
    onSave: (EntryDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val start = existing?.let { enumOr(it.type, EntryType.SPEND) } ?: initial?.type ?: EntryType.SPEND
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: initial?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: initial?.label ?: "") }
    var type by remember { mutableStateOf(start) }
    // 계획에서 연 것이면 계획한 분류 그대로
    var picked by remember { mutableStateOf(existing?.let { Category.of(it.category) } ?: initial?.takeIf { it.planId != null }?.category) }
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
    var sub by remember { mutableStateOf(existing?.sub ?: initial?.sub) }
    var months by remember { mutableStateOf(1) }
    var addingSub by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var splitMode by remember {
        mutableStateOf(
            when {
                existing?.owe == true || (existing == null && initial?.owe == true) -> SplitMode.THEY_PAID
                existing?.share != null || (existing == null && initial?.people != null) -> SplitMode.I_PAID
                else -> SplitMode.NONE
            },
        )
    }
    var people by remember { mutableStateOf(existing?.people ?: initial?.people ?: 2) }
    // 내 몫은 고치기 전까지 금액·인원으로 자동 계산
    var shareText by remember { mutableStateOf(existing?.share?.toString() ?: "") }
    var shareEdited by remember { mutableStateOf(existing?.share != null) }
    val settled = existing?.settleFor != null

    val kind = kindOf(type)
    val isIncome = kind == Kind.INCOME
    // 지출↔수입을 바꾸면 고른 카테고리가 맞지 않을 수 있어 다시 추측
    val category = picked?.takeIf { it.income == isIncome }
        ?: if (isIncome) Category.guessIncome(label, amount.toLongOrNull() ?: 0, 0) else CategoryGuesser.guess(label, learned)
    val wasException = existing?.type == EntryType.EXCEPTION.name
    val exceptionAllowed = canAddException || wasException
    val recent = (0L..6L).map { today.minusDays(it) }.filter { it in minDate..maxDate }
    val shownAccounts = accounts.filter { !it.hidden || it.id == accountId || it.id == toAccountId }
    val transferOk = kind != Kind.TRANSFER || settled || (accountId != null && toAccountId != null && accountId != toAccountId)
    val subs = subcategories[category.name].orEmpty()
    // 카테고리를 바꾸면 그 카테고리에 없는 소분류는 비운다
    val subNow = sub?.takeIf { it in subs || (existing?.sub == it && existing.category == category.name) }
    // 할부는 새 생활비 지출만 (고정·예외·환불은 일시불)
    val splitOk = kind == Kind.SPEND && (type == EntryType.SPEND || type == EntryType.EXCEPTION)
    val split = if (splitOk) splitMode else SplitMode.NONE
    val total = amount.toLongOrNull() ?: 0
    val share = if (shareEdited) shareText.toLongOrNull() ?: 0 else com.simbio.don.app.data.Splits.myShare(total, people)
    val shareOk = split != SplitMode.I_PAID || share in 0 until total
    val installmentOk = existing == null && type == EntryType.SPEND && split == SplitMode.NONE
    val monthsNow = if (installmentOk) months else 1
    val canSave = total > 0 && transferOk && total >= monthsNow && shareOk

    fun draft() = EntryDraft(
        date, amount.toLong(), label.trim(), type, category, memo.trim(), applySame, accountId, toAccountId, minute,
        subNow.takeIf { kind != Kind.TRANSFER }, monthsNow,
        planId = initial?.planId?.takeIf { savedCount == 0 },
        share = share.takeIf { split == SplitMode.I_PAID },
        people = people.takeIf { split == SplitMode.I_PAID },
        owe = split == SplitMode.THEY_PAID,
    )

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar(if (existing == null) "가계부 쓰기" else "기록 고치기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!settled) KindTabs(kind) { k ->
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
                    EntrySource.RECURRING.name -> Text(
                        if (existing.incomeId != null) "반복 수입이라 정한 날에 자동으로 적었어요." else "고정 지출이라 정한 날에 자동으로 적었어요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                    )
                }
                existing?.installmentNo?.let { no ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge("할부 $no", DonColors.Real)
                        Spacer(Modifier.width(8.dp))
                        Text("이 달 몫만 고쳐져요.", fontSize = 12.sp, color = DonColors.InkMuted, modifier = Modifier.weight(1f))
                        if (onDeleteInstallment != null) {
                            TextButton(onClick = { confirmDeleteAll = true }, color = DonColors.Warn) { Text("할부 전체 지우기") }
                        }
                    }
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

            if (settled) {
                FormCard("정산으로 받은 돈") {
                    Text("나눠 낸 돈을 돌려받은 정산이에요. 수입에 넣지 않고 받은 통장 잔액에만 더해요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    FormRow("받은 곳") { AccountChips(shownAccounts, toAccountId, allowNone = true) { toAccountId = it } }
                    if (onUnsettle != null) {
                        TextButton(onClick = { onUnsettle(); onBack() }, color = DonColors.Warn) { Text("정산 아니에요 (다시 수입으로)") }
                    }
                }
            } else if (kind == Kind.TRANSFER) {
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
                    CategoryGrid(category, if (isIncome) Category.incomes else Category.spending) { picked = it; sub = null }
                    // 소분류: 고르지 않아도 된다, 없으면 바로 더할 수 있다
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        subs.forEach { s -> FilterChip(selected = subNow == s, onClick = { sub = if (subNow == s) null else s }, label = { Text(s) }) }
                        FilterChip(selected = false, onClick = { addingSub = true }, label = { Text("+ 소분류") })
                    }
                    if (existing != null && picked != null && picked!!.name != existing.category && !isIncome) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = applySame, onCheckedChange = { applySame = it })
                            Text("'${label.ifBlank { existing.label }}'의 다른 기록도 바꾸기", fontSize = 13.sp)
                        }
                    }
                }
                if (splitOk) {
                    FormCard("나눠 냈나요") {
                        SegmentedToggle(SplitMode.entries.map { it.label }, splitMode.ordinal) { splitMode = SplitMode.entries[it] }
                        when (splitMode) {
                            SplitMode.I_PAID -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    (2..8).forEach { n -> FilterChip(selected = people == n, onClick = { people = n; shareEdited = false }, label = { Text("${n}명") }) }
                                }
                                MoneyField("내 몫", if (shareEdited) shareText else share.takeIf { total > 0 }?.toString() ?: "", { shareText = it; shareEdited = true })
                                Text(
                                    if (!shareOk) "내 몫은 낸 돈보다 작아야 해요"
                                    else "받을 돈 ${won((total - share).coerceAtLeast(0))}" +
                                        (if (!shareEdited && people > 1 && total > 0) " (한 사람 ${won(com.simbio.don.app.data.Splits.perPerson(total, people))})" else "") +
                                        ". 생활비에는 내 몫만 들어가고, 친구가 보낸 돈은 수입이 아니라 정산으로 묶어요.",
                                    fontSize = 12.sp,
                                    color = if (shareOk) DonColors.InkMuted else DonColors.Warn,
                                )
                            }
                            SplitMode.THEY_PAID -> Text(
                                "금액에 내 몫만 적으세요. '보낼 돈'으로 적어 두고, 보내면 그 송금과 묶어요 (통장 잔액은 보낼 때 빠져요).",
                                fontSize = 12.sp,
                                color = DonColors.InkMuted,
                            )
                            SplitMode.NONE -> Unit
                        }
                    }
                }
                if (shownAccounts.isNotEmpty() && split != SplitMode.THEY_PAID) {
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

            if (installmentOk) {
                FormCard("할부") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        INSTALLMENT_CHOICES.forEach { m ->
                            FilterChip(
                                selected = months == m,
                                onClick = { months = m },
                                label = { Text(if (m == 1) "일시불" else "${m}개월") },
                            )
                        }
                    }
                    if (months > 1) {
                        val total = amount.toLongOrNull() ?: 0
                        val each = if (total >= months) Installments.split(total, months) else emptyList()
                        Text(
                            if (each.isEmpty()) "금액이 너무 작아요" else "달마다 ${won(each.last())}씩 ${months}번, 오늘부터 매달 ${date.dayOfMonth}일에 적어요.",
                            fontSize = 12.sp,
                            color = if (each.isEmpty()) DonColors.Warn else DonColors.InkMuted,
                        )
                    }
                }
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
                            amount = ""; label = ""; memo = ""; picked = null; sub = null; months = 1
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

    if (addingSub) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingSub = false },
            title = { Text("${category.label} 소분류 더하기") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(12) }, label = { Text("이름 (예: 점심)") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = text.trim()
                    if (n.isNotEmpty()) { onAddSub(category, n); sub = n }
                    addingSub = false
                }, color = DonColors.Real) { Text("더하기") }
            },
            dismissButton = { TextButton(onClick = { addingSub = false }) { Text("취소") } },
        )
    }

    if (confirmDeleteAll && onDeleteInstallment != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("할부 전체를 지울까요?") },
            text = { Text("달마다 나눠 적은 기록이 모두 지워져요.") },
            confirmButton = { TextButton(onClick = { onDeleteInstallment(); onBack() }, color = DonColors.Warn) { Text("모두 지우기") } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("취소") } },
        )
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
