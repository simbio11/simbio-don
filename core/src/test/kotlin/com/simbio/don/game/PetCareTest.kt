package com.simbio.don.game

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetCareTest {
    private val d0 = LocalDate.of(2026, 11, 2)

    @Test
    fun 시간이_지나면_배고프고_심심하고_꼬질꼬질해진다_잘_때는_천천히() {
        val noon = LocalDateTime.of(2026, 11, 2, 12, 0)
        // 처음이면 지금부터 센다
        val s0 = PetCare.passTime(PetState(full = 80, joy = 80, clean = 80), noon)
        assertEquals(PetCare.sec(noon), s0.at)
        // 낮 8시간: 배부름 -36, 기분 -24, 청결 -10
        val day = PetCare.passTime(s0, noon.plusHours(8))
        assertEquals(44, day.full)
        assertEquals(56, day.joy)
        assertEquals(70, day.clean)
        // 밤(23시~6시)에는 천천히
        val night0 = PetState(full = 80, joy = 80, clean = 80, at = PetCare.sec(LocalDateTime.of(2026, 11, 2, 23, 0)))
        val night = PetCare.passTime(night0, LocalDateTime.of(2026, 11, 3, 5, 0))
        assertEquals(71, night.full)
        assertEquals(74, night.joy)
        // 급식기·잠자리가 있으면 덜
        val geared = PetCare.passTime(s0.copy(gear = setOf(CareKind.FEEDER, CareKind.BED)), noon.plusHours(8))
        assertTrue(geared.full > day.full && geared.joy > day.joy)
        // 하루 굶으면 배고프고, 배고프면 기분이 더 빨리 떨어진다
        val starving = PetCare.passTime(s0.copy(full = 20), noon.plusHours(8))
        assertTrue(starving.hungry)
        assertTrue(starving.joy < day.joy)
        // 1분마다 다시 세도 같은 만큼 떨어진다 (저장은 할 때만)
        assertEquals(day, PetCare.passTime(s0, noon.plusHours(8)))
    }

    @Test
    fun 묶음별_행동_사료마다_효과가_다르고_자주는_못_한다() {
        val now = LocalDateTime.of(2026, 11, 2, 12, 0)
        var s = PetState(full = 20, joy = 20, food = 0, treats = 0, at = PetCare.sec(now))
        fun a(id: String) = PetCare.action(s, PetKind.DOG, id)!!
        assertEquals("사료가 없어요", PetCare.blocked(s, a("feed:food"), now))
        // 프리미엄 사료를 사면 밥 묶음에 그 사료가 생기고 더 든든하다
        s = PetCare.bought(s, "pf8")
        assertEquals(25, s.stock["pf8"])
        s = PetCare.act(s, a("feed:pf8"), now)
        assertEquals(65, s.full)
        assertEquals(24, s.stock["pf8"])
        // 간식마다 다르다: 츄르는 기분, 덴탈 껌은 청결
        s = PetCare.bought(PetCare.bought(s, "pf4"), "pf7")
        assertTrue(a("treat:pf7").clean > 0)
        // 강아지에게 고양이 간식은 덜 좋아한다
        assertTrue(a("treat:pf4").joy < PetCare.action(s, PetKind.CAT, "treat:pf4")!!.joy)
        // 장난감은 하나하나 다른 놀이, 두 시간에 한 번
        assertNull(PetCare.action(s, PetKind.DOG, "toy:pt1"))
        s = PetCare.bought(s, "pt1")
        s = PetCare.act(s, a("toy:pt1"), now)
        assertEquals("2시간 뒤에", PetCare.blocked(s, a("toy:pt1"), now))
        assertNull(PetCare.blocked(s, a("toy:pt1"), now.plusHours(2)))
        // 맨손 놀이는 언제나 있다
        listOf("pet", "rub", "peekaboo", "water", "potty", "sleep").forEach { assertNotNull(PetCare.action(s, PetKind.DOG, it), it) }
        CareGroup.entries.forEach { g -> assertTrue(PetCare.actions(s, PetKind.CAT).any { it.group == g }, g.name) }
        // 쓰다듬기는 30분에 한 번
        s = PetCare.act(s, a("pet"), now)
        assertEquals("30분 뒤에", PetCare.blocked(s, a("pet"), now))
        // 재우기는 밤에만
        assertEquals("밤에 재워요", PetCare.blocked(s, a("sleep"), now))
        assertNull(PetCare.blocked(s, a("sleep"), now.withHour(22)))
    }

    @Test
    fun 저장하고_읽기_그리고_말투() {
        val s = PetState(full = 10, joy = 90, clean = 40, food = 4, treats = 1, stock = mapOf("pf8" to 3), owned = setOf("pt1"), gear = setOf(CareKind.TOY), last = mapOf("toy:pt1" to 99L), at = 1234L)
        assertEquals(s, PetState.decode(s.encode()))
        // 예전 저장 (하루 한 번 기록)도 읽는다
        val old = PetState.decode("full=50;joy=60;food=4;treats=1;gear=TOY;day=5;played=5;walked=;petted=;groomed=")
        assertEquals(50, old.full)
        assertTrue("toy" in old.last)
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
        val now = LocalDateTime.of(2026, 11, 2, 12, 0)
        val base = PetState(full = 20, joy = 20, clean = 20, food = 5, at = PetCare.sec(now))
        // 밥그릇이 있으면 밥이 더 든든
        val bowl = PetCare.bought(base, "pg9")
        assertTrue(PetCare.action(bowl, PetKind.DOG, "feed:food")!!.full > PetCare.action(base, PetKind.DOG, "feed:food")!!.full)
        // 미용 용품마다 다른 돌봄: 목욕은 싫어하지만 아주 깨끗해진다
        var s = PetCare.bought(PetCare.bought(base, "pc1"), "pc2")
        val bath = PetCare.action(s, PetKind.CAT, "groom:pc2")!!
        assertTrue(bath.joy < 0 && bath.clean >= 40)
        s = PetCare.act(s, PetCare.action(s, PetKind.DOG, "groom:pc1")!!, now)
        assertEquals(30, s.clean)
        // 화장실이 있으면 덜 지저분해지고 치우기가 더 깨끗
        assertTrue(PetCare.passTime(PetCare.bought(base, "pg8"), now.plusHours(10)).clean > PetCare.passTime(base, now.plusHours(10)).clean)
        // 꾸밈 용품은 쓰다듬기가 더 좋음
        assertTrue(PetCare.action(PetCare.bought(base, "pa5"), PetKind.DOG, "pet")!!.joy > PetCare.action(base, PetKind.DOG, "pet")!!.joy)
        // 하네스가 있어야 산책, 고양이는 산책 없음
        assertNull(PetCare.action(base, PetKind.DOG, "walk"))
        assertNotNull(PetCare.action(PetCare.bought(base, "pa1"), PetKind.DOG, "walk"))
        assertNull(PetCare.action(PetCare.bought(base, "pa1"), PetKind.CAT, "walk"))
        // 카탈로그의 모든 반려동물 용품이 하는 일이 정해져 있다
        Catalog.products.filter { it.kind == ItemKind.PET }.forEach { assertNotNull(PetCare.effects[it.id], it.id) }
    }

    @Test
    fun 몰의_반려동물_용품은_모두_분류와_그림과_효과가_있고_예전_이름도_찾는다() {
        val pets = Catalog.products.filter { it.kind == ItemKind.PET }
        assertTrue(pets.size >= 60, "${pets.size}")
        pets.forEach { assertTrue(it.group in Catalog.PET_GROUPS, it.id) }
        assertEquals(Catalog.PET_GROUPS.toSet(), pets.map { it.group }.toSet())
        assertEquals(pets.size, pets.map { it.id }.toSet().size)
        assertEquals(pets.size, pets.map { it.name }.toSet().size)
        pets.forEach { assertTrue(it.name.length <= 28, "${it.id} ${it.name}") }
        // 예전 이름으로 산 물건도 같은 상품으로
        assertEquals("pf1", Dex.productOf("강아지 사료 2kg")?.id)
        assertEquals("pg3", Dex.productOf("캣타워")?.id)
        assertEquals("pg9", Dex.productOf("스테인리스 밥그릇 세트")?.id)
        Catalog.LEGACY_NAMES.values.forEach { assertNotNull(Catalog.byId(it), it) }
    }

    @Test
    fun 표정은_잠_배고픔_지갑_심심함_순서로() {
        val ok = PetState(full = 80, joy = 80)
        // 밤늦게는 무엇이든 잔다
        assertEquals("sleep", PetFeelings.of(Mood.ANGRY, ok.copy(full = 0), 23, "콩이").expr)
        assertEquals(PetBubble.ZZZ, PetFeelings.of(Mood.CALM, ok, 3, "콩이").bubble)
        assertEquals(PetEnergy.SLEEP, PetFeelings.of(Mood.CALM, ok, 5, "콩이").energy)
        // 많이 배고프면 지갑 걱정보다 먼저
        val starving = PetFeelings.of(Mood.ANGRY, ok.copy(full = 10), 12, "콩이")
        assertEquals("sad", starving.expr)
        assertEquals(PetBubble.FOOD, starving.bubble)
        assertEquals("콩이가 많이 배고파해요", starving.line)
        // 돈을 다 써서 지갑이 비면 화낸다
        val angry = PetFeelings.of(Mood.ANGRY, ok, 12, "별")
        assertEquals("angry", angry.expr)
        assertEquals(PetBubble.ANGER, angry.bubble)
        assertTrue(angry.line.startsWith("별이 "))
        assertEquals(PetBubble.FOOD, PetFeelings.of(Mood.CALM, ok.copy(full = 25), 12, "콩이").bubble)
        assertEquals(PetBubble.SWEAT, PetFeelings.of(Mood.WORRIED, ok, 12, "콩이").bubble)
        assertEquals(PetBubble.BORED, PetFeelings.of(Mood.CALM, ok.copy(joy = 10), 12, "콩이").bubble)
        assertEquals("joy", PetFeelings.of(Mood.JOYFUL, ok, 12, "콩이").expr)
        assertEquals("calm", PetFeelings.of(Mood.CALM, ok.copy(full = 50, joy = 50), 12, "콩이").expr)
        // 밤 9~10시는 졸려서 덜 움직인다
        assertEquals(PetEnergy.LOW, PetFeelings.of(Mood.JOYFUL, ok, 21, "콩이").energy)
    }

    @Test
    fun 검정_털로_바꾸면_적갈색_외곽선도_어둡게() {
        val black = PetFur.tone("검정")
        val outline = 0xFF6E2A33.toInt() // 그림의 자줏빛 적갈색 외곽선
        val out = PetFur.apply(outline, black)
        val r = (out shr 16) and 0xFF
        val g = (out shr 8) and 0xFF
        assertTrue(r - g < 20, "붉은 기가 남았어요: ${Integer.toHexString(out)}")
        // 밝은 볼 터치·귀 안쪽 분홍은 그대로
        val blush = 0xFFF4A0B0.toInt()
        assertEquals(blush, PetFur.apply(blush, black))
        // 원래 털색이면 외곽선도 그대로
        assertEquals(outline, PetFur.apply(outline, PetFur.tone("탠")))
    }
}
