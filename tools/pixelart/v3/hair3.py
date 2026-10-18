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
    # 긴 생머리 (여자 기본)
    'long': {
        'label': '긴 생머리',
        'ears': False,
        'back': _g('long-back', 4, [
            #012345678901234567890123
            "..jHHHHHHHHHHHHHHHHHHj..",  # 4
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 10
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",  # 15
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhHHHHHHHHHHHHHHHHHHHj.",
            ".jhhHHHHHHHHHHHHHHHHHHj.",
            ".jhhHHHHHHHHHHHHHHHHHHj.",
            ".jhhHHHHHHHHHHHHHHHHHHj.",  # 20
            ".jhhHHHHHHHHHHHHHHHHHHj.",
            "..jhhHHjjjjjjjjjjHHHHj..",
            "..jhhHj..........jHHj...",
            "...jjj............jj....",  # 24
        ]),
        'front': _g('long', 0, [
            #012345678901234567890123
            "........jjjjjjjj........",  # 0
            "......jjhhhJJhhhjj......",
            "....jjhhhJJhhhhhhhjj....",
            "...jhhhJJhhhhhhhhhhHj...",
            "..jhhhJJhhhhhhhhhhhHHj..",
            "..jhhJJhhhhhhhhhhhhhHj..",  # 5
            ".jhhJhhhhhhhhhhhhhhhHHj.",
            ".jhhhhhhhhhhhHhhhhhhHHj.",
            ".jhhhhhhHhhhHhhhhHhhhHj.",
            ".jhhhhhHjSHjhhhhHjShhHj.",
            ".jhhhhHjSSjSjhhHjSSjhHj.",  # 10
            ".jhhhj.......jj....jhHj.",
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
}
