package com.simbio.don.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.simbio.don.budget.WeekResult
import com.simbio.don.budget.headline
import com.simbio.don.game.MockItem
import com.simbio.don.game.WeeklySettlement

/**
 * 주간 결산. 실제 결과를 먼저 보여주고, 그 주 모의 구매를 배송하거나
 * 초과액만큼 반품할 물건을 고르게 한다. 등급·마일리지는 M2.
 */
@Composable
fun SettlementScreen(
    result: WeekResult,
    mockItems: List<MockItem>,
    onDone: (returnIds: Set<Long>) -> Unit,
    onBack: () -> Unit,
) {
    val week = WeeklySettlement.shippingIn(mockItems, result.week.start, result.week.end)
    val mustReturn = result.overspent > 0 && week.isNotEmpty()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val need = minOf(result.overspent, week.sumOf { it.price })
    val picked = week.filter { it.id in selected }.sumOf { it.price }
    val enough = WeeklySettlement.isEnough(week, selected, result.overspent)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 나중에") }
        Text("지난주 결산", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("${shortDate(result.week.start)} ~ ${shortDate(result.week.end)} · ${result.week.days}일", fontSize = 13.sp)

        RealCard("실제 내 돈") {
            Text(
                result.headline(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (result.overspent > 0) DonColors.Warn else DonColors.Good,
            )
            Spacer(Modifier.height(8.dp))
            Line("기본 주 예산", won(result.budget))
            Line("실제 쓴 돈", won(result.spent))
            if (result.carryIn != 0L) Line("지난주에서 넘어온 돈", won(result.carryIn))
            Line("다음 주로 넘어가는 돈", won(result.carryOut), bold = true)
            if (result.overspent > 0 && result.carryIn > 0) {
                Text("넘어온 돈을 쓰면 기본 예산 기준으로는 초과로 쳐요.", fontSize = 12.sp)
            }
        }

        GameCard("게임 · 이번 주 모의 쇼핑") {
            if (week.isEmpty()) {
                Text("이번 주엔 모의로 산 게 없어요.", fontSize = 13.sp)
            } else {
                Text("참은 금액 ${won(WeeklySettlement.resisted(week))}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Game)
                Text("모의로 쓴 돈은 전부 돌려받아요.", fontSize = 12.sp, color = ChartColors.InkSecondary)
                Spacer(Modifier.height(8.dp))
                if (mustReturn) {
                    Text("⚠ 예산을 ${won(result.overspent)} 넘겨서 그만큼 반품해야 해요. 반품할 물건을 골라주세요.", fontSize = 13.sp, color = DonColors.Warn)
                    Text("고른 금액 ${won(picked)} / 필요 ${won(need)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("📦 예산을 지켜서 전부 집에 도착해요!", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                week.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth()
                            .let { if (mustReturn) it.clickable { selected = if (m.id in selected) selected - m.id else selected + m.id } else it }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mustReturn) Checkbox(checked = m.id in selected, onCheckedChange = null)
                        ItemIcon(m.kind, 24.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(m.name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            if (mustReturn && m.id in selected) "반품" else won(m.price),
                            fontSize = 14.sp,
                            color = if (mustReturn && m.id in selected) DonColors.Warn else ChartColors.InkPrimary,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onDone(selected) },
            enabled = !mustReturn || enough,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (mustReturn) "반품하고 결산 완료" else "결산 완료") }
    }
}
