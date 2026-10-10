package com.simbio.don.budget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 주간 결산 시점.
 *
 * 주가 끝난 다음 날(결산 요일) 아침부터 결산할 수 있고, 늦게 들어오는 결제 알림을 기다려
 * 정오까지는 확인함을 비워야 결산한다. 정오가 지나면 확인함이 남아 있어도 자동 결산한다.
 */
object Settlement {
    val GRACE_UNTIL: LocalTime = LocalTime.NOON

    fun deadline(weekEnd: LocalDate): LocalDateTime = weekEnd.plusDays(1).atTime(GRACE_UNTIL)

    fun canSettle(weekEnd: LocalDate, now: LocalDateTime, reviewInboxEmpty: Boolean): Boolean =
        now.toLocalDate().isAfter(weekEnd) && (reviewInboxEmpty || mustAutoSettle(weekEnd, now))

    fun mustAutoSettle(weekEnd: LocalDate, now: LocalDateTime): Boolean = !now.isBefore(deadline(weekEnd))
}
