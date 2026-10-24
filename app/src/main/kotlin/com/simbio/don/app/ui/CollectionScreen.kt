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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.kit.BackBar
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.FilterChip
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.PixelImage
import com.simbio.don.app.ui.kit.SmallButton
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.pressable
import com.simbio.don.app.ui.kit.icon16
import com.simbio.don.game.CardGrade
import com.simbio.don.game.Catalog
import com.simbio.don.game.Dex
import com.simbio.don.game.DexEntry
import com.simbio.don.game.ItemKind
import com.simbio.don.game.ItemSet
import com.simbio.don.game.MockItem
import com.simbio.don.game.PriceTier
import com.simbio.don.game.Sets

/** 아직 못 모은 칸의 그림자 색 */
private val SHADOW = Color(0x2E2B2234)

private enum class DexTab(val label: String) { DEX("도감"), SETS("세트·칭호") }

/**
 * 도감: 돈't 몰 상품 한 칸씩, 모의로 사서 도착하면 채워진다 (비우거나 분리수거해도 남는다).
 * 세트: 네 개를 다 모으면 칭호를 받아 집에 단다. 금액이 아니라 모은 가짓수로 매긴다.
 */
@Composable
fun CollectionScreen(items: List<MockItem>, title: String, onTitle: (String) -> Unit, onMall: () -> Unit, onBack: () -> Unit) {
    val entries = remember(items) { Dex.entries(items) }
    val others = remember(items) { Dex.others(items) }
    val got = remember(entries) { entries.filter { it.got }.map { it.product.id }.toSet() }
    var tab by rememberSaveable { mutableStateOf(DexTab.DEX) }

    Column(Modifier.fillMaxSize().background(DonColors.Bg)) {
        BackBar("도감", onBack, trailing = { Badge("${got.size}/${entries.size}", DonColors.GameDeep, modifier = Modifier.padding(end = 16.dp)) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedToggle(DexTab.entries.map { it.label }, tab.ordinal) { tab = DexTab.entries[it] }
            when (tab) {
                DexTab.DEX -> DexPage(entries, others, got.size, onMall)
                DexTab.SETS -> SetsPage(got, title, onTitle)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DexPage(entries: List<DexEntry>, others: List<com.simbio.don.game.DexOther>, count: Int, onMall: () -> Unit) {
    var kind by remember { mutableStateOf<ItemKind?>(null) }
    var open by remember { mutableStateOf<DexEntry?>(null) }
    ScreenBackHandler(open != null) { open = null }
    GameCard("모은 칸") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$count", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text(" / ${entries.size}칸", fontSize = 15.sp, color = DonColors.InkSoft, modifier = Modifier.padding(bottom = 4.dp))
        }
        ProgressLine(count.toFloat() / entries.size.coerceAtLeast(1))
        Text("모의로 사서 주말 결산 때 도착하면 칸이 채워져요. 비우거나 분리수거해도 남아요.", fontSize = 12.sp, color = DonColors.InkSoft)
        if (count == 0) SmallButton(onClick = onMall, containerColor = DonColors.Game) { Text("돈't 몰 가기") }
    }
    open?.let { DexDetail(it) { open = null } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("전체") })
        ItemKind.entries.forEach { k ->
            val inKind = entries.filter { it.product.kind == k }
            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text("${k.label} ${inKind.count { it.got }}/${inKind.size}") })
        }
    }
    entries.filter { kind == null || it.product.kind == kind }.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { e -> DexSlot(e, Modifier.weight(1f)) { open = e } }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (others.isNotEmpty() && kind == null) {
        SectionCard("내가 고른 물건") {
            Text("몰에 없는 물건 (직접 적거나 공유해서 산 것)", fontSize = 12.sp, color = DonColors.InkMuted)
            Spacer(Modifier.height(6.dp))
            others.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemThumb(o.kind, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(o.name, fontSize = 14.sp, color = DonColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
                    o.best?.takeIf { it != CardGrade.NORMAL }?.let { GradeBadge(it) }
                    Text("${o.count}번", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DexSlot(e: DexEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val tier = PriceTier.of(e.product.price)
    Column(
        modifier.clip(shape).background(if (e.got) DonColors.Surface else DonColors.Surface.copy(alpha = 0.6f))
            .border(if (e.got) 2.dp else 1.dp, if (e.got) tier.color() else DonColors.Line, shape)
            .pressable(onClick = onClick)
            .semantics { contentDescription = if (e.got) "도감 ${e.product.name}" else "도감 빈 칸" }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .background(if (e.got) DonColors.GameSurface else Color.Transparent)
                .let { m -> e.best?.foil()?.let { m.background(it) } ?: m },
            contentAlignment = Alignment.Center,
        ) {
            PixelImage(e.product.icon16(), 40.dp, silhouette = if (e.got) null else SHADOW)
        }
        Text(
            if (e.got) e.product.name else "?",
            fontSize = 10.sp,
            color = if (e.got) DonColors.Ink else DonColors.InkFaint,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DexDetail(e: DexEntry, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().card().clickable(onClick = onClose).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(DonColors.GameSurface), contentAlignment = Alignment.Center) {
            PixelImage(e.product.icon16(), 56.dp, silhouette = if (e.got) null else SHADOW)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (e.got) e.product.name else "아직 못 모은 칸", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text("${e.product.kind.label} · ${won(e.product.price)}", fontSize = 13.sp, color = DonColors.InkSoft)
            if (e.got) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.count}번 들임 · 처음 ${shortDate(e.first!!)}", fontSize = 12.sp, color = DonColors.InkMuted)
                    e.best?.takeIf { it != CardGrade.NORMAL }?.let { GradeBadge(it) }
                }
            } else {
                Text("돈't 몰에서 모의로 사고 주말 결산 때 예산을 지키면 채워져요", fontSize = 12.sp, color = DonColors.InkMuted)
            }
            Sets.all.filter { e.product.id in it.productIds }.takeIf { it.isNotEmpty() }?.let { sets ->
                Text("세트: " + sets.joinToString { it.name }, fontSize = 12.sp, color = DonColors.GameDeep, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SetsPage(got: Set<String>, title: String, onTitle: (String) -> Unit) {
    val titles = Sets.titles(got)
    GameCard("칭호") {
        Text(if (title.isEmpty()) "아직 단 칭호가 없어요" else title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
        Text("세트를 다 모으거나 도감 칸을 채우면 칭호를 받아요. 단 칭호는 집 화면에 보여요. 마일은 주지 않아요 (마일은 실제로 아꼈을 때만).", fontSize = 12.sp, color = DonColors.InkSoft)
        if (title.isNotEmpty()) SmallButton(onClick = { onTitle("") }, containerColor = DonColors.Game) { Text("칭호 떼기") }
    }
    SectionCard("도감 칸 칭호") {
        Dex.MILESTONES.forEach { (n, name) ->
            TitleRow(name, "도감 ${n}칸", minOf(got.size, n), n, name in titles, name == title) { onTitle(name) }
        }
    }
    Sets.all.forEach { set -> SetCard(set, got, title, onTitle) }
}

@Composable
private fun SetCard(set: ItemSet, got: Set<String>, title: String, onTitle: (String) -> Unit) {
    val done = Sets.done(set, got)
    val complete = done == set.productIds.size
    Column(
        Modifier.fillMaxWidth().card().let { if (complete) it.border(2.dp, DonColors.Game, RoundedCornerShape(16.dp)) else it }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(set.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("칭호 「${set.title}」", fontSize = 12.sp, color = DonColors.GameDeep)
            }
            Text("$done/${set.productIds.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (complete) DonColors.Good else DonColors.InkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            set.products.forEach { p ->
                val have = p.id in got
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (have) DonColors.GameSurface else DonColors.SurfaceAlt),
                        contentAlignment = Alignment.Center,
                    ) { PixelImage(p.icon16(), 36.dp, silhouette = if (have) null else SHADOW) }
                    Text(p.name, fontSize = 10.sp, color = if (have) DonColors.Ink else DonColors.InkMuted, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        if (complete) {
            if (title == set.title) Badge("달고 있어요", DonColors.Good)
            else SmallButton(onClick = { onTitle(set.title) }, containerColor = DonColors.Game) { Text("「${set.title}」 달기") }
        }
    }
}

@Composable
private fun TitleRow(name: String, goal: String, now: Int, need: Int, unlocked: Boolean, wearing: Boolean, onWear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("「$name」", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (unlocked) DonColors.Ink else DonColors.InkMuted)
            Text("$goal · $now/$need", fontSize = 12.sp, color = DonColors.InkMuted)
        }
        when {
            wearing -> Badge("달고 있어요", DonColors.Good)
            unlocked -> SmallButton(onClick = onWear, containerColor = DonColors.Game) { Text("달기") }
        }
    }
}

@Composable
fun GradeBadge(grade: CardGrade) =
    Badge(grade.label, if (grade == CardGrade.LEGEND) Color(0xFFD99A2E) else DonColors.Income)

/** 얇은 진행 막대 (게임 색) */
@Composable
fun ProgressLine(ratio: Float, color: Color = DonColors.Game, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(DonColors.Line)) {
        Box(Modifier.fillMaxWidth(ratio.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/** 집 탭 "모으기·놀이" 카드: 도감 · 세트 · 미니게임 둘 */
@Composable
fun PlayCard(items: List<MockItem>, gameLeft: Long, gameCap: Long, onDex: () -> Unit, onGame: (com.simbio.don.game.MiniGame) -> Unit) {
    val got = remember(items) { Dex.gotIds(items) }
    val sets = Sets.completed(got).size
    GameCard("모으기 · 놀이") {
        Text(
            if (gameCap > 0) "이번 주 미니게임으로 ${miles(gameLeft)} 더 받을 수 있어요"
            else "미니게임 마일은 이번 주 결산 마일의 10%까지 — 지금은 마일 없이 놀아요",
            fontSize = 12.sp,
            color = DonColors.InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuTile("도감 ${got.size}/${Catalog.products.size}", com.simbio.don.app.R.drawable.px16_p_k4, Modifier.weight(1f), onDex)
            MenuTile("세트 $sets/${Sets.all.size}", com.simbio.don.app.R.drawable.px16_p_t1, Modifier.weight(1f), onDex)
            MenuTile("얼마일까?", com.simbio.don.app.R.drawable.px16_mile, Modifier.weight(1f)) { onGame(com.simbio.don.game.MiniGame.QUIZ) }
            MenuTile("짝 맞추기", com.simbio.don.app.R.drawable.px16_p_t5, Modifier.weight(1f)) { onGame(com.simbio.don.game.MiniGame.MEMORY) }
        }
    }
}
