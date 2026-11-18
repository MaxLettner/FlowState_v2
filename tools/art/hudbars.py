"""Health / mana / experience bars in the top right of the HUD (`Menu/HudBar.java`), drawn x2.

Each bar is a forged iron trough that comes out of a diamond gem socket on the left and ends in a point:
- `HudBar_<ID>.png`: the empty bar (frame, socket and gem, dark trough, drop shadow).
- `HudFill_<ID>.png`: 3 frames, each `TROUGH_LEN + TILE` wide and as tall as the trough:
  0 the liquid (a seamless pattern repeating every TILE px, scrolled by the game),
  1 the same liquid washed out (the part just lost, which drains away behind the fill),
  2 the bright leading edge of the liquid in column 0.
The trough is always at (TROUGH_X, TROUGH_Y); its height differs per bar. Keep these in sync with HudBar.java.
"""
import math
import random
import sys
from PIL import Image

from pixel import *
from materials import CRIMSON, GOLD
from menu import IRON, SHADOW

SCALE = 2
TROUGH_X = 18
TROUGH_Y = 5
TROUGH_LEN = 128
TILE = 32
SOCKET_X = 8                 # centre column of every gem socket, so the three sockets line up
# iron collars around both ends of the trough
COLLARS = {TROUGH_X - 3, TROUGH_X - 2, TROUGH_X + TROUGH_LEN + 1, TROUGH_X + TROUGH_LEN + 2}
W = 156

OUT = hexc('#0b0a0e')
PIT = hexc('#110f15')
GLASS_SHADOW = hexc('#070609')
PALE = hexc('#f4ead9')

MANA = ramp('#d8e6ff', '#7fa6ff', '#4168ee', '#2b37b4', '#1a1b6e')
TOPAZ = ramp('#fff1b0', '#ffc23d', '#e08a1e', '#a35512', '#5e2a0b')
MOLTEN_GOLD = ramp('#fff6c4', '#f7d35a', '#d9a33a', '#9c6b1f', '#5e3f12')

# id: trough height (odd), liquid ramp, gem ramp, seed
BARS = {
    'HP': (7, CRIMSON, CRIMSON, 3),
    'MP': (5, MANA, MANA, 7),
    'XP': (3, MOLTEN_GOLD, TOPAZ, 11),
}


def geometry(th):
    """Socket radius, centre row, body half-height and the canvas height for a trough of height th."""
    r = th // 2 + 5
    cy = r
    half = th // 2 + 3
    return r, cy, half, 2 * r + 2


def body_mask(th):
    r, cy, half, h = geometry(th)
    xe = TROUGH_X + TROUGH_LEN + 2          # last full-height column
    mask = set()
    for x in range(SOCKET_X, xe + half + 1):
        k = max(0, x - xe)
        grow = 1 if x in COLLARS else 0     # the collars stand out of the frame
        for y in range(cy - half + k - grow, cy + half - k + grow + 1):
            mask.add((x, y))
    return mask


def draw_body(px, th, rng):
    r, cy, half, h = geometry(th)
    mask = body_mask(th)
    trough = {(x, y) for x in range(TROUGH_X, TROUGH_X + TROUGH_LEN) for y in range(TROUGH_Y, TROUGH_Y + th)}
    for (x, y) in mask:
        if (x, y) in trough:
            continue
        if any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = OUT
            continue
        top = min(yy for (xx, yy) in mask if xx == x)
        bot = max(yy for (xx, yy) in mask if xx == x)
        dt, db = y - top, bot - y
        collar = x in COLLARS
        if dt == 1:
            c = IRON[0] if collar else IRON[1]
        elif db == 1:
            c = IRON[3]
        elif dt == 2:
            c = IRON[1] if collar else IRON[2]
        elif db == 2:
            c = IRON[2] if collar else IRON[3]
        else:
            c = IRON[1] if collar else IRON[2] if y < cy else IRON[3]
        if y == TROUGH_Y + th and TROUGH_X <= x < TROUGH_X + TROUGH_LEN:
            c = IRON[1]                         # lit lower lip: the glass sits in a recess
        if collar and x == max(COLLARS & {x - 1, x, x + 1}):
            c = IRON[min(4, IRON.index(c) + 1)]    # shaded right side of a collar
        # pitted, hand-forged iron
        elif c not in (IRON[0], IRON[1]) and rng.random() < 0.08:
            c = IRON[min(4, IRON.index(c) + 1)]
        px[x, y] = c

    # rivets at the quarter marks and on the end plate
    for k in (1, 2, 3):
        x = TROUGH_X + TROUGH_LEN * k // 4 - 1
        for y0 in (cy - half + 1, cy + half - 2):
            rivet(px, x, y0)

    # the dark, empty glass
    tint = mix(GLASS_SHADOW, BARS_TINT[th], 0.35)
    floor = mix(GLASS_SHADOW, BARS_TINT[th], 0.6)
    for (x, y) in trough:
        if y == TROUGH_Y or x == TROUGH_X:
            c = GLASS_SHADOW
        elif y == TROUGH_Y + th - 1:
            c = floor
        else:
            c = tint
        px[x, y] = c


def rivet(px, x, y):
    px[x, y] = IRON[0]
    px[x + 1, y] = IRON[1]
    px[x, y + 1] = IRON[2]
    px[x + 1, y + 1] = PIT


def quadrant(dx, dy):
    """0 top-left (lit) .. 3 bottom-right (shadow)."""
    if dy < 0 or (dy == 0 and dx < 0):
        return 0 if dx <= 0 else 1
    return 2 if dx < 0 else 3


def draw_socket(px, th, gem):
    r, cy, half, h = geometry(th)
    rings = 2 if r >= 7 else 1
    g = r - rings - 2                       # gem radius
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            d = abs(dx) + abs(dy)
            if d > r:
                continue
            q = quadrant(dx, dy)
            if d == r:
                c = OUT
            elif d > r - 1 - rings:
                inner = d == r - rings and rings > 1
                c = IRON[(2, 3, 2, 1)[q]] if inner else IRON[(0, 1, 2, 3)[q]]
            elif d == g + 1:
                c = GOLD[(1, 2, 2, 3)[q]]
            elif d <= g // 2:
                c = gem[1] if q < 3 else gem[2]
            else:
                c = gem[(1, 2, 3, 4)[q]] if d == g else gem[(1, 2, 2, 3)[q]]
            px[SOCKET_X + dx, cy + dy] = c
    # glint on the table of the gem
    px[SOCKET_X - max(1, g // 3), cy - max(1, g // 3)] = gem[0]
    if g >= 4:
        px[SOCKET_X - 1, cy - 2] = hexc('#ffffff')
        px[SOCKET_X - 2, cy - 1] = gem[0]


def add_shadow(img):
    w, h = img.size
    src = img.load()
    out = new(w, h)
    op = out.load()
    for x in range(w - 1):
        for y in range(h - 1):
            if src[x, y][3] == 255:
                op[x + 1, y + 1] = SHADOW
    out.alpha_composite(img)
    return out


BARS_TINT = {th: liquid[4] for th, liquid, gem, seed in BARS.values()}


def bar(th, gem, seed):
    r, cy, half, h = geometry(th)
    img = new(W, h)
    px = img.load()
    draw_body(px, th, random.Random(seed))
    draw_socket(px, th, gem)
    return add_shadow(img)


def liquid_tile(th, liquid, seed):
    """One seamless TILE-wide piece of the liquid: a lip in the shadow of the frame, a broken highlight
    line, rolling darker swells in the body and a few sparks; everything repeats every TILE px."""
    rng = random.Random(seed)
    tile = new(TILE, th)
    px = tile.load()
    p1, p2 = rng.random() * 6.28, rng.random() * 6.28
    for x in range(TILE):
        a = 2 * math.pi * x / TILE
        for y in range(th):
            t = y / max(1, th - 1)
            v = 0.1 + 0.8 * t + 0.08 * math.sin(a + y * 0.7 + p1) + 0.04 * math.sin(2 * a - y * 1.1 + p2)
            idx = 1 + dither_index(min(1, max(0, v)), x, y, 4, band=0.07)
            if y == 0:
                idx = max(idx, 2)            # the lip under the frame
            px[x, y] = liquid[idx]
    # broken highlight line, two dashes per tile
    hy = 1 if th > 3 else 0
    for start, length in ((rng.randrange(0, 8), 6), (rng.randrange(16, 22), 4)):
        for i in range(length):
            px[(start + i) % TILE, hy] = liquid[0]
    # sparks deeper in the liquid
    for _ in range(2 if th > 3 else 1):
        x, y = rng.randrange(TILE), rng.randrange(min(th - 1, hy + 1), th)
        px[x, y] = liquid[0] if y < th - 1 else liquid[1]
    return tile


def fill_frames(th, liquid, seed):
    tile = liquid_tile(th, liquid, seed)
    w = TROUGH_LEN + TILE
    fill = new(w, th)
    for x in range(0, w, TILE):
        fill.paste(tile, (x, 0))
    drain = fill.copy()
    dp = drain.load()
    for x in range(w):
        for y in range(th):
            dp[x, y] = mix(dp[x, y], PALE, 0.45)
    edge = new(w, th)
    ep = edge.load()
    for y in range(th):
        t = y / max(1, th - 1)
        ep[0, y] = liquid[2] if y == 0 else liquid[0] if t < 0.5 else liquid[1]
    return [fill, drain, edge]


def generate(out_dir):
    for bid, (th, liquid, gem, seed) in BARS.items():
        bar(th, gem, seed).save(f'{out_dir}/HudBar_{bid}.png')
        strip(fill_frames(th, liquid, seed)).save(f'{out_dir}/HudFill_{bid}.png')


def compose(th, liquid, gem, seed, value, drained=0.0, scroll=0):
    """The bar as the game draws it (art pixels, before the x2)."""
    img = bar(th, gem, seed)
    fill, drain, edge = fill_frames(th, liquid, seed)
    n = round(TROUGH_LEN * value)
    m = round(TROUGH_LEN * min(1.0, value + drained))
    if m > n:
        img.alpha_composite(drain.crop((scroll, 0, scroll + m, th)), (TROUGH_X, TROUGH_Y))
    if n > 0:
        img.alpha_composite(fill.crop((scroll, 0, scroll + n, th)), (TROUGH_X, TROUGH_Y))
        img.alpha_composite(edge.crop((0, 0, 1, th)), (TROUGH_X + n - 1, TROUGH_Y))
    return img


LAYOUT_RIGHT = 36            # screen px between the bars and the right edge
LAYOUT_TOP = 30
LAYOUT_GAP = 2               # art px between two bars


def layout():
    """Screen position of each bar (x2)."""
    pos, y = {}, LAYOUT_TOP
    for bid, (th, *_rest) in BARS.items():
        pos[bid] = (1920 - LAYOUT_RIGHT - W * SCALE, y)
        y += (geometry(th)[3] + LAYOUT_GAP) * SCALE
    return pos


def preview(path, bg=None):
    """The three bars on a game screenshot (or a dark backdrop) at x2, and zoomed x6 below."""
    values = {'HP': (0.62, 0.18), 'MP': (0.55, 0.0), 'XP': (0.34, 0.0)}
    scene = Image.open(bg).convert('RGBA').crop((1300, 0, 1920, 180)) if bg else Image.new('RGBA', (620, 180), (40, 14, 14, 255))
    zoom = 6
    out = Image.new('RGBA', (max(620, W * zoom + 20), 180 + 3 * (20 * zoom + 10) + 10), (20, 18, 24, 255))
    out.paste(scene, (0, 0))
    zy = 190
    for bid, (x, y) in layout().items():
        th, liquid, gem, seed = BARS[bid]
        img = compose(th, liquid, gem, seed, *values[bid])
        big = img.resize((img.width * SCALE, img.height * SCALE), Image.Resampling.NEAREST)
        out.alpha_composite(big, (x - 1300, y))
        z = img.resize((img.width * zoom, img.height * zoom), Image.Resampling.NEAREST)
        out.alpha_composite(z, (10, zy))
        zy += z.height + 10
    out.save(path)


if __name__ == '__main__':
    preview(sys.argv[1] if len(sys.argv) > 1 else 'hudbars_preview.png', sys.argv[2] if len(sys.argv) > 2 else None)
