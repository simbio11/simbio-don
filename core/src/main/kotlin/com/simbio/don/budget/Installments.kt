package com.simbio.don.budget

import java.time.LocalDate

/**
 * 할부. 산 날부터 달마다 같은 날에 나눠 적는다 (그 달에 그 날짜가 없으면 말일).
 * 무이자로 보고, 나누어 떨어지지 않는 원은 첫 달에 더한다.
 */
object Installments {
    /** 카드 할부는 보통 2~36개월 */
    val MONTHS = 2..36

    /** 달마다 낼 금액 (첫 달에 남는 원을 더함) */
    fun split(total: Long, months: Int): List<Long> {
        require(months in MONTHS) { "할부는 ${MONTHS.first}~${MONTHS.last}개월: $months" }
        require(total >= months) { "금액이 너무 작아요: $total" }
        val each = total / months
        val rest = total - each * months
        return List(months) { if (it == 0) each + rest else each }
    }

    /** 내는 날들 */
    fun dates(first: LocalDate, months: Int): List<LocalDate> =
        (0 until months).map { k -> first.plusMonths(k.toLong()).let { d -> d.withDayOfMonth(minOf(first.dayOfMonth, d.lengthOfMonth())) } }

    private val MONTHS_TEXT = Regex("""(\d{1,2})\s*개월""")
    private val HALBU_TEXT = Regex("""할부\s*\(?\s*(\d{1,2})""")

    /**
     * 결제 알림 문구에서 할부 개월 수 ("3개월", "할부 06", "(12개월)"). 일시불·무이자 표기만 있으면 null.
     * "1개월"·없는 것은 일시불.
     */
    fun monthsIn(text: String): Int? {
        if ("일시불" in text) return null
        val m = MONTHS_TEXT.find(text) ?: HALBU_TEXT.find(text) ?: return null
        return m.groupValues[1].toIntOrNull()?.takeIf { it in MONTHS }
    }
}
