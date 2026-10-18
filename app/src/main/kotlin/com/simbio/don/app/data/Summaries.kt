package com.simbio.don.app.data

import com.simbio.don.budget.BudgetEngine
import com.simbio.don.budget.CycleLedger
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PayCycle
import com.simbio.don.budget.Stats
import java.time.DayOfWeek
import java.time.LocalDate

/** 한 달(월급날 기준) 요약: 월별 추이·모은 돈 */
data class CycleSummary(
    val nominal: String,
    val cycle: PayCycle,
    val disposable: Long,
    /** 먼저 떼어둔 저축 */
    val savings: Long,
    /** 생활비로 쓴 돈 (고정 지출 뺌) — 생활비 예산과 견준다 */
    val spent: Long,
    val income: Long,
    /** 이 달에 모은 돈 = 먼저 뗀 저축 + 남은 생활비 (지난 달은 마지막 주 남은 돈, 이번 달은 지금까지 아낀 돈). 0 아래로는 안 감 */
    val saved: Long,
    val isCurrent: Boolean,
    /** 가계부 지출 (고정 지출 포함) */
    val expense: Long = spent,
)

/** 저축 목표 하나의 진행 */
data class GoalProgress(val goal: SavingsGoalEntity, val filled: Long) {
    val done: Boolean get() = filled >= goal.target
    val ratio: Float get() = if (goal.target <= 0) 1f else (filled.toFloat() / goal.target).coerceIn(0f, 1f)
}

object Summaries {
    /**
     * 달마다 요약 (최신 달 먼저). [cycles]는 최신순, [entries]는 그 기간 전체 기록.
     * 각 달은 다음 달 시작 전날까지 (실제 입금일 기준).
     */
    fun cycles(payDay: Int, cycles: List<CycleEntity>, entries: List<EntryEntity>, today: LocalDate): List<CycleSummary> =
        cycles.mapIndexed { i, c ->
            val base = c.toPayCycle(payDay)
            val next = cycles.getOrNull(i - 1)
            val cycle = next?.let { base.copy(end = LocalDate.ofEpochDay(it.startEpochDay).minusDays(1).coerceAtLeast(base.start)) } ?: base
            val plan = BudgetEngine.plan(
                cycle,
                salary = c.salary,
                fixedExpenses = listOf(FixedExpense("고정비", c.fixedTotal)),
                savings = c.savings,
                settlementDay = DayOfWeek.of(c.settlementDay),
                disposableOverride = c.budgetOverride,
            )
            val inCycle = entries.filter { it.epochDay in cycle.start.toEpochDay()..cycle.end.toEpochDay() }.map { it.toEntry() }
            val ledger = inCycle.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e) }.getOrDefault(l) }
            val leftover = if (i == 0) ledger.savedSoFar(today) else ledger.results().lastOrNull()?.carryOut ?: 0
            CycleSummary(
                nominal = c.nominal,
                cycle = cycle,
                disposable = plan.disposable,
                savings = c.savings,
                spent = Stats.livingTotal(inCycle),
                income = Stats.income(inCycle),
                saved = (c.savings + leftover).coerceAtLeast(0),
                isCurrent = i == 0,
                expense = Stats.total(inCycle),
            )
        }

    /** 모은 돈을 목표에 순서대로 채운다. 쓴 목표(used)는 그만큼 먼저 빠진다. */
    fun goals(total: Long, goals: List<SavingsGoalEntity>): List<GoalProgress> {
        var pool = (total - goals.filter { it.used }.sumOf { it.target }).coerceAtLeast(0)
        return goals.filter { !it.used }.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { g ->
            val filled = minOf(pool, g.target)
            pool -= filled
            GoalProgress(g, filled)
        }
    }

    /** 최근부터 거꾸로 세어 예산을 지킨 결산이 몇 주 이어졌는지 */
    fun streak(settled: List<SettledWeekEntity>): Int =
        settled.sortedWith(compareBy({ it.nominal }, { it.weekIndex })).asReversed().takeWhile { it.kept == true }.size

    /** 다음 마일스톤 (모은 돈) */
    val MILESTONES = listOf(100_000L, 300_000, 500_000, 1_000_000, 2_000_000, 3_000_000, 5_000_000, 10_000_000, 20_000_000, 30_000_000, 50_000_000, 100_000_000)

    fun reachedMilestone(total: Long): Long? = MILESTONES.lastOrNull { total >= it }

    fun nextMilestone(total: Long): Long? = MILESTONES.firstOrNull { total < it }
}
