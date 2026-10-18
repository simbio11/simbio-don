#!/usr/bin/env python3
"""
도트 그림 생성기.

    python3 tools/pixelart/make_art.py [미리보기_폴더]

- app/src/main/res/drawable-nodpi/px_*.png  : 게임 그림 (방·캐릭터·물건). 원본 픽셀 크기, 앱이 확대한다.
  가계부 쪽 아이콘은 도트가 아니라 res/drawable/ic_*.xml (Material Symbols).
- app/src/main/kotlin/.../ui/pixel/RoomLayout.kt : 방 안 좌표 (이 스크립트가 생성)
- 미리보기_폴더/sheet.png, scene_*.png : 확인용 (리포에 넣지 않음)

그림을 손으로 다시 그리고 싶으면 같은 이름·같은 크기의 PNG로 덮어쓰면 된다.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from palette import PALETTE  # noqa: E402
import room  # noqa: E402
import sprites  # noqa: E402
from v3 import char3, export3, lib as lib3  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
LAYOUT_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/RoomLayout.kt")
PARTS_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/CharacterParts.kt")


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
                img.putpixel((x, y), room.col(ch))
    return img


def build():
    outputs = {}
    # 캐릭터 v3: 부위 아틀라스 (앱이 색을 바꿔 겹쳐 그린다)
    outputs["px_char_atlas"] = export3.atlas(export3.parts())
    for name, rows in sprites.ITEMS.items():
        outputs[f"px_item_{name}"] = grid_image(rows, f"item {name}")

    shadow = Image.new("RGBA", (16, 4), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([0, 0, 15, 3], fill=(43, 34, 52, 70))
    outputs["px_shadow"] = shadow

    outputs["px_room_one_room"] = room.draw_base()
    for phase in room.PHASES:
        outputs[f"px_sky_{phase}"] = room.sky(phase)
        outputs[f"px_sky_{phase}_overcast"] = room.sky(phase, overcast=True)
    outputs["px_light_day"] = room.sunlight("day")
    outputs["px_light_sunset"] = room.sunlight("sunset")
    outputs["px_night_shade"] = room.night_shade()
    outputs["px_night_lights"] = room.night_lights()
    return outputs


MIPMAP_SCALES = {"mdpi": 2, "hdpi": 3, "xhdpi": 4, "xxhdpi": 6, "xxxhdpi": 8}


def write_launcher_icon():
    """런처 아이콘 앞면(색)과 테마 아이콘용 흑백 앞면. 54칸 격자, 기기 밀도마다 정수배."""
    art = grid_image(sprites.ICON, "icon")
    canvas = Image.new("RGBA", (54, 54), (0, 0, 0, 0))
    canvas.alpha_composite(art, ((54 - art.width) // 2, (54 - art.height) // 2 + 1))
    mono = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    k = room.col("K")
    for y in range(canvas.height):
        for x in range(canvas.width):
            p = canvas.getpixel((x, y))
            if p[3] and p[:3] != k[:3]:
                mono.putpixel((x, y), (255, 255, 255, 255))
    res = os.path.join(ROOT, "app/src/main/res")
    for dens, s in MIPMAP_SCALES.items():
        d = os.path.join(res, f"mipmap-{dens}")
        os.makedirs(d, exist_ok=True)
        canvas.resize((54 * s, 54 * s), Image.NEAREST).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        mono.resize((54 * s, 54 * s), Image.NEAREST).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
    return canvas


def save(img, name):
    path = os.path.join(OUT, f"{name}.png")
    img.save(path, optimize=True)
    return path


def write_layout_kt():
    def pts(lst):
        return ", ".join(f"IntOffset({x}, {y})" for x, y in lst)

    def rect(r):
        x, y, w, h = r
        return f"IntRect({x}, {y}, {x + w}, {y + h})"

    slots = "\n".join(f"        Slot.{k} to listOf({pts(v)})," for k, v in room.SLOTS.items())
    hits = "\n".join(f"        Slot.{k} to {rect(v)}," for k, v in room.HITS.items())
    cx, cy = room.CHARACTER_POS
    kt = f"""package com.simbio.don.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.simbio.don.game.Slot

// 생성된 파일: tools/pixelart/make_art.py — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 원룸 그림(px_room_one_room) 안의 좌표. 단위는 그림 원본 픽셀. */
object RoomLayout {{
    const val WIDTH = {room.W}
    const val HEIGHT = {room.H}
    const val ITEM = 12

    /** 칸마다 물건이 놓이는 자리 (물건 그림의 왼쪽 위) */
    val slots: Map<Slot, List<IntOffset>> = mapOf(
{slots}
    )

    /** 누르면 그 칸을 여는 자리 */
    val hits: Map<Slot, IntRect> = mapOf(
{hits}
    )
    val trashHit = {rect(room.TRASH_HIT)}
    val floorHit = {rect(room.FLOOR_HIT)}

    const val CHAR_W = {room.CHAR_W}
    const val CHAR_H = {room.CHAR_H}
    val character = IntOffset({cx}, {cy})

    /** 캐릭터가 걸어 다니는 x 범위 (왼쪽 위 기준) */
    const val WALK_MIN = {room.WALK_X[0]}
    const val WALK_MAX = {room.WALK_X[1]}

    /** 수납이 넘친 물건이 바닥에 쌓이는 자리 (채우는 순서) */
    val floorPile = listOf({pts(room.FLOOR_PILE)})

    /** 먹고 남은 쓰레기 자리 */
    val trash = listOf({pts(room.TRASH)})

    /** 하늘이 비치는 유리창. 하늘 그림(px_sky_*)은 가로 {room.SKY_W}px로 이어 붙여 흘린다. */
    val glass = {rect(room.GLASS)}
    const val SKY_W = {room.SKY_W}
}}
"""
    with open(LAYOUT_KT, "w") as f:
        f.write(kt)


def main():
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    os.makedirs(OUT, exist_ok=True)
    outputs = build()
    # 예전 이름 정리
    for f in os.listdir(OUT):
        if f.startswith("px_") and f[:-4] not in outputs:
            os.remove(os.path.join(OUT, f))
    for name, img in outputs.items():
        save(img, name)
    write_layout_kt()
    with open(PARTS_KT, "w") as f:
        f.write(export3.kotlin(export3.parts()))
    icon = write_launcher_icon()
    print(f"{len(outputs)}개 그림 → {OUT}, 런처 아이콘")

    if preview:
        os.makedirs(preview, exist_ok=True)
        make_sheet(outputs, os.path.join(preview, "sheet.png"))
        # 캐릭터 v3: 남녀 기본 모습 × 표정 7
        lib3.sheet(char3.preview(), os.path.join(preview, "characters.png"), scale=6, cols=len(char3.MOOD_FACE))
        bg = Image.new("RGBA", icon.size, room.rgba("#346AFF"))
        bg.alpha_composite(icon)
        bg.resize((54 * 6, 54 * 6), Image.NEAREST).save(os.path.join(preview, "icon.png"))
        for phase in room.PHASES:
            make_scene(outputs, phase).resize((room.W * 5, room.H * 5), Image.NEAREST).save(
                os.path.join(preview, f"scene_{phase}.png"))


def make_sheet(outputs, path, scale=6):
    small = {k: v for k, v in outputs.items() if v.width <= 22}
    cols = 9
    cell = 30 * scale
    rows = (len(small) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), room.rgba("#f7f1e3"))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(small.items()):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x + (cell - big.width) // 2, y + (cell - big.height) // 2))
        d.text((x + 2, y + cell), name.replace("px_", ""), fill=(43, 34, 52, 255))
    sheet.save(path)


def lib_img(rows):
    from v3.lib import img
    return img(rows)


def make_scene(outputs, phase):
    """앱과 같은 순서로 겹친 미리보기 (칸을 채워서)"""
    out = Image.new("RGBA", (room.W, room.H), (0, 0, 0, 255))
    gx, gy, gw, gh = room.GLASS
    out.alpha_composite(outputs[f"px_sky_{phase}"].crop((8, 0, 8 + gw, gh)), (gx, gy))
    out.alpha_composite(outputs["px_room_one_room"])
    if phase != "night":
        out.alpha_composite(outputs[f"px_light_{phase}"])
    fill = {
        "WARDROBE": ["clothes", "clothes", "bag", "clothes", "bag", "clothes"],
        "SHELF": ["book", "beauty", "toy", "living", "book", "etc"],
        "DESK": ["electronics", "book", "drink", "beauty"],
        "SHOE_RACK": ["shoes", "shoes", "shoes", "shoes"],
    }
    for slot, kinds in fill.items():
        for (x, y), k in zip(room.SLOTS[slot], kinds):
            out.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    for (x, y), k in zip(room.TRASH, ["meal_trash", "drink_trash", "meal_trash"]):
        out.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    cx, cy = room.CHARACTER_POS
    out.alpha_composite(outputs["px_shadow"], (cx + 4, cy + room.CHAR_H - 3))
    out.alpha_composite(lib_img(char3.compose(char3.LOOKS["m"], "happy").rows()), (cx, cy))
    pile = ["shoes", "clothes", "toy", "bag", "electronics", "shoes", "book"]
    for (x, y), k in sorted(zip(room.FLOOR_PILE, pile), key=lambda p: p[0][1]):
        out.alpha_composite(outputs[f"px_item_{k}"], (x, y))
    if phase == "sunset":
        out.alpha_composite(Image.new("RGBA", out.size, (255, 140, 80, 34)))
    if phase == "night":
        out.alpha_composite(outputs["px_night_shade"])
        out.alpha_composite(outputs["px_night_lights"])
    return out


if __name__ == "__main__":
    main()
