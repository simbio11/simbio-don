package com.simbio.don.notify

import kotlin.math.roundToLong

/** 외화 금액 하나 ("USD 12.99") */
data class Foreign(val currency: String, val amount: Double) {
    /** "USD 12.99" · "JPY 1,200" */
    fun text(): String {
        val digits = if (Currencies.decimals(currency) == 0) "%,.0f".format(amount) else "%,.2f".format(amount)
        return "$currency $digits"
    }

    /** 원화로 (1단위에 [rate]원) */
    fun toWon(rate: Double): Long = (amount * rate).roundToLong()
}

/**
 * 외화 결제 알림 읽기와 환율. 알림에 원화 금액이 같이 오면 그걸 쓰고(실제 청구 금액),
 * 외화만 오면 환율로 바꾼다 — 환율은 부르는 쪽이 준다 (내 환전 알림 → 받아 둔 환율 → [FALLBACK]).
 */
object Currencies {
    /** 원화로 바꿀 줄 아는 통화 */
    val KNOWN = listOf("USD", "EUR", "JPY", "GBP", "CNY", "HKD", "TWD", "SGD", "AUD", "CAD", "CHF", "THB", "VND", "PHP")

    /** 환율을 못 받았을 때 어림 (1단위에 원). 알림·설정에서 받은 환율이 있으면 그걸 쓴다 */
    val FALLBACK: Map<String, Double> = mapOf(
        "USD" to 1400.0, "EUR" to 1500.0, "JPY" to 9.4, "GBP" to 1800.0, "CNY" to 195.0, "HKD" to 180.0, "TWD" to 44.0,
        "SGD" to 1050.0, "AUD" to 900.0, "CAD" to 1000.0, "CHF" to 1600.0, "THB" to 40.0, "VND" to 0.055, "PHP" to 24.0,
    )

    fun decimals(currency: String): Int = if (currency in setOf("JPY", "VND", "TWD")) 0 else 2

    private const val NUM = """(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)"""

    /** 통화 이름·기호 → 코드 */
    private val SYMBOLS = listOf(
        "US$" to "USD", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY", "￥" to "JPY",
    )
    private val WORDS = listOf(
        // "불"(일시불)·"동"(역삼1동)은 다른 말과 헷갈려 뺀다
        "달러" to "USD", "유로" to "EUR", "엔화" to "JPY", "엔" to "JPY", "円" to "JPY", "파운드" to "GBP", "위안" to "CNY",
        "홍콩달러" to "HKD", "싱가포르달러" to "SGD", "호주달러" to "AUD", "캐나다달러" to "CAD", "바트" to "THB",
    )

    private val CODE_BEFORE = Regex("""\b(${KNOWN.joinToString("|")})\s*$NUM""")
    private val CODE_AFTER = Regex("""$NUM\s*(${KNOWN.joinToString("|")})\b""")
    private val SYMBOL_BEFORE = Regex("""(US\$|\$|€|£|¥|￥)\s*$NUM""")
    // 긴 이름부터 (홍콩달러가 달러보다 먼저). 뒤에 "로·를" 같은 한 글자는 되고 "엔젤"처럼 이어지는 말은 안 됨
    private val WORD_AFTER = Regex("""$NUM\s*(${WORDS.map { it.first }.sortedByDescending { it.length }.joinToString("|")})(?![가-힣]{2})""")

    /** 알림 글에서 외화 금액 하나 (없으면 null). "잔액"·"한도" 바로 뒤 금액은 빼고 */
    fun find(text: String): Foreign? {
        val hits = buildList {
            CODE_BEFORE.findAll(text).forEach { add(Triple(it.range.first, it.groupValues[1], it.groupValues[2])) }
            CODE_AFTER.findAll(text).forEach { add(Triple(it.range.first, it.groupValues[2], it.groupValues[1])) }
            SYMBOL_BEFORE.findAll(text).forEach { m -> add(Triple(m.range.first, SYMBOLS.first { it.first == m.groupValues[1] }.second, m.groupValues[2])) }
            WORD_AFTER.findAll(text).forEach { m -> add(Triple(m.range.first, WORDS.first { it.first == m.groupValues[2] }.second, m.groupValues[1])) }
        }.sortedBy { it.first }
        return hits.firstOrNull { (at, _, _) ->
            val before = text.substring(maxOf(0, at - 6), at)
            listOf("잔액", "한도", "누적", "잔여").none { it in before }
        }?.let { (_, code, num) -> num.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }?.let { Foreign(code, it) } }
    }

    /** 환전 알림의 환율: 원화 [won]으로 외화 [foreign]을 샀으면 1단위에 몇 원 */
    fun rateOf(won: Long, foreign: Foreign): Double? =
        if (won <= 0 || foreign.amount <= 0) null else won / foreign.amount

    /** 이 환율이 말이 되는지 (어림의 1/3 ~ 3배) — 수수료 줄 같은 다른 금액을 잘못 짝지은 것을 거른다 */
    fun plausible(currency: String, rate: Double): Boolean {
        val f = FALLBACK[currency] ?: return rate > 0
        return rate in f / 3..f * 3
    }
}
