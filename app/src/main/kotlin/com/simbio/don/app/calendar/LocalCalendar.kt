package com.simbio.don.app.calendar

import com.simbio.don.app.data.AppEventEntity
import com.simbio.don.cal.Recurrence
import com.simbio.don.cal.Repeat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 이 앱 안 캘린더 ("돈't 캘린더"): 폰 캘린더를 안 이었거나 폰에 남기기 싫은 일정. 일정 id는 폰 일정과 겹치지 않게 음수로 쓴다.
 */
object LocalCalendar {
    const val CALENDAR_ID = -1L
    const val NAME = "돈't 캘린더 (이 앱 안)"
    val COLOR: Int = 0xFF3D6BFF.toInt()

    fun eventId(entityId: Long): Long = -entityId
    fun entityId(eventId: Long): Long = -eventId

    /** [from]~[to]에 보이는 날마다 (반복은 회차마다, 여러 날은 날마다) */
    fun toEvents(list: List<AppEventEntity>, from: LocalDate, to: LocalDate): List<CalEvent> = list.flatMap { a ->
        val start = LocalDate.ofEpochDay(a.startEpochDay)
        val length = ChronoUnit.DAYS.between(start, LocalDate.ofEpochDay(a.endEpochDay)).coerceAtLeast(0)
        val repeat = Repeat.of(a.repeat)
        Recurrence.starts(start, length, repeat, from, to).flatMap { s ->
            spread(
                CalEvent(
                    eventId = eventId(a.id), calendarId = CALENDAR_ID, title = a.title, date = s, minuteOfDay = a.startMinute,
                    color = a.color.takeIf { it != 0 } ?: COLOR, location = a.location.ifBlank { null }, startDate = s,
                    endDate = s.plusDays(length), endMinute = a.endMinute, memo = a.memo.ifBlank { null }, repeat = repeat,
                    reminder = a.reminder, local = true,
                ),
                from, to,
            )
        }
    }

    fun entity(id: Long, d: EventDraft) = AppEventEntity(
        id = id,
        title = d.title.trim().take(60).ifBlank { "(제목 없음)" },
        startEpochDay = d.startDate.toEpochDay(),
        startMinute = d.startMinute,
        endEpochDay = maxOf(d.endDate, d.startDate).toEpochDay(),
        endMinute = if (d.allDay) null else d.endMinute,
        location = d.location.trim().take(60),
        memo = d.memo.trim().take(500),
        repeat = d.repeat.name,
        reminder = d.reminder,
    )
}
