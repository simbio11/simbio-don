package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.ui.DonTheme
import com.simbio.don.app.ui.ReportCard
import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryTotal
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.MonthReport
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.17: 지난달 성적표는 새 달이 시작되면 지난달 기록으로 */
@RunWith(RobolectricTestRunner::class)
class MonthReportRepoTest {
    @Test
    fun `새 달이 시작되면 지난달 성적표가 나온다`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        val repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 500_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 23))
        assertNull(repo.lastMonthReport())
        repo.addManual(LocalDate.of(2026, 10, 24), 120_000, "장보기", EntryType.SPEND, Category.LIVING)
        repo.addManual(LocalDate.of(2026, 11, 2), 80_000, "회식", EntryType.SPEND, Category.FOOD)
        repo.dailyUpkeep(LocalDateTime.of(2026, 11, 25, 9, 0))
        val r = repo.lastMonthReport()
        assertNotNull(r)
        r!!
        assertEquals(LocalDate.of(2026, 10, 23), r.start)
        assertEquals(LocalDate.of(2026, 11, 24), r.end)
        assertEquals(200_000L, r.spent)
        assertEquals("A+", r.grade)
        assertEquals(Category.LIVING, r.top!!.category)
        assertEquals(r.weeks, r.weeksKept)
        db.close()
    }
}

/** 성적표 카드 모습 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class ReportCardShotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun 성적표_카드() {
        val r = MonthReport(
            LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 22), 1_800_000, 1_534_000, 266_000, 3, 4, 7,
            CategoryTotal(Category.FOOD, 432_000, 31, 2800), Category.CAFE to -38_000L, Category.SHOPPING to 112_000L, "A",
        )
        var view: android.view.View? = null
        rule.setContent {
            view = LocalView.current
            DonTheme { Box(Modifier.padding(16.dp)) { ReportCard(r) } }
        }
        rule.onNodeWithText("지난달 성적표").assertExists()
        rule.onNodeWithText("카페·간식 −38,000원").assertExists()
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        val v = view!!.rootView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        File(dir).apply { mkdirs() }.resolve("r1_report_card.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** 이거 사도 될까? → 샀어요 → 가계부에 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class CanBuyFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            val start = LocalDate.now().minusDays(3)
            runBlocking {
                DonRepository.get(appContext).completeOnboarding(3_135_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start)
                DonRepository.get(appContext).addGoal("제주 여행", 1_000_000, "flight", start)
            }
        }

        override fun after() = DonRepository.resetForTests()
    }).around(rule)

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        val bmp = rule.runOnUiThread {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun <T> db(block: suspend com.simbio.don.app.data.DonDao.() -> T): T {
        val dao = DonDatabase.get(appContext).dao()
        var r: Result<T>? = null
        val t = Thread { r = runCatching { runBlocking { dao.block() } } }
        t.start()
        rule.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            !t.isAlive
        }
        return r!!.getOrThrow()
    }

    @Test
    fun 따져보고_산다() {
        rule.waitUntilAtLeastOneExists(hasText("이대로 쓰면"), 5_000)
        rule.onNodeWithText("따져 보기").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("가격"), 5_000)
        rule.onAllNodes(hasSetTextAction() and hasText("가격")).onFirst().performTextReplacement("89000")
        rule.onAllNodes(hasSetTextAction() and hasText("뭘 사나요", substring = true)).onFirst().performTextReplacement("운동화")
        // 시급 15,000원 (3,135,000 ÷ 209시간)
        rule.waitUntilAtLeastOneExists(hasText("5시간 56분", substring = true), 5_000)
        rule.waitUntilAtLeastOneExists(hasText("제주 여행", substring = true), 5_000)
        shot("c1_can_buy")
        rule.onNodeWithText("샀어요 · 적기").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("나눠 냈나요"), 5_000)
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { db { allEntriesNow() }.any { it.label == "운동화" } }
        assertEquals(89_000L, db { allEntriesNow() }.single { it.label == "운동화" }.amount)
    }
}
