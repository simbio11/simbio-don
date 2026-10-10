package com.simbio.don.budget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WeekLedgerTest {

    @Test
    fun `남은 잔액이 곧 절약액이다`() {
        val ledger = WeekLedger(budget = 350_000)
            .spend(18_000, "치킨 배달")
            .spend(320_000, "카드 결제", SpendSource.REAL)

        assertEquals(338_000, ledger.spent)
        assertEquals(12_000, ledger.remaining)
        assertEquals(12_000, ledger.saved)
        assertEquals(0, ledger.overspent)
    }

    @Test
    fun `예산을 넘기면 절약액은 0이고 초과액이 잡힌다`() {
        val ledger = WeekLedger(budget = 100_000).spend(103_000, "장보기", SpendSource.REAL)

        assertEquals(0, ledger.saved)
        assertEquals(3_000, ledger.overspent)
    }

    @Test
    fun `보너스는 주 예산의 상한까지만 쌓인다`() {
        val ledger = WeekLedger(budget = 350_000) // 기본 상한 5% = 17,500원
            .addBonus(10_000, BonusKind.INTEREST)
            .addBonus(10_000, BonusKind.MINIGAME)

        assertEquals(17_500, ledger.bonusCap)
        assertEquals(17_500, ledger.bonusTotal)
        assertEquals(listOf(10_000L, 7_500), ledger.bonuses.map { it.amount })
        assertEquals(367_500, ledger.remaining)
        assertSame(ledger, ledger.addBonus(1, BonusKind.INVESTMENT))
    }

    @Test
    fun `보너스 상한은 5퍼센트에서 10퍼센트 사이만 허용한다`() {
        assertEquals(35_000, WeekLedger(350_000, bonusCapBps = 1_000).bonusCap)
        assertFailsWith<IllegalArgumentException> { WeekLedger(350_000, bonusCapBps = 499) }
        assertFailsWith<IllegalArgumentException> { WeekLedger(350_000, bonusCapBps = 1_001) }
    }

    @Test
    fun `0원 이하 지출과 보너스는 거부한다`() {
        val ledger = WeekLedger(budget = 10_000)
        assertFailsWith<IllegalArgumentException> { ledger.spend(0, "x") }
        assertFailsWith<IllegalArgumentException> { ledger.addBonus(0, BonusKind.INTEREST) }
    }

    @Test
    fun `주간 리포트 문구`() {
        val base = WeekLedger(budget = 350_000)
        assertEquals("이번 주 12,000원 아꼈어요", WeeklyReport.from(base.spend(338_000, "x")).headline)
        assertEquals("이번 주 3,000원 초과했어요", WeeklyReport.from(base.spend(353_000, "x")).headline)
        assertEquals("이번 주 예산을 딱 맞게 썼어요", WeeklyReport.from(base.spend(350_000, "x")).headline)
    }

    @Test
    fun `주간 리포트는 모의 지출과 실제 지출을 나눠 보여준다`() {
        val report = WeeklyReport.from(
            WeekLedger(budget = 100_000)
                .spend(5_000, "모의 쇼핑")
                .spend(20_000, "편의점", SpendSource.REAL)
                .addBonus(2_000, BonusKind.INTEREST),
        )

        assertEquals(5_000, report.spentMock)
        assertEquals(20_000, report.spentReal)
        assertEquals(2_000, report.bonus)
        assertEquals(77_000, report.saved)
    }
}
