package com.simbio.don.app.perf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.AccountEntity
import com.simbio.don.app.data.AccountType
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.EntryEntity
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/** 1년치 기록(하루 10건)으로 화면별 계산 시간을 잰다. -Pbench 일 때만. */
@RunWith(RobolectricTestRunner::class)
class PerfBench {
    @Test
    fun bench() = runBlocking {
        Assume.assumeTrue(System.getProperty("bench") != null)
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java).build()
        val repo = DonRepository(db.dao(), db)
        val today = LocalDate.now()
        val start = today.minusDays(365)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 700_000)), 300_000, DayOfWeek.MONDAY, start)
        repo.saveRule(AppRule("card.app", "OO카드"))
        val bank = repo.saveAccount(AccountEntity(name = "생활비 통장", type = AccountType.BANK.name, opening = 3_000_000, sortOrder = 0))
        val card = repo.saveAccount(AccountEntity(name = "OO카드", type = AccountType.CARD.name, opening = 0, sortOrder = 1, packageName = "card.app"))
        val r = Random(1)
        val names = listOf("스타벅스", "GS25", "김밥천국", "쿠팡", "배달의민족", "CU", "버스", "올리브영", "다이소", "맥도날드")
        for (d in 0..365L) for (i in 0 until 10) {
            db.dao().insertEntry(EntryEntity(epochDay = start.toEpochDay() + d, amount = 1000L + r.nextInt(30_000), label = names[r.nextInt(names.size)],
                type = EntryType.SPEND.name, source = EntrySource.MANUAL.name, category = Category.FOOD.name, accountId = if (i % 2 == 0) bank else card, minuteOfDay = 9 * 60 + i * 40))
        }
        val now = LocalDateTime.now()
        fun t(name: String, block: suspend () -> Unit) = runBlocking {
            block() // 데우기
            val s = System.nanoTime(); repeat(3) { block() }; println("BENCH %-28s %6d ms".format(name, (System.nanoTime() - s) / 3_000_000))
        }
        t("query allEntries") { db.dao().allEntriesNow() }
        t("query accountEntries") { db.dao().accountEntries().first() }
        t("dailyUpkeep") { repo.dailyUpkeep(now) }
        t("home") { repo.home(flowOf(now)).first() }
        t("accountBalances") { repo.accountBalances.first() }
        t("wealth") { repo.wealth(flowOf(now)).first() }
        val nominal = repo.home(flowOf(now)).first()!!.let { db.dao().cyclesNow().last().nominal }
        t("month") { repo.month(nominal, today).first() }
        t("year") { repo.year(today.year).first() }
        t("summaries") { repo.summaries(flowOf(now)).first() }
        t("splitBook") { repo.splitBook(MutableStateFlow(emptySet())).first() }
        t("planHints") { repo.planHints(today).first() }
        t("unassigned") { repo.unassignedEntries.first() }
        t("balanceFixEntries") { repo.balanceFixEntries.first() }
        t("search") { repo.search("스타").first() }
        var k = 0
        t("handleNotification") { repo.handleNotification(RawNotification("card.app", "OO카드 승인", "GS25 ${4000 + k++}원 결제", now.minusMinutes(k.toLong()))) }
        db.close()
    }
}
