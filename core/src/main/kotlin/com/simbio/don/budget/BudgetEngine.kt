package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 금액 단위는 모두 원(KRW) 정수. */
data class FixedExpense(val name: String, val amount: Long) {
    init {
        require(amount >= 0) { "고정비는 0원 이상이어야 합니다: $name=$amount" }
    }
}

/** 주기 안의 한 주. 3일 이하 짧은 주는 옆 주와 합쳐져 최대 10일까지 될 수 있다. */
data class Week(val index: Int, val start: LocalDate, val end: LocalDate, val budget: Long) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    operator fun contains(date: LocalDate) = date in start..end
}

data class CyclePlan(
    val cycle: PayCycle,
    val salary: Long,
    val fixedTotal: Long,
    val savings: Long,
    val weeks: List<Week>,
) {
    /** 월급 − 고정비 − 먼저 떼는 저축. 주 예산 합계와 같다. 모자라면 0. */
    val disposable: Long get() = (salary - fixedTotal - savings).coerceAtLeast(0)

    /** 고정비 + 저축이 월급을 넘는 금액. 0이면 정상. */
    val shortfall: Long get() = (fixedTotal + savings - salary).coerceAtLeast(0)

    fun weekIndexOf(date: LocalDate): Int? = weeks.indexOfFirst { date in it }.takeIf { it >= 0 }
}

object BudgetEngine {
    const val SHORT_WEEK_MAX_DAYS = 3

    /**
     * 월급 → 고정비·저축 차감 → 주 단위 예산 배분.
     *
     * [settlementDay]가 한 주의 시작 요일이다 (결산 요일 아침에 지난 주를 결산하므로 그 전날이 주의 끝).
     */
    fun plan(
        cycle: PayCycle,
        salary: Long,
        fixedExpenses: List<FixedExpense> = emptyList(),
        savings: Long = 0,
        settlementDay: DayOfWeek = DayOfWeek.MONDAY,
    ): CyclePlan {
        require(salary >= 0) { "월급은 0원 이상이어야 합니다: $salary" }
        require(savings >= 0) { "저축은 0원 이상이어야 합니다: $savings" }
        val fixedTotal = fixedExpenses.sumOf { it.amount }
        val disposable = (salary - fixedTotal - savings).coerceAtLeast(0)

        val ranges = splitIntoWeeks(cycle.start, cycle.end, settlementDay)
        val budgets = distribute(disposable, ranges.map { (s, e) -> e.toEpochDay() - s.toEpochDay() + 1 })
        val weeks = ranges.mapIndexed { i, (s, e) -> Week(i, s, e, budgets[i]) }
        return CyclePlan(cycle, salary, fixedTotal, savings, weeks)
    }

    /**
     * [total]을 [weights]에 비례해 나눈다. 나누어떨어지지 않는 잔돈은 소수점 이하가 큰 쪽부터
     * 1원씩 더해 합계가 정확히 [total]이 되게 한다.
     */
    fun distribute(total: Long, weights: List<Long>): List<Long> {
        val weightSum = weights.sum()
        if (weightSum == 0L) return weights.map { 0L }
        val shares = weights.map { total * it / weightSum }.toLongArray()
        var leftover = total - shares.sum()
        for (i in weights.indices.sortedByDescending { (total * weights[it]) % weightSum }) {
            if (leftover == 0L) break
            shares[i] += 1
            leftover -= 1
        }
        return shares.toList()
    }

    internal fun splitIntoWeeks(
        start: LocalDate,
        end: LocalDate,
        weekStart: DayOfWeek,
    ): List<Pair<LocalDate, LocalDate>> {
        val weekEnd = weekStart.minus(1)
        val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()
        var s = start
        while (!s.isAfter(end)) {
            val e = minOf(s.with(TemporalAdjusters.nextOrSame(weekEnd)), end)
            ranges += s to e
            s = e.plusDays(1)
        }
        fun days(r: Pair<LocalDate, LocalDate>) = r.second.toEpochDay() - r.first.toEpochDay() + 1
        if (ranges.size > 1 && days(ranges.first()) <= SHORT_WEEK_MAX_DAYS) {
            ranges[1] = ranges[0].first to ranges[1].second
            ranges.removeAt(0)
        }
        if (ranges.size > 1 && days(ranges.last()) <= SHORT_WEEK_MAX_DAYS) {
            val last = ranges.removeAt(ranges.lastIndex)
            ranges[ranges.lastIndex] = ranges.last().first to last.second
        }
        return ranges
    }
}
