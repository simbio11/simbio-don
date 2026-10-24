package com.simbio.don.app

import com.simbio.don.app.ui.HomeCard
import com.simbio.don.app.ui.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.15: 홈 꾸미기 — 카드 차례·숨기기를 저장하고 되살린다 */
class HomeLayoutTest {
    @Test
    fun 차례를_바꾸고_숨기고_저장했다_되살린다() {
        val l = HomeLayout.Default.move(HomeCard.SAVED, -5).toggle(HomeCard.ROOM)
        assertEquals(HomeCard.SAVED, l.order.first())
        assertFalse(HomeCard.ROOM in l.shown)
        assertEquals(l, HomeLayout.decode(l.encode()))
        // 끝에서 더 내리거나 처음에서 더 올려도 그대로
        assertEquals(l, l.move(HomeCard.SAVED, -1))
        assertTrue(HomeCard.ROOM in l.toggle(HomeCard.ROOM).shown)
    }

    @Test
    fun 모르는_카드는_빼고_새_카드는_끝에_보인다() {
        val l = HomeLayout.decode("RECENT,-WEEK,OLD_CARD,RECENT")
        assertEquals(listOf(HomeCard.RECENT, HomeCard.WEEK), l.order.take(2))
        assertEquals(HomeCard.entries.size, l.order.size)
        assertEquals(setOf(HomeCard.WEEK), l.hidden)
        assertEquals(HomeLayout.Default, HomeLayout.decode(null))
    }
}
