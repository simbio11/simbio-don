package com.simbio.don.budget

import java.time.LocalDate

data class CategoryTotal(val category: Category, val amount: Long, val count: Int, val shareBps: Int)

data class PlaceTotal(val label: String, val amount: Long, val count: Int)

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
 * 가계부 통계. 실제로 나간 돈 기준이라 예외 지출도 포함하고, 환불은 그 카테고리에서 뺀다.
 * (주 예산 판정은 [CycleLedger]가 따로 한다.)
 */
object Stats {

    fun signed(e: Entry): Long = if (e.type == EntryType.REFUND) -e.amount else e.amount

    fun total(entries: List<Entry>): Long = entries.sumOf { signed(it) }.coerceAtLeast(0)

    fun byCategory(entries: List<Entry>): List<CategoryTotal> {
        val groups = entries.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { signed(it) } to list.count { it.type != EntryType.REFUND } }
            .filterValues { it.first > 0 }
        val sum = groups.values.sumOf { it.first }
        return groups.map { (c, v) ->
            CategoryTotal(c, v.first, v.second, if (sum == 0L) 0 else (v.first * 10_000 / sum).toInt())
        }.sortedByDescending { it.amount }
    }

    fun daily(entries: List<Entry>): Map<LocalDate, Long> =
        entries.groupBy { it.date }.mapValues { (_, l) -> l.sumOf { signed(it) } }.filterValues { it != 0L }

    fun topPlaces(entries: List<Entry>, n: Int = 5): List<PlaceTotal> =
        entries.filter { it.type != EntryType.REFUND }
            .groupBy { CategoryGuesser.key(it.label) }
            .map { (_, l) -> PlaceTotal(l.first().label, l.sumOf { it.amount }, l.size) }
            .sortedByDescending { it.amount }
            .take(n)

    fun pace(entries: List<Entry>, start: LocalDate, end: LocalDate, budget: Long, today: LocalDate): SpendingPace {
        val days = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val perDay = daily(entries)
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
