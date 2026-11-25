"""Gloves (fists pointing right) and projectiles (pointing right). Intended display scale x2.

Animated items are horizontal frame strips. The centre of each frame is the projectile's centre,
except trailing ones (FireBall, MagicMissile) whose head centre is listed in CENTERS.
"""
import math
import random
from pixel import new, outline, put, ramp, strip
from materials import (
    BLACK_IRON, BONE, CLOTH_WHITE, CRIMSON, DARK_STEEL, DARK_WOOD, GOLD, ICE, LEATHER, STEEL, WOOD)
from ranged import shade, stroke, LIGHT, POISON, STRING

FIRE = ramp('#ffffff', '#fff3a0', '#ffc23d', '#ff7a1f', '#d1361a', '#7a1a12')
ARCANE = ramp('#ffffff', '#f2d6ff', '#c98bff', '#9550f0', '#5e2ab8', '#33156e')
MISSILE = ramp('#ffffff', '#d4f6ff', '#7fd8ff', '#3d9bf0', '#2155b8', '#14306e')
SPARK = ramp('#ffffff', '#fffbd0', '#ffe066', '#ffab2e', '#e0661a', '#8a3410')
SKIN = ramp('#ffe0c4', '#f4bf98', '#d99772', '#a86a4c', '#6e4130')

CENTERS = {}


def lit(x, y, cx, cy, r):
    """-1..1 lighting term for a round shape lit from the top-left."""
    dx, dy = (x + 0.5 - cx) / max(r, 0.5), (y + 0.5 - cy) / max(r, 0.5)
    return -(dx * LIGHT[0] + dy * LIGHT[1]) * -1


def fill(img, inside, color_at):
    w, h = img.size
    px = img.load()
    for y in range(h):
        for x in range(w):
            if inside(x + 0.5, y + 0.5):
                c = color_at(x, y)
                if c is not None:
                    px[x, y] = c


# ---------------------------------------------------------------- gloves
def fist(body, cuff, decor=None, knuckle=None, W=13, H=12):
    img = new(W, H)
    cx, cy, rx, ry = 7.6, 6.2, 4.0, 3.9

    def inside_body(x, y):
        return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1 or (4 <= x <= cx and 2.6 <= y <= 9.9)

    def body_col(x, y):
        q = ((x + 0.5 - cx) * LIGHT[0] + (y + 0.5 - cy) * LIGHT[1]) / 4.2
        s = shade(q)
        # finger grooves on the front of the fist
        if x >= 9 and (y in (5, 7)) and x < 11:
            s = min(4, s + 2)
        # thumb wrapping over the front-top
        if y == 4 and 6 <= x <= 9:
            s = min(4, s + 1)
        if y == 3 and 6 <= x <= 9:
            s = max(0, s - 1)
        if decor:
            r = decor(x, y, s)
            if r is not None:
                return r
        return body[s]
    fill(img, inside_body, body_col)

    def inside_cuff(x, y):
        return 1 <= x <= 4.5 and 2 <= y <= 10.6

    def cuff_col(x, y):
        s = 1 if y <= 2 else 3 if y >= 9 else 2
        if x == 1:
            s = min(4, s + 1)
        return cuff[s]
    fill(img, inside_cuff, cuff_col)
    px = img.load()
    if knuckle:
        knuckle(px, W, H)
    return outline(img)


def hand_wraps():
    def stripes(x, y, s):
        if (x + y) % 3 == 0 and x >= 4:
            return CLOTH_WHITE[min(4, s + 1)]
        if x >= 11 and y in (4, 6, 8):
            return SKIN[s]  # knuckles peeking through
        return None
    return fist(CLOTH_WHITE, CLOTH_WHITE, decor=stripes)


def leather_glove():
    def stitch(x, y, s):
        if y == 3 and x % 2 == 0 and 5 <= x <= 9:
            return LEATHER[0]
        return None

    def buckle(px, w, h):
        put(px, w, h, 2, 6, GOLD[1])
        put(px, w, h, 3, 6, GOLD[2])
        put(px, w, h, 2, 7, GOLD[3])
    return fist(LEATHER, DARK_WOOD, decor=stitch, knuckle=buckle)


def metal_glove():
    def plates(x, y, s):
        if x in (6, 9) and 2 <= y <= 9:
            return STEEL[min(4, s + 2)]
        if (x, y) in ((7, 3), (10, 4)):
            return STEEL[0]
        return None

    def rivets(px, w, h):
        for y in (3, 6, 9):
            put(px, w, h, 3, y, GOLD[1])
    return fist(STEEL, DARK_STEEL, decor=plates, knuckle=rivets)


def spike_glove():
    dark = ramp('#6d6875', '#4f4a57', '#38343f', '#25222b', '#16141a')

    def spikes(px, w, h):
        for y in (3, 6, 9):
            put(px, w, h, 11, y, STEEL[2])
            put(px, w, h, 12, y, STEEL[0])
        put(px, w, h, 7, 1, STEEL[1])
        put(px, w, h, 7, 0, STEEL[0])
        put(px, w, h, 9, 1, STEEL[2])
    img = fist(dark, CRIMSON, knuckle=spikes, W=15, H=13)
    return img


def metal_guard():
    W, H = 10, 28
    img = new(W, H)

    def inside(x, y):
        # slightly curved plate, pointed bottom
        top = 1.5 + 0.08 * (x - 5) ** 2
        bot = H - 2 - 1.2 * abs(x - 5)
        return 1 <= x <= W - 1 and top <= y <= bot

    def col(x, y):
        if x == 5 or x == 4:  # central rib
            return STEEL[0] if x == 4 else STEEL[2]
        if x <= 2 or x >= W - 3:
            return GOLD[1] if x <= 2 else GOLD[3]
        return STEEL[1 if x < 5 else 3]
    fill(img, inside, col)
    px = img.load()
    for y in (5, 13, 21):
        put(px, W, H, 3, y, STEEL[0])
        put(px, W, H, 6, y, STEEL[0])
    return outline(img)


# ---------------------------------------------------------------- arrows / bolts
def arrow(length, shaft, head, fletch, head_len=4, head_h=2, thick=1, H=7):
    W = length + 2
    img = new(W, H)
    px = img.load()
    cy = H // 2
    xe = W - 2
    for x in range(1, xe - head_len + 1):
        for t in range(thick):
            put(px, W, H, x, cy - t, shaft[1 if t == thick - 1 and thick > 1 else 1 + (x % 4 == 0)])
            if thick > 1 and t == 0:
                put(px, W, H, x, cy, shaft[3])
    # head: triangle
    for i in range(head_len):
        xx = xe - head_len + 1 + i
        hh = round(head_h * (1 - i / head_len))
        for dy in range(-hh, hh + 1):
            s = 1 if dy < 0 else 2 if dy == 0 else 3
            put(px, W, H, xx, cy + dy, head[s])
    put(px, W, H, xe, cy, head[0])
    # fletching
    fl = min(5, length // 3)
    for i in range(fl):
        h = 1 + (i < fl - 1)
        for k in range(1, h + 1):
            put(px, W, H, 1 + i, cy - k - (thick - 1), fletch[1])
            put(px, W, H, 1 + i, cy + k, fletch[3])
    put(px, W, H, 0, cy, STRING[2])
    return outline(img)


def bone_shards():
    out = []
    rng = random.Random(7)
    for k in range(3):
        img = new(8, 6)
        px = img.load()
        pts = [(1 + rng.random(), 2 + rng.random() * 2), (6 + rng.random(), 2 + rng.random() * 2)]
        for i in range(8):
            t = i / 7
            x = pts[0][0] + (pts[1][0] - pts[0][0]) * t
            y = pts[0][1] + (pts[1][1] - pts[0][1]) * t
            put(px, 8, 6, int(x), int(y), BONE[1 if i < 4 else 2])
            if 1 <= i <= 6:
                put(px, 8, 6, int(x), int(y) + 1, BONE[3 if i % 3 else 2])
            if i in (2, 3):
                put(px, 8, 6, int(x), int(y) - 1, BONE[0])
        put(px, 8, 6, int(pts[1][0]), int(pts[1][1]), BONE[0])
        out.append(outline(img))
    return out


# ---------------------------------------------------------------- spells
def glow_q(d, r):
    """distance -> ramp index for a glowing orb (centre brightest)."""
    return max(0, min(5, int(d / r * 5)))


def arcane_bolt(n=4):
    frames = []
    S = 14
    c = S / 2
    for f in range(n):
        img = new(S, S)
        px = img.load()
        r = 4.2 + 0.5 * math.sin(f / n * 2 * math.pi)
        for y in range(S):
            for x in range(S):
                d = math.hypot(x + 0.5 - c, y + 0.5 - c)
                if d <= r:
                    i = glow_q(d, r)
                    if (x + y + f) % 2 == 0 and 2 <= i <= 3:
                        i += 1
                    px[x, y] = ARCANE[i]
        # four orbiting motes
        for k in range(3):
            a = (f / n + k / 3) * 2 * math.pi
            put(px, S, S, int(c + math.cos(a) * 5.6), int(c + math.sin(a) * 5.6), ARCANE[1])
        frames.append(outline(img, ARCANE[5]))
    return frames


def spark(n=4):
    frames = []
    S = 12
    c = S / 2
    for f in range(n):
        img = new(S, S)
        px = img.load()
        rot = f * math.pi / 4
        for y in range(S):
            for x in range(S):
                dx, dy = x + 0.5 - c, y + 0.5 - c
                d = math.hypot(dx, dy)
                a = math.atan2(dy, dx) - rot
                star = 2.2 + 2.8 * abs(math.cos(2 * a)) ** 6
                if d <= star:
                    px[x, y] = SPARK[min(5, int(d / star * 4.5))]
        frames.append(outline(img, SPARK[5]))
    return frames


def comet(W, H, head_x, r, pal, n, tail, wobble, seed):
    frames = []
    cy = H / 2
    for f in range(n):
        rng = random.Random(seed + f)
        img = new(W, H)
        px = img.load()
        ph = f / n * 2 * math.pi
        for y in range(H):
            for x in range(W):
                cx = x + 0.5
                cyy = y + 0.5
                d = math.hypot(cx - head_x, cyy - cy)
                if d <= r:
                    px[x, y] = pal[max(0, min(5, glow_q(d, r) - (1 if d < r * 0.4 else 0)))]
                    continue
                if cx < head_x:
                    t = (head_x - cx) / tail  # 0 at head .. 1 at tail end
                    if t > 1:
                        continue
                    half = r * (1 - t) ** 0.8 + wobble * math.sin(ph + t * 9 + (y - cy) * 0.6) * (1 - t)
                    off = abs(cyy - cy + 0.8 * math.sin(ph * 1 + t * 6) * t)
                    if off <= half:
                        k = 1 + int(t * 4 + off / max(half, 0.5) * 1.5)
                        if rng.random() < 0.12:
                            k += 1
                        if k <= 5:
                            px[x, y] = pal[k]
        frames.append(outline(img, pal[5]))
    return frames


def fireball():
    CENTERS['FireBall'] = (19, 8)
    return comet(26, 16, 19, 5.5, FIRE, 4, 18, 1.4, 11)


def magic_missile():
    CENTERS['MagicMissile'] = (13, 5)
    return comet(18, 10, 13, 3.5, MISSILE, 3, 12, 0.8, 21)


def icicle():
    W, H = 24, 9
    img = new(W, H)
    px = img.load()
    cy = H / 2
    for y in range(H):
        for x in range(W):
            t = x / (W - 1)
            half = 3.6 * (1 - (t - 0.25) / 0.75) if t > 0.25 else 3.6 * (0.35 + t / 0.25 * 0.65) - (1 if (y * 3 + x) % 4 == 0 and t < 0.1 else 0)
            dy = y + 0.5 - cy
            if abs(dy) <= half:
                # facets: upper half bright, a dark centre line, lower half mid
                if abs(dy) < 0.6:
                    s = 2
                elif dy < 0:
                    s = 0 if dy < -half * 0.55 else 1
                else:
                    s = 3 if dy > half * 0.5 else 2
                if x < 3 and (x + y) % 2 == 0:
                    s = min(4, s + 1)
                px[x, y] = ICE[s]
    return outline(img, ICE[4])


def poison_dart():
    W, H = 15, 5
    img = new(W, H)
    px = img.load()
    cy = 2
    for x in range(3, W - 4):
        put(px, W, H, x, cy, DARK_STEEL[1])
    for x in range(W - 4, W - 1):
        put(px, W, H, x, cy, POISON[1 if x < W - 2 else 0])
    put(px, W, H, W - 4, cy - 1, POISON[2])
    put(px, W, H, W - 4, cy + 1, POISON[3])
    for x in range(0, 4):
        put(px, W, H, x, cy - 1, POISON[2])
        put(px, W, H, x, cy + 1, POISON[3])
        put(px, W, H, x, cy, POISON[1])
    return outline(img)


def generate(out_dir):
    items = {
        # gloves
        'HandWraps': [hand_wraps()],
        'LeatherGlove': [leather_glove()],
        'MetalGlove': [metal_glove()],
        'SpikeGlove': [spike_glove()],
        'MetalGuard': [metal_guard()],
        # arrows / bolts
        'Arrow': [arrow(16, WOOD, STEEL, CRIMSON)],
        'BoneArrow': [arrow(16, BONE, BONE, ramp('#5a5a66', '#3a3a44', '#2a2a32', '#1c1c22', '#101014'))],
        'WarArrow': [arrow(20, DARK_WOOD, DARK_STEEL, CRIMSON, head_len=5)],
        'Bolt': [arrow(11, WOOD, STEEL, CRIMSON, head_len=3)],
        'HeavyBolt': [arrow(16, BLACK_IRON, STEEL, DARK_STEEL, head_len=5, head_h=3, thick=2, H=9)],
        'PoisonBolt': [arrow(11, DARK_WOOD, POISON, POISON, head_len=3)],
        'BoneShard': bone_shards(),
        # spells
        'ArcaneBolt': arcane_bolt(),
        'Spark': spark(),
        'MagicMissile': magic_missile(),
        'FireBall': fireball(),
        'Icicle': [icicle()],
        'PoisonDart': [poison_dart()],
    }
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
