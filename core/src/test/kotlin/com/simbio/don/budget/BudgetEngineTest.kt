package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BudgetEngineTest {

    private val october = YearMonth.of(2026, 10) // 10/1 목요일, 31일

    @Test
    fun `월급에서 고정비를 빼고 일수 비례로 주 예산을 나눈다`() {
        val plan = BudgetEngine.plan(
            october,
            salary = 3_000_000,
            fixedExpenses = listOf(FixedExpense("월세", 1_000_000), FixedExpense("통신·보험", 450_000)),
        )

        assertEquals(1_550_000, plan.disposable) // 하루 50,000원
        assertEquals(listOf(4, 7, 7, 7, 6), plan.weeks.map { it.days })
        assertEquals(listOf(200_000L, 350_000, 350_000, 350_000, 300_000), plan.weeks.map { it.budget })
        assertEquals(LocalDate.of(2026, 10, 1), plan.weeks.first().start)
        assertEquals(LocalDate.of(2026, 10, 4), plan.weeks.first().end)
        assertEquals(LocalDate.of(2026, 10, 31), plan.weeks.last().end)
    }

    @Test
    fun `잔돈은 소수점 이하가 큰 주부터 배분해 합계가 정확히 맞는다`() {
        val plan = BudgetEngine.plan(october, salary = 100)

        assertEquals(listOf(13L, 23, 23, 22, 19), plan.weeks.map { it.budget })
        assertEquals(100, plan.weeks.sumOf { it.budget })
    }

    @Test
    fun `어떤 금액이든 주 예산 합계는 가용 금액과 같다`() {
        for (salary in listOf(0L, 1, 7, 999_999, 2_345_678, 10_000_001)) {
            for (month in (1..12).map { YearMonth.of(2026, it) }) {
                val plan = BudgetEngine.plan(month, salary)
                assertEquals(salary, plan.weeks.sumOf { it.budget }, "$month / $salary")
                assertEquals(month.lengthOfMonth(), plan.weeks.sumOf { it.days })
            }
        }
    }

    @Test
    fun `주 시작 요일을 바꿀 수 있다`() {
        val feb = YearMonth.of(2026, 2) // 2/1 일요일, 28일
        assertEquals(listOf(1, 7, 7, 7, 6), BudgetEngine.plan(feb, 280_000).weeks.map { it.days })
        assertEquals(
            listOf(7, 7, 7, 7),
            BudgetEngine.plan(feb, 280_000, weekStart = DayOfWeek.SUNDAY).weeks.map { it.days },
        )
    }

    @Test
    fun `고정비가 월급보다 크면 주 예산은 0이고 부족액을 알려준다`() {
        val plan = BudgetEngine.plan(october, 1_000_000, listOf(FixedExpense("월세", 1_200_000)))

        assertEquals(0, plan.disposable)
        assertEquals(200_000, plan.shortfall)
        assertEquals(List(5) { 0L }, plan.weeks.map { it.budget })
    }

    @Test
    fun `날짜로 해당 주를 찾는다`() {
        val plan = BudgetEngine.plan(october, 1_550_000)
        assertEquals(2, plan.weekOf(LocalDate.of(2026, 10, 15))?.index)
        assertNull(plan.weekOf(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `음수 금액은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { BudgetEngine.plan(october, -1) }
        assertFailsWith<IllegalArgumentException> { FixedExpense("x", -1) }
    }
}
