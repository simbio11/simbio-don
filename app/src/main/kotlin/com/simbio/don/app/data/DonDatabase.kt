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
    ],
    version = 2,
)
abstract class DonDatabase : RoomDatabase() {
    abstract fun dao(): DonDao

    companion object {
        @Volatile private var instance: DonDatabase? = null

        fun get(context: Context): DonDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DonDatabase::class.java, "don.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        /** 테스트끼리 같은 JVM에서 DB를 나눠 쓰지 않도록 */
        @VisibleForTesting
        fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
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
