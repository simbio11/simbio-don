"""
캐릭터 v3: 24×32 레이어 조합.

그리는 순서: 뒷머리 → 몸(피부) → 아래옷 → 신발 → 윗옷 → 얼굴(눈썹·눈·코·입·볼) → 앞머리 → 모자·장신구.
모든 부위는 같은 24×32 칸 위에 그린다 (투명 '.'). 색은 cpal의 표식 글자 → 앱이 고른 색으로 바꾼다.

모양은 '#' 마스크로 그리고 자동으로 외곽선·명암을 넣은 뒤(shape), 손으로 디테일을 덧그린다.
빛은 왼쪽 위: 왼쪽 가장자리 안쪽은 밝게, 오른쪽 가장자리 안쪽은 어둡게.
"""
from .lib import Canvas, check

W, H = 24, 32

# 묶음별 (밝음, 기본, 그늘, 외곽선)
RAMP = {
    'skin': ('z', 's', 'S', 't'),
    'hair': ('J', 'h', 'H', 'j'),
    'cloth1': ('v', 'c', 'C', 'N'),
    'cloth2': ('q', 'p', 'P', 'Q'),
    'shoes': ('o', 'o', 'O', 'x'),
}


def shape(c, mask, group, y0=0, light=True, outline=True):
    """'#' 마스크(y0줄부터)를 채운다. 바깥과 닿은 칸 = 외곽선, 그 안쪽 오른쪽 = 그늘, 왼쪽 = 밝음."""
    L, B, D, O = RAMP[group]
    filled = {(x, y0 + y) for y, r in enumerate(mask) for x, ch in enumerate(r) if ch == '#'}

    def edge(x, y):
        return any((x + dx, y + dy) not in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

    for (x, y) in filled:
        if outline and edge(x, y):
            c.set(x, y, O)
        elif (x + 1, y) in filled and edge(x + 1, y) and outline:
            c.set(x, y, D)
        elif light and (x - 1, y) in filled and edge(x - 1, y) and outline:
            c.set(x, y, L)
        else:
            c.set(x, y, B)
    return c


# ── 몸 ────────────────────────────────────────────────────────
HEAD = check("head", [
    #012345678901234567890123
    "........................",  # 0
    "........................",  # 1
    "........########........",  # 2
    "......############......",  # 3
    ".....##############.....",  # 4
    "....################....",  # 5
    "....################....",
    "....################....",
    "....################....",
    "....################....",
    "....################....",  # 10
    "....################....",
    "....################....",
    "....################....",
    "....################....",
    ".....##############.....",  # 15
    "......############......",
    "........########........",  # 17
], W)

EARS = check("ears", [
    "...#................#...",  # 9
    "..##................##..",
    "...#................#...",
], W)

TORSO = check("torso", [
    #012345678901234567890123
    "..........####..........",  # 18 목
    "........########........",  # 19
    ".......##########.......",  # 20
    ".......##########.......",
    ".......##########.......",
    ".......##########.......",
    ".......##########.......",
    ".......##########.......",  # 25
    "........########........",  # 26
], W)

ARM_L = check("arm", [
    #012345678901234567890123
    "......###...............",  # 20
    ".....####...............",
    ".....####...............",
    ".....####...............",
    ".....####...............",
    ".....###................",  # 25
], W)
ARM_R = [r[::-1] for r in ARM_L]

LEG_L = check("leg", [
    #012345678901234567890123
    "........####............",  # 26
    "........####............",
    "........####............",
    "........####............",
    "........####............",
    "........####............",  # 31
], W)
LEG_R = [r[::-1] for r in LEG_L]


def body(c):
    shape(c, LEG_L, 'skin', 26, light=False)
    shape(c, LEG_R, 'skin', 26, light=False)
    shape(c, TORSO, 'skin', 18)
    shape(c, ARM_L, 'skin', 20)
    shape(c, ARM_R, 'skin', 20)
    shape(c, EARS, 'skin', 9, light=False)
    shape(c, HEAD, 'skin', light=False)
    # 턱 그늘
    for x in range(12, 15):
        c.set(x, 16, 'S')
    return c


# ── 얼굴 ──────────────────────────────────────────────────────
# 눈: 14칸(5~18칸) × 5줄(8~12줄). 가운데 10~13칸은 비움. '_' = 안 그림.
#         왼눈 5-9    가운데     오른눈 14-18
EYE_STYLES = {
    # 기본: 둥근 눈동자, 위 눈꺼풀만 선
    'basic': [
        "______________",  # 8
        "__KKK____KKK__",  # 9
        "_KWee____WeeK_",  # 10
        "_Keee____eeeK_",  # 11
        "__EEe____eEE__",  # 12
    ],
}

# 표정용 눈 (스타일 상관없이 같음)
EYE_SHARED = {
    'closed': [
        "______________",
        "______________",
        "______________",
        "_KKKK____KKKK_",
        "______________",
    ],
    'joy': [  # ^ ^
        "______________",
        "______________",
        "__KK______KK__",
        "_K__K____K__K_",
        "______________",
    ],
}

# 속눈썹 (여자 기본): 눈꼬리 바깥으로 한 칸 + 위로 한 칸
LASHES = [
    "K____________K",  # 8
    "KKKKK____KKKKK",  # 9  (눈꺼풀 선을 바깥으로 이어 그림)
    "______________",
    "______________",
    "______________",
]

# 눈썹: 14칸 × 3줄(6~8줄), 앞머리 위에 그린다
BROWS = {
    'normal': [
        "______________",
        "__jjj____jjj__",
        "______________",
    ],
    'angry': [
        "______________",
        "_jj________jj_",
        "___jj____jj___",
    ],
    'sad': [
        "______________",
        "___jj____jj___",
        "_jj________jj_",
    ],
    'up': [
        "__jjj____jjj__",
        "______________",
        "______________",
    ],
}

# 코: 4칸(10~13칸) × 2줄(12~13줄)
NOSES = {
    'dot': ["____", "_S__"],
}

# 입: 4칸(10~13칸) × 3줄(14~16줄)
MOUTHS = {
    'smile': ["____", "K__K", "_KK_"],
    'calm': ["____", "____", "_KK_"],
    'grin': ["KKKK", "KmmK", "_KK_"],
    'open': ["____", "_KK_", "_KK_"],
    'frown': ["____", "_KK_", "K__K"],
    'wobble': ["____", "_K_K", "K_K_"],
    'grit': ["____", "KKKK", "KWWK"],
}
# 볼: 14칸 × 2줄(12~13줄)
BLUSH = [
    "bb__________bb",
    "______________",
]


def face(c, eyes='basic', brow='normal', nose='dot', mouth='smile', lashes=False, blush=True, eye_rows=None):
    rows = eye_rows or EYE_STYLES.get(eyes) or EYE_SHARED[eyes]
    if blush:
        c.stamp(BLUSH, 5, 12)
    c.stamp(rows, 5, 8)
    if lashes and eyes not in EYE_SHARED:
        c.stamp(LASHES, 5, 8)
    c.stamp(NOSES[nose], 10, 12)
    c.stamp(MOUTHS[mouth], 10, 14)


def brows(c, brow='normal'):
    c.stamp(BROWS[brow], 5, 6)


# ── 머리카락 ──────────────────────────────────────────────────
from .hair3 import HAIRS  # noqa: E402


def hair(c, style, part):
    spec = HAIRS[style].get(part)
    if spec:
        y0, rows = spec
        c.stamp(rows, 0, y0)


# ── 옷 ────────────────────────────────────────────────────────
TOPS = {
    'tee': (19, [
        #012345678901234567890123
        "........########........",  # 19
        ".......##########.......",
        "......############......",
        ".....##############.....",
        ".......##########.......",
        ".......##########.......",
        ".......##########.......",  # 25
    ]),
}
BOTTOMS = {
    'pants': (25, [
        #012345678901234567890123
        ".......##########.......",  # 25
        "........########........",
        "........########........",
        "........###..###........",
        "........###..###........",
        "........###..###........",  # 30
    ]),
    'skirt': (25, [
        #012345678901234567890123
        ".......##########.......",  # 25
        "......############......",
        ".....##############.....",
    ]),
}
SHOES = {
    'sneakers': (30, [
        #012345678901234567890123
        ".......#####..#####......",
        ".......#####..#####......",
    ]),
}


def top(c, name):
    y0, mask = TOPS[name]
    shape(c, mask, 'cloth1', y0)
    # 목둘레
    c.stamp(["tSSt"], 10, 19)


def bottom(c, name):
    y0, mask = BOTTOMS[name]
    shape(c, mask, 'cloth2', y0)


def shoes(c, name):
    y0, mask = SHOES[name]
    shape(c, mask, 'shoes', y0)


CH = 36   # 캔버스 높이 (머리 위 4줄 여유)
OY = 4


def compose(look, mood='calm'):
    c = Canvas(W, CH, OY)
    hair(c, look['hair'], 'back')
    body(c)
    bottom(c, look['bottom'])
    shoes(c, look['shoes'])
    top(c, look['top'])
    f = MOOD_FACE[mood]
    face(c, eyes=f['eyes'] if f['eyes'] in EYE_SHARED else look['eyes'], mouth=f['mouth'], lashes=look['lashes'], nose=look['nose'])
    hair(c, look['hair'], 'front')
    brows(c, f['brow'])
    return c


MOOD_FACE = {
    'joyful': {'eyes': 'joy', 'brow': 'up', 'mouth': 'grin'},
    'happy': {'eyes': 'open', 'brow': 'up', 'mouth': 'open'},
    'calm': {'eyes': 'open', 'brow': 'normal', 'mouth': 'smile'},
    'worried': {'eyes': 'open', 'brow': 'sad', 'mouth': 'wobble'},
    'sad': {'eyes': 'open', 'brow': 'sad', 'mouth': 'frown'},
    'angry': {'eyes': 'open', 'brow': 'angry', 'mouth': 'grit'},
    'cheer': {'eyes': 'joy', 'brow': 'up', 'mouth': 'grin'},
}

LOOKS = {
    'm': {'hair': 'short', 'top': 'tee', 'bottom': 'pants', 'shoes': 'sneakers', 'eyes': 'basic', 'lashes': False, 'nose': 'dot'},
    'f': {'hair': 'long', 'top': 'tee', 'bottom': 'skirt', 'shoes': 'sneakers', 'eyes': 'basic', 'lashes': True, 'nose': 'dot'},
}


def preview():
    from .lib import img
    out = []
    for g in ('m', 'f'):
        for mood in MOOD_FACE:
            out.append(img(compose(LOOKS[g], mood).rows()))
    return out
