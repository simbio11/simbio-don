package com.simbio.don.budget

import java.time.LocalDate

data class CategoryTotal(val category: Category, val amount: Long, val count: Int, val shareBps: Int)

data class PlaceTotal(val label: String, val amount: Long, val count: Int)

/** 소분류별 합계. [sub]가 null이면 소분류를 안 고른 것 */
data class SubTotal(val sub: String?, val amount: Long, val count: Int)

/** 한 달(월급 주기) 합계 — 연간 통계용 */
data class MonthTotal(val nominal: java.time.YearMonth, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

/** 한 달 지출 속도: [cumulative]는 시작일부터 하루씩 누적 지출, [pace]는 예산을 고르게 썼을 때의 누적. */
data class SpendingPace(
    val start: LocalDate,
    val cumulative: List<Long>,
    val pace: List<Long>,
    /** 오늘까지 기록된 날 수 (cumulative 중 의미 있는 앞부분) */
    val elapsedDays: Int,
    /** 지금 속도로 계속 쓰면 한 달 끝에 남는 돈 (음수면 모자람) */
    val projectedLeft: Long,
) {
    fun dateAt(i: Int): LocalDate = start.plusDays(i.toLong())
}

/**
 * 가계부 통계. 실제로 나간 돈 기준이라 예외·고정 지출도 포함하고, 환불은 그 카테고리에서 뺀다.
 * 생활비([living])는 고정 지출을 뺀 것 — 주 예산·모의 지갑·지출 속도는 이것으로 본다.
 * (주 예산 판정은 [CycleLedger]가 따로 한다.)
 */
object Stats {

    /** 가계부 지출 쪽 금액 (환불은 빼고, 수입·이체는 0, 고정 지출 포함) */
    fun signed(e: Entry): Long = when (e.type) {
        EntryType.REFUND -> -e.amount
        EntryType.INCOME, EntryType.TRANSFER -> 0
        else -> e.amount
    }

    /** 생활비 쪽 금액: 고정 지출은 월급에서 미리 뺐으므로 0 */
    fun living(e: Entry): Long = if (e.type == EntryType.FIXED) 0 else signed(e)

    /** 가계부 지출 합계 (고정 지출 포함) */
    fun total(entries: List<Entry>): Long = entries.sumOf { signed(it) }.coerceAtLeast(0)

    /** 생활비로 쓴 돈 (고정 지출 뺌) */
    fun livingTotal(entries: List<Entry>): Long = entries.sumOf { living(it) }.coerceAtLeast(0)

    /** 고정 지출 합계 */
    fun fixedTotal(entries: List<Entry>): Long = entries.filter { it.type == EntryType.FIXED }.sumOf { it.amount }

    /** 수입 합계 */
    fun income(entries: List<Entry>): Long = entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount }

    private fun spending(entries: List<Entry>) = entries.filter { it.type != EntryType.INCOME && it.type != EntryType.TRANSFER }

    /** 카테고리별 지출 (수입 제외) */
    fun byCategory(entries: List<Entry>): List<CategoryTotal> {
        val groups = spending(entries).groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { signed(it) } to list.count { it.type != EntryType.REFUND } }
            .filterValues { it.first > 0 }
        val sum = groups.values.sumOf { it.first }
        return groups.map { (c, v) ->
            CategoryTotal(c, v.first, v.second, if (sum == 0L) 0 else (v.first * 10_000 / sum).toInt())
        }.sortedByDescending { it.amount }
    }

    /** [category] 안의 소분류별 (지출 카테고리면 지출, 수입 카테고리면 수입) */
    fun bySub(entries: List<Entry>, category: Category): List<SubTotal> {
        val inCat = entries.filter { it.category == category }
        val list = if (category.income) inCat.filter { it.type == EntryType.INCOME } else spending(inCat)
        return list.groupBy { it.sub?.takeIf(String::isNotBlank) }
            .map { (s, l) ->
                val a = if (category.income) l.sumOf { it.amount } else l.sumOf { signed(it) }
                SubTotal(s, a, l.count { it.type != EntryType.REFUND })
            }
            .filter { it.amount > 0 }
            .sortedWith(compareBy<SubTotal> { it.sub == null }.thenByDescending { it.amount })
    }

    /** 날짜별 지출 ([livingOnly]면 고정 지출 뺌) */
    fun daily(entries: List<Entry>, livingOnly: Boolean = false): Map<LocalDate, Long> =
        spending(entries).groupBy { it.date }
            .mapValues { (_, l) -> l.sumOf { if (livingOnly) living(it) else signed(it) } }
            .filterValues { it != 0L }

    /** 날짜별 수입 */
    fun dailyIncome(entries: List<Entry>): Map<LocalDate, Long> =
        entries.filter { it.type == EntryType.INCOME }.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { it.amount } }

    /** 수입 카테고리별 */
    fun incomeByCategory(entries: List<Entry>): List<CategoryTotal> {
        val groups = entries.filter { it.type == EntryType.INCOME }.groupBy { it.category }
        val sum = groups.values.sumOf { l -> l.sumOf { it.amount } }
        return groups.map { (c, l) ->
            val a = l.sumOf { it.amount }
            CategoryTotal(c, a, l.size, if (sum == 0L) 0 else (a * 10_000 / sum).toInt())
        }.sortedByDescending { it.amount }
    }

    /** 생활비를 많이 쓴 곳 (고정 지출·이체 제외) */
    fun topPlaces(entries: List<Entry>, n: Int = 5): List<PlaceTotal> =
        entries.filter { it.type == EntryType.SPEND || it.type == EntryType.EXCEPTION }
            .groupBy { CategoryGuesser.key(it.label) }
            .map { (_, l) -> PlaceTotal(l.first().label, l.sumOf { it.amount }, l.size) }
            .sortedByDescending { it.amount }
            .take(n)

    /** 생활비 지출 속도 (고정 지출 제외 — 생활비 예산과 견준다) */
    fun pace(entries: List<Entry>, start: LocalDate, end: LocalDate, budget: Long, today: LocalDate): SpendingPace {
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val perDay = daily(entries, livingOnly = true)
        var acc = 0L
        val cumulative = (0 until days).map { i -> acc += perDay[start.plusDays(i.toLong())] ?: 0; acc }
        val pace = (1..days).map { budget * it / days }
        val elapsed = when {
            today.isBefore(start) -> 0
            today.isAfter(end) -> days
            else -> (today.toEpochDay() - start.toEpochDay() + 1).toInt()
        }
        val spent = if (elapsed == 0) 0 else cumulative[elapsed - 1]
        val projected = if (elapsed == 0) 0 else spent * days / elapsed
        return SpendingPace(start, cumulative, pace, elapsed, budget - projected)
    }

    /** 이번 주 남은 돈을 오늘 포함 남은 날 수로 나눈 하루 몫. */
    fun todayAllowance(week: WeekResult, today: LocalDate): Long {
        if (today !in week.week) return 0
        val left = week.week.end.toEpochDay() - today.toEpochDay() + 1
        return (week.available / left).coerceAtLeast(0)
    }
}
