package com.simbio.don.budget

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 돈 나갈 계획 하나. 실제 기록과 따로 두고, 쓰면 [actual](실제로 쓴 돈)을 채워 계획과 견준다.
 * 안 쓰기로 하면 [canceled] — 그만큼 아낀 돈.
 */
data class Plan(
    val id: Long,
    val date: LocalDate,
    val amount: Long,
    val label: String,
    val category: Category = Category.ETC,
    val actual: Long? = null,
    val canceled: Boolean = false,
) {
    val open: Boolean get() = actual == null && !canceled
}

enum class PlanFitKind { STRONG, SPLIT, MAYBE }

/** 계획과 지출이 얼마나 맞는지. [people]은 엔빵 같을 때 몇 명이서 */
data class PlanFit(val kind: PlanFitKind, val people: Int? = null)

/**
 * 한 달 계획 요약.
 * @property planned 이 달 계획 합계 (안 쓰기로 한 것 빼고)
 * @property openAmount 아직 안 쓴 계획
 * @property donePlanned 쓴 계획들의 계획 금액, [doneActual]은 그 실제 금액
 * @property canceledAmount 안 쓰기로 한 계획 (아낀 돈)
 */
data class PlanSummary(
    val planned: Long,
    val openAmount: Long,
    val openCount: Int,
    val donePlanned: Long,
    val doneActual: Long,
    val doneCount: Int,
    val canceledAmount: Long,
) {
    /** 계획보다 덜 쓴 돈 (음수면 더 씀) */
    val doneDiff: Long get() = donePlanned - doneActual
}

object Plans {
    /** [start]~[end] 사이 날짜의 계획 요약 */
    fun summary(plans: List<Plan>, start: LocalDate, end: LocalDate): PlanSummary {
        val inRange = plans.filter { it.date in start..end }
        val open = inRange.filter { it.open }
        val done = inRange.filter { it.actual != null }
        val canceled = inRange.filter { it.canceled }
        return PlanSummary(
            planned = inRange.filter { !it.canceled }.sumOf { it.amount },
            openAmount = open.sumOf { it.amount },
            openCount = open.size,
            donePlanned = done.sumOf { it.amount },
            doneActual = done.sumOf { it.actual ?: 0 },
            doneCount = done.size,
            canceledAmount = canceled.sumOf { it.amount },
        )
    }

    /** 아직 안 쓴 계획 중 [from]부터 [to]까지 날짜인 것 (날짜순) */
    fun upcoming(plans: List<Plan>, from: LocalDate, to: LocalDate): List<Plan> =
        plans.filter { it.open && it.date in from..to }.sortedBy { it.date }

    /** 날짜가 지났는데 아직 안 쓴(썼다고 안 한) 계획 */
    fun overdue(plans: List<Plan>, today: LocalDate): List<Plan> = plans.filter { it.open && it.date.isBefore(today) }.sortedBy { it.date }

    /**
     * 이 계획을 쓴 것 같은 실제 지출: 날짜가 3일 안이고, 금액이 ±30% 안이며, 같은 카테고리이거나 이름이 겹치면.
     * 가장 가까운 것 하나 (이미 다른 계획에 쓴 [taken]은 빼고).
     */
    fun <T> match(plan: Plan, candidates: List<Pair<T, Entry>>, taken: Set<T> = emptySet()): T? =
        candidates.filter { (id, e) ->
            id !in taken &&
                (e.type == EntryType.SPEND || e.type == EntryType.EXCEPTION || e.type == EntryType.FIXED) &&
                kotlin.math.abs(ChronoUnit.DAYS.between(plan.date, e.date)) <= 3 &&
                kotlin.math.abs(e.amount - plan.amount) * 10 <= plan.amount * 3 &&
                (e.category == plan.category || nameOverlaps(plan.label, e.label))
        }.minByOrNull { (_, e) -> kotlin.math.abs(e.amount - plan.amount) * 1000 + kotlin.math.abs(ChronoUnit.DAYS.between(plan.date, e.date)) }?.first

    /**
     * 실제 지출 [e]가 계획 [plan]을 쓴 것인지. [paid]는 낸 돈 전부 (나눠 냈으면 [e].amount는 내 몫).
     * - 확실(STRONG): 계획 날 ±1일에, 같은 분류·이름이면 금액이 계획의 ±50% 안, 아니면 거의 같은 금액(±3%) — 자동으로 잇는다
     * - 엔빵 같음(SPLIT): 계획 날 ±1일에 같은 분류인데 낸 돈이 계획의 2~8배(±12%) — "n명이 나눠 냈나요?"
     * - 아마(MAYBE): 그 밖에 그럴듯한 것 (일주일 전에 미리 산 것 등) — 물어본다
     */
    fun fit(plan: Plan, e: Entry, paid: Long = e.amount, alreadySplit: Boolean = false): PlanFit? {
        if (!plan.open || plan.amount <= 0) return null
        if (e.type != EntryType.SPEND && e.type != EntryType.EXCEPTION) return null
        val days = ChronoUnit.DAYS.between(plan.date, e.date)
        if (days !in -7..2) return null
        val near = days in -1..1
        val same = e.category == plan.category || nameOverlaps(plan.label, e.label)
        val p = plan.amount
        val a = e.amount
        val diff = kotlin.math.abs(a - p)
        return when {
            near && ((same && diff * 2 <= p) || diff * 100 <= p * 3) -> PlanFit(PlanFitKind.STRONG)
            near && same && !alreadySplit && paid >= p * 2 -> (2..8).firstOrNull { n -> kotlin.math.abs(paid - p * n) * 100 <= p * n * 12 }
                ?.let { PlanFit(PlanFitKind.SPLIT, it) }
                ?: if (paid <= p * 3) PlanFit(PlanFitKind.MAYBE) else null
            near && same && a * 10 >= p * 4 && a <= p * 3 -> PlanFit(PlanFitKind.MAYBE)
            same && diff * 10 <= p * 3 -> PlanFit(PlanFitKind.MAYBE)
            near && diff * 10 <= p * 3 -> PlanFit(PlanFitKind.MAYBE)
            else -> null
        }
    }

    /** 여러 계획 중 이 지출과 가장 잘 맞는 것 (확실 > 엔빵 > 아마, 날이 가깝고 금액이 비슷한 순) */
    fun <T> best(plans: List<Pair<T, Plan>>, e: Entry, paid: Long = e.amount, alreadySplit: Boolean = false): Pair<T, PlanFit>? =
        plans.mapNotNull { (id, p) -> fit(p, e, paid, alreadySplit)?.let { Triple(id, p, it) } }
            .minWithOrNull(
                compareBy<Triple<T, Plan, PlanFit>> { it.third.kind.ordinal }
                    .thenBy { kotlin.math.abs(ChronoUnit.DAYS.between(it.second.date, e.date)) }
                    .thenBy { kotlin.math.abs(e.amount - it.second.amount) },
            )?.let { it.first to it.third }

    /** 캘린더 일정 이름으로 계획 카테고리 짐작 (결혼식·생일 → 경조사, 회식·약속 → 식비…). 모르면 가게 이름처럼 */
    fun categoryFor(title: String): Category {
        val t = CategoryGuesser.key(title)
        EVENT_WORDS.firstOrNull { (_, words) -> words.any { it in t } }?.let { return it.first }
        return CategoryGuesser.guess(title)
    }

    private val EVENT_WORDS: List<Pair<Category, List<String>>> = listOf(
        Category.EVENT to listOf("결혼", "웨딩", "생일", "돌잔치", "장례", "조문", "집들이", "선물", "기념일", "환갑", "칠순"),
        Category.CULTURE to listOf("미술", "영화", "공연", "콘서트", "전시", "뮤지컬", "여행", "캠핑", "야구", "축구"),
        Category.HEALTH to listOf("병원", "치과", "검진", "진료"),
        Category.EDUCATION to listOf("수업", "학원", "강의", "시험"),
        Category.FOOD to listOf("회식", "술", "모임", "약속", "저녁", "점심", "밥", "식사", "브런치", "동창"),
    )

    private fun nameOverlaps(a: String, b: String): Boolean {
        val x = CategoryGuesser.key(a)
        val y = CategoryGuesser.key(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x in y || y in x)
    }
}
