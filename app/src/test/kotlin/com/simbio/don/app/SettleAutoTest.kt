package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.SplitHint
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.31: 엔빵 정산 — 끝전이 달라도 알아보고, 잔액을 맞추기 전에 이미 받은 돈을 정리하면 생활비 차이가 안 남는다 */
@RunWith(RobolectricTestRunner::class)
class SettleAutoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 12)
    private fun at(h: Int) = LocalDateTime.of(2026, 11, 12, h, 0)
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_000_000, 25, listOf(FixedExpense("월세", 600_000)), 200_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
        repo.saveRule(AppRule("bank.app", "신한은행"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "bank.app"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balance() = AccountBalances.compute(db.dao().accountsNow(), db.dao().allEntriesNow(), db.dao().assetChecksNow(), d).single { it.account.id == bank }.balance

    @Test
    fun `한 사람 몫과 끝전이 달라도 정산 같은 돈으로 묻고, 맞아요 하면 수입이 아니라 정산`() = runTest {
        // 80,000원 4명 → 한 사람 20,000원
        repo.addManual(d.minusDays(1), 80_000, "고깃집", EntryType.SPEND, Category.FOOD, accountId = bank, share = 20_000, people = 4)
        repo.handleNotification(RawNotification("bank.app", "[신한은행]", "11/12 12:00 입금 20,500원 김철수 잔액 940,500원", at(12)))
        val book = repo.splitBookNow(today = d)
        val hint = book.hints.single() as SplitHint.Received
        assertEquals(20_500L, hint.income.amount)
        repo.settleWith(hint.target.entry, hint.income)
        val after = repo.splitBookNow(today = d)
        assertEquals(60_000L - 20_500, after.toReceive)
        assertEquals(0L, repo.home(flowOf(at(20))).first()!!.monthIncome)
    }

    @Test
    fun `이미 받아 통장에 있는 받을 돈은 받았어요로 정리 — 잔액은 그대로, 받을 돈만 0`() = runTest {
        repo.addManual(d.minusDays(1), 60_000, "피자", EntryType.SPEND, Category.DELIVERY, accountId = bank, share = 20_000, people = 3)
        val before = balance()
        val r = repo.splitBookNow(today = d).receivables.single()
        repo.settleDirect(r.entry, r.left, null, d)
        assertEquals(0L, repo.splitBookNow(today = d).toReceive)
        assertEquals(before, balance())
        assertTrue(repo.splitBookNow(today = d).hints.isEmpty())
    }
}
