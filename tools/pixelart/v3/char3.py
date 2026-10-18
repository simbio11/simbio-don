"""
캐릭터 v3: 24×32 레이어 조합 (머리 위 4줄 여유를 둔 24×36 칸).

그리는 순서: 뒷머리 → 몸(피부) → 아래옷 → 신발 → 윗옷 → 얼굴(볼·눈·코·입) → 앞머리 → 눈썹·표정 → 모자 → 장신구.
모든 부위는 같은 칸 위에 그린다 (투명 '.'). 색은 cpal의 표식 글자 → 앱이 고른 색으로 바꾼다.

얼굴 자리: 눈썹 6~8줄, 눈 8~12줄 (왼눈 5~9칸, 오른눈 14~18칸), 볼 12줄, 코 12~13줄, 입 14~16줄 (10~13칸).
빛은 왼쪽 위: 왼쪽은 밝게, 오른쪽·아래는 어둡게.
"""
from .lib import Canvas, row

W, H = 24, 36
OY = 4  # 머리 꼭대기(0줄) 위로 4줄: 올림머리·모자 자리

# ── 몸 ────────────────────────────────────────────────────────
HEAD = {2: (8, 15), 3: (6, 17), 4: (5, 18), **{y: (4, 19) for y in range(5, 15)}, 15: (5, 18), 16: (6, 17), 17: (8, 15)}

# 얼굴형: 아래쪽 줄 윤곽만 다르다
FACE_SHAPES = {
    'round': {'label': '동글', 'rows': {}},
    'vline': {'label': 'V라인', 'rows': {14: (5, 18), 15: (6, 17), 16: (7, 16), 17: (9, 14)}},
    'square': {'label': '각진', 'rows': {15: (4, 19), 16: (5, 18), 17: (7, 16)}},
}


def head(c, shape='round'):
    spans = {**HEAD, **FACE_SHAPES[shape]['rows']}
    last = max(spans)
    for y, (l, r) in spans.items():
        if y in (2, last):
            for x in range(l, r + 1):
                c.set(x, y, 't')
            continue
        for x in range(l, r + 1):
            c.set(x, y, 's')
        c.set(l, y, 't')
        c.set(r, y, 't')
        # 둥근 모서리: 위아래 줄보다 넓은 부분도 선
        nl, nr = spans.get(y + 1, (l, r))
        pl, pr = spans.get(y - 1, (l, r))
        for x in range(l + 1, min(nl, pl)):
            c.set(x, y, 't')
        for x in range(max(nr, pr) + 1, r):
            c.set(x, y, 't')
    for y in range(6, last - 1):
        l, r = spans[y]
        if c.get(r - 1, y) == 's':
            c.set(r - 1, y, 'S')
    l, r = spans[last - 1]
    for x in range(r - 3, r):
        if c.get(x, last - 1) == 's':
            c.set(x, last - 1, 'S')


EARS = [
    row(3, "t", 20, "t"),     # 9
    row(2, "tS", 20, "St"),
    row(3, "t", 20, "t"),
]

BODY = [
    row(10, "tSSt"),                                        # 18 목
    row(6, "tttt", 10, "sSSs", 14, "tttt"),                  # 19 어깨
    row(5, "t", 6, "zsss", 10, "ssss", 14, "sssS", 18, "t"),  # 20
    row(4, "tzst", 8, "sssssssS", 16, "tsSt"),               # 21 팔
    row(4, "tzst", 8, "sssssssS", 16, "tsSt"),
    row(4, "tzst", 8, "sssssssS", 16, "tsSt"),
    row(4, "tsSt", 8, "sssssssS", 16, "tsSt"),               # 24 손
    row(5, "tt", 7, "t", 8, "sssssssS", 16, "t", 17, "tt"),  # 25
    row(7, "t", 8, "ssssssSS", 16, "t"),                    # 26 엉덩이
    row(7, "tsSt", 13, "tsSt"),                             # 27 다리
    row(7, "tsSt", 13, "tsSt"),
    row(7, "tsSt", 13, "tsSt"),
    row(7, "tsSt", 13, "tsSt"),                             # 30
    row(7, "tttt", 13, "tttt"),                             # 31
]


def body(c, face_shape='round'):
    c.stamp(BODY, 0, 18)
    c.stamp(EARS, 0, 9)
    head(c, face_shape)
    return c


# ── 눈 ────────────────────────────────────────────────────────
# 14칸(5~18칸) × 5줄(8~12줄). 가운데 10~13칸은 비움. '_' = 안 그림.
EYE_STYLES = {
    'basic': {'label': '또렷한 눈', 'open': [
        "______________",  # 8
        "__KKK____KKK__",  # 9
        "_KWee____WeeK_",  # 10
        "_KeKe____eKeK_",  # 11
        "__EEE____EEE__",  # 12
    ]},
    'big': {'label': '왕눈', 'open': [
        "__KKK____KKK__",
        "_KWWe____WWeK_",
        "_KWeK____WeKK_",
        "_KeeK____eeeK_",
        "__EEE____EEE__",
    ]},
    'cat': {'label': '고양이 눈', 'open': [
        "____K____K____",
        "__KKK____KKK__",
        "_KWee____WeeK_",
        "__eKe____eKe__",
        "___EE____EE___",
    ]},
    'puppy': {'label': '강아지 눈', 'open': [
        "______________",
        "__KKK____KKK__",
        "__WeeK__KWee__",
        "_KeKe____eKeK_",
        "_KEEE____EEEK_",
    ]},
    'sharp': {'label': '날렵한 눈', 'open': [
        "______________",
        "______________",
        "_KKKK____KKKK_",
        "__WeK____KWe__",
        "__eEe____eEe__",
    ]},
}

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
    'xx': [  # 질끈 감은 눈 > <
        "______________",
        "_K__________K_",
        "__KK______KK__",
        "_K__________K_",
        "______________",
    ],
}

# 속눈썹: 눈꼬리 바깥으로 한 칸 + 위로 한 칸
LASHES = [
    "______________",
    "_K__________K_",
    "K____________K",
    "______________",
    "______________",
]


def _lid_row(g, xs):
    for y, r in enumerate(g):
        if any(r[x] == 'K' for x in xs):
            return y
    return None


def eyes_for(style, state):
    """눈 격자. state: open / closed / joy / xx / angry / sad"""
    if state in EYE_SHARED:
        return EYE_SHARED[state]
    g = [list(r) for r in EYE_STYLES[style]['open']]
    if state in ('angry', 'sad'):
        for xs, inner_last in ((range(0, 5), True), (range(9, 14), False)):
            y = _lid_row(g, xs)
            ks = [x for x in xs if g[y][x] == 'K']
            inner, outer = (ks[-1], ks[0]) if inner_last else (ks[0], ks[-1])
            x = inner if state == 'angry' else outer
            g[y][x] = '_'
            if y + 1 < len(g):
                g[y + 1][x] = 'K'
    return [''.join(r) for r in g]


# ── 눈썹 · 코 · 입 · 볼 ──────────────────────────────────────
# 눈썹: 14칸 × 3줄(6~8줄). 앞머리 위에 그린다. 색 = 머리 가장 어두운 색
BROW_STYLES = {
    'normal': {'label': '일자 눈썹', 'w': 3, 'thick': False},
    'thick': {'label': '진한 눈썹', 'w': 3, 'thick': True},
    'short': {'label': '짧은 눈썹', 'w': 2, 'thick': False},
}
BROW_STATES = ['normal', 'up', 'angry', 'sad']


def brows_for(style, state):
    w = BROW_STYLES[style]['w']
    thick = BROW_STYLES[style]['thick']
    g = [['_'] * 14 for _ in range(3)]
    lx = list(range(5 - w, 5))
    rx = list(range(9, 9 + w))
    if state == 'normal':
        ys_l = [1] * w
        ys_r = [1] * w
    elif state == 'up':
        ys_l = [0] * w
        ys_r = [0] * w
    elif state == 'angry':
        ys_l = [1] * (w - 1) + [2]
        ys_r = [2] + [1] * (w - 1)
        lx = [x - 1 for x in lx]
        rx = [x + 1 for x in rx]
    else:  # sad
        ys_l = [2] + [1] * (w - 1)
        ys_r = [1] * (w - 1) + [2]
    for xs, ys in ((lx, ys_l), (rx, ys_r)):
        for x, y in zip(xs, ys):
            g[y][x] = 'j'
            if thick and y + 1 < 3 and state in ('normal', 'up'):
                g[y + 1][x] = 'H'
    return [''.join(r) for r in g]


# 코: 4칸(10~13칸) × 2줄(12~13줄). S = 피부 그늘, z = 콧등 빛
NOSES = {
    'dot': {'label': '점 코', 'rows': ["____", "_S__"]},
    'button': {'label': '동글 코', 'rows': ["____", "_SS_"]},
    'high': {'label': '오똑한 코', 'rows': ["__S_", "_zS_"]},
    'none': {'label': '코 없음', 'rows': ["____", "____"]},
}

# 입 모양 (성형): 웃음·보통 입만 다르고, 크게 웃거나 화낸 입은 같다
MOUTH_STYLES = {
    'basic': {'label': '기본 입', 'smile': ["____", "k__k", "_kk_"], 'calm': ["____", "____", "_kk_"]},
    'cat': {'label': '고양이 입', 'smile': ["____", "k_k_", "_k_k"], 'calm': ["____", "k_k_", "_k_k"]},
    'lips': {'label': '도톰한 입술', 'smile': ["____", "n__n", "_nn_"], 'calm': ["____", "_nn_", "_bb_"]},
    'small': {'label': '작은 입', 'smile': ["____", "____", "_kk_"], 'calm': ["____", "____", "_k__"]},
}
MOUTH_SHARED = {
    'grin': ["kkkk", "kmmk", "_nn_"],
    'open': ["____", "_kk_", "_mm_"],
    'frown': ["____", "_kk_", "k__k"],
    'wobble': ["____", "_k_k", "k_k_"],
    'grit': ["____", "kkkk", "kWWk"],
}


def mouth_for(style, state):
    if state in ('smile', 'calm'):
        return MOUTH_STYLES[style][state]
    return MOUTH_SHARED[state]


BLUSH = ["_bb________bb_"]

# 기분 → 얼굴 (앱 Mood와 같은 이름)
MOOD_FACE = {
    'joyful': {'eyes': 'joy', 'brow': 'up', 'mouth': 'grin', 'extra': 'sparkle'},
    'happy': {'eyes': 'open', 'brow': 'up', 'mouth': 'open'},
    'calm': {'eyes': 'open', 'brow': 'normal', 'mouth': 'smile'},
    'worried': {'eyes': 'open', 'brow': 'sad', 'mouth': 'wobble', 'extra': 'sweat'},
    'sad': {'eyes': 'sad', 'brow': 'sad', 'mouth': 'frown', 'extra': 'tears'},
    'angry': {'eyes': 'angry', 'brow': 'angry', 'mouth': 'grit', 'extra': 'anger'},
    'cheer': {'eyes': 'xx', 'brow': 'up', 'mouth': 'grin', 'extra': 'sparkle'},
}

EXTRAS = {
    'tears': [(6, 13, 'F'), (6, 14, 'f'), (17, 13, 'F'), (17, 14, 'f')],
    'sweat': [(20, 5, 'F'), (20, 6, 'F'), (21, 6, 'f')],
    'anger': [(18, -1, 'r'), (20, -1, 'r'), (19, 0, 'r'), (18, 1, 'r'), (20, 1, 'r')],
    'sparkle': [(1, 1, 'y'), (0, 2, 'y'), (2, 2, 'y'), (1, 3, 'y'), (22, 4, 'y'), (21, 5, 'Y'), (23, 5, 'Y'), (22, 6, 'y')],
}


def blink_state(state):
    return 'closed' if state in ('open', 'angry', 'sad') else state


def face(c, look, mood='calm', blink=False):
    f = MOOD_FACE[mood]
    state = blink_state(f['eyes']) if blink else f['eyes']
    if look.get('blush', True):
        c.stamp(BLUSH, 5, 12)
    c.stamp(eyes_for(look['eyes'], state), 5, 8)
    if look.get('lashes') and state not in EYE_SHARED:
        c.stamp(LASHES, 5, 8)
    c.stamp(NOSES[look['nose']]['rows'], 10, 12)
    c.stamp(mouth_for(look['mouth'], f['mouth']), 10, 14)


def face_top(c, look, mood='calm'):
    """앞머리 위에 얹는 것: 눈썹, 눈물·땀·화남 표시"""
    f = MOOD_FACE[mood]
    c.stamp(brows_for(look['brow'], f['brow']), 5, 6)
    for x, y, ch in EXTRAS.get(f.get('extra'), []):
        c.set(x, y, ch)


# ── 머리카락 · 옷 ─────────────────────────────────────────────
from .hair3 import HAIRS  # noqa: E402
from .clothes3 import TOPS, BOTTOMS, SHOES, HATS, ACCS  # noqa: E402


def stamp_part(c, spec):
    if spec:
        y0, rows = spec
        c.stamp(rows, 0, y0)


def compose(look, mood='calm', blink=False):
    c = Canvas(W, H, OY)
    hair = HAIRS[look['hair']]
    stamp_part(c, hair.get('back'))
    body(c, look.get('face', 'round'))
    top = TOPS[look['top']]
    if not top.get('dress'):
        stamp_part(c, BOTTOMS[look['bottom']]['rows'])
    stamp_part(c, SHOES[look['shoes']]['rows'])
    stamp_part(c, top['rows'])
    face(c, look, mood, blink)
    stamp_part(c, hair.get('front'))
    face_top(c, look, mood)
    if look.get('hat'):
        stamp_part(c, HATS[look['hat']]['rows'])
    if look.get('acc'):
        stamp_part(c, ACCS[look['acc']]['rows'])
    return c


LOOKS = {
    'm': {'hair': 'short', 'top': 'hoodie', 'bottom': 'pants', 'shoes': 'sneakers', 'eyes': 'basic', 'lashes': False,
          'nose': 'dot', 'mouth': 'basic', 'brow': 'thick', 'face': 'round'},
    'f': {'hair': 'long', 'top': 'blouse', 'bottom': 'skirt', 'shoes': 'flats', 'eyes': 'big', 'lashes': True,
          'nose': 'dot', 'mouth': 'lips', 'brow': 'normal', 'face': 'vline'},
}


def preview():
    from .lib import img
    out = []
    for g in ('m', 'f'):
        for mood in MOOD_FACE:
            out.append(img(compose(LOOKS[g], mood).rows()))
    return out
