package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
    // 2026-10-25(일) ~ 11-24(화), 31일
    private val start = LocalDate.of(2026, 10, 25)
    private val end = LocalDate.of(2026, 11, 24)
    private fun spend(d: LocalDate, a: Long, label: String = "가게", c: Category = Category.FOOD, t: EntryType = EntryType.SPEND) =
        Entry(d, a, label, t, category = c)

    @Test
    fun `평일·주말 속도로 월말을 예측하고 큰돈 한 번은 덜 센다`() {
        val today = start.plusDays(13) // 11/7(토), 14일째
        val entries = (0..13).map { i ->
            val d = start.plusDays(i.toLong())
            val weekend = d.dayOfWeek.value >= 6
            spend(d, if (weekend) 40_000 else 20_000)
        } + spend(start.plusDays(3), 500_000) + spend(start.plusDays(4), 300_000, t = EntryType.FIXED)
        val f = Forecast.month(entries, start, end, 900_000, today)
        assertEquals(17, f.daysLeft)
        // 고정 지출은 빼고, 지금까지 = 평일 10일×2만 + 주말 4일×4만 + 50만
        assertEquals(10 * 20_000L + 4 * 40_000 + 500_000, f.spent)
        // 50만 원짜리 날은 평균에 크게 들어가지 않는다
        assertTrue(f.weekdayAvg in 20_000L..40_000L, "평일 평균 ${f.weekdayAvg}")
        assertTrue(f.weekendAvg in 35_000L..45_000L, "주말 평균 ${f.weekendAvg}")
        assertTrue(f.leftover < 0)
        assertTrue(f.runOut != null && f.runOut!!.isAfter(today))
        assertEquals((900_000L - f.spent) / 17, f.perDayToFit)
    }

    @Test
    fun `첫날은 계획한 속도에 가깝게 본다`() {
        val f = Forecast.month(listOf(spend(start, 200_000)), start, end, 930_000, start)
        // 첫날 20만을 그대로 31일 하면 620만이지만, 계획(하루 3만)과 섞는다
        assertTrue(f.projected < 2_000_000, "예측 ${f.projected}")
        assertNull(Forecast.month(emptyList(), start, end, 930_000, start).runOut)
    }

    @Test
    fun `무지출 날과 이어진 날`() {
        val today = start.plusDays(6)
        val entries = listOf(
            spend(start, 5_000),
            spend(start.plusDays(2), 300_000, t = EntryType.FIXED), // 고정 지출만 나간 날도 무지출
            spend(start.plusDays(1), 3_000),
            spend(start.plusDays(3), 4_000),
        )
        val n = NoSpendDays.of(entries, start, today)
        assertEquals(setOf(start.plusDays(2), start.plusDays(4), start.plusDays(5)), n.days)
        assertEquals(2, n.streak)
    }

    @Test
    fun `매달 비슷한 날 비슷한 금액이면 정기 결제`() {
        val entries = listOf(
            spend(LocalDate.of(2026, 8, 15), 13_500, "넷플릭스", Category.CULTURE),
            spend(LocalDate.of(2026, 9, 15), 13_500, "넷플릭스", Category.CULTURE),
            spend(LocalDate.of(2026, 10, 16), 13_500, "넷플릭스", Category.CULTURE),
            // 금액이 들쭉날쭉
            spend(LocalDate.of(2026, 9, 3), 10_000, "편의점"),
            spend(LocalDate.of(2026, 10, 3), 25_000, "편의점"),
            // 한 달에 여러 번
            spend(LocalDate.of(2026, 9, 5), 4_500, "스타벅스"),
            spend(LocalDate.of(2026, 9, 6), 4_500, "스타벅스"),
            spend(LocalDate.of(2026, 10, 5), 4_500, "스타벅스"),
            // 이미 고정 지출로 둔 것
            spend(LocalDate.of(2026, 9, 20), 55_000, "SKT 통신비"),
            spend(LocalDate.of(2026, 10, 20), 55_000, "SKT 통신비"),
        )
        val subs = Subscriptions.detect(entries, known = listOf("SKT 통신비"))
        assertEquals(listOf("넷플릭스"), subs.map { it.label })
        assertEquals(15, subs.single().day)
        assertEquals(3, subs.single().months)
    }

    @Test
    fun `지난주보다 늘거나 준 카테고리와 주말 씀씀이`() {
        val weekStart = LocalDate.of(2026, 11, 2) // 월
        val today = weekStart.plusDays(3)
        val entries = buildList {
            // 지난주 월~목 카페 4만, 이번 주 월~목 카페 1만
            (0..3).forEach { add(spend(weekStart.minusDays(7).plusDays(it.toLong()), 10_000, "카페", Category.CAFE)) }
            add(spend(weekStart, 10_000, "카페", Category.CAFE))
            // 최근 4주 주말에 많이
            (1..28).forEach { i ->
                val d = today.minusDays(i.toLong())
                add(spend(d, if (d.dayOfWeek.value >= 6) 60_000 else 10_000, "밥"))
            }
        }
        val list = Insights.of(entries, weekStart, today)
        assertTrue(list.any { it.title == "주말에 많이 써요" }, list.toString())
        assertTrue(list.any { it.good }, list.toString())
    }

    @Test
    fun `일주일 안에 나갈 고정 지출`() {
        val cycle = PayCycle(java.time.YearMonth.of(2026, 10), start, end)
        val today = LocalDate.of(2026, 11, 1)
        val up = UpcomingPayments.of(
            listOf(Triple("월세", 500_000L, 5), Triple("통신비", 55_000L, 20), Triple("보험", 80_000L, null), Triple("구독", 9_900L, 3)),
            listOf(null, null, null, LocalDate.of(2026, 11, 3)),
            cycle,
            today,
        )
        assertEquals(listOf("월세"), up.map { it.label })
        assertEquals(LocalDate.of(2026, 11, 5), up.single().date)
    }

    @Test
    fun `을 를`() {
        assertEquals("를", Insights.eul("기타"))
        assertEquals("을", Insights.eul("카페·간식"))
        assertEquals("를", Insights.eul("식비"))
        assertEquals("을", Insights.eul("생활"))
    }
}
