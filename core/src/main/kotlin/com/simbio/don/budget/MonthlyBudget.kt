package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 금액 단위는 모두 원(KRW) 정수. */
data class FixedExpense(val name: String, val amount: Long) {
    init {
        require(amount >= 0) { "고정비는 0원 이상이어야 합니다: $name=$amount" }
    }
}

/** 한 달 안에서 잘린 한 주. 월 경계에 걸친 주는 그 달에 속한 날만 포함한다. */
data class WeekSlice(
    val index: Int,
    val start: LocalDate,
    val end: LocalDate,
    val budget: Long,
) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
}

data class MonthlyPlan(
    val month: YearMonth,
    val salary: Long,
    val fixedTotal: Long,
    val weeks: List<WeekSlice>,
) {
    /** 고정비를 빼고 남은, 주 예산으로 나눌 금액. 고정비가 월급보다 크면 0. */
    val disposable: Long get() = (salary - fixedTotal).coerceAtLeast(0)

    /** 고정비가 월급을 넘는 금액. 0이면 정상. */
    val shortfall: Long get() = (fixedTotal - salary).coerceAtLeast(0)

    fun weekOf(date: LocalDate): WeekSlice? = weeks.firstOrNull { date in it.start..it.end }
}

object BudgetEngine {

    /**
     * 월급 → 고정비 차감 → 주 단위 예산 배분.
     *
     * 남은 금액을 각 주에 포함된 일수에 비례해 나누고, 나누어떨어지지 않는 잔돈은
     * 소수점 이하가 큰 주부터 1원씩 더해 주 예산 합계가 정확히 [MonthlyPlan.disposable]과 같게 한다.
     */
    fun plan(
        month: YearMonth,
        salary: Long,
        fixedExpenses: List<FixedExpense> = emptyList(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): MonthlyPlan {
        require(salary >= 0) { "월급은 0원 이상이어야 합니다: $salary" }
        val fixedTotal = fixedExpenses.sumOf { it.amount }
        val disposable = (salary - fixedTotal).coerceAtLeast(0)

        val ranges = splitIntoWeeks(month, weekStart)
        val totalDays = month.lengthOfMonth().toLong()
        val dayCounts = ranges.map { (s, e) -> e.toEpochDay() - s.toEpochDay() + 1 }

        val budgets = dayCounts.map { disposable * it / totalDays }.toLongArray()
        var leftover = disposable - budgets.sum()
        val byRemainder = dayCounts.indices.sortedByDescending { (disposable * dayCounts[it]) % totalDays }
        for (i in byRemainder) {
            if (leftover == 0L) break
            budgets[i] += 1
            leftover -= 1
        }

        val weeks = ranges.mapIndexed { i, (s, e) -> WeekSlice(i, s, e, budgets[i]) }
        return MonthlyPlan(month, salary, fixedTotal, weeks)
    }

    private fun splitIntoWeeks(month: YearMonth, weekStart: DayOfWeek): List<Pair<LocalDate, LocalDate>> {
        val weekEnd = weekStart.minus(1)
        val last = month.atEndOfMonth()
        val result = mutableListOf<Pair<LocalDate, LocalDate>>()
        var start = month.atDay(1)
        while (!start.isAfter(last)) {
            val end = minOf(start.with(TemporalAdjusters.nextOrSame(weekEnd)), last)
            result += start to end
            start = end.plusDays(1)
        }
        return result
    }
}
