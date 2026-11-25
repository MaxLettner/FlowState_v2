"""The Void Matriarch in 2D (replaces the 3D render of matriarch3d.py, 2026-11-23), plus her mites, egg sacs,
the strand knot, the web gates and her acid.

Drawn like the Pit Lord (lord2d.py): shaded primitives on the demon Rig (demons.py), pose-driven. She is always
seen from the front, hanging head-down: the cracked void abdomen above, the cephalothorax with its crown of spikes,
a head with eight pale eyes, pedipalps and fangs. Each of her eight legs is two bones solved with IK from its root
on the thorax to a tip the pose places, so a pose only says where the feet go (layouts blend like the 3D version).

    DemonMatriarch_*  288x240 frames, anchor (144, 130) = centre of the 170x170 hitbox (a flyer); must match DemonType
      idle 12 (hanging), descend 8, crouch 4, dive 4, bite 8, dangle 8, climb 8, weave 8, pluck 8, brood 10, spit 10,
      reel 8, jolt 4, fall 4, land 4, downed 8, rise 6, screech 6, death 12
    DemonMite_*       48x40, anchor (24, 37) (walker, side view, faces right): idle 4, walk 6, leap 2
    DemonEgg_idle     28x34, anchor (14, 33): 4
    DemonThread_idle  10x10: the knot at the bottom of an anchor strand (the strand is drawn in Java)
    WebGate           4 frames 24x64 (the arena walls)
    AcidGlob          4 frames 16x16 (her spit in flight, centre anchored)
    AcidPool          4 frames 32x16 tile (the puddle it leaves, bottom-anchored; tiles sideways like MagmaPool)

    python tools/art/matriarch2d.py generate <dir>
    python tools/art/matriarch2d.py sheet <out.png>
    python tools/art/matriarch2d.py frame <anim> <i> [out.png]
"""
import math
import sys

from PIL import Image

from pixel import ramp, hexc, mix, clamp, OUTLINE
from demons import Rig, ell, limb, chain, poly
from lord2d import ik, R, A, S

W, H = 288, 240
AX, AY = 144, 130
FEET = 43                              # the bottom of her hitbox (art px below the anchor): where she stands

CHITIN = ramp('#5a4b7e', '#352a52', '#211a38', '#140f25', '#0a0712')
CHITIN_LIT = ramp('#9c88cc', '#6a5a92', '#3e3262', '#262040', '#17122a')
CHITIN_FAR = ramp('#3e3360', '#251e3e', '#18132a', '#0f0b1c', '#07050d')
VOIDC = ramp('#f6efff', '#d6bcff', '#ab7dff', '#7c48e2', '#4a24a0')
EYE = ramp('#ffffff', '#ece4ff', '#c8b6f0', '#9884c8', '#5e4e88')
FANG = ramp('#f2eaf8', '#cfc2de', '#9c8cb2', '#685a7c', '#3a3048')
ACID = ramp('#fdf0ff', '#f6a8ff', '#d85cff', '#9e26e6', '#5a0e96')
SILK = hexc('#d8ccf4')
SILK_RAMP = ramp('#f4eeff', '#d8ccef', '#a898c4', '#6e5e8c', '#3e3256')
SILK_DIM = hexc('#9c86d8', 200)

# leg roots on the thorax (right side; the left mirrors x) and bone lengths (femur, tibia)
ROOTS = [(15, 9), (21, 3), (21, -4), (15, -10)]
BONES = [(68, 74), (64, 70), (58, 64), (56, 60)]

# where the feet go, right side, art px relative to the anchor
LAYOUTS = {
    'hang': [(82, -108), (114, 26), (104, 62), (72, 84)],        # front pair grips the side strands
    'stand': [(70, FEET), (106, FEET), (122, FEET), (98, FEET)],
    'raise': [(70, -118), (118, -78), (110, 56), (80, 82)],        # front two pairs up: the threat display
    'dive': [(52, -116), (80, -110), (88, -98), (64, -92)],        # all swept back above her: head-first drop
    'grip': [(66, -112), (84, -36), (78, 8), (58, 26)],            # drawn in tight before the drop
    'strike': [(96, 40), (118, 10), (110, 52), (80, 70)],          # front legs forward and down around the prey
    'curl': [(12, 30), (20, 24), (24, 18), (18, 10)],
    'sprawl': [(100, FEET), (134, FEET - 2), (130, FEET), (104, FEET)],
    'spin': [(82, -108), (112, 6), (30, -84), (20, -74)],          # rear pairs reach up to the spinnerets
    'climb': [(60, -116), (92, -90), (96, -20), (70, 20)],
}

DEFAULT = dict(legs={'hang': 1.0}, pin=0.6, knee=0.9, bob=0.0, sway=0.0, tilt=0.0, squash=1.0,
               abd=0.0, abd_y=0.0, fang=0.0, maw=0.0, glow=0.7, eyes=1.0, cracks=0.0,
               walk=None, walk_amp=(0.0, 0.0), flail=None, flail_amp=1.0, offs=None,
               spit=0.0, glob=None, drool=0.0, silk=None, eggs=0.0, egg_out=None, shake=0.0, phase=0.0)


def pose(**kw):
    P = dict(DEFAULT)
    P.update(kw)
    return P


def F(p):
    """Anchor-local -> frame pixels."""
    return (AX + p[0], AY + p[1])


def layout_tips(P):
    mix_ = P['legs']
    total = sum(mix_.values()) or 1.0
    out = []
    for i in range(4):
        x = y = 0.0
        for name, w in mix_.items():
            tx, ty = LAYOUTS[name][i]
            x += tx * w / total
            y += ty * w / total
        out.append((x, y))
    return out


def stroke_line(r, a, b, c):
    r.stroke(a[0], a[1], b[0], b[1], c)


def silk_line(r, a, b, sag=0.0, c=SILK):
    """A strand of silk from a to b (frame px), sagging a little."""
    n = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1]) / 6))
    pts = []
    for i in range(n + 1):
        t = i / n
        pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t + sag * 4 * t * (1 - t)))
    for p, q in zip(pts, pts[1:]):
        r.stroke(p[0], p[1], q[0], q[1], c)


def acid_blob(r, c, rad, phase=0.0, streak=0.0):
    """A glob of glowing purple acid at c (frame px); streak > 0 trails it upward (it is flying down)."""
    x, y = c
    if streak > 0:
        r.part(chain([(x, y - rad - streak), (x, y - rad * 0.4)], 1.0, rad * 0.8), ACID, flat=0.45, edge=False)
    r.part(ell(x, y, rad + 1, rad + 1.5 + 0.6 * math.sin(phase * 6.28)), ACID, flat=0.62, edge=False)
    r.part(ell(x, y + 0.5, rad, rad), ACID, flat=0.35, edge=False)
    r.part(ell(x - rad * 0.3, y - rad * 0.3, rad * 0.4, rad * 0.4), ACID, flat=0.0, edge=False)
    r.glow(x, y, rad * 2.6 + 4, ACID[2], 0.55)


# =====================================================================================================
# the Matriarch
# =====================================================================================================
def matriarch_frame(P):
    P = {**DEFAULT, **P}
    r = Rig(W, H, rim=0.32)
    shake = P['shake'] * math.sin(P['phase'] * 6.28 * 3)
    bx, by = P['sway'] + shake, P['bob']
    tilt, sq = P['tilt'], P['squash']
    glow, phase = P['glow'], P['phase']

    def B(p):
        """Body-local -> frame px (the body bobs, sways, tilts and squashes around the anchor)."""
        q = R((p[0], p[1] * sq), tilt)
        return (AX + bx + q[0], AY + by + q[1])

    # ---- legs (solved first, drawn in layers)
    tips = layout_tips(P)
    pin = P['pin']
    legs = []
    for side in (1, -1):
        for i in range(4):
            tx, ty = tips[i]
            tip = (AX + tx * side + bx * (1 - pin), AY + ty + by * (1 - pin))
            k = i + (0 if side > 0 else 4)
            if P['offs'] and P['offs'][k]:
                o = P['offs'][k]
                tip = (tip[0] + o[0] * side, tip[1] + o[1])
            if P['walk'] is not None:
                ph = 2 * math.pi * (P['walk'] + 0.5 * ((i + (side < 0)) % 2))
                ax_, ay_ = P['walk_amp']
                tip = (tip[0] + math.cos(ph) * ax_ * side, tip[1] + math.sin(ph) * ay_)
            if P['flail'] is not None:
                ph = 2 * math.pi * (P['flail'] + i * 0.27 + (side < 0) * 0.5)
                a = P['flail_amp']
                tip = (tip[0] + math.sin(ph) * 12 * a * side, tip[1] + math.cos(ph) * 14 * a)
            root = B((ROOTS[i][0] * side, ROOTS[i][1]))
            l1, l2 = BONES[i]
            pref = (side * 1.0, -P['knee'])
            knee, end, _ = ik(root, tip, l1, l2, pref)
            legs.append((i, side, root, knee, end))

    def draw_leg(i, side, root, knee, end, far):
        rmp = CHITIN_FAR if far else CHITIN
        b = 0.08 if far else 0.0
        w0 = 5.2 - i * 0.3
        r.part(limb(root[0], root[1], knee[0], knee[1], w0, w0 - 1.2), rmp, bias=b)
        for t in (0.35, 0.6, 0.85):                         # bristles along the femur
            p = (root[0] + (knee[0] - root[0]) * t, root[1] + (knee[1] - root[1]) * t)
            a = math.atan2(knee[1] - root[1], knee[0] - root[0]) - side * 1.9
            stroke_line(r, p, (p[0] + math.cos(a) * 5, p[1] + math.sin(a) * 5), rmp[3])
        claw = (end[0] + (knee[0] - end[0]) * 0.1, end[1] + (knee[1] - end[1]) * 0.1)
        r.part(limb(knee[0], knee[1], claw[0], claw[1], w0 - 1.4, 1.9), rmp, bias=b + 0.06)
        r.part(limb(claw[0], claw[1], end[0], end[1], 1.6, 0.6), FANG, bias=b + 0.25)
        dx, dy = claw[0] - knee[0], claw[1] - knee[1]
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        for t in (0.22, 0.5, 0.78):                         # pale bands around the tibia
            p = (knee[0] + dx * t, knee[1] + dy * t)
            wd = (w0 - 1.4) * (1 - t) + 1.9 * t
            stroke_line(r, (p[0] - nx * wd, p[1] - ny * wd), (p[0] + nx * wd, p[1] + ny * wd), rmp[1] if not far else rmp[2])
        for t in (0.35, 0.65):                              # spines
            p = (knee[0] + dx * t, knee[1] + dy * t)
            stroke_line(r, p, (p[0] + nx * 4 * side + dx / ln * 2, p[1] + ny * 4 * side + dy / ln * 2), rmp[3])
        r.part(ell(knee[0], knee[1], w0 - 0.4, w0 - 0.4), CHITIN_LIT if not far else CHITIN, bias=b)
        # a band of void light at the knee and halfway down the tibia
        r.part(ell(knee[0], knee[1] + 0.5, 1.8, 1.8), VOIDC, flat=0.4 - 0.3 * min(glow, 1), edge=False)
        mid = (knee[0] + (claw[0] - knee[0]) * 0.5, knee[1] + (claw[1] - knee[1]) * 0.5)
        r.part(ell(mid[0], mid[1], 1.4, 1.4), VOIDC, flat=0.55 - 0.3 * min(glow, 1), edge=False)
        if glow > 0.9:
            r.glow(knee[0], knee[1], 6, VOIDC[2], 0.25 * min(glow, 2))

    # ---- abdomen (behind and above): cracked chitin, the hourglass sigil, void showing through the cracks
    ab = 1 + P['abd']
    ac = B((0, -44 + P['abd_y']))
    arx, ary = 40 * ab, 38 * ab * sq
    front_abd = P['abd_y'] > 20

    def abdomen():
        r.part(ell(ac[0], ac[1], arx, ary, tilt), CHITIN, bias=0.02)
        r.part(ell(ac[0] - arx * 0.28, ac[1] - ary * 0.42, arx * 0.42, ary * 0.24, tilt - 0.4), CHITIN_LIT, flat=0.55, edge=False)
        for k in (-1, 1):                                   # ridges
            pts = [B((k * 18 * ab, -72 * ab + P['abd_y'])), B((k * 30 * ab, -46 + P['abd_y'])), B((k * 20 * ab, -16 + P['abd_y']))]
            for p, q in zip(pts, pts[1:]):
                stroke_line(r, p, q, CHITIN[3])
        cracks = [((-4, -44), (-22, -52), (-34, -38)), ((4, -44), (22, -56), (32, -44)), ((-6, -32), (-16, -22), (-30, -18)),
                  ((6, -32), (18, -24), (31, -22)), ((-6, -58), (-14, -70), (-8, -78)), ((6, -58), (16, -68), (24, -62))]
        n = 2 + int(P['cracks'] * 4.99)
        cc = VOIDC[0] if glow > 1.4 else VOIDC[1] if glow > 0.8 else VOIDC[2]
        for yy in (-70, -58, -20):                          # the plates of her abdomen
            pts = [B((x * ab, (yy + 44 - abs(x) * 0.22) * ab - 44 + P['abd_y'])) for x in range(-30, 31, 6)
                   if ((x / 40) ** 2 + ((yy + 44) / 38) ** 2) < 0.8]
            for p, q in zip(pts, pts[1:]):
                stroke_line(r, p, q, CHITIN[4])
        for c in cracks[:n]:
            pts = [B((x * ab, (y + 44) * ab - 44 + P['abd_y'])) for x, y in c]
            r.part(chain(pts, 1.6, 0.7), VOIDC, flat=clamp(0.55 - 0.3 * glow), edge=False)
            for p, q in zip(pts, pts[1:]):
                stroke_line(r, p, q, cc)
            r.glow(pts[1][0], pts[1][1], 7, VOIDC[2], 0.2 + 0.12 * min(glow, 2))
        # the hourglass sigil
        top = [B((x * ab, (y + 44) * ab - 44 + P['abd_y'])) for x, y in ((-9, -60), (9, -60), (0, -45))]
        bot = [B((x * ab, (y + 44) * ab - 44 + P['abd_y'])) for x, y in ((-9, -30), (9, -30), (0, -45))]
        fv = clamp(0.5 - 0.3 * glow)
        r.part(poly(top, 0.2), VOIDC, flat=fv, edge=False)
        r.part(poly(bot, 0.2), VOIDC, flat=fv, edge=False)
        r.glow(*B((0, -45 * ab + P['abd_y'])), 12 + 6 * min(glow, 2), VOIDC[2], 0.3 + 0.2 * min(glow, 2))
        # eggs glowing through the skin (brood)
        if P['eggs'] > 0:
            for x, y in ((-16, -44), (14, -50), (-2, -26), (20, -30), (-20, -62)):
                p = B((x * ab, (y + 44) * ab - 44 + P['abd_y']))
                r.part(ell(p[0], p[1], 4.5 * P['eggs'], 5.5 * P['eggs']), VOIDC, flat=0.5 - 0.3 * P['eggs'], edge=False)
            r.glow(ac[0], ac[1], 30, VOIDC[2], 0.35 * P['eggs'])
        # spinnerets at the top
        sp = B((0, -44 + P['abd_y'] - 38 * ab))
        r.part(ell(sp[0], sp[1] + 2, 5, 4), CHITIN_LIT, bias=0.1)
        r.part(ell(sp[0], sp[1] + 1, 2, 1.5), VOIDC, flat=0.3, edge=False)
        return sp

    sp = None
    if not front_abd:
        sp = abdomen()
    # silk being drawn out (weave): from the spinnerets to the feet that pull it
    if P['silk'] and sp:
        for k in P['silk']:
            i, side, root, knee, end = legs[k]
            silk_line(r, sp, end, sag=3)

    # far legs (the rear two pairs), then the thorax, then the near legs
    for (i, side, root, knee, end) in legs:
        if i >= 2:
            draw_leg(i, side, root, knee, end, True)
    if P['silk'] and sp is None:
        pass
    th = B((0, 0))
    r.part(ell(th[0], th[1] - 2, 29, 19 * sq, tilt), CHITIN)
    r.part(ell(th[0] - 8, th[1] - 7, 12, 6, tilt - 0.3), CHITIN_LIT, flat=0.5, edge=False)
    for k in (-1, 1):                                      # the fovea groove and radiating striae
        stroke_line(r, B((k * 6, -12)), B((k * 18, -2)), CHITIN[3])
        stroke_line(r, B((k * 6, -6)), B((k * 22, 6)), CHITIN[3])
    for (i, side, root, knee, end) in legs:
        if i < 2:
            draw_leg(i, side, root, knee, end, False)

    # crown of chitin spikes along the top of the thorax, tipped with void light
    for x, h, a in ((-24, 16, -2.2), (-12, 24, -1.95), (0, 30, -1.5708), (12, 24, -1.19), (24, 16, -0.94)):
        base = B((x, -12))
        tip = (base[0] + math.cos(a + tilt) * h, base[1] + math.sin(a + tilt) * h)
        r.part(chain([base, ((base[0] + tip[0]) / 2, (base[1] + tip[1]) / 2 - 1), tip], 4.2, 0.8), CHITIN_LIT, bias=0.05)
        r.part(ell(tip[0], tip[1], 1.6, 1.6), VOIDC, flat=clamp(0.3 - 0.2 * glow), edge=False)
        r.glow(tip[0], tip[1], 5, VOIDC[2], 0.2 + 0.15 * min(glow, 2))

    # head
    hd = B((0, 20))
    r.part(ell(hd[0], hd[1], 19, 14 * sq, tilt), CHITIN_FAR, bias=-0.1)
    r.part(ell(hd[0] - 6, hd[1] - 6, 8, 3.5, tilt - 0.2), CHITIN_LIT, flat=0.55, edge=False)
    # pedipalps
    for k in (-1, 1):
        a, b_, c = B((k * 12, 22)), B((k * (22 + P['fang'] * 4), 32)), B((k * (18 + P['fang'] * 6), 41))
        r.part(chain([a, b_, c], 3.4, 2.2), CHITIN, bias=0.05)
        r.part(ell(c[0], c[1], 2.6, 2.6), CHITIN_LIT, bias=0.1)
    # the maw (between the fangs): glows when she screeches or spits
    f = P['fang']
    if P['maw'] > 0.05 or f > 0.4:
        m = B((0, 30))
        r.part(ell(m[0], m[1] + 2, 6 + 3 * f, 4 + 5 * f), ACID if P['spit'] > 0 else VOIDC,
               flat=clamp(0.75 - 0.6 * P['maw']), edge=False)
        r.glow(m[0], m[1] + 3, 10 + 10 * P['maw'], (ACID if P['spit'] > 0 else VOIDC)[2], 0.25 + 0.45 * P['maw'])
    # chelicerae and fangs
    for k in (-1, 1):
        base = B((k * 7, 24))
        knuckle = B((k * (8 + f * 7), 37))
        r.part(limb(base[0], base[1], knuckle[0], knuckle[1], 5.5, 4.2), CHITIN, bias=0.04)
        tip = B((k * (2 + f * 12), 50 + f * 4))
        bend = B((k * (8 + f * 10), 46 + f * 3))
        r.part(chain([knuckle, bend, tip], 2.6, 0.6), FANG, bias=0.0)
        if P['drool'] > 0:
            d = P['drool']
            ln = 4 + 10 * d * (0.6 + 0.4 * math.sin(phase * 6.28 + k))
            r.part(limb(tip[0], tip[1], tip[0], tip[1] + ln, 1.0, 1.8), ACID, flat=0.4, edge=False)
            r.part(ell(tip[0], tip[1] + ln + 1, 2, 2.2), ACID, flat=0.3, edge=False)
    # eight eyes: two big, two side, four small above
    eb = P['eyes']
    ev = clamp(0.7 - 0.5 * eb)
    for k in (-1, 1):
        for (x, y, rad) in ((5, 19, 3.6), (13, 16, 2.6), (4.5, 11.5, 2.0), (11, 9.5, 1.7)):
            p = B((x * k, y))
            r.part(ell(p[0], p[1], rad + 1, rad + 1), CHITIN, flat=1.0, edge=False)
            r.part(ell(p[0], p[1], rad, rad), EYE, flat=ev, edge=False)
            r.dot(p[0] - rad * 0.35, p[1] - rad * 0.35, EYE[0] if eb > 0.4 else EYE[2])
        r.glow(*B((5 * k, 19)), 6 + 3 * eb, VOIDC[1], 0.25 + 0.35 * eb)

    if front_abd:
        abdomen()

    # her spit: the glob gathering at the maw, then flying down
    if P['spit'] > 0 and P['glob'] is None:
        m = B((0, 34))
        acid_blob(r, (m[0], m[1] + 2 + 3 * P['spit']), 2 + 5 * P['spit'], phase)
    if P['glob'] is not None:
        g = B((0, 34 + P['glob']))
        acid_blob(r, g, 7, phase, streak=min(30, P['glob'] * 0.6))
    # eggs flung from the spinnerets (brood)
    if P['egg_out'] is not None and sp:
        t = P['egg_out']
        for k in (-1, 1):
            p = (sp[0] + k * 44 * t, sp[1] - 30 * t + 20 * t * t - 6)
            silk_line(r, sp, p, c=SILK_DIM)
            r.part(ell(p[0], p[1], 9, 11), SILK_RAMP)
            r.part(ell(p[0] - 1, p[1] + 1, 3.4, 4.2), VOIDC, flat=0.3, edge=False)
            r.glow(p[0], p[1], 10, VOIDC[2], 0.3)
    return r.render()


# ---------------------------------------------------------------- animation
def blend(a, b, t):
    a, b = {**DEFAULT, **a}, {**DEFAULT, **b}
    out = dict(b if t >= 0.5 else a)
    for k in ('pin', 'knee', 'bob', 'sway', 'tilt', 'squash', 'abd', 'abd_y', 'fang', 'maw', 'glow', 'eyes', 'cracks',
              'spit', 'drool', 'eggs', 'shake', 'phase', 'flail_amp'):
        out[k] = a[k] + (b[k] - a[k]) * t
    legs = {}
    for name in set(a['legs']) | set(b['legs']):
        legs[name] = a['legs'].get(name, 0.0) * (1 - t) + b['legs'].get(name, 0.0) * t
    out['legs'] = legs
    oa, ob = a['offs'] or [None] * 8, b['offs'] or [None] * 8
    if a['offs'] or b['offs']:
        out['offs'] = [((x or (0, 0))[0] * (1 - t) + (y or (0, 0))[0] * t, (x or (0, 0))[1] * (1 - t) + (y or (0, 0))[1] * t)
                       for x, y in zip(oa, ob)]
    for k in ('glob', 'egg_out'):
        if a[k] is not None and b[k] is not None:
            out[k] = a[k] + (b[k] - a[k]) * t
    return out


def keys_strip(keys, frames):
    n = len(keys)
    out = []
    for i in range(frames):
        kp = i * (n - 1) / (frames - 1)
        k = min(n - 2, int(kp))
        out.append(blend(keys[k], keys[k + 1], kp - k))
    return out


def loop(fn, n):
    return [fn(i / n) for i in range(n)]


def one(k, dx, dy):
    """Leg offsets with only leg k moved (0-3 right, 4-7 left; dx is outward)."""
    o = [None] * 8
    o[k] = (dx, dy)
    return o


def poses():
    tau = 2 * math.pi

    def hang(t):
        sn = math.sin(t * tau)
        return pose(sway=sn * 2.0, bob=math.sin(t * tau * 2) * 0.8, abd=0.015 * (sn + 1), glow=0.6 + 0.25 * (sn + 1) / 2,
                    walk=t, walk_amp=(1.5, 3), phase=t)

    def descend(t):
        # paying out line: the rear legs feed it from the spinnerets in turn, the rest spread wide
        s = math.sin(t * tau)
        return pose(legs={'hang': 0.6, 'spin': 0.4}, sway=s * 3, bob=math.cos(t * tau) * 1.5, glow=0.8, eyes=0.7 + 0.3 * (s + 1) / 2,
                    offs=[None, None, (0, 14 * s), (0, -12 * s), None, None, (0, -14 * s), (0, 12 * s)], phase=t)

    crouch = [
        pose(glow=0.8),
        pose(legs={'grip': 1.0}, bob=-8, squash=0.95, glow=1.2, fang=0.4, eyes=1.3, pin=0.3),
        pose(legs={'grip': 1.0}, bob=-12, squash=0.92, glow=1.5, fang=0.6, eyes=1.6, pin=0.3, shake=0.6, phase=0.3),
        pose(legs={'grip': 1.0}, bob=-13, squash=0.92, glow=1.6, fang=0.7, eyes=1.7, pin=0.3, shake=0.6, phase=0.6),
    ]

    def dive(t):
        return pose(legs={'dive': 1.0}, fang=0.6, glow=1.1, flail=t, flail_amp=0.5, bob=0, squash=1.06, pin=0.0, phase=t)

    bite = [
        pose(legs={'dive': 1.0}, fang=0.6, glow=1.1, pin=0.0),
        pose(legs={'strike': 1.0}, fang=1.0, maw=0.6, glow=1.5, bob=-4, pin=0.0),
        pose(legs={'strike': 1.0}, fang=1.0, maw=0.9, glow=1.7, bob=10, squash=1.08, pin=0.0),
        pose(legs={'strike': 0.7, 'stand': 0.3}, fang=0.0, maw=0.3, glow=1.3, bob=14, squash=0.94, pin=0.0, shake=0.4),
        pose(legs={'strike': 0.4, 'hang': 0.6}, fang=0.1, glow=0.9, bob=6, pin=0.2),
    ]

    def dangle(t):
        # at the bottom of her line after a bite: turning slowly, legs groping, fangs clicking
        s = math.sin(t * tau)
        return pose(legs={'strike': 0.5, 'hang': 0.5}, pin=0.0, tilt=0.06 * s, sway=4 * s, fang=0.3 + 0.3 * max(0, math.sin(t * tau * 2)),
                    glow=0.8, walk=t, walk_amp=(6, 10), phase=t)

    def climb(t):
        return pose(legs={'climb': 1.0}, walk=t, walk_amp=(4, 18), glow=0.8, bob=math.sin(t * tau * 2) * 2, pin=0.0, phase=t)

    weave = [
        pose(glow=0.8),
        pose(legs={'spin': 1.0}, abd=0.06, glow=1.3, silk=[2, 3, 6, 7], offs=[None, None, (10, 30), None, None, None, None, (6, 26)]),
        pose(legs={'spin': 1.0}, abd=0.1, glow=1.7, silk=[2, 3, 6, 7], offs=[None, None, (40, 70), (30, 10), None, None, (10, 30), (40, 60)],
             eyes=1.2),
        pose(legs={'spin': 1.0}, abd=0.06, glow=1.6, silk=[2, 3, 6, 7], offs=[None, None, (10, 30), (50, 70), None, None, (46, 74), (10, 20)],
             eyes=1.2),
        pose(legs={'hang': 1.0}, glow=0.9),
    ]

    pluck = [
        pose(glow=0.8),
        pose(glow=1.2, offs=[(40, 56), (10, -40), None, None, None, None, None, None], eyes=1.2, fang=0.2, sway=-4),
        pose(glow=1.4, offs=[(-8, -6), (-6, 4), None, None, None, None, None, None], eyes=1.4, fang=0.4, shake=0.8, phase=0.2),
        pose(glow=1.3, offs=[None, None, None, None, (40, 56), (10, -40), None, None], eyes=1.3, fang=0.3, sway=4),
        pose(glow=1.6, offs=[None, None, None, None, (-8, -6), (-6, 4), None, None], eyes=1.6, fang=0.6, shake=0.8, phase=0.6),
        pose(glow=0.9),
    ]

    brood = [
        pose(glow=0.8),
        pose(abd=0.12, eggs=0.6, glow=1.2, legs={'hang': 0.6, 'raise': 0.4}, bob=-2),
        pose(abd=0.2, eggs=1.0, glow=1.5, legs={'hang': 0.5, 'raise': 0.5}, bob=-4, shake=0.4, phase=0.4),
        pose(abd=-0.06, eggs=0.4, glow=1.8, legs={'hang': 1.0}, bob=4, egg_out=0.2),
        pose(abd=-0.02, eggs=0.0, glow=1.2, bob=2, egg_out=1.0),
        pose(glow=0.8),
    ]

    spit = [
        pose(glow=0.8),
        pose(legs={'raise': 1.0}, bob=-10, squash=1.04, abd=0.1, fang=0.6, maw=0.5, spit=0.3, drool=0.3, glow=1.2, pin=0.4),
        pose(legs={'raise': 1.0}, bob=-14, squash=1.06, abd=0.14, fang=0.9, maw=0.9, spit=0.9, drool=0.6, glow=1.4, pin=0.4,
             shake=0.5, phase=0.3),
        pose(legs={'raise': 0.4, 'strike': 0.6}, bob=8, squash=0.9, abd=-0.06, fang=1.0, maw=1.0, spit=1.0, glob=10, glow=1.6, pin=0.4),
        pose(legs={'strike': 0.5, 'hang': 0.5}, bob=6, squash=0.96, abd=-0.02, fang=0.7, maw=0.5, spit=1.0, glob=70, drool=0.8,
             glow=1.2, pin=0.4),
        pose(bob=0, fang=0.2, drool=0.5, glow=0.9),
    ]

    def reel(t):
        # hauling her line in hand over hand with the front pair
        s, c = math.sin(t * tau), math.cos(t * tau)
        return pose(legs={'hang': 0.4, 'strike': 0.6}, pin=0.4, fang=0.5, glow=1.2, eyes=1.2,
                    offs=[(-50 + 10 * c, -40 + 44 * s), None, None, None, (-50 - 10 * c, -40 - 44 * s), None, None, None],
                    bob=2 * s, abd=0.03, phase=t)

    jolt = [
        pose(glow=0.8),
        pose(sway=14, tilt=0.16, glow=1.4, eyes=1.6, fang=0.6, offs=one(0, 20, 60), pin=0.3),
        pose(sway=-8, tilt=-0.1, glow=1.2, eyes=1.3, fang=0.4, offs=one(0, 10, 30), pin=0.4),
        pose(glow=0.9),
    ]

    def fall(t):
        return pose(legs={'raise': 0.6, 'dive': 0.4}, flail=t, flail_amp=1.2, fang=0.8, glow=1.1, pin=0.0, phase=t)

    land = [
        pose(legs={'sprawl': 1.0}, bob=8, squash=0.78, fang=0.8, glow=1.0, pin=1.0, eyes=1.2),
        pose(legs={'sprawl': 1.0}, bob=5, squash=0.9, fang=0.5, glow=0.7, pin=1.0, eyes=0.8),
        pose(legs={'sprawl': 1.0}, bob=4, squash=0.88, fang=0.3, glow=0.45, pin=1.0, eyes=0.5),
        pose(legs={'sprawl': 1.0}, bob=2, squash=0.92, fang=0.2, glow=0.35, pin=1.0, eyes=0.45),
    ]

    def downed(t):
        sn = math.sin(t * tau)
        return pose(legs={'sprawl': 1.0}, bob=2 + sn * 1.5, squash=0.92, fang=0.2 + 0.1 * sn, glow=0.35, eyes=0.45, pin=1.0,
                    knee=0.5, walk=t, walk_amp=(2, 3), tilt=0.03 * sn, phase=t)

    rise = [
        pose(legs={'sprawl': 1.0}, bob=2, squash=0.92, glow=0.35, eyes=0.45, pin=1.0, knee=0.5),
        pose(legs={'stand': 1.0}, bob=-6, glow=0.8, eyes=1.0, fang=0.4, pin=1.0),
        pose(legs={'stand': 1.0}, bob=4, squash=0.9, glow=1.0, eyes=1.0, fang=0.6, pin=1.0),
        pose(legs={'dive': 1.0}, bob=-10, squash=1.08, glow=1.1, eyes=1.0, fang=0.2, pin=0.0),
    ]

    def screech(t):
        return pose(legs={'raise': 1.0}, fang=0.9 + 0.1 * math.sin(t * tau), maw=0.9 + 0.1 * math.sin(t * tau),
                    glow=1.5 + 0.3 * math.sin(t * tau), shake=1.0, abd=0.06, eyes=1.6, bob=-4, pin=0.4,
                    flail=t, flail_amp=0.25, phase=t)

    death = [
        pose(legs={'raise': 1.0}, fang=1.0, maw=1.0, glow=1.4, cracks=0.2, pin=0.3),
        pose(legs={'raise': 0.4, 'curl': 0.6}, fang=0.7, maw=0.6, glow=1.2, cracks=0.4, eyes=0.8, bob=4, knee=1.4, pin=0.2),
        pose(legs={'curl': 1.0}, fang=0.3, glow=1.0, cracks=0.7, eyes=0.5, bob=8, knee=2.2, pin=0.0),
        pose(legs={'curl': 1.0}, fang=0.1, glow=1.8, cracks=1.0, eyes=0.3, bob=9, abd=0.06, knee=2.2, pin=0.0),
        pose(legs={'curl': 1.0}, fang=0.0, glow=2.6, cracks=1.0, eyes=0.1, bob=9, abd=0.12, knee=2.2, pin=0.0, shake=0.5, phase=0.5),
    ]
    return dict(idle=loop(hang, 12), descend=loop(descend, 8), crouch=keys_strip(crouch, 4), dive=loop(dive, 4),
                bite=keys_strip(bite, 8), dangle=loop(dangle, 8), climb=loop(climb, 8), weave=keys_strip(weave, 8),
                pluck=keys_strip(pluck, 8), brood=keys_strip(brood, 10), spit=keys_strip(spit, 10), reel=loop(reel, 8),
                jolt=keys_strip(jolt, 4), fall=loop(fall, 4), land=keys_strip(land, 4), downed=loop(downed, 8),
                rise=keys_strip(rise, 6), screech=loop(screech, 6), death=keys_strip(death, 12))


# =====================================================================================================
# the mite: a small void spider, side view, facing right
# =====================================================================================================
MITE_W, MITE_H, MAX_, MAY = 48, 40, 24, 37


def mite_frame(bob=0.0, legs=(0.0,) * 4, lift=(0.0,) * 4, fang=0.2, glow=0.9, crouch=0.0, air=False, phase=0.0):
    r = Rig(MITE_W, MITE_H, rim=0.3)
    by = bob + crouch * 3
    ab, th = (MAX_ - 7, MAY - 15 + by), (MAX_ + 5, MAY - 13 + by)
    feet_y = MAY

    def leg(i, near):
        root = (th[0] - 2 + i * 1.5, th[1] + 1)
        if air:
            tip = (th[0] + (12 if i < 2 else -14) + i * 2, th[1] + (-6 if i < 2 else 6))
        else:
            tip = (th[0] - 17 + i * 11 + legs[i] * (1 if near else -1) + (0 if near else -2), feet_y - lift[i])
        knee, end, _ = ik(root, tip, 10, 12, ((1 if i >= 2 else -1) * 0.3, -1))
        rmp = CHITIN if near else CHITIN_FAR
        r.part(limb(root[0], root[1], knee[0], knee[1], 1.4, 1.2), rmp, bias=0 if near else 0.1, edge=False)
        r.part(limb(knee[0], knee[1], end[0], end[1], 1.2, 0.6), rmp, bias=0 if near else 0.1, edge=False)

    for i in range(4):
        leg(i, False)
    r.part(ell(ab[0], ab[1], 8, 7), CHITIN, bias=0.02)
    r.part(ell(ab[0] - 2, ab[1] - 3, 3.5, 2), CHITIN_LIT, flat=0.5, edge=False)
    r.part(poly([(ab[0] - 2, ab[1] - 3), (ab[0] + 2, ab[1] - 3), (ab[0], ab[1])], 0.2), VOIDC, flat=clamp(0.5 - 0.3 * glow), edge=False)
    r.part(poly([(ab[0] - 2, ab[1] + 3), (ab[0] + 2, ab[1] + 3), (ab[0], ab[1])], 0.2), VOIDC, flat=clamp(0.5 - 0.3 * glow), edge=False)
    r.glow(ab[0], ab[1], 7, VOIDC[2], 0.3 * glow)
    r.part(ell(th[0], th[1], 6, 4.5), CHITIN)
    r.part(ell(th[0] + 5, th[1] + 1, 3.6, 3.4), CHITIN, bias=0.05)
    r.part(limb(th[0] + 7, th[1] + 3, th[0] + 8 + fang * 2, th[1] + 7, 1.3, 0.5), FANG)
    r.dots([(th[0] + 7, th[1] - 0.5), (th[0] + 8.5, th[1] + 0.5)], EYE[0])
    r.dot(th[0] + 5.5, th[1] - 1.5, EYE[1])
    r.glow(th[0] + 7.5, th[1], 4, VOIDC[1], 0.35)
    for i in range(4):
        leg(i, True)
    return r.render()


def mite_strips():
    tau = 2 * math.pi
    idle = [mite_frame(bob=math.sin(i / 4 * tau) * 0.6, glow=0.8 + 0.2 * math.sin(i / 4 * tau), phase=i / 4) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * tau
        sw = tuple(math.sin(p + k * math.pi / 2 + (k % 2) * math.pi) * 3 for k in range(4))
        lf = tuple(max(0.0, math.cos(p + k * math.pi / 2 + (k % 2) * math.pi)) * 2.5 for k in range(4))
        walk.append(mite_frame(bob=-abs(math.sin(p * 2)) * 0.8, legs=sw, lift=lf, fang=0.3, phase=i / 6))
    leap = [mite_frame(crouch=1.0, fang=0.8, glow=1.3), mite_frame(bob=-4, air=True, fang=1.0, glow=1.4)]
    return dict(idle=idle, walk=walk, leap=leap)


# =====================================================================================================
# egg sac, knot, web gate, acid
# =====================================================================================================
def egg_frame(t):
    r = Rig(28, 34, rim=0.2)
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    cx, cy = 14, 19
    r.part(ell(cx, cy, 10, 13 + pulse * 0.6), SILK_RAMP)
    for k in range(5):                                       # silk wrapping
        y = cy - 10 + k * 5
        half = 10 * math.sqrt(max(0.0, 1 - ((y - cy) / 13.5) ** 2))
        r.stroke(cx - half + 1, y + 1, cx + half - 1, y - 1, SILK_RAMP[3])
    for x, y, rad in ((-3, -3, 2.6), (3, 2, 2.1), (-1, 6, 1.7), (4, -6, 1.5)):   # something glowing inside
        r.part(ell(cx + x, cy + y, rad, rad), VOIDC, flat=clamp(0.65 - 0.4 * pulse), edge=False)
    r.glow(cx, cy, 10, VOIDC[2], 0.2 + 0.25 * pulse)
    r.stroke(cx, 2, cx, cy - 12, SILK_RAMP[1])              # the tether stub
    return r.render()


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

        for k, x0 in enumerate((3, 9, 15, 21)):
            for y in range(gh):
                x = int(round(x0 + math.sin(y * 0.15 + i * 1.57 + k) * 1.2))
                shimmer = 0.5 + 0.5 * math.sin(y * 0.3 - i * 1.57 + k * 2)
                put(x, y, (int(120 + 110 * shimmer), int(80 + 120 * shimmer), 255, 235))
        for k in range(3):
            y0 = 10 + k * 21 + (i % 2)
            for x in range(gw):
                y = int(round(y0 + 2.5 * math.sin(x / gw * math.pi)))
                put(x, y, (190, 160, 255, 200))
        for k in range(2):
            x, y = (5 + 11 * k + i * 3) % gw, (14 + 29 * k + i * 7) % gh
            put(x, y, (240, 230, 255, 255))
        frames.append(img)
    return frames


def acid_glob_frames():
    out = []
    for i in range(4):
        r = Rig(16, 16, rim=0.0)
        t = i / 4
        acid_blob(r, (8, 8.5), 4.2 + 0.4 * math.sin(t * 6.28), t)
        out.append(r.render())
    return out


def acid_pool_frames():
    """A 32x16 tile, seamless sideways: a shallow puddle of glowing acid in the bottom rows, bubbles rising."""
    tw, th = 32, 16
    out = []
    for i in range(4):
        img = Image.new('RGBA', (tw, th), (0, 0, 0, 0))
        px = img.load()
        t = i / 4
        for x in range(tw):
            surf = 10 + 1.2 * math.sin((x / tw) * 2 * math.pi * 2 + t * 6.28) + 0.6 * math.sin((x / tw) * 2 * math.pi * 3 - t * 6.28)
            for y in range(th):
                if y < surf - 0.5:
                    continue
                depth = (y - surf) / (th - surf)
                if y < surf + 1:
                    c = ACID[1]
                elif depth < 0.35:
                    c = ACID[2]
                elif depth < 0.75:
                    c = ACID[3] if (x + y) % 2 or depth > 0.55 else ACID[2]
                else:
                    c = ACID[4]
                px[x, y] = c
            # a faint haze above the surface
            for y in range(max(0, int(surf) - 4), int(surf)):
                a = int(70 * (1 - (surf - y) / 4))
                if a > 0 and px[x, y][3] == 0:
                    px[x, y] = (ACID[2][0], ACID[2][1], ACID[2][2], a)
        for k, (bx_, sp) in enumerate(((5, 1.0), (14, 1.3), (23, 0.8), (29, 1.15))):    # bubbles
            ph = (t * sp + k * 0.37) % 1
            by = int(12 - ph * 9)
            if ph < 0.85:
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                    x, y = bx_ + dx, by + dy
                    if 0 <= y < th:
                        px[x, y] = ACID[0] if dy == 0 else ACID[1]
        for x in (8, 20):
            px[x, 11] = ACID[0]
        out.append(img)
    return out


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
        out[f'DemonMatriarch_{name}'] = [matriarch_frame(P) for P in ps]
    for name, frames in mite_strips().items():
        out[f'DemonMite_{name}'] = frames
    out['DemonEgg_idle'] = [egg_frame(i / 4) for i in range(4)]
    out['DemonThread_idle'] = [thread_knot()]
    out['WebGate'] = web_gate_frames()
    out['AcidGlob'] = acid_glob_frames()
    out['AcidPool'] = acid_pool_frames()
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip(frames).save(f'{out_dir}/{name}.png')


def sheet(path, strips=None, only=None):
    from PIL import ImageDraw
    strips = strips or all_strips()
    rows = []
    for name, frames in strips.items():
        if only and only not in name:
            continue
        st = strip(frames)
        k = 1 if st.height > 100 else 3
        rows.append((name, st.resize((st.width * k, st.height * k), Image.NEAREST)))
    pad, lab = 8, 12
    width = max(r.width for _, r in rows) + pad * 2
    out = Image.new('RGBA', (width, sum(r.height + pad + lab for _, r in rows) + pad), (24, 10, 16, 255))
    d = ImageDraw.Draw(out)
    y = pad
    for name, r in rows:
        d.text((pad, y), name.replace('DemonMatriarch_', ''), fill=(220, 200, 240, 255))
        y += lab
        out.paste(Image.new('RGBA', r.size, (44, 18, 26, 255)), (pad, y))
        out.alpha_composite(r, (pad, y))
        y += r.height + pad
    out.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'generate':
        generate(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2], only=sys.argv[3] if len(sys.argv) > 3 else None)
    elif len(sys.argv) >= 4 and sys.argv[1] == 'frame':
        P = poses()[sys.argv[2]][int(sys.argv[3])]
        im = matriarch_frame(P)
        bg = Image.new('RGBA', im.size, (40, 14, 20, 255))
        bg.alpha_composite(im)
        bg.resize((W * 3, H * 3), Image.NEAREST).save(sys.argv[4] if len(sys.argv) > 4 else 'frame.png')
