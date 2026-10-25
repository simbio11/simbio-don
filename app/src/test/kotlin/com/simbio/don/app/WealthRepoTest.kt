package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.SalarySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.13: 모으는 통장(저축·투자)·월급날 자산 점검, 월급 안내의 지난달 월급, 첫 설정에서 고른 알림 앱 */
@RunWith(RobolectricTestRunner::class)
class WealthRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository

    // 2026-10-23(금) 시작 (25일이 일요일이라 23일이 월급날), 다음 월급날 11/25
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(month: Int, day: Int, h: Int = 12) = LocalDateTime.of(2026, month, day, h, 0)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(
            3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 500_000, DayOfWeek.MONDAY, today,
            accounts = listOf(
                DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_000_000),
                DonRepository.AccountDraft("적금", AccountType.SAVINGS, 5_000_000),
                DonRepository.AccountDraft("주식", AccountType.INVEST, 3_000_000),
            ),
            apps = listOf(com.simbio.don.notify.AppRule("viva.republica.toss", "토스"), com.simbio.don.notify.AppRule("com.example.mybank", "내 은행")),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(now: LocalDateTime) = repo.home(flowOf(now)).first()!!

    @Test
    fun `처음 넣은 모으는 돈은 원금 그대로고 이번 달은 점검하지 않아도 된다`() = runTest {
        val h = home(at(10, 24))
        assertEquals(2, h.wealth.pots.size)
        assertEquals(8_000_000L, h.wealth.total)
        assertEquals(0L, h.wealth.gain)
        assertFalse(h.assetCheckDue)
    }

    @Test
    fun `월급날 자산 점검으로 넣은 돈은 이체로, 불어난 돈은 따로 본다`() = runTest {
        repo.dailyUpkeep(at(11, 25))
        val due = home(at(11, 25))
        assertTrue(due.assetCheckDue)
        // 새 달 안내에 지난달 월급
        assertEquals(SalarySource.ESTIMATED, due.salaryNotice!!.source)
        assertEquals(3_300_000L, due.salaryNotice!!.previous)

        val ids = due.accounts.associate { it.account.name to it.account.id }
        repo.checkAssets(
            listOf(
                DonRepository.AssetCheckInput(ids.getValue("적금"), 300_000, 5_320_000),
                DonRepository.AssetCheckInput(ids.getValue("주식"), 200_000, 3_500_000),
            ),
            fromAccountId = ids.getValue("생활비 통장"),
            today = d(11, 25),
        )
        val h = home(at(11, 25))
        assertFalse(h.assetCheckDue)
        assertEquals(8_820_000L, h.wealth.total)
        assertEquals(8_500_000L, h.wealth.principal)
        assertEquals(320_000L, h.wealth.gain)
        assertEquals(500_000L, h.accounts.single { it.account.name == "생활비 통장" }.balance)
        // 이체는 생활비가 아니다
        assertEquals(0L, h.currentWeek!!.spent)
        assertEquals(2, h.entries.count { it.type == EntryType.TRANSFER.name })
        // 달마다 금액: 10월(처음) · 11월
        assertEquals(listOf(8_000_000L, 8_820_000L), h.wealth.history.map { it.value })
        assertEquals(listOf(8_000_000L, 8_500_000L), h.wealth.history.map { it.principal })

        // 점검 뒤 넣은 돈은 그 금액에 더해진다
        repo.addManual(d(11, 27), 100_000, "적금 추가", EntryType.TRANSFER, accountId = ids.getValue("생활비 통장"), toAccountId = ids.getValue("적금"))
        assertEquals(8_920_000L, home(at(11, 27)).wealth.total)
    }

    @Test
    fun `예금·적금은 목표에 저절로 안 들어가고, 목표에 이은 통장은 저축에서 빠진다`() = runTest {
        repo.addGoal("여행", 10_000_000, "FLIGHT", today)
        val h = home(at(10, 24))
        assertEquals(0L, h.goals.single().filled)
        assertEquals(8_000_000L, h.wealth.total)
        assertEquals(8_000_000L, h.split.savings)
        assertTrue(h.split.savingsFromAccounts)

        // 적금 통장을 여행 목표에 이으면 그 잔액이 목표의 돈, 저축 합계에서는 빠진다
        val goal = h.goals.single().goal
        val deposit = h.accounts.single { it.account.name == "적금" }.account
        repo.linkGoal(goal.id, deposit.id)
        val linked = home(at(10, 24))
        assertEquals(5_000_000L, linked.goals.single().filled)
        assertTrue(linked.goals.single().linked)
        assertEquals(3_000_000L, linked.wealth.total)
        assertEquals(3_000_000L, linked.split.savings)
        assertEquals(5_000_000L, linked.split.goals)

        // 백업·자산 지우기에도 점검과 목표 연결이 따라간다
        val stock = linked.accounts.single { it.account.name == "주식" }.account
        repo.checkAssets(listOf(DonRepository.AssetCheckInput(stock.id, 0, 3_600_000)), null, d(10, 24))
        val json = repo.exportBackup(at(10, 24))
        repo.deleteAccount(stock)
        assertEquals(0L, home(at(10, 24)).wealth.total)
        assertTrue(db.dao().assetChecksNow().none { it.accountId == stock.id })
        repo.linkGoal(goal.id, null)
        repo.restoreBackup(json)
        val back = home(at(10, 24))
        assertEquals(3_600_000L, back.wealth.total)
        assertEquals(deposit.id, back.goals.single().goal.accountId)
    }

    @Test
    fun `첫 설정에서 고른 앱만 켜고 고르지 않은 기본 앱은 꺼 둔다`() = runTest {
        val rules = db.dao().appRulesNow().associateBy { it.packageName }
        assertTrue(rules.getValue("viva.republica.toss").enabled)
        assertTrue(rules.getValue("com.example.mybank").enabled)
        assertFalse(rules.getValue("com.kakaopay.app").enabled)
    }
    /** 0.15: 자산 고치기의 "지금 잔액"은 처음 잔액이 아니라 지금 잔액 — 기록은 그대로 두고 잔액만 맞춘다 */
    @Test
    fun `지금 잔액을 고치면 기록은 그대로 두고 잔액만 맞춘다`() = runTest {
        val bank = db.dao().accountsNow().single { it.name == "생활비 통장" }
        repo.addManual(d(10, 23), 200_000, "장보기", EntryType.SPEND, com.simbio.don.budget.Category.LIVING, accountId = bank.id)
        assertEquals(800_000L, repo.accountBalances.first().single { it.account.id == bank.id }.balance)

        repo.saveAccount(bank, balance = 1_250_000)
        val fixed = repo.accountBalances.first().single { it.account.id == bank.id }
        assertEquals(1_250_000L, fixed.balance)
        assertEquals(1_450_000L, fixed.account.opening) // 처음 잔액이 옮겨짐
        assertEquals(1, db.dao().allEntriesNow().count { it.label == "장보기" })

        // 점검한 적 있는 모으는 통장은 오늘 점검으로: 넣은 돈은 그대로, 불어난 돈이 생긴다
        val stock = db.dao().accountsNow().single { it.name == "주식" }
        repo.saveAccount(stock, balance = 3_400_000)
        val s = repo.accountBalances.first().single { it.account.id == stock.id }
        assertEquals(3_400_000L, s.balance)
        assertEquals(3_000_000L, s.principal)
        assertEquals(400_000L, s.gain)

        // 순자산 = 쓰는 돈 + 모으는 돈 − 부채
        val all = repo.accountBalances.first()
        assertEquals(1_250_000L, com.simbio.don.app.data.AccountBalances.spending(all))
        assertEquals(8_400_000L, com.simbio.don.app.data.AccountBalances.growing(all))
        assertEquals(9_650_000L, com.simbio.don.app.data.AccountBalances.net(all))
    }
}
