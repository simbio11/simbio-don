package com.simbio.don.game

import java.time.LocalDateTime
import java.time.ZoneId

/** 반려동물 용품이 하는 일 */
enum class CareKind(val label: String) {
    FOOD("사료"),
    TREAT("간식"),
    TOY("장난감"),
    BED("잠자리"),
    FEEDER("자동 급식기"),
    HARNESS("하네스"),

    /** 빗·샴푸·발톱깎이: 털 빗어 주기 */
    GROOM("미용 용품"),

    /** 밥그릇·물 분수대: 밥이 더 든든해요 */
    BOWL("밥그릇"),

    /** 고양이 화장실·배변패드·모래: 덜 지저분해져요 */
    LITTER("화장실"),

    /** 옷·이름표·우비처럼 꾸며 주는 것: 쓰다듬으면 더 좋아해요 */
    DECOR("꾸밈 용품"),
}

/**
 * 집 친구(동물) 돌보기 상태. 시간이 지나면 배고파지고 심심해지고 꼬질꼬질해진다 ([PetCare.passTime]).
 * 실제 돈과는 상관없다 (모의 지갑으로 산 용품만 쓴다).
 */
data class PetState(
    /** 배부름 0~100 */
    val full: Int = 80,
    /** 기분 0~100 */
    val joy: Int = 80,
    /** 청결 0~100 */
    val clean: Int = 80,
    /** 기본 사료 (번) — 처음에 조금 주는 것과 예전에 산 사료 */
    val food: Int = 3,
    /** 기본 간식 (개) */
    val treats: Int = 2,
    /** 산 사료·간식·영양제: 상품 id → 남은 수 */
    val stock: Map<String, Int> = emptyMap(),
    /** 산 장난감·미용 용품 (다시 쓰는 것): 상품 id */
    val owned: Set<String> = emptySet(),
    /** 가진 용품 종류 */
    val gear: Set<CareKind> = emptySet(),
    /** 행동 id → 마지막으로 한 때 (초) — 너무 자주는 못 한다 */
    val last: Map<String, Long> = emptyMap(),
    /** 마지막으로 배고픔·심심함을 센 때 (초, null = 아직 시작 안 함) */
    val at: Long? = null,
) {
    val hungry: Boolean get() = full < 30
    val bored: Boolean get() = joy < 30
    val dirty: Boolean get() = clean < 30

    fun encode(): String = listOf(
        "v=2", "full=$full", "joy=$joy", "clean=$clean", "food=$food", "treats=$treats",
        "stock=" + stock.filterValues { it > 0 }.entries.joinToString(",") { "${it.key}:${it.value}" },
        "owned=" + owned.joinToString(","),
        "gear=" + gear.joinToString(",") { it.name },
        "last=" + last.entries.joinToString(",") { "${it.key}:${it.value}" },
        "at=${at ?: ""}",
    ).joinToString(";")

    companion object {
        fun decode(s: String?): PetState {
            if (s.isNullOrBlank()) return PetState()
            val m = s.split(';').mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()
            fun int(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
            fun pairs(k: String) = m[k].orEmpty().split(',').mapNotNull { p ->
                val i = p.lastIndexOf(':')
                if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
            }
            val last = pairs("last").mapNotNull { (k, v) -> v.toLongOrNull()?.let { k to it } }.toMap().toMutableMap()
            // 예전(하루 한 번) 기록: 그날 낮 12시에 한 것으로
            listOf("played" to "toy", "walked" to "walk", "petted" to "pet", "groomed" to "brush").forEach { (old, id) ->
                m[old]?.toLongOrNull()?.let { day -> last.putIfAbsent(id, day * 86_400 + 43_200 - 9 * 3_600) }
            }
            return PetState(
                full = int("full", 80).coerceIn(0, 100),
                joy = int("joy", 80).coerceIn(0, 100),
                clean = int("clean", 80).coerceIn(0, 100),
                food = int("food", 3).coerceAtLeast(0),
                treats = int("treats", 2).coerceAtLeast(0),
                stock = pairs("stock").mapNotNull { (k, v) -> v.toIntOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap(),
                owned = m["owned"].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
                gear = m["gear"].orEmpty().split(',').mapNotNull { g -> CareKind.entries.firstOrNull { it.name == g } }.toSet(),
                last = last,
                at = m["at"]?.toLongOrNull(),
            )
        }
    }
}

/** 돌보기 묶음 (위쪽 큰 버튼). [icon]은 16×16 도트 이름 (몰 상품 id 또는 care_*) */
enum class CareGroup(val label: String, val icon: String) {
    FOOD("밥", "pg9"),
    TREAT("간식", "pf3"),
    PLAY("놀이", "pt1"),
    CARE("돌봄", "pc1"),
}

/** 반응 그림: 먹기·놀기·기뻐하기·산책 */
enum class CareAnim { EAT, PLAY, HAPPY, WALK, SLEEP }

/**
 * 돌보기 행동 하나. [full]·[joy]·[clean]은 바뀌는 양, [cooldownMin]분 안에는 다시 못 한다.
 * [uses]는 쓰면 줄어드는 것 (상품 id, 또는 기본 사료 "food"·기본 간식 "treat").
 */
data class CareAction(
    val id: String,
    val group: CareGroup,
    val label: String,
    val icon: String,
    val full: Int = 0,
    val joy: Int = 0,
    val clean: Int = 0,
    val cooldownMin: Int = 0,
    val uses: String? = null,
    val anim: CareAnim = CareAnim.HAPPY,
    /** 해 준 뒤 한마디 ({이름} 자리에 이름+이/가) */
    val done: String = "{가} 좋아해요",
) {
    /** 남은 수 (쓰면 줄어드는 것만) */
    fun left(s: PetState): Int? = when (uses) {
        null -> null
        "food" -> s.food
        "treat" -> s.treats
        else -> s.stock[uses] ?: 0
    }

    /** 바뀌는 양 한 줄 ("배부름 +45 · 기분 +6") */
    val effect: String
        get() = listOfNotNull(
            full.takeIf { it != 0 }?.let { "배부름 ${sign(it)}" },
            joy.takeIf { it != 0 }?.let { "기분 ${sign(it)}" },
            clean.takeIf { it != 0 }?.let { "청결 ${sign(it)}" },
        ).joinToString(" · ")

    private fun sign(v: Int) = if (v > 0) "+$v" else "$v"
}

object PetCare {
    /** 몰 상품 → 하는 일, 사면 늘어나는 수 (사료·간식만) */
    val effects: Map<String, Pair<CareKind, Int>> = mapOf(
        "pf1" to (CareKind.FOOD to 10), "pf2" to (CareKind.FOOD to 10),
        "pf3" to (CareKind.TREAT to 5), "pf4" to (CareKind.TREAT to 10), "pf5" to (CareKind.TREAT to 3),
        "pt1" to (CareKind.TOY to 0), "pt2" to (CareKind.TOY to 0), "pt3" to (CareKind.TOY to 0), "pg3" to (CareKind.TOY to 0), "pg4" to (CareKind.TOY to 0),
        "pg1" to (CareKind.BED to 0), "pg2" to (CareKind.BED to 0),
        "pg5" to (CareKind.FEEDER to 0),
        "pa1" to (CareKind.HARNESS to 0), "pa2" to (CareKind.HARNESS to 0),
        // 새로 늘어난 용품
        "pf6" to (CareKind.TREAT to 3), "pf7" to (CareKind.TREAT to 8), "pf8" to (CareKind.FOOD to 25), "ph1" to (CareKind.TREAT to 6),
        "pt4" to (CareKind.TOY to 0), "pt5" to (CareKind.TOY to 0), "pt6" to (CareKind.TOY to 0), "pt7" to (CareKind.TOY to 0),
        "pg6" to (CareKind.BED to 0), "pg7" to (CareKind.BED to 0), "pg8" to (CareKind.LITTER to 0), "pg9" to (CareKind.BOWL to 0),
        "pc1" to (CareKind.GROOM to 0), "pc2" to (CareKind.GROOM to 0), "pc3" to (CareKind.GROOM to 0),
        "pa3" to (CareKind.DECOR to 0), "pa4" to (CareKind.DECOR to 0), "pa5" to (CareKind.DECOR to 0), "pa6" to (CareKind.DECOR to 0),
        // 쿠팡·네이버 느낌으로 더 늘린 것들
        "pf9" to (CareKind.FOOD to 15), "pf10" to (CareKind.FOOD to 10), "pf11" to (CareKind.FOOD to 8),
        "pf12" to (CareKind.TREAT to 6), "pf13" to (CareKind.TREAT to 10), "pf14" to (CareKind.TREAT to 5), "pf15" to (CareKind.TREAT to 2),
        "pt8" to (CareKind.TOY to 0), "pt9" to (CareKind.TOY to 0), "pt10" to (CareKind.TOY to 0),
        "pg10" to (CareKind.DECOR to 0), "pg11" to (CareKind.BED to 0), "pg12" to (CareKind.DECOR to 0), "pg13" to (CareKind.DECOR to 0),
        "pg14" to (CareKind.BOWL to 0), "pg15" to (CareKind.LITTER to 0), "pg16" to (CareKind.LITTER to 0),
        "pc4" to (CareKind.GROOM to 0), "pc5" to (CareKind.GROOM to 0), "pc6" to (CareKind.GROOM to 0), "pc7" to (CareKind.GROOM to 0), "pc8" to (CareKind.GROOM to 0),
        "pa7" to (CareKind.DECOR to 0), "pa8" to (CareKind.DECOR to 0), "pa9" to (CareKind.DECOR to 0), "pa10" to (CareKind.DECOR to 0), "pa11" to (CareKind.DECOR to 0),
        "ph2" to (CareKind.DECOR to 0), "ph3" to (CareKind.DECOR to 0),
    )

    /** 사료·간식·영양제: 상품 id → (배부름, 기분, 청결) */
    private val EDIBLE: Map<String, Triple<Int, Int, Int>> = mapOf(
        "pf1" to Triple(35, 3, 0), "pf2" to Triple(35, 3, 0), "pf8" to Triple(45, 6, 0), "pf9" to Triple(40, 5, 0),
        "pf10" to Triple(38, 4, 0), "pf11" to Triple(30, 6, 0),
        "pf3" to Triple(3, 12, 6), "pf4" to Triple(4, 18, 0), "pf5" to Triple(8, 12, 0), "pf6" to Triple(10, 12, 0),
        "pf7" to Triple(2, 8, 10), "pf12" to Triple(5, 15, 0), "pf13" to Triple(10, 14, 0), "pf14" to Triple(4, 12, 0),
        "pf15" to Triple(6, 8, 0), "ph1" to Triple(0, 6, 4),
    )

    /** 장난감 → (놀이 이름, 기분) */
    private val TOYS: Map<String, Pair<String, Int>> = mapOf(
        "pt1" to ("공 던져 주기" to 20), "pt2" to ("낚싯대 흔들기" to 24), "pt3" to ("터그 놀이" to 22), "pt4" to ("삑삑이 인형 놀이" to 18),
        "pt5" to ("터널 숨바꼭질" to 22), "pt6" to ("원반 던지기" to 24), "pt7" to ("노즈워크" to 20), "pt8" to ("레이저 쫓기" to 22),
        "pt9" to ("캣닢 인형 주기" to 18), "pt10" to ("스마트 공 굴리기" to 18), "pg3" to ("캣타워 오르기" to 20), "pg4" to ("스크래쳐 긁기" to 14),
    )

    /** 미용·위생 용품 → (이름, 기분, 청결, 다시 하려면 몇 분) */
    private data class Groom(val label: String, val joy: Int, val clean: Int, val cooldownMin: Int)
    private val GROOMS: Map<String, Groom> = mapOf(
        "pc1" to Groom("빗질해 주기", 10, 10, 6 * 60),
        "pc2" to Groom("목욕시키기", -6, 45, 3 * 24 * 60),
        "pc3" to Groom("발톱 깎기", -3, 6, 7 * 24 * 60),
        "pc4" to Groom("털 다듬기", 3, 18, 10 * 24 * 60),
        "pc5" to Groom("귀 청소", 0, 10, 3 * 24 * 60),
        "pc6" to Groom("눈물 자국 닦기", 2, 6, 12 * 60),
        "pc7" to Groom("양치시키기", -2, 12, 12 * 60),
        "pc8" to Groom("탈취 스프레이", 0, 10, 24 * 60),
    )

    private fun productName(id: String) = Catalog.byId(id)?.name?.replace(Regex("\\[[^]]*]\\s*"), "")?.trim() ?: id

    /** 강아지에게 고양이 용품(또는 반대)은 덜 좋아한다 */
    private fun liking(id: String, kind: PetKind?): Float {
        val name = Catalog.byId(id)?.name.orEmpty()
        val forCat = listOf("냥", "캣", "고양이", "키튼").any { it in name }
        val forDog = listOf("멍", " 독 ", "강아지", "개껌").any { it in name }
        return when {
            kind == PetKind.DOG && forCat -> 0.5f
            kind == PetKind.CAT && forDog -> 0.5f
            else -> 1f
        }
    }

    /** 지금 할 수 있는 돌보기 목록 (가진 용품으로 하는 것 + 맨손으로 하는 것) */
    fun actions(s: PetState, kind: PetKind?): List<CareAction> {
        val out = mutableListOf<CareAction>()
        val cat = kind == PetKind.CAT
        val bowl = if (CareKind.BOWL in s.gear) 8 else 0
        // 밥
        out += CareAction("feed:food", CareGroup.FOOD, "사료 주기", if (cat) "pf2" else "pf1", full = 35 + bowl, joy = 2, uses = "food", anim = CareAnim.EAT, done = "{가} 맛있게 먹었어요")
        s.stock.keys.filter { (effects[it]?.first == CareKind.FOOD) }.sorted().forEach { id ->
            val (f, j, c) = EDIBLE[id] ?: Triple(35, 3, 0)
            out += CareAction("feed:$id", CareGroup.FOOD, productName(id), id, full = f + bowl, joy = (j * liking(id, kind)).toInt(), clean = c, uses = id, anim = CareAnim.EAT, done = "{가} 맛있게 먹었어요")
        }
        out += CareAction("water", CareGroup.FOOD, "물 갈아 주기", "care_water", full = 3, joy = if ("pg14" in s.owned) 6 else 4, cooldownMin = 4 * 60, anim = CareAnim.EAT, done = "{가} 시원한 물을 마셨어요")
        // 간식
        out += CareAction("treat:treat", CareGroup.TREAT, "간식 주기", "pf5", full = 5, joy = 15, cooldownMin = 30, uses = "treat", anim = CareAnim.EAT, done = "{가} 간식에 신났어요")
        s.stock.keys.filter { effects[it]?.first == CareKind.TREAT }.sorted().forEach { id ->
            val (f, j, c) = EDIBLE[id] ?: Triple(5, 15, 0)
            out += CareAction("treat:$id", CareGroup.TREAT, productName(id), id, full = f, joy = (j * liking(id, kind)).toInt(), clean = c, cooldownMin = 30, uses = id, anim = CareAnim.EAT, done = if (id == "ph1") "{가} 영양제를 꿀꺽 먹었어요" else "{가} 간식에 신났어요")
        }
        // 놀이: 맨손 놀이 + 가진 장난감마다
        val decor = if (CareKind.DECOR in s.gear) 4 else 0
        out += CareAction("pet", CareGroup.PLAY, "쓰다듬기", "care_hand", joy = 8 + decor, cooldownMin = 30, done = "{가} 기분 좋아해요")
        out += CareAction("rub", CareGroup.PLAY, if (cat) "턱 긁어 주기" else "배 문질러 주기", "care_heart", joy = 6 + decor, cooldownMin = 60, done = if (cat) "{가} 골골송을 불러요" else "{가} 발라당 누웠어요")
        out += CareAction("peekaboo", CareGroup.PLAY, if (cat) "손가락 사냥 놀이" else "손 장난 놀이", "care_ball", full = -2, joy = 10, cooldownMin = 90, anim = CareAnim.PLAY, done = "{가} 신나게 놀았어요")
        val toys = s.owned.filter { it in TOYS }.sorted()
        toys.forEach { id ->
            val (label, j) = TOYS.getValue(id)
            out += CareAction("toy:$id", CareGroup.PLAY, label, id, full = -4, joy = (j * liking(id, kind)).toInt(), cooldownMin = 2 * 60, anim = CareAnim.PLAY, done = "{가} 신나게 놀았어요")
        }
        // 예전에 산 장난감 (어느 것인지 모를 때)
        if (toys.isEmpty() && CareKind.TOY in s.gear) {
            out += CareAction("toy", CareGroup.PLAY, "장난감 놀이", "pt1", full = -4, joy = 20, cooldownMin = 2 * 60, anim = CareAnim.PLAY, done = "{가} 신나게 놀았어요")
        }
        if (!cat && CareKind.HARNESS in s.gear) {
            out += CareAction("walk", CareGroup.PLAY, "산책 가기", "pa2", full = -10, joy = 25, clean = -10, cooldownMin = 4 * 60, anim = CareAnim.WALK, done = "{가} 산책 다녀왔어요")
        }
        // 돌봄
        val litter = CareKind.LITTER in s.gear
        out += CareAction("potty", CareGroup.CARE, if (cat) "화장실 치우기" else "배변 치우기", "care_poop", joy = 2, clean = if (litter) 25 else 18, cooldownMin = 3 * 60, done = "{가} 깨끗한 화장실이 좋대요")
        val grooms = s.owned.filter { it in GROOMS }.sorted()
        grooms.forEach { id ->
            val g = GROOMS.getValue(id)
            out += CareAction("groom:$id", CareGroup.CARE, g.label, id, joy = if (cat && id == "pc2") g.joy * 2 else g.joy, clean = g.clean, cooldownMin = g.cooldownMin, done = if (g.joy < 0) "{가} 싫어했지만 깨끗해졌어요" else "{가} 보송보송해졌어요")
        }
        if (grooms.isEmpty() && CareKind.GROOM in s.gear) {
            out += CareAction("brush", CareGroup.CARE, "털 빗어 주기", "pc1", joy = 10, clean = 10, cooldownMin = 6 * 60, done = "{가} 보송보송해졌어요")
        }
        out += CareAction("sleep", CareGroup.CARE, "재우기", "care_moon", joy = if (CareKind.BED in s.gear) 10 else 5, cooldownMin = 12 * 60, anim = CareAnim.SLEEP, done = "{가} 새근새근 자요")
        return out
    }

    fun action(s: PetState, kind: PetKind?, id: String): CareAction? = actions(s, kind).firstOrNull { it.id == id }

    /** 지금 할 수 있는지 (못 하면 그 까닭) */
    fun blocked(s: PetState, a: CareAction, now: LocalDateTime): String? {
        a.left(s)?.let { if (it <= 0) return if (a.group == CareGroup.FOOD) "사료가 없어요" else "다 먹었어요" }
        if (a.group == CareGroup.FOOD && a.full >= 20 && s.full >= 90) return "배불러요"
        if (a.group == CareGroup.TREAT && s.full >= 98) return "배불러요"
        if (a.id == "sleep" && now.hour in 7..19) return "밤에 재워요"
        if (a.clean > 0 && a.joy <= 0 && s.clean >= 95) return "이미 깨끗해요"
        if (a.cooldownMin > 0) {
            val t = s.last[a.id] ?: return null
            val wait = (t + a.cooldownMin * 60L - sec(now)) / 60
            if (wait > 0) return when {
                wait >= 24 * 60 -> "${(wait + 24 * 60 - 1) / (24 * 60)}일 뒤에"
                wait >= 60 -> "${(wait + 59) / 60}시간 뒤에"
                else -> "${wait}분 뒤에"
            }
        }
        return null
    }

    fun act(s: PetState, a: CareAction, now: LocalDateTime): PetState {
        if (blocked(s, a, now) != null) return s
        fun c(v: Int) = v.coerceIn(0, 100)
        var n = s.copy(full = c(s.full + a.full), joy = c(s.joy + a.joy), clean = c(s.clean + a.clean), last = s.last + (a.id to sec(now)))
        when (a.uses) {
            null -> Unit
            "food" -> n = n.copy(food = n.food - 1)
            "treat" -> n = n.copy(treats = n.treats - 1)
            else -> n = n.copy(stock = (n.stock + (a.uses to (n.stock[a.uses] ?: 0) - 1)).filterValues { it > 0 })
        }
        return n
    }

    /** 몰에서 산 것 반영: 사료·간식은 쌓이고, 장난감·미용 용품은 생긴다 */
    fun bought(s: PetState, productId: String, qty: Int = 1): PetState {
        val (kind, n) = effects[productId] ?: return s
        return when (kind) {
            CareKind.FOOD, CareKind.TREAT -> s.copy(stock = s.stock + (productId to (s.stock[productId] ?: 0) + n * qty), gear = s.gear + kind)
            else -> s.copy(owned = s.owned + productId, gear = s.gear + kind)
        }
    }

    // ── 시간 ───────────────────────────────────────────────

    /** 한 시간에 줄어드는 양 (깨어 있을 때 / 잘 때) */
    const val HUNGER_AWAKE = 4.5
    const val HUNGER_ASLEEP = 1.5
    const val BOREDOM_AWAKE = 3.0
    const val BOREDOM_ASLEEP = 1.0
    const val DIRT_AWAKE = 1.2
    const val DIRT_ASLEEP = 0.5

    private val zone: ZoneId = ZoneId.systemDefault()
    fun sec(t: LocalDateTime): Long = t.atZone(zone).toEpochSecond()

    /**
     * 시간이 흐른 만큼: 배고파지고, 심심해지고, 꼬질꼬질해진다. 잘 때(밤 11시~아침 6시)는 천천히.
     * 배가 고프거나 지저분하면 기분이 더 빨리 떨어진다. 급식기·잠자리·화장실이 있으면 덜.
     * 처음이면 지금부터 센다.
     */
    fun passTime(s: PetState, now: LocalDateTime): PetState {
        val end = sec(now)
        val start = s.at ?: return s.copy(at = end)
        if (end <= start) return s
        val from = maxOf(start, end - 30L * 86_400)
        var full = s.full.toDouble()
        var joy = s.joy.toDouble()
        var clean = s.clean.toDouble()
        val feeder = if (CareKind.FEEDER in s.gear) 0.7 else 1.0
        val bed = if (CareKind.BED in s.gear) 0.85 else 1.0
        val litter = if (CareKind.LITTER in s.gear) 0.7 else 1.0
        var t = from
        while (t < end) {
            // 15분씩 (시간대가 바뀌는 곳에서 잘 끊기게)
            val step = minOf(900L, end - t)
            val h = step / 3_600.0
            val hour = java.time.Instant.ofEpochSecond(t).atZone(zone).hour
            val asleep = PetFeelings.asleep(hour)
            full -= (if (asleep) HUNGER_ASLEEP else HUNGER_AWAKE) * feeder * h
            clean -= (if (asleep) DIRT_ASLEEP else DIRT_AWAKE) * litter * h
            var bore = (if (asleep) BOREDOM_ASLEEP else BOREDOM_AWAKE) * bed
            if (full < 30) bore *= 1.5
            if (clean < 30) bore *= 1.3
            joy -= bore * h
            t += step
        }
        fun r(v: Double) = Math.round(v).toInt().coerceIn(0, 100)
        return s.copy(full = r(full), joy = r(joy), clean = r(clean), at = end)
    }

    /** 한 줄 상태 ("콩이가 배고파해요") */
    fun line(name: String, s: PetState): String {
        val ga = name + josa(name, "이", "가")
        return when {
            s.full < 15 -> "$ga 많이 배고파해요"
            s.hungry -> "$ga 배고파해요"
            s.joy < 15 -> "$ga 많이 심심해해요"
            s.bored -> "$ga 심심해해요"
            s.dirty -> "$ga 꼬질꼬질해요"
            s.full >= 70 && s.joy >= 70 -> "$ga 행복해요"
            else -> name + josa(name, "은", "는") + " 잘 지내요"
        }
    }

    /** "{가}" 자리에 이름+이/가 */
    fun say(template: String, name: String): String = template.replace("{가}", name + josa(name, "이", "가"))

    /** 받침이 있으면 [withFinal], 없으면 [without] ("별이" / "콩이가") */
    fun josa(word: String, withFinal: String, without: String): String {
        val ch = word.lastOrNull() ?: return without
        return if (ch in '가'..'힣' && (ch - '가') % 28 != 0) withFinal else without
    }
}
