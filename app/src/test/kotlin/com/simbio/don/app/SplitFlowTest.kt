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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
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

/** 0.17: 밥값을 내가 다 내고 → 친구 입금을 정산으로 묶고 → 남은 사람은 현금으로 받았다 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class SplitFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
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
    fun 내가_다_낸_밥값을_정산한다() {
        waitText("이대로 쓰면")
        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("나눠 냈나요")
        rule.onAllNodes(hasSetTextAction() and hasText("금액")).onFirst().performTextReplacement("80000")
        rule.onAllNodes(hasSetTextAction() and hasText("어디에 썼나요", substring = true)).onFirst().performTextReplacement("고깃집")
        rule.onNodeWithText("내가 다 냄").performScrollTo().performClick()
        rule.onNodeWithText("4명").performScrollTo().performClick()
        waitText("받을 돈 60,000원", substring = true)
        rule.onNodeWithText("받을 돈 60,000원", substring = true).performScrollTo()
        shot("s1_entry_split")
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { db { allEntriesNow() }.any { it.label == "고깃집" } }
        val shared = db { allEntriesNow() }.single { it.label == "고깃집" }
        assertEquals(20_000L, shared.share)
        assertEquals(4, shared.people)

        // 친구 입금이 알림으로 수입처럼 들어왔다
        val bank = db { accountsNow() }.single().id
        runBlocking { DonRepository.get(appContext).addManual(LocalDate.now(), 20_000, "김철수", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank) }

        waitText("이대로 쓰면")
        waitText("정산 같은 돈 1건", substring = true)
        rule.onNodeWithText("정산 같은 돈 1건", substring = true).performScrollTo()
        shot("s2_home_split_card")
        // 홈 카드의 안내 줄을 누르면 정산으로
        rule.onNodeWithText("정산 같은 돈 1건", substring = true).performClick()
        waitText("이 돈, 정산인가요?")
        shot("s3_split_screen")
        rule.onNodeWithText("정산이에요").performClick()
        rule.waitUntil(5_000) { db { allEntriesNow() }.any { it.label == "김철수" && it.type == EntryType.TRANSFER.name } }
        waitText("40,000원")

        // 나머지 한 사람은 현금으로
        rule.onNodeWithText("받았어요").performClick()
        waitText("'고깃집' 받은 돈")
        rule.onAllNodesWithText("받았어요").onLast().performScrollTo().performClick()
        rule.waitUntil(5_000) { db { allEntriesNow() }.count { it.settleFor == shared.id } == 2 }
        waitText("20,000원")
        shot("s4_split_after")
    }
}
