package com.simbio.don.game

/**
 * 돈't 몰 상품. 모의 쇼핑용으로 직접 만든 목록이다 (쇼핑몰을 긁어 오지 않는다 — 금지 사항).
 * 이름은 브랜드 없이 흔한 물건 이름, 값은 흔히 보는 값 언저리.
 */
data class Product(
    val id: String,
    val name: String,
    val price: Long,
    val kind: ItemKind,
    /** 고르는 칸 이름 ("사이즈", "색상") — 없으면 null */
    val optionLabel: String? = null,
    val options: List<String> = emptyList(),
    /** 많이 담는 물건 (첫 화면에 먼저) */
    val popular: Boolean = false,
    /** 몰 안 분류 (반려동물 용품: 사료·간식·장난감 …), 없으면 null */
    val group: String? = null,
)

/** 장바구니 한 줄 */
data class CartLine(val product: Product, val option: String?, val qty: Int) {
    val total: Long get() = product.price * qty
}

object Catalog {
    private val CLOTHES_SIZE = listOf("S", "M", "L", "XL")
    private val COLORS = listOf("블랙", "화이트", "그레이", "네이비", "베이지")
    private val SHOE_SIZE = (230..280 step 5).map { it.toString() }

    private fun p(id: String, name: String, price: Long, kind: ItemKind, popular: Boolean = false, group: String? = null): Product = when (kind) {
        ItemKind.CLOTHES -> Product(id, name, price, kind, "사이즈", CLOTHES_SIZE, popular)
        ItemKind.SHOES -> Product(id, name, price, kind, "사이즈", SHOE_SIZE, popular)
        ItemKind.BAG, ItemKind.ELECTRONICS -> Product(id, name, price, kind, "색상", COLORS, popular)
        else -> Product(id, name, price, kind, popular = popular, group = group)
    }

    /** 반려동물 용품 분류 (몰의 둘째 줄 칩 순서) */
    val PET_GROUPS = listOf("사료", "간식", "장난감", "하우스", "미용·위생", "산책·패션", "건강")

    val products: List<Product> = listOf(
        p("c1", "오버핏 맨투맨", 39_000, ItemKind.CLOTHES, true),
        p("c2", "기본 반팔티 3팩", 29_900, ItemKind.CLOTHES),
        p("c3", "니트 가디건", 59_000, ItemKind.CLOTHES),
        p("c4", "데님 재킷", 89_000, ItemKind.CLOTHES),
        p("c5", "롱패딩", 239_000, ItemKind.CLOTHES, true),
        p("c6", "와이드 슬랙스", 45_000, ItemKind.CLOTHES),
        p("c7", "트레이닝 세트", 69_000, ItemKind.CLOTHES),
        p("c8", "플리츠 원피스", 79_000, ItemKind.CLOTHES),
        p("c9", "경량 패딩 조끼", 49_000, ItemKind.CLOTHES),
        p("c10", "후드 집업", 55_000, ItemKind.CLOTHES),
        p("b1", "캔버스 에코백", 19_000, ItemKind.BAG),
        p("b2", "미니 크로스백", 59_000, ItemKind.BAG, true),
        p("b3", "노트북 백팩", 89_000, ItemKind.BAG),
        p("b4", "가죽 토트백", 189_000, ItemKind.BAG),
        p("b5", "명품 숄더백", 2_890_000, ItemKind.BAG, true),
        p("b6", "여행용 캐리어", 159_000, ItemKind.BAG),
        p("s1", "흰색 스니커즈", 99_000, ItemKind.SHOES, true),
        p("s2", "쿠션 러닝화", 139_000, ItemKind.SHOES),
        p("s3", "첼시 부츠", 129_000, ItemKind.SHOES),
        p("s4", "여름 슬리퍼", 25_000, ItemKind.SHOES),
        p("s5", "한정판 운동화", 259_000, ItemKind.SHOES, true),
        p("s6", "가죽 로퍼", 119_000, ItemKind.SHOES),
        p("e1", "무선 이어폰", 199_000, ItemKind.ELECTRONICS, true),
        p("e2", "노이즈캔슬링 헤드폰", 359_000, ItemKind.ELECTRONICS),
        p("e3", "스마트워치", 329_000, ItemKind.ELECTRONICS),
        p("e4", "태블릿 11인치", 899_000, ItemKind.ELECTRONICS, true),
        p("e5", "최신 스마트폰", 1_550_000, ItemKind.ELECTRONICS, true),
        p("e6", "게이밍 노트북", 2_190_000, ItemKind.ELECTRONICS),
        p("e7", "기계식 키보드", 129_000, ItemKind.ELECTRONICS),
        p("e8", "보조배터리", 39_000, ItemKind.ELECTRONICS),
        p("e9", "휴대용 게임기", 398_000, ItemKind.ELECTRONICS),
        p("e10", "미니 빔프로젝터", 299_000, ItemKind.ELECTRONICS),
        p("k1", "베스트셀러 소설", 16_800, ItemKind.BOOK),
        p("k2", "재테크 입문서", 18_000, ItemKind.BOOK, true),
        p("k3", "만화책 전권 세트", 98_000, ItemKind.BOOK),
        p("k4", "가죽 다이어리", 22_000, ItemKind.BOOK),
        p("k5", "영어 단어장", 14_000, ItemKind.BOOK),
        p("y1", "향수 50ml", 129_000, ItemKind.BEAUTY, true),
        p("y2", "쿠션 팩트", 32_000, ItemKind.BEAUTY),
        p("y3", "립 틴트 세트", 24_000, ItemKind.BEAUTY),
        p("y4", "스킨케어 세트", 89_000, ItemKind.BEAUTY),
        p("y5", "고속 헤어드라이어", 399_000, ItemKind.BEAUTY),
        p("y6", "네일 키트", 19_000, ItemKind.BEAUTY),
        p("l1", "무드등", 29_000, ItemKind.LIVING),
        p("l2", "디퓨저", 35_000, ItemKind.LIVING),
        p("l3", "극세사 이불", 59_000, ItemKind.LIVING),
        p("l4", "로봇청소기", 599_000, ItemKind.LIVING, true),
        p("l5", "캡슐 커피머신", 289_000, ItemKind.LIVING),
        p("l6", "보온 텀블러", 35_000, ItemKind.LIVING),
        p("l7", "안마의자", 2_490_000, ItemKind.LIVING),
        p("l8", "공기청정기", 259_000, ItemKind.LIVING),
        p("t1", "블록 조립 세트", 89_000, ItemKind.TOY, true),
        p("t2", "한정 피규어", 59_000, ItemKind.TOY),
        p("t3", "곰 인형", 32_000, ItemKind.TOY),
        p("t4", "보드게임", 39_000, ItemKind.TOY),
        p("t5", "1000피스 퍼즐", 25_000, ItemKind.TOY),
        p("t6", "랜덤 키링", 9_900, ItemKind.TOY),
        p("m1", "치킨 한 마리", 23_000, ItemKind.MEAL, true),
        p("m2", "마라탕", 18_000, ItemKind.MEAL),
        p("m3", "떡볶이 세트", 21_000, ItemKind.MEAL),
        p("m4", "피자 라지", 29_000, ItemKind.MEAL),
        p("m5", "초밥 세트", 32_000, ItemKind.MEAL),
        p("m6", "삼겹살 2인분", 36_000, ItemKind.MEAL),
        p("m7", "편의점 도시락", 5_500, ItemKind.MEAL),
        p("m8", "햄버거 세트", 9_800, ItemKind.MEAL),
        p("d1", "아이스 아메리카노", 4_500, ItemKind.DRINK, true),
        p("d2", "흑당 버블티", 6_500, ItemKind.DRINK),
        p("d3", "딸기 라떼", 6_800, ItemKind.DRINK),
        p("d4", "생과일 주스", 7_500, ItemKind.DRINK),
        p("d5", "와인 한 병", 39_000, ItemKind.DRINK),
        p("d6", "수제 맥주 4캔", 16_000, ItemKind.DRINK),
        // 반려동물 용품 — 쿠팡·네이버쇼핑에 흔한 것들을 살짝 비튼 이름으로. 사면 집 친구를 돌볼 수 있다 (PetCare)
        // 사료
        p("pf1", "[멍멍배송] 로얄캔딘 독 어덜트 2kg", 29_000, ItemKind.PET, true, "사료"),
        p("pf2", "[로캣배송] 로얄캔딘 캣 인도어 2kg", 32_000, ItemKind.PET, false, "사료"),
        p("pf8", "오리진짜 프리미엄 사료 5kg", 69_000, ItemKind.PET, true, "사료"),
        p("pf9", "아가나 그레인프리 연어 2kg", 54_000, ItemKind.PET, false, "사료"),
        p("pf10", "로얄캔딘 키튼 퍼스트 2kg", 33_000, ItemKind.PET, false, "사료"),
        p("pf11", "하리미 소프트 사료 1.2kg", 17_000, ItemKind.PET, false, "사료"),
        // 간식
        p("pf3", "수제 개껌 뼈다귀 대", 7_900, ItemKind.PET, false, "간식"),
        p("pf4", "챠오츄륵 냥이 간식 20입", 12_000, ItemKind.PET, true, "간식"),
        p("pf5", "멍멍 캔 간식 닭가슴살", 6_500, ItemKind.PET, false, "간식"),
        p("pf6", "냥냥 연어 파우치 (1개)", 3_500, ItemKind.PET, false, "간식"),
        p("pf7", "닥터덴탈 칫솔껌 30개입", 9_900, ItemKind.PET, false, "간식"),
        p("pf12", "닭가슴살 육포 300g", 15_900, ItemKind.PET, false, "간식"),
        p("pf13", "고양이 참치캔 24개입", 21_000, ItemKind.PET, false, "간식"),
        p("pf14", "동결건조 북어 트릿", 11_900, ItemKind.PET, false, "간식"),
        p("pf15", "반려동물 전용 우유 250ml", 3_900, ItemKind.PET, false, "간식"),
        // 장난감
        p("pt1", "삑삑이 고무공 레드", 6_900, ItemKind.PET, false, "장난감"),
        p("pt2", "깃털 낚싯대 (고양이 환장)", 8_900, ItemKind.PET, false, "장난감"),
        p("pt3", "튼튼 터그 로프 매듭", 7_900, ItemKind.PET, false, "장난감"),
        p("pt4", "오리 삑삑이 인형", 9_500, ItemKind.PET, false, "장난감"),
        p("pt5", "냥냥 와글 터널 3구", 14_900, ItemKind.PET, false, "장난감"),
        p("pt6", "원반 던지기 프리스비", 8_900, ItemKind.PET, false, "장난감"),
        p("pt7", "스너플 노즈워크 매트", 19_000, ItemKind.PET, false, "장난감"),
        p("pt8", "레이저 포인터 (집사 팔 아픔)", 6_900, ItemKind.PET, false, "장난감"),
        p("pt9", "캣닢 물고기 쿠션 인형", 5_900, ItemKind.PET, false, "장난감"),
        p("pt10", "자동 굴러가는 스마트 공", 18_900, ItemKind.PET, false, "장난감"),
        // 하우스·가구
        p("pg1", "구름 푹신 방석 (M)", 35_000, ItemKind.PET, false, "하우스"),
        p("pg2", "원목 강아지 하우스 실내용", 69_000, ItemKind.PET, false, "하우스"),
        p("pg3", "5단 캣타워 (집사 필수)", 129_000, ItemKind.PET, true, "하우스"),
        p("pg4", "골판지 스크래쳐 (L)", 15_000, ItemKind.PET, false, "하우스"),
        p("pg5", "스마트 자동급식기 4L", 89_000, ItemKind.PET, false, "하우스"),
        p("pg6", "숨숨집 텐트 (낯가림 방지)", 39_000, ItemKind.PET, false, "하우스"),
        p("pg7", "여름 쿨매트 (냉감)", 22_000, ItemKind.PET, false, "하우스"),
        p("pg8", "후드형 고양이 화장실", 45_000, ItemKind.PET, false, "하우스"),
        p("pg9", "스텐 밥그릇 2종 세트", 15_000, ItemKind.PET, false, "하우스"),
        p("pg10", "소파 오르기 계단 (슬개골)", 29_000, ItemKind.PET, false, "하우스"),
        p("pg11", "창문 부착 고양이 해먹", 15_900, ItemKind.PET, false, "하우스"),
        p("pg12", "안전 펜스 문 120cm", 39_000, ItemKind.PET, false, "하우스"),
        p("pg13", "[로캣배송] 하드 이동장", 36_000, ItemKind.PET, false, "하우스"),
        p("pg14", "자동 물 분수대 2L", 32_000, ItemKind.PET, false, "하우스"),
        p("pg15", "배변패드 100매 (대용량)", 15_000, ItemKind.PET, false, "하우스"),
        p("pg16", "두부 고양이 모래 7L", 14_900, ItemKind.PET, false, "하우스"),
        // 미용·위생
        p("pc1", "털 쏙쏙 빗질 브러시", 12_000, ItemKind.PET, false, "미용·위생"),
        p("pc2", "저자극 반려동물 샴푸 500ml", 14_000, ItemKind.PET, false, "미용·위생"),
        p("pc3", "무소음 발톱깎이", 8_000, ItemKind.PET, false, "미용·위생"),
        p("pc4", "집에서 하는 전동 이발기", 38_000, ItemKind.PET, false, "미용·위생"),
        p("pc5", "귀 세정제 100ml", 9_900, ItemKind.PET, false, "미용·위생"),
        p("pc6", "눈물 자국 클리닝 티슈", 6_900, ItemKind.PET, false, "미용·위생"),
        p("pc7", "치약 칫솔 세트 (닭맛)", 8_900, ItemKind.PET, false, "미용·위생"),
        p("pc8", "꼬순내 지우개 탈취 스프레이", 9_900, ItemKind.PET, false, "미용·위생"),
        // 산책·패션
        p("pa1", "[멍멍배송] 편한 H형 하네스", 25_000, ItemKind.PET, false, "산책·패션"),
        p("pa2", "튼튼 리드줄 1.5m", 18_000, ItemKind.PET, false, "산책·패션"),
        p("pa3", "배변봉투 롤 300매", 4_500, ItemKind.PET, false, "산책·패션"),
        p("pa4", "각인 이름표 (뼈다귀형)", 12_000, ItemKind.PET, false, "산책·패션"),
        p("pa5", "노란 우비 (비 오는 날 산책)", 21_000, ItemKind.PET, false, "산책·패션"),
        p("pa6", "줄무늬 니트 스웨터", 24_000, ItemKind.PET, false, "산책·패션"),
        p("pa7", "휴대용 물병 겸 물그릇", 12_900, ItemKind.PET, false, "산책·패션"),
        p("pa8", "LED 야광 목걸이 (야간 산책)", 9_900, ItemKind.PET, false, "산책·패션"),
        p("pa9", "미끄럼 방지 멍멍 부츠 4P", 14_900, ItemKind.PET, false, "산책·패션"),
        p("pa10", "리플렉터 안전 조끼", 17_000, ItemKind.PET, false, "산책·패션"),
        p("pa11", "핼러윈 호박 코스튬", 16_000, ItemKind.PET, false, "산책·패션"),
        // 건강
        p("ph1", "닥터멍 멀티비타 60정", 28_000, ItemKind.PET, false, "건강"),
        p("ph2", "넥카라 (수술 후 케어)", 9_900, ItemKind.PET, false, "건강"),
        p("ph3", "펫 전용 체중계", 35_000, ItemKind.PET, false, "건강"),
        p("x1", "꽃다발", 39_000, ItemKind.ETC),
        p("x2", "콘서트 티켓", 154_000, ItemKind.ETC, true),
        p("x3", "놀이공원 자유이용권", 62_000, ItemKind.ETC),
        p("x4", "호캉스 1박", 249_000, ItemKind.ETC),
        p("x5", "제주도 왕복 항공권", 129_000, ItemKind.ETC),
    )

    fun byId(id: String): Product? = products.firstOrNull { it.id == id }

    /** 이름이 바뀐 상품의 예전 이름 → id (예전에 산 물건이 도감에서 빠지지 않게) */
    val LEGACY_NAMES: Map<String, String> = mapOf(
        "강아지 사료 2kg" to "pf1", "고양이 사료 2kg" to "pf2", "수제 개껌" to "pf3", "츄르 20개입" to "pf4", "강아지 캔 간식" to "pf5",
        "연어 습식 파우치" to "pf6", "덴탈 스틱 30개입" to "pf7", "프리미엄 사료 5kg" to "pf8", "삑삑이 공" to "pt1", "깃털 낚싯대" to "pt2",
        "터그 로프" to "pt3", "고양이 터널" to "pt5", "비행 원반" to "pt6", "노즈워크 매트" to "pt7", "푹신 방석" to "pg1",
        "원목 강아지 집" to "pg2", "캣타워" to "pg3", "스크래쳐" to "pg4", "자동 급식기" to "pg5", "숨숨집 텐트" to "pg6", "여름 쿨매트" to "pg7",
        "고양이 화장실" to "pg8", "스테인리스 밥그릇 세트" to "pg9", "빗질 브러시" to "pc1", "저자극 샴푸" to "pc2", "발톱깎이" to "pc3",
        "산책 하네스" to "pa1", "튼튼 리드줄" to "pa2", "배변봉투 롤" to "pa3", "이름표 목걸이" to "pa4", "노란 우비" to "pa5",
        "줄무늬 스웨터" to "pa6", "반려동물 종합 영양제" to "ph1",
    )

    /** 이름에 검색어가 들어간 상품 (띄어쓰기 무시) */
    fun search(query: String): List<Product> {
        val q = query.filterNot(Char::isWhitespace).lowercase()
        if (q.isEmpty()) return products
        return products.filter { q in it.name.filterNot(Char::isWhitespace).lowercase() || q in it.kind.label }
    }

    fun popular(): List<Product> = products.filter { it.popular }

    /** 장바구니 담기 (같은 상품·옵션이면 수량만 늘림, 한 줄 최대 5개) */
    fun add(cart: List<CartLine>, product: Product, option: String?, qty: Int = 1): List<CartLine> {
        val i = cart.indexOfFirst { it.product.id == product.id && it.option == option }
        return if (i < 0) {
            cart + CartLine(product, option, qty.coerceIn(1, MAX_QTY))
        } else {
            cart.mapIndexed { j, l -> if (j == i) l.copy(qty = (l.qty + qty).coerceAtMost(MAX_QTY)) else l }
        }
    }

    const val MAX_QTY = 5
}
