package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 0.31 부자들의 소비법: 필요·욕구, 최저시급, 7일 참기 */
class HabitsTest {
    private val d = LocalDate.of(2026, 11, 14)

    @Test
    fun `필요와 욕구 — 분류대로, 직접 고르면 그대로`() {
        assertFalse(Wants.isWant(Category.FOOD, null))
        assertTrue(Wants.isWant(Category.CAFE, null))
        assertTrue(Wants.isWant(Category.FOOD, 1))
        assertFalse(Wants.isWant(Category.SHOPPING, 0))
        val s = Wants.split(
            listOf(
                Entry(d, 9_000, "점심", category = Category.FOOD) to null,
                Entry(d, 5_000, "커피", category = Category.CAFE) to null,
                Entry(d, 30_000, "운동화", category = Category.SHOPPING) to null,
                Entry(d, 10_000, "운동화 일부 환불", EntryType.REFUND, category = Category.SHOPPING) to null,
                Entry(d, 600_000, "월세", EntryType.FIXED, category = Category.BILLS) to null,
            ),
        )
        assertEquals(9_000, s.need)
        assertEquals(25_000, s.want)
        assertEquals(73, s.wantPercent)
    }

    @Test
    fun `최저시급으로 몇 시간 — 2026년 10,320원`() {
        assertEquals(10_320, MinWage.hourly(2026))
        assertEquals(10_320, MinWage.hourly(2030))
        assertEquals(60, MinWage.minutes(10_320, 2026))
        // 89,000원 운동화 = 8시간 38분 (올림)
        assertEquals("8시간 38분", MinWage.text(MinWage.minutes(89_000, 2026)))
        assertEquals(1_068_000, MinWage.yearIfSkippedMonthly(89_000))
    }

    @Test
    fun `7일 참기`() {
        val remind = WaitRule.remindOn(d)
        assertEquals(LocalDate.of(2026, 11, 21), remind)
        assertEquals(3, WaitRule.daysLeft(remind, d.plusDays(4)))
        assertFalse(WaitRule.due(remind, d.plusDays(6)))
        assertTrue(WaitRule.due(remind, d.plusDays(7)))
    }
}
