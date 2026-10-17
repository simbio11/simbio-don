package com.simbio.don.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.simbio.don.app.R
import com.simbio.don.app.ui.kit.icon
import com.simbio.don.app.ui.kit.sprite
import com.simbio.don.app.ui.pixel.RoomLayout
import com.simbio.don.game.HouseLayout
import com.simbio.don.game.ItemKind
import com.simbio.don.game.Mood
import com.simbio.don.game.Slot
import java.time.LocalTime

/** 창밖 시간대. 하늘·햇빛·밤 조명이 바뀐다. */
enum class DayPhase {
    DAY, SUNSET, NIGHT;

    companion object {
        fun of(time: LocalTime): DayPhase = when (time.hour) {
            in 6..16 -> DAY
            in 17..18 -> SUNSET
            else -> NIGHT
        }
    }
}

/** 방에서 누를 수 있는 곳 */
sealed interface RoomHit {
    data class OfSlot(val slot: Slot) : RoomHit
    data object Floor : RoomHit
    data object Trash : RoomHit
}

private fun hitAt(x: Int, y: Int): RoomHit? {
    RoomLayout.hits.entries.firstOrNull { (_, r) -> x in r.left until r.right && y in r.top until r.bottom }?.let {
        return RoomHit.OfSlot(it.key)
    }
    val t = RoomLayout.trashHit
    if (x in t.left until t.right && y in t.top until t.bottom) return RoomHit.Trash
    val f = RoomLayout.floorHit
    if (x in f.left until f.right && y in f.top until f.bottom) return RoomHit.Floor
    return null
}

/**
 * 캐릭터 산책 계획 (한 바퀴 [WALK_CYCLE] ms). 서 있다가 러그 위를 왔다 갔다 한다.
 * (끝 시각 ms, 그때까지 도착할 x) — 앞 구간과 x가 같으면 서 있는 구간.
 */
private const val WALK_CYCLE = 16_000
private val WALK_PLAN: List<Pair<Int, Int>> by lazy {
    val home = RoomLayout.character.x
    listOf(
        3_000 to home,
        4_400 to RoomLayout.WALK_MIN,
        7_000 to RoomLayout.WALK_MIN,
        10_000 to RoomLayout.WALK_MAX,
        13_000 to RoomLayout.WALK_MAX,
        14_600 to home,
        WALK_CYCLE to home,
    )
}

/** [ms] 시각의 캐릭터 x 와 그림 번호 (0 기본, 1 눈 감음, 2·3 걷기), 숨쉬기 오프셋 */
internal data class CharacterPose(val x: Int, val frame: Int, val bob: Int)

internal fun poseAt(ms: Int): CharacterPose {
    var prevT = 0
    var prevX = RoomLayout.character.x
    for ((t, x) in WALK_PLAN) {
        if (ms < t) {
            if (x == prevX) {
                val blink = ms % 3_200 > 3_000
                return CharacterPose(x, if (blink) 1 else 0, (ms / 800) % 2)
            }
            val p = (ms - prevT).toFloat() / (t - prevT)
            val step = if ((ms / 180) % 2 == 0) 2 else 3
            return CharacterPose(prevX + Math.round((x - prevX) * p), step, 0)
        }
        prevT = t
        prevX = x
    }
    return CharacterPose(prevX, 0, 0)
}

/**
 * 원룸 도트 장면. 하늘(흐름) → 방 → 햇빛 → 물건·캐릭터 → 노을빛/밤 그늘 → 밤 불빛 순으로
 * 원본 픽셀 크기 그림을 겹치고, 주어진 너비에 맞게 키운다.
 *
 * [onTap]이 있으면 가구·바닥·쓰레기를 누를 수 있다.
 */
@Composable
fun PixelRoom(
    house: HouseLayout,
    mood: Mood,
    modifier: Modifier = Modifier,
    phase: DayPhase = remember { DayPhase.of(LocalTime.now()) },
    onTap: ((RoomHit) -> Unit)? = null,
) {
    val room = ImageBitmap.imageResource(R.drawable.px_room_one_room)
    val sky = ImageBitmap.imageResource(
        when (phase) {
            DayPhase.DAY -> R.drawable.px_sky_day
            DayPhase.SUNSET -> R.drawable.px_sky_sunset
            DayPhase.NIGHT -> R.drawable.px_sky_night
        },
    )
    val light = when (phase) {
        DayPhase.DAY -> ImageBitmap.imageResource(R.drawable.px_light_day)
        DayPhase.SUNSET -> ImageBitmap.imageResource(R.drawable.px_light_sunset)
        DayPhase.NIGHT -> null
    }
    val nightShade = if (phase == DayPhase.NIGHT) ImageBitmap.imageResource(R.drawable.px_night_shade) else null
    val nightLights = if (phase == DayPhase.NIGHT) ImageBitmap.imageResource(R.drawable.px_night_lights) else null
    val shadow = ImageBitmap.imageResource(R.drawable.px_shadow)
    val items = ItemKind.entries.associateWith { ImageBitmap.imageResource(it.icon()) }
    val trash = ItemKind.entries.filter { it.isFood }.associateWith { ImageBitmap.imageResource(it.icon(trash = true)) }
    val frames = (0..3).map { ImageBitmap.imageResource(mood.sprite(it)) }

    val clock = rememberInfiniteTransition(label = "room")
    val ms by clock.animateFloat(0f, WALK_CYCLE.toFloat(), infiniteRepeatable(tween(WALK_CYCLE, easing = LinearEasing)), label = "walk")
    val skyOffset by clock.animateFloat(
        0f,
        RoomLayout.SKY_W.toFloat(),
        infiniteRepeatable(tween(if (phase == DayPhase.NIGHT) 120_000 else 64_000, easing = LinearEasing)),
        label = "sky",
    )

    // 원본 크기(160×136) 버퍼에 한 장으로 겹쳐 그린 뒤 통째로 키운다.
    // 레이어끼리 픽셀 격자가 어긋나지 않고, 카드 너비를 빈틈없이 채운다.
    val buffer = remember { ImageBitmap(RoomLayout.WIDTH, RoomLayout.HEIGHT) }
    val bufferScope = remember { CanvasDrawScope() }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = maxWidth * RoomLayout.HEIGHT / RoomLayout.WIDTH
        val tap = if (onTap == null) Modifier else Modifier.pointerInput(house) {
            detectTapGestures { o ->
                val k = size.width.toFloat() / RoomLayout.WIDTH
                when (val hit = hitAt((o.x / k).toInt(), (o.y / k).toInt())) {
                    is RoomHit.OfSlot -> onTap(hit)
                    RoomHit.Floor -> if (house.floor.isNotEmpty()) onTap(hit)
                    RoomHit.Trash -> if (house.trash.isNotEmpty()) onTap(hit)
                    null -> Unit
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(height).then(tap).semantics {
                contentDescription = "내 방: 물건 ${house.itemCount}개, 쓰레기 ${house.trash.size}개"
            },
        ) {
            val pose = poseAt(ms.toInt())
            bufferScope.draw(density, layoutDirection, androidx.compose.ui.graphics.Canvas(buffer), Size(RoomLayout.WIDTH.toFloat(), RoomLayout.HEIGHT.toFloat())) {
                fun put(img: ImageBitmap, x: Int, y: Int) = drawImage(img, Offset(x.toFloat(), y.toFloat()))
                drawRect(Color.Black)

                // 하늘은 유리창 안에서만 흐른다
                val g = RoomLayout.glass
                clipRect(g.left.toFloat(), g.top.toFloat(), g.right.toFloat(), g.bottom.toFloat()) {
                    val off = skyOffset.toInt()
                    put(sky, g.left - off, g.top)
                    put(sky, g.left - off + RoomLayout.SKY_W, g.top)
                }
                put(room, 0, 0)
                light?.let { put(it, 0, 0) }

                RoomLayout.slots.forEach { (slot, spots) ->
                    house.slots[slot].orEmpty().zip(spots).forEach { (item, p) -> put(items.getValue(item.kind), p.x, p.y) }
                }
                house.trash.zip(RoomLayout.trash).forEach { (item, p) -> put(trash.getValue(item.kind), p.x, p.y) }

                val cy = RoomLayout.character.y
                put(shadow, pose.x + 3, cy + RoomLayout.CHAR_H - 3)
                put(frames[pose.frame], pose.x, cy + pose.bob)

                house.floor.zip(RoomLayout.floorPile).sortedBy { it.second.y }.forEach { (item, p) ->
                    put(items.getValue(item.kind), p.x, p.y)
                }
                if (phase == DayPhase.SUNSET) drawRect(Color(0x22FF8C50))
                nightShade?.let { put(it, 0, 0) }
                nightLights?.let { put(it, 0, 0) }
            }
            drawImage(
                buffer,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(RoomLayout.WIDTH, RoomLayout.HEIGHT),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}
