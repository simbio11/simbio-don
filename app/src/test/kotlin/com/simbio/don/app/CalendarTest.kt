package com.simbio.don.app

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.calendar.DeviceCalendarSource
import com.simbio.don.app.calendar.FakeCalendarSource
import com.simbio.don.app.calendar.PhoneCalendar
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/** 폰 캘린더 흉내 (CalendarContract 주소에 붙인다) */
class FakeCalendarProvider : ContentProvider() {
    companion object {
        val calendars = mutableListOf<Array<Any?>>()
        val instances = mutableListOf<Array<Any?>>()
        val inserted = mutableListOf<ContentValues>()
        val deleted = mutableListOf<Uri>()
        val reminders = mutableListOf<ContentValues>()
        var instancesUri: Uri? = null

        fun reset() {
            calendars.clear(); instances.clear(); inserted.clear(); deleted.clear(); reminders.clear(); instancesUri = null
        }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val c = MatrixCursor(projection)
        when {
            uri.toString().startsWith(CalendarContract.Calendars.CONTENT_URI.toString()) -> calendars.forEach { c.addRow(it) }
            uri.toString().startsWith(CalendarContract.Instances.CONTENT_URI.toString()) -> {
                instancesUri = uri
                instances.forEach { c.addRow(it) }
            }
        }
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        if (uri.toString().startsWith(CalendarContract.Reminders.CONTENT_URI.toString())) {
            reminders += ContentValues(values)
            return ContentUris.withAppendedId(uri, 1)
        }
        inserted += ContentValues(values)
        return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, 500L + inserted.size)
    }

    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
        if (uri.toString().startsWith(CalendarContract.Reminders.CONTENT_URI.toString())) return 0
        deleted += uri
        return 1
    }

    override fun getType(uri: Uri): String? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

/** 0.19: 폰 캘린더 읽고 쓰기 — 하루 종일 일정은 UTC, 시각 있는 일정은 폰 시간대 */
@RunWith(RobolectricTestRunner::class)
class PhoneCalendarTest {
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()
    private val seoul = ZoneId.of("Asia/Seoul")
    private lateinit var before: TimeZone

    @Before
    fun setUp() {
        before = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(seoul))
        FakeCalendarProvider.reset()
        Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(before)
        PhoneCalendar.sourceForTests = null
    }

    @Test
    fun `캘린더 목록 — 숨긴 캘린더는 빼고 읽기만 되는 건 못 쓴다`() {
        FakeCalendarProvider.calendars += arrayOf<Any?>(1L, "me@gmail.com", "me@gmail.com", 0xFF4285F4.toInt(), CalendarContract.Calendars.CAL_ACCESS_OWNER, 1)
        FakeCalendarProvider.calendars += arrayOf<Any?>(2L, "대한민국 공휴일", "me@gmail.com", 0xFF0B8043.toInt(), CalendarContract.Calendars.CAL_ACCESS_READ, 1)
        FakeCalendarProvider.calendars += arrayOf<Any?>(3L, "숨김", "me@gmail.com", 0, CalendarContract.Calendars.CAL_ACCESS_OWNER, 0)
        val cals = DeviceCalendarSource(context.contentResolver).calendars()
        assertEquals(listOf(1L, 2L), cals.map { it.id })
        assertEquals(listOf(true, false), cals.map { it.writable })
    }

    @Test
    fun `일정 — 하루 종일은 UTC 자정, 시각은 폰 시간대, 고른 캘린더만, 범위 밖은 뺀다`() {
        val day = LocalDate.of(2026, 10, 28)
        val utcMidnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val seven = day.atTime(19, 0).atZone(seoul).toInstant().toEpochMilli()
        val nightBefore = day.minusDays(1).atTime(23, 0).atZone(seoul).toInstant().toEpochMilli()
        val day1 = 24 * 60 * 60 * 1000L
        FakeCalendarProvider.instances += arrayOf<Any?>(10L, 1L, "민지 결혼식", utcMidnight, 1, 0xFF4285F4.toInt(), "강남", utcMidnight + day1, "축의금 챙기기", null)
        FakeCalendarProvider.instances += arrayOf<Any?>(11L, 1L, "팀 회식", seven, 0, 0xFF4285F4.toInt(), null, seven + 2 * 3_600_000L, null, "FREQ=WEEKLY;BYDAY=WE")
        FakeCalendarProvider.instances += arrayOf<Any?>(12L, 2L, "개천절", utcMidnight, 1, 0xFF0B8043.toInt(), null, utcMidnight + day1, null, null)
        // 전날 밤 11시 ~ 그날 밤 0시 (그날까지 걸쳐 끝나지 않음)
        FakeCalendarProvider.instances += arrayOf<Any?>(13L, 1L, "전날 밤", nightBefore, 0, 0, null, nightBefore + 3_600_000L, null, null)
        // 사흘 여행 (하루 종일, 끝은 다음 날 0시)
        val tripStart = day.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        FakeCalendarProvider.instances += arrayOf<Any?>(14L, 1L, "제주 여행", tripStart, 1, 0, null, tripStart + 3 * day1, null, null)
        val list = DeviceCalendarSource(context.contentResolver).events(day, day, setOf(1L))
        assertEquals(listOf("민지 결혼식", "팀 회식", "제주 여행"), list.map { it.title })
        assertEquals(listOf<Int?>(null, 19 * 60, null), list.map { it.minuteOfDay })
        assertTrue(list.all { it.date == day })
        assertEquals("강남", list[0].location)
        assertEquals("축의금 챙기기", list[0].memo)
        assertEquals(day, list[0].endDate)
        assertEquals(21 * 60, list[1].endMinute)
        assertEquals(com.simbio.don.cal.Repeat.WEEKLY, list[1].repeat)
        // 여러 날 일정: 시작·끝 날과 이 날
        assertEquals(day.minusDays(1), list[2].startDate)
        assertEquals(day.plusDays(1), list[2].endDate)
        assertTrue(list[2].multiDay)
        // Instances는 그날 0시~다음 날 0시 (폰 시간대)로 묻는다
        val segs = FakeCalendarProvider.instancesUri!!.pathSegments
        assertEquals(day.atStartOfDay(seoul).toInstant().toEpochMilli(), segs[segs.size - 2].toLong())
        assertEquals(day.plusDays(1).atStartOfDay(seoul).toInstant().toEpochMilli(), segs.last().toLong())
    }

    @Test
    fun `일정 넣기 — 하루 종일은 UTC로, 시각은 폰 시간대로 한 시간, 지우기`() {
        val src = DeviceCalendarSource(context.contentResolver)
        val day = LocalDate.of(2026, 10, 28)
        val id = src.insert(1, com.simbio.don.app.calendar.EventDraft("엄마 생일", day, null, memo = "돈't 예산 50,000원"))
        assertEquals(501L, id)
        val allDay = FakeCalendarProvider.inserted[0]
        assertEquals(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals(1, allDay.getAsInteger(CalendarContract.Events.ALL_DAY))
        assertEquals("UTC", allDay.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
        assertEquals("돈't 예산 50,000원", allDay.getAsString(CalendarContract.Events.DESCRIPTION))

        src.insert(1, com.simbio.don.app.calendar.EventDraft("치과", day, 12 * 60, reminder = 30))
        val timed = FakeCalendarProvider.inserted[1]
        val noon = day.atTime(12, 0).atZone(seoul).toInstant().toEpochMilli()
        assertEquals(noon, timed.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals(noon + 3_600_000, timed.getAsLong(CalendarContract.Events.DTEND))
        assertEquals("Asia/Seoul", timed.getAsString(CalendarContract.Events.EVENT_TIMEZONE))
        assertEquals(30, FakeCalendarProvider.reminders.single().getAsInteger(CalendarContract.Reminders.MINUTES))

        // 반복 일정은 끝 대신 길이 (DURATION)
        src.insert(1, com.simbio.don.app.calendar.EventDraft("요가", day, 19 * 60, day, 20 * 60, repeat = com.simbio.don.cal.Repeat.WEEKLY))
        val weekly = FakeCalendarProvider.inserted[2]
        assertEquals("FREQ=WEEKLY", weekly.getAsString(CalendarContract.Events.RRULE))
        assertEquals("PT3600S", weekly.getAsString(CalendarContract.Events.DURATION))
        assertNull(weekly.getAsLong(CalendarContract.Events.DTEND))

        assertTrue(src.delete(501))
        assertEquals(501L, ContentUris.parseId(FakeCalendarProvider.deleted.single()))
    }

    @Test
    fun `연동 — 끄면 아무것도 안 읽고, 켜면 날짜별로, 넣을 곳을 골라야 넣는다`() {
        val fake = FakeCalendarSource()
        val day = LocalDate.of(2026, 10, 28)
        fake.list += CalEvent(1, 1, "저녁 약속", day, 19 * 60, 0)
        fake.list += CalEvent(2, 1, "생일", day, null, 0)
        fake.list += CalEvent(3, 1, "다음 날", day.plusDays(1), 9 * 60, 0)
        // 권한도 가짜도 없으면 켜도 꺼진 것
        PhoneCalendar.setOn(context, true)
        assertTrue(!PhoneCalendar.isOn(context))
        PhoneCalendar.sourceForTests = fake
        PhoneCalendar.setOn(context, false)
        assertTrue(PhoneCalendar.events(context, day, day.plusDays(1)).isEmpty())

        PhoneCalendar.setOn(context, true)
        val byDay = PhoneCalendar.events(context, day, day.plusDays(1))
        // 하루 종일이 먼저, 그다음 시각순
        assertEquals(listOf("생일", "저녁 약속"), byDay.getValue(day).map { it.title })
        assertEquals(listOf("다음 날"), byDay.getValue(day.plusDays(1)).map { it.title })

        assertNull(PhoneCalendar.insert(context, "모임", day, null, 30_000))
        PhoneCalendar.setWriteCalendarId(context, 1)
        val id = PhoneCalendar.insert(context, "모임", day, null, 30_000)!!
        assertEquals("모임", fake.list.single { it.eventId == id }.title)
        assertTrue(PhoneCalendar.update(context, id, com.simbio.don.app.calendar.EventDraft("동창 모임", day, 18 * 60)))
        assertEquals("동창 모임", fake.list.single { it.eventId == id }.title)
        assertTrue(PhoneCalendar.delete(context, id))
        assertTrue(fake.list.none { it.eventId == id })

        // 보일 캘린더만
        PhoneCalendar.setShownIds(context, setOf(2L))
        assertTrue(PhoneCalendar.events(context, day, day.plusDays(1)).isEmpty())
    }
}

/** 0.19: 일정마다 예산 → 돈 나갈 계획, 쓴 뒤 견주기 */
@RunWith(RobolectricTestRunner::class)
class CalendarRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private val today = LocalDate.of(2026, 10, 23)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, today)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `일정 예산은 계획이 되고 분류는 일정 이름으로, 다시 정하면 금액만 바뀐다`() = runTest {
        val id = repo.budgetForEvent(10, "민지 결혼식", d(10, 31), 100_000)
        val p = repo.planNow(id)!!
        assertEquals(Category.EVENT.name, p.category)
        assertEquals(10L, p.eventId)
        assertEquals("민지 결혼식", p.label)
        assertEquals(id, repo.budgetForEvent(10, "민지 결혼식", d(10, 31), 150_000))
        assertEquals(150_000L, repo.planNow(id)!!.amount)
        // 반복 일정의 다른 날은 따로
        val other = repo.budgetForEvent(10, "민지 결혼식", d(11, 7), 50_000)
        assertTrue(other != id)
        assertEquals(2, repo.plans.first().size)
    }

    @Test
    fun `썼어요 뒤에 또 이으면 더한다 (2차)`() = runTest {
        val id = repo.budgetForEvent(11, "팀 회식", d(10, 27), 50_000)
        assertEquals(Category.FOOD.name, repo.planNow(id)!!.category)
        val first = repo.addManual(d(10, 27), 42_000, "고깃집", EntryType.SPEND, Category.FOOD)
        repo.completePlan(id, first, 42_000, d(10, 27))
        val second = repo.addManual(d(10, 27), 18_000, "호프", EntryType.SPEND, Category.FOOD)
        repo.completePlan(id, second, 18_000, d(10, 27))
        val p = repo.planNow(id)!!
        assertEquals(60_000L, p.doneAmount)
        assertEquals(first, p.doneEntryId)
        // 같은 기록을 다시 이으면 (고친 것) 그대로 바꾼다
        repo.completePlan(id, first, 45_000, d(10, 27))
        assertEquals(45_000L, repo.planNow(id)!!.doneAmount)
    }

    @Test
    fun `예산 정한 회식은 식비여도 오늘 몫을 안 넘기고, 남은 일정 예산은 이번 주에서 미리 뺀다`() = runTest {
        val now = LocalDateTime.of(2026, 10, 27, 21, 0)
        val h0 = repo.home(flowOf(now)).first()!!
        val dinner = repo.budgetForEvent(11, "팀 회식", d(10, 27), 50_000)
        val wedding = repo.budgetForEvent(12, "민지 결혼식", d(10, 31), 100_000)
        val h1 = repo.home(flowOf(now)).first()!!
        assertEquals(150_000L, h1.day.reserved)
        assertTrue(h1.day.allowance < h0.day.allowance)

        val e = repo.addManual(d(10, 27), 62_000, "고깃집", EntryType.SPEND, Category.FOOD)
        repo.completePlan(dinner, e, 62_000, d(10, 27))
        repo.addManual(d(10, 27), 9_000, "점심", EntryType.SPEND, Category.FOOD)
        val h2 = repo.home(flowOf(now)).first()!!
        assertEquals(9_000L, h2.day.spentDaily)
        assertEquals(62_000L, h2.day.spentBig)
        assertEquals(100_000L, h2.day.reserved)
        assertTrue(!h2.day.over)
        assertTrue(repo.planNow(wedding)!!.open)
    }
}
