package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
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

/** 0.20: 캘린더 탭 — 폰 캘린더 없이 앱 안 캘린더로 일정 만들기·보기·고치기·지우기, 예산, 가계부 같이 보기 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class CalendarTabFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now()
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            val start = LocalDate.now().minusDays(3)
            runBlocking {
                DonRepository.get(appContext).completeOnboarding(
                    3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null,
                    accounts = listOf(DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_500_000)),
                )
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

    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntil(10_000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun <T> bg(block: suspend () -> T): T {
        var r: Result<T>? = null
        val t = Thread { r = runCatching { runBlocking { block() } } }
        t.start()
        rule.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            !t.isAlive
        }
        return r!!.getOrThrow()
    }

    private val repo get() = DonRepository.get(appContext)

    @Test
    fun 캘린더_탭에서_일정을_만들고_예산과_가계부를_같이_본다() {
        waitText("이대로 쓰면")
        rule.onNodeWithText("캘린더").performClick()
        waitText("${today.year}년 ${today.monthValue}월")
        waitText("구글 캘린더 등 폰 캘린더와 이으면", substring = true)

        // 사흘짜리 여행 (하루 종일) + 예산
        rule.onNodeWithContentDescription("일정 추가").performClick()
        waitText("일정 추가")
        rule.onAllNodes(hasSetTextAction() and hasText("제목")).onFirst().performTextReplacement("제주 여행")
        rule.onAllNodes(isToggleable()).onFirst().performClick()
        rule.onNodeWithContentDescription("끝 날짜 뒤로").performClick()
        rule.onNodeWithContentDescription("끝 날짜 뒤로").performClick()
        rule.onNodeWithText("매년").performScrollTo().performClick()
        rule.onAllNodes(hasSetTextAction() and hasText("장소 (선택)")).onFirst().performScrollTo().performTextReplacement("제주")
        rule.onAllNodes(hasSetTextAction() and hasText("예산")).onFirst().performScrollTo().performTextReplacement("300000")
        shot("k1_event_edit")
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { bg { repo.appEventsNow() }.isNotEmpty() }
        val trip = bg { repo.appEventsNow() }.single()
        assertEquals("제주 여행", trip.title)
        assertEquals(today.plusDays(2).toEpochDay(), trip.endEpochDay)
        assertEquals(null, trip.startMinute)
        assertEquals("YEARLY", trip.repeat)
        // 예산은 일정 시작 날 계획으로
        rule.waitUntil(5_000) { bg { repo.plans.first() }.any { it.eventId == -trip.id } }
        assertEquals(300_000L, bg { repo.plans.first() }.single { it.eventId == -trip.id }.amount)
        waitText("예산 30만")

        // 시각 있는 일정 + 알림
        rule.onNodeWithContentDescription("일정 추가").performClick()
        waitText("일정 추가")
        rule.onAllNodes(hasSetTextAction() and hasText("제목")).onFirst().performTextReplacement("치과")
        rule.onNodeWithText("30분 전").performScrollTo().performClick()
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { bg { repo.appEventsNow() }.size == 2 }
        assertEquals(30, bg { repo.appEventsNow() }.single { it.title == "치과" }.reminder)
        waitText("치과")
        shot("k2_month_day")

        // 이날 가계부 쓰기
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("+ 가계부 쓰기"))
        rule.onNodeWithText("+ 가계부 쓰기").performClick()
        waitText("저장")
        rule.onAllNodes(hasSetTextAction() and hasText("금액")).onFirst().performTextReplacement("12000")
        rule.onAllNodes(hasSetTextAction() and hasText("어디에 썼나요", substring = true)).onFirst().performTextReplacement("김밥")
        rule.onNodeWithText("저장").performClick()
        waitText("−12,000원")
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("김밥"))
        shot("k3_day_money")

        // 일정 보기 → 고치기
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("+ 일정"))
        rule.onAllNodesWithText("치과").onLast().performClick()
        waitText("이 일정에 쓸 돈")
        shot("k4_event_detail")
        rule.onNodeWithText("고치기").performClick()
        waitText("일정 고치기")
        rule.onAllNodes(hasSetTextAction() and hasText("제목")).onFirst().performTextReplacement("치과 정기검진")
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { bg { repo.appEventsNow() }.any { it.title == "치과 정기검진" } }
        waitText("치과 정기검진")

        // 목록 보기
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("목록"))
        rule.onNodeWithText("목록").performClick()
        waitText("제주 여행")
        shot("k5_list")
        rule.onNodeWithText("월").performClick()

        // 지우기
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("치과 정기검진"))
        rule.onAllNodesWithText("치과 정기검진").onLast().performClick()
        waitText("이 일정에 쓸 돈")
        rule.onNodeWithText("지우기").performClick()
        waitText("일정을 지울까요?")
        rule.onAllNodesWithText("지우기").onLast().performClick()
        rule.waitUntil(5_000) { bg { repo.appEventsNow() }.size == 1 }
        assertTrue(bg { repo.appEventsNow() }.none { it.title.startsWith("치과") })
    }
}
