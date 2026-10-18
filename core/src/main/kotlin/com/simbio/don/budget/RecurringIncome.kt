package com.simbio.don.budget

import java.time.LocalDate
import kotlin.math.abs

/** 매달 [day]일에 들어오는 수입 (월급 말고: 부수입·용돈·이자 같은 것) */
data class RecurringIncome(val name: String, val amount: Long, val day: Int)

object RecurringIncomes {
    /** 입금 알림이 들어오기로 한 날 앞뒤 이 날짜 안이면 그 반복 수입으로 본다 */
    const val DAY_TOLERANCE = 3L

    /**
     * 입금이 반복 수입 [r]과 맞는지. 이름이 알림에 있거나, 금액이 ±3%이고 들어오기로 한 날 앞뒤 [DAY_TOLERANCE]일 안.
     */
    fun matches(text: String, amount: Long, date: LocalDate, r: RecurringIncome, cycle: PayCycle?): Boolean {
        if (r.name.length >= 2 && r.name in text) return true
        if (r.amount <= 0 || cycle == null) return false
        val close = abs(amount - r.amount) * 10_000 <= r.amount * 300
        return close && Payday.dueDates(cycle, r.day).any { abs(it.toEpochDay() - date.toEpochDay()) <= DAY_TOLERANCE }
    }
}
