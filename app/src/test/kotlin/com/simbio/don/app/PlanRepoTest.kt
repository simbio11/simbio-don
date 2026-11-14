package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
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

/** 0.14: 돈 나갈 계획 — 실제와 따로, 예측·일주일 안에 들어가고, 쓰면 실제와 견준다 */
@RunWith(RobolectricTestRunner::class)
class PlanRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val today = LocalDate.of(2026, 10, 23)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun at(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 12, 0)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, today)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun home(now: LocalDateTime) = repo.home(flowOf(now)).first()!!
    private fun plan(day: Int, amount: Long, label: String, c: Category) =
        PlanEntity(epochDay = d(10, day).toEpochDay(), amount = amount, label = label, category = c.name)

    @Test
    fun `계획은 실제 지출에 안 들어가고 예측과 일주일 안에 보인다`() = runTest {
        val gift = repo.savePlan(plan(28, 50_000, "친구 생일 선물", Category.EVENT))
        repo.savePlan(plan(30, 30_000, "병원", Category.HEALTH))
        val h = home(at(10, 26))
        assertEquals(0L, h.monthLiving)
        assertEquals(0L, h.currentWeek!!.spent)
        assertEquals(listOf("친구 생일 선물", "병원"), h.insights.upcoming.filter { it.planned }.map { it.label })
        assertEquals(80_000L, h.insights.forecast!!.plannedAhead)
        assertEquals(80_000L, h.insights.plans!!.openAmount)

        // 썼어요: 실제 기록과 잇는다 → 계획 50,000 / 실제 43,000
        val id = repo.addManual(d(10, 28), 43_000, "올리브영", EntryType.SPEND, Category.EVENT)
        repo.completePlan(gift, id, 43_000, d(10, 28))
        val after = home(at(10, 28))
        assertEquals(43_000L, after.monthLiving)
        assertEquals(30_000L, after.insights.plans!!.openAmount)
        assertEquals(7_000L, after.insights.plans!!.doneDiff)
        assertEquals(30_000L, after.insights.forecast!!.plannedAhead)
    }

    @Test
    fun `비슷한 실제 지출을 찾아 주고, 안 쓰기로 하면 아낀 돈`() = runTest {
        val p = repo.savePlan(plan(27, 30_000, "병원", Category.HEALTH))
        repo.addManual(d(10, 27), 28_000, "서울정형외과", EntryType.SPEND, Category.HEALTH)
        val h = home(at(10, 27))
        val match = repo.planMatches(h.plans, h.entries)
        assertEquals("서울정형외과", match.getValue(p).label)
        repo.linkPlan(p, match.getValue(p))
        assertTrue(repo.planMatches(home(at(10, 27)).plans, h.entries).isEmpty())

        val c = repo.savePlan(plan(31, 100_000, "콘서트", Category.CULTURE))
        repo.cancelPlan(c)
        assertEquals(100_000L, home(at(10, 28)).insights.plans!!.canceledAmount)
    }

    @Test
    fun `계획과 내 이름도 백업에 담긴다`() = runTest {
        repo.savePlan(plan(29, 20_000, "택배비", Category.ETC))
        repo.saveOwnerName("홍길동")
        val json = repo.exportBackup(at(10, 26))
        repo.deletePlan(db.dao().plansNow().single().id)
        repo.saveOwnerName("")
        repo.restoreBackup(json)
        assertEquals("택배비", db.dao().plansNow().single().label)
        assertEquals("홍길동", db.dao().settingsNow()!!.ownerName)
    }

    @Test
    fun `경조사는 한 달에 나눠서 — 남은 주에 조금씩 빼고, 쓴 돈은 그 주 지출로 다시 안 센다`() = runTest {
        // 0.31: 주마다 쓸 수 있는 돈 = 남은 생활비 ÷ 남은 날. 마지막 주의 남은 돈 = 한 달 남은 생활비
        fun can(r: com.simbio.don.budget.WeekResult) = r.budget + r.carryIn
        val before = home(at(10, 23)).results
        val id = repo.savePlan(plan(30, 100_000, "지수 결혼식", Category.EVENT).copy(createdEpochDay = d(10, 23).toEpochDay()))
        assertTrue(repo.planNow(id)!!.monthly)
        val h = home(at(10, 23))
        // 한 달 남은 생활비에서 미리 떼어 둔다
        assertEquals(100_000L, before.last().available - h.results.last().available)
        // 결혼식 주만 10만 원 깎이지 않는다 (만든 주부터 나눠서)
        val weddingWeek = h.plan.weekIndexOf(d(10, 30))!!
        assertTrue(can(before[weddingWeek]) - can(h.results[weddingWeek]) in 1 until 100_000)
        // 하루 몫에서 미리 떼지도 않는다
        assertEquals(0L, h.day.reserved)

        // 축의금을 내고 계획과 이으면 그 주 지출로 다시 안 센다
        val e = repo.addManual(d(10, 30), 100_000, "축의금", com.simbio.don.budget.EntryType.SPEND, Category.EVENT)
        repo.completePlan(id, e, 100_000, d(10, 30))
        val h2 = home(at(10, 30))
        assertEquals(0L, h2.results[weddingWeek].spent)
        assertEquals(100_000L, before.last().available - h2.results.last().available)

        // 그 주 예산에서로 바꾸면 그 주 지출로
        repo.setPlanMonthly(id, false)
        val h3 = home(at(10, 30))
        assertEquals(100_000L, h3.results[weddingWeek].spent)
        assertEquals(100_000L, before.last().available - h3.results.last().available)
    }
}
