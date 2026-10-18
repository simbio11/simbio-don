"""
머리 모양 (손으로 그림). 글자: j 외곽선 · H 그늘 · h 기본 · J 빛 · S 앞머리 그림자(피부색 그늘).

각 모양: 'front' (얼굴 위에 그림), 'back' (몸 뒤에 그림), 'ears' (귀가 보이면 True).
좌표는 char3 기준 (머리 꼭대기 0줄, 모자 자리 -4줄까지).
"""
from .lib import check

W = 24


def _g(name, y0, rows):
    return (y0, check(name, rows, W))


HAIRS = {
    # 짧은 머리 (남자 기본): 옆으로 넘긴 앞머리
    'short': {
        'label': '짧은 머리',
        'ears': True,
        'front': _g('short', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "..jhhhJJhhhhhhhhhhhHHj..",
            "..jhhJJhhhhhhHhhhhhhHj..",  # 5
            "..jhhhhhHhhhHjhhhhHhHj..",
            "..jhhjhhjjhhjSjhhjjhHj..",  # 7
            "..jhjSjjSSjjSSSjjSSjHj..",  # 8
            "...jj..............jj...",  # 9
        ]),
    },
    # 긴 생머리 (여자 기본): 짧은 남자 머리와 같은 앞머리 + 얼굴 옆으로 내려오는 옆머리
    'long': {
        'label': '긴 생머리',
        'ears': False,
        'back': _g('long-back', 9, [
            #012345678901234567890123
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 9
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 15
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhhHHHHHHHHHHHHHHHHHHj.",
            ".jhhHHHHHHHHHHHHHHHHHHj.",  # 20
            ".jhhHHHHHHHHHHHHHHHHHHj.",
            "..jhHHHjjjjjjjjjjHHHHj..",
            "..jhHj............jHj...",
            "...jj..............jj...",  # 24
        ]),
        'front': _g('long', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "..jhhhJJhhhhhhhhhhhHHj..",
            ".jhhhJJhhhhhhhhhhhhhHHj.",  # 5
            ".jhhhhhhHhhhHjhhhhHhHHj.",
            ".jhhhjhhjjhhjSjhhjjhHHj.",
            ".jhhjSjjSSjjSSSjjSSjhHj.",  # 8
            ".jhhj..............jhHj.",
            ".jJhj..............jhHj.",  # 10
            ".jJhj..............jhHj.",
            ".jhhj..............jhHj.",
            ".jhhj..............jhHj.",
            ".jhhj..............jhHj.",
            ".jhhj..............jHHj.",  # 15
            ".jhhHj............jhHHj.",
            ".jhhHj............jhHHj.",
            "..jhHj............jhHj..",
            "..jhHj............jhHj..",
            "...jj..............jj...",  # 20
        ]),
    },

    # 스포츠머리: 두피에 붙는 아주 짧은 머리, 앞머리가 거의 없어 이마가 넓게 보인다
    'buzz': {
        'label': '스포츠머리',
        'ears': True,
        'front': _g('buzz', 1, [
            #012345678901234567890123
            ".......jjjjjjjjjj.......",  # 1
            ".....jjhhJJJhhhhhjj.....",
            "....jhhJJhhhhhhhhhHj....",
            "...jhhJJhhhhhhhhhHhHj...",
            "...jhJhhjjjhhjjjhhhHj...",  # 5 이마 선
            "...jhhjjSSSjjSSSjjHHj...",  # 가운데 살짝 내려온 머리 + 이마 그림자
            "...jhj............jHj...",  # 7 구레나룻
            "....jj............jj....",  # 8
        ]),
    },
    # 댄디 가르마: 왼쪽에서 가르고 양옆으로 넘긴 앞머리, 가르마 아래로 이마가 살짝 보인다
    'parted': {
        'label': '댄디 가르마',
        'ears': True,
        'front': _g('parted', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhJjjhhhjj......",
            "....jjhhJJjhhJJhhhjj....",
            "...jhhJJhjhhJJhhhhhHj...",
            "..jhhJJhhjhJJhhhhhhHHj..",
            "..jhJJhhjShhhhhhhhhhHj..",  # 5
            "..jhhhhjSSjhhhhHhhhhHj..",
            "..jhhhjSSSSjjhhHjhhHHj..",
            "..jhhjSS....SjjjSjjjHj..",  # 8
            "...jj..............jj...",
        ]),
    },
    # 곱슬 펌: 동글동글한 곱슬 덩어리로 머리보다 넓게 부푼 모양 (귀를 덮는다)
    'perm': {
        'label': '곱슬 펌',
        'ears': False,
        'front': _g('perm', -1, [
            #012345678901234567890123
            "......jjj.jjjj.jjj......",  # -1 위 곱슬 셋
            "....jjJJhjJJhhjJhhjj....",  # 0
            "...jJhhhHHhhhHHhhHhHj...",
            "..jJhhHJJhhHJhhhHhhHHj..",  # 2 가운데 곱슬 넷 (위 곱슬과 엇갈림)
            "..jhhhHhhhhHhhhHHhhHHj..",
            "...jhHHhhhHHhhhHHhHHj...",
            ".jjJHJJhhHJJhhHJhhhHHjj.",  # 5 앞머리 곱슬 셋 + 옆 곱슬
            ".jJhHhhhHHhhhHHhhHHHhHj.",
            ".jJhHhhHjjhhHHjjhHHHHHj.",
            "..jhjjjjSSjjjjSSjjjjHj..",  # 8 앞머리 곱슬 끝
            ".jJhj..............jhHj.",
            ".jJhj..............jhHj.",  # 10
            ".jhHj..............jHHj.",
            "..jj................jj..",  # 12
        ]),
    },
    # 단발: 긴 생머리와 같은 앞머리, 옆머리는 턱 근처에서 안으로 말린다
    'bob': {
        'label': '단발',
        'ears': False,
        'back': _g('bob-back', 9, [
            #012345678901234567890123
            "..jHHHHHHHHHHHHHHHHHHj..",  # 9
            "..jHHHHHHHHHHHHHHHHHHj..",
            "..jHHHHHHHHHHHHHHHHHHj..",
            "..jHHHHHHHHHHHHHHHHHHj..",
            "..jHHHHHHHHHHHHHHHHHHj..",
            "..jHHHHHHHHHHHHHHHHHHj..",
            "..jHHHHHHHHHHHHHHHHHHj..",  # 15
            "...jHHHHHHHHHHHHHHHHj...",
            "....jjjjjjjjjjjjjjjj....",  # 17
        ]),
        'front': _g('bob', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "..jhhhJJhhhhhhhhhhhHHj..",
            ".jhhhJJhhhhhhhhhhhhhHHj.",  # 5
            ".jhhhhhhHhhhHjhhhhHhHHj.",
            ".jhhhjhhjjhhjSjhhjjhHHj.",
            ".jhhjSjjSSjjSSSjjSSjhHj.",  # 8
            ".jhhj..............jhHj.",
            ".jJhj..............jhHj.",  # 10
            ".jJhj..............jhHj.",
            "jhJhj..............jhHHj",
            "jhhhj..............jhHHj",
            "jhhHj..............jHHHj",
            ".jHHj..............jHHj.",  # 15 안으로 말린 끝
            "..jjj..............jjj..",  # 16
        ]),
    },
    # 포니테일: 앞머리 + 뒤로 높이 묶은 머리 (귀가 보임). 오른쪽 위 머리끈에서 꼬리가 솟았다가 내려온다
    'ponytail': {
        'label': '포니테일',
        'ears': True,
        'back': _g('ponytail-back', -3, [
            #012345678901234567890123
            "....................jj..",  # -3 꼬리 위 끝
            "...................jJhj.",
            "..................jJhhHj",
            "..................jhhhHj",  # 0
            "...................jhhHj",
            "...................jhJHj",
            "...................jhJHj",
            "...................jhJHj",
            "...................jhJHj",  # 5
            "...................jhhHj",
            "...................jhhHj",
            "..................jhJhHj",
            "..................jhJhHj",
            "..................jhhHHj",  # 10
            "..................jhJhHj",
            "..................jhhHHj",
            "..................jhhHHj",
            "..................jhhHj.",
            ".................jhhHHj.",  # 15
            ".................jhhHj..",
            ".................jhHHj..",
            ".................jHHj...",
            "..................jj....",  # 19 꼬리 끝
        ]),
        'front': _g('ponytail', 0, [
            #012345678901234567890123
            "........jjjjjjjj..aaj...",  # 0 머리끈
            "......jjhhhJJhhhjjaAj...",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "...jhhJJhhhhhhhhhhhHj...",
            "...jhJJhhhhhhHhhhhhHj...",  # 5
            "...jhhhhHhhhHjhhhhHHj...",
            "...jhjhhjjhhjSjhhjjHj...",
            "...jjSjjSSjjSSSjjSSjj...",  # 8
        ]),
    },
    # 양갈래: 앞머리 + 머리 양옆 위에서 묶어 귀 뒤로 내려오는 두 갈래 (끈 위로 짧은 머리 뭉치)
    'twintail': {
        'label': '양갈래',
        'ears': True,
        'back': _g('twintail-back', 1, [
            #012345678901234567890123
            "..jj................jj..",  # 1 끈 위 뭉치
            ".jJhj..............jhHj.",
            "jJhhj..............jhHHj",
            "jhhj................jHHj",  # 4 끈 자리
            "jJhj................jhHj",  # 5
            "jJhj................jhHj",
            "jJhHj..............jhhHj",
            "jhhHj..............jhHHj",
            "jJhHj..............jhHHj",
            "jJhHj..............jhHHj",  # 10
            "jhhHj..............jhHHj",
            "jhhHj..............jhHHj",
            ".jhhHj............jhhHj.",
            ".jhhHj............jhHHj.",
            ".jhhHj............jhHHj.",  # 15
            "..jhHj............jhHj..",
            "..jhHj............jhHj..",
            "..jHj..............jHj..",
            "...j................j...",  # 19 꼬리 끝
        ]),
        'front': _g('twintail', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            ".aajhhhJJhhhhhhhhhhHjaa.",  # 3 방울 머리끈
            "aaaAhhJJhhhhhhhhhhhHaaaA",
            ".AAjhJJhhhhhhHhhhhhHjAA.",  # 5
            "...jhhhhHhhhHjhhhhHHj...",
            "...jhjhhjjhhjSjhhjjHj...",
            "...jjSjjSSjjSSSjjSSjj...",  # 8
        ]),
    },
    # 똥머리: 앞머리 + 정수리 위에 동그랗게 말아 올린 머리 (귀가 보임)
    'bun': {
        'label': '똥머리',
        'ears': True,
        'front': _g('bun', -4, [
            #012345678901234567890123
            "..........jjjj..........",  # -4
            "........jjJJhhjj........",
            ".......jJJhhHhhHj.......",
            ".......jJhhHhhHHj.......",  # -1
            "........jjaaaAjj........",  # 0 머리끈
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "...jhhJJhhhhhhhhhhhHj...",
            "...jhJJhhhhhhHhhhhhHj...",  # 5
            "...jhhhhHhhhHjhhhhHHj...",
            "...jhjhhjjhhjSjhhjjHj...",
            "...jjSjjSSjjSSSjjSSjj...",  # 8
        ]),
    },
    # 웨이브 긴머리: 물결치는 옆머리 + 같은 물결로 부푼 뒷머리, 끝이 바깥으로 말린다
    'wave': {
        'label': '웨이브 긴머리',
        'ears': False,
        'back': _g('wave-back', 9, [
            #012345678901234567890123
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 9
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 15
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            "jhHHHHHHHHHHHHHHHHHHHHHj",
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 20
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            "jhJhHHHHHHHHHHHHHHHHHHHj",
            "jJhHHjjjjjjjjjjjjjjHHHHj",
            ".jhHj..............jHHj.",
            "..jj................jj..",  # 25
        ]),
        'front': _g('wave', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "..jhhhJJhhhhhhhhhhhHHj..",
            ".jhhhJJhhhhhhhhhhhhhHHj.",  # 5
            ".jhhhhhhHhhhHjhhhhHhHHj.",
            ".jhhhjhhjjhhjSjhhjjhHHj.",
            ".jhhjSjjSSjjSSSjjSSjhHj.",  # 8
            ".jJhj..............jhHj.",
            ".jJhj..............jhHj.",  # 10
            "jJhHj..............jHHHj",  # 물결
            "jJhj................jHHj",
            "jhHj................jHHj",
            ".jhhj..............jhHj.",
            ".jJhj..............jhHj.",  # 15
            ".jJhj..............jhHj.",
            "jJhHj..............jHHHj",  # 물결
            "jJhj................jHHj",
            "jhHj................jHHj",
            ".jhhj..............jhHj.",  # 20
            ".jJhj..............jhHj.",
            "..jhj..............jhj..",
            "...j................j...",  # 23
        ]),
    },
}
