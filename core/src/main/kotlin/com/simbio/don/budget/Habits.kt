package com.simbio.don.budget

import java.time.LocalDate

/**
 * 부자들의 소비법 ① 필요와 욕구를 나눈다.
 * 필요 = 살아가는 데 드는 돈 (밥·교통·생활용품·병원·교육·집), 욕구 = 갖고 싶어서 쓰는 돈 (카페·배달·쇼핑·놀거리).
 * 기록마다 바꿀 수 있고(`want` 0/1), 안 바꾸면 분류로 정한다.
 */
object Wants {
    /** 처음에 "필요"로 보는 분류 (나머지와 직접 만든 분류는 욕구) */
    val NEED: Set<String> = setOf(
        Category.FOOD.name, Category.TRANSPORT.name, Category.LIVING.name, Category.HEALTH.name,
        Category.EDUCATION.name, Category.BILLS.name, Category.EVENT.name, "PET",
    )

    /** [override]: 기록에 직접 고른 값 (1 욕구, 0 필요, null 분류대로) */
    fun isWant(category: Category, override: Int?): Boolean = when (override) {
        1 -> true
        0 -> false
        else -> category.income.not() && category.name !in NEED
    }

    /** 생활비로 쓴 돈을 필요·욕구로 (환불은 뺀다, 고정·수입·이체는 넣지 않는다) */
    data class Split(val need: Long, val want: Long) {
        val total: Long get() = need + want

        /** 욕구 비율 (0~100) */
        val wantPercent: Int get() = if (total <= 0) 0 else (want * 100 / total).toInt()
    }

    fun split(items: List<Pair<Entry, Int?>>): Split {
        var need = 0L
        var want = 0L
        items.forEach { (e, o) ->
            val v = when (e.type) {
                EntryType.SPEND, EntryType.EXCEPTION -> e.amount
                EntryType.REFUND -> -e.amount
                else -> 0L
            }
            if (isWant(e.category, o)) want += v else need += v
        }
        return Split(need.coerceAtLeast(0), want.coerceAtLeast(0))
    }
}

/**
 * 부자들의 소비법 ② 빠른 보상을 긴 보상으로: 지금 사고 싶은 걸 "최저시급으로 몇 시간 일한 돈"과
 * "참으면 1년에 얼마"로 바꿔 보여 준다.
 */
object MinWage {
    /** 해마다 최저시급 (원). 모르는 해는 알고 있는 가장 최근 값 */
    private val HOURLY = mapOf(2024 to 9_860L, 2025 to 10_030L, 2026 to 10_320L)

    fun hourly(year: Int): Long = HOURLY[year] ?: HOURLY.filterKeys { it <= year }.maxByOrNull { it.key }?.value ?: HOURLY.values.last()

    /** 최저시급으로 몇 분 일해야 버는 돈 */
    fun minutes(amount: Long, year: Int): Long = if (amount <= 0) 0 else (amount * 60 + hourly(year) - 1) / hourly(year)

    /** "5시간 12분" */
    fun text(minutes: Long): String = when {
        minutes < 60 -> "${minutes}분"
        minutes % 60 == 0L -> "${minutes / 60}시간"
        else -> "${minutes / 60}시간 ${minutes % 60}분"
    }

    /** 한 달에 한 번 이만큼 참으면 1년에 모이는 돈 */
    fun yearIfSkippedMonthly(amount: Long): Long = amount * 12

    /** 일주일에 한 번 이만큼 참으면 1년에 모이는 돈 */
    fun yearIfSkippedWeekly(amount: Long): Long = amount * 52
}

/**
 * 부자들의 소비법 ③ 사고 싶은 건 바로 사지 말고 7일 참아 본다. 7일이 지나도 사고 싶으면 그때 산다.
 */
object WaitRule {
    const val DAYS = 7L

    fun remindOn(created: LocalDate): LocalDate = created.plusDays(DAYS)

    /** 오늘 기준 남은 날 (0이면 오늘이 그날, 음수면 지남) */
    fun daysLeft(remind: LocalDate, today: LocalDate): Long = remind.toEpochDay() - today.toEpochDay()

    fun due(remind: LocalDate, today: LocalDate): Boolean = !today.isBefore(remind)
}
