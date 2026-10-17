"""
원룸 장면. 바탕 그림 한 장 + 시간대 레이어(하늘, 햇빛, 밤 조명)로 나눠 그린다.

앱은 이 순서로 겹친다:
    하늘(흐름) → 방 바탕(유리창은 비어 있음) → 햇빛 → 물건·캐릭터 → 밤 그늘 → 밤 불빛
"""
from PIL import Image, ImageDraw

from palette import PALETTE, ROOM

W, H = 160, 136
FLOOR_Y = 60

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

CHAR_W, CHAR_H = 22, 28
# 캐릭터 발이 러그 가운데에 오게 (왼쪽 위 좌표)
CHARACTER_POS = (73, 78)
# 걸어 다니는 범위 (왼쪽 위 x)
WALK_X = (58, 90)

FLOOR_PILE = [
    (76, 122), (88, 122), (64, 122), (100, 122), (52, 122),
    (70, 110), (94, 110), (82, 116), (62, 116), (98, 116), (56, 110), (106, 110),
]
TRASH = [(46, 123), (41, 107), (49, 113), (36, 95), (52, 100), (44, 90)]

# 유리창 (하늘이 비치는 곳)
GLASS = (69, 9, 26, 20)
SKY_W = 64

# 시간대
PHASES = ("day", "sunset", "night")


def rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def col(name):
    return rgba(PALETTE[name] if name in PALETTE else ROOM[name])


BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dot(x, y, level):
    """4×4 바이어 디더: level 0~16 중 몇 칸을 칠할지"""
    return BAYER[y % 4][x % 4] < level


class Canvas:
    def __init__(self, w=W, h=H):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def inside(self, x, y):
        return 0 <= x < self.img.width and 0 <= y < self.img.height

    def rect(self, x, y, w, h, c):
        if w > 0 and h > 0:
            self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=c if isinstance(c, tuple) else col(c))

    def px(self, x, y, c):
        if self.inside(x, y):
            self.img.putpixel((x, y), c if isinstance(c, tuple) else col(c))

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
        """이미 칠한 픽셀을 어둡게 (그늘·접지 그림자)"""
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if self.inside(xx, yy) and dot(xx, yy, level):
                    r, g, b, a = self.img.getpixel((xx, yy))
                    if a:
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

# 전구줄: (시작x, 끝x, 걸린 높이, 처짐)
GARLANDS = [(108, 159, 3, 5), (2, 52, 3, 4)]
BULB_COLORS = ["y", "r", "q", "f", "i"]


def garland_points(x0, x1, y0, sag):
    pts = []
    n = x1 - x0
    for i in range(n + 1):
        t = i / n
        pts.append((x0 + i, y0 + round(sag * 4 * t * (1 - t))))
    return pts


def bulbs():
    out = []
    for gi, (x0, x1, y0, sag) in enumerate(GARLANDS):
        pts = garland_points(x0, x1, y0, sag)
        for i in range(3, len(pts) - 2, 7):
            x, y = pts[i]
            out.append((x, y + 1, BULB_COLORS[(i // 7 + gi) % len(BULB_COLORS)]))
    return out


def draw_wall(cv):
    cv.rect(0, 0, W, FLOOR_Y - 4, "wall")
    # 세로 줄무늬 벽지 + 작은 꽃점
    for x in range(4, W, 10):
        cv.vline(x, 4, 38, "wall_stripe")
        cv.vline(x + 1, 4, 38, "wall_stripe")
    for y in range(10, 40, 10):
        for x in range(9 if (y // 10) % 2 else 4, W, 10):
            cv.px(x - 1, y, "wall_dot")
            cv.px(x + 1, y, "wall_dot")
            cv.px(x, y - 1, "wall_dot")
            cv.px(x, y + 1, "wall_dot")
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
    # 양쪽 구석 그늘
    cv.shade(0, 0, 3, 56, 0.93, 16)
    cv.shade(3, 0, 2, 56, 0.95, 8)
    cv.shade(W - 3, 0, 3, 56, 0.93, 16)
    cv.shade(W - 5, 0, 2, 56, 0.95, 8)


def draw_floor(cv):
    plank = 7
    tones = ["plank_1", "plank_2", "plank_3"]
    for i, y in enumerate(range(FLOOR_Y, H, plank)):
        off = (i * 23) % 37
        xs = [0] + list(range(off, W, 37)) + [W]
        xs = sorted(set(xs))
        for j in range(len(xs) - 1):
            x0, x1 = xs[j], xs[j + 1]
            tone = tones[(i * 5 + j * 7) % 3]
            cv.rect(x0, y, x1 - x0, plank, tone)
            cv.hline(x0, y, x1 - x0, "floor_hi")
            # 나뭇결
            gy = y + 2 + (i + j) % 3
            gx = x0 + 4 + (i * 3 + j * 5) % 9
            cv.hline(gx, gy, 6 + (i + j) % 5, "floor_b" if tone != "plank_3" else "floor_shadow")
            if (i * 7 + j * 3) % 5 == 0:
                kx = x0 + 10 + (i * 11) % 14
                cv.px(kx, y + 3, "knot")
                cv.px(kx + 1, y + 3, "knot")
            if x0 > 0:
                cv.vline(x0, y, plank - 1, "floor_seam")
        cv.hline(0, y + plank - 1, W, "floor_seam")
    # 벽 쪽 그늘 + 아래쪽 어둡게
    cv.shade(0, FLOOR_Y, W, 1, 0.78)
    cv.shade(0, FLOOR_Y + 1, W, 1, 0.86, 12)
    cv.shade(0, FLOOR_Y + 2, W, 1, 0.9, 8)
    cv.shade(0, FLOOR_Y + 3, W, 1, 0.92, 4)
    cv.shade(0, H - 6, W, 3, 0.94, 6)
    cv.shade(0, H - 3, W, 3, 0.92, 10)


def draw_window(cv):
    gx, gy, gw, gh = GLASS
    # 틀
    cv.box(gx - 3, gy - 3, gw + 6, gh + 6, "frame", sh="frame_shade")
    cv.hline(gx - 2, gy - 2, gw + 4, "frame_hi")
    cv.clear(gx, gy, gw, gh)
    # 창살
    cv.rect(gx + 12, gy, 2, gh, "frame")
    cv.vline(gx + 13, gy, gh, "frame_shade")
    cv.rect(gx, gy + 9, gw, 2, "frame")
    cv.hline(gx, gy + 10, gw, "frame_shade")
    # 유리 반사 (반투명)
    for (sx, sy, n) in [(gx + 2, gy + 6, 5), (gx + 4, gy + 6, 3), (gx + 16, gy + 17, 5), (gx + 18, gy + 17, 3)]:
        for k in range(n):
            cv.px(sx + k, sy - k, (255, 255, 255, 110))
    # 창턱 + 작은 선인장
    cv.box(gx - 6, gy + gh + 2, gw + 12, 4, "l", hi="L", sh="o")
    cv.grid(CACTUS, gx + 19, gy + gh - 6)


def draw_curtains(cv):
    gx, gy, gw, gh = GLASS
    # 봉
    cv.rect(56, 3, 52, 2, "O")
    cv.hline(56, 3, 52, "l")
    cv.box(54, 2, 3, 4, "o", hi="L")
    cv.box(107, 2, 3, 4, "o", hi="L")
    for x in range(59, 106, 4):
        cv.px(x, 5, "metal_dark")
    for (x, right) in [(57, False), (97, True)]:
        w = 10
        cv.rect(x, 5, w, 33, "K")
        cv.rect(x + 1, 5, w - 2, 32, "curtain")
        # 주름: 밝은 면 / 그늘
        for fx in (1, 4, 7):
            cv.vline(x + fx, 5, 32, "curtain_hi")
            cv.vline(x + fx + 1, 5, 32, "curtain")
            cv.vline(x + fx + 2, 5, 32, "curtain_dark")
        # 묶은 끈 (끈 아래로 살짝 퍼짐)
        cv.rect(x, 23, w, 3, "K")
        cv.rect(x + 1, 24, w - 2, 1, "y")
        cv.px(x + (w - 2 if right else 1), 24, "Y")
        # 아랫단 물결
        for k in range(1, w - 1):
            if k % 3 == 1:
                cv.px(x + k, 37, (0, 0, 0, 0))
                cv.px(x + k, 36, "K")
        cv.shade(x + 1, 5, w - 2, 2, 0.85)


def draw_wall_decor(cv):
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
    clock = [
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
    cv.grid(clock, cx, cy)
    cv.px(cx + 5, cy + 2, "r")
    # 전구줄 (낮에는 꺼진 색)
    for (x0, x1, y0, sag) in GARLANDS:
        for (px_, py_) in garland_points(x0, x1, y0, sag):
            cv.px(px_, py_, "D")
    for (bx, by, c) in bulbs():
        cv.px(bx, by, c)
        cv.px(bx, by + 1, c)
        cv.px(bx, by + 2, "K")


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


def draw_rug(cv):
    rx, ry, rw, rh = 52, 94, 64, 28
    # 타원 러그 (모서리 깎기)
    rows = []
    for yy in range(rh):
        t = abs(yy - (rh - 1) / 2) / ((rh - 1) / 2)
        inset = int(round(10 * (1 - (1 - t * t) ** 0.5)))
        rows.append(inset)
    for yy, inset in enumerate(rows):
        cv.hline(rx + inset, ry + yy, rw - 2 * inset, "rug_dark")
    for yy, inset in enumerate(rows[1:-1], start=1):
        cv.hline(rx + inset + 1, ry + yy, rw - 2 * inset - 2, "rug")
    for yy, inset in enumerate(rows[3:-3], start=3):
        cv.px(rx + inset + 3, ry + yy, "rug_pattern")
        cv.px(rx + rw - inset - 4, ry + yy, "rug_pattern")
    for yy in (ry + 3, ry + rh - 4):
        inset = rows[yy - ry]
        for xx in range(rx + inset + 4, rx + rw - inset - 4, 3):
            cv.px(xx, yy, "rug_pattern")
    # 가운데 무늬
    cx, cy = rx + rw // 2, ry + rh // 2
    for (dx, dy) in [(0, -4), (-1, -3), (1, -3), (-2, -2), (2, -2), (-3, -1), (3, -1), (-4, 0), (4, 0),
                     (-3, 1), (3, 1), (-2, 2), (2, 2), (-1, 3), (1, 3), (0, 4)]:
        cv.px(cx + dx, cy + dy, "rug_dark")
    cv.px(cx, cy, "rug_pattern")
    # 술 (양 끝)
    for yy in range(ry + 8, ry + rh - 8, 2):
        cv.hline(rx - 2, yy, 2, "rug_fringe")
        cv.hline(rx + rw, yy, 2, "rug_fringe")
    cv.shade(rx + 8, ry + rh - 2, rw - 16, 1, 0.85, 8)


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


def draw_bed(cv):
    bx, by = 114, 94
    head = [
        "...KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK...",
    ]
    bw = 46
    # 머리판 (둥근 위)
    cv.rect(bx + 3, by, bw - 6, 1, "K")
    cv.rect(bx + 1, by + 1, bw - 2, 1, "K")
    cv.rect(bx, by + 2, bw, 10, "K")
    cv.rect(bx + 3, by + 1, bw - 6, 1, "headboard_hi")
    cv.rect(bx + 1, by + 2, bw - 2, 9, "headboard")
    cv.hline(bx + 1, by + 2, bw - 2, "headboard_hi")
    for x in range(bx + 5, bx + bw - 3, 6):
        cv.rect(x, by + 4, 3, 6, "headboard_dark")
        cv.hline(x, by + 4, 3, "K")
    # 매트리스 + 이불
    my = by + 11
    cv.box(bx + 1, my, bw - 2, H - my + 1, "sheet", sh="pillow_shade")
    # 베개 둘
    for px_ in (bx + 4, bx + 24):
        pil = [
            ".KKKKKKKKKKKKKKK.",
            "KWWWWWWWWWWWWWWwK",
            "KWwwwwwwwwwwwwwgK",
            "KWwwwwwwwwwwwwwgK",
            "KwwwwwwwwwwwwwggK",
            ".KKKKKKKKKKKKKKK.",
        ]
        cv.grid(pil, px_, my + 1)
    # 이불 (접힌 윗단 + 퀼트)
    qy = my + 8
    cv.rect(bx + 1, qy, bw - 2, H - qy, "K")
    cv.rect(bx + 2, qy + 1, bw - 4, H - qy - 1, "quilt")
    cv.rect(bx + 2, qy + 1, bw - 4, 3, "sheet")
    cv.hline(bx + 2, qy + 3, bw - 4, "pillow_shade")
    cv.hline(bx + 2, qy + 4, bw - 4, "K")
    cv.hline(bx + 2, qy + 5, bw - 4, "quilt_hi")
    for yy in range(qy + 9, H, 6):
        for xx in range(bx + 2, bx + bw - 2):
            if (xx - bx) % 2 == 0:
                cv.px(xx, yy, "quilt_stitch")
    for xx in range(bx + 8, bx + bw - 3, 9):
        for yy in range(qy + 6, H):
            if yy % 2 == 0:
                cv.px(xx, yy, "quilt_stitch")
    cv.vline(bx + 2, qy + 5, H - qy - 5, "quilt_hi")
    cv.vline(bx + bw - 3, qy + 5, H - qy - 5, "quilt_dark")
    # 이불 위 작은 하트 쿠션
    heart = [
        ".KK.KK.",
        "KiiKi6K",
        "KiiiiiK",
        ".KIiIK.",
        "..KIK..",
        "...K...",
    ]
    cv.grid(heart, bx + 30, qy + 9)


def contact_shadows(cv):
    for (x, y, w) in [(5, 80, 44), (49, 80, 12), (62, 80, 40), (101, 80, 11), (112, 80, 44),
                      (122, 94, 18), (5, 132, 30), (35, 132, 13)]:
        cv.shade(x, y, w, 1, 0.72)
        cv.shade(x + 1, y + 1, w - 2, 1, 0.84, 10)


def draw_base():
    cv = Canvas()
    draw_wall(cv)
    draw_window(cv)
    draw_curtains(cv)
    draw_wall_decor(cv)
    draw_floor(cv)
    draw_wardrobe(cv)
    draw_plant(cv)
    draw_shelf(cv)
    draw_lamp(cv)
    draw_desk(cv)
    draw_chair(cv)
    draw_rug(cv)
    draw_shoe_rack(cv)
    draw_trash_can(cv)
    draw_bed(cv)
    contact_shadows(cv)
    return cv.img


# ── 시간대 레이어 ───────────────────────────────────────────

def sky(phase, overcast=False):
    """창밖 하늘 띠. [overcast]면 흐린 날 (비·눈·흐림·천둥·안개 — 비·눈 알갱이는 앱이 따로 뿌린다)"""
    gx, gy, gw, gh = GLASS
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


def sunlight(phase):
    """창으로 들어온 햇빛이 책장 앞 바닥에 닿은 자리 (창살 그림자 포함)"""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if phase == "day":
        top, bot, skew, a = 82, 114, 16, (255, 240, 200, 60)
    else:
        top, bot, skew, a = 82, 104, 40, (255, 170, 90, 70)
    x0, x1 = 70, 100
    def at(y):
        return (y - top) / (bot - top) * skew
    d.polygon([(x0, top), (x1, top), (x1 + skew, bot), (x0 + skew, bot)], fill=a)
    # 창살 그림자: 세로 하나, 가로 하나
    mid = (x0 + x1) // 2
    d.polygon([(mid - 1, top), (mid + 1, top), (mid + 1 + skew, bot), (mid - 1 + skew, bot)], fill=(0, 0, 0, 0))
    my = (top + bot) // 2
    d.rectangle([x0 + int(at(my)), my, x1 + int(at(my + 1)), my + 1], fill=(0, 0, 0, 0))
    return layer


LAMP_CENTER = (107, 44)


def night_shade():
    """밤: 전체를 남색으로 덮되 스탠드 주변은 밝게. 둥근 띠 모양으로 단계를 끊는다 (도트 조명)."""
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    base = (16, 20, 66)
    lx, ly = LAMP_CENTER
    gx, gy, gw, gh = GLASS
    for y in range(H):
        for x in range(W):
            if gx <= x < gx + gw and gy <= y < gy + gh:
                continue
            dist = ((x - lx) ** 2 + ((y - ly) * 1.3) ** 2) ** 0.5
            t = min(1.0, dist / 80)
            a = 30 + 130 * t
            step = 20
            q = int(a // step) * step
            # 띠 경계 2픽셀만 디더로 섞는다
            frac = (a - q) / step
            if frac > 0.8 and dot(x, y, 8):
                q += step
            layer.putpixel((x, y), base + (int(min(150, q)),))
    return layer


def night_lights():
    """밤에만 켜지는 불빛: 스탠드 갓, 전구줄"""
    cv = Canvas()
    draw_lamp(cv, lit=True)
    # 스탠드 둘레 빛무리
    lx, ly = LAMP_CENTER
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([lx - 14, ly - 12, lx + 14, ly + 14], fill=(255, 220, 140, 26))
    gd.ellipse([lx - 9, ly - 8, lx + 9, ly + 9], fill=(255, 225, 150, 34))
    out = Image.alpha_composite(glow, cv.img)
    cv.img = out
    cv.d = ImageDraw.Draw(cv.img)
    for (bx, by, c) in bulbs():
        cv.px(bx, by, c)
        cv.px(bx, by + 1, c)
        for dx, dy in [(-1, 0), (1, 0), (-1, 1), (1, 1), (0, -1), (0, 2)]:
            cv.px(bx + dx, by + dy, col(c)[:3] + (90,))
    return cv.img


def phase_of_hour(h):
    if 6 <= h < 17:
        return "day"
    if 17 <= h < 19:
        return "sunset"
    return "night"
