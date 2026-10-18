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
    /** 기본 소분류를 넣었는지 (한 번만, 지운 것을 되살리지 않게) */
    @ColumnInfo(defaultValue = "0") val subSeeded: Boolean = false,
    /** 지난 결산 마일을 한 번 소급해 줬는지 (0.11) */
    @ColumnInfo(defaultValue = "0") val milesSeeded: Boolean = false,
    /** 마일 키우기: 마지막으로 이자를 붙인 날 (epochDay) */
    val growEpochDay: Long? = null,
)

/** 고정 지출. [day]를 정하면 그날 가계부에 자동으로 적는다 (그 전에 결제 알림이 오면 알림으로). */
@Entity(tableName = "fixed_expense")
data class FixedExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    /** 매달 나가는 날 (1~31, 모르면 null) */
    val day: Int? = null,
    /** 빠져나가는 자산 */
    val accountId: Long? = null,
    @ColumnInfo(defaultValue = "BILLS") val category: String = "BILLS",
    /** 마지막으로 처리한 납부일 (epochDay). 그 기록을 지워도 다시 적지 않게 */
    val lastDueEpochDay: Long? = null,
)

/** 월급이 어디서 왔는지 */
enum class SalarySource {
    /** 처음 설정에서 넣음 */
    ONBOARDING,

    /** 월급 입금 알림(또는 직접 적은 월급 수입)으로 맞춤 */
    NOTIFICATION,

    /** 새 달이 됐지만 입금을 아직 못 봐서 지난달 월급으로 둠 */
    ESTIMATED,

    /** 사용자가 고침 */
    MANUAL,
}

/** 자산 종류 */
enum class AccountType(val label: String) {
    CASH("현금"),
    BANK("은행"),
    CARD("카드"),
    ETC("기타"),
}

/** 자산 (현금·통장·카드). 잔액 = 처음 잔액 + 이 자산으로 적은 수입·지출·이체. */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [AccountType] 이름 */
    val type: String,
    /** 처음 잔액 (카드는 보통 0, 갚을 돈이 있으면 음수) */
    val opening: Long,
    val sortOrder: Int,
    val hidden: Boolean = false,
    /** 이 앱의 결제 알림은 이 자산으로 적는다 */
    val packageName: String? = null,
)

/** 한 주기(월급날 ~ 다음 월급날 전날). 처음 설정 때 만들고, 그다음부터는 월급날마다 자동으로 만든다. */
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
    /** [SalarySource] 이름 */
    @ColumnInfo(defaultValue = "MANUAL") val salarySource: String = SalarySource.MANUAL.name,
    /** 새 달 시작 안내를 봤는지 */
    @ColumnInfo(defaultValue = "1") val noticeShown: Boolean = true,
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
    /** 돈이 나가거나 들어온 자산 (이체면 보낸 자산) */
    val accountId: Long? = null,
    /** 이체 받은 자산 */
    val toAccountId: Long? = null,
    /** 시각 (0시부터 분, 모르면 null) */
    val minuteOfDay: Int? = null,
    /** 고정 지출에서 온 기록 */
    val fixedId: Long? = null,
    /** 소분류 */
    val sub: String? = null,
    /** 할부에서 온 기록과 몇 번째인지 ("2/6") */
    val installmentId: Long? = null,
    val installmentNo: String? = null,
    /** 반복 수입에서 온 기록 */
    val incomeId: Long? = null,
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
    /** 서비스면 내용 ("look:…" = 뷰티샵 시술, 도착하면 얼굴·머리가 바뀐다) */
    val payload: String? = null,
    /** 도착할 때 정한 카드 반짝이 (com.simbio.don.game.CardGrade 이름) */
    val grade: String? = null,
)

/** 내 집과 캐릭터. 한 줄만 쓴다 (id = 0). */
@Entity(tableName = "house")
data class HouseEntity(
    @PrimaryKey val id: Int = 0,
    /** com.simbio.don.game.HouseLevel 이름 */
    val level: String = "ONE_ROOM",
    val characterName: String = "나",
    /** 캐릭터 모습 (com.simbio.don.game.CharacterLook.encode). 비면 기본 */
    val look: String = "",
    /** 수납 가구·인테리어 (com.simbio.don.game.HouseSetup.encode) */
    @ColumnInfo(defaultValue = "") val setup: String = "",
    /** 옷장에서 산 것 ("top:knit,hat:cap") */
    @ColumnInfo(defaultValue = "") val owned: String = "",
    /** 첫 꾸미기는 공짜 (얼굴·머리 바꾸기 한 번) */
    @ColumnInfo(defaultValue = "1") val freeMakeover: Boolean = true,
)

/** 직접 만든 카테고리. [name]은 "U" + 번호 (기록에는 이 이름이 저장된다) */
@Entity(tableName = "category_def")
data class CategoryDefEntity(
    @PrimaryKey val name: String,
    val label: String,
    val income: Boolean,
    /** ui/kit 아이콘 이름 */
    val icon: String,
    /** ARGB */
    val color: Long,
    val sortOrder: Int = 0,
)

/** 소분류. 카테고리마다 순서대로 */
@Entity(tableName = "subcategory")
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** com.simbio.don.budget.Category 이름 */
    val category: String,
    val name: String,
    val sortOrder: Int = 0,
)

/** 할부 하나. 달마다 나눈 기록이 [EntryEntity.installmentId]로 이어진다 */
@Entity(tableName = "installment")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val total: Long,
    val months: Int,
    /** 처음 낸 날 (산 날) */
    val firstEpochDay: Long,
    val category: String,
    val accountId: Long? = null,
)

/** 반복 수입 (월급 말고). [day]일에 가계부에 저절로 적고, 입금 알림이 오면 알림으로 고친다 */
@Entity(tableName = "recurring_income")
data class RecurringIncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val day: Int,
    val accountId: Long? = null,
    @ColumnInfo(defaultValue = "SIDE_INCOME") val category: String = "SIDE_INCOME",
    val lastDueEpochDay: Long? = null,
)

/** 마일이 오간 기록. 지갑([account] = WALLET)과 키우기(GROW)의 잔액은 이 기록의 합 */
@Entity(tableName = "mile_tx", indices = [androidx.room.Index(value = ["refKey"], unique = true)])
data class MileTxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** 들어오면 +, 나가면 − */
    val amount: Long,
    /** EARN · BONUS · BUY · DEPOSIT · WITHDRAW · INTEREST */
    val kind: String,
    /** WALLET · GROW */
    val account: String = "WALLET",
    val memo: String = "",
    /** 같은 결산을 두 번 적립하지 않게 ("2026-10#2:EARN"), 그 밖엔 null */
    val refKey: String? = null,
)
