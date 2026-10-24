package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonPalette
import com.simbio.don.app.ui.ThemeMode
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** 0.15: 어둡게 모드 — 화면마다 스크린샷으로 확인, 설정에서 바로 바뀐다 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class DarkModeTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            UiPrefs.setThemeMode(appContext, ThemeMode.DARK)
            // 화면이 뜨기 전에 기록을 넣어 둔다
            val today = LocalDate.now()
            val start = today.minusDays(10)
            val repo = DonRepository.get(appContext)
            runBlocking {
                repo.completeOnboarding(
                    3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null,
                    accounts = listOf(
                        DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_500_000),
                        DonRepository.AccountDraft("신용카드", AccountType.CARD, -230_000),
                        DonRepository.AccountDraft("적금", AccountType.SAVINGS, 4_000_000),
                    ),
                )
                (0 until 10).filter { it % 3 != 2 }.forEach { i ->
                    repo.addManual(start.plusDays(i.toLong()), 12_000L + i * 1_000, "점심", EntryType.SPEND, Category.FOOD)
                }
                repo.addManual(start.plusDays(4), 98_000, "회식", EntryType.SPEND, Category.FOOD)
                repo.addManual(start.plusDays(5), 45_000, "올리브영", EntryType.SPEND, Category.SHOPPING)
                repo.savePlan(PlanEntity(epochDay = today.plusDays(2).toEpochDay(), amount = 50_000, label = "친구 생일", category = Category.EVENT.name))
            }
        }

        override fun after() {
            DonRepository.resetForTests()
            DonColors.palette = DonPalette.Light
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

    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 5_000)

    @Test
    fun 어둡게_모드_화면들() {
        waitText("이대로 쓰면")
        assertTrue(DonColors.dark)
        shot("d01_home")
        rule.onNodeWithText("하루 몫", substring = true).performScrollTo()
        shot("d02_home_calendar")
        rule.onNodeWithText("모은 돈").performScrollTo()
        shot("d03_home_saved")

        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        shot("d04_ledger")
        rule.onNodeWithText("달력").performClick()
        shot("d05_ledger_calendar")
        rule.onNodeWithText("통계").performClick()
        rule.waitForIdle()
        shot("d06_stats")
        rule.onNodeWithText("자산").performClick()
        waitText("순자산")
        shot("d07_assets")
        rule.onNodeWithText("내 집").performClick()
        rule.waitForIdle()
        shot("d08_house")

        rule.onNodeWithText("홈").performClick()
        waitText("이대로 쓰면")
        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("금액")
        shot("d09_entry")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitText("이대로 쓰면")

        // 설정에서 밝게로 바꾸면 바로 바뀐다
        rule.onNodeWithContentDescription("설정").performScrollTo().performClick()
        waitText("밝기")
        rule.onNodeWithText("밝기").performScrollTo()
        shot("d10_settings")
        rule.onNodeWithText("밝게").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(DonColors.dark)
        assertEquals(ThemeMode.LIGHT, UiPrefs.themeMode(appContext))
        shot("d11_settings_light")
    }
}
