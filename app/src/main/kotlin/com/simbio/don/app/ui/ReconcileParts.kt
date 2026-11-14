package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.BalanceGapEntity
import com.simbio.don.app.data.DonRepository.BalanceFix
import com.simbio.don.app.data.FxRateEntity
import com.simbio.don.app.data.PlanHint
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.PlanFitKind
import java.time.LocalDate

// ── 알림 잔액이 앱 잔액과 다를 때 ─────────────────────────────

/** "생활비 통장 잔액이 알림보다 n원 많아요" — 빠진 지출로 넣거나 잔액만 맞춘다 */
@Composable
fun BalanceGapCard(
    gap: BalanceGapEntity,
    accountName: String,
    onFix: (BalanceFix) -> Unit,
    onDismiss: () -> Unit,
    /** 자산 없이 적힌 지출 (이미 생활비엔 들어갔지만 잔액엔 빠진 것) */
    unassignedSpend: Long = 0,
) {
    val diff = gap.real - gap.app
    RealCard("$accountName 잔액이 달라요", icon = { Icon(UiIcon.WARN, 18.dp, DonColors.Warn) }) {
        Text(
            "알림 잔액 ${won(gap.real)} · 앱 계산 ${won(gap.app)}",
            fontSize = 13.sp,
            color = DonColors.InkSoft,
        )
        Text(
            if (diff < 0) "실제로 ${won(-diff)} 더 나갔어요. 놓친 결제 알림이 있었나 봐요." else "실제로 ${won(diff)} 더 있어요. 빠진 입금이나 두 번 센 지출이 있나 봐요.",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DonColors.Ink,
        )
        // 이미 적었는데 통장이 안 정해진 지출이 차이일 수 있다 → 빠진 지출로 넣으면 두 번 세진다
        if (diff < 0 && unassignedSpend > 0) {
            Text(
                "통장 없이 적힌 지출 ${won(unassignedSpend)}이 잔액에서 빠져 있어요. 이미 생활비에 들어간 돈이라 '빠진 지출'로 넣으면 두 번 세져요. 먼저 그 기록의 통장을 골라 주세요 (자산 탭).",
                fontSize = 12.sp,
                color = DonColors.Warn,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (diff < 0) {
                if (unassignedSpend > 0) SubButton(onClick = { onFix(BalanceFix.MISSING_SPEND) }, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("빠진 지출로") }
                else SmallButton(onClick = { onFix(BalanceFix.MISSING_SPEND) }, modifier = Modifier.weight(1f)) { Text("빠진 지출로") }
            } else {
                SmallButton(onClick = { onFix(BalanceFix.OVERCOUNTED) }, modifier = Modifier.weight(1f)) { Text("더 센 지출") }
                SmallButton(onClick = { onFix(BalanceFix.MISSING_INCOME) }, modifier = Modifier.weight(1f)) { Text("빠진 입금") }
            }
            SubButton(onClick = { onFix(BalanceFix.BALANCE_ONLY) }, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("잔액만") }
        }
        TextButton(onClick = onDismiss) { Text("괜찮아요 (그대로 둘게요)", fontSize = 12.sp) }
    }
}

// ── 계획과 실제 결제 짝 ──────────────────────────────────────

/**
 * "이 결제, '팀 회식' 계획인가요?" — 몇 명이서 냈는지는 내가 고른다 (앱이 정하지 않음) → 맞아요 / 아니에요.
 * 계획보다 몇 배 많이 나온 결제는 아무것도 골라 두지 않아 혼자인지 나눠 냈는지 꼭 고르게 한다.
 */
@Composable
fun PlanHintCard(h: PlanHint, onConfirm: (people: Int?) -> Unit, onReject: () -> Unit) {
    val paid = h.entry.amount
    val split = h.fit.kind == PlanFitKind.SPLIT
    // 이미 나눠 적은 결제면 그 사람 수, 아니면 혼자 (많이 나온 결제는 고를 때까지 비워 둔다)
    var people by remember(h.plan.id, h.entry.id) { mutableStateOf(h.entry.people ?: if (split) null else 1) }
    RealCard("이 결제, 계획한 거예요?", icon = { Icon(UiIcon.FLAG, 18.dp, DonColors.Real) }) {
        Text(
            "'${h.plan.label}' 계획 ${won(h.plan.amount)} · ${shortDate(LocalDate.ofEpochDay(h.plan.epochDay))}",
            fontSize = 13.sp,
            color = DonColors.InkSoft,
        )
        Text(
            "${h.entry.label} ${won(paid)} · ${shortDate(LocalDate.ofEpochDay(h.entry.epochDay))}",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = DonColors.Ink,
        )
        Text(
            if (split) "계획보다 많이 나왔어요. 혼자 냈는지, 몇 명이서 나눠 냈는지 골라 주세요."
            else "맞으면 계획과 이어서 한 번만 세요. 여럿이 먹고 내가 다 냈으면 몇 명이서인지 골라 주세요.",
            fontSize = 12.sp,
            color = DonColors.InkMuted,
        )
        PeoplePicker(people) { people = it }
        val n = people
        if (n != null && n > 1) {
            val mine = com.simbio.don.app.data.Splits.myShare(paid, n)
            Text(
                "내 몫 ${won(mine)}만 생활비에 · 나머지 ${won(paid - mine)}은 받을 돈 (한 사람 ${won(com.simbio.don.app.data.Splits.perPerson(paid, n))})",
                fontSize = 12.sp,
                color = DonColors.InkSoft,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SmallButton(onClick = { if (n != null) onConfirm(n) }, modifier = Modifier.weight(1.4f), containerColor = if (n == null) DonColors.InkMuted else DonColors.Real) {
                Text(if (n == null) "몇 명인지 골라 주세요" else if (n > 1) "${n}명이서 낸 계획이에요" else "혼자 낸 계획이에요")
            }
            SubButton(onClick = onReject, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("계획 아니에요") }
        }
    }
}

/** 몇 명이서: 혼자·2~6명 고르기, 더 많으면 − / + */
@Composable
fun PeoplePicker(people: Int?, onPick: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        (1..6).forEach { k -> FilterChip(selected = people == k, onClick = { onPick(k) }, label = { Text(if (k == 1) "혼자" else "${k}명") }) }
        val more = people != null && people > 6
        TextButton(onClick = { if (!more) onPick(7) }) { Text(if (more) "${people}명" else "7명+", fontSize = 13.sp) }
        if (more) {
            TextButton(onClick = { onPick(people!! - 1) }) { Text("−") }
            TextButton(onClick = { onPick((people!! + 1).coerceAtMost(30)) }) { Text("+") }
        }
    }
}

// ── 누구 돈이에요? ─────────────────────────────────────────

/**
 * 알림으로 들어온 돈이 정말 내 수입인지 묻는다: 정산 / 내 돈 옮김 / 친구가 맡긴 돈 / 수입 맞아요.
 * 수입이 아닌 것은 수입 합계에서 빠지고 통장 잔액에만 들어간다.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IncomeQuestionCard(
    items: List<com.simbio.don.app.data.EntryEntity>,
    receivables: List<com.simbio.don.app.data.Receivable>,
    ownerName: String?,
    onExplain: (com.simbio.don.app.data.EntryEntity, com.simbio.don.app.data.DonRepository.IncomeKind, Long?) -> Unit,
) {
    if (items.isEmpty()) return
    RealCard("들어온 돈, 다 내 수입이에요?", icon = { Icon(UiIcon.FLAG, 18.dp, DonColors.Real) }) {
        Text("친구한테 정산받은 돈, 내 다른 통장에서 옮긴 돈, 대신 내 달라고 맡긴 돈은 수입이 아니에요. 골라 주면 수입에서 빼고 잔액에만 넣어요.", fontSize = 12.sp, color = DonColors.InkMuted)
        items.take(3).forEach { e ->
            var settling by remember(e.id) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.SurfaceAlt).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("+${won(e.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Income)
                }
                Text(shortDate(LocalDate.ofEpochDay(e.epochDay)), fontSize = 12.sp, color = DonColors.InkMuted)
                val person = com.simbio.don.notify.SelfTransfer.PERSON.matches(e.label.removeSuffix("님")) && '*' !in e.label
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (receivables.isNotEmpty()) {
                        SmallButton(onClick = { settling = !settling }) { Text("정산 받은 돈") }
                    }
                    SmallButton(onClick = { onExplain(e, com.simbio.don.app.data.DonRepository.IncomeKind.SELF, null) }, containerColor = DonColors.Ink) { Text("내 돈 옮김") }
                    SmallButton(onClick = { onExplain(e, com.simbio.don.app.data.DonRepository.IncomeKind.HELD, null) }, containerColor = DonColors.Ink) { Text("친구가 맡긴 돈") }
                    SubButton(onClick = { onExplain(e, com.simbio.don.app.data.DonRepository.IncomeKind.INCOME, null) }, containerColor = DonColors.Surface) { Text("수입 맞아요") }
                }
                if (person && ownerName.isNullOrBlank()) {
                    Text("'${e.label.removeSuffix("님")}'이 나라면 '내 돈 옮김' — 다음부터 이 이름에서 온 돈은 저절로 내 계좌 이체로 적어요.", fontSize = 11.sp, color = DonColors.InkMuted)
                }
                if (settling) {
                    Text("어느 걸 정산받았어요?", fontSize = 12.sp, color = DonColors.InkSoft)
                    receivables.take(6).forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DonColors.Surface)
                                .clickable { onExplain(e, com.simbio.don.app.data.DonRepository.IncomeKind.SETTLE, r.entry.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${r.entry.label} · ${shortDate(LocalDate.ofEpochDay(r.entry.epochDay))}", fontSize = 13.sp, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("남은 ${won(r.left)}", fontSize = 12.sp, color = DonColors.Good)
                        }
                    }
                }
            }
        }
        if (items.size > 3) Text("그 밖에 ${items.size - 3}건 더", fontSize = 12.sp, color = DonColors.InkMuted)
    }
}

// ── 실제 잔액 맞추기 ─────────────────────────────────────────

/** 잔액을 맞출 수 있는 자산 (통장·현금·기타. 카드는 카드값 이체로, 모으는 통장은 자산 점검으로) */
fun fixable(b: AccountBalance): Boolean = !b.account.hidden && b.type in setOf(AccountType.BANK, AccountType.CASH, AccountType.ETC)

/**
 * 실제 잔액 맞추기: 자산마다 지금 실제 잔액을 적으면 앱 잔액과의 차이를 어떻게 볼지 고른다.
 * 빠진 지출·입금·더 센 지출은 오늘 기록으로 남아 생활비도 맞고, 잔액만 맞추면 기록은 그대로.
 */
@Composable
fun BalanceFixScreen(
    balances: List<AccountBalance>,
    gaps: List<BalanceGapEntity>,
    onFix: (accountId: Long, diff: Long, how: BalanceFix) -> Unit,
    onBack: () -> Unit,
    unassignedSpend: Long = 0,
    /** 어느 통장·카드인지 골라 줄 기록 */
    questions: List<com.simbio.don.app.data.EntryEntity> = emptyList(),
    onChooseAccount: (com.simbio.don.app.data.EntryEntity, Long, Boolean) -> Unit = { _, _, _ -> },
    /** 예전에 잔액 맞추기로 넣은 기록 (두 번 센 것이면 생활비에서 뺄 수 있게) */
    fixes: List<com.simbio.don.app.data.EntryEntity> = emptyList(),
    onDropFix: (com.simbio.don.app.data.EntryEntity) -> Unit = {},
    /** 아직 남은 받을 돈 (나눠 낸 것) — 이미 받아서 통장에 있으면 정리해야 생활비 차이가 안 남는다 */
    receivables: List<com.simbio.don.app.data.Receivable> = emptyList(),
    onReceived: (com.simbio.don.app.data.Receivable) -> Unit = {},
    onForgive: (com.simbio.don.app.data.Receivable) -> Unit = {},
) {
    val list = balances.filter(::fixable)
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("실제 잔액 맞추기", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "은행 앱에 보이는 지금 잔액을 적어 주세요. 결제 알림을 놓쳤거나 두 번 센 것이 있으면 그 차이를 기록으로 남겨 남은 생활비도 같이 맞춰요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            if (list.isEmpty()) {
                Text("맞출 통장이 없어요. 자산 탭에서 통장이나 현금을 넣어 주세요.", fontSize = 14.sp, color = DonColors.InkMuted)
            }
            if (questions.isNotEmpty()) {
                Text("먼저 어느 통장에서 나갔는지 골라 주세요. 통장 없이 적힌 지출은 잔액에서 빠져 있어서 잔액이 안 맞아 보일 수 있어요 (생활비는 그대로).", fontSize = 12.sp, color = DonColors.InkMuted)
                AccountQuestionCard(questions, balances.map { it.account }, onChoose = onChooseAccount, limit = 50)
            }
            if (receivables.isNotEmpty()) {
                // 받을 돈이 남아 있으면 "쓸 수 있는 돈"에 더해져 있다. 이미 받아서 통장 잔액에 들어왔는데 기록이 없으면
                // 잔액을 맞춘 뒤에도 그만큼 차이가 남으니 먼저 정리한다.
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("아직 받을 돈 ${won(receivables.sumOf { it.left })}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text(
                        "이미 받아서 통장에 들어와 있으면 '받았어요'를 먼저 눌러 주세요. 그래야 잔액을 맞춘 뒤에 받을 돈이 또 더해지지 않아요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                    )
                    receivables.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DonColors.SurfaceAlt).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${r.entry.label} · ${shortDate(LocalDate.ofEpochDay(r.entry.epochDay))}", fontSize = 13.sp, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("남은 ${won(r.left)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Good)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallButton(onClick = { onReceived(r) }) { Text("받았어요 (통장에 있음)") }
                                SubButton(onClick = { onForgive(r) }, containerColor = DonColors.Surface) { Text("안 받기로") }
                            }
                        }
                    }
                }
            }
            val livingId = list.filter { it.type == AccountType.BANK }.minByOrNull { it.account.sortOrder }?.account?.id
                ?: list.filter { it.type == AccountType.CASH }.minByOrNull { it.account.sortOrder }?.account?.id
            list.forEach { b -> BalanceFixRow(b, gaps.firstOrNull { it.accountId == b.account.id }, onFix, unassignedSpend > 0, living = b.account.id == livingId) }
            if (fixes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("잔액 맞추기로 넣은 기록", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text(
                        "이미 적힌 지출 때문에 생긴 차이였다면 생활비에 두 번 들어가 있어요. '생활비에서 빼기'를 누르면 기록은 지우고 통장 잔액은 그대로 둬요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                    )
                    fixes.sortedByDescending { it.epochDay }.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.label, fontSize = 14.sp, color = DonColors.Ink)
                                Text("${shortDate(LocalDate.ofEpochDay(e.epochDay))} · ${won(e.amount)}", fontSize = 12.sp, color = DonColors.InkMuted)
                            }
                            SubButton(onClick = { onDropFix(e) }, containerColor = DonColors.SurfaceAlt) { Text("생활비에서 빼기") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BalanceFixRow(b: AccountBalance, gap: BalanceGapEntity?, onFix: (Long, Long, BalanceFix) -> Unit, hasUnassigned: Boolean = false, living: Boolean = false) {
    // 알림에서 본 잔액이 있으면 그 차이를 먼저 채워 둔다
    var real by remember(b.account.id, b.balance) { mutableStateOf(gap?.let { (b.balance + (it.real - it.app)).toString() } ?: "") }
    val value = real.toLongOrNull()
    val diff = value?.let { it - b.balance }
    var how by remember(b.account.id) { mutableStateOf<BalanceFix?>(null) }
    val choice = how ?: when {
        diff == null || diff == 0L -> null
        // 생활비 통장은 기본으로 남은 생활비도 같이 통장에 맞춘다 (통장 = 생활비)
        living -> BalanceFix.ALIGN_LIVING
        // 통장 없이 적힌 지출이 남아 있으면 차이가 그것일 수 있어 기본은 잔액만 (두 번 세지 않게)
        diff < 0 -> if (hasUnassigned) BalanceFix.BALANCE_ONLY else BalanceFix.MISSING_SPEND
        else -> BalanceFix.MISSING_INCOME
    }
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.account.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text("앱 잔액 ${won(b.balance)}", fontSize = 13.sp, color = DonColors.InkMuted)
        }
        if (gap != null) Text("알림에서 본 잔액과 ${won(kotlin.math.abs(gap.real - gap.app))} 달라요", fontSize = 12.sp, color = DonColors.Warn)
        MoneyField("지금 실제 잔액", real, { real = it; how = null })
        if (diff != null && diff != 0L) {
            Text(
                if (diff < 0) "실제가 ${won(-diff)} 적어요" else "실제가 ${won(diff)} 많아요",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (diff < 0) DonColors.Warn else DonColors.Good,
            )
            val options = (if (living) listOf(BalanceFix.ALIGN_LIVING to "남은 생활비도 통장에 맞추기") else emptyList()) + if (diff < 0) {
                listOf(BalanceFix.MISSING_SPEND to "빠진 지출 (생활비에 넣기)", BalanceFix.BALANCE_ONLY to "잔액만 맞추기")
            } else {
                listOf(BalanceFix.MISSING_INCOME to "빠진 입금", BalanceFix.OVERCOUNTED to "더 센 지출 (생활비에서 빼기)", BalanceFix.BALANCE_ONLY to "잔액만 맞추기")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                options.forEach { (o, label) -> FilterChip(choice == o, { how = o }, { Text(label) }) }
            }
            Text(
                when (choice) {
                    BalanceFix.MISSING_SPEND -> "오늘 날짜로 '빠진 지출' ${won(-diff)}을 적어요. 이번 주·이번 달 남은 생활비도 그만큼 줄어요. 가계부에 이미 적힌 지출이면 고르지 마세요 (두 번 세져요)."
                    BalanceFix.MISSING_INCOME -> "오늘 날짜로 '빠진 입금' ${won(diff)}을 수입으로 적어요."
                    BalanceFix.OVERCOUNTED -> "오늘 날짜로 ${won(diff)}을 돌려받은 것처럼 적어 생활비에서 빼요 (두 번 센 지출이 있을 때)."
                    BalanceFix.BALANCE_ONLY -> "기록은 그대로 두고 잔액만 맞춰요 (생활비는 안 바뀌어요 — 홈의 남은 생활비와는 계속 다를 수 있어요)."
                    BalanceFix.ALIGN_LIVING -> "잔액을 맞추고, 이번 달 남은 생활비도 이 통장 기준으로 맞춰요. 차이는 '생활비 맞춤 (통장 기준)' 기록으로 남아요 (나중에 빼면 되돌려져요)."
                    null -> ""
                },
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
            Button(onClick = { choice?.let { onFix(b.account.id, diff, it) }; real = ""; how = null }, enabled = choice != null, modifier = Modifier.fillMaxWidth()) {
                Text("맞추기")
            }
        } else if (diff == 0L) {
            Text("앱 잔액과 같아요 👍", fontSize = 13.sp, color = DonColors.Good)
        }
    }
}

// ── 환율 ────────────────────────────────────────────────────

/** 설정: 해외 결제 환율 (내 환전 알림 · 공개 환율 · 직접) */
@Composable
fun FxRatesCard(rates: List<FxRateEntity>, auto: Boolean, onAuto: (Boolean) -> Unit, onSave: (String, Double) -> Unit) {
    RealCard("해외 결제·환율") {
        Text(
            "달러 같은 외화로 결제한 알림은 원화로 바꿔 적어요. 알림에 원화 금액이 있으면 그걸, 없으면 내 환전 알림의 환율 → 공개 환율 → 어림 순으로 써요. 환전은 지출이 아니라 이체로 적어요.",
            fontSize = 12.sp,
            color = DonColors.InkMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("공개 환율 자동 받기", fontSize = 14.sp, color = DonColors.Ink)
                Text("해외 결제가 있을 때만 하루 한 번, 통화 이름만 보내요 (유럽중앙은행 기준, frankfurter.app).", fontSize = 11.sp, color = DonColors.InkMuted)
            }
            Switch(checked = auto, onCheckedChange = onAuto)
        }
        if (rates.isEmpty()) Text("아직 받은 환율이 없어요. 해외 결제가 오면 여기 보여요.", fontSize = 12.sp, color = DonColors.InkMuted)
        rates.forEach { r ->
            var editing by remember(r.currency) { mutableStateOf(false) }
            var text by remember(r.currency, r.rate) { mutableStateOf("%.2f".format(r.rate)) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("1 ${r.currency} = ${"%,.2f".format(r.rate)}원", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                    Text(
                        shortDate(LocalDate.ofEpochDay(r.epochDay)) + " · " + when (r.source) {
                            "EXCHANGE" -> "내 환전 알림"
                            "PUBLIC" -> "공개 환율"
                            else -> "직접 정함"
                        },
                        fontSize = 11.sp,
                        color = DonColors.InkMuted,
                    )
                }
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "닫기" else "고치기", fontSize = 12.sp) }
            }
            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(10) },
                        label = { Text("1 ${r.currency}에 몇 원") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    SmallButton(onClick = { text.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSave(r.currency, it) }; editing = false }) { Text("저장") }
                }
            }
        }
    }
}

/**
 * "통장 잔액 ↔ 남은 생활비" 한 줄. 누르면 왜 다른지 항목별로 보인다 (카드값 낼 돈·받을 돈·보낼 돈·통장 없는 지출).
 * 그래도 남는 차이가 크면 잔액 맞추기로.
 */
@Composable
fun LivingGapRow(gap: com.simbio.don.app.data.LivingGap, onBalanceFix: () -> Unit, modifier: Modifier = Modifier, onAlign: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "통장 잔액과 남은 생활비 차이" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${gap.account.name} ${won(gap.bank)}", fontSize = 12.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
            Text(
                if (gap.hasItems) "쓸 수 있는 돈 ${won(gap.usable)}" else "차이 보기",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonColors.InkSoft,
            )
            Text(if (open) "  ▴" else "  ▾", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        if (open) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.SurfaceAlt).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GapLine("${gap.account.name} 잔액", gap.bank)
                if (gap.cash != 0L) GapLine("현금·페이 잔액", gap.cash)
                if (gap.cardDue != 0L) GapLine("카드값 낼 돈 (쓴 만큼 아직 안 나감)", -gap.cardDue)
                if (gap.cardFixed != 0L) Text("카드로 쓴 고정 지출 ${won(gap.cardFixed)}은 생활비가 아니라서 빼지 않았어요", fontSize = 11.sp, color = DonColors.InkMuted)
                if (gap.receivable != 0L) GapLine("받을 돈 (나눠 낸 것)", gap.receivable)
                if (gap.payable != 0L) GapLine("보낼 돈 (남이 낸 것)", -gap.payable)
                if (gap.unassigned != 0L) GapLine("통장 없이 적힌 지출", -gap.unassigned)
                Divider(Modifier.padding(vertical = 2.dp))
                GapLine("실제로 쓸 수 있는 돈", gap.usable, bold = true)
                GapLine("이번 달 남은 생활비", gap.remaining)
                GapLine("그래도 남는 차이", gap.rest, bold = true, warn = kotlin.math.abs(gap.rest) >= 1_000)
                Text(
                    if (kotlin.math.abs(gap.rest) < 1_000) "거의 맞아요."
                    else "통장에 처음부터 있던 생활비 밖의 돈(비상금·월세·저축으로 나갈 돈 등)이거나, 놓친 결제·입금 알림일 수 있어요. 은행 앱 잔액과 다르면 잔액 맞추기로 고쳐요.",
                    fontSize = 11.sp,
                    color = DonColors.InkMuted,
                )
                if (onAlign != null && gap.rest != 0L) {
                    Button(onClick = onAlign, modifier = Modifier.fillMaxWidth()) {
                        Text("남은 생활비를 통장에 맞추기 (${if (gap.rest > 0) "+" else "−"}${won(kotlin.math.abs(gap.rest))})", fontSize = 13.sp)
                    }
                    Text("통장 잔액이 맞다면 눌러요. 차이는 '생활비 맞춤' 기록으로 남고, 통장 잔액은 그대로예요.", fontSize = 11.sp, color = DonColors.InkMuted)
                }
                TextButton(onClick = onBalanceFix) { Text("통장 잔액부터 실제와 맞추기", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun GapLine(label: String, value: Long, bold: Boolean = false, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = if (bold) DonColors.Ink else DonColors.InkSoft, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(
            (if (value > 0 && label != "그래도 남는 차이" && !bold) "+" else "") + won(value),
            fontSize = 12.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (warn) DonColors.Warn else if (bold) DonColors.Ink else DonColors.InkSoft,
        )
    }
}
