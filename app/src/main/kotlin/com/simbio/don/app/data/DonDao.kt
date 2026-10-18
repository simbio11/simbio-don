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

    @Upsert
    suspend fun upsertFixedExpense(f: FixedExpenseEntity): Long

    @Query("DELETE FROM fixed_expense WHERE id NOT IN (:keep)")
    suspend fun deleteFixedExcept(keep: List<Long>)

    /** 고정 지출 목록 저장. id가 있는 것은 그대로 고쳐서, 이미 적은 기록과의 연결(fixedId)이 끊기지 않게 한다 */
    @Transaction
    suspend fun syncFixedExpenses(items: List<FixedExpenseEntity>) {
        val kept = items.filter { it.id != 0L }.map { it.id }
        if (kept.isEmpty()) clearFixedExpenses() else deleteFixedExcept(kept)
        items.forEach { upsertFixedExpense(it) }
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

    @Query("SELECT * FROM cycle ORDER BY nominal DESC")
    suspend fun cyclesNow(): List<CycleEntity>

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

    @Query("SELECT * FROM house WHERE id = 0")
    suspend fun houseNow(): HouseEntity?

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

    // ── 자산 ──
    @Query("SELECT * FROM account ORDER BY sortOrder, id")
    fun accounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY sortOrder, id")
    suspend fun accountsNow(): List<AccountEntity>

    @Upsert
    suspend fun saveAccount(a: AccountEntity): Long

    @Delete
    suspend fun deleteAccount(a: AccountEntity)

    @Query("UPDATE entry SET accountId = NULL WHERE accountId = :id")
    suspend fun detachAccountFrom(id: Long)

    @Query("UPDATE entry SET toAccountId = NULL WHERE toAccountId = :id")
    suspend fun detachAccountTo(id: Long)

    @Query("UPDATE fixed_expense SET accountId = NULL WHERE accountId = :id")
    suspend fun detachAccountFixed(id: Long)

    /** 잔액 계산용: 자산이 붙은 기록 */
    @Query("SELECT * FROM entry WHERE accountId IS NOT NULL OR toAccountId IS NOT NULL")
    fun accountEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE accountId = :id OR toAccountId = :id ORDER BY epochDay DESC, id DESC")
    fun entriesOfAccount(id: Long): Flow<List<EntryEntity>>

    // ── 전체 검색 ──
    @Query(
        "SELECT * FROM entry WHERE label LIKE '%' || :q || '%' OR memo LIKE '%' || :q || '%' OR CAST(amount AS TEXT) = :q " +
            "ORDER BY epochDay DESC, id DESC LIMIT 500",
    )
    fun search(q: String): Flow<List<EntryEntity>>

    // ── 월급 입금 ──
    @Query("SELECT * FROM entry WHERE type = 'INCOME' AND category = 'SALARY' AND epochDay BETWEEN :from AND :to ORDER BY amount DESC")
    suspend fun salaryIncomeBetween(from: Long, to: Long): List<EntryEntity>

    // ── 직접 만든 카테고리·소분류 ──
    @Query("SELECT * FROM category_def ORDER BY sortOrder, name")
    fun categoryDefs(): Flow<List<CategoryDefEntity>>

    @Query("SELECT * FROM category_def ORDER BY sortOrder, name")
    suspend fun categoryDefsNow(): List<CategoryDefEntity>

    @Upsert
    suspend fun saveCategoryDef(c: CategoryDefEntity)

    @Query("DELETE FROM category_def WHERE name = :name")
    suspend fun deleteCategoryDef(name: String)

    @Query("SELECT * FROM subcategory ORDER BY category, sortOrder, id")
    fun subcategories(): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategory ORDER BY category, sortOrder, id")
    suspend fun subcategoriesNow(): List<SubcategoryEntity>

    @Insert
    suspend fun insertSubcategories(x: List<SubcategoryEntity>)

    @Upsert
    suspend fun saveSubcategory(x: SubcategoryEntity)

    @Query("DELETE FROM subcategory WHERE id = :id")
    suspend fun deleteSubcategory(id: Long)

    @Query("DELETE FROM subcategory WHERE category = :category")
    suspend fun deleteSubcategoriesOf(category: String)

    /** 카테고리를 지우면 그 기록은 [to]로 옮기고 소분류는 비운다 */
    @Query("UPDATE entry SET category = :to, sub = NULL WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)

    @Query("UPDATE entry SET sub = :to WHERE category = :category AND sub = :from")
    suspend fun renameSub(category: String, from: String, to: String?)

    @Query("UPDATE fixed_expense SET category = :to WHERE category = :from")
    suspend fun moveFixedCategory(from: String, to: String)

    @Query("UPDATE recurring_income SET category = :to WHERE category = :from")
    suspend fun moveIncomeCategory(from: String, to: String)

    @Query("UPDATE category_rule SET category = :to WHERE category = :from")
    suspend fun moveRuleCategory(from: String, to: String)

    @Query("DELETE FROM category_budget WHERE category = :category")
    suspend fun deleteCategoryBudget(category: String)

    // ── 할부 ──
    @Insert
    suspend fun insertInstallment(x: InstallmentEntity): Long

    @Query("SELECT * FROM installment WHERE id = :id")
    suspend fun installment(id: Long): InstallmentEntity?

    @Query("DELETE FROM installment WHERE id = :id")
    suspend fun deleteInstallment(id: Long)

    @Query("DELETE FROM entry WHERE installmentId = :id")
    suspend fun deleteInstallmentEntries(id: Long)

    @Query("SELECT * FROM entry WHERE installmentId = :id ORDER BY epochDay")
    suspend fun installmentEntries(id: Long): List<EntryEntity>

    // ── 반복 수입 ──
    @Query("SELECT * FROM recurring_income ORDER BY id")
    fun recurringIncomes(): Flow<List<RecurringIncomeEntity>>

    @Query("SELECT * FROM recurring_income ORDER BY id")
    suspend fun recurringIncomesNow(): List<RecurringIncomeEntity>

    @Upsert
    suspend fun upsertRecurringIncome(x: RecurringIncomeEntity): Long

    @Query("DELETE FROM recurring_income WHERE id = :id")
    suspend fun deleteRecurringIncome(id: Long)

    // ── 연간 통계 ──
    @Query("SELECT * FROM entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun entriesBetweenFlow(from: Long, to: Long): Flow<List<EntryEntity>>

    // ── 백업·복원 ──
    @Query("SELECT * FROM fixed_expense") suspend fun allFixed(): List<FixedExpenseEntity>
    @Query("SELECT * FROM cycle") suspend fun allCycles(): List<CycleEntity>
    @Query("SELECT * FROM review_item") suspend fun allReviews(): List<ReviewItemEntity>
    @Query("SELECT * FROM app_rule") suspend fun allRules(): List<AppRuleEntity>
    @Query("SELECT * FROM settled_week") suspend fun allSettled(): List<SettledWeekEntity>
    @Query("SELECT * FROM category_budget") suspend fun allCategoryBudgets(): List<CategoryBudgetEntity>
    @Query("SELECT * FROM house") suspend fun allHouses(): List<HouseEntity>
    @Query("SELECT * FROM installment") suspend fun allInstallments(): List<InstallmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettings(x: List<SettingsEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putFixed(x: List<FixedExpenseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCycles(x: List<CycleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEntries(x: List<EntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReviews(x: List<ReviewItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(x: List<AppRuleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSettled(x: List<SettledWeekEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategoryRules(x: List<CategoryRuleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMocks(x: List<MockPurchaseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHouses(x: List<HouseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategoryBudgets(x: List<CategoryBudgetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoals(x: List<SavingsGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(x: List<AccountEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategoryDefs(x: List<CategoryDefEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSubcategories(x: List<SubcategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putInstallments(x: List<InstallmentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurringIncomes(x: List<RecurringIncomeEntity>)

    @Query("DELETE FROM settings") suspend fun wipeSettings()
    @Query("DELETE FROM fixed_expense") suspend fun wipeFixed()
    @Query("DELETE FROM cycle") suspend fun wipeCycles()
    @Query("DELETE FROM entry") suspend fun wipeEntries()
    @Query("DELETE FROM review_item") suspend fun wipeReviews()
    @Query("DELETE FROM income") suspend fun wipeIncome()
    @Query("DELETE FROM app_rule") suspend fun wipeRules()
    @Query("DELETE FROM seen_app") suspend fun wipeSeen()
    @Query("DELETE FROM settled_week") suspend fun wipeSettled()
    @Query("DELETE FROM category_rule") suspend fun wipeCategoryRules()
    @Query("DELETE FROM mock_purchase") suspend fun wipeMocks()
    @Query("DELETE FROM house") suspend fun wipeHouses()
    @Query("DELETE FROM category_budget") suspend fun wipeCategoryBudgets()
    @Query("DELETE FROM savings_goal") suspend fun wipeGoals()
    @Query("DELETE FROM account") suspend fun wipeAccounts()
    @Query("DELETE FROM category_def") suspend fun wipeCategoryDefs()
    @Query("DELETE FROM subcategory") suspend fun wipeSubcategories()
    @Query("DELETE FROM installment") suspend fun wipeInstallments()
    @Query("DELETE FROM recurring_income") suspend fun wipeRecurringIncomes()
}
