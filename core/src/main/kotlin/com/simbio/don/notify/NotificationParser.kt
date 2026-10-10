package com.simbio.don.notify

import java.time.LocalDateTime

data class ParsedPayment(
    val packageName: String,
    val action: RuleAction,
    val amount: Long,
    val label: String,
    val postedAt: LocalDateTime,
    val rawText: String,
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

    fun parse(n: RawNotification, rules: List<AppRule>): ParseResult {
        val rule = rules.firstOrNull { it.packageName == n.packageName && it.enabled } ?: return ParseResult.NotWatched
        val body = "${n.title} ${n.text}"

        val action = RuleAction.entries.firstOrNull { action ->
            rule.keywordRules.any { r -> r.action == action && r.keywords.any { it.isNotBlank() && it in body } }
        }
        if (action == RuleAction.IGNORE) return ParseResult.Ignored

        val amount = extractAmount(body) ?: return ParseResult.Ignored
        val payment = ParsedPayment(n.packageName, action ?: RuleAction.SPEND, amount, label(n, rule), n.postedAt, body)
        return if (action == null) ParseResult.Unclassified(payment) else ParseResult.Payment(payment)
    }

    fun extractAmount(text: String): Long? = AMOUNT.findAll(text)
        .firstOrNull { m ->
            val before = text.substring(maxOf(0, m.range.first - 6), m.range.first)
            NON_PAYMENT_PREFIXES.none { it in before }
        }
        ?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        ?.takeIf { it > 0 }

    private fun label(n: RawNotification, rule: AppRule): String =
        n.title.ifBlank { rule.displayName }.take(40)
}
