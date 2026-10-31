package com.simbio.don.notify

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 알림 문구는 실제 앱 형식이 아니라 흔한 모양을 흉내 낸 예시다.
class ForeignTest {
    private val card = AppRule("card.app", "OO카드")
    private val wallet = AppRule("wallet.app", "OO뱅크")
    private val t0 = LocalDateTime.of(2026, 10, 29, 21, 0)
    private fun parse(pkg: String, text: String, title: String = "", rates: Map<String, Double> = emptyMap()) =
        NotificationParser.parse(RawNotification(pkg, title, text, t0), listOf(card, wallet), rates)

    @Test
    fun `외화 금액 읽기`() {
        assertEquals(Foreign("USD", 12.99), Currencies.find("[해외승인] OO카드 USD 12.99 NETFLIX.COM"))
        assertEquals(Foreign("USD", 10.0), Currencies.find("$10.00 결제 | 스타벅스 (외화통장)"))
        assertEquals(Foreign("USD", 1234.5), Currencies.find("1,234.50달러 결제했어요"))
        assertEquals(Foreign("JPY", 1200.0), Currencies.find("JPY 1,200 로손"))
        assertEquals(Foreign("EUR", 8.5), Currencies.find("€8.50 결제"))
        assertEquals(Foreign("USD", 25.0), Currencies.find("25 USD 승인"))
        // 잔액·한도는 빼고, 일시불·동네 이름은 통화가 아니다
        assertEquals(Foreign("USD", 3.0), Currencies.find("잔액 USD 100.00 · 결제 USD 3.00"))
        assertNull(Currencies.find("12,000원 일시불 역삼1동 김밥"))
        assertEquals("USD 12.99", Foreign("USD", 12.99).text())
        assertEquals("JPY 1,200", Foreign("JPY", 1200.0).text())
    }

    @Test
    fun `원화가 없으면 받은 환율로, 없으면 어림으로 바꾼다`() {
        val withRate = assertIs<ParseResult.Payment>(parse("wallet.app", "$10.00 결제 | 스타벅스", rates = mapOf("USD" to 1385.0))).payment
        assertEquals(13_850, withRate.amount)
        assertEquals(Foreign("USD", 10.0), withRate.foreign)
        assertEquals(false, withRate.rateGuess)
        assertEquals("스타벅스", withRate.label)

        val guess = assertIs<ParseResult.Payment>(parse("wallet.app", "$10.00 결제 | 스타벅스")).payment
        assertEquals(14_000, guess.amount)
        assertTrue(guess.rateGuess)

        // 원화 청구 금액이 같이 오면 그게 실제 금액
        val billed = assertIs<ParseResult.Payment>(parse("card.app", "해외승인 USD 12.99 (17,850원) NETFLIX")).payment
        assertEquals(17_850, billed.amount)
        assertEquals(Foreign("USD", 12.99), billed.foreign)
        assertEquals(false, billed.rateGuess)
        assertEquals("NETFLIX", billed.label)
    }

    @Test
    fun `환전은 지출이 아니라 환전으로`() {
        val r = assertIs<ParseResult.Payment>(parse("wallet.app", "12,500원을 10.00달러로 환전했어요 · 출금 계좌 OO뱅크"))
        assertEquals(RuleAction.EXCHANGE, r.payment.action)
        assertEquals(12_500, r.payment.amount)
        assertEquals("달러 환전", r.payment.label)
        assertEquals(Foreign("USD", 10.0), r.payment.foreign)
        assertEquals(1250.0, Currencies.rateOf(12_500, Foreign("USD", 10.0)))
        assertTrue(Currencies.plausible("USD", 1250.0))
        assertTrue(!Currencies.plausible("USD", 12.5))
        assertIs<Decision.Exchange>(ReviewPolicy.decide(r, emptyList(), emptyList()))
        // 환전 규칙이 없던 옛 앱 규칙도 환전으로 본다
        val old = AppRule("old.app", "옛 은행", keywordRules = AppRule.DEFAULT_KEYWORD_RULES.filter { it.action != RuleAction.EXCHANGE })
        val o = NotificationParser.parse(RawNotification("old.app", "", "외화 출금 50,000원 환전", t0), listOf(old))
        assertEquals(RuleAction.EXCHANGE, assertIs<ParseResult.Payment>(o).payment.action)
    }

    @Test
    fun `거래 뒤 잔액 읽기`() {
        assertEquals(1_234_000, NotificationParser.extractBalance("출금 5,000원 잔액 1,234,000원"))
        assertEquals(98_000, NotificationParser.extractBalance("입금 2,000원 | 잔액: 98,000원"))
        assertNull(NotificationParser.extractBalance("결제 5,000원 누적 120,000원"))
        val p = assertIs<ParseResult.Payment>(parse("wallet.app", "체크카드 출금 5,000원 GS25 잔액 1,234,000원")).payment
        assertEquals(5_000, p.amount)
        assertEquals(1_234_000, p.balanceAfter)
    }
}
