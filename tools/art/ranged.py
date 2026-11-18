"""Bows (upright, facing right, 3 frames: rest / half draw / full draw) and crossbows
(horizontal, facing right, 2 frames: loaded / empty).

Conventions for the Java side (see PIVOTS):
  bows:      grip centre at (GRIP_X, H/2) of each frame; the arrow leaves to the right.
  crossbows: grip/trigger at PIVOTS[name]; the bolt leaves to the right along the stock top.
Intended display scale x2.
"""
import math
from pixel import *
from materials import *

LIGHT = (-1 / math.sqrt(2), -1 / math.sqrt(2))  # light comes from the top-left
STRING = ramp('#fbf7ec', '#e2dccb', '#b8ae98', '#857c69', '#58513f')
FLETCH_RED = CRIMSON
FLETCH_GREEN = GREEN
POISON = ramp('#eaffb8', '#a8f046', '#5fc22a', '#2f8a22', '#175214')

PIVOTS = {}


def shade(q):
    """q: -1 (shadow side) .. 1 (lit side) -> ramp index."""
    if q > 0.6:
        return 0
    if q > 0.15:
        return 1
    if q > -0.3:
        return 2
    if q > -0.75:
        return 3
    return 4


def stroke(img, pts, radius, mat, band=None):
    """Paints a thick shaded curve. pts: list of (x, y) samples; radius(i/len) -> px.
    band(frac) -> optional ramp override for that sample (decor bands, wraps)."""
    w, h = img.size
    px = img.load()
    n = len(pts)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    rmax = max(radius(i / (n - 1)) for i in range(n)) + 1
    x0, x1 = int(min(xs) - rmax), int(max(xs) + rmax) + 1
    y0, y1 = int(min(ys) - rmax), int(max(ys) + rmax) + 1
    for y in range(max(0, y0), min(h, y1 + 1)):
        for x in range(max(0, x0), min(w, x1 + 1)):
            cx, cy = x + 0.5, y + 0.5
            best, bi = 1e9, 0
            for i, (sx, sy) in enumerate(pts):
                dd = (cx - sx) ** 2 + (cy - sy) ** 2
                if dd < best:
                    best, bi = dd, i
            frac = bi / (n - 1)
            r = radius(frac)
            if math.sqrt(best) > r:
                continue
            dx, dy = cx - pts[bi][0], cy - pts[bi][1]
            q = (dx * LIGHT[0] + dy * LIGHT[1]) / max(r, 0.6)
            m = mat
            if band:
                m = band(frac) or mat
            px[x, y] = m[shade(q)]


def seg(px, w, h, x0, y0, x1, y1, c):
    line(px, w, h, x0, y0, x1, y1, c)


def rect(px, w, h, x0, y0, x1, y1, mat, lit_top=True):
    """Box with a lit top row, darker bottom row and a highlight on the left edge."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if y == y0:
                s = 1
            elif y == y1:
                s = 4 if y1 - y0 >= 2 else 3
            elif y == y1 - 1 and y1 - y0 >= 3:
                s = 3
            else:
                s = 2
            if x == x0 and s > 1:
                s -= 1
            put(px, w, h, x, y, mat[s])


def arrow_side(px, w, h, x_nock, y, length, shaft=WOOD, head=STEEL, fletch=FLETCH_RED):
    """Small arrow pointing right, nock at x_nock."""
    xe = x_nock + length
    for x in range(int(x_nock), int(xe) - 2):
        put(px, w, h, x, y, shaft[1] if x % 3 else shaft[2])
    # head (3px long arrowhead)
    put(px, w, h, int(xe) - 2, y - 1, head[2])
    put(px, w, h, int(xe) - 2, y, head[1])
    put(px, w, h, int(xe) - 2, y + 1, head[3])
    put(px, w, h, int(xe) - 1, y, head[0])
    put(px, w, h, int(xe), y, head[2])
    # fletching
    for i in range(3):
        put(px, w, h, int(x_nock) + i, y - 1, fletch[1])
        put(px, w, h, int(x_nock) + i, y + 1, fletch[3])
    put(px, w, h, int(x_nock) - 1, y, STRING[2])


# ---------------------------------------------------------------- bows
def bow_frames(H, grip_x, depth, extra, curl, r_grip, r_tip, stave, wrap_mat,
               decor=None, tips=None, arrow=None, string=STRING, frames=(0.0, 0.5, 1.0)):
    cy = H / 2
    hh = H / 2 - 2.5
    out = []
    # canvas width: room for half-draw arrow head
    x_tip_rest = grip_x - depth + curl
    pull = x_tip_rest - extra * 0.6 - 2
    arrow_len = grip_x + 3 - 2
    W = int(math.ceil(max(grip_x + 4, x_tip_rest - pull * 0.5 + arrow_len + 2)))
    for a in frames:
        img = new(W, H)
        px = img.load()
        pts = []
        N = 60
        for i in range(N + 1):
            t = -1 + 2 * i / N
            y = cy + t * hh * (1 - 0.07 * a)
            x = grip_x + 0.5 - (depth + extra * a) * t * t
            if abs(t) > 0.78:
                x += curl * ((abs(t) - 0.78) / 0.22) ** 2
            pts.append((x, y))
        top, bot = pts[0], pts[-1]
        nock_x = top[0] - a * (top[0] - 2.5 - (1 - a) * 0)
        nock_x = top[0] - a * pull
        # string first so the stave covers its ends
        seg(px, W, H, top[0], top[1] + 0.5, nock_x, cy, string[2])
        seg(px, W, H, bot[0], bot[1] - 0.5, nock_x, cy, string[2])
        if arrow is not None and a > 0:
            arrow_side(px, W, H, round(nock_x), int(cy), arrow_len, **arrow)

        def radius(frac):
            t = abs(frac * 2 - 1)
            return r_tip + (r_grip - r_tip) * (1 - t) ** 1.4

        def band(frac):
            t = frac * 2 - 1
            if abs(t) < 0.17:
                return wrap_mat if int((t + 1) * 40) % 2 == 0 else [wrap_mat[min(4, i + 1)] for i in range(5)]
            if decor:
                return decor(t)
            return None
        stroke(img, pts, radius, stave, band)
        if tips:
            tips(px, W, H, top, bot)
        out.append(outline(img))
    return out, W


def steel_caps(px, w, h, top, bot):
    for (x, y), dy in ((top, 1), (bot, -1)):
        put(px, w, h, int(x), int(y), STEEL[1])
        put(px, w, h, int(x), int(y) + dy, STEEL[2])
        put(px, w, h, int(x) + 1, int(y), STEEL[3])


def bone_spikes(px, w, h, top, bot):
    for (x, y), dy in ((top, -1), (bot, 1)):
        put(px, w, h, int(x) + 1, int(y) + dy, BONE[1])
        put(px, w, h, int(x) + 2, int(y) + 2 * dy, BONE[2])


def basic_bow():
    fr, W = bow_frames(26, 16, 5, 2.5, 1.5, 2.0, 0.9, WOOD, LEATHER,
                       arrow=dict(shaft=WOOD, head=STEEL, fletch=FLETCH_RED))
    return fr


def short_bow():
    fr, W = bow_frames(22, 13, 4, 2, 0.0, 1.6, 0.8, ramp('#e8b878', '#c48a52', '#9a6538', '#6e4524', '#472c16'),
                       CLOTH_WHITE, arrow=dict(shaft=WOOD, head=STEEL, fletch=FLETCH_GREEN))
    return fr


def bone_bow():
    def decor(t):
        # vertebra segments: dark joint every few px
        k = (abs(t) * 30) % 5
        return [BONE[min(4, i + 2)] for i in range(5)] if k < 0.9 else None
    fr, W = bow_frames(26, 16, 5, 2.5, 2.5, 2.2, 0.9, BONE, DARK_WOOD, decor=decor, tips=bone_spikes,
                       arrow=dict(shaft=BONE, head=BONE, fletch=ramp('#5a5a66', '#3a3a44', '#2a2a32', '#1c1c22', '#101014')))
    return fr


def war_bow():
    def decor(t):
        return [STEEL[min(4, i)] for i in range(5)] if 0.22 < abs(t) < 0.3 else None
    fr, W = bow_frames(32, 20, 6.5, 3, 2.5, 2.8, 1.1, DARK_WOOD, CRIMSON, decor=decor, tips=steel_caps,
                       arrow=dict(shaft=DARK_WOOD, head=DARK_STEEL, fletch=FLETCH_RED))
    return fr


# ---------------------------------------------------------------- crossbows
def crossbow_frames(W, H, stock, prod, metal, loaded_bolt, prod_r=1.5, prod_depth=3, double=False,
                    extra=None):
    frames = []
    cy = H // 2 - 1          # top of the stock / bolt rail
    prod_x = W - 6
    latch_x = W // 2 - 1
    for loaded in (True, False):
        img = new(W, H)
        px = img.load()
        # stock: rail from the butt to the front, butt drops lower at the back
        rect(px, W, H, 3, cy, W - 3, cy + 2, stock)
        for x in range(2, 9):
            drop = max(0, 8 - x) // 2
            rect(px, W, H, x, cy, x, cy + 3 + drop, stock)
        # grip / trigger
        rect(px, W, H, latch_x + 1, cy + 3, latch_x + 3, cy + 6, stock)
        put(px, W, H, latch_x - 1, cy + 4, metal[2])
        put(px, W, H, latch_x - 1, cy + 5, metal[3])
        put(px, W, H, latch_x, cy + 6, metal[3])
        # metal fittings
        rect(px, W, H, prod_x - 2, cy, prod_x + 2, cy + 2, metal)
        rect(px, W, H, latch_x - 1, cy - 1, latch_x, cy, metal)
        if extra:
            extra(img, px, W, H, cy, latch_x, prod_x, loaded)
        # prods (limbs), seen as an upright bow at the front
        prods = [(prod_x, 0)] if not double else [(prod_x + 1, 0), (prod_x - 3, 1)]
        for pxx, k in prods:
            hh = H / 2 - 2
            pts = []
            for i in range(41):
                t = -1 + 2 * i / 40
                pull = (prod_depth + (1.5 if loaded else 0)) * t * t
                pts.append((pxx + 0.5 - pull, cy + 1.5 + t * hh))
            top, bot = pts[0], pts[-1]
            sx = latch_x if loaded else top[0]
            sc = STRING[2] if k == 0 else STRING[3]
            seg(px, W, H, top[0], top[1] + 0.5, sx, cy + 1, sc)
            seg(px, W, H, bot[0], bot[1] - 0.5, sx, cy + 1, sc)
            stroke(img, pts, lambda f: prod_r * (0.7 + 0.3 * (1 - abs(f * 2 - 1))), prod)
        if loaded:
            loaded_bolt(px, W, H, latch_x, cy - 1)
        frames.append(outline(img))
    return frames


def bolt_side(px, w, h, x0, y, length, shaft=WOOD, head=STEEL, fletch=FLETCH_RED, head_len=3):
    xe = x0 + length
    for x in range(x0, xe - head_len + 1):
        put(px, w, h, x, y, shaft[1])
    for i in range(head_len):
        put(px, w, h, xe - head_len + 1 + i, y, head[min(4, i)])
        if i < head_len - 1:
            put(px, w, h, xe - head_len + 1 + i, y - 1, head[1])
    put(px, w, h, x0, y - 1, fletch[1])
    put(px, w, h, x0 + 1, y - 1, fletch[2])


def basic_crossbow():
    PIVOTS['BasicCrossbow'] = (13, 13)
    return crossbow_frames(28, 18, WOOD, DARK_STEEL, STEEL,
                           lambda px, w, h, x, y: bolt_side(px, w, h, x, y, 14))


def dual_crossbow():
    def bolts(px, w, h, x, y):
        bolt_side(px, w, h, x, y, 14)
        bolt_side(px, w, h, x - 2, y - 2, 14)
    PIVOTS['DualCrossbow'] = (13, 13)
    return crossbow_frames(28, 18, DARK_WOOD, STEEL, GOLD, bolts, prod_r=2.0, prod_depth=3.5)


def heavy_crossbow():
    def crank(img, px, w, h, cy, latch_x, prod_x, loaded):
        # winding crank at the butt + reinforcement bands
        for yy in range(cy - 3, cy + 1):
            put(px, w, h, 4, yy, BLACK_IRON[2])
        put(px, w, h, 5, cy - 3, BLACK_IRON[1])
        put(px, w, h, 6, cy - 3, BLACK_IRON[1])
        put(px, w, h, 6, cy - 4, CRIMSON[1])
        for bx in (9, 15):
            rect(px, w, h, bx, cy, bx, cy + 2, DARK_STEEL)

    def bolt(px, w, h, x, y):
        bolt_side(px, w, h, x - 2, y, 18, shaft=BLACK_IRON, head=STEEL, fletch=DARK_STEEL, head_len=4)
        put(px, w, h, x + 13, y - 2, STEEL[2])
    PIVOTS['HeavyCrossbow'] = (16, 15)
    return crossbow_frames(34, 22, BLACK_IRON, DARK_STEEL, STEEL, bolt, prod_r=2.2, prod_depth=4, extra=crank)


def poison_crossbow():
    def vial(img, px, w, h, cy, latch_x, prod_x, loaded):
        # glass vial strapped under the stock, front
        for yy in range(cy + 3, cy + 7):
            for xx in range(prod_x - 11, prod_x - 8):
                c = POISON[1] if yy > cy + 4 else ramp('#e0f4ff', '#b6d4e0', '#8aa6b4', '#5d7684', '#3a4c56')[1]
                put(px, w, h, xx, yy, c)
        put(px, w, h, prod_x - 11, cy + 5, POISON[0])
        put(px, w, h, prod_x - 9, cy + 6, POISON[3])

    def bolt(px, w, h, x, y):
        bolt_side(px, w, h, x, y, 14, shaft=DARK_WOOD, head=POISON, fletch=POISON)
    PIVOTS['PoisonCrossbow'] = (13, 13)
    return crossbow_frames(28, 18, ramp('#8fa37a', '#6c8059', '#4f603f', '#36432b', '#222b1b'),
                           DARK_STEEL, POISON, bolt, extra=vial)


def generate(out_dir):
    items = {
        'BasicBow': basic_bow(),
        'ShortBow': short_bow(),
        'BoneBow': bone_bow(),
        'WarBow': war_bow(),
        'BasicCrossbow': basic_crossbow(),
        'DualCrossbow': dual_crossbow(),
        'HeavyCrossbow': heavy_crossbow(),
        'PoisonCrossbow': poison_crossbow(),
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
