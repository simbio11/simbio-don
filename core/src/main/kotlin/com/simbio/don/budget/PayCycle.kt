package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 월급날 기준 한 달 = 월급날 ~ 다음 월급날 전날.
 *
 * 월급날이 주말·공휴일이면 그 전 영업일에 입금된다고 본다. 공휴일 목록은 앱이 넣어주고,
 * 실제 입금일은 월급날마다 사용자가 확인하므로 [cycleFor]의 `actualStart`로 덮어쓸 수 있다.
 */
data class PayCycle(val nominal: YearMonth, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
    operator fun contains(date: LocalDate) = date in start..end

    companion object {
        fun expectedPayday(month: YearMonth, payDay: Int, holidays: Set<LocalDate> = emptySet()): LocalDate {
            require(payDay in 1..31) { "월급날은 1~31일이어야 합니다: $payDay" }
            var day = month.atDay(minOf(payDay, month.lengthOfMonth()))
            while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY || day in holidays) {
                day = day.minusDays(1)
            }
            return day
        }

        fun cycleFor(
            nominal: YearMonth,
            payDay: Int,
            holidays: Set<LocalDate> = emptySet(),
            actualStart: LocalDate? = null,
        ): PayCycle {
            val start = actualStart ?: expectedPayday(nominal, payDay, holidays)
            val end = expectedPayday(nominal.plusMonths(1), payDay, holidays).minusDays(1)
            require(!end.isBefore(start)) { "주기 끝($end)이 시작($start)보다 앞섭니다" }
            return PayCycle(nominal, start, end)
        }

        /** [date]가 속한 주기. */
        fun containing(date: LocalDate, payDay: Int, holidays: Set<LocalDate> = emptySet()): PayCycle {
            var nominal = YearMonth.from(date).plusMonths(1)
            while (true) {
                val cycle = cycleFor(nominal, payDay, holidays)
                if (date in cycle) return cycle
                nominal = nominal.minusMonths(1)
            }
        }
    }
}
