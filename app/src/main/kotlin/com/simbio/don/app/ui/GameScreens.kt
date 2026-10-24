package com.simbio.don.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.data.MileState
import com.simbio.don.app.data.MileTxEntity
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.game.CardGrade
import com.simbio.don.game.Cards
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MileGrowth
import com.simbio.don.game.MockItem
import com.simbio.don.game.PriceTier
import com.simbio.don.game.PurchaseStatus
import java.time.LocalDate

/** "12,300마일" */
fun miles(n: Long): String = "%,d마일".format(n)

/**
 * 마일 지갑과 마일 키우기. 마일은 실제로 아꼈을 때만 쌓이는 게임 돈이라 원화로 바꿔 보여주지 않는다.
 */
@Composable
fun MilesScreen(state: MileState, onDeposit: (Long) -> Unit, onWithdraw: (Long) -> Unit, onBack: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    val value = amount.toLongOrNull() ?: 0
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("마일", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GameCard("지갑", icon = { com.simbio.don.app.ui.kit.PixelImage(com.simbio.don.app.R.drawable.px16_mile, 20.dp) }) {
                Text(miles(state.wallet), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(
                    "주말 결산 때 그 주 실제 예산에서 아낀 비율만큼 쌓여요. 아낀 1% = 100마일, 연속으로 지키면 최대 +50%. " +
                        "월급과 상관없이 비율로 쌓여서 누구나 같은 속도로 모여요.",
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
            }
            GameCard("마일 키우기", icon = { com.simbio.don.app.ui.kit.PixelImage(com.simbio.don.app.R.drawable.px16_grow, 20.dp) }) {
                Text(miles(state.grow), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("맡긴 마일은 한 주마다 0.5%씩 붙어요 (복리). 언제든 넣고 뺄 수 있어요.", fontSize = 12.sp, color = DonColors.InkSoft)
                if (state.grow > 0) {
                    Text(
                        "그대로 두면 1년 뒤 ${miles(MileGrowth.projected(state.grow, 52))}, 3년 뒤 ${miles(MileGrowth.projected(state.grow, 156))}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DonColors.GameDeep,
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(9) },
                    label = { Text("마일") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(1_000L, 5_000L, 10_000L).forEach { n -> FilterChip(selected = false, onClick = { amount = n.toString() }, label = { Text("%,d".format(n)) }) }
                    FilterChip(selected = false, onClick = { amount = state.wallet.toString() }, label = { Text("지갑 전부") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onDeposit(value); amount = "" },
                        enabled = value in 1..state.wallet,
                        containerColor = DonColors.Game,
                        modifier = Modifier.weight(1f),
                    ) { Text("넣기") }
                    SubButton(onClick = { onWithdraw(value); amount = "" }, enabled = value in 1..state.grow, modifier = Modifier.weight(1f)) { Text("빼기") }
                }
            }
            SectionCard("기록") {
                if (state.history.isEmpty()) {
                    Text("아직 기록이 없어요. 이번 주 예산을 지키면 결산 때 마일이 들어와요.", fontSize = 13.sp, color = DonColors.InkSoft)
                }
                state.history.forEach { MileRow(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MileRow(t: MileTxEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.memo.ifBlank { mileKindLabel(t.kind) }, fontSize = 14.sp, color = DonColors.Ink, maxLines = 1)
            Text(
                shortDate(LocalDate.ofEpochDay(t.epochDay)) + " · " + mileKindLabel(t.kind) + if (t.account == "GROW") " · 키우기" else "",
                fontSize = 12.sp,
                color = DonColors.InkMuted,
            )
        }
        Text(
            (if (t.amount >= 0) "+" else "−") + "%,d".format(kotlin.math.abs(t.amount)),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (t.amount >= 0) DonColors.GameDeep else DonColors.InkSoft,
        )
    }
}

private fun mileKindLabel(kind: String) = when (kind) {
    "EARN" -> "결산"
    "BONUS" -> "연속 보너스"
    "BUY" -> "사용"
    "DEPOSIT" -> "넣기"
    "WITHDRAW" -> "빼기"
    "INTEREST" -> "키움"
    else -> kind
}

// ── 카드 ────────────────────────────────────────────────────

/** 테두리 색 (값 구간) */
internal fun PriceTier.color(): Color = when (this) {
    PriceTier.BRONZE -> Color(0xFFC08A5B)
    PriceTier.SILVER -> Color(0xFFA9B2BF)
    PriceTier.GOLD -> Color(0xFFE2B23C)
    PriceTier.DIAMOND -> Color(0xFF5BC3E8)
}

/** 반짝이 (그 주 아낀 비율) */
internal fun CardGrade.foil(): Brush? = when (this) {
    CardGrade.NORMAL -> null
    CardGrade.RARE -> Brush.linearGradient(listOf(Color(0x339CC0F5), Color(0x00FFFFFF), Color(0x339CC0F5)))
    CardGrade.LEGEND -> Brush.linearGradient(listOf(Color(0x55FFD25E), Color(0x33F59BBA), Color(0x339CC0F5), Color(0x55FFD25E)))
}

/**
 * 모의로 산 물건 카드: 테두리는 값 구간(브론즈·실버·골드·다이아), 반짝이는 산 주에 실제로 아낀 비율(일반·레어·전설).
 * 아래에 "월급의 몇 %, 몇 시간 일한 돈"을 적어 실제로 샀다면 어땠을지 느끼게 한다.
 */
@Composable
fun ItemCard(item: MockItem, salary: Long, modifier: Modifier = Modifier) {
    val tier = PriceTier.of(item.price)
    val grade = item.grade ?: CardGrade.NORMAL
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(DonColors.Surface).border(3.dp, tier.color(), shape).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tier.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tier.color(), modifier = Modifier.weight(1f))
            if (grade != CardGrade.NORMAL) Badge(grade.label, if (grade == CardGrade.LEGEND) Color(0xFFD99A2E) else DonColors.Income)
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(10.dp)).background(DonColors.GameSurface)
                .let { m -> grade.foil()?.let { m.background(it) } ?: m },
            contentAlignment = Alignment.Center,
        ) { com.simbio.don.app.ui.kit.PixelImage(com.simbio.don.app.ui.kit.icon16For(item.name, item.kind), 64.dp) }
        Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
        Text(won(item.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        val share = Cards.salaryShareBps(item.price, salary)
        val hours = Cards.workHours(item.price, salary)
        if (share != null && hours != null) {
            Text(
                "월급의 ${"%.1f".format(share / 100.0)}% · ${if (hours < 1) "${(hours * 60).toInt()}분" else "${hours}시간"} 일한 돈",
                fontSize = 11.sp,
                color = DonColors.InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 카드 모음: 모의로 사서 도착한 물건들 (비운 것도 남는다 — 도감) */
@Composable
fun CardsScreen(items: List<MockItem>, salary: Long, onBack: () -> Unit) {
    val cards = items.filter { it.payload == null && it.status in setOf(PurchaseStatus.DELIVERED, PurchaseStatus.DISCARDED, PurchaseStatus.RECYCLED) }
        .sortedWith(compareByDescending<MockItem> { it.date }.thenByDescending { it.id })
    var kind by remember { mutableStateOf<ItemKind?>(null) }
    val shown = cards.filter { kind == null || it.kind == kind }
    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("카드 ${cards.size}장", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "모의로 사서 도착한 물건이 카드로 남아요. 테두리는 값, 반짝이는 산 주에 실제로 아낀 비율이에요 (10% 레어 · 25% 전설). 뽑기는 없어요.",
                fontSize = 13.sp,
                color = DonColors.InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("전체") })
                cards.map { it.kind }.distinct().forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
            if (shown.isEmpty()) {
                Text("아직 카드가 없어요. 모의로 사고 주말 결산 때 예산을 지키면 도착해요.", fontSize = 13.sp, color = DonColors.InkMuted, modifier = Modifier.padding(vertical = 24.dp))
            }
            shown.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { ItemCard(it, salary, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 집 탭의 마일 카드: 지갑·키우기 + 마일로 하는 것들 */
@Composable
fun MilesCard(state: MileState, cardCount: Int, onMiles: () -> Unit, onFurniture: () -> Unit, onCards: () -> Unit, onDress: () -> Unit) {
    GameCard("마일", trailing = { com.simbio.don.app.ui.kit.MoreButton("기록", onMiles) }) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(miles(state.wallet), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Spacer(Modifier.width(8.dp))
            if (state.grow > 0) Text("키우는 중 ${miles(state.grow)}", fontSize = 12.sp, color = DonColors.GameDeep, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text("실제로 아낀 비율만큼 쌓여요. 이사·가구·옷을 살 수 있어요.", fontSize = 12.sp, color = DonColors.InkSoft)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuTile("마일 키우기", com.simbio.don.app.R.drawable.px16_grow, Modifier.weight(1f), onMiles)
            MenuTile("가구점·이사", com.simbio.don.app.R.drawable.px16_move, Modifier.weight(1f), onFurniture)
            MenuTile("카드 $cardCount", com.simbio.don.app.R.drawable.px16_card, Modifier.weight(1f), onCards)
            MenuTile("옷장·뷰티샵", com.simbio.don.app.R.drawable.px16_closet, Modifier.weight(1f), onDress)
        }
    }
}

@Composable
internal fun MenuTile(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(DonColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        com.simbio.don.app.ui.kit.PixelImage(icon, 32.dp, Modifier.padding(bottom = 4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Ink, maxLines = 1)
    }
}
