package com.simbio.don.budget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 돈 나갈 계획 하나. 실제 기록과 따로 두고, 쓰면 [actual](실제로 쓴 돈)을 채워 계획과 견준다.
 * 안 쓰기로 하면 [canceled] — 그만큼 아낀 돈.
 */
data class Plan(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val category: Category = Category.ETC,
    val actual: Long? = null,
    val canceled: Boolean = false,
) {
    val open: Boolean get() = actual == null && !canceled
}

/**
 * 한 달 계획 요약.
 * @property planned 이 달 계획 합계 (안 쓰기로 한 것 빼고)
 * @property openAmount 아직 안 쓴 계획
 * @property donePlanned 쓴 계획들의 계획 금액, [doneActual]은 그 실제 금액
 * @property canceledAmount 안 쓰기로 한 계획 (아낀 돈)
 */
data class PlanSummary(
    val planned: Long,
    val openAmount: Long,
    val openCount: Int,
    val donePlanned: Long,
    val doneActual: Long,
    val doneCount: Int,
    val canceledAmount: Long,
) {
    /** 계획보다 덜 쓴 돈 (음수면 더 씀) */
    val doneDiff: Long get() = donePlanned - doneActual
}

object Plans {
    /** [start]~[end] 사이 날짜의 계획 요약 */
    fun summary(plans: List<Plan>, start: LocalDate, end: LocalDate): PlanSummary {
        val inRange = plans.filter { it.date in start..end }
        val open = inRange.filter { it.open }
        val done = inRange.filter { it.actual != null }
        val canceled = inRange.filter { it.canceled }
        return PlanSummary(
            planned = inRange.filter { !it.canceled }.sumOf { it.amount },
            openAmount = open.sumOf { it.amount },
            openCount = open.size,
            donePlanned = done.sumOf { it.amount },
            doneActual = done.sumOf { it.actual ?: 0 },
            doneCount = done.size,
            canceledAmount = canceled.sumOf { it.amount },
        )
    }

    /** 아직 안 쓴 계획 중 [from]부터 [to]까지 날짜인 것 (날짜순) */
    fun upcoming(plans: List<Plan>, from: LocalDate, to: LocalDate): List<Plan> =
        plans.filter { it.open && it.date in from..to }.sortedBy { it.date }

    /** 날짜가 지났는데 아직 안 쓴(썼다고 안 한) 계획 */
    fun overdue(plans: List<Plan>, today: LocalDate): List<Plan> = plans.filter { it.open && it.date.isBefore(today) }.sortedBy { it.date }

    /**
     * 이 계획을 쓴 것 같은 실제 지출: 날짜가 3일 안이고, 금액이 ±30% 안이며, 같은 카테고리이거나 이름이 겹치면.
     * 가장 가까운 것 하나 (이미 다른 계획에 쓴 [taken]은 빼고).
     */
    fun <T> match(plan: Plan, candidates: List<Pair<T, Entry>>, taken: Set<T> = emptySet()): T? =
        candidates.filter { (id, e) ->
            id !in taken &&
                (e.type == EntryType.SPEND || e.type == EntryType.EXCEPTION || e.type == EntryType.FIXED) &&
                kotlin.math.abs(ChronoUnit.DAYS.between(plan.date, e.date)) <= 3 &&
                kotlin.math.abs(e.amount - plan.amount) * 10 <= plan.amount * 3 &&
                (e.category == plan.category || nameOverlaps(plan.label, e.label))
        }.minByOrNull { (_, e) -> kotlin.math.abs(e.amount - plan.amount) * 1000 + kotlin.math.abs(ChronoUnit.DAYS.between(plan.date, e.date)) }?.first

    private fun nameOverlaps(a: String, b: String): Boolean {
        val x = CategoryGuesser.key(a)
        val y = CategoryGuesser.key(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x in y || y in x)
    }
}
