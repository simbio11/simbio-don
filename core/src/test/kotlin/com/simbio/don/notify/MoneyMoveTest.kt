package com.simbio.don.notify

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.time.LocalDateTime

class MoneyMoveTest {
    private val at = LocalDateTime.of(2026, 10, 27, 10, 0)

    @Test
    fun 종류() {
        assertEquals(MoveKind.CARD_BILL, MoneyMoves.kind("카드대금 30,000원 출금", RuleAction.SPEND))
        assertEquals(MoveKind.CASH, MoneyMoves.kind("ATM 출금 100,000원", RuleAction.SPEND))
        assertEquals(MoveKind.SAVINGS, MoneyMoves.kind("청년적금 자동이체 300,000원 출금", RuleAction.SPEND))
        assertEquals(MoveKind.SAVINGS_OUT, MoneyMoves.kind("적금 만기 3,000,000원 입금", RuleAction.INCOME))
        assertEquals(MoveKind.CHARGE, MoneyMoves.kind("카카오페이머니 50,000원 충전", RuleAction.SPEND))
        assertNull(MoneyMoves.kind("티머니 교통카드 충전 10,000원 결제", RuleAction.SPEND))
        assertNull(MoneyMoves.kind("적금 이자 1,200원 입금", RuleAction.INCOME))
        assertNull(MoneyMoves.kind("GS25 4,000원 결제", RuleAction.SPEND))
        assertTrue(MoneyMoves.isCardCompany("신한카드"))
        assertTrue(MoneyMoves.isNotMoney("11월 결제예정금액 523,000원 안내"))
    }

    @Test
    fun 통장에서_카드사로_나간_카드값() {
        assertTrue(MoneyMoves.isCardBillFromBank("출금 출금 50,000원 신한카드"))
        assertTrue(!MoneyMoves.isCardBillFromBank("신한 체크카드 출금 5,000원 GS25"))
        assertTrue(!MoneyMoves.isCardBillFromBank("신한카드 승인 5,000원 GS25"))
    }
}
