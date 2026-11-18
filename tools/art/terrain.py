"""Terrain tiles (seamless in both directions unless noted) and parallax background layers
(seamless horizontally). Tiles are meant to be drawn at x2 via a world-anchored ImagePattern;
backgrounds are 480x216 and scale x5 to 2400x1080.

  Ground        64x64  basalt rock (fills ground bodies)
  GroundTop     64x12  crust strip for the top edge of ground (horizontal tiling, transparent below)
  Platform      32x32  brown stone bricks (structures)
  PlatformTop   32x8   cap stones for the top edge of platforms
  bg_far        480x216 opaque, slowest layer
  bg_mid        480x216 transparent, middle layer
"""
import math
import random
from pixel import *
from materials import *
from effects import Noise
from projectiles import FIRE

BASALT = ramp('#6b6470', '#4e4855', '#3a3540', '#29252e', '#1a171e')
ASH = ramp('#9a929c', '#7a727e', '#5d5662', '#433d48', '#2c2731')
BRICK = ramp('#c08a5c', '#9a6a44', '#7a5134', '#5a3a25', '#3a2416')
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


def ground_tile(S=64, seed=1):
    img = new(S, S)
    px = img.load()
    pts, near = voronoi_tile(S, 14, seed)
    nz = Noise(seed, n=16)
    rng = random.Random(seed + 5)
    tone = [rng.choice((0, 0, 1)) for _ in pts]
    for y in range(S):
        for x in range(S):
            i, edge = near[y][x]
            if edge < 1.1:
                px[x, y] = BASALT[4]
                continue
            # lit along the top-left of each stone: compare with the cell a step up-left
            ul = near[(y - 2) % S][(x - 2) % S]
            dr = near[(y + 2) % S][(x + 2) % S]
            s = 2 + tone[i]
            if ul[0] != i or ul[1] < 1.1:
                s -= 1
            elif dr[0] != i or dr[1] < 1.1:
                s += 1
            n = nz(x * 16 / S * 2, y * 16 / S * 2)
            if n > 0.68:
                s -= 1
            elif n < 0.3 and (x + y) % 2 == 0:
                s += 1
            px[x, y] = BASALT[max(0, min(4, s))]
    # a few glowing cracks inside crevices
    for y in range(S):
        for x in range(S):
            i, edge = near[y][x]
            if edge < 1.1 and nz(x * 0.25, y * 0.25 + 30) > 0.82:
                px[x, y] = FIRE[4] if (x + y) % 3 else FIRE[3]
    return img


def ground_top(W=64, H=12, seed=2):
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    for x in range(W):
        top = 1 + round(1.6 * nz(x * 16 / W * 2, 0))
        bottom = 7 + round(3.5 * nz(x * 16 / W * 3, 5))
        for y in range(top, min(H, bottom + 1)):
            if y == top:
                c = ASH[0] if nz(x * 0.9, 9) > 0.45 else ASH[1]
            elif y == top + 1:
                c = ASH[1]
            elif y >= bottom - 1:
                c = BASALT[4] if y == bottom else ASH[3]
            else:
                c = ASH[2] if (x * 7 + y * 3) % 11 else ASH[3]
            px[x, y] = c
        # ember specks
        if nz(x * 1.3, 17) > 0.8:
            px[x, top + 2] = FIRE[3]
    return img


def platform_tile(W=32, H=32, seed=3):
    img = new(W, H)
    px = img.load()
    rng = random.Random(seed)
    bh, bw = 8, 16
    tones = {}
    for y in range(H):
        row = y // bh
        off = (bw // 2) * (row % 2)
        for x in range(W):
            col = ((x + off) % W) // bw
            lx, ly = (x + off) % bw, y % bh
            key = (row, col)
            if key not in tones:
                tones[key] = rng.choice((0, 0, 1, -1))
            if ly == bh - 1 or lx == bw - 1:
                px[x, y] = BRICK[4]
                continue
            s = 2 + tones[key]
            if ly == 0 or lx == 0:
                s -= 1
            elif ly == bh - 2 or lx == bw - 2:
                s += 1
            if rng.random() < 0.08:
                s += rng.choice((-1, 1))
            px[x, y] = BRICK[max(0, min(4, s))]
    return img


def platform_top(W=32, H=8):
    img = new(W, H)
    px = img.load()
    for y in range(H):
        for x in range(W):
            lx = x % 16
            if lx == 15:
                c = BRICK[4]
            elif y == 0:
                c = BRICK[0]
            elif y == 1:
                c = BRICK[1]
            elif y >= H - 2:
                c = BRICK[4] if y == H - 1 else BRICK[3]
            else:
                c = BRICK[2] if lx else BRICK[1]
            px[x, y] = c
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
        'Platform': [platform_tile()],
        'PlatformTop': [platform_top()],
        'bg_far': [bg_far()],
        'bg_mid': [bg_mid()],
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
