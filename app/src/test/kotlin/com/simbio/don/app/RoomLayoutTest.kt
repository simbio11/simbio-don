package com.simbio.don.app

import com.simbio.don.app.ui.pixel.RoomLayout
import com.simbio.don.game.HouseLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 그림 생성기(tools/pixelart)가 만든 방 좌표가 게임 규칙과 맞는지 */
class RoomLayoutTest {
    @Test
    fun 원룸_칸_자리_수는_수납_칸_수와_같다() {
        HouseLevel.ONE_ROOM.capacity.forEach { (slot, cap) ->
            assertEquals("$slot", cap, RoomLayout.slots[slot]?.size)
        }
    }

    @Test
    fun 모든_자리는_방_그림_안에_있다() {
        val all = RoomLayout.slots.values.flatten() + RoomLayout.floorPile + RoomLayout.trash + RoomLayout.character
        all.forEach { p ->
            assertTrue("$p", p.x >= 0 && p.y >= 0 && p.x + RoomLayout.ITEM <= RoomLayout.WIDTH && p.y + RoomLayout.ITEM <= RoomLayout.HEIGHT)
        }
    }
}
