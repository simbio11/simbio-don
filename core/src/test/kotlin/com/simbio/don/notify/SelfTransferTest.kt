package com.simbio.don.notify

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfTransferTest {
    private val at = LocalDateTime.of(2026, 10, 24, 9, 0)
    private fun p(action: RuleAction, text: String, amount: Long = 300_000) = ParsedPayment("bank", action, amount, "은행", at, text)

    @Test
    fun `송금으로 나간 것과 카드 결제를 가린다`() {
        assertTrue(SelfTransfer.isSendOut(p(RuleAction.SPEND, "홍길동님께 300,000원 보냈어요")))
        assertTrue(SelfTransfer.isSendOut(p(RuleAction.SPEND, "[KB] 출금 300,000원 이체")))
        assertFalse(SelfTransfer.isSendOut(p(RuleAction.SPEND, "신한카드 승인 300,000원 스타벅스")))
    }

    @Test
    fun `내 계좌라는 말이나 내 이름이 송금 글에 보이면 내 계좌끼리`() {
        assertTrue(SelfTransfer.looksSelf(p(RuleAction.SPEND, "내 토스뱅크 통장으로 300,000원 보냈어요 (내 계좌)"), null))
        assertTrue(SelfTransfer.looksSelf(p(RuleAction.SPEND, "홍길동님께 300,000원 보냈어요"), "홍길동"))
        assertTrue(SelfTransfer.looksSelf(p(RuleAction.INCOME, "입금 300,000원 홍길동"), "홍길동"))
        // 이름이 있어도 카드 결제 알림은 송금이 아니다
        assertFalse(SelfTransfer.looksSelf(p(RuleAction.SPEND, "홍길동님 카드 승인 300,000원"), "홍길동"))
        assertFalse(SelfTransfer.looksSelf(p(RuleAction.INCOME, "입금 300,000원 김철수"), "홍길동"))
        assertFalse(SelfTransfer.looksSelf(p(RuleAction.SPEND, "홍길동님께 보냈어요"), "홍"))
    }

    @Test
    fun `같은 금액이 15분 안에 나가고 들어오면 짝`() {
        assertTrue(SelfTransfer.isPair(300_000, at, 300_000, at.plusMinutes(2)))
        assertTrue(SelfTransfer.isPair(300_000, at.plusMinutes(3), 300_000, at))
        assertFalse(SelfTransfer.isPair(300_000, at, 300_000, at.plusMinutes(20)))
        assertFalse(SelfTransfer.isPair(300_000, at, 299_000, at))
    }
}
