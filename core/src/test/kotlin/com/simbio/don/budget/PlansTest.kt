package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlansTest {
    private val d = LocalDate.of(2026, 11, 2)

    @Test
    fun `한 달 계획 요약은 계획·실제·안 쓴 것·안 쓰기로 한 것을 나눈다`() {
        val plans = listOf(
            Plan(1, d, 50_000, "친구 생일 선물", Category.EVENT, actual = 43_000),
            Plan(2, d.plusDays(3), 30_000, "병원", Category.HEALTH),
            Plan(3, d.plusDays(5), 100_000, "콘서트", Category.CULTURE, canceled = true),
            Plan(4, d.plusDays(40), 70_000, "다음 달 것", Category.ETC),
        )
        val s = Plans.summary(plans, d, d.plusDays(30))
        assertEquals(80_000L, s.planned)
        assertEquals(30_000L, s.openAmount)
        assertEquals(1, s.openCount)
        assertEquals(7_000L, s.doneDiff)
        assertEquals(100_000L, s.canceledAmount)
        assertEquals(listOf(2L), Plans.upcoming(plans, d, d.plusDays(7)).map { it.id })
        assertEquals(listOf(2L), Plans.overdue(plans, d.plusDays(10)).map { it.id })
    }

    @Test
    fun `계획과 맞는 실제 지출은 가까운 날·비슷한 금액·같은 분류나 이름`() {
        val plan = Plan(1, d, 50_000, "생일 선물", Category.EVENT)
        val entries = listOf(
            10L to Entry(d.plusDays(1), 45_000, "올리브영 선물", category = Category.EVENT),
            11L to Entry(d.plusDays(1), 48_000, "주유", category = Category.TRANSPORT),
            12L to Entry(d.plusDays(5), 50_000, "생일 선물", category = Category.EVENT),
            13L to Entry(d, 20_000, "생일 케이크", category = Category.EVENT),
        )
        assertEquals(10L, Plans.match(plan, entries))
        assertNull(Plans.match(plan, entries, taken = setOf(10L)))
    }

    @Test
    fun `예측에 앞으로의 계획을 그날 더한다`() {
        val start = LocalDate.of(2026, 10, 25)
        val end = LocalDate.of(2026, 11, 24)
        val today = start.plusDays(13)
        val entries = (0..13).map { Entry(start.plusDays(it.toLong()), 20_000, "밥") }
        val plain = Forecast.month(entries, start, end, 900_000, today)
        val planned = Forecast.month(entries, start, end, 900_000, today, listOf(end.minusDays(2) to 100_000L, start.plusDays(1) to 50_000L))
        // 지난 날 계획은 빼고, 앞으로의 것만
        assertEquals(plain.projected + 100_000, planned.projected)
        assertEquals(100_000L, planned.plannedAhead)
        assertEquals((900_000 - plain.spent - 100_000) / plain.daysLeft, planned.perDayToFit)
    }

    @Test
    fun `일정 이름으로 계획 분류를 짐작한다`() {
        assertEquals(Category.EVENT, Plans.categoryFor("민지 결혼식"))
        assertEquals(Category.EVENT, Plans.categoryFor("엄마 생일"))
        assertEquals(Category.FOOD, Plans.categoryFor("팀 회식"))
        assertEquals(Category.FOOD, Plans.categoryFor("대학 동기 술약속"))
        assertEquals(Category.CULTURE, Plans.categoryFor("미술관 데이트"))
        assertEquals(Category.CULTURE, Plans.categoryFor("콘서트"))
        // 일정 낱말이 없으면 가게 이름처럼
        assertEquals(Category.CAFE, Plans.categoryFor("스타벅스에서 스터디"))
        assertEquals(Category.ETC, Plans.categoryFor("회의"))
    }

    @Test
    fun `계획과 실제 지출 짝 — 확실하면 잇고, 엔빵이면 몇 명인지, 애매하면 묻는다`() {
        val dinner = Plan(1, d, 50_000, "팀 회식", Category.FOOD)
        // 같은 날 같은 분류, 예산의 ±50% → 확실
        assertEquals(PlanFitKind.STRONG, Plans.fit(dinner, Entry(d, 62_000, "고깃집", category = Category.FOOD))?.kind)
        // 다음 날 새벽 결제도
        assertEquals(PlanFitKind.STRONG, Plans.fit(dinner, Entry(d.plusDays(1), 41_000, "호프", category = Category.FOOD))?.kind)
        // 친구에게 보낸 내 몫 (분류는 기타지만 금액이 거의 같음)
        assertEquals(PlanFitKind.STRONG, Plans.fit(dinner, Entry(d, 50_000, "김철수", category = Category.ETC))?.kind)
        // 내가 다 낸 20만 원 → 4명이 나눴나?
        val split = Plans.fit(dinner, Entry(d, 200_000, "고깃집", category = Category.FOOD))!!
        assertEquals(PlanFitKind.SPLIT, split.kind)
        assertEquals(4, split.people)
        // 이미 나눠 냈다고 적었으면 내 몫으로 견준다
        assertEquals(PlanFitKind.STRONG, Plans.fit(dinner, Entry(d, 50_000, "고깃집", category = Category.FOOD), paid = 200_000, alreadySplit = true)?.kind)
        // 점심값은 아님
        assertNull(Plans.fit(dinner, Entry(d, 9_000, "김밥", category = Category.FOOD)))
        // 일주일 전에 미리 산 선물은 물어본다
        val gift = Plan(2, d, 50_000, "엄마 생일 선물", Category.EVENT)
        assertEquals(PlanFitKind.MAYBE, Plans.fit(gift, Entry(d.minusDays(5), 46_000, "꽃집", category = Category.EVENT))?.kind)
        assertNull(Plans.fit(gift, Entry(d.minusDays(10), 46_000, "꽃집", category = Category.EVENT)))
        // 끝난 계획은 짝이 없다
        assertNull(Plans.fit(dinner.copy(actual = 50_000), Entry(d, 50_000, "고깃집", category = Category.FOOD)))
        // 여럿 중 가장 잘 맞는 것
        val best = Plans.best(listOf(1L to dinner, 2L to gift), Entry(d, 48_000, "꽃집", category = Category.EVENT))
        assertEquals(2L, best?.first)
    }
}
