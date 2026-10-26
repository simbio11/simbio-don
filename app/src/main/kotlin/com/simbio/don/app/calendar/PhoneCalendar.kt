package com.simbio.don.app.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.annotation.VisibleForTesting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** 폰에 있는 캘린더 하나 (구글 계정·삼성 캘린더 등) */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean)

/** 캘린더 일정 하나 (반복 일정은 날마다 따로) */
data class CalEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val date: LocalDate,
    /** 0시부터 분 (하루 종일이면 null) */
    val minuteOfDay: Int?,
    val color: Int,
    val location: String? = null,
)

/** 폰 캘린더 읽고 쓰기 (테스트는 가짜로 바꾼다) */
interface CalendarSource {
    fun calendars(): List<DeviceCalendar>
    fun events(from: LocalDate, to: LocalDate, calendarIds: Set<Long>?): List<CalEvent>

    /** 일정 만들기 → 일정 id (못 만들면 null). [minuteOfDay]가 없으면 하루 종일 */
    fun insert(calendarId: Long, title: String, date: LocalDate, minuteOfDay: Int?, description: String): Long?
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
        return list.sortedWith(compareBy({ it.date }, { it.minuteOfDay ?: -1 }, { it.title })).groupBy { it.date }
    }

    fun insert(c: Context, title: String, date: LocalDate, minuteOfDay: Int?, budget: Long?): Long? {
        if (!isOn(c)) return null
        val cal = writeCalendarId(c) ?: return null
        val description = budget?.let { "돈't 예산 %,d원".format(it) }.orEmpty()
        return runCatching { source(c)?.insert(cal, title, date, minuteOfDay, description) }.getOrNull()
    }

    fun delete(c: Context, eventId: Long): Boolean =
        isOn(c) && runCatching { source(c)?.delete(eventId) == true }.getOrDefault(false)
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
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val calId = cur.getLong(1)
                if (calendarIds != null && calId !in calendarIds) continue
                val allDay = cur.getInt(4) == 1
                val startMs = cur.getLong(3)
                // 하루 종일 일정은 UTC 자정으로 저장된다
                val start = if (allDay) LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneOffset.UTC) else LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
                if (start.toLocalDate() !in from..to) continue
                out += CalEvent(
                    eventId = cur.getLong(0),
                    calendarId = calId,
                    title = cur.getString(2).orEmpty().ifBlank { "(제목 없음)" },
                    date = start.toLocalDate(),
                    minuteOfDay = if (allDay) null else start.hour * 60 + start.minute,
                    color = cur.getInt(5),
                    location = cur.getString(6),
                )
            }
        }
        return out
    }

    override fun insert(calendarId: Long, title: String, date: LocalDate, minuteOfDay: Int?, description: String): Long? {
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            if (minuteOfDay == null) {
                val utc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, utc)
                put(CalendarContract.Events.DTEND, utc + 24 * 60 * 60 * 1000L)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                val start = date.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 60 * 60 * 1000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            }
        }
        return resolver.insert(CalendarContract.Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) }
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
        list.filter { it.date in from..to && (calendarIds == null || it.calendarId in calendarIds) }

    override fun insert(calendarId: Long, title: String, date: LocalDate, minuteOfDay: Int?, description: String): Long {
        val id = next++
        list += CalEvent(id, calendarId, title, date, minuteOfDay, cals.firstOrNull { it.id == calendarId }?.color ?: 0)
        return id
    }

    override fun delete(eventId: Long) = list.removeAll { it.eventId == eventId }
}
