package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/**
 * 내가 다 내고 나눠 받을 돈. 생활비에는 내 몫([EntryEntity.share])만, 잔액에는 낸 돈 전부가 들어가고
 * 받은 정산([receipts])은 수입이 아니라 잔액에만 더해진다.
 */
data class Receivable(val entry: EntryEntity, val receipts: List<EntryEntity>) {
    /** 남들 몫 (받을 돈 전체) */
    val others: Long get() = (entry.amount - (entry.share ?: entry.amount)).coerceAtLeast(0)
    val received: Long get() = receipts.sumOf { it.amount }
    val left: Long get() = (others - received).coerceAtLeast(0)
    val open: Boolean get() = left > 0

    /** 한 사람이 보낼 돈 (몇 명이서를 알면) */
    val perPerson: Long? get() = entry.people?.takeIf { it > 1 }?.let { Splits.perPerson(entry.amount, it) }
}

/** 들어오거나 나간 돈이 정산 같아 보일 때 묻는 것 */
sealed interface SplitHint {
    /** 들어오거나 나간 기록 id ("아니에요"로 다시 안 묻기) */
    val key: Long

    /** 들어온 돈이 [target]의 정산 같다 */
    data class Received(val income: EntryEntity, val target: Receivable) : SplitHint {
        override val key: Long get() = income.id
    }

    /** 나간 송금이 보낼 돈 [target]을 보낸 것 같다 */
    data class Sent(val out: EntryEntity, val target: EntryEntity) : SplitHint {
        override val key: Long get() = out.id
    }
}

/** 정산 장부: 받을 돈·보낼 돈·짝 제안, 끝난 것 */
data class SplitBook(
    val receivables: List<Receivable>,
    val payables: List<EntryEntity>,
    val hints: List<SplitHint>,
    val done: List<Receivable>,
) {
    val toReceive: Long get() = receivables.sumOf { it.left }
    val toSend: Long get() = payables.sumOf { it.amount }
    val isEmpty: Boolean get() = receivables.isEmpty() && payables.isEmpty() && hints.isEmpty()

    companion object {
        val Empty = SplitBook(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

object Splits {
    /** 나눠 낸 날부터 이만큼 안에 들어오거나 나간 돈만 짝으로 본다 */
    const val WINDOW_DAYS = 14L

    /** 한 사람 몫: 똑같이 나누고 10원 단위로 올림 (남는 끝전은 낸 사람이 안는다) */
    fun perPerson(total: Long, people: Int): Long =
        if (people <= 1) total else ((total + people - 1) / people + 9) / 10 * 10

    /** 내가 다 냈을 때 내 몫 = 전체 − 남들이 보낼 돈 */
    fun myShare(total: Long, people: Int): Long =
        if (people <= 1) total else (total - perPerson(total, people) * (people - 1)).coerceAtLeast(0)

    fun book(entries: List<EntryEntity>, dismissed: Set<Long> = emptySet()): SplitBook {
        val receiptsBy = entries.filter { it.settleFor != null }.groupBy { it.settleFor!! }
        val all = entries.filter { e ->
            val s = e.share
            s != null && s < e.amount && enumOr(e.type, EntryType.SPEND).spendLike
        }.map { Receivable(it, receiptsBy[it.id].orEmpty().sortedWith(compareBy({ r -> r.epochDay }, { r -> r.id }))) }
            .sortedWith(compareByDescending<Receivable> { it.entry.epochDay }.thenByDescending { it.entry.id })
        val open = all.filter { it.open }
        val payables = entries.filter { it.owe }.sortedWith(compareByDescending<EntryEntity> { it.epochDay }.thenByDescending { it.id })

        // 들어온 돈 → 정산일까: 나눠 낸 날부터 2주 안, 남은 받을 돈보다 크지 않게. 한 사람 몫과 같으면 먼저
        val received = entries.filter {
            it.type == EntryType.INCOME.name && it.settleFor == null && it.incomeId == null && it.id !in dismissed
        }.sortedBy { it.epochDay }.mapNotNull { inc ->
            open.filter { r -> inc.epochDay in r.entry.epochDay..(r.entry.epochDay + WINDOW_DAYS) && inc.amount <= r.left }
                .minByOrNull { r ->
                    val fit = when (inc.amount) {
                        r.perPerson -> 0
                        r.left -> 1
                        else -> 2
                    }
                    fit * 1_000L + (inc.epochDay - r.entry.epochDay)
                }?.let { SplitHint.Received(inc, it) }
        }
        // 나간 송금 → 보낼 돈일까: 같은 금액, 하루 전 ~ 2주 안
        val sent = entries.filter { it.type == EntryType.SPEND.name && it.sentOut && !it.owe && it.id !in dismissed }
            .sortedBy { it.epochDay }
            .mapNotNull { out ->
                payables.filter { p -> p.amount == out.amount && out.epochDay in (p.epochDay - 1)..(p.epochDay + WINDOW_DAYS) }
                    .minByOrNull { p -> kotlin.math.abs(out.epochDay - p.epochDay) }
                    ?.let { SplitHint.Sent(out, it) }
            }
            // 보낼 돈 하나에는 송금 하나만
            .distinctBy { it.target.id }
        return SplitBook(open, payables, received + sent, all.filter { !it.open })
    }
}
