"""
옷·신발·모자·장신구 (손으로 그림). 좌표는 char3 기준 (24칸, 머리 꼭대기 0줄).

색 묶음: 윗옷 v c C N (빛·기본·그늘·외곽선), 아래옷 q p P Q, 신발 o O x, 모자 1 2 3 4, 포인트 a A.
고정 색: W 흰색, y Y 금색, B 아주 어두운 색, K k 선.
"""
from .lib import row


def _p(y0, rows):
    return (y0, rows)


TOPS = {
    'tee': {'label': '반팔 티', 'rows': _p(19, [
        row(6, "NNNN", 10, "NSSN", 14, "NNNN"),
        row(5, "N", 6, "vvcc", 10, "cCCc", 14, "ccCC", 18, "N"),
        row(4, "NvcN", 8, "vcccccCC", 16, "NcCN"),
        row(4, "NNNN", 8, "vcccccCC", 16, "NNNN"),
        row(7, "N", 8, "vcccccCC", 16, "N"),
        row(7, "N", 8, "vcccccCC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'hoodie': {'label': '후드티', 'rows': _p(19, [
        row(6, "NCCN", 10, "NSSN", 14, "NCCN"),
        row(5, "N", 6, "vccC", 10, "aCCa", 14, "CccC", 18, "N"),
        row(4, "NvcN", 8, "vcaccacC", 16, "NcCN"),
        row(4, "NvcN", 8, "vccccccC", 16, "NcCN"),
        row(4, "NCCN", 8, "vccyYcCC", 16, "NCCN"),
        row(7, "N", 8, "vcNNNNcC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'shirt': {'label': '셔츠', 'rows': _p(19, [
        row(6, "NNNW", 10, "WSSW", 14, "WNNN"),
        row(5, "N", 6, "vccc", 10, "WCCW", 14, "cccC", 18, "N"),
        row(4, "NvcN", 8, "vccCCccC", 16, "NcCN"),
        row(4, "NvcN", 8, "vccaCccC", 16, "NcCN"),
        row(4, "NCCN", 8, "vccCCccC", 16, "NCCN"),
        row(7, "N", 8, "vccaCcCC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'blouse': {'label': '블라우스', 'rows': _p(19, [
        row(6, "NNN", 9, "WWSSWW", 15, "NNN"),
        row(4, "Nvvc", 8, "vcWaaWCC", 16, "cCCN"),
        row(4, "NvcN", 8, "vcccccCC", 16, "NcCN"),
        row(4, "NNNN", 8, "vcccccCC", 16, "NNNN"),
        row(7, "N", 8, "vcccccCC", 16, "N"),
        row(7, "N", 8, "vcccccCC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'knit': {'label': '니트', 'rows': _p(19, [
        row(6, "NCCN", 10, "NSSN", 14, "NCCN"),
        row(5, "N", 6, "vccc", 10, "CccC", 14, "cccC", 18, "N"),
        row(4, "NvcN", 8, "vcCccCcC", 16, "NcCN"),
        row(4, "NvcN", 8, "vcCccCcC", 16, "NcCN"),
        row(4, "NCCN", 8, "vcCccCcC", 16, "NCCN"),
        row(7, "N", 8, "CcCcCcCC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'jacket': {'label': '재킷', 'rows': _p(19, [
        row(6, "NcNW", 10, "WSSW", 14, "WNcN"),
        row(5, "Nvcc", 9, "NWaAWN", 15, "cCC", 18, "N"),
        row(4, "NvcN", 8, "vNWaAWNC", 16, "NcCN"),
        row(4, "NvcN", 8, "vcNaANcC", 16, "NcCN"),
        row(4, "NCCN", 8, "vccNNccC", 16, "NCCN"),
        row(7, "N", 8, "vccaccCC", 16, "N"),
        row(7, "NNNNNNNNNN"),
    ])},
    'padding': {'label': '패딩', 'rows': _p(19, [
        row(6, "NvcccccccCCN"),
        row(4, "NvcccccgccccccCN"),
        row(4, "NCCCCCCgCCCCCCCN"),
        row(4, "NvcccccgcccccCCN"),
        row(4, "NCCCCCCgCCCCCCCN"),
        row(4, "NvcccccgcccccCCN"),
        row(7, "NNNNNNNNNN"),
    ])},
    'tank': {'label': '민소매', 'rows': _p(19, [
        row(8, "Nc", 14, "cN"),
        row(7, "Nvc", 14, "CCN"),
        row(7, "NvccccccCN"),
        row(7, "NvccccccCN"),
        row(7, "NvccccccCN"),
        row(7, "NvcccccCCN"),
        row(7, "NNNNNNNNNN"),
    ])},
    'dress': {'label': '원피스', 'dress': True, 'rows': _p(19, [
        row(6, "NNNN", 10, "NSSN", 14, "NNNN"),
        row(5, "N", 6, "vvcc", 10, "cCCc", 14, "ccCC", 18, "N"),
        row(4, "NvcN", 8, "vcccccCC", 16, "NcCN"),
        row(4, "NNNN", 8, "vcccccCC", 16, "NNNN"),
        row(7, "N", 8, "vcccccCC", 16, "N"),
        row(7, "N", 8, "aAaAaAaA", 16, "N"),
        row(7, "NvccccccCN"),
        row(6, "NvcccccccCCN"),
        row(5, "NvccccCccccCCN"),
        row(5, "NNNNNNNNNNNNNN"),
    ])},
}

BOTTOMS = {
    'pants': {'label': '긴바지', 'rows': _p(25, [
        row(7, "QQQQQQQQQQ"),
        row(7, "QqppppppPQ"),
        row(7, "QqpQ", 13, "QpPQ"),
        row(7, "QqpQ", 13, "QpPQ"),
        row(7, "QqpQ", 13, "QpPQ"),
        row(7, "QQQQ", 13, "QQQQ"),
    ])},
    'shorts': {'label': '반바지', 'rows': _p(25, [
        row(7, "QQQQQQQQQQ"),
        row(7, "QqppppppPQ"),
        row(7, "QqpQ", 13, "QpPQ"),
        row(7, "QQQQ", 13, "QQQQ"),
    ])},
    'skirt': {'label': '치마', 'rows': _p(25, [
        row(7, "QQQQQQQQQQ"),
        row(6, "QqppppppppPQ"),
        row(5, "QqppPpppPpppPQ"),
        row(5, "QQQQQQQQQQQQQQ"),
    ])},
    'longskirt': {'label': '롱스커트', 'rows': _p(25, [
        row(7, "QQQQQQQQQQ"),
        row(6, "QqppppppppPQ"),
        row(6, "QqppPppPppPQ"),
        row(5, "QqpppPppPpppPQ"),
        row(5, "QqppPpppPpppPQ"),
        row(5, "QQQQQQQQQQQQQQ"),
    ])},
    'jogger': {'label': '트레이닝', 'rows': _p(25, [
        row(7, "QQQQQQQQQQ"),
        row(7, "QqppppppPQ"),
        row(7, "QapQ", 13, "QpaQ"),
        row(7, "QapQ", 13, "QpaQ"),
        row(7, "QPPQ", 13, "QPPQ"),
        row(7, "QQQQ", 13, "QQQQ"),
    ])},
}

SHOES = {
    'sneakers': {'label': '운동화', 'rows': _p(30, [
        row(6, "xooOx", 13, "xOoox"),
        row(6, "xxxxx", 13, "xxxxx"),
    ])},
    'flats': {'label': '구두', 'rows': _p(30, [
        row(7, "xooO", 13, "Ooox"),
        row(6, "xxxxx", 13, "xxxxx"),
    ])},
    'boots': {'label': '부츠', 'rows': _p(28, [
        row(7, "xooO", 13, "Ooox"),
        row(7, "xooO", 13, "Ooox"),
        row(6, "xoooO", 13, "Ooox"),
        row(6, "xxxxx", 13, "xxxxx"),
    ])},
}

HATS = {
    'cap': {'label': '캡모자', 'rows': _p(-1, [
        row(8, "44444444"),
        row(6, "441122222344"),
        row(5, "41222222222334"),
        row(4, "4122222222222334"),
        row(4, "4222222a22222334"),
        row(3, "444444444444444444"),
        row(3, "4", 4, "3333333333333333", 20, "4"),
    ])},
    'beanie': {'label': '비니', 'rows': _p(-2, [
        row(9, "444444"),
        row(7, "4411222244"),
        row(5, "44122222222344"),
        row(4, "4122222222222334"),
        row(4, "4122222222222334"),
        row(4, "4343434343434344"),
        row(4, "4444444444444444"),
    ])},
    'ribbon': {'label': '리본', 'rows': _p(0, [
        row(15, "KK___KK"),
        row(15, "KaKKKaK"),
        row(15, "KAKaKAK"),
        row(15, "KK_K_KK"),
    ])},
    'crown': {'label': '왕관', 'rows': _p(-3, [
        row(8, "y__yy__y"),
        row(8, "yy_yy_yy"),
        row(8, "yYyYyYyY"),
        row(8, "YYYYYYYY"),
    ])},
}

ACCS = {
    'glasses': {'label': '안경', 'rows': _p(8, [
        row(5, "kkkkkk", 13, "kkkkkk"),
        row(5, "k", 10, "kkkk", 18, "k"),
        row(5, "k", 10, "k", 13, "k", 18, "k"),
        row(5, "k", 10, "k", 13, "k", 18, "k"),
        row(5, "kkkkkk", 13, "kkkkkk"),
    ])},
    'sunglasses': {'label': '선글라스', 'rows': _p(9, [
        row(4, "KKKKKKKKKKKKKKKK"),
        row(5, "BWBBBB", 13, "BWBBBB"),
        row(5, "BBBBBB", 13, "BBBBBB"),
        row(6, "BBBB", 14, "BBBB"),
    ])},
    'earrings': {'label': '귀걸이', 'rows': _p(12, [
        row(3, "y", 20, "y"),
        row(3, "Y", 20, "Y"),
    ])},
    'necklace': {'label': '목걸이', 'rows': _p(19, [
        row(9, "Y", 14, "Y"),
        row(10, "Y", 13, "Y"),
        row(11, "yy"),
    ])},
}
