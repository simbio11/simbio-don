package com.simbio.don.budget

import java.time.LocalDate

/** "이거 사도 될까?" — 산다면 이번 주·이번 달·목표가 어떻게 되는지 */
data class PurchaseImpact(
    val price: Long,
    /** 이번 주 남은 돈 (산 뒤) */
    val weekLeftBefore: Long,
    val weekLeftAfter: Long,
    /** 이번 주 남은 날 하루에 쓸 수 있는 돈 (산 뒤) */
    val perDayBefore: Long,
    val perDayAfter: Long,
    /** 이대로 쓰면 월말에 남을 돈 (산 뒤, 음수면 모자람) */
    val monthLeftBefore: Long,
    val monthLeftAfter: Long,
    /** 월급으로 치면 몇 분 일한 돈 (월급을 모르면 null) */
    val workMinutes: Long?,
    val verdict: PurchaseVerdict,
)

enum class PurchaseVerdict(val title: String, val body: String) {
    OK("사도 괜찮아요", "이번 주 예산 안이고 월말에도 남아요."),
    WEEK_OVER("이번 주는 넘어요", "월말엔 괜찮아요. 다음 주에 사면 이번 주를 지킬 수 있어요."),
    MONTH_SHORT("월말이 빠듯해져요", "이번 주 예산 안이지만, 이대로 쓰면 월말에 모자라요."),
    NO("다시 생각해 봐요", "이번 주 예산도 넘고 월말에도 모자라요."),
}

object PurchaseCheck {
    /** 한 달 일하는 시간 (주 40시간 기준 월 소정근로시간) */
    const val MONTH_WORK_HOURS = 209L

    /**
     * [weekLeft]: 이번 주 쓸 수 있는 남은 돈, [weekDaysLeft]: 오늘 포함 이번 주 남은 날,
     * [monthLeft]: 월말 예측으로 남을 돈, [salary]: 월급 (0이면 일한 시간은 안 셈).
     */
    fun of(price: Long, weekLeft: Long, weekDaysLeft: Int, monthLeft: Long, salary: Long): PurchaseImpact {
        val days = weekDaysLeft.coerceAtLeast(1)
        val weekAfter = weekLeft - price
        val monthAfter = monthLeft - price
        val verdict = when {
            weekAfter >= 0 && monthAfter >= 0 -> PurchaseVerdict.OK
            weekAfter < 0 && monthAfter >= 0 -> PurchaseVerdict.WEEK_OVER
            weekAfter >= 0 -> PurchaseVerdict.MONTH_SHORT
            else -> PurchaseVerdict.NO
        }
        return PurchaseImpact(
            price = price,
            weekLeftBefore = weekLeft,
            weekLeftAfter = weekAfter,
            perDayBefore = (weekLeft / days).coerceAtLeast(0),
            perDayAfter = (weekAfter / days).coerceAtLeast(0),
            monthLeftBefore = monthLeft,
            monthLeftAfter = monthAfter,
            workMinutes = if (salary > 0) price * 60 * MONTH_WORK_HOURS / salary else null,
            verdict = verdict,
        )
    }
}

/** 지난달 성적표 (월급날 기준 한 달) */
data class MonthReport(
    val start: LocalDate,
    val end: LocalDate,
    val budget: Long,
    val spent: Long,
    /** 생활비에서 남긴 돈 (마지막 주 남은 돈, 넘었으면 음수) */
    val leftover: Long,
    val weeksKept: Int,
    val weeks: Int,
    val noSpendDays: Int,
    val top: CategoryTotal?,
    /** 그 전 달보다 가장 많이 줄인·늘린 분류 (그 전 달 기록이 없으면 null) */
    val bestCut: Pair<Category, Long>?,
    val worstRise: Pair<Category, Long>?,
    val grade: String,
) {
    /** 예산 대비 쓴 비율 (%) */
    val usedPercent: Int get() = if (budget <= 0) 0 else (spent * 100 / budget).toInt()
}

object MonthReports {
    /** 예산 대비 쓴 돈으로 매기는 등급 */
    fun grade(spent: Long, budget: Long): String {
        if (budget <= 0) return "-"
        val p = spent * 100 / budget
        return when {
            p <= 85 -> "A+"
            p <= 95 -> "A"
            p <= 100 -> "B"
            p <= 110 -> "C"
            else -> "D"
        }
    }

    /** [entries]: 그 달 기록, [before]: 그 전 달 기록 (없으면 견주지 않음) */
    fun of(plan: CyclePlan, entries: List<Entry>, before: List<Entry>? = null): MonthReport {
        val ledger = entries.fold(CycleLedger(plan)) { l, e -> runCatching { l.add(e) }.getOrDefault(l) }
        val results = ledger.results()
        val spent = Stats.livingTotal(entries)
        val living = entries.filter { it.type != EntryType.FIXED }
        val cats = Stats.byCategory(living)
        val diffs = before?.let { b ->
            val prev = Stats.byCategory(b.filter { it.type != EntryType.FIXED }).associate { it.category to it.amount }
            val now = cats.associate { it.category to it.amount }
            (prev.keys + now.keys).associateWith { (now[it] ?: 0) - (prev[it] ?: 0) }
        }
        val start = plan.cycle.start
        val end = plan.cycle.end
        return MonthReport(
            start = start,
            end = end,
            budget = plan.disposable,
            spent = spent,
            leftover = results.lastOrNull()?.carryOut ?: (plan.disposable - spent),
            weeksKept = results.count { it.available >= 0 },
            weeks = results.size,
            noSpendDays = NoSpendDays.of(entries, start, end.plusDays(1)).days.size,
            top = cats.firstOrNull(),
            bestCut = diffs?.filterValues { it < 0 }?.minByOrNull { it.value }?.toPair(),
            worstRise = diffs?.filterValues { it > 0 }?.maxByOrNull { it.value }?.toPair(),
            grade = grade(spent, plan.disposable),
        )
    }
}
