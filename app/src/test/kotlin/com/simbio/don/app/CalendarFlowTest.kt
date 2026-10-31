package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.calendar.DeviceCalendar
import com.simbio.don.app.calendar.FakeCalendarSource
import com.simbio.don.app.calendar.PhoneCalendar
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.ui.shortDate
import com.simbio.don.budget.Category
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

/** 0.19: 폰 캘린더 일정에 쓸 돈을 정하고 → 쓴 뒤 지켰는지 보고 → 앱에서 일정을 넣는다 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class CalendarFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now()
    private val fake = FakeCalendarSource(
        listOf(
            DeviceCalendar(1, "me@gmail.com", "me@gmail.com", 0xFF4285F4.toInt(), true),
            DeviceCalendar(2, "대한민국 공휴일", "me@gmail.com", 0xFF0B8043.toInt(), false),
        ),
    ).apply {
        list += CalEvent(1, 1, "팀 회식", today, 19 * 60, 0xFF4285F4.toInt(), "을지로")
        list += CalEvent(2, 1, "민지 결혼식", today.plusDays(1), null, 0xFFE67C73.toInt())
        list += CalEvent(3, 2, "쉬는 날", today.plusDays(2), null, 0xFF0B8043.toInt())
    }
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            // 권한 대신 가짜 캘린더 (연동은 설정 화면에서 켠다)
            PhoneCalendar.sourceForTests = fake
            val start = LocalDate.now().minusDays(3)
            runBlocking {
                DonRepository.get(appContext).completeOnboarding(
                    3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null,
                    accounts = listOf(DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_500_000)),
                )
            }
        }

        override fun after() {
            PhoneCalendar.sourceForTests = null
            DonRepository.resetForTests()
        }
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

    /** 캘린더는 다른 스레드에서 읽어 메인으로 넘기므로 메인 루퍼를 돌리며 기다린다 */
    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntil(10_000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }

    /** 메인 스레드 밖에서 DB 읽기 (Room은 메인에서 못 읽는다) */
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
    fun 일정마다_쓸_돈을_정하고_지켰는지_본다() {
        waitText("이대로 쓰면")
        // 설정에서 폰 캘린더 연동 → 보일 캘린더 · 새 일정 넣을 곳 (처음엔 구글 기본 캘린더)
        rule.onNodeWithContentDescription("설정").performClick()
        waitText("폰 캘린더 (선택)")
        rule.onNodeWithText("캘린더 연동하기").performScrollTo().performClick()
        waitText("새 일정 넣을 곳")
        rule.onNodeWithText("새 일정 넣을 곳").performScrollTo()
        shot("c0_settings_calendar")
        assertTrue(rule.onAllNodesWithText("대한민국 공휴일").fetchSemanticsNodes().isNotEmpty())
        val vm = rule.runOnUiThread { androidx.lifecycle.ViewModelProvider(rule.activity)[com.simbio.don.app.ui.MainViewModel::class.java] }
        rule.waitUntil(5_000) { vm.writeCalendarId.value == 1L }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        // 홈 "일주일 안에": 예산 안 정한 일정 → 누르면 그날 캘린더
        waitText("예산 안 정한 일정")
        rule.onNodeWithText("민지 결혼식").performScrollTo()
        shot("c1_home_events")
        rule.onNodeWithText("민지 결혼식").performClick()
        waitText("${today.plusDays(1).monthValue}월 ${today.plusDays(1).dayOfMonth}일", substring = true)
        shot("c2_calendar_day")

        // 결혼식 일정 → 10만 원
        rule.onAllNodesWithText("민지 결혼식").onLast().performClick()
        waitText("이 일정에 쓸 돈")
        rule.onAllNodes(hasSetTextAction() and hasText("예산")).onFirst().performTextReplacement("100000")
        rule.onNodeWithText("예산 정하기").performClick()
        rule.waitUntil(5_000) { bg { repo.plans.first() }.any { it.eventId == 2L } }
        val wedding = bg { repo.plans.first() }.single { it.eventId == 2L }
        assertEquals(100_000L, wedding.amount)
        assertEquals(Category.EVENT.name, wedding.category)
        assertEquals(today.plusDays(1).toEpochDay(), wedding.epochDay)
        waitText("예산 100,000원")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        // 오늘: 팀 회식 5만 원 → 썼어요 (실제 62,000원)
        // 내일이 다음 달이면 오늘 칸이 안 보이니 "오늘"로 돌아간다
        rule.onAllNodesWithText("오늘").onFirst().performClick()
        waitText("을지로", substring = true)
        rule.onAllNodesWithText("팀 회식").onLast().performClick()
        waitText("이 일정에 쓸 돈")
        rule.onAllNodes(hasSetTextAction() and hasText("예산")).onFirst().performTextReplacement("50000")
        rule.onNodeWithText("예산 정하기").performClick()
        waitText("예산 50,000원")
        rule.onNodeWithText("썼어요").performClick()
        waitText("저장")
        rule.onAllNodes(hasSetTextAction() and hasText("금액")).onFirst().performTextReplacement("62000")
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { bg { repo.plans.first() }.any { it.eventId == 1L && it.done } }
        val dinner = bg { repo.plans.first() }.single { it.eventId == 1L }
        assertEquals(62_000L, dinner.doneAmount)
        assertEquals(Category.FOOD.name, dinner.category)
        waitText("1.2만 넘음", substring = true)
        shot("c3_event_over")
        // 미리 정한 돈이라 오늘 몫(그날 쓰는 돈)에는 안 들어간다
        val home = bg { repo.home(flowOf(LocalDateTime.now())).first()!! }
        assertEquals(0L, home.day.spentDaily)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

        // 가계부 달력에서도 일정 넣기: 치과 · 점심 12시 · 3만 원 (폰 캘린더로)
        rule.onAllNodesWithText("가계부").onLast().performClick()
        waitText("달력")
        rule.onNodeWithText("달력").performClick()
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("+ 일정 추가"))
        rule.onNodeWithText("+ 일정 추가").performClick()
        waitText("일정 추가", substring = true)
        rule.onAllNodes(hasSetTextAction() and hasText("일정 (예: 친구 생일 모임)")).onFirst().performTextReplacement("치과")
        rule.onAllNodes(hasSetTextAction() and hasText("쓸 돈 (선택)")).onFirst().performTextReplacement("30000")
        rule.waitForIdle()
        rule.onNodeWithText("점심 12시").performClick()
        rule.waitForIdle()
        shot("c4_add_event")
        rule.onNodeWithText("캘린더에 넣기").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.list.any { it.title == "치과" } }
        val added = fake.list.single { it.title == "치과" }
        assertEquals(12 * 60, added.minuteOfDay)
        assertEquals(1L, added.calendarId)
        assertEquals(today, added.date)
        rule.waitUntil(5_000) { bg { repo.plans.first() }.any { it.eventId == added.eventId } }
        assertEquals(Category.HEALTH.name, bg { repo.plans.first() }.single { it.eventId == added.eventId }.category)
        waitText("치과")
        waitText("12:00", substring = true)
        shot("c5_after_add")
    }
}
