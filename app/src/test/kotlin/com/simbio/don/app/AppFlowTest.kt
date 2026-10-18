package com.simbio.don.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.runBlocking
import com.simbio.don.app.data.DonDatabase
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Assert.assertEquals
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
    val rule = createAndroidComposeRule<MainActivity>()

    /** 테스트마다 새 메모리 DB로 시작한다 (화면이 뜨기 전에 설정해야 해서 바깥 규칙에서) */
    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
        }

        override fun after() = DonRepository.resetForTests()
    }).around(rule)


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
    private fun waitText(text: String, substring: Boolean = false) = rule.waitUntilAtLeastOneExists(hasText(text, substring = substring), 5_000)

    /**
     * DB 읽기. 메인 스레드에서 runBlocking으로 기다리면 앱 쪽 코루틴(메인에서 이어지며 쓰기 잠금을 쥔 것)과
     * 서로 기다려 멈출 수 있어, 다른 스레드에서 읽고 메인 루퍼를 돌리며 기다린다.
     */
    private fun <T> readDb(block: suspend com.simbio.don.app.data.DonDao.() -> T): T {
        val dao = DonDatabase.get(appContext).dao()
        return background { dao.block() }
    }

    /** 미리 기록 넣기 등: 다른 스레드에서 돌리고 메인 루퍼를 돌리며 기다린다 (위 [readDb]와 같은 까닭) */
    private val appContext: android.content.Context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private fun <T> background(block: suspend () -> T): T {
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var result: Result<T>? = null
        Thread { result = runCatching { runBlocking { block() } }; done.set(true) }.start()
        // 메인에서 이어질 앱 쪽 코루틴(쓰기 잠금을 쥔 것)이 돌 수 있게 메인 루퍼를 비워 가며 기다린다
        rule.waitUntil(15_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            done.get()
        }
        return result!!.getOrThrow()
    }

    @Test
    fun 초기_설정부터_지출_입력까지() {
        waitText("시작하기")
        shot("01_welcome")
        rule.onNodeWithText("시작하기").performClick()

        waitText("내 캐릭터")
        rule.onNodeWithText("여자").performClick()
        shot("01b_character")
        rule.onNodeWithText("다음").performClick()

        waitText("월급을 알려주세요")
        rule.onNodeWithText("월급 (세금 뗀 실수령액)").performTextReplacement("3300000")
        // 월급날을 열흘 전으로 두면 언제 돌려도 '한 달 중간에 시작' 흐름을 탄다
        rule.onNodeWithText("25").performTextReplacement(LocalDate.now().minusDays(10).dayOfMonth.toString())
        shot("02_salary")
        rule.onNodeWithText("다음").performClick()

        waitText("매달 나가는 고정비")
        rule.onAllNodesWithText("금액").onFirst().performTextReplacement("1000000")
        rule.onAllNodesWithText("날").onFirst().performTextReplacement("1")
        shot("03_fixed")
        rule.onNodeWithText("다음").performClick()

        waitText("먼저 떼어둘 저축")
        rule.onNodeWithText("매달 저축").performTextReplacement("320000")
        shot("04_savings")
        rule.onNodeWithText("다음").performClick()

        waitText("이번 달은 이미 시작됐어요")
        rule.onNodeWithText("통장 잔액으로").performClick()
        rule.onNodeWithText("지금 생활비 통장·현금 잔액").performTextReplacement("1500000")
        shot("05_this_month")
        rule.onNodeWithText("다음").performClick()

        waitText("자산 잔액 (선택)")
        shot("06_assets_setup")
        rule.onNodeWithText("다음").performClick()

        waitText("거의 다 됐어요")
        shot("07_finish")
        rule.onNodeWithText("이대로 시작하기").performClick()

        waitText("실제 내 돈 · 이번 주")
        assertEquals(com.simbio.don.game.Gender.FEMALE, readDb { houseNow() }?.look?.let { com.simbio.don.game.CharacterLook.decode(it).gender })
        rule.onNodeWithText("결제 알림 자동 기록이 꺼져 있어요").assertExists()
        rule.onNodeWithText("지난주 결산이 준비됐어요").assertDoesNotExist()

        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("내용 (어디에 썼나요)")
        rule.onNode(hasSetTextAction() and hasText("금액")).performTextReplacement("4500")
        rule.onNodeWithText("내용 (어디에 썼나요)").performTextReplacement("스타벅스")
        // 가게 이름으로 카테고리가 골라진다
        rule.onNode(hasText("카페·간식") and isSelected()).assertExists()
        shot("08_add_entry")
        rule.onNodeWithText("저장").performClick()

        waitText("−4,500원")
        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        shot("09_ledger_daily")
        rule.onNodeWithText("달력").performClick()
        shot("09b_ledger_calendar")
        rule.onNodeWithText("월별").performClick()
        shot("09c_ledger_monthly")
        rule.onNodeWithText("통계").performClick()
        waitText("카테고리별")
        rule.onNodeWithText("자산").performClick()
        waitText("순자산")
        shot("10_assets")
        rule.onNodeWithText("홈").performClick()
        rule.onNodeWithContentDescription("설정").performClick()
        waitText("알림 읽을 앱")
        shot("11_settings")
    }

    /** 2주쯤 쓴 상태를 만들어 화면을 찍는다. */
    @Test
    fun 기록이_쌓인_화면() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(
                salary = 3_300_000,
                payDay = today.minusDays(14).dayOfMonth,
                fixed = listOf(FixedExpense("월세", 700_000, today.minusDays(10).dayOfMonth), FixedExpense("통신·보험", 280_000, today.minusDays(3).dayOfMonth)),
                savings = 400_000,
                settlementDay = DayOfWeek.MONDAY,
                today = today.minusDays(14),
                firstCycleBudget = null,
                accounts = listOf(
                    DonRepository.AccountDraft("현금", AccountType.CASH, 80_000),
                    DonRepository.AccountDraft("생활비 통장", AccountType.BANK, 1_850_000),
                    DonRepository.AccountDraft("신용카드", AccountType.CARD, -320_000),
                ),
            )
            val ids = DonDatabase.get(appContext).dao().accountsNow().associate { it.name to it.id }
            val sample = listOf(
                Triple(14, 4_500L, "스타벅스"), Triple(14, 9_000L, "김밥천국"), Triple(13, 23_000L, "배달의민족"),
                Triple(13, 1_450L, "지하철"), Triple(12, 68_000L, "이마트"), Triple(12, 4_800L, "메가커피"),
                Triple(11, 12_000L, "카카오T 택시"), Triple(10, 39_000L, "무신사"), Triple(10, 8_500L, "GS25"),
                Triple(9, 15_000L, "CGV"), Triple(8, 27_000L, "OO치킨"), Triple(8, 4_500L, "스타벅스"),
                Triple(7, 13_000L, "약국"), Triple(6, 52_000L, "쿠팡"), Triple(5, 9_900L, "넷플릭스"),
                Triple(4, 6_000L, "동네빵집"), Triple(3, 31_000L, "배달의민족"), Triple(2, 4_500L, "스타벅스"),
                Triple(1, 18_000L, "국밥"), Triple(0, 3_200L, "CU"),
            )
            sample.forEachIndexed { i, (ago, amount, label) ->
                val account = if (i % 3 == 0) ids["현금"] else ids["신용카드"]
                repo.addManual(today.minusDays(ago.toLong()), amount, label, EntryType.SPEND, accountId = account, minuteOfDay = 9 * 60 + i * 37 % 600)
            }
            repo.addManual(today.minusDays(6), 100_000, "친구 결혼식", EntryType.EXCEPTION, Category.EVENT, accountId = ids["생활비 통장"])
            repo.addManual(today.minusDays(4), 150_000, "중고거래", EntryType.INCOME, Category.SIDE_INCOME, accountId = ids["생활비 통장"])
            repo.addManual(today.minusDays(2), 100_000, "ATM 출금", EntryType.TRANSFER, accountId = ids["생활비 통장"], toAccountId = ids["현금"])
            // 나가는 날이 지난 고정 지출은 앱을 켤 때 적힌다
            repo.dailyUpkeep(java.time.LocalDateTime.now())
        }
        waitText("실제 내 돈 · 이번 주")
        shot("12_home")
        rule.onNodeWithText("최근 내역").performScrollTo()
        shot("13_home_bottom")
        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        shot("14_ledger_daily")
        rule.onNodeWithText("달력").performClick()
        shot("15_ledger_calendar")
        rule.onNodeWithText("주별").performClick()
        shot("16_ledger_weekly")
        rule.onNodeWithText("통계").performClick()
        waitText("카테고리별")
        shot("17_stats")
        rule.onNodeWithText("생활비 쓰는 속도").performScrollTo()
        shot("18_stats_pace")
        rule.onNodeWithText("자산").performClick()
        waitText("순자산")
        shot("19_assets")
        rule.onNodeWithText("생활비 통장").performClick()
        waitText("이체 적기")
        waitText("ATM 출금")
        shot("19b_account")
        rule.onNodeWithContentDescription("뒤로").performClick()
        rule.onNodeWithText("홈").performClick()
        waitText("결산 보기")
        rule.onNodeWithText("결산 보기").performClick()
        waitText("지난주 결산")
        shot("19c_settlement")
    }

    /** 모의 쇼핑 → 결제 → 결산 → 집. */
    @Test
    fun 모의_쇼핑과_집() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(3_300_000, today.minusDays(9).dayOfMonth, listOf(FixedExpense("월세", 700_000)), 400_000, DayOfWeek.MONDAY, today.minusDays(9), null)
            val bought = listOf(
                "나이키 에어포스1" to 129_000L, "오버핏 맨투맨" to 39_000L, "뉴발란스 530" to 109_000L, "에어팟 프로" to 359_000L,
                "레고 테크닉" to 89_000L, "황금올리브 치킨" to 23_000L, "아이스 아메리카노" to 4_500L, "엽떡" to 18_000L,
                "가디건" to 49_000L, "아디다스 삼바" to 139_000L, "조던 1" to 179_000L, "샌들" to 29_000L, "향수" to 120_000L,
            )
            bought.forEachIndexed { i, (n, p) ->
                repo.buyMock(n, p, com.simbio.don.game.ItemKind.guess(n), "쿠팡", today.minusDays((9 - i % 6).toLong()), java.time.LocalDateTime.now())
            }
            // 지난 주들이 결산돼 집에 도착한 상태로 만든다
            val dao = DonDatabase.get(appContext).dao()
            dao.updateMocks(dao.mockPurchasesNow().map { it.copy(status = com.simbio.don.game.PurchaseStatus.DELIVERED.name) })
            repo.buyMock("스타벅스 텀블러", 35_000, com.simbio.don.game.ItemKind.LIVING, "스타벅스", today, java.time.LocalDateTime.now())
        }
        waitText("실제 내 돈 · 이번 주")
        rule.onNodeWithText("내 집").performClick()
        waitText("모의 지갑")
        shot("20_house")
        rule.onNodeWithText("모의 지갑").performScrollTo()
        shot("20b_house_bottom")
        rule.onAllNodesWithText("모의 쇼핑").onFirst().performScrollTo().performClick()
        // 돈't 몰: 둘러보고 장바구니에 담아 결제
        waitText("많이 담는 물건")
        shot("47_mall")
        rule.onAllNodesWithText("무선 이어폰").onFirst().performScrollTo().performClick()
        waitText("실제로 산다면")
        shot("48_product")
        rule.onNodeWithText("장바구니").performClick()
        waitText("장바구니에 담았어요")
        rule.onNodeWithContentDescription("장바구니 1개").performClick()
        waitText("결제 후 모의 지갑")
        shot("49_cart")
        rule.onNodeWithText("199,000원 모의 결제하기").performClick()
        waitText("모의 결제 완료!")
        rule.onNodeWithText("계속 둘러보기").performClick()
        // 찾는 게 없으면 직접 적기
        rule.onNodeWithText("찾는 게 없으면 직접 적어서 모의로 사기").performScrollTo().performClick()
        waitText("뭘 사고 싶어요?")
        rule.onNodeWithText("물건 이름").performTextReplacement("나이키 덩크 로우")
        rule.onNode(hasSetTextAction() and hasText("가격")).performTextReplacement("139000")
        shot("21_shop_form")
        rule.onNodeWithText("결제하러 가기").performClick()
        waitText("주문 / 결제")
        shot("22_checkout")
        rule.onNodeWithText("139,000원 결제하기").performClick()
        waitText("결제 완료!")
        shot("23_paid")
        rule.onNodeWithText("내 집 보기").performClick()
        waitText("배송 중 3개")
        rule.onNodeWithText("배송 중 3개").performScrollTo()
        shot("24_house_shipping")
    }

    /** 캐릭터 꾸미기: 부위·색을 바꿔 저장하면 집에 그 모습으로 산다 */
    @Test
    fun 캐릭터_꾸미기() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(3_300_000, today.minusDays(9).dayOfMonth, emptyList(), 400_000, DayOfWeek.MONDAY, today.minusDays(9), null)
            // 원피스·비니는 옷장에서 마일로 산다
            DonDatabase.get(appContext).dao().insertMileTx(com.simbio.don.app.data.MileTxEntity(epochDay = today.toEpochDay(), amount = 2_000, kind = "EARN"))
        }
        waitText("실제 내 돈 · 이번 주")
        rule.onNodeWithText("내 집").performClick()
        waitText("옷장·뷰티샵")
        rule.onNodeWithText("옷장·뷰티샵").performScrollTo().performClick()
        waitText("옷장 · 뷰티샵")
        rule.onNodeWithText("바뀐 게 없어요").assertExists()
        shot("25_dress_face")
        rule.onNodeWithText("눈").performClick()
        rule.onNodeWithText("왕눈").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithContentDescription("초록").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        shot("26_dress_eyes")
        rule.onNodeWithText("머리").performClick()
        rule.onNodeWithText("긴 생머리").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithContentDescription("금발").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        shot("27_dress_hair")
        rule.onNodeWithText("옷").performClick()
        rule.onNodeWithText("원피스").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitText("원피스를 입으면 아래옷은 안 보여요.")
        shot("28_dress_clothes")
        rule.onNodeWithText("모자·소품").performClick()
        rule.onNodeWithText("비니").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("슬퍼요").performScrollTo().performClick()
        shot("29_dress_hat")
        waitText("첫 꾸미기라 공짜예요!")
        rule.onNodeWithText("1,500마일로 바꾸기").performSemanticsAction(SemanticsActions.OnClick)
        waitText("모의 지갑")
        // 저장은 비동기라 기다렸다 읽는다
        rule.waitUntil(5_000) { readDb { houseNow() }?.look?.contains("hair=long") == true }
        val look = readDb { houseNow() }!!.look.let { com.simbio.don.game.CharacterLook.decode(it) }
        assertEquals("big", look.eyes)
        assertEquals("초록", look.eyeColor)
        assertEquals("long", look.hair)
        assertEquals("금발", look.hairColor)
        assertEquals("dress", look.top)
        assertEquals("beanie", look.hat)
        shot("30_house_dressed")
    }

    /** 찜하기 · 내역 검색 · 자주 쓰는 곳 */
    @Test
    fun 찜하기와_검색() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(3_300_000, today.minusDays(5).dayOfMonth, listOf(FixedExpense("월세", 700_000)), 400_000, DayOfWeek.MONDAY, today.minusDays(5), null)
            listOf(4_500L to "스타벅스", 9_000L to "김밥천국", 5_000L to "스타벅스", 3_200L to "CU").forEachIndexed { i, (a, l) ->
                repo.addManual(today.minusDays(i.toLong() % 3), a, l, EntryType.SPEND)
            }
            repo.wishMock("아이패드 에어", 899_000, com.simbio.don.game.ItemKind.ELECTRONICS, "쿠팡", java.time.LocalDateTime.now().minusHours(25))
            repo.wishMock("니트 가디건", 59_000, com.simbio.don.game.ItemKind.CLOTHES, null, java.time.LocalDateTime.now().minusHours(3))
        }
        waitText("찜한 물건, 아직도 사고 싶어요?")
        shot("30_home_wish")
        rule.onNodeWithText("보러 가기").performClick()
        waitText("찜 목록 2개")
        rule.onNodeWithText("찜 목록 2개").performScrollTo()
        shot("31_wishlist")
        rule.onAllNodesWithText("안 살래요").onFirst().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitText("안 산 돈 899,000원")

        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        rule.onNodeWithContentDescription("검색").performClick()
        rule.onNodeWithContentDescription("내용·메모·금액으로 찾기").performTextInput("스타")
        waitText("2건 · 지출 9,500원")
        shot("32_search")
        rule.onNodeWithContentDescription("뒤로").performClick()

        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("자주 쓰는 곳")
        rule.onNodeWithText("스타벅스 · 4,500원").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(hasText("카페·간식") and isSelected()).assertExists()
        shot("33_add_quick")
        rule.onNodeWithContentDescription("뒤로").performClick()

        rule.onNodeWithText("내 집").performClick()
        rule.onAllNodesWithText("모의 쇼핑").onFirst().performScrollTo().performClick()
        waitText("많이 담는 물건")
        rule.onNodeWithText("찾는 게 없으면 직접 적어서 모의로 사기").performScrollTo().performClick()
        waitText("뭘 사고 싶어요?")
        rule.onNodeWithText("물건 이름").performTextReplacement("에어팟 맥스")
        rule.onNode(hasSetTextAction() and hasText("가격")).performTextReplacement("769000")
        rule.onNodeWithText("찜하기").performClick()
        waitText("찜했어요!")
        shot("34_wished")
    }

    /** 0.10 가계부 더: 할부·소분류 입력, 분류 관리, 반복 수입, 연간 통계, 앱 잠금 */
    @Test
    fun 가계부_더_채우기() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(3_300_000, today.minusDays(9).dayOfMonth, listOf(FixedExpense("월세", 700_000)), 400_000, DayOfWeek.MONDAY, today.minusDays(9), null)
        }
        waitText("실제 내 돈 · 이번 주")

        // 할부 + 소분류
        rule.onNodeWithContentDescription("가계부 쓰기").performClick()
        waitText("내용 (어디에 썼나요)")
        rule.onNode(hasSetTextAction() and hasText("금액")).performTextReplacement("1200000")
        rule.onNodeWithText("내용 (어디에 썼나요)").performTextReplacement("노트북")
        rule.onNodeWithText("쇼핑").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("전자기기").performSemanticsAction(SemanticsActions.OnClick)
        // 칩이 아래 버튼 줄에 가려질 수 있어 누르기 동작을 바로 보낸다
        rule.onNodeWithText("3개월").performSemanticsAction(SemanticsActions.OnClick)
        waitText("달마다 400,000원씩 3번, 오늘부터 매달 ${today.dayOfMonth}일에 적어요.")
        rule.onNodeWithText("달마다 400,000원씩 3번, 오늘부터 매달 ${today.dayOfMonth}일에 적어요.").performScrollTo()
        shot("35_add_installment")
        rule.onNodeWithText("저장").performClick()
        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        waitText("쇼핑 > 전자기기 · 할부 1/3", substring = true)
        shot("36_ledger_installment")
        assertEquals(3, readDb { allEntriesNow() }.count { it.installmentId != null })

        // 분류 관리: 소분류 보기 + 직접 만들기
        rule.onNodeWithText("홈").performClick()
        rule.onNodeWithContentDescription("설정").performClick()
        waitText("알림 읽을 앱")
        rule.onNodeWithText("분류 관리 (소분류 · 직접 만들기)").performScrollTo().performClick()
        waitText("분류 관리")
        rule.onNodeWithText("식비").performClick()
        waitText("+ 소분류")
        shot("37_categories")
        // 분류 만들기·소분류 고치기는 대화상자라 화면 테스트 대신 LedgerExtrasRepoTest에서 본다
        // (Robolectric에서 대화상자를 열면 Compose가 쉬지 않는다)
        rule.onNodeWithContentDescription("뒤로").performClick()

        // 반복 수입
        waitText("알림 읽을 앱")
        rule.onNodeWithText("반복 수입 (부수입·용돈 등)").performScrollTo().performClick()
        waitText("+ 반복 수입 추가")
        rule.onNodeWithText("이름").performTextReplacement("과외비")
        rule.onNodeWithText("날").performTextReplacement("27")
        rule.onNode(hasSetTextAction() and hasText("금액")).performTextReplacement("400000")
        shot("38_recurring_income")
        rule.onNodeWithText("저장").performClick()
        waitText("반복 수입 1개")
        assertEquals(listOf("과외비"), readDb { recurringIncomesNow() }.map { it.name })

        // 앱 잠금 켜기: 암호 두 번
        rule.onNodeWithContentDescription("앱 잠금").performScrollTo().performClick()
        waitText("새 암호 4자리")
        "1234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        waitText("한 번 더 넣어 주세요")
        "1234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        waitText("암호 바꾸기")
        rule.onNodeWithContentDescription("뒤로").performClick()

        // 연간 통계 (홈에도 '통계' 글자가 있어 가계부 탭을 거쳐 간다)
        rule.onNodeWithText("가계부").performClick()
        waitText("일일")
        rule.onNodeWithText("통계").performClick()
        waitText("연간")
        rule.onNodeWithText("연간").performClick()
        waitText("달마다")
        shot("39_year_stats")

        // 다시 열면 잠겨 있다
        rule.activityRule.scenario.recreate()
        waitText("암호를 넣어 주세요")
        "0000".forEach { rule.onNodeWithText(it.toString()).performClick() }
        waitText("암호가 달라요 (4번 남음)")
        shot("40_lock")
        "1234".forEach { rule.onNodeWithText(it.toString()).performClick() }
        waitText("실제 내 돈 · 이번 주")
    }

    /** 0.11 M2: 마일 카드 · 마일 키우기 · 가구점(이사·수납) · 카드 · 옷장·뷰티샵 */
    @Test
    fun 마일과_가게() {
        val today = LocalDate.now()
        val repo = DonRepository.get(appContext)
        background {
            repo.completeOnboarding(3_300_000, today.minusDays(9).dayOfMonth, listOf(FixedExpense("월세", 700_000)), 400_000, DayOfWeek.MONDAY, today.minusDays(9), null)
            val dao = DonDatabase.get(appContext).dao()
            dao.insertMileTx(com.simbio.don.app.data.MileTxEntity(epochDay = today.toEpochDay(), amount = 21_000, kind = "EARN", memo = "10월 1주 25% 아낌", refKey = "t#1:EARN"))
            repo.buyMock("나이키 에어포스1", 129_000, com.simbio.don.game.ItemKind.SHOES, "쿠팡", today.minusDays(3), java.time.LocalDateTime.now())
            repo.buyMock("아이스 아메리카노", 4_500, com.simbio.don.game.ItemKind.DRINK, null, today.minusDays(2), java.time.LocalDateTime.now())
            dao.updateMocks(dao.mockPurchasesNow().mapIndexed { i, m -> m.copy(status = com.simbio.don.game.PurchaseStatus.DELIVERED.name, grade = if (i == 0) "LEGEND" else "RARE") })
        }
        waitText("실제 내 돈 · 이번 주")
        rule.onNodeWithText("내 집").performClick()
        waitText("21,000마일")
        rule.onNodeWithText("21,000마일").performScrollTo()
        shot("41_house_miles")

        // 마일 키우기
        rule.onNodeWithText("마일 키우기").performScrollTo().performClick()
        waitText("지갑")
        rule.onNode(hasSetTextAction() and hasText("마일")).performTextReplacement("5000")
        rule.onNodeWithText("넣기").performClick()
        waitText("키우기에 넣음")
        shot("42_miles")
        rule.onNodeWithContentDescription("뒤로").performClick()

        // 가구점: 이사 + 수납 가구
        waitText("가구점·이사")
        rule.onNodeWithText("가구점·이사").performScrollTo().performClick()
        waitText("투룸으로 이사하기")
        shot("43_furniture_move")
        rule.onNodeWithText("투룸으로 이사하기").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { readDb { houseNow() }?.level == "TWO_ROOM" }
        // 아파트 미리보기
        rule.onNodeWithText("아파트").performScrollTo().performClick()
        shot("43b_furniture_apartment")
        rule.onNodeWithText("수납 가구").performClick()
        waitText("+3칸 · 800마일")
        shot("44_furniture_storage")
        // 인테리어: 색을 눌러 미리보기
        rule.onNodeWithText("인테리어").performClick()
        rule.onNodeWithContentDescription("벽지 민트").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithContentDescription("러그 빨강").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitText("1,500마일로 민트 사기")
        shot("44b_furniture_interior")
        rule.onNodeWithContentDescription("뒤로").performClick()

        // 카드
        waitText("카드 2")
        rule.onNodeWithText("카드 2").performScrollTo().performClick()
        waitText("카드 2장")
        shot("45_cards")
        rule.onNodeWithContentDescription("뒤로").performClick()

        // 옷장·뷰티샵: 없는 옷은 마일 값, 첫 꾸미기는 공짜
        waitText("옷장·뷰티샵")
        rule.onNodeWithText("옷장·뷰티샵").performScrollTo().performClick()
        waitText("옷장 · 뷰티샵")
        rule.onNodeWithText("눈").performClick()
        rule.onNodeWithText("왕눈").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitText("첫 꾸미기라 공짜예요!")
        rule.onNodeWithText("옷").performClick()
        rule.onNodeWithText("니트").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        waitText("옷장에서 새로 사는 것 600마일")
        shot("46_dress_costs")
        rule.onNodeWithText("600마일로 바꾸기").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { readDb { houseNow() }?.owned?.contains("top:knit") == true }
        val h = readDb { houseNow() }!!
        assertEquals("big", com.simbio.don.game.CharacterLook.decode(h.look).eyes)
        assertEquals(false, h.freeMakeover)
    }
}
