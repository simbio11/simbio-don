package com.simbio.don.budget

import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 0.10: 직접 만든 카테고리 · 소분류 · 할부 · 반복 수입 */
class LedgerExtrasTest {
    @AfterTest
    fun clear() = Category.register(emptyList())

    @Test
    fun `직접 만든 카테고리는 이름으로 찾고 기본 카테고리 뒤에 온다`() {
        val pet = Category.custom("U1", "반려동물", income = false, icon = "pets_fill", color = 0xFFFF8A3D)
        val rent = Category.custom("U2", "임대 수입", income = true, icon = "home_fill", color = 0xFF346AFF)
        // 읽기 전엔 이름만 가진 임시 카테고리 (같은 카테고리로 묶이게)
        assertEquals(pet, Category.of("U1"))
        assertEquals("내 분류", Category.of("U1").label)
        Category.register(listOf(pet, rent))
        assertEquals("반려동물", Category.of("U1").label)
        assertEquals(pet, Category.spending.last())
        assertEquals(rent, Category.incomes.last())
        assertTrue(pet.custom)
        assertFalse(Category.FOOD.custom)
        assertEquals(Category.ETC, Category.of("없는이름"))
        assertFailsWith<IllegalArgumentException> { Category.custom("FOOD2", "x", false, "x", 0) }
    }

    @Test
    fun `소분류별로 나눠 본다`() {
        val d = LocalDate.of(2026, 10, 24)
        val entries = listOf(
            Entry(d, 9_000, "김밥", category = Category.FOOD, sub = "점심"),
            Entry(d, 12_000, "백반", category = Category.FOOD, sub = "점심"),
            Entry(d, 30_000, "이마트", category = Category.FOOD, sub = "장보기"),
            Entry(d, 5_000, "빵", category = Category.FOOD),
            Entry(d, 2_000, "김밥", type = EntryType.REFUND, category = Category.FOOD, sub = "점심"),
            Entry(d, 4_500, "스타벅스", category = Category.CAFE, sub = "커피"),
        )
        val subs = Stats.bySub(entries, Category.FOOD)
        assertEquals(listOf("장보기" to 30_000L, "점심" to 19_000L, null to 5_000L), subs.map { it.sub to it.amount })
        assertEquals(2, subs[1].count)
    }

    @Test
    fun `할부는 달마다 나누고 남는 원은 첫 달에`() {
        assertEquals(listOf(333_334L, 333_333L, 333_333L), Installments.split(1_000_000, 3))
        assertEquals(1_000_000L, Installments.split(1_000_000, 7).sum())
        val dates = Installments.dates(LocalDate.of(2026, 1, 31), 3)
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)), dates)
        assertFailsWith<IllegalArgumentException> { Installments.split(10_000, 1) }
    }

    @Test
    fun `알림 문구에서 할부 개월을 읽는다`() {
        assertEquals(3, Installments.monthsIn("OO카드 승인 홍길동 1,200,000원 03개월 10/24 14:02 하이마트"))
        assertEquals(6, Installments.monthsIn("[OO카드] 할부(6) 600,000원 승인"))
        assertEquals(12, Installments.monthsIn("승인 (12개월) 무이자"))
        assertNull(Installments.monthsIn("OO카드 승인 45,000원 일시불 스타벅스"))
        assertNull(Installments.monthsIn("OO카드 승인 45,000원 01개월"))
        assertNull(Installments.monthsIn("스타벅스 4,500원 결제"))
    }

    @Test
    fun `반복 수입은 이름이나 금액과 날짜로 알아본다`() {
        val cycle = PayCycle(java.time.YearMonth.of(2026, 10), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 11, 24))
        val r = RecurringIncome("과외비", 400_000, 5)
        assertTrue(RecurringIncomes.matches("입금 과외비 김OO 400,000원", 400_000, LocalDate.of(2026, 11, 20), r, cycle))
        assertTrue(RecurringIncomes.matches("입금 김OO", 405_000, LocalDate.of(2026, 11, 7), r, cycle))
        assertFalse(RecurringIncomes.matches("입금 김OO", 405_000, LocalDate.of(2026, 11, 15), r, cycle))
        assertFalse(RecurringIncomes.matches("입금 김OO", 500_000, LocalDate.of(2026, 11, 5), r, cycle))
    }
}
