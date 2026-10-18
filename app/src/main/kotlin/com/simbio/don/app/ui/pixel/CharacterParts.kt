package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (v3/export3.py) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 캐릭터 부위 아틀라스(px_char_atlas) 색인과 색 표식. */
object CharacterParts {
    const val W = 24
    const val H = 36
    const val COLS = 16

    /** 머리 꼭대기 위 여유 줄 (모자·올림머리 자리) */
    const val TOP = 4

    /** 부위 이름 → 아틀라스 칸 번호 */
    val index: Map<String, Int> = mapOf(
        "body:round" to 0,
        "body:vline" to 1,
        "body:square" to 2,
        "eyes:basic:open" to 3,
        "eyes:basic:sad" to 4,
        "eyes:basic:angry" to 5,
        "eyes:big:open" to 6,
        "eyes:big:sad" to 7,
        "eyes:big:angry" to 8,
        "eyes:cat:open" to 9,
        "eyes:cat:sad" to 10,
        "eyes:cat:angry" to 11,
        "eyes:puppy:open" to 12,
        "eyes:puppy:sad" to 13,
        "eyes:puppy:angry" to 14,
        "eyes:sharp:open" to 15,
        "eyes:sharp:sad" to 16,
        "eyes:sharp:angry" to 17,
        "eyes:shared:closed" to 18,
        "eyes:shared:joy" to 19,
        "eyes:shared:xx" to 20,
        "lashes" to 21,
        "brows:normal:normal" to 22,
        "brows:normal:up" to 23,
        "brows:normal:angry" to 24,
        "brows:normal:sad" to 25,
        "brows:thick:normal" to 26,
        "brows:thick:up" to 27,
        "brows:thick:angry" to 28,
        "brows:thick:sad" to 29,
        "brows:short:normal" to 30,
        "brows:short:up" to 31,
        "brows:short:angry" to 32,
        "brows:short:sad" to 33,
        "nose:dot" to 34,
        "nose:button" to 35,
        "nose:high" to 36,
        "nose:none" to 37,
        "mouth:basic:smile" to 38,
        "mouth:basic:calm" to 39,
        "mouth:cat:smile" to 40,
        "mouth:cat:calm" to 41,
        "mouth:lips:smile" to 42,
        "mouth:lips:calm" to 43,
        "mouth:small:smile" to 44,
        "mouth:small:calm" to 45,
        "mouth:shared:grin" to 46,
        "mouth:shared:open" to 47,
        "mouth:shared:frown" to 48,
        "mouth:shared:wobble" to 49,
        "mouth:shared:grit" to 50,
        "blush" to 51,
        "extra:tears" to 52,
        "extra:sweat" to 53,
        "extra:anger" to 54,
        "extra:sparkle" to 55,
        "hair:short:front" to 56,
        "hair:long:front" to 57,
        "hair:long:back" to 58,
        "hair:buzz:front" to 59,
        "hair:parted:front" to 60,
        "hair:perm:front" to 61,
        "hair:bob:front" to 62,
        "hair:bob:back" to 63,
        "hair:ponytail:front" to 64,
        "hair:ponytail:back" to 65,
        "hair:twintail:front" to 66,
        "hair:twintail:back" to 67,
        "hair:bun:front" to 68,
        "hair:wave:front" to 69,
        "hair:wave:back" to 70,
        "top:tee" to 71,
        "top:hoodie" to 72,
        "top:shirt" to 73,
        "top:blouse" to 74,
        "top:knit" to 75,
        "top:jacket" to 76,
        "top:padding" to 77,
        "top:tank" to 78,
        "top:dress" to 79,
        "bottom:pants" to 80,
        "bottom:shorts" to 81,
        "bottom:skirt" to 82,
        "bottom:longskirt" to 83,
        "bottom:jogger" to 84,
        "shoes:sneakers" to 85,
        "shoes:flats" to 86,
        "shoes:boots" to 87,
        "hat:cap" to 88,
        "hat:beanie" to 89,
        "hat:ribbon" to 90,
        "hat:crown" to 91,
        "acc:glasses" to 92,
        "acc:sunglasses" to 93,
        "acc:earrings" to 94,
        "acc:necklace" to 95,
    )

    // 색 묶음별 표식 색 (밝음 → 어둠)
    val SKIN = intArrayOf(0xFFFDE3CF.toInt(), 0xFFF7CDB0.toInt(), 0xFFE2A689.toInt(), 0xFFB9785F.toInt())
    val HAIR = intArrayOf(0xFFB98A62.toInt(), 0xFF8A5A3C.toInt(), 0xFF6B432D.toInt(), 0xFF4B2C1F.toInt())
    val CLOTH1 = intArrayOf(0xFF9CC0F5.toInt(), 0xFF5F93E6.toInt(), 0xFF4371C4.toInt(), 0xFF304F93.toInt())
    val CLOTH2 = intArrayOf(0xFF6F7AA6.toInt(), 0xFF4D5784.toInt(), 0xFF373F66.toInt(), 0xFF262B47.toInt())
    val ACCENT = intArrayOf(0xFFFFD25E.toInt(), 0xFFD99A2E.toInt())
    val SHOES = intArrayOf(0xFFF4F1EC.toInt(), 0xFFC9C4BD.toInt(), 0xFF8D6A55.toInt())
    val EYE = intArrayOf(0xFF4A3328.toInt(), 0xFF9A6A48.toInt())
    val HAT = intArrayOf(0xFFFFB3B8.toInt(), 0xFFF2707A.toInt(), 0xFFC94F5B.toInt(), 0xFF8E3442.toInt())

    /** 고를 수 있는 색 (이름 → 묶음 순서대로) */
    val skinTones: List<Pair<String, IntArray>> = listOf(
        "밝은" to intArrayOf(0xFFFFF0E6.toInt(), 0xFFFBDCC8.toInt(), 0xFFEAB79C.toInt(), 0xFFC08670.toInt()),
        "기본" to intArrayOf(0xFFFDE3CF.toInt(), 0xFFF7CDB0.toInt(), 0xFFE2A689.toInt(), 0xFFB9785F.toInt()),
        "건강한" to intArrayOf(0xFFF2C9A4.toInt(), 0xFFDBA57E.toInt(), 0xFFB97D5A.toInt(), 0xFF8C5A3E.toInt()),
        "구릿빛" to intArrayOf(0xFFC9906A.toInt(), 0xFFA8704D.toInt(), 0xFF83513A.toInt(), 0xFF5E3727.toInt()),
    )
    val hairColors: List<Pair<String, IntArray>> = listOf(
        "검정" to intArrayOf(0xFF5A5266.toInt(), 0xFF3D3646.toInt(), 0xFF2C2633.toInt(), 0xFF1D1922.toInt()),
        "흑갈" to intArrayOf(0xFF8B6A58.toInt(), 0xFF5E4336.toInt(), 0xFF45302A.toInt(), 0xFF2F201C.toInt()),
        "갈색" to intArrayOf(0xFFB98A62.toInt(), 0xFF8A5A3C.toInt(), 0xFF6B432D.toInt(), 0xFF4B2C1F.toInt()),
        "밝은 갈색" to intArrayOf(0xFFE2B47E.toInt(), 0xFFC08A56.toInt(), 0xFF9B6A3F.toInt(), 0xFF744B2B.toInt()),
        "금발" to intArrayOf(0xFFFFF0B0.toInt(), 0xFFF2CF6E.toInt(), 0xFFD8A94A.toInt(), 0xFFA97A32.toInt()),
        "분홍" to intArrayOf(0xFFFFD0E1.toInt(), 0xFFF39AB9.toInt(), 0xFFD4709A.toInt(), 0xFFA44F76.toInt()),
        "하늘" to intArrayOf(0xFFC9E7FF.toInt(), 0xFF86BDF0.toInt(), 0xFF5B93D6.toInt(), 0xFF3E68A8.toInt()),
        "은발" to intArrayOf(0xFFFFFFFF.toInt(), 0xFFDDE0E8.toInt(), 0xFFB4B9C6.toInt(), 0xFF868B9B.toInt()),
        "빨강" to intArrayOf(0xFFFF9C86.toInt(), 0xFFE05A4A.toInt(), 0xFFB63E35.toInt(), 0xFF842A28.toInt()),
    )
    val clothColors: List<Pair<String, IntArray>> = listOf(
        "파랑" to intArrayOf(0xFF9CC0F5.toInt(), 0xFF5F93E6.toInt(), 0xFF4371C4.toInt(), 0xFF304F93.toInt()),
        "남색" to intArrayOf(0xFF7B88B8.toInt(), 0xFF4D5784.toInt(), 0xFF373F66.toInt(), 0xFF272C4A.toInt()),
        "빨강" to intArrayOf(0xFFFF9F95.toInt(), 0xFFE5574F.toInt(), 0xFFBD3F3C.toInt(), 0xFF8F2C30.toInt()),
        "분홍" to intArrayOf(0xFFFFD1E0.toInt(), 0xFFF59BBA.toInt(), 0xFFD97399.toInt(), 0xFFA95276.toInt()),
        "노랑" to intArrayOf(0xFFFFF1A8.toInt(), 0xFFF7D058.toInt(), 0xFFDBA63A.toInt(), 0xFFA87628.toInt()),
        "초록" to intArrayOf(0xFFB8E6A4.toInt(), 0xFF74BF6A.toInt(), 0xFF4E9452.toInt(), 0xFF356A3E.toInt()),
        "민트" to intArrayOf(0xFFBFF0E2.toInt(), 0xFF71CFB7.toInt(), 0xFF47A691.toInt(), 0xFF2F7768.toInt()),
        "보라" to intArrayOf(0xFFD6C2FA.toInt(), 0xFFA07DDF.toInt(), 0xFF7A57BD.toInt(), 0xFF56398E.toInt()),
        "검정" to intArrayOf(0xFF6A6474.toInt(), 0xFF45404F.toInt(), 0xFF322E3A.toInt(), 0xFF211E27.toInt()),
        "흰색" to intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1EFEB.toInt(), 0xFFD6D1CA.toInt(), 0xFFAAA39B.toInt()),
        "회색" to intArrayOf(0xFFD7D9DE.toInt(), 0xFFA9ADB6.toInt(), 0xFF80858F.toInt(), 0xFF5C606A.toInt()),
        "베이지" to intArrayOf(0xFFF6E6CC.toInt(), 0xFFE1C79F.toInt(), 0xFFC3A47B.toInt(), 0xFF957A57.toInt()),
        "갈색" to intArrayOf(0xFFD1A27A.toInt(), 0xFFA6744D.toInt(), 0xFF7F5435.toInt(), 0xFF5A3A25.toInt()),
        "청" to intArrayOf(0xFFA9C7EA.toInt(), 0xFF6D97C9.toInt(), 0xFF4F74A6.toInt(), 0xFF38547A.toInt()),
    )
    val eyeColors: List<Pair<String, IntArray>> = listOf(
        "갈색" to intArrayOf(0xFF4A3328.toInt(), 0xFF9A6A48.toInt()),
        "검정" to intArrayOf(0xFF24202A.toInt(), 0xFF5A5266.toInt()),
        "파랑" to intArrayOf(0xFF2F5FB0.toInt(), 0xFF78A8EE.toInt()),
        "초록" to intArrayOf(0xFF2F7A49.toInt(), 0xFF72C08A.toInt()),
        "보라" to intArrayOf(0xFF6A45A8.toInt(), 0xFFA888E0.toInt()),
        "빨강" to intArrayOf(0xFF9A2F3C.toInt(), 0xFFE0707A.toInt()),
    )
    val hatColors: List<Pair<String, IntArray>> = listOf(
        "분홍" to intArrayOf(0xFFFFB3B8.toInt(), 0xFFF2707A.toInt(), 0xFFC94F5B.toInt(), 0xFF8E3442.toInt()),
        "검정" to intArrayOf(0xFF6A6474.toInt(), 0xFF45404F.toInt(), 0xFF322E3A.toInt(), 0xFF211E27.toInt()),
        "흰색" to intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1EFEB.toInt(), 0xFFD6D1CA.toInt(), 0xFFAAA39B.toInt()),
        "파랑" to intArrayOf(0xFF9CC0F5.toInt(), 0xFF5F93E6.toInt(), 0xFF4371C4.toInt(), 0xFF304F93.toInt()),
        "노랑" to intArrayOf(0xFFFFF1A8.toInt(), 0xFFF7D058.toInt(), 0xFFDBA63A.toInt(), 0xFFA87628.toInt()),
        "베이지" to intArrayOf(0xFFF6E6CC.toInt(), 0xFFE1C79F.toInt(), 0xFFC3A47B.toInt(), 0xFF957A57.toInt()),
    )

    /** 고를 수 있는 부위: id, 이름 */
    data class Part(val id: String, val label: String, val flag: Boolean = false)

    val faceShapes = listOf(
        Part("round", "동글"),
        Part("vline", "V라인"),
        Part("square", "각진"),
    )
    val eyeStyles = listOf(
        Part("basic", "또렷한 눈"),
        Part("big", "왕눈"),
        Part("cat", "고양이 눈"),
        Part("puppy", "강아지 눈"),
        Part("sharp", "날렵한 눈"),
    )
    val browStyles = listOf(
        Part("normal", "일자 눈썹"),
        Part("thick", "진한 눈썹"),
        Part("short", "짧은 눈썹"),
    )
    val noses = listOf(
        Part("dot", "점 코"),
        Part("button", "동글 코"),
        Part("high", "오똑한 코"),
        Part("none", "코 없음"),
    )
    val mouths = listOf(
        Part("basic", "기본 입"),
        Part("cat", "고양이 입"),
        Part("lips", "도톰한 입술"),
        Part("small", "작은 입"),
    )

    /** flag = 귀가 보이는 머리 */
    val hairs = listOf(
        Part("short", "짧은 머리", true),
        Part("long", "긴 생머리"),
        Part("buzz", "스포츠머리", true),
        Part("parted", "댄디 가르마", true),
        Part("perm", "곱슬 펌"),
        Part("bob", "단발"),
        Part("ponytail", "포니테일", true),
        Part("twintail", "양갈래", true),
        Part("bun", "똥머리", true),
        Part("wave", "웨이브 긴머리"),
    )

    /** flag = 원피스 (아래옷을 가린다) */
    val tops = listOf(
        Part("tee", "반팔 티"),
        Part("hoodie", "후드티"),
        Part("shirt", "셔츠"),
        Part("blouse", "블라우스"),
        Part("knit", "니트"),
        Part("jacket", "재킷"),
        Part("padding", "패딩"),
        Part("tank", "민소매"),
        Part("dress", "원피스", true),
    )
    val bottoms = listOf(
        Part("pants", "긴바지"),
        Part("shorts", "반바지"),
        Part("skirt", "치마"),
        Part("longskirt", "롱스커트"),
        Part("jogger", "트레이닝"),
    )
    val shoes = listOf(
        Part("sneakers", "운동화"),
        Part("flats", "구두"),
        Part("boots", "부츠"),
    )
    val hats = listOf(
        Part("cap", "캡모자"),
        Part("beanie", "비니"),
        Part("ribbon", "리본"),
        Part("crown", "왕관"),
    )
    val accs = listOf(
        Part("glasses", "안경"),
        Part("sunglasses", "선글라스"),
        Part("earrings", "귀걸이"),
        Part("necklace", "목걸이"),
    )

    /** 기분마다 얼굴: 눈 상태, 눈썹 상태, 입 상태, 덧그림 */
    data class Face(val eyes: String, val brow: String, val mouth: String, val extra: String?)

    val moods: Map<String, Face> = mapOf(
        "joyful" to Face("joy", "up", "grin", "sparkle"),
        "happy" to Face("open", "up", "open", null),
        "calm" to Face("open", "normal", "smile", null),
        "worried" to Face("open", "sad", "wobble", "sweat"),
        "sad" to Face("sad", "sad", "frown", "tears"),
        "angry" to Face("angry", "angry", "grit", "anger"),
        "cheer" to Face("xx", "up", "grin", "sparkle"),
    )

    /** 공통 눈 (스타일 상관없이 같은 그림) */
    val sharedEyes = setOf("closed", "joy", "xx")
}
