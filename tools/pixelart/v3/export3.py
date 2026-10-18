"""
캐릭터 v3 내보내기: 부위마다 24×36 칸 하나씩 아틀라스 한 장(px_char_atlas.png)에 담고,
부위 이름·색 표식·고를 수 있는 색·라벨을 Kotlin 파일(CharacterParts.kt)로 만든다.

앱은 표식 색을 고른 색으로 바꿔 부위를 차례로 겹쳐 그린다 (ui/kit/CharacterRenderer).
"""
from PIL import Image

from . import char3
from .clothes3 import TOPS, BOTTOMS, SHOES, HATS, ACCS
from .cpal import PALETTE, GROUPS, RAMPS
from .hair3 import HAIRS
from .lib import Canvas, img

COLS = 16


def _frame(draw):
    c = Canvas(char3.W, char3.H, char3.OY)
    draw(c)
    return img(c.rows())


def parts():
    """(이름, 그림) 목록. 이름은 앱에서 그대로 찾는다."""
    out = []

    def add(name, draw):
        out.append((name, _frame(draw)))

    for f in char3.FACE_SHAPES:
        add(f"body:{f}", lambda c, f=f: char3.body(c, f))
    for style in char3.EYE_STYLES:
        for state in ('open', 'sad', 'angry'):
            add(f"eyes:{style}:{state}", lambda c, s=style, st=state: c.stamp(char3.eyes_for(s, st), 5, 8))
    for state in char3.EYE_SHARED:
        add(f"eyes:shared:{state}", lambda c, st=state: c.stamp(char3.EYE_SHARED[st], 5, 8))
    add("lashes", lambda c: c.stamp(char3.LASHES, 5, 8))
    for style in char3.BROW_STYLES:
        for state in char3.BROW_STATES:
            add(f"brows:{style}:{state}", lambda c, s=style, st=state: c.stamp(char3.brows_for(s, st), 5, 6))
    for k, v in char3.NOSES.items():
        add(f"nose:{k}", lambda c, v=v: c.stamp(v['rows'], 10, 12))
    for style, v in char3.MOUTH_STYLES.items():
        for state in ('smile', 'calm'):
            add(f"mouth:{style}:{state}", lambda c, v=v, st=state: c.stamp(v[st], 10, 14))
    for state, rows in char3.MOUTH_SHARED.items():
        add(f"mouth:shared:{state}", lambda c, rows=rows: c.stamp(rows, 10, 14))
    add("blush", lambda c: c.stamp(char3.BLUSH, 5, 12))
    for k, pts in char3.EXTRAS.items():
        def draw(c, pts=pts):
            for x, y, ch in pts:
                c.set(x, y, ch)
        add(f"extra:{k}", draw)
    for k, h in HAIRS.items():
        for part in ('front', 'back'):
            if h.get(part):
                add(f"hair:{k}:{part}", lambda c, spec=h[part]: char3.stamp_part(c, spec))
    for prefix, table in (("top", TOPS), ("bottom", BOTTOMS), ("shoes", SHOES), ("hat", HATS), ("acc", ACCS)):
        for k, v in table.items():
            add(f"{prefix}:{k}", lambda c, spec=v['rows']: char3.stamp_part(c, spec))
    return out


def atlas(items):
    rows = (len(items) + COLS - 1) // COLS
    a = Image.new("RGBA", (COLS * char3.W, rows * char3.H), (0, 0, 0, 0))
    for i, (_, im) in enumerate(items):
        a.alpha_composite(im, ((i % COLS) * char3.W, (i // COLS) * char3.H))
    return a


def _argb(hexstr):
    h = hexstr.lstrip('#').upper()
    return f"0xFF{h}.toInt()"


def _kt_str(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'


def kotlin(items):
    # 표식 색은 서로 달라야 앱이 바꿀 수 있다
    values = [v.lower() for v in PALETTE.values()]
    assert len(values) == len(set(values)), "팔레트에 같은 색이 두 번 있어요"
    idx = ",\n".join(f"        {_kt_str(n)} to {i}" for i, (n, _) in enumerate(items))
    groups = "\n".join(
        f"    val {g.upper()} = intArrayOf({', '.join(_argb(PALETTE[m]) for m in ms)})" for g, ms in GROUPS.items()
    )

    def ramps(name):
        return ",\n".join(
            f"        {_kt_str(label)} to intArrayOf({', '.join(_argb(c) for c in cs)})" for label, cs in RAMPS[name].items()
        )

    def labels(table, extra=None):
        rows = []
        for k, v in table.items():
            more = extra(v) if extra else ""
            rows.append(f"        Part({_kt_str(k)}, {_kt_str(v['label'])}{more})")
        return ",\n".join(rows)

    moods = ",\n".join(
        f"        {_kt_str(m)} to Face({_kt_str(f['eyes'])}, {_kt_str(f['brow'])}, {_kt_str(f['mouth'])}, "
        f"{_kt_str(f['extra']) if f.get('extra') else 'null'})"
        for m, f in char3.MOOD_FACE.items()
    )
    return f"""package com.simbio.don.app.ui.pixel

// 생성된 파일: tools/pixelart/make_art.py (v3/export3.py) — 직접 고치지 말고 스크립트를 고친 뒤 다시 돌린다.

/** 캐릭터 부위 아틀라스(px_char_atlas) 색인과 색 표식. */
object CharacterParts {{
    const val W = {char3.W}
    const val H = {char3.H}
    const val COLS = {COLS}

    /** 머리 꼭대기 위 여유 줄 (모자·올림머리 자리) */
    const val TOP = {char3.OY}

    /** 부위 이름 → 아틀라스 칸 번호 */
    val index: Map<String, Int> = mapOf(
{idx},
    )

    // 색 묶음별 표식 색 (밝음 → 어둠)
{groups}

    /** 고를 수 있는 색 (이름 → 묶음 순서대로) */
    val skinTones: List<Pair<String, IntArray>> = listOf(
{ramps('skin')},
    )
    val hairColors: List<Pair<String, IntArray>> = listOf(
{ramps('hair')},
    )
    val clothColors: List<Pair<String, IntArray>> = listOf(
{ramps('cloth')},
    )
    val eyeColors: List<Pair<String, IntArray>> = listOf(
{ramps('eye')},
    )
    val hatColors: List<Pair<String, IntArray>> = listOf(
{ramps('hat')},
    )

    /** 고를 수 있는 부위: id, 이름 */
    data class Part(val id: String, val label: String, val flag: Boolean = false)

    val faceShapes = listOf(
{labels(char3.FACE_SHAPES)},
    )
    val eyeStyles = listOf(
{labels(char3.EYE_STYLES)},
    )
    val browStyles = listOf(
{labels(char3.BROW_STYLES)},
    )
    val noses = listOf(
{labels(char3.NOSES)},
    )
    val mouths = listOf(
{labels(char3.MOUTH_STYLES)},
    )

    /** flag = 귀가 보이는 머리 */
    val hairs = listOf(
{labels(HAIRS, lambda v: ', true' if v.get('ears') else '')},
    )

    /** flag = 원피스 (아래옷을 가린다) */
    val tops = listOf(
{labels(TOPS, lambda v: ', true' if v.get('dress') else '')},
    )
    val bottoms = listOf(
{labels(BOTTOMS)},
    )
    val shoes = listOf(
{labels(SHOES)},
    )
    val hats = listOf(
{labels(HATS)},
    )
    val accs = listOf(
{labels(ACCS)},
    )

    /** 기분마다 얼굴: 눈 상태, 눈썹 상태, 입 상태, 덧그림 */
    data class Face(val eyes: String, val brow: String, val mouth: String, val extra: String?)

    val moods: Map<String, Face> = mapOf(
{moods},
    )

    /** 공통 눈 (스타일 상관없이 같은 그림) */
    val sharedEyes = setOf({', '.join(_kt_str(k) for k in char3.EYE_SHARED)})
}}
"""
