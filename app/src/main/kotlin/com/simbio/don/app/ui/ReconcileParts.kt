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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onAssign: () -> Unit = {},
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
                "통장 없이 적힌 지출 ${won(unassignedSpend)}이 잔액에서 빠져 있어요. 이미 생활비에 들어간 돈이라 '빠진 지출'로 넣으면 두 번 세져요. 먼저 그 기록을 통장에 넣어 보세요.",
                fontSize = 12.sp,
                color = DonColors.Warn,
            )
            SmallButton(onClick = onAssign, modifier = Modifier.fillMaxWidth()) { Text("적힌 지출을 $accountName 에 넣기") }
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

/** "이 결제, '팀 회식' 계획인가요?" — 맞아요 / n명이 나눠 냄 / 아니에요 */
@Composable
fun PlanHintCard(h: PlanHint, onConfirm: (people: Int?) -> Unit, onReject: () -> Unit) {
    RealCard("이 결제, 계획한 거예요?", icon = { Icon(UiIcon.FLAG, 18.dp, DonColors.Real) }) {
        Text(
            "'${h.plan.label}' 계획 ${won(h.plan.amount)} · ${shortDate(LocalDate.ofEpochDay(h.plan.epochDay))}",
            fontSize = 13.sp,
            color = DonColors.InkSoft,
        )
        Text(
            "${h.entry.label} ${won(h.entry.share ?: h.entry.amount)} · ${shortDate(LocalDate.ofEpochDay(h.entry.epochDay))}" +
                (h.entry.share?.let { " (낸 돈 ${won(h.entry.amount)})" } ?: ""),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = DonColors.Ink,
        )
        val people = h.fit.people
        if (h.fit.kind == PlanFitKind.SPLIT && people != null) {
            Text("계획의 ${people}배쯤이에요. ${people}명이 나눠 냈으면 내 몫만 생활비에 넣고 나머지는 받을 돈으로 둬요.", fontSize = 12.sp, color = DonColors.InkMuted)
        } else {
            Text("맞으면 계획과 이어서 한 번만 세요 (계획 따로, 결제 따로 두 번 세지 않게).", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (h.fit.kind == PlanFitKind.SPLIT && people != null) {
                SmallButton(onClick = { onConfirm(people) }, modifier = Modifier.weight(1.4f)) { Text("${people}명이 나눠 냄") }
                SubButton(onClick = { onConfirm(null) }, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("혼자 냄") }
            } else {
                SmallButton(onClick = { onConfirm(null) }, modifier = Modifier.weight(1f)) { Text("맞아요") }
            }
            SubButton(onClick = onReject, modifier = Modifier.weight(1f), containerColor = DonColors.SurfaceAlt) { Text("아니에요") }
        }
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
    onAssign: () -> Unit = {},
    /** 예전에 잔액 맞추기로 넣은 기록 (두 번 센 것이면 생활비에서 뺄 수 있게) */
    fixes: List<com.simbio.don.app.data.EntryEntity> = emptyList(),
    onDropFix: (com.simbio.don.app.data.EntryEntity) -> Unit = {},
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
            if (unassignedSpend > 0) {
                Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("먼저: 통장 없이 적힌 지출 ${won(unassignedSpend)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text("생활비에는 이미 들어갔지만 통장 잔액에서는 빠져 있어요. 이걸 통장에 넣으면 차이가 줄어요 (생활비는 그대로).", fontSize = 12.sp, color = DonColors.InkMuted)
                    Button(onClick = onAssign, modifier = Modifier.fillMaxWidth()) { Text("생활비 통장에 넣기") }
                }
            }
            list.forEach { b -> BalanceFixRow(b, gaps.firstOrNull { it.accountId == b.account.id }, onFix, unassignedSpend > 0) }
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
private fun BalanceFixRow(b: AccountBalance, gap: BalanceGapEntity?, onFix: (Long, Long, BalanceFix) -> Unit, hasUnassigned: Boolean = false) {
    // 알림에서 본 잔액이 있으면 그 차이를 먼저 채워 둔다
    var real by remember(b.account.id, b.balance) { mutableStateOf(gap?.let { (b.balance + (it.real - it.app)).toString() } ?: "") }
    val value = real.toLongOrNull()
    val diff = value?.let { it - b.balance }
    var how by remember(b.account.id) { mutableStateOf<BalanceFix?>(null) }
    val choice = how ?: when {
        diff == null || diff == 0L -> null
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
            val options = if (diff < 0) {
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
                    BalanceFix.BALANCE_ONLY -> "기록은 그대로 두고 잔액만 맞춰요 (생활비는 안 바뀌어요)."
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
