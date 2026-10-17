"""격자로 그린 작은 도트 그림. 글자 하나 = 픽셀 하나, 색은 palette.PALETTE.

빛은 왼쪽 위에서 온다: 왼쪽·위는 밝은 색(1~7, v, J …), 오른쪽·아래는 어두운 색(d, M, V, Z …).
"""

# ── 캐릭터 22×28 ───────────────────────────────────────────
# 머리·눈·입·몸·다리를 조합한다. 20칸으로 그리고 양옆을 한 칸씩 띄워 22칸 (주먹 든 자세 자리).

HAIR = [
    "......KKKKKKKK......",
    "....KKhhhhhhhhKK....",
    "...KhhJJJJhhhhhhK...",
    "..KhJJhhhhhhhhhhjK..",
    ".KhJhhhhhhhhhhhhhjK.",
    ".KhhhhhhhhhhhhhhhjK.",
    ".KhhhjhhhhhjhhhhjjK.",
    ".KhhjshhjhhsjhshhjK.",
]
EYES = {
    "open": [
        ".KhsssKKssssKKssshK.",
        ".KhsssKWssssKWssshK.",
        ".KhsssKKssssKKssshK.",
    ],
    "blink": [
        ".KhsssssssssssssshK.",
        ".KhsssssssssssssshK.",
        ".KhsssKKssssKKssshK.",
    ],
    "joy": [  # ^ ^
        ".KhssssKssssssKsshK.",
        ".KhsssKsKssssKsKshK.",
        ".KhsssssssssssssshK.",
    ],
    "sweat": [  # 오른쪽 땀방울
        ".KhsssKWssssKWssFhK.",
        ".KhsssKKssssKKssFhK.",
        ".KhsssssssssssssshK.",
    ],
    "sweat_blink": [
        ".KhsssssssssssssFhK.",
        ".KhsssssssssssssFhK.",
        ".KhsssKKssssKKssshK.",
    ],
    "sad": [  # 처진 눈
        ".KhsssssssssssssshK.",
        ".KhssKKKssssKKKsshK.",
        ".KhsssKKssssKKssshK.",
    ],
    "sad_blink": [
        ".KhsssssssssssssshK.",
        ".KhssKKKssssKKKsshK.",
        ".KhsssssssssssssshK.",
    ],
    "angry": [  # 치켜올린 눈썹
        ".KhsKKssssssssKKshK.",
        ".KhsssKKssssKKssshK.",
        ".KhsssKKssssKKssshK.",
    ],
    "angry_blink": [
        ".KhsKKssssssssKKshK.",
        ".KhsssssssssssssshK.",
        ".KhsssKKssssKKssshK.",
    ],
}
BLUSH = ".KhsbbssssssssbbshK."
MOUTH = {
    "smile": [".KhSsssKssssKsssShK.", "..KhSsssKKKKsssShK.."],
    "open": [".KhSssssKKKKssssShK.", "..KhSsssKmmKsssShK.."],
    "frown": [".KhSsssssKKsssssShK.", "..KhSsssKssKsssShK.."],
    "sad": [".KhSssssssssssssShK.", "..KhSssKKKKKsssShK.."],
    "angry": [".KhSsssKKKKKKsssShK.", "..KhSssKWWWWKssShK.."],
    "grin": [".KhSsssKKKKKKsssShK.", "..KhSssKmmmmKssShK.."],
}
JAW = [
    "...KhSSSssssSSSShK..",
    "....KKKKKKKKKKKK....",
]
# 후드티 가슴의 노란 동전 = 이 앱의 상징
BODY = [
    "...KKcvcKssKccCKK...",
    "..KvvcccWssWccccCK..",
    "..KvvccccWWcccccCK..",
    "..KvvcCccccccCccCK..",
    "..KvccCccyyccCccCK..",
    "..KvccCccyYccCcCCK..",
    "..KssSKCCCCCCKsSSK..",
    "...KKKppppppppKKK...",
]
LEGS = {
    "stand": [
        ".....KpppPPpppK.....",
        ".....KpppKKpppK.....",
        "....KwwwgKKwwwgK....",
        "....KKKKKKKKKKKK....",
    ],
    "walk0": [
        ".....KpppPPpppK.....",
        ".....KpppKKwwwgK....",
        "....KwwwgKKKKKKK....",
        "....KKKKKK..........",
    ],
    "walk1": [
        ".....KpppPPpppK.....",
        "....KwwwgKKpppK.....",
        "....KKKKKKKwwwgK....",
        "..........KKKKKK....",
    ],
}
# 오른 주먹을 볼 옆으로 든 '화이팅' 자세 (22칸 기준, _ = 그대로 둠)
FIST = [
    "_KKK_",
    "KssSK",
    "KsSSK",
    "KKcCK",
    "_KcCK",
    "KKcCK",
    "KcCK_",
    "CCK__",
    "CK___",
    "CK___",
    "CK___",
    "KK___",
]
SPARKLE = [
    "y.........",
    "..........",
    "2y........",
    "y.........",
]


def patch(rows, x, y, p):
    rows = list(rows)
    for dy, line in enumerate(p):
        r = list(rows[y + dy])
        for dx, ch in enumerate(line):
            if ch != "_" and ch != ".":
                r[x + dx] = ch
        rows[y + dy] = "".join(r)
    return rows


def _char(eyes, mouth, legs="stand", fist=False, sparkle=False, tear=False, anger=False):
    rows = HAIR + EYES[eyes] + [BLUSH] + MOUTH[mouth] + JAW + BODY + LEGS[legs]
    rows = ["." + r + "." for r in rows]
    if fist:
        rows = patch(rows, 16, 11, FIST)
    if sparkle:
        rows = patch(rows, 0, 0, SPARKLE)
        rows = patch(rows, 19, 2, ["y.", "2y", "y."])
    if tear:
        rows = patch(rows, 6, 11, ["F", "F", "f"])
        rows = patch(rows, 15, 11, ["F", "F", "f"])
    if anger:
        rows = patch(rows, 15, 1, [".r.r", "rr.r", ".r.r"])
    return rows


# 기분마다 [기본, 눈 감음, 걷기1, 걷기2]. 모의 지갑 여유에 따라 기뻐요 → 화났어요
_FACE = {
    # 기분: (눈, 눈 감은 눈, 입, 꾸밈)
    "joyful": ("joy", "joy", "grin", {"sparkle": True}),
    "happy": ("open", "blink", "open", {}),
    "calm": ("open", "blink", "smile", {}),
    "worried": ("sweat", "sweat_blink", "frown", {}),
    "sad": ("sad", "sad_blink", "sad", {"tear": True}),
    "angry": ("angry", "angry_blink", "angry", {"anger": True}),
    "cheer": ("joy", "joy", "open", {"fist": True}),
}
CHARACTER = {}
for mood, (eyes, blink, mouth, deco) in _FACE.items():
    blink_deco = dict(deco)
    if mood == "cheer":
        blink_deco["sparkle"] = True
    CHARACTER[mood] = [
        _char(eyes, mouth, **deco),
        _char(blink, mouth, **blink_deco),
        _char(eyes, mouth, "walk0", **deco),
        _char(eyes, mouth, "walk1", **deco),
    ]

# ── 물건 12×12 (집에 놓이는 것) ─────────────────────────────

ITEMS = {
    "shoes": [
        "............",
        "............",
        ".KKKK.......",
        "KWWwwK......",
        "KWwgwwKK....",
        "KWwrgwwgKK..",
        "KwwrrwgwwwK.",
        "KwwwrrrwwwgK",
        "K1rrrrrrrrdK",
        "KWWWWWWWWWwK",
        "KxxxxxxxxxXK",
        ".KKKKKKKKKK.",
    ],
    "clothes": [
        "............",
        "..KKK..KKK..",
        ".KvcvKKvccK.",
        "KvvccvvccCCK",
        "KvcccccccCNK",
        "KKKvcccccKKK",
        "..KvccccCK..",
        "..KvcyycCK..",
        "..KvcyYcCK..",
        "..KvccccCK..",
        "..KCCCCCNK..",
        "..KKKKKKKK..",
    ],
    "bag": [
        "............",
        "....KKKK....",
        "...KIKKIK...",
        "...KI..IK...",
        ".KKKKKKKKKK.",
        ".K66iiiiiIK.",
        "K6iiiiiiiIVK",
        "KiiiKyyKiIVK",
        "KiiiiKKiiIVK",
        "KiiiiiiiiIVK",
        "KIIIIIIIIVVK",
        ".KKKKKKKKKK.",
    ],
    "electronics": [
        "............",
        "...KKKKKK...",
        "..KgggggDK..",
        ".KgKKKKKKDK.",
        ".KgK....KDK.",
        ".KgK....KDK.",
        "KKKKK..KKKKK",
        "K5uuK..K5uUK",
        "K5uUK..KuuUK",
        "KuuUK..KuuUK",
        "KUUMK..KUUMK",
        "KKKKK..KKKKK",
    ],
    "book": [
        "............",
        "...KKKKKKKK.",
        "...K1rrrrwK.",
        "...KrrrrdwK.",
        "..KKKKKKKKK.",
        "..K4ttttT0K.",
        "..Kwwwwwww0K",
        ".KKKKKKKKKKK",
        ".K2yyyyyyEK.",
        ".KyyyyyyEwK.",
        ".KEEEEEEEwK.",
        ".KKKKKKKKKK.",
    ],
    "beauty": [
        "............",
        ".....KK.....",
        "....K2yK....",
        "....KyEK....",
        "...KKKKKK...",
        "..K6iiiiIK..",
        ".K6+iiiiiIK.",
        ".Ki+iiiiiIK.",
        ".KiiiiiiiIK.",
        ".KiiiiiiIIK.",
        ".KIIIIIIIVK.",
        "..KKKKKKKK..",
    ],
    "living": [
        "......K.....",
        ".....K2K....",
        ".....KyK....",
        "......K.....",
        "..KKKKKKKK..",
        "..K+wwwwgK..",
        "..K6iiiiIK..",
        "..K6iiiiIK..",
        "..KiiiiiIK..",
        "..KiiiiIIK..",
        "..KIIIIIVK..",
        "..KKKKKKKK..",
    ],
    "toy": [
        "............",
        ".KKK....KKK.",
        "KLlLKKKKKlOK",
        "KllllllllloK",
        ".KlKllllKoK.",
        ".KllLLLLloK.",
        ".KlLLKKLLoK.",
        "..KllLLloK..",
        ".KlKlllloKK.",
        "KlKlLLLlKoOK",
        ".KllLLlllOK.",
        ".KKKK..KKKK.",
    ],
    "meal": [
        "............",
        "...KKKKKK...",
        "..K77aaaaK..",
        ".K7a2aa2aAK.",
        ".KaaaaaaaAK.",
        "KKKKKKKKKKKK",
        "K3q3qq3qq3#K",
        "KzzzzzzzzzOK",
        "KyyyyyyyyyEK",
        ".K7aaaaaaAK.",
        ".KAAAAAAAZK.",
        "..KKKKKKKK..",
    ],
    "drink": [
        ".......KK...",
        "......K1K...",
        ".....KrK....",
        "..KKKKdKKK..",
        ".K+wwwwwgGK.",
        ".KKKKKKKKKK.",
        "..K+xxxxXK..",
        "..K+WzWzXK..",
        "..KxzzzzZK..",
        "...KzzzzK...",
        "...KzzzZK...",
        "....KKKK....",
    ],
    "etc": [
        "............",
        "...KK..KK...",
        "..K2yKKyEK..",
        "...KKyyKK...",
        "KKKKKyyKKKKK",
        "K55uuyEuuUUK",
        "KUUUUyEUUUMK",
        ".K5uuyEuuUK.",
        ".KuuuyEuuUK.",
        ".KuuuyEuuUK.",
        ".KUUUyEUUMK.",
        ".KKKKKKKKKK.",
    ],
    # 먹고 남은 쓰레기 (구겨진 종이봉투 + 파리)
    "meal_trash": [
        ".........K+.",
        "........+K..",
        "....K.KK....",
        "...K8KxXK...",
        "..K8xKxxXK..",
        "..K8xxxxXK..",
        ".K8xxXxxxXK.",
        ".KxxxxxAxXK.",
        ".KxAxxxxxXK.",
        ".KxxxxxxXXK.",
        ".KXXXXXXXzK.",
        "..KKKKKKKK..",
    ],
    # 옆으로 쓰러진 빈 컵 + 흘린 커피
    "drink_trash": [
        "............",
        "............",
        "............",
        "..........K.",
        ".........KrK",
        "..KKKKKKKrK.",
        ".K+ggggggKK.",
        "K+wwwwwwwgK.",
        "KwwwwwwwwgK.",
        ".KggggggGK..",
        "..KKKKKKK...",
        "zzZz........",
    ],
}


# ── 앱 아이콘 (돼지 저금통 + 떨어지는 동전) ─────────────────────
# 런처 아이콘은 54칸 격자(1칸 = 2dp)의 가운데에 놓는다. 안전 영역(지름 33칸) 안에 들어가는 크기.
ICON = [
    ".........KKKKK.........",
    "........Ky222yK........",
    "........K2yyyyEK.......",
    "........KyyyyyEK.......",
    "........KyyyyEEK.......",
    ".........KKKKK.........",
    ".......................",
    ".....KK..KKKKKKKK......",
    "....K6IKK66666666KK....",
    "....KIK666666666666K...",
    "...K6666KKKKK666666iK..",
    "...K66666666666666iiK.K",
    "..K666K666666666iiiiKKK",
    "..K666K66666666iiiiIIK.",
    "KKKK666666666iiiiiiIIK.",
    "KIIIK6bb666iiiiiiiIIIK.",
    "KKIKK666iiiiiiiiiiIIIK.",
    "KIIIK6iiiiiiiiiiiIIIIK.",
    ".KKKKiiiiiiiiiiiIIIIK..",
    "....KIIKKKKKKKKKIIIK...",
    "....KIIK.......KIIK....",
    "....KKKK.......KKKK....",
]
