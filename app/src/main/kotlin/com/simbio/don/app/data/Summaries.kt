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
    /** 생활비에서 남긴 돈 (지난 달은 마지막 주 남은 돈, 이번 달은 지금까지 아낀 돈). 넘게 썼으면 음수 */
    val leftover: Long = 0,
)

/**
 * 모은 돈 나눠 보기.
 * - 저축: 예금·적금·투자처럼 오래 모으는 돈 (목표에 이은 통장은 뺌). 그런 통장이 없으면 월급에서 먼저 뗀 저축의 합(어림).
 * - 목표 모으기: 여행·새 폰처럼 쓰려고 모으는 돈. 목표마다 넣은 만큼만 찬다.
 * - 아낀 돈: 생활비에서 남긴 돈. 목표에 넣을 수 있다 ([unallocated]가 아직 안 넣은 돈).
 */
data class SavingsSplit(
    val savings: Long = 0,
    val savingsFromAccounts: Boolean = false,
    val goals: Long = 0,
    val leftover: Long = 0,
    val leftoverThisMonth: Long = 0,
    val unallocated: Long = 0,
) {
    /** 모은 돈 모두 = 저축 + 목표 + 아직 안 넣은 아낀 돈 */
    val total: Long get() = savings + goals + unallocated
}

/** 모으는 목표 하나의 진행. [linked]면 이은 통장 잔액이 곧 [filled] */
data class GoalProgress(val goal: SavingsGoalEntity, val filled: Long, val linked: Boolean = false) {
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
            val plan = plan(payDay, cycles, i)
            val cycle = plan.cycle
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
                leftover = leftover,
            )
        }

    /** [cycles](최신순)의 [i]번째 달 예산 — 다음 달 시작 전날까지 */
    fun plan(payDay: Int, cycles: List<CycleEntity>, i: Int): com.simbio.don.budget.CyclePlan {
        val c = cycles[i]
        val base = c.toPayCycle(payDay)
        val next = cycles.getOrNull(i - 1)
        val cycle = next?.let { base.copy(end = LocalDate.ofEpochDay(it.startEpochDay).minusDays(1).coerceAtLeast(base.start)) } ?: base
        return BudgetEngine.plan(
            cycle,
            salary = c.salary,
            fixedExpenses = listOf(FixedExpense("고정비", c.fixedTotal)),
            savings = c.savings,
            settlementDay = DayOfWeek.of(c.settlementDay),
            disposableOverride = c.budgetOverride,
        )
    }

    /**
     * 진행 중인 목표: 목표마다 넣고 뺀 돈의 합. 통장을 이었으면 그 통장 잔액 ([balances]: 통장 id → 잔액).
     * 예금·적금 같은 저축은 목표에 저절로 들어가지 않는다.
     */
    fun goals(goals: List<SavingsGoalEntity>, moves: List<GoalMoveEntity>, balances: Map<Long, Long> = emptyMap()): List<GoalProgress> {
        val byGoal = moves.groupBy { it.goalId }
        return goals.filter { !it.used }.sortedWith(compareBy({ it.sortOrder }, { it.id })).map { g ->
            val linked = g.accountId?.let { balances[it] }
            GoalProgress(g, (linked ?: byGoal[g.id].orEmpty().sumOf { it.amount }).coerceAtLeast(0), linked != null)
        }
    }

    /**
     * 모은 돈 나눠 보기. [cycles]는 달마다 요약(최신 먼저), [pots]는 저축 통장 합(목표에 이은 통장 뺌, 없으면 null).
     * 아낀 돈은 달마다 0 아래로는 안 센다 (한 달 넘게 써도 지난달 아낀 돈을 깎지는 않는다).
     */
    fun split(cycles: List<CycleSummary>, pots: Long?, goals: List<GoalProgress>, moves: List<GoalMoveEntity>): SavingsSplit {
        val leftover = cycles.sumOf { it.leftover.coerceAtLeast(0) }
        val allocated = moves.filter { it.source == GoalSource.SAVED.name }.sumOf { it.amount }
        return SavingsSplit(
            savings = pots ?: cycles.sumOf { it.savings },
            savingsFromAccounts = pots != null,
            goals = goals.sumOf { it.filled },
            leftover = leftover,
            leftoverThisMonth = cycles.firstOrNull()?.leftover?.coerceAtLeast(0) ?: 0,
            unallocated = (leftover - allocated).coerceAtLeast(0),
        )
    }

    /** 최근부터 거꾸로 세어 예산을 지킨 결산이 몇 주 이어졌는지 */
    fun streak(settled: List<SettledWeekEntity>): Int =
        settled.sortedWith(compareBy({ it.nominal }, { it.weekIndex })).asReversed().takeWhile { it.kept == true }.size

    /** 다음 마일스톤 (모은 돈) */
    val MILESTONES = listOf(100_000L, 300_000, 500_000, 1_000_000, 2_000_000, 3_000_000, 5_000_000, 10_000_000, 20_000_000, 30_000_000, 50_000_000, 100_000_000)

    fun reachedMilestone(total: Long): Long? = MILESTONES.lastOrNull { total >= it }

    fun nextMilestone(total: Long): Long? = MILESTONES.firstOrNull { total < it }
}
