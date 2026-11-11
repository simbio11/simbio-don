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

    /**
     * 자주 쓴 지출 분류 순서 (많이 쓴 순, 같으면 최근 순). 입력 화면에서 이 순서로 앞에 보여 준다.
     * 고정 지출·이체·수입은 빼고, 내가 고른 생활비·예외 지출만 센다.
     */
    fun categories(entries: List<EntryEntity>): List<Category> =
        entries.asSequence()
            .filter { (it.type == EntryType.SPEND.name || it.type == EntryType.EXCEPTION.name) && it.amount > 0 }
            .groupBy { it.category }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<EntryEntity>>> { it.value.size }.thenByDescending { e -> e.value.maxOf { it.epochDay } })
            .map { Category.of(it.key) }
            .filter { !it.income }
            .distinct()
}
