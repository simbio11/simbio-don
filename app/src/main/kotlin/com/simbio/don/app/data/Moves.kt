package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/**
 * 내 계좌끼리 옮긴 돈 찾기 (Sure의 이체 짝짓기처럼).
 *
 * - [into]가 있으면: A 통장에서 나간 송금과 B 통장에 들어온 입금이 금액이 거의 같고(수수료 정도 차이) 사흘 안 → 같은 돈을 옮긴 것 같다.
 * - [into]가 없으면: 통장에서 사람·계좌로 보낸 큰 송금인데 짝이 없다 → 저축·내 다른 통장으로 옮긴 건지 묻는다.
 *
 * 어느 쪽이든 앱이 혼자 바꾸지 않고 확인받는다. "아니에요"는 기억해서([EntryEntity.moveState] = 2) 다시 묻지 않는다.
 */
data class MoveQuestion(val out: EntryEntity, val into: EntryEntity? = null) {
    val key: Long get() = out.id

    /** 금액 차이 (수수료) */
    val fee: Long get() = into?.let { (out.amount - it.amount).coerceAtLeast(0) } ?: 0
}

object Moves {
    /** 출금과 입금 날짜 차이 */
    const val WINDOW_DAYS = 3L

    /** 짝 없이 물어볼 만큼 큰 송금 */
    const val ASK_FROM = 50_000L

    private val SEND = listOf("이체", "송금", "보냈", "보냄", "출금")

    /** 금액이 이만큼 안이면 같은 돈 (수수료·끝전): 1,000원 또는 1% 중 큰 것 */
    fun tolerance(amount: Long): Long = maxOf(1_000L, amount / 100)

    fun close(a: Long, b: Long): Boolean = kotlin.math.abs(a - b) <= tolerance(maxOf(a, b))

    /** 통장·현금·기타에서 송금으로 나간 지출 */
    private fun sendOut(e: EntryEntity, spendable: Set<Long>): Boolean =
        e.type == EntryType.SPEND.name && e.accountId in spendable && e.moveState == 0 && e.settleFor == null && !e.owe &&
            e.share == null && (e.sentOut || SEND.any { (e.rawText ?: "").contains(it) })

    private fun incoming(e: EntryEntity): Boolean =
        e.type == EntryType.INCOME.name && e.accountId != null && e.moveState == 0 && e.settleFor == null && e.incomeId == null && e.linkState != 2

    fun find(entries: List<EntryEntity>, accounts: List<AccountEntity>): List<MoveQuestion> {
        val shown = accounts.filter { !it.hidden }
        val spendable = shown.filter { enumOr(it.type, AccountType.ETC).let { t -> t != AccountType.CARD } }.map { it.id }.toSet()
        val outs = entries.filter { sendOut(it, spendable) }.sortedBy { it.epochDay }
        val ins = entries.filter(::incoming).toMutableList()
        val pairs = mutableListOf<MoveQuestion>()
        val singles = mutableListOf<MoveQuestion>()
        outs.forEach { o ->
            val m = ins.filter { i ->
                i.accountId != o.accountId && kotlin.math.abs(i.epochDay - o.epochDay) <= WINDOW_DAYS && i.amount <= o.amount && close(i.amount, o.amount)
            }.minByOrNull { i -> (o.amount - i.amount) * 10 + kotlin.math.abs(i.epochDay - o.epochDay) }
            if (m != null) {
                ins.remove(m)
                pairs += MoveQuestion(o, m)
            } else if (o.amount >= ASK_FROM) {
                singles += MoveQuestion(o)
            }
        }
        return (pairs + singles).sortedByDescending { it.out.epochDay }
    }
}
