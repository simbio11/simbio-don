package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.pc.PcChange
import com.simbio.don.app.pc.PcChanges
import com.simbio.don.app.pc.PcInput
import com.simbio.don.app.pc.PcLink
import com.simbio.don.app.pc.PcSnapshot
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 0.35 PC로 보내기 — 생활관리 서버 계약(format dont-pc-snapshot v1)대로 만들고, 알림 글·메모는 빼는지.
 * 만든 스냅샷은 build/pc-snapshot-sample.json 으로도 남긴다 (생활관리 서버 쪽 계약 확인용).
 */
@RunWith(RobolectricTestRunner::class)
class PcSyncTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val d = LocalDate.of(2026, 11, 16)
    private val at = OffsetDateTime.of(2026, 11, 16, 21, 40, 0, 0, ZoneOffset.ofHours(9))
    private var bank = 0L
    private var savings = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_200_000, 25, listOf(FixedExpense("월세", 500_000)), 500_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 25))
        bank = repo.saveAccount(AccountEntity(name = "카카오뱅크", type = AccountType.BANK.name, opening = 1_000_000, sortOrder = 0))
        savings = repo.saveAccount(AccountEntity(name = "적금", type = AccountType.SAVINGS.name, opening = 0, sortOrder = 1), today = LocalDate.of(2026, 10, 25))
        repo.saveAccount(AccountEntity(name = "숨긴 통장", type = AccountType.BANK.name, opening = 7, sortOrder = 2, hidden = true))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `연결 링크 읽기`() {
        assertEquals("http://100.72.99.70:9100" to "abcdefghijklmnop1234",
            PcLink.parse("lifeapp://pair?u=http%3A//100.72.99.70%3A9100&t=abcdefghijklmnop1234"))
        assertNull(PcLink.parse("https://example.com/?u=x&t=abcdefghijklmnop1234"))
        assertNull(PcLink.parse("lifeapp://pair?u=http%3A//x&t=short"))
        assertNull(PcLink.parse("lifeapp://pair?u=javascript%3Aalert(1)&t=abcdefghijklmnop1234"))
    }

    @Test
    fun `스냅샷 — 홈과 같은 예산 숫자, 기록은 계약 필드만`() = runTest {
        repo.addManual(d, 9_000, "김밥천국", EntryType.SPEND, Category.FOOD, memo = "비밀 메모", accountId = bank, minuteOfDay = 12 * 60 + 5)
        repo.addManual(d, 40_000, "돈까스집", EntryType.SPEND, Category.FOOD, accountId = bank, share = 10_000, people = 4)
        repo.addManual(d.minusDays(1), 6_500, "스타벅스", EntryType.SPEND, Category.CAFE, accountId = bank, want = 1)
        repo.addManual(d.minusDays(2), 300_000, "적금", EntryType.TRANSFER, null, accountId = bank, toAccountId = savings)
        repo.addManual(d.minusDays(300), 1_000, "오래된 기록", EntryType.SPEND, Category.FOOD, accountId = bank)
        val home = repo.home(flowOf(LocalDateTime.of(2026, 11, 16, 21, 40))).first()!!
        val (entries, accounts, fixed) = repo.pcData(d.minusDays(PcSnapshot.DAYS))
        val json = PcSnapshot.build(PcInput.from(home, entries, accounts, fixed), at, "0.35.0")
        File("build").mkdirs()
        File("build/pc-snapshot-sample.json").writeText(json.toString(1))

        assertEquals("dont-pc-snapshot", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        assertEquals("2026-11-16T21:40+09:00", json.getString("created_at"))
        val c = json.getJSONObject("cycle")
        assertEquals(home.cycle.start.toString(), c.getString("start"))
        assertEquals(home.plan.disposable, c.getLong("budget"))
        assertEquals(home.monthLiving, c.getLong("spent"))
        assertEquals(home.plan.disposable - home.monthLiving, c.getLong("remaining"))
        assertEquals(home.currentWeek!!.available, c.getJSONObject("week").getLong("remaining"))
        assertEquals(home.day.allowance, c.getLong("today_allowance"))

        val acc = json.getJSONArray("accounts")
        assertEquals(listOf("카카오뱅크", "적금"), (0 until acc.length()).map { acc.getJSONObject(it).getString("name") })  // 숨긴 자산은 안 보냄
        val es = json.getJSONArray("entries").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertFalse(es.any { it.getString("label") == "오래된 기록" })  // 190일보다 오래된 건 안 보냄
        val gimbap = es.single { it.getString("label") == "김밥천국" }
        assertEquals("12:05", gimbap.getString("time"))
        assertEquals("카카오뱅크", gimbap.getString("account"))
        val dutch = es.single { it.getString("label") == "돈까스집" }
        assertEquals(10_000L, dutch.getLong("my_amount"))
        assertTrue(es.single { it.getString("label") == "스타벅스" }.getBoolean("want"))
        assertEquals("적금", es.single { it.getString("type") == "TRANSFER" }.getString("to_account"))
        // 알림 글·메모·알림 앱은 어디에도 없다
        val raw = json.toString()
        assertFalse(raw.contains("비밀 메모"))
        es.forEach { e -> listOf("memo", "rawText", "packageName", "tails").forEach { k -> assertFalse(e.has(k)) } }
        val cats = json.getJSONArray("categories").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        assertTrue(cats.all { it.getString("color").matches(Regex("#[0-9A-F]{6}")) })
        assertEquals("월세", json.getJSONArray("fixed").getJSONObject(0).getString("name"))
    }

    @Test
    fun `와이파이 주소 u2와 평문 허용 주소`() {
        val link = "lifeapp://pair?u=http%3A%2F%2F100.72.99.70%3A9100&t=abcdefghijklmnop1234&u2=http%3A%2F%2F192.168.0.12%3A9100"
        assertEquals("http://192.168.0.12:9100", PcLink.lan(link))
        assertNull(PcLink.lan("lifeapp://pair?u=http%3A//100.72.99.70%3A9100&t=abcdefghijklmnop1234"))
        assertNull(PcLink.lan("lifeapp://pair?u=x&t=y&u2=http%3A%2F%2F8.8.8.8%3A9100"))  // 공인 주소는 안 됨
        val ok = listOf("http://100.72.99.70:9100", "http://192.168.0.12:9100", "http://10.0.0.3:9100", "http://172.20.1.2:9100",
            "http://my-pc.tail1234.ts.net:9100", "http://127.0.0.1:9100", "https://example.com")
        ok.forEach { assertTrue(it, com.simbio.don.app.pc.PcSync.allowedHost(it)) }
        listOf("http://8.8.8.8:9100", "http://example.com", "http://172.32.0.1:9100", "http://100.200.1.1:9100")
            .forEach { assertFalse(it, com.simbio.don.app.pc.PcSync.allowedHost(it)) }
        val cfg = com.simbio.don.app.pc.PcSync.Config(true, "http://100.72.99.70:9100", "abcdefghijklmnop1234", "http://192.168.0.12:9100/")
        assertEquals(listOf("http://100.72.99.70:9100", "http://192.168.0.12:9100"), cfg.urls)
    }

    @Test
    fun `양방향 — 응답 changes 읽기 (이상한 줄은 버림)`() {
        val r = JSONObject("""{"ok":true,"stored":true,"changes":[
            {"id":10,"account":"현금"},{"id":"11","want":true},{"id":12,"want":false,"account":null},
            {"id":"x","want":true},{"id":13},{"id":14,"account":"  "},{"id":15,"want":"yes"},"줄"]}""")
        assertEquals(listOf(PcChange(10, account = "현금"), PcChange(11, want = true), PcChange(12, want = false)), PcChanges.parse(r))
        assertEquals(emptyList<PcChange>(), PcChanges.parse(JSONObject("""{"ok":true,"stored":true}""")))  // 예전 서버
        assertEquals(emptyList<PcChange>(), PcChanges.parse(null))
    }

    @Test
    fun `양방향 — PC에서 답한 통장·필요욕구는 폰의 빈 칸만 채운다`() = runTest {
        val cash = repo.saveAccount(AccountEntity(name = "지갑", type = AccountType.CASH.name, opening = 50_000, sortOrder = 3))
        val gimbap = repo.addManual(d, 9_000, "김밥천국", EntryType.SPEND, Category.FOOD)
        val coffee = repo.addManual(d, 6_500, "스타벅스", EntryType.SPEND, Category.CAFE, accountId = bank)
        val store = repo.addManual(d, 4_000, "편의점", EntryType.SPEND, Category.FOOD, accountId = bank, want = 0)
        val fish = repo.addManual(d, 3_000, "붕어빵", EntryType.SPEND, Category.FOOD)
        val move = repo.addManual(d, 300_000, "적금", EntryType.TRANSFER, null, accountId = bank, toAccountId = savings)
        val changes = listOf(
            PcChange(gimbap, account = "카카오뱅크"),
            PcChange(coffee, want = true),
            PcChange(store, want = true),           // 폰에서 이미 '필요'로 정함 → 그대로
            PcChange(fish, account = "현금"),       // 이름이 '현금'인 자산이 없으면 현금 자산으로
            PcChange(coffee, account = "없는 통장"), // 이미 통장 있음 + 모르는 이름 → 무시
            PcChange(move, want = true),            // 이체엔 필요/욕구가 없다
            PcChange(999_999, account = "카카오뱅크"),
            PcChange(gimbap, account = "숨긴 통장"),
        )
        assertEquals(3, repo.applyPcChanges(changes))
        assertEquals(bank, db.dao().entryNow(gimbap)!!.accountId)
        assertFalse(db.dao().entryNow(gimbap)!!.acctGuess)
        assertEquals(1, db.dao().entryNow(coffee)!!.want)
        assertEquals(0, db.dao().entryNow(store)!!.want)
        assertEquals(cash, db.dao().entryNow(fish)!!.accountId)
        assertNull(db.dao().entryNow(move)!!.want)
        assertEquals(0, repo.applyPcChanges(changes))  // 두 번 받아도 그대로
        // 다시 보내는 스냅샷에 채운 값이 실린다 → 서버는 반영된 걸 보고 지운다
        val home = repo.home(flowOf(LocalDateTime.of(2026, 11, 16, 21, 40))).first()!!
        val (entries, accounts, fixed) = repo.pcData(d.minusDays(PcSnapshot.DAYS))
        val es = PcSnapshot.build(PcInput.from(home, entries, accounts, fixed), at, "0.36.0").getJSONArray("entries")
            .let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.associateBy { it.getLong("id") }
        assertEquals("카카오뱅크", es.getValue(gimbap).getString("account"))
        assertEquals("지갑", es.getValue(fish).getString("account"))
        assertTrue(es.getValue(coffee).getBoolean("want"))
    }

    @Test
    fun `정산으로 받은 돈은 수입이 아니라 이체로`() {
        val e = EntryEntity(id = 5, epochDay = d.toEpochDay(), amount = 10_000, label = "김철수", type = EntryType.INCOME.name,
            source = "MANUAL", settleFor = 3)
        assertEquals("TRANSFER", PcSnapshot.entry(e, emptyMap())!!.getString("type"))
        val bad = e.copy(type = "WHATEVER", settleFor = null)
        assertNull(PcSnapshot.entry(bad, emptyMap()))
        val j: JSONObject = PcSnapshot.entry(e.copy(settleFor = null, minuteOfDay = 7), emptyMap())!!
        assertEquals("00:07", j.getString("time"))
    }
}
