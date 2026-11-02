"""
반려동물 용품 16×16 추가분 (pet16 과 같은 규칙, 같은 팔레트). 모양을 코드로 그려 글자 격자로 만든다 —
채우기 → 바깥 외곽선(K) 자동 → 손으로 빛·무늬 얹기.
"""

def _new():
    return [['.'] * 16 for _ in range(16)]


def rect(g, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < 16 and 0 <= y < 16:
                g[y][x] = ch


def ell(g, x0, y0, x1, y1, ch):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    rx, ry = (x1 - x0) / 2 + 0.5, (y1 - y0) / 2 + 0.5
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0 and 0 <= x < 16 and 0 <= y < 16:
                g[y][x] = ch


def px(g, ch, *pts):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            g[y][x] = ch


def line(g, x0, y0, x1, y1, ch, w=1):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        rect(g, x, y, x + w - 1, y + w - 1, ch)


def done(g):
    """바깥에 외곽선 K 를 두르고 16줄 문자열로"""
    out = [r[:] for r in g]
    for y in range(16):
        for x in range(16):
            if g[y][x] == '.':
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and g[ny][nx] not in '.':
                        out[y][x] = 'K'
                        break
    return [''.join(r) for r in out]


def pouch():  # 습식 파우치 (연어)
    g = _new()
    rect(g, 4, 3, 11, 13, 't')
    rect(g, 4, 3, 11, 4, 'g')
    px(g, 'G', *[(x, 4) for x in range(4, 12, 2)])
    rect(g, 4, 12, 11, 13, 'T')
    ell(g, 6, 6, 9, 10, 'W')
    px(g, 'a', (6, 8), (7, 7), (8, 7), (9, 8), (8, 9), (7, 9))
    px(g, 'K', (7, 8))
    rect(g, 4, 5, 4, 11, 'v')
    return done(g)


def dental():  # 덴탈 스틱 세 개
    g = _new()
    line(g, 2, 3, 12, 13, 'q', 3)
    line(g, 4, 2, 13, 11, 'q', 2)
    line(g, 2, 7, 8, 13, 'q', 2)
    line(g, 3, 3, 11, 12, 'Q', 1)
    px(g, '3', (3, 3), (5, 3), (3, 7), (4, 8))
    px(g, 'W', (5, 6), (8, 9), (11, 11), (7, 5), (10, 6))
    return done(g)


def bigbag():  # 프리미엄 사료 5kg
    g = _new()
    rect(g, 2, 2, 13, 14, 'Y')
    rect(g, 2, 2, 13, 3, 'g')
    rect(g, 2, 13, 13, 14, 'E')
    rect(g, 4, 5, 11, 10, 'w')
    rect(g, 3, 4, 3, 12, 'y')
    px(g, 'r', *[(x, 11) for x in range(4, 12)])
    ell(g, 6, 6, 9, 9, 'o')
    px(g, 'W', (7, 6), (6, 7))
    px(g, 'K', (6, 5), (9, 5), (7, 10), (8, 10))
    return done(g)


def duck():  # 오리 인형
    g = _new()
    ell(g, 3, 8, 12, 14, 'y')
    ell(g, 5, 3, 11, 9, 'y')
    px(g, 'a', (11, 6), (12, 6), (13, 6), (11, 7), (12, 7))
    px(g, 'K', (8, 5))
    px(g, '2', (6, 4), (7, 4), (4, 10), (5, 9))
    px(g, 'Y', *[(x, 14) for x in range(4, 12)], (12, 12), (12, 11), (3, 12))
    px(g, 'b', (8, 7))
    return done(g)


def tunnel():  # 고양이 터널
    g = _new()
    rect(g, 3, 4, 13, 12, 'u')
    ell(g, 1, 4, 7, 12, 'U')
    ell(g, 2, 5, 6, 11, 'B')
    for x in (9, 11):
        rect(g, x, 4, x, 12, '5')
    px(g, 'W', (4, 5))
    rect(g, 3, 12, 13, 12, 'U')
    return done(g)


def disc():  # 원반
    g = _new()
    ell(g, 1, 5, 14, 12, 'c')
    ell(g, 3, 6, 12, 10, 'v')
    ell(g, 5, 7, 10, 9, 'c')
    px(g, 'W', (4, 7), (5, 6), (7, 5))
    px(g, 'C', *[(x, 12) for x in range(5, 12)])
    return done(g)


def nosemat():  # 노즈워크 매트
    g = _new()
    rect(g, 2, 3, 13, 12, 'q')
    rect(g, 2, 11, 13, 12, 'Q')
    for x in range(3, 13, 2):
        for y in range(4, 11, 3):
            px(g, 'i' if (x + y) % 4 == 0 else 'y', (x, y), (x, y + 1))
    px(g, 'o', (4, 5), (9, 8), (6, 9), (11, 5))
    return done(g)


def hideout():  # 숨숨집(텐트)
    g = _new()
    for y in range(2, 14):
        half = (y - 2) * 6 // 11 + 1
        rect(g, 8 - half - 1, y, 7 + half + 1, y, 'x')
    for y in range(2, 14):
        half = (y - 2) * 6 // 11 + 1
        px(g, 'X', (7 + half + 1, y), (7 + half, y))
    rect(g, 2, 13, 13, 13, 'X')
    ell(g, 5, 8, 10, 14, 'B')
    rect(g, 5, 11, 10, 13, 'B')
    px(g, 'i', (8, 2), (8, 1))
    px(g, 'W', (7, 3))
    return done(g)


def coolmat():  # 쿨매트
    g = _new()
    rect(g, 1, 6, 14, 12, 'f')
    rect(g, 1, 11, 14, 12, 'F')
    for x in range(3, 14, 3):
        rect(g, x, 6, x, 11, 'F')
    px(g, 'W', (2, 7), (3, 7), (6, 8), (9, 7), (12, 9), (13, 8))
    px(g, 'v', *[(x, 6) for x in range(2, 14)])
    return done(g)


def litterbox():  # 고양이 화장실
    g = _new()
    ell(g, 2, 2, 13, 9, 'w')
    rect(g, 2, 6, 13, 13, 'w')
    rect(g, 2, 12, 13, 13, 'D')
    rect(g, 3, 10, 12, 11, 'g')
    ell(g, 5, 7, 10, 12, 'B')
    rect(g, 5, 10, 10, 11, 'B')
    px(g, 'W', (4, 4), (5, 3), (6, 3))
    px(g, 'G', (12, 7), (12, 8), (12, 9))
    return done(g)


def bowls():  # 밥그릇 두 개
    g = _new()
    for x0, fill in ((0, 'o'), (8, 'F')):
        ell(g, x0, 5, x0 + 7, 8, fill)
        rect(g, x0 + 1, 8, x0 + 6, 12, 'g')
        rect(g, x0, 7, x0 + 7, 8, 'g')
        ell(g, x0, 5, x0 + 7, 7, 'G')
        ell(g, x0 + 1, 5, x0 + 6, 7, fill)
        rect(g, x0 + 1, 12, x0 + 6, 13, 'G')
        px(g, 'W', (x0 + 2, 9), (x0 + 2, 10))
    px(g, 'O', (2, 6), (4, 6), (5, 5), (3, 5))
    px(g, 'f', (10, 6), (11, 6))
    return done(g)


def brush():  # 빗
    g = _new()
    rect(g, 3, 2, 12, 7, 'i')
    rect(g, 3, 6, 12, 7, 'I')
    for x in range(4, 12, 2):
        px(g, 'W', (x, 3))
        px(g, 'D', (x, 8), (x, 9))
    rect(g, 6, 8, 9, 14, 'L')
    rect(g, 6, 8, 6, 14, 'x')
    rect(g, 9, 8, 9, 14, 'o')
    return done(g)


def shampoo():  # 샴푸 + 거품
    g = _new()
    rect(g, 4, 5, 9, 14, 'f')
    rect(g, 4, 12, 9, 14, 'F')
    rect(g, 5, 3, 8, 4, 'r')
    rect(g, 6, 2, 7, 2, 'R')
    rect(g, 5, 7, 8, 10, 'W')
    px(g, 'c', (6, 8), (7, 8), (6, 9), (7, 9))
    ell(g, 11, 5, 13, 7, 'W')
    ell(g, 12, 9, 14, 11, 'f')
    ell(g, 10, 11, 12, 13, 'W')
    px(g, 'v', (11, 5), (12, 9), (10, 11))
    return done(g)


def clipper():  # 발톱깎이
    g = _new()
    line(g, 2, 13, 11, 4, 'r', 2)
    line(g, 4, 14, 13, 6, 'R', 2)
    rect(g, 10, 2, 13, 5, 'g')
    px(g, 'G', (13, 3), (13, 4), (12, 5))
    px(g, 'W', (11, 2), (11, 3))
    px(g, 'Y', (8, 8), (9, 7))
    return done(g)


def leash():  # 리드줄
    g = _new()
    ell(g, 1, 1, 8, 8, 'r')
    ell(g, 3, 3, 6, 6, '.')
    px(g, 'R', (2, 7), (3, 8), (4, 8), (5, 8), (7, 7))
    line(g, 7, 7, 12, 12, 'r', 2)
    rect(g, 11, 11, 14, 14, 'Y')
    rect(g, 12, 12, 13, 13, '.')
    px(g, 'y', (11, 11))
    return done(g)


def bagroll():  # 배변봉투 롤
    g = _new()
    ell(g, 2, 2, 13, 13, 'q')
    ell(g, 4, 4, 11, 11, 'Q')
    ell(g, 6, 6, 9, 9, 'w')
    px(g, 'K', (7, 7), (8, 7), (7, 8), (8, 8))
    px(g, '3', (4, 3), (5, 3), (3, 5), (3, 6))
    rect(g, 11, 11, 14, 13, 'q')
    px(g, 'Q', (14, 11), (14, 12), (14, 13))
    return done(g)


def nametag():  # 이름표 목걸이
    g = _new()
    ell(g, 1, 1, 14, 9, 'c')
    ell(g, 3, 3, 12, 7, '.')
    px(g, 'C', (3, 8), (4, 9), (5, 9), (10, 9), (11, 9), (12, 8), (13, 7))
    px(g, 'v', (3, 2), (4, 1), (6, 1))
    # 뼈 모양 이름표
    rect(g, 5, 10, 10, 13, 'y')
    px(g, 'y', (4, 10), (4, 13), (11, 10), (11, 13))
    px(g, 'Y', (5, 13), (6, 13), (7, 13), (8, 13), (9, 13), (10, 13))
    px(g, 'K', (7, 11), (8, 11), (7, 12), (8, 12))
    px(g, 'Y', (7, 9), (8, 9))
    return done(g)


def raincoat():  # 우비
    g = _new()
    rect(g, 4, 5, 11, 14, 'y')
    ell(g, 4, 2, 11, 8, 'y')
    rect(g, 2, 6, 3, 11, 'y')
    rect(g, 12, 6, 13, 11, 'y')
    ell(g, 6, 4, 9, 7, 'Y')
    rect(g, 4, 13, 11, 14, 'Y')
    rect(g, 7, 8, 8, 14, '2')
    px(g, 'K', (7, 9), (8, 9), (7, 11), (8, 11))
    px(g, '2', (5, 3), (6, 2))
    return done(g)


def sweater():  # 스웨터
    g = _new()
    rect(g, 4, 4, 11, 13, 'r')
    rect(g, 1, 5, 3, 9, 'r')
    rect(g, 12, 5, 14, 9, 'r')
    rect(g, 6, 3, 9, 4, 'w')
    for y in (7, 10):
        rect(g, 4, y, 11, y, 'W')
    rect(g, 4, 13, 11, 13, 'R')
    rect(g, 1, 9, 3, 9, 'R')
    rect(g, 12, 9, 14, 9, 'R')
    px(g, '1', (5, 5), (6, 5), (9, 8))
    return done(g)


def vitamin():  # 영양제
    g = _new()
    rect(g, 3, 5, 9, 14, 'a')
    rect(g, 3, 12, 9, 14, 'A')
    rect(g, 3, 3, 9, 4, 'W')
    rect(g, 3, 4, 9, 4, 'g')
    rect(g, 4, 7, 8, 11, 'w')
    px(g, 'q', (6, 8), (6, 9), (6, 10), (5, 9), (7, 9))
    ell(g, 10, 11, 12, 13, 'i')
    ell(g, 12, 9, 14, 11, 'W')
    px(g, 'I', (11, 13), (13, 11))
    return done(g)


PRODUCTS = {
    "pf6": pouch(), "pf7": dental(), "pf8": bigbag(),
    "pt4": duck(), "pt5": tunnel(), "pt6": disc(), "pt7": nosemat(),
    "pg6": hideout(), "pg7": coolmat(), "pg8": litterbox(), "pg9": bowls(),
    "pc1": brush(), "pc2": shampoo(), "pc3": clipper(),
    "pa2": leash(), "pa3": bagroll(), "pa4": nametag(), "pa5": raincoat(), "pa6": sweater(),
    "ph1": vitamin(),
}
