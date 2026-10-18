package com.simbio.don.game

/**
 * 마일로 사는 것들 (가구점·이사·옷장)과 뷰티샵 값.
 * 실제 돈(원)은 건드리지 않는다. 뷰티샵만 모의 지갑(가짜 돈)으로도 낼 수 있다.
 */
object Shop {
    // ── 이사 ──

    /** 그 집으로 이사하는 값 (마일). 원룸은 처음 집 */
    fun moveCost(level: HouseLevel): Long = when (level) {
        HouseLevel.ONE_ROOM -> 0
        HouseLevel.TWO_ROOM -> 15_000
        HouseLevel.APARTMENT -> 50_000
    }

    // ── 수납 가구 ──

    const val MAX_STORAGE_LEVEL = 3

    /** 수납 가구 한 단계마다 늘어나는 칸 */
    fun storageStep(slot: Slot): Int = when (slot) {
        Slot.WARDROBE, Slot.SHELF -> 3
        Slot.SHOE_RACK, Slot.DESK -> 2
        Slot.TRASH -> 0
    }

    /** 가구점에서 늘릴 수 있는 수납 칸 */
    val storageSlots = listOf(Slot.WARDROBE, Slot.SHOE_RACK, Slot.DESK, Slot.SHELF)

    /** [level]단계(1~3)로 올리는 값 (마일) */
    fun storageCost(level: Int): Long = when (level) {
        1 -> 800
        2 -> 1_600
        3 -> 3_200
        else -> 0
    }

    // ── 인테리어 (색 바꾸기) ──

    enum class Interior(val label: String, val cost: Long) {
        WALL("벽지", 1_500),
        FLOOR("바닥", 2_000),
        RUG("러그", 800),
        CURTAIN("커튼", 600),
        BEDDING("이불", 700),
    }

    // ── 옷장 (마일) ──

    /** 처음부터 가진 옷 (값 0). 색은 언제나 무료 */
    private val WARDROBE_PRICE: Map<String, Long> = mapOf(
        "top:knit" to 600, "top:jacket" to 1_200, "top:padding" to 1_500, "top:tank" to 400, "top:dress" to 1_000,
        "bottom:shorts" to 400, "bottom:longskirt" to 800, "bottom:jogger" to 500,
        "shoes:boots" to 900,
        "hat:cap" to 500, "hat:beanie" to 500, "hat:ribbon" to 600, "hat:crown" to 5_000,
        "acc:glasses" to 400, "acc:sunglasses" to 700, "acc:earrings" to 800, "acc:necklace" to 1_000,
    )

    /** 옷장 물건 값 (마일). 키는 "top:knit"처럼 "종류:이름". 처음부터 가진 것은 0 */
    fun wardrobeCost(key: String): Long = WARDROBE_PRICE[key] ?: 0

    // ── 뷰티샵 (원, 모의 돈) ──

    enum class Procedure(val label: String, val won: Long) {
        HAIR("커트·펌", 40_000),
        HAIR_COLOR("염색", 80_000),
        SKIN("피부 관리", 150_000),
        FACE("윤곽", 600_000),
        EYES("눈 성형", 400_000),
        EYE_COLOR("컬러 렌즈", 30_000),
        LASHES("속눈썹 펌", 60_000),
        BROWS("눈썹 정리", 40_000),
        NOSE("코 성형", 500_000),
        MOUTH("입술 시술", 300_000),
    }

    /** 지금 모습에서 [next]로 바꾸는 데 드는 시술 (옷·모자는 뷰티샵이 아니라 옷장) */
    fun procedures(now: CharacterLook, next: CharacterLook): List<Procedure> = buildList {
        if (now.hair != next.hair) add(Procedure.HAIR)
        if (now.hairColor != next.hairColor) add(Procedure.HAIR_COLOR)
        if (now.skin != next.skin) add(Procedure.SKIN)
        if (now.face != next.face) add(Procedure.FACE)
        if (now.eyes != next.eyes) add(Procedure.EYES)
        if (now.eyeColor != next.eyeColor) add(Procedure.EYE_COLOR)
        if (now.lashes != next.lashes) add(Procedure.LASHES)
        if (now.brow != next.brow) add(Procedure.BROWS)
        if (now.nose != next.nose) add(Procedure.NOSE)
        if (now.mouth != next.mouth) add(Procedure.MOUTH)
    }

    /** 옷장에서 새로 사야 하는 것 (아직 없는 옷·모자·소품) */
    fun wardrobeNeeded(next: CharacterLook, owned: Set<String>): List<String> =
        listOfNotNull("top:${next.top}", "bottom:${next.bottom}", "shoes:${next.shoes}", next.hat?.let { "hat:$it" }, next.acc?.let { "acc:$it" })
            .filter { wardrobeCost(it) > 0 && it !in owned }

    /** 얼굴·머리는 그대로 두고 옷·모자·소품만 [dress]에서 가져온 모습 */
    fun withClothes(face: CharacterLook, dress: CharacterLook): CharacterLook = face.copy(
        top = dress.top, topColor = dress.topColor, bottom = dress.bottom, bottomColor = dress.bottomColor,
        shoes = dress.shoes, shoesColor = dress.shoesColor, hat = dress.hat, hatColor = dress.hatColor, acc = dress.acc,
    )
}

/** 집 꾸민 상태: 수납 가구 단계, 산 인테리어 색, 고른 인테리어 색 */
data class HouseSetup(
    val storage: Map<Slot, Int> = emptyMap(),
    /** "WALL:민트"처럼 산 것 (기본 색은 늘 있음) */
    val ownedInterior: Set<String> = emptySet(),
    /** 고른 색 (없으면 기본) */
    val interior: Map<Shop.Interior, String> = emptyMap(),
) {
    /** 수납 가구로 늘어난 칸 */
    fun bonus(): Map<Slot, Int> = storage.mapValues { (slot, lv) -> lv.coerceIn(0, Shop.MAX_STORAGE_LEVEL) * Shop.storageStep(slot) }

    fun encode(): String = listOf(
        "storage=" + storage.entries.joinToString(",") { "${it.key.name}:${it.value}" },
        "owned=" + ownedInterior.joinToString(","),
        "interior=" + interior.entries.joinToString(",") { "${it.key.name}:${it.value}" },
    ).joinToString(";")

    companion object {
        fun decode(s: String?): HouseSetup {
            if (s.isNullOrBlank()) return HouseSetup()
            val m = s.split(';').mapNotNull { kv -> kv.indexOf('=').takeIf { it > 0 }?.let { kv.substring(0, it) to kv.substring(it + 1) } }.toMap()
            fun pairs(k: String) = m[k].orEmpty().split(',').filter { ':' in it }.map { it.substringBefore(':') to it.substringAfter(':') }
            return HouseSetup(
                storage = pairs("storage").mapNotNull { (k, v) -> Slot.entries.firstOrNull { it.name == k }?.let { it to (v.toIntOrNull() ?: 0) } }.toMap(),
                ownedInterior = m["owned"].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
                interior = pairs("interior").mapNotNull { (k, v) -> Shop.Interior.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
            )
        }
    }
}
