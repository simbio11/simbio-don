package com.simbio.don.budget

import java.time.LocalDate

enum class EntryType {
    /** 일반 실제 지출 — 그 주 예산을 깎는다 */
    SPEND,

    /** 취소·환불 — 그 주 지출에서 뺀다 */
    REFUND,

    /** 병원비·경조사비 등 예외 지출 — 그 주는 그대로, 남은 주 예산을 다시 나눈다 */
    EXCEPTION,

    /** 수입 (월급·부수입·용돈 등) — 가계부에만 적고 주 예산은 건드리지 않는다 */
    INCOME,

    /** 고정 지출 (월세·통신비 등) — 월급에서 미리 뺐으므로 가계부 지출에만 들어가고 주 예산은 그대로 */
    FIXED,

    /** 내 자산끼리 옮긴 돈 (통장 → 현금, 카드값 내기) — 수입도 지출도 아니다 */
    TRANSFER,
}

enum class EntrySource {
    NOTIFICATION,
    MANUAL,

    /** 고정 지출을 정한 날에 자동으로 적은 것 */
    RECURRING,
}

data class Entry(
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val type: EntryType = EntryType.SPEND,
    val source: EntrySource = EntrySource.MANUAL,
    val category: Category = Category.ETC,
    /** 소분류 (카테고리 안에서 더 나눈 것, 없으면 null) */
    val sub: String? = null,
) {
    init {
        require(amount > 0) { "금액은 0원보다 커야 합니다: $label=$amount" }
    }
}

/**
 * 한 주의 결과.
 *
 * 0.31부터 주 예산은 "한 달 남은 생활비 ÷ 남은 날"로 정해진다 (Sure의 남은 예산 = 예산 − 쓴 돈 방식).
 * [budget]은 한 달 생활비를 날 수로 나눈 **기본 주 예산** (아낀 비율·초과 판정 기준),
 * [carryIn]은 그 주를 시작할 때 지난 주들에서 아끼거나 더 써서 늘거나 준 몫 (= 이번 주 쓸 수 있는 돈 − 기본 주 예산).
 */
data class WeekResult(
    val week: Week,
    val carryIn: Long,
    val spent: Long,
    /** 마지막 주라 뒤로 미룰 곳이 없는 예외 지출 */
    val exceptionOverflow: Long,
) {
    val budget: Long get() = week.budget

    /** 이번 주 쓸 수 있는 돈 − 쓴 돈 */
    val available: Long get() = budget + carryIn - spent - exceptionOverflow
    val carryOut: Long get() = available

    val savedVsBudget: Long get() = (budget - spent).coerceAtLeast(0)
    val overspent: Long get() = (spent - budget).coerceAtLeast(0)

    /** 기본 주 예산 대비 아낀 비율 (만분율, 0~10000). */
    val savedBps: Int
        get() = if (budget <= 0) 0 else (savedVsBudget * 10_000 / budget).coerceAtMost(10_000).toInt()
}

/**
 * 한 달에 나눠서 낼 큰돈 (경조사처럼): [from]이 든 주부터 이번 주기 끝까지 남은 생활비에서 미리 떼어 둔다.
 * 예외 지출과 달리 횟수 한도가 없다. 이 돈으로 쓴 기록은 주 지출로 다시 세지 않는다 (부르는 쪽이 이체로 넘긴다).
 */
data class Spread(val from: LocalDate, val amount: Long)

/**
 * 한 주기(월급날 ~ 다음 월급날 전날)의 장부. 불변이며 기록할 때마다 새 장부를 돌려준다.
 *
 * 계산은 한 가지뿐이다: **남은 생활비 = 한 달 생활비 − 쓴 돈**.
 * 주마다 쓸 수 있는 돈 = 그 주를 시작할 때 남은 생활비(미리 떼어 둔 돈 빼고) × 그 주 날 수 ÷ 남은 날 수.
 * 그래서 아끼면 남은 주가 저절로 늘고, 더 쓰면 저절로 줄고, 생활비를 고치면 오늘부터 다시 나뉜다 (따로 이월 계산이 없다).
 * 예외 지출은 그 주를 깎지 않고 다음 주부터 나뉘어 빠진다 (마지막 주면 그 주에서).
 */
data class CycleLedger(val plan: CyclePlan, val entries: List<Entry> = emptyList(), val spreads: List<Spread> = emptyList()) {

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

    /** 주마다 쓸 수 있는 돈으로 본 주 목록 */
    fun adjustedWeeks(): List<Week> = results().map { it.week.copy(budget = it.budget + it.carryIn) }

    fun results(): List<WeekResult> {
        val weeks = plan.weeks
        if (weeks.isEmpty()) return emptyList()
        val total = plan.disposable
        val end = plan.cycle.end.toEpochDay()
        val lastIndex = weeks.lastIndex
        return weeks.mapIndexed { i, week ->
            val start = week.start
            // 이 주 전까지 생활비로 쓴 돈 (예외 지출 포함)
            val before = entries.filter { it.date.isBefore(start) }.sumOf { living(it) }
            // 이 주까지 시작한 "한 달에 나눠서" 계획
            val reserved = spreads.filter { !it.from.isAfter(week.end) }.sumOf { it.amount }
            val daysLeft = end - start.toEpochDay() + 1
            val pool = total - before - reserved
            val can = if (i == lastIndex) pool else Math.floorDiv(pool * week.days, daysLeft)
            val inWeek = entries.filter { it.date in week }
            val spent = inWeek.sumOf { weekly(it) }
            val overflow = if (i == lastIndex) inWeek.filter { it.type == EntryType.EXCEPTION }.sumOf { it.amount } else 0L
            WeekResult(week, can - week.budget, spent, overflow)
        }
    }

    fun resultOn(date: LocalDate): WeekResult? = plan.weekIndexOf(date)?.let { results()[it] }

    /** [date]가 든 주 전까지 끝난 주들에서 기본 주 예산보다 아낀 돈 (더 썼으면 음수) */
    fun savedSoFar(date: LocalDate): Long {
        val i = plan.weekIndexOf(date) ?: return 0
        return plan.weeks.take(i).sumOf { w -> w.budget - entries.filter { it.date in w }.sumOf { weekly(it) } }
    }

    companion object {
        const val MAX_EXCEPTIONS = 2

        /** 한 달 생활비에 들어가는 돈: 생활비 +, 예외 지출 +, 환불 − (고정 지출·수입·이체는 0) */
        fun living(e: Entry): Long = when (e.type) {
            EntryType.SPEND, EntryType.EXCEPTION -> e.amount
            EntryType.REFUND -> -e.amount
            EntryType.INCOME, EntryType.FIXED, EntryType.TRANSFER -> 0L
        }

        /** 그 주 지출로 세는 돈: 예외 지출은 빼고 (다음 주부터 나뉜다) */
        fun weekly(e: Entry): Long = when (e.type) {
            EntryType.SPEND -> e.amount
            EntryType.REFUND -> -e.amount
            else -> 0L
        }
    }
}

/** 예외 지출을 한 달 한도보다 더 적었으면 버리지 않고 보통 지출로 센다 (장부에서 사라지면 돈이 안 맞는다) */
fun CycleLedger.addSafely(entry: Entry): CycleLedger =
    if (entry.type == EntryType.EXCEPTION && !canAddException) add(entry.copy(type = EntryType.SPEND)) else add(entry)
