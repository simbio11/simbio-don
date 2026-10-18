package com.simbio.don.game

/** 캐릭터 성별 — 기본 머리·옷·얼굴이 다르다 (부위는 누구나 고를 수 있다) */
enum class Gender(val label: String) { MALE("남자"), FEMALE("여자") }

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
) {
    /** 저장용 한 줄: "gender=MALE;face=round;…" */
    fun encode(): String = listOf(
        "gender" to gender.name, "face" to face, "skin" to skin, "hair" to hair, "hairColor" to hairColor,
        "eyes" to eyes, "eyeColor" to eyeColor, "brow" to brow, "nose" to nose, "mouth" to mouth,
        "lashes" to lashes.toString(), "top" to top, "topColor" to topColor, "bottom" to bottom,
        "bottomColor" to bottomColor, "shoes" to shoes, "shoesColor" to shoesColor,
        "hat" to (hat ?: ""), "hatColor" to hatColor, "acc" to (acc ?: ""),
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
            )
        }
    }
}
