package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun e(day: Int, amount: Long, label: String, c: Category, type: EntryType = EntryType.SPEND) =
        Entry(d(day), amount, label, type, category = c)

    private val entries = listOf(
        e(1, 23_000, "OO치킨", Category.FOOD),
        e(1, 4_500, "스타벅스", Category.CAFE),
        e(2, 4_500, "스타벅스", Category.CAFE),
        e(3, 50_000, "무신사", Category.SHOPPING),
        e(4, 20_000, "무신사", Category.SHOPPING, EntryType.REFUND),
        e(5, 300_000, "병원", Category.HEALTH, EntryType.EXCEPTION),
    )

    @Test
    fun `카테고리별 합계는 환불을 빼고 예외 지출을 포함한다`() {
        val c = Stats.byCategory(entries)
        assertEquals(listOf(Category.HEALTH, Category.SHOPPING, Category.FOOD, Category.CAFE), c.map { it.category })
        assertEquals(30_000, c[1].amount)
        assertEquals(1, c[1].count)
        assertEquals(362_000, Stats.total(entries))
        // 내림 때문에 합이 10000보다 몇 bp 모자랄 수 있다
        kotlin.test.assertTrue(c.sumOf { it.shareBps } in 9_997..10_000)
    }

    @Test
    fun `날짜별 합계와 많이 쓴 곳`() {
        assertEquals(27_500, Stats.daily(entries)[d(1)])
        assertEquals(-20_000, Stats.daily(entries)[d(4)])
        val top = Stats.topPlaces(entries, 2)
        assertEquals(listOf("병원", "무신사"), top.map { it.label })
        assertEquals(2, Stats.topPlaces(entries).first { it.label == "스타벅스" }.count)
    }

    @Test
    fun `지출 속도와 월말 예상`() {
        val p = Stats.pace(entries.take(4), d(1), d(10), budget = 1_000_000, today = d(4))
        assertEquals(listOf(27_500L, 32_000, 82_000, 82_000), p.cumulative.take(4))
        assertEquals(100_000, p.pace.first())
        assertEquals(1_000_000, p.pace.last())
        assertEquals(4, p.elapsedDays)
        assertEquals(1_000_000 - 82_000 * 10 / 4, p.projectedLeft)
    }

    @Test
    fun `오늘 쓸 수 있는 돈은 이번 주 남은 돈을 남은 날로 나눈 것`() {
        val week = Week(0, d(2), d(8), 70_000)
        val r = WeekResult(week, carryIn = 0, spent = 10_000, exceptionOverflow = 0)
        assertEquals(12_000, Stats.todayAllowance(r, d(4))) // 60,000 / 5일
        assertEquals(0, Stats.todayAllowance(r, d(9)))
    }

    @Test
    fun `고정 지출은 가계부 지출에 들고 생활비에서는 빠지며, 이체는 어디에도 안 든다`() {
        val more = entries + listOf(
            e(6, 600_000, "월세", Category.LIVING, EntryType.FIXED),
            e(6, 100_000, "ATM 출금", Category.ETC, EntryType.TRANSFER),
            e(7, 3_000_000, "월급", Category.SALARY, EntryType.INCOME),
        )
        assertEquals(Stats.total(entries) + 600_000, Stats.total(more))
        assertEquals(Stats.total(entries), Stats.livingTotal(more))
        assertEquals(600_000, Stats.fixedTotal(more))
        assertEquals(3_000_000, Stats.income(more))
        assertEquals(mapOf(d(7) to 3_000_000L), Stats.dailyIncome(more))
        assertEquals(null, Stats.daily(more, livingOnly = true)[d(6)])
        assertEquals(600_000L, Stats.daily(more)[d(6)])
        assertTrue(Stats.byCategory(more).none { it.category == Category.ETC })
        assertEquals(listOf(Category.SALARY), Stats.incomeByCategory(more).map { it.category })
    }
}
