package com.simbio.don.app.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.simbio.don.app.ui.pixel.RoomLayout
import com.simbio.don.game.PetBubble
import com.simbio.don.game.PetEnergy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 방에서 동물이 지금 하는 일 */
internal enum class PetAct { SIT, LOOK, SNIFF, TROT }

/** [x]: 방 픽셀 좌표(가운데), [faceLeft]: 왼쪽을 보는지, [step]: 걷는 박자 (0~1, 걸을 때만) */
internal data class PetPose(val x: Float, val act: PetAct, val faceLeft: Boolean, val step: Float)

/**
 * 동물 하루 일과 (한 바퀴): 앉아서 쉬다가 두리번거리고, 종종걸음으로 옮겨 가 냄새를 맡고, 다시 돌아온다.
 * 튀어 오르지 않고 천천히 출발·멈춘다 (가운데가 빠른 곡선). 기운이 없으면 거의 앉아만 있고, 자면 그대로 있다.
 *
 * 구간: (끝 ms, 도착 x, 그동안 하는 일)
 */
private data class Leg(val end: Int, val x: Float, val act: PetAct)

private val HOME = RoomLayout.character.x + RoomLayout.CHAR_W / 2f
private val LEFT = RoomLayout.WALK_MIN + RoomLayout.CHAR_W / 2f
private val RIGHT = RoomLayout.WALK_MAX + RoomLayout.CHAR_W / 2f

private val NORMAL = listOf(
    Leg(3_500, HOME, PetAct.SIT),
    Leg(4_600, HOME, PetAct.LOOK),
    Leg(7_400, LEFT, PetAct.TROT),
    Leg(8_400, LEFT, PetAct.SIT),
    Leg(10_000, LEFT, PetAct.SNIFF),
    Leg(11_200, LEFT, PetAct.SIT),
    Leg(15_600, RIGHT, PetAct.TROT),
    Leg(17_000, RIGHT, PetAct.SIT),
    Leg(18_200, RIGHT, PetAct.LOOK),
    Leg(19_400, RIGHT, PetAct.SIT),
    Leg(21_600, HOME, PetAct.TROT),
    Leg(24_000, HOME, PetAct.SIT),
)
private val LIVELY = listOf(
    Leg(2_000, HOME, PetAct.SIT),
    Leg(4_000, LEFT, PetAct.TROT),
    Leg(5_000, LEFT, PetAct.SNIFF),
    Leg(8_200, RIGHT, PetAct.TROT),
    Leg(9_000, RIGHT, PetAct.LOOK),
    Leg(11_000, LEFT + 6f, PetAct.TROT),
    Leg(12_400, LEFT + 6f, PetAct.SIT),
    Leg(14_200, HOME, PetAct.TROT),
    Leg(16_000, HOME, PetAct.SIT),
)
private val SLUGGISH = listOf(
    Leg(9_000, HOME, PetAct.SIT),
    Leg(10_500, HOME, PetAct.LOOK),
    Leg(14_500, (HOME + LEFT) / 2f, PetAct.TROT),
    Leg(21_000, (HOME + LEFT) / 2f, PetAct.SIT),
    Leg(25_000, HOME, PetAct.TROT),
    Leg(28_000, HOME, PetAct.SIT),
)

private fun plan(e: PetEnergy) = when (e) {
    PetEnergy.SLEEP -> null
    PetEnergy.LOW -> SLUGGISH
    PetEnergy.NORMAL -> NORMAL
    PetEnergy.HIGH -> LIVELY
}

/** 걷는 박자: 1초에 몇 걸음 */
private fun stepsPerSec(e: PetEnergy) = if (e == PetEnergy.HIGH) 3.6f else if (e == PetEnergy.LOW) 2.2f else 2.8f

internal fun petPoseAt(nowMs: Long, energy: PetEnergy): PetPose {
    val legs = plan(energy) ?: return PetPose(HOME, PetAct.SIT, false, 0f)
    val cycle = legs.last().end
    val ms = (nowMs % cycle).toInt()
    var prevEnd = 0
    var prevX = legs.last().x
    var faceLeft = false
    for (leg in legs) {
        if (leg.x != prevX) faceLeft = leg.x < prevX
        if (ms < leg.end) {
            val p = (ms - prevEnd).toFloat() / (leg.end - prevEnd)
            return when (leg.act) {
                PetAct.TROT -> {
                    // 천천히 출발해 천천히 멈춘다
                    val e = p * p * (3f - 2f * p)
                    val step = ((ms - prevEnd) / 1000f * stepsPerSec(energy)) % 1f
                    PetPose(prevX + (leg.x - prevX) * e, PetAct.TROT, leg.x < prevX, step)
                }
                // 두리번: 구간 가운데에 반대쪽을 본다
                PetAct.LOOK -> PetPose(leg.x, PetAct.LOOK, if (p in 0.3f..0.75f) !faceLeft else faceLeft, 0f)
                else -> PetPose(leg.x, leg.act, faceLeft, 0f)
            }
        }
        prevEnd = leg.end
        prevX = leg.x
    }
    return PetPose(prevX, PetAct.SIT, faceLeft, 0f)
}

/** 말풍선이 보이는 정도 (0~1): 9초마다 3초쯤 떴다가 사라진다. 잠잘 땐 늘 */
internal fun bubbleAlpha(nowMs: Long, bubble: PetBubble?): Float {
    if (bubble == null) return 0f
    if (bubble == PetBubble.ZZZ) return 1f
    val t = (nowMs % 9_000).toInt()
    return when {
        t < 2_000 -> 0f
        t < 2_300 -> (t - 2_000) / 300f
        t < 5_000 -> 1f
        t < 5_400 -> 1f - (t - 5_000) / 400f
        else -> 0f
    }
}

/**
 * 머리 위 말풍선. [anchor]는 머리 꼭대기, [u]는 크기 단위(방 픽셀 1칸의 화면 크기).
 * 그림은 동물 그림처럼 매끄럽게 (도트 칸에 맞추지 않는다).
 */
internal fun DrawScope.drawPetBubble(kind: PetBubble, anchor: Offset, u: Float, alpha: Float, nowMs: Long) {
    if (alpha <= 0f) return
    val t = nowMs / 1000f
    if (kind == PetBubble.ZZZ) {
        // Z 세 개가 번갈아 떠오르며 옅어진다
        repeat(3) { i ->
            val p = ((t * 0.45f + i / 3f) % 1f)
            val size = u * (2.2f + 1.6f * p)
            val c = Offset(anchor.x + u * (3f + 5f * p + sin(t * 2f + i) * 0.8f), anchor.y - u * (1f + 9f * p))
            drawZ(c, size, Color(0xFF6B7BB5).copy(alpha = alpha * (1f - p) * 0.95f), u)
        }
        return
    }
    // 둥근 말풍선 + 꼬리, 살짝 떠 있다
    val float = sin(t * 2.2f) * u * 0.5f
    val w = u * 11f
    val h = u * 9f
    val left = anchor.x + u * 1f
    val top = anchor.y - h - u * 3f + float
    val bg = Color.White.copy(alpha = 0.94f * alpha)
    val line = Color(0xFF3B3355).copy(alpha = 0.35f * alpha)
    drawRoundRect(bg, Offset(left, top), Size(w, h), CornerRadius(u * 3.5f))
    drawRoundRect(line, Offset(left, top), Size(w, h), CornerRadius(u * 3.5f), style = Stroke(u * 0.4f))
    val tail = Path().apply {
        moveTo(left + u * 2.2f, top + h - u * 0.3f)
        lineTo(left + u * 0.2f, top + h + u * 2.6f)
        lineTo(left + u * 5f, top + h - u * 0.3f)
        close()
    }
    drawPath(tail, bg)
    val c = Offset(left + w / 2f, top + h / 2f)
    val a = alpha
    when (kind) {
        PetBubble.HEART -> {
            val beat = 1f + 0.08f * abs(sin(t * 4f))
            drawHeart(c, u * 3.2f * beat, Color(0xFFF05A7E).copy(alpha = a))
        }
        PetBubble.NOTE -> {
            val col = Color(0xFF7C5CD6).copy(alpha = a)
            val head = Offset(c.x - u * 1.2f, c.y + u * 2f)
            drawOval(col, Offset(head.x - u * 1.5f, head.y - u * 1.1f), Size(u * 3f, u * 2.2f))
            drawLine(col, Offset(head.x + u * 1.3f, head.y), Offset(head.x + u * 1.3f, head.y - u * 5.5f), u * 0.8f, StrokeCap.Round)
            drawLine(col, Offset(head.x + u * 1.3f, head.y - u * 5.5f), Offset(head.x + u * 3.4f, head.y - u * 4.2f), u * 0.8f, StrokeCap.Round)
        }
        PetBubble.FOOD -> {
            // 밥그릇 + 사료 알갱이
            val bowl = Color(0xFFE0703C).copy(alpha = a)
            val kib = Color(0xFF8A5A2B).copy(alpha = a)
            drawCircle(kib, u * 0.9f, Offset(c.x - u * 1.4f, c.y - u * 0.4f))
            drawCircle(kib, u * 0.9f, Offset(c.x + u * 0.4f, c.y - u * 0.9f))
            drawCircle(kib, u * 0.9f, Offset(c.x + u * 1.8f, c.y - u * 0.2f))
            val p = Path().apply {
                moveTo(c.x - u * 3.6f, c.y)
                lineTo(c.x + u * 3.6f, c.y)
                quadraticBezierTo(c.x + u * 3.2f, c.y + u * 3.2f, c.x, c.y + u * 3.2f)
                quadraticBezierTo(c.x - u * 3.2f, c.y + u * 3.2f, c.x - u * 3.6f, c.y)
                close()
            }
            drawPath(p, bowl)
        }
        PetBubble.BORED -> repeat(3) { i ->
            // 말줄임표가 하나씩 생긴다
            val on = ((t * 2f).toInt() % 4) > i
            if (on) drawCircle(Color(0xFF6B6680).copy(alpha = a), u * 0.9f, Offset(c.x + (i - 1) * u * 2.6f, c.y + u * 0.6f))
        }
        PetBubble.SWEAT -> {
            val drop = Color(0xFF59A8E8).copy(alpha = a)
            val y = c.y + sin(t * 3f) * u * 0.4f
            val p = Path().apply {
                moveTo(c.x, y - u * 3.4f)
                quadraticBezierTo(c.x + u * 2.6f, y + u * 0.2f, c.x + u * 1.6f, y + u * 1.8f)
                quadraticBezierTo(c.x, y + u * 3.2f, c.x - u * 1.6f, y + u * 1.8f)
                quadraticBezierTo(c.x - u * 2.6f, y + u * 0.2f, c.x, y - u * 3.4f)
                close()
            }
            drawPath(p, drop)
            drawCircle(Color.White.copy(alpha = 0.7f * a), u * 0.55f, Offset(c.x - u * 0.6f, y + u * 0.6f))
        }
        PetBubble.ANGER -> {
            // 만화의 화난 핏줄 표시 (네 모서리 곡선)
            val col = Color(0xFFE5484D).copy(alpha = a)
            val k = 1f + 0.1f * abs(sin(t * 6f))
            val r = u * 1.4f * k
            val g = u * 0.9f
            listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                val o = Offset(c.x + sx * (g + r), c.y + sy * (g + r))
                drawArc(
                    col, startAngle = when {
                        sx < 0 && sy < 0 -> 0f
                        sx > 0 && sy < 0 -> 90f
                        sx > 0 -> 180f
                        else -> 270f
                    },
                    sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(o.x - r, o.y - r), size = Size(r * 2f, r * 2f),
                    style = Stroke(u * 0.9f, cap = StrokeCap.Round),
                )
            }
        }
        PetBubble.ZZZ -> Unit
    }
}

private fun DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.5f, c.y - r * 0.05f, c.x - r * 0.85f, c.y - r * 1.25f, c.x, c.y - r * 0.45f)
        cubicTo(c.x + r * 0.85f, c.y - r * 1.25f, c.x + r * 1.5f, c.y - r * 0.05f, c.x, c.y + r * 0.95f)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawZ(c: Offset, s: Float, color: Color, u: Float) {
    val h = s / 2f
    val p = Path().apply {
        moveTo(c.x - h, c.y - h)
        lineTo(c.x + h, c.y - h)
        lineTo(c.x - h, c.y + h)
        lineTo(c.x + h, c.y + h)
    }
    drawPath(p, color, style = Stroke(u * 0.7f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** 걷는 몸짓: 위로 튀지 않고 좌우로 살짝 기우뚱 (도) */
internal fun trotTilt(step: Float): Float = sin(step * 2f * PI.toFloat()) * 2.2f

/** 걷는 몸짓: 발을 디딜 때 아주 살짝 눌린다 (세로 배율) */
internal fun trotSquash(step: Float): Float = 1f - 0.018f * abs(sin(step * 2f * PI.toFloat()))
