package com.simbio.don.app.data

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

fun EntryEntity.toEntry() = Entry(
    date = LocalDate.ofEpochDay(epochDay),
    amount = amount,
    label = label,
    type = EntryType.valueOf(type),
    source = EntrySource.valueOf(source),
)

fun FixedExpenseEntity.toFixed() = FixedExpense(name, amount)

fun AppRuleEntity.toRule() = AppRule(packageName, displayName, enabled, KeywordRuleCodec.decode(keywordRules))

fun AppRule.toEntity() = AppRuleEntity(packageName, displayName, enabled, KeywordRuleCodec.encode(keywordRules))

fun CycleEntity.toPayCycle(payDay: Int): PayCycle {
    val ym = YearMonth.parse(nominal)
    return PayCycle.cycleFor(ym, payDay, actualStart = LocalDate.ofEpochDay(startEpochDay))
}

fun EntryEntity.toParsedOrNull(): ParsedPayment? {
    val pkg = packageName ?: return null
    val posted = postedAtEpochSecond ?: return null
    val action = if (type == EntryType.REFUND.name) RuleAction.REFUND else RuleAction.SPEND
    return ParsedPayment(pkg, action, amount, label, posted.toLocalDateTime(), "")
}

fun ReviewItemEntity.toParsed() =
    ParsedPayment(packageName, RuleAction.valueOf(action), amount, label, postedAtEpochSecond.toLocalDateTime(), rawText)
