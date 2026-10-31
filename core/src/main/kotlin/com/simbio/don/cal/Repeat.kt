package com.simbio.don.cal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 일정 반복 (폰 캘린더에는 RRULE로 쓴다) */
enum class Repeat(val label: String, val rrule: String?) {
    NONE("반복 안 함", null),
    DAILY("매일", "FREQ=DAILY"),
    WEEKLY("매주", "FREQ=WEEKLY"),
    MONTHLY("매월", "FREQ=MONTHLY"),
    YEARLY("매년", "FREQ=YEARLY"),
    ;

    companion object {
        fun of(name: String?): Repeat = entries.firstOrNull { it.name == name } ?: NONE

        /** 폰 캘린더 RRULE ("FREQ=WEEKLY;BYDAY=MO") → 반복 (모르는 모양은 FREQ만 본다) */
        fun fromRrule(rrule: String?): Repeat {
            val freq = rrule?.split(';')?.firstOrNull { it.startsWith("FREQ=") }?.removePrefix("FREQ=") ?: return NONE
            return when (freq) {
                "DAILY" -> DAILY
                "WEEKLY" -> WEEKLY
                "MONTHLY" -> MONTHLY
                "YEARLY" -> YEARLY
                else -> NONE
            }
        }
    }
}

/** 반복 일정 펼치기 (이 앱 안 캘린더용 — 폰 캘린더는 폰이 펼쳐 준다) */
object Recurrence {
    /**
     * [start]에 시작해 [length]일 걸리는(0이면 하루) 일정이 [repeat]으로 반복될 때,
     * [from]~[to]와 겹치는 회차의 시작 날들. 31일·2월 29일처럼 없는 달은 건너뛴다 (구글 캘린더와 같게).
     */
    fun starts(start: LocalDate, length: Long, repeat: Repeat, from: LocalDate, to: LocalDate, limit: Int = 500): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        fun overlaps(d: LocalDate) = !d.isAfter(to) && !d.plusDays(length).isBefore(from)
        if (repeat == Repeat.NONE) return if (overlaps(start)) listOf(start) else emptyList()
        val out = mutableListOf<LocalDate>()
        // 범위 바로 앞 회차부터 (매일·매주는 계산으로 건너뛴다)
        var n = when (repeat) {
            Repeat.DAILY -> maxOf(0, ChronoUnit.DAYS.between(start, from) - length)
            Repeat.WEEKLY -> maxOf(0, (ChronoUnit.DAYS.between(start, from) - length) / 7)
            Repeat.MONTHLY -> maxOf(0, ChronoUnit.MONTHS.between(start.withDayOfMonth(1), from.withDayOfMonth(1)) - 1 - length / 28)
            Repeat.YEARLY -> maxOf(0, from.year - start.year - 1L)
            Repeat.NONE -> 0
        }
        var guard = 0
        while (guard++ < limit) {
            val d = when (repeat) {
                Repeat.DAILY -> start.plusDays(n)
                Repeat.WEEKLY -> start.plusWeeks(n)
                Repeat.MONTHLY -> start.plusMonths(n).takeIf { it.dayOfMonth == start.dayOfMonth }
                Repeat.YEARLY -> start.plusYears(n).takeIf { it.dayOfMonth == start.dayOfMonth && it.month == start.month }
                Repeat.NONE -> null
            }
            n++
            if (d == null) continue
            if (d.isAfter(to)) break
            if (overlaps(d)) out += d
        }
        return out
    }
}
