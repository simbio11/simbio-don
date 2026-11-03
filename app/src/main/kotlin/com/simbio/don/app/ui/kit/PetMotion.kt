package com.simbio.don.app.ui.kit

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.simbio.don.app.ui.PetAct
import com.simbio.don.app.ui.idleFrame
import com.simbio.don.app.ui.pixel.PetArt
import com.simbio.don.game.CareAnim
import com.simbio.don.game.CharacterLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 한 품종의 그림 묶음: 동작 프레임(idle·walk·sit·sleep·play·eat 중 있는 것)과 표정 8장, 모두 털색을 입힌 것.
 * 모든 그림은 같은 크기 단위 (앉은 키 ≈ [UNIT_H] 픽셀)라 그대로 비율을 맞춰 그리면 된다.
 */
class PetFrames(val anims: Map<String, List<ImageBitmap>>, val faces: Map<String, ImageBitmap>, val facesLeft: Boolean) {
    val canWalk: Boolean get() = "walk" in anims

    /**
     * 지금 그릴 그림과 아래로 내릴 양(그림 픽셀). [face]는 지금 표정 — 기분 표정(슬픔·화남·걱정…)이면
     * 서 있거나 앉아 있을 때 표정 그림을 보여 준다 (동작 그림은 얼굴이 늘 평온해서).
     */
    fun pick(act: PetAct, inAct: Long, left: Long, face: String, now: Long): Pair<ImageBitmap, Float> {
        val moody = face !in setOf("calm", "smile")
        fun f(name: String) = faces[name] ?: faces.getValue("calm")
        fun loop(name: String, fps: Int, ms: Long) = anims[name]?.let { it[((ms * fps / 1000) % it.size).toInt()] }
        return when (act) {
            PetAct.WALK -> {
                val w = anims["walk"] ?: return f(face) to 0f
                val i = ((inAct * PetArt.fps.getValue("walk") / 1000) % w.size).toInt()
                // 발을 디딜 때(짝수 칸) 1픽셀 내려앉는다
                w[i] to if (i % 2 == 0) 2f else 0f
            }
            PetAct.IDLE, PetAct.LOOK -> {
                val idle = anims["idle"]
                if (moody || idle == null) f(face) to 0f else idle[idleFrame(now, idle.size)] to 0f
            }
            PetAct.SIT -> {
                val sit = anims["sit"]
                when {
                    moody || sit == null -> f(face) to 0f
                    // 앉는 중·일어나는 중엔 1번, 나머지는 앉은 자세
                    inAct < 350 || left < 300 -> sit.first() to 0f
                    else -> sit.last() to 0f
                }
            }
            // 엎드려 숨쉬기: 1.6초마다 두 장을 번갈아
            PetAct.LIE -> (anims["sleep"]?.let { it[((now / 1_600) % it.size).toInt()] } ?: f("sleep")) to 0f
            PetAct.PLAY -> (loop("play", PetArt.fps.getValue("play"), inAct) ?: f("joy")) to 0f
            PetAct.EAT -> (loop("eat", PetArt.fps.getValue("eat"), inAct) ?: f("smile")) to 0f
        }
    }

    companion object {
        /** 표정 그림 높이 (앉은 키 + 위아래 1픽셀) — 화면 크기를 정하는 기준 */
        const val UNIT_H = 212f

        fun load(context: Context, look: CharacterLook): PetFrames {
            val m = PetArt.motions[look.kind.id]
            val anims = m?.frames.orEmpty().mapValues { (_, ids) -> ids.map { PetRenderer.frame(context, look, it) } }
            val faces = listOf("joy", "smile", "calm", "worry", "sad", "angry", "tease", "sleep").associateWith { PetRenderer.bitmap(context, look, it) }
            return PetFrames(anims, faces, m?.facesLeft ?: true)
        }
    }
}

/** 품종 그림을 뒤에서 읽어 둔다 (읽는 동안은 null) */
@Composable
fun rememberPetFrames(look: CharacterLook): PetFrames? {
    val context = LocalContext.current
    return produceState<PetFrames?>(null, look) { value = withContext(Dispatchers.Default) { PetFrames.load(context, look) } }.value
}

/** 돌봐 줬을 때 잠깐 보여 줄 동작 */
fun CareAnim.act(): PetAct = when (this) {
    CareAnim.EAT -> PetAct.EAT
    CareAnim.PLAY -> PetAct.PLAY
    CareAnim.SLEEP -> PetAct.LIE
    CareAnim.HAPPY, CareAnim.WALK -> PetAct.IDLE
}

/**
 * [img]를 발바닥([footX], [footY])에 맞춰 그린다. [unit]: 그림 1픽셀의 화면 크기. [flip]이면 좌우 뒤집기.
 */
fun DrawScope.drawPetFrame(img: ImageBitmap, footX: Float, footY: Float, unit: Float, flip: Boolean, colorFilter: androidx.compose.ui.graphics.ColorFilter? = null, scaleY: Float = 1f, rotate: Float = 0f) {
    val w = img.width * unit
    val h = img.height * unit
    withTransform({
        if (rotate != 0f) rotate(rotate, pivot = Offset(footX, footY))
        scale(if (flip) -1f else 1f, scaleY, pivot = Offset(footX, footY))
    }) {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset((footX - w / 2f).toInt(), (footY - h).toInt()),
            dstSize = IntSize(w.toInt(), h.toInt()),
            filterQuality = FilterQuality.Medium,
            colorFilter = colorFilter,
        )
    }
}

/**
 * 돌보기 카드의 동물 초상화: 평소엔 서 있는 동작(숨쉬기·꼬리·눈 깜빡·하품, 기본 얼굴),
 * 돌봐 주면 [reaction] 동작(먹기·놀기·기뻐하기·자기)을 3초쯤 보여 준다.
 */
@Composable
fun PetPortrait(look: CharacterLook, height: Dp, reaction: Pair<CareAnim, Int>?, modifier: Modifier = Modifier) {
    val frames = rememberPetFrames(look)
    if (frames == null) {
        PetSprite(look, com.simbio.don.game.Mood.CALM, height, modifier, expr = "calm")
        return
    }
    val clock = remember { mutableLongStateOf(0L) }
    val reactAt = remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            delay(80)
            clock.longValue = withFrameMillis { it } - start
        }
    }
    LaunchedEffect(reaction) { if (reaction != null) reactAt.longValue = clock.longValue }
    val calm = frames.faces.getValue("calm")
    val width = height * calm.width / calm.height * 1.25f
    Canvas(modifier.size(width, height).semantics { contentDescription = look.callName }) {
        val now = clock.longValue
        val since = now - reactAt.longValue
        val r = reaction?.first?.takeIf { reactAt.longValue >= 0 && since in 0L..3_000L }
        val unit = size.height / PetFrames.UNIT_H * 0.96f
        val (img, _) = if (r != null) {
            frames.pick(r.act(), since, 3_000 - since, if (r == CareAnim.HAPPY || r == CareAnim.WALK) "joy" else "calm", now)
        } else {
            // 서 있는 동작 그림이 없는 품종은 기본 얼굴 그림으로
            frames.pick(PetAct.IDLE, now, Long.MAX_VALUE, "calm", now)
        }
        // 숨쉬기 (아주 살짝)
        val breathe = 1f + 0.012f * (0.5f + 0.5f * kotlin.math.sin(now / 1000f * 2.4f))
        val wiggle = if (r == CareAnim.HAPPY || r == CareAnim.WALK) kotlin.math.sin(since / 1000f * 13f) * 3f * (1f - since / 3_000f) else 0f
        drawPetFrame(img, size.width / 2f, size.height - 1f, unit, false, scaleY = breathe, rotate = wiggle)
    }
}
