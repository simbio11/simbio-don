package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/**
 * 이번 달 생활비가 계획에서 얼마나, 왜 바뀌었나. 바뀐 만큼은 쓴 돈이 아니라 **예산 자체**라서 남은 날에 다시 나뉜다.
 *
 * - [manual]: 직접 고친 것 (남은 생활비를 통장에 맞춤 등)
 * - 저축: 생활비 쪽 통장 → 저축·투자 통장으로 옮긴 돈 [savedIn], 거꾸로 [savedOut].
 *   한 달 계획한 저축 [planned]까지는 이미 월급에서 뗀 돈이라 그대로, **넘게 옮긴 만큼 생활비가 준다**.
 *   저축에서 꺼내 쓴 만큼(넣은 것보다 더 꺼냈으면)은 생활비가 는다.
 */
data class BudgetChange(val manual: Long, val savedIn: Long, val savedOut: Long, val planned: Long) {
    /** 저축을 계획보다 더 옮긴 돈 (생활비에서 빠짐) */
    val extraSaved: Long get() = (savedIn - savedOut - planned).coerceAtLeast(0)

    /** 저축에서 꺼내 생활비로 쓴 돈 */
    val withdrawn: Long get() = (savedOut - savedIn).coerceAtLeast(0)

    /** 생활비에 더할 금액 (음수면 줄어듦) */
    val total: Long get() = manual - extraSaved + withdrawn

    /** 계획한 저축 중 아직 안 옮긴 돈 */
    val plannedLeft: Long get() = (planned - (savedIn - savedOut)).coerceAtLeast(0)

    companion object {
        val None = BudgetChange(0, 0, 0, 0)

        /**
         * [entries]: 이번 달 기록, [accounts]: 자산 (어느 통장이 저축·투자인지 보려고).
         * 저축 통장 자산이 없어도 "저축 넣기"(적금 자동이체 알림 등)는 저축으로 센다.
         */
        fun of(cycle: CycleEntity, entries: List<EntryEntity>, accounts: List<AccountEntity>): BudgetChange {
            val grows = accounts.filter { enumOr(it.type, AccountType.ETC).grows }.map { it.id }.toSet()
            fun grow(id: Long?) = id != null && id in grows
            var into = 0L
            var out = 0L
            entries.filter { it.type == EntryType.TRANSFER.name && !it.owe && it.settleFor == null }.forEach { e ->
                val fromGrow = grow(e.accountId)
                val toGrow = grow(e.toAccountId) || (e.toAccountId == null && e.label == SAVE_IN)
                val outOfGrow = fromGrow || (e.accountId == null && e.label == SAVE_OUT)
                when {
                    toGrow && !fromGrow -> into += e.amount
                    outOfGrow && !grow(e.toAccountId) -> out += e.amount
                }
            }
            return BudgetChange(cycle.budgetAdjust, into, out, cycle.savings)
        }

        const val SAVE_IN = "저축 넣기"
        const val SAVE_OUT = "저축 찾기"
    }
}
