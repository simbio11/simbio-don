package com.simbio.don.app

import com.simbio.don.app.data.AccountBalance
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.LivingGap
import com.simbio.don.app.data.SplitBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 생활비 통장 잔액과 남은 생활비가 왜 다른지 항목으로 맞춰 본다 */
class LivingGapTest {
    private fun acct(id: Long, name: String, type: AccountType, order: Int, hidden: Boolean = false) =
        AccountEntity(id = id, name = name, type = type.name, opening = 0, sortOrder = order, hidden = hidden)

    private fun bal(a: AccountEntity, v: Long) = AccountBalance(a, v)

    @Test
    fun `카드로 쓴 돈은 통장에서 아직 안 나갔으니 쓸 수 있는 돈에서 뺀다`() {
        val bank = acct(1, "생활비 통장", AccountType.BANK, 0)
        val card = acct(2, "신한카드", AccountType.CARD, 1)
        // 월 생활비 100만원 중 카드로 30만원 씀 → 남은 생활비 70만원, 통장은 그대로 100만원, 카드 -30만원
        val g = LivingGap.of(listOf(bal(bank, 1_000_000), bal(card, -300_000)), SplitBook.Empty, 0, 700_000)!!
        assertEquals(300_000L, g.cardDue)
        assertEquals(700_000L, g.usable)
        assertEquals(0L, g.rest)
        assertTrue(g.hasItems)
    }

    @Test
    fun `나눠 낸 돈 못 받은 것은 더하고, 보낼 돈과 통장 없는 지출은 뺀다`() {
        val bank = acct(1, "생활비 통장", AccountType.BANK, 0)
        // 통장에서 10만원 냈고 내 몫은 2만원 (남은 생활비 98만원), 8만원은 받을 돈
        val book = SplitBook(
            receivables = listOf(com.simbio.don.app.data.Receivable(entry = com.simbio.don.app.data.EntryEntity(id = 1, epochDay = 0, amount = 100_000, label = "삼겹살", type = "SPEND", source = "MANUAL", share = 20_000, people = 5), receipts = emptyList())),
            payables = emptyList(), hints = emptyList(), done = emptyList(),
        )
        val g = LivingGap.of(listOf(bal(bank, 900_000)), book, 0, 980_000)!!
        assertEquals(80_000L, g.receivable)
        assertEquals(980_000L, g.usable)
        assertEquals(0L, g.rest)
        // 통장 없이 적힌 지출 5천원: 생활비엔 들어갔지만 잔액엔 안 빠졌다
        val g2 = LivingGap.of(listOf(bal(bank, 1_000_000)), SplitBook.Empty, 5_000, 995_000)!!
        assertEquals(995_000L, g2.usable)
        assertEquals(0L, g2.rest)
    }

    @Test
    fun `생활비 통장은 첫 은행 통장, 없으면 현금, 숨긴 통장은 건너뛴다`() {
        val hidden = acct(1, "옛 통장", AccountType.BANK, 0, hidden = true)
        val cash = acct(2, "지갑", AccountType.CASH, 1)
        assertEquals("지갑", LivingGap.of(listOf(bal(hidden, 5), bal(cash, 7)), SplitBook.Empty, 0, 0)!!.account.name)
        val bank2 = acct(3, "월급 통장", AccountType.BANK, 2)
        assertEquals("월급 통장", LivingGap.of(listOf(bal(cash, 7), bal(bank2, 9)), SplitBook.Empty, 0, 0)!!.account.name)
        assertNull(LivingGap.of(listOf(bal(acct(4, "카드", AccountType.CARD, 0), -1)), SplitBook.Empty, 0, 0))
    }
}
