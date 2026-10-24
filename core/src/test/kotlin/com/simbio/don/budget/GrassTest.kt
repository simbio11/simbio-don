package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrassTest {
    private val today = LocalDate.of(2026, 10, 24) // 토
    private fun spend(d: LocalDate, a: Long, t: EntryType = EntryType.SPEND) = Entry(d, a, "가게", t)

    @Test
    fun `덜 쓸수록 진한 초록, 하루 몫을 넘으면 빨강`() {
        assertEquals(GrassLevel.NO_SPEND, Grasses.level(0, 30_000))
        assertEquals(GrassLevel.LOW, Grasses.level(15_000, 30_000))
        assertEquals(GrassLevel.OK, Grasses.level(30_000, 30_000))
        assertEquals(GrassLevel.OVER, Grasses.level(30_001, 30_000))
        assertEquals(GrassLevel.OVER, Grasses.level(1, 0))
    }

    @Test
    fun `일요일부터 한 주, 마지막 주는 오늘까지`() {
        val since = today.minusDays(20)
        val entries = listOf(
            spend(today, 10_000),
            spend(today.minusDays(1), 50_000),
            spend(today.minusDays(2), 300_000, EntryType.FIXED), // 고정 지출만 나간 날은 무지출
            spend(today.minusDays(5), 12_000),
            spend(today.minusDays(30), 5_000), // 기록 시작 전 — 그래도 빈칸
        )
        val g = Grasses.of(entries, today, weeks = 6, dailyPlan = 30_000, since = since)
        assertEquals(6, g.weeks.size)
        assertEquals(DayOfWeek.SUNDAY, g.weeks.first().first()!!.date.dayOfWeek)
        // 오늘이 토요일이라 마지막 주가 꽉 찬다
        assertEquals(today, g.weeks.last().last()!!.date)
        assertEquals(GrassLevel.LOW, g.weeks.last().last()!!.level)
        val byDate = g.days.associateBy { it.date }
        assertEquals(GrassLevel.OVER, byDate.getValue(today.minusDays(1)).level)
        assertEquals(GrassLevel.NO_SPEND, byDate.getValue(today.minusDays(2)).level)
        assertEquals(GrassLevel.LOW, byDate.getValue(today.minusDays(5)).level)
        assertEquals(GrassLevel.EMPTY, byDate.getValue(today.minusDays(30)).level)
        assertEquals(GrassLevel.NO_SPEND, byDate.getValue(since).level)
        assertEquals(GrassLevel.EMPTY, byDate.getValue(since.minusDays(1)).level)
        // 어제 썼으니 연속 무지출은 0
        assertEquals(0, g.streak)
        assertEquals(1, g.over)
        assertEquals(21 - 1 - 2, g.noSpend) // 21일 중 어제·5일 전 빼고, 오늘은 아직 모름
    }

    @Test
    fun `오늘 안 쓴 건 아직 빈칸, 어제부터 거꾸로 무지출을 센다`() {
        val entries = listOf(spend(today.minusDays(4), 8_000))
        val g = Grasses.of(entries, today.minusDays(2), weeks = 2, dailyPlan = 20_000, since = today.minusDays(10))
        // 목요일까지: 마지막 주의 금·토는 null
        assertNull(g.weeks.last()[5])
        assertNull(g.weeks.last()[6])
        assertEquals(GrassLevel.EMPTY, g.days.last().level)
        assertEquals(1, g.streak) // 어제(10/21)만 무지출, 10/20엔 씀
        // 기록 전이면 연속도 없다
        assertEquals(0, Grasses.of(emptyList(), today, 4, 20_000, since = null).streak)
    }
}
