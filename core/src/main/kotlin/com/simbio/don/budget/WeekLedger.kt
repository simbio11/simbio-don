package com.simbio.don.budget

enum class SpendSource {
    /** 게임 안 모의 쇼핑·배달 */
    MOCK,

    /** 결제 알림에서 자동 기록된 실제 지출 */
    REAL,
}

data class Spend(val amount: Long, val label: String, val source: SpendSource) {
    init {
        require(amount > 0) { "지출은 0원보다 커야 합니다: $label=$amount" }
    }
}

enum class BonusKind { INTEREST, INVESTMENT, MINIGAME }

data class Bonus(val amount: Long, val kind: BonusKind)

/**
 * 한 주의 예산 장부. 불변 객체이며 기록할 때마다 새 장부를 돌려준다.
 *
 * 재화 불리기 보너스는 주 예산의 [bonusCapBps] (만분율)까지만 쌓인다.
 * 너무 잘 불리면 절약의 의미가 사라지므로 상한은 5~10% 범위로 제한한다.
 */
data class WeekLedger(
    val budget: Long,
    val bonusCapBps: Int = DEFAULT_BONUS_CAP_BPS,
    val spends: List<Spend> = emptyList(),
    val bonuses: List<Bonus> = emptyList(),
) {
    init {
        require(budget >= 0) { "주 예산은 0원 이상이어야 합니다: $budget" }
        require(bonusCapBps in MIN_BONUS_CAP_BPS..MAX_BONUS_CAP_BPS) {
            "보너스 상한은 주 예산의 5~10% 범위여야 합니다: ${bonusCapBps}bps"
        }
    }

    val spent: Long get() = spends.sumOf { it.amount }
    val bonusTotal: Long get() = bonuses.sumOf { it.amount }
    val bonusCap: Long get() = budget * bonusCapBps / 10_000
    val bonusRoom: Long get() = bonusCap - bonusTotal

    /** 예산 + 보너스 − 지출. 음수면 초과 지출. */
    val remaining: Long get() = budget + bonusTotal - spent

    /** 이번 주 절약액 = 남은 잔액 (초과 지출이면 0). */
    val saved: Long get() = remaining.coerceAtLeast(0)
    val overspent: Long get() = (-remaining).coerceAtLeast(0)

    fun spend(amount: Long, label: String, source: SpendSource = SpendSource.MOCK): WeekLedger =
        copy(spends = spends + Spend(amount, label, source))

    /** 상한을 넘는 보너스는 잘라낸다. 이미 상한이면 장부는 그대로. */
    fun addBonus(amount: Long, kind: BonusKind): WeekLedger {
        require(amount > 0) { "보너스는 0원보다 커야 합니다: $amount" }
        val granted = minOf(amount, bonusRoom)
        if (granted <= 0) return this
        return copy(bonuses = bonuses + Bonus(granted, kind))
    }

    companion object {
        const val MIN_BONUS_CAP_BPS = 500
        const val MAX_BONUS_CAP_BPS = 1_000
        const val DEFAULT_BONUS_CAP_BPS = MIN_BONUS_CAP_BPS

        fun of(week: WeekSlice, bonusCapBps: Int = DEFAULT_BONUS_CAP_BPS) = WeekLedger(week.budget, bonusCapBps)
    }
}
