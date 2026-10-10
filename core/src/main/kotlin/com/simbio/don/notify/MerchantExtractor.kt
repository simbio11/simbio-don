package com.simbio.don.notify

/**
 * 결제 알림 본문에서 가게 이름을 뽑는다. 형식이 앱마다 달라 추측일 뿐이고, 사용자가 언제든 고칠 수 있다.
 *
 * 금액·날짜·시각·카드번호·결제 관련 단어를 지우고 남은 말 중에서,
 * 시각 뒤에 나오는 첫 단어(카드 문자 형식) 또는 첫 단어를 고른다.
 */
object MerchantExtractor {
    private val TIME = Regex("""\d{1,2}:\d{2}""")
    private val NOISE = listOf(
        Regex("""\[[^\]]*]"""), // [Web발신] 등
        Regex("""\(\s*\d{2,4}\s*\)"""), // (1234)
        Regex("""\d{2,4}\*+\d*|\*+\d{2,4}"""), // 1234** / **12
        Regex("""\d[\d,]*\s*원"""), // 금액
        Regex("""\d{1,4}[/.-]\d{1,2}([/.-]\d{1,2})?"""), // 날짜
        Regex("""\d{1,2}:\d{2}(:\d{2})?"""), // 시각
        Regex("""[가-힣]\*[가-힣]?님?"""), // 홍*동 / 홍*님
    )
    private val STOP_WORDS = setOf(
        "승인", "결제", "일시불", "할부", "누적", "잔액", "체크", "신용", "카드", "완료", "사용", "출금", "입금", "취소",
        "환불", "님", "개월", "되었습니다", "했어요", "결제완료", "승인완료", "해외", "국내", "건", "금액", "이용", "가맹점",
    )

    fun extract(title: String, text: String, appName: String = ""): String {
        val afterTime = TIME.find(text)?.let { text.substring(it.range.last + 1) }
        val candidates = listOfNotNull(afterTime, text, title)
        for (c in candidates) {
            words(c, appName).firstOrNull()?.let { return it }
        }
        return title.ifBlank { appName }.take(40)
    }

    private fun words(s: String, appName: String): List<String> {
        var t = s
        NOISE.forEach { t = it.replace(t, " ") }
        return t.split(Regex("""[\s,|/()\[\]<>:·]+"""))
            .map { it.trim('.', '-', '_', '!', '~') }
            // 카드사·은행·결제 앱 이름은 가게가 아니다
            .filterNot { it.endsWith("카드") || it.endsWith("은행") || it.endsWith("뱅크") || it.endsWith("페이") }
            .map { w -> STOP_WORDS.fold(w) { acc, sw -> if (acc.endsWith(sw) && acc.length > sw.length) acc.removeSuffix(sw) else acc } }
            .filter { it.length >= 2 && it !in STOP_WORDS && it.any { ch -> !ch.isDigit() } }
            .filter { appName.isBlank() || it != appName }
    }
}
