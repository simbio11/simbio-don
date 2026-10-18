package com.simbio.don.game

import kotlin.math.roundToLong

/**
 * 마일리지. 실제로 아꼈을 때만 쌓이는 게임 돈 (원화로 바꿔 보여주지 않는다).
 *
 * - 결산한 주마다: 기본 주 예산 대비 아낀 1% = 100마일 (= 아낀 비율 bps), 최대 100%.
 *   7일보다 짧거나 긴 주는 일수에 비례. 예산을 넘긴 주는 0 (모의 구매 반품도 0 — 일부러 쓰고 돌려받는 꼼수 차단).
 * - 연속 지킴 보너스: 이번 주까지 n주 연속이면 +(n−1)×10%, 최대 +50%.
 * - 비율로 쌓이므로 월급이 많든 적든 같은 속도로 모인다.
 */
object Miles {
    /** 물건 값 환산: 모두에게 같은 비율 (100원 = 1마일) */
    const val WON_PER_MILE = 100L

    const val STREAK_STEP_BPS = 1_000
    const val STREAK_MAX_BPS = 5_000

    /** 원화 가격 → 마일 (올림) */
    fun ofWon(won: Long): Long = if (won <= 0) 0 else (won + WON_PER_MILE - 1) / WON_PER_MILE

    data class Earned(val base: Long, val bonus: Long, val bonusBps: Int) {
        val total: Long get() = base + bonus
    }

    /** 결산한 주 하나의 마일. [streak]은 이번 주를 포함한 연속 지킴 횟수 (넘긴 주면 0) */
    fun forWeek(savedBps: Int, overspent: Long, days: Int, streak: Int): Earned {
        if (savedBps <= 0 || overspent > 0) return Earned(0, 0, 0)
        val base = savedBps.coerceAtMost(10_000).toLong() * days.coerceIn(1, 14) / 7
        val bonusBps = ((streak - 1).coerceAtLeast(0) * STREAK_STEP_BPS).coerceAtMost(STREAK_MAX_BPS)
        return Earned(base, base * bonusBps / 10_000, bonusBps)
    }
}

/**
 * 마일 키우기: 맡긴 마일이 한 주마다 0.5%씩 붙는 복리 (실제 이자와 헷갈리지 않게 게임 이름).
 * 자유롭게 넣고 뺀다. 1년이면 약 1.3배, 3년이면 약 2.2배 — 너무 높으면 물건이 공짜가 된다.
 */
object MileGrowth {
    const val WEEKLY_BPS = 50

    /** [weeks]주 동안 불린 잔액 (주마다 내림) */
    fun grow(balance: Long, weeks: Int): Long {
        var b = balance
        repeat(weeks.coerceAtLeast(0)) { b += b * WEEKLY_BPS / 10_000 }
        return b
    }

    /** 지금 잔액으로 [weeks]주 뒤 예상 (보여주기용) */
    fun projected(balance: Long, weeks: Int): Long = grow(balance, weeks)
}

/** 카드 반짝이: 산 주에 실제로 아낀 비율 (금액이 아니라 비율이라 월급과 상관없다). 뽑기 없음 */
enum class CardGrade(val label: String) {
    NORMAL("일반"),
    RARE("레어"),
    LEGEND("전설"),
    ;

    companion object {
        /** 0~9% 일반 · 10~24% 레어 · 25%+ 전설 (모르면 일반) */
        fun of(savedBps: Int?): CardGrade = when {
            savedBps == null || savedBps < 1_000 -> NORMAL
            savedBps < 2_500 -> RARE
            else -> LEGEND
        }
    }
}

/** 카드 테두리: 물건 값 구간 */
enum class PriceTier(val label: String, val minWon: Long) {
    BRONZE("브론즈", 0),
    SILVER("실버", 10_000),
    GOLD("골드", 50_000),
    DIAMOND("다이아", 200_000),
    ;

    companion object {
        fun of(price: Long): PriceTier = entries.last { price >= it.minWon }
    }
}

/** 카드에 적는 "이 돈이면": 월급의 몇 %, 몇 시간 일한 돈 */
object Cards {
    /** 한 달 표준 근로시간 (주 40시간 기준 209시간) */
    const val MONTH_HOURS = 209

    /** 월급 대비 (bps). 월급을 모르면 null */
    fun salaryShareBps(price: Long, salary: Long): Int? = if (salary <= 0) null else (price * 10_000 / salary).toInt()

    /** 이 돈을 벌려고 일한 시간 (0.1시간 단위). 월급을 모르면 null */
    fun workHours(price: Long, salary: Long): Double? =
        if (salary <= 0) null else (price.toDouble() * MONTH_HOURS / salary * 10).roundToLong() / 10.0
}
