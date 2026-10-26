package com.simbio.don.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.simbio.don.app.MainActivity
import com.simbio.don.app.R
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.Summaries
import com.simbio.don.app.data.toEntry
import com.simbio.don.app.ui.compactWon
import com.simbio.don.app.ui.kit.CharacterRenderer
import com.simbio.don.budget.Grass
import com.simbio.don.budget.Stats
import com.simbio.don.budget.Won
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** 고를 수 있는 위젯 (설정에서 홈 화면에 바로 놓기) */
enum class WidgetKind(val label: String, val description: String, val cls: Class<*>) {
    WEEK("이번 주 남은 돈", "남은 돈과 오늘 쓸 수 있는 돈, + 버튼으로 바로 적기", WeekWidget::class.java),
    GRASS("달력 + 잔디", "큰 달력(날마다 수입·지출, 바탕은 그날 잔디 색)과 오른쪽 아래 작은 잔디", GrassWidget::class.java),
    CALENDAR("이번 달 달력", "날마다 수입·지출·다가올 계획, + 버튼으로 바로 적기", CalendarWidget::class.java),
    SAVED("모은 돈", "지금까지 모은 돈과 목표까지 남은 돈", SavedWidget::class.java),
    BUDDY("내 캐릭터", "이번 주 씀씀이에 따라 표정이 바뀌어요", BuddyWidget::class.java),
}

/** 위젯 공통: 기록이 바뀌면 다시 그리고, 누르면 앱의 그 화면을 연다. 모두 이 기기 안에서만 읽는다. */
object Widgets {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 기록이 바뀐 뒤 부른다. 홈 화면에 없는 위젯은 건너뛴다. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        WeekWidget.refresh(app)
        listOf(GrassWidget(), CalendarWidget(), SavedWidget(), BuddyWidget()).forEach { w ->
            val ids = ids(app, w.javaClass)
            if (ids.isNotEmpty()) scope.launch { runCatching { w.update(app, ids) } }
        }
    }

    fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    fun open(context: Context, action: String?, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action ?: Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** 세로 화면 기준 위젯 크기 (dp). 모르면 [fallback] */
    fun sizeDp(options: Bundle?, fallback: Pair<Float, Float>): Pair<Float, Float> {
        val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return if (w > 0 && h > 0) w.toFloat() to h.toFloat() else fallback
    }

    /** 앱을 켤 때처럼 월급날이 지났으면 새 달을 시작하고 읽는다 (앱을 한동안 안 열어도 위젯은 이번 달로) */
    internal suspend fun home(context: Context): HomeState? {
        val repo = DonRepository.get(context)
        val now = LocalDateTime.now()
        runCatching { repo.dailyUpkeep(now) }
        return repo.home(flowOf(now)).first()
    }

    /** 위젯 바탕 색·투명도 (설정의 위젯 색) */
    fun background(v: RemoteViews, st: WidgetStyle, color: Int = st.bg) {
        v.setInt(R.id.widget_bg, "setColorFilter", color)
        v.setInt(R.id.widget_bg, "setImageAlpha", st.bgAlpha)
    }

    /**
     * 바탕을 비치게 했으면 글자 뒤에 반투명 알약을 깔아 배경화면 위에서도 읽히게 한다.
     * (꽉 찬 바탕이면 알약 없이)
     */
    fun pills(context: Context, v: RemoteViews, st: WidgetStyle, vararg ids: Int) {
        val d = context.resources.displayMetrics.density
        ids.forEach { id ->
            if (st.clear) {
                v.setInt(id, "setBackgroundResource", if (st.dark) R.drawable.widget_scrim_dark else R.drawable.widget_scrim_light)
                v.setViewPadding(id, (8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
            } else {
                v.setInt(id, "setBackgroundResource", 0)
                v.setViewPadding(id, 0, 0, 0, 0)
            }
        }
    }

    /** 글자색 여럿 (view id to 색) */
    fun colors(v: RemoteViews, vararg pairs: Pair<Int, Int>) = pairs.forEach { (id, c) -> v.setTextColor(id, c) }

    /** 막대: 밝게·어둡게 막대 중 하나만 보이게 */
    fun progress(v: RemoteViews, st: WidgetStyle, pct: Int) {
        v.setViewVisibility(R.id.widget_progress, if (st.dark) android.view.View.GONE else android.view.View.VISIBLE)
        v.setViewVisibility(R.id.widget_progress_dark, if (st.dark) android.view.View.VISIBLE else android.view.View.GONE)
        v.setProgressBar(if (st.dark) R.id.widget_progress_dark else R.id.widget_progress, 100, pct, false)
    }

    /** 하루 몫: 이번 달 쓸 돈 ÷ 날 수 */
    fun dailyPlan(home: HomeState): Long = home.plan.disposable / home.cycle.days.coerceAtLeast(1)
}

/** 위젯 하나: 크기에 맞춰 그림을 그린다 */
abstract class DonWidget : AppWidgetProvider() {
    abstract suspend fun views(context: Context, id: Int, widthDp: Float, heightDp: Float): RemoteViews
    abstract val fallbackSize: Pair<Float, Float>

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = launch(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle?) =
        launch(context, intArrayOf(id))

    private fun launch(context: Context, ids: IntArray) {
        val pending = goAsync()
        Widgets.scope.launch {
            try {
                update(context.applicationContext, ids)
            } finally {
                pending.finish()
            }
        }
    }

    suspend fun update(context: Context, ids: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        ids.forEach { id ->
            val (w, h) = Widgets.sizeDp(manager.getAppWidgetOptions(id), fallbackSize)
            runCatching { manager.updateAppWidget(id, views(context, id, w, h)) }
        }
    }
}

/** 무지출 잔디: 깃허브 잔디처럼 최근 몇 달 — 안 쓴 날일수록 진한 초록, 하루 몫을 넘긴 날은 빨강 */
class GrassWidget : DonWidget() {
    override val fallbackSize = 320f to 160f

    override suspend fun views(context: Context, id: Int, widthDp: Float, heightDp: Float): RemoteViews {
        val home = Widgets.home(context)
        val v = RemoteViews(context.packageName, R.layout.widget_grass)
        val st = WidgetStyle.of(context)
        Widgets.background(v, st)
        Widgets.colors(v, R.id.widget_title to st.ink, R.id.widget_sub to st.good)
        v.setInt(R.id.widget_sub, "setBackgroundResource", if (st.dark) R.drawable.widget_chip_good_dark else R.drawable.widget_chip_good)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, MainActivity.ACTION_CALENDAR, 10))
        if (home == null) {
            v.setTextViewText(R.id.widget_sub, "앱을 열어 처음 설정을 해 주세요")
            return v
        }
        val (gw, gh) = imageSize(widthDp, heightDp)
        val density = context.resources.displayMetrics.density
        val repo = DonRepository.get(context)
        val plan = Widgets.dailyPlan(home)
        val chosen = com.simbio.don.app.UiPrefs.grassWeeks(context)
        if (com.simbio.don.app.UiPrefs.grassCalendar(context)) {
            // 큰 달력 + (넓으면) 오른쪽 위 이번 달 합계, 오른쪽 아래 작은 잔디
            val cal = CalendarWidget.cells(home)
            v.setTextViewText(R.id.widget_title, CalendarWidget.range(home))
            Widgets.pills(context, v, st, R.id.widget_title)
            if (gw >= WidgetArt.CALENDAR_MIN_WIDTH) {
                val g = repo.grass(home.today, WidgetArt.miniWeeks(gw, gh, chosen), plan)
                v.setTextViewText(R.id.widget_sub, subtitle(g))
                v.setImageViewBitmap(R.id.widget_image, WidgetArt.calendarWithGrass(cal, g, gw, gh, density, st))
            } else {
                v.setTextViewText(R.id.widget_sub, subtitle(repo.grass(home.today, 4, plan)))
                v.setImageViewBitmap(R.id.widget_image, WidgetArt.calendar(cal, gw, gh, density, st))
            }
            return v
        }
        val g = repo.grass(home.today, weeks(WidgetArt.grassWeeks(gw, gh), chosen), plan)
        v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_grass_title))
        Widgets.pills(context, v, st, R.id.widget_title)
        v.setTextViewText(R.id.widget_sub, subtitle(g))
        v.setImageViewBitmap(R.id.widget_image, WidgetArt.grass(g, gw, gh, density, st))
        return v
    }

    companion object {
        /** 바깥 여백 14dp씩, 제목 줄 22dp */
        fun imageSize(widthDp: Float, heightDp: Float) = (widthDp - 28f).coerceAtLeast(120f) to (heightDp - 28f - 24f).coerceAtLeast(70f)

        /** 고른 기간 (0이면 크기에 맞춤). 위젯이 작으면 들어가는 만큼만 */
        fun weeks(fit: Int, chosen: Int): Int = if (chosen <= 0) fit else minOf(chosen, fit)

        fun subtitle(g: Grass): String = when {
            g.streak >= 1 -> "무지출 ${g.streak}일째"
            g.kept > 0 -> "예산 지킨 날 ${g.kept}일"
            else -> "오늘부터 한 칸씩"
        }
    }
}

/** 이번 달 달력: 날마다 쓴 돈, 무지출 도장, 다가올 계획 */
class CalendarWidget : DonWidget() {
    override val fallbackSize = 320f to 300f

    override suspend fun views(context: Context, id: Int, widthDp: Float, heightDp: Float): RemoteViews {
        val home = Widgets.home(context)
        val v = RemoteViews(context.packageName, R.layout.widget_calendar)
        val st = WidgetStyle.of(context)
        Widgets.background(v, st)
        Widgets.colors(v, R.id.widget_title to st.ink, R.id.widget_sub to st.inkMuted)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, MainActivity.ACTION_CALENDAR, 11))
        v.setOnClickPendingIntent(R.id.widget_add, Widgets.open(context, MainActivity.ACTION_ADD, 12))
        if (home == null) {
            v.setTextViewText(R.id.widget_title, "이번 달 달력")
            v.setTextViewText(R.id.widget_sub, "앱을 열어 처음 설정을 해 주세요")
            return v
        }
        val cal = cells(home)
        v.setTextViewText(R.id.widget_title, range(home))
        Widgets.pills(context, v, st, R.id.widget_title, R.id.widget_sub)
        v.setTextViewText(R.id.widget_sub, "생활비 ${compactWon(home.monthLiving)}원 · 무지출 ${cal.noSpend.size}일")
        val w = (widthDp - 24f).coerceAtLeast(150f)
        val h = (heightDp - 24f - 40f).coerceAtLeast(110f)
        v.setImageViewBitmap(R.id.widget_image, WidgetArt.calendar(cal, w, h, context.resources.displayMetrics.density, st))
        return v
    }

    companion object {
        /** "10/23 ~ 11/24" */
        fun range(home: HomeState) = "${home.cycle.start.monthValue}/${home.cycle.start.dayOfMonth} ~ ${home.cycle.end.monthValue}/${home.cycle.end.dayOfMonth}"

        fun cells(home: HomeState): CalendarCells {
            val entries = home.entries.mapNotNull { runCatching { it.toEntry() }.getOrNull() }
            return CalendarCells(
            start = home.cycle.start,
            end = home.cycle.end,
            today = home.today,
            spent = Stats.daily(entries, livingOnly = true),
            noSpend = home.insights.noSpend?.days.orEmpty(),
            planned = home.plans.filter { it.open }.groupBy { LocalDate.ofEpochDay(it.epochDay) }.mapValues { (_, l) -> l.sumOf { it.amount } },
            dailyPlan = Widgets.dailyPlan(home),
            income = Stats.dailyIncome(entries),
            expense = Stats.daily(entries),
            )
        }
    }
}

/** 모은 돈: 지금까지 모은 돈, 이번 달 아낀 돈, 목표까지 */
class SavedWidget : DonWidget() {
    override val fallbackSize = 250f to 160f

    override suspend fun views(context: Context, id: Int, widthDp: Float, heightDp: Float): RemoteViews {
        val home = Widgets.home(context)
        val v = RemoteViews(context.packageName, R.layout.widget_saved)
        val st = WidgetStyle.of(context)
        Widgets.background(v, st)
        Widgets.colors(
            v,
            R.id.widget_label to st.inkMuted,
            R.id.widget_amount to st.ink,
            R.id.widget_sub to st.good,
            R.id.widget_goal to st.inkSoft,
            R.id.widget_goal_pct to st.good,
        )
        Widgets.pills(context, v, st, R.id.widget_label, R.id.widget_amount, R.id.widget_sub, R.id.widget_goal_row)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, MainActivity.ACTION_ASSETS, 13))
        if (home == null) {
            v.setTextViewText(R.id.widget_amount, "—")
            v.setTextViewText(R.id.widget_sub, "앱을 열어 처음 설정을 해 주세요")
            return v
        }
        val s = summary(home)
        v.setTextViewText(R.id.widget_amount, Won.format(s.headline))
        v.setTextViewText(R.id.widget_sub, s.sub)
        v.setTextViewText(R.id.widget_goal, s.goalLabel)
        v.setTextViewText(R.id.widget_goal_pct, "${s.pct}%")
        Widgets.progress(v, st, s.pct)
        return v
    }

    data class Summary(val headline: Long, val sub: String, val goalLabel: String, val pct: Int)

    companion object {
        /**
         * 모은 돈 모두(저축 + 목표 + 안 넣은 아낀 돈), 아래 줄에 저축·목표를 나눠서.
         * 막대는 목표가 있으면 그 목표, 없으면 다음 고개(100만·500만…)까지.
         */
        fun summary(home: HomeState): Summary {
            val sp = home.split
            val headline = home.savedTotal
            val sub = buildList {
                add("저축 ${compactWon(sp.savings)}")
                add("목표 ${compactWon(sp.goals)}")
                if (sp.unallocated > 0) add("아낀 돈 ${compactWon(sp.unallocated)}")
            }.joinToString(" · ")
            val goal = home.goals.firstOrNull { !it.done } ?: home.goals.firstOrNull()
            if (goal != null) {
                return Summary(headline, sub, "${goal.goal.name} · ${compactWon(goal.goal.target)}원", (goal.ratio * 100).toInt())
            }
            val next = Summaries.nextMilestone(headline)
            val prev = Summaries.reachedMilestone(headline) ?: 0
            return if (next == null) {
                Summary(headline, sub, "목표를 세워 보세요", 100)
            } else {
                Summary(headline, sub, "${compactWon(next)}원까지 ${compactWon(next - headline)}원", ((headline - prev) * 100 / (next - prev)).toInt().coerceIn(0, 100))
            }
        }
    }
}

/** 내 캐릭터: 이번 주 씀씀이에 따라 표정이 바뀌고 오늘 쓸 수 있는 돈을 말해 준다 */
class BuddyWidget : DonWidget() {
    override val fallbackSize = 160f to 160f

    override suspend fun views(context: Context, id: Int, widthDp: Float, heightDp: Float): RemoteViews {
        val home = Widgets.home(context)
        val v = RemoteViews(context.packageName, R.layout.widget_buddy)
        val st = WidgetStyle.of(context)
        Widgets.background(v, st, st.buddyBg)
        Widgets.colors(v, R.id.widget_title to st.ink, R.id.widget_sub to st.game)
        Widgets.pills(context, v, st, R.id.widget_title, R.id.widget_sub)
        v.setOnClickPendingIntent(R.id.widget_root, Widgets.open(context, null, 14))
        v.setOnClickPendingIntent(R.id.widget_add, Widgets.open(context, MainActivity.ACTION_ADD, 15))
        if (home == null) {
            v.setTextViewText(R.id.widget_title, "안녕!")
            v.setTextViewText(R.id.widget_sub, "앱을 열어 처음 설정을 해 주세요")
            return v
        }
        val px = CharacterRenderer.pixels(context, home.look, home.mood)
        v.setImageViewBitmap(R.id.widget_image, WidgetArt.pixels(px, CharacterRenderer.W, CharacterRenderer.H, 8))
        val (title, sub) = speech(home)
        v.setTextViewText(R.id.widget_title, title)
        v.setTextViewText(R.id.widget_sub, sub)
        return v
    }

    companion object {
        fun speech(home: HomeState): Pair<String, String> {
            val week = home.currentWeek ?: return home.mood.label to "월급을 확인해 주세요"
            return when {
                week.available <= 0 -> home.mood.label to "이번 주 예산을 다 썼어요"
                home.day.over -> home.mood.label to "오늘 몫 ${compactWon(-home.day.left)}원 넘음"
                home.insights.noSpend?.streak?.let { it >= 2 } == true -> "무지출 ${home.insights.noSpend!!.streak}일째!" to "오늘 ${compactWon(home.day.left)}원 남음"
                else -> home.mood.label to "오늘 ${compactWon(home.day.left)}원 남음"
            }
        }
    }
}
