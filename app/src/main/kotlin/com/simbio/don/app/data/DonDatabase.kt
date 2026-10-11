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
    ],
    version = 3,
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                }
                ).build().also { instance = it }
        }

        @VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
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
