"""
반려동물 용품 16×16 (돈't 몰 상품 id → 그림). products16 과 같은 규칙: 글자 하나 = 픽셀 하나 (palette.PALETTE).
REUSE: 같은 그림을 쓰는 상품.
"""

PRODUCTS = {
    # 강아지 사료 봉지: 주황 봉지에 하얀 뼈
    "pf1": [
        "................",
        "...KKKKKKKKKK...",
        "..KgwwwwwwwwgK..",
        "..KKKKKKKKKKKK..",
        "..Ka7aaaaaaaAK..",
        "..Ka7aaaaaaaAK..",
        "..KaWWaaaaWWAK..",
        "..KaWWWWWWWWAK..",
        "..KaWWaaaaWWAK..",
        "..Ka7aaaaaaaAK..",
        "..KaaaaaaaaaAK..",
        "..KaaaaaaaaaAK..",
        "..KAAAAAAAAAAK..",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
    ],
    # 고양이 사료 봉지: 파란 봉지에 생선
    "pf2": [
        "................",
        "...KKKKKKKKKK...",
        "..KgwwwwwwwwgK..",
        "..KKKKKKKKKKKK..",
        "..KvccccccccCK..",
        "..KvccccccccCK..",
        "..KcccWWWccWCK..",
        "..KccWKWWWWWCK..",
        "..KcccWWWccWCK..",
        "..KvccccccccCK..",
        "..KccccccccCCK..",
        "..KccccccccCCK..",
        "..KCCCCCCCCCCK..",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
    ],
    # 개껌: 하얀 뼈다귀
    "pf3": [
        "................",
        "................",
        "................",
        "................",
        "..KK........KK..",
        ".KwwK......KwwK.",
        ".KwwwKKKKKKwwgK.",
        "..KwwwwwwwwwwK..",
        "..KwwwwwwwwwgK..",
        ".KwwgKKKKKKwggK.",
        ".KwgK......KggK.",
        "..KK........KK..",
        "................",
        "................",
        "................",
        "................",
    ],
    # 츄르: 짜 먹는 간식 세 개
    "pf4": [
        "................",
        "...KKK.KKK.KKK..",
        "...K6K.K2K.K3K..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KiK.KyK.KqK..",
        "...KIK.KYK.KQK..",
        "...KKK.KKK.KKK..",
        "................",
        "................",
        "................",
    ],
    # 삑삑이 공
    "pt1": [
        "................",
        "................",
        ".....KKKKKK.....",
        "....K11rrrrK....",
        "...K1rrrrrrRK...",
        "..K1rrrrrrrrRK..",
        "..KrrrrrrrrrRK..",
        "..KyyyyyyyyyYK..",
        "..KrrrrrrrrrRK..",
        "..KrrrrrrrrRRK..",
        "...KrrrrrrRRK...",
        "....KRRRRRRK....",
        ".....KKKKKK.....",
        "................",
        "................",
        "................",
    ],
    # 깃털 낚싯대
    "pt2": [
        "................",
        "..KK............",
        ".KiiK...........",
        ".KiIiK..........",
        "..KiiK..........",
        "...KKoK.........",
        ".....KoK........",
        "......KoK.......",
        ".......KoK......",
        "........KoK.....",
        ".........KoK....",
        "..........KoK...",
        "...........KOK..",
        "............KK..",
        "................",
        "................",
    ],
    # 푹신 방석
    "pg1": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...KKKKKKKKKK...",
        ".KKuuuuuuuuuuKK.",
        "Ku5555555555uuUK",
        "KuUUUUUUUUUUUuUK",
        "KuuuuuuuuuuuuUUK",
        ".KUUUUUUUUUUUUK.",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
    ],
    # 원목 강아지 집
    "pg2": [
        "................",
        ".......KK.......",
        "......KrrK......",
        ".....KrrrrK.....",
        "....KrrrrrrK....",
        "...KrrrrrrrrK...",
        "..KRRRRRRRRRRK..",
        ".KKKKKKKKKKKKKK.",
        "..KlLllllllloK..",
        "..KlllKKKKlloK..",
        "..KllKBBBBKloK..",
        "..KllKBBBBKloK..",
        "..KllKBBBBKloK..",
        "..KooKBBBBKOOK..",
        "..KKKKKKKKKKKK..",
        "................",
    ],
    # 캣타워
    "pg3": [
        "................",
        "..KKKKKK........",
        ".KiiiiiIK.......",
        "..KKggKK........",
        "....KgK.........",
        "....KgK..KKKKKK.",
        "....KgK.KiiiiiIK",
        "....KgK..KKggKK.",
        "....KgK....KgK..",
        "..KKKKKKKKKKgK..",
        ".KiiiiiiiiiiiIK.",
        "..KKgKKKKKKgKK..",
        "....KgK..KgK....",
        "..KKKKKKKKKKKKK.",
        ".KooooooooooooOK",
        ".KKKKKKKKKKKKKKK",
    ],
    # 산책 하네스: 빨간 고리와 노란 고리쇠
    "pa1": [
        "................",
        "................",
        "................",
        ".....KKKKKK.....",
        "...KKrrrrrrKK...",
        "..KrrKKKKKKrrK..",
        ".KrK........KrK.",
        ".KrK........KrK.",
        ".KrK.......KYYK.",
        "..KrK......KyYK.",
        "...KrKKKKKKrKK..",
        "....KRrrrrRK....",
        ".....KKKKKK.....",
        "................",
        "................",
        "................",
    ],
}

# 같은 그림을 쓰는 상품
REUSE = {
    "pf5": "pf1",   # 강아지 캔 간식
    "pt3": "pt1",   # 터그 로프
    "pg4": "pg3",   # 스크래쳐
    "pg5": "pf1",   # 자동 급식기
}

# 물건 종류 그림 (반려동물 용품): 발자국
KIND = [
    "................",
    "....KK....KK....",
    "...KhhK..KhhK...",
    "...KhhK..KhhK...",
    "....KK....KK....",
    ".KK..........KK.",
    "KhhK..KKKK..KhhK",
    "KhhK.KhhhhK.KhhK",
    ".KK.KhhhhhhK.KK.",
    "....KhhhhhhK....",
    "...KhhhhhhhhK...",
    "...KhhhhhhhhK...",
    "....KhhKKhhK....",
    ".....KK..KK.....",
    "................",
    "................",
]

# 방 선반 위 작은 그림 (12×12): 뼈다귀
ROOM = [
    "............",
    "............",
    "............",
    ".KK......KK.",
    "KwwK....KwwK",
    "KwwwKKKKwwgK",
    ".KwwwwwwwwK.",
    "KwwgKKKKwggK",
    "KwgK....KggK",
    ".KK......KK.",
    "............",
    "............",
]


def all_rows():
    out = {k: PRODUCTS[v] for k, v in REUSE.items()}
    out.update(PRODUCTS)
    return out
