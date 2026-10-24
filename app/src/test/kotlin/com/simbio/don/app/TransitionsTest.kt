package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import com.simbio.don.app.ui.HomeCard
import com.simbio.don.app.ui.HomeCards
import com.simbio.don.app.ui.HomeLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

/** 0.15: 화면 넘김·탭 바꾸기가 실제로 움직이는지 (중간 장면을 찍어 본다), 폰 애니메이션을 꺼도 효과 보이기 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class TransitionsTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()

    private fun onboard() = runBlocking {
        val today = LocalDate.now()
        DonRepository.get(appContext).completeOnboarding(3_300_000, today.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, today)
    }

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            onboard()
        }

        override fun after() = DonRepository.resetForTests()
    }).around(rule)

    private fun capture(): Bitmap = rule.runOnUiThread {
        val view = rule.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun save(name: String, bmp: Bitmap) {
        val dir = System.getProperty("screenshotDir") ?: return
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 가운데 줄의 왼쪽 끝 색: 밀려나는 화면은 어두워진다 */
    private fun leftEdge(b: Bitmap) = b.getPixel(4, b.height / 2)

    @Test
    fun 새_화면은_밀려_들어오고_아래_화면은_어두워진다() {
        rule.waitUntilAtLeastOneExists(hasText("이대로 쓰면"), 5_000)
        val before = capture()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("설정").performClick()
        rule.mainClock.advanceTimeBy(120)
        val mid = capture()
        save("t1_push_mid", mid)
        // 아래 화면(홈)이 어두워지는 중
        assertNotEquals(leftEdge(before), leftEdge(mid))
        rule.mainClock.advanceTimeBy(600)
        save("t2_push_done", capture())

        // 뒤로: 오른쪽으로 빠진다
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(100)
        save("t3_pop_mid", capture())
        rule.mainClock.advanceTimeBy(600)

        // 탭: 오른쪽 탭은 오른쪽에서 들어온다
        rule.onNodeWithText("자산").performClick()
        rule.mainClock.advanceTimeBy(90)
        save("t4_tab_mid", capture())
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.autoAdvance = true
        rule.waitUntilAtLeastOneExists(hasText("순자산"), 5_000)
    }
    /** 홈 꾸미기: 카드를 숨기고 올리면 홈이 그대로 바뀌고 저장된다 */
    @Test
    fun 홈_꾸미기() {
        rule.waitUntilAtLeastOneExists(hasText("이대로 쓰면"), 5_000)
        rule.onNodeWithText("홈 꾸미기", substring = true).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("보고 싶은 카드만 켜고", substring = true), 5_000)
        // "이대로 쓰면" 숨기고, "최근 내역"을 맨 위로
        rule.onAllNodes(androidx.compose.ui.test.isToggleable())[1].performClick()
        repeat(HomeCard.entries.size) { rule.onNodeWithContentDescription("최근 내역 위로").performClick() }
        assertEquals(HomeCard.RECENT, HomeCards.layout.order.first())
        assertTrue(HomeCard.FORECAST in HomeCards.layout.hidden)
        System.getProperty("screenshotDir")?.let { save("t6_home_cards", capture()) }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntilAtLeastOneExists(hasText("최근 내역"), 5_000)
        rule.onAllNodes(hasText("이대로 쓰면")).assertCountEquals(0)
        assertEquals(HomeCards.layout, HomeLayout.decode(appContext.getSharedPreferences("ui", 0).getString("home_cards", null)))
        HomeCards.layout = HomeLayout.Default
    }
}



/** 폰에서 애니메이션을 꺼도 "이 앱에서는 효과 보이기"를 켜면 움직임 배율 1인 재구성기로 그린다 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class FullMotionTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun 애니메이션을_꺼도_효과_보이기를_켜면_그대로_뜬다() {
        DonDatabase.inMemoryForTests = true
        DonRepository.resetForTests()
        Settings.Global.putFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        UiPrefs.setEffectsAlways(appContext, true)
        assertTrue(UiPrefs.systemAnimationsOff(appContext))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
                s.onActivity { a ->
                    assertTrue(a.window.decorView.compositionContext is Recomposer)
                    val v = a.window.decorView
                    assertTrue(v.width > 0)
                    val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
                    System.getProperty("screenshotDir")?.let { dir ->
                        File(dir).apply { mkdirs() }.resolve("t5_full_motion.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
            // 닫힌 화면의 재구성기가 끝까지 정리되게 (남으면 다음 테스트의 Compose가 쉬지 못한다)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(Recomposer.runningRecomposers.value.isEmpty())
        } finally {
            Settings.Global.putFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            DonRepository.resetForTests()
        }
    }
}
