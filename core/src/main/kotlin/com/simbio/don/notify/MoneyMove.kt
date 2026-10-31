package com.simbio.don.notify

/**
 * 결제·입금처럼 보이지만 실제로는 **내 돈끼리 옮긴 것**(지출도 수입도 아님)이거나 **돈이 안 움직인 알림**.
 * 이것을 지출로 세면 같은 돈이 두 번 나간다 (카드로 쓸 때 한 번 + 카드값 낼 때 또 한 번).
 */
enum class MoveKind {
    /** 카드값 내기: 통장 → 카드 (카드로 쓴 건 이미 지출로 셌다) */
    CARD_BILL,

    /** 현금 뽑기: 통장 → 현금 */
    CASH,

    /** 페이·머니 충전: 통장 → 페이 (충전한 돈으로 결제할 때 지출로 센다) */
    CHARGE,

    /** 적금·청약·예금 넣기: 통장 → 모으는 통장 (월급에서 먼저 뗀 저축이라 생활비가 아니다) */
    SAVINGS,

    /** 적금·예금 만기·해지: 모으는 통장 → 통장 (수입이 아니다) */
    SAVINGS_OUT,
}

object MoneyMoves {
    private val CARD_BILL = listOf("카드대금", "카드 대금", "결제대금", "결제 대금", "카드값", "카드결제대금", "이용대금 출금", "청구대금")
    private val CASH = listOf("ATM", "현금인출", "현금 인출", "현금출금", "현금 출금", "CD출금", "CD 출금")
    private val SAVINGS = listOf("적금", "청약", "정기예금", "자유예금", "펀드 매수", "펀드매수")
    private val SAVINGS_OUT = listOf("만기", "해지", "중도해지")
    private val PAY_WORDS = Regex("머니|페이|포인트|충전금|선불|캐시")
    private val NOT_PAY = Regex("교통|티머니|하이패스|전기차|EV|주유")

    /** 돈이 안 움직인 알림: 결제 예정·명세서 안내, 잔액 부족으로 실패, 거절 */
    private val NOT_MONEY = listOf(
        "결제예정", "결제 예정", "청구예정", "청구 예정", "출금예정", "출금 예정", "납부예정", "납부 예정", "이체예정", "이체 예정",
        "명세서", "잔액부족", "잔액 부족", "출금실패", "출금 실패", "이체실패", "이체 실패", "결제실패", "결제 실패", "미출금", "승인거절", "승인 거절",
    )

    /** 카드사 이름 (통장에서 "출금 · 신한카드"처럼 나가면 카드값) */
    private val CARD_COMPANY = Regex("(신한|삼성|현대|KB국민|국민|롯데|하나|우리|BC|비씨|NH농협|농협|씨티|IBK기업|기업|카카오뱅크|토스뱅크)\\s?카드")

    /** 체크카드로 가게에서 쓴 것 (카드값이 아님) */
    private val CARD_USE = listOf("체크", "승인", "사용", "결제")

    fun isNotMoney(text: String): Boolean = NOT_MONEY.any { it in text }

    fun isCardCompany(label: String): Boolean = CARD_COMPANY.containsMatchIn(label.trim())

    /** 통장 알림이 카드사로 돈이 나갔다고 할 때 (예: "출금 50,000원 신한카드") — 체크카드 결제는 빼고 */
    fun isCardBillFromBank(text: String): Boolean =
        CARD_COMPANY.containsMatchIn(text) && listOf("출금", "자동이체").any { it in text } && CARD_USE.none { it in text }

    /** [text]는 알림 제목+본문. 결제·입금 알림일 때만 본다 */
    fun kind(text: String, action: RuleAction): MoveKind? {
        if (action != RuleAction.SPEND && action != RuleAction.INCOME) return null
        // 이자는 수입
        if ("이자" in text) return null
        return when {
            CARD_BILL.any { it in text } -> MoveKind.CARD_BILL
            CASH.any { it in text } && action == RuleAction.SPEND -> MoveKind.CASH
            SAVINGS.any { it in text } -> if (SAVINGS_OUT.any { it in text }) MoveKind.SAVINGS_OUT else MoveKind.SAVINGS
            "충전" in text && PAY_WORDS.containsMatchIn(text) && !NOT_PAY.containsMatchIn(text) -> MoveKind.CHARGE
            else -> null
        }
    }
}
