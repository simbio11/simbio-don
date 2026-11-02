package com.simbio.don.game

/** 캐릭터 성별 — 기본 머리·옷·얼굴이 다르다 (부위는 누구나 고를 수 있다) */
enum class Gender(val label: String) { MALE("남자"), FEMALE("여자") }

/** 강아지인지 고양이인지 */
enum class PetKind(val label: String) { DOG("강아지"), CAT("고양이") }

/**
 * 같이 사는 친구. 사람 아니면 품종 하나. 처음부터 고를 수 있는 품종은 [miles]가 0이고, 나머지는 마일로 한 번 사면 된다
 * ([Shop.wardrobeCost] "pet:품종"). [id]는 그림 폴더 이름(assets/pets/<id>)이기도 해서 바꾸지 않는다.
 * [fur]: 그 품종의 기본 털색 이름 ([PetFur]).
 */
enum class Species(val id: String, val label: String, val kind: PetKind?, val miles: Long, val fur: String) {
    HUMAN("human", "사람", null, 0, ""),
    SHIBA_MIX("shiba_mix", "믹스견", PetKind.DOG, 0, "탠"),
    TABBY_CAT("tabby_cat", "치즈태비", PetKind.CAT, 0, "치즈"),
    SHIBA("shiba", "시바견", PetKind.DOG, 2_000, "탠"),
    POODLE("poodle", "푸들", PetKind.DOG, 2_500, "크림"),
    POMERANIAN("pomeranian", "포메라니안", PetKind.DOG, 3_000, "크림"),
    LONGHAIR_CAT("longhair_cat", "장모 고양이", PetKind.CAT, 3_000, "은빛"),
    CORGI("corgi", "웰시코기", PetKind.DOG, 3_500, "탠"),
    BEAGLE("beagle", "비글", PetKind.DOG, 4_000, "브라운"),
    LONGHAIR_TABBY("longhair_tabby_cat", "장모 치즈냥", PetKind.CAT, 4_000, "치즈"),
    BULLDOG("bulldog", "불독", PetKind.DOG, 5_000, "크림"),
    POMERANIAN_ALT("pomeranian_alt", "스피츠", PetKind.DOG, 6_000, "흰색"),
    ;

    val isPet: Boolean get() = this != HUMAN

    /** 이 품종에서 고를 수 있는 털색 (기본 털색이 맨 앞) */
    val furs: List<String> get() = PetFur.optionsFor(this)

    companion object {
        /** 저장된 id → 품종. 예전 동물(강아지·고양이 말고 토끼·펭귄…)이나 모르는 값은 가까운 친구로 */
        fun of(id: String?): Species = entries.firstOrNull { it.id == id } ?: when (id) {
            "cat" -> TABBY_CAT
            "dog", "rabbit", "hamster", "fox", "panda", "penguin" -> SHIBA_MIX
            else -> HUMAN
        }

        val pets: List<Species> get() = entries.filter { it.isPet }

        /** 처음 설정에서 고르는 친구 */
        val starters: List<Species> get() = pets.filter { it.miles == 0L }
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
    /** 동물 이름 (예: 콩이) */
    val petName: String = "",
) {
    val kind: Species get() = Species.of(species)
    val isPet: Boolean get() = kind.isPet

    /** 지금 털색 이름 (비면 그 품종 기본) */
    val furName: String get() = fur.takeIf { it in kind.furs } ?: kind.fur

    /** 화면에 부를 이름: 동물 이름이 있으면 그 이름, 없으면 품종 이름 */
    val callName: String get() = petName.ifBlank { kind.label }

    /** 저장용 한 줄: "gender=MALE;face=round;…" */
    fun encode(): String = listOf(
        "gender" to gender.name, "face" to face, "skin" to skin, "hair" to hair, "hairColor" to hairColor,
        "eyes" to eyes, "eyeColor" to eyeColor, "brow" to brow, "nose" to nose, "mouth" to mouth,
        "lashes" to lashes.toString(), "top" to top, "topColor" to topColor, "bottom" to bottom,
        "bottomColor" to bottomColor, "shoes" to shoes, "shoesColor" to shoesColor,
        "hat" to (hat ?: ""), "hatColor" to hatColor, "acc" to (acc ?: ""),
        "species" to species, "fur" to fur, "petName" to petName,
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
                petName = m["petName"].orEmpty(),
            )
        }
    }
}
