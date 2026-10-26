package com.simbio.don.app.data

import com.simbio.don.budget.Category
import com.simbio.don.budget.Entry
import com.simbio.don.budget.EntrySource
import com.simbio.don.budget.EntryType
import com.simbio.don.budget.FixedExpense
import com.simbio.don.budget.PayCycle
import com.simbio.don.notify.AppRule
import com.simbio.don.notify.KeywordRuleCodec
import com.simbio.don.notify.ParsedPayment
import com.simbio.don.notify.RuleAction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

internal val zone: ZoneId get() = ZoneId.systemDefault()

internal fun LocalDateTime.epochSecond(): Long = atZone(zone).toEpochSecond()
internal fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(this), zone)

/** 저장된 이름을 enum으로. 모르는 이름(예: 새 버전에서 쓰다 되돌린 경우)은 [default]로 — 앱이 죽지 않게 */
inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/**
 * 예산·통계용 기록. 나눠 낸 지출은 내 몫만 — 내 몫이 0이면(남 것을 대신 내 줌) 지출이 아니라 이체처럼 센다.
 * 잔액은 [AccountBalances]가 낸 돈 전부로 따로 센다.
 */
fun EntryEntity.toEntry(): Entry {
    val t = enumOr(type, EntryType.SPEND)
    val mine = share?.takeIf { t.spendLike }
    return Entry(
        date = LocalDate.ofEpochDay(epochDay),
        amount = if (mine != null && mine > 0) mine else amount,
        label = label,
        type = if (mine == 0L) EntryType.TRANSFER else t,
        source = enumOr(source, EntrySource.MANUAL),
        category = Category.of(category),
        sub = sub,
    )
}

/** 내 몫을 나눌 수 있는 지출 (생활비·예외·고정) */
val EntryType.spendLike: Boolean get() = this == EntryType.SPEND || this == EntryType.EXCEPTION || this == EntryType.FIXED

fun CategoryDefEntity.toCategory() = Category.custom(name, label, income, icon, color)

fun RecurringIncomeEntity.toRecurring() = com.simbio.don.budget.RecurringIncome(name, amount, day)

fun FixedExpenseEntity.toFixed() = FixedExpense(name, amount, day)

fun AppRuleEntity.toRule() = AppRule(packageName, displayName, enabled, KeywordRuleCodec.decode(keywordRules), com.simbio.don.notify.MerchantSpot.decode(merchantSpot))

fun AppRule.toEntity() = AppRuleEntity(packageName, displayName, enabled, KeywordRuleCodec.encode(keywordRules), merchantSpot?.encode())

/**
 * 저장된 주기 → 기간. 끝은 다음 달 예상 월급날 전날 (다음 주기가 있으면 부르는 쪽이 그 시작 전날로 줄인다).
 * 월급날을 바꾸면 옛 주기는 끝이 시작보다 앞설 수 있어 시작일로 맞춘다 (앱이 죽지 않게).
 */
fun CycleEntity.toPayCycle(payDay: Int): PayCycle {
    val ym = YearMonth.parse(nominal)
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = PayCycle.expectedPayday(ym.plusMonths(1), payDay).minusDays(1)
    return PayCycle(ym, start, if (end.isBefore(start)) start else end)
}

fun EntryEntity.toParsedOrNull(): ParsedPayment? {
    val pkg = packageName ?: return null
    val posted = postedAtEpochSecond ?: return null
    val action = when {
        type == EntryType.REFUND.name -> RuleAction.REFUND
        // 정산으로 바꾼 입금도 알림으로는 입금 (다른 앱에 온 같은 입금 알림과 짝)
        type == EntryType.INCOME.name || settleFor != null -> RuleAction.INCOME
        else -> RuleAction.SPEND
    }
    return ParsedPayment(pkg, action, amount, label, posted.toLocalDateTime(), "")
}

fun ReviewItemEntity.toParsed() =
    ParsedPayment(packageName, enumOr(action, RuleAction.SPEND), amount, label, postedAtEpochSecond.toLocalDateTime(), rawText)

fun MockPurchaseEntity.toItem() = com.simbio.don.game.MockItem(
    id = id,
    name = name,
    price = price,
    kind = com.simbio.don.game.ItemKind.of(kind),
    date = LocalDate.ofEpochDay(epochDay),
    status = enumOr(status, com.simbio.don.game.PurchaseStatus.DISCARDED),
    createdAt = createdAtEpochSecond.toLocalDateTime(),
    payload = payload,
    grade = grade?.let { enumOr(it, com.simbio.don.game.CardGrade.NORMAL) },
)

fun PlanEntity.toPlan() = com.simbio.don.budget.Plan(
    id = id,
    date = java.time.LocalDate.ofEpochDay(epochDay),
    amount = amount,
    label = label,
    category = com.simbio.don.budget.Category.of(category),
    actual = doneAmount,
    canceled = canceled,
)
