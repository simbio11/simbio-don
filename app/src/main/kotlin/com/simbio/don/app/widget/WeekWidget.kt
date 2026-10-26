package com.simbio.don.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.simbio.don.app.MainActivity
import com.simbio.don.app.R
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.budget.Won
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 홈 화면 위젯: 이번 주 남은 돈 + 오늘 쓸 수 있는 돈 + 지출 입력 버튼. 이 기기 안에서만 읽는다. */
class WeekWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                update(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** 기록이 바뀐 뒤 부른다. 위젯이 없으면 아무것도 안 한다. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (ids(app).isEmpty()) return
            scope.launch { runCatching { update(app) } }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WeekWidget::class.java))

        private suspend fun update(context: Context) {
            val home = DonRepository.get(context).home(flowOf(LocalDateTime.now())).first()
            val views = render(context, home)
            AppWidgetManager.getInstance(context).updateAppWidget(ComponentName(context, WeekWidget::class.java), views)
        }

        internal fun render(context: Context, home: HomeState?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_week)
            val st = WidgetStyle.of(context)
            Widgets.background(v, st)
            Widgets.colors(v, R.id.widget_title to st.inkMuted, R.id.widget_amount to st.ink, R.id.widget_today to st.real, R.id.widget_sub to st.inkMuted)
            Widgets.pills(context, v, st, R.id.widget_title, R.id.widget_amount, R.id.widget_today, R.id.widget_sub)
            val week = home?.currentWeek
            if (home == null || week == null) {
                v.setTextViewText(R.id.widget_amount, "—")
                v.setTextViewText(R.id.widget_today, if (home == null) "앱을 열어 처음 설정을 해 주세요" else "월급을 확인해 주세요")
                Widgets.progress(v, st, 0)
                v.setTextViewText(R.id.widget_sub, "")
            } else {
                v.setTextViewText(R.id.widget_amount, Won.format(week.available))
                v.setTextColor(R.id.widget_amount, if (week.available >= 0) st.ink else st.expense)
                // 오늘: 오늘 몫 − 그날 쓴 돈 (식비·카페 같은 분류). 큰 지출은 이번 주에서 나뉜다
                val day = home.day
                v.setTextViewText(
                    R.id.widget_today,
                    when {
                        week.available <= 0 -> "이번 주 예산을 다 썼어요"
                        day.over -> "오늘 몫을 ${Won.format(-day.left)} 넘었어요"
                        else -> "오늘 남은 돈 ${Won.format(day.left)}"
                    },
                )
                v.setTextColor(R.id.widget_today, if (week.available > 0 && !day.over) st.real else st.expense)
                val pct = if (week.budget <= 0) 0 else ((week.spent * 100) / week.budget).toInt().coerceIn(0, 100)
                Widgets.progress(v, st, pct)
                v.setTextViewText(R.id.widget_sub, "쓴 돈 ${Won.format(week.spent)} / 예산 ${Won.format(week.budget)}")
            }
            v.setOnClickPendingIntent(R.id.widget_root, open(context, null, 0))
            v.setOnClickPendingIntent(R.id.widget_add, open(context, MainActivity.ACTION_ADD, 1))
            return v
        }

        private fun open(context: Context, action: String?, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                this.action = action ?: Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
