"""v3 도트 그림 공용 도구: 격자 캔버스, 글자 격자 → 이미지."""
from PIL import Image

from .cpal import PALETTE


class Canvas:
    """글자 격자 캔버스. '.', '_', ' ' 는 그리지 않는다 (아래가 그대로 보임)."""

    def __init__(self, w, h, oy=0):
        """oy: 원점 아래로 밀기. oy=4면 y=-4 줄까지 그릴 수 있다 (머리 위 모자·올림머리 자리)."""
        self.w, self.h, self.oy = w, h, oy
        self.px = [['.'] * w for _ in range(h)]

    def set(self, x, y, ch):
        y += self.oy
        if 0 <= y < self.h and 0 <= x < self.w and ch not in '._ ':
            self.px[y][x] = ch

    def get(self, x, y):
        y += self.oy
        if 0 <= y < self.h and 0 <= x < self.w:
            return self.px[y][x]
        return '.'

    def stamp(self, rows, x=0, y=0):
        for dy, r in enumerate(rows):
            for dx, ch in enumerate(r):
                self.set(x + dx, y + dy, ch)
        return self

    def rows(self):
        return [''.join(r) for r in self.px]


def check(name, rows, w=None, palette=PALETTE):
    w = w or len(rows[0])
    for i, r in enumerate(rows):
        assert len(r) == w, f"{name} {i}번째 줄 길이 {len(r)} != {w}: {r!r}"
        for ch in r:
            assert ch in '._ #' or ch in palette, f"{name}: 모르는 색 {ch!r} ({i}번째 줄)"
    return rows


def row(*segs, w=24):
    """row(7, "ttt", 12, "SS") → 7칸부터 "ttt", 12칸부터 "SS" 를 놓은 w칸 줄. 손으로 칸 세다 틀리지 않게."""
    r = ['.'] * w
    for i in range(0, len(segs), 2):
        x, s = segs[i], segs[i + 1]
        assert x + len(s) <= w, f"줄 넘침: {x} + {s!r}"
        for k, ch in enumerate(s):
            r[x + k] = ch
    return ''.join(r)


def rgba(hexstr, a=255):
    h = hexstr.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def img(rows, palette=PALETTE):
    w = len(rows[0])
    im = Image.new('RGBA', (w, len(rows)), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch not in '._ ':
                im.putpixel((x, y), rgba(palette[ch]))
    return im


def mirror(rows):
    return [r[::-1] for r in rows]


def sheet(ims, out, scale=10, bg=(242, 244, 246, 255), cols=None):
    """미리보기: 그림들을 한 줄(또는 cols개씩)로 늘어놓는다."""
    cols = cols or len(ims)
    pad = 3 * scale
    cw = max(i.width for i in ims) * scale + pad
    ch = max(i.height for i in ims) * scale + pad
    nrows = (len(ims) + cols - 1) // cols
    s = Image.new('RGBA', (cols * cw + pad, nrows * ch + pad), bg)
    for n, i in enumerate(ims):
        x = pad + (n % cols) * cw
        y = pad + (n // cols) * ch
        s.alpha_composite(i.resize((i.width * scale, i.height * scale), Image.NEAREST), (x, y))
    s.save(out)
