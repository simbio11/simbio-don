package com.simbio.don.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 동물 털색 바꾸기. 그림은 모두 주황 갈색 털로 그려져 있어서, 털 색(색상 8°~58°, 어느 정도 선명한 픽셀)만
 * 골라 색상·채도·밝기를 바꾼다. 외곽선·눈·코·볼 터치·눈물·Zzz는 털 색이 아니라서 그대로 둔다.
 */
object PetFur {
    /** [hue]: 바꿀 색상(도, null이면 그대로), [sat]: 채도 곱, [light]: 밝기 L' = L×[mul] + [add] (흰 쪽으로 가려면 [toward1]) */
    data class Tone(val name: String, val hue: Float?, val sat: Float, val mul: Float, val add: Float, val toward1: Float? = null) {
        val identity: Boolean get() = hue == null && sat == 1f && mul == 1f && add == 0f && toward1 == null
    }

    val tones: List<Tone> = listOf(
        Tone("탠", null, 1f, 1f, 0f),
        Tone("치즈", null, 1f, 1f, 0f),
        Tone("크림", 38f, 0.5f, 1f, 0f, toward1 = 0.5f),
        Tone("흰색", 36f, 0.07f, 1f, 0f, toward1 = 0.8f),
        Tone("회색", 30f, 0.05f, 0.95f, 0.02f),
        Tone("은빛", 215f, 0.10f, 1f, 0f, toward1 = 0.6f),
        Tone("검정", 25f, 0.12f, 0.36f, 0.02f),
        Tone("브라운", 22f, 0.8f, 0.62f, 0f),
        Tone("골든", 44f, 1.05f, 0.92f, 0.08f),
        Tone("레드", 11f, 1.15f, 0.86f, 0f),
    )

    fun tone(name: String): Tone = tones.firstOrNull { it.name == name } ?: tones.first()

    /** 그 품종의 털색 목록: 기본이 맨 앞, 강아지·고양이는 어울리는 색만 */
    fun optionsFor(sp: Species): List<String> {
        val dog = listOf("탠", "크림", "흰색", "골든", "브라운", "레드", "회색", "검정")
        val cat = listOf("치즈", "크림", "흰색", "회색", "은빛", "브라운", "검정")
        val all = if (sp.kind == PetKind.CAT) cat else dog
        return if (sp == Species.HUMAN) emptyList() else listOf(sp.fur) + all.filter { it != sp.fur }
    }

    /** 고르는 칸에 보일 대표 색 (원래 털의 중간 색에 이 변환을 건 것) */
    fun swatch(name: String): Int = apply(0xFFE0A058.toInt(), tone(name))

    /** 한 픽셀 (ARGB)에 털색 변환. 털이 아니면 그대로 */
    fun apply(argb: Int, tone: Tone): Int {
        if (tone.identity) return argb
        val a = argb ushr 24
        if (a == 0) return argb
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 0.04f) return argb
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        var h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        if (h < 0) h += 360f
        // 털 색상 범위 (가장자리는 서서히)
        val hw = when {
            h in 14f..52f -> 1f
            h in 8f..14f -> (h - 8f) / 6f
            h in 52f..58f -> (58f - h) / 6f
            else -> 0f
        }
        if (hw == 0f || s < 0.12f) return argb
        // 어두운 곳(외곽선·눈)은 지킨다
        val lw = ((l - 0.24f) / 0.2f).coerceIn(0f, 1f)
        val w = hw * lw
        if (w == 0f) return argb
        val h2 = tone.hue ?: h
        val s2 = (s * tone.sat).coerceIn(0f, 1f)
        var l2 = l * tone.mul + tone.add
        tone.toward1?.let { k -> l2 = 1f - (1f - l2) * (1f - k) }
        l2 = l2.coerceIn(0f, 1f)
        val hh = lerpHue(h, h2, w)
        val ss = s + (s2 - s) * w
        val ll = l + (l2 - l) * w
        return (a shl 24) or hsl(hh, ss, ll)
    }

    private fun lerpHue(a: Float, b: Float, t: Float): Float {
        var dh = b - a
        if (dh > 180f) dh -= 360f
        if (dh < -180f) dh += 360f
        var x = a + dh * t
        if (x < 0) x += 360f
        if (x >= 360f) x -= 360f
        return x
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}

/** 기분 → 어느 표정 그림 (그림 파일의 표정 이름) */
object PetExpr {
    fun of(mood: Mood, sleeping: Boolean = false): String = if (sleeping) "sleep" else when (mood) {
        Mood.JOYFUL -> "joy"
        Mood.HAPPY -> "smile"
        Mood.CALM -> "calm"
        Mood.WORRIED -> "worry"
        Mood.SAD -> "sad"
        Mood.ANGRY -> "angry"
        Mood.CHEER -> "tease"
    }
}
