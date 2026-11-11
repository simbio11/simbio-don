package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.CategoryRuleEntity
import com.simbio.don.app.data.DonRepository.AutoCheck
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.CategoryIcon
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.budget.AutoRate
import com.simbio.don.budget.Category
import com.simbio.don.notify.AccountHints
import java.time.LocalDate

/** 가게 분류로 눌러 알려 줄 수 있는 지출 카테고리 (자주 쓰는 순) */

/**
 * 홈의 "확인해 주세요": 알림으로 적혔지만 가게 분류를 모르거나 가게 이름을 못 뽑은 결제.
 * 분류를 눌러 알려 주면 그 가게는 기억해서 다음부터 저절로 적혀요 — 처음엔 몇 번 묻지만 갈수록 줄어요.
 * [rate]가 있으면 위에 "알아서 기록된 비율"을 같이 보인다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckCard(
    checks: List<AutoCheck>,
    rate: AutoRate,
    onConfirm: (EntryEntity, Category) -> Unit,
    onOpenEntry: (EntryEntity) -> Unit,
    onLearned: () -> Unit,
    modifier: Modifier = Modifier,
    limit: Int = 2,
) {
    // 아무것도 안 물을 땐 한 줄만 (자동 기록률 → 학습 화면으로)
    val ask = checks.filter { it.needsCategory || it.needsName }
    if (rate.total == 0 && ask.isEmpty()) return
    Column(modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onLearned), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (ask.isEmpty()) "자동 기록 ${rate.percent}%" else "확인해 주세요 · ${ask.size}건", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (ask.isEmpty()) DonColors.Good else DonColors.Ink)
                Text(rate.label, fontSize = 11.sp, color = DonColors.InkMuted)
            }
            Text("배운 것 ›", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Game)
        }
        ask.take(limit).forEach { c ->
            val e = c.entry
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${shortDate(LocalDate.ofEpochDay(e.epochDay))} · ${won(e.amount)}", fontSize = 12.sp, color = DonColors.InkSoft)
                }
                if (c.needsName) {
                    Text("가게 이름을 알림에서 못 찾았어요. 이름을 적어 주면 그 앱은 앞으로 같은 자리에서 찾아요.", fontSize = 11.sp, color = DonColors.InkMuted)
                    SubButton(onClick = { onOpenEntry(e) }, containerColor = DonColors.SurfaceAlt) { Text("이름 적기", fontSize = 12.sp) }
                } else {
                    Text("무슨 소비예요? 알려 주면 이 가게는 다음부터 저절로 분류해요.", fontSize = 11.sp, color = DonColors.InkMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 내가 만든 분류·반려동물까지 (기타는 맨 끝)
                        (Category.spending.filter { it != Category.ETC } + Category.ETC).forEach { cat ->
                            FilterChip(selected = false, onClick = { onConfirm(e, cat) }, label = { Text(cat.label, fontSize = 12.sp) })
                        }
                    }
                }
            }
        }
        if (ask.size > limit) Text("외 ${ask.size - limit}건 더 — 하나씩 알려 주면 다음 것이 나와요", fontSize = 11.sp, color = DonColors.InkMuted)
    }
}

/**
 * 자동 기록 학습 화면: 얼마나 알아서 되는지, 어느 앱·카드·계좌가 어느 자산으로 이어졌는지, 배운 가게 분류.
 * 잘못 배운 것은 지우면 다음부터 다시 물어본다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoLearnScreen(
    rate: AutoRate,
    accounts: List<AccountEntity>,
    appName: (String) -> String,
    rules: List<CategoryRuleEntity>,
    onForgetRule: (String) -> Unit,
    onUnlinkApp: (Long) -> Unit,
    onForgetTail: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("자동 기록 학습", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${rate.percent}%", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (rate.percent >= 90) DonColors.Good else DonColors.Ink)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(DonColors.Line)) {
                    Box(Modifier.fillMaxWidth(rate.percent / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(DonColors.Good))
                }
                Text(rate.label, fontSize = 13.sp, color = DonColors.InkSoft)
                Text(
                    "알림에서 가게 이름과 금액을 읽고, 가게 이름으로 분류를, 앱·카드 끝자리로 어느 통장인지 정해요. " +
                        "처음엔 모르는 것을 물어보지만, 한 번 알려 준 가게·앱·카드는 기억해서 다음부터는 묻지 않아요.",
                    fontSize = 12.sp,
                    color = DonColors.InkMuted,
                )
            }

            // 이은 앱·카드·계좌
            val linked = accounts.filter { !it.hidden && (it.packageName != null || AccountHints.parse(it.tails).isNotEmpty()) }
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("어느 앱·카드가 어느 통장인지", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                if (linked.isEmpty()) Text("아직 배운 게 없어요. \"어느 통장이에요?\"에 한 번 답하면 여기에 쌓여요.", fontSize = 12.sp, color = DonColors.InkMuted)
                linked.forEach { a ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            a.packageName?.let { FilterChip(selected = true, onClick = { onUnlinkApp(a.id) }, label = { Text("${appName(it)}  ✕", fontSize = 12.sp) }) }
                            AccountHints.parse(a.tails).forEach { t ->
                                FilterChip(selected = true, onClick = { onForgetTail(a.id, t) }, label = { Text("${AccountHints.label(t)}  ✕", fontSize = 12.sp) })
                            }
                        }
                    }
                }
                if (linked.isNotEmpty()) Text("누르면 잊어요. 다음에 그 결제가 오면 다시 물어봐요.", fontSize = 11.sp, color = DonColors.InkMuted)
            }

            // 배운 가게
            Column(Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("배운 가게 ${rules.size}곳", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                if (rules.isEmpty()) Text("가게 분류를 알려 주면 여기에 쌓여요.", fontSize = 12.sp, color = DonColors.InkMuted)
                rules.sortedBy { it.labelKey }.forEach { r ->
                    val c = Category.of(r.category)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryIcon(c, 26.dp)
                        Text(r.labelKey, fontSize = 14.sp, color = DonColors.Ink, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(c.label, fontSize = 12.sp, color = DonColors.InkSoft)
                        Text("✕", fontSize = 14.sp, color = DonColors.InkMuted, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onForgetRule(r.labelKey) }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}
