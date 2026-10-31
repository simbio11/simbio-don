package com.simbio.don.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.simbio.don.app.weather.Weather
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.simbio.don.app.R
import com.simbio.don.app.ui.kit.icon
import com.simbio.don.app.ui.kit.CharacterRenderer
import com.simbio.don.app.ui.kit.LocalCharacterLook
import com.simbio.don.app.ui.kit.Pose
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

/** 번개: 6초마다 0.12초쯤 번쩍 */
private fun lightning(ms: Int) = ms % 6_000 < 120 || ms % 6_000 in 220..300

/** 유리창 안 날씨 (비·눈 알갱이, 안개, 번개). 원본 픽셀 좌표 */
private fun DrawScope.drawWeather(weather: Weather, ms: Int, g: androidx.compose.ui.unit.IntRect) {
    val t = ms / 1000f
    when (weather) {
        Weather.RAIN, Weather.STORM -> {
            // 비 오는 하늘은 더 어둡게, 빗줄기는 밝게
            drawRect(Color(0x40303A4C), Offset(g.left.toFloat(), g.top.toFloat()), Size(g.width.toFloat(), g.height.toFloat()))
            repeat(30) { i ->
                val speed = 30f + (i * 7 % 12)
                // 빗방울마다 시작 높이를 흩어 둔다
                val y = kotlin.math.floor(((t * speed + (i * 47) % (g.height + 6)) % (g.height + 6)) - 3)
                val x = kotlin.math.floor(((i * 37 % g.width) - y / 3 + g.width) % g.width)
                drawRect(Color(0xFFEAF3FF), Offset(g.left + x, g.top + y), Size(1f, 2f))
                drawRect(Color(0xAAEAF3FF), Offset(g.left + x - 1, g.top + y + 2), Size(1f, 2f))
            }
            if (weather == Weather.STORM && lightning(ms)) drawRect(Color(0xB0FFFFFF), Offset(g.left.toFloat(), g.top.toFloat()), Size(g.width.toFloat(), g.height.toFloat()))
        }
        Weather.SNOW -> repeat(14) { i ->
            val y = ((t * (3.5f + i % 3) + i * 7) % (g.height + 2)) - 1
            val x = ((i * 23 % g.width) + kotlin.math.sin(t * 1.3f + i) * 1.5f + g.width) % g.width
            drawRect(Color.White, Offset(g.left + kotlin.math.floor(x), g.top + kotlin.math.floor(y)), Size(1f, 1f))
        }
        Weather.FOG -> drawRect(Color(0x66FFFFFF), Offset(g.left.toFloat(), g.top.toFloat()), Size(g.width.toFloat(), g.height.toFloat()))
        else -> Unit
    }
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
private const val ROOM_FRAME_MS = 83L
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
    weather: Weather = Weather.CLEAR,
    onTap: ((RoomHit) -> Unit)? = null,
    /** 고른 인테리어 색 (없으면 기본) */
    interior: Map<com.simbio.don.game.Shop.Interior, String> = emptyMap(),
) {
    val level = house.level
    val roomContext = LocalContext.current
    val room = remember(level, interior) { RoomArt.base(roomContext, level, interior) }
    val overcast = weather.overcast
    val sky = ImageBitmap.imageResource(
        when (phase) {
            DayPhase.DAY -> if (overcast) R.drawable.px_sky_day_overcast else R.drawable.px_sky_day
            DayPhase.SUNSET -> if (overcast) R.drawable.px_sky_sunset_overcast else R.drawable.px_sky_sunset
            DayPhase.NIGHT -> if (overcast) R.drawable.px_sky_night_overcast else R.drawable.px_sky_night
        },
    )
    // 흐린 날엔 바닥에 햇빛이 들지 않는다
    val light = when {
        overcast -> null
        phase == DayPhase.DAY -> ImageBitmap.imageResource(RoomArt.lightDay(level))
        phase == DayPhase.SUNSET -> ImageBitmap.imageResource(RoomArt.lightSunset(level))
        else -> null
    }
    val nightShade = if (phase == DayPhase.NIGHT) ImageBitmap.imageResource(RoomArt.nightShade(level)) else null
    val nightLights = if (phase == DayPhase.NIGHT) ImageBitmap.imageResource(RoomArt.nightLights(level)) else null
    val shadow = ImageBitmap.imageResource(R.drawable.px_shadow)
    val items = ItemKind.entries.associateWith { ImageBitmap.imageResource(it.icon()) }
    val trash = ItemKind.entries.filter { it.isFood }.associateWith { ImageBitmap.imageResource(it.icon(trash = true)) }
    val context = LocalContext.current
    val look = LocalCharacterLook.current
    val frames = remember(look, mood) {
        listOf(Pose.STAND, Pose.BLINK, Pose.WALK_LEFT, Pose.WALK_RIGHT).map { CharacterRenderer.bitmap(context, look, mood, it) }
    }

    // 도트 그림이라 1초에 12장이면 충분하다 (매 프레임 다시 그리면 폰이 버벅인다)
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            delay(ROOM_FRAME_MS)
            elapsed.longValue = withFrameMillis { it } - start
        }
    }
    val skyCycle = if (phase == DayPhase.NIGHT) 120_000L else 64_000L

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
            val now = elapsed.longValue
            val ms = (now % WALK_CYCLE).toFloat()
            val skyOffset = (now % skyCycle).toFloat() / skyCycle * RoomLayout.SKY_W
            val pose = poseAt(ms.toInt())
            bufferScope.draw(density, layoutDirection, androidx.compose.ui.graphics.Canvas(buffer), Size(RoomLayout.WIDTH.toFloat(), RoomLayout.HEIGHT.toFloat())) {
                fun put(img: ImageBitmap, x: Int, y: Int) = drawImage(img, Offset(x.toFloat(), y.toFloat()))
                drawRect(Color.Black)

                // 하늘은 유리창 안에서만 흐른다
                val g = RoomLayout.glassByLevel[level.name] ?: RoomLayout.glass
                clipRect(g.left.toFloat(), g.top.toFloat(), g.right.toFloat(), g.bottom.toFloat()) {
                    val off = skyOffset.toInt()
                    put(sky, g.left - off, g.top)
                    put(sky, g.left - off + RoomLayout.SKY_W, g.top)
                    drawWeather(weather, ms.toInt(), g)
                }
                put(room, 0, 0)
                light?.let { put(it, 0, 0) }
                // 눈 오는 날엔 창턱에 눈이 쌓인다
                if (weather == Weather.SNOW) {
                    drawRect(Color.White, Offset((g.left - 5).toFloat(), (g.bottom + 2).toFloat()), Size((g.width + 10).toFloat(), 1f))
                    drawRect(Color(0xFFE3ECF7), Offset((g.left - 4).toFloat(), (g.bottom + 3).toFloat()), Size((g.width + 8).toFloat(), 1f))
                }

                RoomLayout.slots.forEach { (slot, spots) ->
                    house.slots[slot].orEmpty().zip(spots).forEach { (item, p) -> put(items.getValue(item.kind), p.x, p.y) }
                }
                house.trash.zip(RoomLayout.trash).forEach { (item, p) -> put(trash.getValue(item.kind), p.x, p.y) }

                val cy = RoomLayout.character.y
                put(shadow, pose.x + 4, cy + RoomLayout.CHAR_H - 3)
                put(frames[pose.frame], pose.x, cy + pose.bob)

                house.floor.zip(RoomLayout.floorPile).sortedBy { it.second.y }.forEach { (item, p) ->
                    put(items.getValue(item.kind), p.x, p.y)
                }
                if (phase == DayPhase.SUNSET) drawRect(Color(if (overcast) 0x14FF8C50 else 0x22FF8C50))
                // 비·흐린 날엔 방이 조금 어둑하다, 번개가 치면 잠깐 환해진다
                if (overcast && phase != DayPhase.NIGHT) drawRect(Color(if (weather == Weather.RAIN || weather == Weather.STORM) 0x2A28354A else 0x1A28354A))
                if (weather == Weather.STORM && lightning(ms.toInt())) drawRect(Color(0x30FFFFFF))
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
