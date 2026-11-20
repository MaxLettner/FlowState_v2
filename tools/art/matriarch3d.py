"""Void Matriarch (second boss): a void spider queen, built from 3D primitives and lit by lord3d's renderer (style A,
pre-rendered + dithered, like the Pit Lord).

She is always seen from the front: a symmetric spider (round abdomen behind and above, cephalothorax with a crown of
chitin spikes, eight pale eyes, fangs) whose eight legs blend between named leg layouts (hang, stand, raise, dive,
curl, sprawl). The front view works hanging, standing and falling, and never needs flipping.

    DemonMatriarch_*  288x240 frames, anchor (144, 130) = centre of the 170x170 hitbox (she is a flyer in DemonType)
    DemonMite_*       48x40 frames, anchor (24, 37) = bottom centre (walker)
    DemonEgg_idle     28x34 frames, anchor (14, 33)
    DemonThread_idle  10x10, anchor (5, 9): the knot at the bottom of an anchor thread (the strand itself is drawn in Java)
    WebGate           4 frames 24x64, tiles upward (the arena's walls, like BossGate)

    python tools/art/matriarch3d.py generate <dir>
    python tools/art/matriarch3d.py sheet <out.png>     preview sheet of every strip
"""
import math
import sys

from PIL import Image

import lord3d
from lord3d import Scene, Mat, rgb, render, norm

W, H = 288, 240
SCALE = 0.85
ANCHOR = (144, 130)
YOFF = 20                   # model (0, -20) (between thorax and abdomen) lands on the anchor = hitbox centre

CHITIN = Mat('chitin', rgb('#2d2244'), spec=0.75, shiny=26)
CHITIN_DARK = Mat('chitin_dark', rgb('#1b1529'), spec=0.55, shiny=20)
CHITIN_EDGE = Mat('chitin_edge', rgb('#5d4c7c'), spec=0.85, shiny=30)
FANG = Mat('fang', rgb('#cdbfdc'), spec=0.6, shiny=24)
SILK = Mat('silk', rgb('#c4b4ec'), spec=0.15, shiny=6)
VOID = Mat('void', rgb('#8c4dff'), emissive=True)
EYE = Mat('eye', rgb('#e4d8ff'), emissive=True)
MINE = (VOID, EYE)
HOT_VOID = (0.93, 0.86, 1.0)

_base_emissive = lord3d.emissive


def _emissive(mat, heat, n):
    if mat not in MINE:
        return _base_emissive(mat, heat, n)
    k = max(0.0, min(1.0, heat * (0.55 + 0.45 * max(0.0, n[2]))))
    return tuple(mat.base[c] * (1 - k) + HOT_VOID[c] * k for c in range(3))


def M(x, y, z=0.0):
    """Model point relative to the thorax centre -> lord3d's model space (pivot 176, 268)."""
    return (176 + x, 268 + y + YOFF, z)


# =====================================================================================================
# legs: right side (left mirrors x); each leg is (knee, tip) per layout
# =====================================================================================================
LEG_ROOT = [(20, 8, 16), (27, 2, 6), (27, 4, -6), (21, 8, -18)]
LAYOUTS = {
    # front legs up, gripping the anchor strands (tips at the strand grips); the rest hang relaxed
    'hang': [((58, -62, 24), (96, -150, 14)), ((92, -40, 10), (128, 28, 8)),
             ((96, -20, -12), (120, 70, -16)), ((74, -6, -30), (84, 96, -36))],
    'stand': [((62, -40, 30), (94, 70, 46)), ((98, -44, 10), (138, 70, 10)),
              ((102, -30, -12), (130, 70, -18)), ((80, -20, -32), (98, 70, -40))],
    'raise': [((52, -82, 30), (70, -150, 46)), ((90, -88, 10), (120, -140, 10)),
              ((102, -30, -12), (130, 70, -18)), ((80, -20, -32), (98, 70, -40))],
    'dive': [((50, -60, 30), (64, -126, 30)), ((76, -62, 10), (92, -132, 8)),
             ((80, -50, -12), (88, -122, -16)), ((64, -40, -30), (66, -112, -36))],
    'curl': [((38, -30, 40), (14, 12, 48)), ((50, -36, 26), (20, 2, 30)),
             ((52, -30, 8), (24, 12, 10)), ((46, -20, -10), (22, 18, -8))],
    'sprawl': [((72, -8, 30), (112, 62, 40)), ((112, 0, 10), (150, 62, 10)),
               ((114, 10, -10), (142, 64, -16)), ((88, 16, -30), (112, 66, -36))],
}
FLOOR_Y = 70                # where standing feet are (model y)


def leg_points(P):
    mix = P['legs']
    total = sum(mix.values()) or 1.0
    legs = []
    for i in range(4):
        knee, tip = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        for name, wgt in mix.items():
            k, t = LAYOUTS[name][i]
            for c in range(3):
                knee[c] += k[c] * wgt / total
                tip[c] += t[c] * wgt / total
        legs.append((knee, tip))
    return legs


# =====================================================================================================
# the model
# =====================================================================================================
DEFAULT = dict(legs={'hang': 1.0}, walk=None, walk_amp=0.0, walk_lift=0.0, fang=0.0, bob=0.0, sway=0.0, abd=0.0,
               glow=0.7, eyes=1.0, cracks=0.0, flail=None)


def pose(**kw):
    P = dict(DEFAULT)
    P.update(kw)
    return P


def surface_z(c, rx, ry, rz, x, y):
    q = ((x - c[0]) / rx) ** 2 + ((y - c[1]) / ry) ** 2
    return c[2] + rz * math.sqrt(max(0.0, 1 - q))


def build(s, P):
    by, bx = P['bob'], P['sway']
    glow = P['glow']

    def B(x, y, z=0.0):
        return M(x + bx, y + by, z)

    # abdomen: behind and above the thorax, with the glowing hourglass sigil and void cracks
    ab = 1 + P['abd']
    ac, arx, ary, arz = (0, -46, -34), 50 * ab, 46 * ab, 42
    s.ellipsoid(B(*ac), arx, ary, arz, 0.0, CHITIN)
    sig = []
    for tri in (((-12, -86), (12, -86), (0, -68)), ((-12, -50), (12, -50), (0, -68))):
        sig.append([B(x, y, surface_z(ac, arx, ary, arz, x, y) + 3.5) for x, y in tri])
    for tri in sig:
        s.plate3d(tri, VOID, bevel=1.5, heat=glow)
    cracks = [((-6, -68), (-30, -62), (-42, -44)), ((6, -68), (28, -76), (40, -58)), ((-8, -52), (-20, -38), (-36, -30)),
              ((8, -52), (20, -40), (38, -36)), ((-8, -84), (-22, -94), (-16, -76)), ((8, -84), (24, -92), (32, -80))]
    n = 2 + int(P['cracks'] * 4.99)
    for c in cracks[:n]:
        pts = []
        for i in range(7):
            t = i / 6
            a = (c[0][0] + (c[1][0] - c[0][0]) * t, c[0][1] + (c[1][1] - c[0][1]) * t)
            b = (c[1][0] + (c[2][0] - c[1][0]) * t, c[1][1] + (c[2][1] - c[1][1]) * t)
            x, y = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
            pts.append(B(x, y, surface_z(ac, arx, ary, arz, x, y) + 0.8))
        s.tube(pts, 2.0, 0.8, VOID, heat=glow * 0.8)
    s.sphere(B(0, -92 * ab, -34), 5, CHITIN_EDGE)                       # spinneret, where the drag line comes out

    # legs (behind the thorax first is handled by the z-buffer)
    wk = P['walk']
    for side in (-1, 1):
        for i, (knee, tip) in enumerate(leg_points(P)):
            root = LEG_ROOT[i]
            knee, tip = list(knee), list(tip)
            if wk is not None:
                ph = 2 * math.pi * (wk + 0.5 * ((i + (side > 0)) % 2))
                tip[1] += math.sin(ph) * P['walk_amp'] - max(0.0, math.cos(ph)) * P['walk_lift']
                knee[1] += math.sin(ph) * P['walk_amp'] * 0.5
            if P['flail'] is not None:
                ph = 2 * math.pi * (P['flail'] + i * 0.27 + (side > 0) * 0.5)
                tip[0] += math.sin(ph) * 14
                tip[1] += math.cos(ph) * 16
                knee[1] += math.sin(ph) * 8
            r = B(root[0] * side, root[1], root[2])
            k = B(knee[0] * side, knee[1], knee[2])
            t = B(tip[0] * side, tip[1], tip[2])
            mid1 = ((r[0] + k[0]) / 2 + side * 4, (r[1] + k[1]) / 2 - 6, (r[2] + k[2]) / 2)
            upper = lord3d.bez3(r, mid1, k, 6)
            claw_at = (t[0] + (k[0] - t[0]) * 0.12, t[1] + (k[1] - t[1]) * 0.12, t[2] + (k[2] - t[2]) * 0.12)
            mid2 = ((k[0] + claw_at[0]) / 2 + side * 3, (k[1] + claw_at[1]) / 2, (k[2] + claw_at[2]) / 2)
            lower = lord3d.bez3(k, mid2, claw_at, 6)
            s.tube(upper, 6.5, 5.0, CHITIN)
            s.tube(lower, 4.8, 2.6, CHITIN)
            s.tube([claw_at, t], 2.4, 0.8, CHITIN_EDGE)
            s.sphere(k, 5.2, CHITIN_EDGE)
            s.sphere(k[:2] + (k[2] + 4,), 2.0, VOID, heat=glow * 0.9)      # glowing joint
            mid = lower[3]
            s.sphere(mid[:2] + (mid[2] + 3,), 2.0, VOID, heat=glow * 0.7)

    # cephalothorax and its crown of spikes
    s.ellipsoid(B(0, 0, 0), 34, 26, 30, 0.0, CHITIN)
    for x, h in ((-26, 20), (-13, 30), (0, 40), (13, 30), (26, 20)):
        base = B(x, -14, surface_z((0, 0, 0), 34, 26, 30, x, -14) - 6)
        tip = B(x * 1.45, -14 - h, 8)
        s.tube([base, tip], 5.0, 0.9, CHITIN_EDGE)
        s.sphere(tip, 2.0, VOID, heat=glow)

    # head, eyes, pedipalps, fangs
    s.ellipsoid(B(0, 16, 20), 22, 17, 18, 0.0, CHITIN_DARK)
    eyes = P['eyes']
    for side in (-1, 1):
        for (x, y, z, r) in ((7.5, 15, 36, 4.4), (17, 11, 32, 3.1), (5, 5, 35, 2.4), (13.5, 3, 31, 2.0)):
            s.sphere(B(x * side, y, z), r, EYE, heat=eyes)
        s.tube([B(14 * side, 22, 26), B(27 * side, 34, 34), B(22 * side, 47, 37)], 3.2, 2.0, CHITIN)
    f = P['fang']
    for side in (-1, 1):
        base = B(8 * side, 26, 30)
        knuckle = B((9 + f * 6) * side, 44, 34)
        s.tube([base, knuckle], 6.2, 4.6, CHITIN_DARK)
        s.tube(lord3d.bez3(knuckle, B((8 + f * 9) * side, 54 + f * 3, 36), B((2 + f * 11) * side, 60 + f * 5, 32), 6),
               2.8, 0.6, FANG)


def render_frame(P, scale=SCALE, w=W, h=H, anchor=ANCHOR):
    s = Scene(w=w, h=h, scale=scale, anchor=anchor)
    build(s, P)
    lord3d.emissive = _emissive
    try:
        return render(s, 'prerender')
    finally:
        lord3d.emissive = _base_emissive


# =====================================================================================================
# animations
# =====================================================================================================
def blend(a, b, t):
    out = dict(a)
    for k in ('fang', 'bob', 'sway', 'abd', 'glow', 'eyes', 'cracks', 'walk_amp', 'walk_lift'):
        out[k] = a[k] + (b[k] - a[k]) * t
    legs = {}
    for name in set(a['legs']) | set(b['legs']):
        legs[name] = a['legs'].get(name, 0.0) * (1 - t) + b['legs'].get(name, 0.0) * t
    out['legs'] = legs
    out['walk'] = b['walk'] if t >= 0.5 else a['walk']
    out['flail'] = b['flail'] if t >= 0.5 else a['flail']
    return out


def keys_strip(keys, frames):
    n = len(keys)
    out = []
    for i in range(frames):
        kp = i * (n - 1) / (frames - 1)
        k = min(n - 2, int(kp))
        out.append(blend(keys[k], keys[k + 1], kp - k))
    return out


def poses():
    tau = 2 * math.pi

    def hang(t):
        sn = math.sin(t * tau)
        return pose(legs={'hang': 1.0}, sway=sn * 2.0, bob=math.sin(t * tau * 2) * 0.8, abd=0.02 * (sn + 1),
                    glow=0.6 + 0.25 * (sn + 1) / 2)

    weave = [
        pose(legs={'hang': 1.0}, glow=0.7),
        pose(legs={'hang': 0.4, 'raise': 0.6}, abd=0.08, glow=1.2, fang=0.3, bob=-3),
        pose(legs={'hang': 0.2, 'raise': 0.8}, abd=0.12, glow=1.6, fang=0.6, bob=-4, sway=-3),
        pose(legs={'hang': 0.5, 'raise': 0.5}, abd=0.04, glow=1.2, fang=0.4, bob=2, sway=3),
        pose(legs={'hang': 1.0}, glow=0.8),
    ]

    def dive(t):
        return pose(legs={'dive': 1.0}, fang=0.5, glow=1.0, flail=t, bob=0)

    bite = [
        pose(legs={'dive': 1.0}, fang=0.5, glow=1.0),
        pose(legs={'raise': 1.0}, fang=1.0, glow=1.4, bob=-6),
        pose(legs={'raise': 0.5, 'stand': 0.5}, fang=1.0, glow=1.6, bob=8),
        pose(legs={'stand': 1.0}, fang=0.1, glow=1.2, bob=12),
        pose(legs={'stand': 0.6, 'hang': 0.4}, fang=0.0, glow=0.9, bob=6),
    ]

    def climb(t):
        return pose(legs={'dive': 0.6, 'hang': 0.4}, walk=t, walk_amp=16, walk_lift=8, glow=0.8,
                    bob=math.sin(t * tau * 2) * 2)

    def fall(t):
        return pose(legs={'hang': 0.5, 'raise': 0.5}, flail=t, fang=0.8, glow=1.1, eyes=1.0)

    def downed(t):
        sn = math.sin(t * tau)
        return pose(legs={'sprawl': 1.0}, bob=18 + sn * 1.5, flail=None, fang=0.2 + 0.1 * sn, glow=0.35, eyes=0.45,
                    walk=t, walk_amp=3, walk_lift=0)

    rise = [
        pose(legs={'sprawl': 1.0}, bob=18, glow=0.35, eyes=0.45),
        pose(legs={'stand': 1.0}, bob=4, glow=0.8, eyes=1.0, fang=0.4),
        pose(legs={'stand': 1.0}, bob=14, glow=1.0, eyes=1.0, fang=0.6),
        pose(legs={'dive': 1.0}, bob=-8, glow=1.1, eyes=1.0, fang=0.2),
    ]

    def screech(t):
        return pose(legs={'raise': 1.0}, fang=0.85 + 0.15 * math.sin(t * tau), glow=1.5 + 0.3 * math.sin(t * tau),
                    sway=math.sin(t * tau * 3) * 1.5, abd=0.06)

    death = [
        pose(legs={'raise': 1.0}, fang=1.0, glow=1.4, cracks=0.2),
        pose(legs={'raise': 0.4, 'curl': 0.6}, fang=0.7, glow=1.2, cracks=0.4, eyes=0.8, bob=4),
        pose(legs={'curl': 1.0}, fang=0.3, glow=1.0, cracks=0.7, eyes=0.5, bob=8),
        pose(legs={'curl': 1.0}, fang=0.1, glow=1.8, cracks=1.0, eyes=0.3, bob=9, abd=0.06),
        pose(legs={'curl': 1.0}, fang=0.0, glow=2.6, cracks=1.0, eyes=0.1, bob=9, abd=0.12),
    ]
    loop = lambda fn, n: [fn(i / n) for i in range(n)]
    return dict(idle=loop(hang, 12), weave=keys_strip(weave, 8), dive=loop(dive, 4), bite=keys_strip(bite, 8),
                climb=loop(climb, 8), fall=loop(fall, 4), downed=loop(downed, 8), rise=keys_strip(rise, 6),
                screech=loop(screech, 6), death=keys_strip(death, 12))


# ---- the mite: the same spider, tiny, walking ----
MITE_W, MITE_H, MITE_SCALE, MITE_ANCHOR = 48, 40, 0.155, (24, 37)


def mite_frame(P):
    # its feet (model y FLOOR_Y) on the anchor row
    return render_frame(P, scale=MITE_SCALE, w=MITE_W, h=MITE_H,
                        anchor=(MITE_ANCHOR[0], MITE_ANCHOR[1] - (FLOOR_Y + YOFF) * MITE_SCALE))


def mite_poses():
    def idle(t):
        return pose(legs={'stand': 1.0}, bob=math.sin(t * 2 * math.pi) * 2, glow=0.9, fang=0.2)

    def walk(t):
        return pose(legs={'stand': 1.0}, walk=t, walk_amp=10, walk_lift=18, glow=1.0, fang=0.3)

    leap = [pose(legs={'dive': 1.0}, fang=1.0, glow=1.4), pose(legs={'raise': 1.0}, fang=1.0, glow=1.4)]
    return dict(idle=[idle(i / 4) for i in range(4)], walk=[walk(i / 6) for i in range(6)], leap=leap)


# ---- egg sac ----
def egg_frame(heat):
    s = Scene(w=28, h=34, scale=1.0, anchor=(14, 33))
    c = (176, 268 - 14, 0)
    s.ellipsoid(c, 11, 14, 10, 0.0, SILK)
    for k in range(5):                                              # silk wrapping
        y = -24 + k * 5.5
        half = 11 * math.sqrt(max(0.0, 1 - ((y + 14) / 14) ** 2))
        pts = [(176 + x, 268 + y + x * 0.25, surface_z((176, 268 - 14, 0), 11, 14, 10, 176 + x, 268 + y) + 0.6)
               for x in [(-half + i * half / 3) for i in range(7)]]
        s.tube(pts, 0.8, 0.8, CHITIN_EDGE)
    for x, y, r in ((-3, -16, 2.6), (4, -11, 2.0), (-1, -7, 1.6), (5, -19, 1.4)):   # something inside, glowing through
        s.sphere((176 + x, 268 + y, surface_z((176, 268 - 14, 0), 11, 14, 10, 176 + x, 268 + y) - 0.4), r, VOID, heat=heat)
    lord3d.emissive = _emissive
    try:
        img = render(s, 'prerender')
    finally:
        lord3d.emissive = _base_emissive
    return img


# ---- 2D bits ----
def thread_knot():
    img = Image.new('RGBA', (10, 10), (0, 0, 0, 0))
    px = img.load()
    for y in range(10):
        for x in range(10):
            d = math.hypot(x - 4.5, y - 6.0)
            if d < 3.6:
                v = 1 - d / 3.6
                px[x, y] = (int(150 + 100 * v), int(100 + 120 * v), 255, int(255 * min(1, v * 2.2)))
    for y in range(0, 4):
        px[4, y] = px[5, y] = (190, 160, 255, 200)
    return img


def web_gate_frames():
    """A curtain of void silk; 24 wide, tiles every 64 px upward (4 frames), like BossGate."""
    gw, gh = 24, 64
    frames = []
    for i in range(4):
        img = Image.new('RGBA', (gw, gh), (0, 0, 0, 0))
        px = img.load()

        def put(x, y, c):
            if 0 <= x < gw and 0 <= y < gh:
                px[x, y] = c

        for k, x0 in enumerate((3, 9, 15, 21)):                                  # hanging strands
            for y in range(gh):
                x = int(round(x0 + math.sin(y * 0.15 + i * 1.57 + k) * 1.2))
                shimmer = 0.5 + 0.5 * math.sin(y * 0.3 - i * 1.57 + k * 2)
                put(x, y, (int(120 + 110 * shimmer), int(80 + 120 * shimmer), 255, 235))
        for k in range(3):                                                       # cross threads, sagging
            y0 = 10 + k * 21 + (i % 2)
            for x in range(gw):
                y = int(round(y0 + 2.5 * math.sin(x / gw * math.pi)))
                put(x, y, (190, 160, 255, 200))
        for k in range(2):                                                       # dew of void light
            x, y = (5 + 11 * k + i * 3) % gw, (14 + 29 * k + i * 7) % gh
            put(x, y, (240, 230, 255, 255))
        frames.append(img)
    return frames


# =====================================================================================================
# output
# =====================================================================================================
def strip(frames):
    w, h = frames[0].size
    out = Image.new('RGBA', (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (i * w, 0))
    return out


def all_strips():
    out = {}
    for name, ps in poses().items():
        out[f'DemonMatriarch_{name}'] = [render_frame(P) for P in ps]
    for name, ps in mite_poses().items():
        out[f'DemonMite_{name}'] = [mite_frame(P) for P in ps]
    out['DemonEgg_idle'] = [egg_frame(0.5 + 0.5 * math.sin(i / 4 * 2 * math.pi)) for i in range(4)]
    out['DemonThread_idle'] = [thread_knot()]
    out['WebGate'] = web_gate_frames()
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip(frames).save(f'{out_dir}/{name}.png')


def sheet(path, strips=None):
    strips = strips or all_strips()
    rows = []
    for name, frames in strips.items():
        st = strip(frames)
        k = 1 if st.height > 100 else 3
        rows.append(st.resize((st.width * k, st.height * k), Image.NEAREST))
    pad = 8
    width = max(r.width for r in rows) + pad * 2
    out = Image.new('RGBA', (width, sum(r.height + pad for r in rows) + pad), (24, 10, 16, 255))
    y = pad
    for r in rows:
        out.paste(Image.new('RGBA', r.size, (44, 18, 26, 255)), (pad, y))
        out.alpha_composite(r, (pad, y))
        y += r.height + pad
    out.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'generate':
        generate(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'one':
        render_frame(poses()['hang'][0]).resize((W * 2, H * 2), Image.NEAREST).save(sys.argv[2])
