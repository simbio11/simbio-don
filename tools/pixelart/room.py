"""
방 장면. 집 단계(원룸·투룸·아파트)마다 바탕 그림 한 장 + 시간대 레이어(햇빛, 밤 그늘, 밤 불빛)로 나눠 그린다.
가구 자리(옷장·책장·책상·신발장·침대·러그·쓰레기통)는 세 단계가 같고, 벽·창·바닥·장식이 다르다.

앱은 이 순서로 겹친다:
    하늘(흐름, 단계마다 다른 유리창 안) → 방 바탕(유리창은 비어 있음) → 햇빛 → 물건·캐릭터 → 밤 그늘 → 밤 불빛

벽지·바닥·러그·커튼·이불은 '표식 색'(palette.MARKERS)으로 그리고, 앱이 표식 색을 고른 색 묶음으로 바꾼다.
그늘도 곱하지 않고 표식 사다리 안에서 어두운 칸으로 옮겨 칠해서, 색을 바꿔도 그늘이 그대로 진다.
"""
from contextlib import contextmanager

from PIL import Image, ImageDraw

from palette import MARKERS, PALETTE, ROOM

W, H = 160, 136
FLOOR_Y = 60

LEVELS = ("one_room", "two_room", "apartment")

# 물건 12×12의 왼쪽 위
SLOTS = {
    "WARDROBE": [(9, 23), (21, 23), (33, 23), (9, 38), (21, 38), (33, 38)],
    "SHELF": [(64, 41), (76, 41), (88, 41), (64, 55), (76, 55), (88, 55)],
    "DESK": [(113, 48), (123, 48), (133, 48), (143, 48)],
    "SHOE_RACK": [(8, 102), (20, 102), (8, 116), (20, 116)],
}
# 누르는 자리 (x, y, w, h)
HITS = {
    "WARDROBE": (0, 12, 49, 70),
    "SHELF": (62, 38, 40, 44),
    "DESK": (112, 44, 44, 38),
    "SHOE_RACK": (5, 99, 30, 34),
}
TRASH_HIT = (33, 86, 22, 50)
FLOOR_HIT = (50, 104, 66, 32)

CHAR_W, CHAR_H = 24, 36  # v3 캐릭터 칸 (머리 위 4줄 여유 포함)
# 캐릭터 발이 러그 가운데에 오게 (왼쪽 위 좌표)
CHARACTER_POS = (72, 70)  # 발끝이 106줄 (예전 22×28과 같은 자리)
# 걸어 다니는 범위 (왼쪽 위 x)
WALK_X = (57, 89)

FLOOR_PILE = [
    (76, 122), (88, 122), (64, 122), (100, 122), (52, 122),
    (70, 110), (94, 110), (82, 116), (62, 116), (98, 116), (56, 110), (106, 110),
]
TRASH = [(46, 123), (41, 107), (49, 113), (36, 95), (52, 100), (44, 90)]

# 유리창 (하늘이 비치는 곳 = 방 그림에서 비어 있는 칸). 단계마다 다르다. 높이는 하늘 그림 높이와 같게.
GLASS_BY_LEVEL = {
    "one_room": (69, 9, 26, 20),
    "two_room": (61, 9, 43, 20),
    "apartment": (54, 8, 56, 20),
}
GLASS = GLASS_BY_LEVEL["one_room"]
SKY_W, SKY_H = 64, 20

# 시간대
PHASES = ("day", "sunset", "night")

LAMP_CENTER = (107, 44)
SCONCE = (148, 27)    # 투룸 벽등 (왼쪽 위)
PENDANT = (134, 7)    # 아파트 펜던트 (갓 왼쪽 위 x, 줄 시작 y)
# 밤 그늘을 밝히는 불빛 (x, y, 반경)
NIGHT_SOURCES = {
    "one_room": [(107, 44, 80)],
    "two_room": [(107, 44, 80), (152, 34, 38)],
    "apartment": [(107, 44, 80), (139, 30, 46)],
}


def rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# ── 표식 색 ────────────────────────────────────────────────
# 그릴 때 "WALL0"~"WALL3"처럼 요소 이름 + 사다리 칸으로 부른다.
_active = {k: list(v) for k, v in MARKERS.items()}


def marker(name):
    """'WALL2' → ('WALL', 2), 표식이 아니면 None"""
    k, i = name[:-1], name[-1:]
    if k in MARKERS and i.isdigit() and int(i) < len(MARKERS[k]):
        return k, int(i)
    return None


@contextmanager
def markers(colors):
    """표식 자리에 다른 색을 넣고 그린다 (확인용). colors: {요소: [색…]}"""
    global _active
    old = _active
    _active = {k: list(colors.get(k, v)) for k, v in MARKERS.items()}
    try:
        yield
    finally:
        _active = old


def _lum(h):
    r, g, b, _ = rgba(h)
    return 0.299 * r + 0.587 * g + 0.114 * b


def ramp_step(key, i, f):
    """사다리 i칸을 f배 어둡게 할 때 갈 칸. 기본 색 밝기로 정해서 어떤 색 묶음이든 같은 칸이 된다."""
    ramp = MARKERS[key]
    target = _lum(ramp[i]) * f
    return min(range(i, len(ramp)), key=lambda j: abs(_lum(ramp[j]) - target))


def marker_lookup():
    return {rgba(c)[:3]: (k, i) for k, cs in _active.items() for i, c in enumerate(cs)}


def col(name):
    m = marker(name)
    if m:
        return rgba(_active[m[0]][m[1]])
    return rgba(PALETTE[name] if name in PALETTE else ROOM[name])


BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dot(x, y, level):
    """4×4 바이어 디더: level 0~16 중 몇 칸을 칠할지"""
    return BAYER[y % 4][x % 4] < level


class Canvas:
    def __init__(self, w=W, h=H):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.skyline = set()  # 유리창 안에 그린 바깥 풍경 (빛이 지나가고, 밤엔 더 어둡게)

    def inside(self, x, y):
        return 0 <= x < self.img.width and 0 <= y < self.img.height

    def rect(self, x, y, w, h, c):
        if w > 0 and h > 0:
            self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=c if isinstance(c, tuple) else col(c))

    def px(self, x, y, c):
        if self.inside(x, y):
            self.img.putpixel((x, y), c if isinstance(c, tuple) else col(c))

    def get(self, x, y):
        return self.img.getpixel((x, y))

    def hline(self, x, y, w, c):
        self.rect(x, y, w, 1, c)

    def vline(self, x, y, h, c):
        self.rect(x, y, 1, h, c)

    def box(self, x, y, w, h, fill, outline="K", hi=None, sh=None):
        """채움 + 외곽선 + (선택) 위·왼쪽 밝은 선, 아래·오른쪽 어두운 선"""
        self.rect(x, y, w, h, outline)
        self.rect(x + 1, y + 1, w - 2, h - 2, fill)
        if hi:
            self.hline(x + 1, y + 1, w - 2, hi)
            self.vline(x + 1, y + 1, h - 2, hi)
        if sh:
            self.hline(x + 1, y + h - 2, w - 2, sh)
            self.vline(x + w - 2, y + 1, h - 2, sh)

    def dither(self, x, y, w, h, c, level):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if dot(xx, yy, level):
                    self.px(xx, yy, c)

    def shade(self, x, y, w, h, f=0.82, level=16):
        """이미 칠한 픽셀을 어둡게 (그늘·접지 그림자). 표식 색은 사다리의 어두운 칸으로 옮긴다."""
        lut = marker_lookup()
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if self.inside(xx, yy) and dot(xx, yy, level):
                    r, g, b, a = self.img.getpixel((xx, yy))
                    if not a:
                        continue
                    m = lut.get((r, g, b))
                    if m:
                        k, i = m
                        self.img.putpixel((xx, yy), rgba(_active[k][ramp_step(k, i, f)], a))
                    else:
                        self.img.putpixel((xx, yy), (int(r * f), int(g * f), int(b * f), a))

    def grid(self, rows, x, y):
        for yy, r in enumerate(rows):
            for xx, ch in enumerate(r):
                if ch != ".":
                    self.px(x + xx, y + yy, ch)

    def clear(self, x, y, w, h):
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=(0, 0, 0, 0))

    def poly(self, points, c):
        self.d.polygon(points, fill=c if isinstance(c, tuple) else col(c))


PLANT = [
    "......KK......",
    ".....K33K.....",
    "....K3qqqK....",
    "....K3qQqK.KK.",
    "....KqqQqKK3qK",
    ".KK..KqQKK3qqK",
    "K33K..KQK3qQqK",
    "K3qqK.KQKqqQK.",
    "KqqQqKKQ.KQKK.",
    ".KqqQQ#Q..QK..",
    "..KKK#Q#.Q#...",
    ".....K#QQ#KK..",
    "..KK.KQ#QK33K.",
    ".K3qKK#QKK3qqK",
    "K3qqqKQ#K3qQqK",
    "KqqQQQ#QQqQQK.",
    ".KQQK#Q#KQQK..",
    "..KK.KQ#.KK...",
    ".....KQ#K.....",
    ".....K#QK.....",
    ".KKKKKKKKKKKK.",
    "Kr1rrrrrrrrRRK",
    "KRRRRRRRRRRddK",
    ".KrrrrrrrrrRK.",
    ".K1rrrrrrrRRK.",
    ".KrrrrrrrrRRK.",
    "..KrrrrrrrRK..",
    "..KrrrrrrRRK..",
    "..KRRRRRRRdK..",
    "...KKKKKKKK...",
]

CACTUS = [
    "..K...",
    ".K3K..",
    "KKqKK.",
    "K3qQK.",
    ".KqQK.",
    "KKKKKK",
    "KaaaAK",
    ".KAAK.",
]

FLOWER_POT = [
    ".K.K..",
    "KiKyK.",
    ".KqK..",
    "KKqKK.",
    "KaaaAK",
    ".KAAK.",
]

# 전구줄 (원룸): (시작x, 끝x, 걸린 높이, 처짐)
GARLANDS = {"one_room": [(108, 159, 3, 5), (2, 52, 3, 4)]}
BULB_COLORS = ["y", "r", "q", "f", "i"]


def garland_points(x0, x1, y0, sag):
    pts = []
    n = x1 - x0
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + i, y0 + round(sag * 4 * t * (1 - t))))
    return pts


def bulbs(level="one_room"):
    out = []
    for gi, (x0, x1, y0, sag) in enumerate(GARLANDS.get(level, [])):
        pts = garland_points(x0, x1, y0, sag)
        for i in range(3, len(pts) - 2, 7):
            x, y = pts[i]
            out.append((x, y + 1, BULB_COLORS[(i // 7 + gi) % len(BULB_COLORS)]))
    return out


# ── 벽 ─────────────────────────────────────────────────────

def corner_shade(cv, y0=0, y1=56):
    cv.shade(0, y0, 3, y1 - y0, 0.93, 16)
    cv.shade(3, y0, 2, y1 - y0, 0.95, 8)
    cv.shade(W - 3, y0, 3, y1 - y0, 0.93, 16)
    cv.shade(W - 5, y0, 2, y1 - y0, 0.95, 8)


def white_baseboard(cv, y=54):
    cv.hline(0, y, W, "paint_dark")
    cv.hline(0, y + 1, W, "paint_hi")
    cv.rect(0, y + 2, W, FLOOR_Y - y - 3, "paint")
    cv.hline(0, FLOOR_Y - 2, W, "paint_shade")
    cv.hline(0, FLOOR_Y - 1, W, "paint_dark")


def draw_wall_one_room(cv):
    cv.rect(0, 0, W, FLOOR_Y - 4, "WALL0")
    # 세로 줄무늬 벽지 + 작은 꽃점
    for x in range(4, W, 10):
        cv.vline(x, 4, 38, "WALL1")
        cv.vline(x + 1, 4, 38, "WALL1")
    for y in range(10, 40, 10):
        for x in range(9 if (y // 10) % 2 else 4, W, 10):
            cv.px(x - 1, y, "WALL2")
            cv.px(x + 1, y, "WALL2")
            cv.px(x, y - 1, "WALL2")
            cv.px(x, y + 1, "WALL2")
    # 천장 몰딩 + 천장 그늘
    cv.rect(0, 0, W, 2, "molding")
    cv.hline(0, 2, W, "molding_dark")
    cv.shade(0, 3, W, 1, 0.94, 12)
    cv.shade(0, 4, W, 1, 0.95, 6)
    # 허리 몰딩 아래 판벽
    cv.rect(0, 42, W, 14, "wainscot")
    cv.hline(0, 42, W, "wainscot_hi")
    cv.hline(0, 43, W, "molding_dark")
    for x in range(5, W, 16):
        cv.rect(x, 46, 12, 8, "wainscot_dark")
        cv.rect(x + 1, 47, 10, 6, "wainscot")
        cv.hline(x + 1, 52, 10, "wainscot_hi")
    # 걸레받이
    cv.rect(0, 56, W, 4, "base")
    cv.hline(0, 56, W, "base_hi")
    cv.hline(0, 59, W, "base_dark")
    corner_shade(cv)


def draw_wall_two_room(cv):
    cv.rect(0, 0, W, FLOOR_Y - 4, "WALL0")
    # 마름모 격자 벽지 + 마름모 가운데 작은 꽃
    for y in range(5, 40):
        for x in range(W):
            if (x + y) % 12 == 0 or (x - y) % 12 == 0:
                cv.px(x, y, "WALL1")
    for cy in range(6, 40, 6):
        for cx in range(0 if (cy // 6) % 2 else 6, W + 1, 12):
            cv.px(cx, cy, "WALL2")
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                cv.px(cx + dx, cy + dy, "WALL2")
    # 이 단 천장 몰딩 (톱니 장식)
    cv.hline(0, 0, W, "paint_shade")
    cv.hline(0, 1, W, "paint_hi")
    cv.hline(0, 2, W, "paint")
    for x in range(W):
        cv.px(x, 3, "paint" if x % 3 else "paint_shade")
    cv.hline(0, 4, W, "paint_dark")
    cv.shade(0, 5, W, 1, 0.9, 12)
    cv.shade(0, 6, W, 1, 0.94, 6)
    # 흰 판벽 (세로 홈) + 허리 몰딩
    cv.hline(0, 39, W, "paint_shade")
    cv.hline(0, 40, W, "paint_hi")
    cv.hline(0, 41, W, "paint")
    cv.hline(0, 42, W, "paint_dark")
    cv.rect(0, 43, W, 11, "paint")
    for x in range(1, W, 5):
        cv.vline(x, 43, 11, "paint_shade")
        cv.vline(x + 1, 43, 11, "paint_hi")
    cv.shade(0, 43, W, 1, 0.92, 16)
    cv.shade(0, 44, W, 1, 0.95, 8)
    cv.shade(0, 38, W, 1, 0.9, 8)
    white_baseboard(cv, 54)
    corner_shade(cv)


def draw_wall_apartment(cv):
    cv.rect(0, 0, W, FLOOR_Y - 4, "WALL0")
    # 결 고운 실크 벽지: 성긴 무늬 점 + 은은한 세로 결
    for y in range(7, 36):
        for x in range(W):
            if (x * 5 + y * 3) % 13 == 0 and (x + y) % 2 == 0:
                cv.px(x, y, "WALL1")
    for x in range(20, W, 40):
        cv.vline(x, 7, 29, "WALL1")
    # 천장 몰딩 + 간접조명 홈
    cv.hline(0, 0, W, "paint_shade")
    cv.hline(0, 1, W, "paint_hi")
    cv.rect(0, 2, W, 2, "paint")
    cv.hline(0, 4, W, "paint_shade")
    cv.hline(0, 5, W, "paint_hi")
    cv.hline(0, 6, W, "paint_dark")
    cv.shade(0, 7, W, 1, 0.9, 16)
    cv.shade(0, 8, W, 1, 0.93, 10)
    cv.shade(0, 9, W, 1, 0.95, 4)
    # 높은 흰 판벽 (액자 몰딩)
    cv.hline(0, 35, W, "paint_shade")
    cv.hline(0, 36, W, "paint_hi")
    cv.hline(0, 37, W, "paint")
    cv.hline(0, 38, W, "paint_dark")
    cv.rect(0, 39, W, 15, "paint")
    for x in range(3, W, 20):
        cv.rect(x, 42, 16, 9, "paint_shade")
        cv.rect(x + 1, 43, 14, 7, "paint")
        cv.hline(x + 1, 43, 14, "paint_hi")
        cv.vline(x + 1, 43, 7, "paint_hi")
        cv.hline(x, 51, 16, "paint_hi")
    cv.shade(0, 39, W, 1, 0.94, 12)
    cv.shade(0, 34, W, 1, 0.92, 10)
    white_baseboard(cv, 54)
    corner_shade(cv)


# ── 창 ─────────────────────────────────────────────────────

def glints(cv, spots):
    """유리 반사 (반투명 흰 빗금)"""
    for (sx, sy, n) in spots:
        for k in range(n):
            cv.px(sx + k, sy - k, (255, 255, 255, 110))


def draw_window_one_room(cv):
    gx, gy, gw, gh = GLASS_BY_LEVEL["one_room"]
    # 틀
    cv.box(gx - 3, gy - 3, gw + 6, gh + 6, "frame", sh="frame_shade")
    cv.hline(gx - 2, gy - 2, gw + 4, "frame_hi")
    cv.clear(gx, gy, gw, gh)
    # 창살
    cv.rect(gx + 12, gy, 2, gh, "frame")
    cv.vline(gx + 13, gy, gh, "frame_shade")
    cv.rect(gx, gy + 9, gw, 2, "frame")
    cv.hline(gx, gy + 10, gw, "frame_shade")
    glints(cv, [(gx + 2, gy + 6, 5), (gx + 4, gy + 6, 3), (gx + 16, gy + 17, 5), (gx + 18, gy + 17, 3)])
    # 창턱 + 작은 선인장
    cv.box(gx - 6, gy + gh + 2, gw + 12, 4, "l", hi="L", sh="o")
    cv.grid(CACTUS, gx + 19, gy + gh - 6)


def draw_window_two_room(cv):
    """세 칸 여닫이창 + 위 채광창"""
    gx, gy, gw, gh = GLASS_BY_LEVEL["two_room"]
    cv.box(gx - 3, gy - 3, gw + 6, gh + 6, "frame", sh="frame_shade")
    cv.hline(gx - 2, gy - 2, gw + 4, "frame_hi")
    cv.clear(gx, gy, gw, gh)
    for mx in (gx + 13, gx + 28):
        cv.rect(mx, gy, 2, gh, "frame")
        cv.vline(mx + 1, gy, gh, "frame_shade")
    cv.rect(gx, gy + 6, gw, 2, "frame")
    cv.hline(gx, gy + 7, gw, "frame_shade")
    # 가운데 칸 손잡이
    cv.px(gx + 16, gy + 13, "gold")
    cv.px(gx + 16, gy + 14, "gold_dark")
    glints(cv, [(gx + 2, gy + 14, 5), (gx + 4, gy + 14, 3), (gx + 18, gy + 18, 6), (gx + 32, gy + 13, 5),
                (gx + 34, gy + 13, 3), (gx + 3, gy + 4, 3), (gx + 31, gy + 4, 3)])
    # 창턱 + 화분 둘
    cv.box(gx - 6, gy + gh + 2, gw + 12, 4, "l", hi="L", sh="o")
    cv.grid(FLOWER_POT, gx + 3, gy + gh - 4)
    cv.grid(CACTUS, gx + gw - 9, gy + gh - 6)


# 아파트 창밖 도시: (유리 왼쪽에서 x, 너비, 높이) — 먼 건물, 가까운 건물
CITY_FAR = [(0, 7, 11), (6, 5, 13), (11, 8, 10), (18, 4, 12), (23, 9, 9), (31, 6, 13), (38, 5, 10), (43, 7, 12), (50, 6, 10)]
CITY_NEAR = [(2, 6, 8), (9, 7, 6), (17, 5, 9), (25, 8, 7), (34, 6, 9), (41, 9, 6), (49, 7, 8)]
RAIL_Y = 14  # 유리 위에서 난간 손잡이까지


def city_windows(level="apartment"):
    """가까운 건물 창 자리 (밤에 몇 개가 켜진다)"""
    gx, gy, gw, gh = GLASS_BY_LEVEL[level]
    out = []
    for bi, (x, w, h) in enumerate(CITY_NEAR):
        top = gy + gh - h
        for yy in range(top + 2, gy + RAIL_Y, 2):
            for xx in range(x + 1, x + w - 1, 2):
                out.append((gx + xx, yy, bi))
    return out


def draw_window_apartment(cv):
    """통창 (흰 샷시 이중창) + 베란다 난간 너머 도시"""
    gx, gy, gw, gh = GLASS_BY_LEVEL["apartment"]
    cv.box(gx - 3, gy - 3, gw + 6, gh + 6, "paint", hi="paint_hi", sh="paint_shade")
    cv.clear(gx, gy, gw, gh)
    # 먼 건물 → 가까운 건물
    for (x, w, h) in CITY_FAR:
        w = min(w, gw - x)
        cv.rect(gx + x, gy + gh - h, w, h, "city_far")
        for yy in range(gy + gh - h + 1, gy + gh):
            cv.skyline.update((gx + xx, yy) for xx in range(x, x + w))
        cv.skyline.update((gx + xx, gy + gh - h) for xx in range(x, x + w))
    for (x, w, h) in CITY_NEAR:
        w = min(w, gw - x)
        top = gy + gh - h
        cv.rect(gx + x, top, w, h, "city_near")
        cv.vline(gx + x + w - 1, top, h, "city_dark")
        cv.hline(gx + x, top, w, "city_dark")
        for yy in range(top, gy + gh):
            cv.skyline.update((gx + xx, yy) for xx in range(x, x + w))
    for (x, y, _) in city_windows():
        cv.px(x, y, "city_win")
    # 난간 (유리 아래쪽)
    ry = gy + RAIL_Y
    cv.hline(gx, ry, gw, "rail_hi")
    cv.hline(gx, ry + 1, gw, "rail")
    for x in range(gx + 1, gx + gw, 3):
        cv.vline(x, ry + 2, gh - RAIL_Y - 3, "rail")
    cv.hline(gx, gy + gh - 1, gw, "rail")
    for (x, y) in list(cv.skyline):
        if cv.get(x, y)[:3] == col("rail")[:3] or cv.get(x, y)[:3] == col("rail_hi")[:3]:
            cv.skyline.discard((x, y))
    # 창틀: 가운데 겹침 문틀 + 양쪽 얇은 문틀
    mid = gx + gw // 2 - 1
    cv.rect(mid, gy, 3, gh, "paint")
    cv.vline(mid, gy, gh, "paint_hi")
    cv.vline(mid + 2, gy, gh, "paint_shade")
    cv.rect(mid + 1, gy + 8, 1, 4, "metal_dark")
    for sx in (gx + 13, gx + gw - 15):
        cv.rect(sx, gy, 2, gh, "paint")
        cv.vline(sx + 1, gy, gh, "paint_shade")
    for p in [(mid + dx, gy + yy) for dx in range(3) for yy in range(gh)] + \
             [(sx + dx, gy + yy) for sx in (gx + 13, gx + gw - 15) for dx in range(2) for yy in range(gh)]:
        cv.skyline.discard(p)
    glints(cv, [(gx + 2, gy + 7, 5), (gx + 4, gy + 7, 3), (gx + 17, gy + 6, 5), (gx + 32, gy + 7, 5),
                (gx + 34, gy + 7, 3), (gx + 46, gy + 6, 5)])
    # 돌 창턱
    cv.box(gx - 6, gy + gh + 2, gw + 12, 4, "paint", hi="paint_hi", sh="paint_dark")


# ── 커튼 ───────────────────────────────────────────────────

def curtain_panel(cv, x, y0, length, w, tie_y, right):
    """묶은 커튼 한 폭 (주름: 밝은 면 / 바탕 / 그늘)"""
    bottom = y0 + length - 1
    keep = [(x + k, bottom, cv.get(x + k, bottom)) for k in range(w)]
    cv.rect(x, y0, w, length, "K")
    cv.rect(x + 1, y0, w - 2, length - 1, "CURTAIN1")
    for fx in range(1, w - 2, 3):
        cv.vline(x + fx, y0, length - 1, "CURTAIN0")
        cv.vline(x + fx + 1, y0, length - 1, "CURTAIN1")
        cv.vline(x + fx + 2, y0, length - 1, "CURTAIN2")
    # 묶은 끈
    cv.rect(x, tie_y, w, 3, "K")
    cv.rect(x + 1, tie_y + 1, w - 2, 1, "y")
    cv.px(x + (w - 2 if right else 1), tie_y + 1, "Y")
    # 아랫단 물결 (뒤 벽이 보이게)
    for k in range(1, w - 1):
        if k % 3 == 1:
            cv.px(x + k, bottom, keep[k][2])
            cv.px(x + k, bottom - 1, "K")
    cv.shade(x + 1, y0, w - 2, 2, 0.85)


def draw_curtains_one_room(cv):
    # 봉
    cv.rect(56, 3, 52, 2, "O")
    cv.hline(56, 3, 52, "l")
    cv.box(54, 2, 3, 4, "o", hi="L")
    cv.box(107, 2, 3, 4, "o", hi="L")
    for x in range(59, 106, 4):
        cv.px(x, 5, "metal_dark")
    for (x, right) in [(57, False), (97, True)]:
        curtain_panel(cv, x, 5, 33, 10, 23, right)


def draw_curtains_two_room(cv):
    """긴 커튼 두 폭 + 물결 덮개(밸런스)"""
    for (x, right) in [(50, False), (105, True)]:
        curtain_panel(cv, x, 6, 47, 10, 30, right)
    x0, x1, y0 = 47, 117, 3
    w = x1 - x0 + 1
    cv.rect(x0, y0, w, 6, "K")
    cv.rect(x0 + 1, y0 + 1, w - 2, 5, "CURTAIN1")
    cv.hline(x0 + 1, y0 + 1, w - 2, "CURTAIN0")
    for x in range(x0 + 2, x1, 4):
        cv.vline(x, y0 + 2, 4, "CURTAIN0")
        cv.vline(x + 2, y0 + 2, 4, "CURTAIN2")
    # 아래 물결
    for i, x in enumerate(range(x0, x1 + 1, 7)):
        sw = min(7, x1 + 1 - x)
        cv.rect(x + 1, y0 + 6, sw - 2, 1, "CURTAIN1")
        cv.px(x + 2, y0 + 6, "CURTAIN0")
        cv.px(x + sw - 2, y0 + 6, "CURTAIN2")
        cv.rect(x + 2, y0 + 7, sw - 4, 1, "CURTAIN2")
        cv.px(x, y0 + 6, "K")
        cv.px(x + 1, y0 + 7, "K")
        cv.px(x + sw - 1, y0 + 6, "K")
        cv.px(x + sw - 2, y0 + 7, "K")
        cv.hline(x + 2, y0 + 8, sw - 4, "K")
        cv.px(x + 3, y0 + 7, "CURTAIN2")
    cv.px(x1, y0 + 6, "K")
    cv.shade(x0 + 1, y0 + 1, w - 2, 1, 0.85)


def drape(cv, x, y0, y1, w):
    """바닥까지 내려오는 주름 커튼 한 폭"""
    cv.rect(x, y0, w, y1 - y0 + 1, "K")
    cv.rect(x + 1, y0 + 1, w - 2, y1 - y0 - 1, "CURTAIN1")
    for fx in range(1, w - 1, 3):
        cv.vline(x + fx, y0 + 2, y1 - y0 - 2, "CURTAIN0")
        cv.vline(x + fx + 2, y0 + 2, y1 - y0 - 2, "CURTAIN2")
    # 위 주름 머리 (고리)
    cv.hline(x + 1, y0 + 1, w - 2, "CURTAIN2")
    for fx in range(1, w - 1, 3):
        cv.px(x + fx + 1, y0 + 1, "metal")
    # 아랫단: 주름 끝이 살짝 퍼진다
    for fx in range(1, w - 1, 3):
        cv.px(x + fx + 2, y1 - 1, "CURTAIN3")
    cv.shade(x + 1, y0 + 2, w - 2, 2, 0.85)
    cv.shade(x + 1, y1 - 2, w - 2, 2, 0.9, 8)


def draw_curtains_apartment(cv):
    # 천장 커튼 레일
    cv.rect(42, 7, 80, 2, "metal_dark")
    cv.hline(42, 7, 80, "metal")
    drape(cv, 44, 8, 59, 10)
    drape(cv, 110, 8, 59, 10)


# ── 벽 장식 ────────────────────────────────────────────────

CLOCK = [
    "...KKKKK...",
    ".KKoooooKK.",
    ".KowwwwwOK.",
    "KowwwKwwwOK",
    "KowwwKwwwOK",
    "KowwwKKwwOK",
    "KowwwwwwwOK",
    "KowwwwwwwOK",
    ".KOwwwwwOK.",
    ".KKOOOOOKK.",
    "...KKKKK...",
]


def draw_wall_decor_one_room(cv):
    # 액자 (산 풍경)
    x, y = 114, 11
    cv.box(x, y, 23, 18, "o", hi="l", sh="O")
    cv.rect(x + 2, y + 2, 19, 14, "W")
    cv.rect(x + 3, y + 3, 17, 12, "f")
    cv.rect(x + 3, y + 3, 17, 4, "F")
    cv.dither(x + 3, y + 7, 17, 2, "F", 6)
    cv.poly([(x + 3, y + 14), (x + 9, y + 8), (x + 15, y + 14)], "Q")
    cv.poly([(x + 10, y + 14), (x + 15, y + 9), (x + 19, y + 13), (x + 19, y + 14)], "q")
    cv.px(x + 9, y + 8, "W")
    cv.px(x + 8, y + 9, "W")
    cv.px(x + 10, y + 9, "W")
    cv.rect(x + 3, y + 13, 17, 2, "Q")
    cv.rect(x + 15, y + 4, 3, 3, "y")
    cv.px(x + 15, y + 4, "2")
    cv.vline(x + 11, y - 4, 4, "metal_dark")
    # 벽시계
    cx, cy = 143, 12
    cv.grid(CLOCK, cx, cy)
    cv.px(cx + 5, cy + 2, "r")
    # 전구줄 (낮에는 꺼진 색)
    for (x0, x1, y0, sag) in GARLANDS["one_room"]:
        for (px_, py_) in garland_points(x0, x1, y0, sag):
            cv.px(px_, py_, "D")
    for (bx, by, c) in bulbs("one_room"):
        cv.px(bx, by, c)
        cv.px(bx, by + 1, c)
        cv.px(bx, by + 2, "K")


AIRCON = [
    "KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK",
    "KWWWWWWWWWWWWWWWWWWWWWWWWWWWWWwK",
    "KWwwwwwwwwwwwwwwwwwwwwwwwwwwwwgK",
    "KWwwwwwwwwwwwwwwwwwwwwwwwwwqwwgK",
    "KgggggggggggggggggggggggggggggGK",
    "KDBDBDBDBDBDBDBDBDBDBDBDBDBDBDDK",
    ".KKKKKKKKKKKKKKKKKKKKKKKKKKKKKK.",
]

SCONCE_ART = [
    "..KKKKK..",
    ".KwwwwgK.",
    ".KwwwwgK.",
    "KwwwwwwgK",
    "KgggggggK",
    ".KKKKKKK.",
    "....K....",
    "...KjK...",
    "..KjjjK..",
    "...KKK...",
]


def draw_sconce(cv, lit=False):
    rows = SCONCE_ART
    if lit:
        rows = [r.replace("w", "2").replace("g", "y") for r in rows[:5]] + rows[5:]
    rows = [r.replace("j", "gold") for r in rows]
    x, y = SCONCE
    for yy, r in enumerate(rows):
        for xx, ch in enumerate(r.replace("gold", "@")):
            if ch == "@":
                cv.px(x + xx, y + yy, "gold")
            elif ch != ".":
                cv.px(x + xx, y + yy, ch)
    cv.vline(x + 4, y + 6, 1, "gold_dark")


def framed(cv, x, y, w, h, outer, outer_hi, outer_sh, mat):
    """액자 테두리 + 매트. 그림 칸 (x, y, w, h)을 돌려준다"""
    cv.box(x, y, w, h, outer, hi=outer_hi, sh=outer_sh)
    cv.rect(x + 2, y + 2, w - 4, h - 4, mat)
    return x + 4, y + 4, w - 8, h - 8


def wall_drop_shadow(cv, x, y, w, h):
    """벽에 걸린 것의 오른쪽·아래 그림자"""
    cv.shade(x + w, y + 1, 1, h, 0.9)
    cv.shade(x + 1, y + h, w, 1, 0.9)


def draw_wall_decor_two_room(cv):
    # 벽걸이 에어컨 (옷장 위)
    cv.grid(AIRCON, 10, 5)
    wall_drop_shadow(cv, 10, 5, 32, 7)
    # 금색 액자 (바다 풍경)
    x, y, w, h = 119, 9, 24, 20
    wall_drop_shadow(cv, x, y, w, h)
    ix, iy, iw, ih = framed(cv, x, y, w, h, "gold", "gold_hi", "gold_dark", "paint_hi")
    cv.rect(ix, iy, iw, ih, "f")
    cv.rect(ix, iy, iw, 3, "v")
    cv.rect(ix, iy + 7, iw, ih - 7, "c")
    cv.hline(ix, iy + 7, iw, "v")
    cv.hline(ix, iy + ih - 2, iw, "C")
    cv.hline(ix + 2, iy + ih - 4, 3, "v")
    cv.hline(ix + 9, iy + ih - 3, 4, "v")
    cv.rect(ix + 11, iy + 3, 3, 3, "y")
    cv.px(ix + 11, iy + 3, "2")
    # 돛단배
    cv.px(ix + 5, iy + 3, "W")
    cv.vline(ix + 5, iy + 4, 2, "W")
    cv.hline(ix + 4, iy + 5, 2, "W")
    cv.vline(ix + 6, iy + 2, 5, "K")
    cv.hline(ix + 3, iy + 7, 6, "R")
    cv.hline(ix + 4, iy + 8, 4, "R")
    cv.vline(x + w // 2, y - 4, 4, "metal_dark")
    # 벽시계 + 벽등
    cv.grid(CLOCK, 147, 10)
    cv.px(152, 12, "r")
    wall_drop_shadow(cv, 147, 10, 11, 11)
    draw_sconce(cv)


def draw_pendant(cv, lit=False):
    x, y = PENDANT
    cx = x + 5
    if not lit:
        cv.vline(cx, y, 12, "B")
    rows = [
        "....KKK....",
        "..KKkBBKK..",
        ".KkkBBBBBK.",
        "KkBBBBBBBBK",
        "KjjjjjjjjjK",
        ".KK.www.KK.",
        "....KKK....",
    ]
    if lit:
        rows = [r.replace("w", "2") for r in rows]
    for yy, r in enumerate(rows):
        for xx, ch in enumerate(r):
            if ch == "j":
                cv.px(x + xx, y + 12 + yy, "gold")
            elif ch != "." and (not lit or yy >= 5):
                cv.px(x + xx, y + 12 + yy, ch)


def draw_wall_decor_apartment(cv):
    # 식물 그림 액자 둘 (검은 얇은 테)
    for (x, art) in ((121, "leaf"), (147, "arch")):
        y, w, h = 13, 11, 17
        wall_drop_shadow(cv, x, y, w, h)
        cv.box(x, y, w, h, "B", hi="k")
        cv.rect(x + 2, y + 2, w - 4, h - 4, "paint_hi")
        ix, iy = x + 3, y + 3
        if art == "leaf":
            cv.rect(ix, iy, 5, 11, "w")
            cv.vline(ix + 2, iy + 3, 8, "Q")
            for k, yy in enumerate(range(iy + 1, iy + 9, 2)):
                side = -1 if k % 2 else 1
                cv.px(ix + 2 + side, yy, "q")
                cv.px(ix + 2 + side * 2, yy + 1 if k else yy, "3")
                cv.px(ix + 2 + side, yy + 1, "Q")
        else:
            cv.rect(ix, iy, 5, 11, "w")
            cv.rect(ix, iy + 5, 5, 6, "a")
            cv.hline(ix + 1, iy + 4, 3, "a")
            cv.rect(ix + 1, iy + 7, 3, 4, "7")
            cv.px(ix + 2, iy + 1, "y")
            cv.hline(ix, iy + 10, 5, "A")
    # 펜던트 조명 (꺼짐)
    draw_pendant(cv)


# ── 바닥 ───────────────────────────────────────────────────

def floor_shading(cv):
    """벽 쪽 그늘 + 아래쪽 어둡게"""
    cv.shade(0, FLOOR_Y, W, 1, 0.78)
    cv.shade(0, FLOOR_Y + 1, W, 1, 0.86, 12)
    cv.shade(0, FLOOR_Y + 2, W, 1, 0.9, 8)
    cv.shade(0, FLOOR_Y + 3, W, 1, 0.92, 4)
    cv.shade(0, H - 6, W, 3, 0.94, 6)
    cv.shade(0, H - 3, W, 3, 0.92, 10)


def draw_floor_one_room(cv):
    """제각각 길이의 좁은 마루"""
    plank = 7
    for i, y in enumerate(range(FLOOR_Y, H, plank)):
        off = (i * 23) % 37
        xs = sorted(set([0] + list(range(off, W, 37)) + [W]))
        for j in range(len(xs) - 1):
            x0, x1 = xs[j], xs[j + 1]
            dark = (i * 5 + j * 7) % 3 == 2
            tone = "FLOOR2" if dark else "FLOOR1"
            cv.rect(x0, y, x1 - x0, plank, tone)
            cv.hline(x0, y, x1 - x0, "FLOOR0")
            # 나뭇결
            gy = y + 2 + (i + j) % 3
            gx = x0 + 4 + (i * 3 + j * 5) % 9
            cv.hline(gx, gy, 6 + (i + j) % 5, "FLOOR1" if dark else "FLOOR2")
            if (i * 7 + j * 3) % 5 == 0:
                kx = x0 + 10 + (i * 11) % 14
                cv.px(kx, y + 3, "FLOOR3")
                cv.px(kx + 1, y + 3, "FLOOR3")
            if x0 > 0:
                cv.vline(x0, y, plank - 1, "FLOOR3")
        cv.hline(0, y + plank - 1, W, "FLOOR3")
    floor_shading(cv)


def draw_floor_two_room(cv):
    """넓고 긴 마루 (반씩 엇갈림)"""
    plank, length = 9, 52
    for i, y in enumerate(range(FLOOR_Y, H, plank)):
        off = -((i * 26) % length) - (i % 3) * 5
        for j, x0 in enumerate(range(off, W, length)):
            a, b = max(0, x0), min(W, x0 + length)
            if b <= a:
                continue
            dark = (i * 3 + j * 5) % 4 == 1
            tone = "FLOOR2" if dark else "FLOOR1"
            grain = "FLOOR1" if dark else "FLOOR2"
            cv.rect(a, y, b - a, plank, tone)
            cv.hline(a, y, b - a, "FLOOR0")
            cv.vline(a, y, plank - 1, "FLOOR0") if x0 >= 0 else None
            # 긴 나뭇결 두 줄
            for k, gy in enumerate((y + 2 + (i + j) % 2, y + 5 + (i * j) % 2)):
                gx = x0 + 5 + ((i * 7 + j * 11 + k * 13) % 17)
                cv.hline(max(a, gx), gy, max(0, min(b, gx + 14 + (i + k) % 9) - max(a, gx)), grain)
                cv.px(gx + 15 + (i + k) % 9, gy + 1, grain) if a <= gx + 15 + (i + k) % 9 < b else None
            if (i * 5 + j * 3) % 7 == 0:
                kx = x0 + 30 + (i * 3) % 9
                if a <= kx < b - 2:
                    cv.hline(kx, y + 4, 2, "FLOOR3")
                    cv.px(kx - 1, y + 4, grain)
            if x0 > 0:
                cv.vline(x0 - 1, y, plank - 1, "FLOOR3")
        cv.hline(0, y + plank - 1, W, "FLOOR3")
    floor_shading(cv)


def draw_floor_apartment(cv):
    """헤링본 마루"""
    w, k = 4, 3          # 판자 너비, 길이 = k × 너비
    L = w * k
    period = 2 * k * w   # 한 줄기(계단)가 세로로 차지하는 높이
    # 판자가 벽까지 올라가지 않게 따로 그려서 바닥 부분만 옮긴다
    floor, cv = cv, Canvas()
    cv.rect(0, FLOOR_Y, W, H - FLOOR_Y, "FLOOR3")
    for s in range(-12, 16):
        ox, oy = 0, FLOOR_Y + s * period - 4
        for i in range(-30, 60):
            x, y = ox + i * w, oy + i * w
            if y > H + L or x > W + L:
                break
            # 가로 판자
            cv.rect(x, y, L - 1, w - 1, "FLOOR1")
            cv.hline(x, y, L - 1, "FLOOR0")
            cv.px(x + 3 + (i + s) % 5, y + 2, "FLOOR2")
            cv.px(x + 4 + (i + s) % 5, y + 2, "FLOOR2")
            # 세로 판자
            vy = y + w
            cv.rect(x, vy, w - 1, L - 1, "FLOOR2")
            cv.vline(x, vy, L - 1, "FLOOR0")
            cv.px(x + 2, vy + 3 + (i * 3 + s) % 6, "FLOOR1")
    floor.img.paste(cv.img.crop((0, FLOOR_Y, W, H)), (0, FLOOR_Y))
    cv = floor
    # 벽 쪽 테두리 판자
    cv.rect(0, FLOOR_Y, W, 3, "FLOOR2")
    cv.hline(0, FLOOR_Y, W, "FLOOR0")
    cv.hline(0, FLOOR_Y + 3, W, "FLOOR3")
    floor_shading(cv)


# ── 가구 (세 단계 모두 같은 자리) ─────────────────────────

def draw_wardrobe(cv):
    # 열린 문 (원근으로 좁게)
    cv.box(0, 15, 7, 62, "l", hi="L", sh="o")
    cv.rect(2, 19, 3, 22, "o")
    cv.rect(2, 45, 3, 26, "o")
    cv.vline(2, 19, 22, "9")
    cv.vline(2, 45, 26, "9")
    cv.rect(4, 42, 2, 3, "y")
    cv.px(4, 42, "2")
    # 몸통
    cv.box(5, 12, 44, 3, "l", hi="L")  # 머리 장식
    cv.box(6, 14, 42, 66, "o", sh="O")
    cv.hline(7, 15, 40, "l")
    # 안쪽 (어둡게, 세로 판자)
    cv.rect(9, 18, 36, 34, "O")
    for x in range(12, 45, 6):
        cv.vline(x, 18, 34, "z")
    cv.shade(9, 18, 36, 3, 0.75)
    cv.shade(9, 21, 36, 2, 0.85, 8)
    cv.shade(9, 18, 3, 34, 0.8, 10)
    # 옷걸이 봉
    cv.hline(9, 21, 36, "metal")
    cv.hline(9, 22, 36, "metal_dark")
    for sx, _ in SLOTS["WARDROBE"][:3]:
        cv.px(sx + 6, 22, "metal_dark")
        cv.px(sx + 6, 23, "metal")
        for i in range(4):
            cv.px(sx + 5 - i, 24 + i, "metal")
            cv.px(sx + 6 + i, 24 + i, "metal")
        cv.hline(sx + 2, 27, 8, "metal")
    # 선반
    for y in (36, 50):
        cv.rect(9, y, 36, 2, "l")
        cv.hline(9, y, 36, "L")
        cv.shade(9, y + 2, 36, 1, 0.8)
    # 서랍 두 칸
    for y in (53, 63):
        cv.box(9, y, 36, 10, "l", hi="9", sh="o")
        cv.rect(23, y + 4, 8, 2, "O")
        cv.hline(23, y + 4, 8, "z")
        cv.px(23, y + 4, "L")
    # 받침 + 다리
    cv.rect(6, 73, 42, 3, "O")
    cv.hline(6, 73, 42, "o")
    cv.box(8, 75, 5, 5, "O")
    cv.box(41, 75, 5, 5, "O")
    # 나뭇결
    for (x, y, n) in [(7, 30, 6), (46, 40, 8), (7, 58, 5), (46, 66, 4)]:
        cv.vline(x, y, n, "9")


def draw_plant(cv):
    cv.grid(PLANT, 48, 50)


def draw_shelf(cv):
    cv.box(62, 38, 40, 42, "o", sh="O")
    cv.rect(63, 39, 38, 2, "l")
    cv.hline(63, 39, 38, "9")
    cv.rect(64, 41, 36, 26, "O")
    cv.shade(64, 41, 36, 2, 0.78)
    for y in (53, 67):
        cv.rect(64, y, 36, 2, "l")
        cv.hline(64, y, 36, "9")
    cv.shade(64, 55, 36, 2, 0.82)
    cv.box(64, 69, 18, 9, "l", hi="9", sh="o")
    cv.box(82, 69, 18, 9, "l", hi="9", sh="o")
    cv.rect(79, 72, 1, 3, "O")
    cv.rect(84, 72, 1, 3, "O")
    cv.px(79, 72, "y")
    cv.px(84, 72, "y")


def draw_lamp(cv, lit=False):
    """스탠드 (책장과 책상 사이)"""
    shade = [
        "...KKKK...",
        "..KwwwwK..",
        "..KwwwgK..",
        ".KwwwwwgK.",
        ".KwwwwwgK.",
        "KwwwwwwwgK",
        "KggggggggK",
        "KKKKKKKKKK",
    ]
    if lit:
        shade = [r.replace("w", "2").replace("g", "y") for r in shade]
    cv.grid(shade, 102, 34)
    if not lit:
        cv.vline(106, 42, 37, "metal_dark")
        cv.vline(105, 42, 37, "metal")
        cv.box(102, 77, 9, 3, "metal", sh="metal_dark")


def draw_desk(cv):
    cv.box(112, 59, 44, 6, "l", hi="9", sh="o")
    cv.hline(113, 60, 42, "L")
    cv.box(113, 64, 4, 16, "o", sh="O")
    cv.box(151, 64, 4, 16, "o", sh="O")
    cv.shade(117, 64, 19, 2, 0.75)
    cv.box(136, 64, 15, 14, "l", hi="9", sh="o")
    cv.hline(137, 70, 13, "o")
    for y in (66, 73):
        cv.rect(141, y, 5, 1, "O")
        cv.px(141, y, "L")


def draw_chair(cv):
    cv.box(125, 70, 12, 13, "o", hi="l", sh="O")
    for x in (128, 131, 134):
        cv.vline(x, 72, 9, "O")
        cv.vline(x - 1, 72, 9, "9")
    cv.box(123, 82, 16, 4, "l", hi="9", sh="o")
    cv.box(124, 85, 3, 9, "o")
    cv.box(135, 85, 3, 9, "o")


def draw_shoe_rack(cv):
    cv.box(6, 100, 28, 32, "o", sh="O")
    cv.rect(8, 102, 24, 26, "O")
    cv.shade(8, 102, 24, 2, 0.8)
    cv.shade(8, 116, 24, 2, 0.8)
    for y in (100, 114, 128):
        cv.rect(7, y, 26, 2, "l")
        cv.hline(7, y, 26, "9")


def draw_trash_can(cv):
    can = [
        ".KKKKKKKKK.",
        "KgggggggggK",
        "KDDDDDDDDDK",
        ".KKKKKKKKK.",
        ".KgWgggggK.",
        ".KgWgGgGGK.",
        ".KgWgGgGGK.",
        ".KgWgGgGGK.",
        ".KgggGgGGK.",
        ".KgggGgGGK.",
        ".KgggGgGGK.",
        ".KggggggGK.",
        ".KGGGGGGGK.",
        "..KKKKKKK..",
    ]
    cv.grid(can, 36, 118)


# ── 러그 ───────────────────────────────────────────────────

def draw_rug_one_room(cv):
    """둥근 러그"""
    rx, ry, rw, rh = 52, 94, 64, 28
    rows = []
    for yy in range(rh):
        t = abs(yy - (rh - 1) / 2) / ((rh - 1) / 2)
        rows.append(int(round(10 * (1 - (1 - t * t) ** 0.5))))
    for yy, inset in enumerate(rows):
        cv.hline(rx + inset, ry + yy, rw - 2 * inset, "RUG2")
    for yy, inset in enumerate(rows[1:-1], start=1):
        cv.hline(rx + inset + 1, ry + yy, rw - 2 * inset - 2, "RUG1")
    for yy, inset in enumerate(rows[3:-3], start=3):
        cv.px(rx + inset + 3, ry + yy, "RUG0")
        cv.px(rx + rw - inset - 4, ry + yy, "RUG0")
    for yy in (ry + 3, ry + rh - 4):
        inset = rows[yy - ry]
        for xx in range(rx + inset + 4, rx + rw - inset - 4, 3):
            cv.px(xx, yy, "RUG0")
    # 가운데 무늬
    cx, cy = rx + rw // 2, ry + rh // 2
    for (dx, dy) in [(0, -4), (-1, -3), (1, -3), (-2, -2), (2, -2), (-3, -1), (3, -1), (-4, 0), (4, 0),
                     (-3, 1), (3, 1), (-2, 2), (2, 2), (-1, 3), (1, 3), (0, 4)]:
        cv.px(cx + dx, cy + dy, "RUG2")
    cv.px(cx, cy, "RUG0")
    # 술 (양 끝)
    for yy in range(ry + 8, ry + rh - 8, 2):
        cv.hline(rx - 2, yy, 2, "rug_fringe")
        cv.hline(rx + rw, yy, 2, "rug_fringe")
    cv.shade(rx + 8, ry + rh - 2, rw - 16, 1, 0.85, 8)
    # 앞쪽 두께
    cv.hline(rx + rows[-1], ry + rh - 1, rw - 2 * rows[-1], "RUG3")


def draw_rug_two_room(cv):
    """줄무늬 네모 러그 (둥근 모서리)"""
    rx, ry, rw, rh = 51, 94, 66, 28
    cv.rect(rx + 2, ry, rw - 4, rh, "RUG2")
    cv.rect(rx + 1, ry + 1, rw - 2, rh - 2, "RUG2")
    cv.rect(rx, ry + 2, rw, rh - 4, "RUG2")
    cv.rect(rx + 2, ry + 2, rw - 4, rh - 4, "RUG1")
    for yy in range(ry + 4, ry + rh - 3, 5):
        cv.hline(rx + 2, yy, rw - 4, "RUG2")
        for xx in range(rx + 3, rx + rw - 3, 2):
            cv.px(xx, yy + 2, "RUG0")
    cv.rect(rx + 2, ry + 2, 3, rh - 4, "RUG0")
    cv.rect(rx + rw - 5, ry + 2, 3, rh - 4, "RUG0")
    cv.vline(rx + 3, ry + 2, rh - 4, "RUG2")
    cv.vline(rx + rw - 4, ry + 2, rh - 4, "RUG2")
    cv.hline(rx + 2, ry + rh - 1, rw - 4, "RUG3")
    cv.px(rx + 1, ry + rh - 2, "RUG3")
    cv.px(rx + rw - 2, ry + rh - 2, "RUG3")
    # 술
    for yy in range(ry + 3, ry + rh - 3, 2):
        cv.hline(rx - 2, yy, 2, "rug_fringe")
        cv.hline(rx + rw, yy, 2, "rug_fringe")
    cv.shade(rx + 6, ry + rh - 3, rw - 12, 1, 0.85, 8)


def draw_rug_apartment(cv):
    """테두리 무늬가 있는 큰 네모 러그"""
    rx, ry, rw, rh = 49, 92, 68, 31
    cv.rect(rx, ry, rw, rh, "RUG3")
    cv.rect(rx + 1, ry + 1, rw - 2, rh - 3, "RUG2")
    # 테두리 띠 무늬
    for xx in range(rx + 2, rx + rw - 2):
        if xx % 4 == 1:
            cv.px(xx, ry + 2, "RUG0")
            cv.px(xx, ry + rh - 4, "RUG0")
        elif xx % 4 in (0, 2):
            cv.px(xx, ry + 3 if xx % 4 == 0 else ry + 2, "RUG1")
    for yy in range(ry + 2, ry + rh - 3):
        if yy % 3 == 0:
            cv.px(rx + 2, yy, "RUG0")
            cv.px(rx + rw - 3, yy, "RUG0")
    # 안쪽 바탕
    ix, iy, iw, ih = rx + 4, ry + 5, rw - 8, rh - 11
    cv.rect(ix, iy, iw, ih, "RUG0")
    cv.rect(ix + 1, iy + 1, iw - 2, ih - 2, "RUG1")
    # 가운데 메달 무늬
    cx, cy = rx + rw // 2, iy + ih // 2
    for r, c in ((6, "RUG2"), (4, "RUG0"), (2, "RUG2")):
        for dy in range(-r // 2, r // 2 + 1):
            span = r - abs(dy) * 2
            cv.hline(cx - span, cy + dy, span * 2 + 1, c)
    cv.px(cx, cy, "RUG0")
    # 모서리 꽃
    for (x, y) in ((ix + 3, iy + 3), (ix + iw - 4, iy + 3), (ix + 3, iy + ih - 4), (ix + iw - 4, iy + ih - 4)):
        cv.px(x, y, "RUG2")
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            cv.px(x + dx, y + dy, "RUG0")
    # 술
    for yy in range(ry + 2, ry + rh - 2, 2):
        cv.hline(rx - 2, yy, 2, "rug_fringe")
        cv.hline(rx + rw, yy, 2, "rug_fringe")
    cv.shade(rx + 2, ry + rh - 3, rw - 4, 1, 0.85, 8)


# ── 침대 ───────────────────────────────────────────────────

BED_X, BED_Y, BED_W = 114, 94, 46

PILLOW = [
    ".KKKKKKKKKKKKKKK.",
    "KWWWWWWWWWWWWWWwK",
    "KWwwwwwwwwwwwwwgK",
    "KWwwwwwwwwwwwwwgK",
    "KwwwwwwwwwwwwwggK",
    ".KKKKKKKKKKKKKKK.",
]


def headboard_slats(cv):
    bx, by, bw = BED_X, BED_Y, BED_W
    cv.rect(bx + 3, by, bw - 6, 1, "K")
    cv.rect(bx + 1, by + 1, bw - 2, 1, "K")
    cv.rect(bx, by + 2, bw, 10, "K")
    cv.rect(bx + 3, by + 1, bw - 6, 1, "headboard_hi")
    cv.rect(bx + 1, by + 2, bw - 2, 9, "headboard")
    cv.hline(bx + 1, by + 2, bw - 2, "headboard_hi")
    for x in range(bx + 5, bx + bw - 3, 6):
        cv.rect(x, by + 4, 3, 6, "headboard_dark")
        cv.hline(x, by + 4, 3, "K")


def headboard_arch(cv):
    """둥근 지붕 머리판 + 기둥 (투룸)"""
    bx, by, bw = BED_X, BED_Y, BED_W
    cv.rect(bx, by + 2, bw, 10, "K")
    for x in range(bx + 4, bx + bw - 4):
        t = abs(x - (bx + bw / 2 - 0.5)) / (bw / 2 - 4)
        top = by + int(round(2 * t * t))
        cv.vline(x, top, by + 2 - top, "K")
        cv.vline(x, top + 1, by + 11 - top - 1, "headboard")
        cv.px(x, top + 1, "headboard_hi")
    cv.rect(bx + 1, by + 3, bw - 2, 8, "headboard")
    cv.hline(bx + 4, by + 3, bw - 8, "headboard")
    # 안쪽 판
    cv.rect(bx + 7, by + 4, bw - 14, 6, "headboard_dark")
    cv.rect(bx + 8, by + 5, bw - 16, 4, "headboard")
    cv.hline(bx + 8, by + 8, bw - 16, "headboard_hi")
    # 기둥 + 둥근 머리
    for px_ in (bx, bx + bw - 4):
        cv.box(px_, by + 1, 4, 11, "headboard", hi="headboard_hi")
        cv.rect(px_, by - 1, 4, 2, "K")
        cv.hline(px_ + 1, by, 2, "headboard_hi")


def headboard_fabric(cv):
    """단추 박은 천 머리판 (아파트)"""
    bx, by, bw = BED_X, BED_Y, BED_W
    cv.rect(bx + 2, by, bw - 4, 1, "K")
    cv.rect(bx + 1, by + 1, bw - 2, 1, "K")
    cv.rect(bx, by + 2, bw, 10, "K")
    cv.rect(bx + 2, by + 1, bw - 4, 1, "fabric_hi")
    cv.rect(bx + 1, by + 2, bw - 2, 9, "fabric")
    cv.hline(bx + 1, by + 2, bw - 2, "fabric_hi")
    for row, y in enumerate((by + 4, by + 8)):
        for x in range(bx + 4 + (3 if row else 0), bx + bw - 3, 6):
            cv.px(x, y, "fabric_dark")
            cv.px(x - 1, y - 1, "fabric_hi")
            cv.px(x + 1, y + 1, "fabric_dark")
    cv.vline(bx + bw - 2, by + 2, 9, "fabric_dark")


def draw_bed(cv, level):
    bx, by, bw = BED_X, BED_Y, BED_W
    {"one_room": headboard_slats, "two_room": headboard_arch, "apartment": headboard_fabric}[level](cv)
    # 매트리스 + 베개 둘
    my = by + 11
    cv.box(bx + 1, my, bw - 2, H - my + 1, "sheet", sh="pillow_shade")
    for px_ in (bx + 4, bx + 24):
        cv.grid(PILLOW, px_, my + 1)
    # 이불 (접힌 윗단)
    qy = my + 8
    cv.rect(bx + 1, qy, bw - 2, H - qy, "K")
    cv.rect(bx + 2, qy + 1, bw - 4, H - qy - 1, "BEDDING1")
    cv.rect(bx + 2, qy + 1, bw - 4, 3, "sheet")
    cv.hline(bx + 2, qy + 3, bw - 4, "pillow_shade")
    cv.hline(bx + 2, qy + 4, bw - 4, "K")
    cv.hline(bx + 2, qy + 5, bw - 4, "BEDDING0")
    if level == "one_room":
        # 누빔 이불 + 하트 쿠션
        for yy in range(qy + 9, H, 6):
            for xx in range(bx + 2, bx + bw - 2):
                if (xx - bx) % 2 == 0:
                    cv.px(xx, yy, "BEDDING3")
        for xx in range(bx + 8, bx + bw - 3, 9):
            for yy in range(qy + 6, H):
                if yy % 2 == 0:
                    cv.px(xx, yy, "BEDDING3")
        cv.vline(bx + 2, qy + 5, H - qy - 5, "BEDDING0")
        cv.vline(bx + bw - 3, qy + 5, H - qy - 5, "BEDDING2")
        heart = [
            ".KK.KK.",
            "KiiKi6K",
            "KiiiiiK",
            ".KIiIK.",
            "..KIK..",
            "...K...",
        ]
        cv.grid(heart, bx + 30, qy + 9)
    elif level == "two_room":
        # 체크무늬 이불 + 둥근 쿠션
        for xx in range(bx + 2, bx + bw - 2):
            if (xx - bx) % 8 in (5, 6):
                cv.vline(xx, qy + 6, H - qy - 6, "BEDDING2")
        for yy in range(qy + 6, H):
            if (yy - qy) % 8 in (2, 3):
                for xx in range(bx + 2, bx + bw - 2):
                    c = "BEDDING3" if (xx - bx) % 8 in (5, 6) else "BEDDING2"
                    cv.px(xx, yy, c)
        for yy in range(qy + 6, H):
            for xx in range(bx + 2, bx + bw - 2):
                if (xx - bx) % 8 == 1 and (yy - qy) % 8 == 6:
                    cv.px(xx, yy, "BEDDING0")
        cv.vline(bx + 2, qy + 5, H - qy - 5, "BEDDING0")
        cv.vline(bx + bw - 3, qy + 5, H - qy - 5, "BEDDING3")
        cushion = [
            "..KKKK..",
            ".KyyyyK.",
            "Ky2yyyYK",
            "KyyyYyYK",
            "KyyYyyYK",
            ".KYYYYK.",
            "..KKKK..",
        ]
        cv.grid(cushion, bx + 30, qy + 8)
    else:
        # 도톰한 이불 (접힌 주름) + 발치 베드 러너
        cv.vline(bx + 2, qy + 5, H - qy - 5, "BEDDING0")
        cv.vline(bx + bw - 3, qy + 5, H - qy - 5, "BEDDING2")
        for (x, y0, n) in [(bx + 11, qy + 7, 6), (bx + 22, qy + 9, 4), (bx + 33, qy + 6, 5)]:
            for k in range(n):
                cv.px(x + k // 2, y0 + k, "BEDDING2")
                cv.px(x + k // 2 - 1, y0 + k, "BEDDING0")
        ry = qy + 16
        cv.hline(bx + 2, ry, bw - 4, "K")
        cv.rect(bx + 2, ry + 1, bw - 4, 6, "BEDDING2")
        cv.hline(bx + 2, ry + 1, bw - 4, "BEDDING1")
        for xx in range(bx + 3, bx + bw - 3, 3):
            cv.px(xx, ry + 3, "BEDDING3")
            cv.px(xx + 1, ry + 4, "BEDDING3")
        cv.hline(bx + 2, ry + 6, bw - 4, "BEDDING3")
        cv.hline(bx + 2, ry + 7, bw - 4, "K")
        # 장식 쿠션 (베개 앞)
        cush = [
            ".KKKKKKKK.",
            "KfffffffFK",
            "KfvvvvvvFK",
            "KfvFvvFvFK",
            "KfvvvvvvFK",
            "KFFFFFFFFK",
            ".KKKKKKKK.",
        ]
        cv.grid(cush, bx + 18, my + 3)


def contact_shadows(cv):
    for (x, y, w) in [(5, 80, 44), (49, 80, 12), (62, 80, 40), (101, 80, 11), (112, 80, 44),
                      (122, 94, 18), (5, 132, 30), (35, 132, 13)]:
        cv.shade(x, y, w, 1, 0.72)
        cv.shade(x + 1, y + 1, w - 2, 1, 0.84, 10)


# ── 한 장으로 ─────────────────────────────────────────────

DRAW = {
    "one_room": (draw_wall_one_room, draw_window_one_room, draw_curtains_one_room, draw_wall_decor_one_room,
                 draw_floor_one_room, draw_rug_one_room),
    "two_room": (draw_wall_two_room, draw_window_two_room, draw_curtains_two_room, draw_wall_decor_two_room,
                 draw_floor_two_room, draw_rug_two_room),
    "apartment": (draw_wall_apartment, draw_window_apartment, draw_curtains_apartment, draw_wall_decor_apartment,
                  draw_floor_apartment, draw_rug_apartment),
}


def draw_level(level="one_room"):
    """바탕 그림을 그린 캔버스 (img, skyline)"""
    wall, window, curtains, decor, floor, rug = DRAW[level]
    cv = Canvas()
    wall(cv)
    window(cv)
    curtains(cv)
    decor(cv)
    floor(cv)
    draw_wardrobe(cv)
    draw_plant(cv)
    draw_shelf(cv)
    draw_lamp(cv)
    draw_desk(cv)
    draw_chair(cv)
    rug(cv)
    draw_shoe_rack(cv)
    draw_trash_can(cv)
    draw_bed(cv, level)
    contact_shadows(cv)
    return cv


def draw_base(level="one_room"):
    return draw_level(level).img


_cache = {}


def _base(level):
    """기본 표식 색으로 그린 바탕 (레이어 계산용)"""
    if level not in _cache:
        with markers(MARKERS):
            _cache[level] = draw_level(level)
    return _cache[level]


# ── 시간대 레이어 ───────────────────────────────────────────

def sky(phase, overcast=False):
    """창밖 하늘 띠 (세 단계 공용). [overcast]면 흐린 날 (비·눈·흐림·천둥·안개 — 비·눈 알갱이는 앱이 따로 뿌린다)"""
    gh = SKY_H
    cv = Canvas(SKY_W, gh)
    if overcast:
        stops = {
            "day": ["#8e9aab", "#a3aebc", "#b6bfcb", "#c9d0d9"],
            "sunset": ["#6f6a86", "#8d7f95", "#a8939c", "#bba6a3"],
            "night": ["#15182a", "#1b1f33", "#22273d", "#2a3047"],
        }[phase]
    else:
        stops = {
            "day": ["#7cc3f2", "#8fd0f7", "#a9dcfa", "#c8edff"],
            "sunset": ["#6a5aa8", "#c86f9a", "#f39a74", "#ffcf7a"],
            "night": ["#141a3a", "#1c2550", "#263266", "#2f3c78"],
        }[phase]
    band = gh / len(stops)
    for y in range(gh):
        i = min(len(stops) - 1, int(y / band))
        cv.hline(0, y, SKY_W, rgba(stops[i]))
        # 띠 경계를 디더로 섞는다
        if i + 1 < len(stops) and y >= (i + 1) * band - 2:
            for x in range(SKY_W):
                if dot(x, y, 6):
                    cv.px(x, y, rgba(stops[i + 1]))
    if overcast:
        # 두꺼운 회색 구름 띠
        cloud = {"day": ("#d5dbe3", "#aab4c1"), "sunset": ("#b7a7ad", "#8f8090"), "night": ("#2f354c", "#23283b")}[phase]
        for (x, y, w) in [(0, 1, 14), (12, 4, 16), (26, 0, 14), (38, 5, 18), (52, 2, 12), (6, 10, 12), (30, 12, 14), (48, 11, 14)]:
            cv.rect(x + 2, y, w - 4, 1, rgba(cloud[0]))
            cv.rect(x, y + 1, w, 3, rgba(cloud[0]))
            cv.hline(x + 1, y + 3, w - 2, rgba(cloud[1]))
        return cv.img
    if phase == "night":
        for (x, y) in [(3, 2), (11, 6), (19, 1), (27, 9), (36, 3), (44, 7), (52, 2), (59, 11), (8, 13), (31, 15), (48, 14)]:
            cv.px(x, y, rgba("#fff6c8"))
        for (x, y) in [(15, 4), (40, 11), (56, 6)]:
            cv.px(x, y, rgba("#fff6c8"))
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                cv.px(x + dx, y + dy, rgba("#9aa4d6"))
        moon = [
            "..yyy.",
            ".yy2..",
            "yy2...",
            "yy2...",
            ".yy2..",
            "..yyy.",
        ]
        cv.grid(moon, 22, 3)
    else:
        cloud_c = "#ffffff" if phase == "day" else "#ffd9c4"
        shade_c = "#e3f2fc" if phase == "day" else "#f2a98f"
        for (x, y, w) in [(4, 4, 10), (30, 11, 8), (46, 3, 12)]:
            cv.rect(x + 2, y, w - 4, 1, rgba(cloud_c))
            cv.rect(x + 1, y + 1, w - 2, 1, rgba(cloud_c))
            cv.rect(x, y + 2, w, 2, rgba(cloud_c))
            cv.hline(x + 1, y + 3, w - 2, rgba(shade_c))
        if phase == "sunset":
            sun = [
                "..aaa..",
                ".a777a.",
                "a77777a",
                "a77777a",
            ]
            cv.grid(sun, 38, gh - 4)
    return cv.img


def sunlight(phase, level="one_room"):
    """창으로 들어온 햇빛이 책장 앞 바닥에 닿은 자리. 유리 칸만 빛이 지나가서 창살·난간 그림자가 진다.
    창 아래쪽이 벽 가까이(위), 창 위쪽이 멀리(아래) 비친다. 바닥·러그에만 칠한다."""
    base = _base(level)
    gx, gy, gw, gh = GLASS_BY_LEVEL[level]
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if phase == "day":
        top, bot, skew, a = 82, 114, 16, (255, 240, 200, 60)
    else:
        top, bot, skew, a = 82, 104, 40, (255, 170, 90, 70)
    x0 = gx + 1
    x1 = gx + 1 + round(gw * 30 / 26)
    floorish = {rgba(c)[:3] for k in ("FLOOR", "RUG") for c in MARKERS[k]} | {col("rug_fringe")[:3]}
    for y in range(top, bot + 1):
        s = (y - top) / (bot - top)
        for x in range(W):
            u = (x - x0 - s * skew) / (x1 - x0)
            if not 0 <= u < 1:
                continue
            sx = gx + min(gw - 1, int(u * gw))
            sy = gy + gh - 1 - min(gh - 1, int(s * gh))
            if base.get(sx, sy)[3] == 255 and (sx, sy) not in base.skyline:
                continue
            if base.get(x, y)[:3] not in floorish:
                continue
            layer.putpixel((x, y), a)
    return layer


def night_shade(level="one_room"):
    """밤: 전체를 남색으로 덮되 불빛 주변은 밝게. 둥근 띠 모양으로 단계를 끊는다 (도트 조명).
    비어 있는 유리 칸은 그대로 두고 (하늘이 보이게), 창밖 건물은 더 어둡게."""
    base = _base(level)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    color = (16, 20, 66)
    sources = NIGHT_SOURCES[level]
    for y in range(H):
        for x in range(W):
            if base.get(x, y)[3] < 255:
                continue
            if (x, y) in base.skyline:
                layer.putpixel((x, y), color + (210,))
                continue
            t = 1.0
            for (lx, ly, r) in sources:
                dist = ((x - lx) ** 2 + ((y - ly) * 1.3) ** 2) ** 0.5
                t = min(t, dist / r)
            if level == "apartment" and y >= 7:
                t = min(t, 0.5 + 0.5 * min(1.0, (y - 7) / 26))  # 천장 간접조명
            a = 30 + 130 * t
            step = 20
            q = int(a // step) * step
            # 띠 경계 2픽셀만 디더로 섞는다
            frac = (a - q) / step
            if frac > 0.8 and dot(x, y, 8):
                q += step
            layer.putpixel((x, y), color + (int(min(150, q)),))
    return layer


def _glow(layer, box, rgba_):
    g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(g).ellipse(box, fill=rgba_)
    return Image.alpha_composite(layer, g)


def night_lights(level="one_room"):
    """밤에만 켜지는 불빛: 스탠드 갓 + 단계마다 전구줄 / 벽등 / 펜던트·간접조명·창밖 불빛"""
    lx, ly = LAMP_CENTER
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img = _glow(img, [lx - 14, ly - 12, lx + 14, ly + 14], (255, 220, 140, 26))
    img = _glow(img, [lx - 9, ly - 8, lx + 9, ly + 9], (255, 225, 150, 34))
    if level == "two_room":
        sx, sy = SCONCE[0] + 4, SCONCE[1] + 4
        img = _glow(img, [sx - 12, sy - 10, sx + 12, sy + 14], (255, 220, 140, 24))
        img = _glow(img, [sx - 7, sy - 6, sx + 7, sy + 8], (255, 225, 150, 32))
    if level == "apartment":
        px_, py_ = PENDANT[0] + 5, PENDANT[1] + 17
        cone = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(cone).polygon([(px_ - 4, py_ + 1), (px_ + 4, py_ + 1), (px_ + 14, 59), (px_ - 14, 59)],
                                     fill=(255, 220, 140, 22))
        img = Image.alpha_composite(img, cone)
        img = _glow(img, [px_ - 8, py_ - 4, px_ + 8, py_ + 8], (255, 225, 150, 34))
        # 천장 간접조명: 몰딩 아래로 번지는 빛
        band = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for y, al in ((6, 120), (7, 70), (8, 44), (9, 30), (10, 20), (11, 12)):
            ImageDraw.Draw(band).line([(0, y), (W - 1, y)], fill=(255, 214, 140, al))
        img = Image.alpha_composite(img, band)
    cv = Canvas()
    cv.img = img
    cv.d = ImageDraw.Draw(cv.img)
    draw_lamp(cv, lit=True)
    for (bx, by, c) in bulbs(level):
        cv.px(bx, by, c)
        cv.px(bx, by + 1, c)
        for dx, dy in [(-1, 0), (1, 0), (-1, 1), (1, 1), (0, -1), (0, 2)]:
            cv.px(bx + dx, by + dy, col(c)[:3] + (90,))
    if level == "two_room":
        draw_sconce(cv, lit=True)
        cv.px(10 + 27, 5 + 3, "q")  # 에어컨 불빛
    if level == "apartment":
        draw_pendant(cv, lit=True)
        # 창밖 건물에 켜진 불
        for i, (x, y, b) in enumerate(city_windows()):
            if (x * 7 + y * 3 + b) % 5 in (0, 2):
                cv.px(x, y, "y" if (x + y) % 3 else "7")
        gx, gy, gw, gh = GLASS_BY_LEVEL[level]
        tall = max(CITY_FAR, key=lambda b: b[2])
        cv.px(gx + tall[0] + tall[1] // 2, gy + gh - tall[2], "r")
    return cv.img


def phase_of_hour(h):
    if 6 <= h < 17:
        return "day"
    if 17 <= h < 19:
        return "sunset"
    return "night"
