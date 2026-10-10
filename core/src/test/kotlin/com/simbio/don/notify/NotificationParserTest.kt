package com.simbio.don.notify

import com.simbio.don.budget.FixedExpense
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// 알림 문구는 실제 앱 형식이 아니라 규칙 동작을 보기 위한 예시다.
class NotificationParserTest {

    private val card = AppRule("card.app", "OO카드")
    private val pay = AppRule("pay.app", "OO페이")
    private val rules = listOf(card, pay, AppRule("off.app", "꺼진 앱", enabled = false))
    private val t0 = LocalDateTime.of(2026, 11, 3, 19, 2)

    private fun n(pkg: String, text: String, title: String = "", at: LocalDateTime = t0) = RawNotification(pkg, title, text, at)
    private fun parse(pkg: String, text: String) = NotificationParser.parse(n(pkg, text), rules)

    @Test
    fun `고르지 않은 앱과 꺼진 앱은 보지 않는다`() {
        assertEquals(ParseResult.NotWatched, parse("other.app", "결제 10,000원"))
        assertEquals(ParseResult.NotWatched, parse("off.app", "결제 10,000원"))
    }

    @Test
    fun `결제 금액을 뽑는다`() {
        val r = assertIs<ParseResult.Payment>(parse("card.app", "승인 23,000원 일시불 OO치킨"))
        assertEquals(23_000, r.payment.amount)
        assertEquals(RuleAction.SPEND, r.payment.action)
    }

    @Test
    fun `잔액·누적 금액은 결제 금액이 아니다`() {
        assertEquals(4_500, NotificationParser.extractAmount("누적 1,234,000원 결제 4,500원"))
        assertEquals(4_500, NotificationParser.extractAmount("잔액 500,000원 / 출금 4,500원"))
        assertNull(NotificationParser.extractAmount("결제가 완료되었습니다"))
    }

    @Test
    fun `무시가 환불보다, 환불이 결제보다 먼저다`() {
        assertEquals(ParseResult.Ignored, parse("card.app", "[광고] 결제하면 5,000원 할인"))
        val r = assertIs<ParseResult.Payment>(parse("card.app", "결제 취소 23,000원"))
        assertEquals(RuleAction.REFUND, r.payment.action)
    }

    @Test
    fun `키워드가 없지만 금액이 있으면 미분류`() {
        assertIs<ParseResult.Unclassified>(parse("card.app", "OO마트 12,000원"))
    }

    @Test
    fun `사용자가 키워드 규칙을 바꿀 수 있다`() {
        val custom = AppRule("bank.app", "OO은행", keywordRules = listOf(KeywordRule(listOf("이체"), RuleAction.SPEND)))
        val r = NotificationParser.parse(n("bank.app", "이체 30,000원"), listOf(custom))
        assertEquals(RuleAction.SPEND, assertIs<ParseResult.Payment>(r).payment.action)
    }

    @Test
    fun `다른 앱에서 비슷한 시각에 같은 금액이면 중복 의심`() {
        val first = assertIs<ParseResult.Payment>(parse("card.app", "승인 23,000원")).payment
        val second = NotificationParser.parse(n("pay.app", "결제 23,000원", at = t0.plusSeconds(40)), rules)

        val d = assertIs<Decision.NeedsReview>(ReviewPolicy.decide(second, listOf(first), emptyList()))
        assertEquals(ReviewReason.POSSIBLE_DUPLICATE, d.reason)
        assertEquals(first, d.related)

        val later = NotificationParser.parse(n("pay.app", "결제 23,000원", at = t0.plusMinutes(10)), rules)
        assertIs<Decision.AutoApply>(ReviewPolicy.decide(later, listOf(first), emptyList()))
    }

    @Test
    fun `고정비와 비슷하면 확인함으로`() {
        val fixed = listOf(FixedExpense("월세", 700_000))
        val byAmount = ReviewPolicy.decide(parse("card.app", "출금 700,000원"), emptyList(), fixed)
        assertEquals(ReviewReason.LOOKS_LIKE_FIXED_EXPENSE, assertIs<Decision.NeedsReview>(byAmount).reason)
        val byName = ReviewPolicy.decide(parse("card.app", "월세 출금 650,000원"), emptyList(), fixed)
        assertEquals(ReviewReason.LOOKS_LIKE_FIXED_EXPENSE, assertIs<Decision.NeedsReview>(byName).reason)
        assertIs<Decision.AutoApply>(ReviewPolicy.decide(parse("card.app", "출금 500,000원"), emptyList(), fixed))
    }

    @Test
    fun `환불·미분류는 확인함, 입금은 월급 후보`() {
        assertEquals(
            ReviewReason.REFUND,
            assertIs<Decision.NeedsReview>(ReviewPolicy.decide(parse("card.app", "환불 9,000원"), emptyList(), emptyList())).reason,
        )
        assertIs<Decision.Income>(ReviewPolicy.decide(parse("card.app", "급여 입금 3,240,000원"), emptyList(), emptyList()))
        assertNull(ReviewPolicy.decide(parse("other.app", "결제 1원"), emptyList(), emptyList()))
    }
}
