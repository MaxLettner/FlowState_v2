"""The Pit Lord in 2D: the goat-skull executioner (picked 2026-11-21 from preview_lord2d_concepts.png, concept C).

A pose-driven version of the 2D demon Rig (demons.py): charred crimson muscle with ember runes, a goat skull with
black ram horns, goat legs, a tattered kilt, one spiked pauldron and a huge flamberge with a burning edge. Both hands
sit on the grip and the arms are solved with two-bone IK, so a pose only says where the sword is.
The legs are digitigrade and also solved with IK, from the hip to a hock above each hoof.

Frame 480x320, anchor (240, 309): the feet rest on row 308. Must match DemonType.LORD. Shown at x2 in game.
Strips (same names, key counts and frame counts the game's PitLordBehaviour drives):
  idle 8, walk 12, roar 4, charge 12, dazed 8 (loops)
  slam 6 keys/12, cast 4/8, sheathe 4/8, crash 2/4, draw 4/8, leap 3/6, stab 3/6, swing 6/12, death 5/10
  + DemonLord_cleaver: the dropped sword alone, stuck in the ground, in a full frame with the same anchor.
Fast strokes get a smear (an arc of hot light behind the blade) so the heavy blows read even at 2 frames.

    python tools/art/lord2d.py sheet out.png
"""
import math
import random
import sys
from PIL import Image
from pixel import OUTLINE, clamp, mix, ramp
from materials import BLACK_IRON, DARK_STEEL, BONE, LEATHER
from demons import Rig, ell, limb, chain, poly, polar, dark, EMBER

W, H = 480, 320
AX, AY = 240, 309                 # anchor: the ground line under the feet (pixel rows >= AY are underground)

CHAR = ramp('#b86a56', '#80383a', '#56222c', '#36141d', '#1d0910')
FUR = ramp('#5a4440', '#3e2e2c', '#2a1f1f', '#1b1314', '#0f0a0b')
LOIN = ramp('#c8a878', '#9a7a52', '#6e5236', '#463221', '#281a10')
HORN_D = ramp('#8c7e72', '#5e524a', '#3e3530', '#28211f', '#151110')
HOT = ramp('#ffffff', '#fff6c0', '#ffd25a', '#ff8a2a', '#c2300f')

BLADE_LEN, GRIP_LEN = 156, 36
NEAR_GRIP, FAR_GRIP = 10, 24       # hands on the grip, measured back from the guard
UPPER, FORE = 40, 40               # arm bones
THIGH, SHIN = 30, 29               # leg bones (hip -> knee -> hock), the pastern runs hock -> hoof
STUCK_TIP, STUCK_ANG, STUCK_SINK = (78, 8), 1.3, 0.3   # the dropped sword: tip (local), angle, part buried


def R(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c)


def A(p, q):
    return (p[0] + q[0], p[1] + q[1])


def S(p, k):
    return (p[0] * k, p[1] * k)


def L(p):
    """Anchor-local point -> frame pixels."""
    return (AX + p[0], AY + p[1])


def unit(a):
    return (math.cos(a), math.sin(a))


def ik(s, t, l1, l2, pref):
    """Two-bone IK: the joint between s and t (the solution leaning toward pref) and the end actually reached."""
    dx, dy = t[0] - s[0], t[1] - s[1]
    d0 = math.hypot(dx, dy) or 1e-6
    d = clamp(d0, abs(l1 - l2) + 0.5, l1 + l2 - 0.5)
    base = math.atan2(dy, dx)
    a = math.acos(clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1, 1))
    j1 = (s[0] + math.cos(base + a) * l1, s[1] + math.sin(base + a) * l1)
    j2 = (s[0] + math.cos(base - a) * l1, s[1] + math.sin(base - a) * l1)
    score = lambda j: (j[0] - s[0]) * pref[0] + (j[1] - s[1]) * pref[1]
    j = j1 if score(j1) >= score(j2) else j2
    end = (s[0] + dx / d0 * d, s[1] + dy / d0 * d)
    return j, end, d0 - d


def clip(shape):
    return {p: n for p, n in shape.items() if p[1] < AY}


# ---------------------------------------------------------------- blades
def line_pts(a, b, n):
    return [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n + 1)]


def _norms(center):
    n = len(center)
    out = []
    for i in range(n):
        a, b = center[min(i + 1, n - 1)], center[max(i - 1, 0)]
        dx, dy = a[0] - b[0], a[1] - b[1]
        l = math.hypot(dx, dy) or 1
        out.append((-dy / l, dx / l))
    return out


def side_points(center, half, side):
    return [(p[0] + nx * (hd if side > 0 else hu) * side, p[1] + ny * (hd if side > 0 else hu) * side)
            for p, (hu, hd), (nx, ny) in zip(center, half, _norms(center))]


def blade_zones(center, half, bevel=0.68):
    """A blade's pixels in 4 zones across it: (-1 bevel, -1 face, +1 face, +1 bevel) (diamond section)."""
    up, dn = side_points(center, half, -1), side_points(center, half, +1)
    raw = poly(up + dn[::-1])
    zones = [{}, {}, {}, {}]
    n = len(center)
    for (x, y) in raw:
        px_, py_ = x + 0.5, y + 0.5
        best = None
        for i in range(n - 1):
            ax, ay = center[i]
            bx, by = center[i + 1]
            dx, dy = bx - ax, by - ay
            l2 = dx * dx + dy * dy or 1
            t = clamp(((px_ - ax) * dx + (py_ - ay) * dy) / l2)
            d = math.hypot(px_ - ax - dx * t, py_ - ay - dy * t)
            if best is None or d < best[0]:
                s = (dx * (py_ - ay) - dy * (px_ - ax)) / math.sqrt(l2)
                hu = half[i][0] + (half[i + 1][0] - half[i][0]) * t
                hd = half[i][1] + (half[i + 1][1] - half[i][1]) * t
                best = (d, s, hu, hd)
        _, s, hu, hd = best
        rel = s / max(hd if s > 0 else hu, 0.5)
        z = (0 if abs(rel) > bevel else 1) if s <= 0 else (2 if rel <= bevel else 3)
        zones[z][(x, y)] = (0, 0)
    return zones


def polyline(r, pts, c):
    for a, b in zip(pts, pts[1:]):
        if a[1] < AY - 0.5 and b[1] < AY - 0.5:
            r.stroke(a[0], a[1], b[0], b[1], c)


def flamberge(r, G, ang, phase, heat, fore=1.0, flames=True):
    """The sword: guard at G (frame px), blade toward ang. fore < 1 foreshortens it (pointing at or away from us)."""
    d = unit(ang)
    ln = BLADE_LEN * fore * (1 - 0.42 * max(0.0, -math.sin(ang)) ** 4)   # over the top it tips toward us
    n = 22
    center = line_pts(A(G, S(d, 2)), A(G, S(d, ln)), n)
    half = []
    for i in range(n + 1):
        t = i / n
        w = 12 + 2.4 * math.sin(t * 7 * math.pi)
        if t > 0.86:
            w *= (1 - t) / 0.14
        half.append((max(w, 0.5), max(w, 0.5)))
    # the lit face: the side whose normal faces the key light (upper left)
    lit = 1 if 0.5 * math.sin(ang) - 0.75 * math.cos(ang) > 0 else -1
    zones = blade_zones(center, half)
    if lit > 0:
        zones = zones[::-1]
    for k, (z, v) in enumerate(zip(zones, (0.0, 0.28, 0.62, 0.9))):
        r.part(clip(z), DARK_STEEL, flat=v, edge=(k == 0))
    polyline(r, line_pts(A(G, S(d, 10)), A(G, S(d, ln * 0.84)), 10), DARK_STEEL[4])       # fuller
    up_side = 1 if math.cos(ang) < 0 else -1                                              # the edge facing up
    top = side_points(center, half, up_side)
    polyline(r, side_points(center, half, -up_side)[2:-1], DARK_STEEL[0])
    polyline(r, top[2:-1], EMBER[2] if heat < 1.2 else EMBER[1])
    if flames and abs(math.cos(ang)) > 0.28:
        for k, p in enumerate(top[3:-2:2]):
            if p[1] > AY - 6:
                continue
            h = (6 + (k % 3) * 3.5) * (0.7 + 0.5 * min(heat, 1.6))
            sway = math.sin(phase * 6.28 + k * 1.3) * 2.2
            tp = (p[0] + sway + (2 if up_side > 0 else -2) * 0.6, p[1] - h)
            mid = (p[0] + sway * 0.4, p[1] - h * 0.5)
            r.part(clip(chain([(p[0], p[1] + 2), mid, tp], 3.2, 0.4)), EMBER, flat=0.75, edge=False)
            r.part(clip(chain([(p[0], p[1] + 2), mid], 1.9, 0.8)), EMBER, flat=0.3, edge=False)
        for p in top[3:-2:4]:
            if p[1] < AY - 6:
                r.glow(p[0], p[1] - 3, 7, EMBER[1], 0.3 + 0.15 * min(heat, 1.5))
    # where it bites into the ground: a scar of rubble
    tip = A(G, S(d, ln))
    if tip[1] > AY and d[1] > 0.2:
        t = (AY - G[1]) / d[1]
        x = G[0] + d[0] * t
        r.part(ell(x, AY - 2, 13, 3.5), dark(FUR, 0.1), bias=0.1)
        for k in range(-3, 4):
            r.dot(x + k * 3.5, AY - 3 - abs(k % 2) * 2, FUR[0] if k % 2 else HOT[3])
        r.glow(x, AY - 2, 10, EMBER[2], 0.45)


def hilt(r, G, ang, heat, fore=1.0):
    d = unit(ang)
    gl = GRIP_LEN * max(fore, 0.55)
    gend = A(G, S(d, -gl))
    r.part(clip(limb(G[0], G[1], gend[0], gend[1], 4, 4)), LEATHER)
    for k in range(1, 6):
        p = A(G, S(d, -gl * k / 6))
        nx, ny = -d[1], d[0]
        r.stroke(p[0] - nx * 4, p[1] - ny * 4, p[0] + nx * 4 + d[0] * 2, p[1] + ny * 4 + d[1] * 2, LEATHER[3])
    r.part(clip(ell(gend[0], gend[1], 6, 6)), BLACK_IRON)
    g1, g2 = A(G, R((0, -22), ang)), A(G, R((0, 22), ang))
    r.part(clip(chain([A(g1, S(d, -5)), G, A(g2, S(d, -5))], 5, 3)), BLACK_IRON)
    r.part(clip(ell(G[0], G[1], 5, 5)), EMBER, flat=0.05 if heat > 0.5 else 0.35, edge=False)
    r.glow(G[0], G[1], 8, EMBER[1], 0.35 + 0.2 * min(heat, 1.5))


def sword(r, G, ang, phase, heat, fore=1.0):
    flamberge(r, G, ang, phase, heat, fore)
    hilt(r, G, ang, heat, fore)


def smear_layer(sm):
    """An arc of hot light where the blade has just been: an elliptical band from angle a0 to a1 around c."""
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    if not sm:
        return img
    px = img.load()
    cx, cy = sm['c']
    rx, ry = sm['r']
    rin = sm.get('rin', 0.66)
    a0, a1 = sm['a0'], sm['a1']
    span = a1 - a0
    sgn = 1 if span > 0 else -1
    for y in range(max(0, int(cy - ry) - 1), min(H, int(cy + ry) + 2)):
        for x in range(max(0, int(cx - rx) - 1), min(W, int(cx + rx) + 2)):
            if y >= AY:
                continue
            u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            rho = math.hypot(u, v)
            if rho < rin or rho > 1:
                continue
            th = math.atan2(v, u)
            f = ((th - a0) * sgn) % (2 * math.pi) / abs(span)
            if f > 1:
                continue
            if f < 0.4:
                continue
            k = ((f - 0.4) / 0.6) ** 1.3 * (0.6 + 0.4 * (rho - rin) / (1 - rin))
            a = round(k * 6) / 6
            if a <= 0.02 or ((x + y) % 2 and a < 0.34):
                continue
            c = mix(EMBER[1], HOT[1], a)
            px[x, y] = (c[0], c[1], c[2], int(110 + 145 * a))
    return img


# ---------------------------------------------------------------- the body
DEFAULT = dict(bob=0.0, sx=0.0, lean=0.0, hunch=0.0, head=0.0, jaw=0.0,
               near_foot=(4, -5), far_foot=(-26, -5), lift=(0.0, 0.0), kneel=0.0,
               sword='hand', G=(42, -58), ang=-2.41, fore=1.0, layer='mid',
               near_hand=None, far_hand=None, one_hand=False,
               elbow_near=(-0.3, 1.0), elbow_far=(-0.3, 1.0),
               smear=None, fire=0.0, heat=0.5, cracks=0.0, phase=0.0)

RUNES_TORSO = [[(4, -38), (10, -30), (4, -20), (10, -12)], [(-14, -66), (-8, -56), (-16, -46), (-10, -36)],
               [(18, -70), (24, -62), (20, -54)]]


def rune_col(heat):
    return HOT[0] if heat > 1.8 else EMBER[0] if heat > 1.3 else EMBER[1] if heat > 0.75 else EMBER[2] if heat > 0.35 else EMBER[3]


def lord_frame(P, report=None):
    P = {**DEFAULT, **P}
    r = Rig(W, H, rim=0.55)
    heat, phase = P['heat'], P['phase']
    rc = rune_col(heat)
    O = L((-4 + P['sx'], -72 + P['bob']))
    lean, hunch = P['lean'], P['hunch']
    up = lean + hunch
    WAIST = (0, -30)

    def TL(p):
        return A(O, R(p, lean))

    def TU(p):
        return A(O, R(A(R((p[0] - WAIST[0], p[1] - WAIST[1]), hunch), WAIST), lean))

    def runes(pts, T=TU):
        q = [T(p) for p in pts]
        polyline(r, q, rc)
        r.dot(q[0][0], q[0][1], HOT[1] if heat > 0.75 else EMBER[1])

    # ---- the sword and the hands
    mode = P['sword']
    if mode == 'hand':
        G, ang = A(O, P['G']), P['ang']
    elif mode == 'back':
        G, ang = TU((-16, -84)), 2.45 + up * 0.35
    else:
        d0 = unit(STUCK_ANG)
        G, ang = L(A(STUCK_TIP, S(d0, -BLADE_LEN * (1 - STUCK_SINK)))), STUCK_ANG
    d = unit(ang)
    fore = P['fore'] if mode == 'hand' else 1.0
    grip_near, grip_far = A(G, S(d, -NEAR_GRIP * max(fore, 0.55))), A(G, S(d, -FAR_GRIP * max(fore, 0.55)))
    rest_near, rest_far = A(O, (34, -34)), A(O, (14, -28))
    near_hand = A(O, P['near_hand']) if P['near_hand'] else grip_near if mode == 'hand' else rest_near
    far_hand = A(O, P['far_hand']) if P['far_hand'] else (grip_far if mode == 'hand' and not P['one_hand'] else rest_far)
    sh_near, sh_far = TU((12, -76)), TU((-8, -74))
    el_n, hand_n, miss_n = ik(sh_near, near_hand, UPPER, FORE, P['elbow_near'])
    el_f, hand_f, miss_f = ik(sh_far, far_hand, UPPER, FORE, P['elbow_far'])
    if report is not None and (miss_n > 1.5 or miss_f > 1.5):
        report.append((round(miss_n, 1), round(miss_f, 1)))

    def draw_sword():
        if mode == 'hand':
            sword(r, G, ang, phase, heat, fore)
        else:
            sword(r, G, ang, phase, heat * (0.6 if mode == 'back' else 1))

    # ---- legs
    def leg(hip, foot, lift, near):
        hoof = L((foot[0], foot[1] - lift))
        kn = P['kneel']
        past = (-4 * (1 - kn) - 18 * kn, -16 * (1 - kn) - 2 * kn)
        if lift > 4:
            past = R(past, -0.35 * min(1, lift / 12))
        hock = A(hoof, past)
        knee, hock2, _ = ik(hip, hock, THIGH, SHIN, (1.0, 0.15))
        fur = FUR if near else dark(FUR, 0.25)
        skin = CHAR if near else dark(CHAR, 0.3)
        b = 0.0 if near else 0.1
        r.part(limb(hip[0], hip[1], knee[0], knee[1], 16 if near else 14, 12 if near else 11), fur, bias=b)
        if near:
            for k in range(4):
                p = A(hip, S((knee[0] - hip[0], knee[1] - hip[1]), 0.2 + k * 0.2))
                r.stroke(p[0] + 4, p[1] - 4, p[0] - 3, p[1] + 5, FUR[3])
        r.part(limb(knee[0], knee[1], hock2[0], hock2[1], 10 if near else 9, 7), skin, bias=b)
        r.part(limb(hock2[0], hock2[1], hoof[0], hoof[1], 7, 5), skin, bias=b)
        hx, hy = hoof
        r.part(poly([(hx - 7, hy - 3), (hx + 8, hy - 3), (hx + 10, hy + 5), (hx - 8, hy + 5)], 0.3), BLACK_IRON, bias=0.0 if near else 0.2)
        if near:
            polyline(r, [A(knee, (-2, 4)), A(knee, S((hock2[0] - knee[0], hock2[1] - knee[1]), 0.5)), hock2], rc)

    # ---- arms
    def arm(sh, el, hand, near, open_hand=False):
        skin = CHAR if near else dark(CHAR, 0.3)
        b = 0.0 if near else 0.1
        r.part(limb(sh[0], sh[1], el[0], el[1], 12 if near else 11, 10 if near else 9), skin, bias=b)
        if near:
            mid = A(sh, S((el[0] - sh[0], el[1] - sh[1]), 0.5))
            r.part(ell(mid[0], mid[1], 9, 12, math.atan2(el[1] - sh[1], el[0] - sh[0]) - math.pi / 2), skin, bias=-0.1, edge=False)
        r.part(limb(el[0], el[1], hand[0], hand[1], 10 if near else 9, 8), skin, bias=b)
        if near:
            runes([A(el, (-2, -6)), A(el, S((hand[0] - el[0], hand[1] - el[1]), 0.45)), A(el, S((hand[0] - el[0], hand[1] - el[1]), 0.7))], T=lambda p: p)
            for k in range(3):
                p = A(el, S((hand[0] - el[0], hand[1] - el[1]), 0.72 + k * 0.07))
                fa = math.atan2(hand[1] - el[1], hand[0] - el[0]) + math.pi / 2
                r.stroke(p[0] - math.cos(fa) * 8, p[1] - math.sin(fa) * 8, p[0] + math.cos(fa) * 8, p[1] + math.sin(fa) * 8, LEATHER[2])
        r.part(ell(hand[0], hand[1], 10 if near else 9, 9 if near else 8), skin, bias=-0.05 if near else 0.1)
        if open_hand:
            fa = math.atan2(hand[1] - el[1], hand[0] - el[0])
            for k in (-0.5, 0, 0.5):
                tip = polar(hand, 13, fa + k)
                r.part(limb(hand[0], hand[1], tip[0], tip[1], 3, 2), skin, bias=0.05 if near else 0.15)

    sword_free = mode != 'hand'
    back_layer = mode == 'back' or P['layer'] == 'back'

    # ---- draw, back to front
    if back_layer:
        draw_sword()
    hip_far, hip_near = TL((-18, 4)), TL((4, 4))
    leg(hip_far, P['far_foot'], P['lift'][1], False)
    r.part(ell(sh_far[0], sh_far[1], 13, 11), dark(CHAR, 0.3), bias=0.1)
    arm(sh_far, el_f, hand_f, False, open_hand=bool(P['far_hand']) and P['fire'] <= 0 and (sword_free or P['one_hand']))
    flap_top = [TL(p) for p in ((-28, 0), (-6, 0))]
    sway = math.sin(phase * 6.28) * 1.5
    r.part(poly([flap_top[0], flap_top[1], A(flap_top[1], (-6 + sway, 40)), A(flap_top[1], (-14 + sway, 50)),
                 A(flap_top[0], (2 + sway, 36)), A(flap_top[0], (-4 + sway, 44)), A(flap_top[0], (-6, 18))], 0.35),
           dark(LOIN, 0.2), bias=0.1)
    # torso
    r.part(ell(*TU((-6, -48)), 24, 32, 0.15 + up), CHAR, bias=0.12)          # lats
    r.part(ell(*TU((2, -84)), 16, 9, -0.3 + up), CHAR, bias=0.05)            # traps
    r.part(ell(*TL((0, -12)), 20, 14, lean), CHAR, bias=0.05)                # waist
    r.part(ell(*TU((6, -34)), 19, 22, up), CHAR)                             # abs
    for y in (-42, -32, -22):
        a, b_ = TU((12, y)), TU((22, y + 1))
        r.stroke(a[0], a[1], b_[0], b_[1], CHAR[3])
    a, b_ = TU((14, -46)), TU((16, -14))
    r.stroke(a[0], a[1], b_[0], b_[1], CHAR[3])
    r.part(ell(*TU((14, -64)), 24, 18, -0.25 + up), CHAR)                    # chest
    a, b_ = TU((-2, -50)), TU((32, -56))
    r.stroke(a[0], a[1], b_[0], b_[1], CHAR[3])
    for pts in RUNES_TORSO:
        runes(pts)
    r.glow(*TU((8, -34)), 10, EMBER[1], 0.2 + 0.2 * min(heat, 1.5))
    if P['cracks'] > 0:
        rnd = random.Random(9)
        for k in range(1 + int(P['cracks'] * 4)):
            st = TU((rnd.uniform(-8, 20), rnd.uniform(-70, -20)))
            ln_ = 14 + 26 * P['cracks']
            x, y, a_ = st[0], st[1], rnd.uniform(0, 6.28)
            for i in range(int(ln_)):
                a_ += rnd.uniform(-0.5, 0.5)
                x, y = x + math.cos(a_), y + math.sin(a_)
                if (int(x), int(y)) in r.owner:
                    r.dot(x, y, HOT[1] if i < ln_ * 0.4 else EMBER[1])
        r.glow(*TU((8, -44)), 16 + 14 * P['cracks'], EMBER[1], 0.3 + 0.4 * P['cracks'])
    # near leg, front flap, belt, chains
    leg(hip_near, P['near_foot'], P['lift'][0], True)
    ft = [TL(p) for p in ((4, 2), (26, 2))]
    r.part(poly([ft[0], ft[1], A(ft[1], (6 + sway, 38)), A(ft[1], (-4 + sway, 32)), A(ft[1], (-10 + sway, 48)),
                 A(ft[0], (4 + sway, 32)), A(ft[0], (-2 + sway, 40))], 0.35), LOIN)
    a, b_ = TL((14, 8)), A(TL((14, 8)), (3 + sway, 30))
    r.stroke(a[0], a[1], b_[0], b_[1], LOIN[3])
    a, b_ = TL((-22, 0)), TL((28, 2))
    r.part(limb(a[0], a[1], b_[0], b_[1], 4, 4), FUR, bias=0.1)
    r.part(ell(*TL((24, 2)), 5, 5), BLACK_IRON, bias=-0.1)
    for k in range(8):
        p = A(TL((-22 + k * 6, 6)), (0, 7 * math.sin(k / 7 * math.pi)))
        r.part(ell(p[0], p[1], 3, 2.2), BLACK_IRON, bias=-0.1)
    if mode == 'back':                                                        # the scabbard strap across the chest
        a, b_ = TU((18, -78)), TL((-18, -6))
        r.part(limb(a[0], a[1], b_[0], b_[1], 3, 3), LEATHER, bias=0.1)
        r.part(ell(*TU((4, -50)), 4, 4), BLACK_IRON)
    # near shoulder: deltoid and the spiked pauldron
    r.part(ell(*sh_near, 15, 12, -0.3 + up), CHAR)
    for base, a_, ln_, rb in (((0, -88), -2.1, 14, 4), ((10, -92), -1.75, 12, 3.5)):
        r.part(limb(*TU(base), *polar(TU(base), ln_, a_ + up), rb, 0.4), BLACK_IRON, bias=-0.1)
    r.part(ell(*TU((8, -84)), 19, 11, -0.3 + up), BLACK_IRON)
    a, b_ = TU((-6, -80)), TU((24, -70))
    r.part(limb(a[0], a[1], b_[0], b_[1], 2.5, 2.5), LEATHER)
    if not back_layer and P['layer'] == 'mid':
        draw_sword()
    # head: goat skull + black ram horns
    ha = up + P['head']
    N = TU((24, -86))
    HC = A(N, R((6, -14), ha))

    def TH(p):
        return A(HC, R(p, ha))

    far_spiral = []
    cx, cy = 8, -6
    for i in range(12):
        t = i / 11
        far_spiral.append(TH((cx + 11 * math.cos(-1.5 - t * 3.6), cy + (9 + 7 * math.sin(t * 2.8)) * math.sin(-1.5 - t * 3.6))))
    r.part(chain(far_spiral, 6.5, 2), dark(HORN_D, 0.35), bias=0.15)
    nk = TH((-4, 16))
    r.part(ell(nk[0], nk[1], 12, 9, ha), CHAR, bias=0.1)
    r.part(ell(*TH((0, 0)), 15, 14, ha), BONE)
    r.part(poly([TH(p) for p in ((4, -6), (28, 4), (27, 12), (6, 13))], 0.4), BONE, bias=0.05)
    jaw_a = P['jaw'] * 0.55
    J = TH((6, 13))

    def TJ(p):
        return A(J, R(R((p[0] - 6, p[1] - 13), jaw_a), ha))

    if P['jaw'] > 0.05:                                                       # the maw glows inside
        r.part(poly([TH((6, 12)), TH((26, 12)), TJ((22, 13)), TJ((6, 14))], 0.2), EMBER, flat=0.35 + 0.3 * (1 - min(heat, 1)), edge=False)
        r.glow(*TH((16, 14)), 9 + 5 * P['jaw'], EMBER[1], 0.35 + 0.25 * P['jaw'])
    r.part(poly([TJ(p) for p in ((6, 13), (24, 12), (20, 21), (6, 19))], 0.4), BONE, bias=0.3)
    for k in range(5):
        r.dot(*TH((9 + k * 3, 13)), BONE[4])
    so = TH((7, 1))
    r.part(ell(so[0], so[1], 5, 4.5, ha), [OUTLINE], flat=0, edge=False)
    ec = HOT[0] if heat > 1.3 else EMBER[0]
    r.dots([TH((7, 1)), TH((8, 1))], ec)
    r.dots([TH((6, 2)), TH((9, 2))], EMBER[2])
    r.glow(*TH((7.5, 1.5)), 7 + 3 * min(heat, 1.5), EMBER[1], 0.45 + 0.2 * min(heat, 1.5))
    r.dot(*TH((26, 6)), BONE[4])
    a, b_ = TH((-6, -10)), TH((2, 8))
    r.stroke(a[0], a[1], b_[0], b_[1], BONE[3])
    spiral = []
    for i in range(15):
        t = i / 14
        spiral.append(TH(polar((-10, -2), 11 + 9 * math.sin(t * math.pi * 0.9), -1.5 - t * 4.6)))
    r.part(chain(spiral, 8.5, 2.2), HORN_D)
    for p, q in zip(spiral[2:-3:2], spiral[3:-2:2]):
        dx, dy = q[0] - p[0], q[1] - p[1]
        l = math.hypot(dx, dy) or 1
        r.stroke(p[0] - dy / l * 6, p[1] + dx / l * 6, p[0] + dy / l * 6, p[1] - dx / l * 6, HORN_D[3])
    if P['layer'] == 'front':
        draw_sword()
    # near arm over the grip
    arm(sh_near, el_n, hand_n, True, open_hand=bool(P['near_hand']) and sword_free)
    if P['fire'] > 0:
        fx, fy = hand_f[0], hand_f[1] - P['fire'] * 0.6
        rad = P['fire']
        r.part(ell(fx, fy, rad + 2, rad + 2), EMBER, flat=0.8, edge=False)
        r.part(ell(fx, fy, rad, rad), EMBER, flat=0.45, edge=False)
        r.part(ell(fx - rad * 0.2, fy - rad * 0.2, rad * 0.55, rad * 0.55), HOT, flat=0.1, edge=False)
        for k in range(5):
            a_ = phase * 6.28 + k * 1.26
            p = polar((fx, fy), rad + 3, a_)
            r.part(chain([p, polar(p, 4 + rad * 0.4, -1.57 + math.sin(a_) * 0.4)], 2, 0.4), EMBER, flat=0.6, edge=False)
        r.glow(fx, fy, rad * 2.4 + 6, EMBER[1], 0.6)
    img = r.render()
    base = smear_layer(P['smear'] and {**P['smear'], 'c': A(O, P['smear']['c'])})
    base.alpha_composite(img)
    return base


def stuck_sword_prop():
    r = Rig(W, H, rim=0.55)
    d0 = unit(STUCK_ANG)
    G = L(A(STUCK_TIP, S(d0, -BLADE_LEN * (1 - STUCK_SINK))))
    sword(r, G, STUCK_ANG, 0.0, 0.25)
    return r.render()


# ---------------------------------------------------------------- poses
REST = dict(G=(42, -58), ang=-2.41)          # on the shoulder (idle, walk)


def keys_strip(keys, frames):
    n = len(keys)
    out = []
    for i in range(frames):
        kp = i * (n - 1) / (frames - 1)
        k = min(n - 2, int(kp))
        out.append(lerp_pose(keys[k], keys[k + 1], kp - k))
    return out


def lerp_pose(a, b, t):
    a, b = {**DEFAULT, **a}, {**DEFAULT, **b}
    out = {}
    for k in DEFAULT:
        x, y = a[k], b[k]
        if k == 'ang':
            dd = (y - x + math.pi) % (2 * math.pi) - math.pi        # the short way round
            if a.get('spin'):
                dd = a['spin']
            out[k] = x + dd * t ** a.get('ang_ease', 1)
        elif isinstance(x, (int, float)) and isinstance(y, (int, float)) and not isinstance(x, bool):
            out[k] = x + (y - x) * t
        elif isinstance(x, tuple) and isinstance(y, tuple):
            out[k] = tuple(p + (q - p) * t for p, q in zip(x, y))
        elif x is None and isinstance(y, tuple) or y is None and isinstance(x, tuple):
            out[k] = (x if t < 0.5 else y)
        else:
            out[k] = x if t < 0.5 else y
    out['smear'] = a['smear'] if t < 0.5 else b['smear']
    return out


def loop_strip(fn, frames):
    return [fn(i / frames) for i in range(frames)]


def lord_poses():
    tau = 2 * math.pi

    def idle(t):
        sn = math.sin(t * tau)
        return dict(REST, bob=1.2 * sn, head=0.03 * sn, G=(42, -58 + sn), heat=0.45 + 0.15 * (sn + 1) / 2, phase=t)

    def walk(t):
        p = t * tau
        s, c = math.sin(p), math.cos(p)
        return dict(REST, bob=3 * abs(s) - 1.5, lean=0.06, near_foot=(4 + 16 * s, -5), far_foot=(-26 - 16 * s, -5),
                    lift=(max(0, c) * 8, max(0, -c) * 8), G=(42, -58 + 3 * abs(s) * 0.4), ang=-2.41 + s * 0.04, phase=t)

    # the overhead chop: off the shoulder, up and back (hold), over the top, bites deep into the ground in front
    raised = dict(lean=-0.22, head=-0.25, bob=-2, G=(10, -128), ang=2.6, layer='back', elbow_near=(0.6, -1),
                  elbow_far=(0.4, -1), jaw=1.0, heat=1.1, near_foot=(8, -5), far_foot=(-30, -5))
    chop = dict(lean=0.42, hunch=0.18, bob=16, G=(104, -48), ang=0.98, near_foot=(14, -5), far_foot=(-40, -5),
                jaw=0.9, heat=1.2, elbow_near=(1, 0.3), elbow_far=(1, 0.3))
    slam = [
        dict(lean=-0.05, G=(36, -80), ang=-2.6, jaw=0.3, heat=0.7, phase=0.1, elbow_near=(0.6, -1)),
        dict(raised, phase=0.3),
        dict(lean=0.18, bob=2, G=(66, -92), ang=-0.8, jaw=1.0, heat=1.2, phase=0.5, elbow_near=(1, -0.3), elbow_far=(1, -0.3),
             near_foot=(10, -5), far_foot=(-34, -5), smear=dict(c=(10, -80), r=(120, 120), a0=2.6, a1=5.48)),
        dict(chop, phase=0.6, smear=dict(c=(20, -70), r=(150, 150), a0=-1.4, a1=0.95)),
        dict(chop, bob=18, lean=0.45, heat=1.0, jaw=0.6, phase=0.8, spin=-3.39, ang_ease=1.5),   # pulled up the front, not under
        dict(REST, lean=0.08, bob=4, jaw=0.2, heat=0.6, phase=0.95),
    ]
    # the far hand comes up behind the head with a growing fireball and hurls it; the sword stays on the shoulder
    one = dict(REST, one_hand=True)
    cast = [
        dict(one, far_hand=(-24, -40), fire=3, heat=0.6, phase=0.1, elbow_far=(-1, 0.2)),
        dict(one, lean=-0.08, far_hand=(-34, -118), fire=6, jaw=0.4, heat=0.8, phase=0.3, elbow_far=(-1, 0.3)),
        dict(one, lean=-0.15, head=-0.3, far_hand=(-14, -150), fire=9, jaw=0.7, heat=1.1, phase=0.5, elbow_far=(-1, 0)),
        dict(one, lean=0.28, far_hand=(78, -96), fire=0, jaw=0.6, heat=0.9, phase=0.7, elbow_far=(0, 1)),
    ]

    def roar(t):
        sn = math.sin(t * tau)
        return dict(lean=-0.2, head=-0.6 + 0.04 * sn, jaw=0.9 + 0.1 * sn, G=(36, -16), ang=2.0, one_hand=True,
                    far_hand=(-44, -112), elbow_far=(-1, 0.2), heat=1.2, phase=t, bob=math.sin(t * tau * 2) * 0.8)

    paw = dict(lean=0.45, hunch=0.5, head=0.75, bob=10, near_foot=(16, -5), far_foot=(-42, -5), sword='back',
               near_hand=(52, -22), far_hand=(32, -12), jaw=0.5, heat=0.9, elbow_near=(0, 1), elbow_far=(0, 1))
    sheathe = [
        dict(lean=-0.05, G=(30, -112), ang=-2.0, heat=0.6, phase=0.1, elbow_near=(0.6, -1), elbow_far=(0.4, -1)),
        dict(lean=-0.12, head=-0.2, G=(-6, -132), ang=2.3, layer='back', heat=0.6, phase=0.3, elbow_near=(0.6, -1), elbow_far=(0.2, -1)),
        dict(sword='back', near_hand=(36, -40), far_hand=(12, -32), jaw=0.3, heat=0.6, phase=0.5),
        dict(paw, phase=0.7),
    ]

    def charge(t):
        p = t * tau
        s, c = math.sin(p), math.cos(p)
        return dict(paw, lean=0.5, hunch=0.55, head=0.85, bob=6 - 3 * abs(c), near_foot=(6 + 26 * s, -5),
                    far_foot=(-28 - 26 * s, -5), lift=(max(0, c) * 14, max(0, -c) * 14),
                    near_hand=(46 + 18 * s, -28 + 6 * c), far_hand=(24 - 18 * s, -22), jaw=0.6, heat=1.0, phase=t)

    crash = [
        dict(paw, lean=0.65, hunch=0.6, head=0.95, bob=12, near_foot=(22, -5), far_foot=(-46, -5),
             near_hand=(64, -34), far_hand=(48, -24), jaw=1.0, heat=1.1, phase=0.2),
        dict(paw, lean=-0.35, hunch=-0.1, head=-0.5, bob=4, near_foot=(-2, -5), far_foot=(-30, -5),
             near_hand=(30, -142), far_hand=(-12, -150), elbow_near=(0.5, -1), elbow_far=(-0.5, -1), jaw=0.8, heat=0.7, phase=0.5),
    ]

    def dazed(t):
        sn, cs = math.sin(t * tau), math.cos(t * tau)
        return dict(sword='back', lean=0.1 + 0.12 * sn, hunch=0.3 + 0.08 * cs, head=0.45 + 0.1 * sn, bob=4 + 1.5 * sn,
                    near_hand=(30 + 4 * sn, 12), far_hand=(10, 16), jaw=0.35, heat=0.25, phase=t)

    draw = [
        dict(sword='back', near_hand=(-6, -112), far_hand=(20, -30), elbow_near=(1, -0.6), heat=0.4, phase=0.1),
        dict(lean=-0.12, head=-0.15, G=(-10, -124), ang=2.4, layer='back', heat=0.6, phase=0.3, elbow_near=(0.6, -1), elbow_far=(0.2, -1)),
        dict(lean=-0.2, bob=-1, G=(28, -116), ang=-1.9, jaw=0.8, head=-0.3, heat=0.9, phase=0.5, elbow_near=(0.6, -1),
             elbow_far=(0.4, -1), smear=dict(c=(0, -96), r=(110, 110), a0=2.4, a1=4.38)),
        dict(lean=0.22, G=(60, -40), ang=0.35, jaw=1.0, head=-0.2, heat=1.0, phase=0.7, near_foot=(12, -5), far_foot=(-34, -5),
             elbow_near=(0.2, 1), smear=dict(c=(10, -70), r=(170, 140), a0=-1.9, a1=0.35)),
    ]
    leap = [
        dict(raised, lean=-0.05, hunch=0.15, bob=12, near_foot=(14, -5), far_foot=(-40, -5), jaw=0.5, phase=0.1),
        dict(raised, bob=-6, lift=(14, 10), near_foot=(14, -5), far_foot=(-14, -5), lean=-0.2, G=(8, -130), ang=2.7, phase=0.4),
        dict(raised, bob=-2, lift=(6, 4), lean=0.05, G=(36, -112), ang=-2.2, layer='mid', jaw=0.9, phase=0.7),
    ]
    plunge = dict(lean=0.35, hunch=0.2, bob=14, G=(82, -44), ang=1.5, near_foot=(14, -5), far_foot=(-38, -5),
                  jaw=1.0, heat=1.2, elbow_near=(0.3, -1), elbow_far=(0.3, -1))
    stab = [
        dict(lean=-0.1, bob=1, G=(34, -116), ang=1.45, layer='front', jaw=0.5, head=-0.2, heat=0.8, phase=0.1,
             elbow_near=(0.6, -1), elbow_far=(0.4, -1)),
        dict(plunge, phase=0.4),
        dict(plunge, bob=15, jaw=0.6, heat=1.5, phase=0.8),
    ]
    # the sideways swing: wound back behind him at hip height, around past us, through the front, follow through
    ready = dict(REST, near_foot=(10, -5), far_foot=(-32, -5), heat=0.6)
    swing = [
        ready,
        dict(lean=-0.15, hunch=-0.1, bob=8, G=(-28, -2), ang=3.0, layer='back', near_foot=(12, -5), far_foot=(-36, -5),
             elbow_near=(0, 1), elbow_far=(0, 1), jaw=0.5, heat=0.9, phase=0.2),
        dict(lean=0.05, bob=9, G=(28, 4), ang=1.5, fore=0.38, layer='front', near_foot=(14, -5), far_foot=(-38, -5),
             elbow_near=(0, 1), elbow_far=(0, 1), jaw=1.0, heat=1.2, phase=0.35,
             smear=dict(c=(10, 4), r=(190, 40), a0=-3.0, a1=-5.0, rin=0.55)),
        dict(lean=0.3, bob=9, G=(44, 0), ang=0.02, near_foot=(16, -5), far_foot=(-40, -5),
             elbow_near=(0.3, 1), elbow_far=(0.3, 1), jaw=1.0, heat=1.2, phase=0.45,
             smear=dict(c=(10, 4), r=(196, 40), a0=-3.0, a1=-6.26, rin=0.55)),
        dict(lean=0.34, bob=8, G=(50, -24), ang=-0.6, fore=0.55, layer='back', near_foot=(16, -5), far_foot=(-40, -5),
             elbow_near=(0.5, 0.5), elbow_far=(0.5, 0.5), jaw=0.6, heat=1.0, phase=0.6),
        ready,
    ]
    # death: staggered, the sword drops and sticks, he clutches the core, the knees give, he slumps while the cracks
    # spread, then throws his head back in a last roar as the core flares (the game makes him burst)
    death = [
        dict(lean=-0.35, head=-0.6, jaw=1.0, G=(40, -24), ang=1.0, one_hand=True, far_hand=(-34, -64),
             elbow_far=(-1, 0), heat=1.0, phase=0.1, near_foot=(10, -5), far_foot=(-34, -5)),
        dict(sword='ground', lean=-0.15, hunch=0.2, head=-0.2, near_hand=(36, -56), far_hand=(24, -50), jaw=0.7,
             heat=1.1, cracks=0.2, phase=0.3),
        dict(sword='ground', bob=18, kneel=0.6, hunch=0.4, head=0.2, near_hand=(34, -44), far_hand=(22, -40),
             jaw=0.5, heat=1.25, cracks=0.45, phase=0.5),
        dict(sword='ground', bob=30, kneel=1.0, hunch=0.7, head=0.7, near_hand=(30, 18), far_hand=(10, 22),
             jaw=0.3, heat=1.5, cracks=0.75, phase=0.7),
        dict(sword='ground', bob=28, kneel=1.0, lean=-0.25, hunch=0.2, head=-0.8, jaw=1.0, near_hand=(40, -112),
             far_hand=(-30, -120), elbow_near=(0.5, -1), elbow_far=(-0.5, -1), heat=2.3, cracks=1.0, phase=0.9),
    ]
    return dict(
        idle=loop_strip(idle, 8), walk=loop_strip(walk, 12), slam=keys_strip(slam, 12), cast=keys_strip(cast, 8),
        roar=loop_strip(roar, 4), sheathe=keys_strip(sheathe, 8), charge=loop_strip(charge, 12),
        crash=keys_strip(crash, 4), dazed=loop_strip(dazed, 8), draw=keys_strip(draw, 8), leap=keys_strip(leap, 6),
        stab=keys_strip(stab, 6), swing=keys_strip(swing, 12), death=keys_strip(death, 10))


def lord_strips(report=None):
    out = {}
    for name, poses in lord_poses().items():
        frames = []
        for i, P in enumerate(poses):
            rep = []
            frames.append(lord_frame(P, rep))
            if rep and report is not None:
                report.append((name, i, rep[0]))
        out[f'DemonLord_{name}'] = frames
    out['DemonLord_cleaver'] = [stuck_sword_prop()]
    return out


def strip_image(frames):
    out = Image.new('RGBA', (W * len(frames), H), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (i * W, 0))
    return out


def generate(out_dir):
    for name, frames in lord_strips().items():
        strip_image(frames).save(f'{out_dir}/{name}.png')


def sheet(path, scale=1):
    from PIL import ImageDraw
    rep = []
    strips = lord_strips(rep)
    for x in rep:
        print('out of reach (near, far):', x)
    rows = [(n, f) for n, f in strips.items()]
    cols = max(len(f) for _, f in rows)
    cw, ch = W // 2, H // 2 + 14
    img = Image.new('RGBA', (cw * cols, ch * len(rows)), (26, 10, 12, 255))
    d = ImageDraw.Draw(img)
    for j, (name, frames) in enumerate(rows):
        d.text((4, j * ch), name.replace('DemonLord_', ''), fill=(240, 210, 190, 255))
        for i, f in enumerate(frames):
            x, y = i * cw, j * ch + 14
            d.line((x, y + (AY // 2), x + cw, y + (AY // 2)), fill=(70, 30, 26, 255))
            img.alpha_composite(f.resize((cw, H // 2), Image.NEAREST), (x, y))
    if scale != 1:
        img = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    img.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'generate':
        generate(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
    elif len(sys.argv) >= 4 and sys.argv[1] == 'frame':
        name, i = sys.argv[2], int(sys.argv[3])
        P = lord_poses()[name][i]
        im = lord_frame(P)
        bg = Image.new('RGBA', im.size, (26, 10, 12, 255))
        bg.alpha_composite(im)
        bg.resize((W * 2, H * 2), Image.NEAREST).save(sys.argv[4] if len(sys.argv) > 4 else 'frame.png')
