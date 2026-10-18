package com.simbio.don.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.simbio.don.app.MainActivity
import com.simbio.don.app.R
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.HomeState
import com.simbio.don.app.data.toEntry
import com.simbio.don.budget.Stats
import com.simbio.don.budget.Won
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 매일 밤 알림 (설정에서 켤 때만): 오늘 쓴 돈과 이번 주 남은 돈, 결산이 준비됐으면 결산 안내.
 * 내용은 이 기기 안에서 계산하고 어디로도 보내지 않는다.
 */
object DailyReminder {
    private const val PREFS = "don_prefs"
    private const val KEY_ON = "daily_reminder"
    private const val CHANNEL = "daily"
    private const val NOTIFICATION_ID = 21
    val TIME: LocalTime = LocalTime.of(21, 0)

    fun isOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 다음 21시부터 하루마다 (정확한 시각이 아니어도 되므로 배터리에 순한 방식) */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pending(context))
    }

    private fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, Receiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 알림 문구 (제목, 본문) */
    internal fun message(home: HomeState): Pair<String, String> {
        if (home.settleable.isNotEmpty()) {
            return "지난주 결산이 준비됐어요" to "예산을 지켰는지 보고, 모의로 산 물건을 받아보세요."
        }
        val todaySpent = Stats.livingTotal(home.entries.filter { it.epochDay == home.today.toEpochDay() }.map { it.toEntry() })
        val week = home.currentWeek
        val title = if (todaySpent > 0) "오늘 ${Won.format(todaySpent)} 썼어요" else "오늘은 아직 기록된 지출이 없어요"
        val body = buildString {
            if (week != null) append("이번 주 남은 돈 ${Won.format(week.available)}")
            if (home.reviewCount > 0) append(if (isEmpty()) "" else " · ").append("확인할 결제 알림 ${home.reviewCount}건")
            if (isEmpty()) append("현금으로 쓴 돈이 있으면 적어 두세요.")
        }
        return title to body
    }

    private fun notify(context: Context, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_savings_fill)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!isOn(context)) return
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val repo = DonRepository.get(context.applicationContext)
                    // 앱을 안 켠 날에도 월급날·고정 지출은 챙긴다
                    repo.dailyUpkeep(LocalDateTime.now())
                    val home = repo.home(flowOf(LocalDateTime.now())).first() ?: return@launch
                    val (title, body) = message(home)
                    notify(context, title, body)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    /** 폰을 다시 켜면 알람이 사라지므로 다시 건다 */
    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED && isOn(context)) schedule(context)
        }
    }
}
