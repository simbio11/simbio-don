package com.simbio.don.game

/** 캐릭터 성별 — 기본 머리·옷·얼굴이 다르다 (부위는 누구나 고를 수 있다) */
enum class Gender(val label: String) { MALE("남자"), FEMALE("여자") }

/** 동물 캐릭터. 강아지·고양이는 처음부터, 나머지는 마일로 산다 ([Shop.wardrobeCost] "pet:이름") */
enum class Species(val id: String, val label: String) {
    HUMAN("human", "사람"),
    DOG("dog", "강아지"),
    CAT("cat", "고양이"),
    RABBIT("rabbit", "토끼"),
    HAMSTER("hamster", "햄스터"),
    FOX("fox", "여우"),
    PANDA("panda", "판다"),
    PENGUIN("penguin", "펭귄"),
    ;

    val isPet: Boolean get() = this != HUMAN

    companion object {
        fun of(id: String?): Species = entries.firstOrNull { it.id == id } ?: HUMAN
        val pets: List<Species> get() = entries.filter { it.isPet }
    }
}

/** 동물 소품 (사람 모자와 같은 색 고르기) */
enum class PetAcc(val id: String, val label: String) {
    COLLAR("collar", "방울 목걸이"),
    BANDANA("bandana", "반다나"),
    RIBBON("ribbon", "리본"),
    GLASSES("glasses", "동그란 안경"),
    SCARF("scarf", "목도리"),
    CROWN("crown", "왕관"),
    ;

    companion object {
        fun of(id: String?): PetAcc? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 내 캐릭터 모습. 부위·색은 그림 생성기(tools/pixelart/v3)가 만든 이름 그대로 쓴다.
 * 모르는 이름이 오면 앱이 기본 그림으로 그린다.
 */
data class CharacterLook(
    val gender: Gender = Gender.MALE,
    val face: String = "round",
    val skin: String = "기본",
    val hair: String = "short",
    val hairColor: String = "갈색",
    val eyes: String = "basic",
    val eyeColor: String = "갈색",
    val brow: String = "thick",
    val nose: String = "dot",
    val mouth: String = "basic",
    val lashes: Boolean = false,
    val top: String = "hoodie",
    val topColor: String = "파랑",
    val bottom: String = "pants",
    val bottomColor: String = "남색",
    val shoes: String = "sneakers",
    val shoesColor: String = "흰색",
    val hat: String? = null,
    val hatColor: String = "분홍",
    val acc: String? = null,
    /** 동물이면 그 동물 (사람이면 human) */
    val species: String = Species.HUMAN.id,
    /** 동물 털색 이름 (비면 그 동물 기본) */
    val fur: String = "",
    /** 동물 소품 */
    val petAcc: String? = null,
    /** 동물 이름 (예: 콩이) */
    val petName: String = "",
) {
    val kind: Species get() = Species.of(species)
    val isPet: Boolean get() = kind.isPet

    /** 화면에 부를 이름: 동물 이름이 있으면 그 이름, 없으면 동물 이름 */
    val callName: String get() = petName.ifBlank { kind.label }

    /** 저장용 한 줄: "gender=MALE;face=round;…" */
    fun encode(): String = listOf(
        "gender" to gender.name, "face" to face, "skin" to skin, "hair" to hair, "hairColor" to hairColor,
        "eyes" to eyes, "eyeColor" to eyeColor, "brow" to brow, "nose" to nose, "mouth" to mouth,
        "lashes" to lashes.toString(), "top" to top, "topColor" to topColor, "bottom" to bottom,
        "bottomColor" to bottomColor, "shoes" to shoes, "shoesColor" to shoesColor,
        "hat" to (hat ?: ""), "hatColor" to hatColor, "acc" to (acc ?: ""),
        "species" to species, "fur" to fur, "petAcc" to (petAcc ?: ""), "petName" to petName,
    ).joinToString(";") { (k, v) -> "$k=${v.replace(";", "").replace("=", "")}" }

    companion object {
        val MALE = CharacterLook()

        val FEMALE = CharacterLook(
            gender = Gender.FEMALE,
            face = "vline",
            hair = "long",
            eyes = "big",
            brow = "normal",
            mouth = "lips",
            lashes = true,
            top = "blouse",
            topColor = "분홍",
            bottom = "skirt",
            bottomColor = "남색",
            shoes = "flats",
            shoesColor = "갈색",
        )

        fun default(gender: Gender) = if (gender == Gender.FEMALE) FEMALE else MALE

        /** 동물 기본 모습 (사람 부위는 그대로 두어 나중에 사람으로 돌아가도 그 모습) */
        fun pet(species: Species, fur: String = "", name: String = "", base: CharacterLook = MALE) =
            base.copy(species = species.id, fur = fur, petName = name.trim().take(10))

        /** 저장된 한 줄 읽기. 비었거나 망가졌으면 기본 (남자) */
        fun decode(s: String?): CharacterLook {
            if (s.isNullOrBlank()) return MALE
            val m = s.split(';').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
            }.toMap()
            val gender = Gender.entries.firstOrNull { it.name == m["gender"] } ?: Gender.MALE
            val d = default(gender)
            fun str(k: String, def: String) = m[k]?.takeIf { it.isNotBlank() } ?: def
            return CharacterLook(
                gender = gender,
                face = str("face", d.face),
                skin = str("skin", d.skin),
                hair = str("hair", d.hair),
                hairColor = str("hairColor", d.hairColor),
                eyes = str("eyes", d.eyes),
                eyeColor = str("eyeColor", d.eyeColor),
                brow = str("brow", d.brow),
                nose = str("nose", d.nose),
                mouth = str("mouth", d.mouth),
                lashes = m["lashes"]?.toBooleanStrictOrNull() ?: d.lashes,
                top = str("top", d.top),
                topColor = str("topColor", d.topColor),
                bottom = str("bottom", d.bottom),
                bottomColor = str("bottomColor", d.bottomColor),
                shoes = str("shoes", d.shoes),
                shoesColor = str("shoesColor", d.shoesColor),
                hat = m["hat"]?.takeIf { it.isNotBlank() },
                hatColor = str("hatColor", d.hatColor),
                acc = m["acc"]?.takeIf { it.isNotBlank() },
                species = Species.of(m["species"]).id,
                fur = m["fur"].orEmpty(),
                petAcc = m["petAcc"]?.takeIf { it.isNotBlank() },
                petName = m["petName"].orEmpty(),
            )
        }
    }
}
