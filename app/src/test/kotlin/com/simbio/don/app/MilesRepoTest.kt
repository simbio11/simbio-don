package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.LookResult
import com.simbio.don.app.data.SettledWeekEntity
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.game.CardGrade
import com.simbio.don.game.CharacterLook
import com.simbio.don.game.HouseLevel
import com.simbio.don.game.ItemKind
import com.simbio.don.game.PurchaseStatus
import com.simbio.don.game.Shop
import com.simbio.don.game.Slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

/** 0.11 M2: 마일 적립·소급·키우기, 가구점·이사·인테리어, 옷장·뷰티샵, 카드 반짝이 */
@RunWith(RobolectricTestRunner::class)
class MilesRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository

    // 2026-10-23(금) 시작, 첫 주 10/23~11/1 (10일, 주 예산 600,000), 결산 요일 월요일
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(month: Int, day: Int, h: Int = 12) = LocalDateTime.of(2026, month, day, h, 0)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 320_000, DayOfWeek.MONDAY, today)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(now: LocalDateTime) = repo.home(flowOf(now)).first()!!

    /** 지갑에 마일 넣기 (결산 대신) */
    private suspend fun giveMiles(n: Long) =
        db.dao().insertMileTx(com.simbio.don.app.data.MileTxEntity(epochDay = today.toEpochDay(), amount = n, kind = "EARN", memo = "테스트"))

    @Test
    fun `결산하면 아낀 비율만큼 마일이 쌓이고 두 번 쌓이지 않는다`() = runTest {
        repo.addManual(d(10, 24), 450_000, "장보기", EntryType.SPEND) // 600,000 중 150,000 아낌 = 25%
        val week = home(at(11, 2)).settleable.single()
        repo.settle(week, at(11, 2), emptySet())
        repo.settle(week, at(11, 2), emptySet())
        val m = home(at(11, 2)).miles
        // 25% → 2,500마일, 10일짜리 주라 ×10/7
        assertEquals(2_500L * 10 / 7, m.wallet)
        assertEquals(1, m.history.size)
        assertEquals("EARN", m.history.single().kind)
    }

    @Test
    fun `예산을 넘긴 주는 마일이 없고 연속이 끊긴다`() = runTest {
        repo.addManual(d(10, 24), 620_000, "장보기", EntryType.SPEND)
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), null)
        assertEquals(0L, home(at(11, 2)).miles.wallet)
    }

    @Test
    fun `마일이 생기기 전에 결산한 주도 한 번 적립해 준다`() = runTest {
        val dao = db.dao()
        dao.markSettled(SettledWeekEntity("2026-09", 0, 0, false, 500_000, 400_000, 2_000, 0))
        dao.markSettled(SettledWeekEntity("2026-09", 1, 0, false, 500_000, 450_000, 1_000, 0))
        dao.markSettled(SettledWeekEntity("2026-09", 2, 0, false, -1, 0, -1, 0)) // 모름
        repo.dailyUpkeep(at(10, 24))
        repo.dailyUpkeep(at(10, 25))
        // 2,000 + (1,000 + 연속 2주 보너스 10%)
        assertEquals(2_000L + 1_000 + 100, home(at(10, 25)).miles.wallet)
    }

    @Test
    fun `마일 키우기는 한 주마다 0점5%씩 붙는다`() = runTest {
        giveMiles(10_000)
        repo.dailyUpkeep(at(10, 23))
        assertTrue(repo.depositMiles(10_000, today))
        assertFalse(repo.depositMiles(1, today))
        repo.dailyUpkeep(at(11, 6)) // 2주
        val m = home(at(11, 6)).miles
        assertEquals(0L, m.wallet)
        assertEquals(10_100L, m.grow)
        assertTrue(repo.withdrawMiles(100, d(11, 6)))
        assertEquals(100L, home(at(11, 6)).miles.wallet)
        assertFalse(repo.withdrawMiles(1_000_000, d(11, 6)))
    }

    @Test
    fun `이사·수납 가구·인테리어는 마일로 산다`() = runTest {
        assertFalse(repo.moveHouse(HouseLevel.TWO_ROOM, today))
        giveMiles(20_000)
        assertTrue(repo.moveHouse(HouseLevel.TWO_ROOM, today))
        assertFalse(repo.moveHouse(HouseLevel.ONE_ROOM, today)) // 작은 집으로는 안 감
        assertTrue(repo.buyStorage(Slot.WARDROBE, today))
        val h = home(at(10, 24))
        assertEquals(HouseLevel.TWO_ROOM, h.house.level)
        assertEquals(HouseLevel.TWO_ROOM.capacity.values.sum() + 3, h.house.capacity)
        assertEquals(20_000L - 15_000 - 800, h.miles.wallet)
        // 기본 색은 공짜, 새 색은 한 번 사면 다시 골라도 공짜
        assertTrue(repo.chooseInterior(Shop.Interior.WALL, "민트", isDefault = false, today = today))
        assertTrue(repo.chooseInterior(Shop.Interior.WALL, "크림", isDefault = true, today = today))
        assertTrue(repo.chooseInterior(Shop.Interior.WALL, "민트", isDefault = false, today = today))
        val after = home(at(10, 24))
        assertEquals(20_000L - 15_000 - 800 - 1_500, after.miles.wallet)
        assertEquals("민트", after.setup.interior[Shop.Interior.WALL])
    }

    @Test
    fun `첫 꾸미기는 공짜, 다음부터 뷰티샵은 모의 지갑 예약이나 마일`() = runTest {
        val first = CharacterLook.MALE.copy(eyes = "big")
        assertEquals(LookResult.SAVED_FREE, repo.applyLook(first, payWithMiles = false, now = at(10, 24)))
        assertFalse(home(at(10, 24)).freeMakeover)

        // 마일이 모자라면 안 바뀐다
        val nose = first.copy(nose = "high")
        assertEquals(LookResult.NOT_ENOUGH_MILES, repo.applyLook(nose, payWithMiles = true, now = at(10, 24)))
        giveMiles(5_000)
        assertEquals(LookResult.SAVED, repo.applyLook(nose, payWithMiles = true, now = at(10, 24)))
        assertEquals(5_000L - 5_000, home(at(10, 24)).miles.wallet) // 500,000원 = 5,000마일
        assertEquals("high", home(at(10, 24)).look.nose)

        // 모의 지갑으로 예약: 옷은 바로, 얼굴은 결산 때
        val next = nose.copy(mouth = "cat", topColor = "빨강")
        assertEquals(LookResult.BOOKED, repo.applyLook(next, payWithMiles = false, now = at(10, 25)))
        val booked = home(at(10, 25))
        assertEquals("basic", booked.look.mouth)
        assertEquals("빨강", booked.look.topColor)
        assertEquals(300_000L, booked.shipping.single().price)
        assertTrue(booked.house.itemCount == 0) // 시술은 집에 안 놓인다

        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), emptySet())
        val done = home(at(11, 2))
        assertEquals("cat", done.look.mouth)
        assertEquals("빨강", done.look.topColor)
        assertEquals(PurchaseStatus.DELIVERED, done.mockItems.single().status)
    }

    @Test
    fun `예산을 넘겨 반품된 시술은 적용되지 않는다`() = runTest {
        repo.applyLook(CharacterLook.MALE.copy(eyes = "big"), payWithMiles = false, now = at(10, 24)) // 공짜 한 번
        assertEquals(LookResult.BOOKED, repo.applyLook(CharacterLook.MALE.copy(eyes = "big", nose = "high"), payWithMiles = false, now = at(10, 24)))
        repo.addManual(d(10, 25), 700_000, "여행", EntryType.SPEND) // 100,000 초과
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), null)
        val h = home(at(11, 2))
        assertEquals("dot", h.look.nose)
        assertEquals(PurchaseStatus.RETURNED, h.mockItems.single().status)
    }

    @Test
    fun `옷장에 없는 옷은 마일로 사서 입는다`() = runTest {
        val knit = CharacterLook.MALE.copy(top = "knit")
        assertEquals(LookResult.NOT_ENOUGH_MILES, repo.applyLook(knit, payWithMiles = true, now = at(10, 24)))
        giveMiles(600)
        assertEquals(LookResult.SAVED, repo.applyLook(knit, payWithMiles = true, now = at(10, 24)))
        val h = home(at(10, 24))
        assertTrue("top:knit" in h.owned)
        assertEquals(0L, h.miles.wallet)
        assertTrue(h.freeMakeover) // 옷만 바꾼 건 첫 꾸미기를 쓰지 않는다
    }

    @Test
    fun `도착한 물건 카드는 그 주 아낀 비율로 반짝인다`() = runTest {
        repo.buyMock("운동화", 129_000, ItemKind.SHOES, null, d(10, 24), at(10, 24))
        repo.addManual(d(10, 24), 300_000, "장보기", EntryType.SPEND) // 50% 아낌
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), emptySet())
        assertEquals(CardGrade.LEGEND, home(at(11, 2)).mockItems.single().grade)
    }
}
