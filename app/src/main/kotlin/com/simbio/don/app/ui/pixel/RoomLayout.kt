package com.simbio.don.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import com.simbio.don.game.Slot

// 생성된 파일: tools/pixelart/make_art.py — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 원룸 그림(px_room_one_room) 안의 좌표. 단위는 그림 원본 픽셀. */
object RoomLayout {
    const val WIDTH = 160
    const val HEIGHT = 136
    const val ITEM = 12

    /** 칸마다 물건이 놓이는 자리 (물건 그림의 왼쪽 위) */
    val slots: Map<Slot, List<IntOffset>> = mapOf(
        Slot.WARDROBE to listOf(IntOffset(9, 23), IntOffset(21, 23), IntOffset(33, 23), IntOffset(9, 38), IntOffset(21, 38), IntOffset(33, 38)),
        Slot.SHELF to listOf(IntOffset(64, 41), IntOffset(76, 41), IntOffset(88, 41), IntOffset(64, 55), IntOffset(76, 55), IntOffset(88, 55)),
        Slot.DESK to listOf(IntOffset(113, 48), IntOffset(123, 48), IntOffset(133, 48), IntOffset(143, 48)),
        Slot.SHOE_RACK to listOf(IntOffset(8, 102), IntOffset(20, 102), IntOffset(8, 116), IntOffset(20, 116)),
    )

    val character = IntOffset(76, 82)

    /** 수납이 넘친 물건이 바닥에 쌓이는 자리 (채우는 순서) */
    val floorPile = listOf(IntOffset(76, 122), IntOffset(88, 122), IntOffset(64, 122), IntOffset(100, 122), IntOffset(52, 122), IntOffset(70, 110), IntOffset(94, 110), IntOffset(82, 116), IntOffset(62, 116), IntOffset(98, 116), IntOffset(56, 110), IntOffset(106, 110))

    /** 먹고 남은 쓰레기 자리 */
    val trash = listOf(IntOffset(46, 123), IntOffset(41, 107), IntOffset(49, 113), IntOffset(36, 95), IntOffset(52, 100), IntOffset(44, 90))
}
