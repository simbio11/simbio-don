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
from palette import MARKERS, PALETTE, THEMES  # noqa: E402
import room  # noqa: E402
import sprites  # noqa: E402
from v3 import char3, export3, items16, lib as lib3, products16  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
LAYOUT_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/RoomLayout.kt")
PARTS_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/CharacterParts.kt")
COLORS_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/RoomColors.kt")
PRODUCTS_KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/ProductIcons.kt")


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

    # 카드·가게용 16×16 (방 안의 물건은 12×12 그대로)
    outputs.update(items16.outputs())
    # 돈't 몰 상품마다 16×16 (도감·몰·카드)
    outputs.update(products16.outputs())

    # 집 단계마다 방 바탕과 시간대 레이어 (벽지·바닥·러그·커튼·이불은 표식 색 — 앱이 바꾼다)
    for level in room.LEVELS:
        outputs[f"px_room_{level}"] = room.draw_base(level)
        outputs[f"px_light_day_{level}"] = room.sunlight("day", level)
        outputs[f"px_light_sunset_{level}"] = room.sunlight("sunset", level)
        outputs[f"px_night_shade_{level}"] = room.night_shade(level)
        outputs[f"px_night_lights_{level}"] = room.night_lights(level)
    check_markers(outputs)
    for phase in room.PHASES:
        outputs[f"px_sky_{phase}"] = room.sky(phase)
        outputs[f"px_sky_{phase}_overcast"] = room.sky(phase, overcast=True)
    return outputs


def _pixels(img):
    return list(img.get_flattened_data() if hasattr(img, "get_flattened_data") else img.getdata())


def check_markers(outputs):
    """표식 색은 서로 달라야 하고, 방 바탕에서 표식 아닌 곳에 쓰이면 안 된다 (앱이 그 색만 찾아 바꾸므로)"""
    flat = [c.lower() for cs in MARKERS.values() for c in cs]
    assert len(flat) == len(set(flat)), "표식 색이 겹쳐요"
    for k, themes in THEMES.items():
        for name, ramp in themes:
            assert len(ramp) == len(MARKERS[k]), f"{k} {name}: 색 수가 표식과 달라요"
    marks = {room.rgba(c)[:3] for c in flat}
    for level in room.LEVELS:
        data = _pixels(outputs[f"px_room_{level}"])
        used = {p[:3] for p in data if p[3] == 255}
        for k, cs in MARKERS.items():
            assert any(room.rgba(c)[:3] in used for c in cs), f"{level}: {k} 표식이 안 쓰였어요"
        # 반투명 칸은 표식과 같은 색이면 안 됨
        semi = {p[:3] for p in data if 0 < p[3] < 255}
        assert not (semi & marks), f"{level}: 반투명 칸에 표식 색"


def write_products_kt():
    ids = sorted(products16.all_rows(), key=lambda k: (k[0], int(k[1:])))
    lines = "\n".join(f'        "{k}" to R.drawable.px16_p_{k},' for k in ids)
    kt = f"""package com.simbio.don.app.ui.pixel

import com.simbio.don.app.R

// 생성된 파일: tools/pixelart/make_art.py (v3/products16.py) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 돈't 몰 상품 id (core Catalog) → 16×16 도트 */
object ProductIcons {{
    val byId: Map<String, Int> = mapOf(
{lines}
    )
}}
"""
    with open(PRODUCTS_KT, "w") as f:
        f.write(kt)


def write_colors_kt():
    def arr(cs):
        return "intArrayOf(" + ", ".join(f"0xFF{c.lstrip('#').upper()}.toInt()" for c in cs) + ")"

    def themes(k):
        return ",\n".join(f'        "{n}" to {arr(cs)}' for n, cs in THEMES[k])

    kt = f"""package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (palette.MARKERS·THEMES) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 방 인테리어 색 바꾸기: 방 그림(px_room_*)에서 표식 색을 찾아 고른 색 묶음으로 바꾼다 (밝음 → 어둠). */
object RoomColors {{
    val WALL = {arr(MARKERS["WALL"])}
    val FLOOR = {arr(MARKERS["FLOOR"])}
    val RUG = {arr(MARKERS["RUG"])}
    val CURTAIN = {arr(MARKERS["CURTAIN"])}
    val BEDDING = {arr(MARKERS["BEDDING"])}

    /** 고를 수 있는 색 (첫째가 기본) */
    val walls: List<Pair<String, IntArray>> = listOf(
{themes("WALL")},
    )
    val floors: List<Pair<String, IntArray>> = listOf(
{themes("FLOOR")},
    )
    val rugs: List<Pair<String, IntArray>> = listOf(
{themes("RUG")},
    )
    val curtains: List<Pair<String, IntArray>> = listOf(
{themes("CURTAIN")},
    )
    val beddings: List<Pair<String, IntArray>> = listOf(
{themes("BEDDING")},
    )
}}
"""
    with open(COLORS_KT, "w") as f:
        f.write(kt)


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
    glass_by_level = "\n".join(f'        "{lv.upper()}" to {rect(g)},' for lv, g in room.GLASS_BY_LEVEL.items())
    kt = f"""package com.simbio.don.app.ui.pixel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import com.simbio.don.game.Slot

// 생성된 파일: tools/pixelart/make_art.py — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 방 그림(px_room_*) 안의 좌표 — 세 집 모두 가구 자리가 같다. 단위는 그림 원본 픽셀. */
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

    /** 집 단계(HouseLevel 이름)마다 유리창 — 투룸·아파트는 창이 더 넓다 */
    val glassByLevel: Map<String, IntRect> = mapOf(
{glass_by_level}
    )
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
    write_colors_kt()
    write_products_kt()
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
        for level in room.LEVELS:
            for phase in room.PHASES:
                make_scene(outputs, phase, level).resize((room.W * 5, room.H * 5), Image.NEAREST).save(
                    os.path.join(preview, f"scene_{level}_{phase}.png"))


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


def make_scene(outputs, phase, level="one_room"):
    """앱과 같은 순서로 겹친 미리보기 (칸을 채워서)"""
    out = Image.new("RGBA", (room.W, room.H), (0, 0, 0, 255))
    gx, gy, gw, gh = room.GLASS_BY_LEVEL[level]
    sky = outputs[f"px_sky_{phase}"]
    x = gx - 8
    while x < gx + gw:
        layer = Image.new("RGBA", out.size, (0, 0, 0, 0))
        layer.alpha_composite(sky, (x, gy)) if x >= 0 else layer.alpha_composite(sky.crop((-x, 0, sky.width, sky.height)), (0, gy))
        mask = Image.new("L", out.size, 0)
        mask.paste(255, (gx, gy, gx + gw, gy + gh))
        out.paste(layer, (0, 0), mask)
        x += sky.width
    out.alpha_composite(outputs[f"px_room_{level}"])
    if phase != "night":
        out.alpha_composite(outputs[f"px_light_{phase}_{level}"])
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
        out.alpha_composite(outputs[f"px_night_shade_{level}"])
        out.alpha_composite(outputs[f"px_night_lights_{level}"])
    return out


if __name__ == "__main__":
    main()
