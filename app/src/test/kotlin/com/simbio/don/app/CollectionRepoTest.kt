package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.game.Catalog
import com.simbio.don.game.Dex
import com.simbio.don.game.MiniGame
import com.simbio.don.game.Sets
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

/** 0.12 M3: 도감·세트 칭호, 미니게임 마일 상한, 백업 */
@RunWith(RobolectricTestRunner::class)
class CollectionRepoTest {
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

    /** 몰에서 사듯 이름 + (옵션) */
    private suspend fun buy(id: String, option: String? = null, day: Int = 24) {
        val p = Catalog.byId(id)!!
        repo.buyMock(p.name + (option?.let { " ($it)" } ?: ""), p.price, p.kind, "돈't 몰", d(10, day), at(10, day))
    }

    @Test
    fun `도착한 몰 물건이 도감을 채우고 세트를 다 모으면 칭호를 단다`() = runTest {
        buy("c7", "M"); buy("s2", "260"); buy("e3", "블랙")
        repo.addManual(d(10, 24), 400_000, "장보기", EntryType.SPEND)
        // 아직 도착 전이면 칭호를 못 단다
        assertFalse(repo.chooseTitle("오운완"))
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), emptySet())
        val got = Dex.gotIds(home(at(11, 2)).mockItems)
        assertEquals(setOf("c7", "s2", "e3"), got)
        assertFalse(repo.chooseTitle("오운완")) // 하나 모자람

        val p = Catalog.byId("e1")!!
        repo.buyMock(p.name + " (화이트)", p.price, p.kind, "돈't 몰", d(11, 3), at(11, 3))
        repo.settle(home(at(11, 9)).settleable.single(), at(11, 9), emptySet())
        val after = home(at(11, 9))
        assertEquals(listOf("오운완"), Sets.titles(Dex.gotIds(after.mockItems)))
        assertTrue(repo.chooseTitle("오운완"))
        assertEquals("오운완", home(at(11, 9)).title)
        assertFalse(repo.chooseTitle("도감 마스터"))
        assertTrue(repo.chooseTitle(""))
        assertEquals("", home(at(11, 9)).title)
    }

    @Test
    fun `미니게임 마일은 이번 주 결산 마일의 10%까지만`() = runTest {
        // 결산 전에는 마일 없이 논다
        assertEquals(0L, repo.playGame(MiniGame.QUIZ, 5, 50, d(10, 30)))
        assertEquals(0L, home(at(10, 30)).game.cap)

        repo.addManual(d(10, 24), 450_000, "장보기", EntryType.SPEND) // 25% 아낌 → 10일 주 3,571마일
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), emptySet())
        val g = home(at(11, 3)).game
        assertEquals(2_500L * 10 / 7, g.settled)
        assertEquals(357L, g.cap)

        var total = 0L
        repeat(8) { total += repo.playGame(MiniGame.MEMORY, 9, 50, d(11, 3)) }
        assertEquals(357L, total)
        assertEquals(0L, repo.playGame(MiniGame.QUIZ, 5, 50, d(11, 4)))
        val h = home(at(11, 4))
        assertEquals(0L, h.game.left)
        assertEquals(10, h.game.plays.size)
        assertEquals(9, h.game.best(MiniGame.MEMORY))
        assertEquals(5, h.game.best(MiniGame.QUIZ))
        assertEquals(2_500L * 10 / 7 + 357, h.miles.wallet)

        // 다음 주는 그 주 결산 마일로 다시 정한다 (예산을 넘기면 0)
        repo.addManual(d(11, 5), 700_000, "여행", EntryType.SPEND)
        repo.settle(home(at(11, 9)).settleable.single(), at(11, 9), null)
        assertEquals(0L, home(at(11, 10)).game.cap)
        assertEquals(0L, repo.playGame(MiniGame.QUIZ, 5, 50, d(11, 10)))
    }

    @Test
    fun `칭호와 미니게임 기록도 백업에 담긴다`() = runTest {
        buy("c1", "M"); buy("c10", "L"); buy("s1", "250"); buy("b2", "블랙")
        repo.addManual(d(10, 24), 300_000, "장보기", EntryType.SPEND)
        repo.settle(home(at(11, 2)).settleable.single(), at(11, 2), emptySet())
        assertTrue(repo.chooseTitle("패션 피플"))
        repo.playGame(MiniGame.QUIZ, 4, 40, d(11, 3))
        val json = repo.exportBackup(at(11, 3))

        repo.chooseTitle("")
        repo.restoreBackup(json)
        val h = home(at(11, 3))
        assertEquals("패션 피플", h.title)
        assertEquals(1, h.game.plays.size)
        assertEquals(40L, h.game.earned)
    }
}
