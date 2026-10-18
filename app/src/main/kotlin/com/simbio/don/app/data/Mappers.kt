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

fun EntryEntity.toEntry() = Entry(
    date = LocalDate.ofEpochDay(epochDay),
    amount = amount,
    label = label,
    type = enumOr(type, EntryType.SPEND),
    source = enumOr(source, EntrySource.MANUAL),
    category = Category.of(category),
)

fun FixedExpenseEntity.toFixed() = FixedExpense(name, amount, day)

fun AppRuleEntity.toRule() = AppRule(packageName, displayName, enabled, KeywordRuleCodec.decode(keywordRules))

fun AppRule.toEntity() = AppRuleEntity(packageName, displayName, enabled, KeywordRuleCodec.encode(keywordRules))

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
    val action = when (type) {
        EntryType.REFUND.name -> RuleAction.REFUND
        EntryType.INCOME.name -> RuleAction.INCOME
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
)
