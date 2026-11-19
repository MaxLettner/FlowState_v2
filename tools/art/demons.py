"""Demons (wave 1): Imp, Hellhound, Pit Brute, Brimstone Hurler, Cinderbat, Hellfire Cultist,
plus their projectiles and effects.

Every frame is posed from shaded primitives (ellipses, tapered limbs, polygons) on a small Rig: each part
is lit from the top left like the slimes, overlapping parts get a dark inner line so limbs stay readable,
the lower edges catch the lava bounce light and the whole silhouette gets the shared outline.
All demons face right (+x) and are shown at x2. Frame sizes and anchors must match DemonType.java:

  name            frame    anchor       strips
  DemonImp        40x36    (20, 34)     idle 4, run 6, crouch 1, leap 2
  DemonHound      64x40    (32, 38)     idle 4, run 6, crouch 2, lunge 2, pant 4
  DemonBrute      160x168  (80, 165)    idle 8, walk 12, slam 12, air 2 (twice the pixel detail and frames)
  DemonHurler     56x52    (28, 50)     idle 4, walk 6, throw 5, air 1
  DemonBat        48x44    (24, 22)*    fly 4, dive 2           (* centre anchor, it flies)
  DemonCultist    48x60    (24, 58)     idle 4, walk 6, cast 5, air 1

Effects: MagmaBall (4f 14x14), HellfireOrb (4f 16x16, flies right), BruteShockwave (4f 80x48, rolls right,
bottom-anchored), MagmaPool (4f 32x16 tile, bottom-anchored).

    python tools/art/demons.py sheet out.png     # review sheet of every strip at x4
"""
import math
import random
import sys
from pixel import *
from materials import BONE, BLACK_IRON, GOLD

LAVA_RIM = hexc('#ff8a3d')
_l = (-0.5, -0.75, 0.45)
_ll = math.sqrt(sum(c * c for c in _l))
LIGHT = tuple(c / _ll for c in _l)
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))

EMBER = ramp('#fff6c0', '#ffd25a', '#ff8a2a', '#ff5a1a', '#c2300f')   # white-hot .. deep red
FEL = ramp('#f4ffd0', '#b8ff6a', '#5fe83a', '#22a82e', '#0f5c1c')     # hellfire green
HORN = ramp('#f4e6c8', '#d1bc92', '#a08a62', '#6b5a3e', '#3e3424')


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


# ---------- shapes: dicts (x, y) -> (nx, ny), the surface normal's screen part ----------
def ell(cx, cy, rx, ry, rot=0.0):
    out = {}
    c, s = math.cos(rot), math.sin(rot)
    r = max(rx, ry) + 1
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u = (dx * c + dy * s) / rx
            v = (-dx * s + dy * c) / ry
            if u * u + v * v <= 1:
                out[(x, y)] = ((u * c - v * s) * 0.95, (u * s + v * c) * 0.95)
    return out


def limb(x0, y0, x1, y1, r0, r1=None):
    """Tapered capsule from (x0, y0) radius r0 to (x1, y1) radius r1, shaded like a cylinder."""
    r1 = r0 if r1 is None else r1
    out = {}
    dx, dy = x1 - x0, y1 - y0
    ln2 = dx * dx + dy * dy
    m = max(r0, r1) + 1
    for y in range(int(min(y0, y1) - m) - 1, int(max(y0, y1) + m) + 2):
        for x in range(int(min(x0, x1) - m) - 1, int(max(x0, x1) + m) + 2):
            px_, py_ = x + 0.5, y + 0.5
            t = 0.0 if ln2 == 0 else clamp(((px_ - x0) * dx + (py_ - y0) * dy) / ln2)
            cx, cy = x0 + dx * t, y0 + dy * t
            r = r0 + (r1 - r0) * t
            d = math.hypot(px_ - cx, py_ - cy)
            if d <= r + 0.35:
                k = 0.95 / max(r + 0.35, 0.5)
                out[(x, y)] = ((px_ - cx) * k, (py_ - cy) * k)
    return out


def union(*shapes):
    out = {}
    for s in shapes:
        for p, n in s.items():
            if p not in out or n[0] ** 2 + n[1] ** 2 < out[p][0] ** 2 + out[p][1] ** 2:
                out[p] = n
    return out


def chain(pts, r0, r1):
    """Tapered limb along a polyline (horns, tails)."""
    lens = [0.0]
    for a, b in zip(pts, pts[1:]):
        lens.append(lens[-1] + math.hypot(b[0] - a[0], b[1] - a[1]))
    total = max(lens[-1], 1e-6)
    parts = []
    for i, (a, b) in enumerate(zip(pts, pts[1:])):
        ra = r0 + (r1 - r0) * lens[i] / total
        rb = r0 + (r1 - r0) * lens[i + 1] / total
        parts.append(limb(a[0], a[1], b[0], b[1], ra, rb))
    return union(*parts)


def poly(points, bulge=0.6):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    cx, cy = sum(xs) / len(xs), sum(ys) / len(ys)
    ext = max(max(xs) - min(xs), max(ys) - min(ys)) / 2 or 1
    out = {}
    for y in range(int(min(ys)) - 1, int(max(ys)) + 2):
        for x in range(int(min(xs)) - 1, int(max(xs)) + 2):
            px_, py_ = x + 0.5, y + 0.5
            inside = False
            j = len(points) - 1
            for i in range(len(points)):
                xi, yi = points[i]
                xj, yj = points[j]
                if (yi > py_) != (yj > py_) and px_ < (xj - xi) * (py_ - yi) / (yj - yi + 1e-9) + xi:
                    inside = not inside
                j = i
            if inside:
                out[(x, y)] = (clamp((px_ - cx) / ext, -1, 1) * bulge, clamp((py_ - cy) / ext, -1, 1) * bulge)
    return out


def bezier(p0, p1, p2, n=8):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in (i / n for i in range(n + 1))]


def rot(p, origin, angle):
    c, s = math.cos(angle), math.sin(angle)
    dx, dy = p[0] - origin[0], p[1] - origin[1]
    return origin[0] + dx * c - dy * s, origin[1] + dx * s + dy * c


def polar(origin, length, angle):
    """Point at angle (radians, 0 = right, positive = clockwise on screen / downward)."""
    return origin[0] + math.cos(angle) * length, origin[1] + math.sin(angle) * length


# ---------- the rig ----------
class Rig:
    def __init__(self, w, h, rim=0.5):
        self.w, self.h = w, h
        self.img = new(w, h)
        self.px = self.img.load()
        self.owner = {}
        self.normal = {}
        self.n = 0
        self.rim = rim
        self.post = []

    def part(self, shape, rmp, edge=True, bias=0.0, line=None, flat=None):
        """Shades shape with the ramp (index 0 = highlight). bias > 0 darkens (far limbs), flat = fixed
        value 0..1 instead of lighting. edge: dark inner line where it overlaps earlier parts."""
        self.n += 1
        pid = self.n
        for (x, y), (nx, ny) in shape.items():
            if not (0 <= x < self.w and 0 <= y < self.h):
                continue
            if flat is not None:
                value = flat
            else:
                nz = math.sqrt(max(0.0, 1 - nx * nx - ny * ny))
                lam = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
                value = 1 - clamp((lam + 0.25) / 1.15)
            idx = dither_index(clamp(value + bias), x, y, len(rmp))
            self.px[x, y] = rmp[idx]
        if edge:
            lc = line or mix(rmp[-1], OUTLINE, 0.5)
            for (x, y) in shape:
                if not (0 <= x < self.w and 0 <= y < self.h):
                    continue
                for dx, dy in N4:
                    q = (x + dx, y + dy)
                    if q not in shape and q in self.owner:
                        self.px[x, y] = lc
                        break
        for p, n in shape.items():
            if 0 <= p[0] < self.w and 0 <= p[1] < self.h:
                self.owner[p] = pid
                self.normal[p] = n
        return self

    def dot(self, x, y, c):
        x, y = int(round(x - 0.5)), int(round(y - 0.5))
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def dots(self, pts, c):
        for x, y in pts:
            self.dot(x, y, c)

    def stroke(self, x0, y0, x1, y1, c):
        line(self.px, self.w, self.h, int(round(x0 - 0.5)), int(round(y0 - 0.5)),
             int(round(x1 - 0.5)), int(round(y1 - 0.5)), c)

    def glow(self, cx, cy, r, c, strength=0.6):
        """Light spilling out of an emissive spot, also onto the transparent surroundings (after the outline)."""
        self.post.append((cx, cy, r, c, strength))

    def render(self):
        # lava bounce light along the lower edges
        if self.rim > 0:
            for (x, y), (nx, ny) in self.normal.items():
                if ny > 0.15 and (x, y + 1) not in self.owner:
                    self.px[x, y] = mix(self.px[x, y], LAVA_RIM, self.rim * min(1.0, ny + 0.3))
        img = outline(self.img, OUTLINE)
        px = img.load()
        for cx, cy, r, c, strength in self.post:
            for y in range(int(cy - r) - 1, int(cy + r) + 2):
                for x in range(int(cx - r) - 1, int(cx + r) + 2):
                    if not (0 <= x < self.w and 0 <= y < self.h):
                        continue
                    d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
                    if d >= 1:
                        continue
                    t = round(strength * (1 - d) ** 1.5 * 4) / 4
                    if t <= 0:
                        continue
                    old = px[x, y]
                    if old[3] == 0:
                        px[x, y] = (c[0], c[1], c[2], int(150 * t))
                    else:
                        px[x, y] = mix(old, (c[0], c[1], c[2], 255), t * 0.6)
        return img


def flame(rig, x, y, height, width, phase, rmp=EMBER, lean=0.0):
    """A licking flame tongue standing on (x, y), drawn flat (emissive)."""
    pts = []
    n = 6
    for i in range(n + 1):
        t = i / n
        w = width * (1 - t) ** 0.8 * (0.8 + 0.2 * math.sin(phase * 6.28 + t * 5))
        pts.append((t, w))
    shape = {}
    for t, w in pts:
        cx = x + lean * t * height + math.sin(phase * 6.28 + t * 4) * 0.8 * t
        cy = y - t * height
        for dx in range(int(-w - 1), int(w + 2)):
            if abs(dx + 0.5) <= w + 0.3:
                shape[(int(cx + dx), int(cy))] = (0, 0)
    # colour by height: white-hot root .. deep red tip
    for (px_, py_) in shape:
        t = clamp((y - py_) / max(height, 1))
        idx = min(len(rmp) - 1, int(1 + t * (len(rmp) - 1)))
        rig.part({(px_, py_): (0, 0)}, [rmp[idx]], edge=False, flat=0)
    return rig


def eye(rig, x, y, c=hexc('#ffe14d'), hot=hexc('#fffbe0'), w=2, glow=True, brow=1):
    """Glowing slit eye looking forward; brow 1 = angry slant over it."""
    for k in range(w):
        rig.dot(x + k, y, c)
    rig.dot(x + w - 1, y, hot)
    if brow:
        for k in range(-1, w + 1):
            rig.dot(x + k, y - 1 - (k * 0.5 if brow > 0 else 0) + (w * 0.5 if brow > 0 else 0) * 0, OUTLINE)
    if glow:
        rig.glow(x + w / 2, y + 0.5, 3.2, c, 0.5)


def dark(rmp, t=0.35):
    """The same ramp in shadow (far limbs)."""
    return [mix(c, OUTLINE, t) for c in rmp]


# =====================================================================================================
# Imp: small crimson demon, comes in packs, hops at you
# =====================================================================================================
IMP_SKIN = ramp('#ffab86', '#ef5a3e', '#b82e2c', '#7a1826', '#430c18')
IMP_WING = ramp('#d24a4a', '#962436', '#651628', '#3e0c1a', '#22060e')
IMP_W, IMP_H, IMP_FEET = 40, 36, 34


def draw_imp(bob=0.0, lean=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), arms=(0.4, -0.3), wing=0.0,
             tail=0.0, mouth=0.0, crouch=0.0, tuck=0.0, blink=False):
    """legs: swing angle per leg (front, back) in radians; lift: foot lift px; arms: (front, back) swing angle
    from straight down (positive = forward); wing: flap 0 rest .. 1 up, -1 down; tuck: airborne knees up."""
    r = Rig(IMP_W, IMP_H, rim=0.55)
    cx = 19.0
    hip = (cx - 1 + lean * 0.3, IMP_FEET - 8 + bob + crouch * 3)
    chest = (hip[0] + 1 + lean * 0.8, hip[1] - 6 + crouch * 0.5)
    head = (chest[0] + 2 + lean * 0.6, chest[1] - 6 + crouch * 1.2)

    def leg(i, rmp, bias):
        a = legs[i]
        foot = (hip[0] + math.sin(a) * 6 + (i - 0.5) * 1.5 - tuck * 1.5, IMP_FEET - 0.5 - lift[i] - tuck * 4)
        if crouch > 0:
            foot = (foot[0] + (i - 0.5) * 2 * crouch, foot[1])
        mid = ((hip[0] + foot[0]) / 2 + 2.2 + tuck * 2 + crouch * 1.5, (hip[1] + foot[1]) / 2 - crouch * 1.2)
        r.part(union(limb(hip[0], hip[1], mid[0], mid[1], 2.0, 1.5), limb(mid[0], mid[1], foot[0] - 0.5, foot[1] - 1, 1.4, 1.0)),
               rmp, bias=bias)
        r.part(limb(foot[0] - 1.2, foot[1] - 0.8, foot[0] + 2.2, foot[1] - 0.6, 1.0, 0.7), rmp, bias=bias)
        r.dot(foot[0] + 3.0, foot[1] - 0.3, HORN[2])

    def arm(i, rmp, bias):
        a = arms[i]
        sh = (chest[0] + (1.5 if i == 0 else -1.0), chest[1] - 2)
        el = polar(sh, 4, math.pi / 2 - a * 0.9)
        hand = polar(el, 3.6, math.pi / 2 - a * 1.4 - 0.5)
        r.part(union(limb(sh[0], sh[1], el[0], el[1], 1.4, 1.1), limb(el[0], el[1], hand[0], hand[1], 1.1, 0.9)), rmp, bias=bias)
        ang = math.atan2(hand[1] - el[1], hand[0] - el[0])
        for k in (-0.45, 0.35):
            tip = polar(hand, 1.8, ang + k)
            r.stroke(hand[0], hand[1], tip[0], tip[1], HORN[1] if i == 0 else HORN[3])

    # wings behind everything
    base = (chest[0] - 2.5, chest[1] - 1.5)
    up = 0.6 + wing * 0.6
    tip1 = polar(base, 10, -math.pi * 0.62 - up * 0.5)
    tip2 = polar(base, 8, -math.pi * 0.85 - up * 0.3)
    tip3 = polar(base, 5, -math.pi * 1.05)
    mem = poly([base, tip1, ((tip1[0] + tip2[0]) / 2 + 0.8, (tip1[1] + tip2[1]) / 2 + 1.5), tip2,
                ((tip2[0] + tip3[0]) / 2 + 0.8, (tip2[1] + tip3[1]) / 2 + 1.2), tip3], bulge=0.2)
    r.part(mem, IMP_WING, bias=0.1)
    for t in (tip1, tip2):
        r.stroke(base[0], base[1], t[0], t[1], IMP_SKIN[3])

    # tail: from the hip, swaying, ending in a spade
    tp = [(hip[0] - 1.5, hip[1] + 0.5)]
    for k in range(1, 5):
        tp.append((hip[0] - 1.5 - k * 2.4, hip[1] - 0.5 - k * 0.6 + math.sin(tail + k * 0.9) * 1.4 * k / 4))
    r.part(chain(tp, 1.0, 0.6), IMP_SKIN, bias=0.25)
    end = tp[-1]
    r.part(poly([(end[0] + 0.5, end[1] - 1.8), (end[0] - 2.8, end[1]), (end[0] + 0.5, end[1] + 1.8), (end[0] - 0.6, end[1])], 0.3),
           IMP_SKIN, bias=0.2)

    leg(1, IMP_SKIN, 0.3)
    arm(1, IMP_SKIN, 0.32)
    r.part(union(ell(chest[0], chest[1] + 0.5, 3.6, 4.4, -0.2 - lean * 0.08), ell(hip[0] + 0.3, hip[1] - 0.6, 3.0, 2.6)), IMP_SKIN)
    leg(0, IMP_SKIN, 0.0)

    # head: round skull, pointed ear swept back, two small horns
    ear = poly([(head[0] - 3.2, head[1] - 1.0), (head[0] - 8.0, head[1] - 3.6), (head[0] - 3.0, head[1] + 1.4)], 0.3)
    r.part(ear, IMP_SKIN, bias=0.05)
    r.part(ell(head[0], head[1], 4.8, 4.3), IMP_SKIN)
    r.part(ell(head[0] + 2.8, head[1] + 1.6, 2.4, 1.8), IMP_SKIN, edge=False)          # snout
    for hx, bend, b in ((-2.2, -2.8, 0.3), (1.6, -0.6, 0.0)):
        hb = (head[0] + hx, head[1] - 3.2)
        pts = bezier(hb, (hb[0] + 0.3, hb[1] - 2.6), (hb[0] + bend, hb[1] - 4.4), 5)
        r.part(chain(pts, 1.0, 0.2), HORN, bias=b)
    # face
    ey = head[1] - 0.4
    if blink:
        r.stroke(head[0] + 0.6, ey, head[0] + 4.2, ey, OUTLINE)
    else:
        for ex in (head[0] + 0.6, head[0] + 3.6):
            r.dot(ex, ey, hexc('#ffd23a'))
            r.dot(ex + 1, ey, hexc('#fffbe0'))
            r.stroke(ex - 0.5, ey - 1.6, ex + 1.5, ey - 0.8, OUTLINE)
        r.glow(head[0] + 2.6, ey + 0.5, 4.0, hexc('#ffd23a'), 0.45)
    my = head[1] + 2.4
    open_px = int(round(mouth * 2))
    r.stroke(head[0] + 0.5, my, head[0] + 4.6, my - 0.6, OUTLINE)
    if open_px:
        for k in range(1, open_px + 1):
            r.stroke(head[0] + 1.0, my + k, head[0] + 4.0, my + k - 0.6, hexc('#2a0610'))
        r.stroke(head[0] + 1.0, my + open_px + 1, head[0] + 4.0, my + open_px + 0.4, OUTLINE)
        r.dot(head[0] + 3.6, my + open_px + 1 - 0.5, hexc('#ff7a3a'))
    for tx in (1.5, 3.3):
        r.dot(head[0] + tx, my + 0.5, hexc('#fff4dc'))

    arm(0, IMP_SKIN, 0.0)
    return r.render()


def imp_anims():
    idle = []
    for i in range(4):
        s = math.sin(i / 4 * 2 * math.pi)
        idle.append(draw_imp(bob=s * 0.5, arms=(0.25 + s * 0.1, -0.2 - s * 0.1), wing=s * 0.5, tail=i * 1.57,
                             blink=(i == 2)))
    run = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        a = math.sin(p) * 0.85
        run.append(draw_imp(bob=-abs(math.cos(p)) * 1.2 + 0.6, lean=3, legs=(a, -a),
                            lift=(max(0, -math.cos(p)) * 2.5, max(0, math.cos(p)) * 2.5),
                            arms=(-a * 0.9 + 0.2, a * 0.9 + 0.2), wing=math.sin(p * 2) * 0.6 - 0.2,
                            tail=p, mouth=0.5))
    crouch = [draw_imp(crouch=1.0, lean=2, arms=(-0.4, -0.7), wing=0.8, tail=1.0, mouth=1.0)]
    leap = [draw_imp(bob=-3, lean=4, legs=(-0.9, -1.1), tuck=1.0, arms=(1.6, 1.2), wing=1.0, tail=2.2, mouth=1.0),
            draw_imp(bob=-2, lean=1, legs=(0.6, 0.3), lift=(1, 1), arms=(2.4, 2.0), wing=-0.8, tail=3.4, mouth=1.0)]
    return dict(idle=idle, run=run, crouch=crouch, leap=leap)


# =====================================================================================================
# Hellhound: skull-faced hound with a burning mane; crouches, then lunges a long way
# =====================================================================================================
HOUND_FUR = ramp('#b0705e', '#7c3f3a', '#55262b', '#37161d', '#200b10')
SKULL = ramp('#fffaee', '#e8dcc0', '#bfae88', '#8a7a5a', '#544a36')
HOUND_W, HOUND_H, HOUND_FEET = 64, 40, 38


def draw_hound(bob=0.0, legs=(0.0, 0.0, 0.0, 0.0), lift=(0, 0, 0, 0), head=(0.0, 0.0), jaw=0.2, rear=0.0,
               stretch=0.0, phase=0.0, tail=0.0, mane_lean=-0.3, tongue=False, eye_hot=0.0, shake=0.0):
    """legs: swing (+ = forward) for front-near, front-far, back-near, back-far; head: offset of the head;
    rear: lowers the haunches (crouch); stretch: lengthens the body (lunge)."""
    r = Rig(HOUND_W, HOUND_H, rim=0.5)
    ox = shake
    chest = (40 + ox + stretch * 2, 19 + bob)
    haunch = (21 + ox - stretch * 2, 20 + bob + rear)
    sh_root = (chest[0] - 1, chest[1] + 4)
    hip_root = (haunch[0] + 1, haunch[1] + 3)

    def front_leg(a, lf, rmp, bias):
        paw = (sh_root[0] + math.sin(a) * 11, HOUND_FEET - 1 - lf)
        el = ((sh_root[0] + paw[0]) / 2 - 1.5, (sh_root[1] + paw[1]) / 2 - 0.5)
        r.part(union(limb(sh_root[0], sh_root[1], el[0], el[1], 3.2, 2.2), limb(el[0], el[1], paw[0], paw[1] - 1, 2.0, 1.5)),
               rmp, bias=bias)
        r.part(ell(paw[0] + 1, paw[1] - 0.6, 2.4, 1.3), rmp, bias=bias)
        r.dot(paw[0] + 3.4, paw[1] - 0.2, SKULL[2])

    def back_leg(a, lf, rmp, bias):
        paw = (hip_root[0] + math.sin(a) * 11 - 1, HOUND_FEET - 1 - lf)
        knee = rot((hip_root[0] + 3.5, hip_root[1] + 4.5 - rear * 0.5), hip_root, -a * 0.6)
        hock = ((knee[0] + paw[0]) / 2 - 2.5 - rear * 0.8, (knee[1] + paw[1]) / 2 + 0.5)
        r.part(union(limb(hip_root[0], hip_root[1], knee[0], knee[1], 4.0, 2.6),
                     limb(knee[0], knee[1], hock[0], hock[1], 2.2, 1.6),
                     limb(hock[0], hock[1], paw[0], paw[1] - 1, 1.6, 1.4)), rmp, bias=bias)
        r.part(ell(paw[0] + 1, paw[1] - 0.6, 2.4, 1.3), rmp, bias=bias)

    # tail with a burning tip
    tb = (haunch[0] - 5, haunch[1] - 3)
    tp = bezier(tb, (tb[0] - 5, tb[1] - 1 + math.sin(tail) * 2), (tb[0] - 8, tb[1] - 7 + math.sin(tail + 1) * 2.5), 6)
    r.part(chain(tp, 1.8, 0.8), HOUND_FUR, bias=0.1)

    front_leg(legs[1], lift[1], HOUND_FUR, 0.35)
    back_leg(legs[3], lift[3], HOUND_FUR, 0.35)
    body = union(ell((chest[0] + haunch[0]) / 2, (chest[1] + haunch[1]) / 2 + 0.5, 11 + stretch * 2, 6.0,
                     math.atan2(chest[1] - haunch[1], chest[0] - haunch[0])),
                 ell(chest[0], chest[1], 7.0, 7.5), ell(haunch[0], haunch[1], 6.5, 6.5))
    r.part(body, HOUND_FUR)
    # glowing rib cracks
    mx = (chest[0] + haunch[0]) / 2
    for k, rx in enumerate((-3.5, 0.0, 3.5)):
        x0, y0 = mx + rx, chest[1] - 1 + abs(rx) * 0.1
        r.stroke(x0, y0, x0 - 1, y0 + 5.5, EMBER[3])
        r.dot(x0 - 0.5, y0 + 2.5, EMBER[1])
        r.glow(x0 - 0.5, y0 + 2.5, 3.5, EMBER[2], 0.35)
    back_leg(legs[2], lift[2], HOUND_FUR, 0.0)
    front_leg(legs[0], lift[0], HOUND_FUR, 0.0)

    # neck + mane of fire along the back of the neck and shoulders
    hc = (48 + ox + stretch * 3 + head[0], 12 + bob + head[1])
    r.part(limb(chest[0] + 1, chest[1] - 2, hc[0] - 1, hc[1] + 1, 4.5, 3.4), HOUND_FUR, edge=False)
    for k in range(6):
        t = k / 5
        bx = chest[0] - 6 + (hc[0] - 3 - (chest[0] - 6)) * t
        by = chest[1] - 5.5 + (hc[1] - 3.5 - (chest[1] - 5.5)) * t
        flame(r, bx, by + 1, 5 + 3 * math.sin(phase * 6.28 + k * 1.7) ** 2 + (1 - abs(t - 0.5)) * 2, 1.6,
              phase + k * 0.21, lean=mane_lean)
    r.glow(chest[0] - 1, chest[1] - 7, 7, EMBER[2], 0.25)
    # tail flame
    flame(r, tp[-1][0], tp[-1][1] + 1, 5 + 2 * math.sin(phase * 6.28 + 2), 1.5, phase + 0.5, lean=mane_lean - 0.2)

    # skull head: cranium, snout, opening jaw, a short horn
    r.part(chain(bezier((hc[0] - 2, hc[1] - 3), (hc[0] - 5, hc[1] - 6), (hc[0] - 8.5, hc[1] - 5), 4), 1.3, 0.3), HORN, bias=0.1)
    jaw_end = (hc[0] + 7.5, hc[1] + 4.5 + jaw * 3.5)
    r.part(limb(hc[0] + 0.5, hc[1] + 3, jaw_end[0], jaw_end[1], 1.7, 1.1), SKULL, bias=0.35)
    if jaw > 0.3:
        r.part(poly([(hc[0] + 2, hc[1] + 2.5), (hc[0] + 8.5, hc[1] + 3), jaw_end, (hc[0] + 2, hc[1] + 4)], 0.1),
               [hexc('#5a0f12'), hexc('#3a080c')], edge=False)
        if tongue:
            r.part(limb(hc[0] + 4, hc[1] + 4.5, hc[0] + 7, hc[1] + 6.5 + jaw, 0.9, 0.9), [hexc('#ff6a6a'), hexc('#c23040')], edge=False)
        r.glow(hc[0] + 5, hc[1] + 4, 3.5, EMBER[2], 0.4)
    r.part(union(ell(hc[0], hc[1], 4.6, 4.0), limb(hc[0] + 1, hc[1] + 0.5, hc[0] + 8, hc[1] + 2, 2.6, 1.9)), SKULL)
    for tx in (3.5, 5.5, 7.2):                                  # upper fangs
        r.dot(hc[0] + tx, hc[1] + 4.2, SKULL[1])
    if jaw > 0.3:
        for tx in (4.5, 6.5):
            r.dot(jaw_end[0] - 7.5 + tx, jaw_end[1] - 1.5 - jaw * (1 - tx / 8), SKULL[1])
    # eye socket with an ember pupil, nostril, cracks
    r.part(ell(hc[0] + 1.6, hc[1] - 0.5, 1.7, 1.4), [hexc('#1a0a08')], edge=False)
    hot = EMBER[0] if eye_hot > 0.5 else EMBER[1]
    r.dot(hc[0] + 2.0, hc[1] - 0.5, hot)
    r.glow(hc[0] + 2.0, hc[1] - 0.5, 3.5 + eye_hot * 2, EMBER[2], 0.5 + eye_hot * 0.3)
    r.dot(hc[0] + 8.2, hc[1] + 0.8, hexc('#2a1a12'))
    r.stroke(hc[0] - 2.5, hc[1] - 2.5, hc[0] - 1, hc[1] - 0.5, SKULL[3])
    return r.render()


def hound_anims():
    idle = [draw_hound(bob=math.sin(i / 4 * 6.28) * 0.5, legs=(0.05, -0.05, 0.0, 0.08), phase=i / 4,
                       tail=i * 1.57, jaw=0.15) for i in range(4)]
    run = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        fa = math.sin(p) * 0.9
        ba = math.sin(p + math.pi * 0.85) * 0.9
        run.append(draw_hound(bob=-math.sin(p * 2) * 1.0, legs=(fa, math.sin(p - 0.5) * 0.9, ba, math.sin(p + math.pi * 0.85 - 0.5) * 0.9),
                              lift=(max(0, -math.cos(p)) * 3, max(0, -math.cos(p - 0.5)) * 3,
                                    max(0, -math.cos(p + 2.7)) * 3, max(0, -math.cos(p + 2.2)) * 3),
                              head=(0.5, math.sin(p * 2) * 0.6), stretch=math.sin(p) * 0.6, phase=i / 6,
                              tail=p, mane_lean=-0.9, jaw=0.35))
    crouch = [draw_hound(bob=3.5, rear=1.5, head=(-1.5, 4.5), legs=(0.35, 0.25, -0.35, -0.25), jaw=0.6, phase=k * 0.5,
                         mane_lean=0.1, eye_hot=1.0, shake=(-0.6 if k == 0 else 0.6)) for k in range(2)]
    lunge = [draw_hound(bob=-2.5, stretch=2.0, head=(2.5, 1.0), legs=(1.25, 1.1, -1.2, -1.05), lift=(3, 3, 2, 2),
                        jaw=1.0, phase=k * 0.5, mane_lean=-1.3, tail=2.5 + k, eye_hot=1.0) for k in range(2)]
    pant = [draw_hound(bob=0.8 + math.sin(i / 4 * 6.28) * 0.8, head=(-0.5, 2.5), legs=(0.1, 0.0, -0.05, 0.05),
                       jaw=0.7 + 0.2 * math.sin(i / 4 * 6.28), tongue=True, phase=i / 4, tail=i * 0.8) for i in range(4)]
    return dict(idle=idle, run=run, crouch=crouch, lunge=lunge, pant=pant)


# =====================================================================================================
# Pit Brute: hulking ash-violet demon with ram horns and shackled fists; slams the ground
# =====================================================================================================
BRUTE_SKIN = ramp('#d4c0de', '#a88dbd', '#7e649a', '#59447a', '#3a2a55', '#22172f')
BRUTE_LEATHER = ramp('#8a6a52', '#634a38', '#463224', '#2e2018', '#1c130e')
BRUTE_K = 2                          # drawn at twice the pixel detail of the other demons
BRUTE_W, BRUTE_H, BRUTE_FEET = 160, 168, 82     # BRUTE_FEET in design units (x BRUTE_K = pixel row 164)


def draw_brute(bob=0.0, lean=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), arms=((1.35, 1.5), (1.3, 1.45)),
               mouth=0.0, chains=0.0, heat=0.3, blink=False):
    """The pose is laid out in design units (the old 80x84 frame) and every primitive is drawn at x BRUTE_K,
    so outlines, cracks, wrinkles and rivets come out at the finer pixel size.
    arms: ((upper, fore) near, (upper, fore) far) as absolute screen angles (pi/2 = straight down);
    lean: torso tilt (+ = forward); heat 0..1: how bright the magma cracks glow."""
    K = BRUTE_K
    r = Rig(BRUTE_W, BRUTE_H, rim=0.5)
    E = lambda cx, cy, rx, ry, a=0.0: ell(cx * K, cy * K, rx * K, ry * K, a)
    L = lambda x0, y0, x1, y1, r0, r1=None: limb(x0 * K, y0 * K, x1 * K, y1 * K, r0 * K, (r0 if r1 is None else r1) * K)
    C = lambda pts, r0, r1: chain([(x * K, y * K) for x, y in pts], r0 * K, r1 * K)
    PG = lambda pts, b=0.6: poly([(x * K, y * K) for x, y in pts], b)
    D = lambda x, y, c: r.dot(x * K, y * K, c)
    S = lambda x0, y0, x1, y1, c: r.stroke(x0 * K, y0 * K, x1 * K, y1 * K, c)
    G = lambda x, y, rad, c, st: r.glow(x * K, y * K, rad * K, c, st)
    skin = BRUTE_SKIN
    crease = skin[4]

    bob += BRUTE_FEET - 70           # the pose numbers below are for feet at y = 70
    hip = (31.0, 51 + bob)
    torso = rot((37, 39 + bob), hip, lean * 0.6)
    hump = rot((43, 30 + bob), hip, lean * 0.8)
    head = rot((54, 33 + bob), hip, lean * 0.9)
    hot = EMBER[1] if heat >= 0.6 else EMBER[2]

    def magma(pts, bright=True, branch=None):
        """A glowing crack along pts: dark red rim, molten core, a small branch, light spilling out."""
        for a, b in zip(pts, pts[1:]):
            S(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5, EMBER[4])
        for a, b in zip(pts, pts[1:]):
            S(a[0], a[1], b[0], b[1], hot if bright else EMBER[3])
        if branch:
            S(branch[0][0], branch[0][1], branch[1][0], branch[1][1], EMBER[3])
        if bright:
            mid = pts[len(pts) // 2]
            G(mid[0], mid[1], 4, EMBER[2], 0.2 + heat * 0.35)

    def leg(i, bias):
        a = legs[i]
        root = (hip[0] + (2 if i == 0 else -5), hip[1] + 1)
        foot = (root[0] + math.sin(a) * 7 + 1, BRUTE_FEET - 1.5 - lift[i])
        knee = ((root[0] + foot[0]) / 2 + 3.5, (root[1] + foot[1]) / 2 - 1)
        r.part(union(L(root[0], root[1], knee[0], knee[1], 6.5, 5.0), L(knee[0], knee[1], foot[0], foot[1] - 1.5, 4.8, 3.8)),
               skin, bias=bias)
        # kneecap and calf
        r.part(E(knee[0] + 1.2, knee[1] - 0.3, 2.6, 2.2), skin, edge=False, bias=bias - 0.08)
        S(knee[0] - 2.5, knee[1] + 2, knee[0] - 1.5, foot[1] - 4, crease if bias == 0 else skin[5])
        # foot with three clawed toes
        r.part(E(foot[0] + 2, foot[1] - 0.8, 5.0, 2.2), skin, bias=bias)
        for k in range(3):
            tx, ty = foot[0] + 4.6 + k * 0.4, foot[1] - 1.9 + k * 0.85
            r.part(E(tx, ty, 1.3, 0.9), skin, bias=bias + 0.05)
            S(tx + 1.0, ty, tx + 2.2, ty + 0.4, HORN[1 if bias == 0 else 3])
            D(tx + 2.4, ty + 0.5, HORN[2])
        if i == 0:
            magma([(knee[0] - 3, knee[1] - 4), (knee[0] - 1.5, knee[1] - 1.5), (knee[0] - 2, knee[1] + 1)], bright=False)

    def arm(i, bias):
        a1, a2 = arms[i]
        sh = rot((47, 33 + bob) if i == 0 else (35, 31 + bob), hip, lean * 0.8)
        el = polar(sh, 13, a1)
        wr = polar(el, 13, a2)
        fist = polar(wr, 3.2, a2)
        r.part(union(L(sh[0], sh[1], el[0], el[1], 6.0, 4.6), L(el[0], el[1], wr[0], wr[1], 6.2, 5.2)), skin, bias=bias)
        # deltoid cap, biceps line, elbow wrinkles
        r.part(E(sh[0] + 0.5, sh[1] + 0.5, 5.0, 4.2, a1), skin, edge=False, bias=bias - 0.1)
        b0, b1 = polar(sh, 5.5, a1), polar(sh, 10.5, a1)
        S(b0[0] - 2.5, b0[1] + 1, b1[0] - 2.5, b1[1] + 0.5, crease if bias == 0 else skin[5])
        for k in (-1, 0, 1):
            w0 = polar(el, 1.2, a2 + math.pi / 2)
            S(el[0] - 1 + k * 0.8, el[1] - 0.5 + k, w0[0] - 1.5 + k * 0.8, w0[1] + k, crease)
        # iron shackle round the wrist: band, rims, rivets
        s0 = polar(el, 9.5, a2)
        s1 = polar(el, 12.0, a2)
        r.part(L(s0[0], s0[1], s1[0], s1[1], 6.0, 5.8), BLACK_IRON, bias=bias)
        nrm = a2 + math.pi / 2
        for t in (0.0, 1.0):
            c = (s0[0] + (s1[0] - s0[0]) * t, s0[1] + (s1[1] - s0[1]) * t)
            e0, e1 = polar(c, 5.6, nrm), polar(c, -5.6, nrm)
            S(e0[0], e0[1], e1[0], e1[1], BLACK_IRON[1] if bias == 0 else BLACK_IRON[3])
        mid = ((s0[0] + s1[0]) / 2, (s0[1] + s1[1]) / 2)
        for k in (-3.2, 0.0, 3.2):
            rv = polar(mid, k, nrm)
            D(rv[0], rv[1], BLACK_IRON[0] if bias == 0 else BLACK_IRON[2])
        # fist: knuckles, finger lines, thumb
        r.part(E(fist[0], fist[1], 6.0, 5.4, a2), skin, bias=bias)
        for k in range(4):
            kn = polar(fist, 4.4, a2 - 0.9 + k * 0.5)
            r.part(E(kn[0], kn[1], 1.5, 1.4), skin, edge=False, bias=bias - 0.15)
            if k:
                f0, f1 = polar(fist, 2.2, a2 - 1.15 + k * 0.5), polar(fist, 5.4, a2 - 1.15 + k * 0.5)
                S(f0[0], f0[1], f1[0], f1[1], crease if bias == 0 else skin[5])
        th = polar(fist, 3.5, a2 + 1.6)
        r.part(E(th[0], th[1], 2.0, 1.4, a2), skin, bias=bias + 0.05)
        if i == 0:
            # broken chain: alternating face-on rings and side-on links
            cx_, cy_ = s0[0] - 4.5, s0[1] + 3
            for k in range(5):
                lx = cx_ - k * 1.3 - math.sin(chains + k * 0.8) * 1.0 * k
                ly = cy_ + k * 2.4
                if k % 2 == 0:
                    outer, inner = E(lx, ly, 1.9, 2.2), E(lx, ly, 0.9, 1.2)
                    r.part({p: n for p, n in outer.items() if p not in inner}, BLACK_IRON, edge=False)
                else:
                    r.part(L(lx, ly - 1.4, lx, ly + 1.4, 0.75), BLACK_IRON, edge=False)
                D(lx - 0.8, ly - 1.2, BLACK_IRON[0])
            magma([polar(el, 2.0, a2), polar(el, 4.8, a2 + 0.25), polar(el, 7.5, a2 + 0.1)],
                  branch=(polar(el, 4.8, a2 + 0.25), polar(el, 5.6, a2 + 0.6)))
        else:
            magma([polar(el, 2.5, a2), polar(el, 7, a2 + 0.2)], bright=False)

    def horn(dx, dy, bias):
        hb = (head[0] - 1 + dx, head[1] - 4.5 + dy)
        pts = bezier(hb, (hb[0] - 1, hb[1] - 10), (hb[0] - 7, hb[1] - 7), 10) + \
            bezier((hb[0] - 7, hb[1] - 7), (hb[0] - 12, hb[1] - 2), (hb[0] - 6.5, hb[1] + 3.5), 8)[1:]
        r.part(C(pts, 2.8, 1.0), HORN, bias=bias)
        # growth ridges across the horn, getting closer toward the tip
        for k in range(1, len(pts) - 2):
            if k % 2:
                continue
            a, b = pts[k - 1], pts[k + 1]
            ang = math.atan2(b[1] - a[1], b[0] - a[0]) + math.pi / 2
            rad = 2.8 + (1.0 - 2.8) * k / len(pts)
            e0, e1 = polar(pts[k], rad * 0.85, ang), polar(pts[k], -rad * 0.85, ang)
            S(e0[0], e0[1], e1[0], e1[1], HORN[3] if bias == 0 else HORN[4])
        D(pts[-1][0], pts[-1][1], HORN[0])

    arm(1, 0.35)
    leg(1, 0.32)
    # bony spikes along the hump
    for k, (sx, sy, ln) in enumerate(((-9, -5, 3.2), (-5.5, -8.2, 4.0), (-1.5, -9.6, 4.4), (2.5, -9.4, 3.6))):
        base = rot((hump[0] + sx, hump[1] + sy), hump, -0.2)
        tip = (base[0] - 1.8 - ln * 0.3, base[1] - ln)
        r.part(PG([(base[0] - 1.6, base[1] + 1.5), tip, (base[0] + 1.7, base[1] + 1.2)], 0.4), HORN, bias=0.12)
    r.part(union(E(torso[0], torso[1], 14.5, 14.0, -0.35 + lean * 0.3), E(hump[0], hump[1], 13, 9.5, -0.2),
                 E(hip[0] + 1, hip[1] - 2, 10, 7)), skin)
    r.part(E(torso[0] + 5, torso[1] + 6, 8, 7.5), skin, edge=False, bias=-0.1)   # belly
    # pecs and abs
    S(torso[0] + 0.5, torso[1] - 1.5, torso[0] + 5, torso[1] + 0.8, crease)
    S(torso[0] + 5, torso[1] + 0.8, torso[0] + 9.5, torso[1] - 0.8, crease)
    for k in range(3):
        y = torso[1] + 4 + k * 2.6
        S(torso[0] + 3.2 + k * 0.3, y, torso[0] + 6.2 + k * 0.3, y + 0.4, crease)
        S(torso[0] + 7.2 + k * 0.3, y + 0.2, torso[0] + 10 + k * 0.2, y - 0.3, crease)
    S(torso[0] + 6.6, torso[1] + 2.5, torso[0] + 7.2, torso[1] + 11, crease)
    # warty, scarred hide on the hump (fixed to the body so it moves with it)
    rng = random.Random(77)
    for _ in range(26):
        ox, oy = rng.uniform(-11, 7), rng.uniform(-8, 5)
        if (ox / 12) ** 2 + (oy / 9) ** 2 > 0.8:
            continue
        x, y = hump[0] + ox, hump[1] + oy
        if (int(x * K), int(y * K)) in r.owner:
            D(x, y, skin[4] if rng.random() < 0.6 else skin[1])
    # magma scars across the chest and shoulder
    magma([(torso[0] - 1, torso[1] - 6), (torso[0] + 1.5, torso[1] - 3.5), (torso[0] + 4, torso[1] - 1), (torso[0] + 2.5, torso[1] + 2.5),
           (torso[0] + 3, torso[1] + 5)], branch=((torso[0] + 1.5, torso[1] - 3.5), (torso[0] - 1.5, torso[1] - 1.5)))
    magma([(hump[0] - 6, hump[1] - 3), (hump[0] - 3, hump[1] - 1), (hump[0] - 3.5, hump[1] + 2.5)])
    G(torso[0] + 3, torso[1] - 2, 7, EMBER[2], 0.15 + heat * 0.3)
    leg(0, 0.0)
    # iron-studded belt and a tattered loincloth
    belt = rot((hip[0] + 3, hip[1] - 2.5), hip, lean * 0.3)
    strap = bezier((belt[0] - 6.5, belt[1] - 2.2), (belt[0] + 0.5, belt[1] + 1.6), (belt[0] + 8.5, belt[1] - 0.6), 8)
    r.part(C(strap, 1.3, 1.3), BRUTE_LEATHER)
    for k in range(1, len(strap) - 1, 2):
        D(strap[k][0], strap[k][1] - 0.2, BLACK_IRON[0])
    cloth = [(belt[0] + 1, belt[1] + 1.2), (belt[0] + 9, belt[1] + 1.6), (belt[0] + 8.5 + lean * 3, belt[1] + 9),
             (belt[0] + 6.5, belt[1] + 8), (belt[0] + 5 + lean * 3, belt[1] + 11), (belt[0] + 3, belt[1] + 8.5), (belt[0] + 1.5, belt[1] + 10)]
    r.part(PG(cloth, 0.3), BRUTE_LEATHER)
    S(belt[0] + 4, belt[1] + 2.5, belt[0] + 4.5, belt[1] + 8, BRUTE_LEATHER[3])
    # head: heavy wrinkled brow, glowing slit eyes, nostrils, underbite with a row of teeth and two tusks
    jaw = (head[0] + 1, head[1] + 4 + mouth * 2.5)
    if mouth > 0.2:
        r.part(E(head[0] + 4, head[1] + 2.5 + mouth, 3.5, 1.5 + mouth * 1.5), [hexc('#3a0a10'), hexc('#24060a')], edge=False)
        G(head[0] + 4.5, head[1] + 3 + mouth, 4, EMBER[2], 0.3 + mouth * 0.3)
        for tx in (2.5, 4.0, 5.5):                                          # upper teeth
            D(head[0] + tx, head[1] + 2.2, SKULL[1])
    horn(-2.5, -1, 0.35)
    r.part(E(head[0], head[1], 7.0, 6.2), skin)
    r.part(L(jaw[0] - 2, jaw[1], jaw[0] + 6.5, jaw[1] + 0.5, 3.0, 2.4), skin, bias=0.1)
    for tx in (1.0, 2.0, 4.5):                                              # little lower teeth
        D(jaw[0] + tx, jaw[1] - 2.0, SKULL[2])
    for tx in (3.0, 6.0):                                                   # tusks
        r.part(L(jaw[0] + tx, jaw[1] - 1.5, jaw[0] + tx + 0.4, jaw[1] - 4.2, 0.8, 0.35), SKULL, edge=False)
        D(jaw[0] + tx - 0.2, jaw[1] - 3.2, SKULL[0])
    S(jaw[0] - 1.5, jaw[1] + 1.2, jaw[0] + 4, jaw[1] + 1.8, crease)        # chin fold
    r.part(L(head[0] - 1, head[1] - 2.8, head[0] + 6, head[1] - 2.0, 1.8, 1.4), skin, bias=-0.15)  # brow
    for k in range(3):                                                      # forehead wrinkles
        S(head[0] - 2 + k * 0.4, head[1] - 4.6 - k * 0.8, head[0] + 3.5 + k * 0.2, head[1] - 4.3 - k * 0.9, crease)
    if blink:
        S(head[0] + 1.5, head[1] - 0.5, head[0] + 5.5, head[1] - 0.5, OUTLINE)
    else:
        for ex in (head[0] + 1.5, head[0] + 4.8):
            S(ex - 0.6, head[1] - 0.6, ex + 1.6, head[1] - 0.6, hexc('#ff5a2a'))
            S(ex - 0.3, head[1] - 0.1, ex + 1.3, head[1] - 0.1, hexc('#b8241a'))
            D(ex + 0.8, head[1] - 0.6, hexc('#fff3a0'))
            S(ex - 1, head[1] - 1.3, ex + 2, head[1] - 1.5, OUTLINE)       # lid in the brow's shadow
        G(head[0] + 3.5, head[1] - 0.5, 4.5, hexc('#ff5a2a'), 0.45)
    D(head[0] + 6.4, head[1] + 1.0, hexc('#2a1530'))
    D(head[0] + 5.6, head[1] + 1.3, hexc('#2a1530'))
    S(head[0] - 3.5, head[1] + 1, head[0] - 1, head[1] + 3.5, crease)      # cheek scar
    horn(0, 0, 0.0)
    arm(0, 0.0)
    return r.render()


def _lerp_pose(a, b, t):
    """In-between of two draw_brute keyword poses (numbers and nested tuples of numbers; bools snap)."""
    def mix(x, y):
        if isinstance(x, bool) or isinstance(y, bool):
            return x if t < 0.5 else y
        if isinstance(x, tuple):
            return tuple(mix(u, v) for u, v in zip(x, y))
        return x + (y - x) * t
    defaults = dict(bob=0.0, lean=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), arms=((1.35, 1.5), (1.3, 1.45)),
                    mouth=0.0, chains=0.0, heat=0.3, blink=False)
    a, b = {**defaults, **a}, {**defaults, **b}
    return {k: mix(a[k], b[k]) for k in defaults}


def brute_anims():
    """Twice the frames of the other demons (idle 8, walk 12, slam 12, air 2), played at twice the rate."""
    idle = []
    for i in range(8):
        s = math.sin(i / 8 * 6.28)
        idle.append(draw_brute(bob=s * 0.8, arms=((1.35 - s * 0.03, 1.5), (1.3, 1.45 + s * 0.03)), chains=i * 0.4,
                               heat=0.3 + 0.2 * (s + 1) / 2, blink=(i in (4, 5))))
    walk = []
    for i in range(12):
        p = i / 12 * 2 * math.pi
        a = math.sin(p) * 0.55
        walk.append(draw_brute(bob=-abs(math.cos(p)) * 1.5 + 0.8, lean=0.05, legs=(a, -a),
                               lift=(max(0, -math.cos(p)) * 3, max(0, math.cos(p)) * 3),
                               arms=((1.3 - a * 0.35, 1.45 - a * 0.3), (1.3 + a * 0.35, 1.45 + a * 0.3)),
                               chains=p, heat=0.35))
    rest = dict(arms=((1.35, 1.5), (1.3, 1.45)), heat=0.3)
    keys = [
        dict(lean=-0.12, arms=((-0.6, -1.3), (-0.8, -1.4)), mouth=0.4, chains=1, heat=0.6),                 # raising
        dict(lean=-0.22, bob=-1, arms=((-1.75, -2.4), (-1.85, -2.5)), mouth=1.0, chains=2, heat=1.0),       # overhead
        dict(lean=0.18, bob=1.5, arms=((0.45, 0.95), (0.35, 0.85)), mouth=1.0, chains=3, heat=1.0),         # coming down
        dict(lean=0.32, bob=4, arms=((1.15, 1.42), (1.1, 1.4)), legs=(0.25, -0.3), mouth=0.8, chains=4, heat=1.0),   # impact
        dict(lean=0.3, bob=3.5, arms=((1.12, 1.4), (1.08, 1.38)), legs=(0.25, -0.3), mouth=0.5, chains=4.6, heat=0.8),
        dict(lean=0.12, bob=1.5, arms=((1.25, 1.45), (1.2, 1.42)), legs=(0.1, -0.15), mouth=0.2, chains=5, heat=0.5),
    ]
    # the held overhead pose strains: a little higher, hotter, the jaw wide open
    strain = dict(lean=-0.25, bob=-1.6, arms=((-1.8, -2.5), (-1.9, -2.6)), mouth=1.0, chains=2.4, heat=1.0)
    L = _lerp_pose
    slam_poses = [
        dict(lean=-0.06, bob=0.5, arms=((0.8, -1.25), (0.7, -1.35)), mouth=0.2, chains=0.5, heat=0.45),  # 0  elbows bend, fists rise
        keys[0],                        # 1
        L(keys[0], keys[1], 0.5),       # 2
        keys[1],                        # 3  overhead                 (held: 3 and 4 alternate)
        strain,                         # 4
        L(keys[1], keys[2], 0.5),       # 5  swinging down
        keys[2],                        # 6
        keys[3],                        # 7  impact
        L(keys[3], keys[4], 0.5),       # 8
        keys[4],                        # 9
        L(keys[4], keys[5], 0.5),       # 10
        keys[5],                        # 11
    ]
    slam = [draw_brute(**p) for p in slam_poses]
    air = [draw_brute(bob=-1, legs=(0.3, -0.25), lift=(2, 1), arms=((0.6, 0.9), (0.5, 0.8)), mouth=0.6, heat=0.5),
           draw_brute(bob=-1.3, legs=(0.36, -0.3), lift=(2.6, 1.4), arms=((0.5, 0.78), (0.42, 0.7)), mouth=0.7,
                      chains=0.8, heat=0.55)]
    return dict(idle=idle, walk=walk, slam=slam, air=air)


# =====================================================================================================
# Brimstone Hurler: squat sulfur demon with a volcanic shell, lobs balls of magma
# =====================================================================================================
HURLER_SKIN = ramp('#f4e690', '#cdb34a', '#93782c', '#5c4a1c', '#33280f')
ROCK = ramp('#857571', '#5a4b48', '#3e3331', '#2a2121', '#181212')
HURLER_W, HURLER_H, HURLER_FEET = 56, 52, 50


def magma_ball(r, cx, cy, rad, phase=0.0):
    r.part(ell(cx, cy, rad, rad), [EMBER[0], EMBER[1], EMBER[2], EMBER[3], EMBER[4]], edge=False)
    if rad > 2:
        for k in range(3):                                     # dark crust flakes turning round
            a = phase * 6.28 + k * 2.1
            r.dot(cx + math.cos(a) * rad * 0.55, cy + math.sin(a) * rad * 0.5, hexc('#5a1a0e'))
    r.glow(cx, cy, rad + 4, EMBER[2], 0.55)


def draw_hurler(bob=0.0, lean=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), arm=(1.0, -0.2), far=(1.3, 1.5),
                ball=3.2, maw=0.4, phase=0.0, blink=False):
    """arm / far: (upper, fore) absolute angles of the throwing arm and the other one; ball: radius of the
    magma ball in the throwing hand (0 = just thrown); maw 0..1: how wide the fiery mouth is open."""
    r = Rig(HURLER_W, HURLER_H, rim=0.5)
    hip = (25.0, 41 + bob)
    body = rot((26.5, 33 + bob), hip, lean)
    head = rot((33.5, 24 + bob), hip, lean * 1.2)

    def leg(i, bias):
        a = legs[i]
        root = (hip[0] + (1.5 if i == 0 else -3), hip[1])
        foot = (root[0] + math.sin(a) * 5 + 1, HURLER_FEET - 1 - lift[i])
        knee = ((root[0] + foot[0]) / 2 + 2.5, (root[1] + foot[1]) / 2 - 0.5)
        r.part(union(limb(root[0], root[1], knee[0], knee[1], 4.2, 3.0), limb(knee[0], knee[1], foot[0], foot[1] - 1, 3.0, 2.4)),
               HURLER_SKIN, bias=bias)
        r.part(ell(foot[0] + 1.5, foot[1] - 0.6, 3.4, 1.5), HURLER_SKIN, bias=bias)
        r.dot(foot[0] + 4.6, foot[1] - 0.4, HORN[2])

    def limb_arm(angles, bias, holds):
        a1, a2 = angles
        sh = rot((30, 28 + bob) if holds else (21.5, 28 + bob), hip, lean)
        el = polar(sh, 6.5, a1)
        hand = polar(el, 6.5, a2)
        r.part(union(limb(sh[0], sh[1], el[0], el[1], 2.9, 2.4), limb(el[0], el[1], hand[0], hand[1], 2.6, 2.2)),
               HURLER_SKIN, bias=bias)
        r.part(ell(hand[0], hand[1], 2.4, 2.2), HURLER_SKIN, bias=bias)
        return hand

    limb_arm(far, 0.35, False)
    leg(1, 0.32)
    r.part(union(ell(body[0], body[1], 9.5, 9.0), ell(hip[0], hip[1] - 1, 7.5, 5.5)), HURLER_SKIN)
    r.part(ell(body[0] + 3.5, body[1] + 3, 5.5, 5.5), HURLER_SKIN, edge=False, bias=-0.12)   # belly
    # volcanic shell on the back: jagged rocks with glowing seams
    b = body
    shell = poly([rot(p, b, lean * 0.5) for p in ((b[0] - 11, b[1] + 5), (b[0] - 12, b[1] - 2), (b[0] - 9, b[1] - 9),
                                                   (b[0] - 6, b[1] - 10), (b[0] - 4, b[1] - 14), (b[0] - 1, b[1] - 11),
                                                   (b[0] + 2, b[1] - 13), (b[0] + 4, b[1] - 9), (b[0] + 0.5, b[1] - 6),
                                                   (b[0] - 5, b[1] - 2), (b[0] - 6, b[1] + 4))], bulge=0.7)
    r.part(shell, ROCK)
    for (x0, y0, x1, y1) in ((-9, -6, -5, -3), (-5, -3, -6, 2), (-3, -10, -1, -6), (-9, 1, -6, 2)):
        p0, p1 = rot((b[0] + x0, b[1] + y0), b, lean * 0.5), rot((b[0] + x1, b[1] + y1), b, lean * 0.5)
        r.stroke(p0[0], p0[1], p1[0], p1[1], EMBER[2] if (x0 + phase * 8) % 3 > 1 else EMBER[3])
    r.glow(b[0] - 6, b[1] - 3, 6, EMBER[2], 0.25)
    leg(0, 0.0)
    # head: wide, low, a mouth full of fire, two little horns curving forward
    for hx, bias in ((-2.5, 0.3), (0.5, 0.0)):
        hb = (head[0] + hx, head[1] - 4)
        r.part(chain(bezier(hb, (hb[0] - 0.5, hb[1] - 3.5), (hb[0] + 2.2, hb[1] - 5), 4), 1.3, 0.3), HORN, bias=bias)
    r.part(ell(head[0], head[1], 5.8, 4.8), HURLER_SKIN)
    my = head[1] + 1.8
    mo = maw * 2.2
    r.part(ell(head[0] + 2.8, my + mo * 0.5, 3.0, 0.8 + mo * 0.6), [EMBER[1], EMBER[2], EMBER[3]], edge=False, flat=0.3)
    r.stroke(head[0] + 0.0, my - 0.4, head[0] + 5.8, my - 0.4, OUTLINE)
    r.dot(head[0] + 2.0, my + 0.2, SKULL[1])
    r.dot(head[0] + 4.2, my + 0.2, SKULL[1])
    r.glow(head[0] + 3, my + 1, 4 + maw * 2, EMBER[2], 0.35 + maw * 0.3)
    ey = head[1] - 1.6
    if blink:
        r.stroke(head[0] + 0.5, ey, head[0] + 4.5, ey, OUTLINE)
    else:
        for ex in (head[0] + 0.6, head[0] + 3.4):
            r.dot(ex, ey, hexc('#fff3b0'))
            r.dot(ex + 1, ey, OUTLINE)
        r.stroke(head[0] - 0.5, ey - 1.2, head[0] + 5, ey - 1.6, OUTLINE)
    hand = limb_arm(arm, 0.0, True)
    if ball > 0:
        a1, a2 = arm
        bc = polar(hand, 1.5 + ball * 0.6, a2)
        magma_ball(r, bc[0], bc[1], ball, phase)
    return r.render()


def hurler_anims():
    idle = [draw_hurler(bob=math.sin(i / 4 * 6.28) * 0.6, arm=(1.0, -0.25 + math.sin(i / 4 * 6.28) * 0.06), phase=i / 4,
                        maw=0.3 + 0.15 * math.sin(i / 4 * 6.28), blink=(i == 1)) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        a = math.sin(p) * 0.7
        walk.append(draw_hurler(bob=-abs(math.cos(p)) * 1.2 + 0.6, lean=0.06, legs=(a, -a),
                                lift=(max(0, -math.cos(p)) * 2.5, max(0, math.cos(p)) * 2.5),
                                arm=(1.0 - a * 0.1, -0.25), far=(1.3 + a * 0.4, 1.5 + a * 0.3), phase=i / 6, maw=0.3))
    throw = [
        draw_hurler(lean=-0.1, arm=(-2.3, -2.0), far=(0.6, 0.2), maw=0.6, phase=0.1),
        draw_hurler(lean=-0.2, bob=0.5, arm=(-2.7, -2.7), far=(0.2, -0.3), maw=0.9, phase=0.3),
        draw_hurler(lean=0.12, arm=(-1.2, -0.5), far=(1.5, 1.8), maw=1.0, phase=0.5),
        draw_hurler(lean=0.22, bob=0.8, arm=(0.4, 0.6), far=(1.7, 2.0), ball=0, maw=0.7),
        draw_hurler(lean=0.08, bob=0.3, arm=(0.9, -0.05), far=(1.4, 1.6), ball=1.4, maw=0.4),
    ]
    air = [draw_hurler(bob=-1, legs=(0.3, -0.3), lift=(1.5, 1), arm=(0.7, -0.4), far=(0.9, 0.6), maw=0.6)]
    return dict(idle=idle, walk=walk, throw=throw, air=air)


# =====================================================================================================
# Cinderbat: coal-black bat with ember-veined wings, flies in swarms and dives at you
# =====================================================================================================
COAL = ramp('#7a6666', '#4e3e3e', '#332828', '#211919', '#120d0d')
MEMBRANE = ramp('#e0643e', '#ad3e2a', '#762822', '#4c1818', '#2c0c0c')
BAT_W, BAT_H = 48, 44


def draw_bat(flap=0.0, folded=False, mouth=0.0, phase=0.0):
    """flap 1 = wings up .. -1 = wings down; folded: wings swept back tight (dive)."""
    r = Rig(BAT_W, BAT_H, rim=0.4)
    body = (23.0, 23.0)
    head = (27.5, 20.5)

    def wing(root, bias, far):
        if folded:
            phi, ln = (-2.75 if far else 2.85), 11.0
        else:
            phi = -2.0 - (1 - flap) / 2 * (2 * math.pi - 4.2)
            ln = 20 * (0.45 + 0.55 * abs(math.sin(phi)))
            if far:
                phi += 0.25
                ln *= 0.85
        s = -1 if math.sin(phi) < 0 else 1                     # the side the membrane fans out to (backwards)
        el = polar(root, ln * 0.45, phi - s * 0.15)
        f1 = polar(el, ln * 0.62, phi + s * 0.15)
        f2 = polar(el, ln * 0.55, phi + s * 0.75)
        f3 = polar(el, ln * 0.42, phi + s * 1.35)
        back = (root[0] - 3.5, root[1] + 1.5)
        mid = lambda a, b, k=0.25: ((a[0] + b[0]) / 2 + (root[0] - (a[0] + b[0]) / 2) * k,
                                    (a[1] + b[1]) / 2 + (root[1] - (a[1] + b[1]) / 2) * k)
        pts = [root, el, f1, mid(f1, f2), f2, mid(f2, f3), f3, mid(f3, back, 0.15), back]
        r.part(poly(pts, 0.25), MEMBRANE, bias=bias)
        bone = COAL[1] if not far else COAL[3]
        for f in (f1, f2, f3):
            r.stroke(el[0], el[1], f[0], f[1], bone)
        r.stroke(root[0], root[1], el[0], el[1], bone)
        if not far:                                             # smouldering trailing edge
            for a, b in ((f1, f2), (f2, f3)):
                m = mid(a, b)
                r.stroke(a[0], a[1], m[0], m[1], EMBER[3])
                r.stroke(m[0], m[1], b[0], b[1], EMBER[3])
                r.dot(m[0], m[1], EMBER[1])
                r.glow(m[0], m[1], 2.5, EMBER[2], 0.4)
            r.dot(f1[0], f1[1], EMBER[1])

    wing((body[0] + 0.5, body[1] - 2.5), 0.35, True)
    r.part(union(ell(body[0], body[1], 4.6, 4.0, -0.2), ell(head[0], head[1], 3.6, 3.3)), COAL)
    # ears
    for ex, bias in ((-1.6, 0.3), (0.6, 0.0)):
        r.part(poly([(head[0] + ex - 1.2, head[1] - 2), (head[0] + ex - 0.6, head[1] - 7), (head[0] + ex + 1.3, head[1] - 2.2)], 0.3),
               COAL, bias=bias)
    # feet tucked under
    r.dot(body[0] - 1, body[1] + 4.5, COAL[2])
    r.dot(body[0] + 1, body[1] + 4.5, COAL[2])
    # eyes and fangs
    r.dot(head[0] + 1.0, head[1] - 0.5, EMBER[1])
    r.dot(head[0] + 2.6, head[1] - 0.5, EMBER[1])
    r.glow(head[0] + 1.8, head[1] - 0.5, 3.2, EMBER[2], 0.55)
    r.dot(head[0] + 3.6, head[1] + 0.8, COAL[3])
    if mouth > 0.3:
        r.stroke(head[0] + 1.2, head[1] + 2.0, head[0] + 3.2, head[1] + 2.0, hexc('#3a0a0a'))
    r.dot(head[0] + 1.4, head[1] + 2.0 + (1 if mouth > 0.3 else 0), SKULL[0])
    r.dot(head[0] + 2.8, head[1] + 2.0 + (1 if mouth > 0.3 else 0), SKULL[0])
    wing((body[0] - 0.5, body[1] - 1.5), 0.0, False)
    return r.render()


def bat_anims():
    fly = [draw_bat(flap=f, phase=i / 4) for i, f in enumerate((1.0, 0.2, -1.0, 0.0))]
    dive = [draw_bat(folded=True, mouth=1.0), draw_bat(folded=True, mouth=1.0, flap=0.2)]
    return dict(fly=fly, dive=dive)


# =====================================================================================================
# Hellfire Cultist: horned demon in a violet robe, casts homing hellfire and teleports away
# =====================================================================================================
ROBE = ramp('#9a78b8', '#6a4c88', '#463262', '#2c1f42', '#191127')
ASH = ramp('#cfc6bc', '#9c938a', '#6e6660', '#4a4440', '#2b2724')
CULT_W, CULT_H, CULT_FEET = 48, 60, 58


def draw_cultist(bob=0.0, sway=0.0, near=(1.05, 0.0), far=(1.15, 0.0), orb=0.0, steps=(0.0, 0.0), lean=0.0,
                 phase=0.0, eye_hot=0.0):
    """near / far: (angle, reach) of the sleeves, reach 0..1 extends the hand out; orb: radius of the hellfire
    held between the hands; steps: shoe peek for the walk."""
    r = Rig(CULT_W, CULT_H, rim=0.45)
    top = (25.0 + lean, 24 + bob)
    head = (26.5 + lean * 1.4, 18 + bob)

    def sleeve(angle, reach, bias, root):
        ln = 8.5 + reach * 3
        hand = polar(root, ln, angle)
        r.part(limb(root[0], root[1], hand[0] - math.cos(angle) * 1.5, hand[1] - math.sin(angle) * 1.5, 2.6, 3.6), ROBE, bias=bias)
        r.part(ell(hand[0], hand[1], 1.7, 1.7), ASH, bias=bias)
        for k in (-0.5, 0.4):
            tip = polar(hand, 2.0, angle + k)
            r.dot(tip[0], tip[1], ASH[2] if bias == 0 else ASH[3])
        return hand

    far_hand = sleeve(far[0], far[1], 0.35, (top[0] - 2.5, top[1] + 1.5))
    # shoes under the hem
    for k, (dx, s) in enumerate(((-3.5, steps[1]), (3.0, steps[0]))):
        fx = top[0] + dx + s * 2.5
        r.part(poly([(fx - 2, CULT_FEET - 0.2), (fx - 1.5, CULT_FEET - 2.5), (fx + 2.5, CULT_FEET - 2.0), (fx + 4, CULT_FEET - 0.2)], 0.2),
               [hexc('#3a2a20'), hexc('#24180f')], bias=0.3 if k == 0 else 0)
    # robe: narrow shoulders flaring to a wavy hem
    hem = []
    for k in range(9):
        t = k / 8
        x = top[0] - 11 + 22 * t + sway * (1 - abs(t - 0.5))
        y = CULT_FEET - 2.0 + math.sin(t * 9 + phase * 6.28) * 0.9
        hem.append((x, y))
    robe = poly([(top[0] - 5, top[1]), (top[0] + 5, top[1])] + [hem[-1]] + list(reversed(hem[:-1])), bulge=0.55)
    r.part(robe, ROBE)
    # gold trim down the front and round the hem, rope belt
    r.stroke(top[0] + 4, top[1] + 1, hem[-1][0] - 1.5, hem[-1][1] - 0.5, GOLD[2])
    for a, b in zip(hem, hem[1:]):
        r.stroke(a[0], a[1] - 0.5, b[0], b[1] - 0.5, GOLD[3])
    by = top[1] + 11
    r.stroke(top[0] - 7, by + 0.8, top[0] + 7, by - 0.4, GOLD[1])
    r.stroke(top[0] + 4.5, by, top[0] + 3.8 + sway * 0.3, by + 6, GOLD[2])
    r.dot(top[0] + 3.8 + sway * 0.3, by + 6.5, GOLD[0])
    # rune glowing on the chest
    rx, ry = top[0] + 1, top[1] + 5
    r.dots([(rx, ry), (rx + 1, ry + 1), (rx, ry + 2), (rx - 1, ry + 1), (rx, ry + 3)], FEL[2])
    r.glow(rx + 0.5, ry + 1.5, 3, FEL[2], 0.3 + eye_hot * 0.3)
    # goat horns through the hood, hood with a point at the back, dark face
    for hx, bias in ((-3, 0.35), (0.5, 0.0)):
        hb = (head[0] + hx, head[1] - 4)
        r.part(chain(bezier(hb, (hb[0] - 1, hb[1] - 5), (hb[0] - 5.5, hb[1] - 7), 5), 1.6, 0.35), HORN, bias=bias)
    r.part(union(ell(head[0], head[1], 5.8, 6.2), poly([(head[0] - 3, head[1] - 4.5), (head[0] - 9, head[1] + 3.5),
                                                        (head[0] - 2, head[1] + 5)], 0.4)), ROBE)
    r.part(ell(head[0] + 2.6, head[1] + 0.8, 2.9, 3.8), [hexc('#120a18'), hexc('#0a050e')], edge=False)
    eye_c = FEL[0] if eye_hot > 0.5 else FEL[1]
    r.dot(head[0] + 2.0, head[1] - 0.2, eye_c)
    r.dot(head[0] + 4.2, head[1] - 0.2, eye_c)
    r.glow(head[0] + 3.1, head[1] - 0.2, 4 + eye_hot * 2, FEL[2], 0.5 + eye_hot * 0.3)
    r.stroke(head[0] + 4.8, head[1] - 4, head[0] + 4.8, head[1] + 4.5, GOLD[3])          # hood rim
    near_hand = sleeve(near[0], near[1], 0.0, (top[0] + 2.0, top[1] + 2))
    if orb > 0:
        ox = (near_hand[0] + far_hand[0]) / 2 + 1.5
        oy = min(near_hand[1], far_hand[1]) - orb * 0.6
        r.part(ell(ox, oy, orb, orb * 1.1), [FEL[0], FEL[1], FEL[2], FEL[3]], edge=False)
        flame(r, ox, oy - orb * 0.6, orb * 1.6, orb * 0.55, phase, rmp=FEL)
        r.glow(ox, oy, orb + 5, FEL[2], 0.6)
    return r.render()


def cultist_anims():
    idle = [draw_cultist(bob=math.sin(i / 4 * 6.28) * 0.6, sway=math.sin(i / 4 * 6.28) * 0.6, phase=i / 4,
                         near=(1.15, 0.1), far=(1.2, 0.15)) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        walk.append(draw_cultist(bob=-abs(math.cos(p)) * 0.8 + 0.4, sway=-math.sin(p) * 1.6, lean=0.6,
                                 steps=(max(0, math.sin(p)), max(0, -math.sin(p))), phase=i / 6,
                                 near=(1.2 - math.sin(p) * 0.15, 0.0), far=(1.25 + math.sin(p) * 0.15, 0.0)))
    cast = [
        draw_cultist(near=(-0.2, 0.0), far=(-0.1, 0.0), orb=1.5, phase=0.1, eye_hot=0.6),
        draw_cultist(bob=-0.5, near=(-1.1, 0.2), far=(-1.0, 0.2), orb=3.0, phase=0.3, eye_hot=1.0),
        draw_cultist(bob=-0.8, near=(-1.25, 0.35), far=(-1.15, 0.3), orb=4.2, phase=0.55, eye_hot=1.0),
        draw_cultist(lean=1.0, near=(-0.05, 1.0), far=(0.1, 0.9), orb=0, phase=0.7, eye_hot=1.0, sway=-1.5),
        draw_cultist(lean=0.4, near=(0.7, 0.3), far=(0.8, 0.3), orb=0, phase=0.9, eye_hot=0.3, sway=-0.6),
    ]
    air = [draw_cultist(bob=-1, sway=1.5, near=(0.4, 0.1), far=(0.6, 0.1), phase=0.4)]
    return dict(idle=idle, walk=walk, cast=cast, air=air)


# =====================================================================================================
# projectiles & effects
# =====================================================================================================
def magma_ball_frames():
    frames = []
    for i in range(4):
        r = Rig(14, 14, rim=0)
        magma_ball(r, 7, 7, 4.3, i / 4)
        frames.append(r.render())
    return frames


def hellfire_orb_frames():
    """Green hellfire orb flying right, its flames trailing to the left."""
    frames = []
    for i in range(4):
        r = Rig(16, 16, rim=0)
        ph = i / 4
        for k in range(4):                                       # flame tail
            t = k / 3
            r.part(ell(8 - 2.2 - t * 4.5, 8 + math.sin(ph * 6.28 + k) * 0.9, 3.0 - t * 2.0, 2.4 - t * 1.4),
                   [FEL[min(4, 2 + k)]], edge=False, flat=0)
        r.part(ell(9, 8, 3.6, 3.6), [FEL[0], FEL[1], FEL[2], FEL[3]], edge=False)
        r.dot(9.5, 7.5, hexc('#ffffff'))
        r.glow(9, 8, 7, FEL[2], 0.6)
        frames.append(r.render())
    return frames


def shockwave_frames():
    """A crest of rock shards and dust ploughing through the ground to the right, bottom-anchored.
    80x48: twice the size and pixel detail of the first version, to go with the bigger Brute."""
    W, H = 80, 48
    DUST_R = ramp('#a88a7c', '#86685c', '#664c42', '#48332c', '#2e1f1a')
    frames = []
    for i in range(4):
        rng = random.Random(300 + i)
        r = Rig(W, H, rim=0)
        # layered dust billowing behind the crest, bigger and darker toward the back
        for k in range(9):
            t = k / 8
            cx = 6 + t * 42 + rng.uniform(-2, 2) + i * 1.2
            ry = 3.5 + t * 4.5 + rng.uniform(-1, 1.5)
            cy = H - ry - rng.uniform(0, 7) * (1 - t * 0.6)
            r.part(ell(cx, cy, ry * 1.15, ry), DUST_R, edge=True, bias=0.3 - t * 0.25,
                   line=mix(DUST_R[3], OUTLINE, 0.3))
        # molten seam torn open under the crest
        for x in range(34, 74):
            y = H - 2 + math.sin(x * 0.7 + i) * 0.6
            r.dot(x, y, EMBER[2] if (x + i) % 3 else EMBER[1])
        r.glow(60, H - 3, 18, EMBER[2], 0.5)
        # shards jutting out of the ground, tallest at the front; each with a lit and a shadow face
        for k in range(5):
            bx = 36 + k * 8 + rng.uniform(-1.5, 1.5)
            hgt = 9 + k * 5.5 + rng.uniform(-2.5, 2.5) + (i % 2) * 2
            lean = rng.uniform(-1.5, 4.5)
            wl, wr = rng.uniform(4.5, 6.5), rng.uniform(4.0, 6.0)
            tip = (bx + lean, H - hgt)
            lit = poly([(bx - wl, H), tip, (bx + lean * 0.35, H)], 0.3)
            dark_ = poly([(bx + lean * 0.35 - 0.5, H), tip, (tip[0] + 1.6, tip[1] + 2.5), (bx + wr, H)], 0.3)
            r.part(lit, ROCK, flat=0.25)
            r.part(dark_, ROCK, flat=0.7, edge=False)
            r.stroke(tip[0], tip[1], bx + lean * 0.35, H - 1, ROCK[0])           # the ridge between the faces
            c0 = (bx + lean * 0.2, H - 2)
            r.stroke(c0[0], c0[1], c0[0] + lean * 0.3, H - hgt * 0.55, EMBER[3])
            r.dot(c0[0] + lean * 0.15, H - hgt * 0.3, EMBER[1])
        # pebbles and embers thrown up and forward
        for k in range(9):
            px_, py_ = 40 + rng.uniform(0, 36), H - 12 - rng.uniform(0, 26)
            if k % 3 == 0:
                r.part(ell(px_, py_, 1.6, 1.3), ROCK, bias=0.1)
            else:
                r.dot(px_, py_, EMBER[rng.randint(0, 2)])
                r.glow(px_, py_, 2.5, EMBER[2], 0.4)
        frames.append(r.render())
    return frames


def magma_pool_frames():
    """Tileable strip of a bubbling magma puddle (32 wide, repeats along the pool), bottom-anchored."""
    W, H = 32, 16
    frames = []
    for i in range(4):
        img = new(W, H)
        px = img.load()
        for x in range(W):
            # crust surface height wobbles a little; wraps at W
            top = H - 5 + math.sin(x / W * 6.28 * 2) * 0.8 + math.sin(x / W * 6.28 * 3 + 1) * 0.5
            for y in range(int(top), H):
                d = y - top
                heat_ = 0.5 + 0.5 * math.sin(x / W * 6.28 * 4 + i * 1.57 + y * 0.7)
                if d < 1:
                    c = EMBER[1] if heat_ > 0.6 else EMBER[2]
                elif heat_ > 0.75:
                    c = EMBER[2]
                elif heat_ > 0.35:
                    c = EMBER[3]
                else:
                    c = hexc('#4a140c')
                px[x, y] = c
        # bubbles popping
        for k in range(3):
            bx = (k * 11 + i * 5) % W
            stage = (i + k) % 4
            by = H - 6 - stage
            if stage < 3:
                for dx, dy in ((0, 0), (1, 0), (0, -1), (1, -1)) if stage == 1 else ((0, 0),):
                    put(px, W, H, (bx + dx) % W, by + dy, EMBER[1])
        # low flames licking up
        for k in range(4):
            fx_ = (k * 8 + 3 + i * 2) % W
            hgt = 3 + (k + i) % 3 * 2
            for t in range(hgt):
                put(px, W, H, fx_, H - 5 - t, EMBER[min(4, 1 + t * 3 // hgt + 1)])
                if t < hgt // 2:
                    put(px, W, H, (fx_ + 1) % W, H - 5 - t, EMBER[3])
        frames.append(img)
    return frames


# =====================================================================================================
# sheet & generate
# =====================================================================================================
def all_strips():
    """name -> list of frames."""
    out = {}
    for anim, frames in imp_anims().items():
        out[f'DemonImp_{anim}'] = frames
    for anim, frames in hound_anims().items():
        out[f'DemonHound_{anim}'] = frames
    for anim, frames in brute_anims().items():
        out[f'DemonBrute_{anim}'] = frames
    for anim, frames in hurler_anims().items():
        out[f'DemonHurler_{anim}'] = frames
    for anim, frames in bat_anims().items():
        out[f'DemonBat_{anim}'] = frames
    for anim, frames in cultist_anims().items():
        out[f'DemonCultist_{anim}'] = frames
    out['MagmaBall'] = magma_ball_frames()
    out['HellfireOrb'] = hellfire_orb_frames()
    out['BruteShockwave'] = shockwave_frames()
    out['MagmaPool'] = magma_pool_frames()
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip(frames).save(f'{out_dir}/{name}.png')


def sheet(path, scale=4):
    from PIL import Image
    strips = all_strips()
    pad = 6
    rows = []
    for name, frames in strips.items():
        s = strip(frames)
        rows.append(s.resize((s.width * scale, s.height * scale), Image.NEAREST))
    w = max(r.width for r in rows) + pad * 2
    h = sum(r.height + pad for r in rows) + pad
    out = Image.new('RGBA', (w, h), (34, 14, 18, 255))
    y = pad
    for r_ in rows:
        bg = Image.new('RGBA', r_.size, (58, 22, 26, 255))
        out.paste(bg, (pad, y))
        out.paste(r_, (pad, y), r_)
        y += r_.height + pad
    out.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
