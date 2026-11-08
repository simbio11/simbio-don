package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.CategoryRuleEntity
import com.simbio.don.app.data.DonRepository.AutoCheck
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.ui.AccountQuestionCard
import com.simbio.don.app.ui.AutoLearnScreen
import com.simbio.don.app.ui.CheckCard
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonTheme
import com.simbio.don.app.ui.WorthLine
import com.simbio.don.budget.AutoRate
import com.simbio.don.budget.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** 0.28: 확인해 주세요 카드·자동 기록 학습 화면·"이 돈이면" 한 줄을 눈으로 확인하고, 누르면 알려 주는지 본다 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class AutoLearnScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val day = LocalDate.of(2026, 11, 8).toEpochDay()
    private fun entry(id: Long, label: String, amount: Long) =
        EntryEntity(id = id, epochDay = day, amount = amount, label = label, type = "SPEND", source = "NOTIFICATION", packageName = "card.app", accountId = 1)

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `확인해 주세요 카드에서 분류를 누르면 알려 준다`() {
        val asked = mutableListOf<Pair<Long, Category>>()
        val checks = listOf(
            AutoCheck(entry(1, "김씨네", 8_000), needsCategory = true, needsName = false, needsAccount = false),
            AutoCheck(entry(2, "신한카드 결제", 12_000).copy(nameUnknown = true), needsCategory = false, needsName = true, needsAccount = false),
            AutoCheck(entry(3, "동네마트", 5_000), needsCategory = true, needsName = false, needsAccount = false),
        )
        rule.setContent {
            DonTheme {
                Column(Modifier.fillMaxSize().background(DonColors.Bg).padding(16.dp)) {
                    CheckCard(checks, AutoRate(20, 3), { e, c -> asked += e.id to c }, {}, {})
                    WorthLine(3_000_000)
                    WorthLine(52_000_000)
                }
            }
        }
        rule.onNodeWithTextExists("확인해 주세요 · 3건")
        rule.onNodeWithTextExists("최근 20건 중 17건은 알아서 · 3건 확인 필요")
        rule.onNodeWithTextExists("김씨네")
        rule.onNodeWithTextExists("이름 적기")
        shot("l1_check_card")
        rule.onNodeWithTextClick("식비")
        assertEquals(listOf(1L to Category.FOOD), asked)
    }

    @Test
    fun `자동 기록 학습 화면`() {
        val accounts = listOf(
            AccountEntity(1, "생활비 통장", AccountType.BANK.name, 0, 0, packageName = "bank.app", tails = "a88507"),
            AccountEntity(2, "신한카드 주력", AccountType.CARD.name, 0, 1, packageName = "card.app", tails = "c1111,c3333"),
        )
        val rules = listOf(CategoryRuleEntity("김씨네", Category.FOOD.name), CategoryRuleEntity("동네마트", Category.LIVING.name), CategoryRuleEntity("헬스장", Category.HEALTH.name))
        val forgot = mutableListOf<String>()
        rule.setContent {
            DonTheme {
                AutoLearnScreen(AutoRate(24, 2), accounts, { if (it == "bank.app") "신한 SOL뱅크" else "신한 SOL페이" }, rules, { forgot += it }, {}, { _, _ -> }, {})
            }
        }
        rule.onNodeWithTextExists("91%")
        rule.onNodeWithTextExists("카드 끝 1111  ✕")
        rule.onNodeWithTextExists("배운 가게 3곳")
        shot("l2_learned")
    }

    @Test
    fun `어느 통장이에요 카드`() {
        val accounts = listOf(
            AccountEntity(1, "생활비 통장", AccountType.BANK.name, 0, 0),
            AccountEntity(2, "신한카드 주력", AccountType.CARD.name, 0, 1),
            AccountEntity(3, "지갑", AccountType.CASH.name, 0, 2),
        )
        val chosen = mutableListOf<Long>()
        rule.setContent {
            DonTheme {
                Column(Modifier.fillMaxSize().background(DonColors.Bg).padding(16.dp)) {
                    AccountQuestionCard(listOf(entry(1, "GS25", 3_000), entry(2, "스타벅스", 6_500)), accounts, { _, id, _ -> chosen += id })
                }
            }
        }
        rule.onNodeWithTextExists("어느 통장이에요?")
        shot("l3_account_question")
        rule.onAllNodesWithTextFirstClick("신한카드 주력")
        assertTrue(chosen == listOf(2L))
    }
}

// 확인용 짧은 도우미
private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithTextExists(text: String) =
    androidx.compose.ui.test.hasText(text, substring = true).let { m -> onNode(m).assertExists() }

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithTextClick(text: String) =
    androidx.compose.ui.test.hasText(text).let { m -> onAllNodes(m).onFirst().performClick() }

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextFirstClick(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).onFirst().performClick()
