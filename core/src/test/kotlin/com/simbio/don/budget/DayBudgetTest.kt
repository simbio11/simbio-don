package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayBudgetTest {
    // 월~일 한 주, 예산 350,000 (넘어온 돈 없음)
    private val week = Week(0, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), 350_000)
    private fun result(entries: List<Entry>) = WeekResult(week, 0, entries.filter { it.type == EntryType.SPEND }.sumOf { it.amount }, 0)
    private val wed = LocalDate.of(2026, 10, 21)

    @Test
    fun `그날 쓴 식비는 오늘 몫에서 빠지고 넘으면 넘었다고`() {
        // 월·화에 50,000씩 → 수요일 아침 남은 돈 250,000, 남은 날 5일 → 오늘 몫 50,000
        val before = listOf(
            Entry(wed.minusDays(2), 50_000, "장보기", category = Category.LIVING),
            Entry(wed.minusDays(1), 50_000, "점심", category = Category.FOOD),
        )
        val today = listOf(Entry(wed, 12_000, "점심", category = Category.FOOD), Entry(wed, 48_000, "술", category = Category.FOOD))
        val all = before + today
        val b = DayBudgets.of(result(all), all, wed, DayBudgets.DEFAULT_DAILY)
        assertEquals(50_000, b.allowance)
        assertEquals(60_000, b.spentDaily)
        assertEquals(-10_000, b.left)
        assertTrue(b.over)
        // 그날 몫을 넘겨도 오늘 몫 자체는 아침 기준 그대로 (나눠지지 않는다)
        assertEquals(5, b.daysLeft)
    }

    @Test
    fun `큰 지출과 이번 주 계획은 이번 주에서 미리 빼고 나눈다`() {
        val today = listOf(Entry(wed, 100_000, "축의금", category = Category.EVENT), Entry(wed, 8_000, "카페", category = Category.CAFE))
        val plans = listOf(LocalDate.of(2026, 10, 24) to 50_000L, LocalDate.of(2026, 10, 30) to 70_000L)
        val b = DayBudgets.of(result(today), today, wed, DayBudgets.DEFAULT_DAILY, plans)
        // 아침 350,000 − 축의금 100,000 − 토요일 계획 50,000 (다음 주 계획은 빼지 않음) = 200,000 ÷ 5일
        assertEquals(40_000, b.allowance)
        assertEquals(100_000, b.spentBig)
        assertEquals(50_000, b.reserved)
        assertEquals(8_000, b.spentDaily)
        assertEquals(32_000, b.left)
    }

    @Test
    fun `환불은 그날 지출에서 빼고, 이번 주 밖이면 0`() {
        val today = listOf(Entry(wed, 20_000, "점심", category = Category.FOOD), Entry(wed, 5_000, "점심 일부 취소", EntryType.REFUND, category = Category.FOOD))
        val b = DayBudgets.of(WeekResult(week, 0, 15_000, 0), today, wed, DayBudgets.DEFAULT_DAILY)
        assertEquals(15_000, b.spentDaily)
        assertEquals(DayBudget.Empty, DayBudgets.of(WeekResult(week, 0, 0, 0), emptyList(), LocalDate.of(2026, 10, 27), DayBudgets.DEFAULT_DAILY))
    }

    @Test
    fun `미리 예산을 정한 회식은 식비여도 이번 주에서 빼 둔 돈이라 오늘 몫을 안 넘긴다`() {
        val dinner = Entry(wed, 62_000, "팀 회식", category = Category.FOOD)
        val lunch = Entry(wed, 9_000, "점심", category = Category.FOOD)
        val today = listOf(dinner, lunch)
        val plain = DayBudgets.of(result(today), today, wed, DayBudgets.DEFAULT_DAILY)
        assertTrue(plain.over)
        val b = DayBudgets.of(result(today), today, wed, DayBudgets.DEFAULT_DAILY, planned = { it === dinner })
        // 아침 350,000 − 회식 62,000 = 288,000 ÷ 5일 → 57,600, 점심만 오늘 몫에서
        assertEquals(57_600, b.allowance)
        assertEquals(62_000, b.spentBig)
        assertEquals(9_000, b.spentDaily)
        assertEquals(48_600, b.left)
    }

    @Test
    fun `편의점·마트처럼 그날 분류가 아니어도 오늘 몫보다 작으면 오늘 쓴 돈으로 센다`() {
        // 아침 350,000 ÷ 5일 = 오늘 몫 70,000. 편의점 18,190원은 작아서 오늘 쓴 돈, 노트북 1,200,000원은 커서 이번 주에서
        val small = Entry(wed, 18_190, "GS25", category = Category.LIVING)
        val other = Entry(wed, 9_900, "모르는 가게", category = Category.ETC)
        val b = DayBudgets.of(result(listOf(small, other)), listOf(small, other), wed, DayBudgets.DEFAULT_DAILY)
        assertEquals(28_090, b.spentDaily)
        assertEquals(0, b.spentBig)
        assertEquals(28_090, b.spentToday)
        assertEquals(70_000, b.allowance)
        val laptop = Entry(wed, 300_000, "노트북", category = Category.SHOPPING)
        val all = listOf(small, laptop)
        val c = DayBudgets.of(WeekResult(week, 0, 318_190, 0), all, wed, DayBudgets.DEFAULT_DAILY)
        assertEquals(18_190, c.spentDaily)
        assertEquals(300_000, c.spentBig)
        assertEquals(318_190, c.spentToday)
        // 아침 350,000 − 노트북 300,000 = 50,000 ÷ 5일
        assertEquals(10_000, c.allowance)
    }
}
