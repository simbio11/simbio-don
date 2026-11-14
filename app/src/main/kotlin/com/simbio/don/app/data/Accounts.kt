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
        // 아직 안 보낸 돈은 잔액을 바꾸지 않는다
        if (e.owe) return 0
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
     * [today]를 주면 그날까지의 기록만 센다 — 할부 남은 달처럼 앞으로 적힐 기록은 그날이 와야 잔액에 들어간다
     * (안 그러면 카드값을 다 내도 카드에 남은 할부가 "낼 돈"으로 남아 생활비와 통장이 안 맞는다).
     */
    fun compute(accounts: List<AccountEntity>, all: List<EntryEntity>, allChecks: List<AssetCheckEntity>, today: java.time.LocalDate? = null): List<AccountBalance> {
        val entries = today?.toEpochDay()?.let { d -> all.filter { it.epochDay <= d } } ?: all
        val checks = today?.toEpochDay()?.let { d -> allChecks.filter { it.epochDay <= d } } ?: allChecks
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
        entries.filter { !it.owe }.forEach { e ->
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

/**
 * 생활비 통장 잔액과 "이번 달 남은 생활비"가 왜 다른지.
 *
 * 둘은 원래 다른 숫자다: 통장 잔액은 실제로 통장에 있는 돈이고, 남은 생활비는 예산에서 내 몫 지출만 뺀 것.
 * 그래도 이 항목들을 맞춰 보면 거의 같아져야 한다 — 남는 [rest]가 크면 놓친 알림이나 잘못 적힌 기록이 있다는 뜻.
 *
 * 카드로 쓴 돈 중 **고정 지출**로 적은 것은 처음부터 생활비에서 빼고 따로 챙긴 돈이라, 카드값이 남아 있어도
 * "쓸 수 있는 돈"에서 또 빼지 않는다 ([cardFixed]).
 */
data class LivingGap(
    val account: AccountEntity,
    /** 통장 잔액 */
    val bank: Long,
    /** 현금·페이 머니 잔액 (통장에서 뽑거나 충전한 생활비) */
    val cash: Long,
    /** 카드로 쓰고 아직 안 낸 카드값 중 생활비로 쓴 몫 (생활비엔 들어갔지만 통장에선 아직 안 나감) */
    val cardDue: Long,
    /** 아직 안 낸 카드값 중 고정 지출로 적은 몫 (생활비가 아니라 따로 챙긴 돈이라 빼지 않는다) */
    val cardFixed: Long,
    /** 나눠 낸 돈 중 아직 못 받은 것 (통장에선 나갔지만 생활비엔 내 몫만) */
    val receivable: Long,
    /** 남이 내서 아직 안 보낸 돈 (생활비엔 들어갔지만 통장에선 아직 안 나감) */
    val payable: Long,
    /** 어느 통장인지 없이 적힌 지출 (생활비엔 들어갔지만 통장 잔액엔 빠짐) */
    val unassigned: Long,
    /** 이번 달 남은 생활비 */
    val remaining: Long,
) {
    /** 통장 잔액에 위 항목을 맞춰 본 실제로 쓸 수 있는 돈 */
    val usable: Long get() = bank + cash - cardDue + receivable - payable - unassigned

    /** 그래도 남는 차이 (처음 통장에 있던 생활비 밖의 돈이거나, 놓친 알림·잘못 적힌 기록) */
    val rest: Long get() = usable - remaining

    /** 보여 줄 만큼 설명할 항목이 있는지 */
    val hasItems: Boolean get() = cash != 0L || cardDue != 0L || cardFixed != 0L || receivable != 0L || payable != 0L || unassigned != 0L

    companion object {
        /**
         * 생활비 통장 = 첫 번째 은행 통장 (없으면 현금). 없으면 null.
         * [cycleEntries]는 이번 달 기록 — 카드로 쓴 고정 지출을 찾아 카드값에서 생활비 몫만 남긴다.
         */
        fun of(
            balances: List<AccountBalance>,
            book: SplitBook,
            unassignedSpend: Long,
            remaining: Long,
            cycleEntries: List<EntryEntity> = emptyList(),
            /** 오늘. 주면 이번 달 안에 앞으로 카드로 나갈 기록(할부 다음 몫 등)도 카드값으로 센다 — 남은 생활비에서 이미 뺀 돈이라 */
            today: java.time.LocalDate? = null,
        ): LivingGap? {
            val shown = balances.filter { !it.account.hidden }.sortedBy { it.account.sortOrder }
            val main = shown.firstOrNull { it.type == AccountType.BANK } ?: shown.firstOrNull { it.type == AccountType.CASH } ?: return null
            val cards = shown.filter { it.type == AccountType.CARD }
            val debt = cards.filter { it.balance < 0 }.sumOf { -it.balance }
            // 카드로 쓴 고정 지출 (이번 달): 아직 안 낸 카드값 안에서만 센다
            val cardIds = cards.map { it.account.id }.toSet()
            val fixedOnCards = cycleEntries.filter { it.type == com.simbio.don.budget.EntryType.FIXED.name && it.accountId in cardIds && !it.owe }.sumOf { it.amount }
            val upcoming = today?.toEpochDay()?.let { d ->
                cycleEntries.filter { it.epochDay > d && it.accountId in cardIds && !it.owe }.sumOf { e ->
                    when (e.type) {
                        com.simbio.don.budget.EntryType.SPEND.name, com.simbio.don.budget.EntryType.EXCEPTION.name -> e.amount
                        com.simbio.don.budget.EntryType.REFUND.name -> -e.amount
                        else -> 0L
                    }
                }
            } ?: 0L
            val cardFixed = minOf(debt, fixedOnCards)
            val cash = shown.filter { it.account.id != main.account.id && (it.type == AccountType.CASH || it.type == AccountType.ETC) }.sumOf { it.balance }
            return LivingGap(main.account, main.balance, cash, debt - cardFixed + upcoming, cardFixed, book.toReceive, book.toSend, unassignedSpend, remaining)
        }
    }
}
