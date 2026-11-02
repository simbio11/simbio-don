package com.simbio.don.game

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetCareTest {
    private val d0 = LocalDate.of(2026, 11, 2)

    @Test
    fun 날마다_배고프고_심심해진다_용품이_있으면_덜() {
        val s = PetState(full = 80, joy = 80, day = d0.toEpochDay())
        val after = PetCare.passDays(s, d0.plusDays(2))
        assertEquals(40, after.full)
        assertEquals(56, after.joy)
        val geared = PetCare.passDays(s.copy(gear = setOf(CareKind.FEEDER, CareKind.BED)), d0.plusDays(2))
        assertEquals(56, geared.full)
        assertEquals(64, geared.joy)
        assertTrue(PetCare.passDays(s, d0.plusDays(5)).hungry)
    }

    @Test
    fun 사료를_사서_밥을_주고_장난감은_하루에_한_번() {
        var s = PetState(full = 20, joy = 20, food = 0, treats = 0, day = d0.toEpochDay())
        assertEquals("사료가 없어요", PetCare.blocked(s, PetCare.Action.FEED, d0))
        s = PetCare.bought(s, "pf1")
        assertEquals(10, s.food)
        s = PetCare.act(s, PetCare.Action.FEED, d0)
        assertEquals(55, s.full)
        assertEquals(9, s.food)
        assertNotNull(PetCare.blocked(s, PetCare.Action.PLAY, d0))
        s = PetCare.bought(s, "pt1")
        assertNull(PetCare.blocked(s, PetCare.Action.PLAY, d0))
        s = PetCare.act(s, PetCare.Action.PLAY, d0)
        assertEquals(40, s.joy)
        assertEquals("오늘 놀았어요", PetCare.blocked(s, PetCare.Action.PLAY, d0))
        assertNull(PetCare.blocked(s, PetCare.Action.PLAY, d0.plusDays(1)))
    }

    @Test
    fun 저장하고_읽기_그리고_말투() {
        val s = PetState(full = 10, joy = 90, food = 4, treats = 1, gear = setOf(CareKind.TOY), day = 5, played = 5)
        assertEquals(s, PetState.decode(s.encode()))
        assertEquals("콩이가 많이 배고파해요", PetCare.line("콩이", s))
        assertEquals("별이 많이 배고파해요", PetCare.line("별", s))
    }

    @Test
    fun 동물_모습_저장과_마일_값() {
        val look = CharacterLook.pet(Species.RABBIT, "회색", "토순이").copy(petAcc = "ribbon")
        val back = CharacterLook.decode(look.encode())
        assertEquals("rabbit", back.species)
        assertEquals("회색", back.fur)
        assertEquals("토순이", back.petName)
        assertEquals(listOf("pet:rabbit", "petacc:ribbon"), Shop.wardrobeNeeded(back, emptySet()))
        assertEquals(emptyList(), Shop.wardrobeNeeded(CharacterLook.pet(Species.DOG), emptySet()))
        // 예전 저장(동물 칸 없음)은 사람
        assertTrue(!CharacterLook.decode("gender=FEMALE;hair=long").isPet)
    }

    @Test
    fun 새_용품_밥그릇_빗_화장실_꾸밈_효과() {
        val base = PetState(full = 20, joy = 20, food = 5, day = d0.toEpochDay())
        // 밥그릇이 있으면 밥이 더 든든
        val bowl = PetCare.bought(base, "pg9")
        assertEquals(base.full + PetCare.FEED + PetCare.BOWL_BONUS, PetCare.act(bowl, PetCare.Action.FEED, d0).full)
        // 빗: 하루 한 번
        assertEquals("빗이 없어요", PetCare.blocked(base, PetCare.Action.GROOM, d0))
        var s = PetCare.bought(base, "pc1")
        s = PetCare.act(s, PetCare.Action.GROOM, d0)
        assertEquals(20 + PetCare.GROOM, s.joy)
        assertEquals("오늘 빗었어요", PetCare.blocked(s, PetCare.Action.GROOM, d0))
        // 화장실이 있으면 덜 심심해함, 꾸밈 용품은 쓰다듬기가 더 좋음
        assertEquals(PetCare.passDays(base, d0.plusDays(1)).joy + 3, PetCare.passDays(PetCare.bought(base, "pg8"), d0.plusDays(1)).joy)
        assertEquals(20 + PetCare.PET + PetCare.DECOR_BONUS, PetCare.act(PetCare.bought(base, "pa5"), PetCare.Action.PET, d0).joy)
        // 프리미엄 사료는 25번분, 덴탈 스틱은 간식 8개
        assertEquals(30, PetCare.bought(base, "pf8").food)
        assertEquals(8, PetCare.bought(base, "pf7").treats - base.treats)
        // 카탈로그의 모든 반려동물 용품이 하는 일이 정해져 있다
        Catalog.products.filter { it.kind == ItemKind.PET }.forEach { assertNotNull(PetCare.effects[it.id], it.id) }
    }
}
