package com.simbio.don.app.ui.kit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 두 손가락으로 위로 쓸어 올리면 [onSwipe]. 한 손가락 스크롤·누르기는 그대로 아래 화면이 받는다
 * (두 손가락이 닿았을 때만 보고, 올렸다고 판단한 뒤에만 그 움직임을 가져간다).
 */
fun Modifier.twoFingerSwipeUp(enabled: Boolean = true, onSwipe: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val need = 90.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startY: Float? = null
            var startX = 0f
            var fired = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val down = ev.changes.filter { it.pressed }
                if (down.isEmpty()) break
                if (down.size >= 2) {
                    val y = down.map { it.position.y }.average().toFloat()
                    val x = down.map { it.position.x }.average().toFloat()
                    if (startY == null) {
                        startY = y
                        startX = x
                    }
                    val dy = startY - y
                    if (!fired && dy > need && dy > kotlin.math.abs(x - startX) * 1.5f) {
                        fired = true
                        ev.changes.forEach { it.consume() }
                        onSwipe()
                    } else if (fired) {
                        ev.changes.forEach { it.consume() }
                    }
                } else if (startY != null && !fired) {
                    // 한 손가락을 뗐다 — 다시 두 손가락이 닿으면 거기서부터 잰다
                    startY = null
                }
            }
        }
    }
