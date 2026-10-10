package com.simbio.don.notify

import java.time.LocalDateTime

/** 알림 접근 권한으로 받은 알림 한 건. 고르지 않은 앱의 알림은 이 객체로 만들지도 않는다. */
data class RawNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: LocalDateTime,
)

enum class RuleAction {
    /** 광고·혜택 등 — 버림. 가장 먼저 검사한다 */
    IGNORE,

    /** 취소·환불 */
    REFUND,

    /** 입금 — 월급 금액 제안에만 쓴다 */
    INCOME,

    /** 결제·승인 */
    SPEND,
}

/** [keywords] 중 하나라도 알림 제목·본문에 들어 있으면 [action]. */
data class KeywordRule(val keywords: List<String>, val action: RuleAction)

/** 알림을 읽을 앱 하나와 그 앱의 키워드 규칙. */
data class AppRule(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val keywordRules: List<KeywordRule> = DEFAULT_KEYWORD_RULES,
) {
    companion object {
        val DEFAULT_KEYWORD_RULES = listOf(
            KeywordRule(listOf("광고", "혜택", "이벤트", "쿠폰", "인증번호"), RuleAction.IGNORE),
            KeywordRule(listOf("취소", "환불"), RuleAction.REFUND),
            KeywordRule(listOf("입금"), RuleAction.INCOME),
            KeywordRule(listOf("결제", "승인", "사용", "출금"), RuleAction.SPEND),
        )
    }
}

/**
 * 기본으로 켜 둘 결제 앱. 폰에 설치된 앱만 목록에 보여주고, 나머지 앱은 사용자가 직접 고른다.
 * 패키지 이름은 실기기에서 확인 후 고친다.
 */
object DefaultApps {
    val presets = listOf(
        AppRule("com.samsung.android.spay", "삼성페이(삼성 월렛)"),
        AppRule("viva.republica.toss", "토스"),
        AppRule("com.kakaopay.app", "카카오페이"),
        AppRule("com.kakao.bank", "카카오뱅크"),
        AppRule("com.shcard.smartpay", "신한 SOL페이"),
        AppRule("com.kbcard.cxh.appcard", "KB Pay"),
        AppRule("com.hyundaicard.appcard", "현대카드"),
        AppRule("kr.co.samsungcard.mpocket", "삼성카드"),
    )

    /** 문자 앱은 스미싱이 섞일 수 있어 켤 때 경고한다. */
    val messagingPackages = setOf("com.samsung.android.messaging", "com.google.android.apps.messaging")
}
