package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.Receivable
import com.simbio.don.app.data.SplitBook
import com.simbio.don.app.data.SplitHint
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.popIn
import java.time.LocalDate

/** 펼친 칸: 받았어요 / 보냈어요 */
private sealed interface SplitPanel {
    data class Receive(val id: Long) : SplitPanel
    data class Pay(val id: Long) : SplitPanel
}

/**
 * 정산 장부 — 내가 다 내고 받을 돈, 남이 내서 보낼 돈.
 * 생활비에는 늘 내 몫만 들어가고, 친구가 보낸 돈은 수입이 아니라 정산으로 묶는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitScreen(
    book: SplitBook,
    accounts: List<AccountEntity>,
    onSettleWith: (target: EntryEntity, income: EntryEntity) -> Unit,
    onSettleDirect: (target: EntryEntity, amount: Long, accountId: Long?) -> Unit,
    onForgive: (EntryEntity) -> Unit,
    onUnsettle: (EntryEntity) -> Unit,
    onPaid: (owe: EntryEntity, accountId: Long?) -> Unit,
    onPayWith: (owe: EntryEntity, out: EntryEntity) -> Unit,
    onDismiss: (key: Long) -> Unit,
    onAdd: () -> Unit,
    onConfirmAuto: (EntryEntity) -> Unit = {},
    onOpen: (EntryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var panel by remember { mutableStateOf<SplitPanel?>(null) }
    val shown = accounts.filter { !it.hidden }
    val names = accounts.associate { it.id to it.name }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("정산", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormCard {
                Row(Modifier.fillMaxWidth()) {
                    SplitTotal("받을 돈", book.toReceive, DonColors.Good, Modifier.weight(1f))
                    SplitTotal("보낼 돈", book.toSend, DonColors.Warn, Modifier.weight(1f))
                }
                Text(
                    "내가 다 내면 내 몫만 생활비로 세고, 친구가 보낸 돈은 수입이 아니라 정산으로 묶어요. " +
                        "남이 내면 내 몫을 '보낼 돈'으로 적었다가 보낸 송금과 묶어요. 그래서 가계부가 꼬이지 않아요.",
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
                SubButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("나눠 낸 것 적기") }
            }

            if (book.autoLinks.isNotEmpty()) {
                FormCard("정산으로 저절로 묶었어요") {
                    Text("금액이 딱 맞아서 묶었어요. 아니면 되돌리세요 — 다시 저절로 묶지 않아요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    book.autoLinks.forEachIndexed { i, a ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        AutoLinkRow(a, { onConfirmAuto(a.link) }, { onUnsettle(a.link) })
                    }
                }
            }

            if (book.hints.isNotEmpty()) {
                FormCard("이 돈, 정산인가요?") {
                    book.hints.forEachIndexed { i, h ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        HintRow(h, onSettleWith, onPayWith, onDismiss)
                    }
                }
            }

            if (book.receivables.isNotEmpty()) {
                FormCard("받을 돈") {
                    book.receivables.forEachIndexed { i, r ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        ReceivableRow(r, names, onOpen, onUnsettle)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallButton(onClick = { panel = if (panel == SplitPanel.Receive(r.entry.id)) null else SplitPanel.Receive(r.entry.id) }) { Text("받았어요") }
                            TextButton(onClick = { onForgive(r.entry) }, color = DonColors.InkMuted) { Text("안 받을래요") }
                            TextButton(onClick = { onOpen(r.entry) }, color = DonColors.InkMuted) { Text("고치기") }
                        }
                        if (panel == SplitPanel.Receive(r.entry.id)) {
                            ReceivePanel(r, shown, { amount, acc -> onSettleDirect(r.entry, amount, acc); panel = null }) { panel = null }
                        }
                    }
                }
            }

            if (book.payables.isNotEmpty()) {
                FormCard("보낼 돈") {
                    book.payables.forEachIndexed { i, p ->
                        if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
                                Text(
                                    listOfNotNull(shortDate(LocalDate.ofEpochDay(p.epochDay)), p.memo.ifBlank { null }).joinToString(" · "),
                                    fontSize = 12.sp,
                                    color = DonColors.InkMuted,
                                    maxLines = 1,
                                )
                            }
                            Text(won(p.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Warn)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SmallButton(onClick = { panel = if (panel == SplitPanel.Pay(p.id)) null else SplitPanel.Pay(p.id) }) { Text("보냈어요") }
                            TextButton(onClick = { onOpen(p) }, color = DonColors.InkMuted) { Text("고치기") }
                        }
                        if (panel == SplitPanel.Pay(p.id)) {
                            PayPanel(p, shown, { acc -> onPaid(p, acc); panel = null }) { panel = null }
                        }
                    }
                }
            }

            if (book.receivables.isEmpty() && book.payables.isEmpty() && book.hints.isEmpty() && book.autoLinks.isEmpty()) {
                FormCard {
                    Text("남은 정산이 없어요.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                    Text(
                        "밥값을 내가 다 냈으면 가계부 쓰기에서 '나눠 냈나요 → 내가 다 냄'을, 남이 냈으면 '남이 냄'을 고르세요.",
                        fontSize = 12.sp,
                        color = DonColors.InkMuted,
                    )
                }
            }

            if (book.done.isNotEmpty()) {
                FormCard("끝난 정산") {
                    book.done.take(6).forEachIndexed { i, r ->
                        if (i > 0) Divider(Modifier.padding(vertical = 2.dp))
                        ReceivableRow(r, names, onOpen, onUnsettle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitTotal(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = DonColors.InkMuted)
        Text(won(amount), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (amount > 0) color else DonColors.InkFaint)
    }
}

@Composable
private fun ReceivableRow(r: Receivable, names: Map<Long, String>, onOpen: (EntryEntity) -> Unit, onUnsettle: (EntryEntity) -> Unit) {
    val e = r.entry
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
            Text(
                listOfNotNull(
                    shortDate(LocalDate.ofEpochDay(e.epochDay)),
                    e.people?.let { "${it}명" },
                    "낸 돈 ${won(e.amount)}",
                    "내 몫 ${won(e.share ?: e.amount)}",
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = DonColors.InkMuted,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (r.open) won(r.left) else "다 받음", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (r.open) DonColors.Good else DonColors.InkMuted)
            if (r.received > 0 && r.open) Text("받은 ${won(r.received)}", fontSize = 11.sp, color = DonColors.InkMuted)
        }
    }
    if (r.others > 0) {
        Column(Modifier.padding(vertical = 4.dp)) { Progress(r.received.toFloat() / r.others, DonColors.Good, 6.dp) }
    }
    r.receipts.forEach { rc ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "· ${shortDate(LocalDate.ofEpochDay(rc.epochDay))} +${won(rc.amount)} ${rc.label}" + (rc.toAccountId?.let(names::get)?.let { " → $it" } ?: ""),
                fontSize = 12.sp,
                color = DonColors.InkSoft,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                "되돌리기",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onUnsettle(rc) }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AutoLinkRow(a: com.simbio.don.app.data.AutoLink, onOk: () -> Unit, onUndo: () -> Unit) {
    val received = a.link.toAccountId != null || a.link.accountId == null
    Text(
        "${shortDate(LocalDate.ofEpochDay(a.link.epochDay))} ${if (received) "들어온 +" else "보낸 −"}${won(a.link.amount)} (${a.link.label})",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = DonColors.Ink,
    )
    Text(
        if (received) "'${a.target.label}' 정산으로 — 수입에 안 넣고 받을 돈을 줄였어요."
        else "보낼 돈 '${a.target.label}'을 보낸 것으로 — 지출로 또 세지 않아요.",
        fontSize = 12.sp,
        color = DonColors.InkMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton(onClick = onOk) { Text("맞아요") }
        TextButton(onClick = onUndo, color = DonColors.InkMuted) { Text(if (received) "아니에요 (수입으로)" else "아니에요 (송금 지출로)") }
    }
}

@Composable
private fun HintRow(
    h: SplitHint,
    onSettleWith: (EntryEntity, EntryEntity) -> Unit,
    onPayWith: (EntryEntity, EntryEntity) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    when (h) {
        is SplitHint.Received -> {
            Text(
                "${shortDate(LocalDate.ofEpochDay(h.income.epochDay))} 들어온 +${won(h.income.amount)} (${h.income.label})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonColors.Ink,
            )
            Text("'${h.target.entry.label}' 정산 같아요. 맞으면 수입에서 빼고 받을 돈을 줄여요.", fontSize = 12.sp, color = DonColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(onClick = { onSettleWith(h.target.entry, h.income) }) { Text("정산이에요") }
                TextButton(onClick = { onDismiss(h.key) }, color = DonColors.InkMuted) { Text("아니에요") }
            }
        }
        is SplitHint.Sent -> {
            Text(
                "${shortDate(LocalDate.ofEpochDay(h.out.epochDay))} 보낸 −${won(h.out.amount)} (${h.out.label})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonColors.Ink,
            )
            Text("보낼 돈 '${h.target.label}'을 보낸 것 같아요. 맞으면 두 기록을 하나로 합쳐요.", fontSize = 12.sp, color = DonColors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(onClick = { onPayWith(h.target, h.out) }) { Text("보낸 거예요") }
                TextButton(onClick = { onDismiss(h.key) }, color = DonColors.InkMuted) { Text("아니에요") }
            }
        }
    }
}

/** 받았어요: 금액(한 사람 몫·남은 돈 전부)과 받은 곳 */
@Composable
private fun ReceivePanel(r: Receivable, accounts: List<AccountEntity>, onDone: (Long, Long?) -> Unit, onClose: () -> Unit) {
    var amount by remember { mutableStateOf((r.perPerson?.coerceAtMost(r.left) ?: r.left).toString()) }
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    val v = amount.toLongOrNull() ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.GoodSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("'${r.entry.label}' 받은 돈", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("통장으로 들어온 입금이 기록돼 있으면 위 '이 돈, 정산인가요?'에서 고르면 돼요. 현금 등은 여기서 적어요.", fontSize = 12.sp, color = DonColors.InkMuted)
        MoneyField("받은 금액", amount, { amount = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            r.perPerson?.takeIf { it < r.left }?.let { p -> FilterChip(false, { amount = p.toString() }, { Text("한 사람 ${won(p)}") }) }
            FilterChip(false, { amount = r.left.toString() }, { Text("남은 돈 전부") })
        }
        if (accounts.isNotEmpty()) FormRow("받은 곳") { AccountChips(accounts, account, allowNone = true) { account = it } }
        if (v > r.left) Text("남은 받을 돈은 ${won(r.left)}이에요", fontSize = 12.sp, color = DonColors.Warn)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onDone(v, account) }, enabled = v in 1..r.left, modifier = Modifier.weight(2f)) { Text("받았어요") }
        }
    }
}

/** 보냈어요: 어느 통장에서 */
@Composable
private fun PayPanel(p: EntryEntity, accounts: List<AccountEntity>, onDone: (Long?) -> Unit, onClose: () -> Unit) {
    var account by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.RealSurface).padding(12.dp).popIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("'${p.label}' ${won(p.amount)} 보냄", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("송금 알림이 기록돼 있으면 위 '이 돈, 정산인가요?'에서 묶으면 돼요.", fontSize = 12.sp, color = DonColors.InkMuted)
        if (accounts.isNotEmpty()) FormRow("보낸 곳") { AccountChips(accounts, account, allowNone = true) { account = it } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubButton(onClick = onClose, modifier = Modifier.weight(1f), containerColor = DonColors.Surface) { Text("닫기") }
            Button(onClick = { onDone(account) }, modifier = Modifier.weight(2f)) { Text("보냈어요") }
        }
    }
}

/** 홈 카드: 남은 정산이 있을 때만 */
@Composable
fun SplitHomeCard(
    book: SplitBook,
    onOpen: () -> Unit,
    onSettle: (com.simbio.don.app.data.SplitHint) -> Unit = {},
    onDismiss: (Long) -> Unit = {},
) {
    if (book.isEmpty) return
    SectionCard("정산", trailing = { TextButton(onClick = onOpen) { Text("보기") } }) {
        Row(Modifier.fillMaxWidth()) {
            SplitTotal("받을 돈", book.toReceive, DonColors.Good, Modifier.weight(1f))
            SplitTotal("보낼 돈", book.toSend, DonColors.Warn, Modifier.weight(1f))
        }
        if (book.autoLinks.isNotEmpty()) {
            Text(
                "정산으로 저절로 묶은 돈 ${book.autoLinks.size}건 — 눌러서 확인해요",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DonColors.Good,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(DonColors.GoodSurface).clickable(onClick = onOpen)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        // 정산 같은 돈: 맨 위 하나는 여기서 바로 맞아요/아니에요 (나머지는 정산 화면에서)
        book.hints.firstOrNull()?.let { h ->
            val (what, ask) = when (h) {
                is com.simbio.don.app.data.SplitHint.Received ->
                    "${h.income.label} +${won(h.income.amount)}" to "'${h.target.entry.label}' 나눠 낸 돈을 받은 거예요?"
                is com.simbio.don.app.data.SplitHint.Sent ->
                    "${h.out.label} −${won(h.out.amount)}" to "보낼 돈 '${h.target.label}'을 보낸 거예요?"
            }
            Column(
                Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DonColors.RealSurface).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(what, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                Text(ask, fontSize = 12.sp, color = DonColors.InkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.simbio.don.app.ui.kit.SmallButton(onClick = { onSettle(h) }) { Text("맞아요") }
                    com.simbio.don.app.ui.kit.SubButton(onClick = { onDismiss(h.key) }, containerColor = DonColors.Surface) { Text("아니에요") }
                }
                if (book.hints.size > 1) {
                    Text("정산 같은 돈 ${book.hints.size - 1}건 더 — 눌러서 보기", fontSize = 12.sp, color = DonColors.Real, modifier = Modifier.clickable(onClick = onOpen))
                }
            }
        }
    }
}
