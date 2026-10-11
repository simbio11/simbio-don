package com.simbio.don.game

/** 쇼핑 앱에서 "공유"로 받은 글 */
data class SharedProduct(val name: String, val price: Long?, val store: String?)

/**
 * 공유된 글에서 상품명·가격·쇼핑몰을 뽑는다. 링크를 열어보지 않고 글만 쓴다 (스크래핑 금지).
 * 형식이 앱마다 달라 추측이고, 사용자가 결제 화면에서 고칠 수 있다.
 */
object SharedTextParser {
    private val URL = Regex("""https?://\S+""")
    private val PRICE = Regex("""(\d{1,3}(?:,\d{3})+|\d{3,})\s*원""")
    private val BRACKET_PREFIX = Regex("""^\s*[\[【(][^\]】)]{1,15}[\]】)]\s*""")

    private val STORES = listOf(
        "coupang" to "쿠팡", "baemin" to "배달의민족", "musinsa" to "무신사", "29cm" to "29CM", "zigzag" to "지그재그",
        "a-bly" to "에이블리", "11st" to "11번가", "gmarket" to "G마켓", "auction" to "옥션", "ssg" to "SSG",
        "smartstore.naver" to "네이버", "naver" to "네이버", "kurly" to "컬리", "oliveyoung" to "올리브영",
        "aliexpress" to "알리", "temu" to "테무", "yogiyo" to "요기요", "kakao" to "카카오",
    )

    fun parse(text: String): SharedProduct {
        val url = URL.find(text)?.value
        val store = url?.lowercase()?.let { u -> STORES.firstOrNull { it.first in u }?.second }
        var body = URL.replace(text, " ")
        val price = PRICE.findAll(body).mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }.lastOrNull()
        body = PRICE.replace(body, " ")
        val name = body.lines().map { it.trim() }.firstOrNull { it.length >= 2 }
            ?.let { BRACKET_PREFIX.replace(it, "") }
            ?.replace(Regex("""\s+"""), " ")
            ?.trim('-', '|', ':', ' ')
            ?.take(40)
            .orEmpty()
        return SharedProduct(name, price, store)
    }
}
