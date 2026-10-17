package com.simbio.don.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
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

    /** 누르면 그 칸을 여는 자리 */
    val hits: Map<Slot, IntRect> = mapOf(
        Slot.WARDROBE to IntRect(0, 12, 49, 82),
        Slot.SHELF to IntRect(62, 38, 102, 82),
        Slot.DESK to IntRect(112, 44, 156, 82),
        Slot.SHOE_RACK to IntRect(5, 99, 35, 133),
    )
    val trashHit = IntRect(33, 86, 55, 136)
    val floorHit = IntRect(50, 104, 116, 136)

    const val CHAR_W = 22
    const val CHAR_H = 28
    val character = IntOffset(73, 78)

    /** 캐릭터가 걸어 다니는 x 범위 (왼쪽 위 기준) */
    const val WALK_MIN = 58
    const val WALK_MAX = 90

    /** 수납이 넘친 물건이 바닥에 쌓이는 자리 (채우는 순서) */
    val floorPile = listOf(IntOffset(76, 122), IntOffset(88, 122), IntOffset(64, 122), IntOffset(100, 122), IntOffset(52, 122), IntOffset(70, 110), IntOffset(94, 110), IntOffset(82, 116), IntOffset(62, 116), IntOffset(98, 116), IntOffset(56, 110), IntOffset(106, 110))

    /** 먹고 남은 쓰레기 자리 */
    val trash = listOf(IntOffset(46, 123), IntOffset(41, 107), IntOffset(49, 113), IntOffset(36, 95), IntOffset(52, 100), IntOffset(44, 90))

    /** 하늘이 비치는 유리창. 하늘 그림(px_sky_*)은 가로 64px로 이어 붙여 흘린다. */
    val glass = IntRect(69, 9, 95, 29)
    const val SKY_W = 64
}
