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
    fun 품종_저장과_마일_값과_예전_동물() {
        val look = CharacterLook.pet(Species.POODLE, "검정", "별이")
        val back = CharacterLook.decode(look.encode())
        assertEquals("poodle", back.species)
        assertEquals("검정", back.fur)
        assertEquals("별이", back.petName)
        assertEquals("검정", back.furName)
        // 비었거나 그 품종에 없는 색이면 품종 기본색
        assertEquals("크림", CharacterLook.pet(Species.POODLE).furName)
        assertEquals("탠", CharacterLook.pet(Species.CORGI, "은빛").furName)
        // 처음부터 고르는 품종은 공짜, 나머지는 마일
        assertEquals(listOf(Species.SHIBA_MIX, Species.TABBY_CAT), Species.starters)
        assertEquals(listOf("pet:corgi"), Shop.wardrobeNeeded(CharacterLook.pet(Species.CORGI), emptySet()))
        assertEquals(3_500L, Shop.wardrobeCost("pet:corgi"))
        assertEquals(emptyList(), Shop.wardrobeNeeded(CharacterLook.pet(Species.SHIBA_MIX), emptySet()))
        assertEquals(emptyList(), Shop.wardrobeNeeded(CharacterLook.pet(Species.CORGI), setOf("pet:corgi")))
        // 예전 동물은 가까운 친구로, 사람 저장은 그대로 사람
        assertEquals(Species.SHIBA_MIX, CharacterLook.decode("species=dog;petName=콩이").kind)
        assertEquals(Species.TABBY_CAT, CharacterLook.decode("species=cat").kind)
        assertEquals(Species.SHIBA_MIX, CharacterLook.decode("species=penguin").kind)
        assertTrue(!CharacterLook.decode("gender=FEMALE;hair=long").isPet)
        // 모든 품종은 고를 수 있는 색이 있고 기본이 맨 앞
        Species.pets.forEach { sp -> assertEquals(sp.fur, sp.furs.first()); assertTrue(sp.furs.size >= 6, sp.id) }
    }

    @Test
    fun 털색_바꾸기_털만_바꾸고_외곽선_눈_볼은_그대로() {
        val fur = 0xFFE0A058.toInt()      // 주황 갈색 털
        val outline = 0xFF4A2A1A.toInt()  // 어두운 외곽선
        val pink = 0xFFF2A0A8.toInt()     // 볼 터치
        val blue = 0xFF5B9BE0.toInt()     // 눈물·눈
        fun lum(c: Int) = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        val black = PetFur.apply(fur, PetFur.tone("검정"))
        val white = PetFur.apply(fur, PetFur.tone("흰색"))
        assertTrue(lum(black) < 70, "검정 ${black.toString(16)}")
        assertTrue(lum(white) > 220, "흰색 ${white.toString(16)}")
        assertEquals(fur, PetFur.apply(fur, PetFur.tone("탠")))
        listOf("검정", "흰색", "은빛", "크림", "브라운").forEach { name ->
            val t = PetFur.tone(name)
            assertEquals(pink, PetFur.apply(pink, t), "$name 볼")
            assertEquals(blue, PetFur.apply(blue, t), "$name 눈")
            assertEquals(outline, PetFur.apply(outline, t), "$name 외곽선")
        }
        // 투명은 그대로, 알파는 유지
        assertEquals(0, PetFur.apply(0, PetFur.tone("검정")))
        assertEquals(0x80, PetFur.apply(0x80E0A058.toInt(), PetFur.tone("회색")) ushr 24)
        // 고르는 칸 대표 색은 서로 달라야 한다
        assertEquals(PetFur.tones.size - 1, PetFur.tones.map { PetFur.swatch(it.name) }.toSet().size) // 탠·치즈만 같다
    }

    @Test
    fun 기분에_맞는_표정() {
        assertEquals("joy", PetExpr.of(Mood.JOYFUL))
        assertEquals("tease", PetExpr.of(Mood.CHEER))
        assertEquals("sleep", PetExpr.of(Mood.ANGRY, sleeping = true))
        Mood.entries.forEach { assertTrue(PetExpr.of(it) in setOf("joy", "smile", "calm", "worry", "sad", "angry", "tease")) }
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
