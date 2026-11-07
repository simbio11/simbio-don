package com.simbio.don.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import java.time.LocalDate

/** 고를 수 있는 자산 순서: 통장 → 카드 → 현금 → 기타 */
private val PICK_ORDER = listOf(AccountType.BANK, AccountType.CARD, AccountType.CASH, AccountType.ETC)

/**
 * "어느 통장에서 나갔어요?" — 앱이 어디서 나갔는지 모르거나 짐작만 한 결제·입금을 자산 목록에서 골라 정한다.
 * 고르면 바로 그 자산 잔액에 반영된다. [remember]를 켜 두면 그 알림 앱은 앞으로 고른 자산으로 적는다.
 *
 * [limit]보다 많으면 나머지는 접어 두고 "N건 더" 만 보인다 ([onMore]가 있으면 눌러 자산 탭으로).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountQuestionCard(
    questions: List<EntryEntity>,
    accounts: List<AccountEntity>,
    onChoose: (entry: EntryEntity, accountId: Long, rememberApp: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 3,
    onMore: (() -> Unit)? = null,
) {
    if (questions.isEmpty()) return
    val choices = accounts.filter { !it.hidden && AccountType.entries.firstOrNull { t -> t.name == it.type } in PICK_ORDER }
        .sortedWith(compareBy({ PICK_ORDER.indexOfFirst { t -> t.name == it.type } }, { it.sortOrder }))
    if (choices.isEmpty()) return
    var rememberApp by remember { mutableStateOf(true) }
    val shown = questions.take(limit)
    Column(modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("어느 통장이에요?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
            Text("${questions.size}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DonColors.Warn)
        }
        Text(
            "알림만으로는 어느 통장·카드에서 나갔는지 몰라요. 골라 주면 그 잔액에 바로 반영돼요.",
            fontSize = 12.sp,
            color = DonColors.InkMuted,
        )
        shown.forEach { e ->
            val income = e.type == EntryType.INCOME.name || e.type == EntryType.REFUND.name
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcon(Category.of(e.category), 26.dp)
                    Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(
                        "${shortDate(LocalDate.ofEpochDay(e.epochDay))} · ${if (income) "+" else "−"}${won(e.amount)}",
                        fontSize = 12.sp,
                        color = if (income) DonColors.Good else DonColors.InkSoft,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { a ->
                        FilterChip(selected = false, onClick = { onChoose(e, a.id, rememberApp && e.packageName != null) }, label = { Text(a.name, fontSize = 12.sp) })
                    }
                }
            }
        }
        if (questions.size > shown.size) {
            Text(
                "외 ${questions.size - shown.size}건 더" + if (onMore != null) " · 자산 탭에서 모두 보기" else "",
                fontSize = 12.sp,
                fontWeight = if (onMore != null) FontWeight.Bold else FontWeight.Normal,
                color = if (onMore != null) DonColors.Game else DonColors.InkMuted,
                modifier = if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier,
            )
        }
        if (shown.any { it.packageName != null }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 앱의 알림은 앞으로 고른 통장으로", fontSize = 12.sp, color = DonColors.InkSoft, modifier = Modifier.weight(1f))
                Switch(checked = rememberApp, onCheckedChange = { rememberApp = it })
            }
        }
    }
}
