package com.simbio.don.app.pc

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.app.data.FixedExpenseEntity
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.ui.kit.tint
import com.simbio.don.budget.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.time.LocalDate
import java.time.OffsetDateTime

/*
 * PC로 보내기 — 생활관리 데스크탑(PC 서버 POST /money/snapshot)에 가계부 숫자를 보낸다.
 * 계약: 생활관리 저장소 docs/08_돈트_연동_계약.md (format "dont-pc-snapshot", version 1)
 *
 * 원칙 (docs/decisions.md 0.35):
 *  - 켤 때만. 사용자가 넣은 '내 PC 서버' 주소로만 (Tailscale 사설망). 다른 곳으로는 보내지 않는다
 *  - 보내는 것: 거래(날짜·시각·금액·내 몫·종류·분류·가게 이름·자산 이름·필요/욕구), 이번 달 예산 숫자, 자산 잔액, 고정 지출
 *  - 안 보내는 것: 알림 글(rawText)·메모·알림 보낸 앱·계좌 끝자리·게임·AI 키
 *  - 받는 것 (0.36 양방향): 응답의 changes — PC 알림함에서 답한 '어느 통장'·'필요/욕구'. 폰에 아직 빈 칸만 채운다
 */

/** 생활관리 서버가 찍어 주는 연결 링크 `lifeapp://pair?u=…&t=…` (python -m app pair) */
object PcLink {
    fun parse(text: String): Pair<String, String>? {
        val s = text.trim()
        if (!s.startsWith("lifeapp://pair")) return null
        val q = runCatching { URI(s).rawQuery }.getOrNull() ?: return null
        val p = q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val u = p["u"]?.trim()?.trimEnd('/') ?: return null
        val t = p["t"]?.trim() ?: return null
        if (!(u.startsWith("http://") || u.startsWith("https://")) || t.length < 16) return null
        return u to t
    }
}

/** 스냅샷 재료 — 화면 상태(HomeState)에서 뽑는다. 테스트는 이걸 직접 만든다 */
data class PcInput(
    val today: LocalDate,
    val cycleStart: LocalDate,
    val cycleEnd: LocalDate,
    val salary: Long,
    val fixedTotal: Long,
    val savings: Long,
    /** 이번 달 생활비 */
    val budget: Long,
    /** 이번 달 생활비로 쓴 돈 */
    val spent: Long,
    val week: Week?,
    val todayAllowance: Long?,
    val todaySpent: Long?,
    val accounts: List<Pair<AccountEntity, Long>>,
    val entries: List<EntryEntity>,
    val fixed: List<FixedExpenseEntity>,
) {
    data class Week(val start: LocalDate, val end: LocalDate, val available: Long, val spent: Long, val remaining: Long)

    companion object {
        fun from(h: HomeState, entries: List<EntryEntity>, accounts: List<AccountEntity>, fixed: List<FixedExpenseEntity>): PcInput {
            val balances: Map<Long, AccountBalance> = h.accounts.associateBy { it.account.id }
            val r = h.currentWeek
            return PcInput(
                today = h.today, cycleStart = h.cycle.start, cycleEnd = h.cycle.end,
                salary = h.plan.salary, fixedTotal = h.plan.fixedTotal, savings = h.plan.savings,
                budget = h.plan.disposable, spent = h.monthLiving,
                week = r?.let { Week(it.week.start, it.week.end, it.budget + it.carryIn - it.exceptionOverflow, it.spent, it.available) },
                todayAllowance = h.day.allowance, todaySpent = h.day.spentDaily + h.day.spentBig,
                accounts = accounts.filter { !it.hidden }.map { it to (balances[it.id]?.balance ?: it.opening) },
                entries = entries, fixed = fixed,
            )
        }
    }
}

/** PC(생활관리 알림함)에서 고친 기록 하나 — 응답 changes 한 줄 (계약 docs/08 6절) */
data class PcChange(val entryId: Long, val account: String? = null, val want: Boolean? = null)

object PcChanges {
    /** 응답에서 changes만 읽는다. 모르는 칸·이상한 줄은 버린다 (서버가 바뀌어도 앱이 죽지 않게) */
    fun parse(reply: JSONObject?): List<PcChange> {
        val a = reply?.optJSONArray("changes") ?: return emptyList()
        return (0 until minOf(a.length(), 500)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.opt("id")?.toString()?.toLongOrNull() ?: return@mapNotNull null
            val account = if (o.isNull("account")) null else o.optString("account").trim().take(80).ifBlank { null }
            val want = if (o.isNull("want")) null else (o.opt("want") as? Boolean)
            if (account == null && want == null) null else PcChange(id, account, want)
        }
    }
}

object PcSnapshot {
    const val FORMAT = "dont-pc-snapshot"
    const val VERSION = 1
    /** 보내는 기록 기간 — 데스크탑 '달마다' 그래프(6달)에 맞춤 */
    const val DAYS = 190L
    private val TYPES = setOf("SPEND", "REFUND", "EXCEPTION", "FIXED", "TRANSFER", "INCOME")

    private fun hex(c: Category): String = String.format("#%06X", c.tint.toArgb() and 0xFFFFFF)

    fun build(input: PcInput, createdAt: OffsetDateTime, appVersion: String): JSONObject {
        val names = input.accounts.associate { (a, _) -> a.id to a.name }
        val from = input.today.minusDays(DAYS).toEpochDay()
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("created_at", createdAt.withNano(0).toString())
            put("app_version", appVersion.take(20))
            put("cycle", JSONObject().apply {
                put("start", input.cycleStart.toString())
                put("end", input.cycleEnd.toString())
                put("salary", input.salary)
                put("fixed_total", input.fixedTotal)
                put("savings", input.savings)
                put("budget", input.budget)
                put("spent", input.spent)
                put("remaining", input.budget - input.spent)
                input.week?.let { w ->
                    put("week", JSONObject().apply {
                        put("start", w.start.toString()); put("end", w.end.toString())
                        put("available", w.available); put("spent", w.spent); put("remaining", w.remaining)
                    })
                }
                input.todayAllowance?.let { put("today_allowance", it) }
                input.todaySpent?.let { put("today_spent", it) }
            })
            put("accounts", JSONArray().apply {
                input.accounts.forEach { (a, bal) ->
                    put(JSONObject().put("id", a.id).put("name", a.name.take(80)).put("kind", a.type.take(20)).put("balance", bal))
                }
            })
            put("categories", JSONArray().apply {
                Category.entries.forEach { c ->
                    put(JSONObject().put("name", c.name).put("label", c.label.take(40)).put("emoji", c.emoji.take(8))
                        .put("income", c.income).put("color", hex(c)))
                }
            })
            put("entries", JSONArray().apply {
                input.entries.filter { it.epochDay >= from }.forEach { e -> entry(e, names)?.let { put(it) } }
            })
            put("fixed", JSONArray().apply {
                input.fixed.forEach { f ->
                    put(JSONObject().put("name", f.name.take(80)).put("amount", f.amount.coerceAtLeast(0)).apply {
                        f.day?.let { put("day", it) }
                    }.put("category", f.category.take(40)))
                }
            })
        }
    }

    /** 기록 한 줄 — 알림 글·메모·알림 앱은 넣지 않는다 */
    fun entry(e: EntryEntity, accountNames: Map<Long, String>): JSONObject? {
        // 정산으로 받은 돈은 수입이 아니라 잔액만 움직이는 돈 — 서버 합계에서 수입으로 세지 않게 이체로
        val type = if (e.settleFor != null) "TRANSFER" else e.type
        if (type !in TYPES) return null
        return JSONObject().apply {
            put("id", e.id)
            put("date", LocalDate.ofEpochDay(e.epochDay).toString())
            e.minuteOfDay?.let { m -> put("time", String.format("%02d:%02d", (m / 60).coerceIn(0, 23), m % 60)) }
            put("amount", kotlin.math.abs(e.amount))
            e.share?.let { s -> if (s != e.amount) put("my_amount", kotlin.math.abs(s)) }
            put("type", type)
            put("category", e.category.take(40))
            e.sub?.let { put("sub", it.take(40)) }
            put("label", e.label.take(120))
            e.accountId?.let { id -> accountNames[id]?.let { put("account", it.take(80)) } }
            e.toAccountId?.let { id -> accountNames[id]?.let { put("to_account", it.take(80)) } }
            e.want?.let { put("want", it == 1) }
        }
    }
}

/**
 * 자동 보내기 (0.37) — 앱을 열 때마다(화면으로 돌아올 때 포함)·알림으로 기록이 적힐 때 백그라운드에서.
 * 같은 일을 하던 ViewModel 코드를 여기로 모아, 화면이 없어도(알림 수신 서비스) 보낼 수 있게 했다.
 * 받은 changes(PC 알림함 답)는 채우고 한 번 더 보낸다.
 */
object PcAuto {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val lock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var job: kotlinx.coroutines.Job? = null

    /** [wait] 동안 모았다가 보낸다(연달아 부르면 마지막 한 번만) */
    fun trigger(c: Context, force: Boolean = false, wait: Long = 1_500) {
        val app = c.applicationContext
        if (!PcSync.config(app).ready) return
        job?.cancel()
        job = scope.launch {
            kotlinx.coroutines.delay(wait)
            runCatching { run(app, force) }
        }
    }

    /** 보내고, PC에서 고친 칸을 채웠으면 다시 보낸다. 채운 칸 수 */
    suspend fun run(c: Context, force: Boolean): Int = lock.withLock {
        val cfg = PcSync.config(c)
        if (!cfg.ready) return@withLock 0
        if (!force && System.currentTimeMillis() - PcSync.lastOkAt(c) < PcSync.MIN_INTERVAL_MS) return@withLock 0
        val repo = com.simbio.don.app.data.DonRepository.get(c)
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: ""
        var received = 0
        repeat(2) {
            val home = repo.home(kotlinx.coroutines.flow.flowOf(java.time.LocalDateTime.now())).first() ?: return@withLock received
            val (entries, accounts, fixed) = repo.pcData(home.today.minusDays(PcSnapshot.DAYS))
            val body = PcSnapshot.build(PcInput.from(home, entries, accounts, fixed), OffsetDateTime.now(), version)
            val reply = PcSync.send(c, cfg, body)
            val n = if (reply.changes.isEmpty()) 0 else repo.applyPcChanges(reply.changes)
            if (n == 0) {
                if (received > 0 && reply.status.ok) PcSync.annotate(c, "PC에서 고친 ${received}칸 받음")
                return@withLock received
            }
            received += n
        }
        received
    }
}

object PcSync {
    private const val PREFS = "pc_sync"
    /** 자동으로는 2분에 한 번까지 — 앱을 열 때마다 보내되 연달아 열면 건너뜀 (지금 보내기는 바로) */
    const val MIN_INTERVAL_MS = 2 * 60_000L

    data class Config(val enabled: Boolean = false, val url: String = "", val token: String = "") {
        val ready: Boolean get() = enabled && url.isNotBlank() && token.length >= 16
    }

    data class Status(val at: Long, val ok: Boolean, val message: String)

    /** 보낸 결과 + PC에서 고쳐 돌려준 것 */
    data class Reply(val status: Status, val changes: List<PcChange> = emptyList())

    val status = MutableStateFlow<Status?>(null)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun config(c: Context): Config = prefs(c).let { Config(it.getBoolean("enabled", false), it.getString("url", "") ?: "", it.getString("token", "") ?: "") }

    fun save(c: Context, cfg: Config) {
        prefs(c).edit().putBoolean("enabled", cfg.enabled).putString("url", cfg.url).putString("token", cfg.token).apply()
    }

    fun lastStatus(c: Context): Status? = status.value ?: prefs(c).let { p ->
        if (!p.contains("last_at")) null else Status(p.getLong("last_at", 0), p.getBoolean("last_ok", false), p.getString("last_msg", "") ?: "")
    }?.also { status.value = it }

    fun lastOkAt(c: Context): Long = prefs(c).getLong("last_ok_at", 0)

    /** 마지막 상태 글에 덧붙인다 ("· PC에서 고친 2건 받음") */
    fun annotate(c: Context, extra: String) {
        lastStatus(c)?.let { record(c, it.copy(message = it.message + " · " + extra)) }
    }

    private fun record(c: Context, s: Status) {
        status.value = s
        prefs(c).edit().putLong("last_at", s.at).putBoolean("last_ok", s.ok).putString("last_msg", s.message).apply {
            if (s.ok) putLong("last_ok_at", s.at)
        }.apply()
    }

    /** POST {url}/money/snapshot — 5초 안에 못 붙으면 포기(다음 기회에). 응답의 changes(PC에서 고친 것)도 돌려준다 */
    suspend fun send(c: Context, cfg: Config, body: JSONObject, now: Long = System.currentTimeMillis()): Reply = withContext(Dispatchers.IO) {
        var changes = emptyList<PcChange>()
        val s = try {
            val conn = URL(cfg.url.trimEnd('/') + "/money/snapshot").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 5_000
                conn.readTimeout = 20_000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer ${cfg.token}")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                when (val code = conn.responseCode) {
                    in 200..299 -> {
                        val r = runCatching { JSONObject(conn.inputStream.bufferedReader().readText()) }.getOrNull()
                        changes = PcChanges.parse(r)
                        if (r?.optBoolean("stored", true) == false) Status(now, true, "PC에 더 새 기록이 있어 건너뜀")
                        else Status(now, true, "보냄 · 기록 ${body.optJSONArray("entries")?.length() ?: 0}건")
                    }
                    401 -> Status(now, false, "토큰이 맞지 않아요 — 연결 링크를 다시 넣어 주세요")
                    413 -> Status(now, false, "보낼 기록이 너무 많아요")
                    422 -> Status(now, false, "PC 서버가 형식을 거절했어요 — 생활관리 서버를 업데이트하세요")
                    else -> Status(now, false, "PC 서버 오류 $code")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            Status(now, false, "PC 서버에 닿지 않아요 (Tailscale·서버가 켜져 있는지)")
        } catch (e: SecurityException) {
            Status(now, false, "이 주소로는 보낼 수 없어요 (Tailscale 100.x 또는 *.ts.net만)")
        }
        record(c, s)
        Reply(s, changes)
    }
}
