package com.simbio.don.app.data

import com.simbio.don.budget.EntryType

/** 처음부터 "한 달 예산에서" 쓰는 곳으로 보는 이름 (주유소) — 설정에서 빼거나 더할 수 있다 */
const val MONTHLY_DEFAULT = "주유,오일뱅크,SK에너지,GS칼텍스,S-OIL,에쓰오일,알뜰주유,SK엔크린"

/**
 * "한 달 예산에서" 쓰는 돈 (주유비·가전·옷처럼 한 달에 한 번 큰돈).
 * 가계부·통계·한 달 쓴 돈에는 그대로 들어가고, 날마다 보는 것(달력 빨강·잔디·무지출·"이대로 쓰면" 속도·오늘 몫)에서는 뺀다.
 * 주 예산에서는 그날부터 이번 달 남은 날 모두에 나눠 뺀다 ([DonRepository] ledgerOf).
 */
object MonthlySpend {
    fun places(csv: String?): List<String> = csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun join(places: List<String>): String = places.map { it.trim().replace(",", " ") }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    /** 이름에 기억한 곳이 들어 있으면 */
    fun matches(label: String, places: List<String>): Boolean = label.isNotBlank() && places.any { label.contains(it, ignoreCase = true) }

    /** 한 달 예산에서 쓴 생활비 지출 */
    fun isMonthly(e: EntryEntity): Boolean = e.bucket == 2 && e.type == EntryType.SPEND.name

    /** 날마다 보는 것에 넣을 기록 (한 달 예산에서 쓴 것 빼고) */
    fun daily(list: List<EntryEntity>): List<EntryEntity> = list.filterNot(::isMonthly)
}
