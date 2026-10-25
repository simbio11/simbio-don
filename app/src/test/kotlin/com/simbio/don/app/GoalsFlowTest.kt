package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.GoalSource
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/** 0.16: 모은 돈 나누기 — 저축(예금·적금)은 목표에 안 들어가고, 목표는 아낀 돈에서 넣거나 통장을 이어 채운다 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class GoalsFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            // 열흘 전부터 쓴 사람: 지난주 결산으로 아낀 돈이 생겼다
            val today = LocalDate.now()
            val start = today.minusDays(10)
            val repo = DonRepository.get(appContext)
            runBlocking {
                repo.completeOnboarding(
                    3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null,
                    accounts = listOf(
                        DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_500_000),
                        DonRepository.AccountDraft("적금", AccountType.SAVINGS, 4_000_000),
                        DonRepository.AccountDraft("여행 세이프박스", AccountType.BANK, 300_000),
                    ),
                )
                repo.addManual(start.plusDays(1), 12_000, "점심", EntryType.SPEND, Category.FOOD)
                val now = LocalDateTime.now()
                repo.home(flowOf(now)).first()!!.settleable.forEach { w -> repo.settle(w, now, emptySet()) }
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

    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 5_000)

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
    fun 저축과_목표를_나눠_모은다() {
        waitText("이대로 쓰면")
        rule.onNodeWithText("모은 돈").performScrollTo()
        waitText("아낀 돈 중 목표에 안 넣은 돈")
        rule.onNodeWithText("아낀 돈 중 목표에 안 넣은 돈").performScrollTo()
        shot("g1_home_saved")
        // 적금 4,000,000은 저축 — 목표에는 안 들어간다
        waitText("4,000,000원")

        rule.onNodeWithText("목표 만들기").performScrollTo().performClick()
        waitText("모으는 목표")
        rule.onAllNodes(hasSetTextAction() and hasText("이름", substring = true)).onFirst().performTextReplacement("제주 여행")
        rule.onAllNodes(hasSetTextAction() and hasText("목표 금액")).onFirst().performTextReplacement("1000000")
        rule.onNodeWithText("목표 추가").performScrollTo().performClick()
        rule.waitUntil(5_000) { db { goalsNow() }.size == 1 }
        rule.onAllNodes(hasSetTextAction() and hasText("이름", substring = true)).onFirst().performTextReplacement("새 노트북")
        rule.onAllNodes(hasSetTextAction() and hasText("목표 금액")).onFirst().performTextReplacement("1500000")
        rule.onNodeWithText("목표 추가").performScrollTo().performClick()
        rule.waitUntil(5_000) { db { goalsNow() }.size == 2 }

        // 제주 여행: 아낀 돈에서 50,000원 넣기
        waitText("넣기")
        rule.onAllNodesWithText("넣기").onFirst().performScrollTo().performClick()
        waitText("'제주 여행'에 넣기")
        rule.onAllNodes(hasSetTextAction() and hasText("넣을 금액")).onFirst().performTextReplacement("50000")
        rule.onNodeWithText("'제주 여행'에 넣기").performScrollTo()
        shot("g2_put_panel")
        // 넣기 글자: [0] 제주 여행 버튼, [1] 펼친 칸의 넣기, [2] 새 노트북 버튼
        rule.onAllNodesWithText("넣기")[1].performScrollTo().performClick()
        rule.waitUntil(5_000) { db { goalMovesNow() }.size == 1 }
        val move = db { goalMovesNow() }.single()
        assertEquals(50_000L, move.amount)
        assertEquals(GoalSource.SAVED.name, move.source)
        waitText("50,000원 / 1,000,000원", substring = true)

        // 새 노트북: 세이프박스 통장 잇기 → 그 잔액으로 채움
        rule.onAllNodesWithText("통장 잇기").onLast().performScrollTo().performClick()
        waitText("'새 노트북'에 통장 잇기")
        rule.onNodeWithText("여행 세이프박스").performClick()
        rule.waitUntil(5_000) { db { goalsNow() }.any { it.accountId != null } }
        waitText("여행 세이프박스 잔액으로 채워요")
        shot("g3_goals")
        rule.onNodeWithText("진행 중").performScrollTo()
        shot("g3b_goals_list")

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("이대로 쓰면")
        rule.onNodeWithText("모은 돈").performScrollTo()
        // 목표 = 50,000 + 세이프박스 300,000
        waitText("목표 2개에 모은 돈")
        shot("g4_home_saved_after")
        val goals = db { goalsNow() }
        assertTrue(goals.single { it.name == "새 노트북" }.accountId != null)
    }
}
