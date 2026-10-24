package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.14: 내 계좌끼리 송금은 지출·수입이 아니라 이체 한 건 */
@RunWith(RobolectricTestRunner::class)
class SelfTransferRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 26, h, m)

    private val kb = "com.kbstar.kbbankstar"
    private val toss = "viva.republica.toss"
    private val card = "com.hyundaicard.appcard"

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(
            3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, today,
            accounts = listOf(
                DonRepository.AccountDraft("KB 통장", AccountType.BANK, 1_000_000, kb),
                DonRepository.AccountDraft("토스뱅크", AccountType.BANK, 0, toss),
                DonRepository.AccountDraft("현대카드", AccountType.CARD, 0, card),
            ),
            apps = listOf(AppRule(kb, "KB스타뱅킹"), AppRule(toss, "토스"), AppRule(card, "현대카드")),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow().filter { it.source == "NOTIFICATION" }
    private suspend fun balance(name: String) = repo.accountBalances.first().single { it.account.name == name }.balance

    @Test
    fun `송금으로 나가고 곧 같은 금액이 들어오면 이체 한 건`() = runTest {
        repo.handleNotification(RawNotification(kb, "KB국민", "[KB] 출금 300,000원 이체 토스뱅크", at(9, 0)))
        repo.handleNotification(RawNotification(toss, "입금", "300,000원 입금되었어요", at(9, 2)))
        // 같은 알림이 다시 와도 한 번만
        repo.handleNotification(RawNotification(toss, "입금", "300,000원 입금되었어요", at(9, 2)))
        val e = entries().single()
        assertEquals(EntryType.TRANSFER.name, e.type)
        assertEquals(DonRepository.SELF_TRANSFER_LABEL, e.label)
        assertEquals(700_000L, balance("KB 통장"))
        assertEquals(300_000L, balance("토스뱅크"))
        // 생활비·수입이 아니다
        val h = repo.home(flowOf(at(12))).first()!!
        assertEquals(0L, h.currentWeek!!.spent)
        assertEquals(0L, h.monthIncome)
    }

    @Test
    fun `들어온 게 먼저 와도 합치고, 카드 결제는 송금이 아니라 그대로`() = runTest {
        repo.handleNotification(RawNotification(toss, "입금", "50,000원 입금되었어요", at(10, 0)))
        repo.handleNotification(RawNotification(kb, "KB국민", "출금 50,000원 이체", at(10, 1)))
        assertEquals(listOf(EntryType.TRANSFER.name), entries().map { it.type })

        repo.handleNotification(RawNotification(card, "현대카드", "승인 80,000원 백화점", at(11, 0)))
        repo.handleNotification(RawNotification(toss, "입금", "80,000원 입금되었어요", at(11, 1)))
        assertEquals(setOf(EntryType.SPEND.name, EntryType.INCOME.name), entries().filter { it.amount == 80_000L }.map { it.type }.toSet())

        // 15분 넘게 떨어지면 따로
        repo.handleNotification(RawNotification(kb, "KB국민", "출금 20,000원 이체", at(13, 0)))
        repo.handleNotification(RawNotification(toss, "입금", "20,000원 입금되었어요", at(13, 30)))
        assertEquals(setOf(EntryType.SPEND.name, EntryType.INCOME.name), entries().filter { it.amount == 20_000L }.map { it.type }.toSet())
    }

    @Test
    fun `내 이름이나 내 계좌라는 말이 보이면 한쪽만 와도 이체, 다른 쪽이 오면 통장을 채운다`() = runTest {
        repo.saveOwnerName("홍길동")
        repo.handleNotification(RawNotification(kb, "KB국민", "출금 100,000원 홍길동 이체", at(14, 0)))
        val t = entries().single()
        assertEquals(EntryType.TRANSFER.name, t.type)
        assertNull(t.toAccountId)
        repo.handleNotification(RawNotification(toss, "입금", "입금 100,000원 홍길동", at(14, 1)))
        val done = entries().single()
        assertEquals(db.dao().accountsNow().single { it.name == "토스뱅크" }.id, done.toAccountId)

        repo.handleNotification(RawNotification(toss, "토스", "내 계좌로 30,000원 출금 이체했어요", at(15, 0)))
        assertEquals(EntryType.TRANSFER.name, entries().single { it.amount == 30_000L }.type)
    }
}
