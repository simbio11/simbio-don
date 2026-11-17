package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
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
