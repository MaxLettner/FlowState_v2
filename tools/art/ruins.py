"""Ambient ruin structures standing on the ground (purely visual, drawn behind the player).

  Ruins  4 frames 96x72, bottom-aligned, intended display x2 (192x144):
    0 destroyed house   two broken walls, empty window, collapsed charred roof beams
    1 broken colonnade  pillars snapped at different heights, a fallen drum
    2 tower stump       round watchtower torn open, a glowing ember inside
    3 broken archway    one side of an arch still standing, rubble heap
"""
import math
import random
from pixel import *
from materials import *
from terrain import DIM_BONE, GLOW, HELLROCK, OBSIDIAN

FW, FH = 96, 72
BASE = FH - 1

# ash-grey masonry with a faint warm cast so it reads against the red background
ASHSTONE = ramp('#8a7a78', '#6b5c5c', '#4e4146', '#352a32', '#1f1820')
CHAR = ramp('#6a4a38', '#4a3226', '#33211a', '#22150f', '#140c09')


def _masonry(px, mask, rng, bh=5, bw=9, pal=ASHSTONE, light_x=None):
    """Fills every masked pixel with staggered bricks; mask is a set of (x, y)."""
    tones = {}
    for (x, y) in mask:
        row = y // bh
        off = (bw // 2) * (row % 2)
        col = (x + off) // bw
        lx, ly = (x + off) % bw, y % bh
        if (row, col) not in tones:
            tones[(row, col)] = rng.choice((0, 0, 1, -1))
        if ly == bh - 1 or lx == bw - 1:
            px[x, y] = pal[4]
            continue
        s = 2 + tones[(row, col)]
        if ly == 0:
            s -= 1
        # simple side light: the left edge of each shape is lit
        if (x - 1, y) not in mask:
            s -= 1
        if (x + 1, y) not in mask:
            s += 1
        if rng.random() < 0.05:
            s += rng.choice((-1, 1))
        px[x, y] = pal[max(0, min(4, s))]


def _broken_top(x0, x1, top, rng, jag=6):
    """Per-column top y for a wall with a jagged broken upper edge."""
    tops = {}
    y = top
    for x in range(x0, x1):
        y += rng.choice((-2, -1, 0, 0, 1, 2))
        y = max(top - jag, min(top + jag, y))
        tops[x] = y
    return tops


def _wall(mask, x0, x1, tops, bottom=BASE):
    for x in range(x0, x1):
        for y in range(tops[x], bottom + 1):
            mask.add((x, y))


def _rubble(px, rng, x0, x1, height, pal=ASHSTONE):
    """A heap of loose blocks along the ground."""
    for _ in range((x1 - x0) * 2):
        cx = rng.uniform(x0, x1)
        t = (cx - x0) / max(1, x1 - x0)
        hmax = height * math.sin(t * math.pi)
        cy = BASE - rng.uniform(0, hmax)
        rx, ry = rng.uniform(1.5, 3.2), rng.uniform(1.2, 2.2)
        ellipse(px, FW, FH, cx, cy, rx, ry, pal[rng.choice((1, 2, 2, 3))])
        put(px, FW, FH, int(cx - rx * 0.5), int(cy - ry * 0.6), pal[0])


def _beam(px, x0, y0, x1, y1, pal=CHAR, ember=None):
    line(px, FW, FH, x0, y0, x1, y1, pal[1])
    line(px, FW, FH, x0, y0 + 1, x1, y1 + 1, pal[2])
    line(px, FW, FH, x0, y0 + 2, x1, y1 + 2, pal[3])
    if ember:
        rng = ember
        n = int(max(abs(x1 - x0), abs(y1 - y0)))
        for i in range(0, n, 5):
            if rng.random() < 0.5:
                t = i / max(1, n)
                put(px, FW, FH, int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t) + 1, GLOW[rng.choice((1, 2))])


def destroyed_house(seed=60):
    rng = random.Random(seed)
    f = new(FW, FH); px = f.load()
    mask = set()
    # back wall (left, tall, with a gable stump) and a short broken right wall
    tops = _broken_top(10, 44, 20, rng, jag=3)
    for x in range(10, 44):  # gable: rises toward x=27
        tops[x] = min(tops[x], 8 + abs(x - 27) // 1 + rng.choice((0, 1)))
    for x in range(36, 44):  # broken off upper right of the gable
        tops[x] = max(tops[x], 24 + (x - 36) * 2)
    _wall(mask, 10, 44, tops)
    tops2 = _broken_top(56, 84, 38, rng, jag=5)
    for x in range(56, 84):
        tops2[x] += max(0, (x - 74)) * 2
    _wall(mask, 56, 84, tops2)
    # window and doorway holes
    for x in range(18, 26):
        for y in range(30, 40):
            mask.discard((x, y))
    for x in range(62, 71):
        for y in range(52, BASE + 1):
            mask.discard((x, y))
    _masonry(px, mask, rng)
    # dark interior seen through the holes
    for x in range(18, 26):
        for y in range(30, 40):
            px[x, y] = OBSIDIAN[4] if y > 31 else ASHSTONE[4]
    put(px, FW, FH, 23, 37, GLOW[3])
    for x in range(62, 71):
        for y in range(52, BASE + 1):
            px[x, y] = OBSIDIAN[4]
    # window sill + lintel in charred wood
    line(px, FW, FH, 17, 29, 26, 29, CHAR[1]); line(px, FW, FH, 17, 40, 26, 40, CHAR[2])
    line(px, FW, FH, 61, 51, 71, 51, CHAR[1])
    # collapsed roof beams: one still resting on the gable, two fallen across the gap
    _beam(px, 27, 9, 52, 30, ember=rng)
    _beam(px, 40, 40, 86, 60, ember=rng)
    _beam(px, 44, 58, 60, 47)
    # a few roof tiles scattered
    for _ in range(14):
        x, y = rng.randint(44, 90), rng.randint(56, BASE)
        put(px, FW, FH, x, y, HELLROCK[1]); put(px, FW, FH, x + 1, y, HELLROCK[2])
    _rubble(px, rng, 40, 60, 9)
    _rubble(px, rng, 82, 95, 5)
    # skull in the doorway
    from terrain import skull
    skull(px, FW, FH, 66, BASE - 4)
    return outline(f)


def colonnade(seed=61):
    rng = random.Random(seed)
    f = new(FW, FH); px = f.load()
    # stepped plinth
    for x in range(4, 92):
        for y in range(BASE - 4, BASE + 1):
            px[x, y] = ASHSTONE[1] if y == BASE - 4 else ASHSTONE[2] if y < BASE - 1 else ASHSTONE[3]
    for cx, ht in ((14, 58), (38, 30), (62, 48), (84, 16)):
        top = BASE - 4 - ht
        for y in range(top, BASE - 4):
            for x in range(cx - 5, cx + 6):
                # fluting: alternating light/dark vertical grooves, lit from the left
                k = x - (cx - 5)
                s = 1 if k < 3 else 2 if k < 8 else 3
                if k % 3 == 2:
                    s += 1
                px[x, y] = ASHSTONE[min(4, s)]
        # capital on the tallest, snapped jagged tops on the others
        if ht > 50:
            for x in range(cx - 8, cx + 9):
                for y in range(top - 4, top):
                    px[x, y] = ASHSTONE[0] if y == top - 4 else ASHSTONE[1 if y < top - 1 else 3]
        else:
            for x in range(cx - 5, cx + 6):
                cut = rng.randint(0, 4)
                for y in range(top, top + cut):
                    px[x, y] = (0, 0, 0, 0)
        # base ring
        for x in range(cx - 7, cx + 8):
            px[x, BASE - 5] = ASHSTONE[0]
            px[x, BASE - 6] = ASHSTONE[2]
    # a fallen drum lying between the pillars
    ellipse(px, FW, FH, 50, BASE - 9, 7, 5, ASHSTONE[2])
    ellipse(px, FW, FH, 49, BASE - 10, 5, 3.5, ASHSTONE[1])
    ellipse(px, FW, FH, 49, BASE - 10, 2, 1.5, ASHSTONE[3])
    # cracks
    for cx, y0 in ((14, 20), (62, 30)):
        x, y = cx, y0
        for _ in range(9):
            put(px, FW, FH, x, y, ASHSTONE[4])
            x += rng.choice((-1, 0, 1)); y += 1
    # chain hanging from the capital
    for i in range(14):
        put(px, FW, FH, 21 + i % 2, BASE - 66 + 4 + i, BLACK_IRON[2 if i % 2 else 3])
    _rubble(px, rng, 70, 94, 6)
    return outline(f)


def tower_stump(seed=62):
    rng = random.Random(seed)
    f = new(FW, FH); px = f.load()
    mask = set()
    cx, r = 46, 24
    tops = _broken_top(cx - r, cx + r, 14, rng, jag=4)
    # torn open diagonally: right side much lower
    for x in range(cx - r, cx + r):
        tops[x] += max(0, x - cx + 4) * 1
    _wall(mask, cx - r, cx + r, tops)
    # gaping breach in the middle showing the hollow inside
    breach = set()
    for x in range(cx - 8, cx + 14):
        for y in range(BASE - 30 + abs(x - cx - 3) // 2, BASE - 2):
            breach.add((x, y))
    mask -= breach
    _masonry(px, mask, rng, bh=4, bw=8)
    # curvature shading: darken toward both sides of the round tower
    for (x, y) in mask:
        d = abs(x - cx) / r
        if d > 0.75:
            px[x, y] = mix(px[x, y], ASHSTONE[4], 0.45)
    for (x, y) in breach:
        px[x, y] = OBSIDIAN[4] if (x + y) % 7 else OBSIDIAN[3]
    # ember glow inside
    ellipse(px, FW, FH, cx + 3, BASE - 6, 7, 3.5, GLOW[3])
    ellipse(px, FW, FH, cx + 3, BASE - 6, 4, 2, GLOW[2])
    put(px, FW, FH, cx + 2, BASE - 7, GLOW[0]); put(px, FW, FH, cx + 4, BASE - 6, GLOW[1])
    # arrow slit high up
    for y in range(24, 33):
        put(px, FW, FH, cx - 12, y, OBSIDIAN[4])
    # crenel stumps on the high side
    for x0 in (cx - r, cx - r + 9):
        for x in range(x0, x0 + 5):
            for y in range(tops[x] - 4, tops[x]):
                px[x, y] = ASHSTONE[1 if x == x0 else 2]
    _rubble(px, rng, cx + 10, FW - 2, 11)
    _beam(px, cx + 16, BASE - 18, FW - 4, BASE - 4)
    return outline(f)


def broken_arch(seed=63):
    rng = random.Random(seed)
    f = new(FW, FH); px = f.load()
    mask = set()
    # left pier
    for x in range(14, 26):
        for y in range(12, BASE + 1):
            mask.add((x, y))
    # arch ring springing from the left pier, broken off near the keystone
    acx, acy, R0, R1 = 48, 24, 22, 34
    for a in range(0, 100):
        t = math.radians(180 - a)
        for rr in range(R0, R1):
            x, y = int(round(acx + rr * math.cos(t))), int(round(acy - rr * math.sin(t) * 0.7))
            mask.add((x, y))
    # right pier, snapped low
    tops = _broken_top(70, 82, 44, rng, jag=4)
    _wall(mask, 70, 82, tops)
    _masonry(px, mask, rng, bh=6, bw=10)
    # voussoir joints radiating on the arch
    for a in range(0, 100, 11):
        t = math.radians(180 - a)
        line(px, FW, FH, acx + R0 * math.cos(t), acy - R0 * math.sin(t) * 0.7,
             acx + (R1 - 1) * math.cos(t), acy - (R1 - 1) * math.sin(t) * 0.7, ASHSTONE[4])
    # chunk of the arch that fell
    ellipse(px, FW, FH, 58, BASE - 5, 8, 5, ASHSTONE[2])
    ellipse(px, FW, FH, 56, BASE - 7, 5, 3, ASHSTONE[1])
    line(px, FW, FH, 52, BASE - 6, 64, BASE - 3, ASHSTONE[4])
    # bones in the rubble and a dead vine on the pier
    from terrain import bone
    bone(px, FW, FH, 30, BASE - 2, 40, BASE - 4)
    for i in range(30):
        x = 16 + int(3 * math.sin(i * 0.5))
        put(px, FW, FH, x, 16 + i, hexc('#3a1c1c') if i % 3 else hexc('#5a1e1e'))
    _rubble(px, rng, 62, 92, 8)
    return outline(f)


def ruins():
    return [destroyed_house(), colonnade(), tower_stump(), broken_arch()]


def generate(out_dir):
    items = {'Ruins': ruins()}
    for name, frames in items.items():
        strip(frames).save(f'{out_dir}/{name}.png')
    return items
