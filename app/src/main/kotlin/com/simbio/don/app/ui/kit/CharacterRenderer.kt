package com.simbio.don.app.ui.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.simbio.don.app.R
import com.simbio.don.app.ui.pixel.CharacterParts
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.Mood

/** 지금 내 캐릭터 모습 (화면 어디서나 같은 캐릭터를 그리게) */
val LocalCharacterLook = compositionLocalOf { CharacterLook.MALE }

/** 캐릭터 한 장의 동작 */
enum class Pose { STAND, BLINK, WALK_LEFT, WALK_RIGHT }

/**
 * 캐릭터 v3 그리기: 부위 아틀라스(px_char_atlas)에서 부위를 골라 표식 색을 고른 색으로 바꿔 겹친다.
 * 그린 결과는 모습·기분·동작마다 기억해 둔다.
 */
object CharacterRenderer {
    const val W = CharacterParts.W
    const val H = CharacterParts.H

    private var atlas: IntArray? = null
    private var atlasW = 0
    private val cache = LruCache<String, ImageBitmap>(160)

    private fun atlas(context: Context): IntArray {
        atlas?.let { return it }
        val bmp = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.px_char_atlas,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        atlasW = bmp.width
        atlas = px
        return px
    }

    private fun ramp(list: List<Pair<String, IntArray>>, name: String): IntArray =
        list.firstOrNull { it.first == name }?.second ?: list.first().second

    /** 표식 색 → 고른 색 */
    private fun colorMap(look: CharacterLook): Map<Int, Int> {
        val m = HashMap<Int, Int>()
        fun put(markers: IntArray, colors: IntArray, pick: IntArray = IntArray(markers.size) { it }) {
            markers.forEachIndexed { i, marker -> m[marker] = colors[pick[i].coerceAtMost(colors.lastIndex)] }
        }
        put(CharacterParts.SKIN, ramp(CharacterParts.skinTones, look.skin))
        put(CharacterParts.HAIR, ramp(CharacterParts.hairColors, look.hairColor))
        put(CharacterParts.CLOTH1, ramp(CharacterParts.clothColors, look.topColor))
        put(CharacterParts.CLOTH2, ramp(CharacterParts.clothColors, look.bottomColor))
        // 신발: 밝음·그늘·밑창 ← 옷 색의 1·3·4번
        put(CharacterParts.SHOES, ramp(CharacterParts.clothColors, look.shoesColor), intArrayOf(0, 2, 3))
        put(CharacterParts.EYE, ramp(CharacterParts.eyeColors, look.eyeColor))
        put(CharacterParts.HAT, ramp(CharacterParts.hatColors, look.hatColor))
        return m
    }

    /** 그릴 부위 이름 (아래부터 위로) */
    fun layers(look: CharacterLook, mood: Mood, blink: Boolean): List<String> {
        val face = CharacterParts.moods[mood.name.lowercase()] ?: CharacterParts.moods.getValue("calm")
        var eyes = face.eyes
        if (blink && eyes in setOf("open", "angry", "sad")) eyes = "closed"
        val dress = CharacterParts.tops.firstOrNull { it.id == look.top }?.flag == true
        val mouth = if (face.mouth == "smile" || face.mouth == "calm") "mouth:${look.mouth}:${face.mouth}" else "mouth:shared:${face.mouth}"
        return buildList {
            // 짧은 머리는 뒷머리가 없다
            "hair:${look.hair}:back".let { if (it in CharacterParts.index) add(it) }
            add("body:${look.face}")
            if (!dress) add("bottom:${look.bottom}")
            add("shoes:${look.shoes}")
            add("top:${look.top}")
            add("blush")
            add(if (eyes in CharacterParts.sharedEyes) "eyes:shared:$eyes" else "eyes:${look.eyes}:$eyes")
            if (look.lashes && eyes !in CharacterParts.sharedEyes) add("lashes")
            add("nose:${look.nose}")
            add(mouth)
            add("hair:${look.hair}:front")
            add("brows:${look.brow}:${face.brow}")
            face.extra?.let { add("extra:$it") }
            look.hat?.let { add("hat:$it") }
            look.acc?.let { add("acc:$it") }
        }
    }

    /** 픽셀 배열 (W×H, ARGB) */
    fun pixels(context: Context, look: CharacterLook, mood: Mood, pose: Pose = Pose.STAND): IntArray {
        val src = atlas(context)
        val map = colorMap(look)
        val out = IntArray(W * H)
        for (name in layers(look, mood, pose == Pose.BLINK)) {
            val i = CharacterParts.index[name] ?: continue
            val ox = (i % CharacterParts.COLS) * W
            val oy = (i / CharacterParts.COLS) * H
            for (y in 0 until H) {
                val row = (oy + y) * atlasW + ox
                for (x in 0 until W) {
                    val c = src[row + x]
                    if (c ushr 24 == 0) continue
                    out[y * W + x] = map[c] ?: c
                }
            }
        }
        if (pose == Pose.WALK_LEFT || pose == Pose.WALK_RIGHT) liftFoot(out, pose == Pose.WALK_LEFT)
        return out
    }

    /** 걷기: 한쪽 종아리·발을 한 줄 들어 올린다 */
    private fun liftFoot(px: IntArray, left: Boolean) {
        val xs = if (left) 0 until W / 2 else W / 2 until W
        val top = CharacterParts.TOP + 28
        for (x in xs) {
            for (y in top until H) px[(y - 1) * W + x] = px[y * W + x].takeIf { it ushr 24 != 0 } ?: px[(y - 1) * W + x].takeIf { y - 1 < top } ?: 0
            px[(H - 1) * W + x] = 0
        }
    }

    fun bitmap(context: Context, look: CharacterLook, mood: Mood, pose: Pose = Pose.STAND): ImageBitmap {
        val key = "${look.encode()}|${mood.name}|${pose.name}"
        cache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(pixels(context, look, mood, pose), W, H, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }
}

/** 캐릭터 한 명 (24×36 그림). 높이에 가장 가까운 정수배로 그리고, 숨쉬듯 깜빡인다. */
@Composable
fun CharacterSprite(mood: Mood, height: Dp, modifier: Modifier = Modifier, look: CharacterLook = LocalCharacterLook.current, animate: Boolean = true) {
    val context = LocalContext.current
    val f = if (animate) rememberIdleFrame() else IdleFrame(false, 0)
    val scale = pixelScale(CharacterRenderer.H, height)
    val unit = with(LocalDensity.current) { scale.toDp() }
    val stand = remember(look, mood) { CharacterRenderer.bitmap(context, look, mood, Pose.STAND) }
    val blink = remember(look, mood) { CharacterRenderer.bitmap(context, look, mood, Pose.BLINK) }
    Box(modifier.size(unit * CharacterRenderer.W, unit * (CharacterRenderer.H + 1))) {
        Image(
            if (f.blink) blink else stand,
            contentDescription = mood.label,
            modifier = Modifier.offset(y = unit * f.bob).size(unit * CharacterRenderer.W, unit * CharacterRenderer.H),
            filterQuality = FilterQuality.None,
        )
    }
}

/** 꾸미기 칸 미리보기. [head]면 얼굴 쪽만 (모자 자리부터 어깨 위까지 24×24) */
@Composable
fun CharacterThumb(look: CharacterLook, size: Dp, head: Boolean, modifier: Modifier = Modifier, mood: Mood = Mood.CALM, label: String? = null) {
    val context = LocalContext.current
    val bmp = remember(look, mood) { CharacterRenderer.bitmap(context, look, mood) }
    val rows = if (head) CharacterRenderer.W else CharacterRenderer.H
    val scale = pixelScale(rows, size)
    val density = LocalDensity.current
    val w = with(density) { (CharacterRenderer.W * scale).toDp() }
    val h = with(density) { (rows * scale).toDp() }
    Canvas(modifier.size(w, h).semantics { label?.let { contentDescription = it } }) {
        drawImage(
            bmp,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(CharacterRenderer.W, rows),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(CharacterRenderer.W * scale, rows * scale),
            filterQuality = FilterQuality.None,
        )
    }
}
