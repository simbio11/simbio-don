package com.simbio.don.budget

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaydayTest {
    @Test
    fun `급여라는 말이 있거나 지난 월급과 비슷한 금액이면 월급 입금`() {
        assertTrue(Payday.isSalaryDeposit("OO은행 급여 입금", 2_100_000, 0))
        assertTrue(Payday.isSalaryDeposit("홍길동 입금", 3_200_000, 3_300_000))
        assertFalse(Payday.isSalaryDeposit("홍길동 입금", 1_000_000, 3_300_000))
        assertFalse(Payday.isSalaryDeposit("급여 입금", 50_000, 3_300_000)) // 너무 적음
        assertFalse(Payday.isSalaryDeposit("입금", 3_300_000, 0)) // 처음엔 말로만
    }

    @Test
    fun `월급날이 되거나 며칠 일찍 월급이 들어오면 새 달`() {
        val expected = LocalDate.of(2026, 11, 25)
        assertNull(Payday.nextStart(expected, LocalDate.of(2026, 11, 24), null))
        assertEquals(expected, Payday.nextStart(expected, expected, null))
        assertEquals(LocalDate.of(2026, 11, 23), Payday.nextStart(expected, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 23)))
        // 너무 일찍 들어온 큰 돈은 월급으로 보지 않는다
        assertNull(Payday.nextStart(expected, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 18)))
        // 늦게 들어오면 새 달은 예상 월급날부터
        assertEquals(expected, Payday.nextStart(expected, LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26)))
    }

    @Test
    fun `고정 지출이 나가는 날은 주기 안의 그 날짜, 없으면 말일`() {
        val c = PayCycle.cycleFor(YearMonth.of(2026, 10), 25) // 10/23 ~ 11/24
        assertEquals(LocalDate.of(2026, 11, 1), Payday.dueDate(c, 1))
        assertEquals(LocalDate.of(2026, 10, 25), Payday.dueDate(c, 25))
        assertEquals(LocalDate.of(2026, 10, 31), Payday.dueDate(c, 31))
        val feb = PayCycle.cycleFor(YearMonth.of(2027, 1), 25) // 1/25 ~ 2/24
        assertEquals(LocalDate.of(2027, 1, 30), Payday.dueDate(feb, 30))
        // 한 달 중간에 시작해 이미 지난 날
        assertNull(Payday.dueDate(c.copy(start = LocalDate.of(2026, 11, 10)), 1))
    }

    @Test
    fun `고정 지출 짝은 이름이 있거나, 금액이 비슷하고 나가는 날 근처`() {
        val c = PayCycle.cycleFor(YearMonth.of(2026, 10), 25)
        val rent = FixedExpense("월세", 600_000, 1)
        assertTrue(Payday.matchesFixed("월세 이체 600,000원", 600_000, LocalDate.of(2026, 10, 25), rent, c))
        assertTrue(Payday.matchesFixed("출금 600,000원", 600_000, LocalDate.of(2026, 11, 2), rent, c))
        assertFalse(Payday.matchesFixed("출금 600,000원", 600_000, LocalDate.of(2026, 11, 10), rent, c))
        assertFalse(Payday.matchesFixed("출금 600,000원", 600_000, LocalDate.of(2026, 11, 2), rent.copy(day = null), c))
    }
}
