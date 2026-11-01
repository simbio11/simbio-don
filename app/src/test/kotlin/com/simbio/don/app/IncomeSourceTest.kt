package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.DonRepository.IncomeKind
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.toEntry
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.Stats
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId

/** 0.21.1: 들어온 돈이 정말 수입인지 — 정산·내 돈 옮김·맡은 돈, 잔액을 금액으로 잘못 읽은 알림, 가린 계좌번호 이름 */
@RunWith(RobolectricTestRunner::class)
class IncomeSourceTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 10, 23)
    private val nov1 = LocalDate.of(2026, 11, 1)
    private fun at(m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min)
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("bank.app", "OO은행"))
        repo.saveRule(AppRule("toss.app", "토스"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000, sortOrder = 0, packageName = "bank.app"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun income() = Stats.income(entries().map { it.toEntry() })

    @Test
    fun `엔빵 정산 — 몇 명인지 안 적었어도 남들 몫이 딱 나뉘면 정산, 가린 계좌번호 대신 보낸 사람 이름`() = runTest {
        // 어제 95,000원 내고 내 몫 19,000원 (몇 명인지는 안 적음)
        repo.addManual(LocalDate.of(2026, 10, 31), 95_000, "곱창", EntryType.SPEND, Category.FOOD, accountId = bank, share = 19_000)
        listOf("김민수", "이지은", "박서준", "최유나").forEachIndexed { i, who ->
            repo.handleNotification(RawNotification("bank.app", "입금 19,000원", "240-******-88507 $who", at(11, 1, i, 30)))
        }
        val all = entries().filter { it.amount == 19_000L }
        assertEquals(listOf("김민수", "이지은", "박서준", "최유나"), all.map { it.label })
        assertTrue(all.all { it.settleFor != null })
        assertEquals(0L, income())
        assertTrue(repo.splitBookNow(today = nov1).receivables.isEmpty())
    }

    @Test
    fun `결혼식 — 내 다른 통장에서 옮긴 20만 원은 이체, 잔액을 금액으로 읽지 않기, 다음부터 내 이름은 저절로`() = runTest {
        // 잔액이 금액 앞에 오는 알림 (예전엔 200,995원을 금액으로 읽었다)
        repo.handleNotification(RawNotification("bank.app", "240-******-88507", "잔액(출금가능) 200,995원\n입금 200,000원 심비오", at(11, 1, 11, 31)))
        // 같은 돈을 토스도 알림
        repo.handleNotification(RawNotification("toss.app", "입금", "심비오 200,000원 입금", at(11, 1, 11, 31)))
        assertEquals(listOf(200_000L), entries().map { it.amount })
        assertEquals("심비오", entries().single().label)

        // 누구 돈이에요? → 내 돈 옮김
        val q = repo.incomeQuestions(nov1).first().single()
        repo.explainIncome(q.id, IncomeKind.SELF)
        assertEquals(0L, income())
        assertEquals(EntryType.TRANSFER.name, entries().single().type)
        assertEquals("심비오", db.dao().settingsNow()!!.ownerName)
        assertTrue(repo.incomeQuestions(nov1).first().isEmpty())

        // 다음에 내 이름에서 온 돈은 묻지 않고 내 계좌 이체
        repo.handleNotification(RawNotification("bank.app", "입금 50,000원", "240-******-88507 심비오", at(11, 1, 15)))
        assertEquals(0L, income())
        // 친구 이름이 보낸 돈은 그대로 묻는다
        repo.handleNotification(RawNotification("bank.app", "입금 100,000원", "240-******-88507 지수누", at(11, 1, 16)))
        val friend = repo.incomeQuestions(nov1).first().single()
        assertEquals("지수누", friend.label)
        // 대신 내 달라고 맡긴 돈 → 수입 아님, 잔액에는 들어감
        repo.explainIncome(friend.id, IncomeKind.HELD)
        assertEquals(0L, income())
        assertEquals(DonRepository.HELD_LABEL, db.dao().entryNow(friend.id)!!.label)
        // ATM으로 30만 원 → 지출 아님
        repo.handleNotification(RawNotification("bank.app", "출금 300,000원", "ATM 출금 240-******-88507", at(11, 1, 17)))
        assertEquals(0L, Stats.total(entries().map { it.toEntry() }))
    }

    @Test
    fun `예전에 잘못 적힌 기록 정리 — 잔액을 금액으로 읽은 것은 고치고 다른 앱과 겹치면 하나만, 가린 계좌번호 이름은 다시 찾기`() = runTest {
        val sec = at(11, 1, 11, 31).atZone(ZoneId.systemDefault()).toEpochSecond()
        db.dao().insertEntry(
            EntryEntity(
                epochDay = nov1.toEpochDay(), amount = 200_995, label = "240-******-88507", type = EntryType.INCOME.name,
                source = EntrySource.NOTIFICATION.name, packageName = "bank.app", postedAtEpochSecond = sec, category = Category.OTHER_INCOME.name,
                accountId = bank, rawText = "240-******-88507\n잔액(출금가능) 200,995원\n입금 200,000원 심비오",
            ),
        )
        db.dao().insertEntry(
            EntryEntity(
                epochDay = nov1.toEpochDay(), amount = 200_000, label = "심비오", type = EntryType.INCOME.name,
                source = EntrySource.NOTIFICATION.name, packageName = "toss.app", postedAtEpochSecond = sec, category = Category.OTHER_INCOME.name,
                accountId = bank, rawText = "입금\n심비오 200,000원 입금",
            ),
        )
        db.dao().insertEntry(
            EntryEntity(
                epochDay = nov1.toEpochDay(), amount = 19_000, label = "240-******-88507", type = EntryType.INCOME.name,
                source = EntrySource.NOTIFICATION.name, packageName = "bank.app", postedAtEpochSecond = sec + 600, category = Category.OTHER_INCOME.name,
                accountId = bank, rawText = "입금 19,000원\n240-******-88507 김민수",
            ),
        )
        repo.tidyUp(nov1)
        val left = entries()
        assertEquals(listOf(200_000L, 19_000L), left.map { it.amount })
        assertEquals("김민수", left.single { it.amount == 19_000L }.label)
        assertTrue(repo.splitBook(MutableStateFlow(emptySet()), nov1).first().autoLinks.isEmpty())
    }
}
