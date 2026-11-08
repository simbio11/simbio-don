package com.simbio.don.notify

/**
 * 알림 글에서 "어느 카드·계좌인지" 알려 주는 끝자리를 찾는다. 한 번 어느 자산인지 알려 주면 그 끝자리를 기억해,
 * 같은 앱에서 카드가 여러 장이어도 다음부터는 끝자리로 저절로 자산을 정한다.
 *
 * 돌려주는 값은 종류 글자 + 숫자: 카드 `c1234`, 계좌 `a88507`.
 */
object AccountHints {
    // "신한카드(1234)승인", "KB국민카드[1234]"
    private val CARD_BRACKET = Regex("카드\\s*[(\\[]\\s*(\\d{4})\\s*[)\\]]")

    // "KB국민카드 1234 승인", "삼성카드 1234 결제"
    private val CARD_SPACED = Regex("카드\\s+(\\d{4})\\s+(?:승인|결제|사용|취소|출금)")

    // "[1234] 승인"처럼 카드사 이름 없이 끝자리만
    private val TAIL_APPROVAL = Regex("[(\\[]\\s*(\\d{4})\\s*[)\\]]\\s*(?:승인|결제|사용)")

    // "110-***-123456", "240-******-88507": 가린 계좌번호의 끝 (3~6자리)
    private val MASKED = Regex("\\*{2,}[-*]*(\\d{3,6})(?!\\d)")

    /** 카드 끝자리가 있으면 `c…`, 가린 계좌번호가 있으면 `a…`, 없으면 null */
    fun tail(text: String): String? {
        CARD_BRACKET.find(text)?.let { return "c" + it.groupValues[1] }
        CARD_SPACED.find(text)?.let { return "c" + it.groupValues[1] }
        TAIL_APPROVAL.find(text)?.let { return "c" + it.groupValues[1] }
        MASKED.find(text)?.let { return "a" + it.groupValues[1] }
        return null
    }

    /** 자산에 저장한 끝자리 글 ("c1234,a88507") → 목록 */
    fun parse(stored: String): List<String> = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun encode(tails: Collection<String>): String = tails.distinct().joinToString(",")

    /** 사람이 읽는 말: `c1234` → "카드 끝 1234", `a88507` → "계좌 끝 88507" */
    fun label(tail: String): String = (if (tail.startsWith("c")) "카드 끝 " else "계좌 끝 ") + tail.drop(1)
}
