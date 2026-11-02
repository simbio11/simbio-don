package com.simbio.don.app.ui.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simbio.don.app.ui.pixel.PetArt
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.Mood
import com.simbio.don.game.PetExpr
import com.simbio.don.game.PetFur

/**
 * 동물 그림 그리기: 품종·표정 그림을 읽어 털색을 입힌다 ([PetFur]). 결과는 품종·털색·표정마다 기억해 둔다.
 * 그림은 assets/pets (import_pets.py 가 res 로 옮긴다).
 */
object PetRenderer {
    private val cache = LruCache<String, ImageBitmap>(40)

    /** 그림 한 장 (표정: joy smile calm worry sad angry tease sleep) */
    fun bitmap(context: Context, look: CharacterLook, expr: String): ImageBitmap {
        val species = look.kind
        val tone = look.furName
        val key = "${species.id}|$tone|$expr"
        cache.get(key)?.let { return it }
        val res = PetArt.byKey["${species.id}/$expr"] ?: PetArt.byKey.getValue("shiba_mix/calm")
        val src = BitmapFactory.decodeResource(
            context.resources, res,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
        val t = PetFur.tone(tone)
        val out = if (t.identity) src else {
            val px = IntArray(src.width * src.height)
            src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
            for (i in px.indices) px[i] = PetFur.apply(px[i], t)
            Bitmap.createBitmap(px, src.width, src.height, Bitmap.Config.ARGB_8888)
        }
        return out.asImageBitmap().also { cache.put(key, it) }
    }

    fun bitmap(context: Context, look: CharacterLook, mood: Mood, sleeping: Boolean = false): ImageBitmap =
        bitmap(context, look, PetExpr.of(mood, sleeping))

    /** 위젯 같은 곳에 쓸 안드로이드 비트맵 (긴 쪽이 [max]px) */
    fun androidBitmap(context: Context, look: CharacterLook, mood: Mood, max: Int = 360): Bitmap {
        val b = bitmap(context, look, mood).asAndroidBitmap()
        val k = max.toFloat() / maxOf(b.width, b.height)
        return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }

    /** 가로/세로 */
    fun aspect(context: Context, look: CharacterLook): Float = bitmap(context, look, "calm").let { it.width.toFloat() / it.height }
}

/**
 * 살아 있는 동물 한 마리: 숨 쉬듯 몸이 조금 늘었다 줄고, 기분이 좋을수록 통통 튀고, 슬프면 느리게 처진다.
 * [height]는 그림 높이. 그리는 값은 레이아웃 단계가 아니라 그리기 단계에서만 읽어 화면을 다시 만들지 않는다.
 */
@Composable
fun PetSprite(look: CharacterLook, mood: Mood, height: Dp, modifier: Modifier = Modifier, animate: Boolean = true, sleeping: Boolean = false) {
    val context = LocalContext.current
    val img = remember(look, mood, sleeping) { PetRenderer.bitmap(context, look, mood, sleeping) }
    val w = height * img.width / img.height
    if (!animate) {
        Image(img, look.callName, modifier.size(w, height), filterQuality = FilterQuality.Medium)
        return
    }
    val lively = mood == Mood.JOYFUL || mood == Mood.HAPPY || mood == Mood.CHEER
    val gloomy = mood == Mood.SAD || mood == Mood.WORRIED
    val t = rememberInfiniteTransition(label = "pet")
    val breathe = t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (sleeping) 3200 else if (gloomy) 2800 else 2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val hop = t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (lively) 520 else 1000, easing = LinearEasing), RepeatMode.Reverse), label = "hop")
    val sway = t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val dp = LocalDensity.current.density
    Box(modifier.size(w, height + 6.dp)) {
        Image(
            img, look.callName,
            Modifier.size(w, height).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val b = breathe.value
                scaleY = 1f + (if (sleeping) 0.035f else 0.028f) * b
                scaleX = 1f - 0.012f * b
                if (lively) {
                    // 통통: 위로 튀었다 내려오며 착지할 때 살짝 눌린다
                    val h = hop.value
                    translationY = -(height.toPx() * 0.045f) * h * (1f - h) * 4f
                    scaleY *= 1f - 0.03f * (1f - h) * (1f - h)
                }
                if (!sleeping && !gloomy) rotationZ = sway.value * (if (lively) 2.2f else 1.2f)
                translationY += (height.toPx() * 0.02f) + 3f * dp
            },
            filterQuality = FilterQuality.Medium,
        )
    }
}
