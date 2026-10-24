package com.simbio.don.notify

import java.time.Duration
import java.time.LocalDateTime

/**
 * 내 계좌끼리 옮긴 돈 알아보기. 지출·수입이 아니라 이체로 적어야 예산·수입이 틀리지 않는다.
 *
 * - 송금·이체로 나간 알림과 같은 금액이 [WINDOW] 안에 들어온 알림 → 한 건의 이체로 합친다.
 * - 알림 글에 "내 계좌로"처럼 내 계좌라는 말이 있거나, 송금·입금 글에 내 이름(예금주)이 보이면 한쪽만 와도 이체.
 * 카드 결제("승인·결제")는 송금이 아니라서 짝을 찾지 않는다.
 */
object SelfTransfer {
    val WINDOW: Duration = Duration.ofMinutes(15)

    private val SEND_WORDS = listOf("이체", "송금", "보냈", "보냄", "보내기", "출금")
    private val SELF_WORDS = listOf("내 계좌", "내계좌", "내 통장", "내통장", "본인 계좌", "본인계좌", "본인이체", "본인 이체", "나에게", "내 다른 계좌")

    /** 송금·이체로 나간 알림 (카드 결제가 아니라) */
    fun isSendOut(p: ParsedPayment): Boolean = p.action == RuleAction.SPEND && SEND_WORDS.any { it in p.rawText }

    /** 한쪽만 봐도 내 계좌끼리인 것 같은지 */
    fun looksSelf(p: ParsedPayment, ownerName: String?): Boolean {
        if (p.action != RuleAction.SPEND && p.action != RuleAction.INCOME) return false
        if (SELF_WORDS.any { it in p.rawText }) return true
        val name = ownerName?.trim()?.takeIf { it.length >= 2 } ?: return false
        val moving = isSendOut(p) || p.action == RuleAction.INCOME
        return moving && name in p.rawText
    }

    /** 나간 것과 들어온 것이 같은 돈인지 (같은 금액, [WINDOW] 안) */
    fun isPair(outAmount: Long, outAt: LocalDateTime, inAmount: Long, inAt: LocalDateTime): Boolean =
        outAmount == inAmount && Duration.between(outAt, inAt).abs() <= WINDOW
}
