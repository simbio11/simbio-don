package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountBalances
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.AskGuess
import com.simbio.don.app.data.AskKind
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.ReviewItemEntity
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import kotlin.test.assertEquals as eq
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 0.35 월급날 시뮬레이션: 월급이 들어오면 고정비 통장·적금·생활비 통장으로 나눠 옮긴다.
 * "물어보고 적기"에서는 애매한 알림을 바로 적지 않고 비서가 짐작해 묻는다 — 답하면 그대로 한 번만 적히고,
 * 같은 알림이 또 오거나 다른 쪽 통장 알림이 와도 두 번 적지 않는다. 기억해 두면 다음 달엔 묻지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class PaydayAskTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val kb = "com.kbstar.kbbank"
    private val toss = "viva.republica.toss"
    private val card = "com.shcard.smartpay"
    private var salaryAcc = 0L
    private var living = 0L
    private var fixedAcc = 0L
    private var saving = 0L
    private var creditCard = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        // 월급 320만 · 고정 지출 70만(월세 60만 1일, 통신비 10만 15일) · 저축 50만 → 생활비 200만
        repo.completeOnboarding(
            3_200_000, 25, listOf(FixedExpense("월세", 600_000, 1), FixedExpense("통신비", 100_000, 15)), 500_000, DayOfWeek.MONDAY,
            LocalDate.of(2026, 10, 25), askFirst = true,
        )
        repo.saveRule(AppRule(kb, "KB스타뱅킹"))
        repo.saveRule(AppRule(toss, "토스"))
        repo.saveRule(AppRule(card, "신한 SOL페이"))
        salaryAcc = repo.saveAccount(AccountEntity(name = "KB 월급 통장", type = AccountType.BANK.name, opening = 100_000, sortOrder = 0, packageName = kb), today = LocalDate.of(2026, 10, 25))
        living = repo.saveAccount(AccountEntity(name = "토스 생활비 통장", type = AccountType.BANK.name, opening = 50_000, sortOrder = 1, packageName = toss), today = LocalDate.of(2026, 10, 25))
        fixedAcc = repo.saveAccount(AccountEntity(name = "고정비 통장", type = AccountType.BANK.name, opening = 0, sortOrder = 2), today = LocalDate.of(2026, 10, 25))
        saving = repo.saveAccount(AccountEntity(name = "적금", type = AccountType.SAVINGS.name, opening = 0, sortOrder = 3), today = LocalDate.of(2026, 10, 25))
        creditCard = repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 4, packageName = card), today = LocalDate.of(2026, 10, 25))
    }

    @After
    fun tearDown() = db.close()

    private fun at(m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min)
    private suspend fun entries() = db.dao().allEntriesNow()
    private suspend fun asks(): List<ReviewItemEntity> = repo.askItems.first()
    private suspend fun balance(id: Long, day: LocalDate) =
        AccountBalances.compute(db.dao().accountsNow(), entries(), db.dao().assetChecksNow(), day).single { it.account.id == id }.balance
    private suspend fun home(t: LocalDateTime): HomeState = repo.home(flowOf(t)).first()!!
    private suspend fun kbSays(t: LocalDateTime, text: String) = repo.handleNotification(RawNotification(kb, "[KB]", text, t))
    private suspend fun tossSays(t: LocalDateTime, text: String) = repo.handleNotification(RawNotification(toss, "토스", text, t))

    /** 비서의 짐작 그대로 "맞아요" (+ 기억) */
    private suspend fun yes(item: ReviewItemEntity, remember: Boolean = true) = repo.answerAsk(item.id, AskGuess.decode(item.guess)!!, remember)

    private suspend fun payday(m: Int, d: Int, answer: Boolean) {
        kbSays(at(m, d, 9), "입금 3,200,000원 (주)회사 급여 잔액 3,300,000원")
        kbSays(at(m, d, 9, 10), "이체 출금 700,000원 홍길동 잔액 2,600,000원")
        kbSays(at(m, d, 9, 11), "자동이체 출금 500,000원 적금 잔액 2,100,000원")
        kbSays(at(m, d, 9, 12), "이체 출금 2,000,000원 홍길동 잔액 100,000원")
        tossSays(at(m, d, 9, 13), "홍길동님이 2,000,000원을 보냈어요")
        // 토스 알림이 한 번 더 왔다 (앱을 다시 켬)
        tossSays(at(m, d, 9, 13), "홍길동님이 2,000,000원을 보냈어요")
        if (answer) asks().forEach { yes(it) }
    }

    @Test
    fun `월급날 나누기 — 묻고, 짐작은 맞고, 답하면 한 번씩만, 생활비는 계획대로`() = runTest {
        kbSays(at(11, 25, 9), "입금 3,200,000원 (주)회사 급여 잔액 3,300,000원")
        // 답하기 전엔 적지 않는다 (월급도 아직 새 달로 안 넘긴다)
        assertTrue(entries().none { it.amount == 3_200_000L })
        val salary = asks().single()
        assertEquals(AskKind.SALARY, AskGuess.decode(salary.guess)!!.kind)

        kbSays(at(11, 25, 9, 10), "이체 출금 700,000원 홍길동 잔액 2,600,000원")
        kbSays(at(11, 25, 9, 11), "자동이체 출금 500,000원 적금 잔액 2,100,000원")
        kbSays(at(11, 25, 9, 12), "이체 출금 2,000,000원 홍길동 잔액 100,000원")
        tossSays(at(11, 25, 9, 13), "홍길동님이 2,000,000원을 보냈어요")
        tossSays(at(11, 25, 9, 13), "홍길동님이 2,000,000원을 보냈어요")
        val q = asks().associateBy { it.amount }
        // 다섯 알림(+다시 온 것)이 네 질문으로: 들어온 토스 알림은 나간 알림과 한 질문
        assertEquals(setOf(3_200_000L, 700_000L, 500_000L, 2_000_000L), q.keys)
        val fixed = AskGuess.decode(q.getValue(700_000L).guess)!!
        assertEquals(AskKind.TRANSFER, fixed.kind)
        assertEquals(fixedAcc, fixed.to)
        val save = AskGuess.decode(q.getValue(500_000L).guess)!!
        assertEquals(AskKind.SAVINGS, save.kind)
        assertEquals(saving, save.to)
        val move = AskGuess.decode(q.getValue(2_000_000L).guess)!!
        assertEquals(AskKind.TRANSFER, move.kind)
        assertEquals(salaryAcc, move.from)
        assertEquals(living, move.to)
        assertTrue(entries().isEmpty())

        asks().forEach { yes(it) }
        assertTrue(asks().isEmpty())
        val d = LocalDate.of(2026, 11, 25)
        assertEquals(100_000L, balance(salaryAcc, d))
        assertEquals(2_050_000L, balance(living, d))
        assertEquals(700_000L, balance(fixedAcc, d))
        assertEquals(500_000L, balance(saving, d))
        // 같은 돈은 한 번씩만
        assertEquals(1, entries().count { it.amount == 2_000_000L })
        val h = home(at(11, 25, 20))
        assertEquals(LocalDate.of(2026, 11, 25), h.cycle.start)
        assertEquals(3_200_000L, h.monthIncome)
        assertEquals(0L, h.monthLiving)
        // 계획한 저축만큼 옮겼으니 생활비는 그대로 200만
        assertEquals(2_000_000L, h.plan.disposable)

        // 카드 결제처럼 분명한 건 묻지 않고 적는다
        repo.handleNotification(RawNotification(card, "신한카드(1234)승인", "홍*동 12,000원(일시불)11/26 12:00 김밥천국", at(11, 26, 12)))
        assertTrue(asks().isEmpty())
        assertEquals(12_000L, home(at(11, 26, 20)).monthLiving)
    }

    @Test
    fun `다음 달 월급날 — 기억한 알림은 묻지 않고 전에 고른 대로, 금액이 달라도 같은 모양이면`() = runTest {
        payday(11, 25, answer = true)
        assertTrue(asks().isEmpty())
        val before = entries().size
        // 12월: 월급이 조금 다르게 들어와도 같은 모양
        kbSays(at(12, 25, 9), "입금 3,250,000원 (주)회사 급여 잔액 3,350,000원")
        kbSays(at(12, 25, 9, 10), "이체 출금 700,000원 홍길동 잔액 2,650,000원")
        kbSays(at(12, 25, 9, 11), "자동이체 출금 500,000원 적금 잔액 2,150,000원")
        kbSays(at(12, 25, 9, 12), "이체 출금 2,000,000원 홍길동 잔액 150,000원")
        tossSays(at(12, 25, 9, 13), "홍길동님이 2,000,000원을 보냈어요")
        assertTrue(asks().isEmpty())
        // 월급 1 + 고정비 이체 1 + 적금 1 + 생활비 이체 1 (토스 입금은 그 이체에 합쳐짐)
        eq(before + 4, entries().size, entries().joinToString("\n") { "${it.epochDay} ${it.type} ${it.amount} ${it.label} ${it.accountId}->${it.toAccountId} s${it.moveState}" })
        val d = LocalDate.of(2026, 12, 25)
        assertEquals(1_400_000L, balance(fixedAcc, d))
        assertEquals(1_000_000L, balance(saving, d))
        assertEquals(LocalDate.of(2026, 12, 25), home(at(12, 25, 20)).cycle.start)
        assertEquals(3_250_000L, home(at(12, 25, 20)).salary)
    }

    @Test
    fun `무시하기로 한 알림은 기억해서 다음부터 버린다, 기억 안 하면 또 묻는다`() = runTest {
        kbSays(at(11, 26, 10), "입금 12원 결산이자 잔액 100,012원")
        val item = asks().single()
        repo.answerAsk(item.id, AskGuess(AskKind.IGNORE), remember = true)
        kbSays(at(11, 27, 10), "입금 15원 결산이자 잔액 100,027원")
        assertTrue(asks().isEmpty())
        assertTrue(entries().isEmpty())
        // 기억하지 않은 답은 다음에 또 묻는다 (먼저 그 답을 골라 두지 않고 비서가 다시 짐작)
        kbSays(at(11, 28, 10), "입금 30,000원 김철수 잔액 130,000원")
        repo.answerAsk(asks().single().id, AskGuess(AskKind.INCOME, to = salaryAcc, category = "OTHER_INCOME"), remember = false)
        kbSays(at(11, 29, 10), "입금 40,000원 김철수 잔액 170,000원")
        assertEquals(1, asks().size)
    }

    @Test
    fun `저절로 결산할 때도 묻는 것은 지출로 넣지 않는다`() = runTest {
        kbSays(at(10, 27, 9), "이체 출금 50,000원 김철수 잔액 50,000원")
        assertEquals(1, asks().size)
        // 그 주가 끝나고 정오가 지나 저절로 결산
        val h = home(at(11, 3, 13))
        h.settleable.forEach { repo.settle(it, at(11, 3, 13)) }
        assertEquals(1, asks().size)
        assertTrue(entries().none { it.amount == 50_000L })
    }

    @Test
    fun `들어온 알림이 먼저 와도 한 질문으로 합치고 한 번만 적는다`() = runTest {
        tossSays(at(11, 25, 9, 12), "홍길동님이 2,000,000원을 보냈어요")
        kbSays(at(11, 25, 9, 13), "이체 출금 2,000,000원 홍길동 잔액 100,000원")
        val q = asks().single()
        val g = AskGuess.decode(q.guess)!!
        assertEquals(AskKind.TRANSFER, g.kind)
        assertEquals(salaryAcc, g.from)
        assertEquals(living, g.to)
        yes(q)
        assertEquals(1, entries().size)
        val d = LocalDate.of(2026, 11, 25)
        assertEquals(-1_900_000L, balance(salaryAcc, d))
        assertEquals(2_050_000L, balance(living, d))
    }

    @Test
    fun `나간 쪽에 먼저 답하면 나중에 온 들어온 알림은 묻지 않고 그 이체에 합친다`() = runTest {
        kbSays(at(11, 25, 9, 12), "이체 출금 2,000,000원 홍길동 잔액 100,000원")
        yes(asks().single(), remember = false)
        assertEquals(1, entries().single().moveState)
        tossSays(at(11, 25, 9, 20), "홍길동님이 2,000,000원을 보냈어요")
        assertTrue(asks().isEmpty())
        assertEquals(3, entries().single().moveState)
        assertEquals(2_050_000L, balance(living, LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun `계획과 다른 금액을 옮기면 짐작이 달라도 골라서 이체로 적을 수 있다`() = runTest {
        kbSays(at(11, 25, 9, 12), "이체 출금 1,234,000원 홍길동 잔액 100,000원")
        val q = asks().single()
        val choice = com.simbio.don.app.ui.askChoices(q, AskGuess.decode(q.guess)!!, db.dao().accountsNow())
            .firstOrNull { it.second.kind == AskKind.TRANSFER && it.second.to == living }
        assertTrue(choice != null || AskGuess.decode(q.guess)!!.to == living)
        repo.answerAsk(q.id, choice?.second ?: AskGuess.decode(q.guess)!!, remember = false)
        val e = entries().single()
        assertEquals(EntryType.TRANSFER.name, e.type)
        assertEquals(salaryAcc, e.accountId)
        assertEquals(living, e.toAccountId)
        // 이체라 생활비 지출에 안 들어간다
        assertEquals(0L, home(at(11, 25, 20)).monthLiving)
    }

    @Test
    fun `비서 점검 — 한쪽만 온 이체를 찾고, AI에 보낼 요약엔 이름·알림 글이 없다`() = runTest {
        kbSays(at(11, 20, 9), "이체 출금 300,000원 홍길동 잔액 100,000원")
        repo.answerAsk(asks().single().id, AskGuess(AskKind.TRANSFER, from = salaryAcc, to = living), remember = true)
        kbSays(at(11, 21, 9), "입금 55,000원 김철수 잔액 155,000원")
        val f = repo.findings(LocalDate.of(2026, 11, 25)).first()
        assertTrue(f.any { it.kind == com.simbio.don.app.data.Finding.Kind.HALF_MOVE && it.count == 1 })
        assertTrue(f.any { it.kind == com.simbio.don.app.data.Finding.Kind.ASK && it.count == 1 })
        val text = com.simbio.don.app.data.AssistantCheck.anonSummary(
            f, asks(), repo.notifRules.first(), repo.moveRules.first(), db.dao().accountsNow(), true, 0, 0, 2_000_000,
        )
        listOf("홍길동", "김철수", "KB", "토스", "월급 통장", "생활비 통장", "고정비 통장", "적금", "신한", "잔액 1", "보냈어요").forEach { assertTrue("요약에 '$it'이 들어갔어요:\n$text", !text.contains(it)) }
        assertTrue(text.contains("한쪽 알림만 온 이체 1건"))
        assertTrue(!text.contains("짝지음"))
    }
}

