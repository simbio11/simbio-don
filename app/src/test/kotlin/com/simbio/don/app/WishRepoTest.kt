package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.FixedExpense
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

/** 0.31: 사고 싶은 것 7일 참기 — 7일 뒤 한 번만 알리고, 안 사면 아낀 돈, 백업에도 남는다 */
@RunWith(RobolectricTestRunner::class)
class WishRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 14)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_000_000, 25, listOf(FixedExpense("월세", 600_000)), 200_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `7일 뒤에 한 번만 알리고, 안 사면 참은 것으로`() = runTest {
        val id = repo.addWish("운동화", 89_000, today = d)
        assertTrue(repo.takeDueWishes(d.plusDays(6)).isEmpty())
        assertEquals(listOf("운동화"), repo.takeDueWishes(d.plusDays(7)).map { it.name })
        // 다시 깨워도 또 알리지 않는다
        assertTrue(repo.takeDueWishes(d.plusDays(8)).isEmpty())
        repo.skipWish(id, d.plusDays(8))
        val w = repo.wishesNow().single()
        assertEquals("SKIPPED", w.status)
        // 백업에 들어간다
        val json = repo.exportBackup(java.time.LocalDateTime.of(2026, 11, 22, 9, 0))
        assertTrue(json.contains("운동화"))
    }
}
