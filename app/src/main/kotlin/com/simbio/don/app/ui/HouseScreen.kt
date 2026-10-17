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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.ui.kit.Badge
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.IconBubble
import com.simbio.don.app.ui.kit.ItemThumb
import com.simbio.don.app.ui.kit.MoreButton
import com.simbio.don.app.ui.kit.SubButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.card
import com.simbio.don.app.ui.kit.Divider
import com.simbio.don.app.weather.Weather
import com.simbio.don.game.MockItem
import com.simbio.don.game.MockWallet
import com.simbio.don.game.Wishlist

@Composable
fun HouseScreen(
    home: HomeState,
    onShop: () -> Unit,
    onRecycle: () -> Unit,
    onDiscard: (Set<Long>) -> Unit,
    onBuyWish: (MockItem) -> Unit = {},
    onGiveUpWish: (MockItem) -> Unit = {},
    weather: Weather? = null,
    city: String? = null,
) {
    val house = home.house
    var picking by remember { mutableStateOf<Pair<String, List<MockItem>>?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 ${house.level.label}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text(
                    "나는 지금 ${home.mood.label}" + if (weather != null && city != null) " · $city ${weather.label}" else "",
                    fontSize = 13.sp,
                    color = DonColors.GameDeep,
                )
            }
            Badge(
                "수납 ${house.itemCount}/${house.capacity}",
                color = if (house.isCluttered) DonColors.Warn else DonColors.GameDeep,
            )
        }

        Column(Modifier.fillMaxWidth().card(DonColors.GameSurface)) {
            PixelRoom(
                house,
                home.mood,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                phase = DayPhase.of(home.now.toLocalTime()),
                weather = weather ?: Weather.CLEAR,
                onTap = { hit ->
                    when (hit) {
                        is RoomHit.OfSlot -> picking = hit.slot.label to house.slots[hit.slot].orEmpty()
                        RoomHit.Floor -> picking = "바닥" to house.floor
                        RoomHit.Trash -> onRecycle()
                    }
                },
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("가구를 누르면 그 안의 물건을 볼 수 있어요.", fontSize = 12.sp, color = DonColors.InkMuted)
                if (house.isCluttered) WarnLine("수납이 넘쳐서 바닥에 ${house.floor.size}개가 쌓였어요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShop, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) { Text("모의 쇼핑") }
                    if (house.trash.isNotEmpty()) {
                        SubButton(onClick = onRecycle, containerColor = DonColors.Surface) {
                            Icon(UiIcon.RECYCLE, 18.dp, DonColors.Good)
                            Text("분리수거 ${house.trash.size}")
                        }
                    }
                    if (house.itemCount > 0) {
                        SubButton(onClick = { picking = "집" to house.slots.values.flatten() + house.floor }, containerColor = DonColors.Surface) { Text("비우기") }
                    }
                }
            }
        }

        if (house.wouldHaveCost > 0) {
            Row(
                Modifier.fillMaxWidth().card().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBubble(UiIcon.SAVINGS, DonColors.Good, 44.dp)
                Column {
                    Text("이 물건들 실제로 샀으면", fontSize = 13.sp, color = DonColors.InkMuted)
                    Text(won(house.wouldHaveCost), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text("하지만 실제로는 한 푼도 안 썼어요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Good)
                }
            }
        } else {
            Text("아직 빈 방이에요. 사고 싶은 게 생기면 여기서 모의로 사보세요.", fontSize = 13.sp, color = DonColors.InkSoft, modifier = Modifier.padding(horizontal = 4.dp))
        }

        if (home.wishes.isNotEmpty() || home.gaveUpTotal > 0) {
            WishlistCard(home, onBuyWish, onGiveUpWish)
        }

        GameCard("모의 지갑", icon = { Icon(UiIcon.WALLET, 18.dp, DonColors.Game) }) {
            Text(won(home.wallet), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
            Text("이번 달 쓸 수 있는 돈에서 실제 지출과 아직 돌려받지 않은 모의 구매를 뺀 금액이에요.", fontSize = 12.sp, color = DonColors.InkSoft)
            Text("쇼핑 앱에서 '공유 → 돈't'를 눌러도 바로 와요.", fontSize = 12.sp, color = DonColors.InkMuted)
        }

        if (home.shipping.isNotEmpty()) {
            SectionCard("배송 중 ${home.shipping.size}개", trailing = { Badge("주말 결산 때 도착", DonColors.Game) }) {
                home.shipping.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemThumb(m.kind, 48.dp)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = DonColors.Ink)
                            Text(won(m.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                        }
                        Icon(UiIcon.TRUCK, 20.dp, DonColors.Game)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("이번 주 실제 예산을 지키면 전부 도착하고, 넘기면 넘긴 만큼 반품돼요.", fontSize = 12.sp, color = DonColors.InkMuted)
            }
        }
        Text("수납장 사기·이사는 다음 버전(마일리지)에서 열려요.", fontSize = 11.sp, color = DonColors.InkMuted, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(88.dp))
    }

    picking?.let { (title, items) ->
        DiscardDialog(title, items, onDismiss = { picking = null }, onDiscard = { onDiscard(it); picking = null })
    }
}

/** 찜 목록: 하루가 지나면 "아직도 사고 싶어요?" */
@Composable
private fun WishlistCard(home: HomeState, onBuy: (MockItem) -> Unit, onGiveUp: (MockItem) -> Unit) {
    SectionCard(
        "찜 목록 ${home.wishes.size}개",
        trailing = if (home.gaveUpTotal > 0) ({ Badge("안 산 돈 ${won(home.gaveUpTotal)}", DonColors.Good) }) else null,
    ) {
        if (home.wishes.isEmpty()) {
            Text("찜한 물건을 하루 고민하고 안 샀어요. 그 돈은 그대로 남아 있어요.", fontSize = 13.sp, color = DonColors.InkSoft)
        }
        home.wishes.forEachIndexed { i, w ->
            if (i > 0) Divider(Modifier.padding(vertical = 8.dp))
            val ready = w.createdAt?.let { Wishlist.isReady(it, home.now) } ?: true
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemThumb(w.kind, 52.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DonColors.Ink, maxLines = 1)
                    Text(won(w.price), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    if (ready) {
                        Text("하루 지났어요. 아직도 사고 싶어요?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DonColors.Warn)
                    } else {
                        val left = w.createdAt?.let { Wishlist.hoursLeft(it, home.now) } ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(UiIcon.HOURGLASS, 14.dp, DonColors.InkMuted)
                            Text("${left}시간 뒤에 다시 물어볼게요", fontSize = 12.sp, color = DonColors.InkMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val affordable = MockWallet.canAfford(home.wallet, w.price)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubButton(onClick = { onGiveUp(w) }, modifier = Modifier.weight(1f), containerColor = DonColors.GoodSurface, contentColor = DonColors.Good) {
                    Text("안 살래요")
                }
                SubButton(
                    onClick = { onBuy(w) },
                    enabled = affordable,
                    modifier = Modifier.weight(1f),
                    containerColor = if (ready) DonColors.GameSurface else DonColors.SurfaceAlt,
                    contentColor = if (ready) DonColors.GameDeep else DonColors.InkSoft,
                ) { Text(if (ready) "모의로 사기" else "지금 사기") }
            }
            if (!affordable) Text("모의 지갑 잔고가 부족해요.", fontSize = 12.sp, color = DonColors.InkMuted)
        }
    }
}

@Composable
private fun DiscardDialog(title: String, items: List<MockItem>, onDismiss: () -> Unit, onDiscard: (Set<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DonColors.Surface,
        title = { Text(if (items.isEmpty()) "$title · 비어 있어요" else "$title · 비울 물건 고르기", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (m.id in selected) selected - m.id else selected + m.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = m.id in selected, onCheckedChange = null)
                        ItemThumb(m.kind, 36.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("${m.name} · ${won(m.price)}", fontSize = 13.sp)
                    }
                }
                if (items.isNotEmpty()) Text("비워도 도감에는 남아요.", fontSize = 11.sp, color = DonColors.InkMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onDiscard(selected) }, enabled = selected.isNotEmpty()) { Text("비우기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 첫 화면용 집 카드: 지금 내 방 모습 그대로 */
@Composable
fun HomeGameCard(home: HomeState, onHouse: () -> Unit, onShop: () -> Unit, weather: Weather? = null) {
    Column(Modifier.fillMaxWidth().card(DonColors.GameSurface)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("게임 · 내 ${home.house.level.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DonColors.GameDeep)
                Text("나는 지금 ${home.mood.label}", fontSize = 13.sp, color = DonColors.InkSoft)
            }
            MoreButton("내 집 보기", onHouse)
        }
        PixelRoom(home.house, home.mood, Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)), DayPhase.of(home.now.toLocalTime()), weather ?: Weather.CLEAR)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("모의 지갑", fontSize = 12.sp, color = DonColors.InkMuted)
                    Text(won(home.wallet), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                }
                Text(
                    buildString {
                        append("물건 ${home.house.itemCount}개")
                        if (home.shipping.isNotEmpty()) append(" · 배송 중 ${home.shipping.size}")
                        if (home.house.trash.isNotEmpty()) append(" · 쓰레기 ${home.house.trash.size}")
                        if (home.wishes.isNotEmpty()) append(" · 찜 ${home.wishes.size}")
                    },
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
            }
            Button(onClick = onShop, containerColor = DonColors.Game, modifier = Modifier.fillMaxWidth()) { Text("모의 쇼핑") }
        }
    }
}
