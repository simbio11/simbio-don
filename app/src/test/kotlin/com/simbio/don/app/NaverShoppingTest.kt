package com.simbio.don.app

import com.simbio.don.app.mall.NaverShopping
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 네이버 쇼핑 검색 응답 해석 (네트워크 없이) */
@RunWith(RobolectricTestRunner::class)
class NaverShoppingTest {
    @Test
    fun 제목의_태그를_지우고_값이_없는_것은_뺀다() {
        val json = """
            {"items":[
              {"title":"<b>무선</b> 이어폰 &amp; 케이스","lprice":"89000","mallName":"OO스토어","category1":"디지털/가전","category2":"음향가전","category3":"이어폰"},
              {"title":"가격 없음","lprice":"","mallName":"","category1":""}
            ]}
        """.trimIndent()
        val items = NaverShopping.parse(json)
        assertEquals(1, items.size)
        assertEquals("무선 이어폰 & 케이스", items[0].title)
        assertEquals(89_000L, items[0].price)
        assertEquals("디지털/가전 > 음향가전 > 이어폰", items[0].category)
        assertEquals(emptyList<Any>(), NaverShopping.parse("{}"))
    }
}
