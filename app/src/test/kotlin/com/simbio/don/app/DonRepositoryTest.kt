package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.Backup
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.FixedExpenseEntity
import com.simbio.don.app.data.HouseEntity
import com.simbio.don.app.data.SalarySource
import com.simbio.don.app.notify.DailyReminder
import com.simbio.don.app.widget.WeekWidget
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.ItemKind
import com.simbio.don.game.Slot
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import com.simbio.don.notify.ReviewReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
        repo = DonRepository(db.dao(), db)
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
    fun `월급이 하루 일찍 들어오면 그날부터 새 달이고, 달별 보기는 다음 달 시작 전날까지`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 10_000, "스타벅스", EntryType.SPEND)
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,300,000원", at(11, 24, 10))) // 하루 일찍 입금
        repo.addManual(LocalDate.of(2026, 11, 25), 20_000, "치킨", EntryType.SPEND)

        val oct = repo.month("2026-10", LocalDate.of(2026, 11, 25)).first()!!
        assertEquals(LocalDate.of(2026, 11, 23), oct.cycle.end)
        assertEquals(1, oct.entries.size)
        val nov = repo.month("2026-11", LocalDate.of(2026, 11, 25)).first()!!
        assertTrue(nov.isCurrent)
        assertEquals(LocalDate.of(2026, 11, 24), nov.cycle.start)
        assertEquals(listOf(20_000L), nov.entries.filter { it.type == EntryType.SPEND }.map { it.amount })
        assertEquals(Category.SALARY, nov.entries.single { it.type == EntryType.INCOME }.category)
        assertEquals(listOf(10_000L), nov.previousEntries.map { it.amount })
    }

    @Test
    fun `CSV로 내보낸다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 10_000, "카페 \"별\"", EntryType.SPEND, memo = "친구, 둘")
        val csv = repo.exportCsv()
        assertTrue(csv.startsWith("\uFEFF날짜,시각,구분,카테고리,내용,금액,자산,받은 자산,메모,기록 방식"))
        assertTrue(csv, csv.contains("2026-10-24,,지출,카페·간식,\"카페 \"\"별\"\"\",10000,\"\",\"\",\"친구, 둘\",직접 입력"))
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
    fun `찜은 지갑에서 빠지지 않고, 사면 그날 산 것으로, 안 사면 참은 돈으로`() = runTest {
        repo.wishMock("아이패드", 900_000, ItemKind.ELECTRONICS, null, at(10, 27, 21))
        repo.wishMock("가디건", 49_000, ItemKind.CLOTHES, null, at(10, 27, 22))
        val h = home(at(10, 28, 20))
        assertEquals(1_980_000L, h.wallet)
        assertEquals(listOf("아이패드", "가디건"), h.wishes.map { it.name })
        assertTrue(h.readyWishes.isEmpty())
        assertEquals(2, home(at(10, 28, 22)).readyWishes.size)

        repo.buyWished(h.wishes[1].id, at(10, 29, 9))
        repo.giveUpWished(h.wishes[0].id)
        val after = home(at(10, 29, 10))
        assertTrue(after.wishes.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 29), after.shipping.single().date)
        assertEquals(1_980_000L - 49_000, after.wallet)
        assertEquals(900_000L, after.gaveUpTotal)
    }

    @Test
    fun `지운 기록은 되살릴 수 있고, 자주 쓰는 곳은 횟수 순`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 4_500, "스타벅스", EntryType.SPEND)
        repo.addManual(LocalDate.of(2026, 10, 25), 5_000, "스타벅스", EntryType.SPEND)
        repo.addManual(LocalDate.of(2026, 10, 25), 9_000, "김밥천국", EntryType.SPEND)
        val picks = repo.quickPicks(LocalDate.of(2026, 10, 26))
        assertEquals(listOf("스타벅스", "김밥천국"), picks.map { it.label })
        assertEquals(5_000, picks.first().amount)
        assertEquals(Category.CAFE, picks.first().category)

        val e = home(at(10, 26)).entries.first { it.label == "김밥천국" }
        repo.deleteEntry(e)
        assertEquals(2, home(at(10, 26)).entries.size)
        repo.restoreEntry(e)
        assertEquals(3, home(at(10, 26)).entries.size)
    }

    @Test
    fun `위젯과 밤 알림은 이번 주 남은 돈을 보여준다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 12_000, "국밥", EntryType.SPEND)
        val h = home(at(10, 24, 21))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = WeekWidget.render(context, h).apply(context, android.widget.FrameLayout(context))
        assertEquals("588,000원", view.findViewById<android.widget.TextView>(R.id.widget_amount).text.toString())
        assertTrue(view.findViewById<android.widget.TextView>(R.id.widget_today).text.startsWith("오늘 남은 돈"))

        val (title, body) = DailyReminder.message(h)
        assertTrue(title, title.startsWith("오늘 몫 ") && title.endsWith("중 12,000원 썼어요"))
        assertTrue(body, body.startsWith("이번 주 남은 돈 588,000원"))
    }

    @Test
    fun `그날 쓴 식비는 오늘 몫에서, 경조사·계획은 이번 주에서 나눈다`() = runTest {
        // 첫 주 10/23(금) ~ 11/1(일), 주 예산 600,000. 10/24(토) 아침: 남은 날 9일
        val sat = LocalDate.of(2026, 10, 24)
        val before = home(at(10, 24, 8)).day
        assertEquals(9, before.daysLeft)
        assertEquals(600_000L / 9, before.allowance)
        repo.addManual(sat, 200_000, "축의금", EntryType.SPEND, com.simbio.don.budget.Category.EVENT)
        repo.addManual(sat, 25_000, "술", EntryType.SPEND, com.simbio.don.budget.Category.FOOD)
        repo.savePlan(com.simbio.don.app.data.PlanEntity(epochDay = sat.plusDays(1).toEpochDay(), amount = 100_000, label = "엄마 생신", category = "EVENT"))
        val d = home(at(10, 24, 21)).day
        // (600,000 − 축의금 200,000 − 내일 계획 100,000) ÷ 9일 — 술은 오늘 몫에서
        assertEquals(300_000L / 9, d.allowance)
        assertEquals(25_000L, d.spentDaily)
        assertEquals(200_000L, d.spentBig)
        assertEquals(100_000L, d.reserved)
        assertEquals(300_000L / 9 - 25_000, d.left)
        // 2차까지 가서 오늘 몫을 넘음 — 오늘 몫은 그대로, 넘은 만큼 표시
        repo.addManual(sat, 20_000, "2차", EntryType.SPEND, com.simbio.don.budget.Category.FOOD)
        val over = home(at(10, 24, 23)).day
        assertTrue(over.over)
        assertEquals(300_000L / 9, over.allowance)
        assertEquals(300_000L / 9 - 45_000, over.left)
        // 분류를 바꾸면 (경조사도 그날 쓰는 돈으로) 다시 센다
        repo.setDailyCategories(com.simbio.don.budget.DayBudgets.DEFAULT_DAILY + "EVENT")
        val all = home(at(10, 24, 23)).day
        assertEquals(245_000L, all.spentDaily)
        assertEquals(0L, all.spentBig)
    }

    @Test
    fun `예산을 넘기면 고른 물건이 반품되고, 안 고르면 나중에 산 것부터`() = runTest {
        repo.buyMock("운동화", 129_000, ItemKind.SHOES, null, LocalDate.of(2026, 10, 24), at(10, 24))
        repo.buyMock("에어팟 케이스", 34_000, ItemKind.ELECTRONICS, null, LocalDate.of(2026, 10, 30), at(10, 30))
        repo.addManual(LocalDate.of(2026, 10, 29), 620_000, "장보기", EntryType.SPEND) // 20,000 초과

        val week = home(at(11, 2, 12)).settleable.single()
        assertEquals(20_000, week.result.overspent)
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
    fun `두 앱 알림이 동시에 와도 한 번만 기록한다`() = runTest {
        repeat(5) { i ->
            val amount = 10_000L + i * 1_000
            withContext(Dispatchers.IO) {
                listOf(
                    async { repo.handleNotification(RawNotification("card.app", "OO카드", "승인 ${"%,d".format(amount)}원", at(10, 27, 10 + i, 2))) },
                    async { repo.handleNotification(RawNotification("pay.app", "OO페이", "결제 ${"%,d".format(amount)}원", at(10, 27, 10 + i, 2, 1))) },
                ).awaitAll()
            }
        }
        assertEquals((0 until 5).sumOf { 10_000L + it * 1_000 }, home(at(10, 27, 20)).currentWeek!!.spent)
        assertEquals(5, db.dao().reviewItemsNow().count { it.reason == ReviewReason.POSSIBLE_DUPLICATE.name })
    }

    @Test
    fun `같은 앱이 같은 알림을 다시 올리면 무시`() = runTest {
        repo.handleNotification(RawNotification("card.app", "OO카드", "승인 7,000원", at(10, 27, 19)))
        repo.handleNotification(RawNotification("card.app", "OO카드", "승인 7,000원 (갱신)", at(10, 27, 19)))
        assertEquals(7_000, home(at(10, 27, 20)).currentWeek!!.spent)
        assertTrue(db.dao().reviewItemsNow().isEmpty())
    }

    @Test
    fun `키워드 없는 알림·환불도 다른 앱 짝이면 중복으로, 정오 결산에서 한 번만 반영`() = runTest {
        repo.handleNotification(RawNotification("pay.app", "OO페이", "결제 23,000원", at(10, 27, 19, 2)))
        repo.handleNotification(RawNotification("card.app", "", "OO치킨 23,000원", at(10, 27, 19, 3)))
        repo.handleNotification(RawNotification("card.app", "", "승인취소 9,000원", at(10, 28, 12)))
        repo.handleNotification(RawNotification("pay.app", "", "결제 취소 9,000원", at(10, 28, 12, 0, 30)))
        assertEquals(
            listOf(ReviewReason.POSSIBLE_DUPLICATE, ReviewReason.REFUND, ReviewReason.POSSIBLE_DUPLICATE).map { it.name },
            db.dao().reviewItemsNow().sortedBy { it.postedAtEpochSecond }.map { it.reason },
        )
        val noon = home(at(11, 2, 12))
        repo.settle(noon.settleable.single(), at(11, 2, 12))
        assertEquals(14_000, home(at(11, 2, 13)).results[0].spent)
    }

    @Test
    fun `결산을 두 번 눌러도 한 번만`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "OO마트 12,000원", at(10, 30))) // 미분류
        val week = home(at(11, 2, 12)).settleable.single()
        withContext(Dispatchers.IO) {
            listOf(async { repo.settle(week, at(11, 2, 12)) }, async { repo.settle(week, at(11, 2, 12)) }).awaitAll()
        }
        assertEquals(12_000, home(at(11, 2, 13)).results[0].spent)
        assertEquals(1, db.dao().settledWeeksNow("2026-10").size)
    }

    @Test
    fun `월급날이 되면 저절로 새 달이고, 늦게 들어온 월급으로 금액을 맞춘다`() = runTest {
        repo.addManual(LocalDate.of(2026, 11, 24), 10_000, "국밥", EntryType.SPEND)
        repo.buyMock("모자", 50_000, ItemKind.CLOTHES, null, LocalDate.of(2026, 11, 24), at(11, 24))
        // 월급날 아침: 입금은 아직 — 지난달 월급으로 새 달. 여러 번 불러도 새 달은 하나
        repo.dailyUpkeep(at(11, 25, 0, 5))
        repo.dailyUpkeep(at(11, 25, 9))
        var h = home(at(11, 25, 9))
        assertEquals(LocalDate.of(2026, 11, 25), h.cycle.start)
        assertEquals(SalarySource.ESTIMATED, h.salarySource)
        assertEquals(3_300_000, h.salaryNotice!!.salary)
        assertEquals(2, repo.cycles.first().size)
        // 지난달 마지막 주는 결산 화면으로 남고, 그 전 주들은 자동 결산
        assertEquals(3, db.dao().settledWeeksNow("2026-10").size)
        val last = h.settleable.single()
        assertEquals("2026-10", last.nominal)
        assertEquals(LocalDate.of(2026, 11, 24), last.result.week.end)
        assertEquals(1, h.shipping.size) // 모자는 마지막 주 결산 때 도착
        repo.settle(last, at(11, 25, 12), emptySet())
        assertEquals(4, db.dao().settledWeeksNow("2026-10").size)
        assertTrue(home(at(11, 25, 12)).shipping.isEmpty())

        // 다음 날 월급이 들어오면 그 금액으로 다시 짠다
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,500,000원", at(11, 26, 10)))
        h = home(at(11, 26, 11))
        assertEquals(LocalDate.of(2026, 11, 25), h.cycle.start)
        assertEquals(SalarySource.NOTIFICATION, h.salarySource)
        assertEquals(3_500_000 - 1_000_000 - 320_000L, h.plan.disposable)
        val oct = repo.month("2026-10", LocalDate.of(2026, 11, 26)).first()!!
        assertEquals(LocalDate.of(2026, 11, 24), oct.cycle.end)
        assertTrue(oct.entities.any { it.label == "국밥" })
        repo.dismissSalaryNotice()
        assertEquals(null, home(at(11, 26, 11)).salaryNotice)
    }

    @Test
    fun `몇 달 안 켜도 달마다 차례로 새 달이 생긴다`() = runTest {
        repo.dailyUpkeep(at(1, 27).plusYears(1))
        val cycles = repo.cycles.first()
        assertEquals(listOf("2027-01", "2026-12", "2026-11", "2026-10"), cycles.map { it.nominal })
        assertTrue(cycles.take(3).all { it.salarySource == SalarySource.ESTIMATED.name })
    }

    @Test
    fun `월급을 직접 고치면 이번 달 예산이 바뀌고 알림으로 덮지 않는다`() = runTest {
        repo.dailyUpkeep(at(11, 25, 1))
        repo.updateSalary(3_000_000)
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,500,000원", at(11, 25, 10)))
        val h = home(at(11, 25, 11))
        assertEquals(3_000_000, h.salary)
        assertEquals(SalarySource.MANUAL, h.salarySource)
    }

    @Test
    fun `한 달 중간에 시작한 첫 달도 저축을 바꾸면 남은 날 몫만큼 예산에 반영`() = runTest {
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 320_000, DayOfWeek.MONDAY, LocalDate.of(2026, 11, 10))
        assertEquals(900_000, home(at(11, 10)).plan.disposable)
        repo.updateSettings(listOf(FixedExpenseEntity(name = "월세", amount = 1_000_000)), 900_000, DayOfWeek.MONDAY, applyNow = true)
        assertEquals(637_000, home(at(11, 10)).plan.disposable)
    }

    @Test
    fun `수입은 가계부에만 적고 주 예산·지갑은 그대로`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 150_000, "중고거래", EntryType.INCOME)
        repo.handleNotification(RawNotification("card.app", "OO은행", "급여 입금 3,300,000원", at(10, 25, 9)))
        repo.handleNotification(RawNotification("pay.app", "OO페이", "입금 3,300,000원", at(10, 25, 9, 0, 30)))
        val h = home(at(10, 25, 10))
        assertEquals(3_450_000L, h.monthIncome)
        assertEquals(0, h.currentWeek!!.spent)
        assertEquals(1_980_000L, h.wallet)
        val incomes = h.entries.filter { it.type == EntryType.INCOME.name }
        assertEquals(2, incomes.size)
        assertEquals(Category.SALARY.name, incomes.first { it.amount == 3_300_000L }.category)
        assertEquals(Category.OTHER_INCOME.name, incomes.first { it.amount == 150_000L }.category)
    }

    @Test
    fun `모은 돈은 저축과 목표와 아낀 돈으로 나누고, 목표는 넣은 만큼만 차고, 연속 기록을 센다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 100_000, "장보기", EntryType.SPEND)
        repo.addGoal("이어폰", 300_000, "COMPUTER", today)
        repo.addGoal("여행", 1_000_000, "FLIGHT", today)
        val week = home(at(11, 2, 12)).settleable.single()
        repo.settle(week, at(11, 2, 12), emptySet())
        val h = home(at(11, 2, 13))
        // 저축(먼저 뗀 320,000, 통장이 없어 어림) + 첫 주 아낀 500,000 — 목표에는 저절로 안 들어간다
        assertEquals(820_000L, h.savedTotal)
        assertEquals(320_000L, h.split.savings)
        assertFalse(h.split.savingsFromAccounts)
        assertEquals(500_000L, h.split.unallocated)
        assertEquals(listOf(0L, 0L), h.goals.map { it.filled })
        assertEquals(1, h.streak)

        // 아낀 돈에서 이어폰에 300,000, 보너스로 여행에 100,000
        val (earphone, trip) = h.goals.map { it.goal.id }
        repo.putIntoGoal(earphone, 300_000, com.simbio.don.app.data.GoalSource.SAVED, LocalDate.of(2026, 11, 2))
        repo.putIntoGoal(trip, 100_000, com.simbio.don.app.data.GoalSource.EXTRA, LocalDate.of(2026, 11, 2))
        val h2 = home(at(11, 2, 14))
        assertEquals(listOf(300_000L, 100_000L), h2.goals.map { it.filled })
        assertTrue(h2.goals[0].done)
        assertEquals(400_000L, h2.split.goals)
        assertEquals(200_000L, h2.split.unallocated)
        assertEquals(920_000L, h2.savedTotal)

        // 빼면 아낀 돈에서 넣은 만큼은 아낀 돈으로 돌아가고, 목표를 지우면 넣은 돈도 돌아간다
        repo.takeFromGoal(trip, 50_000, LocalDate.of(2026, 11, 2))
        assertEquals(200_000L, home(at(11, 2, 14)).split.unallocated)
        repo.takeFromGoal(earphone, 100_000, LocalDate.of(2026, 11, 2))
        assertEquals(300_000L, home(at(11, 2, 14)).split.unallocated)
        repo.deleteGoal(db.dao().goalsNow().first { it.id == earphone })
        val h3 = home(at(11, 2, 14))
        assertEquals(500_000L, h3.split.unallocated)
        assertEquals(listOf(50_000L), h3.goals.map { it.filled })
        val settled = db.dao().settledWeeksNow("2026-10").single()
        assertEquals(100_000L, settled.spent)
        assertEquals(8_333, settled.savedBps)
    }

    @Test
    fun `카테고리 예산을 저장한다`() = runTest {
        repo.saveCategoryBudgets(mapOf(Category.CAFE to 50_000L, Category.DELIVERY to 0L))
        assertEquals(mapOf(Category.CAFE to 50_000L), home(at(10, 24)).categoryBudgets)
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
    fun `월급 입금 알림이 오면 그 금액으로 새 달을 시작한다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,400,000원", at(11, 25, 9)))
        val next = home(at(11, 25, 10))
        assertEquals(LocalDate.of(2026, 11, 25), next.cycle.start)
        assertEquals(SalarySource.NOTIFICATION, next.salarySource)
        assertEquals(3_400_000 - 1_000_000 - 320_000L, next.plan.disposable)
        assertEquals(3, db.dao().settledWeeksNow("2026-10").size)
        assertEquals(1, next.settleable.size)
    }

    @Test
    fun `월급날 전 큰 입금이라도 월급이 아니면 새 달이 아니다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "홍길동 입금 50,000원", at(11, 23, 9)))
        assertEquals(1, repo.cycles.first().size)
        assertEquals(Category.OTHER_INCOME.name, db.dao().allEntriesNow().single().category)
    }

    @Test
    fun `결산 요일 변경은 다음 주기부터`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "월세", amount = 1_000_000)), 320_000, DayOfWeek.THURSDAY, applyNow = true)
        assertEquals(DayOfWeek.MONDAY, home(at(10, 27)).plan.weeks[1].start.dayOfWeek)
        repo.dailyUpkeep(at(11, 25, 10))
        val next = home(at(11, 26))
        assertEquals(DayOfWeek.THURSDAY, next.plan.weeks[1].start.dayOfWeek)
    }

    // ── 고정 지출 ──

    @Test
    fun `나가는 날을 정한 고정 지출은 그날 가계부에 저절로 적히고 주 예산은 그대로`() = runTest {
        repo.updateSettings(
            listOf(FixedExpenseEntity(name = "월세", amount = 1_000_000, day = 1), FixedExpenseEntity(name = "통신비", amount = 55_000, day = 15)),
            320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 24),
        )
        repo.dailyUpkeep(at(11, 1, 0, 1))
        repo.dailyUpkeep(at(11, 1, 9)) // 두 번 불러도 한 번만
        val fixed = db.dao().allEntriesNow().filter { it.type == EntryType.FIXED.name }
        assertEquals(listOf("월세"), fixed.map { it.label })
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), fixed.single().epochDay)
        val h = home(at(11, 1, 10))
        assertEquals(1_000_000, h.monthSpent)
        assertEquals(0, h.monthLiving)
        assertEquals(0, h.currentWeek!!.spent)

        // 지운 고정 지출은 이번 달엔 다시 적지 않는다
        repo.deleteEntry(db.dao().allEntriesNow().single())
        repo.dailyUpkeep(at(11, 2, 9))
        assertTrue(db.dao().allEntriesNow().isEmpty())
        // 이미 지난 날짜로 새로 넣은 고정비는 이번 달은 건너뛴다
        repo.updateSettings(
            db.dao().fixedExpensesNow() + FixedExpenseEntity(name = "구독", amount = 10_000, day = 2),
            320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 11, 3),
        )
        repo.dailyUpkeep(at(11, 3, 9))
        assertTrue(db.dao().allEntriesNow().isEmpty())
        // 다음 달엔 모두 적는다
        repo.dailyUpkeep(at(12, 2, 9))
        assertEquals(setOf("월세", "구독"), db.dao().allEntriesNow().map { it.label }.toSet())
    }

    @Test
    fun `고정 지출 결제 알림은 자동으로 적어 둔 것을 실제 금액으로 고친다`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "통신비", amount = 55_000, day = 1)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 24))
        repo.dailyUpkeep(at(11, 1, 0, 1))
        repo.handleNotification(RawNotification("card.app", "", "OO카드 승인 56,100원 KT통신비", at(11, 2, 9)))
        val fixed = db.dao().allEntriesNow().single()
        assertEquals(EntryType.FIXED.name, fixed.type)
        assertEquals(56_100, fixed.amount)
        assertEquals(EntrySource.NOTIFICATION.name, fixed.source)
        assertTrue(db.dao().reviewItemsNow().isEmpty())
    }

    @Test
    fun `금액만 비슷한 결제는 나가는 날 근처일 때만 고정 지출`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "보험", amount = 80_000, day = 10)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 24))
        // 날짜가 먼 같은 금액 결제 → 확인함
        repo.handleNotification(RawNotification("card.app", "", "OO카드 승인 80,000원 레스토랑", at(10, 27, 19)))
        assertEquals(ReviewReason.LOOKS_LIKE_FIXED_EXPENSE.name, db.dao().reviewItemsNow().single().reason)
        // 나가는 날 하루 뒤 → 고정 지출로 바로
        repo.handleNotification(RawNotification("card.app", "", "출금 80,000원 OO생명", at(11, 11, 9)))
        assertEquals(EntryType.FIXED.name, db.dao().allEntriesNow().single().type)
    }

    // ── 자산 ──

    @Test
    fun `자산 잔액은 처음 잔액과 수입·지출·이체로 계산하고, 연결한 앱의 알림은 그 자산으로 적는다`() = runTest {
        val bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 500_000, sortOrder = 0))
        val cash = repo.saveAccount(AccountEntity(name = "현금", type = AccountType.CASH.name, opening = 10_000, sortOrder = 0))
        val card = repo.saveAccount(AccountEntity(name = "OO카드", type = AccountType.CARD.name, opening = 0, sortOrder = 0, packageName = "card.app"))
        repo.handleNotification(RawNotification("card.app", "OO카드 승인", "OO카드 승인 30,000원 이마트", at(10, 27, 18)))
        repo.addManual(LocalDate.of(2026, 10, 28), 50_000, "ATM", EntryType.TRANSFER, accountId = bank, toAccountId = cash)
        repo.addManual(LocalDate.of(2026, 10, 28), 8_000, "김밥", EntryType.SPEND, accountId = cash)
        repo.addManual(LocalDate.of(2026, 10, 29), 30_000, "카드값", EntryType.TRANSFER, accountId = bank, toAccountId = card)
        repo.addManual(LocalDate.of(2026, 10, 30), 100_000, "중고 판매", EntryType.INCOME, Category.SIDE_INCOME, accountId = bank)
        val b = home(at(10, 30)).accounts.associate { it.account.name to it.balance }
        assertEquals(520_000L, b["생활비 통장"])
        assertEquals(52_000L, b["현금"])
        assertEquals(0L, b["OO카드"])
        // 이체는 수입·지출이 아니다
        val h = home(at(10, 30))
        assertEquals(38_000, h.monthSpent)
        assertEquals(100_000, h.monthIncome)
        assertEquals(38_000, h.currentWeek!!.spent)

        repo.deleteAccount(db.dao().accountsNow().first { it.id == cash })
        assertTrue(db.dao().allEntriesNow().none { it.accountId == cash || it.toAccountId == cash })
    }

    @Test
    fun `전체 기간에서 내용·메모·금액으로 찾는다`() = runTest {
        repo.addManual(LocalDate.of(2026, 10, 24), 4_500, "스타벅스", EntryType.SPEND)
        repo.addManual(LocalDate.of(2026, 10, 25), 9_000, "김밥", EntryType.SPEND, memo = "스타 친구랑")
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,300,000원", at(11, 25, 9)))
        repo.addManual(LocalDate.of(2026, 11, 26), 4_500, "메가커피", EntryType.SPEND)
        assertEquals(setOf("스타벅스", "김밥"), repo.search("스타").first().map { it.label }.toSet())
        assertEquals(setOf("스타벅스", "메가커피"), repo.search("4,500원").first().map { it.label }.toSet())
    }

    @Test
    fun `백업으로 모든 기록을 되살린다`() = runTest {
        repo.saveAccount(AccountEntity(name = "통장", type = AccountType.BANK.name, opening = 100_000, sortOrder = 0))
        repo.addManual(LocalDate.of(2026, 10, 24), 4_500, "스타벅스", EntryType.SPEND, memo = "\"따옴표\" 메모", minuteOfDay = 8 * 60 + 30)
        repo.buyMock("모자", 50_000, ItemKind.CLOTHES, null, LocalDate.of(2026, 10, 24), at(10, 24))
        repo.addGoal("여행", 1_000_000, "FLIGHT", LocalDate.of(2026, 10, 24))
        val look = CharacterLook.FEMALE.copy(hairColor = "금발", hat = "ribbon")
        repo.saveLook(look)
        val json = repo.exportBackup(at(10, 24))
        val before = db.dao().allEntriesNow()

        db.clearAllTables()
        repo.completeOnboarding(2_000_000, 10, emptyList(), 0, DayOfWeek.FRIDAY, LocalDate.of(2026, 10, 24))
        repo.restoreBackup(json)
        assertEquals(before, db.dao().allEntriesNow())
        assertEquals(25, db.dao().settingsNow()!!.payDay)
        assertEquals(listOf("통장"), db.dao().accountsNow().map { it.name })
        assertEquals(1, db.dao().mockPurchasesNow().size)
        assertEquals(listOf("여행"), db.dao().goalsNow().map { it.name })
        assertEquals(look, home(at(10, 24)).look)

        // 망가진 파일은 아무것도 바꾸지 않는다
        val bad = runCatching { repo.restoreBackup("{\"format\":\"other\"}") }
        assertTrue(bad.exceptionOrNull() is Backup.BadBackup)
        assertEquals(before, db.dao().allEntriesNow())
    }

    @Test
    fun `캐릭터 모습을 저장해도 집 단계와 이름은 그대로다`() = runTest {
        assertEquals(CharacterLook.MALE, home(at(10, 24)).look)
        db.dao().saveHouse(HouseEntity(level = "TWO_ROOM", characterName = "돈돈"))
        val look = CharacterLook.MALE.copy(hair = "long", topColor = "민트", acc = "glasses")
        repo.saveLook(look)
        assertEquals(look, home(at(10, 24)).look)
        val h = db.dao().houseNow()!!
        assertEquals("TWO_ROOM", h.level)
        assertEquals("돈돈", h.characterName)
    }

    @Test
    fun `첫 설정에서 고른 성별의 기본 모습으로 시작한다`() = runTest {
        db.clearAllTables()
        repo.completeOnboarding(2_000_000, 10, emptyList(), 0, DayOfWeek.FRIDAY, LocalDate.of(2026, 10, 24), look = CharacterLook.FEMALE)
        assertEquals(CharacterLook.FEMALE, home(at(10, 24)).look)
    }

    // ── 검수 반영 ──

    @Test
    fun `월급날을 바꿔도 지난 달들 계산이 죽지 않는다`() = runTest {
        db.clearAllTables()
        repo.completeOnboarding(3_300_000, 25, emptyList(), 0, DayOfWeek.MONDAY, LocalDate.of(2026, 11, 10)) // "2026-10"이 11/10 시작
        repo.dailyUpkeep(at(11, 25, 9)) // "2026-11" 시작
        repo.updateSettings(emptyList(), 0, DayOfWeek.MONDAY, applyNow = false, payDay = 5, today = LocalDate.of(2026, 12, 1))
        val h = home(at(12, 1))
        assertEquals(5, h.payDay)
        assertEquals(2, repo.summaries(flowOf(at(12, 1))).first().size)
        repo.month("2026-10", LocalDate.of(2026, 12, 1)).first()!!
    }

    @Test
    fun `자동으로 적어 둔 고정 지출은 늦게 온 결제를 고정으로 고르면 고쳐지고, 두 번 적히지 않는다`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "통신비", amount = 55_000, day = 1)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 24))
        repo.dailyUpkeep(at(11, 1, 0, 1))
        // 납부일 6일 뒤 같은 금액 결제 → 확인함 (날짜가 멀어서)
        repo.handleNotification(RawNotification("card.app", "", "OO카드 승인 55,000원 SKT", at(11, 7, 9)))
        val item = db.dao().reviewItemsNow().single()
        repo.resolveReview(item, com.simbio.don.app.data.ReviewResolution.APPLY_FIXED)
        val fixed = db.dao().allEntriesNow().filter { it.type == EntryType.FIXED.name }
        assertEquals(1, fixed.size)
        assertEquals(EntrySource.NOTIFICATION.name, fixed.single().source)
        assertEquals(LocalDate.of(2026, 11, 7).toEpochDay(), fixed.single().epochDay)
    }

    @Test
    fun `직접 적은 고정 지출이 있으면 납부일에 또 적지 않는다`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "월세", amount = 600_000, day = 1)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 24))
        repo.addManual(LocalDate.of(2026, 10, 30), 600_000, "월세", EntryType.FIXED, Category.BILLS)
        repo.dailyUpkeep(at(11, 1, 9))
        assertEquals(1, db.dao().allEntriesNow().size)
    }

    @Test
    fun `한 달 안에 납부일이 두 번 오면 둘 다 적는다`() = runTest {
        // 10/23 ~ 11/24 주기: 24일은 10/24와 11/24
        repo.updateSettings(listOf(FixedExpenseEntity(name = "구독", amount = 10_000, day = 24)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 23))
        repo.dailyUpkeep(at(10, 24, 9))
        repo.dailyUpkeep(at(11, 24, 9))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 24)).map { it.toEpochDay() },
            db.dao().allEntriesNow().filter { it.type == EntryType.FIXED.name }.map { it.epochDay }.sorted(),
        )
    }

    @Test
    fun `월급날 새벽 고정 지출 알림은 새 달에 적힌다`() = runTest {
        repo.updateSettings(listOf(FixedExpenseEntity(name = "월세", amount = 600_000, day = 25)), 320_000, DayOfWeek.MONDAY, applyNow = true, today = LocalDate.of(2026, 10, 26))
        // 앱을 안 켠 채 월급날 새벽에 월세가 빠져나감
        repo.handleNotification(RawNotification("card.app", "", "월세 출금 600,000원", at(11, 25, 0, 30)))
        assertEquals("2026-11", repo.cycles.first().first().nominal)
        val e = db.dao().allEntriesNow().single()
        assertEquals(EntryType.FIXED.name, e.type)
        assertEquals(LocalDate.of(2026, 11, 25).toEpochDay(), e.epochDay)
    }

    @Test
    fun `월급날 전 상여는 월급으로 보지 않고, 뒤에 온 진짜 월급으로 맞춘다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "상여 입금 500,000원", at(11, 22, 9)))
        assertEquals(1, repo.cycles.first().size) // 일찍 시작하지 않음
        repo.dailyUpkeep(at(11, 25, 0, 1))
        assertEquals(SalarySource.ESTIMATED, home(at(11, 25, 1)).salarySource)
        repo.handleNotification(RawNotification("card.app", "", "급여 입금 3,400,000원", at(11, 25, 9)))
        val h = home(at(11, 25, 10))
        assertEquals(3_400_000, h.salary)
        assertEquals(SalarySource.NOTIFICATION, h.salarySource)
    }

    @Test
    fun `백업은 확인함도 담는다`() = runTest {
        repo.handleNotification(RawNotification("card.app", "", "OO마트 12,000원", at(10, 30))) // 미분류 → 확인함
        val json = repo.exportBackup(at(10, 30))
        db.clearAllTables()
        repo.completeOnboarding(2_000_000, 10, emptyList(), 0, DayOfWeek.FRIDAY, LocalDate.of(2026, 10, 24))
        repo.restoreBackup(json)
        assertEquals(12_000, db.dao().reviewItemsNow().single().amount)
    }
}
