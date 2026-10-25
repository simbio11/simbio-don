package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecisionsTest {
    @Test
    fun `사면 이번 주·하루·월말이 얼마나 줄고 몇 시간 일한 돈인지`() {
        val r = PurchaseCheck.of(price = 89_000, weekLeft = 200_000, weekDaysLeft = 4, monthLeft = 150_000, salary = 3_135_000)
        assertEquals(111_000, r.weekLeftAfter)
        assertEquals(50_000, r.perDayBefore)
        assertEquals(27_750, r.perDayAfter)
        assertEquals(61_000, r.monthLeftAfter)
        // 시급 15,000원 → 89,000원은 356분 (5시간 56분)
        assertEquals(356, r.workMinutes)
        assertEquals(PurchaseVerdict.OK, r.verdict)
        assertEquals(PurchaseVerdict.WEEK_OVER, PurchaseCheck.of(250_000, 200_000, 4, 400_000, 0).verdict)
        assertEquals(PurchaseVerdict.MONTH_SHORT, PurchaseCheck.of(100_000, 200_000, 4, 50_000, 0).verdict)
        assertEquals(PurchaseVerdict.NO, PurchaseCheck.of(300_000, 200_000, 4, 50_000, 0).verdict)
        assertNull(PurchaseCheck.of(1_000, 0, 0, 0, 0).workMinutes)
        assertEquals(0, PurchaseCheck.of(300_000, 200_000, 0, 50_000, 0).perDayAfter)
    }

    @Test
    fun `지난달 성적표 — 등급·지킨 주·무지출·가장 줄인 분류`() {
        val cycle = PayCycle(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 22))
        val plan = BudgetEngine.plan(cycle, salary = 2_400_000, savings = 400_000, settlementDay = DayOfWeek.MONDAY)
        val d = { day: Int -> cycle.start.plusDays(day.toLong()) }
        val entries = listOf(
            Entry(d(0), 300_000, "장보기", category = Category.LIVING),
            Entry(d(3), 500_000, "회식", category = Category.FOOD),
            Entry(d(10), 200_000, "카페", category = Category.CAFE),
            Entry(d(12), 500_000, "월세", type = EntryType.FIXED),
        )
        val before = listOf(Entry(LocalDate.of(2026, 9, 1), 260_000, "카페", category = Category.CAFE), Entry(LocalDate.of(2026, 9, 2), 100_000, "식당", category = Category.FOOD))
        val r = MonthReports.of(plan, entries, before)
        assertEquals(2_000_000, r.budget)
        assertEquals(1_000_000, r.spent)
        assertEquals("A+", r.grade)
        assertEquals(50, r.usedPercent)
        assertEquals(Category.FOOD, r.top!!.category)
        assertEquals(Category.CAFE to -60_000L, r.bestCut)
        assertEquals(Category.FOOD to 400_000L, r.worstRise)
        assertEquals(r.weeks, r.weeksKept + (r.weeks - r.weeksKept))
        // 28일 중 기록 있는 날 3일(고정 지출은 무지출에 안 셈)
        assertEquals(25, r.noSpendDays)
        assertEquals("D", MonthReports.grade(1_200, 1_000))
        assertEquals("B", MonthReports.grade(1_000, 1_000))
        assertNull(MonthReports.of(plan, entries).bestCut)
    }
}
