package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/** 자산 하나와 지금 잔액. 카드는 음수면 갚을 돈. */
data class AccountBalance(val account: AccountEntity, val balance: Long) {
    val type: AccountType get() = enumOr(account.type, AccountType.ETC)
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

    /** 부채 (갚을 돈, 양수로) */
    fun debts(list: List<AccountBalance>): Long = list.filter { !it.account.hidden && it.balance < 0 }.sumOf { -it.balance }
}
