package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.SplitHint
import com.simbio.don.app.data.Splits
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
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

/** 0.17: 더치페이 — 생활비에는 내 몫만, 친구가 보낸 돈은 수입이 아니라 정산, 보낼 돈은 송금과 합친다 */
@RunWith(RobolectricTestRunner::class)
class SplitRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository

    private val start = LocalDate.of(2026, 10, 23)
    private fun at(month: Int, day: Int, h: Int = 12) = LocalDateTime.of(2026, month, day, h, 0)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(
            3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 500_000, DayOfWeek.MONDAY, start,
            accounts = listOf(DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_000_000)),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(now: LocalDateTime) = repo.home(flowOf(now)).first()!!
    private suspend fun bank() = db.dao().accountsNow().single().id
    private suspend fun balance(now: LocalDateTime) = home(now).accounts.single().balance

    @Test
    fun `한 사람 몫은 10원 단위로 올리고 끝전은 낸 사람이 안는다`() {
        assertEquals(26_670L, Splits.perPerson(80_000, 3))
        assertEquals(26_660L, Splits.myShare(80_000, 3))
        assertEquals(20_000L, Splits.myShare(80_000, 4))
        assertEquals(15_000L, Splits.myShare(15_000, 1))
    }

    @Test
    fun `내가 다 내면 내 몫만 생활비고 친구가 보낸 돈은 수입이 아니라 정산이다`() = runTest {
        val bank = bank()
        repo.addManual(d(10, 24), 80_000, "고깃집", EntryType.SPEND, Category.FOOD, accountId = bank, share = Splits.myShare(80_000, 4), people = 4)
        var h = home(at(10, 24))
        assertEquals(20_000L, h.monthLiving)
        assertEquals(920_000L, h.accounts.single().balance)

        // 친구 입금이 알림으로 수입처럼 들어왔다
        val inc = repo.addManual(d(10, 24), 20_000, "김철수", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank)
        assertEquals(20_000L, home(at(10, 24)).monthIncome)
        var book = repo.splitBookNow(today = d(10, 24))
        assertEquals(60_000L, book.toReceive)
        val hint = book.hints.single() as SplitHint.Received
        assertEquals(inc, hint.income.id)
        assertEquals(20_000L, hint.target.perPerson)

        repo.settleWith(hint.target.entry, hint.income)
        h = home(at(10, 24))
        assertEquals(0L, h.monthIncome)
        assertEquals(20_000L, h.monthLiving)
        assertEquals(940_000L, h.accounts.single().balance)
        book = repo.splitBookNow(today = d(10, 24))
        assertEquals(40_000L, book.toReceive)
        assertTrue(book.hints.isEmpty())

        // 현금으로 받음 (통장 없이)
        repo.settleDirect(hint.target.entry, 20_000, null, d(10, 25))
        assertEquals(20_000L, repo.splitBookNow(today = d(10, 25)).toReceive)
        assertEquals(940_000L, balance(at(10, 25)))

        // 남은 사람은 안 받기로 → 그만큼 내 지출
        repo.forgiveSplit(hint.target.entry)
        book = repo.splitBookNow(today = d(10, 25))
        assertTrue(book.receivables.isEmpty())
        assertEquals(1, book.done.size)
        assertEquals(40_000L, home(at(10, 25)).monthLiving)
    }

    @Test
    fun `남이 내면 보낼 돈으로 적었다가 송금 알림과 하나로 합친다`() = runTest {
        val bank = bank()
        repo.addManual(d(10, 24), 25_000, "회식", EntryType.SPEND, Category.FOOD, accountId = bank, owe = true)
        val owe = db.dao().allEntriesNow().single { it.label == "회식" }
        assertTrue(owe.owe)
        assertNull(owe.accountId)
        assertEquals(25_000L, home(at(10, 24)).monthLiving)
        assertEquals(1_000_000L, balance(at(10, 24)))
        assertEquals(25_000L, repo.splitBookNow(today = d(10, 24)).toSend)

        // 다음 날 송금 알림 (보통 지출 + 송금 표시로 들어온다)
        db.dao().insertEntry(
            EntryEntity(
                epochDay = d(10, 25).toEpochDay(), amount = 25_000, label = "이영희", type = EntryType.SPEND.name,
                source = EntrySource.NOTIFICATION.name, packageName = "viva.republica.toss", postedAtEpochSecond = 1L, accountId = bank, sentOut = true,
            ),
        )
        assertEquals(50_000L, home(at(10, 25)).monthLiving)
        val hint = repo.splitBookNow(today = d(10, 25)).hints.single() as SplitHint.Sent
        assertEquals(owe.id, hint.target.id)

        repo.payWith(hint.target, hint.out)
        val h = home(at(10, 25))
        assertEquals(25_000L, h.monthLiving)
        assertEquals(975_000L, h.accounts.single().balance)
        val merged = db.dao().allEntriesNow().filter { it.type == EntryType.SPEND.name && it.fixedId == null }.single()
        assertEquals("회식", merged.label)
        assertEquals(Category.FOOD.name, merged.category)
        assertEquals(d(10, 24).toEpochDay(), merged.epochDay)
        assertTrue(merged.memo.contains("이영희에게 보냄"))
        // 알림 중복 판정용 정보는 그대로
        assertEquals("viva.republica.toss", merged.packageName)
        val book = repo.splitBookNow(today = d(10, 25))
        assertEquals(0L, book.toSend)
        assertTrue(book.hints.isEmpty())
    }

    @Test
    fun `보냈어요를 누르면 고른 통장에서 빠진다`() = runTest {
        val bank = bank()
        repo.addManual(d(10, 24), 18_000, "카페 모임", EntryType.SPEND, Category.CAFE, owe = true)
        val owe = db.dao().allEntriesNow().single { it.owe }
        repo.markPaid(owe, bank)
        assertEquals(982_000L, balance(at(10, 24)))
        assertTrue(repo.splitBookNow(today = d(10, 24)).payables.isEmpty())
    }

    @Test
    fun `나눠 낸 기록을 지우면 묶었던 입금은 다시 수입이 된다`() = runTest {
        val bank = bank()
        val shared = repo.addManual(d(10, 24), 60_000, "피자", EntryType.SPEND, Category.FOOD, accountId = bank, share = 20_000, people = 3)
        val inc = repo.addManual(d(10, 24), 20_000, "박민수", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank)
        val target = db.dao().entryNow(shared)!!
        repo.settleWith(target, db.dao().entryNow(inc)!!)
        assertEquals(EntryType.TRANSFER.name, db.dao().entryNow(inc)!!.type)
        repo.deleteEntry(target)
        val back = db.dao().entryNow(inc)!!
        assertEquals(EntryType.INCOME.name, back.type)
        assertEquals(bank, back.accountId)
        assertNull(back.settleFor)
        assertEquals(20_000L, home(at(10, 24)).monthIncome)
    }

    @Test
    fun `남 것을 대신 다 내 줬으면 내 지출은 0이고 전부 받을 돈이다`() = runTest {
        val bank = bank()
        repo.addManual(d(10, 24), 30_000, "동생 선물 대신 결제", EntryType.SPEND, Category.SHOPPING, accountId = bank, share = 0)
        assertEquals(0L, home(at(10, 24)).monthLiving)
        assertEquals(970_000L, balance(at(10, 24)))
        assertEquals(30_000L, repo.splitBookNow(today = d(10, 24)).toReceive)
    }

    @Test
    fun `아니에요 한 입금은 다시 묻지 않는다`() = runTest {
        val bank = bank()
        repo.addManual(d(10, 24), 40_000, "치킨", EntryType.SPEND, Category.FOOD, accountId = bank, share = 20_000, people = 2)
        val inc = repo.addManual(d(10, 26), 20_000, "용돈", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank)
        assertEquals(1, repo.splitBookNow(today = d(10, 26)).hints.size)
        assertTrue(repo.splitBookNow(setOf(inc), today = d(10, 26)).hints.isEmpty())
        // 2주가 지나 들어온 돈은 짝으로 보지 않는다
        repo.addManual(d(11, 10), 20_000, "늦은 입금", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank)
        assertTrue(repo.splitBookNow(setOf(inc), today = d(11, 10)).hints.isEmpty())
    }
}
