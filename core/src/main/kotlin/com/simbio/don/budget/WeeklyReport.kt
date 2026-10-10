package com.simbio.don.budget

import java.text.NumberFormat
import java.util.Locale

data class WeeklyReport(
    val budget: Long,
    val bonus: Long,
    val spentMock: Long,
    val spentReal: Long,
    val saved: Long,
    val overspent: Long,
) {
    /** 예: "이번 주 12,000원 아꼈어요" */
    val headline: String
        get() = when {
            overspent > 0 -> "이번 주 ${won(overspent)} 초과했어요"
            saved > 0 -> "이번 주 ${won(saved)} 아꼈어요"
            else -> "이번 주 예산을 딱 맞게 썼어요"
        }

    companion object {
        fun from(ledger: WeekLedger) = WeeklyReport(
            budget = ledger.budget,
            bonus = ledger.bonusTotal,
            spentMock = ledger.spends.filter { it.source == SpendSource.MOCK }.sumOf { it.amount },
            spentReal = ledger.spends.filter { it.source == SpendSource.REAL }.sumOf { it.amount },
            saved = ledger.saved,
            overspent = ledger.overspent,
        )

        fun won(amount: Long): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
    }
}
