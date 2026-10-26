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
    fun `가게 이름이 없으면 빈 이름 (결제·승인 같은 말을 이름으로 쓰지 않는다)`() {
        assertEquals("", MerchantExtractor.extract("OO카드 승인", "23,000원 승인"))
        assertEquals("", MerchantExtractor.extract("결제", "5,000원 결제되었어요"))
        assertEquals("", MerchantExtractor.extract("결제 완료", "₩5,000 · OO체크카드", "OO페이"))
        assertEquals("", MerchantExtractor.extract("결제 알림", "고객님 12,000원 결제가 완료되었습니다"))
    }

    @Test
    fun `여러 흔한 모양`() {
        // 가게 이름이 제목에
        assertEquals("스타벅스", MerchantExtractor.extract("스타벅스 강남R점", "5,000원 결제 · OO뱅크 체크카드"))
        // "에서"를 떼고
        assertEquals("올리브영", MerchantExtractor.extract("결제", "올리브영에서 12,000원 결제했어요"))
        // 가맹점 이름표
        assertEquals("CU 역삼점", MerchantExtractor.extract("OO카드 승인", "승인금액: 3,200원\n가맹점: CU 역삼점\n일시불"))
        // ₩ 금액과 대괄호 가게 이름
        assertEquals("GS25", MerchantExtractor.extract("결제", "[GS25] ₩3,200 결제"))
        // 줄마다 나뉜 카드 알림 (날짜·시각 뒤 줄)
        assertEquals("버거킹", MerchantExtractor.extract("OO카드", "OO카드(1234) 승인\n12,000원 일시불\n10/26 12:34\n버거킹 역삼\n누적 230,000원"))
    }

    @Test
    fun `고친 이름의 자리를 배워 다음 알림에서 쓴다`() {
        // 이 앱은 가게 이름이 제목 빼고 셋째 줄에 통째로 온다 (흔한 규칙으로는 못 찾음)
        val title = "결제 알림"
        val first = "고객님 결제가 완료되었습니다\n5,000원\n동네 빵집 본점"
        val spot = MerchantExtractor.learn(title, first, "동네 빵집 본점")!!
        assertEquals(MerchantSpot(3, null), spot)
        assertEquals(spot, MerchantSpot.decode(spot.encode()))
        assertEquals("우리 동네 김밥", MerchantExtractor.extract(title, "고객님 결제가 완료되었습니다\n7,000원\n우리 동네 김밥", "", spot))
        // 낱말 하나만 고친 경우
        val word = MerchantExtractor.learn("", "승인 12,000원 홍길동님 스타벅스 누적 5만", "스타벅스")!!
        assertEquals("이디야", MerchantExtractor.extract("", "승인 4,000원 홍길동님 이디야 누적 6만", "", word))
        // 알림 글에 없는 이름이면 배우지 않는다
        assertEquals(null, MerchantExtractor.learn(title, first, "엄마 생일 케이크"))
    }
}
