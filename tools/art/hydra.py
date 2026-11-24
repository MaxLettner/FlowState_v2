"""The Tar Hydra, boss #3 (chosen 2026-11-23 from preview_leviathan_concepts.png, concept D).
Design: vault FlowState/Tar Hydra.md.

Each head is drawn on the demon Rig from a pose (pitch, jaw, sac, heat, ...) and blended between keys. The necks are
not in the head frames: Java strings them from HydraNeck discs (frame i = radius 10 + i), HydraScute belly plates
and HydraFin spikes along a curve, and puts the head frame on the neck's end. Everything faces right and is shown at
x2 like every other demon: it is drawn at twice the size (the numbers in the drawing code) and stored at half
(HalfRig), so the shading is worked out finely but the pixels are as big as everyone else's (the user asked
for that on 2026-11-23; at first it was shown at x1).

  name               frame     anchor      strips
  HydraSpitter       150x120   (75, 70)    idle 8, rear 6, spit 6, hurt 4, death 8, regrow 10
  HydraBiter         150x120   (75, 70)    idle 8, rear 6, bite 6, stuck 8, pull 4, hurt 4, death 8, regrow 10
  HydraLure          150x120   (75, 70)    idle 8, sweep 12, hypno 8, hurt 4, death 8, regrow 10
  HydraCrowned       150x120   (75, 70)    idle 8, roar 8, spit 6, bite 6, stuck 8, pull 4, wave 8, hurt 4, death 12
                     the frame centre is the middle of the head, so the sprite flips round it; the neck joins
                     0.9 head sizes behind it
  Hydra<Head>Stump   60x70     (30, 55)    idle 8 (smoking), seared 1, frozen 1, rotted 1, crushed 1   (not the Crowned)
                                             (30, 55) is the neck's end point; the cut face sits 0.6 neck radii above it
  HydraNeck          64x64     centre      21 frames, radius 10..30, fill only
  HydraNeckEdge      64x64     centre      21 frames, the outline disc: draw every edge of a neck first, then the fills
  HydraScute         32x32     centre      11 frames, radius 4..14
  HydraFin           20x30     (10, 29)    2 frames (long, short), points up; Java turns it to the neck's back
  HydraBody          280x75    (140, 73)   idle 4 (73: the bottom of the mound)
  TarGlob            16x16     centre      4, burning tar blob (flies)
  TarFirePool        32x16     bottom      4, tile
  TarWave            120x60    (60, 59)    4, rolls right
  TarLake            64x12     top         4, surface tile
  BasaltPillar_top   56x20     (28, 0)     1
  BasaltPillar_shaft 56x16     tile        1

    python tools/art/hydra.py sheet out.png       # every strip
    python tools/art/hydra.py ingame out.png      # the heads on their necks over the lake, both phases
    python tools/art/hydra.py generate <dir>
"""
import math
import random
import sys
from PIL import Image, ImageDraw
from pixel import *
from materials import BONE, ICE, GREEN
from demons import Rig, ell, limb, chain, poly, union, bezier, dark, clamp, flame, EMBER, N4, LAVA_RIM

TAR_SKIN = ramp('#8a8698', '#46404f', '#28222d', '#17131a', '#0b080d')
FIN = ramp('#d0606a', '#962e40', '#5e1828', '#36101c', '#1a0810')
BASALT = ramp('#665858', '#463a3a', '#2e2424', '#1c1616', '#0f0b0b')
EYE = ramp('#fffbe0', '#ffe14d', '#ffb02e', '#c46a12')
GUM = ramp('#e07a7a', '#b0404c', '#7a2234', '#4a1222', '#260810')
SCUTE = dark(BONE, 0.45)
PLATE = dark(BONE, 0.12)
SMOKE = ramp('#5a5054', '#433a3e', '#30282c', '#211b1e', '#151012')
ROT = ramp('#e2ffb0', '#9be84f', '#5bb42c', '#357a1d', '#1e4612')
MAW = hexc('#2a0606')
MAW_RED = hexc('#5a120a')

# the drawing sizes (twice the stored ones)
W, H, AX, AY = 300, 240, 150, 140
SW, SH, SAX, SAY = 120, 140, 60, 110


def reduce2(img):
    """Half size: each 2x2 block becomes its most common opaque colour (transparent if fewer than two are opaque)."""
    w, h = img.width // 2, img.height // 2
    src = img.load()
    out = new(w, h)
    px = out.load()
    for y in range(h):
        for x in range(w):
            solid = [c for c in (src[2 * x, 2 * y], src[2 * x + 1, 2 * y], src[2 * x, 2 * y + 1], src[2 * x + 1, 2 * y + 1])
                     if c[3] > 0]
            if len(solid) < 2:
                continue
            px[x, y] = max(solid, key=solid.count)        # ties: the first (top left) wins
    return out


class HalfRig(Rig):
    """A Rig drawn at twice the size and stored at half: shaded and lava-rimmed at the big size, reduced by reduce2,
    then outlined and given its glows at the small size, so its pixels are as big as every other demon's."""

    def render(self):
        if self.rim > 0:
            for (x, y), (nx, ny) in self.normal.items():
                if ny > 0.15 and (x, y + 1) not in self.owner:
                    self.px[x, y] = mix(self.px[x, y], LAVA_RIM, self.rim * min(1.0, ny + 0.3))
        small = reduce2(self.img)
        half = Rig(small.width, small.height, rim=0.0)
        half.img, half.px = small, small.load()
        half.post = [(cx / 2, cy / 2, r / 2, c, k) for cx, cy, r, c, k in self.post]
        return half.render()

# per head: size (s), neck end radius, base radius used by Java
HEADS = {
    'Spitter': dict(s=34, neck=26, base=40),
    'Biter': dict(s=42, neck=32, base=48),
    'Lure': dict(s=30, neck=24, base=38),
    'Crowned': dict(s=54, neck=40, base=58),
}

DEFAULT = dict(pitch=0.0, jaw=0.08, sac=0.0, heat=0.3, eye=1.0, lure=0.0, bulb=0.8, crest=0.0, shake=(0, 0),
               scale=1.0, blob=0.0, coat=0.0, flare=0.0, phase=0.0, dust=0.0)


def neck_x(kind):
    """Where the neck joins in the frame: 0.9 head sizes behind the frame centre (the middle of the head)."""
    return AX - round(0.9 * HEADS[kind]['s'])


def pose(**kw):
    return {**DEFAULT, **kw}


# =====================================================================================================
# heads
# =====================================================================================================
class Frame:
    """Maps head-local coordinates (u forward, v down, in units of the head size) into the frame."""

    def __init__(self, P, s, neck_x):
        self.s = s * P['scale']
        self.ax, self.ay = neck_x + P['shake'][0], AY + P['shake'][1]
        self.c, self.sn = math.cos(P['pitch']), math.sin(P['pitch'])
        self.jc, self.js = math.cos(P['jaw'] * 0.9), math.sin(P['jaw'] * 0.9)
        self.pitch = P['pitch']

    def at(self, u, v):
        u, v = u * self.s, v * self.s
        return self.ax + u * self.c - v * self.sn, self.ay + u * self.sn + v * self.c

    def jaw(self, u, v):
        hu, hv = 0.1, 0.4                                # hinge
        du, dv = u - hu, v - hv
        return self.at(hu + du * self.jc - dv * self.js, hv + du * self.js + dv * self.jc)

    def poly(self, pts, bulge=0.5, jaw=False):
        f = self.jaw if jaw else self.at
        return poly([f(u, v) for u, v in pts], bulge=bulge)

    def ell(self, u, v, ru, rv, jaw=False):
        x, y = (self.jaw if jaw else self.at)(u, v)
        return ell(x, y, ru * self.s, rv * self.s, rot=self.pitch)


def upper_edge(t):
    return 0.3 + 1.7 * t, 0.33 - 0.2 * t


def lower_edge(t):
    return 0.25 + 1.65 * t, 0.37 - 0.03 * t


SNOUT = [(-0.2, -0.3), (0.4, -0.5), (1.1, -0.42), (1.85, -0.22), (2.08, -0.02), (2.05, 0.12), (1.6, 0.18),
         (0.3, 0.36), (-0.1, 0.3)]
LOWER_JAW = [(-0.1, 0.38), (0.25, 0.37), (1.9, 0.34), (1.95, 0.4), (1.85, 0.55), (1.2, 0.66), (0.4, 0.7), (0.05, 0.56)]
PLATE_PTS = [(-0.35, -0.42), (0.35, -0.6), (1.2, -0.45), (1.6, -0.28), (1.0, -0.28), (0.5, -0.3), (0.0, -0.17)]


def head_frame(kind, P):
    info = HEADS[kind]
    r = HalfRig(W, H, rim=0.5)
    F = Frame(P, info['s'], neck_x(kind))
    s = F.s
    rng = random.Random(sum(map(ord, kind)))

    if P['blob'] > 0:                                    # regrow: a tar bulb swelling out of the stump
        bx, by = F.ax + 14, F.ay - 6
        rad = info['neck'] * (0.9 + P['blob'] * 0.9)
        r.part(ell(bx, by, rad * 1.1, rad), TAR_SKIN)
        for k in range(5):
            a = k * 1.3 + P['phase'] * 6
            r.part(ell(bx + math.cos(a) * rad * 0.6, by + math.sin(a) * rad * 0.5, rad * 0.22, rad * 0.22), TAR_SKIN)
        if P['blob'] > 0.5:
            r.part(ell(bx + rad * 0.3, by - rad * 0.2, rad * 0.25, rad * 0.12), [EMBER[3]], edge=False, flat=0)
        if P['scale'] < 0.5:
            return r.render()

    # behind the skull
    if kind == 'Biter':
        for k in range(4):
            fl = 1 + 0.35 * P['crest']
            base = F.at(-0.15 + 0.28 * k, -0.42 - 0.04 * k)
            tip = F.at(-0.55 + 0.2 * k - 0.15 * fl, (-1.05 + 0.08 * k) * fl)
            mid = F.at(-0.3 + 0.24 * k, -0.85 * fl + 0.05 * k)
            r.part(chain(bezier(base, mid, tip), s * (0.14 - 0.015 * k), 1.5), BONE)
    if kind == 'Crowned':
        for k in range(7):
            a = -2.6 + k * 0.32
            fl = 1 + 0.3 * P['crest']
            ln = (0.9 - abs(k - 3) * 0.12) * fl
            base = F.at(0.25 + math.cos(a) * 0.45, -0.1 + math.sin(a) * 0.4)
            tip = F.at(0.25 + math.cos(a) * (0.45 + ln), -0.1 + math.sin(a) * (0.4 + ln))
            r.part(chain([base, tip], s * 0.1, 1.5), BONE if k % 2 == 0 else PLATE)

    # mouth: dark gullet, ember throat, tongue
    if P['jaw'] > 0.04:
        ups = [F.at(*upper_edge(t / 8)) for t in range(9)]
        lows = [F.jaw(*lower_edge(t / 8)) for t in range(9)]
        mouth = poly(ups + lows[::-1], bulge=0.0)
        r.part(mouth, [MAW], edge=False, flat=0)
        heat = P['heat']
        if heat > 0.05:
            c = F.at(0.35, 0.37)
            gr = s * (0.2 + 0.25 * heat)
            core = {p: n for p, n in ell(c[0], c[1], gr * 1.4, gr, rot=F.pitch).items() if p in mouth}
            r.part(core, [EMBER[3] if heat > 0.5 else MAW_RED], edge=False, flat=0)
            hot = {p: n for p, n in ell(c[0], c[1], gr * 0.7, gr * 0.5, rot=F.pitch).items() if p in mouth}
            r.part(hot, [EMBER[1] if heat > 0.6 else EMBER[3]], edge=False, flat=0)
        tongue = [F.jaw(0.2, 0.42), F.jaw(0.8, 0.36), F.jaw(1.2, 0.33)]
        r.part({p: n for p, n in chain(tongue, s * 0.1, s * 0.05).items() if p in mouth}, GUM)

    # lower jaw and its teeth
    jaw_pts = LOWER_JAW
    if kind == 'Biter':
        jaw_pts = [(u, v + (0.08 if v > 0.45 else 0)) for u, v in LOWER_JAW]
    r.part(F.poly(jaw_pts, jaw=True), TAR_SKIN)
    n_low = 12 if kind == 'Lure' else 8
    for i in range(n_low):
        t = (i + 0.6) / (n_low + 0.4)
        bu, bv = lower_edge(t)
        ln = 0.16 if kind != 'Lure' else 0.2
        r.part(tooth(F.jaw(bu, bv + 0.03), F.jaw(bu + 0.03, bv - ln), s * (0.065 if kind != 'Lure' else 0.04)), BONE)
    if kind in ('Biter', 'Crowned'):                     # croc tusks outside the snout
        for t in (0.72, 0.9):
            bu, bv = lower_edge(t)
            r.part(tooth(F.jaw(bu, bv + 0.05), F.jaw(bu + 0.06, bv - 0.36), s * 0.1), BONE)

    # spitter's ember throat sac under the jaw
    if kind in ('Spitter', 'Crowned'):
        k = 0.75 + 0.55 * P['sac']
        r.part(F.ell(0.6, 0.7, 0.5 * k, 0.3 * k, jaw=True), dark(TAR_SKIN, 0.1))
        glow = [EMBER[4], EMBER[3], EMBER[2], EMBER[1]][min(3, int(P['sac'] * 3.5))]
        r.part(F.ell(0.66, 0.76, 0.3 * k, 0.15 * k, jaw=True), [glow], edge=False, flat=0)    # ember showing through thin skin
        if P['sac'] > 0.6:
            r.part(F.ell(0.7, 0.78, 0.14 * k, 0.07 * k, jaw=True), [EMBER[0]], edge=False, flat=0)
        if P['sac'] > 0.3:
            r.glow(*F.jaw(0.6, 0.95), s * 0.5 * P['sac'], EMBER[2], 0.3)

    # skull and snout
    r.part(union(F.ell(0.3, -0.02, 0.72, 0.5), F.poly(SNOUT)), TAR_SKIN)

    # upper teeth hang into the mouth
    n_up = 13 if kind == 'Lure' else 9
    for i in range(n_up):
        t = (i + 0.5) / n_up
        bu, bv = upper_edge(t)
        ln = 0.17 + (0.12 if kind in ('Biter', 'Crowned') and i == n_up - 3 else 0) + (0.06 if kind == 'Lure' else 0)
        r.part(tooth(F.at(bu, bv - 0.04), F.at(bu + 0.02, bv + ln), s * (0.07 if kind != 'Lure' else 0.045)), BONE)

    # bone plate, scars / ember cracks, nostril
    r.part(F.poly(PLATE_PTS), PLATE)
    for k in range(3):
        a, b = F.at(0.0 + k * 0.35, -0.48 + k * 0.05), F.at(0.15 + k * 0.35, -0.36 + k * 0.05)
        r.stroke(*a, *b, PLATE[3])
    r.part(F.ell(1.92, -0.08, 0.07, 0.045), dark(TAR_SKIN, 0.4))
    if kind in ('Spitter', 'Crowned'):
        heat = P['heat'] if kind == 'Spitter' else 0.4 + P['heat'] * 0.6
        col = EMBER[1] if heat > 0.7 else EMBER[3]
        for path in (((0.2, 0.05), (0.55, 0.15), (0.8, 0.08), (1.1, 0.16)), ((0.0, -0.1), (0.25, -0.15), (0.4, -0.05)),
                     ((1.2, -0.12), (1.45, -0.05), (1.7, -0.1))):
            pts = [F.at(u, v) for u, v in path]
            for a, b in zip(pts, pts[1:]):
                r.stroke(*a, *b, col)
    if kind == 'Biter':
        for a, b in (((0.6, -0.1), (0.95, 0.12)), ((0.7, -0.15), (1.05, 0.08))):
            r.stroke(*F.at(*a), *F.at(*b), TAR_SKIN[1])

    # eyes
    eyes = [(0.95, -0.24)] + ([(0.65, -0.3), (1.2, -0.3)] if kind == 'Crowned' else [])
    for i, (eu, ev) in enumerate(eyes):
        er = 0.09 if i == 0 else 0.06
        x, y = F.at(eu, ev)
        r.part(ell(x, y, er * s + 2, er * s * 0.75 + 2, rot=F.pitch), dark(TAR_SKIN, 0.3))
        if P['eye'] > 0.15:
            open_ = P['eye']
            r.part(ell(x, y, er * s, er * s * 0.7 * open_ + 0.6, rot=F.pitch), EYE)
            r.stroke(x + 1, y - er * s * 0.5 * open_, x + 1, y + er * s * 0.5 * open_, OUTLINE)
            r.glow(x, y, er * s * 2.8, EMBER[2], 0.45 * open_)
        elif P['eye'] > -0.5:
            r.stroke(x - er * s, y, x + er * s, y, EMBER[4])
        else:
            r.part(ell(x, y, er * s * 0.6, er * s * 0.4), [hexc('#3a1010')], edge=False, flat=0)
        r.stroke(*F.at(eu - 0.13, ev - 0.11), *F.at(eu + 0.14, ev - 0.03), PLATE[2])     # brow

    # lure: stalk from the brow, ember bulb hanging ahead of the mouth
    if kind == 'Lure':
        sw = P['lure']
        root = F.at(0.55, -0.48)
        top = F.at(1.3 + sw * 0.4, -1.75 + abs(sw) * 0.2)
        tip = F.at(2.35 + sw * 0.9, -1.25 + abs(sw) * 0.35)
        r.part(chain(bezier(root, top, tip, 12), s * 0.08, s * 0.045), TAR_SKIN)
        br = s * (0.17 + 0.05 * (P['bulb'] - 0.8))
        bulb = (tip[0], tip[1] + br * 0.9)
        r.part(ell(bulb[0], bulb[1], br, br * 1.08), EMBER)
        r.dot(bulb[0] - br * 0.3, bulb[1] - br * 0.4, EMBER[0])
        r.glow(bulb[0], bulb[1], br * (2.5 + 2.5 * P['bulb']), EMBER[1], clamp(0.35 + 0.35 * P['bulb'], 0, 0.85))

    # muzzle flare, ember drool
    if P['flare'] > 0:
        mx, my = F.at(2.0, 0.2)
        for k in range(4):
            a = F.pitch + (k - 1.5) * 0.3
            ln = s * (0.6 + 0.5 * P['flare']) * (1 - abs(k - 1.5) * 0.25)
            pts = [(mx, my), (mx + math.cos(a) * ln * 0.5, my + math.sin(a) * ln * 0.5 - 3), (mx + math.cos(a) * ln, my + math.sin(a) * ln)]
            r.part(chain(pts, s * 0.12 * P['flare'], 1), [EMBER[1 + k % 2]], edge=False, flat=0)
        r.glow(mx + s * 0.9, my, s * 0.7, EMBER[1], 0.6)
    if kind in ('Spitter', 'Crowned') and P['heat'] > 0.25:
        for k, u in enumerate((1.3, 1.65)):
            ln = s * (0.2 + 0.25 * ((P['phase'] * 2 + k * 0.5) % 1))
            x, y = F.jaw(u, 0.5)
            r.part(chain([(x, y), (x, y + ln)], 2.2, 1.2), [EMBER[2]], edge=False, flat=0)
            r.part(ell(x, y + ln + 2, 2.2, 2.8), [EMBER[1]], edge=False, flat=0)

    # tar coating while regrowing: drips off the new head
    if P['coat'] > 0:
        for k in range(6):
            u = 0.1 + k * 0.33
            x, y = F.at(u, 0.2 if k % 2 else -0.4)
            ln = s * 0.5 * P['coat'] * (0.6 + 0.4 * ((k * 7) % 3) / 2)
            r.part(chain([(x, y), (x, y + ln)], 3, 1.5), TAR_SKIN)

    # stuck biter: grit kicked up round the teeth
    if P['dust'] > 0:
        for k in range(10):
            x, y = F.at(1.0 + rng.uniform(0, 1.1), 0.55 + rng.uniform(-0.1, 0.2))
            r.dot(x + rng.uniform(-6, 6), y + rng.uniform(-4, 6) - P['dust'] * 6, BASALT[rng.randint(0, 2)])
    return r.render()


def tooth(base, tip, width):
    dx, dy = tip[0] - base[0], tip[1] - base[1]
    ln = math.hypot(dx, dy) or 1
    nx, ny = -dy / ln * width / 2, dx / ln * width / 2
    return poly([(base[0] + nx, base[1] + ny), tip, (base[0] - nx, base[1] - ny)], bulge=0.4)


# =====================================================================================================
# animation keys
# =====================================================================================================
NUM = ('pitch', 'jaw', 'sac', 'heat', 'eye', 'lure', 'bulb', 'crest', 'scale', 'blob', 'coat', 'flare', 'dust')


def blend(a, b, t):
    t = t * t * (3 - 2 * t)
    out = dict(b if t >= 0.5 else a)
    for k in NUM:
        out[k] = a[k] + (b[k] - a[k]) * t
    out['shake'] = tuple(a['shake'][i] + (b['shake'][i] - a['shake'][i]) * t for i in range(2))
    out['phase'] = a['phase'] + (b['phase'] - a['phase']) * t
    return out


def keys_strip(keys, frames):
    n = len(keys)
    out = []
    for i in range(frames):
        kp = i * (n - 1) / (frames - 1)
        k = min(n - 2, int(kp))
        P = blend(keys[k], keys[k + 1], kp - k)
        P['phase'] = i / frames
        out.append(P)
    return out


def loop(fn, n):
    return [fn(i / n) for i in range(n)]


def idle(t, **kw):
    s = math.sin(t * math.tau)
    return pose(pitch=0.04 * s + kw.pop('pitch', 0.0), jaw=0.1 + 0.05 * math.sin(t * math.tau + 1),
                heat=0.3 + 0.12 * s, lure=0.35 * math.sin(t * math.tau), bulb=0.8 + 0.15 * s, phase=t,
                shake=(0, round(1.5 * s)), **kw)


def hurt():
    return keys_strip([pose(pitch=0.0), pose(pitch=-0.3, jaw=0.5, eye=0.3, heat=0.1), pose(pitch=-0.2, jaw=0.3, eye=0.6),
                       pose(pitch=0.0)], 4)


def death(n=8):
    return keys_strip([pose(), pose(pitch=-0.35, jaw=0.8, eye=0.4, crest=0.5), pose(pitch=0.3, jaw=0.6, eye=0.0, heat=0.1),
                       pose(pitch=0.8, jaw=0.7, eye=-1, heat=0.0, crest=-0.2)], n)


def regrow():
    return keys_strip([pose(blob=0.05, scale=0.3, eye=0, heat=0), pose(blob=0.6, scale=0.3, eye=0, heat=0),
                       pose(blob=1.0, scale=0.45, eye=0, heat=0), pose(blob=0.6, scale=0.7, coat=1.0, eye=0, jaw=0.4, heat=0.2),
                       pose(blob=0.0, scale=0.9, coat=0.6, eye=0.5, jaw=0.6, pitch=-0.3, heat=0.3),
                       pose(scale=1.0, coat=0.0, eye=1.0, jaw=0.1, heat=0.3)], 10)


def head_poses(kind):
    out = {'idle': loop(idle, 8), 'hurt': hurt()}
    if kind == 'Spitter':
        out['rear'] = keys_strip([pose(), pose(pitch=-0.3, jaw=0.25, sac=0.6, heat=0.7), pose(pitch=-0.45, jaw=0.35, sac=1.0, heat=1.0)], 6)
        out['spit'] = keys_strip([pose(pitch=-0.45, jaw=0.35, sac=1.0, heat=1.0), pose(pitch=0.05, jaw=1.0, sac=0.2, heat=1.0, flare=1.0),
                                  pose(pitch=0.1, jaw=0.8, sac=0.0, heat=0.7, flare=0.3), pose(pitch=0.0, jaw=0.2, heat=0.4)], 6)
    if kind == 'Biter':
        out['rear'] = keys_strip([pose(), pose(pitch=-0.35, jaw=0.5, crest=0.6), pose(pitch=-0.5, jaw=0.95, crest=1.0)], 6)
        out['bite'] = keys_strip([pose(pitch=-0.5, jaw=0.95, crest=1.0), pose(pitch=0.25, jaw=1.0, crest=0.8),
                                  pose(pitch=0.45, jaw=0.0, crest=0.3, shake=(2, 2)), pose(pitch=0.45, jaw=0.05)], 6)
        out['stuck'] = [pose(pitch=0.45 + 0.03 * (i % 2), jaw=0.08, eye=0.55, crest=0.4, dust=(i % 4) / 3, phase=i / 8,
                             shake=((-2, 1), (2, -1), (-1, 2), (2, 0))[i % 4]) for i in range(8)]
        out['pull'] = keys_strip([pose(pitch=0.45, jaw=0.08, eye=0.55), pose(pitch=0.2, jaw=0.4, crest=0.8, dust=1.0),
                                  pose(pitch=-0.2, jaw=0.3, crest=0.5)], 4)
    if kind == 'Lure':
        out['sweep'] = [pose(pitch=0.08, jaw=0.25, lure=0.9 * math.sin(i / 12 * math.tau), bulb=1.2, phase=i / 12) for i in range(12)]
        out['hypno'] = [pose(pitch=0.12, jaw=0.55 + 0.1 * math.sin(i / 8 * math.tau), lure=0.1 * math.sin(i / 8 * math.tau),
                             bulb=0.9 + 0.6 * (0.5 + 0.5 * math.sin(i / 8 * math.tau)), phase=i / 8) for i in range(8)]
    if kind == 'Crowned':
        out['roar'] = keys_strip([pose(), pose(pitch=-0.4, jaw=0.6, crest=0.6, heat=0.7), pose(pitch=-0.6, jaw=1.0, crest=1.0, heat=1.0,
                                  shake=(1, -1)), pose(pitch=-0.55, jaw=1.0, crest=1.0, heat=1.0, shake=(-1, 1)), pose(pitch=-0.2, jaw=0.3)], 8)
        out['spit'] = keys_strip([pose(pitch=-0.4, jaw=0.35, sac=1.0, heat=1.0), pose(pitch=0.05, jaw=1.0, sac=0.2, heat=1.0, flare=1.0),
                                  pose(pitch=0.1, jaw=0.7, heat=0.6, flare=0.3), pose(pitch=0.0, jaw=0.2)], 6)
        out['bite'] = keys_strip([pose(pitch=-0.5, jaw=0.95, crest=1.0), pose(pitch=0.25, jaw=1.0, crest=0.8),
                                  pose(pitch=0.45, jaw=0.0, crest=0.3, shake=(2, 2)), pose(pitch=0.35, jaw=0.1)], 6)
        out['stuck'] = [pose(pitch=0.4 + 0.03 * (i % 2), jaw=0.08, eye=0.55, crest=0.4, heat=0.6, dust=(i % 4) / 3, phase=i / 8,
                             shake=((-2, 1), (2, -1), (-1, 2), (2, 0))[i % 4]) for i in range(8)]
        out['pull'] = keys_strip([pose(pitch=0.4, jaw=0.08, eye=0.55, heat=0.6), pose(pitch=0.2, jaw=0.4, crest=0.8, dust=1.0),
                                  pose(pitch=-0.2, jaw=0.3, crest=0.5)], 4)
        out['wave'] = keys_strip([pose(), pose(pitch=-0.55, jaw=0.4, crest=1.0, heat=0.8), pose(pitch=-0.6, jaw=0.5, crest=1.0, heat=1.0),
                                  pose(pitch=0.7, jaw=0.2, crest=0.2, heat=0.6, shake=(0, 3)), pose(pitch=0.6, jaw=0.15)], 8)
        out['death'] = death(12)
    else:
        out['death'] = death()
        out['regrow'] = regrow()
    return out


# =====================================================================================================
# stumps: the cut neck end, smoking until it's sealed (fire, ice, poison or a heavy blow) or regrows
# =====================================================================================================
def stump_frame(kind, state, t=0.0):
    rad = HEADS[kind]['neck']
    r = HalfRig(SW, SH, rim=0.5)
    cx, top = SAX, SAY - rad * 0.6
    # the cut face, an ellipse seen from slightly above, sitting on the last neck disc
    face = ell(cx, top, rad * 0.95, rad * 0.38)
    if state == 'crushed':
        r.part(poly([(cx - rad * 1.3, top + 8), (cx - rad * 0.6, top - 6), (cx + rad * 0.7, top - 4), (cx + rad * 1.35, top + 10),
                     (cx + rad, top + 16), (cx - rad, top + 16)]), TAR_SKIN)
        for k in range(5):
            r.part(tooth((cx - rad + k * rad * 0.5, top + 2), (cx - rad * 1.1 + k * rad * 0.55, top - 12 - (k % 2) * 6), 5), BONE)
        for k in range(4):
            r.dot(cx - rad * 1.2 + k * rad * 0.8, top + 18, EMBER[3])
        return r.render()
    if state == 'seared':
        r.part(face, [hexc('#1a1010'), hexc('#120a0a')], flat=0.5)
        for k in range(6):
            a = k / 6 * math.tau + 0.4
            r.stroke(cx, top, cx + math.cos(a) * rad * 0.85, top + math.sin(a) * rad * 0.32, EMBER[3] if k % 2 else EMBER[4])
        r.part(ell(cx, top, rad * 0.22, rad * 0.1), [EMBER[2]], edge=False, flat=0)
        return r.render()
    r.part(face, GUM)
    r.part(ell(cx, top + 1, rad * 0.7, rad * 0.26), [GUM[3]], edge=False, flat=0)
    r.part(ell(cx, top, rad * 0.28, rad * 0.14), BONE)
    r.dot(cx, top, BONE[3])
    if state == 'frozen':
        cap = union(ell(cx, top - 6, rad * 1.05, rad * 0.6), *[tooth((cx - rad + k * rad * 0.4, top + 2), (cx - rad + k * rad * 0.4 + 1, top + 14 + (k % 2) * 8), 6)
                                                            for k in range(6)])
        r.part(cap, ICE)
        r.stroke(cx - rad * 0.5, top - 14, cx - rad * 0.2, top - 4, ICE[0])
        return r.render()
    if state == 'rotted':
        r.part(face, [ROT[3], ROT[4]], flat=0.4)
        for k, (u, v, s) in enumerate(((-0.4, 0.0, 5), (0.2, -0.05, 7), (0.5, 0.1, 4), (-0.1, 0.1, 3))):
            r.part(ell(cx + u * rad, top + v * rad - 2, s, s * 0.9), ROT)
        for x0 in (-0.6, 0.1, 0.7):
            r.part(chain([(cx + x0 * rad, top + 6), (cx + x0 * rad, top + 26)], 2.5, 1.2), ROT)
        return r.render()
    # smoking: ember drips and smoke rising
    for k in range(3):
        x0 = cx + (k - 1) * rad * 0.55
        ln = 8 + 14 * ((t + k * 0.37) % 1)
        r.part(chain([(x0, top + 4), (x0, top + 4 + ln)], 2.4, 1.2), [EMBER[3]], edge=False, flat=0)
    for k in range(5):
        p = (t + k / 5) % 1
        y = top - 8 - p * 70
        x = cx + math.sin(p * 5 + k) * 8
        rr = 6 + p * 10
        r.part(ell(x, y, rr, rr * 0.8), SMOKE, edge=False)
    r.glow(cx, top, rad * 1.2, EMBER[3], 0.4)
    return r.render()


def stump_strips(kind):
    return {'idle': [stump_frame(kind, 'smoke', i / 8) for i in range(8)],
            'seared': [stump_frame(kind, 'seared')], 'frozen': [stump_frame(kind, 'frozen')],
            'rotted': [stump_frame(kind, 'rotted')], 'crushed': [stump_frame(kind, 'crushed')]}


# =====================================================================================================
# neck parts (Java strings these along a curve)
# =====================================================================================================
def neck_disc(rad):
    """Fill only: overlapping discs then read as one smooth neck. The outline comes from HydraNeckEdge."""
    r = Rig(64, 64, rim=0.0)
    r.part(ell(32, 32, rad, rad), TAR_SKIN, edge=False)
    return r.img


def neck_edge(rad):
    img = new(64, 64)
    ellipse(img.load(), 64, 64, 32, 32, rad + 1.0, rad + 1.0, OUTLINE)
    return img


def scute_disc(rad):
    r = Rig(32, 32, rim=0.0)
    r.part(ell(16, 16, rad, rad), SCUTE, edge=False)
    return r.img


def fin_spike(long=True):
    r = Rig(20, 30, rim=0.0)
    h = 27 if long else 19
    r.part(poly([(2, 29), (7, 29 - h * 0.6), (11, 29 - h), (13, 29 - h * 0.5), (18, 29)], bulge=0.3), FIN)
    r.stroke(10, 29, 11, 30 - h, FIN[4])
    return r.render()


# =====================================================================================================
# body, effects, arena pieces
# =====================================================================================================
def scutes(r, pts, size, seed=1):
    rng = random.Random(seed)
    for i, (x, y) in enumerate(pts):
        s = size * rng.uniform(0.75, 1.15)
        r.part(poly([(x - s, y + s * 0.3), (x - s * 0.5, y - s * 0.7), (x + s * 0.4, y - s * 0.9),
                     (x + s, y + s * 0.2), (x, y + s * 0.6)], bulge=0.5), BASALT)
        if i:
            r.dot((x + pts[i - 1][0]) / 2, (y + pts[i - 1][1]) / 2, EMBER[3])


def body_frame(t):
    r = HalfRig(560, 150, rim=0.5)
    b = round(2 * math.sin(t * math.tau))
    r.part(union(ell(280, 140 + b, 270, 60), ell(150, 128 + b, 120, 50), ell(420, 130 + b, 110, 44)), TAR_SKIN)
    scutes(r, [(60 + i * 40, 98 + b - 14 * math.sin(i / 11 * math.pi)) for i in range(12)], 15, seed=5)
    for k in range(4):
        x = 90 + k * 120
        r.part(ell(x, 120 + b, 4, 3), [EMBER[3]], edge=False, flat=0)
    return r.render()


def tar_glob_frames():
    out = []
    for i in range(4):
        r = HalfRig(32, 32, rim=0.0)
        wob = math.sin(i / 4 * math.tau)
        r.part(ell(16, 19, 10 + wob * 0.8, 8 - wob * 0.8), TAR_SKIN)
        flame(r, 16, 14, 11 + (i % 2) * 3, 5, i / 4, lean=-0.3)
        r.dot(12, 18, EMBER[3])
        r.dot(19, 22, EMBER[3])
        out.append(r.render())
    return out


def tar_fire_pool_frames():
    out = []
    for i in range(4):
        r = Rig(32, 16, rim=0.0)
        r.part(poly([(0, 15), (0, 12), (32, 12), (32, 15)], bulge=0.0), TAR_SKIN, edge=False, flat=0.6)
        for k in range(3):
            x = (k * 11 + i * 3) % 32
            flame(r, x + 2, 12, 4 + ((i + k) % 3) * 2, 2.2, (i + k) / 4)
        out.append(r.render())
    return out


def tar_wave_frames():
    """A breaking wave of tar rolling right: long back slope, the lip curling over the front, ember foam on the crest."""
    out = []
    for i in range(4):
        r = HalfRig(240, 120, rim=0.4)
        ph = i / 4
        sx = 5 * math.sin(ph * math.tau)
        body = [(0, 118), (40, 104), (80, 76), (112, 46), (140, 26), (168 + sx, 18), (198 + sx, 24), (220 + sx, 40),
                (222 + sx, 58), (210 + sx, 64), (200 + sx, 50), (186 + sx, 46), (176, 56), (178, 78), (194, 100), (222, 118)]
        r.part(poly(body, bulge=0.5), TAR_SKIN)
        r.part(poly([(176, 56), (186 + sx, 46), (200 + sx, 50), (204 + sx, 62), (190, 70), (178, 78)], bulge=0.2),
               dark(TAR_SKIN, 0.4), edge=False)                     # the hollow under the lip
        crest = [(132, 30), (150, 20), (170 + sx, 16), (192 + sx, 20), (212 + sx, 32), (222 + sx, 50)]
        r.part(chain([(x, y - 2) for x, y in crest], 3.5, 2.5), [EMBER[3]], edge=False, flat=0)
        for k, (x, y) in enumerate(crest):
            wob = math.sin(ph * math.tau + k * 1.7) * 1.5
            r.part(ell(x, y - 3 + wob, 2.5, 2), [EMBER[1] if (k + i) % 2 else EMBER[2]], edge=False, flat=0)
        for k in range(3):                                          # drops flung off the lip
            p = (ph + k / 3) % 1
            r.part(ell(226 + sx + p * 10, 60 + p * 40, 2.5, 3), TAR_SKIN, edge=False)
        for k in range(6):
            x = 30 + k * 24 + ph * 10
            r.stroke(x, 112, x + 12, 104 - k, TAR_SKIN[1])
        out.append(r.render())
    return out


def tar_lake_frames():
    out = []
    for i in range(4):
        img = new(128, 24)
        px = img.load()
        for y in range(24):
            for x in range(128):
                px[x, y] = TAR_SKIN[3] if y > 1 else TAR_SKIN[1]
        for k in range(5):
            x0 = (k * 29 + i * 8) % 128 // 2 * 2
            y = 6 + (k * 7) % 14 // 2 * 2
            for x in range(x0, x0 + 14):
                px[x % 128, y] = px[x % 128, y + 1] = TAR_SKIN[2]          # two rows: one survives the halving
        if i % 2 == 0:
            for dx in (0, 1):
                for dy in (0, 1):
                    px[(i * 37 + 20 + dx) % 128 // 2 * 2 + dx, 4 + dy] = EMBER[3]
        out.append(reduce2(img))
    return out


def pillar_top():
    r = HalfRig(112, 40, rim=0.0)
    rng = random.Random(3)
    x = 2
    while x < 108:
        w = min(108 - x, rng.randint(22, 32))
        top = 4 + rng.randint(0, 6)
        r.part(poly([(x, top + 6), (x + w * 0.5, top), (x + w, top + 6), (x + w, 40), (x, 40)], bulge=0.5), BASALT)
        if rng.random() < 0.6:
            r.stroke(x + w * 0.4, top + 12, x + w * 0.55, top + 26, EMBER[3])
        x += w
    return r.render()


def pillar_shaft():
    img = new(112, 32)
    px = img.load()
    rng = random.Random(9)
    edges = [2, 30, 56, 84, 110]
    for y in range(32):
        for x in range(112):
            if x < 2 or x > 109:
                continue
            col = min(range(len(edges) - 1), key=lambda k: 0 if edges[k] <= x < edges[k + 1] else 1)
            lo, hi = edges[col], edges[col + 1]
            v = (x - lo) / max(1, hi - lo)
            idx = 1 if v < 0.25 else 2 if v < 0.7 else 3
            px[x, y] = BASALT[idx]
            if x in edges:
                px[x, y] = BASALT[4]
    for x in (2, 3, 108, 109):
        for y in range(32):
            px[x, y] = OUTLINE
    for _ in range(3):
        x, y = rng.randint(8, 100), rng.randint(2, 26)
        for k in range(6):
            if 0 <= y + k < 32:
                px[x + (k // 3), y + k] = px[x + 1 + (k // 3), y + k] = EMBER[3]
    return reduce2(img)


# =====================================================================================================
def all_strips():
    out = {}
    for kind in HEADS:
        for name, ps in head_poses(kind).items():
            out[f'Hydra{kind}_{name}'] = [head_frame(kind, P) for P in ps]
        if kind != 'Crowned':
            for name, frames in stump_strips(kind).items():
                out[f'Hydra{kind}Stump_{name}'] = frames
    out['HydraNeck'] = [neck_disc(10 + i) for i in range(21)]
    out['HydraNeckEdge'] = [neck_edge(10 + i) for i in range(21)]
    out['HydraScute'] = [scute_disc(4 + i) for i in range(11)]
    out['HydraFin'] = [fin_spike(True), fin_spike(False)]
    out['HydraBody_idle'] = [body_frame(i / 4) for i in range(4)]
    out['TarGlob'] = tar_glob_frames()
    out['TarFirePool'] = tar_fire_pool_frames()
    out['TarWave'] = tar_wave_frames()
    out['TarLake'] = tar_lake_frames()
    out['BasaltPillar_top'] = [pillar_top()]
    out['BasaltPillar_shaft'] = [pillar_shaft()]
    return out


def strip_image(frames):
    w, h = frames[0].size
    out = new(w * len(frames), h)
    for i, f in enumerate(frames):
        out.paste(f, (i * w, 0))
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip_image(frames).save(f'{out_dir}/{name}.png')


def sheet(path, strips=None):
    strips = strips or all_strips()
    rows = []
    for name, frames in strips.items():
        box = [f.getbbox() for f in frames if f.getbbox()]
        if box and frames[0].width >= 120:           # crop the big frames to what the strip uses, for review
            x0, y0 = min(b[0] for b in box) - 4, min(b[1] for b in box) - 4
            x1, y1 = max(b[2] for b in box) + 4, max(b[3] for b in box) + 4
            frames = [f.crop((x0, y0, x1, y1)) for f in frames]
        st = strip_image(frames)
        k = 1 if st.height > 100 else 2
        rows.append((name, st.resize((st.width * k, st.height * k), Image.NEAREST)))
    pad, lab = 8, 12
    width = max(r.width for _, r in rows) + pad * 2
    out = Image.new('RGBA', (width, sum(r.height + pad + lab for _, r in rows) + pad), (24, 10, 16, 255))
    d = ImageDraw.Draw(out)
    y = pad
    for name, r in rows:
        d.text((pad, y), name, fill=(240, 210, 190, 255))
        y += lab
        out.paste(Image.new('RGBA', r.size, (44, 18, 26, 255)), (pad, y))
        out.alpha_composite(r, (pad, y))
        y += r.height + pad
    out.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'generate':
        generate(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'ingame':
        from hydra_preview import ingame
        ingame(sys.argv[2])
