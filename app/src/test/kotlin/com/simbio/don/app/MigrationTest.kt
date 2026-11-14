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
            "m.db", 15, true,
            DonDatabase.MIGRATION_1_2, DonDatabase.MIGRATION_2_3, DonDatabase.MIGRATION_3_4, DonDatabase.MIGRATION_4_5,
            DonDatabase.MIGRATION_5_6, DonDatabase.MIGRATION_6_7, DonDatabase.MIGRATION_7_8, DonDatabase.MIGRATION_8_9, DonDatabase.MIGRATION_9_10, DonDatabase.MIGRATION_10_11,
            DonDatabase.MIGRATION_11_12, DonDatabase.MIGRATION_12_13, DonDatabase.MIGRATION_13_14, DonDatabase.MIGRATION_14_15,
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

    @Test
    fun v11에서_v12로_목표는_그대로고_넣은_돈은_비어_있다() {
        helper.createDatabase("m11.db", 11).use { db ->
            db.execSQL("INSERT INTO savings_goal (id, name, target, icon, createdAtEpochDay, sortOrder, used) VALUES (1, '여행', 1000000, 'FLIGHT', 20000, 0, 0)")
        }
        val db = helper.runMigrationsAndValidate("m11.db", 12, true, DonDatabase.MIGRATION_11_12)
        db.query("SELECT name, accountId FROM savings_goal").use { c -> c.moveToNext(); assertEquals("여행", c.getString(0)); assertTrue(c.isNull(1)) }
        db.query("SELECT COUNT(*) FROM goal_move").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
    }

    @Test
    fun v12에서_v13으로_기록은_그대로고_나눠_내기는_비어_있다() {
        helper.createDatabase("m12.db", 12).use { db ->
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, category, memo, sentOut) VALUES (1, 20000, 80000, '고깃집', 'SPEND', 'MANUAL', 'FOOD', '', 0)")
        }
        val db = helper.runMigrationsAndValidate("m12.db", 13, true, DonDatabase.MIGRATION_12_13)
        db.query("SELECT amount, share, people, settleFor, owe FROM entry").use { c ->
            c.moveToNext()
            assertEquals(80000, c.getLong(0))
            assertTrue(c.isNull(1)); assertTrue(c.isNull(2)); assertTrue(c.isNull(3))
            assertEquals(0, c.getInt(4))
        }
    }

    @Test
    fun v13에서_v14로_그날_쓰는_돈_기본값과_계획_일정_칸() {
        helper.createDatabase("m13.db", 13).use { db ->
            db.execSQL("INSERT INTO settings (id, payDay, settlementDay, savings, subSeeded, milesSeeded) VALUES (0, 25, 1, 500000, 1, 1)")
            db.execSQL("INSERT INTO `plan` (id, epochDay, amount, label, category, memo, canceled) VALUES (1, 20000, 50000, '생일', 'EVENT', '', 0)")
        }
        val db = helper.runMigrationsAndValidate("m13.db", 14, true, DonDatabase.MIGRATION_13_14)
        db.query("SELECT dailyCategories FROM settings").use { c -> c.moveToNext(); assertEquals("FOOD,CAFE,DELIVERY,TRANSPORT", c.getString(0)) }
        db.query("SELECT eventId, calendarOwned FROM `plan`").use { c -> c.moveToNext(); assertTrue(c.isNull(0)); assertEquals(0, c.getInt(1)) }
    }

    @Test
    fun v14에서_v15로_알림_글과_가게_이름_자리_칸() {
        helper.createDatabase("m14.db", 14).use { db ->
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source) VALUES (1, 20000, 4500, '결제', 'SPEND', 'NOTIFICATION')")
            db.execSQL("INSERT INTO app_rule (packageName, displayName, enabled, keywordRules) VALUES ('card.app', 'OO카드', 1, '')")
        }
        val db = helper.runMigrationsAndValidate("m14.db", 15, true, DonDatabase.MIGRATION_14_15)
        db.query("SELECT rawText, nameUnknown, label FROM entry").use { c -> c.moveToNext(); assertTrue(c.isNull(0)); assertEquals(0, c.getInt(1)); assertEquals("결제", c.getString(2)) }
        db.query("SELECT merchantSpot FROM app_rule").use { c -> c.moveToNext(); assertTrue(c.isNull(0)) }
    }

    @Test
    fun v16에서_v17로_정산_짝_칸() {
        helper.createDatabase("m16.db", 16).use { db ->
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source) VALUES (1, 20000, 20000, '입금', 'INCOME', 'NOTIFICATION')")
        }
        val db = helper.runMigrationsAndValidate("m16.db", 17, true, DonDatabase.MIGRATION_16_17)
        db.query("SELECT linkState, amount FROM entry").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)); assertEquals(20000, c.getInt(1)) }
    }

    @Test
    fun v17에서_v18로_동물_돌보기와_한_달에_나눠서() {
        helper.createDatabase("m17.db", 17).use { db ->
            db.execSQL("INSERT INTO `plan` (id, epochDay, amount, label, category, memo, canceled, calendarOwned, autoLinked, rejected) VALUES (1, 20000, 100000, '결혼식', 'EVENT', '', 0, 0, 0, '')")
            db.execSQL("INSERT INTO `plan` (id, epochDay, amount, label, category, memo, canceled, calendarOwned, autoLinked, rejected) VALUES (2, 20000, 50000, '회식', 'FOOD', '', 0, 0, 0, '')")
            db.execSQL("INSERT INTO house (id, level, characterName, look, setup, owned, freeMakeover, title) VALUES (0, 'ONE_ROOM', '나', '', '', '', 1, '')")
        }
        val db = helper.runMigrationsAndValidate("m17.db", 18, true, DonDatabase.MIGRATION_17_18)
        db.query("SELECT monthly, createdEpochDay FROM `plan` ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(1, c.getInt(0)); assertTrue(c.getLong(1) > 20000)
            c.moveToNext(); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT pet FROM house").use { c -> c.moveToNext(); assertEquals("", c.getString(0)) }
    }

    @Test
    fun v18에서_v19로_짐작한_통장_표시는_이은_앱이_아닌_최근_알림_기록에만() {
        val today = java.time.LocalDate.now().toEpochDay()
        helper.createDatabase("m18.db", 18).use { db ->
            db.execSQL("INSERT INTO account (id, name, type, opening, sortOrder, hidden, packageName) VALUES (1, '생활비 통장', 'BANK', 0, 0, 0, 'bank.app')")
            db.execSQL("INSERT INTO account (id, name, type, opening, sortOrder, hidden, packageName) VALUES (2, '신한카드', 'CARD', 0, 1, 0, NULL)")
            // 1: 통장에 이은 앱 → 확실, 2: 안 이은 앱인데 통장에 → 짐작, 3: 직접 적음, 4: 오래된 알림 기록
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, packageName, accountId) VALUES (1, $today, 1000, 'a', 'SPEND', 'NOTIFICATION', 'bank.app', 1)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, packageName, accountId) VALUES (2, $today, 2000, 'b', 'SPEND', 'NOTIFICATION', 'pay.app', 1)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, packageName, accountId) VALUES (3, $today, 3000, 'c', 'SPEND', 'MANUAL', NULL, 1)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, packageName, accountId) VALUES (4, ${today - 100}, 4000, 'd', 'SPEND', 'NOTIFICATION', 'pay.app', 1)")
        }
        val db = helper.runMigrationsAndValidate("m18.db", 19, true, DonDatabase.MIGRATION_18_19)
        db.query("SELECT id, acctGuess FROM entry ORDER BY id").use { c ->
            val got = buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) }
            assertEquals(listOf(1 to 0, 2 to 1, 3 to 0, 4 to 0), got)
        }
    }

    @Test
    fun v19에서_v20으로_자산에_끝자리_칸이_생기고_비어_있다() {
        helper.createDatabase("m19.db", 19).use { db ->
            db.execSQL("INSERT INTO account (id, name, type, opening, sortOrder, hidden, packageName) VALUES (1, '생활비 통장', 'BANK', 0, 0, 0, 'bank.app')")
        }
        val db = helper.runMigrationsAndValidate("m19.db", 20, true, DonDatabase.MIGRATION_19_20)
        db.query("SELECT name, tails, packageName FROM account").use { c ->
            c.moveToNext(); assertEquals("생활비 통장", c.getString(0)); assertEquals("", c.getString(1)); assertEquals("bank.app", c.getString(2))
        }
    }

    @Test
    fun v20에서_v21로_필요욕구_예산조정_사고싶은것이_생긴다() {
        helper.createDatabase("m20.db", 20).use { db ->
            db.execSQL("INSERT INTO cycle (nominal, salary, startEpochDay, settlementDay, savings, fixedTotal, salarySource, noticeShown) VALUES ('2026-11', 3000000, 20000, 1, 300000, 600000, 'MANUAL', 1)")
            db.execSQL("INSERT INTO entry (id, epochDay, amount, label, type, source, category) VALUES (1, 20000, 5000, '커피', 'SPEND', 'MANUAL', 'CAFE')")
        }
        val db = helper.runMigrationsAndValidate("m20.db", 21, true, DonDatabase.MIGRATION_20_21)
        db.query("SELECT budgetAdjust FROM cycle").use { c -> c.moveToNext(); assertEquals(0L, c.getLong(0)) }
        db.query("SELECT want, moveState FROM entry").use { c -> c.moveToNext(); assertTrue(c.isNull(0)); assertEquals(0, c.getInt(1)) }
        db.execSQL("INSERT INTO wish (name, price, createdEpochDay, remindEpochDay) VALUES ('운동화', 89000, 20000, 20007)")
        db.query("SELECT status, notified FROM wish").use { c -> c.moveToNext(); assertEquals("WAITING", c.getString(0)); assertEquals(0, c.getInt(1)) }
    }
}
