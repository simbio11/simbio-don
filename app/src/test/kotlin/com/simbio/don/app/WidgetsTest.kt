package com.simbio.don.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.PlanEntity
import com.simbio.don.app.widget.BuddyWidget
import com.simbio.don.app.widget.CalendarWidget
import com.simbio.don.app.widget.GrassWidget
import com.simbio.don.app.widget.SavedWidget
import com.simbio.don.app.widget.WidgetArt
import com.simbio.don.app.widget.Widgets
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.GrassLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** 0.14: 위젯 더 — 무지출 잔디·이번 달 달력·모은 돈·내 캐릭터 (기기 안 기록만 읽는다) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ko-rKR-w400dp-h880dp-xhdpi")
class WidgetsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        DonDatabase.inMemoryForTests = true
        DonRepository.resetForTests()
    }

    @After
    fun tearDown() = DonRepository.resetForTests()

    /** 두 달 전부터 쓴 사람: 무지출 날·아껴 쓴 날·넘긴 날이 섞여 있다 */
    private fun seed() = runBlocking {
        val repo = DonRepository.get(context)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000, 1)), 300_000, DayOfWeek.MONDAY, today.minusDays(70))
        for (i in 1..70) {
            val d = today.minusDays(i.toLong())
            val amount = when {
                i % 5 == 0 -> 0L
                i % 7 == 3 -> 95_000L
                i % 3 == 0 -> 8_000L
                else -> 45_000L
            }
            if (amount > 0) repo.addManual(d, amount, if (amount > 50_000) "백화점" else "점심", EntryType.SPEND, Category.FOOD)
        }
        repo.addManual(today, 12_000, "카페", EntryType.SPEND, Category.CAFE)
        repo.savePlan(PlanEntity(epochDay = today.plusDays(3).toEpochDay(), amount = 50_000, label = "친구 생일", category = Category.EVENT.name))
        repo.addGoal("여행 자금", 2_000_000, "flight", today)
        repo
    }

    /** 복잡한 배경화면 흉내: 하늘 그라데이션 + 어두운 전선·건물 + 흰 구름 (투명 바탕 위젯이 읽히는지 보려고) */
    private fun busyWallpaper(c: Canvas, w: Int, h: Int) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2E5E86.toInt(), 0xFF46A3B0.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFE9F2F5.toInt()
        listOf(0.2f to 0.35f, 0.55f to 0.6f, 0.8f to 0.25f, 0.4f to 0.85f).forEach { (x, y) ->
            c.drawOval(w * x - 60f, h * y - 22f, w * x + 70f, h * y + 26f, p)
        }
        p.color = 0xFF2A1E1A.toInt()
        c.drawRect(0f, h * 0.55f, w * 0.38f, h.toFloat(), p)
        p.strokeWidth = 3f
        repeat(4) { i -> c.drawLine(w * (0.25f + i * 0.08f), 0f, w * (0.5f + i * 0.1f), h.toFloat(), p) }
    }

    private fun render(rv: RemoteViews, wDp: Int, hDp: Int, name: String, busy: Boolean = false): View {
        val d = context.resources.displayMetrics.density
        val parent = FrameLayout(context)
        val view = rv.apply(context, parent)
        val w = (wDp * d).toInt()
        val h = (hDp * d).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        System.getProperty("screenshotDir")?.let { dir ->
            val bmp = Bitmap.createBitmap(w + 32, h + 32, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            if (busy) busyWallpaper(c, w + 32, h + 32) else c.drawColor(0xFF8FA3B8.toInt()) // 홈 화면 배경처럼
            c.translate(16f, 16f)
            view.draw(c)
            File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return view
    }

    private fun View.text(id: Int) = (findViewById<TextView>(id)).text.toString()

    /** 위젯 고르기 화면에 보일 미리보기 그림을 res에 다시 만든다 (./gradlew ... -PwidgetSamples) — 꽉 찬 예시 기록으로 */
    @Test
    fun 미리보기_그림() {
        val dir = System.getProperty("widgetSamples") ?: return
        fun save(name: String, bmp: Bitmap) = File(dir).resolve("widget_${name}_sample.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val day = LocalDate.of(2026, 10, 24)
        val rnd = kotlin.random.Random(7)
        val entries = (1..200).mapNotNull { i ->
            val amount = listOf(0L, 0L, 8_000L, 15_000L, 22_000L, 28_000L, 45_000L)[rnd.nextInt(7)]
            if (amount == 0L) null else com.simbio.don.budget.Entry(day.minusDays(i.toLong()), amount, "가게", EntryType.SPEND)
        } + com.simbio.don.budget.Entry(day, 9_000, "카페", EntryType.SPEND)
        val start = LocalDate.of(2026, 10, 1)
        val inMonth = entries.filter { it.date >= start } + com.simbio.don.budget.Entry(LocalDate.of(2026, 10, 10), 3_300_000, "월급", EntryType.INCOME)
        val cal = com.simbio.don.app.widget.CalendarCells(
            start, LocalDate.of(2026, 10, 31), day,
            spent = com.simbio.don.budget.Stats.daily(inMonth, livingOnly = true),
            noSpend = (1..23).map { start.plusDays(it - 1L) }.filter { d -> entries.none { it.date == d } }.toSet(),
            planned = mapOf(LocalDate.of(2026, 10, 28) to 50_000L),
            dailyPlan = 30_000,
            income = com.simbio.don.budget.Stats.dailyIncome(inMonth),
            expense = com.simbio.don.budget.Stats.daily(inMonth),
        )
        val (gw, gh) = GrassWidget.imageSize(330f, 250f)
        val grass = com.simbio.don.budget.Grasses.of(entries, day, WidgetArt.miniWeeks(gw, gh, 0), 30_000, since = day.minusDays(400))
        save("grass", WidgetArt.calendarWithGrass(cal, grass, gw, gh, 2f))
        save("calendar", WidgetArt.calendar(cal, 306f, 256f, 2f))
        val px = com.simbio.don.app.ui.kit.CharacterRenderer.pixels(context, com.simbio.don.game.CharacterLook.MALE, com.simbio.don.game.Mood.HAPPY)
        save("buddy", WidgetArt.pixels(px, com.simbio.don.app.ui.kit.CharacterRenderer.W, com.simbio.don.app.ui.kit.CharacterRenderer.H, 6))
    }

    @Test
    fun 위젯_넷을_그린다() {
        val repo = seed()
        val grassView = render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w1_grass")
        assertTrue(grassView.text(R.id.widget_sub), grassView.text(R.id.widget_sub).isNotBlank())

        // 위젯에 그린 잔디는 기록 그대로
        val home = runBlocking { Widgets.home(context) }!!
        val g = runBlocking { repo.grass(home.today, 10, Widgets.dailyPlan(home)) }
        val byDate = g.days.associateBy { it.date }
        assertEquals(GrassLevel.NO_SPEND, byDate.getValue(today.minusDays(5)).level)
        assertEquals(GrassLevel.OVER, byDate.getValue(today.minusDays(3)).level)
        assertEquals(GrassLevel.LOW, byDate.getValue(today.minusDays(6)).level)
        assertEquals(GrassLevel.LOW, byDate.getValue(today).level)

        val calView = render(runBlocking { CalendarWidget().views(context, 2, 330f, 320f) }, 330, 320, "w2_calendar")
        assertTrue(calView.text(R.id.widget_sub).startsWith("생활비 "))
        assertEquals(LocalDate.ofEpochDay(today.plusDays(3).toEpochDay()), CalendarWidget.cells(home).planned.keys.single())

        val savedView = render(runBlocking { SavedWidget().views(context, 3, 250f, 160f) }, 250, 160, "w3_saved")
        assertEquals("여행 자금 · 200만원", savedView.text(R.id.widget_goal))

        val buddyView = render(runBlocking { BuddyWidget().views(context, 4, 170f, 170f) }, 170, 170, "w4_buddy")
        assertTrue(buddyView.text(R.id.widget_sub).isNotBlank())
    }

    @Test
    fun 잔디_위젯은_기본으로_스택만_깔끔하게() {
        seed()
        assertTrue(!com.simbio.don.app.UiPrefs.grassCalendar(context))
        val wide = render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w1_stack")
        assertTrue(wide.text(R.id.widget_sub).isNotBlank())
        render(runBlocking { GrassWidget().views(context, 1, 250f, 110f) }, 250, 110, "w1_stack_small")
        render(runBlocking { GrassWidget().views(context, 1, 400f, 200f) }, 400, 200, "w1_stack_big")
        // 어둡게 · 투명
        com.simbio.don.app.UiPrefs.setWidgetTheme(context, com.simbio.don.app.ui.ThemeMode.DARK)
        com.simbio.don.app.UiPrefs.setWidgetOpacity(context, 0)
        render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w1_stack_clear", busy = true)
    }

    @Test
    fun 캘린더를_이으면_위젯_달력에_일정_점() {
        seed()
        val home = runBlocking { Widgets.home(context) }!!
        assertTrue(CalendarWidget.cells(home, runBlocking { CalendarWidget.eventColors(context, home) }).events.isEmpty())
        val fake = com.simbio.don.app.calendar.FakeCalendarSource()
        fake.list += com.simbio.don.app.calendar.CalEvent(1, 1, "민지 결혼식", today.plusDays(2), null, 0xFFE67C73.toInt())
        fake.list += com.simbio.don.app.calendar.CalEvent(2, 1, "팀 회식", today, 19 * 60, 0xFF4285F4.toInt())
        com.simbio.don.app.calendar.PhoneCalendar.sourceForTests = fake
        try {
            com.simbio.don.app.calendar.PhoneCalendar.setOn(context, true)
            val cells = CalendarWidget.cells(home, runBlocking { CalendarWidget.eventColors(context, home) })
            assertEquals(listOf(0xFFE67C73.toInt()), cells.events[today.plusDays(2)])
            assertEquals(listOf(0xFF4285F4.toInt()), cells.events[today])
            render(runBlocking { CalendarWidget().views(context, 2, 330f, 320f) }, 330, 320, "w2_calendar_events")
        } finally {
            com.simbio.don.app.calendar.PhoneCalendar.sourceForTests = null
        }
    }

    @Test
    fun 처음_설정_전이면_안내만() {
        val v = render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w0_grass_empty")
        assertEquals("앱을 열어 처음 설정을 해 주세요", v.text(R.id.widget_sub))
        assertEquals("앱을 열어 처음 설정을 해 주세요", render(runBlocking { SavedWidget().views(context, 3, 250f, 160f) }, 250, 160, "w0_saved_empty").text(R.id.widget_sub))
    }

    @Test
    fun 잔디_칸_수는_크기에_맞춘다() {
        assertTrue(WidgetArt.grassWeeks(300f, 120f) in 15..30)
        assertTrue(WidgetArt.grassWeeks(600f, 120f) > WidgetArt.grassWeeks(300f, 120f))
        assertEquals(53, WidgetArt.grassWeeks(3000f, 120f))
        assertEquals(4, WidgetArt.grassWeeks(10f, 120f))
        // 달력 옆 작은 잔디: 고른 기간(자동은 10주), 너비의 38%까지
        assertEquals(10, WidgetArt.miniWeeks(400f, 200f, 0))
        assertEquals(4, WidgetArt.miniWeeks(400f, 200f, 4))
        assertTrue(WidgetArt.miniWeeks(300f, 120f, 26) < 26)
    }
    /** 0.17: 잔디 기간을 고르면 그 주 수만큼만 (위젯이 작으면 들어가는 만큼) */
    @Test
    fun 잔디_기간_고르기() {
        seed()
        assertEquals(0, UiPrefs.grassWeeks(context))
        assertEquals(20, GrassWidget.weeks(20, 0))
        assertEquals(8, GrassWidget.weeks(20, 8))
        assertEquals(20, GrassWidget.weeks(20, 26))
        UiPrefs.setGrassWeeks(context, 8)
        assertEquals(8, UiPrefs.grassWeeks(context))
        render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w11_grass_8weeks")
        UiPrefs.setGrassWeeks(context, 4)
        render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w12_grass_4weeks")
        // 달력 없이, 좁은 위젯
        UiPrefs.setGrassCalendar(context, false)
        render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w13_grass_4weeks_no_calendar")
        UiPrefs.setGrassCalendar(context, true)
        UiPrefs.setGrassWeeks(context, 0)
        render(runBlocking { GrassWidget().views(context, 1, 200f, 170f) }, 200, 170, "w14_grass_narrow")
        render(runBlocking { GrassWidget().views(context, 1, 420f, 200f) }, 420, 200, "w15_grass_wide")
        // 새 기본 크기 (4×3)
        val tall = render(runBlocking { GrassWidget().views(context, 1, 330f, 250f) }, 330, 250, "w16_grass_4x3")
        assertTrue(tall.text(R.id.widget_title).contains("~"))
        // 모르는 값은 크기에 맞춤으로
        UiPrefs.setGrassWeeks(context, 5)
        assertEquals(0, UiPrefs.grassWeeks(context))
        UiPrefs.setGrassWeeks(context, 0)
    }

    /** 0.15: 위젯 색(어둡게)·바탕 투명 — 배경화면 위에 놓인 모습 */
    @Test
    fun 위젯_색과_투명() {
        seed()
        UiPrefs.setWidgetTheme(context, com.simbio.don.app.ui.ThemeMode.DARK)
        UiPrefs.setWidgetOpacity(context, 0)
        val st = com.simbio.don.app.widget.WidgetStyle.of(context)
        assertTrue(st.dark)
        assertEquals(0, st.bgAlpha)
        val grass = render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w5_grass_dark_clear", busy = true)
        assertEquals(st.ink, grass.findViewById<TextView>(R.id.widget_title).currentTextColor)
        render(runBlocking { CalendarWidget().views(context, 2, 330f, 320f) }, 330, 320, "w6_calendar_dark_clear", busy = true)
        // 밝게 + 투명 (글자가 어두워 배경화면에 묻히기 쉬운 경우)
        UiPrefs.setWidgetTheme(context, com.simbio.don.app.ui.ThemeMode.LIGHT)
        render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w5b_grass_light_clear", busy = true)
        render(runBlocking { GrassWidget().views(context, 1, 330f, 250f) }, 330, 250, "w5c_grass_light_clear_4x3", busy = true)
        render(runBlocking { com.simbio.don.app.widget.WeekWidget.render(context, runBlocking { Widgets.home(context) }) }, 250, 130, "w5d_week_light_clear", busy = true)
        render(runBlocking { SavedWidget().views(context, 3, 250f, 160f) }, 250, 160, "w5e_saved_light_clear", busy = true)
        UiPrefs.setWidgetTheme(context, com.simbio.don.app.ui.ThemeMode.DARK)
        UiPrefs.setWidgetOpacity(context, 50)
        val saved = render(runBlocking { SavedWidget().views(context, 3, 250f, 160f) }, 250, 160, "w7_saved_dark_half")
        // 어둡게는 어두운 막대만 보인다
        assertEquals(View.GONE, saved.findViewById<View>(R.id.widget_progress).visibility)
        assertEquals(View.VISIBLE, saved.findViewById<View>(R.id.widget_progress_dark).visibility)
        render(runBlocking { BuddyWidget().views(context, 4, 170f, 170f) }, 170, 170, "w8_buddy_dark_half")
        val home = runBlocking { Widgets.home(context) }
        render(com.simbio.don.app.widget.WeekWidget.render(context, home), 250, 130, "w9_week_dark_half")
        UiPrefs.setWidgetTheme(context, com.simbio.don.app.ui.ThemeMode.LIGHT)
        UiPrefs.setWidgetOpacity(context, 25)
        render(com.simbio.don.app.widget.WeekWidget.render(context, home), 250, 130, "w10_week_light_quarter")
    }
}
