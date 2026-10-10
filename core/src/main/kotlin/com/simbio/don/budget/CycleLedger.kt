package com.simbio.don.budget

import java.time.LocalDate

enum class EntryType {
    /** 일반 실제 지출 — 그 주 예산을 깎는다 */
    SPEND,

    /** 취소·환불 — 그 주 지출에서 뺀다 */
    REFUND,

    /** 병원비·경조사비 등 예외 지출 — 그 주는 그대로, 남은 주 예산을 다시 나눈다 */
    EXCEPTION,
}

enum class EntrySource { NOTIFICATION, MANUAL }

data class Entry(
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val type: EntryType = EntryType.SPEND,
    val source: EntrySource = EntrySource.MANUAL,
    val category: Category = Category.ETC,
) {
    init {
        require(amount > 0) { "금액은 0원보다 커야 합니다: $label=$amount" }
    }
}

/**
 * 한 주의 결산 결과.
 *
 * 남은 돈은 다음 주로 이월되지만([carryIn]), 아낀 비율·초과 판정은 이월을 뺀 **기본 주 예산**([budget]) 기준이다.
 * 이월분을 쓰면 그 주는 초과로 잡힌다 (아끼고 몰아 쓰기로 보상만 쌓는 꼼수 차단).
 */
data class WeekResult(
    val week: Week,
    val carryIn: Long,
    val spent: Long,
    /** 이번 주 이후 남은 주가 없어 다시 나눌 곳이 없는 예외 지출 */
    val exceptionOverflow: Long,
) {
    val budget: Long get() = week.budget

    /** 이번 주 쓸 수 있는 돈 = 기본 주 예산 + 이월 − 지출 */
    val available: Long get() = budget + carryIn - spent - exceptionOverflow
    val carryOut: Long get() = available

    val savedVsBudget: Long get() = (budget - spent).coerceAtLeast(0)
    val overspent: Long get() = (spent - budget).coerceAtLeast(0)

    /** 기본 주 예산 대비 아낀 비율 (만분율, 0~10000). */
    val savedBps: Int
        get() = if (budget <= 0) 0 else (savedVsBudget * 10_000 / budget).coerceAtMost(10_000).toInt()
}

/** 한 주기(월급날 ~ 다음 월급날 전날)의 장부. 불변이며 기록할 때마다 새 장부를 돌려준다. */
data class CycleLedger(val plan: CyclePlan, val entries: List<Entry> = emptyList()) {

    val exceptionCount: Int get() = entries.count { it.type == EntryType.EXCEPTION }
    val canAddException: Boolean get() = exceptionCount < MAX_EXCEPTIONS

    fun add(entry: Entry): CycleLedger {
        require(entry.date in plan.cycle) { "${entry.date}는 이번 주기(${plan.cycle.start}~${plan.cycle.end}) 밖입니다" }
        if (entry.type == EntryType.EXCEPTION) {
            require(canAddException) { "예외 지출은 한 달에 ${MAX_EXCEPTIONS}번까지입니다" }
        }
        return copy(entries = entries + entry)
    }

    fun remove(entry: Entry): CycleLedger = copy(entries = entries - entry)

    /** 예외 지출을 반영해 다시 나눈 주 목록. */
    fun adjustedWeeks(): List<Week> = applyExceptions().first

    fun results(): List<WeekResult> {
        val (weeks, overflow) = applyExceptions()
        var carry = 0L
        return weeks.mapIndexed { i, week ->
            val spent = entries.filter { it.date in week }.sumOf {
                when (it.type) {
                    EntryType.SPEND -> it.amount
                    EntryType.REFUND -> -it.amount
                    EntryType.EXCEPTION -> 0L
                }
            }
            WeekResult(week, carry, spent, overflow[i]).also { carry = it.carryOut }
        }
    }

    fun resultOn(date: LocalDate): WeekResult? = plan.weekIndexOf(date)?.let { results()[it] }

    /** [date]까지 결산된 주들에서 아낀 돈 (= 이번 주로 넘어온 이월액). */
    fun savedSoFar(date: LocalDate): Long = resultOn(date)?.carryIn ?: 0

    private fun applyExceptions(): Pair<List<Week>, List<Long>> {
        val budgets = plan.weeks.map { it.budget }.toMutableList()
        val overflow = MutableList(plan.weeks.size) { 0L }
        for (e in entries.filter { it.type == EntryType.EXCEPTION }.sortedBy { it.date }) {
            val i = plan.weekIndexOf(e.date) ?: continue
            val future = (i + 1 until budgets.size).toList()
            val futureTotal = future.sumOf { budgets[it] }
            val taken = minOf(e.amount, futureTotal)
            val redistributed = BudgetEngine.distribute(
                futureTotal - taken,
                future.map { plan.weeks[it].days.toLong() },
            )
            future.forEachIndexed { k, idx -> budgets[idx] = redistributed[k] }
            overflow[i] += e.amount - taken
        }
        return plan.weeks.mapIndexed { i, w -> w.copy(budget = budgets[i]) } to overflow
    }

    companion object {
        const val MAX_EXCEPTIONS = 2
    }
}
