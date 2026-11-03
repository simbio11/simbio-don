"""
assets/pets/<품종>/sprites/NN_표정.png → app/src/main/res/drawable-nodpi/pet_<품종>_<표정>.png
그리고 app/.../ui/pixel/PetArt.kt (품종·표정 → R.drawable) 를 만든다.

    python3 tools/pixelart/import_pets.py

그림은 제미나이로 만든 270px 안팎 일러스트라 24×36 도트 아틀라스(make_art.py)와 따로 가져온다.
"""
import json
import os
import re
from collections import deque

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "assets/pets")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/PetArt.kt")
EXPRS = ["joy", "smile", "calm", "worry", "sad", "angry", "tease", "sleep"]
RIM = 4          # 투명한 곳에서 이만큼(px) 안쪽까지를 가장자리로 본다
OPAQUE = 200     # 이보다 진하면 완전히 불투명으로 (몸 안이 살짝 비치지 않게)
CLEAR = 40       # 이보다 옅으면 지운다


def clean(src):
    """
    제미나이 그림의 마젠타 배경 찌꺼기 지우기.
    - 가장자리(투명에서 RIM px 안쪽)의 자줏빛(빨강·파랑이 초록보다 높음)을 빼서 원래 외곽선 색(어두운 갈색)으로
      — 털을 검정·흰색으로 바꾸면 이 자줏빛이 분홍 테두리로 보였다.
    - 몸 안은 완전히 불투명하게, 아주 옅은 반투명 먼지는 지운다.
    볼 터치·귀 안쪽 분홍은 가장자리가 아니라 그대로 둔다.
    """
    im = src.convert("RGBA")
    w, h = im.size
    px = im.load()
    dist = [[-1] * w for _ in range(h)]
    q = deque()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] < CLEAR:
                dist[y][x] = 0
                q.append((x, y))
    while q:
        x, y = q.popleft()
        d = dist[y][x]
        if d >= RIM:
            continue
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and dist[ny][nx] < 0:
                dist[ny][nx] = d + 1
                q.append((nx, ny))
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < CLEAR:
                px[x, y] = (0, 0, 0, 0)
                continue
            if 0 < dist[y][x] <= RIM:
                m = min(r, b) - g
                if m > 4:
                    # 섞인 마젠타 만큼 빨강·파랑을 덜어 낸다 (갈색 외곽선 쪽으로)
                    r, b = r - m, b - m
                    b = min(b, g)
            if a >= OPAQUE:
                a = 255
            px[x, y] = (max(r, 0), g, max(b, 0), a)
    return im


# ── 동작 프레임 (assets/pets/<품종>/anim, anim.json) ──────────────────────────
ANIMS = ["idle", "walk", "sit", "sleep", "play", "eat"]
# 그림체가 다르거나 깨진 것은 뺀다 (앱은 표정 그림으로 대신한다)
SKIP = {("corgi", "idle"), ("corgi", "play"), ("tabby_cat", "play"), ("beagle", "sleep")}
# 두 장모 고양이의 앉기·자기·먹기 그림이 서로 바뀌어 들어가 있다 (얼굴 무늬·눈 색으로 확인) → 바꿔 쓴다
FROM = {
    ("longhair_cat", "sit"): "longhair_tabby_cat", ("longhair_cat", "sleep"): "longhair_tabby_cat", ("longhair_cat", "eat"): "longhair_tabby_cat",
    ("longhair_tabby_cat", "sit"): "longhair_cat", ("longhair_tabby_cat", "sleep"): "longhair_cat", ("longhair_tabby_cat", "eat"): None,
}
REF_H = 210          # 표정 그림 몸 높이 (앉은 키)
LYING = 0.68         # 엎드리면 앉은 키의 이만큼
FPS = {"idle": 5, "walk": 7, "sit": 4, "sleep": 1, "play": 6, "eat": 4}


def side(im):
    """몸통·꼬리가 머리보다 오른쪽이면 +, 왼쪽이면 - (머리 쪽을 바라본다)"""
    a = im.getchannel("A").load()
    w, h = im.size
    pts = [(x, y) for y in range(0, h, 2) for x in range(0, w, 2) if a[x, y] > 40]
    if not pts:
        return 0.0
    top = min(y for _, y in pts)
    bot = max(y for _, y in pts)
    cut = top + (bot - top) * 0.45
    head = [x for x, y in pts if y < cut]
    low = [x for x, y in pts if y >= cut]
    if not head or not low:
        return 0.0
    return sum(low) / len(low) - sum(head) / len(head)


def bbox(im):
    return im.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()


def union(boxes):
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def save_q(im, path):
    im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(path, optimize=True)


def import_anims(b):
    """한 품종의 동작 프레임 → (동작 → 파일 이름 목록), 바라보는 쪽(왼쪽이면 True)"""
    js = os.path.join(SRC, b, "anim.json")
    if not os.path.exists(js):
        return {}, True
    frames = {}
    for name in ANIMS:
        src = FROM.get((b, name), b)
        if src is None or (b, name) in SKIP:
            continue
        m = json.load(open(os.path.join(SRC, src, "anim.json")))["anims"].get(name)
        if not m or len(m["files"]) < 2:
            continue
        frames[name] = [clean(Image.open(os.path.join(SRC, src, "anim", f))) for f in m["files"]]
    if not frames:
        return {}, True
    # 바라보는 쪽: 걷기 그림 기준. 반대로 그려진 칸은 뒤집어 맞춘다 (몸이 칸마다 좌우로 뒤집히면 어색하다)
    ref_frames = frames.get("walk") or next(iter(frames.values()))
    sides = sorted(side(f) for f in ref_frames)
    ref = sides[len(sides) // 2]
    faces_left = ref >= 0
    out = {}
    for name, ims in frames.items():
        fixed = []
        for im in ims:
            sd = side(im)
            if abs(sd) > 10 and (sd > 0) != (ref >= 0):
                im = im.transpose(Image.FLIP_LEFT_RIGHT)
            fixed.append(im)
        # 크기: 같은 동작 안의 상대 크기는 그대로, 가장 큰 칸을 앉은 키에 맞춘다 (자는 건 낮게)
        boxes = [bbox(im) for im in fixed]
        tall = max(bx[3] - bx[1] for bx in boxes)
        k = REF_H / tall * (LYING if name == "sleep" else 1.0)
        u = union(boxes)
        names = []
        for i, im in enumerate(fixed):
            c = im.crop((u[0] - 1, u[1] - 1, u[2] + 1, u[3] + 1))
            c = c.resize((max(1, round(c.width * k)), max(1, round(c.height * k))), Image.LANCZOS)
            fn = f"pet_{b}_m_{name}_{i + 1}"
            save_q(c, os.path.join(OUT, fn + ".png"))
            names.append(fn)
        out[name] = names
    return out, faces_left


def main():
    for f in os.listdir(OUT):
        if f.startswith("pet_") and f.endswith(".png"):  # 표정·동작 모두 다시 만든다
            os.remove(os.path.join(OUT, f))
    breeds = sorted(b for b in os.listdir(SRC) if os.path.isdir(os.path.join(SRC, b, "sprites")))
    lines = []
    alines = []
    for b in breeds:
        files = {re.sub(r"^\d+_", "", f[:-4]): f for f in os.listdir(os.path.join(SRC, b, "sprites")) if f.endswith(".png")}
        assert set(files) == set(EXPRS), f"{b}: 표정이 달라요 {sorted(files)}"
        ims = {e: clean(Image.open(os.path.join(SRC, b, "sprites", files[e]))) for e in EXPRS}
        # 8장을 같은 칸으로 잘라 발바닥(아래 끝)을 맞춘다
        u = union([bbox(im) for im in ims.values()])
        for e in EXPRS:
            im = ims[e].crop((u[0] - 1, u[1] - 1, u[2] + 1, u[3] + 1))
            # 256색 팔레트로 (용량 절반) — 원본도 팔레트 그림이라 티 안 난다
            im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(os.path.join(OUT, f"pet_{b}_{e}.png"), optimize=True)
            lines.append(f'        "{b}/{e}" to R.drawable.pet_{b}_{e},')
        anims, faces_left = import_anims(b)
        body = ", ".join(f'"{n}" to listOf(' + ", ".join(f"R.drawable.{f}" for f in fs) + ")" for n, fs in anims.items())
        alines.append(f'        "{b}" to PetMotion(mapOf({body}), facesLeft = {"true" if faces_left else "false"}),')
    kt = f"""package com.simbio.don.app.ui.pixel

import com.simbio.don.app.R

// 생성된 파일: tools/pixelart/import_pets.py — 직접 고치지 말고 assets/pets 를 바꾼 뒤 스크립트를 다시 돌린다.

/** 반려동물 그림: 품종 id/표정 → 그림 (표정: {', '.join(EXPRS)}) */
object PetArt {{
    val byKey: Map<String, Int> = mapOf(
{chr(10).join(lines)}
    )

    val breeds: List<String> = listOf({', '.join('"' + b + '"' for b in breeds)})

    /** 품종 → 동작 프레임 (idle·walk·sit·sleep·play·eat 중 있는 것) */
    val motions: Map<String, PetMotion> = mapOf(
{chr(10).join(alines)}
    )

    /** 동작마다 초당 프레임 */
    val fps: Map<String, Int> = mapOf({', '.join(f'"{k}" to {v}' for k, v in FPS.items())})
}}

/** 한 품종의 동작 그림. [facesLeft]: 그림이 왼쪽(머리 쪽)을 보고 있다 — 오른쪽으로 갈 땐 뒤집는다 */
data class PetMotion(val frames: Map<String, List<Int>>, val facesLeft: Boolean)
"""
    with open(KT, "w") as f:
        f.write(kt)
    print(f"{len(breeds)}품종 × {len(EXPRS)}표정 → {OUT}")


main()
