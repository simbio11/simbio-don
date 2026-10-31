package com.simbio.don.notify

import java.time.LocalDateTime

data class ParsedPayment(
    val packageName: String,
    val action: RuleAction,
    val amount: Long,
    val label: String,
    val postedAt: LocalDateTime,
    /** 알림 제목과 본문 (줄바꿈으로 이음) */
    val rawText: String,
    /** 알림에서 가게(보낸 사람) 이름을 못 찾아 [label]이 "○○카드 결제"처럼 앱 이름인 것 */
    val nameUnknown: Boolean = false,
    /** 외화 결제·환전이면 외화 금액 ([amount]는 원화: 알림의 원화 금액, 없으면 환율로 바꾼 것) */
    val foreign: Foreign? = null,
    /** 알림에 원화 금액이 없어 어림 환율로 바꿨음 (나중에 환율을 받으면 다시 바꾼다) */
    val rateGuess: Boolean = false,
    /** 알림에 적힌 거래 뒤 잔액 ("잔액 1,234,000원") — 앱 잔액과 견준다 */
    val balanceAfter: Long? = null,
)

sealed interface ParseResult {
    /** 고르지 않은 앱이거나 꺼진 앱 — 본문을 보지 않는다 */
    data object NotWatched : ParseResult

    /** 무시 키워드에 걸렸거나 금액이 없음 */
    data object Ignored : ParseResult

    data class Payment(val payment: ParsedPayment) : ParseResult

    /** 금액은 있는데 어떤 키워드에도 안 걸림 → 확인함으로 */
    data class Unclassified(val payment: ParsedPayment) : ParseResult
}

object NotificationParser {
    private val AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")

    /** 이 단어가 금액 바로 앞에 있으면 결제 금액이 아니다 */
    private val NON_PAYMENT_PREFIXES = listOf("잔액", "누적", "한도", "포인트", "적립", "잔여")

    /**
     * [rates]: 받아 둔 환율 (통화 → 1단위에 원). 외화만 적힌 알림은 이걸로 원화로 바꾸고, 없으면 [Currencies.FALLBACK] 어림.
     */
    fun parse(n: RawNotification, rules: List<AppRule>, rates: Map<String, Double> = emptyMap()): ParseResult {
        val rule = rules.firstOrNull { it.packageName == n.packageName && it.enabled } ?: return ParseResult.NotWatched
        val body = "${n.title} ${n.text}"
        // 환전 규칙이 생기기 전에 저장한 앱 규칙에도 기본 환전 낱말을
        val keywordRules = if (rule.keywordRules.any { it.action == RuleAction.EXCHANGE }) rule.keywordRules
        else rule.keywordRules + KeywordRule(AppRule.EXCHANGE_KEYWORDS, RuleAction.EXCHANGE)

        val action = RuleAction.entries.firstOrNull { action ->
            keywordRules.any { r -> r.action == action && r.keywords.any { it.isNotBlank() && it in body } }
        }
        if (action == RuleAction.IGNORE) return ParseResult.Ignored

        val won = extractAmount(body)
        val foreign = Currencies.find(body)
        val rate = foreign?.let { rates[it.currency] }
        val amount = won ?: foreign?.let { f -> (rate ?: Currencies.FALLBACK[f.currency])?.let { f.toWon(it) } }?.takeIf { it > 0 }
            ?: return ParseResult.Ignored
        val act = action ?: RuleAction.SPEND
        val merchant = MerchantExtractor.extract(n.title, n.text, rule.displayName, rule.merchantSpot)
        val raw = listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString("\n")
        val exchange = act == RuleAction.EXCHANGE
        val label = if (exchange) fallbackLabel(rule.displayName, act, foreign) else merchant.ifBlank { fallbackLabel(rule.displayName, act, foreign) }
        val payment = ParsedPayment(
            n.packageName, act, amount, label.take(40), n.postedAt, raw, merchant.isBlank() && !exchange,
            foreign = foreign,
            rateGuess = won == null && foreign != null && rate == null,
            balanceAfter = extractBalance(body),
        )
        return if (action == null) ParseResult.Unclassified(payment) else ParseResult.Payment(payment)
    }

    private val BALANCE = Regex("""(?:잔액|잔고|통장잔액|계좌잔액)\s*[:：]?\s*(-?\d{1,3}(?:,\d{3})+|-?\d+)\s*원""")

    /** 거래 뒤 잔액 ("잔액 1,234,000원") */
    fun extractBalance(text: String): Long? =
        BALANCE.find(text)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

    fun extractAmount(text: String): Long? = AMOUNT.findAll(text)
        .firstOrNull { m ->
            val before = text.substring(maxOf(0, m.range.first - 6), m.range.first)
            NON_PAYMENT_PREFIXES.none { it in before }
        }
        ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        ?.takeIf { it > 0 }

    /** 가게 이름을 못 찾았을 때: "결제" 한 마디 대신 어느 앱(카드)인지라도 */
    fun fallbackLabel(appName: String, action: RuleAction, foreign: Foreign? = null): String = when (action) {
        RuleAction.EXCHANGE -> "${foreign?.let { currencyName(it.currency) } ?: "외화"} 환전"
        else -> "${appName.ifBlank { "알림" }} " + when (action) {
            RuleAction.INCOME -> "입금"
            RuleAction.REFUND -> "취소"
            else -> if (foreign != null) "해외 결제" else "결제"
        }
    }

    fun currencyName(code: String): String = when (code) {
        "USD" -> "달러"; "EUR" -> "유로"; "JPY" -> "엔화"; "GBP" -> "파운드"; "CNY" -> "위안"
        else -> code
    }
}
