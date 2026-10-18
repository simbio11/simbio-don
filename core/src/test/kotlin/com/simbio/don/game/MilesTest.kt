package com.simbio.don.game

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilesTest {
    @Test
    fun `아낀 1%에 100마일, 넘긴 주는 0`() {
        assertEquals(Miles.Earned(1_500, 0, 0), Miles.forWeek(1_500, 0, 7, 1))
        assertEquals(0, Miles.forWeek(1_500, 1, 7, 0).total)
        assertEquals(0, Miles.forWeek(0, 0, 7, 3).total)
        // 짧은 주는 일수에 비례, 100%가 최대
        assertEquals(1_000, Miles.forWeek(2_000, 0, 7, 1).total / 2)
        assertEquals(10_000L * 4 / 7, Miles.forWeek(12_000, 0, 4, 1).base)
    }

    @Test
    fun `연속으로 지키면 10%씩 최대 50% 더`() {
        assertEquals(100, Miles.forWeek(1_000, 0, 7, 2).bonus)
        assertEquals(500, Miles.forWeek(1_000, 0, 7, 6).bonus)
        assertEquals(500, Miles.forWeek(1_000, 0, 7, 20).bonus)
    }

    @Test
    fun `원화는 100원에 1마일로 올림`() {
        assertEquals(1_290, Miles.ofWon(129_000))
        assertEquals(46, Miles.ofWon(4_501))
        assertEquals(0, Miles.ofWon(0))
    }

    @Test
    fun `마일 키우기는 주 0점5% 복리`() {
        assertEquals(10_050, MileGrowth.grow(10_000, 1))
        assertEquals(10_100, MileGrowth.grow(10_000, 2)) // 10050 + 50
        val year = MileGrowth.grow(100_000, 52)
        assertTrue(year in 129_000..130_000, "$year")
        assertEquals(500, MileGrowth.grow(500, 0))
    }

    @Test
    fun `카드는 아낀 비율로 반짝이고 값으로 테두리`() {
        assertEquals(CardGrade.NORMAL, CardGrade.of(null))
        assertEquals(CardGrade.NORMAL, CardGrade.of(999))
        assertEquals(CardGrade.RARE, CardGrade.of(1_000))
        assertEquals(CardGrade.LEGEND, CardGrade.of(2_500))
        assertEquals(PriceTier.BRONZE, PriceTier.of(9_999))
        assertEquals(PriceTier.SILVER, PriceTier.of(10_000))
        assertEquals(PriceTier.GOLD, PriceTier.of(129_000))
        assertEquals(PriceTier.DIAMOND, PriceTier.of(1_200_000))
        // 월급 3,300,000원이면 129,000원은 3.9%, 약 8.2시간 일한 돈
        assertEquals(390, Cards.salaryShareBps(129_000, 3_300_000))
        assertEquals(8.2, Cards.workHours(129_000, 3_300_000))
        assertEquals(null, Cards.workHours(129_000, 0))
    }

    @Test
    fun `뷰티샵은 얼굴·머리 바뀐 것만 값을 받는다`() {
        val now = CharacterLook.MALE
        val next = now.copy(eyes = "big", hairColor = "금발", top = "knit")
        assertEquals(listOf(Shop.Procedure.HAIR_COLOR, Shop.Procedure.EYES), Shop.procedures(now, next))
        assertEquals(listOf("top:knit"), Shop.wardrobeNeeded(next, emptySet()))
        assertEquals(emptyList(), Shop.wardrobeNeeded(next, setOf("top:knit")))
        // 옷만 가져오기
        val merged = Shop.withClothes(now, next)
        assertEquals("basic", merged.eyes)
        assertEquals("knit", merged.top)
    }

    @Test
    fun `집 꾸밈은 저장했다 그대로 읽고 수납 가구만큼 칸이 는다`() {
        val setup = HouseSetup(
            storage = mapOf(Slot.WARDROBE to 2, Slot.DESK to 1),
            ownedInterior = setOf("WALL:민트"),
            interior = mapOf(Shop.Interior.WALL to "민트"),
        )
        assertEquals(setup, HouseSetup.decode(setup.encode()))
        assertEquals(HouseSetup(), HouseSetup.decode(""))
        assertEquals(mapOf(Slot.WARDROBE to 6, Slot.DESK to 2), setup.bonus())
        val d = LocalDate.of(2026, 10, 1)
        val shirts = (1L..9L).map { MockItem(it, "티$it", 10_000, ItemKind.CLOTHES, d, PurchaseStatus.DELIVERED) }
        assertEquals(3, HouseLayout.of(HouseLevel.ONE_ROOM, shirts).floor.size)
        assertEquals(0, HouseLayout.of(HouseLevel.ONE_ROOM, shirts, setup.bonus()).floor.size)
        // 시술은 집에 놓이지 않는다
        val beauty = MockItem(99, "뷰티샵", 400_000, ItemKind.BEAUTY, d, PurchaseStatus.DELIVERED, payload = "look:")
        assertEquals(0, HouseLayout.of(HouseLevel.ONE_ROOM, listOf(beauty)).itemCount)
    }
}
