"""
assets/pets/<품종>/sprites/NN_표정.png → app/src/main/res/drawable-nodpi/pet_<품종>_<표정>.png
그리고 app/.../ui/pixel/PetArt.kt (품종·표정 → R.drawable) 를 만든다.

    python3 tools/pixelart/import_pets.py

그림은 제미나이로 만든 270px 안팎 일러스트라 24×36 도트 아틀라스(make_art.py)와 따로 가져온다.
"""
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


def main():
    for f in os.listdir(OUT):
        if f.startswith("pet_") and f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    breeds = sorted(b for b in os.listdir(SRC) if os.path.isdir(os.path.join(SRC, b, "sprites")))
    lines = []
    for b in breeds:
        files = {re.sub(r"^\d+_", "", f[:-4]): f for f in os.listdir(os.path.join(SRC, b, "sprites")) if f.endswith(".png")}
        assert set(files) == set(EXPRS), f"{b}: 표정이 달라요 {sorted(files)}"
        for e in EXPRS:
            im = clean(Image.open(os.path.join(SRC, b, "sprites", files[e])))
            # 256색 팔레트로 (용량 절반) — 원본도 팔레트 그림이라 티 안 난다
            im.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(os.path.join(OUT, f"pet_{b}_{e}.png"), optimize=True)
            lines.append(f'        "{b}/{e}" to R.drawable.pet_{b}_{e},')
    kt = f"""package com.simbio.don.app.ui.pixel

import com.simbio.don.app.R

// 생성된 파일: tools/pixelart/import_pets.py — 직접 고치지 말고 assets/pets 를 바꾼 뒤 스크립트를 다시 돌린다.

/** 반려동물 그림: 품종 id/표정 → 그림 (표정: {', '.join(EXPRS)}) */
object PetArt {{
    val byKey: Map<String, Int> = mapOf(
{chr(10).join(lines)}
    )

    val breeds: List<String> = listOf({', '.join('"' + b + '"' for b in breeds)})
}}
"""
    with open(KT, "w") as f:
        f.write(kt)
    print(f"{len(breeds)}품종 × {len(EXPRS)}표정 → {OUT}")


main()
