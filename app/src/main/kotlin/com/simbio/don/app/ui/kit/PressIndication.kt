package com.simbio.don.app.ui.kit

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * 앱 전체의 누름 효과: 누르는 곳이 살짝 들어가며 어두워지고, 떼면 통통 튀어 돌아오며 짧게 진동한다.
 * LocalIndication으로 깔아 두면 clickable을 쓰는 모든 줄·카드·칸에 같은 느낌이 난다.
 */
object PressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)

    override fun hashCode(): Int = 7

    override fun equals(other: Any?): Boolean = other === this
}

private class PressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val scale = Animatable(1f)
    private val shade = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> {
                        launch { scale.animateTo(if (Motion.reduced) 1f else 0.97f, spring(stiffness = 1400f)) { invalidateDraw() } }
                        launch { shade.animateTo(1f, tween(90)) { invalidateDraw() } }
                    }
                    is PressInteraction.Release -> {
                        tap()
                        release()
                    }
                    is PressInteraction.Cancel -> release()
                }
            }
        }
    }

    private fun release() {
        coroutineScope.launch {
            // 짧게 한 번 더 들어갔다가 튀어 돌아온다 (빨리 톡 눌러도 느껴지게)
            if (!Motion.reduced) scale.animateTo(0.95f, tween(50)) { invalidateDraw() }
            scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 600f)) { invalidateDraw() }
        }
        coroutineScope.launch { shade.animateTo(0f, tween(220)) { invalidateDraw() } }
    }

    private fun tap() {
        val view: View = currentValueOf(LocalView)
        Feedback.tap(view)
    }

    override fun ContentDrawScope.draw() {
        val s = scale.value
        val a = shade.value
        scale(s) {
            this@draw.drawContent()
            if (a > 0f) drawRect(Color.Black.copy(alpha = 0.06f * a))
        }
    }
}
