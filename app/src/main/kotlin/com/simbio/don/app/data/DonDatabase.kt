package com.simbio.don.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simbio.don.budget.CategoryGuesser

@Database(
    entities = [
        SettingsEntity::class,
        FixedExpenseEntity::class,
        CycleEntity::class,
        EntryEntity::class,
        ReviewItemEntity::class,
        IncomeEntity::class,
        AppRuleEntity::class,
        SeenAppEntity::class,
        SettledWeekEntity::class,
        CategoryRuleEntity::class,
        MockPurchaseEntity::class,
        HouseEntity::class,
        CategoryBudgetEntity::class,
        SavingsGoalEntity::class,
        AccountEntity::class,
        CategoryDefEntity::class,
        SubcategoryEntity::class,
        InstallmentEntity::class,
        RecurringIncomeEntity::class,
        MileTxEntity::class,
        GamePlayEntity::class,
        AssetCheckEntity::class,
        PlanEntity::class,
        GoalMoveEntity::class,
    ],
    version = 12,
)
abstract class DonDatabase : RoomDatabase() {
    abstract fun dao(): DonDao

    companion object {
        @Volatile private var instance: DonDatabase? = null

        /** 화면 테스트는 파일 대신 메모리 DB를 써서 테스트끼리 기록이 섞이지 않게 한다 */
        @VisibleForTesting
        @Volatile var inMemoryForTests = false

        fun get(context: Context): DonDatabase = instance ?: synchronized(this) {
            instance ?: (
                if (inMemoryForTests) {
                    Room.inMemoryDatabaseBuilder(context.applicationContext, DonDatabase::class.java)
                } else {
                    Room.databaseBuilder(context.applicationContext, DonDatabase::class.java, "don.db")
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                }
                ).build().also { instance = it }
        }

        @VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }

        /** v11: 돈 나갈 계획, 내 이름(내 계좌끼리 송금 알아보기), 송금으로 나간 기록 표시 */
        /** v12: 목표는 따로 모은다 — 목표마다 넣고 뺀 돈, 목표에 이은 통장 */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goal_move (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, goalId INTEGER NOT NULL, " +
                        "epochDay INTEGER NOT NULL, amount INTEGER NOT NULL, source TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_goal_move_goalId ON goal_move (goalId)")
                db.execSQL("ALTER TABLE savings_goal ADD COLUMN accountId INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL, amount INTEGER NOT NULL, " +
                        "label TEXT NOT NULL, category TEXT NOT NULL, memo TEXT NOT NULL, doneEntryId INTEGER, doneAmount INTEGER, doneEpochDay INTEGER, " +
                        "canceled INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE settings ADD COLUMN ownerName TEXT")
                db.execSQL("ALTER TABLE entry ADD COLUMN sentOut INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v10: 자산 점검 (모으는 통장의 그날 금액) */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS asset_check (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId INTEGER NOT NULL, " +
                        "epochDay INTEGER NOT NULL, value INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_check_accountId ON asset_check (accountId)")
            }
        }

        /** v9: 칭호, 미니게임 기록 */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE house ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS game_play (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, game TEXT NOT NULL, " +
                        "epochDay INTEGER NOT NULL, score INTEGER NOT NULL, miles INTEGER NOT NULL)",
                )
            }
        }

        /** v8: 마일리지·마일 키우기, 집 꾸밈(수납·인테리어·옷장), 뷰티샵 예약, 카드 반짝이 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mile_tx (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, epochDay INTEGER NOT NULL, amount INTEGER NOT NULL, " +
                        "kind TEXT NOT NULL, account TEXT NOT NULL, memo TEXT NOT NULL, refKey TEXT)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_mile_tx_refKey ON mile_tx (refKey)")
                db.execSQL("ALTER TABLE mock_purchase ADD COLUMN payload TEXT")
                db.execSQL("ALTER TABLE mock_purchase ADD COLUMN grade TEXT")
                db.execSQL("ALTER TABLE house ADD COLUMN setup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE house ADD COLUMN owned TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE house ADD COLUMN freeMakeover INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN milesSeeded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN growEpochDay INTEGER")
            }
        }

        /** v7: 직접 만든 카테고리·소분류, 할부, 반복 수입 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_def (name TEXT PRIMARY KEY NOT NULL, label TEXT NOT NULL, income INTEGER NOT NULL, " +
                        "icon TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS subcategory (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, " +
                        "name TEXT NOT NULL, sortOrder INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS installment (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, total INTEGER NOT NULL, " +
                        "months INTEGER NOT NULL, firstEpochDay INTEGER NOT NULL, category TEXT NOT NULL, accountId INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_income (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, amount INTEGER NOT NULL, " +
                        "day INTEGER NOT NULL, accountId INTEGER, category TEXT NOT NULL DEFAULT 'SIDE_INCOME', lastDueEpochDay INTEGER)",
                )
                db.execSQL("ALTER TABLE entry ADD COLUMN sub TEXT")
                db.execSQL("ALTER TABLE entry ADD COLUMN installmentId INTEGER")
                db.execSQL("ALTER TABLE entry ADD COLUMN installmentNo TEXT")
                db.execSQL("ALTER TABLE entry ADD COLUMN incomeId INTEGER")
                db.execSQL("ALTER TABLE settings ADD COLUMN subSeeded INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6: 캐릭터 꾸미기 (모습 한 줄) */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE house ADD COLUMN look TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5: 자산·이체, 기록 시각, 고정 지출 날짜(자동 기록), 월급 자동 처리 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS account (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "type TEXT NOT NULL, opening INTEGER NOT NULL, sortOrder INTEGER NOT NULL, hidden INTEGER NOT NULL, packageName TEXT)",
                )
                db.execSQL("ALTER TABLE entry ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE entry ADD COLUMN toAccountId INTEGER")
                db.execSQL("ALTER TABLE entry ADD COLUMN minuteOfDay INTEGER")
                db.execSQL("ALTER TABLE entry ADD COLUMN fixedId INTEGER")
                db.execSQL("ALTER TABLE fixed_expense ADD COLUMN day INTEGER")
                db.execSQL("ALTER TABLE fixed_expense ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE fixed_expense ADD COLUMN category TEXT NOT NULL DEFAULT 'BILLS'")
                db.execSQL("ALTER TABLE fixed_expense ADD COLUMN lastDueEpochDay INTEGER")
                db.execSQL("ALTER TABLE cycle ADD COLUMN salarySource TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE cycle ADD COLUMN noticeShown INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v4: 카테고리 예산, 저축 목표, 결산 결과 저장 (연속 기록·마일리지용) */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS category_budget (category TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(category))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goal (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "target INTEGER NOT NULL, icon TEXT NOT NULL, createdAtEpochDay INTEGER NOT NULL, sortOrder INTEGER NOT NULL, " +
                        "achievedAtEpochDay INTEGER, used INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE settled_week ADD COLUMN budget INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settled_week ADD COLUMN spent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settled_week ADD COLUMN savedBps INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE settled_week ADD COLUMN overspent INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: 모의 쇼핑과 집 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mock_purchase (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "price INTEGER NOT NULL, kind TEXT NOT NULL, store TEXT, epochDay INTEGER NOT NULL, status TEXT NOT NULL, " +
                        "createdAtEpochSecond INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS house (id INTEGER PRIMARY KEY NOT NULL, level TEXT NOT NULL, characterName TEXT NOT NULL)",
                )
            }
        }

        /** v2: 지출에 카테고리·메모 추가, 가게별 카테고리 규칙. 기존 지출은 가게 이름으로 카테고리를 채운다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entry ADD COLUMN category TEXT NOT NULL DEFAULT 'ETC'")
                db.execSQL("ALTER TABLE entry ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS category_rule (labelKey TEXT NOT NULL, category TEXT NOT NULL, PRIMARY KEY(labelKey))")
                db.query("SELECT id, label FROM entry").use { c ->
                    while (c.moveToNext()) {
                        val category = CategoryGuesser.guess(c.getString(1))
                        db.execSQL("UPDATE entry SET category = ? WHERE id = ?", arrayOf<Any>(category.name, c.getLong(0)))
                    }
                }
            }
        }
    }
}
