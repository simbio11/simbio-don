package com.simbio.don.budget

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class PayCycleTest {

    @Test
    fun `월급날이 주말이면 전 영업일로 당겨진다`() {
        // 2026-10-25는 일요일
        assertEquals(LocalDate.of(2026, 10, 23), PayCycle.expectedPayday(YearMonth.of(2026, 10), 25))
        assertEquals(LocalDate.of(2026, 11, 25), PayCycle.expectedPayday(YearMonth.of(2026, 11), 25))
    }

    @Test
    fun `공휴일도 피한다`() {
        val holidays = setOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 24))
        assertEquals(LocalDate.of(2026, 11, 23), PayCycle.expectedPayday(YearMonth.of(2026, 11), 25, holidays))
    }

    @Test
    fun `말일보다 큰 월급날은 그 달 말일로 본다`() {
        // 2026-02-28은 토요일
        assertEquals(LocalDate.of(2026, 2, 27), PayCycle.expectedPayday(YearMonth.of(2026, 2), 31))
    }

    @Test
    fun `한 달은 월급날부터 다음 월급날 전날까지`() {
        val cycle = PayCycle.cycleFor(YearMonth.of(2026, 10), 25)
        assertEquals(LocalDate.of(2026, 10, 23), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 24), cycle.end)
        assertEquals(33, cycle.days)
    }

    @Test
    fun `실제 입금일로 시작을 덮어쓸 수 있다`() {
        val cycle = PayCycle.cycleFor(YearMonth.of(2026, 10), 25, actualStart = LocalDate.of(2026, 10, 22))
        assertEquals(LocalDate.of(2026, 10, 22), cycle.start)
        assertEquals(LocalDate.of(2026, 11, 24), cycle.end)
    }

    @Test
    fun `날짜가 속한 주기를 찾는다`() {
        assertEquals(YearMonth.of(2026, 10), PayCycle.containing(LocalDate.of(2026, 10, 23), 25).nominal)
        assertEquals(YearMonth.of(2026, 9), PayCycle.containing(LocalDate.of(2026, 10, 22), 25).nominal)
        assertEquals(YearMonth.of(2026, 10), PayCycle.containing(LocalDate.of(2026, 11, 24), 25).nominal)
    }
}
