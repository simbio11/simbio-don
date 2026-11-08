package com.simbio.don.budget

/** 환산에 쓰는 것의 종류 (한 번에 보여 줄 때 겹치지 않게) */
enum class WorthKind { FOOD, FUN, TRIP, GOODS, RIDE, HOME }

/** 이 돈이면 살 수 있는 것: 대략 값 (원). [unit]은 세는 말 ("그릇", "대") */
data class WorthItem(val name: String, val unit: String, val price: Long, val kind: WorthKind)

/**
 * 모은 돈으로 뭘 할 수 있는지.
 * [big]은 이 돈으로 살 수 있는 가장 큰 것, [also]는 다른 종류의 것들(몇 개나 살 수 있는지), [next]는 다음 목표까지 남은 돈.
 */
data class Worth(val big: String, val also: List<String>, val next: String?) {
    /** 한 줄 ("노트북 1대 · 국밥 180그릇 · 제주 왕복 18번") */
    val line: String get() = (listOf(big) + also).joinToString(" · ")
}

/**
 * 금액 구간마다 다른 것으로 바꿔 보여 준다: 몇천 원은 커피·국밥, 몇십만 원은 여행·운동화,
 * 몇백만 원은 노트북·일본 여행, 몇천만 원은 차 한 대. 값은 대략이다 (정확한 시세가 아님).
 * [seed]를 바꾸면 같은 금액에서도 다른 조합이 나온다 (날마다·눌러서 돌려 보기).
 */
object Worths {
    val items: List<WorthItem> = listOf(
        WorthItem("컵라면", "개", 1_500, WorthKind.FOOD),
        WorthItem("아메리카노", "잔", 4_500, WorthKind.FOOD),
        WorthItem("김밥", "줄", 4_000, WorthKind.FOOD),
        WorthItem("국밥", "그릇", 10_000, WorthKind.FOOD),
        WorthItem("영화", "편", 15_000, WorthKind.FUN),
        WorthItem("치킨", "마리", 22_000, WorthKind.FOOD),
        WorthItem("책", "권", 16_000, WorthKind.FUN),
        WorthItem("한우 외식", "번", 90_000, WorthKind.FOOD),
        WorthItem("제주 왕복 항공권", "장", 100_000, WorthKind.TRIP),
        WorthItem("콘서트 티켓", "장", 150_000, WorthKind.FUN),
        WorthItem("호텔", "박", 150_000, WorthKind.TRIP),
        WorthItem("운동화", "켤레", 130_000, WorthKind.GOODS),
        WorthItem("무선 이어폰", "개", 200_000, WorthKind.GOODS),
        WorthItem("헬스장 3개월권", "개", 300_000, WorthKind.FUN),
        WorthItem("태블릿", "대", 600_000, WorthKind.GOODS),
        WorthItem("일본 3박 4일 여행", "번", 1_300_000, WorthKind.TRIP),
        WorthItem("최신 스마트폰", "대", 1_500_000, WorthKind.GOODS),
        WorthItem("전기자전거", "대", 1_500_000, WorthKind.RIDE),
        WorthItem("노트북", "대", 1_800_000, WorthKind.GOODS),
        WorthItem("OLED TV", "대", 2_500_000, WorthKind.GOODS),
        WorthItem("125cc 스쿠터", "대", 3_000_000, WorthKind.RIDE),
        WorthItem("유럽 열흘 여행", "번", 4_500_000, WorthKind.TRIP),
        WorthItem("중고 경차", "대", 7_000_000, WorthKind.RIDE),
        WorthItem("신형 경차", "대", 15_000_000, WorthKind.RIDE),
        WorthItem("신혼여행 풀코스", "번", 15_000_000, WorthKind.TRIP),
        WorthItem("준중형 세단", "대", 25_000_000, WorthKind.RIDE),
        WorthItem("중형 SUV", "대", 40_000_000, WorthKind.RIDE),
        WorthItem("전기 SUV", "대", 55_000_000, WorthKind.RIDE),
        WorthItem("원룸 전세 보증금", "건", 100_000_000, WorthKind.HOME),
        WorthItem("소형 아파트 전세 보증금", "건", 250_000_000, WorthKind.HOME),
    ).sortedBy { it.price }

    /** 센 말 ("국밥 300그릇") */
    private fun count(item: WorthItem, n: Long): String = "${item.name} ${"%,d".format(n)}${item.unit}"

    /** 다음 목표까지 남은 돈 ("일본 3박 4일 여행까지 120만원") */
    private fun toNext(item: WorthItem, amount: Long): String = "${item.name}까지 ${manwon(item.price - amount)}"

    private fun manwon(v: Long): String = when {
        v >= 100_000_000 -> "%.1f억원".format(v / 100_000_000.0).replace(".0억", "억")
        v >= 10_000 -> "%,d만원".format(v / 10_000)
        else -> "%,d원".format(v)
    }

    /** 0원 이하면 null. 가장 싼 것도 못 사면 다음 목표만 */
    fun of(amount: Long, seed: Long = 0): Worth? {
        if (amount <= 0) return null
        val cheapest = items.first()
        if (amount < cheapest.price) return Worth("${cheapest.name} 한 개까지 ${manwon(cheapest.price - amount)}", emptyList(), null)
        val afford = items.filter { it.price <= amount }
        // 가장 큰 것: 같은 값 중에서는 seed 로 돌린다
        val top = afford.last().price
        val heroes = afford.filter { it.price == top }
        val hero = heroes[Math.floorMod(seed, heroes.size.toLong()).toInt()]
        // 같이 보여 줄 것: 몇 개쯤인지 실감 나는 것(2~1000개)만, 다른 종류를 먼저 — 종류마다 하나씩, 시작은 seed 로 돌린다
        val others = afford.filter { it != hero && amount / it.price in 2..1000 }
        val byKind = others.groupBy { it.kind }
        val order = (byKind.keys - hero.kind).toList() + listOfNotNull(hero.kind.takeIf { it in byKind })
        val picked = mutableListOf<WorthItem>()
        if (order.isNotEmpty()) {
            val start = Math.floorMod(seed / 2, order.size.toLong()).toInt()
            for (j in 0 until minOf(2, order.size)) {
                val of = byKind.getValue(order[(start + j) % order.size])
                picked += of[Math.floorMod(seed + j * 3L, of.size.toLong()).toInt()]
            }
        }
        val next = items.firstOrNull { it.price > amount && it.price >= hero.price }?.let { toNext(it, amount) }
        return Worth(count(hero, amount / hero.price), picked.map { count(it, amount / it.price) }, next)
    }
}
