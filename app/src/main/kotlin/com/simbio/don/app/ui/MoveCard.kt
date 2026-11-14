package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.MoveQuestion
import com.simbio.don.app.data.enumOr
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import java.time.LocalDate

/**
 * "내 계좌끼리 옮긴 돈이에요?" — 통장에서 나간 송금이 저축이나 내 다른 통장으로 간 것 같으면 묻는다.
 * 맞다고 하면 지출이 아니라 이체가 되고, 저축으로 옮겼으면 (계획한 저축을 넘은 만큼) 생활비를 다시 나눈다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveQuestionCard(
    questions: List<MoveQuestion>,
    accounts: List<AccountEntity>,
    /** (질문, 받은 통장 id 또는 null, 저축인지) */
    onConfirm: (MoveQuestion, Long?, Boolean) -> Unit,
    onDismiss: (MoveQuestion) -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 2,
) {
    if (questions.isEmpty()) return
    val names = accounts.associate { it.id to it.name }
    val grows = accounts.filter { !it.hidden && enumOr(it.type, AccountType.ETC).grows }
    Column(modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 계좌끼리 옮긴 돈이에요?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text("${questions.size}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.Warn)
        }
        Text("저축이나 내 다른 통장으로 옮긴 돈은 지출이 아니에요. 저축으로 옮겼으면 생활비를 다시 나눠요.", fontSize = 12.sp, color = DonColors.InkMuted)
        questions.take(limit).forEach { q ->
            val o = q.out
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.SurfaceAlt).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("−${won(o.amount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                }
                val from = o.accountId?.let(names::get) ?: "통장"
                val into = q.into
                if (into != null) {
                    // 짝을 찾았다: 같은 돈이 다른 통장으로 들어왔다
                    Text(
                        "${shortDate(LocalDate.ofEpochDay(o.epochDay))} $from → ${into.accountId?.let(names::get) ?: "통장"} +${won(into.amount)}" +
                            (if (q.fee > 0) " (수수료 ${won(q.fee)})" else ""),
                        fontSize = 12.sp,
                        color = DonColors.InkSoft,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallButton(onClick = { onConfirm(q, into.accountId, false) }) { Text("네, 내 돈 옮김") }
                        SubButton(onClick = { onDismiss(q) }, containerColor = DonColors.Surface) { Text("아니에요") }
                    }
                } else {
                    Text("${shortDate(LocalDate.ofEpochDay(o.epochDay))} $from 에서 나감 · 어디로 갔나요?", fontSize = 12.sp, color = DonColors.InkSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        grows.forEach { a -> FilterChip(selected = false, onClick = { onConfirm(q, a.id, true) }, label = { Text("저축 · ${a.name}", fontSize = 12.sp) }) }
                        if (grows.isEmpty()) FilterChip(selected = false, onClick = { onConfirm(q, null, true) }, label = { Text("저축했어요", fontSize = 12.sp) })
                        accounts.filter { !it.hidden && it.id != o.accountId && enumOr(it.type, AccountType.ETC).let { t -> t == AccountType.BANK || t == AccountType.CASH } }
                            .forEach { a -> FilterChip(selected = false, onClick = { onConfirm(q, a.id, false) }, label = { Text(a.name, fontSize = 12.sp) }) }
                        FilterChip(selected = false, onClick = { onConfirm(q, null, false) }, label = { Text("내 다른 통장 (앱에 없음)", fontSize = 12.sp) })
                        SubButton(onClick = { onDismiss(q) }, containerColor = DonColors.Surface) { Text("지출 맞아요") }
                    }
                }
            }
        }
        if (questions.size > limit) Text("그 밖에 ${questions.size - limit}건 더 — 하나씩 답하면 다음 것이 나와요", fontSize = 11.sp, color = DonColors.InkMuted)
    }
}
