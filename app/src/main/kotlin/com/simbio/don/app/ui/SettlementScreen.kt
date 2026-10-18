package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.popIn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.simbio.don.budget.WeekResult
import com.simbio.don.budget.headline
import com.simbio.don.game.MockItem
import com.simbio.don.game.Mood
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
    /** 이번 주 전까지 이어진 연속 기록 */
    streakBefore: Int = 0,
    savedTotal: Long = 0,
) {
    val week = WeeklySettlement.shippingIn(mockItems, result.week.start, result.week.end)
    val mustReturn = result.overspent > 0 && week.isNotEmpty()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val need = minOf(result.overspent, week.sumOf { it.price })
    val picked = week.filter { it.id in selected }.sumOf { it.price }
    val enough = WeeklySettlement.isEnough(week, selected, result.overspent)

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("지난주 결산", onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 결과 한 줄 (지켰으면 초록, 넘겼으면 빨강)
            val over = result.overspent > 0
            Box(Modifier.fillMaxWidth().card()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.popIn().size(88.dp).clip(CircleShape).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
                        CharacterSprite(if (over) Mood.CHEER else Mood.JOYFUL, 60.dp)
                    }
                    Badge(if (over) "예산 초과" else "예산 지킴", if (over) DonColors.Warn else DonColors.Good)
                    if (!over && result.savedVsBudget > 0) {
                        // 아낀 돈이 0부터 올라간다
                        CountUpWon(result.savedVsBudget, 32.sp, DonColors.Good, prefix = "+", durationMs = 1_400)
                    }
                    Text(
                        result.headline(),
                        fontSize = if (over) 22.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DonColors.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Text("${shortDate(result.week.start)} ~ ${shortDate(result.week.end)} · ${result.week.days}일", fontSize = 13.sp, color = DonColors.InkMuted)
                    if (!over) StreakBadge(streakBefore + 1)
                    else if (streakBefore > 0) Text("${streakBefore}주 연속 기록은 여기서 멈춰요. 다음 주에 다시 시작해요!", fontSize = 12.sp, color = DonColors.InkSoft)
                    if (savedTotal > 0) Text("지금까지 모은 돈 ${won(savedTotal)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Real)
                }
                if (!over) Confetti(Modifier.matchParentSize())
            }

            RealCard("실제 내 돈") {
                Line("기본 주 예산", won(result.budget))
                Line("실제 쓴 돈", won(result.spent))
                if (result.carryIn != 0L) Line("지난주에서 넘어온 돈", won(result.carryIn))
                Divider(Modifier.padding(vertical = 4.dp))
                Line("다음 주로 넘어가는 돈", won(result.carryOut), bold = true)
                if (result.overspent > 0 && result.carryIn > 0) {
                    Text("넘어온 돈을 쓰면 기본 예산 기준으로는 초과로 쳐요.", fontSize = 12.sp, color = DonColors.InkMuted)
                }
            }

            // 이번 결산으로 받을 마일 (예산을 넘기면 0)
            val earned = com.simbio.don.game.Miles.forWeek(result.savedBps, result.overspent, result.week.days, if (over) 0 else streakBefore + 1)
            GameCard("게임 · 마일") {
                if (earned.total > 0) {
                    Text("+${miles(earned.total)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
                    Text(
                        "아낀 ${result.savedBps / 100}% × 100" + (if (result.week.days != 7) " · ${result.week.days}일 주" else "") +
                            (if (earned.bonus > 0) " · 연속 보너스 +${earned.bonusBps / 100}%" else ""),
                        fontSize = 12.sp,
                        color = DonColors.InkSoft,
                    )
                } else {
                    Text(if (over) "예산을 넘긴 주는 마일이 없어요." else "아낀 돈이 없어서 이번 주 마일은 없어요.", fontSize = 13.sp, color = DonColors.InkSoft)
                }
            }

            GameCard("게임 · 이번 주 모의 쇼핑") {
                if (week.isEmpty()) {
                    Text("이번 주엔 모의로 산 게 없어요.", fontSize = 13.sp, color = DonColors.InkSoft)
                } else {
                    Text("참은 금액 ${won(WeeklySettlement.resisted(week))}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
                    Text("모의로 쓴 돈은 전부 돌려받아요.", fontSize = 12.sp, color = DonColors.InkMuted)
                    Spacer(Modifier.height(4.dp))
                    if (mustReturn) {
                        WarnLine("예산을 ${won(result.overspent)} 넘겨서 그만큼 반품해야 해요. 반품할 물건을 골라주세요.")
                        Text("고른 금액 ${won(picked)} / 필요 ${won(need)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(UiIcon.TRUCK, 20.dp, DonColors.Game)
                            Text("예산을 지켜서 전부 집에 도착해요!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        }
                    }
                    week.forEach { m ->
                        val returning = mustReturn && m.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(DonColors.Surface)
                                .let { if (mustReturn) it.clickable { selected = if (m.id in selected) selected - m.id else selected + m.id } else it }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (mustReturn) Checkbox(checked = m.id in selected, onCheckedChange = null)
                            ItemThumb(m.kind, 44.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(m.name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, color = DonColors.Ink)
                            Text(
                                if (returning) "반품" else won(m.price),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (returning) DonColors.Warn else DonColors.Ink,
                            )
                        }
                    }
                }
            }
        }
        BottomBar {
            val haptic = LocalHapticFeedback.current
            var sent by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    sent = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone(selected)
                },
                enabled = !sent && (!mustReturn || enough),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (mustReturn) "반품하고 결산 완료" else "결산 완료") }
        }
    }
}
