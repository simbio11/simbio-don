package com.simbio.don.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.ai.Ai
import com.simbio.don.app.ai.AiBackend
import com.simbio.don.app.ai.AiModel
import com.simbio.don.app.ai.AiReply
import com.simbio.don.app.ai.AiRequest
import com.simbio.don.app.ai.AiTasks
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.ui.toDraft
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** 0.17: AI 도우미 — 보낼 내용(합계 숫자만), 답 읽기, 키 보관 */
@RunWith(RobolectricTestRunner::class)
class AiTasksTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 25)

    @Test
    fun `점검에 보내는 요약에는 가게 이름·메모·계좌 이름이 없다`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, DonDatabase::class.java).allowMainThreadQueries().build()
        val repo = DonRepository(db.dao(), db)
        val start = LocalDate.of(2026, 10, 23)
        repo.completeOnboarding(
            3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 500_000, DayOfWeek.MONDAY, start,
            accounts = listOf(DonRepository.AccountDraft("비밀통장", AccountType.BANK, 1_000_000)),
        )
        val bank = db.dao().accountsNow().single().id
        repo.addManual(LocalDate.of(2026, 10, 24), 32_000, "비밀가게", EntryType.SPEND, Category.FOOD, memo = "비밀메모", accountId = bank)
        repo.addGoal("제주 여행", 1_000_000, "flight", start)
        val home = repo.home(flowOf(LocalDateTime.of(2026, 10, 25, 12, 0))).first()!!
        val s = AiTasks.summary(home)
        assertFalse(s, s.contains("비밀"))
        assertTrue(s, s.contains("월급 3,300,000원"))
        assertTrue(s, s.contains("식비 32,000원"))
        assertTrue(s, s.contains("제주 여행"))
        // 점검 요청의 사용자 글은 요약 그대로
        assertEquals(s, AiTasks.coachRequest(s).user)
        db.close()
    }

    @Test
    fun `말로 적기는 친 문장만 보내고 답은 기록으로 바꾼다`() {
        val cats = Category.spending + Category.incomes
        val req = AiTasks.spokenRequest("  어제 친구 셋이랑 고깃집 8만원 내가 다 냄 ", today, cats)
        assertEquals("어제 친구 셋이랑 고깃집 8만원 내가 다 냄", req.user)
        assertTrue(req.quick)
        assertTrue(req.system.contains("2026-10-25"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(10, (req.schema!!["required"] as List<String>).size)

        val e = AiTasks.parseSpoken(
            """{"date":"2026-10-24","amount":80000,"label":"고깃집","kind":"spend","category":"FOOD","split":"i_paid","people":4,"my_share":0,"memo":"","unsure":""}""",
            today, cats,
        )!!
        assertEquals(LocalDate.of(2026, 10, 24), e.date)
        assertEquals(Category.FOOD, e.category)
        val d = e.toDraft()
        assertEquals(20_000L, d.share)
        assertEquals(4, d.people)
        assertFalse(d.owe)

        // 남이 냄 → 보낼 돈
        val owe = AiTasks.parseSpoken(
            """{"date":"2026-10-23","amount":25000,"label":"회식","kind":"spend","category":"FOOD","split":"they_paid","people":5,"my_share":25000,"memo":"","unsure":""}""",
            today, cats,
        )!!.toDraft()
        assertTrue(owe.owe)
        assertNull(owe.share)

        // 이상한 날짜는 오늘, 수입 분류는 수입끼리, 금액이 없으면 못 읽음
        val inc = AiTasks.parseSpoken(
            """{"date":"1999-01-01","amount":3300000,"label":"월급","kind":"income","category":"FOOD","split":"i_paid","people":1,"my_share":0,"memo":"","unsure":""}""",
            today, cats,
        )!!
        assertEquals(today, inc.date)
        assertEquals(Category.OTHER_INCOME, inc.category)
        assertEquals("none", inc.split)
        assertNull(AiTasks.parseSpoken("""{"amount":0}""", today, cats))
        assertNull(AiTasks.parseSpoken("not json", today, cats))
    }

    @Test
    fun `점검 답을 읽는다`() {
        val r = AiTasks.parseCoach("""{"headline":"잘 가고 있어요","pace":"하루 2만원 페이스","good":["무지출 3일"],"watch":[],"actions":["주말 외식 3만원 안으로"]}""")!!
        assertEquals("잘 가고 있어요", r.headline)
        assertEquals(listOf("주말 외식 3만원 안으로"), r.actions)
        assertNull(AiTasks.parseCoach("""{"headline":""}"""))
    }

    @Test
    fun `키는 이 폰에 두고 가려서 보여 주며 지우면 꺼진다`() {
        assertFalse(Ai.isOn(context))
        Ai.setKey(context, "  sk-ant-api03-abcdefghijklmnop1234  ")
        assertTrue(Ai.isOn(context))
        assertEquals("sk-ant-api03-abcdefghijklmnop1234", Ai.key(context))
        assertEquals("••••1234", Ai.maskedKey(context))
        assertEquals(AiModel.OPUS.id, Ai.model(context))
        Ai.setModel(context, AiModel.HAIKU.id)
        assertEquals(AiModel.HAIKU.id, Ai.model(context))
        Ai.clear(context)
        assertFalse(Ai.isOn(context))
        val reply = runBlocking { Ai.send(context, AiRequest("s", "u")) }
        assertTrue(reply is AiReply.Failed)
    }

    @Test
    fun `AI 회사마다 키·모델을 따로 두고, 예전 Claude 설정은 그대로 읽는다`() {
        // 0.35까지: Claude 키는 "key", 모델은 "model"에 AiModel 이름
        context.getSharedPreferences("ai", android.content.Context.MODE_PRIVATE).edit().putString("key", "p:sk-ant-old-key-123456").putString("model", "SONNET").commit()
        assertEquals(com.simbio.don.app.ai.AiProvider.CLAUDE, Ai.provider(context))
        assertEquals("sk-ant-old-key-123456", Ai.key(context))
        assertEquals(AiModel.SONNET.id, Ai.model(context))
        assertEquals("Claude · Sonnet 5.5", Ai.label(context))

        Ai.setProvider(context, com.simbio.don.app.ai.AiProvider.GEMINI)
        assertFalse(Ai.isOn(context))
        Ai.setKey(context, "AIzaSyTestKey000000")
        assertTrue(Ai.isOn(context))
        assertEquals("gemini-4-hydrogen", Ai.model(context))
        Ai.setModel(context, "gemini-4-neon")
        assertEquals("Gemini · gemini-4-neon", Ai.label(context))
        // 회사가 끄는 옛 모델을 골라 뒀으면 같은 자리의 새 모델로
        Ai.setModel(context, "gemini-2.5-pro")
        assertEquals("gemini-4-carbon", Ai.model(context))
        Ai.setModel(context, "gpt-5-mini", com.simbio.don.app.ai.AiProvider.OPENAI)
        assertEquals("gpt-6.2-sol", Ai.model(context, com.simbio.don.app.ai.AiProvider.OPENAI))
        // Claude 키는 그대로
        assertEquals("sk-ant-old-key-123456", Ai.key(context, com.simbio.don.app.ai.AiProvider.CLAUDE))

        // 호환: 주소와 모델이 있어야 켜진다
        Ai.setProvider(context, com.simbio.don.app.ai.AiProvider.COMPATIBLE)
        Ai.setKey(context, "sk-or-v1-test")
        assertFalse(Ai.isOn(context))
        Ai.setBaseUrl(context, "https://openrouter.ai/api/v1/")
        Ai.setModel(context, "deepseek/deepseek-chat")
        assertTrue(Ai.isOn(context))
        assertEquals("https://openrouter.ai/api/v1", Ai.baseUrl(context))
    }
}

/** 화면: 홈 AI 카드 → 말로 적기 → 확인 후 저장 (가짜 AI로) */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class AiFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()
    private val sent = mutableListOf<AiRequest>()

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
            Ai.setKey(appContext, "sk-ant-api03-test-key-0000000000")
            Ai.backendForTests = AiBackend { _, req ->
                sent += req
                if (req.schema?.containsKey("properties") == true && req.quick) {
                    AiReply.Ok(
                        """{"date":"${LocalDate.now()}","amount":80000,"label":"고깃집","kind":"spend","category":"FOOD","split":"i_paid","people":4,"my_share":0,"memo":"","unsure":""}""",
                        812, 64,
                    )
                } else {
                    AiReply.Ok("""{"headline":"이번 달은 여유 있어요","pace":"하루 평균이 예산보다 낮아요","good":["고정비를 미리 떼 뒀어요"],"watch":["주말 외식"],"actions":["이번 주말 외식은 3만원 안으로"]}""", 1200, 300)
                }
            }
        }

        override fun after() {
            Ai.backendForTests = null
            Ai.clear(appContext)
            DonRepository.resetForTests()
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
    fun 말로_적고_점검받기() {
        waitText("이대로 쓰면")
        rule.onNodeWithText("AI 도우미").performScrollTo()
        rule.onNodeWithText("말로 적기").performScrollTo().performClick()
        waitText("보낼 내용")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("오늘 친구 셋이랑 고깃집 8만원 내가 다 냄")
        rule.onNodeWithText("보기").performClick()
        // 보낼 내용 = 친 문장 그대로
        waitText("오늘 친구 셋이랑 고깃집 8만원 내가 다 냄")
        shot("a1_spoken_preview")
        rule.onNodeWithText("AI로 정리하기").performScrollTo().performClick()
        waitText("이렇게 적을까요?")
        assertEquals("오늘 친구 셋이랑 고깃집 8만원 내가 다 냄", sent.single().user)
        rule.onNodeWithText("이대로 적기 (확인 후 저장)").performScrollTo()
        shot("a2_spoken_result")
        rule.onNodeWithText("이대로 적기 (확인 후 저장)").performClick()
        waitText("나눠 냈나요")
        waitText("받을 돈 60,000원", substring = true)
        rule.onNodeWithText("저장").performClick()
        rule.waitUntil(5_000) { db { allEntriesNow() }.any { it.label == "고깃집" } }
        val e = db { allEntriesNow() }.single { it.label == "고깃집" }
        assertEquals(80_000L, e.amount)
        assertEquals(20_000L, e.share)
        assertEquals(Category.FOOD.name, e.category)

        // 이번 달 점검: 합계만 보낸다
        rule.onNodeWithText("이번 달 점검").performClick()
        rule.onNodeWithText("점검 받기").performScrollTo().performClick()
        waitText("이번 달은 여유 있어요")
        val coach = sent.last()
        assertFalse(coach.user.contains("고깃집"))
        assertTrue(coach.user.contains("식비"))
        assertNotNull(coach.schema)
        shot("a3_coach")
    }
}

/** 화면: 두 손가락으로 위로 쓸어 올리면 AI 대화 → 시키기 → 제안 [적용] (가짜 AI로) */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class ChatFlowTest {
    private val appContext: android.content.Context get() = ApplicationProvider.getApplicationContext()
    val rule = createAndroidComposeRule<MainActivity>()
    private val seen = mutableListOf<com.simbio.don.app.ai.AgentRequest>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            DonDatabase.inMemoryForTests = true
            DonRepository.resetForTests()
            val start = LocalDate.now().minusDays(3)
            runBlocking {
                DonRepository.get(appContext).completeOnboarding(3_300_000, start.dayOfMonth, listOf(FixedExpense("월세", 700_000)), 500_000, DayOfWeek.MONDAY, start, null)
            }
            Ai.setProvider(appContext, com.simbio.don.app.ai.AiProvider.GEMINI)
            Ai.setKey(appContext, "AIzaTestKey0000")
            Ai.backendForTests = object : AiBackend {
                override suspend fun send(model: String, req: AiRequest): AiReply = AiReply.Failed("안 씀")
                override suspend fun turn(model: String, req: com.simbio.don.app.ai.AgentRequest): com.simbio.don.app.ai.AgentReply {
                    seen += req
                    return if (req.turns.last() is com.simbio.don.app.ai.Turn.User) {
                        com.simbio.don.app.ai.AgentReply.Ok(
                            com.simbio.don.app.ai.Turn.Model(
                                "",
                                listOf(com.simbio.don.app.ai.ToolCall("c1", "propose_add", org.json.JSONObject("""{"date":"${LocalDate.now()}","amount":4500,"label":"김밥","type":"SPEND","category":"FOOD"}"""))),
                            ),
                        )
                    } else {
                        com.simbio.don.app.ai.AgentReply.Ok(com.simbio.don.app.ai.Turn.Model("오늘 김밥 4,500원을 적을 수 있게 제안했어요. [적용]을 눌러 주세요."))
                    }
                }
            }
        }

        override fun after() {
            Ai.backendForTests = null
            Ai.clear(appContext)
            Ai.setProvider(appContext, com.simbio.don.app.ai.AiProvider.CLAUDE)
            DonRepository.resetForTests()
        }
    }).around(rule)

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

    private fun shot(name: String) {
        val dir = System.getProperty("screenshotDir") ?: return
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun 두_손가락으로_올려_대화하고_제안을_적용한다() {
        waitText("이대로 쓰면")
        rule.onRoot().performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(width * 0.4f, height * 0.75f))
            down(1, androidx.compose.ui.geometry.Offset(width * 0.6f, height * 0.75f))
            repeat(12) {
                updatePointerBy(0, androidx.compose.ui.geometry.Offset(0f, -30f))
                updatePointerBy(1, androidx.compose.ui.geometry.Offset(0f, -30f))
                move()
            }
            up(0)
            up(1)
        }
        waitText("무엇이든 물어보거나 시켜 보세요")
        waitText("Gemini", substring = true)
        shot("c1_chat_empty")
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("오늘 점심 김밥 4500원 적어 줘")
        rule.onNodeWithText("보내기").performClick()
        waitText("오늘 김밥 4,500원을 적을 수 있게 제안했어요", substring = true)
        // 제안만 — 아직 안 적었다
        assertTrue(db { allEntriesNow() }.none { it.label == "김밥" })
        assertEquals("오늘 점심 김밥 4500원 적어 줘", (seen.first().turns.first() as com.simbio.don.app.ai.Turn.User).text)
        shot("c2_chat_proposal")
        rule.onNodeWithText("적용").performClick()
        waitText("✓ 적용했어요")
        rule.waitUntil(5_000) { db { allEntriesNow() }.any { it.label == "김밥" } }
        val e = db { allEntriesNow() }.single { it.label == "김밥" }
        assertEquals(4_500L, e.amount)
        assertEquals(Category.FOOD.name, e.category)
        shot("c3_chat_applied")
    }
}
