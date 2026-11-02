"""반려동물 용품 16×16 세 번째 묶음 (pet16b 의 그리기 도구 사용). 봉지·캔처럼 겹치는 모양은 색·무늬만 바꿔 맛 종류로."""
from .pet16b import _new, rect, ell, px, line, done


def bag(body, shade, icon, band='w'):
    """사료 봉지: 위 주름, 가운데 흰 띠에 무늬"""
    g = _new()
    rect(g, 3, 2, 12, 14, body)
    rect(g, 3, 2, 12, 3, 'g')
    px(g, 'G', *[(x, 3) for x in range(3, 13, 2)])
    rect(g, 3, 13, 12, 14, shade)
    rect(g, 3, 4, 3, 12, 'W')
    rect(g, 5, 6, 10, 11, band)
    if icon == 'fish':
        px(g, 'F', (6, 8), (7, 7), (8, 7), (9, 8), (8, 9), (7, 9), (10, 7), (10, 9))
        px(g, 'K', (7, 8))
    elif icon == 'paw':
        px(g, 'I', (7, 9), (8, 9), (7, 10), (8, 10), (6, 8), (9, 8), (7, 7), (8, 7))
    elif icon == 'leg':
        ell(g, 5, 7, 8, 10, 'a')
        line(g, 8, 9, 10, 7, 'w', 1)
        px(g, 'W', (10, 6), (10, 7))
        px(g, 'A', (6, 10), (7, 10))
    rect(g, 4, 12, 11, 12, shade)
    return done(g)


def jerky():  # 닭가슴살 육포
    g = _new()
    for y, x0 in ((3, 2), (7, 3), (11, 2)):
        rect(g, x0, y, x0 + 10, y + 2, 'o')
        rect(g, x0, y + 2, x0 + 10, y + 2, 'O')
        px(g, 'l', (x0 + 2, y), (x0 + 3, y), (x0 + 6, y), (x0 + 8, y + 1))
        px(g, 'H', (x0 + 4, y + 1), (x0 + 7, y + 1))
    return done(g)


def cans():  # 참치 캔 쌓기
    g = _new()
    for x0, y0 in ((1, 8), (7, 8), (4, 2)):
        rect(g, x0, y0 + 1, x0 + 7, y0 + 5, 'g')
        ell(g, x0, y0, x0 + 7, y0 + 2, 'W')
        rect(g, x0, y0 + 5, x0 + 7, y0 + 5, 'G')
        rect(g, x0 + 1, y0 + 2, x0 + 6, y0 + 4, 'c')
        px(g, 'W', (x0 + 3, y0 + 3), (x0 + 4, y0 + 3))
        px(g, 'v', (x0 + 1, y0 + 2))
    return done(g)


def driedfish():  # 북어 트릿
    g = _new()
    for oy, ox in ((2, 1), (8, 3)):
        ell(g, ox, oy + 1, ox + 9, oy + 4, 'x')
        px(g, 'X', *[(ox + k, oy + 4) for k in range(1, 8)])
        px(g, 'K', (ox + 2, oy + 2))
        rect(g, ox + 9, oy, ox + 12, oy + 5, 'x')
        px(g, '.', (ox + 9, oy + 2), (ox + 9, oy + 3))
        px(g, 'L', (ox + 3, oy + 1), (ox + 5, oy + 2), (ox + 6, oy + 1))
    return done(g)


def milk():  # 펫 우유
    g = _new()
    rect(g, 4, 4, 11, 14, 'w')
    rect(g, 4, 2, 11, 5, 'c')
    rect(g, 6, 1, 9, 1, 'v')
    rect(g, 4, 13, 11, 14, 'g')
    ell(g, 6, 7, 9, 10, 'i')
    px(g, 'I', (6, 6), (9, 6))
    px(g, 'K', (7, 8), (8, 8))
    px(g, 'W', (4, 5), (4, 6))
    return done(g)


def laser():  # 레이저 포인터
    g = _new()
    line(g, 2, 13, 11, 4, 'D', 3)
    line(g, 3, 13, 11, 5, 'B', 1)
    px(g, 'r', (12, 3), (13, 2))
    px(g, '1', (13, 2), (14, 1))
    px(g, 'r', (14, 1), (14, 2), (13, 1))
    px(g, 'g', (5, 11), (6, 10))
    return done(g)


def catnip():  # 캣닢 물고기 인형
    g = _new()
    ell(g, 1, 5, 11, 11, 'q')
    px(g, 'Q', *[(x, 11) for x in range(2, 11)])
    rect(g, 10, 4, 14, 12, 'q')
    px(g, '.', (10, 7), (10, 8), (10, 9), (11, 8), (11, 7))
    px(g, 'Q', (12, 5), (12, 11), (13, 6), (13, 10))
    px(g, 'K', (3, 7))
    px(g, '3', (3, 6), (5, 7), (6, 6), (7, 8), (8, 7))
    px(g, 'W', (4, 9), (7, 10))
    return done(g)


def robotball():  # 자동 회전 공
    g = _new()
    ell(g, 2, 2, 13, 13, 'W')
    ell(g, 2, 2, 13, 13, 'W')
    rect(g, 2, 7, 13, 8, 'c')
    px(g, 'v', (3, 7), (4, 7))
    ell(g, 5, 5, 6, 6, 'B')
    ell(g, 9, 5, 10, 6, 'B')
    px(g, 'W', (5, 5), (9, 5))
    px(g, 'g', (4, 11), (5, 12), (10, 12), (11, 11))
    px(g, 'r', (7, 10), (8, 10))
    return done(g)


def steps():  # 계단
    g = _new()
    rect(g, 1, 11, 14, 13, 'o')
    rect(g, 4, 7, 14, 10, 'o')
    rect(g, 8, 3, 14, 6, 'o')
    rect(g, 1, 10, 14, 10, 'i')
    rect(g, 4, 6, 14, 6, 'i')
    rect(g, 8, 2, 14, 2, 'i')
    px(g, '6', (2, 10), (3, 10), (5, 6), (6, 6), (9, 2), (10, 2))
    px(g, 'O', *[(x, 13) for x in range(1, 15)], (4, 9), (8, 5))
    return done(g)


def hammock():  # 창문 해먹
    g = _new()
    rect(g, 1, 1, 14, 2, 'f')
    ell(g, 1, 1, 3, 3, 'F')
    ell(g, 12, 1, 14, 3, 'F')
    rect(g, 2, 3, 2, 5, 'D')
    rect(g, 13, 3, 13, 5, 'D')
    ell(g, 1, 4, 14, 12, 'u')
    rect(g, 1, 4, 14, 6, '.')
    rect(g, 2, 5, 13, 8, 'u')
    px(g, '5', *[(x, 5) for x in range(3, 13)], (4, 7), (7, 7), (10, 7))
    px(g, 'U', *[(x, 12) for x in range(4, 12)], (2, 10), (13, 10))
    return done(g)


def gate():  # 안전문
    g = _new()
    rect(g, 1, 2, 14, 13, 'w')
    rect(g, 3, 4, 12, 11, '.')
    for x in range(4, 12, 2):
        rect(g, x, 4, x, 11, 'g')
    rect(g, 3, 7, 12, 7, 'g')
    rect(g, 1, 2, 2, 13, 'g')
    rect(g, 13, 2, 14, 13, 'g')
    px(g, 'y', (12, 8), (12, 9))
    px(g, 'D', (2, 13), (13, 13))
    return done(g)


def carrier():  # 이동장
    g = _new()
    rect(g, 1, 4, 14, 13, 'c')
    rect(g, 1, 12, 14, 13, 'C')
    rect(g, 4, 2, 11, 3, 'D')
    rect(g, 3, 6, 11, 11, 'B')
    for x in range(4, 11, 2):
        rect(g, x, 6, x, 11, 'g')
    rect(g, 3, 8, 11, 8, 'g')
    px(g, 'v', (2, 5), (3, 5))
    px(g, 'y', (12, 8), (12, 9))
    return done(g)


def fountain():  # 급수기
    g = _new()
    ell(g, 1, 9, 14, 14, 'f')
    ell(g, 3, 9, 12, 11, 'F')
    rect(g, 6, 3, 9, 9, 'w')
    ell(g, 5, 2, 10, 4, 'W')
    px(g, 'F', (7, 5), (8, 5), (7, 6), (8, 6))
    px(g, 'f', (4, 10), (5, 11), (11, 10), (10, 11))
    px(g, 'W', (3, 12), (12, 12))
    return done(g)


def padpack():  # 배변패드
    g = _new()
    rect(g, 2, 3, 13, 13, 'W')
    rect(g, 2, 3, 13, 4, 'f')
    rect(g, 2, 12, 13, 13, 'g')
    px(g, 'c', (6, 7), (7, 7), (8, 7), (9, 7), (5, 8), (10, 8), (7, 9), (8, 9))
    px(g, 'F', *[(x, 11) for x in range(4, 12)])
    px(g, 'v', (3, 5), (3, 6))
    return done(g)


def sandbag():  # 고양이 모래
    g = _new()
    rect(g, 3, 3, 12, 14, 'x')
    rect(g, 3, 2, 12, 3, 'g')
    rect(g, 3, 13, 12, 14, 'X')
    ell(g, 5, 6, 10, 11, 'w')
    px(g, 'K', (6, 9), (9, 9), (7, 10), (8, 10))
    px(g, 'o', (5, 5), (10, 5), (5, 7), (10, 7), (4, 12), (11, 12))
    px(g, 'L', (4, 4), (4, 5), (11, 5))
    return done(g)


def hairclip():  # 전동 이발기
    g = _new()
    rect(g, 4, 2, 10, 12, 'g')
    rect(g, 4, 2, 5, 12, 'W')
    rect(g, 9, 2, 10, 12, 'G')
    rect(g, 3, 12, 11, 14, 'D')
    for x in range(3, 12, 2):
        px(g, 'g', (x, 14))
    ell(g, 6, 4, 8, 6, 'r')
    px(g, 'R', (7, 6))
    rect(g, 5, 8, 9, 9, 'c')
    px(g, 'v', (5, 8))
    return done(g)


def earwash():  # 귀 세정제
    g = _new()
    rect(g, 5, 5, 10, 14, 'u')
    rect(g, 5, 12, 10, 14, 'U')
    rect(g, 6, 2, 9, 4, 'W')
    rect(g, 7, 1, 8, 1, 'g')
    rect(g, 6, 7, 9, 10, 'w')
    ell(g, 7, 7, 8, 9, 'i')
    px(g, 'I', (7, 9))
    px(g, '5', (5, 6), (5, 7))
    return done(g)


def wipes():  # 눈물 티슈
    g = _new()
    rect(g, 2, 6, 13, 14, 'w')
    rect(g, 2, 13, 13, 14, 'g')
    rect(g, 4, 8, 11, 11, 'i')
    px(g, 'W', (5, 9), (6, 9), (9, 10), (10, 10))
    rect(g, 5, 3, 10, 6, 'W')
    px(g, 'g', (5, 3), (7, 4), (9, 3), (6, 5))
    rect(g, 4, 5, 11, 5, 'w')
    return done(g)


def toothkit():  # 칫솔 + 치약
    g = _new()
    rect(g, 2, 8, 12, 13, 'c')
    rect(g, 2, 8, 3, 13, 'v')
    rect(g, 12, 9, 14, 12, 'W')
    rect(g, 4, 10, 10, 11, 'W')
    px(g, 'C', *[(x, 13) for x in range(3, 13)])
    rect(g, 5, 2, 12, 4, 'q')
    px(g, '3', (5, 2), (6, 2))
    for x in range(6, 12, 2):
        rect(g, x, 5, x, 6, 'W')
    rect(g, 3, 3, 4, 7, 'D')
    return done(g)


def spray():  # 탈취 스프레이
    g = _new()
    rect(g, 4, 6, 9, 14, 't')
    rect(g, 4, 12, 9, 14, 'T')
    rect(g, 5, 3, 8, 5, 'D')
    rect(g, 6, 1, 9, 2, 'W')
    px(g, 'W', (4, 7), (4, 8))
    rect(g, 5, 8, 8, 10, 'w')
    px(g, 'q', (6, 9), (7, 9), (6, 8))
    px(g, 'f', (11, 1), (12, 3), (13, 1), (12, 5), (14, 3), (11, 4))
    return done(g)


def waterbottle():  # 휴대용 물병
    g = _new()
    rect(g, 3, 4, 9, 14, 'f')
    rect(g, 3, 12, 9, 14, 'F')
    rect(g, 4, 2, 8, 3, 'r')
    rect(g, 4, 6, 8, 9, 'W')
    px(g, 'c', (5, 7), (6, 7), (7, 7))
    ell(g, 9, 9, 14, 14, 'g')
    ell(g, 10, 10, 13, 12, 'F')
    px(g, 'W', (4, 5), (4, 10), (11, 10))
    return done(g)


def ledcollar():  # LED 목걸이
    g = _new()
    ell(g, 1, 3, 14, 12, 'D')
    ell(g, 3, 5, 12, 10, '.')
    for x, y in ((2, 7), (4, 4), (8, 3), (12, 4), (13, 8), (10, 11), (5, 11)):
        px(g, '2', (x, y))
    px(g, 'y', (3, 6), (9, 3), (7, 11), (12, 10))
    px(g, 'W', (4, 4), (8, 3))
    rect(g, 6, 12, 9, 14, 'y')
    px(g, 'K', (7, 13))
    return done(g)


def boots():  # 신발
    g = _new()
    for ox in (0, 8):
        rect(g, 1 + ox, 3, 5 + ox, 10, 'r')
        rect(g, 1 + ox, 9, 6 + ox, 12, 'r')
        rect(g, 1 + ox, 12, 6 + ox, 13, 'W')
        rect(g, 1 + ox, 3, 5 + ox, 4, 'R')
        px(g, '1', (2 + ox, 5), (2 + ox, 6))
        px(g, 'y', (3 + ox, 7), (4 + ox, 7))
    return done(g)


def vest():  # 안전 조끼
    g = _new()
    rect(g, 3, 4, 12, 14, 'a')
    rect(g, 1, 3, 4, 7, 'a')
    rect(g, 11, 3, 14, 7, 'a')
    rect(g, 6, 3, 9, 6, '.')
    rect(g, 3, 8, 12, 9, '2')
    rect(g, 3, 11, 12, 12, '2')
    px(g, 'A', *[(x, 14) for x in range(3, 13)], (7, 7), (8, 7))
    px(g, 'W', (4, 8), (7, 11))
    return done(g)


def pumpkin():  # 호박 코스튬
    g = _new()
    ell(g, 1, 4, 14, 14, 'a')
    ell(g, 5, 4, 10, 14, 'A')
    ell(g, 6, 5, 9, 13, 'a')
    rect(g, 7, 2, 8, 4, 'Q')
    px(g, 'q', (9, 2), (10, 2))
    px(g, 'K', (4, 8), (5, 8), (10, 8), (11, 8), (5, 7), (11, 7), (6, 11), (7, 12), (8, 12), (9, 11))
    px(g, 'y', (4, 7), (10, 7))
    px(g, '7', (3, 6), (4, 5))
    return done(g)


def cone():  # 넥카라
    g = _new()
    for y in range(2, 13):
        w = 2 + (y - 2) * 6 // 10
        rect(g, 8 - w, y, 7 + w, y, 'w')
    for y in range(2, 13):
        w = 2 + (y - 2) * 6 // 10
        px(g, 'g', (7 + w, y), (6 + w, y))
    ell(g, 4, 1, 11, 4, 'W')
    ell(g, 6, 2, 9, 3, 'D')
    ell(g, 1, 11, 14, 14, 'g')
    ell(g, 3, 11, 12, 13, 'D')
    px(g, 'G', (3, 8), (4, 6), (11, 7))
    return done(g)


def scale():  # 체중계
    g = _new()
    rect(g, 1, 6, 14, 13, 'g')
    rect(g, 1, 12, 14, 13, 'G')
    rect(g, 4, 3, 11, 6, 'B')
    rect(g, 5, 4, 10, 5, 'f')
    px(g, 'K', (6, 4), (8, 4), (9, 5))
    px(g, 'W', (2, 7), (3, 7))
    ell(g, 5, 8, 10, 12, 'D')
    px(g, 'h', (6, 9), (9, 9), (7, 10), (8, 10), (5, 8), (10, 8))
    return done(g)


PRODUCTS = {
    "pf9": bag('q', 'Q', 'fish'), "pf10": bag('i', 'I', 'paw'), "pf11": bag('c', 'C', 'leg'),
    "pf12": jerky(), "pf13": cans(), "pf14": driedfish(), "pf15": milk(),
    "pt8": laser(), "pt9": catnip(), "pt10": robotball(),
    "pg10": steps(), "pg11": hammock(), "pg12": gate(), "pg13": carrier(), "pg14": fountain(), "pg15": padpack(), "pg16": sandbag(),
    "pc4": hairclip(), "pc5": earwash(), "pc6": wipes(), "pc7": toothkit(), "pc8": spray(),
    "pa7": waterbottle(), "pa8": ledcollar(), "pa9": boots(), "pa10": vest(), "pa11": pumpkin(),
    "ph2": cone(), "ph3": scale(),
}
