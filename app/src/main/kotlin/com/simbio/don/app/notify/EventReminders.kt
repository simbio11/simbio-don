package com.simbio.don.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simbio.don.app.MainActivity
import com.simbio.don.app.R
import com.simbio.don.app.calendar.CalEvent
import com.simbio.don.app.calendar.LocalCalendar
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.ui.eventTimeRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 앱 안 캘린더 일정 알림 (폰 캘린더 일정은 폰 캘린더 앱이 알려 준다). 가장 가까운 알림 하나만 알람으로 걸고,
 * 울리면 그때 알릴 일정을 보여 준 뒤 다음 것을 다시 건다. 배터리에 순한 방식이라 몇 분 늦을 수 있다.
 */
object EventReminders {
    private const val CHANNEL = "events"

    /** [now] 뒤 알릴 시각과 일정 (가까운 순) */
    internal fun upcoming(events: List<CalEvent>, now: LocalDateTime): List<Pair<LocalDateTime, CalEvent>> =
        events.filter { it.reminder != null && it.date == it.startDate }.map { e ->
            val start = e.startDate.atStartOfDay().plusMinutes((e.minuteOfDay ?: 9 * 60).toLong())
            start.minusMinutes(e.reminder!!.toLong()) to e
        }.filter { it.first.isAfter(now.minusMinutes(1)) }.sortedBy { it.first }

    fun reschedule(context: Context) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { scheduleNext(app, LocalDateTime.now()) } }
    }

    private suspend fun scheduleNext(context: Context, now: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val list = LocalCalendar.toEvents(DonRepository.get(context).appEventsNow(), now.toLocalDate(), now.toLocalDate().plusDays(40))
        val next = upcoming(list, now.plusMinutes(1)).firstOrNull()
        if (next == null) {
            am.cancel(pending(context))
            return
        }
        val at = next.first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7, Intent(context, Receiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, e: CalEvent, id: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "일정 알림", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, 8,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar_month)
            .setContentTitle(e.title)
            .setContentText(listOfNotNull(eventTimeRange(e), e.location).joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (DailyReminder.canNotify(context)) NotificationManagerCompat.from(context).notify(1000 + id, n)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext
                    val now = LocalDateTime.now()
                    val today = LocalDate.now()
                    val list = LocalCalendar.toEvents(DonRepository.get(app).appEventsNow(), today, today.plusDays(2))
                    // 지금 알릴 것 (알람이 몇 분 늦을 수 있어 앞뒤 10분)
                    upcoming(list, now.minusMinutes(10)).filter { !it.first.isAfter(now.plusMinutes(2)) }
                        .forEach { (_, e) -> notify(app, e, (e.eventId % 100_000).toInt()) }
                    scheduleNext(app, now.plusMinutes(2))
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
