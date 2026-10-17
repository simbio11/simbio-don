package com.simbio.don.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DonDao {
    // 설정
    @Query("SELECT * FROM settings WHERE id = 0")
    fun settings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 0")
    suspend fun settingsNow(): SettingsEntity?

    @Upsert
    suspend fun saveSettings(s: SettingsEntity)

    @Query("SELECT * FROM fixed_expense ORDER BY id")
    fun fixedExpenses(): Flow<List<FixedExpenseEntity>>

    @Query("SELECT * FROM fixed_expense ORDER BY id")
    suspend fun fixedExpensesNow(): List<FixedExpenseEntity>

    @Query("DELETE FROM fixed_expense")
    suspend fun clearFixedExpenses()

    @Insert
    suspend fun insertFixedExpenses(items: List<FixedExpenseEntity>)

    @Transaction
    suspend fun replaceFixedExpenses(items: List<FixedExpenseEntity>) {
        clearFixedExpenses()
        insertFixedExpenses(items.map { it.copy(id = 0) })
    }

    // 주기
    // 최신 주기 = 가장 늦은 달(nominal "YYYY-MM"). 시작일로 고르면 한 달 중간에 시작한 첫 달과
    // 앞당겨 입금된 다음 달이 같은 날일 때 옛 달에 머문다
    @Query("SELECT * FROM cycle ORDER BY nominal DESC LIMIT 1")
    fun latestCycle(): Flow<CycleEntity?>

    @Query("SELECT * FROM cycle ORDER BY nominal DESC LIMIT 1")
    suspend fun latestCycleNow(): CycleEntity?

    @Upsert
    suspend fun saveCycle(c: CycleEntity)

    @Query("SELECT * FROM cycle ORDER BY nominal DESC")
    fun cycles(): Flow<List<CycleEntity>>

    // 지출
    @Query("SELECT * FROM entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id DESC")
    fun entriesBetween(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE epochDay BETWEEN :from AND :to")
    suspend fun entriesBetweenNow(from: Long, to: Long): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE postedAtEpochSecond >= :since")
    suspend fun notificationEntriesSince(since: Long): List<EntryEntity>

    @Insert
    suspend fun insertEntry(e: EntryEntity): Long

    @Delete
    suspend fun deleteEntry(e: EntryEntity)

    @Update
    suspend fun updateEntry(e: EntryEntity)

    @Update
    suspend fun updateEntries(e: List<EntryEntity>)

    @Query("SELECT * FROM entry ORDER BY epochDay, id")
    suspend fun allEntriesNow(): List<EntryEntity>

    // 카테고리 규칙
    @Query("SELECT * FROM category_rule")
    fun categoryRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rule")
    suspend fun categoryRulesNow(): List<CategoryRuleEntity>

    @Upsert
    suspend fun saveCategoryRule(r: CategoryRuleEntity)

    @Query("DELETE FROM category_rule")
    suspend fun clearCategoryRules()

    // 모의 쇼핑·집
    @Query("SELECT * FROM mock_purchase ORDER BY epochDay DESC, id DESC")
    fun mockPurchases(): Flow<List<MockPurchaseEntity>>

    @Query("SELECT * FROM mock_purchase")
    suspend fun mockPurchasesNow(): List<MockPurchaseEntity>

    @Insert
    suspend fun insertMock(m: MockPurchaseEntity): Long

    @Update
    suspend fun updateMocks(m: List<MockPurchaseEntity>)

    @Query("SELECT * FROM house WHERE id = 0")
    fun house(): Flow<HouseEntity?>

    @Upsert
    suspend fun saveHouse(h: HouseEntity)

    // 확인함
    @Query("SELECT * FROM review_item ORDER BY postedAtEpochSecond DESC")
    fun reviewItems(): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_item")
    suspend fun reviewItemsNow(): List<ReviewItemEntity>

    @Insert
    suspend fun insertReview(r: ReviewItemEntity): Long

    @Delete
    suspend fun deleteReview(r: ReviewItemEntity)

    // 입금
    @Insert
    suspend fun insertIncome(i: IncomeEntity)

    @Query("SELECT * FROM income WHERE postedAtEpochSecond >= :since ORDER BY amount DESC LIMIT 1")
    suspend fun largestIncomeSince(since: Long): IncomeEntity?

    // 알림 앱 규칙
    @Query("SELECT * FROM app_rule ORDER BY displayName")
    fun appRules(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rule")
    suspend fun appRulesNow(): List<AppRuleEntity>

    @Upsert
    suspend fun saveAppRule(r: AppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppRulesIfAbsent(r: List<AppRuleEntity>)

    @Query("DELETE FROM app_rule WHERE packageName = :pkg")
    suspend fun deleteAppRule(pkg: String)

    @Upsert
    suspend fun markSeen(a: SeenAppEntity)

    @Query("SELECT * FROM seen_app ORDER BY lastSeenEpochSecond DESC")
    fun seenApps(): Flow<List<SeenAppEntity>>

    // 결산
    @Query("SELECT * FROM settled_week WHERE nominal = :nominal")
    fun settledWeeks(nominal: String): Flow<List<SettledWeekEntity>>

    @Query("SELECT * FROM settled_week WHERE nominal = :nominal")
    suspend fun settledWeeksNow(nominal: String): List<SettledWeekEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSettled(s: SettledWeekEntity)

    // ── 결산 기록 (모든 달) ──
    @Query("SELECT * FROM settled_week ORDER BY nominal, weekIndex")
    fun allSettledWeeks(): Flow<List<SettledWeekEntity>>

    @Query("SELECT * FROM settled_week ORDER BY nominal, weekIndex")
    suspend fun allSettledWeeksNow(): List<SettledWeekEntity>

    // ── 카테고리 예산 ──
    @Query("SELECT * FROM category_budget")
    fun categoryBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("DELETE FROM category_budget")
    suspend fun clearCategoryBudgets()

    @Insert
    suspend fun insertCategoryBudgets(items: List<CategoryBudgetEntity>)

    @Transaction
    suspend fun replaceCategoryBudgets(items: List<CategoryBudgetEntity>) {
        clearCategoryBudgets()
        insertCategoryBudgets(items)
    }

    // ── 저축 목표 ──
    @Query("SELECT * FROM savings_goal ORDER BY sortOrder, id")
    fun goals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goal ORDER BY sortOrder, id")
    suspend fun goalsNow(): List<SavingsGoalEntity>

    @Upsert
    suspend fun saveGoal(g: SavingsGoalEntity): Long

    @Delete
    suspend fun deleteGoal(g: SavingsGoalEntity)

    // ── 여러 달 기록 (월별 추이·모은 돈) ──
    @Query("SELECT * FROM entry WHERE epochDay >= :from ORDER BY epochDay")
    fun entriesSince(from: Long): Flow<List<EntryEntity>>
}
