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
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Won
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 사고 싶은 것을 7일 참은 날 아침에 "아직도 사고 싶나요?"를 알린다 (밤 알림을 꺼 둬도).
 * 하루 한 번 깨워서 그날 된 것을 모아 보낸다. 알림 글은 이 폰 안에서만 만든다.
 */
object WishReminders {
    private const val CHANNEL = "wish"
    private const val NOTIFICATION_ID = 31
    private val AT: LocalTime = LocalTime.of(10, 0)

    /** 다음 날 10시에 깨운다 (기다리는 것이 있을 때만 부른다) */
    fun schedule(context: Context, day: LocalDate) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = day.atTime(AT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.set(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7, Intent(context, Receiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 가장 이른 기다리는 날로 다시 건다 */
    suspend fun reschedule(context: Context) {
        val repo = DonRepository.get(context.applicationContext)
        val next = repo.wishesNow().filter { it.status == "WAITING" && !it.notified }.minOfOrNull { it.remindEpochDay } ?: return
        schedule(context, LocalDate.ofEpochDay(next).coerceAtLeast(LocalDate.now()))
    }

    internal fun text(names: List<Pair<String, Long>>): Pair<String, String> {
        val first = names.first()
        val title = if (names.size == 1) "7일 참았어요: ${first.first}" else "7일 참은 것 ${names.size}개"
        val body = "아직도 사고 싶나요? " + names.joinToString(" · ") { "${it.first} ${Won.format(it.second)}" } + " — 안 사면 그대로 아낀 돈이에요."
        return title to body
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val repo = DonRepository.get(context.applicationContext)
                    val due = repo.takeDueWishes()
                    if (due.isNotEmpty()) {
                        val (title, body) = text(due.map { it.name to it.price })
                        val nm = context.getSystemService(NotificationManager::class.java)
                        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "사고 싶은 것 7일 참기", NotificationManager.IMPORTANCE_DEFAULT))
                        val open = PendingIntent.getActivity(
                            context, 8,
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            PendingIntent.FLAG_IMMUTABLE,
                        )
                        val n = NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_savings_fill)
                            .setContentTitle(title)
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build()
                        if (DailyReminder.canNotify(context)) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
                    }
                    reschedule(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
