package com.simbio.don.app.ui.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simbio.don.app.ui.DonColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * 화면 움직임 모음 (designbookmark.com/motion 의 Word roll · Tab pill · Expand card · Like burst · 등장 순서를 본떠 Compose로 새로 만든 것).
 * 폰에서 애니메이션을 끄면([reduced]) 모두 바로 끝난 모습으로 보인다.
 */
object Motion {
    /** 폰 설정에서 애니메이션을 껐다 (앱 설정 "효과 늘 보기"를 켜면 false) */
    var reduced by mutableStateOf(false)

    /** 한 번 등장 효과를 보인 화면 (다시 그릴 때마다 또 튀지 않게) */
    internal val shown = mutableSetOf<String>()
}

/**
 * 숫자가 바뀔 때 자리마다 굴러가며 바뀐다 (Word roll). 늘면 아래에서 위로, 줄면 위에서 아래로.
 * 화면 읽기·시험에는 글자 전체가 한 덩어리로 보인다.
 */
@Composable
fun RollingText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier,
) {
    if (Motion.reduced) {
        Text(text, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1, modifier = modifier)
        return
    }
    var last by remember { mutableStateOf(text) }
    val up = digits(text) >= digits(last)
    LaunchedEffect(text) { last = text }
    // 오른쪽 끝 자리부터 맞춰 굴린다 (자릿수가 바뀌어도 일의 자리끼리)
    val chars = text.toList()
    Row(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        chars.forEachIndexed { i, c ->
            val fromRight = chars.size - i
            androidx.compose.runtime.key(fromRight) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        val d = if (up) 1 else -1
                        (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { h -> h * d } + fadeIn(tween(140)))
                            .togetherWith(slideOutVertically(tween(160)) { h -> -h * d } + fadeOut(tween(120)))
                    },
                    label = "roll",
                ) { ch ->
                    Text(ch.toString(), fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1)
                }
            }
        }
    }
}

private fun digits(s: String): Long = s.filter { it.isDigit() }.take(15).toLongOrNull()?.let { if (s.trimStart().startsWith("−") || s.trimStart().startsWith("-")) -it else it } ?: 0

/**
 * 고른 칸 아래로 알약 모양 바탕이 미끄러져 가는 탭 (Tab pill / Segmented control).
 * [colorOf]는 고른 칸 글자색.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    colorOf: (Int) -> Color = { DonColors.Ink },
) {
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DonColors.Line.copy(alpha = 0.6f)).padding(4.dp)) {
        val cell: Dp = maxWidth / labels.size.coerceAtLeast(1)
        val x by animateDpAsState(
            cell * selected,
            if (Motion.reduced) spring(stiffness = Spring.StiffnessHigh * 10) else spring(dampingRatio = 0.72f, stiffness = 420f),
            label = "pill",
        )
        // 칸 줄과 같은 높이 (스크롤 화면 안에서도): 부모 크기에 맞춘 틀 안에서 알약만 옮긴다
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset(x = x).width(cell).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(DonColors.Surface))
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = fontSize, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) colorOf(i) else DonColors.InkMuted, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 처음 보일 때 아래에서 살짝 떠오르며 나타난다 (화면을 열 때 카드가 차례로 — [index]마다 조금씩 늦게).
 * [key]가 같으면 앱을 켠 동안 한 번만.
 */
@Composable
fun Modifier.riseIn(key: String, index: Int = 0): Modifier {
    if (Motion.reduced || key in Motion.shown) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(8))
        p.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 300f))
        Motion.shown += key
    }
    return this.graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * 18.dp.toPx()
    }
}

/**
 * 동전·하트가 톡 터져 퍼지는 효과 (Like burst). [Burst.play]를 부르면 화면 가운데에서 한 번.
 * 참아서 아낀 돈·저축으로 옮긴 돈처럼 잘한 일에만 쓴다.
 */
object Burst {
    var count by mutableIntStateOf(0)
        private set

    fun play() {
        if (!Motion.reduced) count++
    }
}

/** 앱 맨 위에 한 번 깔아 두는 터짐 그림 */
@Composable
fun BurstLayer() {
    val n = Burst.count
    if (n == 0) return
    val t = remember(n) { Animatable(0f) }
    LaunchedEffect(n) { t.animateTo(1f, tween(900)) }
    if (t.value >= 1f) return
    val colors = listOf(Color(0xFFFFC53D), Color(0xFFFF8A65), Color(0xFF4CAF50), Color(0xFF5C7CFA), Color(0xFFFFD54F))
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        val k = t.value
        val ease = 1f - (1f - k) * (1f - k)
        repeat(18) { i ->
            val a = (i / 18f) * 2f * Math.PI.toFloat() + n * 0.37f
            val r = (60 + (i % 3) * 40).dp.toPx() * ease
            val fall = 80.dp.toPx() * k * k
            val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r + fall)
            val alpha = (1f - k).coerceIn(0f, 1f)
            val s = (if (i % 2 == 0) 7 else 5).dp.toPx() * (1f - 0.4f * k)
            drawCircle(colors[i % colors.size].copy(alpha = alpha), s, p)
            // 동전 가운데 반짝
            if (i % 2 == 0) drawCircle(Color.White.copy(alpha = alpha * 0.7f), s * 0.35f, p - Offset(s * 0.25f, s * 0.25f))
        }
    }
}
