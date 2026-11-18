"""Terrain tiles (seamless in both directions unless noted) and parallax background layers
(seamless horizontally). Tiles are meant to be drawn at x2 via a world-anchored ImagePattern;
backgrounds are 480x216 and scale x5 to 2400x1080.

  Ground        64x64  red-black hell rock with glowing crevices (fills ground bodies)
  GroundTop     64x12  charred crust strip for the top edge of ground (horizontal tiling)
  GroundEmbed   6 frames 24x16  bones/skull/crystals/magma buried in the ground body
  GroundSurface 6 frames 24x24  bottom-aligned decorations standing on the ground
  Platform      3 frames 32x32  floating platform variants: black iron, obsidian+bone, rune stone
  PlatformTop   3 frames 32x8   matching top edges
  PlatformUnder 3 frames 32x10  matching things hanging below (chains, bone tips, molten drips)
  bg_far        480x216 opaque, slowest layer
  bg_mid        480x216 transparent, middle layer
"""
import math
import random
from pixel import *
from materials import *
from effects import Noise
from projectiles import FIRE

SKY = ramp('#4a1c1a', '#36161a', '#26111a', '#1a0d16', '#110911')


def wrap_d(a, b, n):
    d = abs(a - b) % n
    return min(d, n - d)


def voronoi_tile(S, cells, seed):
    rng = random.Random(seed)
    pts = [(rng.uniform(0, S), rng.uniform(0, S)) for _ in range(cells)]
    near = [[None] * S for _ in range(S)]
    for y in range(S):
        for x in range(S):
            ds = sorted((wrap_d(x + 0.5, px_, S) ** 2 + wrap_d(y + 0.5, py, S) ** 2, i) for i, (px_, py) in enumerate(pts))
            near[y][x] = (ds[0][1], math.sqrt(ds[1][0]) - math.sqrt(ds[0][0]))
    return pts, near


HELLROCK = ramp('#5a3036', '#40202a', '#2c141c', '#1e0c13', '#12070c')
DIM_BONE = ramp('#d8cbaa', '#b0a07c', '#857659', '#5c503c', '#3a3226')
OBSIDIAN = ramp('#5b4a6e', '#3a2e4a', '#251d31', '#171220', '#0c0912')
GLOW = ramp('#ffd27a', '#ff8a2a', '#e0441c', '#9a2214', '#5a120e')


def ground_tile(S=64, seed=1):
    """Hell rock: red-black stones split by crevices, some of which glow with heat."""
    img = new(S, S)
    px = img.load()
    pts, near = voronoi_tile(S, 12, seed)
    nz = Noise(seed, n=16)
    rng = random.Random(seed + 5)
    tone = [rng.choice((0, 0, 1)) for _ in pts]
    for y in range(S):
        for x in range(S):
            i, edge = near[y][x]
            if edge < 1.1:
                px[x, y] = HELLROCK[4]
                continue
            ul = near[(y - 2) % S][(x - 2) % S]
            dr = near[(y + 2) % S][(x + 2) % S]
            s = 2 + tone[i]
            if ul[0] != i or ul[1] < 1.1:
                s -= 1
            elif dr[0] != i or dr[1] < 1.1:
                s += 1
            n = nz(x * 16 / S * 2, y * 16 / S * 2)
            if n > 0.7:
                s -= 1
            elif n < 0.3 and (x + y) % 2 == 0:
                s += 1
            px[x, y] = HELLROCK[max(0, min(4, s))]
    # hot crevices with a dim halo bleeding into the stone
    hot = set()
    for y in range(S):
        for x in range(S):
            if near[y][x][1] < 1.1 and nz(x * 0.22, y * 0.22 + 30) > 0.74:
                hot.add((x, y))
    for x, y in hot:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = ((x + dx) % S, (y + dy) % S)
            if q not in hot:
                px[q] = mix(px[q], GLOW[3], 0.45)
    for x, y in hot:
        px[x, y] = GLOW[2] if (x * 3 + y) % 4 else GLOW[1]
    return img


def ground_top(W=64, H=12, seed=2):
    """Charred crust: black, lumpy, with a faint red-hot rim and ember specks."""
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    for x in range(W):
        top = 1 + round(2 * nz(x * 16 / W * 3, 0))
        bottom = 7 + round(3.5 * nz(x * 16 / W * 3, 5))
        for y in range(top, min(H, bottom + 1)):
            if y == top:
                c = hexc('#6e2a24') if nz(x * 0.9, 9) > 0.55 else hexc('#4a2026')
            elif y == top + 1:
                c = HELLROCK[1]
            elif y >= bottom - 1:
                c = HELLROCK[4]
            else:
                c = HELLROCK[3] if (x * 7 + y * 3) % 11 else HELLROCK[4]
            px[x, y] = c
        if nz(x * 1.3, 17) > 0.78:
            px[x, top + 2] = GLOW[2]
    return img


# ---------------------------------------------------------------- ground decorations
def bone(px, w, h, x0, y0, x1, y1, pal=DIM_BONE, knob=1.3):
    line(px, w, h, x0, y0, x1, y1, pal[1])
    line(px, w, h, x0, y0 + 1, x1, y1 + 1, pal[3])
    for cx, cy in ((x0, y0), (x1, y1)):
        ellipse(px, w, h, cx, cy, knob, knob, pal[2])
        put(px, w, h, int(cx), int(cy) - 1, pal[0])


def skull(px, w, h, cx, cy, pal=DIM_BONE, eyes=True):
    ellipse(px, w, h, cx, cy, 5, 4.2, pal[2])
    ellipse(px, w, h, cx - 0.5, cy - 1, 3.8, 3, pal[1])
    for y in range(int(cy + 3), int(cy + 6)):
        for x in range(int(cx - 3), int(cx + 3)):
            put(px, w, h, x, y, pal[2] if (x + y) % 2 else pal[3])
    for ex in (cx - 2.5, cx + 1.5):
        ellipse(px, w, h, ex, cy + 0.5, 1.3, 1.3, OBSIDIAN[4])
        if eyes:
            put(px, w, h, int(ex), int(cy + 0.5), GLOW[2])
    put(px, w, h, int(cx - 0.5), int(cy + 2.5), OBSIDIAN[4])


def crystal(px, w, h, x, base, height, lean, pal=GLOW):
    for i in range(height):
        t = i / height
        half = 2.2 * (1 - t) + 0.4
        cx = x + lean * t
        for dx in range(int(-half), int(half) + 1):
            c = pal[1] if dx < 0 else pal[2] if dx == 0 else pal[3]
            if i == height - 1:
                c = pal[0]
            put(px, w, h, int(round(cx + dx)), base - i, c)


def ground_embed(seed=40):
    """Things buried in the ground body: 24x16 frames, transparent around them."""
    fw, fh = 24, 16
    frames = []
    # 0 ribcage
    f = new(fw, fh); px = f.load()
    bone(px, fw, fh, 3, 8, 20, 8)
    for i in range(4):
        x = 6 + i * 4
        for k in range(6):
            a = k / 5 * math.pi * 0.9
            put(px, fw, fh, int(x + 2.5 * math.sin(a)), int(8 - 5 * math.cos(a) + 5), DIM_BONE[2])
            put(px, fw, fh, int(x + 2.5 * math.sin(a)), int(8 - 5 * math.cos(a) + 0), DIM_BONE[1] if k < 3 else DIM_BONE[3])
    frames.append(f)
    # 1 skull with a glint in its sockets
    f = new(fw, fh); px = f.load()
    skull(px, fw, fh, 11, 6)
    bone(px, fw, fh, 16, 12, 22, 9)
    frames.append(f)
    # 2 crossed bones
    f = new(fw, fh); px = f.load()
    bone(px, fw, fh, 3, 3, 20, 12)
    bone(px, fw, fh, 4, 12, 19, 4)
    frames.append(f)
    # 3 cluster of glowing crystals in a pocket
    f = new(fw, fh); px = f.load()
    ellipse(px, fw, fh, 12, 11, 9, 4, HELLROCK[4])
    for x, ht, ln in ((7, 8, -2), (11, 12, 0), (15, 9, 2), (18, 6, 3)):
        crystal(px, fw, fh, x, 13, ht, ln)
    frames.append(f)
    # 4 magma pocket
    f = new(fw, fh); px = f.load()
    ellipse(px, fw, fh, 12, 8, 10, 5.5, HELLROCK[4])
    ellipse(px, fw, fh, 12, 8.5, 8, 4, GLOW[3])
    ellipse(px, fw, fh, 12, 9, 6, 2.8, GLOW[2])
    ellipse(px, fw, fh, 11, 9, 3, 1.4, GLOW[1])
    put(px, fw, fh, 9, 8, GLOW[0])
    frames.append(f)
    # 5 buried horn
    f = new(fw, fh); px = f.load()
    for i in range(18):
        t = i / 17
        cx, cy = 3 + 18 * t, 12 - 8 * math.sin(t * math.pi * 0.6)
        r = 3 * (1 - t) + 0.5
        ellipse(px, fw, fh, cx, cy, r, r, DIM_BONE[3] if i % 4 else DIM_BONE[4])
        put(px, fw, fh, int(cx), int(cy - r + 0.5), DIM_BONE[2])
    frames.append(f)
    return frames


def ground_surface(seed=41):
    """Decorations standing on the ground, 24x24 frames, bottom-aligned (drawn behind the player)."""
    fw, fh = 24, 24
    frames = []
    base = fh - 1
    # 0 bone spikes poking out
    f = new(fw, fh); px = f.load()
    for x, ht, ln in ((6, 12, -2), (11, 18, 1), (16, 10, 3)):
        for i in range(ht):
            t = i / ht
            half = 1.6 * (1 - t) + 0.3
            for dx in range(int(-half), int(half) + 1):
                put(px, fw, fh, int(round(x + ln * t * t + dx)), base - i,
                    DIM_BONE[1] if dx < 0 else DIM_BONE[2] if dx == 0 else DIM_BONE[3])
    frames.append(f)
    # 1 skull impaled on a stake
    f = new(fw, fh); px = f.load()
    line(px, fw, fh, 12, base, 12, 4, OBSIDIAN[3])
    line(px, fw, fh, 13, base, 13, 6, OBSIDIAN[4])
    put(px, fw, fh, 12, 2, OBSIDIAN[2]); put(px, fw, fh, 12, 3, OBSIDIAN[2])
    skull(px, fw, fh, 12.5, 9)
    for y in range(13, 17):
        put(px, fw, fh, 12 + (y % 2), y, CRIMSON[3])
    frames.append(f)
    # 2 black thorns
    f = new(fw, fh); px = f.load()
    for x, ht, ln in ((4, 9, -3), (8, 15, -1), (12, 20, 1), (16, 13, 3), (20, 8, 4)):
        for i in range(ht):
            t = i / ht
            half = 1.8 * (1 - t) + 0.2
            for dx in range(int(-half), int(half) + 1):
                put(px, fw, fh, int(round(x + ln * t * t + dx)), base - i,
                    hexc('#5a1e1e') if dx < 0 else OBSIDIAN[3] if dx == 0 else OBSIDIAN[4])
    frames.append(f)
    # 3 glowing crystal shards
    f = new(fw, fh); px = f.load()
    for x, ht, ln in ((7, 9, -3), (11, 16, -1), (14, 12, 2), (18, 7, 3)):
        crystal(px, fw, fh, x, base, ht, ln)
    frames.append(f)
    # 4 broken stake with a hanging chain
    f = new(fw, fh); px = f.load()
    for y in range(6, fh):
        put(px, fw, fh, 9, y, BLACK_IRON[2]); put(px, fw, fh, 10, y, BLACK_IRON[3]); put(px, fw, fh, 11, y, BLACK_IRON[4])
    put(px, fw, fh, 10, 5, BLACK_IRON[2]); put(px, fw, fh, 11, 4, BLACK_IRON[3])
    for i in range(10):
        a = i / 9
        cx, cy = 12 + 8 * a, 8 + 9 * math.sin(a * math.pi * 0.8)
        put(px, fw, fh, int(cx), int(cy), BLACK_IRON[1] if i % 2 else BLACK_IRON[3])
    frames.append(f)
    # 5 rib bones curling up out of the ground
    f = new(fw, fh); px = f.load()
    for x, ht, d in ((5, 14, 1), (10, 18, 1), (15, 17, -1), (20, 12, -1)):
        for i in range(ht):
            t = i / ht
            cx = x + d * 5 * math.sin(t * math.pi * 0.55)
            put(px, fw, fh, int(round(cx)), base - i, DIM_BONE[2])
            put(px, fw, fh, int(round(cx)) + 1, base - i, DIM_BONE[3])
    frames.append(f)
    return frames


# ---------------------------------------------------------------- floating platforms (3 variants)
def _bricks(W, H, bh, bw, pal, mortar, seed, stagger=True):
    img = new(W, H)
    px = img.load()
    rng = random.Random(seed)
    tones = {}
    for y in range(H):
        row = y // bh
        off = (bw // 2) * (row % 2) if stagger else 0
        for x in range(W):
            col = ((x + off) % W) // bw
            lx, ly = (x + off) % bw, y % bh
            key = (row, col)
            if key not in tones:
                tones[key] = rng.choice((0, 0, 1, -1))
            if ly == bh - 1 or lx == bw - 1:
                px[x, y] = mortar
                continue
            s = 2 + tones[key]
            if ly == 0 or lx == 0:
                s -= 1
            elif ly == bh - 2 or lx == bw - 2:
                s += 1
            if rng.random() < 0.07:
                s += rng.choice((-1, 1))
            px[x, y] = pal[max(0, min(4, s))]
    return img


def platform_iron(W=32, H=32):
    """Riveted black iron plates with red heat glowing through the seams."""
    img = _bricks(W, H, 16, 16, BLACK_IRON, GLOW[3], 3, stagger=False)
    px = img.load()
    for y in range(H):
        for x in range(W):
            if x % 16 == 15 and y % 16 == 7:
                px[x, y] = GLOW[2]
    for y0 in (0, 16):
        for x0 in (0, 16):
            for rx, ry in ((2, 2), (12, 2), (2, 12), (12, 12)):
                put(px, W, H, x0 + rx, y0 + ry, BLACK_IRON[0])
                put(px, W, H, x0 + rx + 1, y0 + ry + 1, BLACK_IRON[4])
            # scratches
            line(px, W, H, x0 + 5, y0 + 9, x0 + 9, y0 + 6, BLACK_IRON[1])
    return img


def platform_iron_top(W=32, H=8):
    img = new(W, H)
    px = img.load()
    for y in range(H):
        for x in range(W):
            c = BLACK_IRON[0] if y == 0 else BLACK_IRON[1] if y == 1 else BLACK_IRON[2] if y < H - 2 else BLACK_IRON[4]
            if 2 <= y <= 5 and x % 8 == 4:
                c = BLACK_IRON[0] if y == 3 else BLACK_IRON[4] if y == 4 else c
            px[x, y] = c
    return img


def platform_iron_under(W=32, H=10):
    """Short chains and a hook hanging under the iron platforms."""
    img = new(W, H)
    px = img.load()
    for cx, ln in ((7, 8), (24, 6)):
        for y in range(ln):
            if (y // 2) % 2:
                put(px, W, H, cx, y, BLACK_IRON[2])
            else:
                put(px, W, H, cx - 1, y, BLACK_IRON[2]); put(px, W, H, cx + 1, y, BLACK_IRON[3])
        put(px, W, H, cx, ln, BLACK_IRON[1]); put(px, W, H, cx + 1, ln + 1, BLACK_IRON[2])
    return img


def platform_obsidian(W=32, H=32):
    """Obsidian bricks set in bone-white mortar, with a sheen streak on each brick."""
    img = _bricks(W, H, 8, 16, OBSIDIAN, DIM_BONE[3], 7)
    px = img.load()
    for y in range(H):
        row = y // 8
        off = 8 * (row % 2)
        for x in range(W):
            lx, ly = (x + off) % 16, y % 8
            if ly < 7 and lx < 15 and lx - ly == 3:
                px[x, y] = OBSIDIAN[0] if ly < 2 else OBSIDIAN[1]
    # a small skull keystone
    skull(px, W, H, 16, 19, eyes=True)
    return img


def platform_obsidian_top(W=32, H=8):
    """Cap of vertebrae."""
    img = new(W, H)
    px = img.load()
    for x in range(W):
        for y in range(H):
            px[x, y] = OBSIDIAN[3] if y >= 5 else (0, 0, 0, 0)
    for i in range(W // 4):
        cx = i * 4 + 2
        ellipse(px, W, H, cx, 3, 1.8, 2.2, DIM_BONE[2])
        put(px, W, H, cx - 1, 1, DIM_BONE[0]); put(px, W, H, cx, 1, DIM_BONE[1])
        put(px, W, H, cx + 1, 5, DIM_BONE[4])
    for x in range(W):
        px[x, H - 1] = OBSIDIAN[4]
    return img


def platform_obsidian_under(W=32, H=10):
    """Bone tips poking out of the underside."""
    img = new(W, H)
    px = img.load()
    for x, ln in ((5, 6), (13, 9), (21, 5), (27, 7)):
        for i in range(ln):
            t = i / ln
            put(px, W, H, x, i, DIM_BONE[2] if t < 0.7 else DIM_BONE[1])
            if t < 0.4:
                put(px, W, H, x + 1, i, DIM_BONE[3])
    return img


RUNES = [
    [(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 1), (2, 2), (1, 3)],
    [(0, 0), (1, 1), (2, 2), (2, 0), (0, 2), (1, 3), (1, 4)],
    [(1, 0), (1, 1), (1, 2), (1, 3), (1, 4), (0, 1), (2, 1), (0, 3), (2, 3)],
    [(0, 4), (1, 3), (1, 2), (1, 1), (2, 0), (0, 0), (2, 4)],
]


def platform_runes(W=32, H=32):
    """Cracked dark stone blocks carved with glowing runes."""
    img = _bricks(W, H, 16, 16, HELLROCK, HELLROCK[4], 11)
    px = img.load()
    k = 0
    for y0 in (0, 16):
        for x0 in (0, 16):
            off = 8 * ((y0 // 16) % 2)
            rx = (x0 + off + 6) % W
            for dx, dy in RUNES[k % len(RUNES)]:
                put(px, W, H, (rx + dx) % W, y0 + 5 + dy, GLOW[2])
                put(px, W, H, (rx + dx + 1) % W, y0 + 6 + dy, mix(px[(rx + dx + 1) % W, y0 + 6 + dy], GLOW[3], 0.5))
            k += 1
    # crack with heat inside
    for i, (x, y) in enumerate(((3, 20), (4, 21), (5, 21), (6, 22), (7, 24), (8, 25))):
        put(px, W, H, x, y, GLOW[3] if i % 2 else GLOW[2])
    return img


def platform_runes_top(W=32, H=8):
    img = new(W, H)
    px = img.load()
    for y in range(H):
        for x in range(W):
            if x % 16 == 15:
                c = HELLROCK[4]
            elif y == 0:
                c = HELLROCK[0]
            elif y == 1:
                c = HELLROCK[1]
            elif y >= H - 2:
                c = HELLROCK[4] if y == H - 1 else HELLROCK[3]
            else:
                c = HELLROCK[2]
            px[x, y] = c
    for x in (4, 11, 20, 27):
        put(px, W, H, x, 3, GLOW[2]); put(px, W, H, x, 4, GLOW[3])
    return img


def platform_runes_under(W=32, H=10):
    """Rock drips with molten tips."""
    img = new(W, H)
    px = img.load()
    for x, ln in ((6, 7), (17, 9), (26, 5)):
        for i in range(ln):
            half = 2 * (1 - i / ln) + 0.3
            for dx in range(int(-half), int(half) + 1):
                put(px, W, H, x + dx, i, HELLROCK[2] if dx <= 0 else HELLROCK[3])
        put(px, W, H, x, ln - 1, GLOW[2]); put(px, W, H, x, ln, GLOW[3])
    return img


# ---------------------------------------------------------------- backgrounds
W, H = 480, 216


def hmap(nz, x, scale, octaves=3):
    v, amp, tot = 0, 1, 0
    for o in range(octaves):
        v += nz(x * scale * (2 ** o), o * 7) * amp
        tot += amp
        amp *= 0.5
    return v / tot


VOID = hexc('#040205')
ABYSS = ramp('#060309', '#0c050a', '#17070c', '#260a0e', '#3d0e0e', '#5c1610')   # black .. hell glow
EYE = ramp('#fff1b0', '#ffc23d', '#ff7a1f', '#d1361a', '#7a1410')


def ridge(nz, x, scale, octaves=4):
    """Ridged noise (0..1): sharp creases instead of soft hills -> jagged silhouettes."""
    v, amp, tot = 0, 1, 0
    for o in range(octaves):
        n = nz(x * scale * (2 ** o), o * 11 + 3)
        v += (1 - abs(2 * n - 1)) * amp
        tot += amp
        amp *= 0.55
    return v / tot


def glow_add(px, w, h, cx, cy, r, col, strength):
    """Soft additive glow (opaque pixels only), wraps horizontally."""
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            d = math.hypot(x - cx, (y - cy) * 1.4) / r
            if d >= 1 or not 0 <= y < h:
                continue
            p = px[x % w, y]
            if p[3] == 0:
                continue
            # quantised falloff keeps it pixel-art (no smooth airbrush)
            k = strength * round((1 - d) ** 2 * 4) / 4
            px[x % w, y] = mix(p, col, k)


def slit_eye(px, w, h, x0, y0, x1, y1, thick):
    """A narrow, slanted glowing eye with a vertical black pupil."""
    n = int(abs(x1 - x0))
    for i in range(n + 1):
        t = i / n
        cx = x0 + (x1 - x0) * t
        cy = y0 + (y1 - y0) * t
        half = thick * math.sin(math.pi * t) ** 0.7
        for dy in range(-int(half) - 1, int(half) + 2):
            e = abs(dy) / max(0.5, half)
            if e > 1.05:
                continue
            c = EYE[0] if e < 0.35 else EYE[1] if e < 0.7 else EYE[2]
            if t < 0.08 or t > 0.92:
                c = EYE[3]
            put(px, w, h, int(cx) % w, int(round(cy + dy)), c)
    # pupil: thin vertical slit slightly off-centre
    px_ = int((x0 + x1) / 2 + (x1 - x0) * 0.08)
    py = (y0 + y1) / 2
    for dy in range(-int(thick), int(thick) + 1):
        put(px, w, h, px_ % w, int(round(py + dy)), VOID)


def bg_far(seed=10):
    """Opaque far layer: black sky over a blood-red glow from the abyss, jagged distant cliffs,
    and a colossal horned silhouette watching with glowing slit eyes. Fades to black at the bottom."""
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    glow_y = 138
    for y in range(H):
        for x in range(W):
            # brightest band just behind the cliff tops, black above and below (bottomless pit)
            d = (y - glow_y) / (glow_y if y < glow_y else (H - glow_y))
            v = max(0.0, 1 - abs(d) ** 0.8)
            # slow smoke streaks
            v *= 0.8 + 0.35 * nz(x * 16 / W * 3, y * 0.07)
            v = min(1.0, v)
            px[x, y] = ABYSS[len(ABYSS) - 1 - dither_index(1 - v, x, y, len(ABYSS), band=0.1)]

    # colossal horned head behind the cliffs (drawn first so the ridges overlap its base)
    head = ramp('#14060a')[0]
    rim = hexc('#5a1612')
    hx, hy = 250, 112
    # two devil horns: sweep outwards from the skull, then curl up and back in at the tip
    horn = []
    for side in (-1, 1):
        for i in range(80):
            t = i / 79
            cx = hx + side * (30 + 58 * math.sin(t * math.pi / 2) - 20 * t ** 3)
            cy = hy - 34 - 62 * t ** 1.5
            horn.append((cx, cy, 11 * (1 - t) ** 0.85 + 0.5))
    for y in range(0, H):
        for x in range(hx - 95, hx + 96):
            dx, dy = x - hx, y - hy
            inside = (dx / 54) ** 2 + (dy / 50) ** 2 <= 1 or (y > hy + 20 and abs(dx) < 50 + (y - hy - 20) * 1.6)
            if not inside:
                inside = any((x - cx) ** 2 + (y - cy) ** 2 <= r * r for cx, cy, r in horn)
            if inside:
                px[x % W, y] = head
    # rim light on the head/horn silhouette from the glow behind
    src = img.copy().load()
    for y in range(1, H):
        for x in range(hx - 97, hx + 98):
            if src[x % W, y] == head and y < H - 1 and any(
                    src[(x + dx) % W, y + dy] != head for dx, dy in ((0, -1), (-1, 0), (1, 0))):
                px[x % W, y] = rim

    # two ridges of jagged distant cliffs
    for base, amp, col, sc, off in ((168, 64, hexc('#1d080c'), 16 / W * 2, 0),
                                     (192, 58, hexc('#0e0508'), 16 / W * 3, 41)):
        edge = mix(col, hexc('#7a1c12'), 0.45)
        for x in range(W):
            hh = int(base - amp * ridge(nz, x + off, sc) ** 2.2)
            for y in range(max(0, hh), H):
                fade = max(0.0, (y - 175) / (H - 175))
                px[x, y] = mix(col, VOID, fade)
            if 0 <= hh < H:
                px[x, hh] = edge

    # the eyes, angry slant: outer corners high, inner corners low
    ey = hy - 4
    slit_eye(px, W, H, hx - 40, ey - 6, hx - 10, ey + 2, 2.2)
    slit_eye(px, W, H, hx + 40, ey - 6, hx + 10, ey + 2, 2.2)
    for sx in (hx - 25, hx + 25):
        glow_add(px, W, H, sx, ey - 1, 26, hexc('#c0301a'), 0.55)
    # repaint eye cores over the glow
    slit_eye(px, W, H, hx - 40, ey - 6, hx - 10, ey + 2, 2.2)
    slit_eye(px, W, H, hx + 40, ey - 6, hx + 10, ey + 2, 2.2)
    return img


def fang(px, x0, base_y, width, length, bend, down, body, lit, dark):
    """Curved tapered spike. down=True hangs from base_y, else rises from it.
    Lit from below (red abyss glow) on the lower/left edge."""
    sgn = 1 if down else -1
    for i in range(int(length) + 1):
        t = i / length
        y = int(base_y + sgn * i)
        if not 0 <= y < H:
            continue
        cx = x0 + bend * t * t
        half = width / 2 * (1 - t) ** 1.15
        if half < 0.35:
            half = 0.35 if t < 0.999 else 0
        for x in range(int(math.floor(cx - half)), int(math.ceil(cx + half))):
            rel = (x + 0.5 - (cx - half)) / max(0.7, 2 * half)
            c = body
            if rel < 0.22:
                c = lit
            elif rel > 0.8:
                c = dark
            px[x % W, y] = c


def bg_mid(seed=20):
    """Transparent middle layer: a jaw of fangs from the ceiling, curved horn/rib spikes from the abyss,
    hanging chains. The bottom sinks into black."""
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    rng = random.Random(seed)
    body, lit, dark = hexc('#1a090d'), hexc('#4c1412'), hexc('#0b0407')
    lit_hot = hexc('#8a2414')

    # ceiling band, jagged
    for x in range(W):
        th = 6 + 10 * ridge(nz, x, 16 / W * 5)
        for y in range(int(th)):
            px[x, y] = lit if y == int(th) - 1 else body
    # chains (behind the fangs)
    for cx, ln in ((96, 120), (318, 92), (402, 140)):
        for y in range(0, ln):
            link = (y // 3) % 2
            c = hexc('#2a1416') if (y % 3) else hexc('#3d1a18')
            if link:
                put(px, W, H, cx, y, c)
            else:
                put(px, W, H, cx - 1, y, c)
                put(px, W, H, cx + 1, y, c)
        # hook at the end
        for i in range(9):
            a = i / 8 * math.pi * 1.1
            put(px, W, H, int(cx + 3 - 3 * math.cos(a)), int(ln + 3 * math.sin(a)), hexc('#4a1e1a'))
        put(px, W, H, cx + 6, ln - 1, hexc('#5a2420'))
    # fangs: big ones spaced out, small teeth in between
    for _ in range(26):
        x0 = rng.randrange(W)
        big = rng.random() < 0.4
        ln = rng.uniform(45, 95) if big else rng.uniform(14, 40)
        # rooted in the top edge so they read as part of the ceiling, like the chains
        fang(px, x0, 0, rng.uniform(9, 16) if big else rng.uniform(4, 8), ln + 8,
             rng.uniform(-6, 6), True, body, lit, dark)

    # horn/rib spikes rising from the abyss, curved, some crossing each other
    for i in range(18):
        x0 = (i * W / 18 + rng.uniform(-8, 8)) % W
        big = i % 3 == 0
        ln = rng.uniform(95, 140) if big else rng.uniform(45, 90)
        bend = rng.choice((-1, 1)) * rng.uniform(10, 30 if big else 18)
        fang(px, x0, H - 1, rng.uniform(14, 20) if big else rng.uniform(7, 12), ln,
             bend, False, body, lit_hot if big else lit, dark)
    # jagged rock floor mass
    for x in range(W):
        top = H - 22 - 26 * ridge(nz, x + 200, 16 / W * 6) ** 1.8
        for y in range(int(top), H):
            px[x, y] = lit if y == int(top) else body

    # sink the bottom into the black pit (and tint the lower half with faint red bounce)
    for y in range(H // 2, H):
        f = max(0.0, (y - (H - 60)) / 60)
        for x in range(W):
            p = px[x, y]
            if p[3]:
                px[x, y] = mix(p, VOID, round(f * 6) / 6)
    return img


def generate(out_dir):
    items = {
        'Ground': [ground_tile()],
        'GroundTop': [ground_top()],
        'GroundEmbed': ground_embed(),
        'GroundSurface': ground_surface(),
        'Platform': [platform_iron(), platform_obsidian(), platform_runes()],
        'PlatformTop': [platform_iron_top(), platform_obsidian_top(), platform_runes_top()],
        'PlatformUnder': [platform_iron_under(), platform_obsidian_under(), platform_runes_under()],
        'bg_far': [bg_far()],
        'bg_mid': [bg_mid()],
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
