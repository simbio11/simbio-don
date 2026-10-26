package com.simbio.don.budget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 오늘 몫. 그날 쓰는 돈(식비·카페·배달·교통처럼 날마다 나가는 것)은 **오늘 몫에서** 빼고,
 * 큰 지출(경조사·쇼핑처럼 가끔 크게 나가는 것)과 이번 주 남은 계획은 **이번 주에서** 미리 빼 남은 날에 나눈다.
 *
 * 오늘 몫 = (오늘 아침 이번 주 남은 돈 − 오늘 큰 지출 − 오늘부터 이번 주 끝까지 잡힌 계획) ÷ 오늘 포함 남은 날.
 * 오늘 남은 돈 = 오늘 몫 − 오늘 그날 지출 (음수면 넘음).
 *
 * 미리 계획한 돈(일정에 정한 예산·돈 나갈 계획)을 "썼어요"로 이은 기록은 식비라도 큰 지출로 본다 —
 * 이미 이번 주에서 떼어 둔 돈이라서. 계획보다 더 쓴 건 그 일정에서 "넘음"으로 보인다.
 */
data class DayBudget(
    val allowance: Long,
    /** 오늘 그날 지출 (환불은 뺌) */
    val spentDaily: Long,
    /** 오늘 큰 지출 (이번 주에서 나눔) */
    val spentBig: Long,
    /** 이번 주 남은 날에 미리 뺀 계획 */
    val reserved: Long,
    /** 오늘 포함 이번 주 남은 날 */
    val daysLeft: Int,
) {
    val left: Long get() = allowance - spentDaily
    val over: Boolean get() = left < 0

    companion object {
        val Empty = DayBudget(0, 0, 0, 0, 0)
    }
}

object DayBudgets {
    /** 처음에 "그날 쓰는 돈"으로 보는 분류 (설정에서 바꿀 수 있다) */
    val DEFAULT_DAILY: Set<String> = setOf(Category.FOOD.name, Category.CAFE.name, Category.DELIVERY.name, Category.TRANSPORT.name)

    /** 이번 주 예산에 들어가는 금액 (CycleLedger와 같게: 생활비 +, 환불 −, 나머지 0) */
    private fun counted(e: Entry): Long = when (e.type) {
        EntryType.SPEND -> e.amount
        EntryType.REFUND -> -e.amount
        else -> 0
    }

    /**
     * [week]: 이번 주 결과, [entries]: 이번 주 기록, [daily]: 그날 쓰는 돈으로 볼 분류 이름,
     * [plans]: 아직 안 쓴 계획 (날짜, 금액), [planned]: 계획과 이은 기록인지.
     */
    fun of(
        week: WeekResult,
        entries: List<Entry>,
        today: LocalDate,
        daily: Set<String>,
        plans: List<Pair<LocalDate, Long>> = emptyList(),
        planned: (Entry) -> Boolean = { false },
    ): DayBudget {
        if (today !in week.week) return DayBudget.Empty
        val todays = entries.filter { it.date == today }
        val spentToday = todays.sumOf { counted(it) }
        val spentDaily = todays.filter { it.category.name in daily && !planned(it) }.sumOf { counted(it) }
        val spentBig = spentToday - spentDaily
        val reserved = plans.filter { (d, _) -> d in today..week.week.end }.sumOf { it.second }
        val daysLeft = (ChronoUnit.DAYS.between(today, week.week.end) + 1).toInt().coerceAtLeast(1)
        // 오늘 아침의 이번 주 남은 돈 = 지금 남은 돈 + 오늘 쓴 돈
        val morning = week.available + spentToday
        val allowance = ((morning - spentBig - reserved) / daysLeft).coerceAtLeast(0)
        return DayBudget(allowance, spentDaily, spentBig, reserved, daysLeft)
    }
}
