package com.simbio.don.game

import java.time.LocalDate

/**
 * 도감: 돈't 몰 상품 한 칸씩. 모의로 사서 **도착한 적 있는** 물건이 채워진다 (비우거나 분리수거해도 남는다).
 * 칸마다 몇 번 들였는지, 가장 좋은 반짝이(그 주 아낀 비율), 처음 들인 날.
 * 몰에 없는 물건(직접 적은 것·네이버 검색·공유)은 "내가 고른 물건"에 이름별로 모인다.
 */
data class DexEntry(val product: Product, val count: Int, val best: CardGrade?, val first: LocalDate?) {
    val got: Boolean get() = count > 0
}

/** 몰에 없는 물건 (이름별) */
data class DexOther(val name: String, val kind: ItemKind, val count: Int, val best: CardGrade?, val first: LocalDate)

object Dex {
    /** 도착한 적 있는 상태 */
    val ARRIVED = setOf(PurchaseStatus.DELIVERED, PurchaseStatus.DISCARDED, PurchaseStatus.RECYCLED)

    /** 도감에 들어가는 물건 (뷰티샵 시술 같은 서비스는 뺀다) */
    fun arrived(items: List<MockItem>): List<MockItem> = items.filter { it.status in ARRIVED && it.payload == null }

    /** 몰에서 산 이름("오버핏 맨투맨 (M)")에서 상품 찾기 — 옵션 괄호는 뗀다 */
    fun productOf(name: String): Product? {
        val base = name.trim().replace(Regex("""\s*\([^()]*\)$"""), "").trim()
        return Catalog.products.firstOrNull { it.name == base }
    }

    fun entries(items: List<MockItem>): List<DexEntry> {
        val byProduct = arrived(items).groupBy { productOf(it.name)?.id }
        return Catalog.products.map { p ->
            val mine = byProduct[p.id].orEmpty()
            DexEntry(p, mine.size, mine.mapNotNull { it.grade }.maxOrNull(), mine.minOfOrNull { it.date })
        }
    }

    fun others(items: List<MockItem>): List<DexOther> =
        arrived(items).filter { productOf(it.name) == null }
            .groupBy { it.name }
            .map { (name, list) -> DexOther(name, list.first().kind, list.size, list.mapNotNull { it.grade }.maxOrNull(), list.minOf { it.date }) }
            .sortedByDescending { it.first }

    /** 채운 칸 id */
    fun gotIds(items: List<MockItem>): Set<String> = arrived(items).mapNotNull { productOf(it.name)?.id }.toSet()

    /** 이번 결산으로 새로 채워질 칸 (이미 채운 칸은 빼고) */
    fun newIds(before: List<MockItem>, arriving: List<MockItem>): Set<String> =
        arriving.filter { it.payload == null }.mapNotNull { productOf(it.name)?.id }.toSet() - gotIds(before)

    /** 칸 수에 따른 칭호 (금액과 상관없이 모은 가짓수로) */
    val MILESTONES: List<Pair<Int, String>> = listOf(10 to "수집 입문", 38 to "수집가", Catalog.products.size to "도감 마스터")
}

/**
 * 세트: 몰 상품 네 개 묶음. 다 모으면 칭호를 받는다 (마일은 주지 않는다 — 마일은 실제로 아꼈을 때만).
 * 비싼 물건만 모으는 세트는 두지 않는다 (월급이 많아야 모으는 놀거리가 되지 않게).
 */
data class ItemSet(val id: String, val name: String, val title: String, val productIds: List<String>) {
    val products: List<Product> get() = productIds.mapNotNull { Catalog.byId(it) }
}

object Sets {
    val all: List<ItemSet> = listOf(
        ItemSet("work", "출근 준비", "갓생 직장인", listOf("c6", "s6", "b3", "l6")),
        ItemSet("fit", "운동 시작", "오운완", listOf("c7", "s2", "e3", "e1")),
        ItemSet("movie", "집콕 영화관", "방구석 영화광", listOf("e10", "l3", "m1", "d6")),
        ItemSet("cafe", "홈카페", "홈카페 사장님", listOf("l5", "d3", "k1", "l1")),
        ItemSet("winter", "겨울나기", "추위 몰라요", listOf("c5", "c3", "s3", "c9")),
        ItemSet("game", "게이머", "프로게이머", listOf("e2", "e7", "e9", "t2")),
        ItemSet("trip", "여행 가자", "프로 여행러", listOf("b6", "x5", "x4", "e8")),
        ItemSet("study", "공부하는 나", "자기계발러", listOf("k2", "k5", "k4", "d1")),
        ItemSet("snack", "분식 데이", "분식 마스터", listOf("m3", "m2", "d2", "m7")),
        ItemSet("beauty", "꾸미기", "뷰티 고수", listOf("y2", "y3", "y6", "y1")),
        ItemSet("hobby", "취미 부자", "취미 부자", listOf("t1", "t5", "t4", "t3")),
        ItemSet("date", "데이트", "로맨티스트", listOf("x1", "x2", "x3", "m5")),
        ItemSet("home", "자취 필수", "살림 9단", listOf("l4", "l8", "l2", "s4")),
        ItemSet("street", "스트리트", "패션 피플", listOf("c1", "c10", "s1", "b2")),
    )

    fun byId(id: String): ItemSet? = all.firstOrNull { it.id == id }

    fun done(set: ItemSet, got: Set<String>): Int = set.productIds.count { it in got }

    fun isComplete(set: ItemSet, got: Set<String>): Boolean = set.productIds.all { it in got }

    fun completed(got: Set<String>): List<ItemSet> = all.filter { isComplete(it, got) }

    /** 이번에 새로 다 모은 세트 */
    fun newlyCompleted(before: Set<String>, after: Set<String>): List<ItemSet> =
        all.filter { !isComplete(it, before) && isComplete(it, after) }

    /** 고를 수 있는 칭호: 다 모은 세트 + 도감 칸 수 */
    fun titles(got: Set<String>): List<String> =
        completed(got).map { it.title } + Dex.MILESTONES.filter { got.size >= it.first }.map { it.second }
}
