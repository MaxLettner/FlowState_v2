"""Foreground parallax layers: near-black cave silhouettes drawn IN FRONT of the game world and scrolled
faster than the camera, so the scene reads as the inside of a cave (Hollow Knight style).

Both layers are transparent, seamless horizontally and 4800 px wide on screen (so the repeat is rare):

  fg_mid   1200x270, shown x4 (4800x1080), scroll factor ~1.3: ceiling stalactites, long hanging chains,
           chains sagging between stalactites, small thorn clusters at the bottom edge
  fg_near   800x180, shown x6 (4800x1080), scroll factor ~1.7: a jagged cave ceiling with big stalactite
           clusters, a few heavy chains, big thorn spikes rising from the bottom edge

Everything is lit only from below by the abyss glow (red rim on the lower edges), the closest layer is the
darkest. Keep the middle of the screen mostly empty: the game is played there.

Preview:  python tools/art/foreground.py out.png   (mock screen: backgrounds + ground + both layers)
"""
import math
import random
import sys
from pixel import *
from effects import WrapNoise

# per layer: body, rim (lit from below), hot rim (closest to the abyss), dark side
MID_PAL = dict(body=hexc('#0d0508'), rim=hexc('#4a1414'), hot=hexc('#6e2216'), dark=hexc('#070306'),
               iron=hexc('#170a0d'), iron_lit=hexc('#5a1e18'))
NEAR_PAL = dict(body=hexc('#050206'), rim=hexc('#3a1012'), hot=hexc('#5a1a14'), dark=hexc('#020103'),
                iron=hexc('#0a0508'), iron_lit=hexc('#401412'))


class Canvas:
    """Silhouette mask + colours on a horizontally wrapping canvas."""
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.img = new(w, h)
        self.px = self.img.load()

    def set(self, x, y, c):
        if 0 <= y < self.h:
            self.px[int(x) % self.w, int(y)] = c

    def get(self, x, y):
        if not 0 <= y < self.h:
            return (0, 0, 0, 0)
        return self.px[int(x) % self.w, int(y)]


def spike(cv, x0, base_y, width, length, bend, down, col, rng=None, knobs=0, exp=1.2):
    """Tapered curved spike (stalactite when down, thorn/stalagmite when up). Rooted at base_y.
    knobs adds little side bumps (rocky stalactites)."""
    sgn = 1 if down else -1
    bumps = []
    if rng and knobs:
        for _ in range(knobs):
            bumps.append((rng.uniform(0.15, 0.7), rng.choice((-1, 1)), rng.uniform(1.0, 2.5)))
    for i in range(int(length) + 1):
        t = i / length
        y = base_y + sgn * i
        cx = x0 + bend * t * t
        half = width / 2 * (1 - t) ** exp
        for bt, side, size in bumps:
            k = max(0.0, 1 - abs(t - bt) * length / (size * 2.2))
            half += size * k * 0.6
        if half < 0.4:
            half = 0.4 if t < 0.999 else 0
        for x in range(int(math.floor(cx - half)), int(math.ceil(cx + half))):
            cv.set(x, y, col)


def rock_mass(cv, x, base_y, width, depth, down, rng, col, tips=1, tip_len=0):
    """A craggy lump of rock hanging from the ceiling (down) or rising from the floor: overlapping blunt
    spikes, plus `tips` long stalactite/stalagmite points of about tip_len."""
    for _ in range(int(width / 6) + 3):
        w = width * rng.uniform(0.25, 0.6)
        ox = rng.uniform(-0.5, 0.5) * (width - w)
        d = depth * rng.uniform(0.5, 1.0) * (1 - abs(ox) / width)
        spike(cv, x + ox, base_y, w, d, rng.uniform(-3, 3), down, col, exp=0.6)
    for i in range(tips):
        ox = rng.uniform(-0.25, 0.25) * width
        ln = tip_len * (1 if i == 0 else rng.uniform(0.45, 0.75))
        spike(cv, x + ox, base_y, width * rng.uniform(0.28, 0.4), ln, rng.uniform(-6, 6), down, col, rng, knobs=3)


def thorn_cluster(cv, x, base_y, rng, scale, col, tall=1.0):
    """A clump of thick curved thorns growing out of a rock mound at the bottom edge."""
    n = rng.randint(2, 4)
    for i in range(n):
        rel = (i - (n - 1) / 2) / max(1, n - 1) if n > 1 else 0     # -0.5 .. 0.5
        main = i == n // 2
        ln = scale * tall * (rng.uniform(0.85, 1.0) if main else rng.uniform(0.4, 0.7))
        lean = (rel * 1.4 + rng.uniform(-0.25, 0.25)) * ln * 0.7
        width = scale * (0.3 if main else 0.22) * rng.uniform(0.85, 1.15)
        sx = x + rel * scale * 0.5
        spike(cv, sx, base_y, width, ln, lean, False, col, exp=1.0)
        # a few hooked barbs
        for _ in range(rng.randint(1, 2) if ln > scale * 0.5 else 0):
            t = rng.uniform(0.25, 0.55)
            bx = sx + lean * t * t
            by = base_y - ln * t
            side = 1 if rng.random() < 0.5 else -1
            spike(cv, bx, by, width * 0.4, width * rng.uniform(1.0, 1.6), side * width * 1.4, False, col)
    rock_mass(cv, x, base_y, scale * 1.1, scale * 0.3, False, rng, col, tips=0)


def ceiling(cv, nz, thick_min, thick_max, col, y0=0):
    """Jagged rock band along the top edge, wrapping horizontally."""
    for x in range(cv.w):
        v = nz(x, 0) * 0.65 + nz(x * 3.1, 0.5) * 0.35
        th = thick_min + (thick_max - thick_min) * v
        for y in range(y0, int(y0 + th)):
            cv.set(x, y, col)


# ------------------------------------------------------------------ chains
def _link(cv, cx, cy, ang, face, a, b, col, lit, dark):
    """One chain link centred at (cx, cy), long axis along ang. face=True: open oval seen face-on,
    else a link seen edge-on (thin bar)."""
    ca, sa = math.cos(ang), math.sin(ang)
    r = int(a) + 2
    for y in range(int(cy) - r, int(cy) + r + 1):
        for x in range(int(cx) - r, int(cx) + r + 1):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u = dx * ca + dy * sa
            v = -dx * sa + dy * ca
            if face:
                outer = (u / a) ** 2 + (v / b) ** 2 <= 1
                inner = (u / (a - 1.3)) ** 2 + (v / max(0.4, b - 1.3)) ** 2 <= 1
                if outer and not inner:
                    # bottom/left side of the ring catches the glow from below
                    c = lit if dy > 0.6 and v * (1 if ca >= 0 else -1) > -0.2 else col
                    cv.set(x, y, c)
            else:
                if abs(u) <= a - 0.3 and abs(v) <= 0.75:
                    cv.set(x, y, dark if dy < 0 else col)


def chain_path(cv, pts, link_len, a, b, pal):
    """Chain links along a polyline of (x, y) points, alternating face-on and edge-on links."""
    # resample the polyline at link spacing
    seg = []
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        seg.append((x0, y0, x1, y1, math.hypot(x1 - x0, y1 - y0)))
    total = sum(s[4] for s in seg)
    n = int(total / link_len)
    d, si, acc = 0.0, 0, 0.0
    for i in range(n + 1):
        d = i * link_len
        while si < len(seg) - 1 and d > acc + seg[si][4]:
            acc += seg[si][4]
            si += 1
        x0, y0, x1, y1, ln = seg[si]
        t = 0 if ln == 0 else (d - acc) / ln
        cx, cy = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        ang = math.atan2(y1 - y0, x1 - x0)
        _link(cv, cx, cy, ang, i % 2 == 0, a, b, pal['iron'], pal['iron_lit'], pal['dark'])


def hanging_chain(cv, x, y_top, length, pal, end, link_len, a, b, sway=0.0):
    pts = [(x + sway * math.sin(t / 10 * math.pi / 2) ** 2, y_top + length * t / 10) for t in range(11)]
    chain_path(cv, pts, link_len, a, b, pal)
    ex, ey = pts[-1][0], pts[-1][1] + a
    s = a / 4.5     # end pieces scale with the link size
    if end == 'hook':
        for i in range(int(22 * s)):
            ang = i / (22 * s - 1) * math.pi * 1.25
            r = 5 * s
            hx = ex + r - r * math.cos(ang)
            hy = ey + r * math.sin(ang)
            for k in range(int(max(1, 2.2 * s * (1 - i / (22 * s) * 0.6)))):
                cv.set(hx - k * 0.5, hy - k * 0.6, pal['iron'] if i < 10 * s else pal['iron_lit'])
        spike(cv, ex + 2 * r - 0.5, ey - 0.5, 2.2 * s, 3.5 * s, 1.2 * s, False, pal['iron_lit'])
    elif end == 'weight':
        # a spiked iron ball
        r = 7 * s
        for yy in range(int(ey - r), int(ey + r) + 2):
            for xx in range(int(ex - r), int(ex + r) + 2):
                dd = math.hypot(xx + 0.5 - ex, yy + 0.5 - ey - r * 0.6)
                if dd <= r:
                    lit = yy + 0.5 - (ey + r * 0.6) > r * 0.45 and xx < ex + r * 0.3
                    cv.set(xx, yy, pal['iron_lit'] if lit else pal['iron'])
        for k in range(8):
            ang = k / 8 * math.pi * 2 + 0.3
            bx, by = ex + math.cos(ang) * r * 0.9, ey + r * 0.6 + math.sin(ang) * r * 0.9
            for i in range(int(5 * s)):
                cv.set(bx + math.cos(ang) * i, by + math.sin(ang) * i, pal['iron'])
    elif end == 'broken':
        # a single bent half link
        _link(cv, ex + 1, ey + 1, math.pi / 2 + 0.6, False, a * 0.7, b, pal['iron'], pal['iron_lit'], pal['dark'])
    elif end == 'cage':
        # small hanging gibbet cage
        cw, ch = 16 * s, 26 * s
        top = ey + 2
        for yy in range(int(top), int(top + ch)):
            t = (yy - top) / ch
            half = cw / 2 * (0.55 + 0.45 * math.sin(min(1.0, t * 1.4) * math.pi / 2))
            for bar in range(5):
                bx = ex - half + bar * half / 2
                cv.set(bx, yy, pal['iron'] if yy < top + ch * 0.75 else pal['iron_lit'])
        for yy in (top, top + ch * 0.45, top + ch - 1):
            t = (yy - top) / ch
            half = cw / 2 * (0.55 + 0.45 * math.sin(min(1.0, t * 1.4) * math.pi / 2))
            for xx in range(int(ex - half), int(ex + half) + 1):
                for k in range(max(1, int(1.6 * s))):
                    cv.set(xx, yy + k, pal['iron_lit'] if yy > top else pal['iron'])
        spike(cv, ex, top + ch, cw * 0.5, 7 * s, 0, True, pal['iron'])


def sagging_chain(cv, x0, y0, x1, y1, sag, pal, link_len, a, b):
    """Chain hanging between two anchors (parabola approximating the catenary)."""
    pts = []
    for i in range(41):
        t = i / 40
        pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t + sag * 4 * t * (1 - t)))
    chain_path(cv, pts, link_len, a, b, pal)


# ------------------------------------------------------------------ shading
def shade(cv, pal, hot_from):
    """Rim light from below on the silhouette: the lower edge of every shape catches the abyss glow,
    hotter towards the bottom of the screen (rows >= hot_from). Upper edges get a darker outline."""
    w, h = cv.w, cv.h
    src = cv.img.copy().load()
    solid = lambda x, y: 0 <= y < h and src[x % w, y][3] > 0
    body = pal['body']
    for y in range(h):
        for x in range(w):
            p = src[x, y]
            if p != body:
                continue    # chains/iron keep their own shading
            below = not solid(x, y + 1)
            side = not solid(x - 1, y) or not solid(x + 1, y)
            above = not solid(x, y - 1)
            if below or (side and solid(x, y - 1) and not solid(x, y + 2)):
                cv.px[x, y] = pal['hot'] if y >= hot_from else pal['rim']
            elif not solid(x, y + 2) and (x + y) % 2 == 0:
                # 2nd rim row, checker-dithered so the light fades instead of stepping
                cv.px[x, y] = pal['rim']
            elif above or side:
                cv.px[x, y] = pal['dark']


# ------------------------------------------------------------------ layers
def fg_mid(seed=31):
    W, H = 1200, 270
    cv = Canvas(W, H)
    rng = random.Random(seed)
    nz = WrapNoise(seed, W, 1, 1 / 26)
    col = MID_PAL['body']

    # broken ceiling: only some stretches (gaps let the background ceiling show through)
    gate = WrapNoise(seed + 1, W, 1, 1 / 140)
    for x in range(W):
        if gate(x, 0) < 0.38:
            continue
        th = 4 + 22 * (gate(x, 0) - 0.38) / 0.62 + 7 * nz(x, 0)
        for y in range(int(th)):
            cv.set(x, y, col)

    # stalactite groups: a hanging rock with one long point and a few short ones around it
    anchors = []
    x = rng.uniform(0, 60)
    while x < W - 60:
        big = rng.random() < 0.5
        ln = rng.uniform(70, 115) if big else rng.uniform(28, 55)
        rock_mass(cv, x, 0, rng.uniform(34, 56) if big else rng.uniform(18, 30), rng.uniform(14, 26), True, rng, col,
                  tips=rng.randint(1, 3), tip_len=ln)
        anchors.append((x, ln))
        x += rng.uniform(110, 200)

    # bottom: thorn clusters
    for i in range(4):
        tx = (i + rng.uniform(0.2, 0.8)) * W / 4
        thorn_cluster(cv, tx, H + 2, rng, rng.uniform(32, 50), col)
    shade(cv, MID_PAL, hot_from=H - 40)

    # chains drawn after the rock so they hang in front of it
    pal = MID_PAL
    link = dict(link_len=5.2, a=3.8, b=2.4)
    for i in range(len(anchors) - 1):
        (xa, la), (xb, lb) = anchors[i], anchors[i + 1]
        if rng.random() < 0.55:
            # sagging between two stalactites, fixed a bit below their roots
            sagging_chain(cv, xa + 4, 14, xb - 4, 12, rng.uniform(25, 55), pal, **link)
    for i in range(6):
        cx = (i + rng.uniform(0.2, 0.8)) * W / 6
        hanging_chain(cv, cx, 0, rng.uniform(70, 150), pal, ('hook', 'cage', 'weight', 'broken', 'hook', 'cage')[i],
                      sway=rng.uniform(-3, 3), **link)
    return cv.img


def fg_near(seed=47):
    W, H = 800, 180
    cv = Canvas(W, H)
    rng = random.Random(seed)
    col = NEAR_PAL['body']

    # continuous jagged ceiling, much thicker in a few places
    nz = WrapNoise(seed, W, 1, 1 / 14)
    bulge = WrapNoise(seed + 3, W, 1, 1 / 120)
    for x in range(W):
        th = 4 + 8 * nz(x, 0) + 40 * max(0.0, bulge(x, 0) - 0.5) ** 1.3 * 2
        for y in range(int(th)):
            cv.set(x, y, col)

    # big hanging rocks, roughly one per screen width
    clusters = []
    for i in range(3):
        x = (i + rng.uniform(0.15, 0.6)) * W / 3
        rock_mass(cv, x, 0, rng.uniform(50, 80), rng.uniform(22, 34), True, rng, col,
                  tips=rng.randint(2, 3), tip_len=rng.uniform(55, 72))
        clusters.append(x)

    # thorns from the bottom edge, between the hanging rocks; one of them tall
    tall = rng.randrange(3)
    for i in range(3):
        tx = clusters[i] + W / 6 + rng.uniform(-20, 20)
        thorn_cluster(cv, tx, H + 3, rng, rng.uniform(40, 52), col, tall=1.6 if i == tall else 0.85)

    shade(cv, NEAR_PAL, hot_from=H - 30)

    # heavy chains, drawn last (in front of the rock)
    link = dict(link_len=7.0, a=5.0, b=3.2)
    hanging_chain(cv, (clusters[0] + 60) % W, 0, rng.uniform(55, 75), NEAR_PAL, 'weight', sway=rng.uniform(-2, 2), **link)
    hanging_chain(cv, (clusters[2] + 75) % W, 0, rng.uniform(35, 50), NEAR_PAL, 'hook', sway=rng.uniform(-2, 2), **link)
    xa = clusters[1] + 25
    sagging_chain(cv, xa, 20, xa + 120, 10, 34, NEAR_PAL, **link)
    return cv.img


def generate(out_dir):
    items = {'fg_mid': fg_mid(), 'fg_near': fg_near()}
    for name, img in items.items():
        img.save(f'{out_dir}/{name}.png')
    return items


# ------------------------------------------------------------------ preview
def preview(path, cam=0):
    """1920x1080 mock: backgrounds, a strip of ground at the game's base level, both foreground layers."""
    import os
    from PIL import Image
    here = os.path.dirname(os.path.abspath(__file__))
    tex = os.path.join(here, '..', '..', 'src', 'main', 'resources', 'assets', 'textures')
    out = Image.new('RGBA', (1920, 1080), (0, 0, 0, 255))

    def layer(img, scale, factor):
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        off = -int(cam * factor) % big.width
        for k in (-1, 0, 1):
            x = k * big.width - off
            if x < 1920 and x + big.width > 0:
                out.paste(big, (x, 0), big)

    layer(Image.open(os.path.join(tex, 'bg_far.png')).convert('RGBA'), 5, 0.03)
    layer(Image.open(os.path.join(tex, 'bg_mid.png')).convert('RGBA'), 5, 0.08)
    ground = Image.open(os.path.join(tex, 'Ground.png')).convert('RGBA')
    top = Image.open(os.path.join(tex, 'GroundTop.png')).convert('RGBA')
    g = ground.resize((ground.width * 2, ground.height * 2), Image.NEAREST)
    t = top.resize((top.width * 2, top.height * 2), Image.NEAREST)
    for (x0, x1, y) in ((0, 700, 885), (900, 1300, 840), (1450, 1920, 905)):
        out.paste(g.crop((x0 % g.width, 0, x0 % g.width + (x1 - x0), 1080 - y)), (x0, y))
        out.paste(t.crop((0, 0, x1 - x0, t.height)), (x0, y - 6), t.crop((0, 0, x1 - x0, t.height)))
    try:
        p = Image.open(os.path.join(tex, 'Player_idle.png')).convert('RGBA')
        f = p.crop((0, 0, 64, 64)).resize((128, 128), Image.NEAREST)
        out.paste(f, (500, 885 - 124), f)
    except FileNotFoundError:
        pass
    layer(fg_mid(), 4, 1.3)
    layer(fg_near(), 6, 1.7)
    out.save(path)


if __name__ == '__main__':
    preview(sys.argv[1] if len(sys.argv) > 1 else 'fg_preview.png', int(sys.argv[2]) if len(sys.argv) > 2 else 0)
