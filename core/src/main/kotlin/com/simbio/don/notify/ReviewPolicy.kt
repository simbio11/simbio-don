package com.simbio.don.notify

import com.simbio.don.budget.FixedExpense
import java.time.Duration
import kotlin.math.abs

enum class ReviewReason {
    /** 비슷한 시각에 같은 금액이 다른 앱에서도 잡힘 (예: 카드사 + 토스페이) */
    POSSIBLE_DUPLICATE,

    /** 등록한 고정비와 이름이나 금액이 비슷함 */
    LOOKS_LIKE_FIXED_EXPENSE,

    /** 취소·환불 */
    REFUND,

    /** 어떤 키워드에도 안 걸림 */
    UNCLASSIFIED,
}

sealed interface Decision {
    data class AutoApply(val payment: ParsedPayment) : Decision
    data class NeedsReview(val payment: ParsedPayment, val reason: ReviewReason, val related: ParsedPayment? = null) :
        Decision

    /** 입금 — 지출로 쓰지 않고 월급 제안 후보로만 둔다 */
    data class Income(val payment: ParsedPayment) : Decision
}

/** 확실한 건 자동 반영, 의심스러운 건 확인함으로. */
object ReviewPolicy {
    val DUPLICATE_WINDOW: Duration = Duration.ofMinutes(3)

    /** 고정비 금액과 이 비율(만분율) 안으로 차이 나면 고정비로 의심 */
    const val FIXED_EXPENSE_TOLERANCE_BPS = 300

    fun decide(
        result: ParseResult,
        recent: List<ParsedPayment>,
        fixedExpenses: List<FixedExpense>,
    ): Decision? {
        val payment = when (result) {
            is ParseResult.Payment -> result.payment
            is ParseResult.Unclassified -> {
                // 키워드가 없어도 다른 앱에 같은 금액이 막 잡혔다면 같은 결제일 가능성이 크다
                val dup = recent.firstOrNull { isDuplicate(result.payment, it) }
                return if (dup != null) {
                    Decision.NeedsReview(result.payment, ReviewReason.POSSIBLE_DUPLICATE, dup)
                } else {
                    Decision.NeedsReview(result.payment, ReviewReason.UNCLASSIFIED)
                }
            }
            ParseResult.Ignored, ParseResult.NotWatched -> return null
        }
        return when (payment.action) {
            RuleAction.INCOME -> Decision.Income(payment)
            RuleAction.REFUND -> {
                val dup = recent.firstOrNull { isDuplicate(payment, it) }
                Decision.NeedsReview(payment, if (dup != null) ReviewReason.POSSIBLE_DUPLICATE else ReviewReason.REFUND, dup)
            }
            RuleAction.IGNORE -> null
            RuleAction.SPEND -> {
                val dup = recent.firstOrNull { isDuplicate(payment, it) }
                when {
                    dup != null -> Decision.NeedsReview(payment, ReviewReason.POSSIBLE_DUPLICATE, dup)
                    fixedExpenses.any { looksLikeFixed(payment, it) } ->
                        Decision.NeedsReview(payment, ReviewReason.LOOKS_LIKE_FIXED_EXPENSE)

                    else -> Decision.AutoApply(payment)
                }
            }
        }
    }

    /** 다른 앱에서 비슷한 시각에 같은 금액·같은 종류(지출끼리, 환불끼리)로 잡힘 */
    fun isDuplicate(a: ParsedPayment, b: ParsedPayment): Boolean =
        a.action == b.action && (a.action == RuleAction.SPEND || a.action == RuleAction.REFUND) &&
            a.amount == b.amount && a.packageName != b.packageName &&
            abs(Duration.between(a.postedAt, b.postedAt).toSeconds()) <= DUPLICATE_WINDOW.toSeconds()

    fun looksLikeFixed(p: ParsedPayment, f: FixedExpense): Boolean {
        if (f.name.isNotBlank() && f.name in p.rawText) return true
        if (f.amount <= 0) return false
        return abs(p.amount - f.amount) * 10_000 <= f.amount * FIXED_EXPENSE_TOLERANCE_BPS
    }
}
