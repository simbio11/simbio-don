package com.simbio.don.app.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.annotation.VisibleForTesting
import com.simbio.don.cal.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** 폰에 있는 캘린더 하나 (구글 계정·삼성 캘린더 등) */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)

/**
 * 캘린더 일정 하나가 보이는 날. 반복 일정은 회차마다, 여러 날 일정은 날마다 하나씩 ([date]가 보이는 날, [startDate]가 시작한 날).
 */
data class CalEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val date: LocalDate,
    /** 시작 시각 0시부터 분 (하루 종일이면 null) */
    val minuteOfDay: Int?,
    val color: Int,
    val location: String? = null,
    val startDate: LocalDate = date,
    val endDate: LocalDate = startDate,
    /** 끝 시각 (하루 종일이면 null) */
    val endMinute: Int? = null,
    val memo: String? = null,
    val repeat: Repeat = Repeat.NONE,
    /** 몇 분 전 알림 */
    val reminder: Int? = null,
    /** 이 앱 안 캘린더 일정 (폰 캘린더가 아님) */
    val local: Boolean = false,
) {
    val allDay: Boolean get() = minuteOfDay == null
    val multiDay: Boolean get() = endDate.isAfter(startDate)

    /** 이 날 칸에 제목을 쓰는지 (여러 날 일정은 시작 날과 주의 첫날만) */
    val labelHere: Boolean get() = date == startDate || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
}

/** 일정 만들기·고치기 입력 */
data class EventDraft(
    val title: String,
    val startDate: LocalDate,
    val startMinute: Int?,
    val endDate: LocalDate = startDate,
    val endMinute: Int? = startMinute?.let { minOf(it + 60, 24 * 60 - 1) },
    val location: String = "",
    val memo: String = "",
    val repeat: Repeat = Repeat.NONE,
    val reminder: Int? = null,
) {
    val allDay: Boolean get() = startMinute == null

    /** 시작부터 끝까지 (분, 하루 종일이면 날 수 × 하루) */
    fun durationMinutes(): Long = if (allDay) (ChronoUnit.DAYS.between(startDate, endDate) + 1) * 24 * 60
    else ChronoUnit.MINUTES.between(startDate.atStartOfDay().plusMinutes(startMinute!!.toLong()), endDate.atStartOfDay().plusMinutes((endMinute ?: startMinute).toLong())).coerceAtLeast(0)

    companion object {
        fun of(e: CalEvent) = EventDraft(e.title, e.startDate, e.minuteOfDay, e.endDate, e.endMinute, e.location.orEmpty(), e.memo.orEmpty(), e.repeat, e.reminder)
    }
}

/** 폰 캘린더 읽고 쓰기 (테스트는 가짜로 바꾼다) */
interface CalendarSource {
    fun calendars(): List<DeviceCalendar>
    fun events(from: LocalDate, to: LocalDate, calendarIds: Set<Long>?): List<CalEvent>

    /** 일정 만들기 → 일정 id (못 만들면 null) */
    fun insert(calendarId: Long, draft: EventDraft): Long?
    fun update(eventId: Long, draft: EventDraft): Boolean
    fun delete(eventId: Long): Boolean
}

/**
 * 폰 캘린더 연동 (켤 때만). 안드로이드 캘린더(CalendarContract)를 읽고 써서
 * 구글 캘린더·삼성 캘린더 등 폰에 동기화된 캘린더와 이어진다 — 동기화는 폰의 계정이 하고, 이 앱은 일정을 밖으로 보내지 않는다.
 */
object PhoneCalendar {
    private const val PREFS = "calendar"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @VisibleForTesting
    @Volatile var sourceForTests: CalendarSource? = null

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(c: Context): Boolean =
        PERMISSIONS.all { c.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** 읽을 수 있는지 (권한이 있거나 테스트용 캘린더) */
    fun canRead(c: Context): Boolean = sourceForTests != null || hasPermission(c)

    fun isOn(c: Context): Boolean = prefs(c).getBoolean("on", false) && canRead(c)

    fun setOn(c: Context, on: Boolean) = prefs(c).edit().putBoolean("on", on).apply()

    fun source(c: Context): CalendarSource? =
        sourceForTests ?: if (hasPermission(c)) DeviceCalendarSource(c.contentResolver) else null

    /** 보일 캘린더 (null이면 모두) */
    fun shownIds(c: Context): Set<Long>? =
        prefs(c).getStringSet("shown", null)?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setShownIds(c: Context, ids: Set<Long>) = prefs(c).edit().putStringSet("shown", ids.map { it.toString() }.toSet()).apply()

    /** 새 일정을 넣을 캘린더 */
    fun writeCalendarId(c: Context): Long? = prefs(c).getLong("write", -1L).takeIf { it >= 0 }

    fun setWriteCalendarId(c: Context, id: Long?) = prefs(c).edit().putLong("write", id ?: -1L).apply()

    fun calendars(c: Context): List<DeviceCalendar> =
        if (!isOn(c)) emptyList() else runCatching { source(c)?.calendars().orEmpty() }.getOrDefault(emptyList())

    /** [from]~[to] 일정을 날짜별로 (꺼져 있거나 권한이 없으면 빈 것) */
    fun events(c: Context, from: LocalDate, to: LocalDate): Map<LocalDate, List<CalEvent>> {
        if (!isOn(c)) return emptyMap()
        val list = runCatching { source(c)?.events(from, to, shownIds(c)).orEmpty() }.getOrDefault(emptyList())
        return byDay(list)
    }

    /** 날짜별로 (하루 종일·여러 날이 먼저, 그다음 시각순) */
    fun byDay(list: List<CalEvent>): Map<LocalDate, List<CalEvent>> =
        list.sortedWith(compareBy({ it.date }, { if (it.multiDay) 0 else 1 }, { it.minuteOfDay ?: -1 }, { it.title })).groupBy { it.date }

    fun insert(c: Context, title: String, date: LocalDate, minuteOfDay: Int?, budget: Long?): Long? =
        insert(c, EventDraft(title, date, minuteOfDay, memo = budgetMemo(budget)))

    fun insert(c: Context, draft: EventDraft, calendarId: Long? = null): Long? {
        if (!isOn(c)) return null
        val cal = calendarId ?: writeCalendarId(c) ?: return null
        return runCatching { source(c)?.insert(cal, draft) }.getOrNull()
    }

    fun update(c: Context, eventId: Long, draft: EventDraft): Boolean =
        isOn(c) && runCatching { source(c)?.update(eventId, draft) == true }.getOrDefault(false)

    fun delete(c: Context, eventId: Long): Boolean =
        isOn(c) && runCatching { source(c)?.delete(eventId) == true }.getOrDefault(false)

    fun budgetMemo(budget: Long?): String = budget?.let { "돈't 예산 %,d원".format(it) }.orEmpty()
}

/** 일정 하나를 [from]~[to] 안의 날마다 펼친다 */
fun spread(e: CalEvent, from: LocalDate, to: LocalDate): List<CalEvent> {
    val first = maxOf(e.startDate, from)
    val last = minOf(e.endDate, to)
    if (last.isBefore(first)) return emptyList()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.map { e.copy(date = it) }.toList()
}

/** 실제 폰 캘린더 (ContentResolver) */
class DeviceCalendarSource(private val resolver: ContentResolver) : CalendarSource {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override fun calendars(): List<DeviceCalendar> {
        val out = mutableListOf<DeviceCalendar>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.VISIBLE,
            ),
            null, null, null,
        )?.use { cur ->
            while (cur.moveToNext()) {
                if (cur.getInt(5) == 0) continue
                val access = cur.getInt(4)
                out += DeviceCalendar(
                    id = cur.getLong(0),
                    name = cur.getString(1).orEmpty(),
                    account = cur.getString(2).orEmpty(),
                    color = cur.getInt(3),
                    writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        return out
    }

    override fun events(from: LocalDate, to: LocalDate, calendarIds: Set<Long>?): List<CalEvent> {
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val out = mutableListOf<CalEvent>()
        resolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.END,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.RRULE,
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val calId = cur.getLong(1)
                if (calendarIds != null && calId !in calendarIds) continue
                val allDay = cur.getInt(4) == 1
                val startMs = cur.getLong(3)
                val endMs = if (cur.isNull(7)) startMs else cur.getLong(7)
                // 하루 종일 일정은 UTC 자정으로 저장되고 끝은 다음 날 0시 (그날은 빼고)
                val start = time(startMs, allDay)
                val endRaw = time(endMs, allDay)
                val endDay = when {
                    allDay -> endRaw.toLocalDate().minusDays(1)
                    endRaw.toLocalTime() == java.time.LocalTime.MIDNIGHT && endRaw.isAfter(start) -> endRaw.toLocalDate().minusDays(1)
                    else -> endRaw.toLocalDate()
                }.let { if (it.isBefore(start.toLocalDate())) start.toLocalDate() else it }
                val e = CalEvent(
                    eventId = cur.getLong(0),
                    calendarId = calId,
                    title = cur.getString(2).orEmpty().ifBlank { "(제목 없음)" },
                    date = start.toLocalDate(),
                    minuteOfDay = if (allDay) null else start.hour * 60 + start.minute,
                    color = cur.getInt(5),
                    location = cur.getString(6),
                    startDate = start.toLocalDate(),
                    endDate = endDay,
                    endMinute = if (allDay) null else endRaw.hour * 60 + endRaw.minute,
                    memo = cur.getString(8),
                    repeat = Repeat.fromRrule(cur.getString(9)),
                )
                out += spread(e, from, to)
            }
        }
        return out
    }

    private fun time(ms: Long, allDay: Boolean): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), if (allDay) ZoneOffset.UTC else zone)

    private fun values(d: EventDraft, calendarId: Long?): ContentValues = ContentValues().apply {
        calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
        put(CalendarContract.Events.TITLE, d.title)
        put(CalendarContract.Events.DESCRIPTION, d.memo)
        put(CalendarContract.Events.EVENT_LOCATION, d.location)
        val startMs: Long
        if (d.allDay) {
            startMs = d.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            startMs = d.startDate.atStartOfDay(zone).plusMinutes(d.startMinute!!.toLong()).toInstant().toEpochMilli()
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        put(CalendarContract.Events.DTSTART, startMs)
        val minutes = d.durationMinutes().coerceAtLeast(if (d.allDay) 24 * 60L else 0)
        // 반복 일정은 끝 시각 대신 길이(DURATION)를 쓴다 (CalendarContract 규칙)
        if (d.repeat.rrule != null) {
            put(CalendarContract.Events.RRULE, d.repeat.rrule)
            put(CalendarContract.Events.DURATION, if (d.allDay) "P${minutes / (24 * 60)}D" else "PT${minutes * 60}S")
            putNull(CalendarContract.Events.DTEND)
        } else {
            putNull(CalendarContract.Events.RRULE)
            putNull(CalendarContract.Events.DURATION)
            put(CalendarContract.Events.DTEND, startMs + minutes * 60_000L)
        }
    }

    private fun setReminder(eventId: Long, minutes: Int?) {
        resolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        if (minutes == null) return
        resolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            },
        )
    }

    override fun insert(calendarId: Long, draft: EventDraft): Long? {
        val id = resolver.insert(CalendarContract.Events.CONTENT_URI, values(draft, calendarId))?.let { ContentUris.parseId(it) } ?: return null
        runCatching { setReminder(id, draft.reminder) }
        return id
    }

    override fun update(eventId: Long, draft: EventDraft): Boolean {
        val n = resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values(draft, null), null, null)
        runCatching { setReminder(eventId, draft.reminder) }
        return n > 0
    }

    override fun delete(eventId: Long): Boolean =
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
}

/** 테스트·미리보기용 캘린더 (메모리) */
class FakeCalendarSource(
    val cals: List<DeviceCalendar> = listOf(DeviceCalendar(1, "내 캘린더", "me@gmail.com", 0xFF4285F4.toInt(), true)),
) : CalendarSource {
    val list = mutableListOf<CalEvent>()
    private var next = 100L

    override fun calendars() = cals

    override fun events(from: LocalDate, to: LocalDate, calendarIds: Set<Long>?) =
        list.filter { calendarIds == null || it.calendarId in calendarIds }.flatMap { spread(it, from, to) }

    private fun event(id: Long, calendarId: Long, d: EventDraft) = CalEvent(
        id, calendarId, d.title, d.startDate, d.startMinute, cals.firstOrNull { it.id == calendarId }?.color ?: 0,
        d.location.ifBlank { null }, d.startDate, d.endDate, d.endMinute.takeIf { !d.allDay }, d.memo.ifBlank { null }, d.repeat, d.reminder,
    )

    override fun insert(calendarId: Long, draft: EventDraft): Long {
        val id = next++
        list += event(id, calendarId, draft)
        return id
    }

    override fun update(eventId: Long, draft: EventDraft): Boolean {
        val i = list.indexOfFirst { it.eventId == eventId }.takeIf { it >= 0 } ?: return false
        list[i] = event(eventId, list[i].calendarId, draft)
        return true
    }

    override fun delete(eventId: Long) = list.removeAll { it.eventId == eventId }
}
