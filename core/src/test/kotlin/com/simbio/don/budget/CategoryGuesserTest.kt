package com.simbio.don.budget

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryGuesserTest {
    @Test
    fun `가게 이름으로 카테고리를 추측한다`() {
        assertEquals(Category.CAFE, CategoryGuesser.guess("스타벅스 강남점"))
        assertEquals(Category.DELIVERY, CategoryGuesser.guess("배달의민족 결제"))
        assertEquals(Category.TRANSPORT, CategoryGuesser.guess("카카오T 택시"))
        assertEquals(Category.LIVING, CategoryGuesser.guess("GS25 역삼점"))
        assertEquals(Category.FOOD, CategoryGuesser.guess("OO치킨"))
        assertEquals(Category.ETC, CategoryGuesser.guess("알 수 없는 가게"))
        assertEquals(Category.ETC, CategoryGuesser.guess(""))
    }

    @Test
    fun `사용자가 고친 가게는 다음부터 그대로 쓴다`() {
        val learned = mapOf(CategoryGuesser.key("스타벅스 강남점") to Category.FOOD)
        assertEquals(Category.FOOD, CategoryGuesser.guess("스타벅스  강남점", learned))
        assertEquals(Category.CAFE, CategoryGuesser.guess("스타벅스 역삼점", learned))
    }
}
