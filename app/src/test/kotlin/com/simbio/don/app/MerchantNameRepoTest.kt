package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.budget.Category
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.MerchantSpot
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.19: 알림 기록 이름이 "결제"만 나오던 것 — 못 찾으면 앱 이름, 고치면 그 앱 알림의 가게 이름 자리를 배운다 */
@RunWith(RobolectricTestRunner::class)
class MerchantNameRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository
    private fun at(day: Int, h: Int) = LocalDateTime.of(2026, 10, day, h, 0)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 300_000, DayOfWeek.MONDAY, LocalDate.of(2026, 10, 23))
        repo.saveRule(AppRule("bank.app", "OO뱅크"))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun entry(label: String) = db.dao().allEntriesNow().single { it.label == label }

    @Test
    fun `가게 이름을 못 찾으면 앱 이름, 고치면 자리를 배워 같은 앱 기록도 고친다`() = runTest {
        // 이 앱은 둘째 줄에 가게 이름이 오는데, 흔한 규칙은 첫 낱말(카드 주인 이름)을 뽑는다
        repo.handleNotification(RawNotification("bank.app", "결제", "홍길동님 12,000원 결제\n스타벅스 강남", at(27, 9)))
        repo.handleNotification(RawNotification("bank.app", "결제", "홍길동님 4,500원 결제\n이디야 역삼", at(27, 13)))
        // 가게 이름이 아예 없는 알림
        repo.handleNotification(RawNotification("bank.app", "결제", "체크카드 5,000원 결제되었습니다", at(27, 18)))

        val all = db.dao().allEntriesNow().sortedBy { it.postedAtEpochSecond }
        assertEquals(listOf("홍길동", "홍길동", "OO뱅크 결제"), all.map { it.label })
        assertEquals(listOf(false, false, true), all.map { it.nameUnknown })
        assertEquals("결제\n체크카드 5,000원 결제되었습니다", all[2].rawText)

        // 첫 기록 이름을 고치면 → 배운 자리로 둘째 기록도 고침 (이름 모르는 셋째는 그대로)
        val first = all[0]
        val fixed = repo.updateEntry(first.copy(label = "스타벅스 강남"), first, applyToSameLabel = false)
        assertEquals(1, fixed)
        assertEquals(MerchantSpot(2, null), MerchantSpot.decode(db.dao().appRulesNow().single { it.packageName == "bank.app" }.merchantSpot))
        val second = entry("이디야 역삼")
        assertEquals(Category.CAFE.name, second.category)
        assertTrue(entry("OO뱅크 결제").nameUnknown)

        // 다음 알림부터 그 자리에서
        repo.handleNotification(RawNotification("bank.app", "결제", "홍길동님 9,000원 결제\n김밥천국 역삼", at(28, 12)))
        val next = entry("김밥천국 역삼")
        assertEquals(false, next.nameUnknown)
        assertEquals(Category.FOOD.name, next.category)

        // 이름 모름 기록에 이름을 적으면 표시가 사라진다 (알림 글에 없는 이름이라 배우지는 않음)
        val unknown = entry("OO뱅크 결제")
        assertEquals(0, repo.updateEntry(unknown.copy(label = "편의점"), unknown, applyToSameLabel = false))
        assertEquals(false, entry("편의점").nameUnknown)
        assertEquals(MerchantSpot(2, null), MerchantSpot.decode(db.dao().appRulesNow().single { it.packageName == "bank.app" }.merchantSpot))
    }

    @Test
    fun `알림의 여러 칸을 모아 읽는다 (여러 줄 알림·작은 글)`() {
        val b = android.os.Bundle().apply {
            putCharSequence(android.app.Notification.EXTRA_TITLE, "결제")
            putCharSequence(android.app.Notification.EXTRA_TEXT, "5,000원 결제")
            putCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES, arrayOf("5,000원 결제", "스타벅스 강남"))
            putCharSequence(android.app.Notification.EXTRA_SUB_TEXT, "OO체크카드")
        }
        assertEquals("결제", com.simbio.don.app.notify.PaymentListenerService.titleOf(b))
        // 같은 글은 한 번만, 본문이 먼저
        assertEquals("5,000원 결제\n스타벅스 강남\nOO체크카드", com.simbio.don.app.notify.PaymentListenerService.textOf(b))
    }
}
