package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 0.31: 생활비 통장에서 저축·내 다른 통장으로 옮긴 돈 — 큰 지출로 세지 않고, 묻고, 저축이면 생활비를 다시 나눈다.
 */
@RunWith(RobolectricTestRunner::class)
class MovesTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 12)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 12, h, m)
    private var bank = 0L
    private var other = 0L
    private var saving = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        // 계획한 저축 20만 원
        repo.completeOnboarding(3_000_000, 25, listOf(FixedExpense("월세", 600_000)), 200_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
        repo.saveRule(AppRule("bank.app", "신한은행"))
        repo.saveRule(AppRule("other.app", "카카오뱅크"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_500_000, sortOrder = 0, packageName = "bank.app"))
        other = repo.saveAccount(AccountEntity(name = "카카오뱅크", type = AccountType.BANK.name, opening = 0, sortOrder = 1, packageName = "other.app"))
        saving = repo.saveAccount(AccountEntity(name = "적금", type = AccountType.SAVINGS.name, opening = 0, sortOrder = 2), today = LocalDate.of(2026, 10, 25))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(): HomeState = repo.home(flowOf(at(22))).first()!!
    private suspend fun questions() = repo.moveQuestions(d).first()
    private suspend fun balance(id: Long) = AccountBalances.compute(db.dao().accountsNow(), db.dao().allEntriesNow(), db.dao().assetChecksNow(), d).single { it.account.id == id }.balance

    @Test
    fun `내 통장끼리 — 출금과 입금이 짝지어지고, 맞다고 하면 지출이 아니라 이체`() = runTest {
        repo.handleNotification(RawNotification("bank.app", "[신한은행]", "11/12 10:00 이체 출금 100,500원 홍길동 잔액 1,399,500원", at(10)))
        repo.handleNotification(RawNotification("other.app", "입금", "입출금통장(1234) 입금 100,000원 홍길동", at(10, 1)))
        val q = questions().single()
        assertEquals(100_000L, q.into!!.amount)
        assertEquals(500L, q.fee)
        val livingBefore = home().monthLiving
        repo.confirmMove(q.out.id, q.into!!.id, null)
        val moved = db.dao().allEntriesNow().single { it.type == EntryType.TRANSFER.name }
        assertEquals(bank, moved.accountId)
        assertEquals(other, moved.toAccountId)
        assertEquals(100_000L, moved.amount)
        // 들어온 돈은 수입이 아니다 (지워짐), 수수료만 지출
        assertTrue(db.dao().allEntriesNow().none { it.type == EntryType.INCOME.name })
        assertEquals(livingBefore - 100_500 + 500, home().monthLiving)
        assertEquals(1_399_500L, balance(bank))
        assertEquals(100_000L, balance(other))
        assertTrue(questions().isEmpty())
    }

    @Test
    fun `저축으로 옮긴 큰 돈 — 계획한 저축까지는 생활비 그대로, 넘으면 그만큼 생활비를 줄여 다시 나눈다`() = runTest {
        val start = home()
        val plan = start.plan.disposable
        repo.handleNotification(RawNotification("bank.app", "[신한은행]", "11/12 09:00 이체 출금 300,000원 홍길동 잔액 1,200,000원", at(9)))
        // 묻기 전에는 큰 지출로 보인다
        assertEquals(300_000L, home().monthLiving)
        val q = questions().single()
        assertNull(q.into)
        repo.confirmMove(q.out.id, null, saving, savings = true)
        val h = home()
        assertEquals(0L, h.monthLiving)
        // 계획한 저축 20만 원 + 더 한 10만 원 → 생활비 10만 원 줄어듦
        assertEquals(100_000L, h.budgetChange.extraSaved)
        assertEquals(plan - 100_000, h.plan.disposable)
        assertEquals(300_000L, balance(saving))
        // 한 달 남은 생활비 = 생활비 − 쓴 돈
        assertEquals(plan - 100_000, h.results.last().available)
    }

    @Test
    fun `적금 자동이체 알림은 묻지 않고 저축으로 — 계획보다 많으면 생활비를 다시 나눈다`() = runTest {
        val plan = home().plan.disposable
        repo.handleNotification(RawNotification("bank.app", "[신한은행]", "11/12 09:00 자동이체 출금 250,000원 적금 잔액 1,250,000원", at(9)))
        assertTrue(questions().isEmpty())
        val h = home()
        assertEquals(0L, h.monthLiving)
        assertEquals(250_000L, balance(saving))
        assertEquals(50_000L, h.budgetChange.extraSaved)
        assertEquals(plan - 50_000, h.plan.disposable)
    }

    @Test
    fun `지출 맞아요 하면 다시 안 묻는다`() = runTest {
        repo.handleNotification(RawNotification("bank.app", "[신한은행]", "11/12 09:00 이체 출금 80,000원 김철수 잔액 1,420,000원", at(9)))
        val q = questions().single()
        repo.dismissMove(q.out.id, null)
        assertTrue(questions().isEmpty())
        assertEquals(80_000L, home().monthLiving)
    }

    @Test
    fun `생활비 직접 정하기·통장에 맞추기는 이번 주만이 아니라 남은 날에 나뉜다`() = runTest {
        val before = home()
        val week = before.currentWeek!!
        repo.setMonthBudget(before.plan.disposable + 230_000, at(12))
        val after = home()
        assertEquals(before.plan.disposable + 230_000, after.plan.disposable)
        val gained = (after.currentWeek!!.budget + after.currentWeek!!.carryIn) - (week.budget + week.carryIn)
        // 이번 주는 일부만 (날 수만큼), 나머지는 남은 주에
        assertTrue("$gained", gained in 1 until 230_000)
        assertEquals(230_000L, after.results.last().available - before.results.last().available)
    }
}
