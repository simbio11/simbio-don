package com.simbio.don.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 1,
)
abstract class DonDatabase : RoomDatabase() {
    abstract fun dao(): DonDao

    companion object {
        @Volatile private var instance: DonDatabase? = null

        fun get(context: Context): DonDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DonDatabase::class.java, "don.db")
                .build().also { instance = it }
        }
    }
}
