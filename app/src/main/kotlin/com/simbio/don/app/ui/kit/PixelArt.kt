package com.simbio.don.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import com.simbio.don.app.R
import com.simbio.don.game.ItemKind
import kotlin.math.roundToInt

// 도트 그림은 게임 안(방·캐릭터·모의로 산 물건)에만 쓴다.

/** 원본 [px] 픽셀짜리 그림을 [size]에 가장 가까운 정수배로 키울 때의 배율 */
@Composable
internal fun pixelScale(px: Int, size: Dp): Int = with(LocalDensity.current) { (size.toPx() / px).roundToInt().coerceAtLeast(1) }

/**
 * 도트 그림을 뭉개지 않고 확대해서 그린다. 기기 픽셀의 정수배로만 키운다
 * (3.94배처럼 소수 배율이면 칸마다 굵기가 달라져 지저분해진다).
 */
@Composable
fun PixelImage(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val img = ImageBitmap.imageResource(id)
    val scale = pixelScale(maxOf(img.width, img.height), size)
    val density = LocalDensity.current
    val w = with(density) { (img.width * scale).toDp() }
    val h = with(density) { (img.height * scale).toDp() }
    Image(img, contentDescription, modifier.size(w, h), filterQuality = FilterQuality.None)
}

@DrawableRes
fun ItemKind.icon(trash: Boolean = false): Int = when (this) {
    ItemKind.CLOTHES -> R.drawable.px_item_clothes
    ItemKind.BAG -> R.drawable.px_item_bag
    ItemKind.SHOES -> R.drawable.px_item_shoes
    ItemKind.ELECTRONICS -> R.drawable.px_item_electronics
    ItemKind.BOOK -> R.drawable.px_item_book
    ItemKind.BEAUTY -> R.drawable.px_item_beauty
    ItemKind.LIVING -> R.drawable.px_item_living
    ItemKind.TOY -> R.drawable.px_item_toy
    ItemKind.MEAL -> if (trash) R.drawable.px_item_meal_trash else R.drawable.px_item_meal
    ItemKind.DRINK -> if (trash) R.drawable.px_item_drink_trash else R.drawable.px_item_drink
    ItemKind.ETC -> R.drawable.px_item_etc
}

@Composable
fun ItemIcon(kind: ItemKind, size: Dp, trash: Boolean = false, modifier: Modifier = Modifier) =
    PixelImage(kind.icon(trash), size, modifier)

/** 카드·가게용 16×16 큰 그림 (방 안은 12×12) */
@DrawableRes
fun ItemKind.icon16(trash: Boolean = false): Int = when (this) {
    ItemKind.CLOTHES -> R.drawable.px16_item_clothes
    ItemKind.BAG -> R.drawable.px16_item_bag
    ItemKind.SHOES -> R.drawable.px16_item_shoes
    ItemKind.ELECTRONICS -> R.drawable.px16_item_electronics
    ItemKind.BOOK -> R.drawable.px16_item_book
    ItemKind.BEAUTY -> R.drawable.px16_item_beauty
    ItemKind.LIVING -> R.drawable.px16_item_living
    ItemKind.TOY -> R.drawable.px16_item_toy
    ItemKind.MEAL -> if (trash) R.drawable.px16_item_meal_trash else R.drawable.px16_item_meal
    ItemKind.DRINK -> if (trash) R.drawable.px16_item_drink_trash else R.drawable.px16_item_drink
    ItemKind.ETC -> R.drawable.px16_item_etc
}

@Composable
fun ItemIcon16(kind: ItemKind, size: Dp, modifier: Modifier = Modifier) = PixelImage(kind.icon16(), size, modifier)

/** 상품 사진 자리: 옅은 회색 둥근 칸 가운데 도트 물건 */
@Composable
fun ItemThumb(kind: ItemKind, size: Dp, modifier: Modifier = Modifier, fill: Color = Color(0xFFF2F4F6)) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 5)).background(fill), contentAlignment = Alignment.Center) {
        ItemIcon16(kind, size * 0.62f)
    }
}

/** 캐릭터 대기 동작: 3초마다 눈 깜빡임 + 한 칸 숨쉬기. */
data class IdleFrame(val blink: Boolean, val bob: Int)

@Composable
fun rememberIdleFrame(): IdleFrame {
    val t = rememberInfiniteTransition(label = "idle")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase")
    return IdleFrame(blink = phase > 0.94f, bob = if ((phase * 4).toInt() % 2 == 0) 0 else 1)
}
