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
)
