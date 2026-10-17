package com.simbio.don.app

import com.simbio.don.app.ui.DayPhase
import com.simbio.don.app.ui.pixel.RoomLayout
import com.simbio.don.app.ui.poseAt
import com.simbio.don.game.HouseLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

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
        val all = RoomLayout.slots.values.flatten() + RoomLayout.floorPile + RoomLayout.trash
        all.forEach { p ->
            assertTrue("$p", p.x >= 0 && p.y >= 0 && p.x + RoomLayout.ITEM <= RoomLayout.WIDTH && p.y + RoomLayout.ITEM <= RoomLayout.HEIGHT)
        }
        val c = RoomLayout.character
        assertTrue(c.x + RoomLayout.CHAR_W <= RoomLayout.WIDTH && c.y + RoomLayout.CHAR_H <= RoomLayout.HEIGHT)
    }

    @Test
    fun 칸마다_물건_자리는_누르는_자리_안에_있다() {
        RoomLayout.slots.keys.forEach { slot ->
            val hit = RoomLayout.hits.getValue(slot)
            RoomLayout.slots.getValue(slot).forEach { p ->
                assertTrue("$slot $p", p.x >= hit.left && p.y >= hit.top && p.x + RoomLayout.ITEM <= hit.right && p.y + RoomLayout.ITEM <= hit.bottom)
            }
        }
    }

    @Test
    fun 캐릭터는_러그_위에서만_걷고_한_바퀴_돌면_제자리() {
        assertEquals(RoomLayout.character.x, poseAt(0).x)
        assertEquals(RoomLayout.character.x, poseAt(15_999).x)
        (0 until 16_000 step 50).forEach { ms ->
            val p = poseAt(ms)
            assertTrue("$ms ${p.x}", p.x in RoomLayout.WALK_MIN..RoomLayout.WALK_MAX)
            assertTrue(p.frame in 0..3)
        }
        // 걷는 중에는 걷기 그림
        assertTrue(poseAt(8_000).frame >= 2)
    }

    @Test
    fun 시간대() {
        assertEquals(DayPhase.NIGHT, DayPhase.of(LocalTime.of(5, 59)))
        assertEquals(DayPhase.DAY, DayPhase.of(LocalTime.of(6, 0)))
        assertEquals(DayPhase.DAY, DayPhase.of(LocalTime.of(16, 59)))
        assertEquals(DayPhase.SUNSET, DayPhase.of(LocalTime.of(17, 0)))
        assertEquals(DayPhase.NIGHT, DayPhase.of(LocalTime.of(19, 0)))
    }
}
