package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.data.ReviewResolution
import com.simbio.don.app.data.Splits
import com.simbio.don.app.data.toEntry
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.Stats
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.MutableStateFlow
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

/** 0.21: 같은 돈이 두 번 세이거나(지출·수입), 서로 어긋나는 경우들 */
@RunWith(RobolectricTestRunner::class)
class ConsistencyRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 10, 23)
    private val day = LocalDate.of(2026, 10, 27)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)
    private var bank = 0L
    private var card = 0L
    private var cash = 0L
    private var savings = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("wallet.app", "OO뱅크"))
        repo.saveRule(AppRule("card.app", "신한카드"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "wallet.app"))
        card = repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
        cash = repo.saveAccount(AccountEntity(name = "지갑", type = AccountType.CASH.name, opening = 0, sortOrder = 2))
        savings = repo.saveAccount(AccountEntity(name = "청년적금", type = AccountType.SAVINGS.name, opening = 0, sortOrder = 3))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun balance(id: Long) =
        AccountBalances.compute(db.dao().accountsNow().filter { it.id == id }, entries(), db.dao().assetChecksNow()).single().balance
    private suspend fun weekSpent() = repo.home(flowOf(at(27, 23))).first()!!.currentWeek!!.spent
    private suspend fun income() = Stats.income(entries().map { it.toEntry() })
    private suspend fun book() = repo.splitBook(MutableStateFlow(emptySet()), day).first()

    @Test
    fun `10만 원 내고 5명이 엔빵 — 친구들이 보낸 2만 원씩은 수입이 아니라 정산으로, 생활비는 내 몫만`() = runTest {
        repo.addManual(day, 100_000, "삼겹살집", EntryType.SPEND, Category.FOOD, accountId = bank, share = Splits.myShare(100_000, 5), people = 5)
        repo.autoMatch(day)
        listOf("김민수", "이지은", "박서준", "최유나").forEachIndexed { i, who ->
            repo.handleNotification(RawNotification("wallet.app", "입금", "$who 20,000원 입금", at(27, 21, i)))
        }
        assertEquals(0L, income())
        assertEquals(20_000L, weekSpent())
        assertEquals(1_000_000 - 100_000 + 80_000, balance(bank))
        val b = book()
        assertTrue(b.receivables.isEmpty())
        assertEquals(4, b.autoLinks.size)

        // 하나는 정산이 아니었다 → 수입으로, 다시 저절로 묶지 않는다
        val wrong = b.autoLinks.first().link
        repo.unsettle(wrong)
        repo.autoMatch(day)
        assertEquals(20_000L, income())
        assertEquals(EntryType.INCOME.name, db.dao().entryNow(wrong.id)!!.type)
        assertEquals(20_000L, book().toReceive)
        // 맞아요 → 확인 목록에서 빠진다
        repo.confirmAutoLink(book().autoLinks.first().link.id)
        assertEquals(2, book().autoLinks.size)
    }

    @Test
    fun `엔빵을 나중에 적어도 이미 들어온 친구 돈을 정산으로 묶는다`() = runTest {
        repo.handleNotification(RawNotification("wallet.app", "입금", "김민수 30,000원 입금", at(27, 22)))
        assertEquals(30_000L, income())
        repo.addManual(day, 60_000, "치킨", EntryType.SPEND, Category.FOOD, accountId = bank, share = Splits.myShare(60_000, 2), people = 2)
        repo.autoMatch(day)
        assertEquals(0L, income())
        assertTrue(book().receivables.isEmpty())
    }

    @Test
    fun `남이 내서 보낼 돈 — 보낸 송금은 지출로 또 세지 않는다`() = runTest {
        repo.addManual(day, 15_000, "점심 (민수가 냄)", EntryType.SPEND, Category.FOOD, owe = true)
        repo.handleNotification(RawNotification("wallet.app", "출금", "김민수님에게 15,000원 송금", at(27, 13)))
        assertEquals(15_000L, weekSpent())
        assertEquals(1_000_000 - 15_000, balance(bank))
        assertTrue(book().payables.isEmpty())
        assertEquals(1, book().autoLinks.size)
        // 아니에요 → 송금 지출로 돌아가고 보낼 돈은 다시 남는다
        repo.unsettle(book().autoLinks.single().link)
        assertEquals(30_000L, weekSpent())
        assertEquals(1, book().payables.size)
    }

    @Test
    fun `카드값·현금 뽑기·적금은 지출이 아니라 내 돈끼리 옮긴 것`() = runTest {
        repo.handleNotification(RawNotification("card.app", "신한카드 승인", "GS25 50,000원 결제", at(27, 9)))
        assertEquals(50_000L, weekSpent())
        // 통장에서 카드사로 나간 카드값 (두 가지 말투)
        repo.handleNotification(RawNotification("wallet.app", "출금", "출금 50,000원 신한카드", at(27, 10)))
        assertEquals(50_000L, weekSpent())
        assertEquals(0L, balance(card))
        assertEquals(950_000L, balance(bank))
        repo.handleNotification(RawNotification("wallet.app", "출금", "카드대금 30,000원 출금", at(27, 11)))
        assertEquals(50_000L, weekSpent())
        // ATM
        repo.handleNotification(RawNotification("wallet.app", "출금", "ATM 출금 100,000원", at(27, 12)))
        assertEquals(100_000L, balance(cash))
        // 적금 자동이체
        repo.handleNotification(RawNotification("wallet.app", "출금", "청년적금 자동이체 300,000원 출금", at(27, 13)))
        // 모으는 통장은 자산 점검 금액 기준이라 기록으로 본다
        assertEquals(savings, entries().single { it.label == "저축 넣기" }.toAccountId)
        assertEquals(50_000L, weekSpent())
        assertEquals(0L, income())
        // 적금 이자는 수입
        repo.handleNotification(RawNotification("wallet.app", "입금", "적금 이자 1,200원 입금", at(27, 14)))
        assertEquals(1_200L, income())
    }

    @Test
    fun `같은 카드값을 카드 앱도 알리면 한 번만`() = runTest {
        repo.handleNotification(RawNotification("wallet.app", "출금", "카드대금 200,000원 출금", at(27, 10)))
        repo.handleNotification(RawNotification("card.app", "신한카드", "결제대금 200,000원 출금 완료", at(27, 10, 1)))
        val moves = entries().filter { it.label == "카드값" }
        assertEquals(1, moves.size)
        assertEquals(bank, moves.single().accountId)
        assertEquals(card, moves.single().toAccountId)
    }

    @Test
    fun `결제 예정 안내·잔액 부족은 돈이 안 움직였다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "신한카드", "11월 결제예정금액 523,000원 안내", at(27, 9)))
        repo.handleNotification(RawNotification("wallet.app", "출금", "잔액부족으로 15,000원 출금 실패", at(27, 10)))
        assertTrue(entries().isEmpty())
        assertTrue(db.dao().reviewItemsNow().isEmpty())
    }

    @Test
    fun `계획과 이은 기록을 지우면 계획이 다시 안 쓴 계획으로, 되살리면 다시 잇는다`() = runTest {
        val plan = repo.savePlan(PlanEntity(epochDay = day.toEpochDay(), amount = 50_000, label = "팀 회식", category = Category.FOOD.name))
        repo.handleNotification(RawNotification("card.app", "신한카드 승인", "삼겹살 48,000원 결제", at(27, 21)))
        val e = db.dao().entryNow(repo.planNow(plan)!!.doneEntryId!!)!!
        repo.deleteEntry(e)
        assertTrue(repo.planNow(plan)!!.open)
        assertEquals(50_000L, repo.home(flowOf(at(27, 22))).first()!!.day.reserved)
        repo.restoreEntry(e)
        assertTrue(repo.planNow(plan)!!.done)
    }

    @Test
    fun `금액이 바뀌면 내 몫과 계획의 쓴 돈도 따라간다`() = runTest {
        val plan = repo.savePlan(PlanEntity(epochDay = day.toEpochDay(), amount = 20_000, label = "넷플릭스", category = Category.ETC.name))
        repo.handleNotification(RawNotification("card.app", "신한카드 해외승인", "USD 30.00 NETFLIX.COM", at(27, 9)))
        val e = entries().single()
        repo.confirmPlanHint(plan, e.id, 2)
        repo.saveFxRate("USD", 1300.0, DonRepository.FX_MANUAL, day)
        val after = db.dao().entryNow(e.id)!!
        assertEquals(39_000L, after.amount)
        assertEquals(Splits.myShare(39_000, 2), after.share)
        assertEquals(after.share, repo.planNow(plan)!!.doneAmount)
    }

    @Test
    fun `엔빵한 결제가 다 취소되면 생활비에서 내 몫만 돌아오고 받을 돈도 닫힌다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "신한카드 승인", "고깃집 100,000원 결제", at(27, 20)))
        val e = entries().single()
        repo.updateEntry(e.copy(share = 20_000, people = 5), e, false)
        repo.handleNotification(RawNotification("card.app", "신한카드 승인취소", "고깃집 100,000원 승인취소", at(28, 9)))
        val item = db.dao().reviewItemsNow().single()
        repo.resolveReview(item, ReviewResolution.APPLY_REFUND)
        val refund = entries().single { it.type == EntryType.REFUND.name }
        assertEquals(20_000L, refund.share)
        assertTrue(book().receivables.isEmpty())
        assertEquals(0L, balance(card))
        val spent = Stats.total(entries().map { it.toEntry() })
        assertEquals(0L, spent)
    }

    @Suppress("unused")
    private fun unused(e: EntryEntity) = assertNull(e.settleFor)
}
