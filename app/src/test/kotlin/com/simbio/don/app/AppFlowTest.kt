package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * 초기 설정 → 첫 화면 → 지출 입력 → 설정까지 실제 화면으로 따라간다.
 * `-Pscreenshots`를 주면 화면마다 build/screenshots/에 PNG를 남긴다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

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
    @Test
    fun 초기_설정부터_지출_입력까지() {
        rule.waitUntilAtLeastOneExists(hasText("처음 설정"), 5_000)
        shot("01_onboarding")

        rule.onNodeWithText("이번 달 월급 (실수령액)").performTextReplacement("3300000")
        // 월급날을 열흘 전으로 두면 언제 돌려도 '한 달 중간에 시작' 흐름을 탄다
        rule.onNodeWithText("25").performTextReplacement(LocalDate.now().minusDays(10).dayOfMonth.toString())
        rule.onAllNodesWithText("금액").onFirst().performTextReplacement("1000000")
        rule.onNodeWithText("매달 저축").performScrollTo().performTextReplacement("320000")
        // 오늘이 월급날이 아니면 첫 달은 오늘부터만 기록한다
        rule.onNodeWithText("이번 달은 이미 시작됐어요").performScrollTo()
        shot("02_onboarding_filled")
        rule.onNodeWithText("이대로 예산 짜기").performScrollTo().performClick()

        rule.waitUntilAtLeastOneExists(hasText("이번 주 쓸 수 있는 돈"), 5_000)
        rule.onNodeWithText("결제 알림 자동 기록이 꺼져 있어요").assertExists()
        rule.onNodeWithText("지난주 결산이 준비됐어요").assertDoesNotExist()
        shot("03_home")

        rule.onNodeWithText("지출 입력").performClick()
        rule.waitUntilAtLeastOneExists(hasText("어디에 썼나요 (선택)"), 5_000)
        rule.onNode(hasSetTextAction() and hasText("금액")).performTextReplacement("18000")
        rule.onNodeWithText("어디에 썼나요 (선택)").performTextReplacement("치킨")
        shot("04_add_entry")
        rule.onNodeWithText("저장").performClick()

        rule.waitUntilAtLeastOneExists(hasText("18,000원"), 5_000)
        shot("05_home_after_spend")

        rule.onNodeWithText("설정").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("알림 읽을 앱"), 5_000)
        shot("06_settings")
    }
}
