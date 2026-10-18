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
)

/** 장바구니 한 줄 */
data class CartLine(val product: Product, val option: String?, val qty: Int) {
    val total: Long get() = product.price * qty
}

object Catalog {
    private val CLOTHES_SIZE = listOf("S", "M", "L", "XL")
    private val COLORS = listOf("블랙", "화이트", "그레이", "네이비", "베이지")
    private val SHOE_SIZE = (230..280 step 5).map { it.toString() }

    private fun p(id: String, name: String, price: Long, kind: ItemKind, popular: Boolean = false): Product = when (kind) {
        ItemKind.CLOTHES -> Product(id, name, price, kind, "사이즈", CLOTHES_SIZE, popular)
        ItemKind.SHOES -> Product(id, name, price, kind, "사이즈", SHOE_SIZE, popular)
        ItemKind.BAG, ItemKind.ELECTRONICS -> Product(id, name, price, kind, "색상", COLORS, popular)
        else -> Product(id, name, price, kind, popular = popular)
    }

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
        p("x1", "꽃다발", 39_000, ItemKind.ETC),
        p("x2", "콘서트 티켓", 154_000, ItemKind.ETC, true),
        p("x3", "놀이공원 자유이용권", 62_000, ItemKind.ETC),
        p("x4", "호캉스 1박", 249_000, ItemKind.ETC),
        p("x5", "제주도 왕복 항공권", 129_000, ItemKind.ETC),
    )

    fun byId(id: String): Product? = products.firstOrNull { it.id == id }

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
