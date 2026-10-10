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

    @Volatile private var watched: Set<String> = emptySet()

    /** 같은 알림이 갱신돼 다시 올 때 두 번 세지 않도록 */
    private val processed = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > 200
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
        val postedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), ZoneId.systemDefault())
        recordSeen(pkg, sbn.postTime, postedAt)

        if (pkg !in watched) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val key = "${sbn.key}|${title.hashCode()}|${text.hashCode()}"
        synchronized(processed) {
            if (processed.containsKey(key)) return
            processed[key] = Unit
        }
        scope.launch { repo.handleNotification(RawNotification(pkg, title, text, postedAt)) }
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
    }
}
