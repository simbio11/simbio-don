package com.simbio.don.game

import com.simbio.don.budget.WeekResult
import java.time.LocalDate

enum class PurchaseStatus {
    /** 주중: 결제했고 주말 결산을 기다림 */
    SHIPPING,

    /** 결산에서 예산을 지켜 집에 도착 (먹을거리는 쓰레기로 남음) */
    DELIVERED,

    /** 결산에서 초과분만큼 반품 */
    RETURNED,

    /** 집에서 비움 (도감에는 남음) */
    DISCARDED,

    /** 먹고 남은 쓰레기를 분리수거함 */
    RECYCLED,
}

/** 모의로 산 물건 하나 (게임 계산용). */
data class MockItem(
    val id: Long,
    val name: String,
    val price: Long,
    val kind: ItemKind,
    val date: LocalDate,
    val status: PurchaseStatus,
)

/** 모의 지갑 = 이번 달 가용액 − 실제로 쓴 돈 − 이번 주 아직 돌려받지 않은 모의 구매. */
object MockWallet {
    fun balance(disposable: Long, realSpent: Long, pending: List<MockItem>): Long =
        disposable - realSpent - pending.filter { it.status == PurchaseStatus.SHIPPING }.sumOf { it.price }

    fun canAfford(balance: Long, price: Long) = price in 1..balance
}

/**
 * 주말 결산. 실제 예산을 지키면 배송, 넘기면 넘긴 금액만큼 반품.
 * 반품할 물건은 사용자가 고르고, 고르지 않고 정오가 지나면 [autoPick]으로 정한다.
 */
object WeeklySettlement {
    /** 그 주 모의 구매 (결산 대기 중) */
    fun shippingIn(items: List<MockItem>, start: LocalDate, end: LocalDate) =
        items.filter { it.status == PurchaseStatus.SHIPPING && it.date in start..end }

    /** 고른 물건으로 초과액을 채웠는지. 모의 구매가 초과액보다 적으면 전부 골라야 한다. */
    fun isEnough(week: List<MockItem>, selected: Set<Long>, overspent: Long): Boolean {
        if (overspent <= 0) return true
        val need = minOf(overspent, week.sumOf { it.price })
        return week.filter { it.id in selected }.sumOf { it.price } >= need
    }

    /** 자동으로 고를 때: 가장 나중에 산 것부터 초과액이 채워질 때까지. */
    fun autoPick(week: List<MockItem>, overspent: Long): Set<Long> {
        if (overspent <= 0) return emptySet()
        var sum = 0L
        val picked = mutableSetOf<Long>()
        for (item in week.sortedWith(compareByDescending<MockItem> { it.date }.thenByDescending { it.id })) {
            if (sum >= overspent) break
            picked += item.id
            sum += item.price
        }
        return picked
    }

    /** 이번 주 참은 금액 = 그 주 모의 구매 전부 (반품 여부와 관계없이 실제로는 안 쓴 돈). */
    fun resisted(week: List<MockItem>): Long = week.sumOf { it.price }
}

/** 집 단계. 단계가 오를수록 칸이 커진다 (이사는 M2). */
enum class HouseLevel(val label: String, val capacity: Map<Slot, Int>) {
    ONE_ROOM("원룸", mapOf(Slot.WARDROBE to 6, Slot.SHOE_RACK to 4, Slot.DESK to 4, Slot.SHELF to 6)),
    TWO_ROOM("투룸", mapOf(Slot.WARDROBE to 12, Slot.SHOE_RACK to 8, Slot.DESK to 8, Slot.SHELF to 12)),
    APARTMENT("아파트", mapOf(Slot.WARDROBE to 20, Slot.SHOE_RACK to 14, Slot.DESK to 12, Slot.SHELF to 20)),
}

/** 집 안 모습: 칸마다 들어간 물건, 넘쳐서 바닥에 쌓인 물건, 쓰레기. */
data class HouseLayout(
    val level: HouseLevel,
    val slots: Map<Slot, List<MockItem>>,
    val floor: List<MockItem>,
    val trash: List<MockItem>,
) {
    val itemCount: Int get() = slots.values.sumOf { it.size } + floor.size
    val capacity: Int get() = level.capacity.values.sum()
    val isCluttered: Boolean get() = floor.isNotEmpty()

    /** 집에 있는 물건을 실제로 샀다면 든 돈 (쓰레기 포함) */
    val wouldHaveCost: Long get() = (slots.values.flatten() + floor + trash).sumOf { it.price }

    companion object {
        /** 도착한 물건을 오래된 것부터 칸에 넣고, 넘치면 바닥으로. */
        fun of(level: HouseLevel, items: List<MockItem>): HouseLayout {
            val home = items.filter { it.status == PurchaseStatus.DELIVERED }.sortedWith(compareBy({ it.date }, { it.id }))
            val (food, goods) = home.partition { it.kind.isFood }
            val slots = mutableMapOf<Slot, MutableList<MockItem>>()
            val floor = mutableListOf<MockItem>()
            goods.forEach { item ->
                val list = slots.getOrPut(item.kind.slot) { mutableListOf() }
                if (list.size < (level.capacity[item.kind.slot] ?: 0)) list += item else floor += item
            }
            return HouseLayout(level, slots, floor, food)
        }
    }
}

enum class Mood(val label: String) {
    HAPPY("신났어요"),
    CALM("평온해요"),
    WORRIED("조마조마해요"),
    CHEER("다시 해보자!"),
}

object Moods {
    /**
     * 이번 주 예산을 쓰는 속도로 표정을 정한다. 넘겨도 혼내지 않고 응원한다.
     * 지난 날 비율보다 덜 썼으면 신남, 비슷하면 평온, 더 빨리 쓰면 조마조마, 넘기면 응원.
     */
    fun of(week: WeekResult?, today: LocalDate): Mood {
        if (week == null || week.budget <= 0) return Mood.CALM
        if (week.overspent > 0) return Mood.CHEER
        val elapsed = (today.toEpochDay() - week.week.start.toEpochDay() + 1).coerceIn(1, week.week.days.toLong())
        val expected = week.budget * elapsed / week.week.days
        return when {
            week.spent <= expected * 8 / 10 -> Mood.HAPPY
            week.spent <= expected * 11 / 10 -> Mood.CALM
            else -> Mood.WORRIED
        }
    }
}
