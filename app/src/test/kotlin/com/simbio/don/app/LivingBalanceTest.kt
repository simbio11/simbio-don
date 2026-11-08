package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.LivingGap
import com.simbio.don.app.data.Splits
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.MutableStateFlow
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
 * "생활비 통장 잔액 ↔ 남은 생활비": 정상적으로 쓰면(카드·체크·현금·나눠 내기·카드값) 항목을 맞춘 뒤 차이가 늘 같다.
 * 한 달 흐름을 그대로 따라가며 매 단계 [LivingGap.rest]가 처음 값(0)에서 안 변하는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class LivingBalanceTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 10, 23)
    private val day = LocalDate.of(2026, 10, 27)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 27, h, m)
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        // 월급 330만 − 월세 100만 − 저축 30만 = 생활비 200만. 생활비 통장에 정확히 200만 원이 있는 상태로 시작
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("bank.app", "신한은행"))
        repo.saveRule(AppRule("card.app", "신한카드"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 2_000_000, sortOrder = 0, packageName = "bank.app"))
        repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
        repo.saveAccount(AccountEntity(name = "지갑", type = AccountType.CASH.name, opening = 0, sortOrder = 2))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun gap(): LivingGap {
        val h = repo.home(flowOf(at(23))).first()!!
        val book = repo.splitBook(MutableStateFlow(emptySet()), day).first()
        val bals = AccountBalances.compute(db.dao().accountsNow(), db.dao().allEntriesNow(), db.dao().assetChecksNow())
        val unassigned = repo.unassignedEntries.first().filter { it.type != EntryType.INCOME.name && it.type != EntryType.REFUND.name }.sumOf { it.amount }
        return LivingGap.of(bals, book, unassigned, h.plan.disposable - h.monthLiving, h.entries)!!
    }

    private suspend fun step(what: String, remaining: Long? = null) {
        val g = gap()
        assertEquals("$what: 차이가 달라졌어요 ($g)", 0L, g.rest)
        remaining?.let { assertEquals("$what: 남은 생활비", it, g.remaining) }
    }

    @Test
    fun `한 달 쓰는 흐름에서 통장 잔액과 남은 생활비가 항목대로 맞는다`() = runTest {
        step("처음", 2_000_000)
        // 신용카드 3만 원 (통장은 그대로, 카드값 낼 돈)
        repo.handleNotification(RawNotification("card.app", "신한카드 승인", "스타벅스 30,000원 일시불 승인", at(9)))
        step("카드", 1_970_000)
        // 체크카드·통장 직접 1만 원
        repo.addManual(day, 10_000, "점심", EntryType.SPEND, Category.FOOD, accountId = bank)
        step("통장 지출", 1_960_000)
        // 10만 원 내고 5명이 나눔 (내 몫 2만 원)
        repo.addManual(day, 100_000, "삼겹살", EntryType.SPEND, Category.FOOD, accountId = bank, share = Splits.myShare(100_000, 5), people = 5)
        step("나눠 내기", 1_940_000)
        // 친구 2명이 2만 원씩 보냄 (정산)
        repo.autoMatch(day)
        listOf("김민수", "이지은").forEachIndexed { i, who ->
            repo.handleNotification(RawNotification("bank.app", "입금", "$who 20,000원 입금", at(21, i)))
        }
        step("정산 입금", 1_940_000)
        // 카드값 3만 원이 통장에서 나감
        repo.handleNotification(RawNotification("bank.app", "출금", "카드대금 30,000원 출금", at(22)))
        step("카드값", 1_940_000)
        // ATM으로 5만 원 뽑음
        repo.handleNotification(RawNotification("bank.app", "출금", "ATM 출금 50,000원", at(22, 30)))
        step("현금 뽑기", 1_940_000)
        // 현금으로 8천 원 씀
        repo.addManual(day, 8_000, "분식", EntryType.SPEND, Category.FOOD, accountId = db.dao().accountsNow().first { it.name == "지갑" }.id)
        step("현금 지출", 1_932_000)
        // 월세 자동이체는 생활비가 아니라 월급에서 미리 뺀 것: 통장은 줄지만 차이도 그만큼 → 처음에 월세를 뺀 200만 원이라 별도 단계에서 본다
    }

    @Test
    fun `통장 없이 적힌 지출이 있으면 그만큼이 차이로 보이고, 고르면 사라진다`() = runTest {
        repo.addManual(day, 12_000, "분식집", EntryType.SPEND, Category.FOOD)
        val g = gap()
        assertEquals(12_000L, g.unassigned)
        assertEquals(0L, g.rest) // 항목으로 설명된다
        assertTrue(g.hasItems)
        // 통장을 골라 주면 항목이 없어지고 통장 잔액이 줄어 그대로 맞는다
        repo.chooseAccount(repo.accountQuestions.first().single().id, bank)
        val after = gap()
        assertEquals(0L, after.unassigned)
        assertEquals(1_988_000L, after.bank)
        assertEquals(0L, after.rest)
    }

    @Test
    fun `카드로 쓴 돈을 고정 지출로 적으면 생활비에서도, 쓸 수 있는 돈 계산에서도 빠진다`() = runTest {
        val card = db.dao().accountsNow().first { it.type == AccountType.CARD.name }.id
        // 생활비로 카드 3만 원, 고정 지출로 카드 2만 9천 원 (카드값 낼 돈은 합쳐서 5만 9천 원)
        repo.handleNotification(RawNotification("card.app", "신한카드 승인", "스타벅스 30,000원 일시불 승인", at(9)))
        repo.addManual(day, 29_000, "보험료", EntryType.FIXED, Category.BILLS, accountId = card)
        val g = gap()
        assertEquals(59_000L, g.cardDue + g.cardFixed)
        assertEquals(29_000L, g.cardFixed)
        assertEquals(30_000L, g.cardDue)
        // 남은 생활비는 생활비로 쓴 3만 원만 줄었고, 쓸 수 있는 돈도 그 3만 원만 뺐다 → 차이 0
        assertEquals(1_970_000L, g.remaining)
        assertEquals(0L, g.rest)
    }
}
