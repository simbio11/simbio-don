package com.simbio.don.app.data

import com.simbio.don.budget.EntryType
import com.simbio.don.budget.Won

/** 비서가 찾은 문제 하나 (폰 안에서만 찾는다) */
data class Finding(
    val kind: Kind,
    val count: Int,
    /** 관련된 돈 합 (없으면 0) */
    val amount: Long,
    val title: String,
    val detail: String,
    /** 고쳐 볼 기록 (눌러서 연다) */
    val entries: List<EntryEntity> = emptyList(),
) {
    enum class Kind(val label: String) {
        ASK("물어볼 알림"),
        SALARY_WAITING("확인 안 한 월급"),
        BALANCE_GAP("알림 잔액과 다른 통장"),
        ACCOUNT_UNKNOWN("어느 통장인지 모르는 기록"),
        MOVE_QUESTION("내 통장끼리인지 모르는 송금"),
        HALF_MOVE("한쪽 알림만 온 이체"),
        DUPLICATE("두 번 적혔을 수 있는 돈"),
        SAME_ACCOUNT_MOVE("같은 통장끼리 이체"),
    }
}

/**
 * 비서의 점검: 지금 기록·질문에서 꼬인 곳을 찾는다. 알림 글·가게 이름은 [anonSummary]로 내보내지 않는다.
 */
object AssistantCheck {
    /** 한쪽 알림만 온 이체를 이만큼 기다렸으면 문제로 본다 (다른 쪽 알림을 못 받은 것) */
    const val HALF_MOVE_DAYS = 3L

    /** 다른 앱에서 같은 금액이 이 안에 또 적히면 두 번 적힌 것 같다 */
    const val DUPLICATE_SECONDS = 10 * 60L

    fun find(
        entries: List<EntryEntity>,
        asks: List<ReviewItemEntity>,
        gaps: List<BalanceGapEntity>,
        accountQuestions: Int,
        moveQuestions: Int,
        todayEpochDay: Long,
    ): List<Finding> {
        val out = mutableListOf<Finding>()
        val salary = asks.filter { AskGuess.decode(it.guess)?.kind == AskKind.SALARY }
        if (salary.isNotEmpty()) {
            out += Finding(
                Finding.Kind.SALARY_WAITING, salary.size, salary.sumOf { it.amount },
                "월급 같은 입금 ${salary.size}건을 아직 확인 안 했어요",
                "맞다고 눌러야 새 달을 시작하고 생활비를 다시 나눠요. 그 전엔 이번 달 계산 그대로예요.",
            )
        }
        val rest = asks - salary.toSet()
        if (rest.isNotEmpty()) {
            out += Finding(
                Finding.Kind.ASK, rest.size, rest.sumOf { it.amount },
                "물어볼 알림 ${rest.size}건",
                "이체·입금·송금처럼 애매한 건 바로 적지 않았어요. 짐작이 맞으면 \"맞아요\"만 누르면 돼요.",
            )
        }
        if (gaps.isNotEmpty()) {
            out += Finding(
                Finding.Kind.BALANCE_GAP, gaps.size, gaps.sumOf { kotlin.math.abs(it.real - it.app) },
                "알림 잔액과 앱 잔액이 다른 통장 ${gaps.size}곳",
                "놓친 알림이 있거나 두 번 적힌 게 있어요. 홈의 잔액 카드에서 바로 맞출 수 있어요.",
            )
        }
        if (accountQuestions > 0) {
            out += Finding(
                Finding.Kind.ACCOUNT_UNKNOWN, accountQuestions, 0,
                "어느 통장·카드인지 모르는 기록 ${accountQuestions}건",
                "한 번 골라 주면 그 앱·카드 끝자리를 기억해서 다음부터는 저절로 넣어요.",
            )
        }
        if (moveQuestions > 0) {
            out += Finding(
                Finding.Kind.MOVE_QUESTION, moveQuestions, 0,
                "내 통장끼리 옮긴 건지 모르는 송금 ${moveQuestions}건",
                "내 돈을 옮긴 거면 지출이 아니라 이체로 바꿔요. 저축이면 생활비를 다시 나눠요.",
            )
        }
        val half = entries.filter {
            it.type == EntryType.TRANSFER.name && (it.moveState == 1 || it.moveState == 4) && todayEpochDay - it.epochDay >= HALF_MOVE_DAYS
        }
        if (half.isNotEmpty()) {
            out += Finding(
                Finding.Kind.HALF_MOVE, half.size, half.sumOf { it.amount },
                "한쪽 알림만 온 이체 ${half.size}건",
                "받은(또는 보낸) 쪽 통장 알림이 안 왔어요. 그 은행 앱 알림이 꺼졌거나, 알림 읽을 앱에서 빠졌을 수 있어요. 이체는 그대로 맞게 적혀 있어요.",
                half,
            )
        }
        val same = entries.filter { it.type == EntryType.TRANSFER.name && it.accountId != null && it.accountId == it.toAccountId }
        if (same.isNotEmpty()) {
            out += Finding(
                Finding.Kind.SAME_ACCOUNT_MOVE, same.size, same.sumOf { it.amount },
                "보낸 통장과 받은 통장이 같은 이체 ${same.size}건",
                "어느 통장으로 갔는지 다시 골라 주세요. 잔액에는 아무 영향이 없어 틀린 게 안 보일 수 있어요.",
                same,
            )
        }
        val dup = duplicates(entries)
        if (dup.isNotEmpty()) {
            out += Finding(
                Finding.Kind.DUPLICATE, dup.size, dup.sumOf { it.first.amount },
                "두 번 적혔을 수 있는 돈 ${dup.size}쌍",
                "다른 앱(카드사·페이·은행)이 같은 결제를 따로 알려 왔을 수 있어요. 하나를 지우거나 그대로 두세요.",
                dup.flatMap { listOf(it.first, it.second) },
            )
        }
        return out
    }

    /** 다른 앱 알림으로 같은 금액·같은 방향이 몇 분 안에 두 번 적힌 것 */
    fun duplicates(entries: List<EntryEntity>): List<Pair<EntryEntity, EntryEntity>> {
        val money = entries.filter {
            it.postedAtEpochSecond != null && it.packageName != null && it.installmentId == null && it.fixedId == null &&
                (it.type == EntryType.SPEND.name || it.type == EntryType.INCOME.name)
        }.sortedBy { it.postedAtEpochSecond }
        val out = mutableListOf<Pair<EntryEntity, EntryEntity>>()
        val used = mutableSetOf<Long>()
        for ((i, a) in money.withIndex()) {
            if (a.id in used) continue
            for (j in i + 1 until money.size) {
                val b = money[j]
                if (b.postedAtEpochSecond!! - a.postedAtEpochSecond!! > DUPLICATE_SECONDS) break
                if (b.id in used || b.type != a.type || b.amount != a.amount || b.packageName == a.packageName) continue
                if (a.accountId != null && b.accountId != null && a.accountId != b.accountId) continue
                out += a to b
                used += a.id
                used += b.id
                break
            }
        }
        return out
    }

    /**
     * AI에게 보낼 점검 요약: 건수·금액·문제 종류·짐작 종류만. 알림 글·가게·사람·통장 이름은 넣지 않는다
     * (통장은 "통장1"처럼 번호로).
     */
    fun anonSummary(
        findings: List<Finding>,
        asks: List<ReviewItemEntity>,
        rules: List<NotifRuleEntity>,
        moveRules: List<MoveRuleEntity>,
        accounts: List<AccountEntity>,
        askFirst: Boolean,
        monthIncome: Long,
        monthLiving: Long,
        disposable: Long,
    ): String = buildString {
        val ids = accounts.filter { !it.hidden }.mapIndexed { i, a -> a.id to "${typeName(a)}${i + 1}" }.toMap()
        appendLine("[설정] 애매한 알림은 물어보고 적기: ${if (askFirst) "켜짐" else "꺼짐"}")
        appendLine("[내 자산] " + accounts.filter { !it.hidden }.joinToString(", ") { ids.getValue(it.id) + if (it.packageName != null) "(알림 받음)" else "(알림 없음)" })
        appendLine("[이번 달] 수입 ${Won.format(monthIncome)} · 생활비로 쓴 돈 ${Won.format(monthLiving)} · 쓸 수 있는 생활비 ${Won.format(disposable)}")
        if (findings.isEmpty()) appendLine("[찾은 문제] 없음")
        findings.forEach { f -> appendLine("[찾은 문제] ${f.kind.label} ${f.count}건" + if (f.amount > 0) ", 합 ${Won.format(f.amount)}" else "") }
        if (asks.isNotEmpty()) {
            appendLine(
                "[물어볼 알림 짐작] " + asks.mapNotNull { AskGuess.decode(it.guess) }.groupingBy { it.kind.label }.eachCount()
                    .entries.joinToString(", ") { "${it.key} ${it.value}" },
            )
            asks.forEach { a ->
                val g = AskGuess.decode(a.guess)
                appendLine(
                    "  - ${if (a.action == "INCOME") "들어온" else "나간"} ${Won.format(a.amount)}, 짐작 ${g?.kind?.label ?: "-"}" +
                        (g?.from?.let { " · ${ids[it] ?: "다른 통장"}에서" } ?: "") + (g?.to?.let { " · ${ids[it] ?: "다른 통장"}으로" } ?: "") +
                        (if (a.rawText.contains(AskGuess.PAIR_SEP)) " · 양쪽 알림 짝지음" else ""),
                )
            }
        }
        val ignore = rules.count { AskGuess.decode(it.decision)?.kind == AskKind.IGNORE }
        appendLine("[기억한 알림] ${rules.size}개 (묻지 않고 적기 ${rules.count { it.auto }}, 무시 $ignore)")
        rules.groupBy { AskGuess.decode(it.decision)?.kind?.label ?: "-" }.forEach { (k, l) -> appendLine("  - $k ${l.size}개, 모두 ${l.sumOf { it.count }}번 씀") }
        appendLine("[배운 내 통장끼리 이체] ${moveRules.size}개")
        moveRules.forEach { r -> appendLine("  - ${r.fromAccountId?.let { ids[it] } ?: "다른 통장"} → ${r.toAccountId?.let { ids[it] } ?: "다른 통장"} (${if (r.side == "OUT") "보낼 때" else "받을 때"}, ${r.count}번)") }
    }.trim()

    private fun typeName(a: AccountEntity) = when (enumOr(a.type, AccountType.ETC)) {
        AccountType.CARD -> "카드"
        AccountType.CASH -> "현금"
        else -> if (enumOr(a.type, AccountType.ETC).grows) "저축" else "통장"
    }
}
