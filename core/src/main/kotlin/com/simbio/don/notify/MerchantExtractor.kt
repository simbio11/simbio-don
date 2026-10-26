package com.simbio.don.notify

/**
 * 사용자가 고쳐 준 가게 이름 자리. 그 앱의 다음 알림은 여기서 먼저 찾는다.
 * [line]: 알림 글(제목 + 본문)을 줄로 나눈 것 중 몇 번째 (빈 줄 빼고, 0부터).
 * [word]: 그 줄에서 금액·날짜 같은 것을 지우고 남은 낱말 중 몇 번째 (null이면 그 줄 전체).
 */
data class MerchantSpot(val line: Int, val word: Int?) {
    fun encode(): String = "$line:${word ?: ""}"

    companion object {
        fun decode(s: String?): MerchantSpot? {
            val parts = s?.split(":") ?: return null
            if (parts.size != 2) return null
            val line = parts[0].toIntOrNull() ?: return null
            return MerchantSpot(line, parts[1].toIntOrNull())
        }
    }
}

/**
 * 결제 알림에서 가게 이름을 뽑는다. 형식이 앱마다 달라 추측일 뿐이고, 사용자가 언제든 고칠 수 있다 —
 * 고치면 그 앱 알림의 가게 이름 자리([MerchantSpot])를 배운다.
 *
 * 찾는 차례: 배운 자리 → "가맹점: ○○" 같은 이름표 → 시각 뒤 첫 낱말(카드 문자 형식) → 본문 → 제목.
 * 금액·날짜·시각·카드번호·"결제/승인/완료" 같은 말·카드사·은행·페이 이름은 가게가 아니다.
 * 못 찾으면 빈 문자열 (부르는 쪽이 "○○카드 결제"처럼 앱 이름으로 채운다).
 */
object MerchantExtractor {
    private val TIME = Regex("""\d{1,2}:\d{2}""")
    private val NOISE = listOf(
        // [Web발신]·[신한체크승인]·[KB국민카드] 같은 보낸 곳 표시 ([스타벅스] 같은 건 남김)
        Regex("""\[[^\]]*(발신|카드|은행|뱅크|승인|체크|광고|알림|페이|Pay|pay|증권|농협|국민|신한|우리|하나|현대|삼성|롯데|BC|비씨)[^\]]*]"""),
        Regex("""\(\s*\d{2,4}\s*\)"""), // (1234)
        Regex("""\d{2,4}\*+\d*|\*+\d{2,4}"""), // 1234** / **12
        Regex("""[₩￦]\s*\d[\d,]*"""), // ₩5,000
        Regex("""\d[\d,]*\s*(원|KRW|krw|USD|usd|달러)"""), // 금액
        Regex("""(KRW|USD)\s*\d[\d,.]*"""),
        Regex("""\d{1,3}(,\d{3})+"""), // 단위 없는 금액
        Regex("""\d{1,4}[/.-]\d{1,2}([/.-]\d{1,2})?"""), // 날짜
        Regex("""\d{1,2}:\d{2}(:\d{2})?"""), // 시각
        Regex("""\d{1,2}월\s*\d{1,2}일"""), // 10월 26일
        Regex("""[가-힣]\*[가-힣]?님?"""), // 홍*동 / 홍*님
    )

    /** 이 말 뒤가 가게 이름 ("가맹점: 스타벅스", "사용처 GS25") */
    private val NAME_TAG = Regex("""(?:가맹점\s*명?|사용처|결제처|이용처|상호\s*명?)\s*[:：]?\s*([^\n|,·]+)""")

    /** 낱말에서 떼어 낼 끝말 (긴 것부터) */
    private val SUFFIXES = listOf(
        "되었습니다", "되었어요", "하였습니다", "하셨습니다", "했습니다", "했어요", "됐어요", "됩니다",
        "결제완료", "승인완료", "결제", "승인", "완료", "사용", "이용", "에서의", "에서", "님",
    )

    /** 이것만 남으면 가게가 아니다 */
    private val GENERIC = setOf(
        "승인", "결제", "일시불", "할부", "누적", "잔액", "체크", "신용", "카드", "완료", "사용", "출금", "입금", "취소",
        "환불", "님", "개월", "되었습니다", "했어요", "결제완료", "승인완료", "해외", "국내", "건", "금액", "이용", "가맹점",
        "알림", "안내", "내역", "고객", "회원", "결제알림", "승인알림", "이용알림", "사용알림", "결제내역", "승인내역", "이용내역", "고객님", "회원님",
        "원", "적립", "포인트", "혜택", "결제가", "승인이", "결제는", "결제를", "완료되었습니다", "사용처", "결제처", "이용처",
        "상호", "상호명", "가맹점명", "일시", "금일", "오늘", "방금", "총", "합계", "결제금액", "승인금액", "이용금액", "사용금액",
        "KRW", "USD", "web발신", "Web발신", "본인", "확인", "바로가기", "자세히", "보기",
    )

    private fun isGeneric(w: String): Boolean {
        if (w in GENERIC || w.lowercase() in GENERIC) return true
        // "결제되었어요", "승인완료됨" 처럼 일반 말끼리 붙은 것
        var t = w
        var changed = true
        while (changed && t.isNotEmpty()) {
            changed = false
            for (s in SUFFIXES + listOf("됨", "가", "이", "을", "를", "은", "는")) {
                if (t.endsWith(s) && t.length > s.length) {
                    t = t.removeSuffix(s); changed = true
                }
                if (t.startsWith(s) && t.length > s.length && s.length >= 2) {
                    t = t.removePrefix(s); changed = true
                }
            }
        }
        return t.isEmpty() || t in GENERIC
    }

    fun extract(title: String, text: String, appName: String = "", spot: MerchantSpot? = null): String {
        spot?.let { at(title, text, appName, it) }?.let { return it }
        val all = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        NAME_TAG.find(all)?.let { m -> words(m.groupValues[1], appName).takeIf { it.isNotEmpty() }?.let { return it.take(2).joinToString(" ").take(40) } }
        val afterTime = TIME.find(text)?.let { text.substring(it.range.last + 1) }
        val candidates = listOfNotNull(afterTime, text, title)
        for (c in candidates) {
            words(c, appName).firstOrNull()?.let { return it.take(40) }
        }
        return ""
    }

    /** 알림 글을 줄로 (제목이 첫 줄, 빈 줄 빼고) */
    fun lines(title: String, text: String): List<String> =
        (listOf(title) + text.split('\n')).map { it.trim() }.filter { it.isNotEmpty() }

    /** [line]에서 금액·날짜 같은 것을 지운 낱말 (일반 말도 그대로, 자리 세기용) */
    private fun tokens(line: String): List<String> {
        var t = line
        NOISE.forEach { t = it.replace(t, " ") }
        return t.split(Regex("""[\s,|/()\[\]<>:：·]+""")).map { it.trim('.', '-', '_', '!', '~') }.filter { it.isNotEmpty() }
    }

    private fun clean(w: String): String {
        var t = w
        for (s in listOf("에서의", "에서")) if (t.endsWith(s) && t.length > s.length + 1) t = t.removeSuffix(s)
        return t
    }

    /** 배운 자리에서 꺼내기 (그 자리가 일반 말이면 null) */
    private fun at(title: String, text: String, appName: String, spot: MerchantSpot): String? {
        val line = lines(title, text).getOrNull(spot.line) ?: return null
        val value = if (spot.word == null) {
            tokens(line).dropWhile { isGeneric(it) }.dropLastWhile { isGeneric(it) }.joinToString(" ")
        } else {
            tokens(line).getOrNull(spot.word)?.let(::clean)
        } ?: return null
        return value.takeIf { it.length >= 2 && !isGeneric(it) && it != appName && it.any { ch -> !ch.isDigit() } }?.take(40)
    }

    /**
     * 사용자가 고친 이름 [name]이 알림 글 어디에 있었는지. 줄 전체가 이름이면 그 줄, 아니면 그 줄의 몇 번째 낱말.
     * 알림 글에 없는 이름이면 null (그 앱 알림에 가게 이름이 안 오는 것).
     */
    fun learn(title: String, text: String, name: String): MerchantSpot? {
        val key = com.simbio.don.budget.CategoryGuesser.key(name)
        if (key.length < 2) return null
        val ls = lines(title, text)
        ls.forEachIndexed { i, line ->
            val ts = tokens(line)
            val whole = ts.dropWhile { isGeneric(it) }.dropLastWhile { isGeneric(it) }.joinToString(" ")
            if (ts.size > 1 && com.simbio.don.budget.CategoryGuesser.key(whole) == key) return MerchantSpot(i, null)
            ts.indexOfFirst { com.simbio.don.budget.CategoryGuesser.key(clean(it)) == key }.takeIf { it >= 0 }?.let { return MerchantSpot(i, it) }
        }
        // "스타벅스강남점"에서 "스타벅스"만 남긴 것처럼 낱말 안에 든 이름
        ls.forEachIndexed { i, line ->
            tokens(line).indexOfFirst { com.simbio.don.budget.CategoryGuesser.key(it).startsWith(key) }.takeIf { it >= 0 }?.let { return MerchantSpot(i, it) }
        }
        return null
    }

    private fun words(s: String, appName: String): List<String> =
        tokens(s)
            // 카드사·은행·결제 앱 이름은 가게가 아니다
            .filterNot { it.endsWith("카드") || it.endsWith("은행") || it.endsWith("뱅크") || it.endsWith("페이") || it.endsWith("Pay") || it.endsWith("pay") }
            .map { w -> SUFFIXES.fold(w) { acc, sw -> if (acc.endsWith(sw) && acc.length > sw.length) acc.removeSuffix(sw) else acc } }
            .map(::clean)
            .filter { it.length >= 2 && !isGeneric(it) && it.any { ch -> !ch.isDigit() } }
            .filter { appName.isBlank() || it != appName }
}
