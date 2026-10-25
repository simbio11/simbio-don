package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.GoalSource
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.data.Splits
import com.simbio.don.app.ui.DonColors
import com.simbio.don.app.ui.DonPalette
import com.simbio.don.app.ui.ThemeMode
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.runBlocking
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

/**
 * 실사용 검수: 6주쯤 쓴 사람의 기록(월급·고정비·날마다 지출·카드·적금·더치페이·계획·목표·지난달)으로
 * 주요 화면을 돌며 스크린샷을 남긴다 (-Pscreenshots). 화면이 뜨는지만 확인하고 모습은 사람이 본다.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class UsageTourTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()
    private var dark = false

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            UiPrefs.setThemeMode(appContext, if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            seed()
        }

        override fun after() {
            DonRepository.resetForTests()
            DonColors.palette = DonPalette.Light
            UiPrefs.setThemeMode(appContext, ThemeMode.SYSTEM)
        }
    }).around(rule)

    /** 지난 월급날(약 40일 전)부터 쓴 사람 */
    private fun seed() = runBlocking {
        val today = LocalDate.now()
        val start = today.minusDays(40)
        val repo = DonRepository.get(appContext)
        repo.completeOnboarding(
            3_200_000, start.dayOfMonth, listOf(FixedExpense("월세", 650_000, 5), FixedExpense("통신비", 55_000, 15)), 500_000, DayOfWeek.MONDAY, start, null,
            accounts = listOf(
                DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_400_000),
                DonRepository.AccountDraft("신용카드", AccountType.CARD, -120_000),
                DonRepository.AccountDraft("적금", AccountType.SAVINGS, 3_000_000),
            ),
        )
        val ids = DonDatabase.get(appContext).dao().accountsNow().associate { it.name to it.id }
        val bank = ids.getValue("생활비 통장")
        val card = ids.getValue("신용카드")
        val rnd = kotlin.random.Random(11)
        val places = listOf(
            Triple("김밥천국", 6_500L, Category.FOOD), Triple("스타벅스", 5_300L, Category.CAFE), Triple("배민", 21_000L, Category.DELIVERY),
            Triple("버스", 1_500L, Category.TRANSPORT), Triple("이마트", 43_000L, Category.LIVING), Triple("올리브영", 27_000L, Category.SHOPPING),
            Triple("CGV", 15_000L, Category.CULTURE), Triple("점심", 9_000L, Category.FOOD),
        )
        for (i in 0..40) {
            val d = start.plusDays(i.toLong())
            if (d.isAfter(today)) break
            if (i % 6 == 4) continue // 무지출 날
            repeat(1 + rnd.nextInt(3)) {
                val (label, base, cat) = places[rnd.nextInt(places.size)]
                repo.addManual(d, base + rnd.nextLong(0, 4) * 500, label, EntryType.SPEND, cat, accountId = if (rnd.nextBoolean()) card else bank)
            }
        }
        // 지난달 → 이번 달 넘어가기 (월급날)
        repo.dailyUpkeep(LocalDateTime.of(start.plusMonths(1), java.time.LocalTime.of(9, 0)))
        repo.dailyUpkeep(LocalDateTime.now())
        repo.addManual(start.plusMonths(1), 3_200_000, "월급", EntryType.INCOME, Category.SALARY, accountId = bank)
        // 더치페이: 내가 다 냄(4명) + 친구 한 명 입금, 남이 냄(보낼 돈)
        repo.addManual(today.minusDays(2), 96_000, "고깃집", EntryType.SPEND, Category.FOOD, accountId = card, share = Splits.myShare(96_000, 4), people = 4)
        repo.addManual(today.minusDays(1), 24_000, "김철수", EntryType.INCOME, Category.OTHER_INCOME, accountId = bank)
        repo.addManual(today.minusDays(1), 18_000, "회식 (팀장님이 냄)", EntryType.SPEND, Category.FOOD, owe = true)
        // 계획·목표
        repo.savePlan(PlanEntity(epochDay = today.plusDays(3).toEpochDay(), amount = 50_000, label = "엄마 생신", category = Category.EVENT.name))
        repo.addGoal("제주 여행", 800_000, "flight", start)
        val goal = DonDatabase.get(appContext).dao().goalsNow().single()
        repo.putIntoGoal(goal.id, 120_000, GoalSource.EXTRA, today)
    }

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        rule.waitForIdle()
        val bmp = rule.runOnUiThread {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(dir).apply { mkdirs() }.resolve("u_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 8_000)

    private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun 둘러보기() {
        waitText("이대로 쓰면")
        shot("01_home_top")
        listOf("이번 달 달력", "일주일 안에", "정산", "이번 달 요약", "모은 돈", "내 방", "이거 사도 될까?", "카테고리", "최근 내역").forEachIndexed { i, t ->
            rule.onAllNodesWithText(t).onFirst().runCatching { performScrollTo() }
            shot("0${2 + i}_home_${i + 2}")
        }

        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        shot("20_ledger_daily")
        rule.onNodeWithText("고깃집").performScrollTo().performClick()
        waitText("나눠 냈나요")
        rule.onNodeWithText("나눠 냈나요").performScrollTo()
        shot("20b_edit_split")
        back()
        waitText("일일")
        rule.onNodeWithText("달력").performClick()
        shot("21_ledger_calendar")
        rule.onNodeWithText("주별").performClick()
        shot("22_ledger_weekly")
        rule.onNodeWithText("월별").performClick()
        shot("23_ledger_monthly")
        rule.onNodeWithText("통계").performClick()
        rule.waitForIdle()
        shot("24_stats")
        rule.onNodeWithText("자산").performClick()
        waitText("순자산")
        shot("25_assets")

        rule.onNodeWithText("홈").performClick()
        waitText("이대로 쓰면")
        rule.onAllNodesWithText("정산 같은 돈", substring = true).onFirst().performScrollTo().performClick()
        waitText("이 돈, 정산인가요?")
        shot("30_split")
        back()
        waitText("이대로 쓰면")
        rule.onNodeWithText("따져 보기").performScrollTo().performClick()
        waitText("가격")
        shot("31_canbuy_empty")
        back()
        waitText("이대로 쓰면")
        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("나눠 냈나요")
        shot("32_entry_new")
        back()
        waitText("이대로 쓰면")
        rule.onNodeWithContentDescription("설정").performScrollTo().performClick()
        waitText("AI 도우미 (선택)")
        rule.onNodeWithText("AI 도우미 (선택)").performScrollTo()
        shot("33_settings_ai")
        rule.onAllNodesWithText("달력 + 잔디 위젯", substring = true).onFirst().performScrollTo()
        shot("34_settings_widgets")
    }

    @Test
    fun 둘러보기_어둡게() {
        dark = true
        UiPrefs.setThemeMode(appContext, ThemeMode.DARK)
        rule.activityRule.scenario.recreate()
        waitText("이대로 쓰면")
        rule.onAllNodesWithText("정산 같은 돈", substring = true).onFirst().performScrollTo()
        shot("40_dark_home_split")
        rule.onAllNodesWithText("정산 같은 돈", substring = true).onFirst().performClick()
        waitText("이 돈, 정산인가요?")
        shot("41_dark_split")
        back()
        waitText("이대로 쓰면")
        rule.onNodeWithText("따져 보기").performScrollTo().performClick()
        waitText("가격")
        shot("42_dark_canbuy")
    }
}
