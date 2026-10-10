package com.simbio.don.budget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CycleLedgerTest {

    // 주 예산 600,000(10일) / 420,000 / 420,000 / 540,000(9일)
    private val plan = BudgetEngine.plan(PayCycle.cycleFor(YearMonth.of(2026, 10), 25), salary = 1_980_000)
    private val ledger = CycleLedger(plan)

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun `남은 돈은 다음 주로 이월된다`() {
        val results = ledger
            .add(Entry(d(10, 24), 480_000, "장보기"))
            .add(Entry(d(11, 3), 100_000, "외식"))
            .results()

        assertEquals(120_000, results[0].carryOut)
        assertEquals(120_000, results[1].carryIn)
        assertEquals(440_000, results[1].available) // 420,000 + 120,000 − 100,000
    }

    @Test
    fun `아낀 비율과 초과는 이월을 뺀 기본 주 예산 기준이다`() {
        val results = ledger
            .add(Entry(d(10, 24), 480_000, "장보기")) // 1주: 120,000 아낌 (20%)
            .add(Entry(d(11, 3), 540_000, "이월분까지 씀")) // 2주: 기본 420,000 기준 120,000 초과
            .results()

        assertEquals(2_000, results[0].savedBps)
        assertEquals(120_000, results[1].overspent)
        assertEquals(0, results[1].savedBps)
        assertEquals(0, results[1].carryOut)
    }

    @Test
    fun `환불은 그 주 지출에서 빠진다`() {
        val r = ledger
            .add(Entry(d(11, 3), 50_000, "옷"))
            .add(Entry(d(11, 4), 50_000, "옷 반품", EntryType.REFUND))
            .results()[1]
        assertEquals(0, r.spent)
        assertEquals(10_000, r.savedBps)
    }

    @Test
    fun `예외 지출은 그 주를 건드리지 않고 남은 주 예산을 다시 나눈다`() {
        val after = ledger.add(Entry(d(10, 27), 300_000, "병원비", EntryType.EXCEPTION))
        val weeks = after.adjustedWeeks()

        assertEquals(600_000, weeks[0].budget)
        assertEquals(listOf(328_696L, 328_695, 422_609), weeks.drop(1).map { it.budget })
        assertEquals(1_680_000, weeks.sumOf { it.budget })
        assertEquals(0, after.results()[0].spent)
    }

    @Test
    fun `마지막 주의 예외 지출은 다시 나눌 곳이 없어 이월액에서 빠진다`() {
        val r = ledger.add(Entry(d(11, 20), 100_000, "경조사비", EntryType.EXCEPTION)).results().last()
        assertEquals(0, r.spent)
        assertEquals(10_000, r.savedBps) // 결산 판정에는 영향 없음
        assertEquals(1_880_000, r.carryOut) // 한 달 가용액에서는 빠짐
    }

    @Test
    fun `예외 지출은 한 달에 2번까지`() {
        val twice = ledger
            .add(Entry(d(10, 27), 10_000, "a", EntryType.EXCEPTION))
            .add(Entry(d(11, 3), 10_000, "b", EntryType.EXCEPTION))
        assertFalse(twice.canAddException)
        assertFailsWith<IllegalArgumentException> { twice.add(Entry(d(11, 4), 10_000, "c", EntryType.EXCEPTION)) }
    }

    @Test
    fun `주기 밖 날짜는 거부한다`() {
        assertFailsWith<IllegalArgumentException> { ledger.add(Entry(d(10, 22), 1_000, "x")) }
    }

    @Test
    fun `지금까지 아낀 돈은 이번 주로 넘어온 이월액이다`() {
        val l = ledger.add(Entry(d(10, 24), 580_000, "x"))
        assertEquals(20_000, l.savedSoFar(d(11, 5)))
        assertEquals(0, l.savedSoFar(d(10, 30)))
    }

    @Test
    fun `결산 문구`() {
        val results = ledger
            .add(Entry(d(10, 24), 480_000, "x"))
            .add(Entry(d(11, 3), 423_000, "x"))
            .add(Entry(d(11, 10), 420_000, "x"))
            .results()
        assertEquals("이번 주 120,000원 아꼈어요 (20%)", results[0].headline())
        assertEquals("이번 주 3,000원 초과했어요", results[1].headline())
        assertEquals("이번 주 예산을 딱 맞게 썼어요", results[2].headline())
    }

    @Test
    fun `결산은 다음 날 정오까지 확인함을 기다린다`() {
        val weekEnd = d(11, 1)
        assertFalse(Settlement.canSettle(weekEnd, LocalDateTime.of(2026, 11, 1, 23, 0), reviewInboxEmpty = true))
        assertTrue(Settlement.canSettle(weekEnd, LocalDateTime.of(2026, 11, 2, 8, 0), reviewInboxEmpty = true))
        assertFalse(Settlement.canSettle(weekEnd, LocalDateTime.of(2026, 11, 2, 8, 0), reviewInboxEmpty = false))
        assertTrue(Settlement.canSettle(weekEnd, LocalDateTime.of(2026, 11, 2, 12, 0), reviewInboxEmpty = false))
    }
}
