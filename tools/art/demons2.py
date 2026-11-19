"""Demons wave 2: Chainwarden, Gargoyle, Watcher, Gatekeeper, Hex Priest and the Pit Lord boss,
plus their effects. Same Rig as demons.py (wave 1). Faces right, shown at x2. Must match DemonType.java:

  name             frame     anchor        strips
  DemonWarden      64x60     (32, 59)      idle 4, walk 6, swing 5 (+ _bare versions once the shield breaks)
  DemonGargoyle    112x112   (56, 64)*     stone 1, wake 3, fly 4, swoop 2        (* centre anchor, it flies; 2x detail)
  DemonWatcher     48x48     (24, 24)*     idle 4, look 8 (pupil at k * 45 deg), charge 2
  DemonGatekeeper  56x72     (28, 71)      idle 4, summon 4
  DemonPriest      48x56     (24, 55)      idle 4, walk 6, cast 4
  DemonLord        352x280   (176, 269)    idle 4, walk 6, slam 6, cast 4, roar 2   (twice the pixel detail)

Effects: Portal (6f 40x56, bottom-anchored), HexBolt (4f 14x14, flies right), BossGate (4f 24x64 tile, repeats
upward).

    python tools/art/demons2.py sheet out.png
"""
import math
import random
import sys
from pixel import *
from materials import BONE, BLACK_IRON, GOLD, DARK_STEEL, RUST, CRIMSON, WOOD, DARK_WOOD
from demons import (Rig, ell, limb, union, chain, poly, bezier, rot, polar, flame, clamp, dark,
                    EMBER, FEL, HORN, SKULL, ROCK, LAVA_RIM)

HEX = ramp('#f6d8ff', '#d08cff', '#a050f0', '#6a28b0', '#3a1266')
PORTAL = ramp('#ffd6e4', '#ff7aa8', '#e0306e', '#9a1450', '#560a32')


def tusk_dots(r, pts, c):
    for x, y in pts:
        r.dot(x, y, c)


# =====================================================================================================
# Chainwarden: armoured demon behind a tower shield, swings a chain flail
# =====================================================================================================
WARDEN_W, WARDEN_H, WARDEN_FEET = 64, 60, 58
WARDEN_SKIN = ramp('#ff8a6a', '#d23e34', '#8e1e26', '#560e18', '#2e060e')


def draw_warden(bob=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), flail=(1.5, 12.0), flail_front=False, shield=True,
                arm=0.0, lean=0.0, phase=0.0, rage=0.0):
    """flail: (angle, length) of the chain from the flail hand; flail_front draws the ball in front of the shield
    (the strike); arm: raise of the flail arm 0 (low) .. 1 (overhead); rage: glow once the shield is gone."""
    r = Rig(WARDEN_W, WARDEN_H, rim=0.45)
    hip = (28.0 + lean * 0.5, 40 + bob)
    chest = (29.5 + lean, 31 + bob)
    head = (32.0 + lean * 1.4, 21 + bob)
    armor = DARK_STEEL

    def leg(i, bias):
        a = legs[i]
        root = (hip[0] + (1.5 if i == 0 else -2.5), hip[1] + 1)
        foot = (root[0] + math.sin(a) * 6 + 1, WARDEN_FEET - 1 - lift[i])
        knee = ((root[0] + foot[0]) / 2 + 2, (root[1] + foot[1]) / 2 - 0.5)
        r.part(union(limb(root[0], root[1], knee[0], knee[1], 3.4, 2.8), limb(knee[0], knee[1], foot[0], foot[1] - 1, 2.8, 2.4)),
               armor, bias=bias)
        r.part(ell(knee[0] + 0.8, knee[1], 2.4, 2.0), BLACK_IRON, bias=bias)            # knee cop
        r.part(ell(foot[0] + 1.5, foot[1] - 0.8, 3.6, 1.6), BLACK_IRON, bias=bias)      # sabaton
        r.dot(foot[0] + 4.8, foot[1] - 0.5, armor[1] if bias == 0 else armor[3])

    hand_far = None

    def flail_arm(bias):
        nonlocal hand_far
        sh = (chest[0] - 3.5, chest[1] - 3.5)
        a1 = 1.4 - arm * 3.2
        a2 = a1 + 0.5 - arm * 0.3
        el = polar(sh, 6, a1)
        hand_far = polar(el, 5.5, a2)
        r.part(union(limb(sh[0], sh[1], el[0], el[1], 2.6, 2.2), limb(el[0], el[1], hand_far[0], hand_far[1], 2.3, 2.0)),
               armor, bias=bias)
        r.part(ell(hand_far[0], hand_far[1], 2.0, 2.0), BLACK_IRON, bias=bias)

    def flail_ball(bias):
        ang, ln = flail
        ball = polar(hand_far, ln, ang)
        n = int(ln / 1.8)
        for k in range(1, n):                                         # chain links
            p = (hand_far[0] + (ball[0] - hand_far[0]) * k / n, hand_far[1] + (ball[1] - hand_far[1]) * k / n
                 + math.sin(k / n * math.pi) * ln * 0.08)
            r.dot(p[0], p[1], BLACK_IRON[1] if k % 2 else BLACK_IRON[3])
        r.part(ell(ball[0], ball[1], 3.6, 3.6), BLACK_IRON, bias=bias)
        for k in range(6):                                            # spikes
            sp = polar(ball, 4.8, k * math.pi / 3 + phase)
            r.dot(sp[0], sp[1], DARK_STEEL[1])
        r.dot(ball[0] - 1, ball[1] - 1.2, BLACK_IRON[0])

    # far side first: flail arm, back leg
    flail_arm(0.3)
    if not flail_front:
        flail_ball(0.25)
    leg(1, 0.3)
    # tabard and plated torso
    r.part(poly([(hip[0] - 4, hip[1] - 1), (hip[0] + 5, hip[1] - 1), (hip[0] + 6 + lean, hip[1] + 9),
                 (hip[0] + 1, hip[1] + 7.5), (hip[0] - 4.5 + lean, hip[1] + 9)], 0.3), [hexc('#8e1e26'), hexc('#6a1420'), hexc('#460c16'), hexc('#2a0710')])
    r.part(union(ell(chest[0], chest[1], 7.5, 8.5, -0.15), ell(hip[0], hip[1] - 1, 6, 4)), armor)
    r.stroke(chest[0] + 1, chest[1] - 7, chest[0] + 2, chest[1] + 6, armor[0])     # breastplate ridge
    for k in range(3):                                                                  # faulds
        y = hip[1] + k * 1.8 - 0.5
        r.stroke(hip[0] - 4.5, y, hip[0] + 5, y + 0.3, BLACK_IRON[2])
    leg(0, 0.0)
    # horned helm with a burning visor slit
    for hx, bias in ((-2.5, 0.3), (0.8, 0.0)):
        hb = (head[0] + hx, head[1] - 3.5)
        r.part(chain(bezier(hb, (hb[0] + 1.5, hb[1] - 3.5), (hb[0] + 5, hb[1] - 4.5), 5), 1.3, 0.3), HORN, bias=bias)
    r.part(union(ell(head[0], head[1], 5.0, 5.6), limb(head[0] + 1, head[1] + 3, head[0] + 4.5, head[1] + 4.5, 2.2, 1.6)), armor)
    r.stroke(head[0] - 4, head[1] - 4.5, head[0] + 1, head[1] - 6, BLACK_IRON[1])     # crest
    eye_c = EMBER[1] if rage < 0.5 else EMBER[0]
    r.stroke(head[0] + 0.5, head[1] - 0.2, head[0] + 4.8, head[1] - 0.2, OUTLINE)
    r.dot(head[0] + 1.8, head[1] - 0.2, eye_c)
    r.dot(head[0] + 3.8, head[1] - 0.2, eye_c)
    r.glow(head[0] + 3, head[1] - 0.2, 4 + rage * 2, EMBER[2], 0.45 + rage * 0.3)
    for k in range(3):
        r.dot(head[0] + 2 + k * 1.2, head[1] + 2.3, BLACK_IRON[3])                     # breaths
    # spiked pauldron
    pa = (chest[0] + 3.5, chest[1] - 5)
    r.part(ell(pa[0], pa[1], 4.6, 3.6, -0.3), BLACK_IRON)
    for k, (dx, dy) in enumerate(((-2, -3), (1.5, -3.5))):
        r.part(poly([(pa[0] + dx - 1, pa[1] + dy + 1.5), (pa[0] + dx + 0.3, pa[1] + dy - 2.5), (pa[0] + dx + 1.5, pa[1] + dy + 1.2)], 0.3),
               DARK_STEEL)
    # near arm: holds the tower shield out front (or a bare fist)
    sh = (chest[0] + 4, chest[1] - 2)
    el = polar(sh, 5.5, 1.0)
    hand = polar(el, 5.0, 0.1)
    r.part(union(limb(sh[0], sh[1], el[0], el[1], 2.6, 2.2), limb(el[0], el[1], hand[0], hand[1], 2.3, 2.0)), armor)
    r.part(ell(hand[0], hand[1], 2.0, 2.0), BLACK_IRON)
    if shield:
        sx, sy = hand[0] + 2.0, hand[1]
        pts = [(sx - 2, sy - 15), (sx + 9, sy - 14.5), (sx + 10, sy + 10), (sx + 4, sy + 15.5), (sx - 2.5, sy + 10)]
        r.part(poly(pts, 0.35), BLACK_IRON)
        for a, b in zip(pts, pts[1:] + pts[:1]):                                       # rim
            r.stroke(a[0] + (0.6 if a[0] < sx + 3 else -0.6), a[1] + 0.6, b[0] + (0.6 if b[0] < sx + 3 else -0.6), b[1] + 0.6,
                     DARK_STEEL[2])
        for rv in ((sx - 0.5, sy - 13), (sx + 7.5, sy - 12.5), (sx + 8, sy + 8.5), (sx - 0.5, sy + 8.5)):
            r.dot(rv[0], rv[1], DARK_STEEL[0])
        # skull emblem with burning eyes, spikes on top
        r.part(ell(sx + 4, sy - 2, 3.2, 2.9), SKULL, edge=False)
        r.part(ell(sx + 4, sy + 1.2, 2.0, 1.3), SKULL, edge=False, bias=0.2)
        for ex in (sx + 2.8, sx + 5.2):
            r.dot(ex, sy - 2, EMBER[1])
        r.glow(sx + 3.5, sy - 2, 3.5, EMBER[2], 0.4)
        for k in range(3):
            tip = (sx + 0.0 + k * 4.0, sy - 18.5 + (k == 1) * -1)
            r.part(poly([(tip[0] - 1.3, sy - 14.5), tip, (tip[0] + 1.3, sy - 14.5)], 0.3), DARK_STEEL)
    else:
        # the shield's broken strap on the arm, cracks glowing through the armour
        r.stroke(hand[0] - 1, hand[1] - 2, hand[0] + 1.5, hand[1] + 2, RUST[2])
        r.stroke(chest[0] - 1, chest[1] - 4, chest[0] + 1.5, chest[1] + 1, EMBER[2])
        r.glow(chest[0], chest[1] - 1.5, 4, EMBER[2], 0.35 * (0.5 + rage))
    if flail_front:
        flail_ball(0.0)
    return r.render()


def warden_anims(shield=True):
    idle = [draw_warden(bob=math.sin(i / 4 * 6.28) * 0.5, flail=(1.5 + math.sin(i / 4 * 6.28) * 0.12, 9), phase=i * 0.3,
                        shield=shield, rage=0 if shield else 1) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        a = math.sin(p) * 0.6
        walk.append(draw_warden(bob=-abs(math.cos(p)) * 1.0 + 0.5, legs=(a, -a),
                                lift=(max(0, -math.cos(p)) * 2.2, max(0, math.cos(p)) * 2.2),
                                flail=(1.5 - a * 0.4, 9), phase=p * 0.2, shield=shield, rage=0 if shield else 1))
    swing = [
        draw_warden(arm=0.65, flail=(-2.6, 11), lean=-0.5, shield=shield, rage=0.6),
        draw_warden(arm=0.95, flail=(-1.9, 9), lean=-0.8, shield=shield, rage=0.8),
        draw_warden(arm=0.55, flail=(-0.15, 17), flail_front=True, lean=1.0, shield=shield, rage=1.0),
        draw_warden(arm=0.25, flail=(0.55, 16), flail_front=True, lean=1.2, shield=shield, rage=1.0),
        draw_warden(arm=0.1, flail=(1.3, 11), lean=0.4, shield=shield, rage=0.5),
    ]
    sfx = '' if shield else '_bare'
    return {'idle' + sfx: idle, 'walk' + sfx: walk, 'swing' + sfx: swing}


# =====================================================================================================
# Gargoyle: a stone statue until you pass; swoops, lands and turns back to stone
# =====================================================================================================
GARG_K = 2                       # drawn at twice the pixel detail (like the Brute): the pose stays in design units
GARG_W, GARG_H = 112, 112        # pixels: the old 56 x 48 design frame x GARG_K, plus GARG_OY of headroom on top
GARG_OY = 8                      # design units: room for the raised wing (the anchor moves down with it)
STONE = ramp('#c4beb4', '#9a958c', '#76726b', '#55524d', '#363431')
SLATE = ramp('#8a8fa8', '#626882', '#45495f', '#2d3042', '#1a1c28')
GARG_FEET = 36          # design units; perched: the bottom of the 100 px hitbox around the centre anchor


def membrane_wing(r, root, phi, ln, rmp, bias, K=1, edge_c=None):
    """Bat-like wing from root pointing at phi (radians), fanned out backwards. Design units, drawn x K:
    finger bones, a thumb claw and veins across the membrane."""
    s_ = -1 if math.sin(phi) < 0 else 1
    el = polar(root, ln * 0.45, phi - s_ * 0.15)
    f1 = polar(el, ln * 0.62, phi + s_ * 0.15)
    f2 = polar(el, ln * 0.55, phi + s_ * 0.75)
    f3 = polar(el, ln * 0.42, phi + s_ * 1.35)
    back = (root[0] - 4, root[1] + 2)
    mid = lambda a, b, k=0.25: ((a[0] + b[0]) / 2 + (root[0] - (a[0] + b[0]) / 2) * k,
                                (a[1] + b[1]) / 2 + (root[1] - (a[1] + b[1]) / 2) * k)
    P = lambda pts, b: poly([(x * K, y * K) for x, y in pts], b)
    S = lambda a, b, c: r.stroke(a[0] * K, a[1] * K, b[0] * K, b[1] * K, c)
    r.part(P([root, el, f1, mid(f1, f2), f2, mid(f2, f3), f3, mid(f3, back, 0.15), back], 0.25), rmp, bias=bias)
    vein = rmp[2] if bias == 0 else rmp[3]
    for a, b in ((f1, f2), (f2, f3), (f3, back)):                         # veins fanning into the membrane
        m = mid(a, b, 0.45)
        S(el, m, vein)
        S(m, mid(a, b, 0.1), vein)
    bone = rmp[3] if bias == 0 else rmp[4]
    for f in (f1, f2, f3):                                                # finger bones with a knuckle
        r.part(limb(el[0] * K, el[1] * K, f[0] * K, f[1] * K, 0.55 * K, 0.3 * K), rmp, edge=False, bias=bias + 0.15)
        S(el, f, bone)
        kn = (el[0] + (f[0] - el[0]) * 0.5, el[1] + (f[1] - el[1]) * 0.5)
        r.dot(kn[0] * K, kn[1] * K, rmp[1])
    r.part(limb(root[0] * K, root[1] * K, el[0] * K, el[1] * K, 0.9 * K, 0.6 * K), rmp, bias=bias)   # arm bone
    thumb = polar(el, 2.2, phi - s_ * 1.2)
    r.part(chain([(el[0] * K, el[1] * K), (thumb[0] * K, thumb[1] * K)], 0.5 * K, 0.15 * K), HORN, edge=False, bias=bias)
    if edge_c:
        for a, b in ((f1, f2), (f2, f3)):
            S(a, mid(a, b), edge_c)


def draw_gargoyle(pose='perch', stone=0.0, flap=0.0, cracks=0.0, phase=0.0, mouth=0.0):
    """pose: perch / fly / swoop; stone 0 living .. 1 statue; cracks 0..1 glowing seams while waking.
    Laid out in the old 56 x 48 design units and drawn x GARG_K (finer outlines, ridges, veins, pitting)."""
    K = GARG_K
    r = Rig(GARG_W, GARG_H, rim=0.4 * (1 - stone))
    E = lambda cx, cy, rx, ry, a=0.0: ell(cx * K, cy * K, rx * K, ry * K, a)
    L = lambda x0, y0, x1, y1, r0, r1=None: limb(x0 * K, y0 * K, x1 * K, y1 * K, r0 * K, (r0 if r1 is None else r1) * K)
    C = lambda pts, r0, r1: chain([(x * K, y * K) for x, y in pts], r0 * K, r1 * K)
    PG = lambda pts, b=0.6: poly([(x * K, y * K) for x, y in pts], b)
    D = lambda x, y, c: r.dot(x * K, y * K, c)
    S = lambda x0, y0, x1, y1, c: r.stroke(x0 * K, y0 * K, x1 * K, y1 * K, c)
    G = lambda x, y, rad, c, st: r.glow(x * K, y * K, rad * K, c, st)
    skin = [mix(SLATE[i], STONE[i], stone) for i in range(5)]
    hornr = [mix(HORN[i], STONE[i], stone * 0.8) for i in range(5)]
    crease = skin[3]
    if pose == 'perch':
        haunch, torso, head = (23.5, 30.5), (28.5, 24.5), (34.5, 17.5)
        tor_rot = -0.55
    elif pose == 'fly':
        haunch, torso, head = (20.5, 24.0), (27.5, 22.0), (35.5, 19.0)
        tor_rot = 0.1
    else:   # swoop: nose down, claws out
        haunch, torso, head = (21.0, 18.5), (28.0, 22.5), (34.5, 27.0)
        tor_rot = 0.65
    haunch, torso, head = [(p[0], p[1] + GARG_OY) for p in (haunch, torso, head)]
    feet = GARG_FEET + GARG_OY
    # tail with a spade tip
    tb = (haunch[0] - 4, haunch[1] + 1)
    tp = bezier(tb, (tb[0] - 6, tb[1] + 4 + math.sin(phase * 6.28) * 1.5), (tb[0] - 9, tb[1] - 2), 8)
    r.part(C(tp, 1.4, 0.5), skin, bias=0.2)
    for k in range(2, len(tp) - 1, 2):                                     # segment rings
        D(tp[k][0], tp[k][1] - 0.6, crease)
    t_ = tp[-1]
    r.part(PG([(t_[0] + 1, t_[1] - 1.6), (t_[0] - 2.6, t_[1] - 1.4), (t_[0] - 0.5, t_[1] + 1.4)], 0.3), skin, bias=0.2)
    S(t_[0] + 0.6, t_[1] - 0.2, t_[0] - 1.8, t_[1] - 0.8, crease)
    # far wing
    wroot = (torso[0] - 1.5, torso[1] - 4)
    if pose == 'perch':
        tip = (wroot[0] - 7, wroot[1] - 9)
        r.part(PG([wroot, tip, (wroot[0] - 10, wroot[1] - 2), (wroot[0] - 9, wroot[1] + 8), (wroot[0] - 4, wroot[1] + 9)], 0.4), skin, bias=0.35)
        for k in range(3):                                                 # folded finger ribs
            S(tip[0], tip[1], wroot[0] - 9 + k * 2, wroot[1] + 7 + k * 0.5, skin[4])
    elif pose == 'fly':
        phi = -2.0 - (1 - flap) / 2 * (2 * math.pi - 4.2)
        membrane_wing(r, (wroot[0] + 1, wroot[1]), phi + 0.25, 19 * (0.45 + 0.55 * abs(math.sin(phi))) * 0.85, skin, 0.35, K)
    else:
        r.part(PG([wroot, (wroot[0] - 13, wroot[1] - 6), (wroot[0] - 11, wroot[1] - 1), (wroot[0] - 3, wroot[1] + 3)], 0.3), skin, bias=0.35)
        S(wroot[0], wroot[1], wroot[0] - 12, wroot[1] - 5, skin[4])
    # hind legs: thigh muscle line, three hooked toe claws
    for i, bias in ((1, 0.3), (0, 0.0)):
        if pose == 'perch':
            knee = (haunch[0] + 3.5 - i * 1.5, haunch[1] + 2)
            foot = (haunch[0] + 1 - i * 2.5, feet - 1)
        elif pose == 'fly':
            knee = (haunch[0] - 3, haunch[1] + 3 - i)
            foot = (haunch[0] - 8, haunch[1] + 4 - i)
        else:
            knee = (haunch[0] - 2, haunch[1] - 3 - i)
            foot = (haunch[0] - 7, haunch[1] - 5 - i)
        r.part(union(L(haunch[0], haunch[1], knee[0], knee[1], 3.4, 2.2), L(knee[0], knee[1], foot[0], foot[1], 2.0, 1.5)), skin, bias=bias)
        S(haunch[0] + 0.5, haunch[1] - 1.5, knee[0] - 0.3, knee[1] - 0.8, crease if bias == 0 else skin[4])
        fa = math.atan2(foot[1] - knee[1], foot[0] - knee[0])
        for k in range(3):
            c0 = (foot[0] + 0.6 + k * 0.9, foot[1] + 0.4 - k * 0.35)
            c1 = polar(c0, 1.6, fa + 0.9 - k * 0.15) if pose != 'perch' else (c0[0] + 1.2, c0[1] + 0.6)
            r.part(C([c0, c1], 0.45, 0.12), hornr, edge=False, bias=bias)
    # torso: chest plates, ribs, a spine of stubby spikes along the back
    r.part(union(E(torso[0], torso[1], 6.0, 7.2, tor_rot), E(haunch[0], haunch[1], 5.5, 5.0)), skin)
    S(torso[0] + 1, torso[1] - 2, torso[0] + 3, torso[1] + 3, crease)
    S(torso[0] - 0.5, torso[1] + 1, torso[0] + 1.5, torso[1] + 5, crease)
    for k in range(3):
        a = tor_rot + 0.35 + k * 0.3
        p0, p1 = polar(torso, 2.5, a), polar(torso, 5.2, a + 0.1)
        S(p0[0], p0[1], p1[0], p1[1], crease)
    back_pts = bezier((head[0] - 3.5, head[1] - 1.5), (torso[0] - 5.5, torso[1] - 7.5), (haunch[0] - 4, haunch[1] - 3), 9)
    for k in range(1, len(back_pts) - 1):
        a, b = back_pts[k - 1], back_pts[k + 1]
        out = math.atan2(b[1] - a[1], b[0] - a[0]) - math.pi / 2           # away from the body (up / back)
        base = back_pts[k]
        tip = polar(base, 1.8 - abs(k - 4) * 0.12, out - 0.35)
        r.part(PG([polar(base, 0.8, out + math.pi / 2), tip, polar(base, -0.8, out + math.pi / 2)], 0.2), hornr, edge=False, bias=0.15)
    # front arms: forearm muscle, two-claw hands
    for i, bias in ((1, 0.3), (0, 0.0)):
        sh = (torso[0] + 2.5 - i, torso[1] - 3)
        if pose == 'perch':
            el, hand = (sh[0] + 3, sh[1] + 6), (sh[0] + 4 + i * -1.5, feet - 1.2)
        elif pose == 'fly':
            el, hand = (sh[0] + 2, sh[1] + 4), (sh[0] + 5, sh[1] + 6)
        else:
            el, hand = (sh[0] + 4, sh[1] + 3), (sh[0] + 9, sh[1] + 7 - i)
        r.part(union(L(sh[0], sh[1], el[0], el[1], 2.2, 1.7), L(el[0], el[1], hand[0], hand[1], 1.7, 1.3)), skin, bias=bias)
        r.part(E(sh[0], sh[1], 2.4, 2.0), skin, edge=False, bias=bias - 0.1)
        S(el[0] - 0.3, el[1] - 0.6, (el[0] + hand[0]) / 2, (el[1] + hand[1]) / 2 - 0.4, crease if bias == 0 else skin[4])
        for k in (-0.55, 0.0, 0.5):
            ang = math.atan2(hand[1] - el[1], hand[0] - el[0]) + k
            tip = polar(hand, 2.1, ang)
            r.part(C([hand, tip], 0.42, 0.1), hornr, edge=False, bias=bias)
    # head: snout, jaw, brow ridge, fangs, ridged horns, pointed ear
    ear = [(head[0] - 2.5, head[1] - 1), (head[0] - 7, head[1] - 5), (head[0] - 2, head[1] + 1.5)]
    r.part(PG(ear, 0.3), skin, bias=0.1)
    S(head[0] - 2.8, head[1] - 0.6, head[0] - 6, head[1] - 4.2, skin[4])
    jaw_drop = mouth * 1.2
    r.part(L(head[0] + 0.5, head[1] + 2.2 + jaw_drop, head[0] + 4.6, head[1] + 3.0 + jaw_drop, 1.3, 1.0), skin, bias=0.15)
    if mouth > 0.3:
        r.part(E(head[0] + 3, head[1] + 2.6 + jaw_drop * 0.5, 2.0, 0.6 + jaw_drop * 0.5), [hexc('#3a0a0a'), hexc('#1e0404')], edge=False)
    r.part(union(E(head[0], head[1], 4.2, 3.8), L(head[0] + 1, head[1] + 0.8, head[0] + 4.8, head[1] + 2.2, 2.2, 1.6)), skin)
    D(head[0] + 4.7, head[1] + 1.0, skin[4])                                # nostril
    S(head[0] + 1.2, head[1] + 2.6, head[0] + 4.6, head[1] + 3.0, crease)    # lip line
    for fx in (1.6, 2.8, 4.0):                                            # fangs
        r.part(L(head[0] + fx, head[1] + 2.4, head[0] + fx + 0.1, head[1] + 3.6 + jaw_drop * 0.3, 0.38, 0.12), BONE, edge=False)
    r.part(L(head[0] - 0.6, head[1] - 1.9, head[0] + 3.8, head[1] - 1.1, 0.9, 0.6), skin, bias=-0.1)   # brow ridge
    for k in range(2):
        S(head[0] - 2.5, head[1] - 3 - k * 0.8, head[0] + 0.5, head[1] - 2.8 - k * 0.9, crease)
    for hx, bias in ((-2.0, 0.3), (0.5, 0.0)):
        hb = (head[0] + hx, head[1] - 3)
        pts = bezier(hb, (hb[0] - 1, hb[1] - 4), (hb[0] - 5, hb[1] - 5.5), 8)
        r.part(C(pts, 1.3, 0.25), hornr, bias=bias)
        for k in range(1, len(pts) - 2, 2):                                # ridges across the horn
            a_, b_ = pts[k - 1], pts[k + 1]
            n_ = math.atan2(b_[1] - a_[1], b_[0] - a_[0]) + math.pi / 2
            rad = (1.3 + (0.25 - 1.3) * k / len(pts)) * 0.85
            e0, e1 = polar(pts[k], rad, n_), polar(pts[k], -rad, n_)
            S(e0[0], e0[1], e1[0], e1[1], hornr[3] if bias == 0 else hornr[4])
    eye_c = mix(EMBER[1], STONE[3], stone)
    eye_hi = mix(hexc('#fff3a0'), STONE[2], stone)
    for ex in (head[0] + 0.7, head[0] + 2.5):
        S(ex - 0.3, head[1] - 0.4, ex + 0.7, head[1] - 0.5, eye_c)
        D(ex + 0.2, head[1] - 0.5, eye_hi)
    S(head[0] - 0.2, head[1] - 1.2, head[0] + 3.6, head[1] - 0.8, OUTLINE)
    if stone < 0.5:
        G(head[0] + 1.9, head[1] - 0.5, 3.5, EMBER[2], 0.5 * (1 - stone))
    # weathered stone: pits and hairline cracks over the body while it is a statue
    if stone > 0.3:
        rng = random.Random(11)
        for _ in range(int(22 * stone)):
            px_, py_ = torso[0] + rng.uniform(-6, 7), torso[1] + rng.uniform(-8, 9)
            D(px_, py_, STONE[4] if rng.random() < 0.6 else STONE[1])
        for _ in range(4):
            cx_, cy_ = torso[0] + rng.uniform(-5, 4), torso[1] + rng.uniform(-6, 7)
            a = rng.uniform(0, 6.28)
            p1 = polar((cx_, cy_), rng.uniform(1.5, 3), a)
            p2 = polar(p1, rng.uniform(1, 2), a + rng.uniform(-0.8, 0.8))
            S(cx_, cy_, p1[0], p1[1], STONE[4])
            S(p1[0], p1[1], p2[0], p2[1], STONE[4])
    # glowing seams as the stone cracks open
    if cracks > 0:
        rng = random.Random(5)
        for k in range(int(4 + cracks * 8)):
            cx_, cy_ = torso[0] + rng.uniform(-5, 4), torso[1] + rng.uniform(-6, 6)
            ln = rng.uniform(2, 4) * cracks
            a = rng.uniform(0, 6.28)
            p1 = polar((cx_, cy_), ln, a)
            p2 = polar(p1, ln * 0.6, a + rng.uniform(-0.9, 0.9))
            col = EMBER[2] if k % 2 else EMBER[1]
            S(cx_, cy_, p1[0], p1[1], col)
            S(p1[0], p1[1], p2[0], p2[1], col)
        G(torso[0], torso[1], 6, EMBER[2], 0.35 * cracks)
    # near wing
    if pose == 'perch':
        tip = (wroot[0] - 4.5, wroot[1] - 10)
        r.part(PG([(wroot[0] + 1, wroot[1]), tip, (wroot[0] - 8, wroot[1] - 3), (wroot[0] - 7, wroot[1] + 9),
                   (wroot[0] - 1, wroot[1] + 8)], 0.4), skin, bias=0.05)
        S(wroot[0] + 1, wroot[1], tip[0], tip[1], skin[1])
        for k in range(3):                                                 # folded finger ribs
            S(tip[0], tip[1], wroot[0] - 6.5 + k * 2.2, wroot[1] + 8 - k * 0.3, crease)
        r.part(C([(tip[0] + 0.2, tip[1] + 0.4), (tip[0] - 0.4, tip[1] - 1.6)], 0.5, 0.15), hornr, edge=False)   # wing claw
    elif pose == 'fly':
        phi = -2.0 - (1 - flap) / 2 * (2 * math.pi - 4.2)
        membrane_wing(r, (wroot[0] + 2, wroot[1] + 1), phi, 21 * (0.45 + 0.55 * abs(math.sin(phi))), skin, 0.0, K)
    else:
        r.part(PG([(wroot[0] + 1, wroot[1] + 1), (wroot[0] - 15, wroot[1] - 3), (wroot[0] - 12, wroot[1] + 1.5),
                   (wroot[0] - 2, wroot[1] + 4.5)], 0.3), skin, bias=0.05)
        S(wroot[0] + 1, wroot[1] + 1, wroot[0] - 14, wroot[1] - 2.6, skin[1])
        for k in range(2):
            S(wroot[0] - 14, wroot[1] - 2.6, wroot[0] - 9 + k * 4, wroot[1] + 2.5 + k * 0.8, crease)
    return r.render()

def gargoyle_anims():
    stone = [draw_gargoyle('perch', stone=1.0)]
    wake = [draw_gargoyle('perch', stone=1.0 - k * 0.4, cracks=0.4 + k * 0.3, mouth=k * 0.5) for k in range(3)]
    fly = [draw_gargoyle('fly', flap=f, phase=i / 4) for i, f in enumerate((1.0, 0.2, -1.0, 0.0))]
    swoop = [draw_gargoyle('swoop', mouth=1.0, phase=k * 0.5) for k in range(2)]
    return dict(stone=stone, wake=wake, fly=fly, swoop=swoop)


# =====================================================================================================
# Watcher: a floating eye on dangling tentacles; aims a thin line, locks, fires a beam
# =====================================================================================================
WATCH_W, WATCH_H = 48, 48
SCLERA = ramp('#fff4ea', '#f2d8ca', '#dcae9e', '#ac786a', '#6c403a')
FLESH = ramp('#cf90a2', '#a05e76', '#723c56', '#4c2239', '#2a1020')
IRIS = ramp('#fff0a0', '#ffb43a', '#e0601a', '#a02a12', '#5a120a')


def draw_watcher(look=None, lid=0.45, hot=0.0, phase=0.0):
    """look: angle (radians, art space, 0 = right) the pupil points at, None = straight ahead (slightly right);
    lid 0 wide open .. 1 shut; hot 0..1 iris glowing for the beam."""
    r = Rig(WATCH_W, WATCH_H, rim=0.35)
    cx, cy, R = 24.0, 20.0, 12.0
    # tentacles hanging below
    for k in range(5):
        x0 = cx - 8 + k * 4
        sway = math.sin(phase * 6.28 + k * 1.3) * 2.2
        pts = bezier((x0, cy + 8), (x0 + sway * 0.5, cy + 16), (x0 + sway - 1 + k * 0.4, cy + 25 - abs(k - 2)), 6)
        r.part(chain(pts, 2.0, 0.5), FLESH, bias=0.25 if k % 2 else 0.1)
        r.dot(pts[3][0] - 0.5, pts[3][1], FLESH[0])
    # spikes on top
    for k, dx in enumerate((-6, 0, 6)):
        r.part(poly([(cx + dx - 2, cy - R + 2), (cx + dx + dx * 0.15, cy - R - 4.5 - (k == 1) * 2), (cx + dx + 2, cy - R + 2)], 0.3), HORN, bias=0.1)
    ball = ell(cx, cy, R, R)
    r.part(ball, SCLERA)
    # veins creeping toward the iris
    rng = random.Random(11)
    for k in range(7):
        a = rng.uniform(0, 6.28)
        p0 = (cx + math.cos(a) * R * 0.95, cy + math.sin(a) * R * 0.95)
        p1 = (cx + math.cos(a + rng.uniform(-0.3, 0.3)) * R * 0.55, cy + math.sin(a + rng.uniform(-0.3, 0.3)) * R * 0.55)
        r.stroke(p0[0], p0[1], p1[0], p1[1], CRIMSON[2] if hot < 0.5 else EMBER[3])
    # iris + slit pupil
    a = 0.0 if look is None else look
    off = 0.0 if look is None else 5.0
    ix, iy = cx + 1.5 + math.cos(a) * off, cy + math.sin(a) * off
    iris = {p: n for p, n in ell(ix, iy, 5.4, 5.4).items() if p in ball}
    rmp = IRIS if hot < 0.5 else [EMBER[0], EMBER[0], EMBER[1], EMBER[2], EMBER[3]]
    r.part(iris, rmp, edge=False)
    r.part({p: n for p, n in ell(ix, iy, 1.1, 3.6).items() if p in ball}, [hexc('#120808')], edge=False)
    r.dot(ix - 1.8, iy - 2, hexc('#ffffff'))
    if hot > 0:
        r.glow(ix, iy, 6 + hot * 5, EMBER[1], 0.4 + hot * 0.4)
    # lids: upper droops over the top, lower sits a little up
    drop = 3 + lid * (2 * R - 6)
    upper = {p: n for p, n in ell(cx, cy, R + 0.6, R + 0.6).items() if p[1] + 0.5 < cy - R + drop}
    lower = {p: n for p, n in ell(cx, cy, R + 0.6, R + 0.6).items() if p[1] + 0.5 > cy + R - 3.5}
    r.part(upper, FLESH)
    r.part(lower, FLESH, bias=0.1)
    for x in range(int(cx - R) + 2, int(cx + R) - 1, 3):                     # lashes / folds
        y = cy - R + drop
        if (x, int(y)) in r.owner:
            r.dot(x + 0.5, y - 0.5, FLESH[3])
    return r.render()


def watcher_anims():
    idle = [draw_watcher(lid=0.22 + (0.6 if i == 2 else 0), phase=i / 4) for i in range(4)]
    look = [draw_watcher(look=k * math.pi / 4, lid=0.3, phase=k / 8) for k in range(8)]
    charge = [draw_watcher(look=None, lid=0.05, hot=0.7 + k * 0.3, phase=k * 0.5) for k in range(2)]
    return dict(idle=idle, look=look, charge=charge)


# =====================================================================================================
# Gatekeeper: robed warden of the gates with an iron key-staff; opens portals full of imps
# =====================================================================================================
GATE_W, GATE_H, GATE_FEET = 56, 72, 70
GATE_ROBE = ramp('#8a4e5a', '#64323e', '#46202a', '#2c131b', '#18090e')
RUSTED_KEY = [mix(GOLD[i], RUST[i], 0.45) for i in range(5)]


def draw_gatekeeper(bob=0.0, raise_=0.0, phase=0.0, hot=0.3):
    r = Rig(GATE_W, GATE_H, rim=0.4)
    top = (26.0, 31 + bob - raise_ * 1)
    head = (28.5, 22 + bob - raise_ * 1)
    pc = PORTAL
    # horns rising into an arch over the head
    for hb, ctrl, end, bias in (((26.5, head[1] - 3), (17, head[1] - 14), (25.5, head[1] - 21), 0.3),
                                ((30.5, head[1] - 3), (39, head[1] - 14), (31.5, head[1] - 21), 0.0)):
        r.part(chain(bezier(hb, ctrl, end, 8), 2.0, 0.4), HORN, bias=bias)
    # robe
    hem = [(top[0] - 13 + 26 * k / 8 + math.sin(k * 2.1 + phase * 6.28) * 0.6, GATE_FEET - 1.5 + (k % 2) * 1.2) for k in range(9)]
    r.part(poly([(top[0] - 6, top[1]), (top[0] + 6, top[1])] + [hem[-1]] + list(reversed(hem[:-1])), 0.5), GATE_ROBE)
    for k in range(3):                                                               # heavy folds
        x = top[0] - 6 + k * 5
        r.stroke(x, top[1] + 8, x - 2 + k, GATE_FEET - 3, GATE_ROBE[3])
    # iron mantle with chains
    r.part(ell(top[0], top[1] + 1, 9.5, 5.2), BLACK_IRON)
    for k in range(5):
        x = top[0] - 7 + k * 3.5
        for j in range(3):
            r.dot(x + math.sin(phase * 6.28 + k) * 0.4, top[1] + 5 + j * 1.6, BLACK_IRON[1 if j % 2 else 3])
    # face: an iron mask with a glowing keyhole
    r.part(ell(head[0], head[1], 4.6, 5.2), BLACK_IRON)
    r.part(ell(head[0] - 1.5, head[1] + 0.5, 4.0, 4.8), GATE_ROBE, bias=0.2)       # hood edge behind
    r.part(ell(head[0] + 0.8, head[1], 3.6, 4.6), DARK_STEEL, edge=True, bias=0.2)
    kc = pc[0] if hot > 0.6 else pc[1]
    r.part(ell(head[0] + 1.6, head[1] - 0.6, 1.1, 1.1), [kc], edge=False)
    r.stroke(head[0] + 1.6, head[1] - 0.2, head[0] + 1.6, head[1] + 2.4, kc)
    r.glow(head[0] + 1.6, head[1] + 0.3, 4 + hot * 3, pc[2], 0.45 + hot * 0.35)
    # key-staff in the near hand
    kx = top[0] + 10 + raise_ * 1.5
    ky0, ky1 = head[1] - 8 - raise_ * 8, GATE_FEET - 1 - raise_ * 8
    r.part(limb(kx, ky0 + 3, kx, ky1, 1.1), RUSTED_KEY)
    ring = {p: n for p, n in ell(kx, ky0, 3.8, 3.8).items() if p not in ell(kx, ky0, 2.0, 2.0)}
    r.part(ring, RUSTED_KEY)
    r.part(poly([(kx, ky1 - 6), (kx + 4, ky1 - 6), (kx + 4, ky1 - 4.5), (kx + 2.5, ky1 - 4.5), (kx + 2.5, ky1 - 3),
                 (kx + 4, ky1 - 3), (kx + 4, ky1 - 1.5), (kx, ky1 - 1.5)], 0.2), RUSTED_KEY)
    hand = (kx - 0.5, top[1] + 5 - raise_ * 9)
    sh = (top[0] + 4, top[1] + 1)
    r.part(limb(sh[0], sh[1], hand[0] - 1, hand[1], 2.6, 3.4), GATE_ROBE, bias=0.05)
    r.part(ell(hand[0], hand[1], 1.7, 1.7), ROCK)
    # runes orbiting the head
    for k in range(4):
        a = phase * 6.28 + k * math.pi / 2
        rx_, ry_ = head[0] + math.cos(a) * 12, head[1] - 6 + math.sin(a) * 5
        c = pc[1] if hot > 0.5 else pc[2]
        r.dots([(rx_, ry_), (rx_ + 1, ry_), (rx_, ry_ + 1)], c)
        r.glow(rx_ + 0.5, ry_ + 0.5, 2.5 + hot * 1.5, pc[2], 0.3 + hot * 0.3)
    return r.render()


def gatekeeper_anims():
    idle = [draw_gatekeeper(bob=math.sin(i / 4 * 6.28) * 0.5, phase=i / 4) for i in range(4)]
    summon = [draw_gatekeeper(raise_=min(1, 0.4 + k * 0.3), phase=k / 4 + 0.1, hot=0.6 + k * 0.12) for k in range(4)]
    return dict(idle=idle, summon=summon)


# =====================================================================================================
# Hex Priest: hunched, skull-masked, with candles burning on its back; shields demons, hexes you
# =====================================================================================================
PRIEST_W, PRIEST_H, PRIEST_FEET = 48, 56, 54
PRIEST_ROBE = ramp('#b2445a', '#82283e', '#5a1a2c', '#3a0f1c', '#20070f')


def draw_priest(bob=0.0, sway=0.0, staff=0.0, hot=0.3, steps=(0.0, 0.0), phase=0.0):
    """staff 0 planted .. 1 thrust forward and up (casting)."""
    r = Rig(PRIEST_W, PRIEST_H, rim=0.45)
    top = (22.0, 27 + bob)
    head = (30.0, 23 + bob)
    # far bony hand
    r.part(limb(top[0] + 2, top[1] + 3, top[0] + 6, top[1] + 9, 2.2, 2.6), PRIEST_ROBE, bias=0.3)
    r.part(ell(top[0] + 6.5, top[1] + 9.5, 1.4, 1.4), BONE, bias=0.3)
    # shoes
    for k, (dx, s_) in enumerate(((-3, steps[1]), (3, steps[0]))):
        fx = top[0] + dx + s_ * 2.5
        r.part(ell(fx + 1, PRIEST_FEET - 0.8, 2.6, 1.2), [hexc('#3a2a20'), hexc('#24180f')], bias=0.3 if k == 0 else 0)
    # hunched tattered robe
    hem = []
    for k in range(10):
        t = k / 9
        hem.append((top[0] - 10 + 21 * t + sway * (1 - abs(t - 0.5)), PRIEST_FEET - 1.5 - (k % 2) * 2.2))
    r.part(poly([(top[0] - 6, top[1] - 2), (top[0] - 2, top[1] - 6), (top[0] + 6, top[1] - 3)] + [hem[-1]] + list(reversed(hem[:-1])), 0.5),
           PRIEST_ROBE)
    r.stroke(top[0] - 3, top[1] + 2, top[0] - 5 + sway * 0.5, PRIEST_FEET - 4, PRIEST_ROBE[3])
    r.stroke(top[0] + 2, top[1] + 3, top[0] + 3 + sway * 0.5, PRIEST_FEET - 4, PRIEST_ROBE[3])
    # candles on the hump
    for k, (dx, h) in enumerate(((-6, 5), (-3, 7), (0.5, 4))):
        cx_, cy_ = top[0] + dx, top[1] - 4 - abs(dx) * 0.2 + (2 if dx < -4 else 0)
        r.part(limb(cx_, cy_, cx_, cy_ - h, 1.1), BONE, bias=0.15)
        flame(r, cx_, cy_ - h - 0.5, 2.5 + math.sin(phase * 6.28 + k * 2) * 0.8, 0.9, phase + k * 0.3)
        r.glow(cx_, cy_ - h - 1.5, 3.5, EMBER[1], 0.35)
        r.dot(cx_ + 0.5, cy_ - h * 0.5, BONE[3])
    # skull mask with curled horns and purple eye-lights
    for hx, bias in ((-1.8, 0.3), (1.0, 0.0)):
        hb = (head[0] + hx, head[1] - 3)
        r.part(chain(bezier(hb, (hb[0] - 3.5, hb[1] - 3.5), (hb[0] - 3, hb[1] + 1.5), 5), 1.2, 0.3), HORN, bias=bias)
    r.part(ell(head[0] - 2.5, head[1], 4.5, 4.5), PRIEST_ROBE, bias=0.1)              # hood
    r.part(union(ell(head[0], head[1], 3.8, 4.0), limb(head[0] + 1, head[1] + 1.5, head[0] + 3.5, head[1] + 3, 1.8, 1.4)), BONE)
    ec = HEX[0] if hot > 0.6 else HEX[1]
    for ex in (head[0] + 0.5, head[0] + 2.6):
        r.part(ell(ex, head[1] - 0.2, 1.0, 1.1), [hexc('#1a0a20')], edge=False)
        r.dot(ex, head[1] - 0.2, ec)
    r.glow(head[0] + 1.5, head[1] - 0.2, 3.5 + hot * 2, HEX[2], 0.45 + hot * 0.3)
    for k in range(3):
        r.dot(head[0] + 1.2 + k, head[1] + 3.3, BONE[3])
    # staff with a skull lantern burning purple
    bx, by = top[0] + 11 + staff * 4, PRIEST_FEET - 1 - staff * 9
    tx, ty = top[0] + 12 + staff * 9, top[1] - 13 - staff * 5
    r.part(limb(bx, by, tx, ty, 1.0, 0.9), WOOD, bias=0.1)
    r.part(limb(tx - 0.5, ty, tx + 3, ty - 1.5, 0.8), WOOD, bias=0.1)
    lx, ly = tx + 3, ty + 2.5
    r.stroke(tx + 3, ty - 1, lx, ly - 1.5, BLACK_IRON[2])
    r.part(ell(lx, ly, 2.2, 2.0), BONE, bias=0.1)
    flame(r, lx, ly - 1, 3 + hot * 4 + math.sin(phase * 6.28) * 0.8, 1.2 + hot, phase, rmp=HEX)
    r.glow(lx, ly - 2, 5 + hot * 5, HEX[2], 0.4 + hot * 0.35)
    # near hand on the staff
    hand = (bx + (tx - bx) * 0.55, by + (ty - by) * 0.55)
    r.part(limb(top[0] + 3, top[1] + 1, hand[0] - 1, hand[1], 2.3, 2.8), PRIEST_ROBE)
    r.part(ell(hand[0], hand[1], 1.5, 1.5), BONE)
    return r.render()


def priest_anims():
    idle = [draw_priest(bob=math.sin(i / 4 * 6.28) * 0.5, sway=math.sin(i / 4 * 6.28) * 0.6, phase=i / 4) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        walk.append(draw_priest(bob=-abs(math.cos(p)) * 0.8 + 0.4, sway=-math.sin(p) * 1.5,
                                steps=(max(0, math.sin(p)), max(0, -math.sin(p))), phase=i / 6))
    cast = [draw_priest(staff=min(1, k * 0.4), hot=0.5 + k * 0.17, phase=k / 4, sway=-k * 0.4) for k in range(4)]
    return dict(idle=idle, walk=walk, cast=cast)


# =====================================================================================================
# Pit Lord (mini-boss): a towering goat-legged demon lord with a molten cleaver and a crown of fire
# =====================================================================================================
LORD_SKIN = ramp('#ea8a6a', '#c04a3e', '#8c2a2e', '#5c1622', '#360b16', '#1c050b')
LORD_FUR = ramp('#6a4a44', '#4a302e', '#33201f', '#221414', '#140b0b')
LORD_K = 2
# The frame is wide and tall enough for the cleaver at full swing (raised overhead, swept forward, roar): the
# body is drawn LORD_OX design units in from the left so it stays centred (the sprite flips around its centre).
LORD_W, LORD_H, LORD_FEET, LORD_OX = 352, 280, 134, 28    # LORD_FEET in design units (x LORD_K = pixel row 268)


def draw_lord(bob=0.0, lean=0.0, legs=(0.0, 0.0), lift=(0.0, 0.0), near=(1.2, 0.9), far=(1.4, 1.6), sword=1.05,
              mouth=0.0, head_up=0.0, fire=0.0, heat=0.4, phase=0.0, grip2=False):
    """Design units (x2 to pixels), offset LORD_OX to the right inside the frame. near/far: (upper, fore) arm angles; sword: blade angle from the
    near hand; fire: radius of a fireball in the far hand (0 = none); grip2: both hands on the hilt (the slam)."""
    K = LORD_K
    r = Rig(LORD_W, LORD_H, rim=0.5)
    X = lambda x: (x + LORD_OX) * K
    E = lambda cx, cy, rx, ry, a=0.0: ell(X(cx), cy * K, rx * K, ry * K, a)
    L = lambda x0, y0, x1, y1, r0, r1=None: limb(X(x0), y0 * K, X(x1), y1 * K, r0 * K, (r0 if r1 is None else r1) * K)
    C = lambda pts, r0, r1: chain([(X(x), y * K) for x, y in pts], r0 * K, r1 * K)
    PG = lambda pts, b=0.6: poly([(X(x), y * K) for x, y in pts], b)
    D = lambda x, y, c: r.dot(X(x), y * K, c)
    S = lambda x0, y0, x1, y1, c: r.stroke(X(x0), y0 * K, X(x1), y1 * K, c)
    G = lambda x, y, rad, c, st: r.glow(X(x), y * K, rad * K, c, st)
    FL = lambda x, y, h, w, ph, rmp=EMBER, ln=0.0: flame(r, X(x), y * K, h * K, w * K, ph, rmp=rmp, lean=ln)
    skin, crease = LORD_SKIN, LORD_SKIN[4]
    hot = EMBER[0] if heat > 0.7 else EMBER[1]

    bob += LORD_FEET - 98            # the pose numbers below are for feet at y = 98
    hip = (56.0 + lean * 2, 64 + bob)
    chest = (62.0 + lean * 5, 44 + bob)
    head = (76.0 + lean * 8, 23 + bob - head_up * 3)
    sh_near, sh_far = (67.0 + lean * 6, 37 + bob), (52.0 + lean * 5, 35 + bob)

    def leg(i, bias):
        a = legs[i]
        root = (hip[0] + (3 if i == 0 else -5), hip[1] + 2)
        hoof = (root[0] + math.sin(a) * 9 + 1, LORD_FEET - 2 - lift[i])
        knee = (root[0] + 7 + math.sin(a) * 4, root[1] + 11)
        hock = (hoof[0] - 5, hoof[1] - 9)
        r.part(union(L(root[0], root[1], knee[0], knee[1], 8.0, 6.0), L(knee[0], knee[1], hock[0], hock[1], 5.5, 3.8),
                     L(hock[0], hock[1], hoof[0], hoof[1] - 1.5, 3.6, 3.0)), LORD_FUR, bias=bias)
        for k in range(6):                                                  # shaggy fur
            fx = root[0] - 4 + k * 2.2
            S(fx, root[1] + 2 + k * 0.8, fx - 1.5, root[1] + 9 + k * 0.6, LORD_FUR[3] if bias == 0 else LORD_FUR[4])
        r.part(E(hoof[0] + 1, hoof[1] - 0.6, 4.2, 2.0), BLACK_IRON, bias=bias)
        S(hoof[0] + 1, hoof[1] - 2, hoof[0] + 1, hoof[1] + 0.6, BLACK_IRON[4])

    def arm(sh, angles, bias):
        a1, a2 = angles
        el = polar(sh, 15, a1)
        wr = polar(el, 14, a2)
        r.part(union(L(sh[0], sh[1], el[0], el[1], 6.5, 5.0), L(el[0], el[1], wr[0], wr[1], 5.4, 4.6)), skin, bias=bias)
        r.part(E(sh[0] + 0.5, sh[1] + 1, 5.5, 4.5, a1), skin, edge=False, bias=bias - 0.1)
        b0, b1 = polar(el, 6, a2), polar(el, 12, a2)                          # iron bracer
        r.part(L(b0[0], b0[1], b1[0], b1[1], 5.6, 5.2), BLACK_IRON, bias=bias)
        for k in (0.3, 0.7):
            rv = polar((b0[0] + (b1[0] - b0[0]) * k, b0[1] + (b1[1] - b0[1]) * k), 3.5, a2 - math.pi / 2)
            D(rv[0], rv[1], DARK_STEEL[1])
        fist = polar(wr, 2.5, a2)
        r.part(E(fist[0], fist[1], 5.0, 4.6, a2), skin, bias=bias)
        for k in range(3):
            kn = polar(fist, 3.8, a2 - 0.6 + k * 0.5)
            r.part(E(kn[0], kn[1], 1.4, 1.3), skin, edge=False, bias=bias - 0.15)
        return fist

    def pauldron(c, bias):
        r.part(E(c[0], c[1], 8.5, 6.0, -0.25), BLACK_IRON, bias=bias)
        S(c[0] - 7, c[1] + 2.5, c[0] + 7, c[1] + 0.5, BLACK_IRON[0] if bias == 0 else BLACK_IRON[2])
        for k, (dx, ln) in enumerate(((-4, 7), (0.5, 9), (5, 6))):
            b = (c[0] + dx, c[1] - 3.5)
            r.part(PG([(b[0] - 2, b[1] + 1.5), (b[0] - 1 - ln * 0.25, b[1] - ln), (b[0] + 2, b[1] + 1)], 0.4), DARK_STEEL, bias=bias)

    def cleaver(hand, ang, bias):
        """Molten-edged cleaver: dark iron blade, glowing cutting edge and runes, horned guard."""
        tip = polar(hand, 44, ang)
        base = polar(hand, 6, ang)
        nrm = ang + math.pi / 2 * (1 if math.cos(ang) >= 0 else -1)          # the edge faces down/forward
        spine = lambda p, w: polar(p, -w, nrm)
        edge = lambda p, w: polar(p, w, nrm)
        mid = polar(hand, 26, ang)
        pts = [spine(base, 3.5), spine(mid, 4.0), spine(polar(hand, 38, ang), 3.0), tip, edge(polar(hand, 40, ang), 5.0),
               edge(mid, 6.0), edge(base, 4.0)]
        r.part(PG(pts, 0.25), BLACK_IRON, bias=bias)
        e0, e1, e2 = edge(base, 3.6), edge(mid, 5.6), edge(polar(hand, 40, ang), 4.6)
        for a, b in ((e0, e1), (e1, e2), (e2, tip)):
            S(a[0], a[1], b[0], b[1], hot)
        G(e1[0], e1[1], 4, EMBER[2], 0.15 + heat * 0.15)
        for k in range(4):                                                  # runes along the fuller
            rp = polar(hand, 12 + k * 7, ang)
            D(rp[0], rp[1], EMBER[2] if k % 2 else EMBER[1])
        for k in (-1, 1):                                                   # horned guard
            g = polar(base, 5.5 * k, nrm)
            r.part(C([base, g, polar(g, 2.5, ang + 0.6 * k)], 1.4, 0.4), HORN, bias=bias)
        grip = polar(hand, -5, ang)
        r.part(L(grip[0], grip[1], base[0], base[1], 1.4), DARK_WOOD, bias=bias)
        r.part(E(grip[0], grip[1], 1.8, 1.8), RUSTED_KEY, bias=bias)

    # tail
    tb = (hip[0] - 7, hip[1] - 1)
    tp = bezier(tb, (tb[0] - 14, tb[1] + 10 + math.sin(phase * 6.28) * 2), (tb[0] - 20, tb[1] - 4), 8)
    r.part(C(tp, 2.4, 0.8), skin, bias=0.25)
    tt = tp[-1]
    r.part(PG([(tt[0] + 1, tt[1] - 3), (tt[0] - 5, tt[1] - 2.5), (tt[0] - 1, tt[1] + 3)], 0.3), skin, bias=0.2)
    # far side: horn, arm, pauldron, leg
    pauldron((sh_far[0] - 1, sh_far[1] - 1), 0.3)
    far_fist = arm(sh_far, far, 0.32)
    leg(1, 0.3)
    # torso: broad chest, abdomen, molten core
    r.part(union(E(chest[0], chest[1], 15, 13, -0.2), E(hip[0] + 3, hip[1] - 7, 11, 10), E(sh_far[0] + 3, sh_far[1] + 2, 8, 6)), skin)
    S(chest[0] - 2, chest[1] - 6, chest[0] + 3, chest[1] - 1, crease)          # pecs
    S(chest[0] + 3, chest[1] - 1, chest[0] + 10, chest[1] - 3, crease)
    for k in range(3):
        y = chest[1] + 7 + k * 3.2
        S(chest[0] - 2 + k * 0.3, y, chest[0] + 2.5, y + 0.4, crease)
        S(chest[0] + 4, y + 0.3, chest[0] + 8 - k * 0.5, y - 0.2, crease)
    core = (chest[0] + 3, chest[1] - 1)
    r.part(E(core[0], core[1], 3.6, 4.2), [EMBER[0], EMBER[1], EMBER[2], EMBER[3]], edge=False)
    for k in range(7):                                                      # cracks radiating from the core
        a = k * 0.9 + 0.3
        p0, p1 = polar(core, 3.5, a), polar(core, 7 + (k % 3) * 2, a + 0.15)
        S(p0[0], p0[1], p1[0], p1[1], hot if k % 2 else EMBER[3])
    G(core[0], core[1], 12, EMBER[2], 0.35 + heat * 0.4)
    # war belt with a skull buckle and hanging plates
    belt = (hip[0] + 2, hip[1] - 1)
    r.part(C(bezier((belt[0] - 11, belt[1] - 2), (belt[0], belt[1] + 2.5), (belt[0] + 12, belt[1] - 1.5), 8), 2.2, 2.2), LORD_FUR)
    for k in range(-2, 3):
        r.part(PG([(belt[0] + k * 4.5 - 2, belt[1] + 1), (belt[0] + k * 4.5 + 2, belt[1] + 1.2), (belt[0] + k * 4.5 + 1.5, belt[1] + 9 - abs(k)),
                   (belt[0] + k * 4.5 - 1.5, belt[1] + 8.5 - abs(k))], 0.3), BLACK_IRON, bias=0.1)
    r.part(E(belt[0] + 1, belt[1] + 0.5, 3.0, 2.8), SKULL, edge=True)
    D(belt[0], belt[1], EMBER[1])
    D(belt[0] + 2, belt[1], EMBER[1])
    leg(0, 0.0)
    # head: horns sweeping up, crown of fire, fanged jaw
    def horn(hb, ctrl, end, bias):
        pts = bezier(hb, ctrl, end, 12)
        r.part(C(pts, 3.6, 0.8), HORN, bias=bias)
        for k in range(2, len(pts) - 2, 2):
            a, b = pts[k - 1], pts[k + 1]
            ang = math.atan2(b[1] - a[1], b[0] - a[0]) + math.pi / 2
            rad = 3.6 + (0.8 - 3.6) * k / len(pts)
            e0, e1 = polar(pts[k], rad * 0.85, ang), polar(pts[k], -rad * 0.85, ang)
            S(e0[0], e0[1], e1[0], e1[1], HORN[3] if bias == 0 else HORN[4])
        D(end[0], end[1], hexc('#2a1a14'))
    horn((head[0] - 4, head[1] - 4), (head[0] - 22, head[1] - 4), (head[0] - 20, head[1] - 22), 0.3)
    for k in range(5):                                                      # crown of fire
        fx = head[0] - 6 + k * 3
        FL(fx, head[1] - 5 + abs(k - 2) * 0.8, 6 + 4 * math.sin(phase * 6.28 + k * 1.3) ** 2 + (2 - abs(k - 2)) * 1.5, 1.4,
           phase + k * 0.2, ln=-0.2)
    G(head[0] - 1, head[1] - 10, 9, EMBER[2], 0.35)
    jaw_open = mouth * 4
    if mouth > 0.2:
        r.part(E(head[0] + 4, head[1] + 4 + jaw_open * 0.5, 4.5, 1.5 + jaw_open * 0.6), [hexc('#3a0808'), hexc('#200404')], edge=False)
        G(head[0] + 4, head[1] + 4 + jaw_open * 0.5, 6, EMBER[2], 0.4 + mouth * 0.4)
        for tx in (1.5, 3.5, 5.5, 7.0):
            S(head[0] + tx, head[1] + 2.5, head[0] + tx, head[1] + 3.6, SKULL[1])
    r.part(E(head[0], head[1], 7.6, 8.0), skin)
    r.part(L(head[0] - 1, head[1] + 5 + jaw_open, head[0] + 8, head[1] + 5.5 + jaw_open, 3.0, 2.2), skin, bias=0.1)
    for tx in (2.0, 6.5):                                                   # fangs
        r.part(L(head[0] + tx, head[1] + 4.5 + jaw_open, head[0] + tx + 0.3, head[1] + 1.8 + jaw_open * 0.5, 0.9, 0.3), SKULL, edge=False)
    r.part(L(head[0] - 3, head[1] - 3.2, head[0] + 6.5, head[1] - 2.0, 2.2, 1.5), skin, bias=-0.15)    # brow
    for k in range(3):
        S(head[0] - 4, head[1] - 5 - k, head[0] + 2, head[1] - 4.6 - k * 1.1, crease)
    for ex in (head[0] + 1.0, head[0] + 4.8):
        S(ex - 0.8, head[1] - 0.6, ex + 1.8, head[1] - 0.6, hexc('#fff3a0'))
        S(ex - 0.5, head[1], ex + 1.5, head[1], EMBER[1])
    G(head[0] + 3, head[1] - 0.4, 6, EMBER[1], 0.55)
    D(head[0] + 7.2, head[1] + 1.8, LORD_SKIN[5])
    S(head[0] - 5.5, head[1] + 1, head[0] - 2, head[1] + 4, crease)
    horn((head[0] + 1, head[1] - 5), (head[0] + 6, head[1] - 20), (head[0] + 16, head[1] - 24), 0.0)
    # near side: pauldron, arm with the cleaver (both hands on it for the slam), fireball in the far hand
    pauldron((sh_near[0] + 1, sh_near[1] - 1), 0.0)
    if fire > 0:
        fb = polar(far_fist, 3 + fire * 0.6, far[1] - 0.6)
        r.part(E(fb[0], fb[1], fire, fire), [EMBER[0], EMBER[1], EMBER[2], EMBER[3]], edge=False)
        FL(fb[0], fb[1] - fire * 0.6, fire * 1.8, fire * 0.6, phase)
        G(fb[0], fb[1], fire + 7, EMBER[2], 0.6)
    near_fist = arm(sh_near, near, 0.0)
    cleaver(near_fist, sword, 0.0)
    if grip2:
        r.part(E(near_fist[0] - 2, near_fist[1] + 1.5, 4.4, 4.0), skin, bias=0.15)
    return r.render()


def lord_anims():
    idle = [draw_lord(bob=math.sin(i / 4 * 6.28) * 0.8, near=(1.15, 0.85), far=(1.35 + math.sin(i / 4 * 6.28) * 0.04, 1.6),
                      sword=1.02, phase=i / 4, heat=0.4 + 0.2 * (math.sin(i / 4 * 6.28) + 1) / 2) for i in range(4)]
    walk = []
    for i in range(6):
        p = i / 6 * 2 * math.pi
        a = math.sin(p) * 0.45
        walk.append(draw_lord(bob=-abs(math.cos(p)) * 1.8 + 0.9, lean=0.05, legs=(a, -a),
                              lift=(max(0, -math.cos(p)) * 3.5, max(0, math.cos(p)) * 3.5),
                              near=(1.15 - a * 0.2, 0.85), far=(1.35 + a * 0.4, 1.6 + a * 0.3), sword=1.0 + a * 0.08, phase=i / 6))
    slam = [
        draw_lord(lean=-0.15, near=(-0.8, -1.4), far=(-0.6, -1.2), sword=-2.2, mouth=0.4, heat=0.7, phase=0.1, grip2=True),
        draw_lord(lean=-0.3, bob=-1, near=(-1.55, -2.1), far=(-1.45, -2.0), sword=-2.55, mouth=1.0, head_up=0.5, heat=1.0, phase=0.3, grip2=True),
        draw_lord(lean=0.25, bob=1.5, near=(-0.4, 0.15), far=(-0.5, 0.05), sword=0.15, mouth=1.0, heat=1.0, phase=0.5, grip2=True),
        draw_lord(lean=0.45, bob=6, near=(0.8, 0.6), far=(0.7, 0.5), sword=0.92, legs=(0.3, -0.35), mouth=0.8, heat=1.0, phase=0.6, grip2=True),
        draw_lord(lean=0.42, bob=5.5, near=(0.82, 0.62), far=(0.72, 0.52), sword=0.94, legs=(0.3, -0.35), mouth=0.5, heat=0.9, phase=0.8, grip2=True),
        draw_lord(lean=0.15, bob=2, near=(1.0, 0.75), far=(1.1, 1.2), sword=1.0, legs=(0.15, -0.15), mouth=0.2, heat=0.6, phase=0.9),
    ]
    cast = [
        draw_lord(far=(0.2, -0.4), fire=2.0, heat=0.6, phase=0.1),
        draw_lord(far=(-0.4, -0.9), fire=4.0, mouth=0.4, heat=0.8, phase=0.3),
        draw_lord(lean=-0.1, far=(-0.8, -1.3), fire=5.5, mouth=0.7, heat=1.0, phase=0.5),
        draw_lord(lean=0.3, far=(0.0, -0.1), fire=0, mouth=0.6, heat=0.8, phase=0.7),
    ]
    roar = [draw_lord(lean=-0.2, head_up=1.0, mouth=1.0, near=(0.5, 0.3), far=(-0.2, -0.5), sword=0.5, heat=1.0, phase=k * 0.5)
            for k in range(2)]
    return dict(idle=idle, walk=walk, slam=slam, cast=cast, roar=roar)


# =====================================================================================================
# effects
# =====================================================================================================
def portal_frames():
    """A swirling rift standing on the ground: 0-1 tearing open, 2-5 loop."""
    W, H = 40, 56
    frames = []
    for i in range(6):
        size = (0.35, 0.7, 1, 1, 1, 1)[i]
        r = Rig(W, H, rim=0)
        cx, cy = W / 2, H - 2 - 24 * size
        rx, ry = 11 * size, 24 * size
        r.part(ell(cx, cy, rx + 1.5, ry + 1.5), [PORTAL[3], PORTAL[4]], edge=False, flat=0.5)
        r.part(ell(cx, cy, rx, ry), [hexc('#1a0610'), hexc('#0c0208')], edge=False, flat=0.5)
        for arm_ in range(3):                                                # spiral arms
            for t in range(18):
                a = arm_ * 2.09 + t * 0.32 + i * 0.6
                d = 1 - t / 18
                r.dot(cx + math.cos(a) * rx * d, cy + math.sin(a) * ry * d, PORTAL[min(4, 1 + t // 5)])
        rng = random.Random(i)
        for _ in range(5):
            r.dot(cx + rng.uniform(-rx, rx) * 0.6, cy + rng.uniform(-ry, ry) * 0.6, PORTAL[0])
        r.glow(cx, cy - ry * 0.6, 4, PORTAL[1], 0.4)
        r.glow(cx, cy + ry * 0.6, 4, PORTAL[1], 0.4)
        frames.append(r.render())
    return frames


def hex_bolt_frames():
    frames = []
    for i in range(4):
        r = Rig(14, 14, rim=0)
        for k in range(3):
            t = k / 2
            r.part(ell(7 - 2 - t * 3.5, 7 + math.sin(i * 1.57 + k) * 0.8, 2.6 - t * 1.6, 2.0 - t * 1.0), [HEX[min(4, 2 + k)]], edge=False, flat=0)
        r.part(ell(8, 7, 3.2, 3.0), [HEX[0], HEX[1], HEX[2]], edge=False)
        r.dot(8.5, 6.5, hexc('#2a0a3a'))                                   # a tiny skull face
        r.dot(9.8, 6.5, hexc('#2a0a3a'))
        r.dot(9.2, 8.3, hexc('#2a0a3a'))
        r.glow(8, 7, 6, HEX[2], 0.6)
        frames.append(r.render())
    return frames


def boss_gate_frames():
    """A wall of hellfire bound in chains; 24 wide, tiles every 64 px upward (4 frames)."""
    W, H = 24, 64
    frames = []
    for i in range(4):
        img = new(W, H)
        px = img.load()
        for y in range(H):
            for x in range(W):
                d = abs(x + 0.5 - W / 2) / (W / 2)
                flick = 0.5 + 0.5 * math.sin(y * 0.35 + i * 1.57 + x * 0.6) * math.sin(y * 0.12 - i * 0.8)
                v = (1 - d) * 0.75 + flick * 0.35
                if v < 0.25:
                    continue
                idx = 4 - min(3, int(v * 4))
                c = EMBER[idx]
                px[x, y] = (c[0], c[1], c[2], int(255 * min(1, (v - 0.2) * 2.5)))
        for k in range(2):                                                    # chains crossing the fire
            y0 = 16 + k * 32 + (i % 2)
            for x in range(W):
                y = y0 + math.sin(x * 0.4) * 2
                put(px, W, H, x, int(y), BLACK_IRON[1] if x % 3 else BLACK_IRON[3])
        frames.append(img)
    return frames


# =====================================================================================================
# sheet & generate
# =====================================================================================================
def all_strips():
    out = {}
    for shield in (True, False):
        for anim, frames in warden_anims(shield).items():
            out[f'DemonWarden_{anim}'] = frames
    for anim, frames in gargoyle_anims().items():
        out[f'DemonGargoyle_{anim}'] = frames
    for anim, frames in watcher_anims().items():
        out[f'DemonWatcher_{anim}'] = frames
    for anim, frames in gatekeeper_anims().items():
        out[f'DemonGatekeeper_{anim}'] = frames
    for anim, frames in priest_anims().items():
        out[f'DemonPriest_{anim}'] = frames
    for anim, frames in lord_anims().items():
        out[f'DemonLord_{anim}'] = frames
    out['Portal'] = portal_frames()
    out['HexBolt'] = hex_bolt_frames()
    out['BossGate'] = boss_gate_frames()
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip(frames).save(f'{out_dir}/{name}.png')


def sheet(path, scale=3):
    from PIL import Image
    rows = []
    for name, frames in all_strips().items():
        s = strip(frames)
        rows.append(s.resize((s.width * scale, s.height * scale), Image.NEAREST))
    pad = 6
    w = max(r_.width for r_ in rows) + pad * 2
    h = sum(r_.height + pad for r_ in rows) + pad
    out = Image.new('RGBA', (w, h), (34, 14, 18, 255))
    y = pad
    for r_ in rows:
        out.paste(Image.new('RGBA', r_.size, (58, 22, 26, 255)), (pad, y))
        out.paste(r_, (pad, y), r_)
        y += r_.height + pad
    out.save(path)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
