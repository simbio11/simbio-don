package com.simbio.don.app.ui.pixel

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import android.graphics.BitmapFactory
import com.simbio.don.game.Mood

/**
 * 임시 도트 그림. 글자 하나가 픽셀 하나이고, '.'은 투명.
 *
 * 나중에 진짜 그림으로 바꿀 때는 `res/drawable-nodpi/sprite_<이름>.png`를 넣기만 하면 된다
 * (이름 목록과 크기는 docs/art-spec.md). 넣은 그림이 있으면 그걸, 없으면 아래 격자를 그린다.
 */
object Sprites {
    private val palette = mapOf(
        'k' to Color(0xFF2B2B2B), // 외곽선
        'h' to Color(0xFF5A3A22), // 머리
        's' to Color(0xFFF2C9A0), // 피부
        'e' to Color(0xFF1E1E1E), // 눈
        'm' to Color(0xFFB4523C), // 입
        'b' to Color(0xFFF09A9A), // 볼
        'c' to Color(0xFF4A6FD1), // 윗옷
        'p' to Color(0xFF2B3A67), // 바지
        'w' to Color(0xFFFFFFFF),
        'd' to Color(0xFF7FC4F0), // 땀방울
        'y' to Color(0xFFF4C95D), // 반짝
    )

    private val hair = listOf(
        "...kkkkkk...",
        "..khhhhhhk..",
    )
    private val face = listOf(
        ".khhhhhhhhk.",
        ".khsssssshk.",
        ".kssssssssk.",
    )
    private val eyes = ".ksessssesk."
    private val blush = ".kbssssssbk."
    private val body = listOf(
        "...kssssk...",
        "..kcccccck..",
        ".kcccccccck.",
        "ksccccccccsk",
        "ks.cccccc.sk",
        "..kppppppk..",
        "..kpp..ppk..",
        "..kkk..kkk..",
    )

    val character: Map<Mood, List<String>> = mapOf(
        Mood.HAPPY to hair + face + listOf(eyes, blush, ".kssmmmmssk.", "..ksmmmmsk..") + body,
        Mood.CALM to hair + face + listOf(eyes, blush, ".ksssmmsssk.", "..kssssssk..") + body,
        Mood.WORRIED to hair + listOf(".khhhhhhhhkd", ".khsssssshkd", ".kssssssssk.") +
            listOf(eyes, ".kssssssssk.", ".kssmsmsssk.", "..kssssssk..") + body,
        Mood.CHEER to listOf("y..kkkkkk..y", "..khhhhhhk..") + face +
            listOf(".kesssssesk.", blush, ".ksssmmsssk.", "..kssssssk..") + body,
    )

    /** 모든 격자가 직사각형인지 (테스트용) */
    fun allGrids(): Map<String, List<String>> = character.mapKeys { "character_${it.key.name.lowercase()}" }

    fun color(ch: Char): Color? = palette[ch]
}

/** 격자 또는 넣은 PNG를 그린다. */
@SuppressLint("DiscouragedApi")
@Composable
fun Sprite(name: String, grid: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier("sprite_$name", "drawable", context.packageName)
    if (resId != 0) {
        val bmp = BitmapFactory.decodeResource(context.resources, resId)
        Image(BitmapPainter(bmp.asImageBitmap(), filterQuality = FilterQuality.None), contentDescription = null, modifier = modifier.size(size))
        return
    }
    Canvas(modifier.size(size)) {
        val rows = grid.size
        val cols = grid.maxOf { it.length }
        val px = minOf(this.size.width / cols, this.size.height / rows)
        val ox = (this.size.width - px * cols) / 2
        val oy = (this.size.height - px * rows) / 2
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                Sprites.color(ch)?.let { drawRect(it, Offset(ox + x * px, oy + y * px), Size(px + 0.5f, px + 0.5f)) }
            }
        }
    }
}

@Composable
fun Character(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    Sprite("character_${mood.name.lowercase()}", Sprites.character.getValue(mood), size, modifier)
}
