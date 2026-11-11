package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
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

/**
 * 0.30: 한 달 살아 보기 — 월급날부터 다음 월급날까지 고정 지출·적금·할부·현금·카드값·월급이 섞여도
 * 통장 잔액은 은행이 알려 준 잔액과, 생활비는 생활비대로 맞는지.
 */
@RunWith(RobolectricTestRunner::class)
class MonthLifeTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val bankApp = "bank.app"
    private val cardApp = "com.shcard.smartpay"
    private var bank = 0L
    private var card = 0L
    private var saving = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_200_000, 25, listOf(FixedExpense("월세", 600_000, 1), FixedExpense("통신비", 55_000, 15)), 300_000, DayOfWeek.SUNDAY, LocalDate.of(2026, 10, 25))
        repo.saveRule(AppRule(bankApp, "신한은행"))
        repo.saveRule(AppRule(cardApp, "신한 SOL페이"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 2_000_000, sortOrder = 0, packageName = bankApp))
        card = repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = cardApp))
        saving = repo.saveAccount(AccountEntity(name = "적금", type = AccountType.SAVINGS.name, opening = 0, sortOrder = 2))
    }

    @After
    fun tearDown() = db.close()

    private fun at(m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min)
    private suspend fun entries() = db.dao().allEntriesNow()
    private var today = LocalDate.of(2026, 10, 25)
    /** 그날까지 적힌 것으로 본 잔액 (앞으로 적힐 할부 남은 달은 빼고) */
    private suspend fun balance(id: Long) = AccountBalances.compute(db.dao().accountsNow(), entries(), db.dao().assetChecksNow(), today).single { it.account.id == id }.balance
    private suspend fun home(t: LocalDateTime): HomeState = repo.home(flowOf(t)).first()!!
    private suspend fun gap(t: LocalDateTime): com.simbio.don.app.data.LivingGap {
        val h = home(t)
        return com.simbio.don.app.data.LivingGap.of(h.accounts, repo.splitBookNow(today = t.toLocalDate()), 0, h.plan.disposable - h.monthLiving, h.entries, h.today)!!
    }

    private suspend fun bankSays(t: LocalDateTime, text: String) {
        today = t.toLocalDate()
        repo.dailyUpkeep(t)
        repo.handleNotification(RawNotification(bankApp, "[신한은행]", text, t))
    }
    private suspend fun cardSays(t: LocalDateTime, text: String) {
        today = t.toLocalDate()
        repo.dailyUpkeep(t)
        repo.handleNotification(RawNotification(cardApp, "신한카드(1234)승인", text, t))
    }

    @Test
    fun `월급날부터 다음 월급날까지`() = runTest {
        cardSays(at(10, 26), "홍*동 30,000원(일시불)10/26 12:00 김밥천국 누적 30,000원")
        // 11/1 월세: 정한 날 아침에 자동으로 적히고, 은행 출금 알림이 와도 두 번 빠지지 않는다
        repo.dailyUpkeep(at(11, 1, 8))
        assertEquals(1, entries().count { it.label == "월세" })
        bankSays(at(11, 1, 10), "11/01 10:00 출금 600,000원 월세 잔액 1,400,000원")
        assertEquals("월세는 한 번만", 1, entries().count { it.amount == 600_000L })
        assertEquals(1_400_000L, balance(bank))
        // 11/2 적금 자동이체: 생활비가 아니라 적금으로 옮긴 돈
        bankSays(at(11, 2, 9), "11/02 09:00 자동이체 출금 300,000원 적금 잔액 1,100,000원")
        assertEquals(1_100_000L, balance(bank))
        val livingAfterSaving = home(at(11, 2, 20)).monthLiving
        assertEquals("적금은 생활비가 아니다", 30_000L, livingAfterSaving)
        // 11/3 노트북 3개월 할부 (카드): 이번 달엔 첫 달 몫만
        repo.addInstallment(LocalDate.of(2026, 11, 3), 900_000, 3, "노트북", Category.SHOPPING, accountId = card)
        assertEquals(330_000L, home(at(11, 3, 20)).monthLiving)
        // 11/5 ATM 현금: 생활비 아님
        bankSays(at(11, 5, 18), "11/05 18:00 ATM 현금출금 100,000원 잔액 1,000,000원")
        assertEquals(1_000_000L, balance(bank))
        assertEquals(330_000L, home(at(11, 5, 20)).monthLiving)
        // 11/10 카드값 (지난달 쓴 김밥 + 이번 할부 첫 달 = 330,000)
        bankSays(at(11, 10, 9), "11/10 09:00 카드대금 출금 330,000원 잔액 670,000원")
        assertEquals(670_000L, balance(bank))
        assertEquals(0L, balance(card))
        assertEquals("카드값은 생활비에 또 안 들어간다", 330_000L, home(at(11, 10, 20)).monthLiving)
        // 홈의 카드 잔액도 할부 남은 달은 아직 빼지 않는다 (카드값 다 냈으면 0)
        assertEquals(0L, home(at(11, 10, 20)).accounts.single { it.account.id == card }.balance)
        // 11/15 통신비: 카드 자동결제로 와도 고정 지출로, 생활비 밖
        repo.dailyUpkeep(at(11, 15, 8))
        cardSays(at(11, 15, 9), "홍*동 55,000원(일시불)11/15 09:00 SKT통신요금 누적 55,000원")
        assertEquals("통신비는 한 번만", 1, entries().count { it.amount == 55_000L && it.type != EntryType.REFUND.name })
        assertEquals(330_000L, home(at(11, 15, 20)).monthLiving)
        assertTrue(db.dao().reviewItemsNow().isEmpty())

        // 11/25 월급: 새 달이 시작되고 생활비는 새로 센다
        bankSays(at(11, 25, 9), "11/25 09:00 입금 3,200,000원 (주)회사 잔액 3,870,000원")
        assertEquals(3_870_000L, balance(bank))
        val next = home(at(11, 25, 20))
        assertEquals(LocalDate.of(2026, 11, 25), next.cycle.start)
        // 새 달 생활비에는 이번 달에 나갈 할부 둘째 몫(12/3)만 미리 들어 있다
        assertEquals(300_000L, next.monthLiving)
        assertTrue(next.entries.none { it.label == "김밥천국" })
        // 남은 생활비와 통장 기준 쓸 수 있는 돈의 차이는 할부를 새로 해도 그대로 (둘 다 같은 만큼 준다)
        val before = gap(at(11, 26, 12))
        repo.addInstallment(LocalDate.of(2026, 11, 26), 400_000, 2, "의자", Category.LIVING, accountId = card)
        today = LocalDate.of(2026, 11, 26)
        val after = gap(at(11, 26, 12))
        assertEquals(before.rest, after.rest)
        assertEquals(before.remaining - 200_000, after.remaining)
        // 노트북 둘째 몫 + 의자 첫 몫 (의자 둘째 몫 12/26은 다음 월급날 뒤라 다음 달)
        assertEquals(500_000L, home(at(12, 4, 20)).monthLiving)
    }
}
