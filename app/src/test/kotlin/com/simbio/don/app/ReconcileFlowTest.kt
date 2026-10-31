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
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.budget.Category
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
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
import java.time.LocalDateTime

/** 0.20: 홈에서 알림 잔액 차이 맞추기, 계획인지 묻는 결제, 실제 잔액 맞추기 화면 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class ReconcileFlowTest {
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
                val repo = DonRepository.get(appContext)
                repo.completeOnboarding(3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null)
                repo.saveRule(AppRule("bank.app", "OO뱅크"))
                repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0, packageName = "bank.app"))
                // 알림 잔액이 앱보다 2만 원 적다 (놓친 결제)
                repo.handleNotification(RawNotification("bank.app", "출금", "체크카드 출금 5,000원 GS25 잔액 975,000원", LocalDateTime.now().minusHours(2)))
                // 계획 5만 원 모임에 20만 원을 내가 다 냄 → 엔빵인지 묻기
                repo.savePlan(PlanEntity(epochDay = LocalDate.now().toEpochDay(), amount = 50_000, label = "동기 모임", category = Category.FOOD.name))
                repo.handleNotification(RawNotification("bank.app", "출금", "체크카드 출금 200,000원 삼겹살집", LocalDateTime.now().minusHours(1)))
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
    fun 홈에서_잔액_차이와_계획_결제를_정리한다() {
        waitText("이대로 쓰면")
        waitText("생활비 통장 잔액이 달라요")
        waitText("이 결제, 계획한 거예요?")
        shot("r1_home_cards")

        // 몇 명이서인지 앱이 정하지 않는다 → 내가 4명을 골라야 잇는다
        rule.onNodeWithText("몇 명인지 골라 주세요").performScrollTo().performClick()
        assertTrue(!bg { repo.plans.first() }.single().done)
        rule.onAllNodesWithText("4명").onFirst().performScrollTo().performClick()
        waitText("내 몫 50,000원만 생활비에", substring = true)
        rule.onNodeWithText("4명이서 낸 계획이에요").performScrollTo().performClick()
        rule.waitUntil(5_000) { bg { repo.plans.first() }.single().done }
        assertEquals(50_000L, bg { repo.plans.first() }.single().doneAmount)

        // 빠진 지출로 잔액 맞추기
        rule.onNodeWithText("빠진 지출로").performScrollTo().performClick()
        rule.waitUntil(5_000) { bg { repo.balanceGaps.first() }.isEmpty() }
        assertTrue(bg { db().allEntriesNow() }.any { it.label == "빠진 지출 (잔액 맞춤)" && it.amount == 20_000L })

        // 실제 잔액 맞추기 화면: 실제가 1만 원 많다 → 잔액만
        rule.onNodeWithText("통장 잔액이 실제와 다르면 · 잔액 맞추기").performScrollTo().performClick()
        waitText("실제 잔액 맞추기")
        val app = bg { repo.balanceNow(db().accountsNow().single().id)!! }
        rule.onNodeWithText("앱 잔액 ${"%,d".format(app)}원", substring = true)
        rule.onAllNodes(hasSetTextAction() and hasText("지금 실제 잔액")).onFirst().performTextReplacement((app + 10_000).toString())
        waitText("실제가 10,000원 많아요")
        rule.onNodeWithText("잔액만 맞추기").performClick()
        shot("r2_balance_fix")
        rule.onNodeWithText("맞추기").performClick()
        rule.waitUntil(5_000) { bg { repo.balanceNow(db().accountsNow().single().id) } == app + 10_000 }
    }

    private fun db() = DonDatabase.get(appContext).dao()
}
