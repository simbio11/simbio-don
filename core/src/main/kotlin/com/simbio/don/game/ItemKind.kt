package com.simbio.don.game

/** 집에서 물건이 놓일 칸 */
enum class Slot(val label: String) {
    WARDROBE("옷장"),
    SHOE_RACK("신발장"),
    DESK("책상"),
    SHELF("선반"),

    /** 먹고 남은 쓰레기 — 칸 없이 바닥에 쌓이고 분리수거로 치운다 */
    TRASH("쓰레기"),
}

/** 모의로 산 물건의 종류. 저장할 때는 [name]을 쓰므로 이름을 바꾸지 않는다. */
enum class ItemKind(val label: String, val slot: Slot) {
    CLOTHES("옷", Slot.WARDROBE),
    BAG("가방·잡화", Slot.WARDROBE),
    SHOES("신발", Slot.SHOE_RACK),
    ELECTRONICS("전자기기", Slot.DESK),
    BOOK("책·문구", Slot.DESK),
    BEAUTY("화장품", Slot.SHELF),
    LIVING("생활용품", Slot.SHELF),
    TOY("취미·장난감", Slot.SHELF),
    MEAL("음식·배달", Slot.TRASH),
    DRINK("커피·음료", Slot.TRASH),
    ETC("기타", Slot.SHELF),
    ;

    val isFood: Boolean get() = slot == Slot.TRASH

    companion object {
        fun of(name: String?): ItemKind = entries.firstOrNull { it.name == name } ?: ETC

        private val KEYWORDS: List<Pair<ItemKind, List<String>>> = listOf(
            DRINK to listOf("커피", "아메리카노", "라떼", "스타벅스", "메가커피", "음료", "스무디", "아이스티", "밀크티", "버블티", "에이드"),
            MEAL to listOf(
                "치킨", "피자", "버거", "떡볶이", "배달", "족발", "보쌈", "초밥", "마라", "짜장", "국밥", "도시락", "케이크",
                "빵", "디저트", "아이스크림", "과자", "라면", "배민", "요기요", "쿠팡이츠",
            ),
            SHOES to listOf("신발", "운동화", "스니커즈", "구두", "샌들", "슬리퍼", "부츠", "나이키", "아디다스", "뉴발란스", "에어포스", "조던"),
            BAG to listOf("가방", "백팩", "지갑", "모자", "벨트", "시계", "목걸이", "반지", "귀걸이", "선글라스"),
            CLOTHES to listOf("티셔츠", "셔츠", "바지", "청바지", "니트", "후드", "자켓", "재킷", "코트", "패딩", "원피스", "스커트", "맨투맨", "가디건", "양말", "옷"),
            ELECTRONICS to listOf(
                "에어팟", "이어폰", "헤드폰", "아이폰", "갤럭시", "아이패드", "태블릿", "노트북", "맥북", "키보드", "마우스",
                "모니터", "충전기", "스피커", "카메라", "닌텐도", "플스", "스위치", "워치",
            ),
            BOOK to listOf("책", "도서", "문구", "펜", "노트", "다이어리", "만화"),
            BEAUTY to listOf("화장품", "립", "쿠션", "토너", "로션", "크림", "향수", "샴푸", "올리브영", "선크림"),
            TOY to listOf("레고", "피규어", "인형", "보드게임", "퍼즐", "굿즈", "키링"),
            LIVING to listOf("수건", "이불", "베개", "컵", "그릇", "냄비", "청소", "세제", "휴지", "조명", "무드등", "다이소"),
        )

        fun guess(name: String): ItemKind {
            val t = name.lowercase().filterNot { it.isWhitespace() }
            if (t.isEmpty()) return ETC
            return KEYWORDS.firstOrNull { (_, words) -> words.any { it in t } }?.first ?: ETC
        }
    }
}
