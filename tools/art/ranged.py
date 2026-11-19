"""Bows (upright, facing right, 3 frames: rest / half draw / full draw) and crossbows
(horizontal, facing right, 2 frames: loaded / empty).

Conventions for the Java side (see PIVOTS):
  bows:      grip centre at (GRIP_X, H/2) of each frame; the arrow leaves to the right.
  crossbows: grip/trigger at PIVOTS[name]; the bolt leaves to the right along the stock top.
Intended display scale x2 (bows are drawn at BOW_RES x native, see below).
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
# Bows are drawn at BOW_RES x the old native size and displayed at x2 in game (DrawnBowAbility.SCALE),
# so they keep their on-screen size but get twice the pixel detail. Geometry below is in old native units.
BOW_RES = 2


def stave_stroke(img, pts, radius, paint):
    """Like stroke(), but hands every pixel to paint(frac, lat, s, x, y) -> colour.
    lat: -1 (back, facing the target) .. 1 (belly, string side); s: lighting ramp index."""
    w, h = img.size
    px = img.load()
    n = len(pts)
    rmax = max(radius(i / (n - 1)) for i in range(n)) + 1
    x0, x1 = int(min(p[0] for p in pts) - rmax), int(max(p[0] for p in pts) + rmax) + 1
    y0, y1 = int(min(p[1] for p in pts) - rmax), int(max(p[1] for p in pts) + rmax) + 1
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
            a, b = pts[max(0, bi - 1)], pts[min(n - 1, bi + 1)]
            tx, ty = b[0] - a[0], b[1] - a[1]
            tl = math.hypot(tx, ty) or 1
            lat = (dx * -ty / tl + dy * tx / tl) / max(r, 0.6)
            q = (dx * LIGHT[0] + dy * LIGHT[1]) / max(r, 0.6)
            c = paint(frac, lat, shade(q), x, y)
            if c is not None:
                px[x, y] = c


def darker(mat, s, k=1):
    return mat[max(0, min(4, s + k))]


def arrow_hd(px, w, h, x_nock, cy, length, shaft=WOOD, head=STEEL, fletch=FLETCH_RED):
    """Detailed arrow pointing right: 2px shaft, nock, three-vane fletching, broadhead. cy = shaft centre line."""
    y0 = int(cy) - 1           # top shaft row; y0 + 1 is the bottom row
    xn = int(x_nock)
    xe = xn + int(length)      # very tip
    head_len = 6
    # shaft (lit top row, darker bottom row, a few grain flecks)
    for x in range(xn + 1, xe - head_len + 2):
        put(px, w, h, x, y0, shaft[1] if (x * 7) % 11 else shaft[2])
        put(px, w, h, x, y0 + 1, shaft[2] if (x * 5) % 9 else shaft[3])
    # nock: a forked end around the string
    put(px, w, h, xn, y0 - 1, shaft[2])
    put(px, w, h, xn, y0 + 2, shaft[3])
    put(px, w, h, xn + 1, y0 - 1, shaft[1])
    put(px, w, h, xn + 1, y0 + 2, shaft[2])
    put(px, w, h, xn, y0, STRING[1])
    put(px, w, h, xn, y0 + 1, STRING[2])
    # fletching: two swept vanes (top and bottom), a third seen edge-on along the shaft
    fx = xn + 3
    for i in range(7):
        hgt = 2 if i < 5 else 1
        for j in range(1, hgt + 1):
            edge = j == hgt
            put(px, w, h, fx + i - (j - 1), y0 - j, fletch[0] if edge else fletch[1])
            put(px, w, h, fx + i - (j - 1), y0 + 1 + j, fletch[3] if edge else fletch[2])
    for i in (1, 3):               # barb lines in the vanes
        put(px, w, h, fx + i, y0 - 1, fletch[2])
    for i in range(5):             # edge-on vane
        put(px, w, h, fx + i, y0 + 1, fletch[2])
    # binding wrap just in front of the fletching
    for x in (fx + 7,):
        put(px, w, h, x, y0, STRING[2])
        put(px, w, h, x, y0 + 1, STRING[3])
    # broadhead: socket, then a shaded diamond with a bright edge on top
    hx = xe - head_len + 1
    put(px, w, h, hx, y0, head[2])
    put(px, w, h, hx, y0 + 1, head[3])
    # rows above/below the 2px shaft: widest (barbs) right behind the socket, tapering to the point
    extent = [2, 2, 1, 1, 0]
    for i, e in enumerate(extent):
        x = hx + 1 + i
        put(px, w, h, x, y0, head[0] if i < 3 else head[1])
        put(px, w, h, x, y0 + 1, head[2])
        for j in range(1, e + 1):
            put(px, w, h, x, y0 - j, head[1] if j < e else head[0])
            put(px, w, h, x, y0 + 1 + j, head[3] if j == e else head[2])
    put(px, w, h, xe, y0, head[1])


def bow_frames(H, grip_x, depth, extra, curl, r_grip, r_tip, stave, wrap_mat,
               decor=None, tips=None, arrow=None, string=STRING, frames=(0.0, 0.5, 1.0), grain=True, res=BOW_RES):
    """decor(t, lat, s, x, y) -> colour or None paints limb decoration; tips(px, w, h, top, bot, R) adds tip pieces."""
    R = res
    H, grip_x, depth, extra, curl = H * R, grip_x * R, depth * R, extra * R, curl * R
    r_grip, r_tip = r_grip * R, r_tip * R
    cy = H / 2
    hh = H / 2 - 2.5 * R
    out = []
    x_tip_rest = grip_x - depth + curl
    pull = x_tip_rest - extra * 0.6 - 2 * R
    hd = R >= 2   # the extra detail only fits at hi-res; R=1 (icons) draws the plain native bow
    if hd:
        arrow_len = int(grip_x + r_grip + 7 - (x_tip_rest - pull))   # head clears the grip at full draw
    else:
        arrow_len = grip_x + 1
    W = int(math.ceil(max(grip_x + r_grip + 4, x_tip_rest - pull * 0.5 + arrow_len + 3)))
    for a in frames:
        img = new(W, H)
        px = img.load()
        pts = []
        N = 60 * R
        for i in range(N + 1):
            t = -1 + 2 * i / N
            y = cy + t * hh * (1 - 0.07 * a)
            x = grip_x + R / 2 - (depth + extra * a) * t * t
            if abs(t) > 0.78:
                x += curl * ((abs(t) - 0.78) / 0.22) ** 2
            pts.append((x, y))
        top, bot = pts[0], pts[-1]
        nock_x = top[0] - a * pull
        # string first so the stave covers its ends; a darker serving where the arrow nocks
        for end in (top, bot):
            ey = end[1] + (0.5 if end is top else -0.5)
            seg(px, W, H, end[0], ey, nock_x, cy, string[1] if hd else string[2])
            if not hd:
                continue
            n = max(1, int(abs(ey - cy)))
            for i in range(n + 1):        # serving: the centre 1/6 of each half
                f = i / n
                if f > 0.82:
                    put(px, W, H, int(round(end[0] + (nock_x - end[0]) * f)), int(round(ey + (cy - ey) * f)), string[3])
        if arrow is not None and a > 0:
            if hd:
                arrow_hd(px, W, H, round(nock_x), cy, arrow_len, **arrow)
            else:
                arrow_side(px, W, H, round(nock_x), int(cy), arrow_len, **arrow)

        def radius(frac):
            t = abs(frac * 2 - 1)
            return r_tip + (r_grip - r_tip) * (1 - t) ** 1.4

        def paint(frac, lat, s, x, y):
            t = frac * 2 - 1
            at = abs(t)
            if at < 0.17 and not hd:
                return wrap_mat[s] if int((t + 1) * 40) % 2 == 0 else wrap_mat[min(4, s + 1)]
            if at < 0.17:
                # grip: diagonal wrap with dark seams, collars at both ends
                if at > 0.145:
                    return darker(wrap_mat, s, 2)
                k = (y + int(lat * 2.5)) % 3
                if k == 0:
                    return darker(wrap_mat, s, 1 if s < 3 else 0)
                return wrap_mat[max(0, s - 1)] if k == 1 and lat < 0 else wrap_mat[s]
            if decor:
                c = decor(t, lat, s, x, y)
                if c is not None:
                    return c
            if grain and hd and s <= 2:
                # a long grain line down the lit part of each limb, broken twice, and one small knot
                if abs(lat + 0.2) < 0.14 and not (0.45 < at < 0.5 or 0.72 < at < 0.76):
                    return darker(stave, s)
                if abs(t - 0.4) < 0.02 and abs(lat - 0.25) < 0.25:
                    return darker(stave, s, 2)
            if hd and at > 0.9 and lat > 0.3:     # notched tip where the string loops on
                return darker(stave, s)
            return stave[s]
        stave_stroke(img, pts, radius, paint)
        if tips:
            tips(px, W, H, top, bot, R)
        out.append(outline(img))
    return out, W


def steel_caps(px, w, h, top, bot, R=1):
    """Pointed steel nock caps with a lit edge and a rivet."""
    for (x, y), dy in ((top, 1), (bot, -1)):
        x, y = int(x), int(y)
        if R == 1:
            put(px, w, h, x, y, STEEL[1])
            put(px, w, h, x, y + dy, STEEL[2])
            put(px, w, h, x + 1, y, STEEL[3])
            continue
        for i in range(2 * R + 1):
            for j in range(-1, 2):
                put(px, w, h, x + j, y + dy * i, STEEL[2] if j < 1 else STEEL[3])
        put(px, w, h, x - 1, y + dy * 1, STEEL[0])
        put(px, w, h, x, y - dy, STEEL[1])
        put(px, w, h, x + 1, y - 2 * dy, STEEL[2])
        put(px, w, h, x, y + dy * 2 * R, DARK_STEEL[3])


def bone_spikes(px, w, h, top, bot, R=1):
    """Two curved bone barbs sweeping back from each tip."""
    for (x, y), dy in ((top, -1), (bot, 1)):
        x, y = int(x), int(y)
        if R == 1:
            put(px, w, h, x + 1, y + dy, BONE[1])
            put(px, w, h, x + 2, y + 2 * dy, BONE[2])
            continue
        for i in range(2 * R + 1):
            put(px, w, h, x + 1 + i // 2, y + dy * (1 + i), BONE[1] if i < 2 * R else BONE[0])
            put(px, w, h, x + 2 + i // 2, y + dy * (1 + i), BONE[3])
        put(px, w, h, x + 2, y + dy * (R + 1), BONE[2])
        put(px, w, h, x + 3 + R, y + dy * (2 * R + 1), BONE[1])
        # small rear barb
        put(px, w, h, x - 1, y + dy * 2, BONE[2])
        put(px, w, h, x - 2, y + dy * 3, BONE[3])


def basic_bow(res=BOW_RES):
    fr, W = bow_frames(26, 16, 5, 2.5, 1.5, 2.0, 0.9, WOOD, LEATHER,
                       arrow=dict(shaft=WOOD, head=STEEL, fletch=FLETCH_RED), res=res)
    return fr


def short_bow(res=BOW_RES):
    fr, W = bow_frames(22, 13, 4, 2, 0.0, 1.6, 0.8, ramp('#e8b878', '#c48a52', '#9a6538', '#6e4524', '#472c16'),
                       CLOTH_WHITE, arrow=dict(shaft=WOOD, head=STEEL, fletch=FLETCH_GREEN), res=res)
    return fr


def bone_bow(res=BOW_RES):
    def decor(t, lat, s, x, y):
        # vertebrae: each segment has a dark joint, then a bright ridge, and a dark pit on the belly side
        k = (abs(t) * 30) % 5
        if res == 1:
            return BONE[min(4, s + 2)] if k < 0.9 else None
        if k < 0.7:
            return BONE[min(4, s + 2)]
        if k < 1.3:
            return BONE[max(0, s - 1)]
        if 2.4 < k < 3.0 and lat > 0.2:
            return BONE[min(4, s + 1)]
        return None
    fr, W = bow_frames(26, 16, 5, 2.5, 2.5, 2.2, 0.9, BONE, DARK_WOOD, decor=decor, tips=bone_spikes, grain=False,
                       arrow=dict(shaft=BONE, head=BONE, fletch=ramp('#5a5a66', '#3a3a44', '#2a2a32', '#1c1c22', '#101014')),
                       res=res)
    return fr


def war_bow(res=BOW_RES):
    def decor(t, lat, s, x, y):
        at = abs(t)
        if res == 1:
            return STEEL[s] if 0.22 < at < 0.3 else None
        if 0.22 < at < 0.3:
            # riveted steel band: lit upper edge, shadowed lower edge, a rivet in the middle
            if abs(at - 0.26) < 0.012 and abs(lat) < 0.3:
                return STEEL[0]
            edge = (at - 0.22 < 0.015) if t < 0 else (0.3 - at < 0.015)
            return STEEL[max(0, s - 1)] if edge else DARK_STEEL[s]
        if 0.3 <= at < 0.34 or 0.18 < at <= 0.22:
            return CRIMSON[min(4, s + 1)] if lat < 0 else None   # thin crimson inlay next to the band
        return None
    fr, W = bow_frames(32, 20, 6.5, 3, 2.5, 2.8, 1.1, DARK_WOOD, CRIMSON, decor=decor, tips=steel_caps,
                       arrow=dict(shaft=DARK_WOOD, head=DARK_STEEL, fletch=FLETCH_RED), res=res)
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
