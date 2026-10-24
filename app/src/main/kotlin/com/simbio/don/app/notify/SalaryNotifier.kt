package com.simbio.don.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simbio.don.app.MainActivity
import com.simbio.don.app.R
import com.simbio.don.budget.Won

/**
 * 월급 입금 알림으로 이번 달 월급을 바꿨을 때 알려준다 (월급은 달마다 조금씩 다를 수 있어서).
 * 알림 권한이 없으면 조용히 넘어가고, 앱 홈의 안내 카드로만 알린다. 내용은 이 기기 안에서만 만든다.
 */
object SalaryNotifier {
    private const val CHANNEL = "salary"
    private const val NOTIFICATION_ID = 31

    fun message(salary: Long, previous: Long?): Pair<String, String> {
        val diff = previous?.let { salary - it }?.takeIf { it != 0L }
        val title = "월급 ${Won.format(salary)} 들어왔어요"
        val body = "통장에 들어온 금액으로 이번 달 월급을 바꿨어요" +
            (diff?.let { " (지난달보다 ${if (it > 0) "+" else "−"}${Won.format(kotlin.math.abs(it))})" } ?: "") +
            ". 주 예산도 다시 나눴어요."
        return title to body
    }

    fun notify(context: Context, salary: Long, previous: Long?) {
        if (!DailyReminder.canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.salary_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, body) = message(salary, previous)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_payments_fill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }
}
