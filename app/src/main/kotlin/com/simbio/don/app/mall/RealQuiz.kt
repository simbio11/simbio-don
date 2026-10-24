package com.simbio.don.app.mall

import com.simbio.don.game.PriceQuiz
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * 얼마일까? 실제 상품판: 흔히 사는 물건 이름으로 네이버 쇼핑 검색 API를 불러 실제 상품의 최저가를 맞힌다.
 * 보내는 것은 [PriceQuiz.REAL_QUERIES]의 검색어뿐이다 (가계부·알림 내용은 보내지 않고, 웹 페이지를 긁지 않는다).
 */
object RealQuiz {
    /** 너무 싼 부속품·샘플은 뺀다 */
    const val MIN_PRICE = 1_000L

    /** 검색 결과 앞쪽(정확도순)에서만 고른다 — 뒤로 갈수록 엉뚱한 물건이 섞인다 */
    const val TOP = 10

    sealed interface Load {
        data class Ok(val questions: List<PriceQuiz.Question>) : Load
        data class Failed(val message: String) : Load
    }

    suspend fun load(seed: Long, search: suspend (String) -> NaverShopping.Result): Load = coroutineScope {
        val results = PriceQuiz.realQueries(seed).map { q -> async { search(q) } }.awaitAll()
        val r = Random(seed)
        val seen = mutableSetOf<String>()
        val qs = results.asSequence()
            .mapNotNull { (it as? NaverShopping.Result.Ok)?.items?.let { items -> pick(items, r, seen) } }
            .take(PriceQuiz.ROUNDS)
            .map { PriceQuiz.questionOf(it.toProduct(), r, source = it.mall) }
            .toList()
        if (qs.size == PriceQuiz.ROUNDS) {
            Load.Ok(qs)
        } else {
            Load.Failed(results.filterIsInstance<NaverShopping.Result.Failed>().firstOrNull()?.message ?: "실제 상품을 넉넉히 찾지 못했어요")
        }
    }

    /** 앞쪽 [TOP]개 중 하나를 무작위로 (같은 이름은 한 번만) */
    fun pick(items: List<NaverItem>, r: Random, seen: MutableSet<String>): NaverItem? {
        val pool = items.filter { it.price >= MIN_PRICE && it.title.isNotBlank() && it.title !in seen }.take(TOP)
        if (pool.isEmpty()) return null
        return pool[r.nextInt(pool.size)].also { seen += it.title }
    }
}
