package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
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

/** 0.28: 처음엔 사용자가 알려 주고, 갈수록 알아서 — 분류·카드 끝자리·앱을 배운다 */
@RunWith(RobolectricTestRunner::class)
class AutoLearnRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val today = LocalDate.now()
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(today, java.time.LocalTime.of(h, m))
    private var bank = 0L
    private var cardA = 0L
    private var cardB = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, today.minusDays(3))
        repo.saveRule(AppRule("bank.app", "신한은행"))
        repo.saveRule(AppRule("card.app", "신한카드"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "bank.app"))
        cardA = repo.saveAccount(AccountEntity(name = "신한카드 주력", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
        cardB = repo.saveAccount(AccountEntity(name = "신한카드 서브", type = AccountType.CARD.name, opening = 0, sortOrder = 2))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entry(label: String) = db.dao().allEntriesNow().first { it.label == label }
    private suspend fun tails(id: Long) = db.dao().accountsNow().first { it.id == id }.tails

    @Test
    fun `카드 끝자리는 처음엔 저절로 배우고, 새 끝자리가 오면 물어보고, 알려 주면 그다음부터 알아서`() = runTest {
        // 첫 결제: 끝자리가 처음이라 이은 카드(주력)에 저절로 붙는다 — 묻지 않는다
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "스타벅스 6,500원 일시불", at(9)))
        assertEquals(cardA, entry("스타벅스").accountId)
        assertFalse(entry("스타벅스").acctGuess)
        assertEquals("c1111", tails(cardA))
        // 같은 끝자리는 계속 주력으로
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "GS25 3,000원 일시불", at(10)))
        assertEquals(cardA, entry("GS25").accountId)
        // 다른 끝자리: 같은 앱의 다른 카드일 수 있어 물어본다
        repo.handleNotification(RawNotification("card.app", "신한카드(2222)승인", "다이소 4,000원 일시불", at(11)))
        assertTrue(entry("다이소").acctGuess)
        assertEquals(listOf("다이소"), repo.accountQuestions.first().map { it.label })
        // 서브 카드라고 알려 주면 끝자리를 기억하고 같은 끝자리 기록도 같이 정한다
        repo.handleNotification(RawNotification("card.app", "신한카드(2222)승인", "올리브영 9,000원 일시불", at(12)))
        repo.chooseAccount(entry("다이소").id, cardB)
        assertEquals("c2222", tails(cardB))
        assertEquals(cardB, entry("올리브영").accountId)
        assertTrue(repo.accountQuestions.first().isEmpty())
        // 다음부터는 묻지 않고 알아서
        repo.handleNotification(RawNotification("card.app", "신한카드(2222)승인", "CU 2,500원 일시불", at(13)))
        assertEquals(cardB, entry("CU").accountId)
        assertFalse(entry("CU").acctGuess)
        assertTrue(repo.accountQuestions.first().isEmpty())
        // 카드마다 쓴 만큼 따로 쌓인다
        val bal = com.simbio.don.app.data.AccountBalances.compute(db.dao().accountsNow(), db.dao().allEntriesNow()).associate { it.account.id to it.balance }
        assertEquals(-9_500L, bal.getValue(cardA))
        assertEquals(-15_500L, bal.getValue(cardB))
    }

    @Test
    fun `모르는 가게 분류는 한 번 알려 주면 같은 가게는 저절로, 자동 기록률이 오른다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "김씨네 8,000원 일시불", at(9)))
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "스타벅스 4,500원 일시불", at(10)))
        var s = repo.autoState().first()
        // 스타벅스는 키워드로 알아서, 김씨네만 묻는다
        assertEquals(listOf("김씨네"), s.toAsk.map { it.entry.label })
        assertTrue(s.toAsk.single().needsCategory)
        assertEquals(50, s.rate.percent)
        // 알려 준다 → 규칙으로 남는다
        repo.confirmCategory(entry("김씨네").id, Category.FOOD)
        assertEquals(Category.FOOD.name, entry("김씨네").category)
        s = repo.autoState().first()
        assertTrue(s.toAsk.isEmpty())
        assertEquals(100, s.rate.percent)
        // 같은 가게의 다음 결제는 알아서 식비로, 묻지 않는다
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "김씨네 12,000원 일시불", at(11)))
        assertEquals(Category.FOOD.name, db.dao().allEntriesNow().last { it.label == "김씨네" }.category)
        assertTrue(repo.autoState().first().toAsk.isEmpty())
        // 잘못 배운 가게는 지우면 다시 묻는다
        repo.forgetLearned(com.simbio.don.budget.CategoryGuesser.key("김씨네"))
        assertEquals(false, repo.learned().containsKey(com.simbio.don.budget.CategoryGuesser.key("김씨네")))
    }

    @Test
    fun `배운 앱과 끝자리를 잊으면 다시 묻는다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "신한카드(1111)승인", "스타벅스 6,500원 일시불", at(9)))
        assertEquals("c1111", tails(cardA))
        repo.forgetTail(cardA, "c1111")
        assertEquals("", tails(cardA))
        repo.unlinkApp(cardA)
        assertEquals(null, db.dao().accountsNow().first { it.id == cardA }.packageName)
    }
}
