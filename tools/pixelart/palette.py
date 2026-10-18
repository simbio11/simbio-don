"""도트 그림 팔레트. 글자 하나 = 색 하나 ('.' = 투명)."""

PALETTE = {
    # 선
    "K": "#2b2234",  # 외곽선 (짙은 자두색 — 검정보다 부드럽게)
    "k": "#5a4a63",  # 안쪽 선
    # 무채색
    "W": "#ffffff",
    "w": "#f6efe3",
    "g": "#d6cdbf",
    "G": "#a99e90",
    "D": "#6b6170",
    "B": "#3a3443",
    # 피부·머리
    "s": "#f9d4b4",
    "S": "#e8ab88",
    "b": "#f4908a",
    "h": "#6b4434",
    "H": "#94634a",
    "j": "#4a2c22",
    # 옷
    "c": "#5c92da",
    "C": "#3f6db6",
    "v": "#93bdf2",
    "p": "#46507c",
    "P": "#323a5e",
    "e": "#4b3d52",
    # 입
    "m": "#c4524c",
    "n": "#f08a80",
    # 나무
    "o": "#94603a",
    "O": "#6e4329",
    "l": "#b9844f",
    "L": "#d8a46a",
    # 색
    "r": "#e5584f",
    "R": "#b13c3c",
    "y": "#f7cd52",
    "Y": "#d99a2e",
    "q": "#7cc46c",
    "Q": "#4e9150",
    "t": "#62bfae",
    "T": "#3f9183",
    "u": "#a07bdc",
    "U": "#7352b8",
    "i": "#f6a9c6",
    "I": "#d87ba2",
    "a": "#f2944c",
    "A": "#c8682c",
    "x": "#dcaa6e",
    "X": "#b07a45",
    "z": "#7b4a2d",
    "f": "#a4dcff",
    "F": "#6fbcf2",
    "N": "#33416f",
    # 밝은 쪽 (빛 받는 면)
    "1": "#ff9a88",  # 빨강
    "2": "#fff0a3",  # 노랑
    "3": "#b6e396",  # 초록
    "4": "#a8eadb",  # 청록
    "5": "#c9acf5",  # 보라
    "6": "#ffd4e3",  # 분홍
    "7": "#ffc78c",  # 주황
    "8": "#f2d29c",  # 황토
    "9": "#e8c08a",  # 나무
    # 어두운 쪽 (그늘)
    "d": "#86283a",  # 빨강
    "E": "#a5651c",  # 노랑
    "#": "#2f6a40",  # 초록
    "0": "#2a6b62",  # 청록
    "M": "#4c338f",  # 보라
    "V": "#a6527e",  # 분홍
    "Z": "#924a20",  # 주황
    "J": "#b47c55",  # 머리 빛
    "+": "#eef4fb",  # 유리 반사
}

# 방을 그릴 때 쓰는 이름 있는 색 (바꿀 수 없는 색 — 벽지·바닥·러그·커튼·이불은 아래 MARKERS)
ROOM = {
    "wainscot": "#e6cfa6",
    "wainscot_dark": "#d3b88c",
    "wainscot_hi": "#f1dfbd",
    "frame_hi": "#ffffff",
    "lamp_shade": "#f6e7c4",
    "lamp_shade_dark": "#dcc196",
    "headboard": "#9a6440",
    "headboard_dark": "#6e4329",
    "headboard_hi": "#c08856",
    "rug_fringe": "#efe2c4",
    "molding": "#dcc39c",
    "molding_dark": "#c4a57c",
    "base": "#a87a50",
    "base_hi": "#c99a6a",
    "base_dark": "#7a5236",
    "frame": "#fbf4e6",
    "frame_shade": "#d9c8ac",
    "sheet": "#fbf5ec",
    "pillow_shade": "#e3d8c6",
    "metal": "#a3abb5",
    "metal_dark": "#6e7682",
    # 흰 판벽·몰딩 (투룸·아파트)
    "paint": "#f7f2ea",
    "paint_hi": "#fffdf8",
    "paint_shade": "#e3dbcf",
    "paint_dark": "#c9bfb1",
    # 금색 액자·황동
    "gold_hi": "#f6dc8a",
    "gold": "#d9ac4c",
    "gold_dark": "#a87a2c",
    # 천 머리판 (아파트 침대)
    "fabric_hi": "#d9d0c6",
    "fabric": "#c2b7ab",
    "fabric_dark": "#9c9083",
    # 창밖 도시 (아파트)
    "city_far": "#c4d4e6",
    "city_near": "#a9bcd4",
    "city_dark": "#91a6c2",
    "city_win": "#e4edf7",
    "rail": "#5d6574",
    "rail_hi": "#8c95a3",
}

# 색 바꾸기 표식: 방 그림에서 이 색들만 앱이 고른 색 묶음으로 바꾼다 (밝음 → 어둠).
# 기본값이 지금 모습이고, 방 그림의 다른 곳에는 쓰지 않는다 (생성기가 확인).
MARKERS = {
    "WALL": ["#f3e4c8", "#efdcbd", "#e9d5b2", "#e4cfa8"],     # 벽지: 바탕, 무늬, 점, 그늘
    "FLOOR": ["#dcab76", "#cd9762", "#c18a58", "#a26d43"],    # 바닥: 빛, 판자, 진한 판자, 틈
    "RUG": ["#f2d68c", "#7fb5a4", "#5e9584", "#4a7a6c"],      # 러그: 무늬, 바탕, 테두리, 그늘
    "CURTAIN": ["#f4a08c", "#e7806c", "#c35e51", "#a24a43"],  # 커튼: 빛, 바탕, 주름, 깊은 그늘
    "BEDDING": ["#a5bff0", "#7fa0e0", "#5d7fc4", "#4a68a8"],  # 이불: 빛, 바탕, 그늘, 바늘땀
}

# 고를 수 있는 색 (요소마다 첫째가 기본 = MARKERS, 밝음 → 어둠)
THEMES = {
    "WALL": [
        ("크림", MARKERS["WALL"]),
        ("민트", ["#e2f1e4", "#d6eadb", "#cbe2d1", "#c0d9c6"]),
        ("라벤더", ["#ebe4f3", "#e1d8ee", "#d7cde6", "#cdc1de"]),
        ("하늘", ["#deedf7", "#d1e4f2", "#c5dbec", "#b9d1e5"]),
        ("분홍", ["#f9e2e2", "#f4d5d7", "#edc9cc", "#e5bdc1"]),
        ("회색", ["#e9e8e6", "#dfdedb", "#d5d3d0", "#cbc8c4"]),
    ],
    "FLOOR": [
        ("원목", MARKERS["FLOOR"]),
        ("진한 원목", ["#a8754c", "#93623d", "#845735", "#5e3b23"]),
        ("화이트 오크", ["#f0dfc2", "#e4cfad", "#d9c29d", "#b59c78"]),
        ("회색 마루", ["#cfc9c1", "#bdb6ad", "#ada59b", "#857d74"]),
        ("체리", ["#d99470", "#c47d5a", "#b26e4c", "#874a32"]),
        ("대리석", ["#f5f3f0", "#e8e5e1", "#dad6d1", "#b4aea7"]),
    ],
    "RUG": [
        ("민트", MARKERS["RUG"]),
        ("빨강", ["#f3d39a", "#cf5f57", "#a8443f", "#84322f"]),
        ("남색", ["#e8d7a8", "#5b6b9e", "#45537f", "#333f63"]),
        ("겨자", ["#fff0c4", "#ddb04f", "#b98c34", "#926b23"]),
        ("분홍", ["#ffffff", "#f2a9bb", "#d9879c", "#b9687f"]),
        ("회색", ["#f4f0e8", "#aaa6a1", "#8c8883", "#6f6b67"]),
    ],
    "CURTAIN": [
        ("코랄", MARKERS["CURTAIN"]),
        ("하늘", ["#bfe0f7", "#92c4ec", "#6fa2d3", "#5584b7"]),
        ("노랑", ["#ffe8a3", "#f6cd63", "#d8a840", "#b5862d"]),
        ("초록", ["#cbe2b6", "#a9cb8f", "#87ad70", "#6a8f57"]),
        ("보라", ["#dac7f3", "#bb9ee7", "#987cca", "#7c61ad"]),
        ("리넨", ["#fbf8f2", "#ece4d6", "#d8ccb9", "#bdae98"]),
    ],
    "BEDDING": [
        ("파랑", MARKERS["BEDDING"]),
        ("분홍", ["#ffd1dc", "#f4a6ba", "#da859c", "#bd6a83"]),
        ("노랑", ["#fff0b8", "#f5d679", "#dab456", "#b8923f"]),
        ("초록", ["#cfe9bd", "#a8d190", "#85b270", "#69955a"]),
        ("회색", ["#e6e4e1", "#c9c6c2", "#aaa6a1", "#8b8782"]),
        ("보라", ["#ddccf6", "#bfa6ea", "#9f83d2", "#8268b6"]),
    ],
}
