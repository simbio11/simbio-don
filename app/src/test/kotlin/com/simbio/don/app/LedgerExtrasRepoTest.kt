package com.simbio.don.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.simbio.don.app.data.DonDatabase
import com.simbio.don.app.data.DonRepository
import com.simbio.don.app.data.RecurringIncomeEntity
import com.simbio.don.app.lock.AppLock
import com.simbio.don.budget.Category
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.RawNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 0.10 가계부 더: 소분류 · 직접 만든 카테고리 · 할부 · 반복 수입 · 연간 통계 · 앱 잠금 */
@RunWith(RobolectricTestRunner::class)
class LedgerExtrasRepoTest {
    private lateinit var db: DonDatabase
    private lateinit var repo: DonRepository

    // 2026-10-23(금) ~ 11/24
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(month: Int, day: Int, h: Int = 12, m: Int = 0, s: Int = 0) = LocalDateTime.of(2026, month, day, h, m, s)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DonDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DonRepository(db.dao(), db)
        repo.completeOnboarding(3_300_000, 25, listOf(FixedExpense("월세", 1_000_000)), 320_000, DayOfWeek.MONDAY, today)
        repo.saveRule(AppRule("card.app", "OO카드"))
        repo.saveRule(AppRule("bank.app", "OO은행"))
    }

    @After
    fun tearDown() {
        db.close()
        Category.register(emptyList())
    }

    private suspend fun entries() = db.dao().allEntriesNow()

    @Test
    fun `처음 설정하면 기본 소분류가 들어가고 지운 것은 다시 안 들어간다`() = runTest {
        val subs = repo.subcategories.first()
        assertTrue(subs.getValue("FOOD").map { it.name }.containsAll(listOf("아침", "점심", "저녁")))
        val lunch = subs.getValue("FOOD").first { it.name == "점심" }
        repo.deleteSubcategory(lunch)
        repo.dailyUpkeep(at(10, 24))
        assertTrue(repo.subcategories.first().getValue("FOOD").none { it.name == "점심" })
    }

    @Test
    fun `소분류 이름을 바꾸거나 지우면 기록도 따라간다`() = runTest {
        repo.addManual(d(10, 24), 9_000, "김밥", EntryType.SPEND, Category.FOOD, sub = "점심")
        assertEquals("점심", entries().single().sub)
        val lunch = repo.subcategories.first().getValue("FOOD").first { it.name == "점심" }
        repo.renameSubcategory(lunch, "점심밥")
        assertEquals("점심밥", entries().single().sub)
        repo.deleteSubcategory(lunch.copy(name = "점심밥"))
        assertNull(entries().single().sub)
        assertEquals("FOOD", entries().single().category)
        // 이체에는 소분류를 안 붙인다
        repo.addManual(d(10, 24), 10_000, "", EntryType.TRANSFER, sub = "점심")
        assertNull(entries().last().sub)
    }

    @Test
    fun `카테고리를 직접 만들고 지우면 기록은 기타로 간다`() = runTest {
        val pet = repo.addCategory("반려동물", income = false, icon = "pets_fill", color = 0xFFFF8A3D)
        assertEquals("U1", pet.name)
        assertEquals("반려동물", Category.of("U1").label)
        assertTrue(pet in Category.spending)
        repo.addManual(d(10, 24), 30_000, "사료", EntryType.SPEND, pet)
        repo.addSubcategory(pet, "간식")
        repo.saveCategoryBudgets(mapOf(pet to 100_000))
        // 같은 가게는 다음부터 이 분류로
        assertEquals(pet, repo.learned()["사료"])
        val month = repo.month("2026-10", d(10, 24)).first()!!
        assertEquals(pet, month.entries.single().category)

        val second = repo.addCategory("임대 수입", income = true, icon = "home_fill", color = 0xFF346AFF)
        assertEquals("U2", second.name)
        repo.deleteCategory("U1")
        assertEquals("ETC", entries().single().category)
        assertTrue(repo.subcategories.first()["U1"].isNullOrEmpty())
        assertTrue(Category.spending.none { it.name == "U1" })
        assertTrue(db.dao().categoryRulesNow().none { it.category == "U1" })
    }

    @Test
    fun `할부는 달마다 나눠 적고 한 번에 지운다`() = runTest {
        val id = repo.addInstallment(d(10, 27), 1_000_000, 3, "노트북", Category.SHOPPING, sub = "전자기기")
        val es = entries().sortedBy { it.epochDay }
        assertEquals(listOf(d(10, 27), d(11, 27), d(12, 27)).map { it.toEpochDay() }, es.map { it.epochDay })
        assertEquals(listOf(333_334L, 333_333L, 333_333L), es.map { it.amount })
        assertEquals(listOf("1/3", "2/3", "3/3"), es.map { it.installmentNo })
        assertTrue(es.all { it.installmentId == id && it.sub == "전자기기" && it.type == EntryType.SPEND.name })
        // 이번 달에는 첫 달 몫만
        val month = repo.month("2026-10", d(10, 27)).first()!!
        assertEquals(333_334L, month.entries.sumOf { it.amount })
        repo.deleteInstallment(id)
        assertTrue(entries().isEmpty())
    }

    @Test
    fun `할부 결제 알림은 나눠 적고 같은 알림이 다시 와도 한 번만`() = runTest {
        val n = RawNotification("card.app", "OO카드 승인", "OO카드 승인 1,200,000원 03개월 10/27 14:02 하이마트", at(10, 27, 14, 2))
        repo.handleNotification(n)
        repo.handleNotification(n)
        val es = entries()
        assertEquals(3, es.size)
        assertEquals(400_000L, es.first { it.installmentNo == "1/3" }.amount)
        assertEquals(EntrySource.NOTIFICATION.name, es.first { it.installmentNo == "1/3" }.source)
        assertEquals("card.app", es.first { it.installmentNo == "1/3" }.packageName)
        // 일시불은 그대로
        repo.handleNotification(RawNotification("card.app", "OO카드 승인", "OO카드 승인 45,000원 일시불 스타벅스", at(10, 27, 15)))
        assertEquals(4, entries().size)
    }

    @Test
    fun `반복 수입은 정한 날 적히고 입금 알림이 오면 실제 금액으로 고친다`() = runTest {
        repo.saveRecurringIncomes(listOf(RecurringIncomeEntity(name = "과외비", amount = 400_000, day = 27, category = Category.SIDE_INCOME.name)), today)
        assertTrue(entries().none { it.type == EntryType.INCOME.name })
        repo.dailyUpkeep(at(10, 27, 9))
        val auto = entries().single { it.type == EntryType.INCOME.name }
        assertEquals(400_000L, auto.amount)
        assertEquals(EntrySource.RECURRING.name, auto.source)
        assertEquals("SIDE_INCOME", auto.category)
        // 하루 지나 다시 돌아도 또 적지 않는다
        repo.dailyUpkeep(at(10, 28, 9))
        assertEquals(1, entries().count { it.type == EntryType.INCOME.name })

        repo.handleNotification(RawNotification("bank.app", "OO은행", "입금 410,000원 과외비 김OO", at(10, 28, 10)))
        val fixed = entries().single { it.type == EntryType.INCOME.name }
        assertEquals(410_000L, fixed.amount)
        assertEquals(EntrySource.NOTIFICATION.name, fixed.source)
        assertEquals(auto.id, fixed.id)
        // 월급은 그대로
        assertEquals(3_300_000L, db.dao().latestCycleNow()!!.salary)
    }

    @Test
    fun `이미 지난 날로 넣은 반복 수입은 이번 달엔 안 적는다`() = runTest {
        repo.saveRecurringIncomes(listOf(RecurringIncomeEntity(name = "용돈", amount = 200_000, day = 23, category = Category.ALLOWANCE.name)), today)
        repo.dailyUpkeep(at(10, 24))
        assertTrue(entries().none { it.type == EntryType.INCOME.name })
        // 지우면 목록에서 빠진다
        repo.saveRecurringIncomes(emptyList(), today)
        assertTrue(repo.recurringIncomes.first().isEmpty())
    }

    @Test
    fun `연간 통계는 그 해 달마다 수입과 지출을 모은다`() = runTest {
        repo.addManual(d(10, 24), 50_000, "장보기", EntryType.SPEND, Category.FOOD)
        repo.addManual(d(10, 25), 100_000, "중고 판매", EntryType.INCOME, Category.SIDE_INCOME)
        val y = repo.year(2026).first()!!
        assertEquals(listOf(2026), y.years)
        assertEquals(1, y.months.size)
        assertEquals(10, y.months.single().nominal.monthValue)
        assertEquals(100_000L, y.income)
        assertEquals(50_000L, y.expense)
        assertTrue(repo.year(2025).first()!!.months.isEmpty())
    }

    @Test
    fun `백업에 직접 만든 카테고리와 소분류·할부·반복 수입이 들어간다`() = runTest {
        val pet = repo.addCategory("반려동물", false, "pets_fill", 0xFFFF8A3D)
        repo.addSubcategory(pet, "병원")
        repo.addInstallment(d(10, 27), 300_000, 3, "가방", Category.SHOPPING)
        repo.saveRecurringIncomes(listOf(RecurringIncomeEntity(name = "과외비", amount = 400_000, day = 27)), today)
        val json = repo.exportBackup(at(10, 27))
        val before = entries()

        db.clearAllTables()
        Category.register(emptyList())
        repo.restoreBackup(json)
        assertEquals(before, entries())
        assertEquals(listOf("반려동물"), db.dao().categoryDefsNow().map { it.label })
        assertEquals("반려동물", Category.of("U1").label)
        assertTrue(db.dao().subcategoriesNow().any { it.category == "U1" && it.name == "병원" })
        assertEquals(1, db.dao().allInstallments().size)
        assertEquals(listOf("과외비"), db.dao().recurringIncomesNow().map { it.name })
        assertTrue(db.dao().settingsNow()!!.subSeeded)
    }

    @Test
    fun `앱 잠금은 다섯 번 틀리면 30초 기다린다`() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        AppLock.setPin(c, "1234")
        assertTrue(AppLock.isOn(c))
        val t = 1_000_000L
        assertEquals(AppLock.Result.Wrong(4), AppLock.check(c, "0000", t))
        repeat(3) { AppLock.check(c, "0000", t) }
        assertTrue(AppLock.check(c, "0000", t) is AppLock.Result.Wait)
        // 기다리는 동안엔 맞는 암호도 안 받는다
        assertTrue(AppLock.check(c, "1234", t + 1_000) is AppLock.Result.Wait)
        assertEquals(AppLock.Result.Ok, AppLock.check(c, "1234", t + AppLock.WAIT_MS + 1))
        AppLock.turnOff(c)
        assertTrue(!AppLock.isOn(c))
    }
}
