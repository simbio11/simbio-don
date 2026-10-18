package com.simbio.don.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.simbio.don.app.data.DonDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val db = helper.runMigrationsAndValidate(
            "m.db", 5, true,
            DonDatabase.MIGRATION_1_2, DonDatabase.MIGRATION_2_3, DonDatabase.MIGRATION_3_4, DonDatabase.MIGRATION_4_5,
        )
        db.query("SELECT id, amount, category, memo, accountId, minuteOfDay FROM entry ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(4500, c.getLong(1)); assertEquals("CAFE", c.getString(2)); assertEquals("", c.getString(3))
            assertTrue(c.isNull(4)); assertTrue(c.isNull(5))
            c.moveToNext(); assertEquals("ETC", c.getString(2))
        }
    }

    @Test
    fun v4에서_v5로_주기는_월급을_직접_확인한_것으로_남고_고정비에_날짜칸이_생긴다() {
        helper.createDatabase("m4.db", 4).use { db ->
            db.execSQL("INSERT INTO cycle (nominal, salary, startEpochDay, settlementDay, savings, fixedTotal) VALUES ('2026-10', 3300000, 20384, 1, 320000, 1000000)")
            db.execSQL("INSERT INTO fixed_expense (id, name, amount) VALUES (1, '월세', 1000000)")
        }
        val db = helper.runMigrationsAndValidate("m4.db", 5, true, DonDatabase.MIGRATION_4_5)
        db.query("SELECT salarySource, noticeShown FROM cycle").use { c ->
            c.moveToNext(); assertEquals("MANUAL", c.getString(0)); assertEquals(1, c.getInt(1))
        }
        db.query("SELECT day, category FROM fixed_expense").use { c ->
            c.moveToNext(); assertTrue(c.isNull(0)); assertEquals("BILLS", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM account").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }
}
