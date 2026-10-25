package com.simbio.don.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.Wealth
import com.simbio.don.app.data.WealthPoint
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.popIn

/** 불어난 돈 글자 ("+320,000원 (+3.8%)") */
fun gainText(gain: Long, bps: Int): String =
    (if (gain >= 0) "+" else "−") + won(kotlin.math.abs(gain)) + " (" + (if (bps >= 0) "+" else "−") + "%.1f".format(kotlin.math.abs(bps) / 100.0) + "%)"

fun gainColor(gain: Long): Color = when {
    gain > 0 -> DonColors.Expense
    gain < 0 -> DonColors.Income
    else -> DonColors.InkMuted
}

/**
 * 저축·투자 카드 (자산 탭): 지금 금액 · 넣은 돈 · 불어난 돈, 달마다 그래프, 자산 점검.
 * 주식처럼 오르면 빨강, 내리면 파랑 (한국 증권 앱 관례).
 */
@Composable
fun WealthCard(w: Wealth, due: Boolean, onCheck: () -> Unit, onAdd: () -> Unit) {
    RealCard("저축·투자", icon = { com.simbio.don.app.ui.kit.Icon(UiIcon.SAVINGS, 18.dp, DonColors.Good) }) {
        if (w.pots.isEmpty()) {
            Text("적금·예금·주식처럼 오래 모으는 돈을 넣어 두면, 월급날마다 넣은 돈과 지금 금액을 적어 얼마나 불었는지 볼 수 있어요. 여행처럼 쓰려고 모으는 돈은 홈의 목표에서 따로 모아요.", fontSize = 13.sp, color = DonColors.InkSoft)
            SmallButton(onClick = onAdd) { Text("저축 통장 추가") }
            return@RealCard
        }
        CountUpWon(w.total, 30.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("넣은 돈 ${won(w.principal)}", fontSize = 13.sp, color = DonColors.InkSoft)
            if (w.gain != 0L) Text(gainText(w.gain, w.gainBps), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = gainColor(w.gain))
        }
        if (w.history.size >= 2) {
            Spacer(Modifier.height(6.dp))
            WealthChart(w.history, Modifier.fillMaxWidth().height(110.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(DonColors.Good, "지금 금액")
                Legend(DonColors.InkFaint, "넣은 돈", dashed = true)
            }
        }
        Spacer(Modifier.height(4.dp))
        w.pots.forEach { b -> PotRow(b) }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onCheck, modifier = Modifier.fillMaxWidth(), containerColor = if (due) DonColors.Real else DonColors.Good) {
            Text(if (due) "이번 달 자산 점검하기" else "자산 점검")
        }
        Text(
            w.lastCheck?.let { "마지막 점검 ${shortDate(it)} · 월급날마다 넣은 돈과 지금 금액을 적어요" } ?: "월급날마다 넣은 돈과 지금 금액을 적어요",
            fontSize = 11.sp,
            color = DonColors.InkMuted,
        )
    }
}

@Composable
private fun PotRow(b: AccountBalance) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(b.type.icon(), b.type.tint(), 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(b.account.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink)
            Text("넣은 돈 ${won(b.principal)}", fontSize = 11.sp, color = DonColors.InkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(won(b.balance), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            if (b.gain != 0L) {
                val bps = if (b.principal > 0) (b.gain * 10_000 / b.principal).toInt() else 0
                Text(gainText(b.gain, bps), fontSize = 11.sp, color = gainColor(b.gain))
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp, 8.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = DonColors.InkMuted)
    }
}

/** 달마다 저축·투자: 지금 금액(채운 선)과 넣은 돈(점선) */
@Composable
fun WealthChart(points: List<WealthPoint>, modifier: Modifier = Modifier) {
    val good = DonColors.Good
    val faint = DonColors.InkFaint
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val max = points.maxOf { maxOf(it.value, it.principal) }.coerceAtLeast(1)
        val min = (points.minOf { minOf(it.value, it.principal) } * 0.9).toLong()
        fun x(i: Int) = size.width * i / (points.size - 1)
        fun y(v: Long) = size.height - size.height * (v - min).toFloat() / (max - min).coerceAtLeast(1)
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.value)) else lineTo(x(i), y(p.value)) } }
        val area = Path().apply {
            addPath(line)
            lineTo(x(points.lastIndex), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(good.copy(alpha = 0.25f), good.copy(alpha = 0f))))
        val principal = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.principal)) else lineTo(x(i), y(p.principal)) } }
        drawPath(principal, faint, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        drawPath(line, good, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(good, 5.dp.toPx(), Offset(x(points.lastIndex), y(points.last().value)))
    }
}

/**
 * 월급날 자산 점검: 저축·투자 통장마다 이번에 넣은 돈과 넣은 뒤 지금 금액(평가액)을 적는다.
 * 넣은 돈은 고른 통장에서 옮긴 이체로 가계부에 남고, 지금 금액과의 차이가 그동안 불어난 돈이다.
 */
@Composable
fun AssetCheckScreen(
    wealth: Wealth,
    sources: List<AccountBalance>,
    plannedSavings: Long,
    onSave: (inputs: List<DonRepository.AssetCheckInput>, fromAccountId: Long?) -> Unit,
    onBack: () -> Unit,
) {
    val added = remember { mutableStateMapOf<Long, String>() }
    val values = remember { mutableStateMapOf<Long, String>() }
    var from by remember { mutableStateOf(sources.firstOrNull { it.type == AccountType.BANK }?.account?.id ?: sources.firstOrNull()?.account?.id) }
    // 점검을 마치면 (불어난 돈, 저축·투자) — 저장하면 위 금액들이 바뀌므로 그때 값으로 둔다
    var done by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    // 처음엔 매달 먼저 떼는 저축을 첫 통장에 넣은 것으로
    remember(wealth.pots.size) {
        wealth.pots.firstOrNull()?.let { if (plannedSavings > 0 && added[it.account.id] == null) added[it.account.id] = plannedSavings.toString() }
        true
    }
    fun addedOf(b: AccountBalance) = added[b.account.id]?.toLongOrNull() ?: 0
    fun valueOf(b: AccountBalance) = values[b.account.id]?.toLongOrNull() ?: (b.balance + addedOf(b))
    val before = wealth.total
    val addedSum = wealth.pots.sumOf { addedOf(it) }
    val after = wealth.pots.sumOf { valueOf(it) }
    val change = after - before - addedSum

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("자산 점검", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            done?.let { (gain, total) ->
                Box(Modifier.fillMaxWidth().popIn()) {
                    Column(
                        Modifier.fillMaxWidth().card(DonColors.GoodSurface).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("점검했어요!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Text(
                            if (gain >= 0) "지난번보다 ${won(gain)} 불어났어요" else "지난번보다 ${won(-gain)} 줄었어요. 길게 보면 괜찮아요.",
                            fontSize = 15.sp,
                            color = gainColor(gain),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text("저축·투자 ${won(total)}", fontSize = 14.sp, color = DonColors.InkSoft)
                    }
                    if (gain > 0) Confetti(Modifier.matchParentSize())
                }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
                return@Column
            }
            Text(
                "월급날 저축 통장에 돈을 옮겼다면 넣은 돈을, 주식처럼 값이 바뀌는 건 지금 금액을 적어요. " +
                    "비워 두면 지난 금액 + 넣은 돈으로 봐요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            if (sources.isNotEmpty()) {
                FormCard("어디서 옮겼어요?") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        sources.forEach { s -> FilterChip(selected = from == s.account.id, onClick = { from = s.account.id }, label = { Text(s.account.name) }) }
                        FilterChip(selected = from == null, onClick = { from = null }, label = { Text("기록 안 함") })
                    }
                }
            }
            wealth.pots.forEach { b ->
                FormCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(b.type.icon(), b.type.tint(), 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.account.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                            Text(
                                "지난 금액 ${won(b.balance)}" + (b.checkedAt?.let { " (${shortDate(it)})" } ?: ""),
                                fontSize = 12.sp,
                                color = DonColors.InkMuted,
                            )
                        }
                        Badge(b.type.label, b.type.tint())
                    }
                    MoneyField("이번에 넣은 돈", added[b.account.id] ?: "", { added[b.account.id] = it })
                    MoneyField(
                        if (b.type == AccountType.INVEST) "넣은 뒤 지금 금액 (평가액)" else "넣은 뒤 지금 잔액",
                        values[b.account.id] ?: "",
                        { values[b.account.id] = it },
                    )
                    val diff = valueOf(b) - b.balance - addedOf(b)
                    if (values[b.account.id] != null && diff != 0L) {
                        Text(
                            (if (diff > 0) "불어난 돈 +" else "줄어든 돈 −") + won(kotlin.math.abs(diff)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = gainColor(diff),
                        )
                    }
                }
            }
            RealCard("점검하면") {
                Line("지금까지", won(before))
                Line("이번에 넣은 돈", "+${won(addedSum)}")
                Line(if (change >= 0) "불어난 돈" else "줄어든 돈", (if (change >= 0) "+" else "−") + won(kotlin.math.abs(change)))
                Line("저축·투자", won(after), bold = true)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (done == null) {
            BottomBar {
                Button(
                    onClick = {
                        onSave(wealth.pots.map { DonRepository.AssetCheckInput(it.account.id, addedOf(it), valueOf(it)) }, from)
                        done = change to after
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("점검 완료") }
            }
        }
    }
}
