package com.simbio.don.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.PixelImage
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.icon16
import com.simbio.don.app.ui.kit.icon16For
import com.simbio.don.app.ui.kit.popIn
import com.simbio.don.budget.WeekResult
import com.simbio.don.budget.headline
import com.simbio.don.game.CardGrade
import com.simbio.don.game.Catalog
import com.simbio.don.game.Dex
import com.simbio.don.game.Miles
import com.simbio.don.game.MiniGameRewards
import com.simbio.don.game.MockItem
import com.simbio.don.game.Mood
import com.simbio.don.game.PriceTier
import com.simbio.don.game.Sets
import com.simbio.don.game.WeeklySettlement

/** 결산쇼 순서 */
private enum class ShowStep { RESULT, SHOPPING, MILES, DEX }

/**
 * 주간 결산쇼. 결과 → 이번 주 모의 쇼핑(도착하는 물건 카드가 한 장씩 뒤집힘, 넘겼으면 반품 고르기)
 * → 받을 마일 → 새로 채운 도감·세트 순으로 "다음"을 눌러 보고, 마지막에 결산을 마친다.
 * 도착·카드 반짝이·마일은 결산 때 정해지는 값을 미리 보여준다 (결산 완료를 눌러야 실제로 기록된다).
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
    val over = result.overspent > 0
    val mustReturn = over && week.isNotEmpty()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val enough = WeeklySettlement.isEnough(week, selected, result.overspent)
    val arriving = week.filter { it.id !in selected && (!mustReturn || enough) }
    val earned = Miles.forWeek(result.savedBps, result.overspent, result.week.days, if (over) 0 else streakBefore + 1)

    // 이번 결산으로 새로 채울 도감 칸·세트
    val gotBefore = remember(mockItems) { Dex.gotIds(mockItems) }
    val newIds = Dex.newIds(mockItems, arriving)
    val newSets = Sets.newlyCompleted(gotBefore, gotBefore + newIds)
    val steps = listOfNotNull(ShowStep.RESULT, ShowStep.SHOPPING, ShowStep.MILES, ShowStep.DEX.takeIf { newIds.isNotEmpty() })
    var step by remember { mutableIntStateOf(0) }
    val last = step >= steps.lastIndex
    val blocked = steps[step.coerceAtMost(steps.lastIndex)] == ShowStep.SHOPPING && mustReturn && !enough
    val scroll = rememberScrollState()
    LaunchedEffect(step) { if (step > 0) scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("지난주 결산", onBack, trailing = {
            if (!last) TextButton(onClick = { step = steps.lastIndex }, modifier = Modifier.padding(end = 8.dp)) { Text("건너뛰기") }
        })
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            steps.take(step + 1).forEach { s ->
                when (s) {
                    ShowStep.RESULT -> ResultPart(result, over, streakBefore, savedTotal)
                    ShowStep.SHOPPING -> ShoppingPart(result, week, mustReturn, selected, arriving, onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    })
                    ShowStep.MILES -> MilesPart(result, earned, over)
                    ShowStep.DEX -> DexPart(newIds, newSets, gotBefore.size + newIds.size)
                }
            }
        }
        BottomBar {
            val haptic = LocalHapticFeedback.current
            var sent by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (!last) {
                        step++
                    } else {
                        sent = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDone(selected)
                    }
                },
                enabled = !sent && !blocked && (!last || !mustReturn || enough),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !last -> if (blocked) "반품할 물건을 골라주세요" else "다음"
                        mustReturn -> "반품하고 결산 완료"
                        else -> "결산 완료"
                    },
                )
            }
        }
    }
}

// ── 1. 결과 ────────────────────────────────────────────────

@Composable
private fun ResultPart(result: WeekResult, over: Boolean, streakBefore: Int, savedTotal: Long) {
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
}

// ── 2. 이번 주 모의 쇼핑 ───────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShoppingPart(
    result: WeekResult,
    week: List<MockItem>,
    mustReturn: Boolean,
    selected: Set<Long>,
    arriving: List<MockItem>,
    onToggle: (Long) -> Unit,
) {
    GameCard("게임 · 이번 주 모의 쇼핑", modifier = Modifier.popIn()) {
        if (week.isEmpty()) {
            Text("이번 주엔 모의로 산 게 없어요.", fontSize = 13.sp, color = DonColors.InkSoft)
            return@GameCard
        }
        Text("참은 금액 ${won(WeeklySettlement.resisted(week))}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
        Text("모의로 쓴 돈은 전부 돌려받아요.", fontSize = 12.sp, color = DonColors.InkMuted)
        Spacer(Modifier.height(4.dp))
        if (mustReturn) {
            val need = minOf(result.overspent, week.sumOf { it.price })
            val picked = week.filter { it.id in selected }.sumOf { it.price }
            WarnLine("예산을 ${won(result.overspent)} 넘겨서 그만큼 반품해야 해요. 반품할 물건을 골라주세요.")
            Text("고른 금액 ${won(picked)} / 필요 ${won(need)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            week.forEach { m ->
                val returning = m.id in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.Surface)
                        .clickable { onToggle(m.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = returning, onCheckedChange = null)
                    ItemThumb(m.kind, 44.dp, name = m.name)
                    Spacer(Modifier.size(10.dp))
                    Text(m.name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, color = DonColors.Ink)
                    Text(if (returning) "반품" else won(m.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (returning) DonColors.Warn else DonColors.Ink)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(UiIcon.TRUCK, 20.dp, DonColors.Game)
                Text("예산을 지켜서 전부 집에 도착해요!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            }
            val grade = CardGrade.of(result.savedBps)
            Text(
                "카드 반짝이: ${grade.label}" + if (grade == CardGrade.NORMAL) " (10% 아끼면 레어, 25%면 전설)" else " — 이번 주 ${result.savedBps / 100}% 아꼈어요",
                fontSize = 12.sp,
                color = DonColors.InkSoft,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                arriving.forEachIndexed { i, m -> FlipCard(m, grade, i) }
            }
        }
    }
}

/** 도착하는 물건 카드: 상자 뒷면에서 한 장씩 뒤집힌다 */
@Composable
private fun FlipCard(item: MockItem, grade: CardGrade, index: Int, width: Dp = 76.dp) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250L + index * 220L)
        angle.animateTo(180f, tween(420, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current.density
    val tier = PriceTier.of(item.price)
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.width(width).height(width * 1.3f)
            .graphicsLayer {
                rotationY = angle.value
                cameraDistance = 12 * density
            }
            .clip(shape)
            .background(if (angle.value > 90f) DonColors.Surface else DonColors.Game)
            .border(if (angle.value > 90f) 3.dp else 0.dp, tier.color(), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (angle.value > 90f) {
            Column(
                Modifier.graphicsLayer { rotationY = 180f }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(width * 0.62f).clip(RoundedCornerShape(6.dp)).background(DonColors.GameSurface)
                        .let { m -> grade.foil()?.let { m.background(it) } ?: m },
                    contentAlignment = Alignment.Center,
                ) { PixelImage(icon16For(item.name, item.kind), 40.dp) }
                Text(item.name, fontSize = 10.sp, color = DonColors.Ink, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                Text(tier.label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tier.color())
            }
        } else {
            PixelImage(com.simbio.don.app.R.drawable.px16_card, 36.dp)
        }
    }
}

// ── 3. 마일 ────────────────────────────────────────────────

@Composable
private fun MilesPart(result: WeekResult, earned: Miles.Earned, over: Boolean) {
    GameCard("게임 · 마일", modifier = Modifier.popIn(), icon = { PixelImage(com.simbio.don.app.R.drawable.px16_mile, 20.dp) }) {
        if (earned.total > 0) {
            CountUpText(earned.total, 28.sp, DonColors.GameDeep) { "+" + miles(it) }
            Text(
                "아낀 ${result.savedBps / 100}% × 100" + (if (result.week.days != 7) " · ${result.week.days}일 주" else "") +
                    (if (earned.bonus > 0) " · 연속 보너스 +${earned.bonusBps / 100}%" else ""),
                fontSize = 12.sp,
                color = DonColors.InkSoft,
            )
            Text(
                "이번 주 미니게임으로 ${miles(MiniGameRewards.weeklyCap(earned.total))}까지 더 받을 수 있어요",
                fontSize = 12.sp,
                color = DonColors.GameDeep,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(if (over) "예산을 넘긴 주는 마일이 없어요. 다음 주에 다시 모아요!" else "아낀 돈이 없어서 이번 주 마일은 없어요.", fontSize = 13.sp, color = DonColors.InkSoft)
        }
    }
}

// ── 4. 도감·세트 ───────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DexPart(newIds: Set<String>, newSets: List<com.simbio.don.game.ItemSet>, total: Int) {
    Box(Modifier.fillMaxWidth().popIn()) {
        GameCard("게임 · 도감") {
            Text("도감에 새로 ${newIds.size}칸!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text("모은 칸 $total/${Catalog.products.size}", fontSize = 12.sp, color = DonColors.InkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                newIds.mapNotNull { Catalog.byId(it) }.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(DonColors.Surface), contentAlignment = Alignment.Center) {
                            PixelImage(p.icon16(), 40.dp)
                        }
                        Text(p.name, fontSize = 10.sp, color = DonColors.Ink, maxLines = 1)
                    }
                }
            }
            newSets.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.Surface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(UiIcon.TROPHY, 28.dp, DonColors.Highlight)
                    Column {
                        Text("「${s.name}」 세트 완성!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        Text("칭호 「${s.title}」를 받았어요. 도감에서 달 수 있어요.", fontSize = 12.sp, color = DonColors.InkSoft)
                    }
                }
            }
        }
        if (newSets.isNotEmpty()) Confetti(Modifier.matchParentSize(), key = "sets")
    }
}

/** 0부터 올라가는 숫자 ([format]으로 글자) */
@Composable
private fun CountUpText(value: Long, fontSize: TextUnit, color: androidx.compose.ui.graphics.Color, durationMs: Int = 1_000, format: (Long) -> String) {
    val p = remember(value) { Animatable(0f) }
    LaunchedEffect(value) { p.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    val shown = if (p.value >= 1f) value else (value * p.value).toLong()
    Text(format(shown), fontSize = fontSize, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
}
