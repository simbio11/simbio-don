package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/** 0.19: 가게 이름 없는 결제 알림 → "가게 이름 적기" → 알림 글 보고 적기 → 같은 앱 다음 알림은 그 자리에서 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class MerchantNameFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            val start = LocalDate.now().minusDays(3)
            runBlocking {
                val repo = DonRepository.get(appContext)
                repo.completeOnboarding(
                    3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null,
                    accounts = listOf(DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_500_000)),
                )
                repo.saveRule(AppRule("bank.app", "OO뱅크"))
                // 가게 이름이 둘째 줄에 오는 앱: 흔한 규칙으로는 카드 주인 이름을 뽑는다
                repo.handleNotification(RawNotification("bank.app", "결제", "홍길동님 12,000원 결제\n스타벅스 강남", LocalDateTime.now().minusHours(3)))
                // 가게 이름이 아예 없는 알림
                repo.handleNotification(RawNotification("bank.app", "결제", "체크카드 5,000원 결제되었습니다", LocalDateTime.now().minusHours(2)))
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

    @Test
    fun 결제만_적히던_기록에_가게_이름을_적는다() {
        waitText("이대로 쓰면")
        rule.onNodeWithText("가계부").performClick()
        waitText("OO뱅크 결제")
        waitText("가게 이름 적기")
        shot("n1_ledger_unknown")

        // 잘못 뽑힌 "홍길동"을 열어 알림 글을 보고 고친다
        rule.onNodeWithText("홍길동").performClick()
        waitText("받은 알림 글 보기")
        rule.onNodeWithText("받은 알림 글 보기").performScrollTo().performClick()
        waitText("스타벅스 강남", substring = true)
        shot("n2_edit_raw_text")
        rule.onAllNodes(hasSetTextAction() and hasText("내용 (어디에 썼나요)")).onFirst().performTextReplacement("스타벅스 강남")
        rule.onNodeWithText("저장").performClick()
        waitText("스타벅스 강남")

        // 이름 모름 기록: 알림 글 안내가 펼쳐져 있다
        rule.onNodeWithText("OO뱅크 결제").performClick()
        waitText("알림에서 가게 이름을 못 찾았어요", substring = true)
        shot("n3_edit_unknown")
        rule.onAllNodes(hasSetTextAction() and hasText("내용 (어디에 썼나요)")).onFirst().performTextReplacement("편의점")
        rule.onNodeWithText("저장").performClick()
        waitText("편의점")

        // 다음 알림은 배운 자리(둘째 줄)에서
        bg { DonRepository.get(appContext).handleNotification(RawNotification("bank.app", "결제", "홍길동님 4,500원 결제\n이디야 역삼", LocalDateTime.now().minusMinutes(30))) }
        waitText("이디야 역삼")
        shot("n4_ledger_learned")
        val labels = bg { DonDatabase.get(appContext).dao().allEntriesNow() }.filter { it.packageName == "bank.app" }.map { it.label }.sorted()
        assertEquals(listOf("스타벅스 강남", "이디야 역삼", "편의점"), labels)
    }
}
