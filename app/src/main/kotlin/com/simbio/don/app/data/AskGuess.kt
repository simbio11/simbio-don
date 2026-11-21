package com.simbio.don.app.data

/** 애매한 알림을 무엇으로 적을지 (비서의 짐작 = 사용자가 고를 답) */
enum class AskKind(val label: String) {
    SPEND("지출"),
    FIXED("고정 지출"),
    INCOME("수입"),
    SALARY("월급"),
    TRANSFER("내 통장끼리 이체"),
    SAVINGS("저축으로 옮김"),
    CARD_BILL("카드값"),
    CASH("현금 뽑기"),
    CHARGE("페이 충전"),
    SETTLE("엔빵 정산 받은 돈"),
    REFUND("환불"),
    IGNORE("무시 (적지 않기)"),
}

/**
 * 비서의 짐작. [from]·[to]는 자산 id (이체·저축·카드값), [category]는 분류 이름, [target]은 정산이면 나눠 낸 기록 id.
 * [why]는 왜 그렇게 짐작했는지 한 줄 ("계획한 저축과 같은 금액").
 */
data class AskGuess(
    val kind: AskKind,
    val from: Long? = null,
    val to: Long? = null,
    val category: String? = null,
    val target: Long? = null,
    val why: String = "",
) {
    fun encode(): String = listOf(kind.name, from?.toString().orEmpty(), to?.toString().orEmpty(), category.orEmpty(), target?.toString().orEmpty(), why.replace("|", " "))
        .joinToString("|")

    companion object {
        /** 한 질문에 합친 두 알림(나간 쪽·들어온 쪽) 사이 표시 — 알림 글 자체의 줄바꿈과 헷갈리지 않게 */
        const val PAIR_SEP = "\n⇄ "

        fun decode(s: String): AskGuess? {
            if (s.isBlank()) return null
            val p = s.split("|")
            val kind = AskKind.entries.firstOrNull { it.name == p.getOrNull(0) } ?: return null
            return AskGuess(
                kind,
                p.getOrNull(1)?.toLongOrNull(),
                p.getOrNull(2)?.toLongOrNull(),
                p.getOrNull(3)?.takeIf { it.isNotBlank() },
                p.getOrNull(4)?.toLongOrNull(),
                p.getOrNull(5).orEmpty(),
            )
        }
    }
}
