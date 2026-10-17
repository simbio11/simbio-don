package com.simbio.don.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.R
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.ui.kit.Button
import com.simbio.don.app.ui.kit.CharacterSprite
import com.simbio.don.app.ui.kit.Icon
import com.simbio.don.app.ui.kit.ItemIcon
import com.simbio.don.app.ui.kit.OutlinedButton
import com.simbio.don.app.ui.kit.TextButton
import com.simbio.don.app.ui.kit.UiIcon
import com.simbio.don.app.ui.kit.WarnLine
import com.simbio.don.app.ui.kit.icon
import com.simbio.don.app.ui.kit.pixelPanel
import com.simbio.don.app.ui.kit.rememberIdleFrame
import com.simbio.don.app.ui.kit.sprite
import com.simbio.don.app.ui.pixel.RoomLayout
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MockItem
import com.simbio.don.game.Mood
import com.simbio.don.game.Slot

/** 방에서 누를 수 있는 곳 (그림 원본 픽셀 좌표) */
private sealed interface RoomHit {
    data class OfSlot(val slot: Slot) : RoomHit
    data object Floor : RoomHit
    data object Trash : RoomHit
}

private fun hitAt(x: Int, y: Int): RoomHit? = when {
    x in 1..47 && y in 14..80 -> RoomHit.OfSlot(Slot.WARDROBE)
    x in 62..101 && y in 38..80 -> RoomHit.OfSlot(Slot.SHELF)
    x in 112..155 && y in 44..80 -> RoomHit.OfSlot(Slot.DESK)
    x in 6..33 && y in 100..132 -> RoomHit.OfSlot(Slot.SHOE_RACK)
    x in 34..62 && y in 88..135 -> RoomHit.Trash
    x in 50..115 && y in 106..135 -> RoomHit.Floor
    else -> null
}

/**
 * 원룸 도트 장면. 배경·물건·캐릭터를 원본 픽셀 크기로 겹쳐 그리고,
 * 화면 너비에 맞는 가장 큰 정수 배율로 확대한다 (픽셀이 뭉개지지 않게).
 */
@Composable
fun PixelRoom(
    house: HouseLayout,
    mood: Mood,
    onSlot: (Slot) -> Unit,
    onFloor: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val room = ImageBitmap.imageResource(R.drawable.px_room_one_room)
    val shadow = ImageBitmap.imageResource(R.drawable.px_shadow)
    val items = ItemKind.entries.associateWith { ImageBitmap.imageResource(it.icon()) }
    val trashImages = ItemKind.entries.filter { it.isFood }.associateWith { ImageBitmap.imageResource(it.icon(trash = true)) }
    val idle = rememberIdleFrame()
    val character = ImageBitmap.imageResource(mood.sprite(idle.blink))

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = maxOf(1, constraints.maxWidth / RoomLayout.WIDTH)
        val height = with(LocalDensity.current) { (RoomLayout.HEIGHT * scale).toDp() }
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(house, scale) {
                detectTapGestures { o ->
                    val ox = (size.width - RoomLayout.WIDTH * scale) / 2
                    when (val hit = hitAt(((o.x - ox) / scale).toInt(), (o.y / scale).toInt())) {
                        is RoomHit.OfSlot -> onSlot(hit.slot)
                        RoomHit.Floor -> if (house.floor.isNotEmpty()) onFloor()
                        RoomHit.Trash -> if (house.trash.isNotEmpty()) onTrash()
                        null -> Unit
                    }
                }
            },
        ) {
            val ox = ((size.width - RoomLayout.WIDTH * scale) / 2).toInt()
            fun put(img: ImageBitmap, x: Int, y: Int) = drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(ox + x * scale, y * scale),
                dstSize = IntSize(img.width * scale, img.height * scale),
                filterQuality = FilterQuality.None,
            )

            put(room, 0, 0)
            RoomLayout.slots.forEach { (slot, spots) ->
                house.slots[slot].orEmpty().zip(spots).forEach { (item, p) -> put(items.getValue(item.kind), p.x, p.y) }
            }
            house.trash.zip(RoomLayout.trash).forEach { (item, p) -> put(trashImages.getValue(item.kind), p.x, p.y) }
            val c = RoomLayout.character
            put(shadow, c.x + 1, c.y + 21)
            put(character, c.x, c.y + idle.bob)
            house.floor.zip(RoomLayout.floorPile).sortedBy { it.second.y }.forEach { (item, p) ->
                put(items.getValue(item.kind), p.x, p.y)
            }
        }
    }
}

@Composable
fun HouseScreen(
    home: HomeState,
    onShop: () -> Unit,
    onRecycle: () -> Unit,
    onDiscard: (Set<Long>) -> Unit,
) {
    val house = home.house
    var picking by remember { mutableStateOf<Pair<String, List<MockItem>>?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("내 ${house.level.label}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                Text("나는 지금 ${home.mood.label}", fontSize = 13.sp, color = DonColors.GameDeep)
            }
            Row(
                Modifier.pixelPanel(if (house.isCluttered) DonColors.WarnSurface else DonColors.PaperLight, depth = 2.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(UiIcon.BOX, 16.dp)
                Text("${house.itemCount}/${house.capacity}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(Modifier.fillMaxWidth().pixelPanel(DonColors.Wood, depth = 4.dp).padding(6.dp)) {
            PixelRoom(
                house,
                home.mood,
                onSlot = { slot -> picking = slot.label to house.slots[slot].orEmpty() },
                onFloor = { picking = "바닥" to house.floor },
                onTrash = onRecycle,
            )
        }
        Text("가구를 누르면 그 안의 물건을 볼 수 있어요.", fontSize = 12.sp, color = DonColors.InkMuted)

        if (house.wouldHaveCost > 0) {
            Row(
                Modifier.fillMaxWidth().pixelPanel(DonColors.PaperLight).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(UiIcon.COIN, 28.dp)
                Column {
                    Text("이 물건들 실제로 샀으면", fontSize = 12.sp, color = DonColors.InkSoft)
                    Text(won(house.wouldHaveCost), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink)
                    Text("하지만 실제로는 한 푼도 안 썼어요", fontSize = 12.sp, color = DonColors.Good)
                }
            }
        } else {
            Text("아직 빈 방이에요. 사고 싶은 게 생기면 여기서 모의로 사보세요.", fontSize = 13.sp, color = DonColors.InkSoft)
        }
        if (house.isCluttered) WarnLine("수납이 넘쳐서 바닥에 ${house.floor.size}개가 쌓였어요")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onShop, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) { Text("모의 쇼핑") }
            if (house.trash.isNotEmpty()) OutlinedButton(onClick = onRecycle) { Text("분리수거 ${house.trash.size}") }
            if (house.itemCount > 0) OutlinedButton(onClick = { picking = "집" to house.slots.values.flatten() + house.floor }) { Text("비우기") }
        }
        Text("수납장 사기·이사는 다음 버전(마일리지)에서 열려요.", fontSize = 11.sp, color = DonColors.InkMuted)

        GameCard("모의 지갑", icon = { Icon(UiIcon.COIN, 16.dp) }) {
            Text(won(home.wallet), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
            Text("이번 달 쓸 수 있는 돈에서 실제 지출과 아직 돌려받지 않은 모의 구매를 뺀 금액이에요.", fontSize = 12.sp, color = DonColors.InkSoft)
            Text("쇼핑 앱에서 '공유 → 돈 아끼는 게임'을 눌러도 바로 와요.", fontSize = 11.sp, color = DonColors.InkMuted)
        }

        if (home.shipping.isNotEmpty()) {
            GameCard("배송 중 ${home.shipping.size}개 · 주말 결산 때 도착", icon = { Icon(UiIcon.BOX, 16.dp) }) {
                home.shipping.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(m.kind, 24.dp)
                        Spacer(Modifier.size(10.dp))
                        Text(m.name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(won(m.price), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("이번 주 실제 예산을 지키면 전부 도착하고, 넘기면 넘긴 만큼 반품돼요.", fontSize = 11.sp, color = DonColors.InkMuted)
            }
        }
        Spacer(Modifier.height(80.dp))
    }

    picking?.let { (title, items) ->
        DiscardDialog(title, items, onDismiss = { picking = null }, onDiscard = { onDiscard(it); picking = null })
    }
}

@Composable
private fun DiscardDialog(title: String, items: List<MockItem>, onDismiss: () -> Unit, onDiscard: (Set<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DonColors.PaperLight,
        title = { Text(if (items.isEmpty()) "$title · 비어 있어요" else "$title · 비울 물건 고르기", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (m.id in selected) selected - m.id else selected + m.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = m.id in selected, onCheckedChange = null)
                        ItemIcon(m.kind, 24.dp)
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

/** 첫 화면용 작은 집 카드 */
@Composable
fun HomeGameCard(home: HomeState, onHouse: () -> Unit, onShop: () -> Unit) {
    GameCard("게임 · 내 ${home.house.level.label}", icon = { Icon(UiIcon.HOUSE, 16.dp) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.pixelPanel(DonColors.WoodSurface, depth = 2.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) { CharacterSprite(home.mood, 60.dp) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("나는 지금 ${home.mood.label}", fontWeight = FontWeight.Bold, color = DonColors.GameDeep)
                Text("모의 지갑 ${won(home.wallet)}", fontSize = 13.sp)
                Text(
                    buildString {
                        append("집에 물건 ${home.house.itemCount}개")
                        if (home.shipping.isNotEmpty()) append(" · 배송 중 ${home.shipping.size}개")
                        if (home.house.trash.isNotEmpty()) append(" · 쓰레기 ${home.house.trash.size}개")
                    },
                    fontSize = 12.sp,
                    color = DonColors.InkSoft,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onShop, containerColor = DonColors.Game, modifier = Modifier.weight(1f)) { Text("모의 쇼핑") }
            OutlinedButton(onClick = onHouse, modifier = Modifier.weight(1f)) { Text("내 집 보기") }
        }
    }
}
