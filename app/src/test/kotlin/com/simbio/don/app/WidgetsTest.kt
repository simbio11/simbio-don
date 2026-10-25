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

    private fun render(rv: RemoteViews, wDp: Int, hDp: Int, name: String): View {
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
            c.drawColor(0xFF8FA3B8.toInt()) // 홈 화면 배경처럼
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
        val (gw, gh) = GrassWidget.imageSize(330f, 170f)
        val grass = com.simbio.don.budget.Grasses.of(entries, day, WidgetArt.grassWeeks(gw, gh), 30_000, since = day.minusDays(400))
        save("grass", WidgetArt.grass(grass, gw, gh, 2f))
        val start = LocalDate.of(2026, 10, 1)
        val cal = com.simbio.don.app.widget.CalendarCells(
            start, LocalDate.of(2026, 10, 31), day,
            spent = com.simbio.don.budget.Stats.daily(entries.filter { it.date >= start }, livingOnly = true),
            noSpend = (1..23).map { start.plusDays(it - 1L) }.filter { d -> entries.none { it.date == d } }.toSet(),
            planned = mapOf(LocalDate.of(2026, 10, 28) to 50_000L),
            dailyPlan = 30_000,
        )
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
        val grass = render(runBlocking { GrassWidget().views(context, 1, 330f, 170f) }, 330, 170, "w5_grass_dark_clear")
        assertEquals(st.ink, grass.findViewById<TextView>(R.id.widget_title).currentTextColor)
        render(runBlocking { CalendarWidget().views(context, 2, 330f, 320f) }, 330, 320, "w6_calendar_dark_clear")
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
