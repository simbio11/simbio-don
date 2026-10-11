package com.simbio.don.app.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.ui.pixel.Character
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.ItemKind
import com.simbio.don.game.MockItem
import com.simbio.don.game.Slot

/** 물건 종류별 임시 그림 (sprite_item_<종류>.png를 넣으면 그걸 쓴다) */
fun ItemKind.emoji(trash: Boolean = false): String = when (this) {
    ItemKind.CLOTHES -> "👕"
    ItemKind.BAG -> "👜"
    ItemKind.SHOES -> "👟"
    ItemKind.ELECTRONICS -> "🎧"
    ItemKind.BOOK -> "📚"
    ItemKind.BEAUTY -> "💄"
    ItemKind.LIVING -> "🧴"
    ItemKind.TOY -> "🧸"
    ItemKind.MEAL -> if (trash) "📦" else "🍗"
    ItemKind.DRINK -> if (trash) "🥤" else "☕"
    ItemKind.ETC -> "🎁"
}

@SuppressLint("DiscouragedApi")
@Composable
fun ItemIcon(kind: ItemKind, size: Dp, trash: Boolean = false) {
    val context = LocalContext.current
    val name = "sprite_item_${kind.name.lowercase()}${if (trash) "_trash" else ""}"
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    if (resId != 0) {
        val bmp = BitmapFactory.decodeResource(context.resources, resId)
        Image(BitmapPainter(bmp.asImageBitmap(), filterQuality = FilterQuality.None), null, Modifier.size(size))
    } else {
        Text(kind.emoji(trash), fontSize = (size.value * 0.8f).sp)
    }
}

/** 방 안 칸 위치 (방 너비·높이에 대한 비율). 방 배경 PNG를 넣을 때도 이 위치에 맞춘다 — docs/art-spec.md */
private data class Area(val x: Float, val y: Float, val w: Float, val h: Float)

private val AREAS = mapOf(
    Slot.WARDROBE to Area(0.03f, 0.10f, 0.26f, 0.50f),
    Slot.SHOE_RACK to Area(0.03f, 0.66f, 0.26f, 0.16f),
    Slot.SHELF to Area(0.34f, 0.28f, 0.32f, 0.16f),
    Slot.DESK to Area(0.68f, 0.52f, 0.29f, 0.18f),
)
private val FLOOR_PILE = Area(0.31f, 0.70f, 0.36f, 0.27f)
private val TRASH_AREA = Area(0.70f, 0.76f, 0.27f, 0.21f)

private object RoomColors {
    val Wall = Color(0xFFF3E9DC)
    val WallLine = Color(0xFFE6D8C6)
    val Floor = Color(0xFFD9B98F)
    val FloorLine = Color(0xFFC9A77A)
    val Wood = Color(0xFF9C6B3F)
    val WoodLight = Color(0xFFB98552)
    val Glass = Color(0xFFBFE3F5)
    val Bed = Color(0xFFE8EEF9)
    val Blanket = Color(0xFF7D9BE0)
}

/** 원룸 그림. 칸을 누르면 [onSlot]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HouseRoom(house: HouseLayout, mood: com.simbio.don.game.Mood, onSlot: (Slot) -> Unit, onFloor: () -> Unit, onTrash: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            fun rect(a: Area, c: Color) = drawRect(c, Offset(a.x * W, a.y * H), Size(a.w * W, a.h * H))
            // 벽과 바닥
            drawRect(RoomColors.Wall, Offset.Zero, Size(W, H * 0.55f))
            for (i in 1..6) drawLine(RoomColors.WallLine, Offset(W * i / 7, 0f), Offset(W * i / 7, H * 0.55f), 2f)
            drawRect(RoomColors.Floor, Offset(0f, H * 0.55f), Size(W, H * 0.45f))
            for (i in 1..5) drawLine(RoomColors.FloorLine, Offset(0f, H * (0.55f + 0.09f * i)), Offset(W, H * (0.55f + 0.09f * i)), 2f)
            // 창문
            rect(Area(0.36f, 0.05f, 0.28f, 0.20f), RoomColors.Wood)
            rect(Area(0.375f, 0.065f, 0.25f, 0.17f), RoomColors.Glass)
            drawLine(RoomColors.Wood, Offset(W * 0.5f, H * 0.065f), Offset(W * 0.5f, H * 0.235f), 4f)
            // 침대 (장식)
            rect(Area(0.68f, 0.20f, 0.29f, 0.28f), RoomColors.WoodLight)
            rect(Area(0.69f, 0.22f, 0.27f, 0.10f), RoomColors.Bed)
            rect(Area(0.69f, 0.32f, 0.27f, 0.14f), RoomColors.Blanket)
            // 수납 가구
            AREAS.values.forEach { a ->
                rect(a, RoomColors.Wood)
                rect(Area(a.x + 0.008f, a.y + 0.008f, a.w - 0.016f, a.h - 0.016f), RoomColors.WoodLight)
            }
        }
        AREAS.forEach { (slot, a) ->
            val items = house.slots[slot].orEmpty()
            val cap = house.level.capacity[slot] ?: 0
            Column(
                Modifier.offset(w * a.x, h * a.y).size(w * a.w, h * a.h).clickable { onSlot(slot) }.padding(4.dp),
            ) {
                Text("${slot.label} ${items.size}/$cap", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                FlowRow { items.forEach { ItemIcon(it.kind, 16.dp) } }
            }
        }
        // 캐릭터
        Box(Modifier.offset(w * 0.42f, h * 0.42f)) { Character(mood, w * 0.17f) }
        // 넘쳐서 바닥에 쌓인 물건
        if (house.floor.isNotEmpty()) {
            FlowRow(
                Modifier.offset(w * FLOOR_PILE.x, h * FLOOR_PILE.y).size(w * FLOOR_PILE.w, h * FLOOR_PILE.h).clickable(onClick = onFloor),
            ) { house.floor.forEach { ItemIcon(it.kind, 18.dp) } }
        }
        if (house.trash.isNotEmpty()) {
            FlowRow(
                Modifier.offset(w * TRASH_AREA.x, h * TRASH_AREA.y).size(w * TRASH_AREA.w, h * TRASH_AREA.h).clickable(onClick = onTrash),
            ) { house.trash.forEach { ItemIcon(it.kind, 16.dp, trash = true) } }
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
    var picking by remember { mutableStateOf<List<MockItem>?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameCard("게임 · 내 ${house.level.label}") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("나는 지금 ${home.mood.label}", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("물건 ${house.itemCount}/${house.capacity}", fontSize = 13.sp, color = if (house.isCluttered) DonColors.Warn else ChartColors.InkSecondary)
            }
            Spacer(Modifier.height(8.dp))
            HouseRoom(
                house,
                home.mood,
                onSlot = { slot -> picking = house.slots[slot].orEmpty() },
                onFloor = { picking = house.floor },
                onTrash = onRecycle,
            )
            Spacer(Modifier.height(8.dp))
            if (house.wouldHaveCost > 0) {
                Text("이 물건들 실제로 샀으면 총 ${won(house.wouldHaveCost)}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("하지만 실제로는 한 푼도 안 썼어요.", fontSize = 12.sp, color = ChartColors.InkSecondary)
            } else {
                Text("아직 빈 방이에요. 사고 싶은 게 생기면 여기서 모의로 사보세요.", fontSize = 13.sp, color = ChartColors.InkSecondary)
            }
            if (house.isCluttered) {
                Text("⚠ 수납이 넘쳐서 바닥에 ${house.floor.size}개가 쌓였어요", fontSize = 13.sp, color = DonColors.Warn)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (house.trash.isNotEmpty()) OutlinedButton(onClick = onRecycle) { Text("분리수거 ${house.trash.size}") }
                if (house.itemCount > 0) OutlinedButton(onClick = { picking = house.slots.values.flatten() + house.floor }) { Text("비우기") }
            }
            Text("수납장 사기·이사는 다음 버전(마일리지)에서 열려요.", fontSize = 11.sp, color = ChartColors.InkMuted)
        }

        GameCard("모의 지갑") {
            Text(won(home.wallet), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = DonColors.Game)
            Text("이번 달 쓸 수 있는 돈에서 실제 지출과 아직 돌려받지 않은 모의 구매를 뺀 금액이에요.", fontSize = 12.sp, color = ChartColors.InkSecondary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onShop, colors = ButtonDefaults.buttonColors(containerColor = DonColors.Game), modifier = Modifier.fillMaxWidth()) {
                Text("모의 쇼핑하기")
            }
            Text("쇼핑 앱에서 '공유 → 돈 아끼는 게임'을 눌러도 바로 와요.", fontSize = 11.sp, color = ChartColors.InkMuted)
        }

        if (home.shipping.isNotEmpty()) {
            GameCard("배송 중 ${home.shipping.size}개 · 주말 결산 때 도착") {
                home.shipping.forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(m.kind, 22.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(m.name, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(won(m.price), fontSize = 14.sp)
                    }
                }
                Text("이번 주 실제 예산을 지키면 전부 도착하고, 넘기면 넘긴 만큼 반품돼요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            }
        }
        Spacer(Modifier.height(80.dp))
    }

    picking?.let { items ->
        DiscardDialog(items, onDismiss = { picking = null }, onDiscard = { onDiscard(it); picking = null })
    }
}

@Composable
private fun DiscardDialog(items: List<MockItem>, onDismiss: () -> Unit, onDiscard: (Set<Long>) -> Unit) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (items.isEmpty()) "비어 있어요" else "비울 물건 고르기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (m.id in selected) selected - m.id else selected + m.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = m.id in selected, onCheckedChange = null)
                        ItemIcon(m.kind, 20.dp)
                        Spacer(Modifier.size(6.dp))
                        Text("${m.name} · ${won(m.price)}", fontSize = 13.sp)
                    }
                }
                if (items.isNotEmpty()) Text("비워도 도감에는 남아요.", fontSize = 11.sp, color = ChartColors.InkMuted)
            }
        },
        confirmButton = { TextButton(onClick = { onDiscard(selected) }, enabled = selected.isNotEmpty()) { Text("비우기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** 첫 화면용 작은 집 카드 */
@Composable
fun HomeGameCard(home: HomeState, onHouse: () -> Unit, onShop: () -> Unit) {
    GameCard("게임 · 내 ${home.house.level.label}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, ChartColors.Grid, RoundedCornerShape(12.dp)).padding(8.dp)) {
                Character(home.mood, 56.dp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("나는 지금 ${home.mood.label}", fontWeight = FontWeight.Bold)
                Text("모의 지갑 ${won(home.wallet)}", fontSize = 13.sp)
                Text(
                    buildString {
                        append("집에 물건 ${home.house.itemCount}개")
                        if (home.shipping.isNotEmpty()) append(" · 배송 중 ${home.shipping.size}개")
                        if (home.house.trash.isNotEmpty()) append(" · 쓰레기 ${home.house.trash.size}개")
                    },
                    fontSize = 12.sp,
                    color = ChartColors.InkSecondary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onShop, colors = ButtonDefaults.buttonColors(containerColor = DonColors.Game), modifier = Modifier.weight(1f)) { Text("모의 쇼핑") }
            OutlinedButton(onClick = onHouse, modifier = Modifier.weight(1f)) { Text("내 집 보기") }
        }
    }
}
