package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
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

/**
 * 0.30: 카드사 여러 곳의 승인 문자가 한 문자 앱으로 와도 카드사마다 다른 카드 자산에, 체크카드는 통장에,
 * 취소·할부·카드값까지 맞는 자산에 한 번씩 들어가는지.
 */
@RunWith(RobolectricTestRunner::class)
class RealTextFlowTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 9)
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 9, h, m)
    private val sms = "com.samsung.android.messaging"
    private val kbBank = "com.kbstar.kbbank"
    private val toss = "viva.republica.toss"
    private var bank = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_200_000, 25, listOf(FixedExpense("월세", 600_000)), 300_000, DayOfWeek.MONDAY, d.minusDays(15))
        repo.saveRule(AppRule(sms, "메시지"))
        repo.saveRule(AppRule(kbBank, "KB스타뱅킹"))
        repo.saveRule(AppRule(toss, "토스"))
        bank = repo.saveAccount(AccountEntity(name = "KB 생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = kbBank))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun accounts() = db.dao().accountsNow()
    private suspend fun idOf(name: String) = accounts().single { it.name == name }.id
    private suspend fun balance(id: Long) = AccountBalances.compute(accounts(), entries(), db.dao().assetChecksNow()).single { it.account.id == id }.balance
    private suspend fun spend(label: String) = entries().single { it.label == label && it.type == EntryType.SPEND.name }
    private suspend fun sms(h: Int, m: Int, text: String) = repo.handleNotification(RawNotification(sms, "", text, at(h, m)))

    @Test
    fun `여러 카드사 문자가 한 앱으로 와도 카드사마다 따로`() = runTest {
        sms(9, 0, "[Web발신]\nKB국민카드1234승인\n홍*동\n15,000원 일시불\n11/09 09:00\n이디야커피\n누적123,400원")
        sms(12, 0, "[Web발신]\n신한카드(5678)승인 홍*동 45,000원(일시불)11/09 12:00 교촌치킨 누적1,234,000원")
        sms(13, 0, "[Web발신]\n삼성카드4567승인 홍*동\n32,000원 일시불\n11/09 13:00 쿠팡\n누적 543,210원")
        sms(14, 0, "[Web발신]\nKB국민카드1234승인\n홍*동\n4,500원 일시불\n11/09 14:00\n메가커피\n누적128,900원")
        val kb = idOf("KB국민카드")
        val sh = idOf("신한카드")
        val ss = idOf("삼성카드")
        assertEquals(3, accounts().count { it.type == AccountType.CARD.name })
        // 문자 앱은 어느 카드와도 잇지 않는다
        assertTrue(accounts().none { it.packageName == sms })
        assertEquals(kb, spend("이디야커피").accountId)
        assertEquals(sh, spend("교촌치킨").accountId)
        assertEquals(ss, spend("쿠팡").accountId)
        assertEquals(kb, spend("메가커피").accountId)
        assertTrue(entries().none { it.acctGuess })
        // 통장은 그대로
        assertEquals(1_000_000L, balance(bank))
        assertEquals(-19_500L, balance(kb))

        // 체크카드는 통장에서 바로 (통장이 하나라 확실)
        sms(15, 0, "NH카드1*2*승인 홍*동 6,000원 체크 11/09 15:00 파리바게뜨")
        assertEquals(bank, spend("파리바게뜨").accountId)
        assertEquals(994_000L, balance(bank))

        // 신한 취소 → 신한카드로 돌아오고 원래 결제와 묶인다
        sms(18, 0, "[Web발신] 신한카드(5678)승인취소 홍*동 45,000원(일시불) 11/09 교촌치킨")
        assertEquals(0L, balance(sh))
        assertEquals(1, entries().count { it.type == EntryType.REFUND.name })

        // 할부: 첫 달 몫만 이번 달, 삼성카드로
        sms(19, 0, "삼성카드4567승인 홍*동 900,000원 03개월 11/09 19:00 하이마트")
        val hi = entries().filter { it.label == "하이마트" }
        assertEquals(3, hi.size)
        assertEquals(900_000L, hi.sumOf { it.amount })
        assertTrue(hi.all { it.accountId == ss })

        // 카드사를 모르는 일시불 문자: 카드가 여럿이면 새 "신용카드"로, 통장은 안 건드린다
        sms(20, 0, "[일시불.승인(9999)]11/09 20:00\n25,000원 / 홍*동\n교보문고")
        val book = spend("교보문고")
        assertFalse(book.accountId == bank)
        assertEquals(994_000L, balance(bank))

        // 카드값: 통장 → KB국민카드 (지출로 또 세지 않는다)
        repo.handleNotification(RawNotification(kbBank, "[KB국민은행]", "11/09 21:00 출금 19,500원 KB국민카드 잔액 974,500원", at(21)))
        assertEquals(0L, balance(kb))
        assertEquals(974_500L, balance(bank))
        assertEquals(0, entries().count { it.label.contains("KB국민카드") && it.type == EntryType.SPEND.name })
    }

    @Test
    fun `사람끼리 주고받은 토스 알림 — 받은 건 수입, 보낸 건 지출`() = runTest {
        repo.saveAccount(AccountEntity(name = "토스머니", type = AccountType.BANK.name, opening = 0, sortOrder = 1, packageName = toss))
        repo.handleNotification(RawNotification(toss, "", "김철수님이 50,000원을 보냈어요", at(10)))
        repo.handleNotification(RawNotification(toss, "", "이영희님에게 12,000원을 보냈어요", at(11)))
        val got = entries().single { it.label == "김철수" }
        assertEquals(EntryType.INCOME.name, got.type)
        assertEquals(50_000L, got.amount)
        val sent = entries().single { it.label == "이영희" }
        assertEquals(EntryType.SPEND.name, sent.type)
        assertEquals(38_000L, balance(idOf("토스머니")))
        // 확인함으로 가지 않았다 (어느 말인지 알았으니)
        assertTrue(db.dao().reviewItemsNow().none { it.amount == 50_000L })
    }
}
