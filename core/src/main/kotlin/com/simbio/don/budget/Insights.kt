package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 이번 달 생활비 예측. 지난 날의 쓰는 속도(평일·주말 따로)로 남은 날을 채워 월말에 얼마나 남을지 본다.
 *
 * @property projected 월말까지 쓸 것으로 보이는 생활비 (지금까지 쓴 것 포함)
 * @property perDayToFit 남은 날 하루에 이만큼씩 쓰면 생활비가 딱 맞음 (이미 넘겼으면 0)
 * @property runOut 이대로면 생활비가 바닥나는 날 (월말 전에 바닥날 때만)
 */
data class MonthForecast(
    val budget: Long,
    val spent: Long,
    val projected: Long,
    val daysLeft: Int,
    val weekdayAvg: Long,
    val weekendAvg: Long,
    val perDayToFit: Long,
    val runOut: LocalDate?,
) {
    /** 월말에 남을 돈 (음수면 모자람) */
    val leftover: Long get() = budget - projected
}

object Forecast {
    /** 날 수가 적을 때는 계획한 속도(예산 ÷ 날 수)와 섞어서 본다 (첫 며칠의 큰 지출에 휘둘리지 않게) */
    private const val TRUST_DAYS = 7

    /**
     * [start]~[end] 한 달에서 [today]까지의 생활비([entries], 고정 지출 제외)로 월말을 예측한다.
     * 하루 지출이 그날까지 중간값의 3배를 넘으면 한 번 쓰는 큰돈으로 보고 평균에는 그만큼만 센다.
     */
    fun month(entries: List<Entry>, start: LocalDate, end: LocalDate, budget: Long, today: LocalDate): MonthForecast {
        val days = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val last = minOf(today, end)
        val elapsedDays = if (last.isBefore(start)) emptyList() else generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val perDay = Stats.daily(entries, livingOnly = true)
        val spent = elapsedDays.sumOf { perDay[it] ?: 0 }.coerceAtLeast(0)
        val plan = if (days > 0) budget / days else 0

        val values = elapsedDays.map { (perDay[it] ?: 0).coerceAtLeast(0) }
        val median = values.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val cap = if (median > 0) median * 3 else plan * 3
        fun avg(pick: (LocalDate) -> Boolean): Long {
            val l = elapsedDays.filter(pick)
            if (l.isEmpty()) return plan
            val mean = l.sumOf { minOf((perDay[it] ?: 0).coerceAtLeast(0), cap.coerceAtLeast(1)) } / l.size
            val n = l.size.coerceAtMost(TRUST_DAYS)
            return (mean * n + plan * (TRUST_DAYS - n)) / TRUST_DAYS
        }
        val weekday = avg { !it.isWeekend() }
        val weekend = avg { it.isWeekend() }

        val rest = generateSequence(last.plusDays(1)) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        var acc = spent
        var runOut: LocalDate? = null
        rest.forEach { d ->
            acc += if (d.isWeekend()) weekend else weekday
            if (runOut == null && acc > budget && spent <= budget) runOut = d
        }
        val daysLeft = rest.size
        return MonthForecast(
            budget = budget,
            spent = spent,
            projected = acc,
            daysLeft = daysLeft,
            weekdayAvg = weekday,
            weekendAvg = weekend,
            perDayToFit = if (daysLeft == 0) 0 else ((budget - spent) / daysLeft).coerceAtLeast(0),
            runOut = runOut,
        )
    }

    private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
}

/** 무지출 날: 지난 날 중 생활비를 한 푼도 안 쓴 날 (고정 지출·이체·수입은 상관없음) */
data class NoSpend(val days: Set<LocalDate>, val streak: Int)

object NoSpendDays {
    /** [start]부터 [today] 전날까지. [streak]는 어제부터 거꾸로 이어진 무지출 날 수 */
    fun of(entries: List<Entry>, start: LocalDate, today: LocalDate): NoSpend {
        val perDay = Stats.daily(entries, livingOnly = true)
        val past = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.toList()
        val days = past.filter { (perDay[it] ?: 0) <= 0 }.toSet()
        var streak = 0
        for (d in past.asReversed()) if (d in days) streak++ else break
        return NoSpend(days, streak)
    }
}

/** 매달 나가는 것 같은 결제 (구독·정기 결제) */
data class Subscription(val label: String, val amount: Long, val day: Int, val months: Int, val category: Category)

object Subscriptions {
    /**
     * 같은 곳에서 비슷한 금액(±10%)이 서로 다른 두 달 이상, 비슷한 날짜(±4일)에 나갔으면 정기 결제로 본다.
     * 이미 고정 지출로 적어 둔 이름([known])·고정 지출 기록은 뺀다.
     */
    fun detect(entries: List<Entry>, known: Collection<String> = emptyList()): List<Subscription> {
        val knownKeys = known.map { CategoryGuesser.key(it) }.toSet()
        return entries.filter { it.type == EntryType.SPEND }
            .groupBy { CategoryGuesser.key(it.label) }
            .filterKeys { it.isNotEmpty() && it !in knownKeys }
            .mapNotNull { (_, list) ->
                val byMonth = list.groupBy { YearMonth.from(it.date) }
                if (byMonth.size < 2) return@mapNotNull null
                // 달마다 하나 (그 달에 여러 번이면 정기 결제가 아님)
                if (byMonth.values.any { it.size > 1 }) return@mapNotNull null
                val amounts = list.map { it.amount }.sorted()
                val median = amounts[amounts.size / 2]
                if (amounts.any { kotlin.math.abs(it - median) * 10 > median }) return@mapNotNull null
                val daysOfMonth = list.map { it.date.dayOfMonth }.sorted()
                if (daysOfMonth.last() - daysOfMonth.first() > 8) return@mapNotNull null
                val latest = list.maxBy { it.date }
                Subscription(latest.label, latest.amount, daysOfMonth[daysOfMonth.size / 2], byMonth.size, latest.category)
            }
            .sortedByDescending { it.amount }
    }
}

/** 한 줄 분석 */
data class Insight(val title: String, val body: String, val good: Boolean)

object Insights {
    /**
     * 이번 주(오늘까지)와 지난주 같은 요일까지를 견줘 가장 많이 늘거나 준 카테고리,
     * 최근 4주의 주말·평일 하루 평균, 가장 많이 쓴 요일.
     */
    fun of(entries: List<Entry>, weekStart: LocalDate, today: LocalDate): List<Insight> {
        val out = mutableListOf<Insight>()
        val living = entries.filter { it.type == EntryType.SPEND || it.type == EntryType.REFUND || it.type == EntryType.EXCEPTION }
        val elapsed = ChronoUnit.DAYS.between(weekStart, today).toInt().coerceAtLeast(0)
        val thisWeek = living.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
        val prevStart = weekStart.minusDays(7)
        val lastWeek = living.filter { !it.date.isBefore(prevStart) && !it.date.isAfter(prevStart.plusDays(elapsed.toLong())) }
        if (lastWeek.isNotEmpty() || thisWeek.isNotEmpty()) {
            val now = Stats.byCategory(thisWeek).associate { it.category to it.amount }
            val before = Stats.byCategory(lastWeek).associate { it.category to it.amount }
            val diffs = (now.keys + before.keys).associateWith { (now[it] ?: 0) - (before[it] ?: 0) }.filterValues { it != 0L }
            diffs.maxByOrNull { kotlin.math.abs(it.value) }?.takeIf { kotlin.math.abs(it.value) >= 3_000 }?.let { (c, d) ->
                out += if (d > 0) {
                    Insight("${c.label}에 더 썼어요", "지난주 이맘때보다 ${"%,d".format(d)}원 더 썼어요.", good = false)
                } else {
                    Insight("${c.label}${eul(c.label)} 아꼈어요", "지난주 이맘때보다 ${"%,d".format(-d)}원 덜 썼어요.", good = true)
                }
            }
            val totalNow = Stats.livingTotal(thisWeek)
            val totalBefore = Stats.livingTotal(lastWeek)
            if (totalBefore > 0 && totalNow < totalBefore * 8 / 10) {
                out += Insight("지난주보다 천천히 쓰고 있어요", "이번 주는 지난주 이맘때보다 ${(100 - totalNow * 100 / totalBefore)}% 덜 썼어요.", good = true)
            }
        }
        // 최근 4주: 주말·평일, 요일
        val from = today.minusDays(28)
        val recent = living.filter { it.date.isAfter(from) && it.date.isBefore(today) }
        if (recent.isNotEmpty()) {
            val perDay = Stats.daily(recent, livingOnly = true)
            val days = generateSequence(from.plusDays(1)) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.toList()
            val (weekend, weekday) = days.partition { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
            val we = if (weekend.isEmpty()) 0 else weekend.sumOf { perDay[it] ?: 0 } / weekend.size
            val wd = if (weekday.isEmpty()) 0 else weekday.sumOf { perDay[it] ?: 0 } / weekday.size
            if (wd > 0 && we >= wd * 3 / 2) {
                out += Insight("주말에 많이 써요", "주말 하루에 평일의 ${"%.1f".format(we.toDouble() / wd)}배를 써요 (최근 4주).", good = false)
            }
            val byDow = days.groupBy { it.dayOfWeek }.mapValues { (_, l) -> l.sumOf { perDay[it] ?: 0 } / l.size }
            byDow.maxByOrNull { it.value }?.takeIf { it.value > 0 && byDow.size >= 7 }?.let { (dow, v) ->
                out += Insight("${dowLabel(dow)}요일에 가장 많이 써요", "최근 4주 ${dowLabel(dow)}요일 하루 평균 ${"%,d".format(v)}원이에요.", good = false)
            }
        }
        return out
    }

    fun dowLabel(d: DayOfWeek): String = "월화수목금토일"[d.value - 1].toString()

    /** 을/를 (받침이 있으면 을) */
    fun eul(word: String): String {
        val c = word.lastOrNull { it in '가'..'힣' } ?: return "를"
        return if ((c - '가') % 28 != 0) "을" else "를"
    }
}

/** 다가오는 고정 지출·수입 */
data class Upcoming(val label: String, val amount: Long, val date: LocalDate, val income: Boolean)

object UpcomingPayments {
    /** [today]부터 [days]일 안에 나갈 고정 지출 (아직 안 적은 것: [lastDue] 이후) */
    fun of(
        fixed: List<Triple<String, Long, Int?>>,
        lastDue: List<LocalDate?>,
        cycle: PayCycle,
        today: LocalDate,
        days: Long = 7,
    ): List<Upcoming> = fixed.mapIndexedNotNull { i, (name, amount, day) ->
        day ?: return@mapIndexedNotNull null
        val due = Payday.dueDates(cycle, day).firstOrNull { !it.isBefore(today) } ?: return@mapIndexedNotNull null
        if (due.isAfter(today.plusDays(days))) return@mapIndexedNotNull null
        if (lastDue[i]?.let { !it.isBefore(due) } == true) return@mapIndexedNotNull null
        Upcoming(name, amount, due, income = false)
    }.sortedBy { it.date }
}
