package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.CreditCards
import com.simbio.don.notify.Institutions
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
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

/** 0.26: 신용카드로 결제했는데 생활비 통장 잔액이 줄던 문제 */
@RunWith(RobolectricTestRunner::class)
class CardSpendTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 10, 23)
    private val day = LocalDate.of(2026, 10, 27)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 27, h, m)
    private val cardApp = "com.shcard.smartpay"
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("bank.app", "신한은행"))
        repo.saveRule(AppRule(cardApp, "신한카드"))
        // 생활비 통장만 있고 카드 자산은 안 만든 사람
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "bank.app"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balances() =
        AccountBalances.compute(db.dao().accountsNow(), db.dao().allEntriesNow(), db.dao().assetChecksNow()).associate { it.account.name to it.balance }

    @Test
    fun `신용카드 결제는 통장이 아니라 카드에서, 카드값을 낼 때 통장에서 나간다`() = runTest {
        repo.handleNotification(RawNotification(cardApp, "신한카드 승인", "스타벅스 6,500원 일시불 승인", at(9)))
        var b = balances()
        assertEquals(1_000_000L, b.getValue("생활비 통장"))
        assertEquals(-6_500L, b.getValue("신한카드"))
        // 다음 결제도 같은 카드 자산으로 (카드를 또 만들지 않는다)
        repo.handleNotification(RawNotification(cardApp, "신한카드 승인", "GS25 3,000원 승인", at(10)))
        assertEquals(1, db.dao().accountsNow().count { it.type == AccountType.CARD.name })
        // 체크카드는 통장에서 바로
        repo.handleNotification(RawNotification(cardApp, "신한카드 체크승인", "김밥천국 5,000원 체크카드 승인", at(12)))
        // 카드값: 통장 → 카드
        repo.handleNotification(RawNotification("bank.app", "출금", "카드대금 9,500원 출금", at(13)))
        b = balances()
        assertEquals(1_000_000L - 5_000 - 9_500, b.getValue("생활비 통장"))
        assertEquals(0L, b.getValue("신한카드"))
    }

    @Test
    fun `예전에 통장에 잘못 적힌 신용카드 결제는 정리할 때 카드로 옮긴다`() = runTest {
        db.dao().insertEntry(
            EntryEntity(
                epochDay = day.toEpochDay(), amount = 20_000, label = "올리브영", type = EntryType.SPEND.name,
                source = EntrySource.NOTIFICATION.name, packageName = cardApp, category = Category.SHOPPING.name,
                accountId = bank, rawText = "신한카드 승인\n올리브영 20,000원 승인",
            ),
        )
        assertEquals(980_000L, balances().getValue("생활비 통장"))
        repo.tidyUp(day)
        val b = balances()
        assertEquals(1_000_000L, b.getValue("생활비 통장"))
        assertEquals(-20_000L, b.getValue("신한카드"))
    }

    @Test
    fun `카드사 판별과 은행 앱 찾기`() {
        assertEquals("신한카드", CreditCards.company(cardApp, "스타벅스 6,500원"))
        assertEquals("KB국민카드", CreditCards.company("sms.app", "[Web발신] 국민카드 승인 12,000원 홍길동"))
        assertNull(CreditCards.company(cardApp, "체크카드 승인 5,000원"))
        assertNull(CreditCards.company("bank.app", "출금 50,000원 신한카드"))
        assertNull(CreditCards.company("toss.app", "편의점 3,000원 결제"))
        // "신한 SOL"로 은행 앱을 찾다가 카드 앱(신한 SOL페이)을 고르지 않는다
        val shinhan = Institutions.named("신한은행")!!
        assertNull(Institutions.appFor(shinhan, mapOf(cardApp to "신한 SOL페이")))
        assertEquals("my.sol", Institutions.appFor(shinhan, mapOf(cardApp to "신한 SOL페이", "my.sol" to "신한 SOL뱅크")))
    }

    @Test
    fun `알림 앱이 자산과 안 이어졌으면 짐작 표시, 골라 주면 반영되고 앱도 기억한다`() = runTest {
        val samsung = repo.saveAccount(AccountEntity(name = "삼성 체크", type = AccountType.BANK.name, opening = 0, sortOrder = 5))
        repo.saveRule(AppRule("pay.app", "OO페이"))
        repo.handleNotification(RawNotification("pay.app", "결제", "GS25 3,000원 결제", at(9)))
        repo.handleNotification(RawNotification("pay.app", "결제", "스타벅스 4,000원 결제", at(10)))
        // 통장 없이 직접 적은 지출도 같이 묻는다
        repo.addManual(day, 5_000, "분식", EntryType.SPEND, Category.FOOD)
        val q = repo.accountQuestions.first()
        assertEquals(3, q.size)
        assertEquals(2, q.count { it.acctGuess && it.accountId == bank })
        // 하나 골라 주면서 "이 앱은 앞으로 이 통장" → 같은 앱의 다른 짐작도 같이 정해진다
        val first = q.first { it.label == "GS25" }
        repo.chooseAccount(first.id, samsung, rememberApp = true)
        assertEquals(1, repo.accountQuestions.first().size)
        assertEquals("pay.app", db.dao().accountsNow().first { it.id == samsung }.packageName)
        assertEquals(listOf(samsung, samsung), db.dao().allEntriesNow().filter { it.packageName == "pay.app" }.map { it.accountId })
        // 다음 결제는 묻지 않고 그 통장으로
        repo.handleNotification(RawNotification("pay.app", "결제", "CU 2,000원 결제", at(11)))
        val cu = db.dao().allEntriesNow().first { it.label == "CU" }
        assertEquals(samsung, cu.accountId)
        assertTrue(!cu.acctGuess)
        // 통장 없이 적은 것도 골라 주면 잔액에 들어간다
        repo.chooseAccount(repo.accountQuestions.first().single().id, bank)
        assertTrue(repo.accountQuestions.first().isEmpty())
        assertEquals(1_000_000L - 5_000, balances().getValue("생활비 통장"))
        assertEquals(-9_000L, balances().getValue("삼성 체크"))
    }

    @Test
    fun `자산이 하나뿐이면 어느 통장인지 묻지 않는다`() = runTest {
        repo.saveRule(AppRule("pay.app", "OO페이"))
        repo.handleNotification(RawNotification("pay.app", "결제", "GS25 3,000원 결제", at(9)))
        assertTrue(repo.accountQuestions.first().isEmpty())
    }
}
