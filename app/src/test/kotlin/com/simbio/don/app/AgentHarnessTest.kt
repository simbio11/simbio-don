package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.ai.AgentChat
import com.simbio.don.app.ai.AgentReply
import com.simbio.don.app.ai.AgentRequest
import com.simbio.don.app.ai.ChatLine
import com.simbio.don.app.ai.ToolCall
import com.simbio.don.app.ai.Turn
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.AgentAction
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.LedgerHarness
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.36: AI 대화 하네스 — 도구가 폰 안에서 가계부를 읽고, 보내면 안 되는 것은 안 보내고, 바꾸기는 제안만 */
@RunWith(RobolectricTestRunner::class)
class AgentHarnessTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private lateinit var harness: LedgerHarness
    private val now = LocalDateTime.of(2026, 11, 21, 20, 0)
    private val card = "com.shcard.smartpay"
    private var living = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_000_000, 25, listOf(FixedExpense("월세", 600_000, 1)), 400_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
        repo.saveRule(AppRule(card, "신한 SOL페이"))
        living = repo.saveAccount(AccountEntity(name = "토스 생활비 통장", type = AccountType.BANK.name, opening = 500_000, sortOrder = 0), today = LocalDate.of(2026, 10, 25))
        repo.saveAccount(AccountEntity(name = "신한카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = card), today = LocalDate.of(2026, 10, 25))
        repo.handleNotification(RawNotification(card, "신한카드(1234)승인", "홍*동 4,500원(일시불)11/20 12:10 김밥천국", LocalDateTime.of(2026, 11, 20, 12, 10)))
        repo.handleNotification(RawNotification(card, "신한카드(1234)승인", "홍*동 4,500원(일시불)11/18 12:30 김밥천국", LocalDateTime.of(2026, 11, 18, 12, 30)))
        repo.addManual(LocalDate.of(2026, 11, 19), 32_000, "고깃집", EntryType.SPEND, Category.FOOD, "비밀메모: 소개팅", living, null, 19 * 60, now, null)
        harness = LedgerHarness(repo) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tool(name: String, args: String = "{}") = harness.run(ToolCall("t", name, JSONObject(args)))

    @Test
    fun `기록 찾기와 합계 - 이름·금액·분류·통장은 보내고 알림 원문·메모는 안 보낸다`() = runTest {
        val found = tool("search_entries", """{"text":"김밥"}""")
        assertFalse(found.content, found.isError)
        val o = JSONObject(found.content)
        assertEquals(2, o.getInt("count"))
        assertEquals("김밥천국", o.getJSONArray("items").getJSONObject(0).getString("label"))
        assertEquals("2026-11-20", o.getJSONArray("items").getJSONObject(0).getString("date"))
        assertEquals("신한카드", o.getJSONArray("items").getJSONObject(0).getString("account"))

        val all = tool("search_entries").content
        listOf("비밀메모", "소개팅", "홍*동", "승인", "일시불", "com.shcard").forEach { assertFalse("'$it'이 나갔어요: $all", all.contains(it)) }
        assertTrue(all.contains("고깃집"))

        val t = JSONObject(tool("totals", """{"by":"category"}""").content)
        assertEquals(41_000L, t.getLong("total"))
        val food = (0 until t.getJSONArray("groups").length()).map { t.getJSONArray("groups").getJSONObject(it) }.first { it.getString("key") == "FOOD" }
        assertEquals(41_000L, food.getLong("sum"))
        assertEquals(3, food.getInt("count"))

        val ov = JSONObject(tool("overview").content)
        assertEquals("2026-10-25", ov.getString("month_from"))
        assertEquals(ov.getLong("living_budget") - 41_000L, ov.getLong("living_left"))
        assertEquals(41_000L, ov.getLong("living_spent"))
        assertTrue(tool("accounts").content.contains("토스 생활비 통장"))
    }

    @Test
    fun `바꾸는 도구는 제안만 - 기록은 그대로, 잘못된 인자는 오류 글로`() = runTest {
        val before = db.dao().allEntriesNow().size
        assertFalse(tool("propose_add", """{"date":"2026-11-21","amount":4500,"label":"김밥","type":"SPEND","category":"FOOD","account":"신한"}""").isError)
        val id = db.dao().allEntriesNow().first { it.label == "고깃집" }.id
        assertFalse(tool("propose_edit", """{"id":$id,"category":"ETC"}""").isError)
        assertFalse(tool("propose_delete", """{"id":$id,"reason":"중복"}""").isError)
        assertEquals(before, db.dao().allEntriesNow().size)
        assertEquals(listOf(AgentAction.Kind.ADD, AgentAction.Kind.EDIT, AgentAction.Kind.DELETE), harness.proposals.map { it.kind })
        val add = harness.proposals.first()
        assertEquals(4_500L, add.amount)
        assertEquals(Category.FOOD, add.category)
        assertEquals("신한카드", db.dao().accountsNow().first { it.id == add.accountId }.name)

        assertTrue(tool("propose_add", """{"date":"어제","amount":4500,"label":"x","type":"SPEND"}""").isError)
        assertTrue(tool("propose_edit", """{"id":999999,"amount":1}""").isError)
        assertTrue(tool("propose_add", """{"date":"2026-11-21","amount":4500,"label":"x","type":"SPEND","category":"없는분류"}""").isError)
        assertTrue(tool("nope").isError)
    }

    @Test
    fun `대화 한 판 - 도구를 부르고 결과를 돌려주고 답을 보여 준다, 제안은 줄로`() = runTest {
        val seen = mutableListOf<AgentRequest>()
        val chat = AgentChat(harness, { req ->
            seen += req
            when (seen.size) {
                1 -> AgentReply.Ok(Turn.Model("찾아볼게요", listOf(ToolCall("c1", "search_entries", JSONObject("""{"text":"김밥"}""")))))
                2 -> AgentReply.Ok(Turn.Model("", listOf(ToolCall("c2", "propose_add", JSONObject("""{"date":"2026-11-21","amount":4500,"label":"김밥천국","type":"SPEND","category":"FOOD"}""")))))
                else -> AgentReply.Ok(Turn.Model("김밥에 9,000원 썼고, 오늘 것도 적을 수 있게 제안했어요."), 100, 20)
            }
        }, { now.toLocalDate() })
        val lines = mutableListOf<ChatLine>()
        chat.ask("김밥에 얼마 썼어? 오늘 것도 적어 줘") { lines += it }
        assertEquals(3, seen.size)
        // 두 번째 요청엔 첫 도구 결과가 들어 있다
        val results = seen[1].turns.last() as Turn.Results
        assertEquals("c1", results.results.single().callId)
        assertTrue(results.results.single().content.contains("\"count\":2"))
        assertTrue(seen[0].system.contains("2026-11-21"))
        assertEquals(9, seen[0].tools.size)
        assertEquals("기록 찾음 · 2건", lines.filterIsInstance<ChatLine.Tool>().first().title)
        assertEquals(1, lines.filterIsInstance<ChatLine.Proposal>().size)
        assertTrue(lines.last() is ChatLine.Bot)
    }

    @Test
    fun `실패하면 그 질문은 대화에서 빼고 알린다`() = runTest {
        var n = 0
        val chat = AgentChat(harness, { req ->
            n++
            if (n == 1) AgentReply.Failed("인터넷 연결을 확인해 주세요") else AgentReply.Ok(Turn.Model("네 (${req.turns.size})"))
        }, { now.toLocalDate() })
        val lines = mutableListOf<ChatLine>()
        chat.ask("첫 질문") { lines += it }
        assertTrue(lines.single() is ChatLine.Problem)
        chat.ask("다시") { lines += it }
        // 실패한 첫 질문은 빠지고 새 질문 하나만
        assertEquals("네 (1)", (lines.last() as ChatLine.Bot).text)
    }
}
