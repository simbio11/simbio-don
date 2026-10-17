package com.simbio.don.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.R
import com.simbio.don.app.ui.DonColors
import com.simbio.don.budget.Category
import com.simbio.don.game.ItemKind
import com.simbio.don.game.Mood

// ── 도트 패널 ───────────────────────────────────────────────

/** 모서리 한 칸씩 깎은 사각형 (도트 그림의 둥근 모서리) */
internal fun notched(x: Float, y: Float, w: Float, h: Float, s: Float) = Path().apply {
    moveTo(x + s, y)
    lineTo(x + w - s, y)
    lineTo(x + w - s, y + s)
    lineTo(x + w, y + s)
    lineTo(x + w, y + h - s)
    lineTo(x + w - s, y + h - s)
    lineTo(x + w - s, y + h)
    lineTo(x + s, y + h)
    lineTo(x + s, y + h - s)
    lineTo(x, y + h - s)
    lineTo(x, y + s)
    lineTo(x + s, y + s)
    close()
}

/**
 * 도트 패널: 짙은 외곽선 + 모서리 깎기 + 아래쪽 단단한 그림자 + 위쪽 밝은 선.
 * 그림자 자리([depth])는 레이아웃 아래쪽에 미리 비워 둔다.
 */
fun Modifier.pixelPanel(
    fill: Color,
    border: Color = DonColors.Ink,
    depth: Dp = 3.dp,
    edge: Dp = 2.dp,
): Modifier = this
    .padding(bottom = depth)
    .drawBehind {
        val e = edge.toPx()
        val d = depth.toPx()
        val w = size.width
        val h = size.height
        if (d > 0f) drawPath(notched(0f, d, w, h, e), border.copy(alpha = 0.9f))
        drawPath(notched(0f, 0f, w, h, e), border)
        drawPath(notched(e, e, w - 2 * e, h - 2 * e, e), fill)
        drawRect(Color.White.copy(alpha = 0.45f), Offset(2 * e, e), Size(w - 4 * e, e))
    }

// ── 버튼 ───────────────────────────────────────────────────

@Composable
private fun PixelButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    content: Color,
    minHeight: Dp,
    horizontal: Dp,
    fontSize: Int,
    depth: Dp,
    row: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = pressed && enabled
    Box(
        modifier
            .padding(top = if (down) depth - 1.dp else 0.dp)
            .pixelPanel(if (enabled) fill else DonColors.Line, depth = if (down) 1.dp else depth)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = minHeight),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(
            TextStyle(color = if (enabled) content else DonColors.InkMuted, fontWeight = FontWeight.Bold, fontSize = fontSize.sp),
        ) {
            Row(
                Modifier.padding(horizontal = horizontal, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = row,
            )
        }
    }
}

/** 주 버튼 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DonColors.Real,
    contentColor: Color = Color.White,
    content: @Composable RowScope.() -> Unit,
) = PixelButtonBase(onClick, modifier, enabled, containerColor, contentColor, 46.dp, 18.dp, 15, 4.dp, content)

/** 보조 버튼 (밝은 바탕) */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = PixelButtonBase(onClick, modifier, enabled, DonColors.PaperLight, DonColors.Ink, 42.dp, 14.dp, 14, 3.dp, content)

/** 글자만 있는 버튼 (돌아가기 등) */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    ProvideTextStyle(TextStyle(color = if (enabled) DonColors.InkSoft else DonColors.InkMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
        Row(
            modifier
                .defaultMinSize(minHeight = 40.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 선택 칩 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .pixelPanel(
                when {
                    !enabled -> DonColors.Line
                    selected -> DonColors.Highlight
                    else -> DonColors.PaperLight
                },
                depth = if (selected) 1.dp else 2.dp,
            )
            .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(
            TextStyle(
                color = if (enabled) DonColors.Ink else DonColors.InkMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
            ),
        ) { label() }
    }
}

/** 둥근 + 버튼 대신 도트 버튼 */
@Composable
fun PixelFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(60.dp)
            .pixelPanel(DonColors.Highlight, depth = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Text("+", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink) }
}

// ── 도트 그림 ───────────────────────────────────────────────

/** 도트 그림을 뭉개지 않고 확대해서 그린다. */
@Composable
fun PixelImage(@DrawableRes id: Int, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(ImageBitmap.imageResource(id), contentDescription, modifier.size(size), filterQuality = FilterQuality.None)
}

enum class UiIcon(@DrawableRes val res: Int) {
    HOME(R.drawable.px_ui_home),
    LIST(R.drawable.px_ui_list),
    HOUSE(R.drawable.px_ui_house),
    CHART(R.drawable.px_ui_chart),
    GEAR(R.drawable.px_ui_gear),
    COIN(R.drawable.px_ui_coin),
    WARN(R.drawable.px_ui_warn),
    BOX(R.drawable.px_ui_box),
    STAR(R.drawable.px_ui_star),
}

@Composable
fun Icon(icon: UiIcon, size: Dp = 18.dp, modifier: Modifier = Modifier) = PixelImage(icon.res, size, modifier)

@DrawableRes
fun Category.icon(): Int = when (this) {
    Category.FOOD -> R.drawable.px_cat_food
    Category.CAFE -> R.drawable.px_cat_cafe
    Category.DELIVERY -> R.drawable.px_cat_delivery
    Category.TRANSPORT -> R.drawable.px_cat_transport
    Category.SHOPPING -> R.drawable.px_cat_shopping
    Category.LIVING -> R.drawable.px_cat_living
    Category.HEALTH -> R.drawable.px_cat_health
    Category.CULTURE -> R.drawable.px_cat_culture
    Category.EVENT -> R.drawable.px_cat_event
    Category.EDUCATION -> R.drawable.px_cat_education
    Category.ETC -> R.drawable.px_cat_etc
}

@Composable
fun CategoryIcon(c: Category, size: Dp = 18.dp, modifier: Modifier = Modifier) = PixelImage(c.icon(), size, modifier)

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

@DrawableRes
fun Mood.sprite(blink: Boolean): Int = when (this) {
    Mood.HAPPY -> if (blink) R.drawable.px_char_happy_1 else R.drawable.px_char_happy_0
    Mood.CALM -> if (blink) R.drawable.px_char_calm_1 else R.drawable.px_char_calm_0
    Mood.WORRIED -> if (blink) R.drawable.px_char_worried_1 else R.drawable.px_char_worried_0
    Mood.CHEER -> if (blink) R.drawable.px_char_cheer_1 else R.drawable.px_char_cheer_0
}

/** 캐릭터 대기 동작: 3초마다 눈 깜빡임 + 한 칸 숨쉬기. 테스트에서는 멈춰 있다. */
data class IdleFrame(val blink: Boolean, val bob: Int)

@Composable
fun rememberIdleFrame(): IdleFrame {
    val t = rememberInfiniteTransition(label = "idle")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase")
    return IdleFrame(blink = phase > 0.94f, bob = if ((phase * 4).toInt() % 2 == 0) 0 else 1)
}

/** 캐릭터 한 명 (16×24 그림) */
@Composable
fun CharacterSprite(mood: Mood, height: Dp, modifier: Modifier = Modifier) {
    val f = rememberIdleFrame()
    val px = height / 24
    Box(modifier.size(px * 16, height + px)) {
        Image(
            ImageBitmap.imageResource(mood.sprite(f.blink)),
            contentDescription = mood.label,
            modifier = Modifier.offset(y = px * f.bob).size(px * 16, height),
            filterQuality = FilterQuality.None,
        )
    }
}

// ── 경고 한 줄 ─────────────────────────────────────────────

@Composable
fun WarnLine(text: String, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(UiIcon.WARN, 16.dp)
        Text(text, color = DonColors.Warn, fontSize = fontSize.sp)
    }
}

// ── 하단 탭 ────────────────────────────────────────────────

data class TabItem(val label: String, val icon: UiIcon)

@Composable
fun PixelTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DonColors.PaperLight)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(DonColors.Ink))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .let { if (on) it.pixelPanel(DonColors.Highlight, depth = 2.dp) else it.padding(bottom = 2.dp) }
                        .clickable(role = Role.Tab, onClick = { onSelect(i) })
                        .padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(item.icon, 24.dp)
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) DonColors.Ink else DonColors.InkSoft,
                    )
                }
            }
        }
    }
}
