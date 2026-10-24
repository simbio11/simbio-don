package com.simbio.don.game

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DexTest {
    private val day = LocalDate.of(2026, 10, 24)
    private var nextId = 1L

    private fun item(name: String, status: PurchaseStatus = PurchaseStatus.DELIVERED, grade: CardGrade? = CardGrade.NORMAL, date: LocalDate = day, payload: String? = null) =
        MockItem(nextId++, name, 10_000, ItemKind.guess(name), date, status, grade = grade, payload = payload)

    @Test
    fun `몰에서 산 이름은 옵션을 떼고 상품을 찾는다`() {
        assertEquals("c1", Dex.productOf("오버핏 맨투맨 (M)")?.id)
        assertEquals("s1", Dex.productOf("흰색 스니커즈 (255)")?.id)
        assertEquals("m1", Dex.productOf("치킨 한 마리")?.id)
        assertNull(Dex.productOf("동네 꽃집 장미"))
    }

    @Test
    fun `도착한 적 있는 물건만 칸을 채우고 비우거나 치워도 남는다`() {
        val items = listOf(
            item("오버핏 맨투맨 (M)", grade = CardGrade.RARE, date = day.minusDays(7)),
            item("오버핏 맨투맨 (L)", PurchaseStatus.DISCARDED, CardGrade.LEGEND),
            item("치킨 한 마리", PurchaseStatus.RECYCLED),
            item("무선 이어폰 (블랙)", PurchaseStatus.SHIPPING),
            item("최신 스마트폰 (화이트)", PurchaseStatus.RETURNED),
            item("기본 반팔티 3팩 (M)", PurchaseStatus.WISHED),
            item("동네 꽃집 장미"),
            item("동네 꽃집 장미", grade = CardGrade.RARE),
            item("쿠션 팩트", payload = "look:…"), // 시술은 도감에 안 들어감
        )
        val entries = Dex.entries(items).associateBy { it.product.id }
        assertEquals(Catalog.products.size, entries.size)
        assertEquals(2, entries["c1"]!!.count)
        assertEquals(CardGrade.LEGEND, entries["c1"]!!.best)
        assertEquals(day.minusDays(7), entries["c1"]!!.first)
        assertTrue(entries["m1"]!!.got)
        assertFalse(entries["e1"]!!.got)
        assertFalse(entries["e5"]!!.got)
        assertFalse(entries["c2"]!!.got)
        assertFalse(entries["y2"]!!.got)
        assertEquals(setOf("c1", "m1"), Dex.gotIds(items))

        val other = Dex.others(items).single()
        assertEquals("동네 꽃집 장미", other.name)
        assertEquals(2, other.count)
        assertEquals(CardGrade.RARE, other.best)
    }

    @Test
    fun `이번 결산으로 새로 채울 칸은 이미 있는 칸을 뺀다`() {
        val before = listOf(item("오버핏 맨투맨 (M)"))
        val arriving = listOf(item("오버핏 맨투맨 (L)", PurchaseStatus.SHIPPING), item("마라탕", PurchaseStatus.SHIPPING), item("나만의 물건", PurchaseStatus.SHIPPING))
        assertEquals(setOf("m2"), Dex.newIds(before, arriving))
    }

    @Test
    fun `세트를 다 모으면 칭호를 고를 수 있다`() {
        // 세트 상품은 모두 몰에 있고, 비싼 물건만 모으는 세트는 없다
        assertTrue(Sets.all.all { s -> s.products.size == 4 })
        assertEquals(Sets.all.size, Sets.all.map { it.id }.toSet().size)
        assertTrue(Sets.all.all { s -> s.products.minOf { it.price } < 200_000 })

        val fit = Sets.byId("fit")!!
        val three = setOf("c7", "s2", "e3")
        assertEquals(3, Sets.done(fit, three))
        assertFalse(Sets.isComplete(fit, three))
        val all = three + "e1"
        assertEquals(listOf(fit), Sets.newlyCompleted(three, all))
        assertEquals(emptyList(), Sets.newlyCompleted(all, all))
        assertEquals(listOf("오운완"), Sets.titles(all))
        // 도감 10칸이면 수집 입문
        val ten = Catalog.products.take(10).map { it.id }.toSet()
        assertTrue("수집 입문" in Sets.titles(ten))
        assertTrue("도감 마스터" in Sets.titles(Catalog.products.map { it.id }.toSet()))
    }

    @Test
    fun `미니게임 마일은 이번 주 결산 마일의 10%까지`() {
        assertEquals(250L, MiniGameRewards.weeklyCap(2_500))
        assertEquals(0L, MiniGameRewards.weeklyCap(0))
        assertEquals(50L, MiniGameRewards.grant(50, 250, 0))
        assertEquals(30L, MiniGameRewards.grant(50, 250, 220))
        assertEquals(0L, MiniGameRewards.grant(50, 250, 250))
        assertEquals(0L, MiniGameRewards.grant(50, 0, 0))
    }

    @Test
    fun `얼마일까는 진짜 값 하나와 그럴듯한 틀린 값 둘`() {
        repeat(30) { seed ->
            val qs = PriceQuiz.questions(seed.toLong())
            assertEquals(PriceQuiz.ROUNDS, qs.size)
            assertEquals(PriceQuiz.ROUNDS, qs.map { it.product.id }.toSet().size)
            qs.forEach { q ->
                assertEquals(3, q.choices.toSet().size)
                assertEquals(1, q.choices.count { it == q.answer })
            }
        }
        assertEquals(PriceQuiz.questions(7), PriceQuiz.questions(7))
        assertEquals(4_500L, PriceQuiz.nice(4_460))
        assertEquals(39_000L, PriceQuiz.nice(38_700))
        assertEquals(1_550_000L, PriceQuiz.nice(1_548_000))
        assertTrue(PriceQuiz.distractors(9_900, Random(1)).none { it == 9_900L })
        // 끝자리로 정답을 알 수 없게 같은 단위로
        assertEquals(1L, PriceQuiz.granularity(23_457))
        assertEquals(10L, PriceQuiz.granularity(23_450))
        assertEquals(100L, PriceQuiz.granularity(29_900))
        assertEquals(1_000L, PriceQuiz.granularity(39_000))
        assertEquals(10_000L, PriceQuiz.granularity(1_550_000))
        repeat(20) { seed -> assertTrue(PriceQuiz.distractors(23_450, Random(seed)).all { it % 10 == 0L && it != 23_450L }) }
        assertEquals(PriceQuiz.ROUNDS + 3, PriceQuiz.realQueries(1).toSet().size)
        assertEquals(50L, PriceQuiz.reward(5))
    }

    @Test
    fun `짝 맞추기는 같은 그림 두 장이면 남고 아니면 다시 뒤집는다`() {
        val cards = MemoryGame.deal(3)
        assertEquals(16, cards.size)
        assertEquals(8, cards.toSet().size)
        assertTrue(cards.groupBy { it }.values.all { it.size == 2 })

        var s = MemoryState(cards)
        val a = 0
        val pair = cards.indices.first { it != a && cards[it] == cards[a] }
        val other = cards.indices.first { cards[it] != cards[a] }
        s = s.flip(a).flip(other)
        assertTrue(s.mismatch)
        assertEquals(1, s.moves)
        // 짝이 아닐 때 다른 카드를 누르면 열린 두 장이 닫히고 새 카드가 열린다
        s = s.flip(a)
        assertFalse(s.mismatch)
        assertEquals(listOf(a), s.open)
        s = s.flip(pair)
        assertEquals(setOf(a, pair), s.matched)
        assertEquals(2, s.moves)
        assertEquals(s, s.flip(a)) // 이미 맞춘 카드는 그대로

        // 전부 맞추기
        cards.indices.groupBy { cards[it] }.values.forEach { (x, y) -> s = s.flip(x).flip(y) }
        assertTrue(s.done)
        assertEquals(9, s.moves)
        assertEquals(50L, MemoryGame.reward(s.moves))
        assertEquals(20L, MemoryGame.reward(30))
    }
}
