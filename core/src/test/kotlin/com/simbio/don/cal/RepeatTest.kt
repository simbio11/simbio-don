package com.simbio.don.cal

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatTest {
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test
    fun `반복 펼치기`() {
        // 한 번뿐인 일정
        assertEquals(listOf(d(10, 27)), Recurrence.starts(d(10, 27), 0, Repeat.NONE, d(10, 1), d(10, 31)))
        assertEquals(emptyList(), Recurrence.starts(d(9, 27), 0, Repeat.NONE, d(10, 1), d(10, 31)))
        // 여러 날 일정은 앞에서 걸쳐 들어온 것도
        assertEquals(listOf(d(9, 29)), Recurrence.starts(d(9, 29), 3, Repeat.NONE, d(10, 1), d(10, 31)))
        // 매주 화요일
        assertEquals(listOf(d(10, 6), d(10, 13), d(10, 20), d(10, 27)), Recurrence.starts(d(9, 1), 0, Repeat.WEEKLY, d(10, 1), d(10, 31)))
        // 매일 (범위 안만)
        assertEquals(3, Recurrence.starts(d(1, 1), 0, Repeat.DAILY, d(10, 29), d(10, 31)).size)
        // 매월 31일은 31일이 있는 달만
        assertEquals(listOf(d(10, 31), d(12, 31)), Recurrence.starts(d(1, 31), 0, Repeat.MONTHLY, d(10, 1), d(12, 31)))
        // 매년 (윤일은 윤년만)
        assertEquals(listOf(d(3, 5)), Recurrence.starts(d(3, 5, 1990), 0, Repeat.YEARLY, d(1, 1), d(12, 31)))
        assertEquals(emptyList(), Recurrence.starts(d(2, 29, 2024), 0, Repeat.YEARLY, d(1, 1), d(12, 31)))
        // 시작 전 범위는 없음
        assertEquals(emptyList(), Recurrence.starts(d(11, 1), 0, Repeat.WEEKLY, d(10, 1), d(10, 31)))
    }

    @Test
    fun `RRULE 읽기`() {
        assertEquals(Repeat.WEEKLY, Repeat.fromRrule("FREQ=WEEKLY;BYDAY=TU;WKST=SU"))
        assertEquals(Repeat.YEARLY, Repeat.fromRrule("FREQ=YEARLY"))
        assertEquals(Repeat.NONE, Repeat.fromRrule(null))
        assertEquals(Repeat.NONE, Repeat.fromRrule("FREQ=HOURLY"))
    }
}
