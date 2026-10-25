package com.simbio.don.app.ai

import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.SplitBook
import com.simbio.don.app.data.toEntry
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.Stats
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 말로 적은 한 건을 정리한 것 (확인 화면에 채워 넣는다) */
data class SpokenEntry(
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val type: EntryType,
    val category: Category,
    /** none · i_paid · they_paid */
    val split: String,
    val people: Int,
    val myShare: Long,
    val memo: String,
    val unsure: String,
)

/** 이번 달 점검 결과 */
data class CoachReport(
    val headline: String,
    val pace: String,
    val good: List<String>,
    val watch: List<String>,
    val actions: List<String>,
)

/**
 * AI가 맡는 세 가지 일과 보낼 내용.
 * - 말로 적기: 사용자가 친 문장 + 오늘 날짜 + 분류 이름만 보낸다.
 * - 이번 달 점검·물어보기: 합계 숫자만 보낸다 ([summary]). 가게 이름·메모·계좌 이름·알림 글은 보내지 않는다.
 */
object AiTasks {
    private fun dow(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    private fun won(v: Long) = "%,d원".format(v)

    // ── 말로 적기 ───────────────────────────────────────────

    fun spokenRequest(text: String, today: LocalDate, categories: List<Category>): AiRequest {
        val spending = categories.filter { !it.income }
        val incomes = categories.filter { it.income }
        val system = """
            너는 한국어 가계부 앱 "돈't"의 입력 도우미야. 사용자가 말로 적은 돈 쓴 일 한 건을 가계부 기록 하나로 정리해.
            - 오늘은 $today (${dow(today)}). "어제", "그저께", "지난 금요일" 같은 말은 YYYY-MM-DD 날짜로 바꿔. 날짜 말이 없으면 오늘.
            - amount는 원 단위 정수. "8만원"=80000, "1만 2천"=12000, "3.5만"=35000.
            - kind: spend(지출), income(수입), transfer(내 계좌끼리 옮김).
            - category는 아래 이름 중 하나. 지출: ${spending.joinToString(", ") { "${it.name}(${it.label})" }}. 수입: ${incomes.joinToString(", ") { "${it.name}(${it.label})" }}.
            - 여럿이 먹고 내가 다 냈으면 split=i_paid, amount는 낸 전체 금액. 남이 내서 내가 내 몫을 보낼 거면 split=they_paid, amount는 내 몫. 아니면 none.
            - people은 나를 포함한 사람 수 (모르면 1). "친구 셋이랑"이면 나까지 4명. my_share는 말한 내 몫, 말이 없으면 0.
            - label은 가게나 내용을 짧게 (예: 고깃집, 스타벅스, 월급). memo는 따로 남길 말, 없으면 빈 문자열.
            - 금액·날짜가 확실하지 않으면 unsure에 짧게 적어. 없으면 빈 문자열.
        """.trimIndent()
        val names = categories.map { it.name }
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "date" to mapOf("type" to "string", "description" to "YYYY-MM-DD"),
                "amount" to mapOf("type" to "integer"),
                "label" to mapOf("type" to "string"),
                "kind" to mapOf("type" to "string", "enum" to listOf("spend", "income", "transfer")),
                "category" to mapOf("type" to "string", "enum" to names),
                "split" to mapOf("type" to "string", "enum" to listOf("none", "i_paid", "they_paid")),
                "people" to mapOf("type" to "integer"),
                "my_share" to mapOf("type" to "integer"),
                "memo" to mapOf("type" to "string"),
                "unsure" to mapOf("type" to "string"),
            ),
            "required" to listOf("date", "amount", "label", "kind", "category", "split", "people", "my_share", "memo", "unsure"),
            "additionalProperties" to false,
        )
        return AiRequest(system, text.trim(), schema, maxTokens = 2_000, quick = true)
    }

    /** 답 JSON → 기록. 이상한 값은 안전하게 고친다 (날짜는 오늘 근처, 금액은 양수) */
    fun parseSpoken(json: String, today: LocalDate, categories: List<Category>): SpokenEntry? = runCatching {
        val o = JSONObject(json)
        val type = when (o.optString("kind")) {
            "income" -> EntryType.INCOME
            "transfer" -> EntryType.TRANSFER
            else -> EntryType.SPEND
        }
        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull()
            ?.takeIf { !it.isAfter(today.plusDays(31)) && !it.isBefore(today.minusDays(366)) } ?: today
        val category = categories.firstOrNull { it.name == o.optString("category") && it.income == (type == EntryType.INCOME) }
            ?: if (type == EntryType.INCOME) Category.OTHER_INCOME else Category.ETC
        val amount = o.optLong("amount", 0)
        if (amount <= 0) return null
        SpokenEntry(
            date = date,
            amount = amount,
            label = o.optString("label").trim().take(40),
            type = type,
            category = category,
            split = o.optString("split", "none").takeIf { type == EntryType.SPEND } ?: "none",
            people = o.optInt("people", 1).coerceIn(1, 20),
            myShare = o.optLong("my_share", 0).coerceIn(0, amount),
            memo = o.optString("memo").trim().take(100),
            unsure = o.optString("unsure").trim(),
        )
    }.getOrNull()

    // ── 이번 달 점검 · 물어보기 ──────────────────────────────

    /** 보낼 합계 숫자. 기록 하나하나(가게 이름·메모)·계좌 이름·알림 글은 넣지 않는다 */
    fun summary(home: HomeState, splits: SplitBook = SplitBook.Empty): String = buildString {
        val p = home.plan
        appendLine("[기간] 이번 달(월급날 기준) ${home.cycle.start} ~ ${home.cycle.end}, 오늘 ${home.today}(${dow(home.today)})")
        appendLine("[돈 나누기] 월급 ${won(home.salary)}, 고정 지출 ${won(p.fixedTotal)}, 먼저 떼는 저축 ${won(p.savings)}, 생활비 예산 ${won(p.disposable)}")
        appendLine("[이번 달] 생활비 쓴 돈 ${won(home.monthLiving)}, 고정 포함 지출 ${won(home.monthSpent)}, 수입 ${won(home.monthIncome)}")
        home.insights.forecast?.let { f ->
            append("[이대로 쓰면] 월말 생활비 예상 ${won(f.projected)} → ")
            append(if (f.leftover >= 0) "${won(f.leftover)} 남음" else "${won(-f.leftover)} 모자람")
            append(", 남은 ${f.daysLeft}일, 하루 ${won(f.perDayToFit)}씩 쓰면 예산에 맞음")
            append(", 하루 평균 평일 ${won(f.weekdayAvg)} · 주말 ${won(f.weekendAvg)}")
            f.runOut?.let { append(", 이 속도면 ${it}에 생활비가 떨어짐") }
            if (f.plannedAhead > 0) append(", 남은 날에 잡아 둔 계획 ${won(f.plannedAhead)}")
            appendLine()
        }
        home.currentWeek?.let { w ->
            appendLine("[이번 주] ${w.week.start}~${w.week.end}, 주 예산 ${won(w.budget)}, 지난주에서 넘어온 돈 ${won(w.carryIn)}, 쓴 돈 ${won(w.spent)}, 남은 돈 ${won(w.available)}, 오늘 쓸 수 있는 돈 ${won(home.todayAllowance)}")
        }
        home.results.filter { it.week.end.isBefore(home.today) }.takeIf { it.isNotEmpty() }?.let { done ->
            appendLine("[끝난 주] " + done.joinToString(", ") { "${it.week.index + 1}주차 예산 ${won(it.budget)} 중 ${won(it.spent)} 씀" })
        }
        if (home.streak > 0) appendLine("[연속] 예산 지킨 주 ${home.streak}주 연속")
        val entries = home.entries.mapNotNull { runCatching { it.toEntry() }.getOrNull() }
            .filter { it.type == EntryType.SPEND || it.type == EntryType.EXCEPTION || it.type == EntryType.REFUND }
        val cats = Stats.byCategory(entries)
        if (cats.isNotEmpty()) {
            appendLine(
                "[분류별 생활비] " + cats.joinToString(", ") { t ->
                    "${t.category.label} ${won(t.amount)}(${t.count}건" + (home.categoryBudgets[t.category]?.let { b -> ", 예산 ${won(b)}" } ?: "") + ")"
                },
            )
        }
        val s = home.split
        appendLine("[모은 돈] 저축·투자 ${won(s.savings)}, 목표에 넣은 돈 ${won(s.goals)}, 목표에 안 넣은 아낀 돈 ${won(s.unallocated)}, 이번 달 아낀 돈 ${won(home.savedThisMonth)}")
        home.goals.takeIf { it.isNotEmpty() }?.let { gs ->
            appendLine("[목표] " + gs.joinToString(", ") { g -> "${g.goal.name} ${won(g.filled)}/${won(g.goal.target)}" })
        }
        if (splits.toReceive > 0 || splits.toSend > 0) appendLine("[더치페이] 받을 돈 ${won(splits.toReceive)}, 보낼 돈 ${won(splits.toSend)}")
        home.insights.subscriptions.takeIf { it.isNotEmpty() }?.let { subs ->
            appendLine("[정기 결제로 보이는 것] ${subs.size}건, 한 달 ${won(subs.sumOf { it.amount })}")
        }
    }.trim()

    fun coachRequest(summary: String): AiRequest {
        val system = """
            너는 가계부 앱 "돈't"의 돈 관리 코치야. 사용자의 이번 달 합계 숫자만 보고 한국어 '~해요'체로 짧고 구체적으로 점검해 줘.
            - 숫자는 아래 요약에 있는 것만 쓰고 지어내지 마. 계산할 때는 정확하게.
            - 훈계하지 말고 친한 친구처럼. 잘한 점이 있으면 먼저 말해.
            - headline: 한 문장 총평. pace: 쓰는 속도와 월말 예상을 한두 문장으로.
            - good·watch: 각 0~3개, 한 문장씩. actions: 이번 주에 바로 할 수 있는 행동 2~3개, 금액을 넣어서.
        """.trimIndent()
        val list = mapOf("type" to "array", "items" to mapOf("type" to "string"))
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "headline" to mapOf("type" to "string"),
                "pace" to mapOf("type" to "string"),
                "good" to list,
                "watch" to list,
                "actions" to list,
            ),
            "required" to listOf("headline", "pace", "good", "watch", "actions"),
            "additionalProperties" to false,
        )
        return AiRequest(system, summary, schema, maxTokens = 6_000)
    }

    fun parseCoach(json: String): CoachReport? = runCatching {
        val o = JSONObject(json)
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        CoachReport(o.optString("headline"), o.optString("pace"), list("good"), list("watch"), list("actions"))
            .takeIf { it.headline.isNotBlank() }
    }.getOrNull()

    fun askRequest(summary: String, question: String): AiRequest {
        val system = """
            너는 가계부 앱 "돈't"의 돈 관리 도우미야. 사용자의 이번 달 합계 숫자(아래 요약)를 근거로 질문에 한국어 '~해요'체로 답해.
            - 다섯 문장 안으로, 숫자를 넣어 구체적으로. 요약에 없는 건 지어내지 말고 모른다고 말해.
            - 투자 종목 추천이나 대출 권유는 하지 마.
        """.trimIndent()
        return AiRequest(system, "$summary\n\n[질문] ${question.trim()}", maxTokens = 3_000)
    }

    /** 물어보기 예시 */
    val SAMPLE_QUESTIONS = listOf(
        "이번 주 남은 날 하루에 얼마씩 쓰면 돼요?",
        "어디서 줄이면 월말에 남을까요?",
        "저축을 더 늘려도 될까요?",
        "주말에 너무 많이 쓰는 편인가요?",
    )
}
