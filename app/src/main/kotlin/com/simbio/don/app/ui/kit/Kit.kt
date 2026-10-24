package com.simbio.don.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
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

// ── 카드 ────────────────────────────────────────────────────

/** 둥근 카드 바탕 */
fun Modifier.card(color: Color = DonColors.Surface, radius: Dp = 16.dp): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color)

/** 처음 나타날 때 톡 튀어나오는 효과 */
@Composable
fun Modifier.popIn(): Modifier {
    val s = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    return this.graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

// ── 버튼 ───────────────────────────────────────────────────

/** 누르는 동안 살짝 줄어드는 바탕 버튼 */
@Composable
private fun BaseButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    content: Color,
    minHeight: Dp,
    horizontal: Dp,
    fontSize: Int,
    shape: Shape,
    row: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, label = "press")
    Box(
        modifier
            .scale(s)
            .clip(shape)
            .background(if (enabled) fill else DonColors.Line)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = minHeight),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(
            TextStyle(color = if (enabled) content else DonColors.InkFaint, fontWeight = FontWeight.Bold, fontSize = fontSize.sp),
        ) {
            Row(
                Modifier.padding(horizontal = horizontal, vertical = 10.dp),
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
) = BaseButton(onClick, modifier, enabled, containerColor, contentColor, 52.dp, 20.dp, 16, RoundedCornerShape(14.dp), content)

/** 보조 버튼 (회색 바탕) */
@Composable
fun SubButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DonColors.SurfaceAlt,
    contentColor: Color = DonColors.InkSoft,
    content: @Composable RowScope.() -> Unit,
) = BaseButton(onClick, modifier, enabled, containerColor, contentColor, 48.dp, 16.dp, 15, RoundedCornerShape(12.dp), content)

/** 작은 버튼 (알림 띠, 카드 안) */
@Composable
fun SmallButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = DonColors.Real,
    contentColor: Color = Color.White,
    content: @Composable RowScope.() -> Unit,
) = BaseButton(onClick, modifier, true, containerColor, contentColor, 36.dp, 12.dp, 13, RoundedCornerShape(10.dp), content)

/** 글자만 있는 버튼 (돌아가기, 더보기) */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = DonColors.InkSoft,
    content: @Composable RowScope.() -> Unit,
) {
    ProvideTextStyle(TextStyle(color = if (enabled) color else DonColors.InkFaint, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)) {
        Row(
            modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** "전체 보기 >" 같은 더보기 글자 */
@Composable
fun MoreButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier, color = DonColors.InkMuted) {
        Text(text, fontSize = 13.sp)
        Icon(UiIcon.CHEVRON_RIGHT, 16.dp, DonColors.InkMuted)
    }
}

/** 화면 맨 위 "← 제목" 줄 */
@Composable
fun BackBar(title: String = "", onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "뒤로" },
            contentAlignment = Alignment.Center,
        ) { Icon(UiIcon.BACK, 22.dp, DonColors.Ink) }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DonColors.Ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 둥근 알약 선택 칩 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) DonColors.Ink else DonColors.Surface)
            .let { if (selected) it else it.border(1.dp, DonColors.Line, shape) }
            .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(
            TextStyle(
                color = when {
                    !enabled -> DonColors.InkFaint
                    selected -> Color.White
                    else -> DonColors.InkSoft
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
            ),
        ) { label() }
    }
}

/** 작은 알약 뱃지 */
@Composable
fun Badge(text: String, color: Color = DonColors.Real, fill: Color = color.copy(alpha = 0.1f), modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(fill).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** + 버튼 */
@Composable
fun AddFab(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        containerColor = DonColors.Real,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(6.dp, 8.dp),
    ) { Icon(UiIcon.ADD, 28.dp, Color.White) }
}

// ── 아이콘 (Material Symbols Rounded, Apache 2.0) ────────────

enum class UiIcon(@DrawableRes val res: Int) {
    HOME(R.drawable.ic_home),
    HOME_ON(R.drawable.ic_home_fill),
    LIST(R.drawable.ic_receipt_long),
    LIST_ON(R.drawable.ic_receipt_long_fill),
    HOUSE(R.drawable.ic_cottage),
    HOUSE_ON(R.drawable.ic_cottage_fill),
    CHART(R.drawable.ic_bar_chart),
    CHART_ON(R.drawable.ic_bar_chart_fill),
    GEAR(R.drawable.ic_settings),
    GEAR_ON(R.drawable.ic_settings_fill),
    ADD(R.drawable.ic_add),
    BACK(R.drawable.ic_arrow_back_ios_new),
    CHEVRON_LEFT(R.drawable.ic_chevron_left),
    CHEVRON_RIGHT(R.drawable.ic_chevron_right),
    CLOSE(R.drawable.ic_close),
    INFO(R.drawable.ic_info),
    DELETE(R.drawable.ic_delete),
    EDIT(R.drawable.ic_edit),
    CALENDAR(R.drawable.ic_calendar_month),
    DOWNLOAD(R.drawable.ic_download),
    WARN(R.drawable.ic_warning_fill),
    CHECK(R.drawable.ic_check_circle_fill),
    BELL(R.drawable.ic_notifications_active_fill),
    TRUCK(R.drawable.ic_local_shipping_fill),
    SAVINGS(R.drawable.ic_savings_fill),
    PAYMENTS(R.drawable.ic_payments_fill),
    CARD(R.drawable.ic_credit_card_fill),
    BOX(R.drawable.ic_inventory_2_fill),
    RECYCLE(R.drawable.ic_recycling_fill),
    TAG(R.drawable.ic_sell_fill),
    STORE(R.drawable.ic_storefront_fill),
    PARTY(R.drawable.ic_celebration_fill),
    WALLET(R.drawable.ic_account_balance_wallet_fill),
    TROPHY(R.drawable.ic_emoji_events_fill),
    STAR(R.drawable.ic_star_fill),
    CLOCK(R.drawable.ic_schedule_fill),
    SEARCH(R.drawable.ic_search),
    HEART(R.drawable.ic_favorite),
    HEART_ON(R.drawable.ic_favorite_fill),
    HOURGLASS(R.drawable.ic_hourglass_top_fill),
    NOTIFY(R.drawable.ic_notifications),
    WIDGET(R.drawable.ic_widgets_fill),
    BOLT(R.drawable.ic_bolt_fill),
    FIRE(R.drawable.ic_local_fire_department_fill),
    FLAG(R.drawable.ic_flag_fill),
    FLIGHT(R.drawable.ic_flight_fill),
    COMPUTER(R.drawable.ic_computer_fill),
    CAR(R.drawable.ic_directions_car_fill),
    DIAMOND(R.drawable.ic_diamond_fill),
    TREND_UP(R.drawable.ic_trending_up),
    UP(R.drawable.ic_arrow_upward),
    DOWN(R.drawable.ic_arrow_downward),
    PET(R.drawable.ic_pets_fill),
    CLOTHES(R.drawable.ic_checkroom_fill),
    SOFA(R.drawable.ic_weekend_fill),
    BANK(R.drawable.ic_account_balance),
    BANK_ON(R.drawable.ic_account_balance_fill),
    TRANSFER(R.drawable.ic_swap_horiz),
    REPEAT(R.drawable.ic_event_repeat),
    UPLOAD(R.drawable.ic_upload),
    RESTORE(R.drawable.ic_settings_backup_restore),
    LOCK(R.drawable.ic_lock),
    MORE(R.drawable.ic_more_horiz),
    PIE(R.drawable.ic_pie_chart),
    PIE_ON(R.drawable.ic_pie_chart_fill),
    EXPAND(R.drawable.ic_expand_more),
    TODAY(R.drawable.ic_today),
    CARD_LINE(R.drawable.ic_credit_card),
    WALLET_LINE(R.drawable.ic_account_balance_wallet),
    ADD_CIRCLE(R.drawable.ic_add_circle_fill),
    TUNE(R.drawable.ic_tune),
    NOTE(R.drawable.ic_edit_note),
    CASH(R.drawable.ic_payments),
    ARROW_RIGHT(R.drawable.ic_arrow_forward),
}

@Composable
fun Icon(icon: UiIcon, size: Dp = 20.dp, tint: Color = DonColors.InkSoft, modifier: Modifier = Modifier) =
    androidx.compose.material3.Icon(painterResource(icon.res), contentDescription = null, modifier = modifier.size(size), tint = tint)

/** 동그라미 안 아이콘 (알림 띠, 목록 머리) */
@Composable
fun IconBubble(icon: UiIcon, tint: Color, size: Dp = 40.dp, fill: Color = tint.copy(alpha = 0.12f)) {
    Box(Modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, size * 0.55f, tint)
    }
}

// ── 카테고리 ───────────────────────────────────────────────

/** 카테고리 아이콘과 알아보기 쉬운 색 (아이콘 타일에만 쓴다 — 차트는 한 색) */
private data class CategoryLook(@DrawableRes val res: Int, val tint: Color)

/** 직접 만든 카테고리에 고를 수 있는 아이콘 (이름 → 그림) */
val CategoryIcons: List<Pair<String, Int>> = listOf(
    "restaurant_fill" to R.drawable.ic_restaurant_fill,
    "local_cafe_fill" to R.drawable.ic_local_cafe_fill,
    "shopping_bag_fill" to R.drawable.ic_shopping_bag_fill,
    "shopping_basket_fill" to R.drawable.ic_shopping_basket_fill,
    "directions_car_fill" to R.drawable.ic_directions_car_fill,
    "directions_bus_fill" to R.drawable.ic_directions_bus_fill,
    "flight_fill" to R.drawable.ic_flight_fill,
    "home_fill" to R.drawable.ic_home_fill,
    "weekend_fill" to R.drawable.ic_weekend_fill,
    "pets_fill" to R.drawable.ic_pets_fill,
    "checkroom_fill" to R.drawable.ic_checkroom_fill,
    "computer_fill" to R.drawable.ic_computer_fill,
    "medication_fill" to R.drawable.ic_medication_fill,
    "school_fill" to R.drawable.ic_school_fill,
    "movie_fill" to R.drawable.ic_movie_fill,
    "redeem_fill" to R.drawable.ic_redeem_fill,
    "favorite_fill" to R.drawable.ic_favorite_fill,
    "diamond_fill" to R.drawable.ic_diamond_fill,
    "savings_fill" to R.drawable.ic_savings_fill,
    "payments_fill" to R.drawable.ic_payments_fill,
    "sell_fill" to R.drawable.ic_sell_fill,
    "storefront_fill" to R.drawable.ic_storefront_fill,
    "bolt_fill" to R.drawable.ic_bolt_fill,
    "celebration_fill" to R.drawable.ic_celebration_fill,
)

/** 직접 만든 카테고리에 고를 수 있는 색 (아이콘 칸에만 쓰는 색) */
val CategoryColors: List<Long> = listOf(
    0xFFFF8A3D, 0xFFF2554A, 0xFFE5508E, 0xFF8A5CF6, 0xFF4C6EF5, 0xFF3D8BFF, 0xFF14A3A3, 0xFF1FAF6B, 0xFFF2A100, 0xFFA0704B, 0xFF5B6B80,
)

@DrawableRes
fun categoryIconRes(icon: String?): Int = CategoryIcons.firstOrNull { it.first == icon }?.second ?: R.drawable.ic_sell_fill

private fun Category.look(): CategoryLook = when (this) {
    Category.FOOD -> CategoryLook(R.drawable.ic_restaurant_fill, Color(0xFFFF8A3D))
    Category.CAFE -> CategoryLook(R.drawable.ic_local_cafe_fill, Color(0xFFA0704B))
    Category.DELIVERY -> CategoryLook(R.drawable.ic_two_wheeler_fill, Color(0xFFF2554A))
    Category.TRANSPORT -> CategoryLook(R.drawable.ic_directions_bus_fill, Color(0xFF1FAF6B))
    Category.SHOPPING -> CategoryLook(R.drawable.ic_shopping_bag_fill, Color(0xFFE5508E))
    Category.LIVING -> CategoryLook(R.drawable.ic_shopping_basket_fill, Color(0xFF14A3A3))
    Category.HEALTH -> CategoryLook(R.drawable.ic_medication_fill, Color(0xFF3D8BFF))
    Category.CULTURE -> CategoryLook(R.drawable.ic_movie_fill, Color(0xFF8A5CF6))
    Category.EVENT -> CategoryLook(R.drawable.ic_redeem_fill, Color(0xFFF2A100))
    Category.EDUCATION -> CategoryLook(R.drawable.ic_school_fill, Color(0xFF4C6EF5))
    Category.BILLS -> CategoryLook(R.drawable.ic_home_fill, Color(0xFF5B6B80))
    Category.ETC -> CategoryLook(R.drawable.ic_more_horiz_fill, Color(0xFF8B95A1))
    Category.SALARY -> CategoryLook(R.drawable.ic_payments_fill, Color(0xFF346AFF))
    Category.SIDE_INCOME -> CategoryLook(R.drawable.ic_bolt_fill, Color(0xFF14A06A))
    Category.ALLOWANCE -> CategoryLook(R.drawable.ic_redeem_fill, Color(0xFFE5508E))
    Category.INTEREST -> CategoryLook(R.drawable.ic_savings_fill, Color(0xFF0FA3B1))
    Category.OTHER_INCOME -> CategoryLook(R.drawable.ic_add, Color(0xFF6B7684))
    // 직접 만든 카테고리
    else -> CategoryLook(categoryIconRes(icon), Color(color ?: 0xFF8B95A1))
}

/** 카테고리 동그라미 아이콘 */
@Composable
fun CategoryIcon(c: Category, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    val look = c.look()
    Box(modifier.size(size).clip(CircleShape).background(look.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(painterResource(look.res), contentDescription = null, modifier = Modifier.size(size * 0.56f), tint = look.tint)
    }
}

// ── 경고 한 줄 ─────────────────────────────────────────────

@Composable
fun WarnLine(text: String, modifier: Modifier = Modifier, fontSize: Int = 13) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(UiIcon.WARN, (fontSize + 3).dp, DonColors.Warn)
        Text(text, color = DonColors.Warn, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

// ── 하단 탭 ────────────────────────────────────────────────

data class TabItem(val label: String, val icon: UiIcon, val selectedIcon: UiIcon)

@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(DonColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DonColors.Line))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(i) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (on) item.selectedIcon else item.icon, 26.dp, if (on) DonColors.Ink else DonColors.InkFaint)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) DonColors.Ink else DonColors.InkMuted,
                    )
                }
            }
        }
    }
}

/** 구분선 */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(DonColors.Line))
}

/** 왼쪽 이름, 오른쪽 값 한 줄 */
@Composable
fun KeyValue(label: String, value: String, bold: Boolean = false, valueColor: Color = DonColors.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = if (bold) DonColors.Ink else DonColors.InkSoft, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = valueColor)
    }
}
