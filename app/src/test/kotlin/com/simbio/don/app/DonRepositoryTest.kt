package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.game.ItemKind
import com.simbio.don.game.Slot
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import com.simbio.don.notify.ReviewReason
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

@RunWith(RobolectricTestRunner::class)
class DonRepositoryTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository

    // 2026-10-23(금) ~ 11/24, 주 예산 600,000 / 420,000 / 420,000 / 540,000
    private val today = LocalDate.of(2026, 10, 23) // 월급날(10/25 일요일 → 10/23 금요일)에 시작
    private fun at(month: Int, day: Int, h: Int = 12, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, month, day, h, m, s)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao())
        repo.completeOnboarding(
            salary = 3_300_000,
            payDay = 25,
            fixed = listOf(FixedExpense("월세", 1_000_000)),
            savings = 320_000,
            settlementDay = DayOfWeek.MONDAY,
            today = today,
        )
        repo.saveRule(AppRule("card.app", "OO카드"))
        repo.saveRule(AppRule("pay.app", "OO페이"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(now: LocalDateTime) = repo.home(flowOf(now)).first()!!

    @Test
    fun `초기 설정으로 이번 주기와 주 예산이 만들어진다`() = runTest {
        val h = home(at(10, 27))
        assertEquals(LocalDate.of(2026, 10, 23), h.cycle.start)
        assertEquals(listOf(600_000L, 420_000, 420_000, 540_000), h.plan.weeks.map { it.budget })
        assertEquals(600_000, h.currentWeek!!.available)
    }

    @Test
    fun `한 달 중간에 시작하면 오늘부터 남은 날 몫만 예산으로 잡는다`() = runTest {
        db.clearAllTables()
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 320_000, DayOfWeek.MONDAY, LocalDate.of(2026, 11, 10))
        val h = home(at(11, 10))
        assertEquals(LocalDate.of(2026, 11, 10), h.cycle.start)
        assertEquals(900_000, h.plan.disposable) // 1,980,000 × 15/33
        assertEquals(0, h.savedSoFar)
        assertTrue(h.settleable.isEmpty())

        db.clearAllTables()
        repo.completeOnboarding(3_300_000, 25, emptyList(), 0, DayOfWeek.MONDAY, LocalDate.of(2026, 11, 10), firstCycleBudget = 500_000)
        assertEquals(500_000, home(at(11, 10)).plan.disposable)
    }

    @Test
    fun `알림 기록은 가게 이름을 뽑아 카테고리를 정하고, 고치면 기억한다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "OO카드 승인", "OO카드 승인 4,500원 10/27 08:10 스타벅스강남", at(10, 27, 8, 10)))
        val e = db.dao().allEntriesNow().single()
        assertEquals("스타벅스강남", e.label)
        assertEquals(Category.CAFE.name, e.category)

        repo.addManual(LocalDate.of(2026, 10, 28), 6_000, "동네빵집", EntryType.SPEND)
        val bread = db.dao().allEntriesNow().first { it.label == "동네빵집" }
        assertEquals(Category.ETC.name, bread.category)
        repo.addManual(LocalDate.of(2026, 10, 29), 7_000, "동네빵집", EntryType.SPEND)

        // 하나를 고치면 같은 가게 기록도 같이 바뀌고, 다음부터 그렇게 분류한다
        repo.updateEntry(bread.copy(category = Category.CAFE.name), bread, applyToSameLabel = true)
        assertTrue(db.dao().allEntriesNow().filter { it.label == "동네빵집" }.all { it.category == Category.CAFE.name })
        repo.addManual(LocalDate.of(2026, 10, 30), 5_000, "동네 빵집", EntryType.SPEND)
        assertEquals(Category.CAFE.name, db.dao().allEntriesNow().last().category)
    }

    @Test
    fun `달별 보기는 다음 주기 시작 전날까지이고 지난달 기록을 함께 준다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 10_000, "스타벅스", EntryType.SPEND)
        repo.confirmSalary(3_300_000, LocalDate.of(2026, 11, 24), at(11, 24, 10)) // 하루 일찍 입금
        repo.addManual(LocalDate.of(2026, 11, 25), 20_000, "치킨", EntryType.SPEND)

        val oct = repo.month("2026-10", LocalDate.of(2026, 11, 25)).first()!!
        assertEquals(LocalDate.of(2026, 11, 23), oct.cycle.end)
        assertEquals(1, oct.entries.size)
        val nov = repo.month("2026-11", LocalDate.of(2026, 11, 25)).first()!!
        assertTrue(nov.isCurrent)
        assertEquals(listOf(20_000L), nov.entries.map { it.amount })
        assertEquals(listOf(10_000L), nov.previousEntries.map { it.amount })
    }

    @Test
    fun `CSV로 내보낸다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 10_000, "카페 \"별\"", EntryType.SPEND, memo = "친구, 둘")
        val csv = repo.exportCsv()
        assertTrue(csv.startsWith("\uFEFF날짜,구분,카테고리,내용,금액,메모,기록 방식"))
        assertTrue(csv.contains("2026-10-24,지출,카페·간식,\"카페 \"\"별\"\"\",10000,\"친구, 둘\",직접 입력"))
    }

    @Test
    fun `모의 구매는 지갑에서 빠지고, 예산을 지키면 집에 도착한다`() = runTest {
        repo.buyMock("나이키 에어포스1", 129_000, ItemKind.SHOES, "쿠팡", LocalDate.of(2026, 10, 27), at(10, 27))
        repo.buyMock("황금올리브 치킨", 23_000, ItemKind.MEAL, null, LocalDate.of(2026, 10, 28), at(10, 28))
        val before = home(at(10, 28, 20))
        assertEquals(1_980_000 - 152_000L, before.wallet)
        assertEquals(2, before.shipping.size)
        assertEquals(600_000, before.currentWeek!!.available) // 실제 예산은 그대로

        val noon = home(at(11, 2, 12))
        repo.settle(noon.settleable.single(), at(11, 2, 12), emptySet())
        val after = home(at(11, 2, 13))
        assertEquals(1_980_000L, after.wallet) // 결산 때 돌려받음
        assertEquals(1, after.house.slots[Slot.SHOE_RACK]!!.size)
        assertEquals(1, after.house.trash.size)
        assertEquals(152_000, after.house.wouldHaveCost)

        repo.recycle()
        assertTrue(home(at(11, 2, 14)).house.trash.isEmpty())
    }

    @Test
    fun `예산을 넘기면 고른 물건이 반품되고, 안 고르면 나중에 산 것부터`() = runTest {
        repo.buyMock("운동화", 129_000, ItemKind.SHOES, null, LocalDate.of(2026, 10, 24), at(10, 24))
        repo.buyMock("에어팟 케이스", 34_000, ItemKind.ELECTRONICS, null, LocalDate.of(2026, 10, 30), at(10, 30))
        repo.addManual(LocalDate.of(2026, 10, 29), 620_000, "장보기", EntryType.SPEND) // 20,000 초과

        val week = home(at(11, 2, 12)).settleable.single()
        assertEquals(20_000, week.overspent)
        repo.settle(week, at(11, 2, 12), returnIds = null)
        val items = db.dao().mockPurchasesNow().associate { it.name to it.status }
        assertEquals("DELIVERED", items["운동화"])
        assertEquals("RETURNED", items["에어팟 케이스"])
    }

    @Test
    fun `결제 알림은 자동 반영, 다른 앱의 같은 금액은 중복 의심`() = runTest {
        repo.handleNotification(RawNotification("card.app", "OO카드", "승인 23,000원", at(10, 27, 19, 2)))
        repo.handleNotification(RawNotification("pay.app", "OO페이", "결제 23,000원", at(10, 27, 19, 2, 40)))
        repo.handleNotification(RawNotification("other.app", "", "결제 99,000원", at(10, 27, 19, 3)))

        val h = home(at(10, 27, 20))
        assertEquals(23_000, h.currentWeek!!.spent)
        val reviews = db.dao().reviewItemsNow()
        assertEquals(listOf(ReviewReason.POSSIBLE_DUPLICATE.name), reviews.map { it.reason })
        assertTrue(reviews.single().relatedEntryId != null)
    }

    @Test
    fun `고정비와 비슷한 출금은 확인함으로`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "출금 1,000,000원", at(10, 25)))
        assertEquals(ReviewReason.LOOKS_LIKE_FIXED_EXPENSE.name, db.dao().reviewItemsNow().single().reason)
        assertEquals(0, home(at(10, 27)).currentWeek!!.spent)
    }

    @Test
    fun `확인함이 남아 있으면 정오까지 결산을 기다리고, 정오가 지나면 남은 건을 정리해 결산한다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "OO마트 12,000원", at(10, 30))) // 미분류
        repo.addManual(LocalDate.of(2026, 10, 28), 100_000, "장보기", EntryType.SPEND)

        val morning = home(at(11, 2, 9))
        assertTrue(morning.settleable.isEmpty())
        assertTrue(morning.waitingForInbox)

        val noon = home(at(11, 2, 12))
        val week = noon.settleable.single()
        repo.settle(week, at(11, 2, 12))

        assertTrue(db.dao().reviewItemsNow().isEmpty())
        val after = home(at(11, 2, 12, 1))
        assertEquals(112_000, after.results[0].spent) // 미분류 → 지출로 반영
        assertTrue(after.settleable.isEmpty())
        assertEquals(488_000, after.currentWeek!!.carryIn)
    }

    @Test
    fun `월급을 확인하면 지난 주기를 마무리하고 새 주기를 만든다`() = runTest {
        val h = home(at(11, 25, 9))
        assertTrue(h.needsSalaryConfirm)
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,400,000원", at(11, 25, 9)))
        assertEquals(3_400_000, repo.suggestedSalary(h.nextExpectedPayday))

        repo.confirmSalary(3_400_000, LocalDate.of(2026, 11, 25), at(11, 25, 10))
        val next = home(at(11, 25, 10))
        assertEquals(LocalDate.of(2026, 11, 25), next.cycle.start)
        assertEquals(3_400_000 - 1_000_000 - 320_000L, next.plan.disposable)
        assertEquals(4, db.dao().settledWeeksNow("2026-10").size)
    }

    @Test
    fun `결산 요일 변경은 다음 주기부터`() = runTest {
        repo.updateSettings(listOf(FixedExpense("월세", 1_000_000)), 320_000, DayOfWeek.THURSDAY, applyNow = true)
        assertEquals(DayOfWeek.MONDAY, home(at(10, 27)).plan.weeks[1].start.dayOfWeek)
        repo.confirmSalary(3_300_000, LocalDate.of(2026, 11, 25), at(11, 25, 10))
        val next = home(at(11, 26))
        assertEquals(DayOfWeek.THURSDAY, next.plan.weeks[1].start.dayOfWeek)
    }
}
