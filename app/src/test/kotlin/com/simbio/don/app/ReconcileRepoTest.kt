package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.DonRepository.BalanceFix
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PlanFitKind
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

/** 0.20: 달러 결제·환전, 알림 잔액과 실제 잔액 맞추기, 계획과 실제 결제 잇기 */
@RunWith(RobolectricTestRunner::class)
class ReconcileRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 10, 23)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)
    private var bank = 0L
    private var card = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("wallet.app", "OO뱅크"))
        repo.saveRule(AppRule("card.app", "OO카드"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "wallet.app"))
        card = repo.saveAccount(AccountEntity(name = "OO카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun balance(id: Long) =
        AccountBalances.compute(db.dao().accountsNow().filter { it.id == id }, entries(), db.dao().assetChecksNow()).single().balance

    @Test
    fun `환전은 이체, 외화 지갑으로 낸 달러 결제는 환전 환율로 원화 지출`() = runTest {
        repo.handleNotification(RawNotification("wallet.app", "환전 완료", "12,500원을 10.00달러로 환전했어요", at(27, 10)))
        val ex = entries().single()
        assertEquals(EntryType.TRANSFER.name, ex.type)
        assertEquals(12_500, ex.amount)
        assertEquals(bank, ex.accountId)
        assertEquals("USD 10.00", ex.fxText)
        assertEquals("달러 환전", ex.label)
        assertEquals(1250.0, repo.fxRateMap()["USD"]!!, 0.001)

        repo.handleNotification(RawNotification("wallet.app", "결제", "$5.00 결제 | 스타벅스 (외화)", at(27, 15)))
        val pay = entries().single { it.type == EntryType.SPEND.name }
        assertEquals(6_250, pay.amount)
        assertEquals("USD 5.00", pay.fxText)
        assertEquals(false, pay.fxGuess)
        // 원화 통장은 환전 때만 줄고, 생활비에는 결제만 들어간다
        assertNull(pay.accountId)
        assertEquals(1_000_000 - 12_500, balance(bank))
        val h = repo.home(flowOf(at(27, 20))).first()!!
        assertEquals(6_250, h.currentWeek!!.spent)
    }

    @Test
    fun `카드 해외 결제 — 어림 환율로 적고, 환율을 받으면 다시, 원화 청구 알림은 같은 기록을 고친다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "OO카드 해외승인", "USD 12.99 NETFLIX.COM", at(27, 9)))
        val e = entries().single()
        assertEquals(18_186, e.amount) // 어림 1,400원
        assertTrue(e.fxGuess)
        assertEquals(card, e.accountId)
        assertEquals("NETFLIX.COM", e.label)
        assertEquals(setOf("USD"), repo.needsFxRefresh(LocalDate.of(2026, 10, 27)))

        repo.saveFxRate("USD", 1385.0, DonRepository.FX_PUBLIC, LocalDate.of(2026, 10, 27))
        assertEquals(17_991, entries().single().amount)
        assertEquals(false, entries().single().fxGuess)
        // 직접 정한 환율은 공개 환율이 덮지 않는다
        repo.saveFxRate("USD", 1400.0, DonRepository.FX_MANUAL)
        repo.saveFxRate("USD", 1300.0, DonRepository.FX_PUBLIC)
        assertEquals(1400.0, repo.fxRateMap()["USD"]!!, 0.001)

        repo.handleNotification(RawNotification("card.app", "OO카드", "NETFLIX.COM 18,220원 승인 확정", at(30, 9)))
        assertEquals(1, entries().size)
        assertEquals(18_220, entries().single().amount)
    }

    @Test
    fun `알림 잔액이 앱과 다르면 남기고, 빠진 지출로 맞추면 잔액과 생활비가 같이 맞는다`() = runTest {
        repo.handleNotification(RawNotification("wallet.app", "출금", "체크카드 출금 5,000원 GS25 잔액 975,000원", at(27, 9)))
        val gap = repo.balanceGaps.first().single()
        assertEquals(bank, gap.accountId)
        assertEquals(975_000, gap.real)
        assertEquals(995_000, gap.app)

        repo.fixBalance(bank, gap.real - gap.app, BalanceFix.MISSING_SPEND, LocalDate.of(2026, 10, 27))
        assertTrue(repo.balanceGaps.first().isEmpty())
        assertEquals(975_000, balance(bank))
        val missing = entries().single { it.label == "빠진 지출 (잔액 맞춤)" }
        assertEquals(20_000, missing.amount)
        assertEquals(25_000, repo.home(flowOf(at(27, 20))).first()!!.currentWeek!!.spent)

        // 다음 알림 잔액이 맞으면 차이 없음
        repo.handleNotification(RawNotification("wallet.app", "출금", "체크카드 출금 3,000원 CU 잔액 972,000원", at(27, 12)))
        assertTrue(repo.balanceGaps.first().isEmpty())

        // 잔액만 맞추기: 기록·생활비는 그대로
        val before = entries().size
        repo.fixBalance(bank, 10_000, BalanceFix.BALANCE_ONLY)
        assertEquals(before, entries().size)
        assertEquals(982_000, balance(bank))
    }

    @Test
    fun `계획과 맞는 결제는 저절로 잇고, 아니라고 하면 풀고 다시 잇지 않는다`() = runTest {
        val dinner = repo.savePlan(PlanEntity(epochDay = LocalDate.of(2026, 10, 27).toEpochDay(), amount = 50_000, label = "팀 회식", category = Category.FOOD.name))
        assertEquals(50_000, repo.home(flowOf(at(27, 9))).first()!!.day.reserved)
        repo.handleNotification(RawNotification("card.app", "OO카드 승인", "삼겹살 62,000원 결제", at(27, 21)))
        val p = repo.planNow(dinner)!!
        assertTrue(p.done)
        assertTrue(p.autoLinked)
        assertEquals(62_000L, p.doneAmount)
        // 계획이 끝나 이번 주에서 두 번 빠지지 않는다
        assertEquals(0, repo.home(flowOf(at(27, 22))).first()!!.day.reserved)

        repo.rejectPlanMatch(dinner, p.doneEntryId!!)
        assertTrue(repo.planNow(dinner)!!.open)
        assertEquals(0, repo.autoLinkPlans(LocalDate.of(2026, 10, 27)))
        val left = repo.planHints(LocalDate.of(2026, 10, 27)).first()
        assertTrue(left.map { it.plan.label + "/" + it.entry.label + "/" + it.plan.rejected }.toString(), left.isEmpty())
    }

    @Test
    fun `계획의 몇 배를 내가 다 냈으면 엔빵인지 묻고, 맞으면 내 몫만 계획과 잇는다`() = runTest {
        val dinner = repo.savePlan(PlanEntity(epochDay = LocalDate.of(2026, 10, 27).toEpochDay(), amount = 50_000, label = "동기 모임", category = Category.FOOD.name))
        repo.handleNotification(RawNotification("card.app", "OO카드 승인", "삼겹살집 200,000원 결제", at(27, 22)))
        assertTrue(repo.planNow(dinner)!!.open)
        val hint = repo.planHints(LocalDate.of(2026, 10, 27)).first().single()
        assertEquals(PlanFitKind.SPLIT, hint.fit.kind)
        assertEquals(4, hint.fit.people)

        repo.confirmPlanHint(dinner, hint.entry.id, 4)
        val e = db.dao().entryNow(hint.entry.id)!!
        assertEquals(50_000L, e.share)
        assertEquals(4, e.people)
        val p = repo.planNow(dinner)!!
        assertEquals(50_000L, p.doneAmount)
        assertEquals(false, p.autoLinked)
        assertTrue(repo.planHints(LocalDate.of(2026, 10, 27)).first().isEmpty())
    }

    @Test
    fun `자산을 안 이은 앱 결제는 생활비 통장으로, 자산 없이 적힌 기록은 한 번에 넣는다`() = runTest {
        repo.saveRule(AppRule("other.app", "다른 페이"))
        repo.handleNotification(RawNotification("other.app", "결제", "GS25 4,000원 결제", at(27, 9)))
        assertEquals(bank, entries().single().accountId)
        // 자산 없이 직접 적은 지출은 잔액에 안 들어가 있다 → 생활비 통장으로
        repo.addManual(LocalDate.of(2026, 10, 27), 10_000, "현금 아닌 지출", EntryType.SPEND, Category.FOOD)
        assertEquals(1, repo.unassignedEntries.first().size)
        assertEquals(1, repo.assignUnassigned(repo.mainAccount()!!))
        assertTrue(repo.unassignedEntries.first().isEmpty())
        assertEquals(1_000_000 - 14_000, balance(bank))
    }
}
