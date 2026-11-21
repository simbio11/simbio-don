package com.simbio.don.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.AskGuess
import com.simbio.don.app.data.AskKind
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.notify.RuleAction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 들어온 돈 알림인지 (짐작과 고를 답이 달라진다) */
fun ReviewItemEntity.isIn() = action == RuleAction.INCOME.name || action == RuleAction.REFUND.name

/** 비서의 짐작을 한 문장으로 */
fun askSentence(g: AskGuess, names: Map<Long, String>): String {
    fun n(id: Long?) = id?.let(names::get)
    return when (g.kind) {
        AskKind.SPEND -> "지출 같아요"
        AskKind.FIXED -> "고정 지출 같아요"
        AskKind.INCOME -> "수입 같아요"
        AskKind.SALARY -> "월급 같아요 — 맞으면 새 달을 시작해요"
        AskKind.TRANSFER -> {
            val from = n(g.from) ?: "내 통장"
            val to = n(g.to) ?: "내 다른 통장"
            "$from → $to, 내 통장끼리 옮긴 돈 같아요"
        }
        AskKind.SAVINGS -> "${n(g.to) ?: "저축"}(으)로 저축한 돈 같아요"
        AskKind.CARD_BILL -> "카드값이 빠져나간 것 같아요"
        AskKind.CASH -> "현금을 뽑은 것 같아요"
        AskKind.CHARGE -> "페이에 충전한 것 같아요"
        AskKind.SETTLE -> "나눠 낸 돈을 돌려받은 것 같아요"
        AskKind.REFUND -> "환불 같아요"
        AskKind.IGNORE -> "적지 않아도 될 알림 같아요"
    }
}

/** 짐작 말고 고를 수 있는 답들 (라벨, 답) */
fun askChoices(item: ReviewItemEntity, g: AskGuess, accounts: List<AccountEntity>): List<Pair<String, AskGuess>> {
    val shown = accounts.filter { !it.hidden }
    val banks = shown.filter { enumOr(it.type, AccountType.ETC).let { t -> t == AccountType.BANK || t == AccountType.CASH } }
    val grows = shown.filter { enumOr(it.type, AccountType.ETC).grows }
    val out = mutableListOf<Pair<String, AskGuess>>()
    if (item.isIn()) {
        val mine = g.to
        out += "수입" to AskGuess(AskKind.INCOME, to = mine, category = g.category?.takeIf { g.kind == AskKind.INCOME })
        out += "월급" to AskGuess(AskKind.SALARY, to = mine)
        banks.filter { it.id != mine }.forEach { a -> out += "${a.name}에서 옮김" to AskGuess(AskKind.TRANSFER, from = a.id, to = mine) }
        grows.filter { it.id != mine }.forEach { a -> out += "${a.name}에서 찾음" to AskGuess(AskKind.TRANSFER, from = a.id, to = mine) }
        out += "내 다른 통장에서 (앱에 없음)" to AskGuess(AskKind.TRANSFER, from = null, to = mine)
        out += "환불" to AskGuess(AskKind.REFUND)
    } else {
        val mine = g.from
        out += "지출" to AskGuess(AskKind.SPEND, from = mine)
        out += "고정 지출" to AskGuess(AskKind.FIXED, from = mine)
        grows.filter { it.id != mine }.forEach { a -> out += "저축 · ${a.name}" to AskGuess(AskKind.SAVINGS, from = mine, to = a.id) }
        banks.filter { it.id != mine }.forEach { a -> out += "→ ${a.name}" to AskGuess(AskKind.TRANSFER, from = mine, to = a.id) }
        out += "내 다른 통장으로 (앱에 없음)" to AskGuess(AskKind.TRANSFER, from = mine, to = null)
    }
    out += "무시" to AskGuess(AskKind.IGNORE)
    // 짐작과 똑같은 답은 "맞아요"로 고른다
    return out.filter { (_, a) -> a.copy(why = "", category = null) != g.copy(why = "", category = null) }
}

/**
 * "비서가 물어봐요" — 월급·이체·송금처럼 애매한 알림은 바로 적지 않고 짐작을 보여 준다.
 * [맞아요] 한 번이면 그대로 적고, 아니면 다른 답을 누른다. "다음에도 이렇게"면 같은 모양 알림은 묻지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskCard(
    items: List<ReviewItemEntity>,
    accounts: List<AccountEntity>,
    onAnswer: (ReviewItemEntity, AskGuess, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 3,
    onMore: (() -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val names = accounts.associate { it.id to it.name }
    Column(modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("비서가 물어봐요", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text("${items.size}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.Warn)
        }
        Text("애매한 알림은 바로 적지 않았어요. 맞는지만 눌러 주세요.", fontSize = 12.sp, color = DonColors.InkMuted)
        items.sortedBy { it.postedAtEpochSecond }.take(limit).forEach { item -> AskRow(item, names, accounts, onAnswer) }
        if (items.size > limit) {
            Text(
                "그 밖에 ${items.size - limit}건 더" + if (onMore != null) " — 비서에서 모두 보기" else "",
                fontSize = 11.sp,
                color = DonColors.InkMuted,
                modifier = if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskRow(item: ReviewItemEntity, names: Map<Long, String>, accounts: List<AccountEntity>, onAnswer: (ReviewItemEntity, AskGuess, Boolean) -> Unit) {
    val g = AskGuess.decode(item.guess) ?: AskGuess(if (item.isIn()) AskKind.INCOME else AskKind.SPEND)
    var other by remember(item.id) { mutableStateOf(false) }
    var keep by remember(item.id) { mutableStateOf(true) }
    val t = LocalDateTime.ofInstant(Instant.ofEpochSecond(item.postedAtEpochSecond), ZoneId.systemDefault())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.SurfaceAlt).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label.ifBlank { "알림" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text((if (item.isIn()) "+" else "−") + won(item.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (item.isIn()) DonColors.Good else DonColors.Ink)
        }
        Text("${shortDate(t.toLocalDate())} ${"%02d:%02d".format(t.hour, t.minute)} · ${if (item.rawText.contains(AskGuess.PAIR_SEP)) "나간·들어온 알림 2개" else "알림 1개"}", fontSize = 11.sp, color = DonColors.InkMuted)
        Text(askSentence(g, names), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
        if (g.why.isNotBlank()) Text(g.why, fontSize = 12.sp, color = DonColors.InkSoft)
        Text(item.rawText.replace(AskGuess.PAIR_SEP, "  ⇄  ").replace('\n', ' ').take(140), fontSize = 11.sp, color = DonColors.InkMuted, maxLines = 2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(onClick = { onAnswer(item, g, keep) }) { Text("맞아요") }
            SubButton(onClick = { other = !other }, containerColor = DonColors.Surface) { Text(if (other) "접기" else "다른 걸로") }
        }
        AnimatedVisibility(other) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                askChoices(item, g, accounts).forEach { (label, a) ->
                    FilterChip(selected = false, onClick = { onAnswer(item, a, keep) }, label = { Text(label, fontSize = 12.sp) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keep = !keep }) {
            Checkbox(checked = keep, onCheckedChange = { keep = it })
            Text("다음에도 이렇게 (같은 알림은 묻지 않고 적기)", fontSize = 12.sp, color = DonColors.InkSoft)
        }
    }
}
