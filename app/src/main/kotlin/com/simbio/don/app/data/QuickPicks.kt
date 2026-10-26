package com.simbio.don.app.data

import com.simbio.don.budget.Category
import com.simbio.don.budget.CategoryGuesser
import com.simbio.don.budget.EntryType

/** 지출 입력에서 한 번에 채우는 "자주 쓰는 곳" */
data class QuickPick(val label: String, val amount: Long, val category: Category)

object QuickPicks {
    /**
     * 최근 기록에서 자주 쓴 곳. 횟수가 많은 순, 같으면 최근 순.
     * 금액·카테고리·표시 이름은 그곳의 가장 최근 기록을 따른다.
     */
    fun from(entries: List<EntryEntity>, limit: Int = 8): List<QuickPick> =
        entries.asSequence()
            .filter { it.type == EntryType.SPEND.name && it.label.isNotBlank() && !it.nameUnknown }
            .groupBy { CategoryGuesser.key(it.label) }
            .values
            .map { list -> list.maxWith(compareBy<EntryEntity> { it.epochDay }.thenBy { it.id }) to list.size }
            .sortedWith(compareByDescending<Pair<EntryEntity, Int>> { it.second }.thenByDescending { it.first.epochDay })
            .take(limit)
            .map { (e, _) -> QuickPick(e.label.trim(), e.amount, Category.of(e.category)) }
}
