package com.simbio.don.notify

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 0.30: 은행·카드사·페이마다 흔히 오는 말투로 — 금액·가게·종류(결제/입금/취소/옮김)·카드사·끝자리를 맞게 읽는지.
 * 문자 앱처럼 어느 회사인지 모르는 앱에서 받았다고 보고 글만으로 판단한다.
 */
class RealTextsTest {
    private val rule = AppRule("sms.app", "문자")
    private val t = LocalDateTime.of(2026, 11, 9, 12, 0)

    private data class Want(
        val action: RuleAction, val amount: Long, val label: String? = null,
        val card: String? = null, val tail: String? = null, val move: MoveKind? = null, val balance: Long? = null,
    )

    private fun check(title: String, text: String, w: Want) {
        val body = "$title $text"
        val r = NotificationParser.parse(RawNotification("sms.app", title, text, t), listOf(rule))
        val p = assertIs<ParseResult.Payment>(r, body).payment
        assertEquals(w.action, p.action, body)
        assertEquals(w.amount, p.amount, body)
        w.label?.let { assertEquals(it, p.label, body) }
        assertEquals(w.card, CreditCards.company("sms.app", body), body)
        w.tail?.let { assertEquals(it, AccountHints.tail(body), body) }
        assertEquals(w.move, MoneyMoves.kind(body, p.action), body)
        w.balance?.let { assertEquals(it, p.balanceAfter, body) }
        assertTrue(!MoneyMoves.isNotMoney(body), body)
    }

    @Test
    fun `카드사 승인 문자 — 카드사·끝자리·가게`() {
        check("", "[Web발신]\nKB국민카드1234승인\n홍*동\n15,000원 일시불\n11/09 12:30\n이디야커피\n누적123,400원", Want(RuleAction.SPEND, 15_000, "이디야커피", "KB국민카드", "c1234"))
        check("신한카드(1234)승인", "홍*동 45,000원(일시불)11/09 19:20 교촌치킨 누적1,234,000원", Want(RuleAction.SPEND, 45_000, "교촌치킨", "신한카드", "c1234"))
        check("", "[Web발신]\n삼성카드4567승인 홍*동\n32,000원 일시불\n11/09 18:11 쿠팡\n누적 543,210원", Want(RuleAction.SPEND, 32_000, "쿠팡", "삼성카드", "c4567"))
        check("현대카드 승인", "홍*동\n12,800원 일시불\n11/09 08:44\nGS25 역삼점\n누적 300,000원", Want(RuleAction.SPEND, 12_800, "GS25", "현대카드"))
        check("", "[Web발신]\n롯데카드 2*1*승인 홍*동 8,900원 일시불 11/09 14:03 올리브영", Want(RuleAction.SPEND, 8_900, "올리브영", "롯데카드", "c2*1*"))
        // "카드" 글자 없이 오는 카드사
        check("", "하나(1*2*)승인 홍*동 9,000원 일시불 11/09 13:10 맥도날드", Want(RuleAction.SPEND, 9_000, "맥도날드", "하나카드", "c1*2*"))
        // 카드사 이름도 없지만 일시불이면 신용카드
        check("", "[일시불.승인(1234)]11/09 12:00\n25,000원 / 홍*동\n교보문고", Want(RuleAction.SPEND, 25_000, "교보문고", CreditCards.UNKNOWN, "c1234"))
        // 할부
        check("", "삼성카드4567승인 홍*동 900,000원 03개월 11/09 15:00 하이마트", Want(RuleAction.SPEND, 900_000, "하이마트", "삼성카드", "c4567"))
        // 체크카드는 통장에서 바로 (카드사가 아님)
        check("", "NH카드1*2*승인 홍*동 6,000원 체크 11/09 10:00 파리바게뜨", Want(RuleAction.SPEND, 6_000, "파리바게뜨", null))
        check("토스뱅크 체크카드", "4,500원 결제 | 스타벅스 강남점", Want(RuleAction.SPEND, 4_500, "스타벅스", null))
        // 취소
        check("", "[Web발신] 신한카드(1234)승인취소 홍*동 45,000원(일시불) 11/09 교촌치킨", Want(RuleAction.REFUND, 45_000, "교촌치킨", "신한카드", "c1234"))
        // 해외 (달러만): 어림 환율로 원화
        val usd = assertIs<ParseResult.Payment>(NotificationParser.parse(RawNotification("sms.app", "", "신한카드(1234)승인 홍*동 USD 12.50 11/09 AMAZON", t), listOf(rule))).payment
        assertEquals("AMAZON", usd.label)
        assertEquals("USD", usd.foreign?.currency)
        assertTrue(usd.rateGuess)
    }

    @Test
    fun `은행 입출금 — 입금·출금·잔액`() {
        check("", "[KB]11/09 09:00\n123456**789\n(주)회사\n입금\n3,200,000\n잔액3,500,000", Want(RuleAction.INCOME, 3_200_000, "회사", balance = 3_500_000, tail = "a789"))
        check("[신한은행]", "11/09 09:00 입금 3,200,000원 (주)회사 잔액 3,500,000원", Want(RuleAction.INCOME, 3_200_000, "회사", balance = 3_500_000))
        check("입금", "입출금통장(1234) 입금 50,000원 김철수", Want(RuleAction.INCOME, 50_000, "김철수"))
        check("", "[우리] 11/09 13:00 출금 50,000원 관리비 잔액 1,000,000원", Want(RuleAction.SPEND, 50_000, "관리비", balance = 1_000_000))
        check("", "[하나]11/09 12:00 123-***-456 출금 30,000원 잔액 970,000원 홍길동", Want(RuleAction.SPEND, 30_000, tail = "a456", balance = 970_000))
        check("", "[신한은행] 이자 입금 1,234원 잔액 1,001,234원", Want(RuleAction.INCOME, 1_234, balance = 1_001_234))
    }

    @Test
    fun `사람끼리 주고받은 돈 — 받았으면 입금, 보냈으면 지출`() {
        check("", "김철수님이 50,000원을 보냈어요", Want(RuleAction.INCOME, 50_000, "김철수"))
        check("토스", "이영희님이 12,000원 보냈어요", Want(RuleAction.INCOME, 12_000))
        check("", "김철수님에게 30,000원을 보냈어요", Want(RuleAction.SPEND, 30_000, "김철수"))
        // 키워드가 있으면 그게 먼저 (사용자가 고친 규칙을 지킨다)
        check("", "김철수님에게 30,000원 송금", Want(RuleAction.SPEND, 30_000))
    }

    @Test
    fun `페이 결제`() {
        check("결제", "GS25 강남점에서 3,200원 결제", Want(RuleAction.SPEND, 3_200, "GS25"))
        check("네이버페이", "결제 완료 29,800원 스마트스토어 OO상점", Want(RuleAction.SPEND, 29_800))
        check("카카오페이", "12,000원 결제 완료 카카오T", Want(RuleAction.SPEND, 12_000, "카카오T"))
        check("", "배달의민족 23,500원 결제 완료", Want(RuleAction.SPEND, 23_500, "배달의민족"))
    }

    @Test
    fun `내 돈끼리 옮긴 것 — 지출이 아니다`() {
        check("", "[신한은행] ATM 현금출금 100,000원 잔액 900,000원", Want(RuleAction.SPEND, 100_000, move = MoveKind.CASH, balance = 900_000))
        check("", "[신한은행] 자동이체 출금 300,000원 적금 잔액 600,000원", Want(RuleAction.SPEND, 300_000, move = MoveKind.SAVINGS))
        check("", "[신한은행] 카드대금 출금 450,000원 잔액 150,000원", Want(RuleAction.SPEND, 450_000, move = MoveKind.CARD_BILL))
        // 통장에서 카드사로 나간 것은 카드값 (신용카드 결제가 아님)
        val bill = "[신한은행] 출금 450,000원 신한카드 잔액 150,000원"
        assertTrue(MoneyMoves.isCardBillFromBank(bill))
        assertNull(CreditCards.company("sms.app", bill))
    }

    @Test
    fun `돈이 안 움직인 알림·광고는 버린다`() {
        assertTrue(MoneyMoves.isNotMoney("11월 결제예정금액 345,000원"))
        assertTrue(MoneyMoves.isNotMoney("잔액부족으로 출금되지 않았습니다 50,000원"))
        assertEquals(ParseResult.Ignored, NotificationParser.parse(RawNotification("sms.app", "", "[광고] 카드 신규 혜택 10,000원", t), listOf(rule)))
        // 은행 자동이체는 일시불·할부 낱말이 있어도 신용카드로 보지 않는다
        assertNull(CreditCards.company("sms.app", "[우리] 자동이체 출금 50,000원 할부금 잔액 100,000원"))
    }
}
