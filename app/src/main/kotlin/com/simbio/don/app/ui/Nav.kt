package com.simbio.don.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/** 이 화면이 지금 맨 위에 있는지 (가려진 화면의 뒤로 가기·효과는 쉬게) */
val LocalScreenActive = compositionLocalOf { true }

/** 화면 안의 단계(몰 상세 → 목록 등)를 시스템 뒤로 가기로 한 칸씩 되돌린다. 가려진 화면에서는 쉰다 */
@Composable
fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled && LocalScreenActive.current, onBack)

/**
 * 화면 위에 쌓아 여는 화면들. 닫으면 바로 아래 화면으로 돌아가고, 아래 화면은 그대로 살아 있어 입력하던 것·스크롤이 남는다.
 */
class ScreenStack<T : Any> {
    class Entry<T>(val id: Long, val value: T)

    val entries = mutableStateListOf<Entry<T>>()

    /** 닫히며 빠져나가는 중 (움직임이 끝나면 지운다) */
    val leaving = mutableStateListOf<Entry<T>>()
    private var next = 0L

    val isEmpty: Boolean get() = entries.isEmpty()
    val top: T? get() = entries.lastOrNull()?.value

    fun push(value: T) {
        entries += Entry(next++, value)
    }

    fun pop() {
        if (entries.isNotEmpty()) leaving += entries.removeAt(entries.lastIndex)
    }

    /** 이 화면 닫기: 맨 위면 움직이며, 가려진 화면이면 조용히 */
    fun close(id: Long) {
        val i = entries.indexOfFirst { it.id == id }
        when {
            i < 0 -> Unit
            i == entries.lastIndex -> pop()
            else -> entries.removeAt(i)
        }
    }

    /** 모두 닫기 (움직임 없이) */
    fun clear() {
        entries.clear()
    }
}

/** 그리지 않기 (상태는 그대로 두고 자리만 비움) */
private fun Modifier.unplacedIf(hidden: Boolean): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, p.height) { if (!hidden) p.place(0, 0) }
}

/** 아래 화면으로 손가락이 새지 않게 */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent() }
}

/**
 * [base](탭 화면들) 위에 [stack]의 화면을 오른쪽에서 밀려 들어오게 쌓는다.
 * 닫을 때는 오른쪽으로 빠지고 바로 아래 화면이 보인다. 다 들어온 화면 아래는 그리지 않는다.
 */
@Composable
fun <T : Any> ScreenHost(stack: ScreenStack<T>, base: @Composable () -> Unit, screen: @Composable (value: T, close: () -> Unit) -> Unit) {
    // 다 들어와 멈춘 화면 (그 아래는 가려져 있다)
    val settled = remember { mutableStateMapOf<Long, Boolean>() }
    val coverFrom = stack.entries.indexOfLast { settled[it.id] == true }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().unplacedIf(coverFrom >= 0)
                .let { if (stack.isEmpty) it else it.clearAndSetSemantics {} },
        ) {
            CompositionLocalProvider(LocalScreenActive provides stack.isEmpty) { base() }
        }
        val all = stack.entries + stack.leaving
        all.forEachIndexed { i, e ->
            key(e.id) {
                val isLeaving = e in stack.leaving
                val isTop = !isLeaving && i == stack.entries.lastIndex
                val slide = remember { Animatable(1f) }
                LaunchedEffect(isLeaving) {
                    if (isLeaving) {
                        settled.remove(e.id)
                        slide.animateTo(1f, tween(200, easing = FastOutLinearInEasing))
                        stack.leaving.remove(e)
                    } else {
                        slide.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                        settled[e.id] = true
                    }
                }
                Box(
                    Modifier.fillMaxSize()
                        .unplacedIf(!isLeaving && i < coverFrom)
                        .graphicsLayer {
                            translationX = slide.value * size.width
                            // 밀려 들어오는 동안 살짝 투명하게
                            alpha = 1f - slide.value * 0.35f
                        }
                        .background(DonColors.Bg)
                        .blockTouches()
                        .let { if (isTop) it else it.clearAndSetSemantics {} },
                ) {
                    BackHandler(enabled = isTop) { stack.pop() }
                    CompositionLocalProvider(LocalScreenActive provides isTop) { screen(e.value) { stack.close(e.id) } }
                }
            }
        }
    }
}

/** 화면 안 단계 바꾸기: 앞으로 가면 오른쪽에서, 뒤로 가면 왼쪽에서 */
fun <S> AnimatedContentTransitionScope<S>.stepTransition(forward: Boolean): ContentTransform =
    if (forward) {
        (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(160)))
    } else {
        (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220))) togetherWith
            (slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160)))
    }

/** 탭 바꾸기: 살짝 떠오르며 바뀜 */
@Composable
fun <S> FadeThrough(target: S, modifier: Modifier = Modifier, content: @Composable (S) -> Unit) {
    AnimatedContent(
        target,
        modifier,
        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
        label = "fade",
    ) { content(it) }
}
