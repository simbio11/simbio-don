package com.simbio.don.app.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.simbio.don.app.data.DonRepository
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.simbio.don.app.widget.WeekWidget
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 결제 알림 수집.
 *
 * 알림 접근 권한은 모든 알림을 볼 수 있지만, 사용자가 고른 앱이 아니면 본문을 열지 않는다.
 * 고르지 않은 앱은 패키지 이름만 기록해 설정 화면의 "알림 보낸 앱" 목록에 쓴다.
 * 모든 처리는 기기 안에서만 하고 외부로 보내지 않는다.
 */
class PaymentListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo by lazy { DonRepository.get(applicationContext) }

    /** 아직 규칙을 못 읽었으면 null — 그동안 온 알림은 DB에서 직접 확인한다 */
    @Volatile private var watched: Set<String>? = null

    /**
     * 같은 알림이 갱신돼 다시 올 때 두 번 세지 않도록 (알림 키 + 알림 시각 + 내용). 10분 지나면 잊는다 —
     * 같은 알림 칸을 다시 쓰는 앱에서 같은 금액을 또 결제한 건 새로 센다.
     */
    private val processed = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 200
    }
    private val lastSeenWrite = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            repo.appRules.collectLatest { rules -> watched = rules.filter { it.enabled }.map { it.packageName }.toSet() }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        // 알림 시각: 앱이 정한 when (갱신돼 다시 올라와도 그대로). 비었거나 올라온 시각과 한 시간 넘게
        // 다르면(다른 뜻으로 쓰는 앱) 올라온 시각
        val time = n.`when`.takeIf { it > 0 && kotlin.math.abs(it - sbn.postTime) <= WHEN_TOLERANCE_MS } ?: sbn.postTime
        val postedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
        recordSeen(pkg, sbn.postTime, postedAt)

        watched?.let { if (pkg !in it) return }
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val key = "${sbn.key}|$time|${title.hashCode()}|${text.hashCode()}"
        val now = System.currentTimeMillis()
        synchronized(processed) {
            val at = processed[key]
            if (at != null && now - at < DEDUP_MS) return
            processed[key] = now
        }
        scope.launch {
            if (watched == null && pkg !in repo.watchedPackages()) return@launch
            val before = repo.salarySnapshot()
            repo.handleNotification(RawNotification(pkg, title, text, postedAt))
            WeekWidget.refresh(this@PaymentListenerService)
            // 입금 알림으로 월급이 바뀌었으면 알려준다
            val after = repo.salarySnapshot()
            if (after != null && after != before && after.third == com.simbio.don.app.data.SalarySource.NOTIFICATION) {
                val previous = if (before?.first == after.first) before.second.takeIf { before.third != com.simbio.don.app.data.SalarySource.ESTIMATED } ?: repo.previousSalary() else repo.previousSalary()
                SalaryNotifier.notify(this@PaymentListenerService, after.second, previous)
            }
        }
    }

    private fun recordSeen(pkg: String, postTime: Long, at: LocalDateTime) {
        val last = synchronized(lastSeenWrite) { lastSeenWrite[pkg] }
        if (last != null && postTime - last < SEEN_THROTTLE_MS) return
        synchronized(lastSeenWrite) { lastSeenWrite[pkg] = postTime }
        scope.launch { repo.markSeen(pkg, at) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SEEN_THROTTLE_MS = 60 * 60 * 1000L
        private const val DEDUP_MS = 10 * 60 * 1000L
        private const val WHEN_TOLERANCE_MS = 60 * 60 * 1000L
    }
}
