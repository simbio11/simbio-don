"""
assets/pets/<품종>/sprites/NN_표정.png → app/src/main/res/drawable-nodpi/pet_<품종>_<표정>.png
그리고 app/.../ui/pixel/PetArt.kt (품종·표정 → R.drawable) 를 만든다.

    python3 tools/pixelart/import_pets.py

그림은 제미나이로 만든 270px 안팎 일러스트라 24×36 도트 아틀라스(make_art.py)와 따로 가져온다.
"""
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "assets/pets")
OUT = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
KT = os.path.join(ROOT, "app/src/main/kotlin/com/simbio/don/app/ui/pixel/PetArt.kt")
EXPRS = ["joy", "smile", "calm", "worry", "sad", "angry", "tease", "sleep"]


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
            shutil.copyfile(os.path.join(SRC, b, "sprites", files[e]), os.path.join(OUT, f"pet_{b}_{e}.png"))
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
