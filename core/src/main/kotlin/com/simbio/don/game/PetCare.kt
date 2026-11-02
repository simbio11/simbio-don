package com.simbio.don.game

import java.time.LocalDate

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

    /** 밥그릇: 밥 주기가 더 든든해요 */
    BOWL("밥그릇"),

    /** 고양이 화장실: 덜 스트레스 받아요 */
    LITTER("화장실"),

    /** 옷·이름표·우비처럼 꾸며 주는 것: 쓰다듬으면 더 좋아해요 */
    DECOR("꾸밈 용품"),
}

/**
 * 집 친구(동물) 돌보기. 날마다 배가 고프고 심심해진다 — 모의 쇼핑으로 산 사료·간식으로 먹이고, 장난감으로 놀아 주고,
 * 하네스가 있으면 산책을 간다. 잠자리가 있으면 덜 심심해하고, 자동 급식기가 있으면 덜 배고파한다.
 * 실제 돈과는 상관없다 (모의 지갑으로 산 용품만 쓴다).
 */
data class PetState(
    /** 배부름 0~100 */
    val full: Int = 80,
    /** 기분 0~100 */
    val joy: Int = 80,
    /** 남은 사료 (번) */
    val food: Int = 3,
    /** 남은 간식 (개) */
    val treats: Int = 2,
    /** 가진 용품 종류 */
    val gear: Set<CareKind> = emptySet(),
    /** 마지막으로 하루치 배고픔을 센 날 (null = 아직 시작 안 함) */
    val day: Long? = null,
    val played: Long? = null,
    val walked: Long? = null,
    val petted: Long? = null,
    val groomed: Long? = null,
) {
    val hungry: Boolean get() = full < 30
    val bored: Boolean get() = joy < 30

    fun encode(): String = listOf(
        "full=$full", "joy=$joy", "food=$food", "treats=$treats", "gear=" + gear.joinToString(",") { it.name },
        "day=${day ?: ""}", "played=${played ?: ""}", "walked=${walked ?: ""}", "petted=${petted ?: ""}", "groomed=${groomed ?: ""}",
    ).joinToString(";")

    companion object {
        fun decode(s: String?): PetState {
            if (s.isNullOrBlank()) return PetState()
            val m = s.split(';').mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()
            fun int(k: String, d: Int) = m[k]?.toIntOrNull() ?: d
            return PetState(
                full = int("full", 80).coerceIn(0, 100),
                joy = int("joy", 80).coerceIn(0, 100),
                food = int("food", 3).coerceAtLeast(0),
                treats = int("treats", 2).coerceAtLeast(0),
                gear = m["gear"].orEmpty().split(',').mapNotNull { g -> CareKind.entries.firstOrNull { it.name == g } }.toSet(),
                day = m["day"]?.toLongOrNull(),
                played = m["played"]?.toLongOrNull(),
                walked = m["walked"]?.toLongOrNull(),
                petted = m["petted"]?.toLongOrNull(),
                groomed = m["groomed"]?.toLongOrNull(),
            )
        }
    }
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

    const val FEED = 35
    const val TREAT_FULL = 5
    const val TREAT_JOY = 15
    const val PLAY = 20
    const val WALK = 25
    const val PET = 8
    const val GROOM = 14
    const val BOWL_BONUS = 10
    const val DECOR_BONUS = 4

    /** 하루가 지날 때마다: 배고파지고(급식기가 있으면 덜) 심심해진다(잠자리가 있으면 덜) */
    fun passDays(s: PetState, today: LocalDate): PetState {
        val t = today.toEpochDay()
        val last = s.day ?: return s.copy(day = t)
        val days = (t - last).coerceIn(0, 30).toInt()
        if (days == 0) return s
        val hunger = if (CareKind.FEEDER in s.gear) 12 else 20
        val boredom = (if (CareKind.BED in s.gear) 8 else 12) - (if (CareKind.LITTER in s.gear) 3 else 0)
        return s.copy(full = (s.full - hunger * days).coerceAtLeast(0), joy = (s.joy - boredom * days).coerceAtLeast(0), day = t)
    }

    /** 몰에서 산 것 반영 */
    fun bought(s: PetState, productId: String, qty: Int = 1): PetState {
        val (kind, n) = effects[productId] ?: return s
        return when (kind) {
            CareKind.FOOD -> s.copy(food = s.food + n * qty, gear = s.gear + kind)
            CareKind.TREAT -> s.copy(treats = s.treats + n * qty, gear = s.gear + kind)
            else -> s.copy(gear = s.gear + kind)
        }
    }

    enum class Action(val label: String) { FEED("밥 주기"), TREAT("간식 주기"), PLAY("놀아 주기"), WALK("산책 가기"), GROOM("털 빗어 주기"), PET("쓰다듬기") }

    /** 이 동물에게 할 수 있는 돌보기 — 고양이는 산책 대신 집에서 논다 */
    fun actionsFor(kind: PetKind?): List<Action> =
        if (kind == PetKind.CAT) Action.entries.filter { it != Action.WALK } else Action.entries

    /** 지금 할 수 있는지 (못 하면 그 까닭) */
    fun blocked(s: PetState, a: Action, today: LocalDate): String? {
        val t = today.toEpochDay()
        return when (a) {
            Action.FEED -> when {
                s.food <= 0 -> "사료가 없어요"
                s.full >= 100 -> "배불러요"
                else -> null
            }
            Action.TREAT -> if (s.treats <= 0) "간식이 없어요" else null
            Action.PLAY -> when {
                CareKind.TOY !in s.gear -> "장난감이 없어요"
                s.played == t -> "오늘 놀았어요"
                else -> null
            }
            Action.WALK -> when {
                CareKind.HARNESS !in s.gear -> "하네스가 없어요"
                s.walked == t -> "오늘 다녀왔어요"
                else -> null
            }
            Action.GROOM -> when {
                CareKind.GROOM !in s.gear -> "빗이 없어요"
                s.groomed == t -> "오늘 빗었어요"
                else -> null
            }
            Action.PET -> if (s.petted == t) "오늘 쓰다듬었어요" else null
        }
    }

    fun act(s: PetState, a: Action, today: LocalDate): PetState {
        if (blocked(s, a, today) != null) return s
        val t = today.toEpochDay()
        fun c(v: Int) = v.coerceIn(0, 100)
        return when (a) {
            Action.FEED -> s.copy(food = s.food - 1, full = c(s.full + FEED + if (CareKind.BOWL in s.gear) BOWL_BONUS else 0))
            Action.TREAT -> s.copy(treats = s.treats - 1, full = c(s.full + TREAT_FULL), joy = c(s.joy + TREAT_JOY))
            Action.PLAY -> s.copy(joy = c(s.joy + PLAY), played = t)
            Action.WALK -> s.copy(joy = c(s.joy + WALK), full = c(s.full - 10), walked = t)
            Action.GROOM -> s.copy(joy = c(s.joy + GROOM), groomed = t)
            Action.PET -> s.copy(joy = c(s.joy + PET + if (CareKind.DECOR in s.gear) DECOR_BONUS else 0), petted = t)
        }
    }

    /** 한 줄 상태 ("콩이가 배고파해요") */
    fun line(name: String, s: PetState): String {
        val ga = name + josa(name, "이", "가")
        return when {
            s.full < 15 -> "$ga 많이 배고파해요"
            s.hungry -> "$ga 배고파해요"
            s.joy < 15 -> "$ga 많이 심심해해요"
            s.bored -> "$ga 심심해해요"
            s.full >= 70 && s.joy >= 70 -> "$ga 행복해요"
            else -> name + josa(name, "은", "는") + " 잘 지내요"
        }
    }

    /** 받침이 있으면 [withFinal], 없으면 [without] ("별이" / "콩이가") */
    fun josa(word: String, withFinal: String, without: String): String {
        val ch = word.lastOrNull() ?: return without
        return if (ch in '가'..'힣' && (ch - '가') % 28 != 0) withFinal else without
    }
}
