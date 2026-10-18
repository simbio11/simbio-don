package com.simbio.don.budget

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * 월급날 자동 처리.
 *
 * 월급은 처음 설정할 때 한 번만 묻는다. 그다음부터는
 * - 예상 월급날이 되면 새 달을 시작하고 (월급은 지난달과 같다고 보고),
 * - 월급 입금 알림이 오면 그 금액으로 이번 달 예산을 다시 짠다.
 * - 예상보다 며칠 일찍 월급이 들어오면 그날부터 새 달이다.
 */
object Payday {
    /** 예상 월급날보다 이만큼 일찍 들어온 월급은 그날부터 새 달로 본다 */
    const val EARLY_DAYS = 5L

    /** 새 달이 시작되고 이 날짜 안에 들어온 월급 입금으로 이번 달 월급을 맞춘다 */
    const val LATE_DAYS = 7L

    /** 이보다 적은 입금은 월급으로 보지 않는다 */
    const val MIN_SALARY = 100_000L

    private val SALARY_WORDS = listOf("급여", "월급", "상여", "salary", "payroll")

    /**
     * 월급 입금인지: "급여·월급" 같은 말이 있거나, 지난 월급의 70%~150% 금액.
     * [lastSalary]가 0이면 말로만 판단한다.
     */
    fun isSalaryDeposit(text: String, amount: Long, lastSalary: Long): Boolean {
        if (amount < MIN_SALARY) return false
        val t = text.lowercase()
        if (SALARY_WORDS.any { it in t }) return true
        return lastSalary > 0 && amount * 10 >= lastSalary * 7 && amount * 10 <= lastSalary * 15
    }

    /** [current] 다음 달이 시작될 예상 월급날 */
    fun nextExpected(current: YearMonth, payDay: Int, holidays: Set<LocalDate> = emptySet()): LocalDate =
        PayCycle.expectedPayday(current.plusMonths(1), payDay, holidays)

    /**
     * 새 달이 시작되는 날. [salaryDepositOn]은 다음 월급날 즈음 들어온 월급 입금 날짜 (없으면 null).
     * 아직 새 달이 아니면 null.
     */
    fun nextStart(expected: LocalDate, today: LocalDate, salaryDepositOn: LocalDate?): LocalDate? = when {
        salaryDepositOn != null && salaryDepositOn.isBefore(expected) &&
            !salaryDepositOn.isBefore(expected.minusDays(EARLY_DAYS)) && !salaryDepositOn.isAfter(today) -> salaryDepositOn
        !today.isBefore(expected) -> expected
        else -> null
    }

    /**
     * 매달 [day]일에 나가는 고정 지출이 [cycle] 안에서 나가는 날. 그 달에 그 날짜가 없으면 말일.
     * 주기 안에 없으면 (한 달 중간에 시작해 이미 지난 날) null.
     */
    fun dueDate(cycle: PayCycle, day: Int): LocalDate? {
        require(day in 1..31) { "날짜는 1~31일이어야 합니다: $day" }
        var ym = YearMonth.from(cycle.start)
        while (!ym.atDay(1).isAfter(cycle.end)) {
            val d = ym.atDay(minOf(day, ym.lengthOfMonth()))
            if (d in cycle) return d
            ym = ym.plusMonths(1)
        }
        return null
    }

    /** [cycle] 안에서 [day]일이 오는 날 모두 (주기가 한 달보다 길면 두 번일 수 있다) */
    fun dueDates(cycle: PayCycle, day: Int): List<LocalDate> {
        require(day in 1..31) { "날짜는 1~31일이어야 합니다: $day" }
        val out = mutableListOf<LocalDate>()
        var ym = YearMonth.from(cycle.start)
        while (!ym.atDay(1).isAfter(cycle.end)) {
            val d = ym.atDay(minOf(day, ym.lengthOfMonth()))
            if (d in cycle) out += d
            ym = ym.plusMonths(1)
        }
        return out
    }

    /** 결제 알림이 고정 지출과 금액만 같을 때, 나가는 날 앞뒤 이 날짜 안이면 고정 지출로 본다 */
    const val FIXED_DAY_TOLERANCE = 3L

    /**
     * 결제가 고정 지출 [f]와 맞는지.
     * 이름이 알림에 있으면 맞다. 금액만 비슷하면 나가는 날 앞뒤 [FIXED_DAY_TOLERANCE]일 안일 때만 (날짜를 모르면 아니다).
     */
    fun matchesFixed(text: String, amount: Long, date: LocalDate, f: FixedExpense, cycle: PayCycle?): Boolean {
        if (f.name.isNotBlank() && f.name.length >= 2 && f.name in text) return true
        if (f.amount <= 0 || f.day == null || cycle == null) return false
        val close = abs(amount - f.amount) * 10_000 <= f.amount * 300
        return close && dueDates(cycle, f.day).any { abs(it.toEpochDay() - date.toEpochDay()) <= FIXED_DAY_TOLERANCE }
    }
}
