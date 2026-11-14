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
    fun `아낀 돈은 남은 주에 날 수만큼 나뉘어 늘어난다`() {
        val results = ledger
            .add(Entry(d(10, 24), 480_000, "장보기"))
            .add(Entry(d(11, 3), 100_000, "외식"))
            .results()

        assertEquals(120_000, results[0].carryOut)
        // 2주 시작 때 남은 생활비 1,500,000 × 7일 ÷ 남은 23일
        assertEquals(36_521, results[1].carryIn)
        assertEquals(356_521, results[1].available)
        // 한 달 남은 생활비는 늘 "생활비 − 쓴 돈"
        assertEquals(1_980_000L - 580_000, results.last().available)
    }

    @Test
    fun `아낀 비율과 초과는 기본 주 예산 기준이다`() {
        val results = ledger
            .add(Entry(d(10, 24), 480_000, "장보기")) // 1주: 120,000 아낌 (20%)
            .add(Entry(d(11, 3), 540_000, "많이 씀")) // 2주: 기본 420,000 기준 120,000 초과
            .results()

        assertEquals(2_000, results[0].savedBps)
        assertEquals(120_000, results[1].overspent)
        assertEquals(0, results[1].savedBps)
        assertEquals(-83_479, results[1].carryOut)
        // 더 쓴 만큼 남은 주가 저절로 준다
        assertTrue(results[2].budget + results[2].carryIn < 420_000)
    }

    @Test
    fun `생활비를 바꾸면 이번 주부터 다시 나뉜다 — 이월 계산 없이`() {
        val more = CycleLedger(BudgetEngine.plan(PayCycle.cycleFor(YearMonth.of(2026, 10), 25), salary = 1_980_000, adjust = -230_000))
            .add(Entry(d(10, 24), 480_000, "장보기"))
            .results()
        // 2주 시작 때 남은 생활비 (1,750,000 − 480,000) × 7 ÷ 23
        assertEquals(386_521, more[1].budget + more[1].carryIn)
        assertEquals(1_750_000L - 480_000, more.last().available)
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
    fun `예외 지출은 그 주를 건드리지 않고 다음 주부터 나뉘어 빠진다`() {
        val after = ledger.add(Entry(d(10, 27), 300_000, "병원비", EntryType.EXCEPTION))
        val r = after.results()
        assertEquals(0, r[0].spent)
        assertEquals(600_000, r[0].available)
        // 2주 시작 때 남은 생활비 1,680,000 × 7 ÷ 23
        assertEquals(511_304, after.adjustedWeeks()[1].budget)
        assertEquals(1_680_000, r.last().available)
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
