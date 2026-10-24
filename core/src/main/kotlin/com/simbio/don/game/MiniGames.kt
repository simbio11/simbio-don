package com.simbio.don.game

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

    data class Question(val product: Product, val choices: List<Long>) {
        val answer: Long get() = product.price
    }

    fun questions(seed: Long, rounds: Int = ROUNDS): List<Question> {
        val r = Random(seed)
        return Catalog.products.shuffled(r).take(rounds).map { p -> Question(p, (distractors(p.price, r) + p.price).shuffled(r)) }
    }

    private val FACTORS = listOf(0.5, 0.6, 0.75, 1.3, 1.6, 2.0)

    /** 그럴듯한 틀린 값 두 개 (진짜 값과 겹치지 않게, 하나는 싸고 하나는 비싸거나 둘 다 한쪽) */
    fun distractors(price: Long, r: Random): List<Long> {
        val out = mutableListOf<Long>()
        for (f in FACTORS.shuffled(r)) {
            val v = nice((price * f).toLong())
            if (v != price && v !in out && v > 0) out += v
            if (out.size == 2) break
        }
        return out
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
