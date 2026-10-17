package com.simbio.don.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.simbio.don.app.data.DonDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** M0(v1)을 이미 설치한 폰에서 기록이 그대로 남고 카테고리가 채워지는지. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DonDatabase::class.java)

    @Test
    fun v1에서_최신으로_기록이_유지되고_카테고리가_채워진다() {
        helper.createDatabase("m.db", 1).use { db ->
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source) VALUES (1, 20000, 4500, '스타벅스 강남점', 'SPEND', 'MANUAL')")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source) VALUES (2, 20001, 9000, '모르는 가게', 'SPEND', 'MANUAL')")
        }
        val db = helper.runMigrationsAndValidate("m.db", 4, true, DonDatabase.MIGRATION_1_2, DonDatabase.MIGRATION_2_3, DonDatabase.MIGRATION_3_4)
        db.query("SELECT id, amount, category, memo FROM entry ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(4500, c.getLong(1)); assertEquals("CAFE", c.getString(2)); assertEquals("", c.getString(3))
            c.moveToNext(); assertEquals("ETC", c.getString(2))
        }
    }
}
