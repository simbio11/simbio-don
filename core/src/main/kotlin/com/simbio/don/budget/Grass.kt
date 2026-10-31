package com.simbio.don.budget

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 위젯 잔디 한 칸의 단계. 깃허브 잔디처럼 진할수록 잘한 날 — 여기서는 **덜 쓸수록** 진한 초록.
 * [EMPTY]는 기록을 시작하기 전이거나 아직 정해지지 않은 오늘(안 쓴 오늘은 끝나야 무지출).
 */
enum class GrassLevel { EMPTY, NO_SPEND, LOW, OK, OVER }

data class GrassDay(val date: LocalDate, val level: GrassLevel, val spent: Long)

/**
 * [weeks]는 왼쪽(오래된 주)부터, 한 주는 [Grass.firstDay]부터 7칸. 오늘 뒤의 칸은 null.
 * [streak]는 어제부터 거꾸로 이어진 무지출 날, [noSpend]·[kept]·[over]는 보이는 칸 중 그런 날 수.
 */
data class Grass(
    val weeks: List<List<GrassDay?>>,
    val today: LocalDate,
    val streak: Int,
    val noSpend: Int,
    val kept: Int,
    val over: Int,
    val firstDay: DayOfWeek,
) {
    val days: List<GrassDay> get() = weeks.flatten().filterNotNull()

    /** 앱을 쓰기 전이라 다 빈칸인 앞쪽 주를 뺀다 (적어도 [minWeeks]주는 남김) */
    fun trimLeadingEmpty(minWeeks: Int): Grass {
        val empty = weeks.takeWhile { w -> w.all { it == null || it.level == GrassLevel.EMPTY } }.size
        val drop = minOf(empty, (weeks.size - minWeeks).coerceAtLeast(0))
        return if (drop == 0) this else copy(weeks = weeks.drop(drop))
    }
}

object Grasses {
    /** 하루 몫의 절반까지는 "아껴 씀" */
    fun level(spent: Long, dailyPlan: Long): GrassLevel = when {
        spent <= 0 -> GrassLevel.NO_SPEND
        dailyPlan <= 0 -> GrassLevel.OVER
        spent * 2 <= dailyPlan -> GrassLevel.LOW
        spent <= dailyPlan -> GrassLevel.OK
        else -> GrassLevel.OVER
    }

    /**
     * 최근 [weeks]주 잔디. 생활비만 센다 (고정 지출·이체·수입은 빼고, [Stats.daily] livingOnly와 같다).
     * [since] 전 날은 기록이 없으니 빈칸 — 쓰기 시작한 날부터 채워진다.
     */
    fun of(
        entries: List<Entry>,
        today: LocalDate,
        weeks: Int,
        dailyPlan: Long,
        since: LocalDate?,
        firstDay: DayOfWeek = DayOfWeek.SUNDAY,
    ): Grass {
        val perDay = Stats.daily(entries, livingOnly = true)
        val weekStart = today.minusDays(((today.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
        val start = weekStart.minusWeeks((weeks - 1).toLong().coerceAtLeast(0))
        val grid = (0 until weeks.coerceAtLeast(1)).map { w ->
            (0 until 7).map { d ->
                val date = start.plusDays(w * 7L + d)
                if (date.isAfter(today)) return@map null
                val spent = (perDay[date] ?: 0).coerceAtLeast(0)
                val level = when {
                    since == null || date.isBefore(since) -> GrassLevel.EMPTY
                    date == today && spent <= 0 -> GrassLevel.EMPTY
                    else -> level(spent, dailyPlan)
                }
                GrassDay(date, level, spent)
            }
        }
        val days = grid.flatten().filterNotNull()
        var streak = 0
        if (since != null) {
            var d = today.minusDays(1)
            while (!d.isBefore(since) && (perDay[d] ?: 0) <= 0) {
                streak++
                d = d.minusDays(1)
            }
        }
        return Grass(
            weeks = grid,
            today = today,
            streak = streak,
            noSpend = days.count { it.level == GrassLevel.NO_SPEND },
            kept = days.count { it.level == GrassLevel.NO_SPEND || it.level == GrassLevel.LOW || it.level == GrassLevel.OK },
            over = days.count { it.level == GrassLevel.OVER },
            firstDay = firstDay,
        )
    }
}
