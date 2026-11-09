package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toEntry
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.Stats
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

/**
 * 0.29.1: 홈 숫자끼리 맞는지 — 오늘 쓴 돈이 "오늘"(오늘 쓴 돈)·"이번 주"·"이번 달"·"달력 오늘 칸"에 같은 금액으로 들어간다.
 * (예전엔 편의점 같은 작은 지출이 분류만 보고 "큰 지출"로 빠져 오늘 쓴 돈이 0원으로 보였다)
 */
@RunWith(RobolectricTestRunner::class)
class HomeConsistencyTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val today = LocalDate.of(2026, 11, 9)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(today, java.time.LocalTime.of(h, m))
    private var bank = 0L
    private var card = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_200_000, 20, listOf(FixedExpense("월세", 600_000), FixedExpense("통신비", 55_000)), 300_000, DayOfWeek.SUNDAY, LocalDate.of(2026, 10, 20))
        repo.saveRule(AppRule("bank.app", "신한은행"))
        repo.saveRule(AppRule("card.app", "신한카드"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "bank.app"))
        card = repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(): HomeState = repo.home(flowOf(at(23))).first()!!
    private fun calendarToday(h: HomeState) = Stats.daily(h.entries.map { it.toEntry() }, livingOnly = true)[today] ?: 0L

    private suspend fun check(what: String, before: HomeState, exceptionToday: Long = 0) {
        val h = home()
        val today = h.day.spentToday
        assertEquals("$what: 달력 오늘 칸 = 오늘 쓴 돈 + 예외", calendarToday(h), today + exceptionToday)
        assertEquals("$what: 이번 주 쓴 돈이 오늘 쓴 만큼 늘었다", before.currentWeek!!.spent + today, h.currentWeek!!.spent)
        assertEquals("$what: 이번 달 생활비가 달력 오늘 칸만큼 늘었다", before.monthLiving + calendarToday(h), h.monthLiving)
    }

    @Test
    fun `오늘 쓴 돈은 종류와 상관없이 오늘·이번 주·이번 달·달력에 같이 들어간다`() = runTest {
        val start = home()
        assertEquals(0L, start.day.spentToday)
        // 편의점 (생활·마트, 그날 쓰는 분류가 아님) — 오늘 몫보다 작으니 오늘 쓴 돈
        repo.handleNotification(RawNotification("card.app", "신한카드(1234)승인", "홍*동 18,190원(일시불)11/09 10:00 GS25", at(10)))
        assertEquals(18_190L, home().day.spentToday)
        assertEquals(18_190L, home().day.spentDaily)
        check("편의점", start)
        // 점심 (식비)
        repo.addManual(today, 9_000, "점심", EntryType.SPEND, Category.FOOD, accountId = bank)
        check("점심", start)
        assertEquals(27_190L, home().day.spentToday)
        // 노트북 (오늘 몫보다 큰 쇼핑) — 오늘 쓴 돈에는 보이고, 오늘 몫이 아니라 이번 주에 나눈다
        val base = home().day.allowance
        assertTrue("노트북이 오늘 몫보다 커야 한다", 1_500_000L > base)
        repo.addManual(today, 1_500_000, "노트북", EntryType.SPEND, Category.SHOPPING, accountId = card)
        val big = home().day
        assertEquals(1_527_190L, big.spentToday)
        assertEquals(1_500_000L, big.spentBig)
        check("큰 지출", start)
        // 부분 환불 (식비)
        repo.addManual(today, 3_000, "점심 일부 환불", EntryType.REFUND, Category.FOOD, accountId = bank)
        assertEquals(1_524_190L, home().day.spentToday)
        check("환불", start)
        // 예외 지출 — 주 예산 밖이지만 이번 달 생활비·달력에는 들어간다
        repo.addManual(today, 50_000, "병원", EntryType.EXCEPTION, Category.HEALTH, accountId = bank)
        check("예외", start, exceptionToday = 50_000)
        // 고정 지출 — 생활비 밖이라 어디에도 안 들어간다
        repo.addManual(today, 55_000, "통신비", EntryType.FIXED, Category.BILLS, accountId = card)
        check("고정", start, exceptionToday = 50_000)
        assertEquals(1_574_190L, calendarToday(home()))
    }
}
