"""Skill tree screen art (Menu/SkillTree/SkillTreeView.java): an ancient rune wall with the tree carved into it.

  SkillTreeBg        480x270 opaque, shown x4 (1920x1080). Weathered stone blocks with cracks, moss and a few
                     glowing seams, a carved rune border, a lintel slab with the molten title, and the carved
                     trunk + roots below the root node (screen ROOT = 960, 880 -> 240, 220 here).
  IconRound_<TYPE>   28x28 round version of every skill icon (disc radius 12 around the centre), shown x2.
  NodeRing_skill     3 frames 32x32: locked / available / owned, for skill nodes, shown x2
  NodeRing_major     3 frames 40x40: same states with teeth, for stat and sub-tree nodes
  NodeRing_root      48x48, the root (Rusty Sword, always owned)

The grooves between the nodes are drawn by the Java view because their glow depends on what is owned.
`python tools/art/skilltree.py out.png` renders a full-screen mock (bg + grooves + nodes) using the same layout
numbers as SkillTreeView; keep LAYOUT in sync with the Java constants.
"""
import math
import random
from PIL import Image, ImageFilter

from pixel import OUTLINE, dither_index, hexc, mix, new, outline, put, ramp, strip
from materials import GOLD
from effects import Noise
import icons
from pixfont import text_cells, text_width

W, H = 480, 270           # background, shown x4
BG_SCALE = 4

WALL = ramp('#8c8794', '#6f6a78', '#57535f', '#44404b', '#34313a', '#26242c', '#1b1a20', '#121116')
MORTAR = hexc('#0b0a0e')
MOSS = ramp('#8a9a52', '#66783b', '#4a5a2b', '#33401f', '#222b15')
EMBER = ramp('#fff1b0', '#ffc23d', '#ff7a1f', '#d1361a', '#7a1a12', '#3e0d0b')
WARM = hexc('#ff5a1f')
LIGHT = (-0.7071, -0.7071)   # towards the light: top-left

# screen-space layout shared with SkillTreeView (1920x1080)
ROOT = (960, 880)
BRANCH_X = {'MELEE': 345, 'RANGED': 960, 'MAGIC': 1575}
STAT_Y = {'MELEE': 670, 'RANGED': 640, 'MAGIC': 670}
SUB_DX = 195
SUB_Y = (430, 400, 430)                       # left, middle, right sub-tree of a branch
SKILL_OFF = ((-58, -125), (0, -195), (58, -125))


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- runes
def rune_glyphs(rng, count=14):
    """Futhark-like 5x5 glyphs: a stem plus a couple of diagonal or straight strokes."""
    strokes = [((2, 0), (4, 2)), ((2, 0), (0, 2)), ((2, 2), (4, 4)), ((2, 2), (0, 4)), ((2, 2), (4, 0)),
               ((2, 2), (0, 0)), ((0, 0), (0, 4)), ((4, 0), (4, 4)), ((0, 2), (4, 2)), ((2, 1), (4, 3)),
               ((0, 1), (2, 3)), ((2, 4), (4, 2))]
    glyphs = []
    while len(glyphs) < count:
        g = set()
        stem = rng.random() < 0.8
        if stem:
            g |= {(2, y) for y in range(5)}
        for a, b in rng.sample(strokes, rng.choice((1, 2, 2, 3))):
            n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
            for i in range(n + 1):
                g.add((a[0] + (b[0] - a[0]) * i // n, a[1] + (b[1] - a[1]) * i // n))
        if 6 <= len(g) <= 13 and g not in glyphs:
            glyphs.append(g)
    return glyphs


# ---------------------------------------------------------------- stone wall
def cyclopean(rng, cols=11, rows=6):
    """Jittered-grid voronoi sites: big irregular polygonal stones like an ancient cyclopean wall."""
    cw, ch = W / cols, H / rows
    sites = []
    for j in range(-1, rows + 1):
        for i in range(-1, cols + 1):
            ox = 0.5 if j % 2 else 0.0
            sites.append(((i + ox + rng.uniform(0.2, 0.8)) * cw, (j + rng.uniform(0.2, 0.8)) * ch,
                          rng.uniform(-0.08, 0.08)))
    return sites, cw, ch


def wall_values(rng):
    """Per pixel darkness (0 = brightest) of the bare wall, the stone id and the distance to its edge."""
    nz1, nz2, nz3 = Noise(71, n=16), Noise(72, n=16), Noise(73, n=16)
    sites, cw, ch = cyclopean(rng)
    val = [[0.0] * W for _ in range(H)]
    edge = [[9.0] * W for _ in range(H)]
    owner = [[0] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            px_, py_ = x + 0.5, y + 0.5
            best = sorted(((px_ - sx) ** 2 + (py_ - sy) ** 2, k) for k, (sx, sy, _) in enumerate(sites)
                          if abs(sx - px_) < cw * 2.2 and abs(sy - py_) < ch * 2.2)
            d1, i = best[0]
            si = sites[i]
            # distance to the nearest bisector, and the direction towards that neighbour
            e, nxy = 99.0, (0.0, 0.0)
            for d2, j in best[1:6]:
                sj = sites[j]
                L = math.hypot(sj[0] - si[0], sj[1] - si[1])
                dist = (d2 - d1) / (2 * L)
                if dist < e:
                    e, nxy = dist, ((sj[0] - si[0]) / L, (sj[1] - si[1]) / L)
            edge[y][x], owner[y][x] = e, i
            v = 0.56 + si[2]
            v += 0.22 * (nz1(x / 9, y / 9) - 0.5) + 0.10 * (nz2(x / 2.6, y / 2.6) - 0.5)
            worn = nz3(x / 3.1, y / 3.1)
            facing = -(nxy[0] * LIGHT[0] + nxy[1] * LIGHT[1])  # >0: this edge faces the light
            if e < 1.0 or (e < 1.8 and worn > 0.64) or (e < 2.6 and worn > 0.82):
                v = 2.0  # joint
            elif e < 2.0:
                v -= 0.2 * facing
            elif e < 3.2:
                v -= 0.08 * facing
            val[y][x] = v
    return val, edge, owner


def crack(px_val, rng, x, y, length, ember, cracks):
    dx, dy = rng.choice((-1, 1)) * rng.uniform(0.3, 1.0), rng.uniform(0.4, 1.0)
    pts = []
    for _ in range(length):
        pts.append((int(round(x)), int(round(y))))
        x += dx + rng.uniform(-0.6, 0.6)
        y += dy + rng.uniform(-0.3, 0.3)
        if rng.random() < 0.12:
            dx = -dx * 0.5 + rng.uniform(-0.5, 0.5)
    cracks.append((pts, ember))


def stone_bg(seed=7, warm=None, canopy=(W / 2, 80, 240, 100), avoid=None, finish=None):
    """The rune wall. warm: light spots (x, y, rx, ry) that tint the stone; canopy: soft light (x, y, rx, ry);
    avoid(x, y): stones that get no inscription; finish(img, rng): frame and centre piece (default: skill tree)."""
    rx, ry = ROOT[0] / BG_SCALE, ROOT[1] / BG_SCALE
    if warm is None:
        warm = ((rx, ry + 35, 170, 85),)
    if avoid is None:
        avoid = lambda sx, sy: abs(sx - rx) < 40 and sy > 180
    rng = random.Random(seed)
    val, edge, owner = wall_values(rng)
    nz_m, nz_v = Noise(81, n=16), Noise(82, n=16)

    # lighting: dark vignette, warm light (rising from the trunk), a little soft light behind the canopy
    warmth = [[0.0] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if val[y][x] > 1.5:
                continue
            ex = abs(x - W / 2) / (W / 2)
            ey = abs(y - H * 0.45) / (H * 0.55)
            vig = smooth((math.hypot(ex * 0.9, ey) - 0.4) / 0.7)
            soft = max(0.0, 1 - math.hypot((x - canopy[0]) / canopy[2], (y - canopy[1]) / canopy[3]))
            w = max(max(0.0, 1 - math.hypot((x - wx) / wrx, (y - wy) / wry)) for wx, wy, wrx, wry in warm)
            warmth[y][x] = w
            val[y][x] += 0.38 * vig - 0.08 * soft - 0.16 * w * w

    img = new(W, H)
    px = img.load()
    for y in range(H):
        for x in range(W):
            v = val[y][x]
            if v > 1.5:
                px[x, y] = MORTAR
                continue
            c = WALL[dither_index(max(0.0, min(1.0, v)), x, y, len(WALL), band=0.12)]
            if rng.random() < 0.012:
                c = WALL[min(7, WALL.index(c) + 2)]  # pits
            w = warmth[y][x]
            if w > 0:  # ordered-dithered warm tint (no smooth airbrush)
                k = dither_index(1 - w * w * 0.9, x, y, 6, band=0.12)  # 0 = warmest
                c = mix(c, WARM, (5 - k) * 0.06)
            px[x, y] = c

    # moss creeping out of some joints, mostly low and in the corners
    for y in range(H):
        for x in range(W):
            if px[x, y] == MORTAR:
                continue
            m = nz_m(x / 13, y / 13) * (0.5 + 0.6 * smooth((y - 110) / 150)) + 0.3 * smooth((abs(x - W / 2) - 160) / 80)
            d = edge[y][x]
            if d <= 2.6 and m > 0.82 - 0.05 * d and nz_v(x / 1.7, y / 1.7) > 0.3:
                k = min(4, max(1, int((1 - m) * 7 + d) + 1))
                px[x, y] = MOSS[min(4, k + (1 if nz_v(x / 2, y / 2) > 0.62 else 0))]

    # faint inscriptions on some stones, a few large dormant runes glowing dimly
    glyphs = rune_glyphs(rng, 18)
    sites = cyclopean(random.Random(seed))[0]
    cand = [i for i, (sx, sy, _) in enumerate(sites)
            if 30 < sx < W - 40 and 50 < sy < H - 40 and not avoid(sx, sy)]
    rng.shuffle(cand)
    for n, i in enumerate(cand[:16]):
        sx, sy, _ = sites[i]
        if n < 4:  # one big rune, 2x
            g = rng.choice(glyphs)
            big = {(gx * 2 + a, gy * 2 + b) for gx, gy in g for a in range(2) for b in range(2)}
            ox, oy = int(sx) - 5, int(sy) - 5
            if all(owner[min(H - 1, oy + gy)][min(W - 1, ox + gx)] == i for gx, gy in big):
                carve_glyph(px, big, ox, oy, EMBER[5], lip=WALL[3])
            continue
        lines = rng.randint(1, 3)
        count = rng.randint(3, 5)
        for li in range(lines):
            ox = int(sx) - count * 3
            oy = int(sy) - lines * 4 + li * 8
            for k in range(count):
                g = rng.choice(glyphs)
                if all(0 <= oy + gy < H and 0 <= ox + k * 6 + gx < W and owner[oy + gy][ox + k * 6 + gx] == i
                       and edge[oy + gy][ox + k * 6 + gx] > 3 for gx, gy in g):
                    carve_glyph(px, g, ox + k * 6, oy, WALL[6], lip=WALL[3])

    # cracks: dark lines with a lit lower-right lip; a few near the bottom glow with heat
    cracks = []
    for _ in range(30):
        x, y = rng.uniform(15, W - 15), rng.uniform(15, H - 30)
        ember = y > 160 and rng.random() < 0.5
        crack(val, rng, x, y, rng.randint(7, 22), ember, cracks)
    for pts, ember in cracks:
        for i, (x, y) in enumerate(pts):
            if not (0 <= x < W - 1 and 0 <= y < H - 1):
                continue
            if px[x + 1, y + 1] != MORTAR:
                px[x + 1, y + 1] = mix(px[x + 1, y + 1], WALL[1], 0.4)
            if ember:
                t = i / len(pts)
                px[x, y] = EMBER[3] if 0.25 < t < 0.7 else EMBER[4]
            else:
                px[x, y] = WALL[7]

    if finish is None:
        border(img, rng)
        trunk(img)
        # deep enough for the skill point count under the title (SkillTreeView draws it, x4, glyph top at row 26)
        lintel(img, box=(162, 318, 3, 40))
    else:
        finish(img, rng)
    return img


# ---------------------------------------------------------------- frame, trunk, title
BORDER = 9


def carve_glyph(px, g, ox, oy, fill, lip=WALL[1]):
    for (gx, gy) in g:
        x, y = ox + gx, oy + gy
        if (gx + 1, gy + 1) not in g:
            put(px, W, H, x + 1, y + 1, lip)
    for (gx, gy) in g:
        put(px, W, H, ox + gx, oy + gy, fill)


def border(img, rng, skip=(150, 330)):
    """Raised stone frame around the screen with a rune band carved into it; corner bosses.
    No runes on the top band between skip[0] and skip[1] (hidden by the lintel)."""
    px = img.load()
    nz = Noise(91, n=16)
    for y in range(H):
        for x in range(W):
            d = min(x, y, W - 1 - x, H - 1 - y)
            if d >= BORDER:
                if d == BORDER:
                    px[x, y] = mix(px[x, y], MORTAR, 0.7)  # shadow cast on the wall
                elif d == BORDER + 1:
                    px[x, y] = mix(px[x, y], MORTAR, 0.35)
                continue
            if d == 0:
                px[x, y] = MORTAR
                continue
            lit = (y == d or x == d) and d <= 1  # outer bevel, top/left
            inner_dark = d == BORDER - 1 and (x == d or y == d)
            v = 0.36 + 0.14 * (nz(x / 6, y / 6) - 0.5)
            if lit:
                v -= 0.22
            elif d == 1:
                v += 0.18
            elif d == BORDER - 1:
                v += 0.12 if not inner_dark else 0.3
            elif d == BORDER - 2 and (x == d or y == d):
                v -= 0.08
            # recessed rune channel
            if 2 <= d <= BORDER - 3:
                v += 0.16
            px[x, y] = WALL[dither_index(max(0, min(1, v)), x, y, len(WALL), band=0.1)]

    glyphs = rune_glyphs(rng)
    off = (BORDER - 5) // 2
    step = 9
    for x in range(14, W - 18, step):  # top / bottom bands
        for y in (off, H - off - 5):
            if skip[0] < x < skip[1] and y < H / 2:
                continue  # under the lintel
            fill = EMBER[4] if rng.random() < 0.18 else WALL[7]
            carve_glyph(px, rng.choice(glyphs), x, y, fill)
    for y in range(16, H - 18, step):  # side bands
        for x in (off, W - off - 5):
            fill = EMBER[4] if rng.random() < 0.18 else WALL[7]
            carve_glyph(px, rng.choice(glyphs), x, y, fill)

    for cx, cy in ((0, 0), (W - 13, 0), (0, H - 13), (W - 13, H - 13)):  # corner bosses
        for y in range(13):
            for x in range(13):
                d = min(x, y, 12 - x, 12 - y)
                v = 0.3 if d > 1 else (0.12 if x == d or y == d else 0.62)
                if d == 0:
                    px[cx + x, cy + y] = MORTAR
                    continue
                if abs(x - 6) + abs(y - 6) <= 3:  # carved diamond with an ember core
                    v = 0.75
                px[cx + x, cy + y] = WALL[dither_index(v, x, y, len(WALL), band=0.1)]
        px[cx + 6, cy + 6] = EMBER[2]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[cx + 6 + dx, cy + 6 + dy] = EMBER[4]


def groove_stamp(px, w, h, pts, radius_fn, fill=None, lip=True, walls=True, lip_col=WALL[1]):
    """Carved channel along a polyline: lit lower-right lip, dark walls, optional molten fill
    as (shrink, colour) layers from the outside in."""
    layers = []
    if lip:
        layers.append((1, 1, 0.5, lambda t: lip_col))
    if walls:
        layers.append((0, 0, 0.0, lambda t: WALL[7]))
        layers.append((0.5, 0.5, -1.0, lambda t: WALL[5]))
    if fill:
        for i, (shrink, col) in enumerate(fill):
            layers.append((0, 0, -shrink, (lambda c: (lambda t: c))(col)))
    for ox, oy, grow, col in layers:
        n = len(pts)
        for i, (x, y) in enumerate(pts):
            t = i / max(1, n - 1)
            r = radius_fn(t) + grow
            if r <= 0:
                continue
            for yy in range(int(y + oy - r) - 1, int(y + oy + r) + 2):
                for xx in range(int(x + ox - r) - 1, int(x + ox + r) + 2):
                    if (xx + 0.5 - x - ox) ** 2 + (yy + 0.5 - y - oy) ** 2 <= r * r:
                        put(px, w, h, xx, yy, col(t))


def bezier(p0, p1, p2, p3, step=0.4):
    est = math.dist(p0, p1) + math.dist(p1, p2) + math.dist(p2, p3)
    n = max(2, int(est / step))
    out = []
    for i in range(n + 1):
        t = i / n
        a = (1 - t) ** 3
        b = 3 * (1 - t) ** 2 * t
        c = 3 * (1 - t) * t * t
        d = t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def root_walk(rng, x, y, angle, thick, out, depth=0):
    """A wiggly root growing down and outwards (angle from straight down) with the odd side rootlet."""
    base = angle
    pts = [(x, y)]
    x0 = x
    while y < H - BORDER + 1 and abs(x - x0) < 105 - 40 * depth and len(pts) < 140:
        angle += rng.uniform(-0.22, 0.22)
        angle = base + max(-0.3, min(0.3, angle - base))
        angle = max(-1.15, min(1.15, angle))
        x += math.sin(angle)
        y += math.cos(angle) * 0.9
        pts.append((x, y))
        if depth < 1 and len(pts) > 10 and rng.random() < 0.035:
            side = 1 if angle > 0 else -1
            root_walk(rng, x, y, angle + side * rng.uniform(0.3, 0.6), thick * 0.55, out, depth + 1)
    out.append((pts, thick))


def trunk(img):
    """Carved trunk below the root node, splitting into roots that wander down into the frame."""
    px = img.load()
    rng = random.Random(33)
    rx, ry = ROOT[0] / BG_SCALE, ROOT[1] / BG_SCALE
    base_y = ry + 22
    roots = []
    for side in (-1, 1):
        for ang, thick in ((0.3, 3.0), (0.8, 2.8), (1.08, 2.5)):
            root_walk(rng, rx + side * 2, base_y + rng.uniform(-1, 2), side * ang, thick, roots)
    trunk_pts = bezier((rx, ry), (rx, ry + 8), (rx, base_y - 6), (rx, base_y + 2))
    trunk_r = lambda t: 4.0 + 2.5 * t * t
    # carve everything first, then fill, so the channels merge cleanly
    groove_stamp(px, W, H, trunk_pts, trunk_r)
    for pts, thick in roots:
        groove_stamp(px, W, H, pts, lambda t, k=thick: k * (1 - 0.65 * t), lip_col=WALL[3])
    for pts, thick in roots:
        groove_stamp(px, W, H, pts, lambda t, k=thick: k * (1 - 0.65 * t), lip=False,
                     fill=[(0.8, EMBER[4]), (1.4, EMBER[3]), (2.0, EMBER[2])], walls=False)
    # bark grooves on the trunk and a molten heart vein
    groove_stamp(px, W, H, trunk_pts, trunk_r, lip=False, walls=False,
                 fill=[(1.2, EMBER[4]), (2.4, EMBER[3]), (3.2, EMBER[2]), (3.8, EMBER[1])])
    for k in (-2.5, 2.5):
        for i in range(int(base_y - ry) - 2):
            y = ry + 9 + i
            x = rx + k * (1 + 0.5 * (i / (base_y - ry))) + math.sin(i * 0.7 + k) * 0.6
            put(px, W, H, int(round(x)), int(round(y)), EMBER[5])


def lintel(img, text='SKILL TREE', box=(162, 318, 3, 35), scale=2, text_dy=7):
    """Raised slab hanging from the top frame with the molten, carved title (box = x0, x1, y0, y1)."""
    px = img.load()
    nz = Noise(95, n=16)
    x0, x1, y0, y1 = box
    for y in range(y0, y1 + 3):  # drop shadow
        for x in range(x0 + 2, x1 + 3):
            if y > y1 or x > x1:
                put(px, W, H, x, y, mix(px[x, y], MORTAR, 0.6))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dl, dr, dt, db = x - x0, x1 - x, y - y0, y1 - y
            d = min(dl, dr, dt, db)
            if d == 0:
                px[x, y] = MORTAR
                continue
            v = 0.3 + 0.16 * (nz(x / 7, y / 7) - 0.5)
            if d == 1:
                v += -0.2 if (dl == 1 or dt == 1) else 0.25
            elif d == 2:
                v += -0.08 if (dl == 2 or dt == 2) else 0.12
            elif d == 4:  # carved inner line
                v = 0.8 if (dl == 4 or dt == 4) else 0.05
            px[x, y] = WALL[dither_index(max(0, min(1, v)), x, y, len(WALL), band=0.1)]
    # title: scaled 5x7 letters, molten with a dark carved rim
    ox, oy = (x0 + x1 + 1) // 2 - text_width(text, scale) // 2, y0 + text_dy
    cells = {(ox + x, oy + y) for x, y in text_cells(text, scale)}
    for x, y in cells:  # rim
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (1, 1)):
            if (x + dx, y + dy) not in cells:
                put(px, W, H, x + dx, y + dy, WALL[7] if (dx, dy) != (1, 1) else EMBER[5])
    for x, y in cells:
        t = (y - oy) / (7 * scale - 1)
        px[x, y] = EMBER[1] if t < 0.3 else EMBER[2] if t < 0.75 else EMBER[3]
        if t < 0.08:
            px[x, y] = EMBER[0]


# ---------------------------------------------------------------- nodes
LOCKED, AVAILABLE, OWNED = 0, 1, 2
STONE_RING = ramp('#a39fab', '#7d7987', '#5e5a68', '#45414e', '#2e2b35')
MOLTEN_CRUST = hexc('#2a0b0a')


def ring_pixel(state, dist, r_in, r_out, nx, ny, x, y, nz):
    """Colour of one pixel of a ring band (dist = distance from centre, n = outward normal)."""
    lit = -(nx * LIGHT[0] + ny * LIGHT[1])  # 1 = facing the light
    across = (dist - r_in) / max(0.01, r_out - r_in)  # 0 inner .. 1 outer
    if state == OWNED:
        heat = 1 - abs(across - 0.45) * 2.2
        idx = 1 if heat > 0.55 else 2 if heat > 0.15 else 3
        if nz(x / 2.2, y / 2.2) > 0.72 and 0.15 < across < 0.85:
            return MOLTEN_CRUST if idx >= 2 else EMBER[3]
        return EMBER[idx]
    pal = GOLD if state == AVAILABLE else STONE_RING
    v = 0.5 - 0.42 * lit + 0.2 * (across - 0.5) * (1 if lit > 0 else -1)
    if state == LOCKED:
        v += 0.18 * (nz(x / 2.5, y / 2.5) - 0.5)
    return pal[dither_index(max(0, min(1, v)), x, y, 4, band=0.1) + (0 if state == AVAILABLE else 1)]


def node_ring(size, r_in, r_out, state, teeth=0, tooth_len=0.0, rays=0, seed=5):
    img = new(size, size)
    px = img.load()
    c = size / 2
    nz = Noise(seed + state, n=16)
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            if d < r_in - 0.5:
                continue
            ang = math.atan2(dy, dx)
            outer = r_out
            if teeth:
                k = (ang / (2 * math.pi) * teeth) % 1.0
                tri = max(0.0, 1 - abs(k - 0.5) * 2 / 0.45)  # pointy tooth around each 1/teeth slot
                outer = r_out + tooth_len * tri
            if rays:
                k = (ang / (2 * math.pi) * rays + 0.5) % 1.0
                long_ray = int(ang / (2 * math.pi) * rays + 0.5 + rays) % 2 == 0
                tri = max(0.0, 1 - abs(k - 0.5) * 2 / 0.32)
                outer = r_out + tooth_len * tri * (1 if long_ray else 0.55)
            if d > outer:
                continue
            nx, ny = dx / max(d, 0.01), dy / max(d, 0.01)
            px[x, y] = ring_pixel(state, d, r_in, outer, nx, ny, x, y, nz)
    img = outline(img, MOLTEN_CRUST if state == OWNED else OUTLINE)
    return img


def ring_strips():
    small = [node_ring(32, 11.5, 15.0, s) for s in (LOCKED, AVAILABLE, OWNED)]
    major = [node_ring(40, 11.5, 15.0, s, teeth=8, tooth_len=4.0) for s in (LOCKED, AVAILABLE, OWNED)]
    root = node_ring(48, 11.5, 16.0, OWNED, rays=12, tooth_len=7.0)
    return strip(small), strip(major), root


def round_tile(branch, S=28, R=12):
    img = new(S, S)
    px = img.load()
    bg = icons.BRANCH_BG[branch]
    c = S / 2
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if d > R:
                continue
            if d > R - 1:
                px[x, y] = bg[4]
                continue
            v = math.hypot(x + 0.5 - (c - 4), y + 0.5 - (c - 4)) / (R * 2.1)
            px[x, y] = bg[dither_index(min(1, 0.1 + v), x, y, 4)]
    return img


def round_icons(limit=19):
    out = {}
    for name, (branch, subject) in icons.icons().items():
        subject = icons.crop(subject)
        lim = limit + 1 if name in ('ENCHANTING', 'IMBUE') else limit
        if subject.width > lim or subject.height > lim:
            subject = icons.fit(subject, lim)
        img = round_tile(branch)
        img.alpha_composite(subject, ((28 - subject.width) // 2, (28 - subject.height) // 2))
        out[name] = img
    return out


def generate(out_dir):
    stone_bg().save(f'{out_dir}/SkillTreeBg.png')
    small, major, root = ring_strips()
    small.save(f'{out_dir}/NodeRing_skill.png')
    major.save(f'{out_dir}/NodeRing_major.png')
    root.save(f'{out_dir}/NodeRing_root.png')
    for name, img in round_icons().items():
        img.save(f'{out_dir}/IconRound_{name}.png')


# ---------------------------------------------------------------- full-screen mock (mirrors SkillTreeView)
def layout():
    """name -> (x, y, kind, parent) in screen pixels, same rules as SkillTreeView."""
    names = list(icons.icons())
    nodes = {'RUSTY_SWORD': (ROOT[0], ROOT[1], 'root', None)}
    stat = {'MELEE': 'STRENGTH', 'RANGED': 'DEXTERITY', 'MAGIC': 'MANA'}
    subs = {'MELEE': ['SWORDS', 'FISTICUFFS', 'BLUNT'], 'RANGED': ['BOW', 'CROSSBOW', 'TRIDENT'],
            'MAGIC': ['ARCANE', 'ELEMENTAL', 'ENCHANTING']}
    order = names
    for b, bx in BRANCH_X.items():
        nodes[stat[b]] = (bx, STAT_Y[b], 'stat', 'RUSTY_SWORD')
        for i, s in enumerate(subs[b]):
            sx, sy = bx + (i - 1) * SUB_DX, SUB_Y[i]
            nodes[s] = (sx, sy, 'sub', stat[b])
            kids = [n for n in order if n not in nodes and order.index(n) > order.index(s)][:3]
            for j, k in enumerate(kids):
                nodes[k] = (sx + SKILL_OFF[j][0], sy + SKILL_OFF[j][1], 'skill', s)
    return nodes


def edge_curve(a, b, kind):
    (x0, y0), (x1, y1) = a, b
    if kind == 'stat' and abs(x1 - x0) > 1:
        s = 1 if x1 > x0 else -1
        return (x0, y0), (x0 + s * 40, y0 - 150), (x1 - s * 220, y1 + 30), (x1, y1)
    if kind == 'sub':
        return (x0, y0), (x0, y0 - 90), (x1, y1 + 90), (x1, y1)
    return (x0, y0), (x0 + (x1 - x0) * 0.15, y0 - 45), (x1, y1 + 45), (x1, y1)


def mock(path):
    bg = stone_bg().resize((W * BG_SCALE, H * BG_SCALE), Image.Resampling.NEAREST).convert('RGBA')
    nodes = layout()
    owned = {'RUSTY_SWORD', 'STRENGTH', 'SWORDS', 'SHORTSWORD', 'DEXTERITY', 'BOW', 'MANA'}
    avail = {'FISTICUFFS', 'BLUNT', 'DUAL_WIELDING', 'ZWEIHANDER', 'CROSSBOW', 'TRIDENT', 'ARCANE', 'ELEMENTAL'}
    lines = new(960, 540)
    lp = lines.load()
    glow = new(960, 540)
    gp = glow.load()
    radius = {'stat': 3.5, 'sub': 3.0, 'skill': 2.5}
    molten = [(0.9, EMBER[3]), (1.6, EMBER[2]), (2.3, EMBER[1])]
    dormant = [(1.0, EMBER[5]), (2.0, EMBER[4])]
    for pass_ in (0, 1):
        for n, (x, y, kind, parent) in nodes.items():
            if not parent:
                continue
            px0, py0 = nodes[parent][:2]
            pts = [(u / 2, v / 2) for u, v in bezier(*edge_curve((px0, py0), (x, y), kind), step=0.8)]
            r = radius[kind]
            if pass_ == 0:
                groove_stamp(lp, 960, 540, pts, lambda t: r)
            elif n in owned:
                groove_stamp(lp, 960, 540, pts, lambda t: r, fill=molten, lip=False, walls=False)
                groove_stamp(gp, 960, 540, pts, lambda t: r - 0.5, fill=molten, lip=False, walls=False)
            elif n in avail:
                groove_stamp(lp, 960, 540, pts, lambda t: r, fill=dormant, lip=False, walls=False)
    lines = lines.resize((1920, 1080), Image.Resampling.NEAREST)
    bg.alpha_composite(lines)
    g = glow.resize((1920, 1080), Image.Resampling.NEAREST).filter(ImageFilter.GaussianBlur(10))
    from PIL import ImageChops
    bg = ImageChops.add(bg, g.convert('RGBA'))
    small, major, root = ring_strips()
    ric = round_icons()
    for n, (x, y, kind, parent) in nodes.items():
        state = OWNED if n in owned else AVAILABLE if n in avail else LOCKED
        if kind == 'root':
            ring = root
        else:
            sheet, size = (small, 32) if kind == 'skill' else (major, 40)
            ring = sheet.crop((state * size, 0, (state + 1) * size, size))
        ring = ring.resize((ring.width * 2, ring.height * 2), Image.Resampling.NEAREST)
        bg.alpha_composite(ring, (int(x - ring.width / 2), int(y - ring.height / 2)))
        ic = ric[n].resize((56, 56), Image.Resampling.NEAREST)
        if state == LOCKED:
            from PIL import ImageEnhance
            ic = ImageEnhance.Brightness(ImageEnhance.Color(ic).enhance(0.15)).enhance(0.6)
        bg.alpha_composite(ic, (int(x - 28), int(y - 28)))
    bg.convert('RGB').save(path)


if __name__ == '__main__':
    import sys
    mock(sys.argv[1] if len(sys.argv) > 1 else 'skilltree_mock.png')
