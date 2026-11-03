package com.simbio.don.app.ui.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.LaunchedEffect
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
    /** 털색 입힌 그림 (품종·털색·그림마다). 동작 프레임까지 담으니 크기(바이트)로 센다 — 약 40MB */
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** 그림 한 장 (표정: joy smile calm worry sad angry tease sleep) */
    fun bitmap(context: Context, look: CharacterLook, expr: String): ImageBitmap {
        val res = PetArt.byKey["${look.kind.id}/$expr"] ?: PetArt.byKey.getValue("shiba_mix/calm")
        return frame(context, look, res)
    }

    /** 그림 파일 하나에 털색을 입힌 것 (표정 그림·동작 프레임 모두) */
    fun frame(context: Context, look: CharacterLook, res: Int): ImageBitmap {
        val tone = look.furName
        val key = "${look.kind.id}|$tone|$res"
        cache.get(key)?.let { return it }
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
    fun androidBitmap(context: Context, look: CharacterLook, mood: Mood, max: Int = 360): Bitmap = androidBitmap(context, look, PetExpr.of(mood), max)

    fun androidBitmap(context: Context, look: CharacterLook, expr: String, max: Int = 360): Bitmap {
        val b = bitmap(context, look, expr).asAndroidBitmap()
        val k = max.toFloat() / maxOf(b.width, b.height)
        return if (k >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }

    /** 가로/세로 */
    fun aspect(context: Context, look: CharacterLook): Float = bitmap(context, look, "calm").let { it.width.toFloat() / it.height }
}

/**
 * 살아 있는 동물 한 마리: 숨 쉬듯 몸이 조금 늘었다 줄고, 가끔 고개를 갸웃한다 (튀지 않는다).
 * [expr]가 있으면 그 표정, 없으면 [mood] 표정. [height]는 그림 높이.
 * 그리는 값은 그리기 단계에서만 읽어 화면을 다시 만들지 않는다.
 */
@Composable
fun PetSprite(
    look: CharacterLook,
    mood: Mood,
    height: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    sleeping: Boolean = false,
    expr: String? = null,
    /** 0이 아니면 바뀔 때마다 몸을 살랑살랑 (돌봐 줬을 때) */
    wiggle: Int = 0,
) {
    val context = LocalContext.current
    val face = expr ?: PetExpr.of(mood, sleeping)
    val img = remember(look, face) { PetRenderer.bitmap(context, look, face) }
    val w = height * img.width / img.height
    if (!animate) {
        Image(img, look.callName, modifier.size(w, height), filterQuality = FilterQuality.Medium)
        return
    }
    val slow = face == "sleep" || face == "sad" || face == "worry"
    val t = rememberInfiniteTransition(label = "pet")
    val breathe = t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (face == "sleep") 3200 else if (slow) 2600 else 2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    // 갸웃: 6초에 한 번 살짝 기울였다 돌아온다
    val tilt = t.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 6000
                0f at 0
                0f at 3800
                3f at 4200 using FastOutSlowInEasing
                3f at 4900
                0f at 5400 using FastOutSlowInEasing
            },
        ),
        label = "tilt",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wiggle) {
        if (wiggle != 0) {
            shake.snapTo(1f)
            shake.animateTo(0f, tween(1400, easing = LinearEasing))
        }
    }
    val dp = LocalDensity.current.density
    Box(modifier.size(w, height + 6.dp)) {
        Image(
            img, look.callName,
            Modifier.size(w, height).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val b = breathe.value
                scaleY = 1f + (if (face == "sleep") 0.035f else 0.025f) * b
                scaleX = 1f - 0.01f * b
                val s = shake.value
                rotationZ = if (s > 0f) kotlin.math.sin((1f - s) * 40f) * 4f * s else if (face == "sleep") 0f else tilt.value
                translationY = (height.toPx() * 0.02f) + 3f * dp
            },
            filterQuality = FilterQuality.Medium,
        )
    }
}
