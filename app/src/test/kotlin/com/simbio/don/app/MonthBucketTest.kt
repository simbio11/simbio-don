package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
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

/** 0.37: "한 달 예산에서" 고른 지출은 그 주에 몰아 깎지 않고 이번 달 남은 날 모두에 나눠 뺀다 */
@RunWith(RobolectricTestRunner::class)
class MonthBucketTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val start = LocalDate.of(2026, 11, 25)
    private val now = LocalDateTime.of(2026, 11, 26, 20, 0)
    private val card = "com.shcard.smartpay"

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_000_000, 25, emptyList(), 0, DayOfWeek.MONDAY, start)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home() = repo.home(flowOf(now)).first()!!

    @Test
    fun `한 달 예산에서 - 이번 주는 조금만, 남은 주도 같이 줄고, 쓴 돈에는 그대로 들어간다`() = runTest {
        val before = home()
        val weekBefore = before.currentWeek!!.available
        val nextBefore = before.results[1].let { it.budget + it.carryIn }
        val id = repo.addManual(LocalDate.of(2026, 11, 26), 300_000, "세탁기", EntryType.SPEND, Category.ETC, "", null, null, 12 * 60, now, null)
        repo.setEntryBucket(id, 2)
        val after = home()
        val thisWeek = weekBefore - after.currentWeek!!.available
        val next = nextBefore - after.results[1].let { it.budget + it.carryIn }
        // 이번 주는 30만 원을 다 깎지 않고 남은 날 비율만큼만
        assertTrue("이번 주 $thisWeek", thisWeek in 1 until 300_000)
        // 다음 주도 줄었다
        assertTrue("다음 주 $next", next > 0)
        // 한 달 쓴 돈에는 그대로
        assertEquals(300_000L, after.monthLiving)
        // 오늘 몫에서도 빠지지 않는다 (큰 지출로도 안 센다)
        assertEquals(0L, after.day.spentDaily)

        // 비교: 앱이 판단(큰 지출)이면 이번 주에서 다 깎인다
        repo.setEntryBucket(id, null)
        assertEquals(300_000L, weekBefore - home().currentWeek!!.available)
    }

    @Test
    fun `주유비는 저절로 한 달 예산에서 - 가계부·쓴 돈엔 있고 달력·무지출·이대로 쓰면엔 안 흔들린다`() = runTest {
        repo.saveRule(com.simbio.don.notify.AppRule(card, "신한 SOL페이"))
        repo.handleNotification(com.simbio.don.notify.RawNotification(card, "신한카드(1234)승인", "홍*동 80,000원(일시불)11/26 08:10 SK에너지 강남주유소", LocalDateTime.of(2026, 11, 26, 8, 10)))
        val e = db.dao().allEntriesNow().single()
        assertEquals(2, e.bucket)
        val h = home()
        // 가계부·한 달 쓴 돈에는 그대로
        assertEquals(80_000L, h.monthLiving)
        // 오늘 몫·이번 주에 몰아 깎지 않는다
        assertEquals(0L, h.day.spentDaily)
        assertTrue(h.currentWeek!!.spent == 0L)
        // 무지출·이대로 쓰면 속도에서 빠진다 (예산은 그만큼 미리 줄인다)
        assertEquals(0L, h.insights.forecast!!.spent)
        assertEquals(h.plan.disposable - 80_000L, h.insights.forecast!!.budget)
    }

    @Test
    fun `한 달 예산에서를 한 번 고르면 그 가게를 기억하고, 오늘 몫으로 바꾸면 잊는다`() = runTest {
        val id = repo.addManual(LocalDate.of(2026, 11, 26), 450_000, "하이마트", EntryType.SPEND, Category.ETC, "", null, null, 12 * 60, now, null)
        assertEquals(null, db.dao().entryNow(id)!!.bucket)
        repo.setEntryBucket(id, 2)
        assertTrue(repo.monthlyPlaces.first().contains("하이마트"))
        val next = repo.addManual(LocalDate.of(2026, 11, 27), 30_000, "하이마트 강남점", EntryType.SPEND, Category.ETC, "", null, null, 12 * 60, now, null)
        assertEquals(2, db.dao().entryNow(next)!!.bucket)
        // 주유소도 오늘 몫으로 고르면 그 이름을 잊는다
        val gas = repo.addManual(LocalDate.of(2026, 11, 27), 50_000, "GS칼텍스 역삼", EntryType.SPEND, Category.TRANSPORT, "", null, null, 9 * 60, now, null)
        assertEquals(2, db.dao().entryNow(gas)!!.bucket)
        repo.setEntryBucket(gas, 0)
        assertFalse(repo.monthlyPlaces.first().contains("GS칼텍스"))
        assertEquals(0, db.dao().entryNow(gas)!!.bucket)
        // 설정에서 더하면 최근 기록에도
        val tv = repo.addManual(LocalDate.of(2026, 11, 27), 900_000, "전자랜드", EntryType.SPEND, Category.ETC, "", null, null, 9 * 60, now, null)
        repo.setMonthlyPlaces(repo.monthlyPlaces.first() + "전자랜드", LocalDate.of(2026, 11, 27))
        assertEquals(2, db.dao().entryNow(tv)!!.bucket)
    }
}

