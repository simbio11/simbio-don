package com.simbio.don.game

import com.simbio.don.budget.Week
import com.simbio.don.budget.WeekResult
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun item(id: Long, price: Long, kind: ItemKind, day: Int = 3, status: PurchaseStatus = PurchaseStatus.SHIPPING) =
        MockItem(id, "물건$id", price, kind, d(day), status)

    @Test
    fun `물건 이름으로 종류를 추측한다`() {
        assertEquals(ItemKind.SHOES, ItemKind.guess("나이키 에어포스1 07"))
        assertEquals(ItemKind.CLOTHES, ItemKind.guess("오버핏 티셔츠"))
        assertEquals(ItemKind.DRINK, ItemKind.guess("아이스 아메리카노"))
        assertEquals(ItemKind.MEAL, ItemKind.guess("황금올리브 치킨"))
        assertEquals(ItemKind.ELECTRONICS, ItemKind.guess("에어팟 프로 2"))
        assertEquals(ItemKind.ETC, ItemKind.guess("무언가"))
    }

    @Test
    fun `공유된 글에서 상품명·가격·쇼핑몰을 뽑는다`() {
        val p = SharedTextParser.parse("[쿠팡] 나이키 에어포스1 07 화이트\n129,000원\nhttps://link.coupang.com/a/abc123")
        assertEquals("나이키 에어포스1 07 화이트", p.name)
        assertEquals(129_000, p.price)
        assertEquals("쿠팡", p.store)

        val noPrice = SharedTextParser.parse("오버핏 맨투맨 https://www.musinsa.com/products/1")
        assertEquals("오버핏 맨투맨", noPrice.name)
        assertEquals(null, noPrice.price)
        assertEquals("무신사", noPrice.store)
    }

    @Test
    fun `모의 지갑은 이번 주 결산 전 모의 구매만 뺀다`() {
        val items = listOf(item(1, 30_000, ItemKind.SHOES), item(2, 10_000, ItemKind.CLOTHES, status = PurchaseStatus.DELIVERED))
        assertEquals(1_000_000 - 200_000 - 30_000, MockWallet.balance(1_000_000, 200_000, items))
        assertFalse(MockWallet.canAfford(5_000, 6_000))
        assertTrue(MockWallet.canAfford(5_000, 5_000))
    }

    @Test
    fun `초과액만큼 반품할 물건을 골랐는지 확인한다`() {
        val week = listOf(item(1, 129_000, ItemKind.SHOES, 2), item(2, 23_000, ItemKind.MEAL, 4), item(3, 34_000, ItemKind.BAG, 5))
        assertTrue(WeeklySettlement.isEnough(week, emptySet(), 0))
        assertFalse(WeeklySettlement.isEnough(week, setOf(2), 30_000))
        assertTrue(WeeklySettlement.isEnough(week, setOf(3), 30_000))
        // 모의 구매가 초과액보다 적으면 전부 반품하면 된다
        assertTrue(WeeklySettlement.isEnough(week, setOf(1, 2, 3), 500_000))
        assertEquals(setOf(3L), WeeklySettlement.autoPick(week, 22_000))
        assertEquals(setOf(3L, 2L), WeeklySettlement.autoPick(week, 50_000))
        assertEquals(186_000, WeeklySettlement.resisted(week))
    }

    @Test
    fun `집에 넘친 물건은 바닥에 쌓이고 먹을거리는 쓰레기`() {
        val delivered = (1..6L).map { item(it, 10_000, ItemKind.SHOES, status = PurchaseStatus.DELIVERED) } +
            item(7, 23_000, ItemKind.MEAL, status = PurchaseStatus.DELIVERED) +
            item(8, 50_000, ItemKind.CLOTHES, status = PurchaseStatus.RETURNED)
        val house = HouseLayout.of(HouseLevel.ONE_ROOM, delivered)
        assertEquals(4, house.slots[Slot.SHOE_RACK]!!.size)
        assertEquals(listOf(5L, 6L), house.floor.map { it.id })
        assertEquals(listOf(7L), house.trash.map { it.id })
        assertTrue(house.isCluttered)
        assertEquals(83_000, house.wouldHaveCost)
    }

    @Test
    fun `표정은 이번 주 쓰는 속도로 정한다`() {
        val week = Week(0, d(2), d(8), 70_000)
        fun mood(spent: Long, today: Int) = Moods.of(WeekResult(week, 0, spent, 0), d(today))
        assertEquals(Mood.HAPPY, mood(10_000, 4)) // 3일 지남, 기대 30,000
        assertEquals(Mood.CALM, mood(30_000, 4))
        assertEquals(Mood.WORRIED, mood(50_000, 4))
        assertEquals(Mood.CHEER, mood(80_000, 6))
    }

    @Test
    fun `찜은 하루가 지나야 다시 묻고, 지갑·집·결산에 들어가지 않는다`() {
        val at = java.time.LocalDateTime.of(2026, 11, 3, 21, 30)
        assertFalse(Wishlist.isReady(at, at.plusHours(23).plusMinutes(59)))
        assertTrue(Wishlist.isReady(at, at.plusHours(24)))
        assertEquals(24, Wishlist.hoursLeft(at, at))
        assertEquals(1, Wishlist.hoursLeft(at, at.plusHours(23).plusMinutes(1)))
        assertEquals(0, Wishlist.hoursLeft(at, at.plusDays(2)))

        val items = listOf(
            item(1, 100_000, ItemKind.SHOES, status = PurchaseStatus.WISHED),
            item(2, 30_000, ItemKind.CLOTHES, status = PurchaseStatus.GAVE_UP),
            item(3, 20_000, ItemKind.BAG),
        )
        assertEquals(1_000_000 - 20_000, MockWallet.balance(1_000_000, 0, items))
        assertEquals(listOf(3L), WeeklySettlement.shippingIn(items, d(1), d(7)).map { it.id })
        assertEquals(0, HouseLayout.of(HouseLevel.ONE_ROOM, items).itemCount)
        assertEquals(30_000, Wishlist.gaveUpTotal(items))
    }

    @Test
    fun `모의 지갑 여유로 표정이 바뀐다 (비면 화남, 바닥나면 슬픔, 넉넉하면 기쁨)`() {
        val cycle = com.simbio.don.budget.PayCycle(java.time.YearMonth.of(2026, 11), d(1), d(30))
        val week = WeekResult(com.simbio.don.budget.Week(0, d(1), d(7), 70_000), 0, 10_000, 0)
        // 11/4: 한 달 300만 중 27일 남음 → 지금쯤 270만이 있어야 함
        fun mood(wallet: Long) = Moods.of(week, d(4), wallet, 3_000_000, cycle)
        assertEquals(Mood.ANGRY, mood(0))
        assertEquals(Mood.SAD, mood(800_000))
        assertEquals(Mood.WORRIED, mood(1_500_000))
        assertEquals(Mood.HAPPY, mood(2_700_000))
        assertEquals(Mood.JOYFUL, mood(3_200_000))
    }
}
