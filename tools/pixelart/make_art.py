#!/usr/bin/env python3
"""
도트 그림 생성기.

    python3 tools/pixelart/make_art.py [미리보기_폴더]

- app/src/main/res/drawable-nodpi/px_*.png  : 앱에서 쓰는 그림 (원본 픽셀 크기, 앱이 정수배로 확대)
- app/src/main/kotlin/.../ui/pixel/RoomLayout.kt : 방 안 칸 좌표 (이 스크립트가 생성)
- 미리보기_폴더/sheet.png, scene.png : 확인용 (리포에 넣지 않음)

그림을 손으로 다시 그리고 싶으면 같은 이름·같은 크기의 PNG로 덮어쓰면 된다.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from palette import PALETTE, ROOM  # noqa: E402
import sprites  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
LAYOUT_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/RoomLayout.kt")


def hex_rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def C(name):
    """팔레트 글자 또는 방 색 이름 → RGBA"""
    if name in PALETTE:
        return hex_rgba(PALETTE[name])
    return hex_rgba(ROOM[name])


def grid_image(rows, name="?"):
    w = len(rows[0])
    for i, r in enumerate(rows):
        assert len(r) == w, f"{name}: {i}번째 줄 길이 {len(r)} != {w}"
        for ch in r:
            assert ch == "." or ch in PALETTE, f"{name}: 모르는 색 '{ch}'"
    img = Image.new("RGBA", (w, len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != ".":
                img.putpixel((x, y), C(ch))
    return img


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def rect(self, x, y, w, h, c):
        if w > 0 and h > 0:
            self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=C(c))

    def px(self, x, y, c):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.img.putpixel((x, y), C(c))

    def hline(self, x, y, w, c):
        self.rect(x, y, w, 1, c)

    def vline(self, x, y, h, c):
        self.rect(x, y, 1, h, c)

    def box(self, x, y, w, h, fill, outline="K", hi=None, sh=None):
        """채움 + 1px 외곽선 + (선택) 위·왼쪽 밝은 선, 아래·오른쪽 어두운 선"""
        self.rect(x, y, w, h, outline)
        self.rect(x + 1, y + 1, w - 2, h - 2, fill)
        if hi:
            self.hline(x + 1, y + 1, w - 2, hi)
            self.vline(x + 1, y + 1, h - 2, hi)
        if sh:
            self.hline(x + 1, y + h - 2, w - 2, sh)
            self.vline(x + w - 2, y + 1, h - 2, sh)

    def paste(self, sprite, x, y):
        self.img.alpha_composite(sprite, (x, y))

    def overlay(self, polygon, rgba):
        layer = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).polygon(polygon, fill=rgba)
        self.img = Image.alpha_composite(self.img, layer)
        self.d = ImageDraw.Draw(self.img)


# ── 방 ─────────────────────────────────────────────────────

ROOM_W, ROOM_H = 160, 136
FLOOR_Y = 60

# 칸 좌표 (물건 12×12의 왼쪽 위). Kotlin RoomLayout.kt로 내보낸다.
SLOTS = {
    "WARDROBE": [(9, 23), (21, 23), (33, 23), (9, 38), (21, 38), (33, 38)],
    "SHELF": [(64, 41), (76, 41), (88, 41), (64, 55), (76, 55), (88, 55)],
    "DESK": [(113, 48), (123, 48), (133, 48), (143, 48)],
    "SHOE_RACK": [(8, 102), (20, 102), (8, 116), (20, 116)],
}
CHARACTER_POS = (76, 82)
# 넘친 물건이 쌓이는 자리 (채우는 순서)
FLOOR_PILE = [
    (76, 122), (88, 122), (64, 122), (100, 122), (52, 122),
    (70, 110), (94, 110), (82, 116), (62, 116), (98, 116), (56, 110), (106, 110),
]
TRASH = [(46, 123), (41, 107), (49, 113), (36, 95), (52, 100), (44, 90)]

PLANT = [
    "..QQ.......",
    ".QqqQ..QQ..",
    ".QqqqQQqqQ.",
    "..QqqqqqqQ.",
    "QQ.QqqqqQ..",
    "QqQ.QqqQ.QQ",
    "QqqQQqqQQqQ",
    ".QqqqQqqqQ.",
    "..QQqqqqQ..",
    "....QqQQ...",
    ".KKKKKKKKK.",
    ".KrrrrrrrK.",
    "..KRRRRRK..",
    "..KrrrrrK..",
    "..KrrrrrK..",
    "...KKKKK...",
]


def draw_room(level="one_room"):
    cv = Canvas(ROOM_W, ROOM_H)

    # 벽
    cv.rect(0, 0, ROOM_W, FLOOR_Y - 4, "wall")
    for y in range(9, 52, 9):
        for x in range(((y // 9) % 2) * 6 + 3, ROOM_W, 12):
            cv.px(x, y, "wall_dot")
            cv.px(x - 1, y + 1, "wall_dot")
            cv.px(x + 1, y + 1, "wall_dot")
            cv.px(x, y + 2, "wall_dot")
    cv.rect(0, 0, ROOM_W, 3, "molding")
    cv.hline(0, 3, ROOM_W, "molding_dark")
    cv.hline(0, 54, ROOM_W, "wall_shade")
    cv.hline(0, 55, ROOM_W, "wall_shade")
    # 걸레받이
    cv.rect(0, 56, ROOM_W, 4, "base")
    cv.hline(0, 56, ROOM_W, "base_hi")
    cv.hline(0, 59, ROOM_W, "base_dark")

    # 나무 바닥
    plank = 7
    for i, y in enumerate(range(FLOOR_Y, ROOM_H, plank)):
        cv.rect(0, y, ROOM_W, plank, "floor_a" if i % 2 == 0 else "floor_b")
        cv.hline(0, y + plank - 1, ROOM_W, "floor_seam")
        off = (i * 23) % 40
        for x in range(off, ROOM_W, 40):
            cv.vline(x, y, plank - 1, "floor_seam")
            if x + 1 < ROOM_W:
                cv.vline(x + 1, y, plank - 1, "floor_hi")
    cv.hline(0, FLOOR_Y, ROOM_W, "floor_shadow")
    # 창문 햇빛
    cv.overlay([(68, 61), (98, 61), (118, 96), (86, 96)], (255, 236, 196, 70))

    # 창문
    cv.box(66, 6, 32, 26, "frame", sh="frame_shade")
    for i, y in enumerate(range(9, 29)):
        t = i / 19
        top, bot = hex_rgba(ROOM["glass_top"]), hex_rgba(ROOM["glass_bot"])
        col = tuple(round(top[k] + (bot[k] - top[k]) * t) for k in range(3)) + (255,)
        cv.d.rectangle([69, y, 94, y], fill=col)
    for (cx, cy, w) in [(72, 13, 7), (86, 20, 6)]:
        cv.rect(cx + 1, cy, w - 2, 1, "W")
        cv.rect(cx, cy + 1, w, 2, "W")
    cv.rect(81, 9, 2, 20, "frame")
    cv.vline(82, 9, 20, "frame_shade")
    cv.rect(69, 18, 26, 2, "frame")
    cv.hline(69, 19, 26, "frame_shade")
    # 창틀
    cv.box(63, 31, 38, 4, "l", hi="L")
    # 커튼 봉
    cv.rect(57, 3, 50, 2, "O")
    cv.hline(57, 3, 50, "o")
    cv.box(55, 2, 3, 4, "o")
    cv.box(106, 2, 3, 4, "o")
    # 커튼
    for (x, flip) in [(58, False), (98, True)]:
        w = 8
        cv.rect(x, 5, w, 33, "K")
        cv.rect(x + 1, 5, w - 2, 32, "curtain")
        for fx in (2, 5):
            cv.vline(x + (w - 1 - fx if flip else fx), 5, 32, "curtain_dark")
            cv.vline(x + (w - 2 - fx if flip else fx - 1) + (0 if flip else 0), 5, 32, "curtain_hi")
        cv.rect(x, 25, w, 2, "Y")
        cv.hline(x, 25, w, "y")

    # 액자
    cv.box(118, 12, 22, 18, "o", hi="l", sh="O")
    cv.rect(121, 15, 16, 12, "f")
    cv.rect(121, 22, 16, 5, "q")
    for x in range(121, 137):
        h = 2 + ((x * 7) % 5 == 0)
        cv.vline(x, 22 - h + 2, 1, "q")
    cv.rect(121, 25, 16, 2, "Q")
    cv.rect(132, 16, 3, 3, "y")

    # 열린 옷장 (WARDROBE)
    cv.box(1, 15, 6, 61, "l", hi="L", sh="o")  # 열린 문
    cv.rect(4, 44, 1, 3, "y")
    cv.box(6, 14, 42, 66, "o", sh="O")
    cv.rect(7, 15, 40, 3, "O")
    cv.hline(7, 15, 40, "l")
    cv.rect(9, 18, 36, 34, "O")
    for x in range(12, 45, 6):
        cv.vline(x, 18, 34, "j")
    cv.hline(9, 21, 36, "metal")
    cv.hline(9, 22, 36, "metal_dark")
    # 빈 옷걸이 (옷이 걸리면 그 위를 덮는다)
    for sx, _ in SLOTS["WARDROBE"][:3]:
        cv.px(sx + 6, 22, "metal_dark")
        cv.px(sx + 6, 23, "metal")
        for i in range(4):
            cv.px(sx + 5 - i, 24 + i, "metal")
            cv.px(sx + 6 + i, 24 + i, "metal")
        cv.hline(sx + 2, 27, 8, "metal")
    cv.rect(9, 36, 36, 2, "l")
    cv.hline(9, 36, 36, "L")
    cv.rect(9, 50, 36, 2, "l")
    cv.hline(9, 50, 36, "L")
    for y in (53, 63):
        cv.box(9, y, 36, 10, "l", hi="L", sh="o")
        cv.rect(25, y + 4, 4, 2, "O")
    cv.rect(6, 74, 42, 2, "O")
    cv.box(8, 75, 5, 5, "O")
    cv.box(41, 75, 5, 5, "O")
    cv.hline(6, 80, 42, "floor_shadow")

    # 화분
    cv.paste(grid_image(PLANT, "plant"), 49, 63)
    cv.hline(50, 79, 9, "floor_shadow")

    # 책장 (SHELF)
    cv.box(62, 38, 40, 42, "o", sh="O")
    cv.rect(63, 39, 38, 2, "l")
    cv.hline(63, 39, 38, "L")
    cv.rect(64, 41, 36, 26, "O")
    for y in (53, 67):
        cv.rect(64, y, 36, 2, "l")
        cv.hline(64, y, 36, "L")
    cv.box(64, 69, 18, 9, "l", hi="L", sh="o")
    cv.box(82, 69, 18, 9, "l", hi="L", sh="o")
    cv.rect(79, 72, 1, 3, "O")
    cv.rect(84, 72, 1, 3, "O")
    cv.hline(62, 80, 40, "floor_shadow")

    # 책상 (DESK)
    cv.box(112, 59, 44, 6, "l", hi="L", sh="o")
    cv.box(113, 64, 4, 16, "o", sh="O")
    cv.box(151, 64, 4, 16, "o", sh="O")
    cv.box(136, 64, 15, 13, "l", hi="L", sh="o")
    cv.hline(137, 70, 13, "o")
    cv.rect(142, 66, 3, 1, "O")
    cv.rect(142, 72, 3, 1, "O")
    cv.hline(112, 80, 44, "floor_shadow")
    # 의자 (등받이가 보이게)
    cv.box(125, 70, 12, 13, "o", hi="l", sh="O")
    for x in (128, 131, 134):
        cv.vline(x, 72, 9, "O")
    cv.box(123, 82, 16, 4, "l", hi="L", sh="o")
    cv.box(124, 85, 3, 9, "o")
    cv.box(135, 85, 3, 9, "o")
    cv.hline(123, 94, 16, "floor_shadow")

    # 러그
    rx, ry, rw, rh = 52, 94, 64, 28
    cv.rect(rx + 3, ry, rw - 6, rh, "rug_dark")
    cv.rect(rx + 1, ry + 1, rw - 2, rh - 2, "rug_dark")
    cv.rect(rx, ry + 3, rw, rh - 6, "rug_dark")
    cv.rect(rx + 3, ry + 2, rw - 6, rh - 4, "rug")
    cv.rect(rx + 2, ry + 3, rw - 4, rh - 6, "rug")
    for x in range(rx + 6, rx + rw - 6, 4):
        cv.rect(x, ry + 4, 2, 1, "rug_pattern")
        cv.rect(x, ry + rh - 5, 2, 1, "rug_pattern")
    for y in range(ry + 7, ry + rh - 7, 4):
        cv.rect(rx + 5, y, 1, 2, "rug_pattern")
        cv.rect(rx + rw - 6, y, 1, 2, "rug_pattern")

    # 신발장 (SHOE_RACK)
    cv.box(6, 100, 28, 32, "o", sh="O")
    cv.rect(8, 102, 24, 26, "O")
    for y in (100, 114, 128):
        cv.rect(7, y, 26, 2, "l")
        cv.hline(7, y, 26, "L")
    cv.hline(6, 132, 28, "floor_shadow")

    # 쓰레기통
    cv.box(37, 118, 9, 14, "metal", sh="metal_dark")
    cv.box(36, 116, 11, 3, "metal_dark")
    for x in (39, 41, 43):
        cv.vline(x, 120, 10, "metal_dark")
    cv.hline(36, 132, 11, "floor_shadow")

    # 침대
    bx, by, bw, bh = 116, 98, 42, 38
    cv.box(bx, by, bw, 7, "o", hi="l", sh="O")  # 머리판
    cv.box(bx + 1, by + 6, bw - 2, bh - 6, "sheet")
    cv.box(bx + 4, by + 8, 15, 7, "W", sh="pillow_shade")
    cv.box(bx + 21, by + 8, 15, 7, "W", sh="pillow_shade")
    cv.box(bx + 1, by + 16, bw - 2, bh - 16, "blanket", hi="blanket_hi", sh="blanket_dark")
    cv.rect(bx + 2, by + 16, bw - 4, 2, "sheet")
    for x in range(bx + 6, bx + bw - 4, 8):
        cv.vline(x, by + 20, bh - 22, "blanket_dark")

    return cv.img


# ── 출력 ───────────────────────────────────────────────────

def save(img, name):
    path = os.path.join(OUT, f"{name}.png")
    img.save(path, optimize=True)
    return path


def write_layout_kt():
    def pts(lst):
        return ", ".join(f"IntOffset({x}, {y})" for x, y in lst)

    slots = "\n".join(f"        Slot.{k} to listOf({pts(v)})," for k, v in SLOTS.items())
    kt = f"""package com.simbio.don.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import com.simbio.don.game.Slot

// 생성된 파일: tools/pixelart/make_art.py — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 원룸 그림(px_room_one_room) 안의 좌표. 단위는 그림 원본 픽셀. */
object RoomLayout {{
    const val WIDTH = {ROOM_W}
    const val HEIGHT = {ROOM_H}
    const val ITEM = 12

    /** 칸마다 물건이 놓이는 자리 (물건 그림의 왼쪽 위) */
    val slots: Map<Slot, List<IntOffset>> = mapOf(
{slots}
    )

    val character = IntOffset({CHARACTER_POS[0]}, {CHARACTER_POS[1]})

    /** 수납이 넘친 물건이 바닥에 쌓이는 자리 (채우는 순서) */
    val floorPile = listOf({pts(FLOOR_PILE)})

    /** 먹고 남은 쓰레기 자리 */
    val trash = listOf({pts(TRASH)})
}}
"""
    with open(LAYOUT_KT, "w") as f:
        f.write(kt)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(OUT, exist_ok=True)
    outputs = {}

    for mood, frames in sprites.CHARACTER.items():
        for i, rows in enumerate(frames):
            outputs[f"px_char_{mood}_{i}"] = grid_image(rows, f"char {mood} {i}")
    for name, rows in sprites.ITEMS.items():
        outputs[f"px_item_{name}"] = grid_image(rows, f"item {name}")
    for name, rows in sprites.CATEGORIES.items():
        outputs[f"px_cat_{name}"] = grid_image(rows, f"cat {name}")
    for name, rows in sprites.UI.items():
        outputs[f"px_ui_{name}"] = grid_image(rows, f"ui {name}")
    shadow = Image.new("RGBA", (14, 4), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([0, 0, 13, 3], fill=(43, 34, 52, 70))
    outputs["px_shadow"] = shadow
    room = draw_room()
    outputs["px_room_one_room"] = room

    for name, img in outputs.items():
        save(img, name)
    write_layout_kt()
    print(f"{len(outputs)}개 그림 → {OUT}")

    if preview:
        os.makedirs(preview, exist_ok=True)
        make_sheet(outputs, os.path.join(preview, "sheet.png"))
        make_scene(outputs, os.path.join(preview, "scene.png"))


def make_sheet(outputs, path, scale=6):
    small = {k: v for k, v in outputs.items() if v.width <= 16}
    cols = 8
    cell = 26 * scale
    rows = (len(small) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), hex_rgba("#f7f1e3"))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(small.items()):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x + (cell - big.width) // 2, y + (cell - big.height) // 2))
        d.text((x + 2, y + cell), name.replace("px_", ""), fill=(43, 34, 52, 255))
    sheet.save(path)


def make_scene(outputs, path, scale=5):
    """칸을 채운 방 미리보기"""
    room = outputs["px_room_one_room"].copy()
    fill = {
        "WARDROBE": ["clothes", "clothes", "bag", "clothes", "bag", "clothes"],
        "SHELF": ["book", "beauty", "toy", "living", "book", "etc"],
        "DESK": ["electronics", "book", "drink", "beauty"],
        "SHOE_RACK": ["shoes", "shoes", "shoes", "shoes"],
    }
    for slot, kinds in fill.items():
        for (x, y), k in zip(SLOTS[slot], kinds):
            room.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    room.alpha_composite(outputs["px_shadow"], (CHARACTER_POS[0] + 1, CHARACTER_POS[1] + 21))
    room.alpha_composite(outputs["px_char_happy_0"], CHARACTER_POS)
    pile = ["shoes", "clothes", "toy", "bag", "electronics", "shoes", "book"]
    for (x, y), k in sorted(zip(FLOOR_PILE, pile), key=lambda p: p[0][1]):
        room.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    for (x, y), k in zip(TRASH, ["meal_trash", "drink_trash", "meal_trash"]):
        room.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    room.resize((room.width * scale, room.height * scale), Image.NEAREST).save(path)


if __name__ == "__main__":
    main()
