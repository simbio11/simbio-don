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
            "m.db", 11, true,
            DonDatabase.MIGRATION_1_2, DonDatabase.MIGRATION_2_3, DonDatabase.MIGRATION_3_4, DonDatabase.MIGRATION_4_5,
            DonDatabase.MIGRATION_5_6, DonDatabase.MIGRATION_6_7, DonDatabase.MIGRATION_7_8, DonDatabase.MIGRATION_8_9, DonDatabase.MIGRATION_9_10, DonDatabase.MIGRATION_10_11,
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

    @Test
    fun v5에서_v6으로_집은_그대로고_캐릭터는_기본_모습이다() {
        helper.createDatabase("m5.db", 5).use { db ->
            db.execSQL("INSERT INTO house (id, level, characterName) VALUES (0, 'ONE_ROOM', '나')")
        }
        val db = helper.runMigrationsAndValidate("m5.db", 6, true, DonDatabase.MIGRATION_5_6)
        db.query("SELECT level, look FROM house").use { c ->
            c.moveToNext(); assertEquals("ONE_ROOM", c.getString(0)); assertEquals("", c.getString(1))
        }
    }

    @Test
    fun v6에서_v7로_기록은_그대로고_소분류는_다음에_채운다() {
        helper.createDatabase("m6.db", 6).use { db ->
            db.execSQL("INSERT INTO settings (id, payDay, settlementDay, savings) VALUES (0, 25, 1, 0)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, category, memo) VALUES (1, 20000, 4500, '스타벅스', 'SPEND', 'MANUAL', 'CAFE', '')")
        }
        val db = helper.runMigrationsAndValidate("m6.db", 7, true, DonDatabase.MIGRATION_6_7)
        db.query("SELECT category, sub, installmentId, incomeId FROM entry").use { c ->
            c.moveToNext(); assertEquals("CAFE", c.getString(0)); assertTrue(c.isNull(1)); assertTrue(c.isNull(2)); assertTrue(c.isNull(3))
        }
        db.query("SELECT subSeeded FROM settings").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM subcategory").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun v7에서_v8로_집과_모의_구매는_그대로고_첫_꾸미기는_공짜다() {
        helper.createDatabase("m7.db", 7).use { db ->
            db.execSQL("INSERT INTO settings (id, payDay, settlementDay, savings, subSeeded) VALUES (0, 25, 1, 0, 1)")
            db.execSQL("INSERT INTO house (id, level, characterName, look) VALUES (0, 'TWO_ROOM', '나', '')")
            db.execSQL("INSERT INTO mock_purchase (id, name, price, kind, epochDay, status, createdAtEpochSecond) VALUES (1, '모자', 30000, 'CLOTHES', 20000, 'DELIVERED', 0)")
        }
        val db = helper.runMigrationsAndValidate("m7.db", 8, true, DonDatabase.MIGRATION_7_8)
        db.query("SELECT level, setup, owned, freeMakeover FROM house").use { c ->
            c.moveToNext(); assertEquals("TWO_ROOM", c.getString(0)); assertEquals("", c.getString(1)); assertEquals("", c.getString(2)); assertEquals(1, c.getInt(3))
        }
        db.query("SELECT payload, grade FROM mock_purchase").use { c -> c.moveToNext(); assertTrue(c.isNull(0)); assertTrue(c.isNull(1)) }
        db.query("SELECT milesSeeded, growEpochDay FROM settings").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)); assertTrue(c.isNull(1)) }
        db.query("SELECT COUNT(*) FROM mile_tx").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun v8에서_v9로_집은_그대로고_칭호는_비어_있다() {
        helper.createDatabase("m8.db", 8).use { db ->
            db.execSQL("INSERT INTO house (id, level, characterName, look, setup, owned, freeMakeover) VALUES (0, 'APARTMENT', '나', '', '', 'top:knit', 0)")
            db.execSQL("INSERT INTO mile_tx (id, epochDay, amount, kind, account, memo) VALUES (1, 20000, 2500, 'EARN', 'WALLET', '')")
        }
        val db = helper.runMigrationsAndValidate("m8.db", 9, true, DonDatabase.MIGRATION_8_9)
        db.query("SELECT level, owned, title FROM house").use { c ->
            c.moveToNext(); assertEquals("APARTMENT", c.getString(0)); assertEquals("top:knit", c.getString(1)); assertEquals("", c.getString(2))
        }
        db.query("SELECT COUNT(*) FROM game_play").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT SUM(amount) FROM mile_tx").use { c -> c.moveToNext(); assertEquals(2500, c.getInt(0)) }
    }

    @Test
    fun v9에서_v10으로_자산은_그대로고_점검은_비어_있다() {
        helper.createDatabase("m9.db", 9).use { db ->
            db.execSQL("INSERT INTO account (id, name, type, opening, sortOrder, hidden) VALUES (1, '생활비 통장', 'BANK', 1000000, 0, 0)")
        }
        val db = helper.runMigrationsAndValidate("m9.db", 10, true, DonDatabase.MIGRATION_9_10)
        db.query("SELECT name, opening FROM account").use { c -> c.moveToNext(); assertEquals("생활비 통장", c.getString(0)); assertEquals(1000000, c.getInt(1)) }
        db.query("SELECT COUNT(*) FROM asset_check").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun v10에서_v11로_기록은_그대로고_계획은_비어_있다() {
        helper.createDatabase("m10.db", 10).use { db ->
            db.execSQL("INSERT INTO settings (id, payDay, settlementDay, savings, subSeeded, milesSeeded) VALUES (0, 25, 1, 0, 1, 1)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source) VALUES (1, 20000, 4500, '스타벅스', 'SPEND', 'MANUAL')")
        }
        val db = helper.runMigrationsAndValidate("m10.db", 11, true, DonDatabase.MIGRATION_10_11)
        db.query("SELECT amount, sentOut FROM entry").use { c -> c.moveToNext(); assertEquals(4500, c.getInt(0)); assertEquals(0, c.getInt(1)) }
        db.query("SELECT ownerName FROM settings").use { c -> c.moveToNext(); assertTrue(c.isNull(0)) }
        db.query("SELECT COUNT(*) FROM plan").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }
}
