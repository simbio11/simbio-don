package com.simbio.don.game

import kotlin.math.roundToLong
import kotlin.random.Random

/** 미니게임 종류 */
enum class MiniGame(val label: String, val blurb: String) {
    QUIZ("얼마일까?", "물건 값을 맞혀요"),
    MEMORY("짝 맞추기", "같은 그림 두 장을 찾아요"),
}

/**
 * 미니게임 마일. 실제로 아낀 만큼만 마일이 쌓이도록 **이번 주 결산으로 받은 마일의 10%**까지만 준다.
 * 결산 마일이 0이면(예산을 넘겼거나 아직 결산 전) 미니게임은 마일 없이 놀기만 한다.
 */
object MiniGameRewards {
    const val CAP_BPS = 1_000

    /** 이번 주 미니게임 상한 */
    fun weeklyCap(settledThisWeek: Long): Long = settledThisWeek.coerceAtLeast(0) * CAP_BPS / 10_000

    /** 이번 판에 실제로 받는 마일 (상한에서 이미 받은 만큼 빼고) */
    fun grant(reward: Long, cap: Long, already: Long): Long = reward.coerceIn(0, (cap - already).coerceAtLeast(0))
}

/**
 * 얼마일까?: 몰 물건을 보고 세 값 중 진짜 값을 고른다. 다섯 문제, 맞힌 문제마다 10마일.
 * 고른 뒤에 "월급의 몇 %, 몇 시간 일한 돈"을 보여줘 값의 무게를 느끼게 한다.
 */
object PriceQuiz {
    const val ROUNDS = 5
    const val MILES_PER_RIGHT = 10L

    /** [source]는 실제 상품이면 쇼핑몰 이름 (네이버 쇼핑 최저가) */
    data class Question(val product: Product, val choices: List<Long>, val source: String? = null) {
        val answer: Long get() = product.price
    }

    fun questions(seed: Long, rounds: Int = ROUNDS): List<Question> {
        val r = Random(seed)
        return Catalog.products.shuffled(r).take(rounds).map { p -> questionOf(p, r) }
    }

    fun questionOf(p: Product, r: Random, source: String? = null): Question = Question(p, (distractors(p.price, r) + p.price).shuffled(r), source)

    /**
     * 실제 상품 문제에 쓸 검색어 (흔히 사는 물건). 이 검색어만 네이버 쇼핑 검색 API로 보낸다.
     * 문제 수보다 조금 더 뽑아 둔다 (검색 결과가 없는 것에 대비).
     */
    val REAL_QUERIES = listOf(
        "무선 이어폰", "운동화", "백팩", "텀블러", "전기포트", "블루투스 스피커", "기계식 키보드", "향수", "레고", "에어프라이어",
        "가습기", "후드티", "롱패딩", "스마트워치", "보조배터리", "캠핑 의자", "캡슐 커피머신", "무선 마우스", "전동칫솔", "헤어드라이어",
        "보드게임", "다이어리", "선글라스", "러닝화", "니트 가디건", "무드등", "공기청정기", "태블릿 거치대", "립스틱", "곰 인형",
    )

    fun realQueries(seed: Long, n: Int = ROUNDS + 3): List<String> = REAL_QUERIES.shuffled(Random(seed)).take(n)

    private val FACTORS = listOf(0.5, 0.6, 0.75, 1.3, 1.6, 2.0)

    /**
     * 그럴듯한 틀린 값 두 개 (진짜 값과 겹치지 않게). 진짜 값과 같은 자릿수로 끊어 (23,450원이면 10원 단위)
     * 끝자리만 보고 정답을 알 수 없게 한다.
     */
    fun distractors(price: Long, r: Random): List<Long> {
        val out = mutableListOf<Long>()
        val g = granularity(price)
        for (f in FACTORS.shuffled(r)) {
            val v = ((price * f / g).roundToLong() * g).coerceAtLeast(g)
            if (v != price && v !in out && v > 0) out += v
            if (out.size == 2) break
        }
        return out
    }

    /** 값의 끝자리 단위: 23,457 → 1, 23,450 → 10, 29,900 → 100, 39,000 → 1,000 (1만 원까지) */
    fun granularity(price: Long): Long {
        var g = 1L
        while (g < 10_000 && price % (g * 10) == 0L) g *= 10
        return g
    }

    /** 값처럼 보이게 반올림: 1만 원 아래 100원, 10만 원 아래 1,000원, 그 위 1만 원 */
    fun nice(v: Long): Long {
        val unit = when {
            v < 10_000 -> 100L
            v < 100_000 -> 1_000L
            else -> 10_000L
        }
        return ((v + unit / 2) / unit * unit).coerceAtLeast(unit)
    }

    fun reward(right: Int): Long = right.coerceIn(0, ROUNDS) * MILES_PER_RIGHT
}

/**
 * 짝 맞추기: 4×4 카드 16장(몰 물건 8쌍). 두 장씩 뒤집어 같은 그림이면 남는다.
 * 적게 뒤집을수록 마일이 많다 (최소 8번).
 */
object MemoryGame {
    const val PAIRS = 8

    /** 카드 16장 (상품 id), 섞어서 */
    fun deal(seed: Long): List<String> {
        val r = Random(seed)
        val picks = Catalog.products.shuffled(r).take(PAIRS).map { it.id }
        return (picks + picks).shuffled(r)
    }

    fun reward(moves: Int): Long = when {
        moves <= 10 -> 50
        moves <= 14 -> 40
        moves <= 18 -> 30
        else -> 20
    }

    /** [stars] 1~3 (보여주기용) */
    fun stars(moves: Int): Int = when {
        moves <= 12 -> 3
        moves <= 18 -> 2
        else -> 1
    }
}

/** 짝 맞추기 진행 상태. [open]은 지금 앞면인 (아직 짝이 안 된) 카드 */
data class MemoryState(
    val cards: List<String>,
    val matched: Set<Int> = emptySet(),
    val open: List<Int> = emptyList(),
    val moves: Int = 0,
) {
    val done: Boolean get() = matched.size == cards.size

    /** 두 장이 열렸는데 짝이 아님 → [hide] 전까지 보여준다 */
    val mismatch: Boolean get() = open.size == 2

    fun isFaceUp(i: Int): Boolean = i in matched || i in open

    fun flip(i: Int): MemoryState {
        if (i !in cards.indices || done) return this
        // 짝이 아닌 두 장이 열려 있으면 먼저 닫고 누른 카드를 연다
        val base = if (mismatch) hide() else this
        if (base.isFaceUp(i)) return base
        val opened = base.open + i
        if (opened.size < 2) return base.copy(open = opened)
        val (a, b) = opened
        val moves = base.moves + 1
        return if (cards[a] == cards[b]) base.copy(matched = base.matched + a + b, open = emptyList(), moves = moves)
        else base.copy(open = opened, moves = moves)
    }

    /** 짝이 아닌 두 장을 다시 뒤집기 */
    fun hide(): MemoryState = if (mismatch) copy(open = emptyList()) else this
}
