"""Effects (frame strips) and the chest. Intended display scale x2 unless Java scales to a radius.

Explosions are centred in their frame; Shockwave_dust and BurningGround are bottom-anchored;
MagicShield is centred on the player; StunStars sit above the head (bottom-centre anchor).
Chest frames: 0-1 idle (1 = glint), 2-5 opening, 6 open + glow, 7 open and empty. Bottom-centre anchor.
"""
import math
import random
from pixel import *
from materials import *
from projectiles import FIRE, MISSILE

SMOKE = ramp('#6e6672', '#524b58', '#3c3743', '#2a2630')
DUST = ramp('#e8d2a8', '#c4a77a', '#9b7f57', '#6e573a', '#47371f')
EMBER = FIRE


class Noise:
    """Small periodic 1D/2D value noise (deterministic)."""
    def __init__(self, seed, n=16):
        r = random.Random(seed)
        self.n = n
        self.v = [[r.random() for _ in range(n)] for _ in range(n)]

    def __call__(self, x, y=0.0):
        n = self.n
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = self.v[y0 % n][x0 % n]
        b = self.v[y0 % n][(x0 + 1) % n]
        c = self.v[(y0 + 1) % n][x0 % n]
        d = self.v[(y0 + 1) % n][(x0 + 1) % n]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


class WrapNoise:
    """Value noise over a W x H pixel area that wraps seamlessly in both directions.

    fx / fy are lattice points per pixel (roughly the feature frequency); they are rounded so a whole
    number of lattice cells fits into W and H, which is what makes the texture tile without a seam."""
    def __init__(self, seed, W, H, fx, fy=None):
        fy = fx if fy is None else fy
        self.nx = max(1, round(W * fx))
        self.ny = max(1, round(H * fy))
        self.sx, self.sy = self.nx / W, self.ny / H
        r = random.Random(seed)
        self.v = [[r.random() for _ in range(self.nx)] for _ in range(self.ny)]

    def __call__(self, x, y=0.0):
        x, y = x * self.sx, y * self.sy
        nx, ny = self.nx, self.ny
        x0, y0 = math.floor(x), math.floor(y)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        r0, r1 = self.v[y0 % ny], self.v[(y0 + 1) % ny]
        a, b = r0[x0 % nx], r0[(x0 + 1) % nx]
        c, d = r1[x0 % nx], r1[(x0 + 1) % nx]
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


# ---------------------------------------------------------------- explosions
def explosion_fire(S=48, n=7, seed=3):
    pal = FIRE[:5] + SMOKE           # 0 white .. 4 red, 5.. smoke
    nz = Noise(seed)
    # per frame: radius, heat (palette shift), dissolve threshold
    keys = [(6, 0.0, 0), (12, 0.3, 0), (16, 1.0, 0), (19, 1.8, 0), (21, 2.7, 0.2), (22, 3.6, 0.45), (22.5, 4.6, 0.7)]
    frames = []
    c = S / 2
    for f in range(n):
        r, heat, diss = keys[f]
        img = new(S, S)
        px = img.load()
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - c, y + 0.5 - c
                d = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                edge = r * (0.82 + 0.3 * nz(a / (2 * math.pi) * 16, f * 0.7))
                if d > edge:
                    continue
                t = d / edge
                k = heat + t * 2.6 + (nz(x * 0.35, y * 0.35 + f) - 0.5) * 1.6
                if diss and nz(x * 0.5 + 40, y * 0.5 + f * 3) < diss:
                    continue
                k = int(round(k))
                if k < 0:
                    k = 0
                if k >= len(pal):
                    continue
                if f >= 5 and k < 5 and nz(x * 0.8, y * 0.8 + 9) < 0.45:
                    k = 5  # embers only in spots once the fire is dying
                px[x, y] = pal[k]
        frames.append(img)
    return frames


def explosion_ice(S=48, n=6, seed=5):
    frames = []
    c = S / 2
    rng = random.Random(seed)
    spikes = [(i / 9 * 2 * math.pi + rng.uniform(-0.2, 0.2), rng.uniform(0.75, 1.0)) for i in range(9)]
    grow = [0.25, 0.6, 0.9, 1.0, 1.0, 1.0]
    flash = [7, 9, 7, 4, 0, 0]
    ring = [0, 10, 15, 19, 21, 22]
    fade = [0, 0, 0, 0.3, 0.6, 0.85]
    nz = Noise(seed + 1)
    for f in range(n):
        img = new(S, S)
        px = img.load()
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - c, y + 0.5 - c
                d = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                col = None
                # frost ring
                if ring[f] and abs(d - ring[f]) < 1.3 and nz(a * 5, f) > 0.35:
                    col = ICE[2] if f < 4 else ICE[3]
                # shards
                for sa, sl in spikes:
                    L = 21 * sl * grow[f]
                    da = math.atan2(math.sin(a - sa), math.cos(a - sa))
                    w = 2.6 * (1 - d / max(L, 1)) if d < L else -1
                    if abs(da) * d < w:
                        side = da > 0
                        col = ICE[1 if side else 3] if d > 3 else ICE[0]
                        if abs(da) * d < 0.6:
                            col = ICE[2]
                if d < flash[f]:
                    col = ICE[0] if d < flash[f] * 0.6 else ICE[1]
                if col and fade[f] and nz(x * 0.6, y * 0.6 + f * 2) < fade[f]:
                    col = None
                if col:
                    px[x, y] = col
        frames.append(outline(img, ICE[4]) if f < 4 else img)
    return frames


def shockwave_dust(W=72, H=24, n=6, seed=9):
    frames = []
    nz = Noise(seed)
    base = H - 1
    for f in range(n):
        img = new(W, H)
        px = img.load()
        spread = 6 + f * 5.5
        diss = [0, 0, 0.1, 0.3, 0.5, 0.7][f]
        puffs = []
        for side in (-1, 1):
            for k in range(4):
                off = spread * (0.45 + k * 0.22)
                r = (7 - k * 1.3) * (0.7 + 0.12 * f)
                puffs.append((W / 2 + side * off, base - r * 0.6 - f * 0.4, r))
        for y in range(H):
            for x in range(W):
                best = None
                for cx, cy, r in puffs:
                    d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.15)
                    if d < r:
                        q = ((x + 0.5 - cx) * -0.5 + (y + 0.5 - cy) * -0.85) / r
                        v = 0 if q > 0.45 else 1 if q > 0 else 2 if q > -0.5 else 3
                        best = v if best is None else min(best, v)
                if best is None:
                    continue
                if diss and nz(x * 0.45, y * 0.45 + f * 2) < diss:
                    continue
                px[x, y] = DUST[min(4, best + f // 3)]
        # flying pebbles
        rng = random.Random(seed + f)
        for side in (-1, 1):
            for k in range(3):
                t = f + 1
                xx = W / 2 + side * (spread + 3 + k * 3)
                yy = base - 4 - k * 2 - t * 2.5 + 0.55 * t * t
                if 0 <= yy < H:
                    put(px, W, H, int(xx), int(yy), DUST[3])
        frames.append(outline(img, DUST[4]))
    return frames


def burning_ground(W=16, H=16, n=4, seed=13):
    """Seamlessly tileable flame strip (horizontally)."""
    frames = []
    nz = Noise(seed, n=W)
    for f in range(n):
        img = new(W, H)
        px = img.load()
        for x in range(W):
            # periodic noise over the tile width keeps the strip seamless
            hgt = 5 + 8 * nz(x * 0.5, f * 1.7) + 2 * nz(x * 1.0 + 3, f * 2.3)
            for y in range(H):
                up = H - 1 - y
                if up > hgt:
                    continue
                t = up / max(hgt, 1)
                k = 4 - int(t * 4.2) if t > 0.15 else 2 + (x + f) % 2
                k = max(0, min(5, 4 - int((1 - t) * 3.5)))
                if up <= 1:
                    k = 4 if (x + f) % 3 else 5
                elif t < 0.4:
                    k = 1 + ((x + y + f) % 2)
                elif t < 0.75:
                    k = 2 + ((x + y) % 3 == 0)
                else:
                    k = 3 + ((x + f) % 2)
                px[x, y] = FIRE[k]
        frames.append(img)
    return frames


def magic_shield(S=52, n=4):
    frames = []
    c = S / 2
    R = S / 2 - 1.5
    for f in range(n):
        img = new(S, S)
        px = img.load()
        shimmer = f / n * 2 * math.pi
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - c, y + 0.5 - c
                d = math.hypot(dx, dy)
                if d > R:
                    continue
                a = math.atan2(dy, dx)
                rim = R - d
                if rim < 1.2:
                    col = MISSILE[2]
                    alpha = 230
                elif rim < 2.4:
                    col = MISSILE[3]
                    alpha = 150
                else:
                    # faint hexagonal lattice + rotating shimmer band
                    hx = (x * 0.5 + y * 0.289) % 4
                    hy = (y * 0.577) % 4
                    lattice = hx < 0.5 or hy < 0.45
                    band = math.cos(a - shimmer) > 0.92 and rim < 6
                    col = MISSILE[1] if band else MISSILE[3]
                    alpha = 170 if band else 70 if lattice else 38
                # specular highlight top-left
                if -2.6 < a < -1.9 and 2 < rim < 5:
                    col, alpha = MISSILE[0], 220
                px[x, y] = (col[0], col[1], col[2], alpha)
        frames.append(img)
    return frames


def star(px, w, h, cx, cy, size, col, dark):
    put(px, w, h, cx, cy, col[0])
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(px, w, h, cx + dx, cy + dy, col[1] if size else dark)
        if size > 1:
            put(px, w, h, cx + 2 * dx, cy + 2 * dy, col[2])


def stun_stars(W=26, H=12, n=4):
    frames = []
    yellow = GOLD
    for f in range(n):
        img = new(W, H)
        px = img.load()
        items = []
        for k in range(3):
            a = (f / n + k / 3) * 2 * math.pi
            x = W / 2 + math.cos(a) * 9
            y = H / 2 + math.sin(a) * 2.5
            depth = math.sin(a)  # >0 front
            items.append((depth, int(x), int(y)))
        for depth, x, y in sorted(items):
            star(px, W, H, x, y, 2 if depth > 0.3 else 1, yellow if depth > -0.3 else ramp('#e8c56a', '#b8902f', '#8a6a20', '#5e4612', '#3a2a0a'), yellow[3])
        frames.append(outline(img))
    return frames


# ---------------------------------------------------------------- chest
def chest_frames(W=40, H=34):
    frames = []
    seq = [(0, 0), (0, 1), (0.2, 0), (0.45, 0), (0.7, 0), (0.9, 0), (1.0, 2), (1.0, 3)]
    x0, x1 = 5, W - 6
    base_top, base_bot = 18, H - 3
    hinge = base_top
    lid_h = 9
    for o, mode in seq:
        img = new(W, H)
        px = img.load()
        glow = new(W, H)
        gpx = glow.load()
        # glow rays behind the open lid
        if mode == 2:
            for y in range(0, base_top + 2):
                for x in range(0, W):
                    spread = (base_top + 2 - y) * 0.3
                    if x0 + 3 - spread <= x <= x1 - 3 + spread:
                        t = y / (base_top + 2)
                        ray = (x * 2 + y) % 9 < 3
                        a = int(200 * t ** 1.5) if ray else int(90 * t ** 2)
                        gpx[x, y] = (255, 236, 150, a)
        # lid (open part: inner face rising above the hinge)
        inner_h = int(round(lid_h * o))
        front_h = int(round(lid_h * (1 - o)))
        if inner_h:
            for y in range(hinge - inner_h - 1, hinge):
                for x in range(x0 + 1, x1):
                    s = 3 if y < hinge - inner_h + 1 else 2
                    if x in (x0 + 1, x1 - 1):
                        s = 3
                    put(px, W, H, x, y, DARK_WOOD[s])
            for y in range(hinge - inner_h - 1, hinge):
                put(px, W, H, x0 + 4, y, DARK_STEEL[2])
                put(px, W, H, x1 - 4, y, DARK_STEEL[2])
        # interior (visible once open)
        if o > 0:
            for y in range(hinge, hinge + 3):
                for x in range(x0 + 1, x1):
                    put(px, W, H, x, y, (30, 20, 16, 255))
            if mode == 2:
                for x in range(x0 + 4, x1 - 3):
                    put(px, W, H, x, hinge + 1 - (x % 3 == 0), GOLD[(x % 4 == 0) and 0 or 1])
                    put(px, W, H, x, hinge + 2, GOLD[2])
        # base box
        top = hinge + (3 if o > 0 else 0)
        for y in range(top, base_bot + 1):
            for x in range(x0, x1 + 1):
                s = 1 if y == top else 4 if y == base_bot else 2
                if y % 4 == 3 and y != base_bot:
                    s = 3  # plank seams
                if x == x0:
                    s = max(1, s - 1)
                if x == x1:
                    s = min(4, s + 1)
                put(px, W, H, x, y, WOOD[s])
        # base metal bands + corners
        for bx in (x0 + 4, x1 - 4):
            for y in range(top, base_bot + 1):
                put(px, W, H, bx, y, GOLD[2])
                put(px, W, H, bx + 1, y, GOLD[3])
        for y in range(top, base_bot + 1):
            put(px, W, H, x0, y, GOLD[2])
            put(px, W, H, x1, y, GOLD[3])
        # closed / partially closed lid front (rounded top)
        if front_h:
            ly1 = hinge - 1 if o == 0 else hinge - 1 - 0
            ly0 = hinge - front_h
            for y in range(ly0, hinge):
                for x in range(x0, x1 + 1):
                    if y == ly0 and (x - x0 < 2 or x1 - x < 2):
                        continue
                    s = 0 if y == ly0 else 1 if y == ly0 + 1 else 2
                    if y == hinge - 1:
                        s = 3
                    if x == x1:
                        s = min(4, s + 1)
                    put(px, W, H, x, y, WOOD[s])
            for bx in (x0 + 4, x1 - 4):
                for y in range(ly0, hinge):
                    put(px, W, H, bx, y, GOLD[1])
                    put(px, W, H, bx + 1, y, GOLD[3])
        # lock plate
        if o == 0:
            lx = (x0 + x1) // 2 - 1
            for y in range(hinge - 3, hinge + 4):
                for x in range(lx, lx + 4):
                    put(px, W, H, x, y, GOLD[1] if y < hinge else GOLD[2])
            put(px, W, H, lx + 1, hinge + 1, (40, 24, 12, 255))
            put(px, W, H, lx + 2, hinge + 1, (40, 24, 12, 255))
            put(px, W, H, lx + 1, hinge + 2, (40, 24, 12, 255))
            if mode == 1:  # glint
                put(px, W, H, lx, hinge - 3, (255, 255, 255, 255))
                put(px, W, H, lx - 1, hinge - 4, (255, 255, 220, 255))
                put(px, W, H, lx + 1, hinge - 4, (255, 255, 220, 255))
                put(px, W, H, lx - 1, hinge - 2, (255, 255, 220, 255))
                put(px, W, H, lx, hinge - 5, (255, 250, 200, 255))
        out = outline(img)
        out.alpha_composite(glow)
        frames.append(out)
    return frames


def generate(out_dir):
    items = {
        'Explosion_fire': explosion_fire(),
        'Explosion_ice': explosion_ice(),
        'Shockwave_dust': shockwave_dust(),
        'BurningGround': burning_ground(),
        'MagicShield': magic_shield(),
        'StunStars': stun_stars(),
        'Chest': chest_frames(),
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
