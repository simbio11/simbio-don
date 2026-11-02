package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (v3/pets.py) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 동물 캐릭터 아틀라스(px_pet_atlas) 배치와 털 표식 색 */
object PetParts {
    const val W = 24
    const val H = 36
    val species = listOf("dog", "cat", "rabbit", "hamster", "fox", "panda", "penguin")
    val moods = listOf("joyful", "happy", "calm", "worried", "sad", "angry", "cheer")
    val poses = listOf("stand", "blink", "walk_left", "walk_right")
    val accs = listOf("collar", "bandana", "ribbon", "glasses", "crown", "scarf")

    /** 털 표식: 빛 · 기본 · 그늘 · 깊은 그늘 · 배 */
    val FUR = longArrayOf(0xFFFBE6CD, 0xFFE8B483, 0xFFC98B5A, 0xFF9A643C, 0xFFFFF7EC).map { it.toInt() }.toIntArray()

    /** 동물마다 고를 수 있는 털색 (첫 번째가 기본) */
    val furColors: Map<String, List<Pair<String, IntArray>>> = mapOf(
        "dog" to listOf("갈색" to longArrayOf(0xFFFBE6CD, 0xFFE8B483, 0xFFC98B5A, 0xFF9A643C, 0xFFFFF7EC).map { it.toInt() }.toIntArray(), "크림" to longArrayOf(0xFFFFF6E6, 0xFFF5DDB8, 0xFFDFBA8A, 0xFFBF9466, 0xFFFFFAF2).map { it.toInt() }.toIntArray(), "흰둥이" to longArrayOf(0xFFFFFFFF, 0xFFF4F1EC, 0xFFDCD5CB, 0xFFB5AA9C, 0xFFFFFFFF).map { it.toInt() }.toIntArray(), "누렁이" to longArrayOf(0xFFFFE2A6, 0xFFF2BB5C, 0xFFD49433, 0xFFA8701F, 0xFFFFF1D6).map { it.toInt() }.toIntArray(), "검정" to longArrayOf(0xFF6D6672, 0xFF4A4452, 0xFF36313D, 0xFF25212B, 0xFFE6DCCF).map { it.toInt() }.toIntArray()),
        "cat" to listOf("치즈" to longArrayOf(0xFFFFD9A8, 0xFFF5A855, 0xFFD88433, 0xFFA9601F, 0xFFFFF3E2).map { it.toInt() }.toIntArray(), "회색" to longArrayOf(0xFFE6E6EA, 0xFFB9BAC2, 0xFF9496A1, 0xFF6D6F7A, 0xFFF7F7F9).map { it.toInt() }.toIntArray(), "고등어" to longArrayOf(0xFFD9D2C3, 0xFFA99F8C, 0xFF857B69, 0xFF5F5747, 0xFFF1ECE2).map { it.toInt() }.toIntArray(), "흰색" to longArrayOf(0xFFFFFFFF, 0xFFF4F1EC, 0xFFDCD5CB, 0xFFB5AA9C, 0xFFFFFFFF).map { it.toInt() }.toIntArray(), "검정" to longArrayOf(0xFF6D6672, 0xFF4A4452, 0xFF36313D, 0xFF25212B, 0xFF5B5560).map { it.toInt() }.toIntArray()),
        "rabbit" to listOf("흰색" to longArrayOf(0xFFFFFFFF, 0xFFF4F1EC, 0xFFDCD5CB, 0xFFB5AA9C, 0xFFFFFFFF).map { it.toInt() }.toIntArray(), "회색" to longArrayOf(0xFFE6E6EA, 0xFFB9BAC2, 0xFF9496A1, 0xFF6D6F7A, 0xFFF7F7F9).map { it.toInt() }.toIntArray(), "갈색" to longArrayOf(0xFFFBE6CD, 0xFFE8B483, 0xFFC98B5A, 0xFF9A643C, 0xFFFFF7EC).map { it.toInt() }.toIntArray()),
        "hamster" to listOf("골든" to longArrayOf(0xFFFFE7C2, 0xFFF3C27F, 0xFFD9A05A, 0xFFB07A3A, 0xFFFFFAF0).map { it.toInt() }.toIntArray(), "흰색" to longArrayOf(0xFFFFFFFF, 0xFFF4F1EC, 0xFFDCD5CB, 0xFFB5AA9C, 0xFFFFFFFF).map { it.toInt() }.toIntArray(), "회색" to longArrayOf(0xFFE6E6EA, 0xFFB9BAC2, 0xFF9496A1, 0xFF6D6F7A, 0xFFF7F7F9).map { it.toInt() }.toIntArray()),
        "fox" to listOf("주황" to longArrayOf(0xFFFFC08A, 0xFFF08A3C, 0xFFCF6A24, 0xFF9E4A14, 0xFFFFF7EE).map { it.toInt() }.toIntArray(), "은여우" to longArrayOf(0xFFECEEF2, 0xFFB4B8C4, 0xFF8C91A0, 0xFF646978, 0xFFFFFFFF).map { it.toInt() }.toIntArray()),
        "panda" to listOf("판다" to longArrayOf(0xFFFFFFFF, 0xFFF6F4F0, 0xFFDEDAD3, 0xFFBDB7AE, 0xFFFFFFFF).map { it.toInt() }.toIntArray()),
        "penguin" to listOf("펭귄" to longArrayOf(0xFF5D6476, 0xFF3C4252, 0xFF2C303C, 0xFF1F222B, 0xFFFFFFFF).map { it.toInt() }.toIntArray()),
    )
}
