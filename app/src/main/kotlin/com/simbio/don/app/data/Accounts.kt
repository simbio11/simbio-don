package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/**
 * 자산 하나와 지금 잔액. 카드는 음수면 갚을 돈.
 * 모으는 통장(저축·투자)은 [principal](처음 금액 + 넣고 뺀 돈)과 견줘 불어난 돈을 본다 — 자산 점검으로 [balance]가 바뀐다.
 */
data class AccountBalance(
    val account: AccountEntity,
    val balance: Long,
    val principal: Long = balance,
    /** 마지막 자산 점검 날 */
    val checkedAt: java.time.LocalDate? = null,
) {
    val type: AccountType get() = enumOr(account.type, AccountType.ETC)

    /** 불어난 돈 (음수면 줄어든 돈) */
    val gain: Long get() = balance - principal
}

/** 모으는 돈 한 시점: 그때 금액과 넣은 돈 */
data class WealthPoint(val date: java.time.LocalDate, val value: Long, val principal: Long)

/** 모으는 돈 (저축·투자 통장) 모아 보기 */
data class Wealth(val pots: List<AccountBalance>, val history: List<WealthPoint>) {
    val total: Long get() = pots.sumOf { it.balance }
    val principal: Long get() = pots.sumOf { it.principal }
    val gain: Long get() = total - principal

    /** 불어난 비율 (bps, 넣은 돈 기준) */
    val gainBps: Int get() = if (principal <= 0) 0 else (gain * 10_000 / principal).toInt()

    /** 마지막 점검 (가장 최근) */
    val lastCheck: java.time.LocalDate? get() = pots.mapNotNull { it.checkedAt }.maxOrNull()
}

object AccountBalances {
    /** 기록 하나가 [accountId] 자산의 잔액을 얼마나 바꾸는지 */
    fun change(e: EntryEntity, accountId: Long): Long {
        var d = 0L
        when (enumOr(e.type, EntryType.SPEND)) {
            EntryType.INCOME, EntryType.REFUND -> if (e.accountId == accountId) d += e.amount
            EntryType.SPEND, EntryType.EXCEPTION, EntryType.FIXED -> if (e.accountId == accountId) d -= e.amount
            EntryType.TRANSFER -> {
                if (e.accountId == accountId) d -= e.amount
                if (e.toAccountId == accountId) d += e.amount
            }
        }
        return d
    }

    /**
     * 잔액. 자산 점검이 있으면 마지막 점검 금액 + 그 다음 날부터의 기록, 없으면 처음 잔액 + 모든 기록.
     * 넣은 돈(원금)은 늘 처음 잔액 + 모든 기록.
     */
    fun compute(accounts: List<AccountEntity>, entries: List<EntryEntity>, checks: List<AssetCheckEntity>): List<AccountBalance> {
        val plain = compute(accounts, entries).associateBy { it.account.id }
        val last = checks.groupBy { it.accountId }.mapValues { (_, l) -> l.maxWith(compareBy({ it.epochDay }, { it.id })) }
        return accounts.map { a ->
            val principal = plain.getValue(a.id).balance
            val c = last[a.id] ?: return@map AccountBalance(a, principal)
            val after = entries.filter { it.epochDay > c.epochDay }.sumOf { change(it, a.id) }
            AccountBalance(a, c.value + after, principal, java.time.LocalDate.ofEpochDay(c.epochDay))
        }
    }

    /** [date]날 끝의 금액과 넣은 돈 */
    private fun at(a: AccountEntity, entries: List<EntryEntity>, checks: List<AssetCheckEntity>, date: Long): Pair<Long, Long> {
        val upto = entries.filter { it.epochDay <= date }
        val principal = a.opening + upto.sumOf { change(it, a.id) }
        val c = checks.filter { it.accountId == a.id && it.epochDay <= date }.maxWithOrNull(compareBy({ it.epochDay }, { it.id }))
            ?: return principal to principal
        return c.value + upto.filter { it.epochDay > c.epochDay }.sumOf { change(it, a.id) } to principal
    }

    /**
     * 모으는 돈 모아 보기. [history]는 처음 점검한 달부터 달마다 마지막 날(이번 달은 오늘) 금액 — 최근 [months]달.
     */
    fun wealth(
        balances: List<AccountBalance>,
        entries: List<EntryEntity>,
        checks: List<AssetCheckEntity>,
        today: java.time.LocalDate,
        months: Int = 12,
    ): Wealth {
        val pots = balances.filter { it.type.grows && !it.account.hidden }
        if (pots.isEmpty()) return Wealth(emptyList(), emptyList())
        val ids = pots.map { it.account.id }.toSet()
        val mine = checks.filter { it.accountId in ids }
        val first = mine.minOfOrNull { it.epochDay }?.let { java.time.LocalDate.ofEpochDay(it) } ?: today
        val start = maxOf(java.time.YearMonth.from(first), java.time.YearMonth.from(today).minusMonths(months - 1L))
        val points = generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(java.time.YearMonth.from(today)) }.map { ym ->
            val end = minOf(ym.atEndOfMonth(), today)
            var v = 0L
            var p = 0L
            pots.forEach { b ->
                val (value, principal) = at(b.account, entries, mine, end.toEpochDay())
                v += value
                p += principal
            }
            WealthPoint(end, v, p)
        }.toList()
        return Wealth(pots, points)
    }

    fun compute(accounts: List<AccountEntity>, entries: List<EntryEntity>): List<AccountBalance> {
        val delta = HashMap<Long, Long>()
        fun add(id: Long?, v: Long) {
            if (id != null) delta[id] = (delta[id] ?: 0) + v
        }
        entries.forEach { e ->
            when (enumOr(e.type, EntryType.SPEND)) {
                EntryType.INCOME, EntryType.REFUND -> add(e.accountId, e.amount)
                EntryType.SPEND, EntryType.EXCEPTION, EntryType.FIXED -> add(e.accountId, -e.amount)
                EntryType.TRANSFER -> {
                    add(e.accountId, -e.amount)
                    add(e.toAccountId, e.amount)
                }
            }
        }
        return accounts.map { AccountBalance(it, it.opening + (delta[it.id] ?: 0)) }
    }

    /** 순자산 = 모든 자산 잔액 합 (카드 빚은 음수라 빠진다) */
    fun net(list: List<AccountBalance>): Long = list.filter { !it.account.hidden }.sumOf { it.balance }

    /** 자산 (0보다 큰 잔액 합) */
    fun assets(list: List<AccountBalance>): Long = list.filter { !it.account.hidden && it.balance > 0 }.sumOf { it.balance }

    /** 쓰는 돈: 현금·통장 등 모으는 통장이 아닌 자산 (0보다 큰 잔액) */
    fun spending(list: List<AccountBalance>): Long =
        list.filter { !it.account.hidden && !it.type.grows && it.balance > 0 }.sumOf { it.balance }

    /** 모으는 돈: 저축·예금·투자 (0보다 큰 잔액) */
    fun growing(list: List<AccountBalance>): Long =
        list.filter { !it.account.hidden && it.type.grows && it.balance > 0 }.sumOf { it.balance }

    /** 부채 (갚을 돈, 양수로) */
    fun debts(list: List<AccountBalance>): Long = list.filter { !it.account.hidden && it.balance < 0 }.sumOf { -it.balance }
}
