package com.simbio.don.app.ui.kit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp

/**
 * 두 손가락으로 위로 끌기. 한 손가락 스크롤·누르기는 그대로 아래 화면이 받는다.
 * 두 손가락이 위로 조금(12dp) 움직이면 [onStart], 그 뒤로는 움직인 만큼 [onDrag] (위로 +px),
 * 손을 떼면 [onEnd] (위로 가는 속도 px/s). 시작한 뒤로는 그 움직임을 가져간다.
 */
fun Modifier.twoFingerDrag(
    enabled: Boolean = true,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
): Modifier = if (!enabled) this else pointerInput(Unit) {
    val slop = 12.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startY: Float? = null
        var startX = 0f
        var lastY = 0f
        var active = false
        val tracker = VelocityTracker()
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val down = ev.changes.filter { it.pressed }
            if (down.size < 2) {
                if (active) {
                    onEnd(-tracker.calculateVelocity().y)
                    ev.changes.forEach { it.consume() }
                    active = false
                }
                if (down.isEmpty()) break
                startY = null
                continue
            }
            val y = down.map { it.position.y }.average().toFloat()
            val x = down.map { it.position.x }.average().toFloat()
            val time = down.maxOf { it.uptimeMillis }
            if (startY == null) {
                startY = y
                startX = x
                lastY = y
            }
            if (!active) {
                val dy = startY - y
                if (dy > slop && dy > kotlin.math.abs(x - startX) * 1.2f) {
                    active = true
                    lastY = y
                    onStart()
                }
            }
            if (active) {
                tracker.addPosition(time, androidx.compose.ui.geometry.Offset(x, y))
                onDrag(lastY - y)
                lastY = y
                ev.changes.forEach { it.consume() }
            }
        }
    }
}
