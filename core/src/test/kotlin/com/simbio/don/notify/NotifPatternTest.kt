package com.simbio.don.notify

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

class NotifPatternTest {
    @Test
    fun `금액·잔액·마스킹이 달라도 같은 모양이면 같은 패턴`() {
        val a = NotifPattern.of("[KB]", "입금 3,200,000원 (주)회사 급여 잔액 3,300,000원")
        val b = NotifPattern.of("[KB]", "입금 3,250,000원 (주)회사 급여 잔액 3,350,000원")
        assertEquals(a, b)
        assertEquals(
            NotifPattern.of("토스", "홍*동님이 12,000원을 보냈어요 123-***-4567"),
            NotifPattern.of("토스", "홍*동님이 9,000원을 보냈어요 123-***-9999"),
        )
    }

    @Test
    fun `보낸 사람이나 방향이 다르면 다른 패턴`() {
        assertNotEquals(
            NotifPattern.of("[KB]", "입금 30,000원 김철수 잔액 130,000원"),
            NotifPattern.of("[KB]", "입금 30,000원 이영희 잔액 130,000원"),
        )
        assertNotEquals(
            NotifPattern.of("[KB]", "입금 30,000원 김철수"),
            NotifPattern.of("[KB]", "출금 30,000원 김철수"),
        )
    }
}
