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
import kotlin.math.abs
import kotlin.math.sin

/** 방에서 동물이 지금 하는 일 (동작 그림: idle·walk·sit·sleep·play·eat) */
enum class PetAct { IDLE, LOOK, WALK, SIT, LIE, PLAY, EAT }

/**
 * [x]: 방 픽셀 좌표(가운데), [faceLeft]: 왼쪽을 보는지,
 * [inAct]: 이 동작을 시작한 지 몇 ms, [left]: 이 동작이 끝나기까지 몇 ms
 */
internal data class PetPose(val x: Float, val act: PetAct, val faceLeft: Boolean, val inAct: Long, val left: Long)

private data class Leg(val start: Long, val end: Long, val fromX: Float, val toX: Float, val act: PetAct)

private val HOME = RoomLayout.character.x + RoomLayout.CHAR_W / 2f
private const val LEFT = 52f
private const val RIGHT = 110f

/** 걷는 빠르기 (방 픽셀/초) — 제자리 걸음 그림이라 너무 빠르면 미끄러져 보인다 */
private fun speed(e: PetEnergy) = when (e) {
    PetEnergy.HIGH -> 15f
    PetEnergy.LOW -> 8f
    else -> 11f
}

private sealed interface PlanStep
private data class Stay(val act: PetAct, val ms: Long) : PlanStep
private data class Go(val x: Float) : PlanStep

private fun build(e: PetEnergy, vararg steps: PlanStep): List<Leg> {
    val out = mutableListOf<Leg>()
    var t = 0L
    var x = HOME
    steps.forEach { st ->
        when (st) {
            is Stay -> { out += Leg(t, t + st.ms, x, x, st.act); t += st.ms }
            is Go -> {
                val ms = (kotlin.math.abs(st.x - x) / speed(e) * 1000).toLong().coerceAtLeast(400)
                out += Leg(t, t + ms, x, st.x, PetAct.WALK)
                t += ms
                x = st.x
            }
        }
    }
    return out
}

/**
 * 하루 일과 (한 바퀴): 서서 두리번 → 걸어가서 앉기 → 일어나 다른 데로 → 놀기 → 돌아와 앉기.
 * 기운이 없으면 오래 앉아 있다가 조금만 걷고 엎드려 쉬고, 신나면 많이 걷고 논다. 자면 엎드려 잔다.
 */
private val PLANS: Map<PetEnergy, List<Leg>> = mapOf(
    PetEnergy.NORMAL to build(
        PetEnergy.NORMAL,
        Stay(PetAct.IDLE, 3_000), Stay(PetAct.LOOK, 1_400), Go(LEFT), Stay(PetAct.SIT, 4_500), Stay(PetAct.IDLE, 1_400),
        Go(RIGHT), Stay(PetAct.IDLE, 2_200), Stay(PetAct.LOOK, 1_400), Go(HOME), Stay(PetAct.SIT, 5_000),
    ),
    PetEnergy.HIGH to build(
        PetEnergy.HIGH,
        Stay(PetAct.IDLE, 1_600), Go(LEFT), Stay(PetAct.PLAY, 2_600), Go(RIGHT), Stay(PetAct.LOOK, 1_200),
        Go(LEFT + 14f), Stay(PetAct.PLAY, 2_200), Go(HOME), Stay(PetAct.IDLE, 2_400),
    ),
    PetEnergy.LOW to build(
        PetEnergy.LOW,
        Stay(PetAct.SIT, 9_000), Stay(PetAct.IDLE, 1_600), Go((HOME + LEFT) / 2f), Stay(PetAct.LIE, 8_000),
        Stay(PetAct.IDLE, 1_200), Go(HOME), Stay(PetAct.SIT, 4_000),
    ),
)

/** [planMs] 시각에 동물이 어디서 무엇을 하는지 ([canWalk]가 아니면 제자리) */
internal fun petPoseAt(planMs: Long, energy: PetEnergy, canWalk: Boolean = true): PetPose {
    if (energy == PetEnergy.SLEEP) return PetPose(HOME, PetAct.LIE, false, planMs, Long.MAX_VALUE)
    val legs = PLANS.getValue(energy)
    if (!canWalk) {
        val act = if (energy == PetEnergy.LOW) PetAct.SIT else PetAct.IDLE
        return PetPose(HOME, act, false, planMs, Long.MAX_VALUE)
    }
    val cycle = legs.last().end
    val t = ((planMs % cycle) + cycle) % cycle
    var faceLeft = legs.last().let { it.toX < it.fromX }
    for (leg in legs) {
        if (leg.toX != leg.fromX) faceLeft = leg.toX < leg.fromX
        if (t < leg.end) {
            val inAct = t - leg.start
            val dur = leg.end - leg.start
            return when (leg.act) {
                PetAct.WALK -> {
                    // 출발·멈춤은 0.3초 동안만 부드럽게 (그 사이 발이 미끄러져 보이지 않게)
                    val ramp = minOf(300f, dur / 3f)
                    val p = walkProgress(inAct.toFloat(), dur.toFloat(), ramp)
                    PetPose(leg.fromX + (leg.toX - leg.fromX) * p, PetAct.WALK, faceLeft, inAct, dur - inAct)
                }
                PetAct.LOOK -> PetPose(leg.toX, PetAct.LOOK, if (inAct in dur * 3 / 10..dur * 3 / 4) !faceLeft else faceLeft, inAct, dur - inAct)
                else -> PetPose(leg.toX, leg.act, faceLeft, inAct, dur - inAct)
            }
        }
    }
    return PetPose(HOME, PetAct.IDLE, faceLeft, 0, 0)
}

/** 처음·끝 [ramp] ms 는 가속·감속, 가운데는 같은 빠르기 (0~1) */
private fun walkProgress(t: Float, dur: Float, ramp: Float): Float {
    val v = 1f / (dur - ramp)
    return when {
        t < ramp -> 0.5f * v * t * t / ramp
        t > dur - ramp -> 1f - 0.5f * v * (dur - t) * (dur - t) / ramp
        else -> 0.5f * v * ramp + v * (t - ramp)
    }.coerceIn(0f, 1f)
}

/**
 * 서 있을 때 그림 순서 (6.4초 한 바퀴): 대부분 1번, 가끔 2번(꼬리), 눈 깜빡(3번), 세 바퀴에 한 번 하품(4번).
 * 그림 번호(0부터)를 돌려준다.
 */
fun idleFrame(ms: Long, n: Int): Int {
    if (n < 2) return 0
    val loop = ms / 6_400
    val t = ms % 6_400
    return when {
        t < 2_200 -> 0
        t < 2_550 -> 1
        t < 2_900 -> 0
        t < 3_250 -> 1
        t < 4_900 -> 0
        t < 5_050 -> if (n > 2) 2 else 0
        loop % 3 == 2L && n > 3 && t < 5_900 -> 3
        else -> 0
    }
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
