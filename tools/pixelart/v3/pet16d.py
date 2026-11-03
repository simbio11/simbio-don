"""돌보기 행동 아이콘 16×16 (용품이 필요 없는 행동). pet16b 의 그리기 도구 사용. id 는 care_* (상품 id 와 겹치지 않게)."""
from .pet16b import _new, rect, ell, px, line, done


def hand():
    # 쓰다듬는 손: 손가락 네 개 + 엄지
    g = _new()
    rect(g, 3, 7, 11, 13, 's')
    for x in (3, 6, 9):
        rect(g, x, 2, x + 1, 7, 's')
    rect(g, 12, 3, 12, 7, '.')
    rect(g, 11, 4, 12, 7, 's')
    rect(g, 12, 8, 14, 10, 's')
    rect(g, 3, 12, 11, 13, 'S')
    px(g, 'S', (5, 7), (8, 7), (11, 7))
    px(g, 'w', (3, 3), (6, 3), (9, 3), (11, 5))
    out = done(g)
    # 손가락 사이 틈
    rows = [list(r) for r in out]
    for x in (5, 8):
        for y in range(2, 7):
            if rows[y][x] == '.':
                rows[y][x] = 'K'
    return [''.join(r) for r in rows]


def heart():
    g = _new()
    ell(g, 2, 3, 7, 8, 'r')
    ell(g, 8, 3, 13, 8, 'r')
    for y in range(7, 14):
        w = 13 - y
        rect(g, 7 - w, y, 8 + w, y, 'r')
    px(g, 'W', (4, 5), (5, 4))
    px(g, '1', (4, 6), (10, 5))
    rect(g, 8, 11, 9, 12, 'R')
    return done(g)


def water():
    # 물그릇에 찰랑이는 물
    g = _new()
    ell(g, 6, 1, 9, 6, 'f')
    rect(g, 7, 1, 8, 2, 'f')
    px(g, 'W', (7, 3))
    rect(g, 1, 10, 14, 13, 'c')
    rect(g, 2, 9, 13, 9, 'F')
    rect(g, 2, 13, 13, 13, 'C')
    px(g, 'v', (3, 11), (4, 11))
    return done(g)


def poop():
    # 배변 치우기: 배변봉투 + 작은 응가
    g = _new()
    ell(g, 3, 9, 12, 13, 'o')
    ell(g, 4, 6, 11, 10, 'o')
    ell(g, 5, 3, 10, 7, 'o')
    px(g, 'O', (6, 12), (9, 12), (5, 9), (10, 9), (7, 6))
    px(g, 'L', (6, 4), (5, 7), (4, 10))
    return done(g)


def moon():
    # 재우기: 초승달 + 별
    g = _new()
    ell(g, 2, 2, 12, 12, 'y')
    ell(g, 6, 1, 14, 9, '.')
    px(g, 'Y', (4, 10), (5, 11), (7, 12))
    px(g, '2', (12, 11), (13, 12), (11, 12), (12, 13), (12, 12))
    return done(g)


def ball_throw():
    # 공 던지기: 테니스공
    g = _new()
    ell(g, 3, 3, 12, 12, 'q')
    line(g, 4, 6, 7, 9, 'W')
    line(g, 7, 9, 11, 7, 'W')
    px(g, 'Q', (10, 11), (11, 10), (12, 9))
    return done(g)


PRODUCTS = {
    "care_hand": hand(),
    "care_heart": heart(),
    "care_water": water(),
    "care_poop": poop(),
    "care_moon": moon(),
    "care_ball": ball_throw(),
}
