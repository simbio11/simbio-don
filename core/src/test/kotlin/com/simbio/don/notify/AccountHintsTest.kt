package com.simbio.don.notify

import com.simbio.don.budget.AutoRate
import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryGuesser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountHintsTest {
    @Test
    fun 카드_끝자리와_가린_계좌번호를_찾는다() {
        assertEquals("c1234", AccountHints.tail("[신한카드(1234)승인] 홍*동 6,500원(일시불) 스타벅스"))
        assertEquals("c4321", AccountHints.tail("[KB국민카드 4321 승인] 홍*동 6,500원 일시불 스타벅스"))
        assertEquals("c9876", AccountHints.tail("삼성카드[9876] 결제 12,000원"))
        assertEquals("c5555", AccountHints.tail("[5555] 승인 홍길동 3,000원 GS25"))
        assertEquals("a123456", AccountHints.tail("신한 11/08 09:12 110-***-123456 출금 6,500원"))
        assertEquals("a88507", AccountHints.tail("입금 19,000원\n240-******-88507 김민수"))
    }

    @Test
    fun 금액이나_날짜를_끝자리로_착각하지_않는다() {
        assertNull(AccountHints.tail("신한카드 승인 스타벅스 6,500원 일시불"))
        assertNull(AccountHints.tail("신한카드 1000원 결제"))
        assertNull(AccountHints.tail("2026 11 08 09:12 GS25 4,000원 결제"))
        assertNull(AccountHints.tail("출금 50,000원 신한카드"))
    }

    @Test
    fun 저장하고_읽고_사람이_읽는_말() {
        assertEquals(listOf("c1234", "a88507"), AccountHints.parse("c1234, a88507"))
        assertEquals("c1234,a88507", AccountHints.encode(listOf("c1234", "a88507", "c1234")))
        assertEquals("카드 끝 1234", AccountHints.label("c1234"))
        assertEquals("계좌 끝 88507", AccountHints.label("a88507"))
    }

    @Test
    fun 분류_근거는_알려_준_것_키워드_모름_순서() {
        val learned = mapOf(CategoryGuesser.key("동네 칼국수") to Category.FOOD, CategoryGuesser.key("이상한가게") to Category.ETC)
        assertEquals(CategoryGuesser.Basis.LEARNED, CategoryGuesser.basis("동네 칼국수", learned))
        // 기타로 알려 준 것도 알려 준 것 (다시 묻지 않는다)
        assertEquals(CategoryGuesser.Basis.LEARNED, CategoryGuesser.basis("이상한가게", learned))
        assertEquals(CategoryGuesser.Basis.KEYWORD, CategoryGuesser.basis("스타벅스 강남", learned))
        assertEquals(CategoryGuesser.Basis.UNKNOWN, CategoryGuesser.basis("김씨네", learned))
    }

    @Test
    fun 자동_기록률() {
        assertEquals(100, AutoRate(0, 0).percent)
        assertEquals(100, AutoRate(20, 0).percent)
        assertEquals(90, AutoRate(20, 2).percent)
        assertEquals(0, AutoRate(5, 9).percent)
        assertEquals("최근 20건 중 18건은 알아서 · 2건 확인 필요", AutoRate(20, 2).label)
    }
}
