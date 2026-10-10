package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 실제 화면으로 따라간다. `-Pscreenshots`를 주면 화면마다 build/screenshots/에 PNG를 남긴다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /** 다음 테스트가 빈 DB로 시작하도록 (Robolectric은 같은 JVM에서 파일·싱글턴을 이어 쓸 수 있다) */
    @After
    fun tearDown() {
        DonRepository.resetForTests()
        rule.activity.deleteDatabase("don.db")
    }

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        // captureToImage()는 Robolectric에서 멈추므로 창을 직접 그린다
        val bmp = rule.runOnUiThread {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun waitText(text: String) = rule.waitUntilAtLeastOneExists(hasText(text), 5_000)

    @Test
    fun 초기_설정부터_지출_입력까지() {
        waitText("처음 설정")
        shot("01_onboarding")

        rule.onNodeWithText("이번 달 월급 (실수령액)").performTextReplacement("3300000")
        // 월급날을 열흘 전으로 두면 언제 돌려도 '한 달 중간에 시작' 흐름을 탄다
        rule.onNodeWithText("25").performTextReplacement(LocalDate.now().minusDays(10).dayOfMonth.toString())
        rule.onAllNodesWithText("금액").onFirst().performTextReplacement("1000000")
        rule.onNodeWithText("매달 저축").performScrollTo().performTextReplacement("320000")
        rule.onNodeWithText("이번 달은 이미 시작됐어요").performScrollTo()
        rule.onNodeWithText("이대로 예산 짜기").performScrollTo().performClick()

        waitText("이번 주 쓸 수 있는 돈")
        rule.onNodeWithText("결제 알림 자동 기록이 꺼져 있어요").assertExists()
        rule.onNodeWithText("지난주 결산이 준비됐어요").assertDoesNotExist()

        rule.onNodeWithContentDescription("지출 입력").performClick()
        waitText("어디에 썼나요")
        rule.onNode(hasSetTextAction() and hasText("금액")).performTextReplacement("4500")
        rule.onNodeWithText("어디에 썼나요").performTextReplacement("스타벅스")
        // 가게 이름으로 카테고리가 골라진다
        rule.onNodeWithText("☕ 카페·간식").assertExists()
        shot("02_add_entry")
        rule.onNodeWithText("저장").performScrollTo().performClick()

        waitText("−4,500원")
        rule.onNodeWithText("내역").performClick()
        waitText("이번 달 4,500원 썼어요")
        rule.onNodeWithText("통계").performClick()
        waitText("지출 속도")
        rule.onNodeWithText("설정").performClick()
        waitText("알림 읽을 앱")
    }

    /** 2주쯤 쓴 상태를 만들어 화면을 찍는다. */
    @Test
    fun 기록이_쌓인_화면() {
        val today = LocalDate.now()
        val repo = DonRepository.get(rule.activity)
        runBlocking {
            repo.completeOnboarding(
                salary = 3_300_000,
                payDay = today.minusDays(14).dayOfMonth,
                fixed = listOf(FixedExpense("월세", 700_000), FixedExpense("통신·보험", 280_000)),
                savings = 400_000,
                settlementDay = DayOfWeek.MONDAY,
                today = today.minusDays(14),
                firstCycleBudget = null,
            )
            val sample = listOf(
                Triple(14, 4_500L, "스타벅스"), Triple(14, 9_000L, "김밥천국"), Triple(13, 23_000L, "배달의민족"),
                Triple(13, 1_450L, "지하철"), Triple(12, 68_000L, "이마트"), Triple(12, 4_800L, "메가커피"),
                Triple(11, 12_000L, "카카오T 택시"), Triple(10, 39_000L, "무신사"), Triple(10, 8_500L, "GS25"),
                Triple(9, 15_000L, "CGV"), Triple(8, 27_000L, "OO치킨"), Triple(8, 4_500L, "스타벅스"),
                Triple(7, 13_000L, "약국"), Triple(6, 52_000L, "쿠팡"), Triple(5, 9_900L, "넷플릭스"),
                Triple(4, 6_000L, "동네빵집"), Triple(3, 31_000L, "배달의민족"), Triple(2, 4_500L, "스타벅스"),
                Triple(1, 18_000L, "국밥"), Triple(0, 3_200L, "CU"),
            )
            sample.forEach { (ago, amount, label) -> repo.addManual(today.minusDays(ago.toLong()), amount, label, EntryType.SPEND) }
            repo.addManual(today.minusDays(6), 100_000, "친구 결혼식", EntryType.EXCEPTION, Category.EVENT)
        }
        waitText("이번 주 쓸 수 있는 돈")
        shot("10_home")
        rule.onNodeWithText("최근 내역").performScrollTo()
        shot("11_home_bottom")
        rule.onNodeWithText("내역").performClick()
        waitText("칸 색이 진할수록 그날 많이 썼어요. 날짜를 누르면 그날 내역만 봐요.")
        shot("12_history")
        rule.onNodeWithText("통계").performClick()
        waitText("지출 속도")
        shot("13_stats")
        rule.onNodeWithText("카테고리별").performScrollTo()
        shot("14_stats_categories")
        rule.onNodeWithText("주별 예산").performScrollTo()
        shot("15_stats_weeks")
    }
}
