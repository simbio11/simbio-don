package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.budget.EntryType
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

/**
 * 0.29: 실제 알림 문구로 — 결제(카드 승인)·입금·출금을 구분하고, 같은 돈을 두 앱이 알리면 한 번만,
 * 어느 통장에서 나갔는지는 더 확실한 알림을 따른다. 마지막 잔액이 은행 알림의 잔액과 맞는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationMatrixTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 9)
    private fun at(h: Int, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, 11, 9, h, m, s)
    private val bankPkg = "com.shinhan.sbanking"
    private val cardPkg = "com.shcard.smartpay"
    private val toss = "viva.republica.toss"
    private val kakao = "com.kakaopay.app"
    private var bank = 0L
    private var card = 0L
    private var cash = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_200_000, 20, listOf(FixedExpense("월세", 600_000), FixedExpense("통신비", 55_000)), 300_000, DayOfWeek.SUNDAY, d.minusDays(10))
        repo.saveRule(AppRule(bankPkg, "신한 SOL뱅크"))
        repo.saveRule(AppRule(cardPkg, "신한 SOL페이"))
        repo.saveRule(AppRule(toss, "토스"))
        repo.saveRule(AppRule(kakao, "카카오페이"))
        bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = bankPkg))
        card = repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = cardPkg))
        cash = repo.saveAccount(AccountEntity(name = "현금", type = AccountType.CASH.name, opening = 0, sortOrder = 2))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun one(label: String): EntryEntity = entries().single { it.label == label && it.type != EntryType.REFUND.name }
    private suspend fun balance(id: Long) = AccountBalances.compute(db.dao().accountsNow(), entries(), db.dao().assetChecksNow()).single { it.account.id == id }.balance
    private suspend fun reviews() = db.dao().reviewItemsNow()

    @Test
    fun `하루 알림 흐름 — 결제·입금·출금·같은 돈 두 알림·취소를 맞는 통장에 한 번씩`() = runTest {
        // 신용카드 승인 → 카드
        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인", "홍*동 12,000원(일시불)11/09 12:30 김밥천국 누적 152,300원", at(12, 30)))
        assertEquals(card, one("김밥천국").accountId)
        // 체크카드 승인 → 통장 (통장이 하나라 확실), 끝자리 기억
        repo.handleNotification(RawNotification(cardPkg, "신한체크(5678)승인", "홍*동 6,500원 11/09 13:00 스타벅스", at(13)))
        assertEquals(bank, one("스타벅스").accountId)
        assertFalse(one("스타벅스").acctGuess)
        // 같은 돈의 은행 출금 알림 → 두 번 적지 않고 확인함에도 안 간다
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 13:00 출금 6,500원 스타벅스 잔액 993,500원", at(13, 0, 40)))
        assertEquals(1, entries().count { it.amount == 6_500L })
        assertTrue(reviews().isEmpty())
        // 월급 입금 → 수입, 통장
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 09:00 입금 3,200,000원 (주)심비오 잔액 4,193,500원", at(9)))
        assertEquals(EntryType.INCOME.name, entries().single { it.amount == 3_200_000L }.type)
        // 은행 송금 → 지출, 통장
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 14:00 출금 50,000원 김철수 잔액 4,143,500원", at(14)))
        assertEquals(bank, one("김철수").accountId)
        // 토스 송금 (어느 통장인지 모름) + 은행 출금 알림 → 한 건, 통장 확실
        repo.handleNotification(RawNotification(toss, "송금 완료", "김영희님에게 30,000원을 보냈어요", at(15)))
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 15:00 출금 30,000원 토스 김영희 잔액 4,113,500원", at(15, 0, 50)))
        assertEquals(1, entries().count { it.amount == 30_000L })
        val sent = entries().single { it.amount == 30_000L }
        assertEquals(bank, sent.accountId)
        assertFalse(sent.acctGuess)
        // 카드값 → 통장에서 카드로 옮긴 돈 (지출 아님)
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 16:00 출금 152,300원 신한카드 잔액 3,961,200원", at(16)))
        assertEquals(EntryType.TRANSFER.name, entries().single { it.amount == 152_300L }.type)
        // ATM → 현금으로
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 17:00 ATM출금 50,000원 잔액 3,911,200원", at(17)))
        assertEquals(cash, entries().single { it.label == "현금 뽑기" }.toAccountId)
        // 카카오페이 결제 (통장 짐작) + 같은 돈의 카드 승인 → 카드로 옮기고 가게 이름은 이마트24
        repo.handleNotification(RawNotification(kakao, "카카오페이", "이마트24에서 15,000원 결제했어요", at(18)))
        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인", "홍*동 15,000원(일시불)11/09 18:00 카카오페이 누적 167,300원", at(18, 0, 30)))
        val pay = entries().single { it.amount == 15_000L }
        assertEquals("이마트24", pay.label)
        assertEquals(card, pay.accountId)
        assertFalse(pay.acctGuess)
        // 승인취소 → 원래 결제와 같은 카드·분류로 바로 환불 (확인함에 안 감)
        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인취소", "홍*동 12,000원 11/09 19:00 김밥천국", at(19)))
        val refund = entries().single { it.type == EntryType.REFUND.name }
        assertEquals(card, refund.accountId)
        assertEquals(one("김밥천국").category, refund.category)
        // 밤 체크카드 → 끝자리로 통장
        repo.handleNotification(RawNotification(cardPkg, "신한체크(5678)승인", "홍*동 8,000원 11/09 20:00 GS25", at(20)))
        assertEquals(bank, one("GS25").accountId)

        // 확인할 것 없이, 통장 잔액이 은행 알림 잔액(3,911,200원) − 그 뒤 체크 8,000원과 같다
        assertTrue(reviews().isEmpty())
        assertTrue(repo.accountQuestions.first().isEmpty())
        assertEquals(3_903_200L, balance(bank))
        assertEquals(50_000L, balance(cash))
        assertTrue(repo.balanceGaps.first().isEmpty())
    }

    @Test
    fun `순서가 반대여도 — 은행 출금이 먼저 오고 체크 승인이 뒤에 와도 한 건, 카드 승인이 먼저 오고 페이가 뒤에 와도 한 건`() = runTest {
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 13:00 출금 6,500원 스타벅스 잔액 993,500원", at(13)))
        repo.handleNotification(RawNotification(cardPkg, "신한체크(5678)승인", "홍*동 6,500원 11/09 13:00 스타벅스", at(13, 0, 30)))
        assertEquals(1, entries().count { it.amount == 6_500L })
        assertEquals(bank, entries().single { it.amount == 6_500L }.accountId)
        // 체크카드 끝자리는 그 통장 것으로 기억
        assertEquals("c5678", db.dao().accountsNow().first { it.id == bank }.tails.split(',').first { it.startsWith("c") })

        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인", "홍*동 15,000원(일시불)11/09 18:00 카카오페이 누적 15,000원", at(18)))
        repo.handleNotification(RawNotification(kakao, "카카오페이", "이마트24에서 15,000원 결제했어요", at(18, 0, 20)))
        val pay = entries().single { it.amount == 15_000L }
        assertEquals(card, pay.accountId)
        // 카드 알림의 가게 이름이 결제 수단 이름("카카오페이")이었으니 페이 알림의 가게 이름으로 바꾼다
        assertEquals("이마트24", pay.label)
        assertTrue(reviews().isEmpty())
    }

    @Test
    fun `진짜 두 번 낸 것일 수 있으면 합치지 않고 묻는다 — 신용카드 결제와 통장 송금이 같은 금액·같은 시각`() = runTest {
        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인", "홍*동 20,000원(일시불)11/09 12:00 교보문고 누적 20,000원", at(12)))
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 12:01 출금 20,000원 김철수 잔액 980,000원", at(12, 1)))
        // 카드도 통장도 확실한데 서로 다르다 → 확인함
        assertEquals(1, entries().count { it.amount == 20_000L })
        assertEquals(1, reviews().size)
    }

    @Test
    fun `원래 결제를 못 찾은 승인취소는 확인함으로`() = runTest {
        repo.handleNotification(RawNotification(cardPkg, "신한카드(1234)승인취소", "홍*동 9,900원 11/09 19:00 넷플릭스", at(19)))
        assertTrue(entries().none { it.type == EntryType.REFUND.name })
        assertEquals(1, reviews().size)
    }

    @Test
    fun `통장 잔액을 맞춘 뒤 남은 생활비도 통장에 맞추면 차이가 0이 되고 통장 잔액은 그대로`() = runTest {
        repo.handleNotification(RawNotification(bankPkg, "[신한은행]", "11/09 13:00 출금 6,500원 스타벅스 잔액 993,500원", at(13)))
        // 실제 통장이 1만 원 적다고 잔액을 맞추면서 생활비도 같이
        repo.fixBalance(bank, -10_000, DonRepository.BalanceFix.ALIGN_LIVING, d)
        assertEquals(983_500L, balance(bank))
        val h = repo.home(kotlinx.coroutines.flow.flowOf(at(21))).first()!!
        val gap = com.simbio.don.app.data.LivingGap.of(h.accounts, repo.splitBookNow(today = d), 0, h.plan.disposable - h.monthLiving, h.entries, h.today)!!
        assertEquals(0L, gap.rest)
        assertTrue(entries().any { it.label == DonRepository.ALIGN_LABEL })
        // 이미 맞으면 다시 눌러도 아무것도 안 남긴다
        assertEquals(0L, repo.alignLivingToBank(at(21, 30)))
        assertEquals(983_500L, balance(bank))
    }
}
