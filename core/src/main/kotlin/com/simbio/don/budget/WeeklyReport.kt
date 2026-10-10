package com.simbio.don.budget

import java.text.NumberFormat
import java.util.Locale

object Won {
    fun format(amount: Long): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(amount) + "원"
}

/** 주간 결산 문구. 예: "이번 주 12,000원 아꼈어요" */
fun WeekResult.headline(): String = when {
    overspent > 0 -> "이번 주 ${Won.format(overspent)} 초과했어요"
    savedVsBudget > 0 -> "이번 주 ${Won.format(savedVsBudget)} 아꼈어요 (${savedBps / 100}%)"
    else -> "이번 주 예산을 딱 맞게 썼어요"
}
