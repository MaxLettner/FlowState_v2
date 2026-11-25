"""Small pixel-art helpers shared by the sprite generators."""
import math
from PIL import Image

OUTLINE = (24, 18, 30, 255)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ramp(*hexes):
    """Colour ramp from highlight (index 0) to deepest shadow (last)."""
    return [hexc(h) for h in hexes]


def mix(c1, c2, t):
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(4))


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dither_index(value, x, y, levels, band=0.18):
    """Quantise value (0..1, 0 = brightest) to a ramp index.
    Only a narrow band around each step boundary is checker-dithered, which keeps
    large areas clean (hand-pixelled look) while softening the transitions."""
    v = value * (levels - 1)
    base = int(math.floor(v))
    frac = v - base
    if abs(frac - 0.5) < band:
        idx = base + ((x + y) % 2)
    else:
        idx = base + (1 if frac > 0.5 else 0)
    return max(0, min(levels - 1, idx))


def new(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def outline(img, color=OUTLINE, diagonal=False):
    """Adds a 1px outline around every opaque pixel (in place, returns img)."""
    w, h = img.size
    src = img.load()
    mask = [[src[x, y][3] > 0 for y in range(h)] for x in range(w)]
    out = img.copy()
    px = out.load()
    nbrs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonal:
        nbrs += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
    for x in range(w):
        for y in range(h):
            if mask[x][y]:
                continue
            for dx, dy in nbrs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and mask[nx][ny]:
                    px[x, y] = color
                    break
    return out


def strip(frames):
    """Concatenates equally sized frames horizontally."""
    w, h = frames[0].size
    s = new(w * len(frames), h)
    for i, f in enumerate(frames):
        s.paste(f, (i * w, 0), f)
    return s


def put(px, w, h, x, y, c):
    if 0 <= x < w and 0 <= y < h:
        px[x, y] = c


def ellipse(px, w, h, cx, cy, rx, ry, c):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                put(px, w, h, x, y, c)


def line(px, w, h, x0, y0, x1, y1, c):
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / max(1, n)
        put(px, w, h, int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)
