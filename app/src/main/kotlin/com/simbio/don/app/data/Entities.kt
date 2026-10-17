package com.simbio.don.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 초기 설정. 한 줄만 쓴다 (id = 0). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0,
    val payDay: Int,
    /** java.time.DayOfWeek 값 (1 = 월요일) */
    val settlementDay: Int,
    val savings: Long,
    /** 결산 요일 변경은 다음 달부터 적용 — 다음 주기에 쓸 값 */
    val pendingSettlementDay: Int? = null,
)

@Entity(tableName = "fixed_expense")
data class FixedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
)

/** 한 주기(월급날 ~ 다음 월급날 전날). 월급날마다 사용자가 확인해서 만든다. */
@Entity(tableName = "cycle")
data class CycleEntity(
    /** "2026-10" 형식 */
    @PrimaryKey val nominal: String,
    val salary: Long,
    /** 실제 입금일 (epochDay) */
    val startEpochDay: Long,
    /** 이 주기에 쓸 결산 요일 */
    val settlementDay: Int,
    val savings: Long,
    /** 이 주기 시작 때의 고정비 합계 */
    val fixedTotal: Long,
    /** 한 달 중간에 처음 시작한 달: 주 예산으로 나눌 금액을 직접 정함 */
    val budgetOverride: Long? = null,
)

@Entity(tableName = "entry")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val amount: Long,
    val label: String,
    /** com.simbio.don.budget.EntryType 이름 */
    val type: String,
    /** com.simbio.don.budget.EntrySource 이름 */
    val source: String,
    /** 알림에서 온 경우: 중복 판정용 */
    val packageName: String? = null,
    val postedAtEpochSecond: Long? = null,
    /** com.simbio.don.budget.Category 이름 */
    @ColumnInfo(defaultValue = "ETC") val category: String = "ETC",
    @ColumnInfo(defaultValue = "") val memo: String = "",
)

/** 사용자가 고친 가게 → 카테고리. 다음부터 같은 가게는 이 카테고리로 분류한다. */
@Entity(tableName = "category_rule")
data class CategoryRuleEntity(
    /** CategoryGuesser.key(가게 이름) */
    @PrimaryKey val labelKey: String,
    val category: String,
)

/** 확인 필요함 */
@Entity(tableName = "review_item")
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** com.simbio.don.notify.RuleAction 이름 */
    val action: String,
    val amount: Long,
    val label: String,
    val postedAtEpochSecond: Long,
    /** 사용자가 판단할 수 있게 알림 문구를 남긴다. 정리하면 같이 지워진다 */
    val rawText: String,
    /** com.simbio.don.notify.ReviewReason 이름 */
    val reason: String,
    /** 중복 의심일 때 짝이 되는 지출 */
    val relatedEntryId: Long? = null,
)

/** 은행 입금 알림 — 월급 금액 제안에만 쓴다. */
@Entity(tableName = "income")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val postedAtEpochSecond: Long,
)

/** 알림을 읽을 앱과 키워드 규칙. */
@Entity(tableName = "app_rule")
data class AppRuleEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    /** "IGNORE=광고,혜택;REFUND=취소,환불;..." 형식 */
    val keywordRules: String,
)

/**
 * 알림을 보낸 앱 목록 (본문은 저장하지 않고 패키지 이름만).
 * 설정에서 "알림 읽을 앱"을 고를 때 보여준다.
 */
@Entity(tableName = "seen_app")
data class SeenAppEntity(
    @PrimaryKey val packageName: String,
    val lastSeenEpochSecond: Long,
)

@Entity(tableName = "settled_week", primaryKeys = ["nominal", "weekIndex"])
data class SettledWeekEntity(
    val nominal: String,
    val weekIndex: Int,
    val settledAtEpochSecond: Long,
    val auto: Boolean,
    /** 결산 때의 결과 (v4부터). 그 전에 결산한 주는 savedBps = -1 (모름) */
    @ColumnInfo(defaultValue = "0") val budget: Long = 0,
    @ColumnInfo(defaultValue = "0") val spent: Long = 0,
    @ColumnInfo(defaultValue = "-1") val savedBps: Int = -1,
    @ColumnInfo(defaultValue = "0") val overspent: Long = 0,
) {
    /** 예산을 지킨 주인지 (모르면 null) */
    val kept: Boolean? get() = if (savedBps < 0) null else overspent == 0L
}

/** 카테고리별 한 달(월급날 기준) 예산. 없으면 정하지 않은 것. */
@Entity(tableName = "category_budget")
data class CategoryBudgetEntity(
    @PrimaryKey val category: String,
    val amount: Long,
)

/** 저축 목표. 모은 돈을 순서대로 채운다. 쓴 목표(used)는 그 금액만큼 모은 돈에서 빠진다. */
@Entity(tableName = "savings_goal")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Long,
    /** 아이콘 이름 (ui UiIcon) */
    val icon: String,
    val createdAtEpochDay: Long,
    val sortOrder: Int,
    val achievedAtEpochDay: Long? = null,
    val used: Boolean = false,
)

/** 모의로 산 물건. 실제 돈과 무관한 게임 기록이다. */
@Entity(tableName = "mock_purchase")
data class MockPurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    /** com.simbio.don.game.ItemKind 이름 */
    val kind: String,
    /** 공유해 온 쇼핑몰 (없으면 null) */
    val store: String? = null,
    val epochDay: Long,
    /** com.simbio.don.game.PurchaseStatus 이름 */
    val status: String,
    val createdAtEpochSecond: Long,
)

/** 내 집과 캐릭터. 한 줄만 쓴다 (id = 0). */
@Entity(tableName = "house")
data class HouseEntity(
    @PrimaryKey val id: Int = 0,
    /** com.simbio.don.game.HouseLevel 이름 */
    val level: String = "ONE_ROOM",
    val characterName: String = "나",
)
