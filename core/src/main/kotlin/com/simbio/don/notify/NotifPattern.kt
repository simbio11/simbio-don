package com.simbio.don.notify

/**
 * 알림 글의 모양: 금액·날짜·시각·잔액·끝자리 같은 숫자를 지우고 남은 글.
 * "[신한은행] 11/25 09:00 이체 출금 300,000원 홍길동 잔액 1,200,000원" → "[신한은행] #/# #:# 이체 출금 #원 홍길동 잔액 #원".
 * 같은 모양이면 같은 종류의 알림이라 전에 고른 답(무시·이체·저축·월급…)을 다시 쓸 수 있다. 이 폰 안에서만 쓴다.
 */
object NotifPattern {
    private val NUMBER = Regex("""\d[\d,.]*""")
    private val MASK = Regex("""[#*]+(?:[-]?[#*]+)*""")
    private val SPACE = Regex("""\s+""")

    fun of(title: String, text: String): String {
        var t = "$title $text".replace('\n', ' ')
        t = NUMBER.replace(t, "#")
        t = MASK.replace(t, "#")
        return SPACE.replace(t, " ").trim().lowercase().take(200)
    }
}
