package com.simbio.don.budget

/** 지출 카테고리. 저장할 때는 [name]을 쓰므로 이름을 바꾸지 않는다. */
enum class Category(val label: String, val emoji: String) {
    FOOD("식비", "🍚"),
    CAFE("카페·간식", "☕"),
    DELIVERY("배달", "🛵"),
    TRANSPORT("교통", "🚌"),
    SHOPPING("쇼핑", "🛍️"),
    LIVING("생활·마트", "🧺"),
    HEALTH("의료·건강", "💊"),
    CULTURE("문화·여가", "🎬"),
    EVENT("경조사·선물", "🎁"),
    EDUCATION("교육", "📚"),
    ETC("기타", "📌"),
    ;

    companion object {
        fun of(name: String?): Category = entries.firstOrNull { it.name == name } ?: ETC
    }
}

/**
 * 가게 이름·알림 문구로 카테고리를 추측한다.
 *
 * 사용자가 한 번 고친 가게는 [learned]에 남아 다음부터 그대로 쓴다 (기본 키워드보다 먼저).
 */
object CategoryGuesser {

    /** 앞에 있는 카테고리가 먼저. 예: "배민 카페"는 배달 */
    private val KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.DELIVERY to listOf("배달의민족", "배민", "쿠팡이츠", "요기요", "땡겨요", "배달"),
        Category.CAFE to listOf(
            "스타벅스", "투썸", "이디야", "메가커피", "메가mgc", "빽다방", "컴포즈", "할리스", "폴바셋", "커피", "카페",
            "베이커리", "파리바게뜨", "뚜레쥬르", "던킨", "배스킨", "설빙", "디저트",
        ),
        Category.TRANSPORT to listOf(
            "택시", "카카오t", "티머니", "버스", "지하철", "코레일", "ktx", "srt", "고속버스", "주유", "주차", "하이패스",
            "타다", "쏘카", "그린카", "따릉이",
        ),
        Category.LIVING to listOf(
            "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스", "gs25", "cu", "세븐일레븐", "이마트24", "미니스톱",
            "편의점", "다이소", "올리브영", "마트", "슈퍼", "세탁", "관리비",
        ),
        Category.HEALTH to listOf("병원", "의원", "약국", "치과", "한의원", "안과", "피부과", "헬스", "필라테스", "요가"),
        Category.CULTURE to listOf(
            "cgv", "메가박스", "롯데시네마", "넷플릭스", "유튜브", "티빙", "웨이브", "디즈니", "왓챠", "멜론", "지니",
            "스포티파이", "스팀", "플레이스토어", "노래방", "pc방", "피시방", "볼링", "공연", "티켓", "인터파크", "yes24",
            "교보문고", "알라딘",
        ),
        Category.SHOPPING to listOf(
            "쿠팡", "무신사", "29cm", "지그재그", "에이블리", "11번가", "g마켓", "지마켓", "옥션", "ssg", "위메프", "티몬",
            "알리", "테무", "네이버페이", "스마트스토어", "백화점", "아울렛", "유니클로", "자라", "h&m",
        ),
        Category.EVENT to listOf("축의", "조의", "부조", "선물", "꽃집", "플라워", "카카오톡 선물"),
        Category.EDUCATION to listOf("학원", "강의", "클래스101", "인프런", "교재", "토익", "시험"),
        Category.FOOD to listOf(
            "식당", "김밥", "분식", "치킨", "피자", "버거", "맥도날드", "롯데리아", "버거킹", "맘스터치", "kfc", "서브웨이",
            "국밥", "냉면", "고기", "삼겹", "족발", "보쌈", "초밥", "스시", "라멘", "우동", "짜장", "중국집", "마라", "떡볶이",
            "한식", "양식", "일식", "푸드", "식육", "반찬", "도시락",
        ),
    )

    /** 같은 가게로 볼 수 있게 이름을 정리한다 (공백·대소문자 무시). */
    fun key(label: String): String = label.lowercase().filterNot { it.isWhitespace() }.take(40)

    fun guess(text: String, learned: Map<String, Category> = emptyMap()): Category {
        learned[key(text)]?.let { return it }
        val t = key(text)
        if (t.isEmpty()) return Category.ETC
        return KEYWORDS.firstOrNull { (_, words) -> words.any { key(it) in t } }?.first ?: Category.ETC
    }
}
