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
 * 0.32: 내 통장끼리 옮긴 돈 — 수입·지출이 아니라 이체 한 건, 어느 통장에서 어느 통장으로인지 맞게,
 * 두 번 적지 않고, 한 번 알면 배워서 다음부터 저절로, 처음 보는 내 통장은 등록한다.
 */
@RunWith(RobolectricTestRunner::class)
class OwnMoveTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 16)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 16, h, m)
    private val shinhan = "com.shinhan.sbanking"
    private val kakao = "com.kakao.bank"
    private val toss = "viva.republica.toss"
    private var a = 0L
    private var b = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_000_000, 25, listOf(FixedExpense("월세", 600_000)), 0, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
        repo.saveRule(AppRule(shinhan, "신한 SOL뱅크"))
        repo.saveRule(AppRule(kakao, "카카오뱅크"))
        repo.saveRule(AppRule(toss, "토스"))
        a = repo.saveAccount(AccountEntity(name = "신한 생활비", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = shinhan))
        b = repo.saveAccount(AccountEntity(name = "카카오 비상금", type = AccountType.BANK.name, opening = 200_000, sortOrder = 1, packageName = kakao))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun balance(id: Long) = AccountBalances.compute(db.dao().accountsNow(), entries(), db.dao().assetChecksNow(), d).single { it.account.id == id }.balance
    private suspend fun home(): HomeState = repo.home(flowOf(at(22))).first()!!
    private suspend fun out(h: Int, m: Int, amount: String, to: String = "홍길동") =
        repo.handleNotification(RawNotification(shinhan, "[신한은행]", "11/16 %02d:%02d 이체 출금 %s원 %s 잔액 900,000원".format(h, m, amount, to), at(h, m)))
    private suspend fun into(h: Int, m: Int, amount: String, from: String = "홍길동") =
        repo.handleNotification(RawNotification(kakao, "입금", "입출금통장(3333) 입금 %s원 %s".format(amount, from), at(h, m)))

    @Test
    fun `나간 알림 뒤에 들어온 알림 — 이체 한 건, 수입·지출 없음, 두 통장 잔액이 따라간다`() = runTest {
        out(10, 0, "100,000")
        into(10, 1, "100,000")
        val t = entries().single()
        assertEquals(EntryType.TRANSFER.name, t.type)
        assertEquals(a, t.accountId)
        assertEquals(b, t.toAccountId)
        assertEquals(900_000L, balance(a))
        assertEquals(300_000L, balance(b))
        val h = home()
        assertEquals(0L, h.monthLiving)
        assertEquals(0L, h.monthIncome)
    }

    @Test
    fun `들어온 알림이 먼저 와도 한 건, 같은 알림이 또 와도 한 건`() = runTest {
        into(10, 0, "70,000")
        out(10, 2, "70,000")
        out(10, 2, "70,000")
        into(10, 0, "70,000")
        val t = entries().single()
        assertEquals(a, t.accountId)
        assertEquals(b, t.toAccountId)
        assertEquals(70_000L, t.amount)
    }

    @Test
    fun `한 번 짝지으면 배워서 — 다음엔 나간 알림만 와도 이체, 늦게 온 입금 알림은 합친다`() = runTest {
        out(9, 0, "50,000")
        into(9, 1, "50,000")
        // 다음 날: 입금 알림을 놓쳤다 (나간 알림만)
        repo.handleNotification(RawNotification(shinhan, "[신한은행]", "11/17 09:00 이체 출금 30,000원 홍길동 잔액 870,000원", LocalDateTime.of(2026, 11, 17, 9, 0)))
        val second = entries().filter { it.amount == 30_000L }.single()
        assertEquals(EntryType.TRANSFER.name, second.type)
        assertEquals(a, second.accountId)
        assertEquals(b, second.toAccountId)
        // 두 시간 뒤에 입금 알림이 와도 수입으로 또 적지 않는다
        repo.handleNotification(RawNotification(kakao, "입금", "입출금통장(3333) 입금 30,000원 홍길동", LocalDateTime.of(2026, 11, 17, 11, 0)))
        assertEquals(1, entries().count { it.amount == 30_000L })
        assertTrue(entries().none { it.type == EntryType.INCOME.name || it.type == EntryType.SPEND.name })
    }

    @Test
    fun `남에게 보낸 돈과 남이 보낸 돈이 우연히 같은 금액이면 합치지 않는다`() = runTest {
        repo.saveOwnerName("홍길동")
        out(12, 0, "20,000", to = "김철수")
        into(12, 5, "20,000", from = "이영희")
        assertEquals(1, entries().count { it.type == EntryType.SPEND.name })
        assertEquals(1, entries().count { it.type == EntryType.INCOME.name })
    }

    @Test
    fun `처음 보는 내 통장 — 내 통장에서 보낸 돈이 다른 앱에 들어오면 새 통장으로 등록하고 알림 잔액으로 맞춘다`() = runTest {
        out(15, 0, "40,000")
        repo.handleNotification(RawNotification(toss, "입금", "40,000원이 들어왔어요 잔액 140,000원", at(15, 1)))
        val tossAcc = db.dao().accountsNow().single { it.packageName == toss }
        val t = entries().single()
        assertEquals(EntryType.TRANSFER.name, t.type)
        assertEquals(a, t.accountId)
        assertEquals(tossAcc.id, t.toAccountId)
        assertEquals(140_000L, balance(tossAcc.id))
    }

    @Test
    fun `결제 내역에서 예산을 직접 고르면 그대로 — 식비도 이번 주에 나눠서`() = runTest {
        val id = repo.addManual(d, 30_000, "회식", EntryType.SPEND, Category.FOOD, accountId = a)
        assertEquals(0L, repo.home(flowOf(at(21))).first()!!.day.spentBig)
        repo.setEntryBucket(id, 1)
        val day = repo.home(flowOf(at(21))).first()!!.day
        assertEquals(30_000L, day.spentBig)
        assertEquals(0L, day.spentDaily)
    }

    private suspend fun raw(type: EntryType, amount: Long, label: String, account: Long, h: Int, m: Int, day: LocalDate = d, sentOut: Boolean = false) =
        db.dao().insertEntry(
            com.simbio.don.app.data.EntryEntity(
                epochDay = day.toEpochDay(), amount = amount, label = label, type = type.name, source = "NOTIFICATION",
                category = if (type == EntryType.INCOME) Category.OTHER_INCOME.name else Category.ETC.name, accountId = account,
                postedAtEpochSecond = day.atTime(h, m).toEpochSecond(java.time.ZoneOffset.UTC), sentOut = sentOut,
            ),
        )

    @Test
    fun `예전에 지출+수입으로 남은 내 통장끼리 이체는 정리할 때 한 건으로`() = runTest {
        raw(EntryType.SPEND, 120_000, "홍길동", a, 9, 0, sentOut = true)
        raw(EntryType.INCOME, 120_000, "홍길동", b, 9, 2)
        // 남끼리 같은 금액 (이름이 다름) — 그대로
        raw(EntryType.SPEND, 15_000, "김철수", a, 12, 0, sentOut = true)
        raw(EntryType.INCOME, 15_000, "이영희", b, 12, 3)
        repo.tidyUp(d)
        val t = entries().single { it.amount == 120_000L }
        assertEquals(EntryType.TRANSFER.name, t.type)
        assertEquals(a, t.accountId)
        assertEquals(b, t.toAccountId)
        assertEquals(2, entries().count { it.amount == 15_000L })
        assertTrue(entries().filter { it.amount == 15_000L }.none { it.type == EntryType.TRANSFER.name })
    }

    @Test
    fun `반쪽 이체와 따로 적힌 다른 쪽, 반쪽 두 개는 정리할 때 합친다`() = runTest {
        db.dao().insertEntry(
            com.simbio.don.app.data.EntryEntity(
                epochDay = d.toEpochDay(), amount = 60_000, label = DonRepository.SELF_TRANSFER_LABEL, type = EntryType.TRANSFER.name,
                source = "NOTIFICATION", category = Category.ETC.name, accountId = a, moveState = 1,
            ),
        )
        raw(EntryType.INCOME, 60_000, "홍길동", b, 15, 0, day = d.plusDays(1))
        db.dao().insertEntry(
            com.simbio.don.app.data.EntryEntity(
                epochDay = d.toEpochDay(), amount = 80_000, label = DonRepository.SELF_TRANSFER_LABEL, type = EntryType.TRANSFER.name,
                source = "NOTIFICATION", category = Category.ETC.name, accountId = a, moveState = 1,
            ),
        )
        db.dao().insertEntry(
            com.simbio.don.app.data.EntryEntity(
                epochDay = d.toEpochDay(), amount = 80_000, label = DonRepository.SELF_TRANSFER_LABEL, type = EntryType.TRANSFER.name,
                source = "NOTIFICATION", category = Category.ETC.name, toAccountId = b, moveState = 4,
            ),
        )
        repo.tidyUp(d.plusDays(1))
        val six = entries().single { it.amount == 60_000L }
        assertEquals(b, six.toAccountId)
        val eight = entries().single { it.amount == 80_000L }
        assertEquals(a, eight.accountId)
        assertEquals(b, eight.toAccountId)
        assertEquals(3, eight.moveState)
    }

    @Test
    fun `일정 예산에 결제를 직접 고른다 — 여럿, 하나 빼기, 다 빼면 다시 안 쓴 예산`() = runTest {
        val plan = repo.savePlan(com.simbio.don.app.data.PlanEntity(epochDay = d.toEpochDay(), amount = 40_000, label = "회식", category = Category.FOOD.name))
        val first = repo.addManual(d, 30_000, "고깃집", EntryType.SPEND, Category.FOOD, accountId = a)
        val second = repo.addManual(d, 20_000, "2차 맥주", EntryType.SPEND, Category.FOOD, accountId = a)
        repo.setPlanEntries(plan, listOf(first, second))
        var p = repo.planNow(plan)!!
        assertEquals(50_000L, p.doneAmount)
        assertEquals(listOf(first, second), p.entryIds)
        repo.rejectPlanMatch(plan, first)
        p = repo.planNow(plan)!!
        assertEquals(20_000L, p.doneAmount)
        repo.setPlanEntries(plan, emptyList())
        p = repo.planNow(plan)!!
        assertTrue(p.open)
        // 뺀 결제는 다시 저절로 잇지 않는다
        repo.autoLinkPlans(d)
        assertTrue(repo.planNow(plan)!!.open)
    }
}
