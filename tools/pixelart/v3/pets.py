"""
동물 캐릭터 (24×36, 사람 캐릭터와 같은 칸). 강아지·고양이는 처음부터, 나머지는 마일로 산다.

한 장(px_pet_atlas)에 [동물 줄] × [표정 7 × 동작 4] 칸을 늘어놓는다. 동작: 서기, 눈 깜빡, 왼발, 오른발.
털은 표식 색(FUR)으로 그리고 앱이 고른 털색으로 바꾼다. 소품(목걸이·리본 등)은 따로 한 줄(소품 칸),
사람 모자와 같은 표식 색(1 2 3 4)이라 앱이 고른 색으로 바꾼다.

    from v3 import pets
    pets.atlas()      # PIL.Image
    pets.preview()    # 미리보기 그림들
"""
from PIL import Image

W, H = 24, 36

SPECIES = ["dog", "cat", "rabbit", "hamster", "fox", "panda", "penguin"]
MOODS = ["joyful", "happy", "calm", "worried", "sad", "angry", "cheer"]
POSES = ["stand", "blink", "walk_left", "walk_right"]
ACCS = ["collar", "bandana", "ribbon", "glasses", "crown", "scarf"]


def c(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# 털 표식 (앱이 바꾼다): 빛 · 기본 · 그늘 · 깊은 그늘 · 배(밝은 털)
FUR = ["#fbe6cd", "#e8b483", "#c98b5a", "#9a643c", "#fff7ec"]
# 소품 표식 = 사람 모자 표식 (cpal SWAP 1~4)
ACC = ["#ffb3b8", "#f2707a", "#c94f5b", "#8e3442"]

K = c("#2b2234")      # 외곽선
INK = c("#3a2a33")    # 눈·코
WHITE = c("#ffffff")
PINK = c("#f3a3a9")   # 귀 안·혀
CHEEK = c("#f7b0ab")
MOUTH = c("#a8434b")
TEAR = c("#8fd0ff")
STAR = c("#ffd65c")
ANGER = c("#e5484d")
PATCH = c("#3a3443")  # 판다 무늬 (고정)
PATCH_L = c("#58506a")
BEAK = c("#f4a340")
BEAK_D = c("#c97a22")
GLASS = c("#cfe8ff")

F_LIGHT, F_BASE, F_SHADE, F_DEEP, F_BELLY = (c(x) for x in FUR)
A1, A2, A3, A4 = (c(x) for x in ACC)

# 털색 (빛 · 기본 · 그늘 · 깊은 그늘 · 배). 동물마다 고를 수 있는 색, 첫 번째가 기본
FUR_COLORS = {
    "dog": [
        ("갈색", FUR),
        ("크림", ["#fff6e6", "#f5ddb8", "#dfba8a", "#bf9466", "#fffaf2"]),
        ("흰둥이", ["#ffffff", "#f4f1ec", "#dcd5cb", "#b5aa9c", "#ffffff"]),
        ("누렁이", ["#ffe2a6", "#f2bb5c", "#d49433", "#a8701f", "#fff1d6"]),
        ("검정", ["#6d6672", "#4a4452", "#36313d", "#25212b", "#e6dccf"]),
    ],
    "cat": [
        ("치즈", ["#ffd9a8", "#f5a855", "#d88433", "#a9601f", "#fff3e2"]),
        ("회색", ["#e6e6ea", "#b9bac2", "#9496a1", "#6d6f7a", "#f7f7f9"]),
        ("고등어", ["#d9d2c3", "#a99f8c", "#857b69", "#5f5747", "#f1ece2"]),
        ("흰색", ["#ffffff", "#f4f1ec", "#dcd5cb", "#b5aa9c", "#ffffff"]),
        ("검정", ["#6d6672", "#4a4452", "#36313d", "#25212b", "#5b5560"]),
    ],
    "rabbit": [
        ("흰색", ["#ffffff", "#f4f1ec", "#dcd5cb", "#b5aa9c", "#ffffff"]),
        ("회색", ["#e6e6ea", "#b9bac2", "#9496a1", "#6d6f7a", "#f7f7f9"]),
        ("갈색", FUR),
    ],
    "hamster": [
        ("골든", ["#ffe7c2", "#f3c27f", "#d9a05a", "#b07a3a", "#fffaf0"]),
        ("흰색", ["#ffffff", "#f4f1ec", "#dcd5cb", "#b5aa9c", "#ffffff"]),
        ("회색", ["#e6e6ea", "#b9bac2", "#9496a1", "#6d6f7a", "#f7f7f9"]),
    ],
    "fox": [
        ("주황", ["#ffc08a", "#f08a3c", "#cf6a24", "#9e4a14", "#fff7ee"]),
        ("은여우", ["#eceef2", "#b4b8c4", "#8c91a0", "#646978", "#ffffff"]),
    ],
    "panda": [
        ("판다", ["#ffffff", "#f6f4f0", "#dedad3", "#bdb7ae", "#ffffff"]),
    ],
    "penguin": [
        ("펭귄", ["#5d6476", "#3c4252", "#2c303c", "#1f222b", "#ffffff"]),
    ],
}

# 무늬 색이 털 표식과 겹치면 안 된다
assert len({*FUR, *ACC}) == 9


class Px:
    def __init__(self):
        self.im = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def set(self, x, y, col):
        if 0 <= x < W and 0 <= y < H:
            self.im.putpixel((x, y), col)

    def get(self, x, y):
        if 0 <= x < W and 0 <= y < H:
            return self.im.getpixel((x, y))
        return (0, 0, 0, 0)

    def blob(self, x0, y0, x1, y1, fill, shade=True, outline=True):
        """둥근 덩어리: 타원 안을 채우고, 왼쪽 위는 빛·오른쪽 아래는 그늘, 바깥은 외곽선."""
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        rx, ry = (x1 - x0) / 2 + 0.5, (y1 - y0) / 2 + 0.5
        inside = set()
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                    inside.add((x, y))
        for (x, y) in inside:
            self.set(x, y, fill)
        if shade and fill == F_BASE:
            for (x, y) in inside:
                if (x + 1, y + 1) not in inside or (x, y + 2) not in inside:
                    self.set(x, y, F_SHADE)
                elif (x - 1, y - 1) not in inside or (x - 2, y) not in inside and (x, y - 2) not in inside:
                    self.set(x, y, F_LIGHT)
        if outline:
            for (x, y) in inside:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = (x + dx, y + dy)
                    if n not in inside and self.get(*n)[3] == 0:
                        self.set(*n, K)
        return inside

    def rect(self, x0, y0, x1, y1, col):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, col)

    def pts(self, col, *ps):
        for (x, y) in ps:
            self.set(x, y, col)


# ── 몸 ─────────────────────────────────────────────────────────

HEAD = (3, 12, 20, 27)     # 머리 (모든 동물이 같은 자리 — 표정·소품을 같이 쓰게)
EYES = ((8, 19), (15, 19))  # 왼눈·오른눈 (2×2 칸의 왼쪽 위)


def ears_back(p, sp):
    """머리 뒤로 가는 귀"""
    if sp == "dog":
        # 늘어진 귀: 머리 양옆으로 내려옴 (깊은 털색)
        for side in (0, 1):
            x0 = 1 if side == 0 else 18
            p.blob(x0, 13, x0 + 4, 24, F_DEEP, shade=False)
    elif sp == "cat":
        for x0, d in ((4, 1), (19, -1)):
            for i in range(5):
                for j in range(i + 1):
                    p.set(x0 + d * j, 9 + i, F_BASE)
            p.pts(K, *[(x0 - d, 9 + i) for i in range(5)], *[(x0 + d * (i + 1), 9 + i) for i in range(5)])
            p.pts(PINK, (x0 + d, 12), (x0 + d, 13), (x0 + d * 2, 13))
            p.set(x0, 8, K)
    elif sp == "fox":
        for x0, d in ((3, 1), (20, -1)):
            for i in range(6):
                for j in range(i + 1):
                    p.set(x0 + d * j, 8 + i, F_BASE)
            p.pts(K, *[(x0 - d, 8 + i) for i in range(6)], *[(x0 + d * (i + 1), 8 + i) for i in range(6)])
            p.set(x0, 7, K)
            p.pts(F_DEEP, (x0, 8), (x0, 9), (x0 + d, 9))
            p.pts(F_BELLY, (x0 + d, 11), (x0 + d, 12), (x0 + d * 2, 12))
    elif sp == "rabbit":
        for x0 in (6, 14):
            p.blob(x0, 1, x0 + 3, 14, F_BASE)
            p.rect(x0 + 1, 3, x0 + 2, 12, PINK)
    elif sp == "hamster":
        for x0 in (4, 16):
            p.blob(x0, 10, x0 + 3, 14, F_BASE)
            p.pts(PINK, (x0 + 1, 12), (x0 + 2, 12))
    elif sp == "panda":
        for x0 in (3, 16):
            p.blob(x0, 10, x0 + 4, 15, PATCH, shade=False)
            p.set(x0 + 1, 11, PATCH_L)


def tail(p, sp, pose):
    wag = 1 if pose == "walk_left" else 0
    if sp == "dog":
        p.blob(16, 28 - wag, 20, 31 - wag, F_BASE)
    elif sp == "cat":
        for i, (x, y) in enumerate([(18, 32), (19, 31), (20, 30), (20, 29), (20, 28), (21, 27), (21, 26)]):
            p.set(x, y - wag, F_BASE)
            p.set(x + 1, y - wag, K)
            p.set(x - 1, y - wag, K if i > 0 else F_BASE)
        p.set(21, 25 - wag, K)
    elif sp == "fox":
        p.blob(15, 24 - wag, 22, 33 - wag, F_BASE)
        p.blob(19, 24 - wag, 22, 28 - wag, F_BELLY, shade=False)
    elif sp == "rabbit":
        p.blob(16, 29, 19, 32, F_BELLY, shade=False)
    elif sp == "penguin":
        pass


def body(p, sp, pose):
    lift_l = 1 if pose == "walk_left" else 0
    lift_r = 1 if pose == "walk_right" else 0
    if sp == "penguin":
        # 날개
        p.blob(4, 26, 7, 32, F_BASE)
        p.blob(16, 26, 19, 32, F_BASE)
    p.blob(6, 25, 17, 34, F_BASE)
    if sp == "panda":
        # 팔·다리가 검다
        p.blob(5, 27, 8, 31, PATCH, shade=False)
        p.blob(15, 27, 18, 31, PATCH, shade=False)
    else:
        p.blob(9, 28, 14, 33, F_BELLY, shade=False, outline=False)
    foot = PATCH if sp == "panda" else (BEAK if sp == "penguin" else F_BASE)
    p.blob(6, 33 - lift_l, 9, 35 - lift_l, foot, shade=False)
    p.blob(14, 33 - lift_r, 17, 35 - lift_r, foot, shade=False)
    if sp != "penguin":
        # 앞발
        p.blob(7, 29, 9, 31, F_LIGHT if sp != "panda" else PATCH, shade=False)
        p.blob(14, 29, 16, 31, F_LIGHT if sp != "panda" else PATCH, shade=False)


def head(p, sp):
    x0, y0, x1, y1 = HEAD
    if sp == "hamster":
        x0, x1 = 2, 21
    p.blob(x0, y0, x1, y1, F_BASE)
    if sp in ("dog", "fox", "hamster", "rabbit"):
        # 주둥이·볼 (밝은 털)
        p.blob(7, 21, 16, 26, F_BELLY, shade=False, outline=False)
    if sp == "fox":
        # 하얀 볼이 옆으로
        p.blob(4, 21, 8, 25, F_BELLY, shade=False, outline=False)
        p.blob(15, 21, 19, 25, F_BELLY, shade=False, outline=False)
    if sp == "cat":
        p.blob(8, 22, 15, 26, F_BELLY, shade=False, outline=False)
        # 이마 줄무늬
        p.pts(F_DEEP, (11, 13), (12, 13), (11, 14), (12, 14), (9, 14), (14, 14))
    if sp == "dog":
        # 이마 무늬
        p.pts(F_DEEP, (11, 13), (12, 13), (10, 13), (13, 13))
    if sp == "panda":
        # 눈 무늬
        for ex in (6, 14):
            p.blob(ex, 18, ex + 3, 22, PATCH, shade=False, outline=False)
    if sp == "penguin":
        # 하얀 얼굴
        p.blob(6, 16, 17, 27, F_BELLY, shade=False, outline=False)


# ── 표정 ─────────────────────────────────────────────────────────

def eyes_open(p, sp, big=True):
    for (ex, ey) in EYES:
        p.rect(ex, ey, ex + 1, ey + 1, WHITE if sp == "panda" else INK)
        if sp != "panda":
            p.set(ex, ey, WHITE)
        else:
            p.set(ex + 1, ey + 1, INK)


def eyes_closed_line(p, sp):
    col = WHITE if sp == "panda" else INK
    for (ex, ey) in EYES:
        p.pts(col, (ex, ey + 1), (ex + 1, ey + 1))


def eyes_happy(p, sp):
    """^ ^"""
    col = WHITE if sp == "panda" else INK
    for (ex, ey) in EYES:
        p.pts(col, (ex - 1, ey + 1), (ex, ey), (ex + 1, ey), (ex + 2, ey + 1))


def eyes_sad(p, sp):
    col = WHITE if sp == "panda" else INK
    for i, (ex, ey) in enumerate(EYES):
        p.rect(ex, ey + 1, ex + 1, ey + 1, col)
        # 처진 눈썹
        if i == 0:
            p.pts(K if sp != "panda" else WHITE, (ex - 1, ey - 1), (ex, ey - 2), (ex + 1, ey - 2))
        else:
            p.pts(K if sp != "panda" else WHITE, (ex + 2, ey - 1), (ex + 1, ey - 2), (ex, ey - 2))


def eyes_angry(p, sp):
    eyes_open(p, sp)
    brow = K if sp != "panda" else WHITE
    (lx, ly), (rx, ry) = EYES
    p.pts(brow, (lx - 1, ly - 2), (lx, ly - 2), (lx + 1, ly - 1))
    p.pts(brow, (rx + 2, ry - 2), (rx + 1, ry - 2), (rx, ry - 1))


def eyes_worried(p, sp):
    eyes_open(p, sp)
    brow = K if sp != "panda" else WHITE
    (lx, ly), (rx, ry) = EYES
    p.pts(brow, (lx - 1, ly - 1), (lx, ly - 2), (lx + 1, ly - 2))
    p.pts(brow, (rx + 2, ry - 1), (rx + 1, ry - 2), (rx, ry - 2))


def nose(p, sp):
    if sp == "penguin":
        p.rect(10, 22, 13, 22, BEAK)
        p.rect(11, 23, 12, 23, BEAK_D)
        return
    p.pts(INK if sp != "rabbit" and sp != "hamster" else PINK, (11, 22), (12, 22))
    if sp == "dog":
        p.pts(INK, (10, 22), (13, 22), (11, 21), (12, 21))


def mouth(p, sp, kind):
    if sp == "penguin":
        # 부리 아래로 입 모양만 살짝
        if kind in ("open", "big"):
            p.pts(MOUTH, (11, 24), (12, 24))
        return
    if kind == "smile":
        p.pts(INK, (10, 24), (11, 25), (12, 25), (13, 24))
    elif kind == "small":
        p.pts(INK, (11, 24), (12, 24))
    elif kind == "open":
        p.pts(INK, (10, 24), (13, 24))
        p.rect(11, 24, 12, 25, MOUTH)
        p.set(11, 25, PINK)
    elif kind == "big":
        p.pts(INK, (9, 24), (14, 24))
        p.rect(10, 24, 13, 25, MOUTH)
        p.rect(11, 25, 12, 26, PINK)
    elif kind == "frown":
        p.pts(INK, (10, 25), (11, 24), (12, 24), (13, 25))
    elif kind == "wavy":
        p.pts(INK, (9, 25), (10, 24), (11, 25), (12, 24), (13, 25))


def cheeks(p, sp):
    if sp in ("panda", "penguin"):
        return
    p.pts(CHEEK, (5, 22), (6, 22), (17, 22), (18, 22))


def face(p, sp, mood, blink):
    if blink and mood in ("happy", "calm", "worried", "angry", "cheer"):
        eyes_closed_line(p, sp)
        if mood == "angry":
            brow = K if sp != "panda" else WHITE
            (lx, ly), (rx, ry) = EYES
            p.pts(brow, (lx - 1, ly - 2), (lx, ly - 2), (lx + 1, ly - 1), (rx + 2, ry - 2), (rx + 1, ry - 2), (rx, ry - 1))
    elif mood == "joyful":
        eyes_happy(p, sp)
    elif mood == "sad":
        eyes_sad(p, sp)
    elif mood == "angry":
        eyes_angry(p, sp)
    elif mood == "worried":
        eyes_worried(p, sp)
    else:
        eyes_open(p, sp)
    nose(p, sp)
    mouth(p, sp, {
        "joyful": "big", "happy": "open", "calm": "smile", "worried": "wavy", "sad": "frown", "angry": "frown", "cheer": "open",
    }[mood])
    if mood in ("joyful", "happy", "cheer"):
        cheeks(p, sp)
    # 덧붙임
    if mood == "sad":
        p.pts(TEAR, (8, 21), (8, 22), (7, 23))
    if mood == "worried":
        p.pts(TEAR, (20, 15), (20, 16), (19, 16), (21, 16), (20, 17))
    if mood == "angry":
        p.pts(ANGER, (19, 12), (21, 12), (20, 13), (19, 14), (21, 14))
    if mood in ("joyful", "cheer"):
        p.pts(STAR, (1, 13), (0, 14), (2, 14), (1, 15), (22, 18), (21, 19), (23, 19), (22, 20))


def draw(sp, mood, pose):
    p = Px()
    tail(p, sp, pose)
    ears_back(p, sp)
    body(p, sp, pose)
    head(p, sp)
    face(p, sp, mood, pose == "blink")
    return p.im


# ── 소품 (모든 동물 같은 자리) ───────────────────────────────────

def acc(name):
    p = Px()
    if name == "collar":
        p.rect(7, 26, 16, 26, A2)
        p.rect(7, 27, 16, 27, A3)
        p.pts(STAR, (11, 28), (12, 28), (11, 29), (12, 29))
        p.pts(c("#e8a83a"), (12, 29))
    elif name == "bandana":
        for i, w in enumerate((10, 8, 6, 4, 2)):
            x0 = 12 - w // 2
            p.rect(x0, 26 + i, x0 + w - 1, 26 + i, A2 if i % 2 == 0 else A3)
        p.pts(A1, (9, 26), (13, 27))
    elif name == "ribbon":
        p.pts(A2, (15, 11), (16, 12), (15, 13), (19, 11), (18, 12), (19, 13), (16, 11), (18, 11), (16, 13), (18, 13))
        p.pts(A3, (17, 12))
        p.pts(A1, (15, 12), (19, 12))
        p.pts(K, (14, 11), (14, 12), (14, 13), (20, 11), (20, 12), (20, 13))
    elif name == "glasses":
        for (ex, ey) in EYES:
            p.pts(K, (ex - 1, ey - 1), (ex, ey - 1), (ex + 1, ey - 1), (ex + 2, ey - 1),
                  (ex - 1, ey + 2), (ex, ey + 2), (ex + 1, ey + 2), (ex + 2, ey + 2),
                  (ex - 1, ey), (ex - 1, ey + 1), (ex + 2, ey), (ex + 2, ey + 1))
        p.pts(K, (11, 19), (12, 19))
    elif name == "crown":
        p.rect(8, 10, 15, 11, STAR)
        p.pts(STAR, (8, 8), (8, 9), (11, 7), (11, 8), (11, 9), (12, 8), (12, 9), (15, 8), (15, 9), (12, 7))
        p.pts(A2, (11, 10), (12, 10))
        p.pts(c("#e8a83a"), (8, 11), (9, 11), (10, 11), (13, 11), (14, 11), (15, 11))
    elif name == "scarf":
        p.rect(6, 25, 17, 26, A2)
        p.rect(6, 26, 17, 26, A3)
        p.rect(14, 27, 16, 31, A2)
        p.pts(A3, (16, 28), (16, 29), (16, 30))
        p.pts(A1, (7, 25), (9, 25), (11, 25))
    return p.im


def atlas():
    """[동물 줄] × [표정 × 동작] + 마지막 줄 소품"""
    cols = len(MOODS) * len(POSES)
    im = Image.new("RGBA", (cols * W, (len(SPECIES) + 1) * H), (0, 0, 0, 0))
    for r, sp in enumerate(SPECIES):
        for m, mood in enumerate(MOODS):
            for k, pose in enumerate(POSES):
                im.alpha_composite(draw(sp, mood, pose), ((m * len(POSES) + k) * W, r * H))
    for i, a in enumerate(ACCS):
        im.alpha_composite(acc(a), (i * W, len(SPECIES) * H))
    return im


def recolor(im, ramp):
    m = {c(a)[:3]: c(b) for a, b in zip(FUR, ramp)}
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] and p[:3] in m:
                px[x, y] = m[p[:3]]
    return out


def preview():
    out = []
    for sp in SPECIES:
        ramp = FUR_COLORS[sp][0][1]
        for mood in MOODS:
            out.append(recolor(draw(sp, mood, "stand"), ramp))
    # 털색 고르기
    for sp in SPECIES:
        for name, ramp in FUR_COLORS[sp]:
            out.append(recolor(draw(sp, "happy", "stand"), ramp))
    for a in ACCS:
        base = draw("dog", "happy", "stand").copy()
        base.alpha_composite(acc(a))
        out.append(base)
    return out


def kotlin():
    q = lambda xs: ", ".join(f'"{x}"' for x in xs)
    hexes = lambda xs: ", ".join("0xFF" + x.lstrip('#').upper() for x in xs)
    furs = "\n".join(
        f'        "{sp}" to listOf(' + ", ".join(f'"{n}" to longArrayOf({hexes(r)}).map {{ it.toInt() }}.toIntArray()' for n, r in FUR_COLORS[sp]) + "),"
        for sp in SPECIES
    )
    return f"""package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (v3/pets.py) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 동물 캐릭터 아틀라스(px_pet_atlas) 배치와 털 표식 색 */
object PetParts {{
    const val W = {W}
    const val H = {H}
    val species = listOf({q(SPECIES)})
    val moods = listOf({q(MOODS)})
    val poses = listOf({q(POSES)})
    val accs = listOf({q(ACCS)})

    /** 털 표식: 빛 · 기본 · 그늘 · 깊은 그늘 · 배 */
    val FUR = longArrayOf({hexes(FUR)}).map {{ it.toInt() }}.toIntArray()

    /** 동물마다 고를 수 있는 털색 (첫 번째가 기본) */
    val furColors: Map<String, List<Pair<String, IntArray>>> = mapOf(
{furs}
    )
}}
"""
