package com.simbio.don.notify

import kotlin.test.Test
import kotlin.test.assertEquals

// 실제 앱 형식이 아니라 흔한 모양을 흉내 낸 예시다.
class MerchantExtractorTest {
    @Test
    fun `카드 문자 형식은 시각 뒤 가게 이름`() {
        assertEquals(
            "스타벅스강남점",
            MerchantExtractor.extract("", "[Web발신] OO카드(1234)승인 홍*동 4,500원(일시불)10/10 19:02 스타벅스강남점 누적 120,000원"),
        )
    }

    @Test
    fun `간편결제 형식은 남은 첫 단어`() {
        assertEquals("GS25", MerchantExtractor.extract("OO페이", "GS25 역삼점에서 3,200원 결제했어요", "OO페이"))
        assertEquals("배달의민족", MerchantExtractor.extract("결제 완료", "배달의민족 23,000원"))
    }

    @Test
    fun `못 찾으면 제목`() {
        assertEquals("OO카드 승인", MerchantExtractor.extract("OO카드 승인", "23,000원 승인"))
    }
}
