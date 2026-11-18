"""Terrain tiles (seamless in both directions unless noted) and parallax background layers
(seamless horizontally). Tiles are meant to be drawn at x2 via a world-anchored ImagePattern;
backgrounds are 480x216 and scale x5 to 2400x1080.

  Ground        64x64  basalt rock (fills ground bodies)
  GroundTop     64x12  crust strip for the top edge of ground (horizontal tiling, transparent below)
  Platform      32x32  brown stone bricks (structures)
  PlatformTop   32x8   cap stones for the top edge of platforms
  bg_far        480x216 opaque, slowest layer
  bg_mid        480x216 transparent, middle layer
  bg_lava       4 frames of 480x216, transparent above the lava, front layer
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


def bg_far(seed=10):
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    for y in range(H):
        t = y / (H - 1)
        for x in range(W):
            # sky gradient (dark at the top, red glow at the bottom) with dithered bands
            v = (1 - t) * 4.2
            px[x, y] = SKY[dither_index(v / 4, x, y, 5, band=0.12)]
    # two rows of distant spires (periodic over W)
    for layer, (base, amp, col, sc) in enumerate(((150, 90, ramp('#5e2c30'), 16 / W * 2),
                                                   (175, 70, ramp('#4a2228'), 16 / W * 3))):
        for x in range(W):
            hh = base - amp * hmap(nz, x + layer * 37, sc) ** 1.6
            for y in range(int(hh), H):
                px[x, y] = col[0]
            # faint rim light on the top pixel
            if 0 <= int(hh) < H:
                px[x, int(hh)] = mix(col[0], hexc('#a03a24'), 0.35)
    return img


def bg_mid(seed=20):
    img = new(W, H)
    px = img.load()
    nz = Noise(seed, n=16)
    rock = ramp('#3a2026', '#2a171d', '#1f1116', '#160c10', '#0e080b')
    glow = hexc('#e2582a')
    # stalactites hanging from the ceiling
    for x in range(W):
        v = hmap(nz, x, 16 / W * 6)
        length = 10 + 70 * max(0, v - 0.35) ** 1.3 * 2.2
        for y in range(0, int(length)):
            edge = (y == int(length) - 1)
            s = 2 if (x * 3 + y) % 13 else 3
            px[x, y] = rock[3] if edge else rock[s]
    # rock pillars / mounds rising from the bottom, lit from the lava below
    for x in range(W):
        v = hmap(nz, x + 300, 16 / W * 4, octaves=2)
        top = H - 20 - 120 * max(0, v - 0.42) ** 1.0 * 1.8
        for y in range(int(top), H):
            d = y - top
            s = 1 if d < 2 else 2 if d < 14 else 3
            if s == 2 and nz(x * 0.4, y * 0.4 + 50) > 0.62:
                s = 1
            elif s >= 2 and (x * 5 + y * 3) % 17 == 0:
                s = min(4, s + 1)
            c = rock[s]
            if y > H - 40:
                c = mix(c, glow, (y - (H - 40)) / 40 * 0.45)
            px[x, y] = c
    # rim light on the left/right silhouette edges near the bottom
    src = img.copy().load()
    for y in range(H // 2, H):
        for x in range(W):
            if src[x, y][3] and (src[(x - 1) % W, y][3] == 0 or src[(x + 1) % W, y][3] == 0):
                px[x, y] = mix(src[x, y], glow, 0.5)
    return img


def bg_lava(n=4, seed=30):
    frames = []
    nz = Noise(seed, n=16)
    surface = H - 34
    for f in range(n):
        img = new(W, H)
        px = img.load()
        ph = f / n * 2 * math.pi
        # glow above the surface
        for y in range(surface - 40, surface):
            a = int(110 * ((y - (surface - 40)) / 40) ** 2)
            for x in range(W):
                px[x, y] = (255, 110, 40, a)
        for x in range(W):
            wave = 2.2 * math.sin(x / W * 2 * math.pi * 12 + ph) + 1.2 * math.sin(x / W * 2 * math.pi * 31 - ph * 2)
            top = int(surface + wave)
            for y in range(top, H):
                d = y - top
                # crust blobs drift: time is the second noise axis (period 16 -> f * 4 loops)
                crust = nz(x * 16 / W * 8, f * 4 + d * 0.15)
                if d == 0:
                    c = FIRE[1]
                elif d == 1:
                    c = FIRE[2]
                elif crust > 0.66:
                    c = hexc('#4a1a12') if crust > 0.75 else FIRE[5]
                else:
                    k = 2 + min(2, d // 6) + ((x + y + f) % 2 if d % 6 == 5 else 0)
                    c = FIRE[min(4, k)]
                px[x, y] = c
        # bubbles
        rng = random.Random(seed)
        for b in range(14):
            bx = rng.randrange(W)
            stage = (f + b) % n
            by = surface + 6 - stage * 2
            r = [1, 2, 2, 0][stage]
            if r:
                ellipse(px, W, H, bx, by, r + 0.5, r + 0.5, FIRE[1])
                put(px, W, H, bx - 1, by - 1, FIRE[0])
            else:
                put(px, W, H, bx, surface - 2, FIRE[2])
                put(px, W, H, bx + 2, surface - 4, FIRE[3])
        frames.append(img)
    return frames


def generate(out_dir):
    items = {
        'Ground': [ground_tile()],
        'GroundTop': [ground_top()],
        'Platform': [platform_tile()],
        'PlatformTop': [platform_top()],
        'bg_far': [bg_far()],
        'bg_mid': [bg_mid()],
        'bg_lava': bg_lava(),
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
