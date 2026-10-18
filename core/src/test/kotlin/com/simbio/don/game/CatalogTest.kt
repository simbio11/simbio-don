package com.simbio.don.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun `상품 목록은 이름이 겹치지 않고 값이 있다`() {
        assertEquals(Catalog.products.size, Catalog.products.map { it.id }.toSet().size)
        assertTrue(Catalog.products.all { it.price > 0 && it.name.isNotBlank() })
        assertTrue(Catalog.products.size >= 70)
        // 모든 종류에 물건이 있다
        assertEquals(ItemKind.entries.toSet(), Catalog.products.map { it.kind }.toSet())
    }

    @Test
    fun `띄어쓰기 없이도 찾고 종류 이름으로도 찾는다`() {
        assertEquals(listOf("e1"), Catalog.search("무선이어폰").map { it.id })
        assertTrue(Catalog.search("신발").all { it.kind == ItemKind.SHOES })
        assertEquals(Catalog.products, Catalog.search("  "))
    }

    @Test
    fun `장바구니에 같은 것을 담으면 수량만 늘고 5개까지`() {
        val shirt = Catalog.byId("c1")!!
        var cart = Catalog.add(emptyList(), shirt, "M")
        cart = Catalog.add(cart, shirt, "M", 2)
        cart = Catalog.add(cart, shirt, "L")
        assertEquals(listOf(3, 1), cart.map { it.qty })
        cart = Catalog.add(cart, shirt, "M", 9)
        assertEquals(5, cart.first().qty)
        assertEquals(39_000L * 5, cart.first().total)
    }
}
