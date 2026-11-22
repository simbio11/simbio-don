package com.simbio.don.app.data

import com.simbio.don.app.ai.ToolCall
import com.simbio.don.app.ai.ToolOutcome
import com.simbio.don.app.ai.ToolSpec
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** AI가 제안한 고치기 — 사용자가 [적용]을 눌러야 가계부에 들어간다 */
data class AgentAction(
    val id: Int,
    val kind: Kind,
    /** 고치기·지우기면 그 기록 */
    val entryId: Long? = null,
    val date: LocalDate? = null,
    val amount: Long? = null,
    val label: String? = null,
    val type: EntryType? = null,
    val category: Category? = null,
    val accountId: Long? = null,
    /** 사람이 읽을 한 줄 ("11/20 점심 김밥 4,500원 적기") */
    val summary: String,
) {
    enum class Kind { ADD, EDIT, DELETE }
}

/**
 * 가계부 하네스: AI 대화에서 에이전트가 가계부 기록을 찾아보고, 합계를 내고, 고칠 것을 "제안"하는 도구들.
 *
 * 보내는 것: 기록의 날짜·시각·종류·금액·이름(가게)·분류·통장 이름, 합계, 자산 잔액, 비서가 찾은 문제의 건수.
 * 보내지 않는 것: 알림 원문, 메모, 캘린더 일정, 다른 앱 이름. 바꾸는 도구는 없다 — 제안만 하고 사용자가 누른다.
 */
class LedgerHarness(
    private val repo: DonRepository,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    /** 이번 대화에서 나온 제안 (화면이 보여 주고 [적용]하면 지운다) */
    val proposals = mutableListOf<AgentAction>()
    private var nextId = 1

    val tools: List<ToolSpec> = listOf(
        ToolSpec(
            "overview",
            "이번 달(월급날 기준) 한눈에: 오늘 날짜, 이번 달 기간, 월급, 고정 지출 합, 저축 목표, 쓸 수 있는 생활비, 생활비로 쓴 돈, 남은 생활비, 이번 주 예산·남은 돈, 수입 합. 먼저 이것부터 보면 좋다.",
        ),
        ToolSpec(
            "search_entries",
            "가계부 기록 찾기 (최근 것부터). 날짜는 YYYY-MM-DD. 아무것도 안 주면 이번 달 기록. 결과에 id가 있어 고치기 제안에 쓴다.",
            mapOf(
                "from" to mapOf("type" to "string", "description" to "시작 날짜 (포함)"),
                "to" to mapOf("type" to "string", "description" to "끝 날짜 (포함)"),
                "text" to mapOf("type" to "string", "description" to "이름(가게)에 들어간 글자"),
                "type" to mapOf("type" to "string", "enum" to EntryType.entries.map { it.name }, "description" to "SPEND 지출, INCOME 수입, TRANSFER 이체, FIXED 고정 지출, REFUND 환불, EXCEPTION 예외 지출"),
                "category" to mapOf("type" to "string", "description" to "분류 id (categories 도구 참고)"),
                "account" to mapOf("type" to "string", "description" to "통장·카드 이름 일부"),
                "min_amount" to mapOf("type" to "integer"),
                "max_amount" to mapOf("type" to "integer"),
                "limit" to mapOf("type" to "integer", "description" to "최대 몇 건 (기본 30, 최대 100)"),
            ),
        ),
        ToolSpec(
            "totals",
            "기간 합계를 묶어서: 분류별·날짜별·주별·월별·통장별·이름별. 지출 합은 고정 지출과 이체를 뺀 생활비 기준.",
            mapOf(
                "from" to mapOf("type" to "string"),
                "to" to mapOf("type" to "string"),
                "by" to mapOf("type" to "string", "enum" to listOf("category", "day", "week", "month", "account", "label")),
                "type" to mapOf("type" to "string", "enum" to EntryType.entries.map { it.name }, "description" to "없으면 지출(SPEND·EXCEPTION − 환불)"),
            ),
            listOf("by"),
        ),
        ToolSpec("accounts", "내 통장·카드·현금·저축 목록과 앱이 계산한 잔액."),
        ToolSpec("categories", "쓸 수 있는 분류 id와 이름 (지출·수입)."),
        ToolSpec("checkup", "비서가 찾은 꼬인 곳(잔액 차이·한쪽만 온 이체·중복 의심 등)과 아직 답하지 않은 질문의 금액·짐작."),
        ToolSpec(
            "propose_add",
            "새 기록 적기를 제안한다 (사용자가 눌러야 적힌다). 금액은 원 단위 정수.",
            mapOf(
                "date" to mapOf("type" to "string", "description" to "YYYY-MM-DD"),
                "amount" to mapOf("type" to "integer"),
                "label" to mapOf("type" to "string"),
                "type" to mapOf("type" to "string", "enum" to listOf("SPEND", "INCOME", "TRANSFER", "REFUND")),
                "category" to mapOf("type" to "string"),
                "account" to mapOf("type" to "string", "description" to "통장·카드 이름 (없으면 비움)"),
            ),
            listOf("date", "amount", "label", "type"),
        ),
        ToolSpec(
            "propose_edit",
            "기록 고치기를 제안한다 (사용자가 눌러야 바뀐다). 바꿀 칸만 넣는다.",
            mapOf(
                "id" to mapOf("type" to "integer"),
                "date" to mapOf("type" to "string"),
                "amount" to mapOf("type" to "integer"),
                "label" to mapOf("type" to "string"),
                "type" to mapOf("type" to "string", "enum" to listOf("SPEND", "INCOME", "TRANSFER", "REFUND", "EXCEPTION")),
                "category" to mapOf("type" to "string"),
            ),
            listOf("id"),
        ),
        ToolSpec(
            "propose_delete",
            "기록 지우기를 제안한다 (중복 등, 사용자가 눌러야 지워진다).",
            mapOf("id" to mapOf("type" to "integer"), "reason" to mapOf("type" to "string")),
            listOf("id"),
        ),
    )

    fun system(today: LocalDate): String = """
        너는 가계부 앱 "돈't" 안의 비서야. 사용자와 한국어 '~해요'체로 대화해. 오늘은 $today (${today.dayOfWeek.korean()}).
        - 가계부 내용은 도구로 직접 찾아봐. 숫자는 도구 결과만 쓰고 지어내지 마. 계산은 정확하게.
        - "이번 달"은 월급날 기준 기간이야 (overview의 기간).
        - 기록을 적거나 고치거나 지우려면 propose_ 도구로 제안만 해. 사용자가 [적용]을 눌러야 반영돼. 제안했으면 무엇을 제안했는지 짧게 말해.
        - 짧고 구체적으로: 다섯 문장 안팎, 금액을 넣어서. 표가 필요하면 짧은 목록으로.
        - 투자 종목 추천이나 대출 권유는 하지 마.
    """.trimIndent()

    private fun DayOfWeek.korean() = listOf("월", "화", "수", "목", "금", "토", "일")[ordinal] + "요일"

    /** 도구 하나 돌리기. 잘못된 인자는 오류 글로 돌려준다 (AI가 고쳐서 다시 부르게) */
    suspend fun run(call: ToolCall): ToolOutcome = try {
        val a = call.args
        val out: Any = when (call.name) {
            "overview" -> overview()
            "search_entries" -> search(a)
            "totals" -> totals(a)
            "accounts" -> accounts()
            "categories" -> categories()
            "checkup" -> checkup()
            "propose_add" -> proposeAdd(a)
            "propose_edit" -> proposeEdit(a)
            "propose_delete" -> proposeDelete(a)
            else -> throw IllegalArgumentException("없는 도구: ${call.name}")
        }
        ToolOutcome(call.id, call.name, out.toString())
    } catch (e: Exception) {
        ToolOutcome(call.id, call.name, e.message ?: "도구 오류", isError = true)
    }

    // ── 읽기 ──

    private suspend fun home(): HomeState? = repo.home(flowOf(now())).first()

    private suspend fun names(): Map<Long, String> = repo.accountsNow().associate { it.id to it.name }

    private suspend fun overview(): JSONObject {
        val h = home() ?: return JSONObject().put("error", "아직 시작 설정을 안 했어요")
        val w = h.currentWeek
        return JSONObject()
            .put("today", h.today.toString())
            .put("month_from", h.cycle.start.toString())
            .put("month_to", h.cycle.end.toString())
            .put("salary", h.salary)
            .put("fixed_total", h.plan.fixedTotal)
            .put("savings_goal", h.plan.savings)
            .put("living_budget", h.plan.disposable)
            .put("living_spent", h.monthLiving)
            .put("living_left", h.plan.disposable - h.monthLiving)
            .put("income_total", h.monthIncome)
            .put("today_allowance", h.todayAllowance)
            .apply {
                if (w != null) put("this_week", JSONObject().put("from", w.week.start.toString()).put("to", w.week.end.toString()).put("budget", w.budget).put("spent", w.spent).put("left", w.available))
            }
    }

    private fun date(a: JSONObject, k: String): LocalDate? = a.optString(k).takeIf { it.isNotBlank() && !a.isNull(k) }?.let {
        runCatching { LocalDate.parse(it.take(10)) }.getOrElse { throw IllegalArgumentException("$k 날짜는 YYYY-MM-DD로 주세요") }
    }

    private suspend fun range(a: JSONObject): Pair<LocalDate, LocalDate> {
        val h = home()
        val from = date(a, "from") ?: h?.cycle?.start ?: now().toLocalDate().withDayOfMonth(1)
        val to = date(a, "to") ?: h?.cycle?.end ?: now().toLocalDate()
        return from to to
    }

    private fun entryJson(e: EntryEntity, names: Map<Long, String>) = JSONObject()
        .put("id", e.id)
        .put("date", LocalDate.ofEpochDay(e.epochDay).toString())
        .apply { e.minuteOfDay?.let { put("time", "%02d:%02d".format(it / 60, it % 60)) } }
        .put("type", e.type)
        .put("amount", e.amount)
        .put("label", e.label)
        .put("category", e.category)
        .apply {
            e.accountId?.let(names::get)?.let { put("account", it) }
            e.toAccountId?.let(names::get)?.let { put("to_account", it) }
            e.share?.let { put("my_share", it) }
            if (e.owe) put("owe", true)
        }

    private suspend fun filtered(a: JSONObject): List<EntryEntity> {
        val (from, to) = range(a)
        val names = names()
        val text = a.optString("text").trim().takeIf { it.isNotBlank() && !a.isNull("text") }
        val type = a.optString("type").takeIf { it.isNotBlank() && !a.isNull("type") }
        val cat = a.optString("category").takeIf { it.isNotBlank() && !a.isNull("category") }
        val acc = a.optString("account").trim().takeIf { it.isNotBlank() && !a.isNull("account") }
        val min = if (a.has("min_amount") && !a.isNull("min_amount")) a.optLong("min_amount") else null
        val max = if (a.has("max_amount") && !a.isNull("max_amount")) a.optLong("max_amount") else null
        return repo.allEntriesNow().filter { e ->
            val d = LocalDate.ofEpochDay(e.epochDay)
            !d.isBefore(from) && !d.isAfter(to) &&
                (text == null || e.label.contains(text, ignoreCase = true)) &&
                (type == null || e.type == type) &&
                (cat == null || e.category == cat) &&
                (acc == null || listOfNotNull(e.accountId, e.toAccountId).any { id -> names[id]?.contains(acc, ignoreCase = true) == true }) &&
                (min == null || e.amount >= min) && (max == null || e.amount <= max)
        }.sortedWith(compareByDescending<EntryEntity> { it.epochDay }.thenByDescending { it.minuteOfDay ?: 0 })
    }

    private suspend fun search(a: JSONObject): JSONObject {
        val list = filtered(a)
        val limit = (if (a.has("limit")) a.optInt("limit", 30) else 30).coerceIn(1, 100)
        val names = names()
        val (from, to) = range(a)
        return JSONObject()
            .put("from", from.toString()).put("to", to.toString())
            .put("count", list.size)
            .put("shown", minOf(limit, list.size))
            .put("items", JSONArray().apply { list.take(limit).forEach { put(entryJson(it, names)) } })
    }

    /** 지출 쪽 부호: 환불은 빼고 */
    private fun spendValue(e: EntryEntity): Long = when (e.type) {
        EntryType.SPEND.name, EntryType.EXCEPTION.name -> e.share ?: e.amount
        EntryType.REFUND.name -> -e.amount
        else -> 0
    }

    private suspend fun totals(a: JSONObject): JSONObject {
        val by = a.optString("by").ifBlank { "category" }
        val type = a.optString("type").takeIf { it.isNotBlank() && !a.isNull("type") }
        val base = JSONObject(a.toString()).apply { remove("type") }
        val list = filtered(base)
        val names = names()
        val value: (EntryEntity) -> Long = if (type == null) ::spendValue else { e -> if (e.type == type) e.amount else 0L }
        val key: (EntryEntity) -> String = when (by) {
            "day" -> { e -> LocalDate.ofEpochDay(e.epochDay).toString() }
            "week" -> { e -> LocalDate.ofEpochDay(e.epochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString() + " 주" }
            "month" -> { e -> LocalDate.ofEpochDay(e.epochDay).toString().take(7) }
            "account" -> { e -> e.accountId?.let(names::get) ?: "통장 모름" }
            "label" -> { e -> e.label.ifBlank { "(이름 없음)" } }
            else -> { e -> e.category }
        }
        val sums = list.filter { value(it) != 0L }.groupBy(key).mapValues { (_, l) -> l.sumOf(value) to l.size }
            .entries.sortedByDescending { it.value.first }
        val (from, to) = range(a)
        return JSONObject()
            .put("from", from.toString()).put("to", to.toString())
            .put("by", by).put("what", type ?: "지출(생활비)")
            .put("total", sums.sumOf { it.value.first })
            .put("groups", JSONArray().apply { sums.take(40).forEach { (k, v) -> put(JSONObject().put("key", k).put("sum", v.first).put("count", v.second)) } })
    }

    private suspend fun accounts(): JSONArray {
        val h = home()
        val list = h?.accounts ?: repo.accountsNow().map { AccountBalance(it, it.opening) }
        return JSONArray().apply {
            list.filter { !it.account.hidden }.forEach { b ->
                put(JSONObject().put("name", b.account.name).put("type", b.account.type).put("balance", b.balance))
            }
        }
    }

    private fun categories(): JSONObject = JSONObject()
        .put("spending", JSONArray().apply { Category.spending.forEach { put(JSONObject().put("id", it.name).put("name", it.label)) } })
        .put("income", JSONArray().apply { Category.incomes.forEach { put(JSONObject().put("id", it.name).put("name", it.label)) } })

    private suspend fun checkup(): JSONObject {
        val f = repo.findings(now().toLocalDate()).first()
        val asks = repo.askItemsNow()
        val names = names()
        return JSONObject()
            .put("problems", JSONArray().apply { f.forEach { put(JSONObject().put("kind", it.kind.label).put("count", it.count).put("amount", it.amount).put("hint", it.detail)) } })
            .put(
                "questions",
                JSONArray().apply {
                    asks.forEach { q ->
                        val g = AskGuess.decode(q.guess)
                        put(
                            JSONObject().put("date", java.time.Instant.ofEpochSecond(q.postedAtEpochSecond).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
                                .put("direction", if (q.action == "INCOME") "들어옴" else "나감").put("amount", q.amount)
                                .put("guess", g?.kind?.label ?: "-")
                                .apply { g?.from?.let(names::get)?.let { put("from", it) }; g?.to?.let(names::get)?.let { put("to", it) } },
                        )
                    }
                },
            )
    }

    // ── 제안 (바꾸지 않는다) ──

    private suspend fun accountByName(name: String?): Long? {
        if (name.isNullOrBlank()) return null
        val list = repo.accountsNow().filter { !it.hidden }
        return (list.firstOrNull { it.name == name } ?: list.firstOrNull { it.name.contains(name, true) || name.contains(it.name, true) })?.id
    }

    private fun category(id: String?, type: EntryType?): Category? {
        if (id.isNullOrBlank()) return null
        val c = Category.entries.firstOrNull { it.name.equals(id, true) || it.label == id }
            ?: throw IllegalArgumentException("없는 분류: $id (categories 도구 참고)")
        return c
    }

    private fun type(s: String?): EntryType? = s?.takeIf { it.isNotBlank() }?.let { t ->
        EntryType.entries.firstOrNull { it.name.equals(t, true) } ?: throw IllegalArgumentException("없는 종류: $t")
    }

    private fun won(v: Long) = "%,d원".format(v)

    private fun short(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

    private suspend fun proposeAdd(a: JSONObject): JSONObject {
        val d = date(a, "date") ?: throw IllegalArgumentException("date가 필요해요")
        val amount = a.optLong("amount").takeIf { it > 0 } ?: throw IllegalArgumentException("amount는 0보다 큰 정수")
        val label = a.optString("label").trim().ifBlank { throw IllegalArgumentException("label이 필요해요") }
        val t = type(a.optString("type")) ?: EntryType.SPEND
        val c = category(a.optString("category").takeUnless { a.isNull("category") }, t)
        val acc = accountByName(a.optString("account").takeUnless { a.isNull("account") })
        val kind = when (t) { EntryType.INCOME -> "수입"; EntryType.TRANSFER -> "이체"; EntryType.REFUND -> "환불"; else -> "지출" }
        val p = AgentAction(
            nextId++, AgentAction.Kind.ADD, date = d, amount = amount, label = label, type = t, category = c, accountId = acc,
            summary = "${short(d)} $label ${won(amount)} $kind 적기" + (c?.let { " · ${it.label}" } ?: ""),
        )
        proposals += p
        return JSONObject().put("proposed", p.summary).put("status", "사용자 확인을 기다려요")
    }

    private suspend fun proposeEdit(a: JSONObject): JSONObject {
        val id = a.optLong("id").takeIf { it > 0 } ?: throw IllegalArgumentException("id가 필요해요")
        val e = repo.entryNow(id) ?: throw IllegalArgumentException("기록 $id 를 찾지 못했어요")
        val d = date(a, "date")
        val amount = if (a.has("amount") && !a.isNull("amount")) a.optLong("amount").takeIf { it > 0 } ?: throw IllegalArgumentException("amount는 0보다 큰 정수") else null
        val label = a.optString("label").trim().takeIf { it.isNotBlank() && !a.isNull("label") }
        val t = type(a.optString("type").takeUnless { a.isNull("type") })
        val c = category(a.optString("category").takeUnless { a.isNull("category") }, t)
        if (d == null && amount == null && label == null && t == null && c == null) throw IllegalArgumentException("바꿀 칸이 없어요")
        val changes = listOfNotNull(
            d?.let { "날짜 ${short(it)}" }, amount?.let { "금액 ${won(it)}" }, label?.let { "이름 '$it'" },
            t?.let { "종류 ${it.name}" }, c?.let { "분류 ${it.label}" },
        )
        val p = AgentAction(
            nextId++, AgentAction.Kind.EDIT, entryId = id, date = d, amount = amount, label = label, type = t, category = c,
            summary = "${short(LocalDate.ofEpochDay(e.epochDay))} ${e.label} ${won(e.amount)} → " + changes.joinToString(", "),
        )
        proposals += p
        return JSONObject().put("proposed", p.summary).put("status", "사용자 확인을 기다려요")
    }

    private suspend fun proposeDelete(a: JSONObject): JSONObject {
        val id = a.optLong("id").takeIf { it > 0 } ?: throw IllegalArgumentException("id가 필요해요")
        val e = repo.entryNow(id) ?: throw IllegalArgumentException("기록 $id 를 찾지 못했어요")
        val why = a.optString("reason").takeIf { it.isNotBlank() && !a.isNull("reason") }
        val p = AgentAction(
            nextId++, AgentAction.Kind.DELETE, entryId = id,
            summary = "${short(LocalDate.ofEpochDay(e.epochDay))} ${e.label} ${won(e.amount)} 지우기" + (why?.let { " ($it)" } ?: ""),
        )
        proposals += p
        return JSONObject().put("proposed", p.summary).put("status", "사용자 확인을 기다려요")
    }
}
