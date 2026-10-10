package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BudgetEngineTest {

    /** 2026-10-23(금) ~ 2026-11-24(화), 33일 */
    private val october = PayCycle.cycleFor(YearMonth.of(2026, 10), 25)

    @Test
    fun `월급에서 고정비와 저축을 빼고 일수 비례로 주 예산을 나눈다`() {
        val plan = BudgetEngine.plan(
            october,
            salary = 3_300_000,
            fixedExpenses = listOf(FixedExpense("월세", 700_000), FixedExpense("통신·보험", 300_000)),
            savings = 320_000,
        )

        assertEquals(1_980_000, plan.disposable) // 하루 60,000원
        assertEquals(listOf(10, 7, 7, 9), plan.weeks.map { it.days })
        assertEquals(listOf(600_000L, 420_000, 420_000, 540_000), plan.weeks.map { it.budget })
    }

    @Test
    fun `3일 이하 짧은 주는 옆 주에 합친다`() {
        val weeks = BudgetEngine.plan(october, 0).weeks
        // 앞: 10/23~10/25(3일) + 10/26~11/1 → 10일, 뒤: 11/16~11/22 + 11/23~11/24(2일) → 9일
        assertEquals(LocalDate.of(2026, 10, 23) to LocalDate.of(2026, 11, 1), weeks.first().start to weeks.first().end)
        assertEquals(LocalDate.of(2026, 11, 16) to LocalDate.of(2026, 11, 24), weeks.last().start to weeks.last().end)
    }

    @Test
    fun `4일 이상인 주는 그대로 둔다`() {
        // 결산 목요일 → 주는 목~수. 10/23(금)~10/28(수) 6일
        val weeks = BudgetEngine.plan(october, 0, settlementDay = DayOfWeek.THURSDAY).weeks
        assertEquals(6, weeks.first().days)
    }

    @Test
    fun `결산 요일이 한 주의 시작이다`() {
        val weeks = BudgetEngine.plan(october, 0, settlementDay = DayOfWeek.SUNDAY).weeks
        weeks.drop(1).forEach { assertEquals(DayOfWeek.SUNDAY, it.start.dayOfWeek) }
        weeks.dropLast(1).forEach { assertEquals(DayOfWeek.SATURDAY, it.end.dayOfWeek) }
    }

    @Test
    fun `잔돈까지 정확히 나눈다`() {
        assertEquals(listOf(13L, 23, 23, 22, 19), BudgetEngine.distribute(100, listOf(4, 7, 7, 7, 6)))
        for (total in listOf(0L, 1, 7, 999_999, 2_345_678)) {
            for (settle in DayOfWeek.entries) {
                for (payDay in listOf(1, 10, 25, 31)) {
                    val cycle = PayCycle.cycleFor(YearMonth.of(2026, 1), payDay)
                    val plan = BudgetEngine.plan(cycle, total, settlementDay = settle)
                    assertEquals(total, plan.weeks.sumOf { it.budget }, "$total/$settle/$payDay")
                    assertEquals(cycle.days, plan.weeks.sumOf { it.days })
                    plan.weeks.forEach { assert(it.days in 4..10) { "$it" } }
                }
            }
        }
    }

    @Test
    fun `고정비와 저축이 월급보다 크면 주 예산은 0이고 부족액을 알려준다`() {
        val plan = BudgetEngine.plan(october, 1_000_000, listOf(FixedExpense("월세", 900_000)), savings = 300_000)
        assertEquals(0, plan.disposable)
        assertEquals(200_000, plan.shortfall)
        assertEquals(0, plan.weeks.sumOf { it.budget })
    }

    @Test
    fun `음수 금액은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { BudgetEngine.plan(october, -1) }
        assertFailsWith<IllegalArgumentException> { BudgetEngine.plan(october, 0, savings = -1) }
        assertFailsWith<IllegalArgumentException> { FixedExpense("x", -1) }
    }
}
