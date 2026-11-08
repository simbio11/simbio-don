package com.simbio.don.budget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorthTest {
    @Test
    fun 구간마다_다른_것으로_바꾼다() {
        assertNull(Worths.of(0))
        // 가장 싼 것도 못 사면 다음 목표만
        assertEquals("컵라면 한 개까지 500원", Worths.of(1_000)!!.big)
        // 몇천 원은 먹을 것, 몇십만 원은 여행·물건, 몇백만 원은 스쿠터·노트북, 몇천만 원은 차, 억대는 보증금
        assertTrue(Worths.of(12_000)!!.big.startsWith("국밥 1그릇"))
        assertTrue(Worths.of(300_000)!!.big.startsWith("헬스장 3개월권 1개"))
        assertTrue(Worths.of(3_000_000)!!.big.startsWith("125cc 스쿠터 1대"))
        assertTrue(Worths.of(30_000_000)!!.big.startsWith("준중형 세단 1대"))
        assertTrue(Worths.of(50_000_000)!!.big.startsWith("중형 SUV 1대"))
        assertTrue(Worths.of(120_000_000)!!.big.startsWith("원룸 전세 보증금 1건"))
    }

    @Test
    fun 가장_큰_것과_같이_보여_줄_것은_다_살_수_있고_종류가_겹치지_않는다() {
        val amounts = listOf(1_500L, 5_000, 30_000, 120_000, 800_000, 1_300_000, 2_000_000, 9_000_000, 26_000_000, 60_000_000, 120_000_000, 400_000_000)
        amounts.forEach { a ->
            (0L..11L).forEach { seed ->
                val w = Worths.of(a, seed)!!
                val shown = (listOf(w.big) + w.also).map { s -> Worths.items.last { s.startsWith(it.name) } }
                shown.forEach { assertTrue(it.price <= a, "$a ${w.line}") }
                // 같이 보여 주는 것은 실감 나게 2~1000개 (너무 큰 수는 안 보여 준다)
                w.also.forEach { s ->
                    val n = s.substringAfterLast(' ').filter { it.isDigit() || it == ',' }.replace(",", "").toLong()
                    assertTrue(n in 2..1000, "$a $s")
                }
                // 큰 것과 같은 종류는 다른 종류가 없을 때만, 서로 겹치지 않는다
                assertEquals(shown.size, shown.map { it.name }.toSet().size, "$a $seed ${w.line}")
            }
        }
    }

    @Test
    fun 다음_목표는_못_사는_것이고_남은_돈이_나온다() {
        assertEquals("태블릿까지 30만원", Worths.of(300_000)!!.next)
        assertEquals("유럽 열흘 여행까지 150만원", Worths.of(3_000_000)!!.next)
        assertEquals("중형 SUV까지 1,000만원", Worths.of(30_000_000)!!.next)
        assertEquals("소형 아파트 전세 보증금까지 1.3억원", Worths.of(120_000_000)!!.next)
        assertNotNull(Worths.of(2_000_000)!!.next)
        assertNull(Worths.of(900_000_000)!!.next)
    }

    @Test
    fun 같은_금액도_seed에_따라_조합이_바뀌고_300만원이면_국밥_300그릇도_나온다() {
        val lines = (0L..9L).map { Worths.of(3_000_000, it)!!.line }.toSet()
        assertTrue(lines.size >= 4, lines.toString())
        assertTrue((0L..40L).any { Worths.of(3_000_000, it)!!.line.contains("국밥 300그릇") })
        // 몇만 원일 때도 다른 종류가 없으면 같은 종류로 채운다
        assertTrue(Worths.of(12_000)!!.also.isNotEmpty())
    }
}
