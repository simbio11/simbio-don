package com.simbio.don.notify

/** 은행·카드사·페이 종류 */
enum class InstitutionKind { BANK, CARD, PAY }

/**
 * 통장·카드를 계좌번호 대신 이름으로 고르는 목록. 고르면 그 앱(설치돼 있으면)의 알림을 이 자산과 잇는다.
 * [packages]는 알려진 앱 패키지, [labelWords]는 폰에 깔린 앱 이름에서 찾을 말 (패키지가 바뀌어도 찾게).
 */
data class Institution(val name: String, val kind: InstitutionKind, val packages: List<String>, val labelWords: List<String>)

object Institutions {
    val all = listOf(
        Institution("카카오뱅크", InstitutionKind.BANK, listOf("com.kakao.bank"), listOf("카카오뱅크")),
        Institution("토스뱅크", InstitutionKind.BANK, listOf("viva.republica.toss"), listOf("토스")),
        Institution("신한은행", InstitutionKind.BANK, listOf("com.shinhan.sbanking"), listOf("신한 SOL", "신한SOL", "SOL뱅크")),
        Institution("KB국민은행", InstitutionKind.BANK, listOf("com.kbstar.kbbank"), listOf("KB스타뱅킹", "스타뱅킹")),
        Institution("우리은행", InstitutionKind.BANK, listOf("com.wooribank.smart.npib"), listOf("우리WON", "우리은행")),
        Institution("하나은행", InstitutionKind.BANK, listOf("com.kebhana.hanapush"), listOf("하나원큐", "하나은행")),
        Institution("NH농협", InstitutionKind.BANK, listOf("nh.smart.banking", "com.nonghyup.nhallonebank"), listOf("NH스마트뱅킹", "올원뱅크", "농협")),
        Institution("IBK기업은행", InstitutionKind.BANK, listOf("com.ibk.android.ionebank"), listOf("i-ONE", "기업은행")),
        Institution("케이뱅크", InstitutionKind.BANK, listOf("com.kbankwith.smartbank"), listOf("케이뱅크")),
        Institution("새마을금고", InstitutionKind.BANK, listOf("com.smg.spbs"), listOf("새마을", "MG더뱅킹")),
        Institution("우체국", InstitutionKind.BANK, listOf("com.epost.psf.sdsi"), listOf("우체국")),
        Institution("SC제일은행", InstitutionKind.BANK, listOf("com.scbank.ma30"), listOf("SC제일", "제일은행")),
        Institution("신한카드", InstitutionKind.CARD, listOf("com.shcard.smartpay"), listOf("신한 SOL페이", "SOL페이", "신한카드")),
        Institution("KB국민카드", InstitutionKind.CARD, listOf("com.kbcard.cxh.appcard"), listOf("KB Pay", "KB국민카드")),
        Institution("삼성카드", InstitutionKind.CARD, listOf("kr.co.samsungcard.mpocket"), listOf("삼성카드")),
        Institution("현대카드", InstitutionKind.CARD, listOf("com.hyundaicard.appcard"), listOf("현대카드")),
        Institution("롯데카드", InstitutionKind.CARD, listOf("com.lcacApp"), listOf("롯데카드", "디지로카")),
        Institution("하나카드", InstitutionKind.CARD, listOf("com.hanaskcard.paycla"), listOf("하나Pay", "하나카드")),
        Institution("우리카드", InstitutionKind.CARD, listOf("com.wooricard.smartapp"), listOf("우리카드")),
        Institution("BC카드", InstitutionKind.CARD, listOf("com.bccard.bcpaybooc"), listOf("페이북", "BC카드")),
        Institution("NH농협카드", InstitutionKind.CARD, listOf("nh.smart.nhallonepay"), listOf("NH pay", "NH페이", "농협카드")),
        Institution("토스", InstitutionKind.PAY, listOf("viva.republica.toss"), listOf("토스")),
        Institution("카카오페이", InstitutionKind.PAY, listOf("com.kakaopay.app"), listOf("카카오페이")),
        Institution("네이버페이", InstitutionKind.PAY, listOf("com.naverfin.payapp"), listOf("네이버페이", "N pay")),
        Institution("삼성페이", InstitutionKind.PAY, listOf("com.samsung.android.spay"), listOf("삼성 월렛", "삼성페이", "Samsung Wallet")),
    )

    fun named(name: String?): Institution? = all.firstOrNull { it.name == name }

    fun of(kind: InstitutionKind): List<Institution> = all.filter { it.kind == kind }

    /**
     * 이 은행·카드의 알림이 올 앱. 폰에 깔린 앱([installed]: 패키지 → 앱 이름)에서 패키지나 이름으로 찾고,
     * 폰 앱 목록을 못 읽었으면 알려진 패키지로 둔다.
     */
    fun appFor(inst: Institution, installed: Map<String, String>): String? {
        if (installed.isEmpty()) return inst.packages.firstOrNull()
        inst.packages.firstOrNull { it in installed }?.let { return it }
        return installed.entries.firstOrNull { (_, label) -> inst.labelWords.any { it in label } }?.key
    }
}
