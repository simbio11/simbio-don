package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.simbio.don.app.data.GameState
import com.simbio.don.app.mall.NaverItem
import com.simbio.don.app.mall.NaverShopping
import com.simbio.don.app.mall.RealQuiz
import com.simbio.don.app.ui.MiniGameScreen
import com.simbio.don.game.MiniGame
import com.simbio.don.game.PriceQuiz
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 0.14: 얼마일까? 실제 상품판 (네이버 쇼핑 검색 API) — 네트워크 없이 검색 결과를 흉내 낸다 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class RealQuizTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun items(q: String) = listOf(
        NaverItem("$q 케이블", 500, "부속몰", "기타"), // 너무 싸면 뺀다
        NaverItem("$q 베이직", 23_450, "OO스토어", "생활"),
        NaverItem("$q 프로", 129_000, "XX몰", "생활"),
    )

    private val searched = mutableListOf<String>()
    private suspend fun fake(q: String): NaverShopping.Result {
        synchronized(searched) { searched += q }
        return NaverShopping.Result.Ok(items(q))
    }

    @Test
    fun 검색어만_보내고_실제_최저가로_다섯_문제() = runTest {
        val r = RealQuiz.load(3) { fake(it) }
        val qs = (r as RealQuiz.Load.Ok).questions
        assertEquals(PriceQuiz.ROUNDS, qs.size)
        assertEquals(PriceQuiz.realQueries(3).toSet(), searched.toSet())
        assertTrue(searched.all { it in PriceQuiz.REAL_QUERIES })
        qs.forEach { q ->
            assertTrue(q.answer >= RealQuiz.MIN_PRICE)
            assertTrue(q.source == "OO스토어" || q.source == "XX몰")
            assertEquals(3, q.choices.toSet().size)
            // 끝자리로 정답을 알 수 없게 틀린 값도 같은 단위로
            assertTrue(q.choices.all { it % PriceQuiz.granularity(q.answer) == 0L })
        }
        assertEquals(RealQuiz.load(3) { fake(it) }, RealQuiz.load(3) { fake(it) })

        // 키가 틀리면 그 까닭, 결과가 모자라면 모자란다고
        val failed = RealQuiz.load(3) { NaverShopping.Result.Failed("키가 맞지 않아요 (설정에서 확인)") }
        assertEquals(RealQuiz.Load.Failed("키가 맞지 않아요 (설정에서 확인)"), failed)
        assertTrue(RealQuiz.load(3) { NaverShopping.Result.Ok(emptyList()) } is RealQuiz.Load.Failed)
    }

    @Test
    fun 실제_상품으로_놀고_못_불러오면_몰_상품으로() {
        val fail = AtomicBoolean(false)
        var played: Pair<Int, Long>? = null
        rule.setContent {
            MiniGameScreen(
                state = GameState(settled = 2_500, cap = 250),
                salary = 3_300_000,
                start = null,
                onPlay = { _, score, reward, granted -> played = score to reward; granted(reward) },
                onBack = {},
                realQuiz = { seed -> if (fail.get()) RealQuiz.Load.Failed("인터넷에 연결하지 못했어요") else RealQuiz.load(seed) { fake(it) } },
                seed = { 5L },
            )
        }
        rule.onNodeWithText("실제 쇼핑몰 상품의 최저가를 맞혀요", substring = true).assertExists()
        rule.onNodeWithText("얼마일까?").performClick()
        rule.waitUntilAtLeastOneExists(hasText("이 상품의 지금 최저가는 얼마일까요?"), 5_000)
        rule.onAllNodesWithText("네이버 쇼핑 · ", substring = true).onFirst().assertExists()
        shot("q1_real_quiz")
        repeat(PriceQuiz.ROUNDS) { i ->
            rule.onAllNodes(hasText("원", substring = true) and hasClickAction()).onFirst().performClick()
            if (i == 0) {
                rule.onNodeWithText("네이버 쇼핑 최저가 (불러온 때 기준)", substring = true).assertExists()
                shot("q2_real_quiz_answer")
            }
            rule.onNodeWithText(if (i < PriceQuiz.ROUNDS - 1) "다음 문제" else "결과 보기").performScrollTo().performClick()
        }
        rule.waitUntilAtLeastOneExists(hasText("실제 상품"), 5_000)
        rule.waitUntil(5_000) { played != null }
        assertEquals(played!!.first * PriceQuiz.MILES_PER_RIGHT, played!!.second)

        // 못 불러오면 까닭을 보여주고 몰 상품으로
        fail.set(true)
        rule.onNodeWithText("한 판 더").performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("몰 상품으로 내요", substring = true), 5_000)
        rule.onNodeWithText("이 물건은 얼마일까요?").assertExists()
        shot("q3_real_quiz_fallback")
    }

    @Test
    fun 키가_없으면_설정에서_넣으라고_알려준다() {
        rule.setContent {
            MiniGameScreen(state = GameState(), salary = 0, start = null, onPlay = { _, _, _, _ -> }, onBack = {}, realQuiz = null)
        }
        rule.onNodeWithText("설정 › 네이버 쇼핑 검색에 키를 넣으면", substring = true).assertExists()
        rule.onNodeWithText("몰 물건 값을 맞혀요", substring = true).assertExists()
    }

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        val bmp = rule.runOnUiThread {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
