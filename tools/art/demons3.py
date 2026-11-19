"""Demons wave 3: Ash Drake, Void Caller and the Mimic, plus their effects. Same Rig as demons.py.
Faces right, shown at x2. Must match DemonType.java:

  name             frame     anchor        strips
  DemonDrake       128x120   (64, 66)*     fly 6, drop 3                       (* centre anchor, it flies; 2x detail)
  DemonVoidCaller  48x64     (24, 63)      idle 4, walk 6, cast 6, air 1
  DemonMimic       56x48     (28, 47)      dormant 2, wake 3, idle 4, hop 4, bite 3

The Mimic's dormant frame 0 is the chest's idle frame pixel for pixel (Chest.png, 40x34, at (8, 14)), so a sleeping
mimic and a chest can't be told apart, except for the rare twitch (dormant frame 1).

Effects: DrakeBomb (4f 16x16), GravityWell (8f 64x64, centre).

    python tools/art/demons3.py sheet out.png
"""
import math
import random
import sys
from pixel import *
from materials import BONE, BLACK_IRON, GOLD, DARK_STEEL, WOOD, DARK_WOOD
from demons import (Rig, ell, limb, union, chain, poly, bezier, rot, polar, flame, clamp, dark,
                    EMBER, HORN, ROCK)
from demons2 import membrane_wing
import effects

ASH = ramp('#c9c0bc', '#958b88', '#6a6160', '#474041', '#2c2627', '#181314')
ASH_WING = ramp('#8a6e6a', '#664e4c', '#483536', '#2e2123', '#1a1113')
VOID = ramp('#e8f6ff', '#9cc2ff', '#6266e8', '#382696', '#180c44')
VOID_ROBE = ramp('#55557c', '#3a3a5a', '#27263f', '#181729', '#0c0b16')
TONGUE = ramp('#ff9ab8', '#e05a86', '#a8325e', '#6c1a3a', '#3a0a1e')
MIMIC_SKIN = ramp('#7a5a6a', '#5a3e4e', '#3e2836', '#281822', '#160c13')


# =====================================================================================================
# Ash Drake: a lean wyvern of ash and cinders that flies high and drops fire bombs (2x detail)
# =====================================================================================================
DRAKE_K = 2
DRAKE_W, DRAKE_H = 128, 120          # pixels (64 x 60 design units x DRAKE_K)


def draw_drake(flap=0.0, phase=0.0, belly=0.3, mouth=0.0, legs=0.0, bob=0.0):
    """flap 0 = wings up .. 1 = wings down; belly 0..1 how bright the bomb furnace in the belly glows;
    legs 0 tucked .. 1 hanging open (the drop)."""
    K = DRAKE_K
    r = Rig(DRAKE_W, DRAKE_H, rim=0.35)
    E = lambda cx, cy, rx, ry, a=0.0: ell(cx * K, cy * K, rx * K, ry * K, a)
    L = lambda x0, y0, x1, y1, r0, r1=None: limb(x0 * K, y0 * K, x1 * K, y1 * K, r0 * K, (r0 if r1 is None else r1) * K)
    C = lambda pts, r0, r1: chain([(x * K, y * K) for x, y in pts], r0 * K, r1 * K)
    PG = lambda pts, b=0.6: poly([(x * K, y * K) for x, y in pts], b)
    D = lambda x, y, c: r.dot(x * K, y * K, c)
    S = lambda x0, y0, x1, y1, c: r.stroke(x0 * K, y0 * K, x1 * K, y1 * K, c)
    G = lambda x, y, rad, c, st: r.glow(x * K, y * K, rad * K, c, st)
    skin = ASH
    crease = skin[4]
    by = 33 + bob
    torso = (28.5, by)
    hot = EMBER[0] if belly > 0.7 else EMBER[1]
    # wing angle: up (-1.8 rad, as 4.48) swinging back through horizontal (pi) to down (2.0)
    phi = 4.48 - flap * 2.48

    # far wing (behind everything, darker)
    membrane_wing(r, (torso[0] + 0.5, by - 3.5), phi + 0.12, 25.0, dark(ASH_WING, 0.25), 0.3, K=K)
    # tail: whips behind with an ember tip
    tb = (torso[0] - 7, by + 1)
    tp = bezier(tb, (tb[0] - 7, tb[1] + 5 + math.sin(phase * 6.28) * 2), (tb[0] - 15, tb[1] + 1 + math.sin(phase * 6.28 + 1.5) * 3), 9)
    r.part(C(tp, 2.8, 0.6), skin, bias=0.15)
    for k in range(2, len(tp) - 1):                                          # dorsal spikes along the tail
        if k % 2 == 0:
            p_, q_ = tp[k], tp[k - 1]
            r.part(PG([(p_[0] - 0.6, p_[1] - 1.2), (q_[0], q_[1] - 2.6 + k * 0.12), (q_[0] + 0.8, q_[1] - 0.8)], 0.2), HORN, edge=False, bias=0.2)
        D(tp[k][0], tp[k][1] + 0.4, crease)
    t_ = tp[-1]
    flame(r, t_[0] * K, t_[1] * K, (3.2 + math.sin(phase * 6.28 * 2) * 0.8) * K, 1.6 * K, phase, lean=-0.6)
    G(t_[0], t_[1] - 1, 3.5, EMBER[1], 0.45)
    # far leg
    def leg(dx, bias):
        hip = (torso[0] + dx, by + 3.5)
        knee = (hip[0] + 3 - legs * 1.5, hip[1] + 2.5 + legs * 2)
        foot = (knee[0] - 1.5 + legs * 1.5, knee[1] + 2 + legs * 2.5)
        r.part(union(L(hip[0], hip[1], knee[0], knee[1], 2.2, 1.6), L(knee[0], knee[1], foot[0], foot[1], 1.4, 1.0)), skin, bias=bias)
        for k in range(3):                                                   # hooked talons
            a = 0.3 + k * 0.55 - legs * 0.4
            tip = polar(foot, 2.2, a)
            r.part(C([foot, tip], 0.55, 0.15), HORN, edge=False, bias=bias)
    leg(-2.5, 0.3)
    # body: barrel chest, belly plates with the furnace glowing through
    r.part(union(E(torso[0], by, 9.5, 5.6, -0.08), E(torso[0] + 6, by - 1.5, 5.5, 4.6, -0.3)), skin)
    for k in range(5):                                                       # belly plates
        bx = torso[0] - 5 + k * 3
        r.part(E(bx, by + 4.0 - abs(k - 2) * 0.3, 1.8, 1.0), ROCK, edge=False, bias=0.05)
        S(bx - 1.3, by + 3.4 - abs(k - 2) * 0.3, bx + 1.3, by + 3.4 - abs(k - 2) * 0.3, crease)
    for k, (x0, y0, x1, y1) in enumerate(((torso[0] - 4, by + 1.5, torso[0] - 1, by + 4.2),
                                          (torso[0] + 1.5, by + 1.2, torso[0] + 3.5, by + 4.4),
                                          (torso[0] + 5.5, by + 0.6, torso[0] + 6.5, by + 3.6))):
        S(x0 + 0.4, y0 + 0.4, x1 + 0.4, y1 + 0.4, EMBER[4])                  # glowing seams between the plates
        S(x0, y0, x1, y1, hot if belly > 0.35 else EMBER[3])
    G(torso[0], by + 3.5, 6 + belly * 4, EMBER[2], 0.15 + belly * 0.45)
    # back ridge
    for k in range(6):
        sx = torso[0] - 7 + k * 2.6
        sy = by - 5.0 - math.sin(k / 5 * 3.14) * 0.8
        r.part(PG([(sx - 1.1, sy + 1.2), (sx - 0.2, sy - 1.8 - (k % 2) * 0.6), (sx + 1.1, sy + 1.0)], 0.2), HORN, edge=False, bias=0.1)
    # scale texture
    rng = random.Random(7)
    for _ in range(26):
        sx_, sy_ = torso[0] + rng.uniform(-8, 9), by + rng.uniform(-4, 1.5)
        D(sx_, sy_, skin[3] if rng.random() < 0.6 else skin[1])
    # neck curving up to the head
    nb = (torso[0] + 9, by - 3)
    nk = bezier(nb, (nb[0] + 5, nb[1] - 4), (nb[0] + 11, nb[1] - 6 + mouth * 1.5), 8)
    r.part(C(nk, 3.4, 2.4), skin)
    for k in range(1, len(nk) - 1):                                          # throat plates
        D(nk[k][0] + 0.6, nk[k][1] + 1.9, ROCK[2])
        D(nk[k][0] - 0.6, nk[k][1] - 2.0, HORN[2] if k % 2 else crease)
    head = (nk[-1][0] + 1.5, nk[-1][1])
    # horns swept back
    for hx, bias in ((-0.5, 0.3), (0.8, 0.0)):
        hb = (head[0] + hx - 1, head[1] - 2)
        hp = bezier(hb, (hb[0] - 3, hb[1] - 3.5), (hb[0] - 7, hb[1] - 3), 6)
        r.part(C(hp, 1.2, 0.25), HORN, bias=bias)
        for k in (2, 4):
            D(hp[k][0], hp[k][1] + 0.4, HORN[3])
    # lower jaw (opens with mouth), skull, snout
    jaw_a = 0.15 + mouth * 0.55
    jt = polar((head[0] + 0.5, head[1] + 1.5), 6.0, jaw_a)
    r.part(L(head[0] + 0.5, head[1] + 1.5, jt[0], jt[1], 1.6, 0.8), skin, bias=0.15)
    if mouth > 0.2:
        G((head[0] + jt[0]) / 2 + 2, (head[1] + jt[1]) / 2 + 0.5, 3 + mouth * 2, EMBER[1], 0.3 + mouth * 0.4)
        for k in range(3):
            D(head[0] + 2.5 + k * 1.6, head[1] + 1.6 + mouth * (k * 0.5), BONE[0])
    r.part(union(E(head[0], head[1], 3.8, 2.7, -0.1), L(head[0] + 1, head[1], head[0] + 7, head[1] + 0.8, 2.2, 1.2)), skin)
    S(head[0] + 1.5, head[1] + 1.3, head[0] + 6.5, head[1] + 1.5, crease)       # lip line
    D(head[0] + 6.6, head[1] + 0.1, skin[5])                                   # nostril
    r.part(E(head[0] + 6.6, head[1] - 0.6, 0.9, 0.5), skin, edge=False, bias=-0.1)
    S(head[0] - 1.5, head[1] - 1.9, head[0] + 2.5, head[1] - 1.2, crease)       # brow ridge
    D(head[0] + 1.3, head[1] - 0.6, EMBER[1])                                  # eye
    D(head[0] + 1.8, head[1] - 0.6, EMBER[0])
    G(head[0] + 1.5, head[1] - 0.6, 2.5, EMBER[1], 0.5)
    for k in range(3):                                                         # teeth along the closed lip
        D(head[0] + 3 + k * 1.4, head[1] + 1.8, BONE[1])
    # near leg
    leg(2.5, 0.0)
    # near wing (in front)
    membrane_wing(r, (torso[0] + 2, by - 3), phi, 27.0, ASH_WING, 0.0, K=K, edge_c=EMBER[3])
    # embers drifting off the wings
    rng = random.Random(int(phase * 100) + 3)
    for _ in range(3):
        D(torso[0] + rng.uniform(-14, 4), by + rng.uniform(-14, 10), EMBER[rng.randint(1, 2)])
    return r.render()


def drake_anims():
    fly = []
    for i in range(6):
        f = (1 - math.cos(i / 6 * 6.28)) / 2
        fly.append(draw_drake(flap=f, phase=i / 6, belly=0.3 + 0.15 * f, bob=-1.2 * math.sin(i / 6 * 6.28) - 0.2))
    drop = [draw_drake(flap=0.35, phase=0.1, belly=0.75, legs=0.4, mouth=0.4, bob=-0.5),
            draw_drake(flap=0.55, phase=0.25, belly=1.0, legs=1.0, mouth=0.9, bob=0.5),
            draw_drake(flap=0.75, phase=0.4, belly=0.6, legs=0.6, mouth=0.5, bob=0.3)]
    return dict(fly=fly, drop=drop)


def drake_bomb_frames():
    """A clump of ash-crusted slag with the fire glowing through the cracks; tumbles (4 frames)."""
    frames = []
    for i in range(4):
        r = Rig(16, 16, rim=0)
        a = i * 1.57
        r.part(union(ell(8, 8, 4.6, 4.2, a), ell(8 + math.cos(a) * 2, 8 + math.sin(a) * 2, 2.6, 2.4)), ROCK)
        for k in range(3):
            b = a + k * 2.1
            p0 = (8 + math.cos(b) * 0.5, 8 + math.sin(b) * 0.5)
            p1 = (8 + math.cos(b + 0.3) * 4, 8 + math.sin(b + 0.3) * 4)
            r.stroke(p0[0], p0[1], p1[0], p1[1], EMBER[1] if k else EMBER[0])
        r.dot(8, 8, EMBER[0])
        r.glow(8, 8, 7, EMBER[2], 0.55)
        frames.append(r.render())
    return frames


# =====================================================================================================
# Void Caller: a gaunt hooded figure drifting above the ground, a starry void where its face should be;
# it calls up gravity wells that drag you in
# =====================================================================================================
CALLER_W, CALLER_H, CALLER_FEET = 48, 64, 63


def draw_caller(bob=0.0, sway=0.0, raise_=0.0, orb=0.3, phase=0.0, release=False):
    """raise_ 0 hands in front .. 1 overhead; orb: size of the void orb between the hands; release: arms thrust
    forward, the orb gone (the well was called)."""
    r = Rig(CALLER_W, CALLER_H, rim=0.3)
    top = (23.0, 27 + bob)
    head = (25.0, 18 + bob)
    hem_y = CALLER_FEET - 5 + bob
    # the glow it floats on
    r.glow(top[0] + 1, CALLER_FEET - 1.5, 6, VOID[2], 0.35)
    # orbiting shards behind the head
    shards = []
    for k in range(4):
        a = phase * 6.28 + k * 1.571
        shards.append((math.sin(a), head[0] + math.cos(a) * 8.5, head[1] - 4 + math.sin(a) * 2.2, k))
    def shard(s):
        _, sx, sy, k = s
        r.part(poly([(sx, sy - 2.2), (sx + 1.3, sy), (sx, sy + 1.6), (sx - 1.2, sy - 0.2)], 0.2), ROCK, bias=0.15)
        r.dot(sx, sy - 0.6, VOID[1])
    for s in shards:
        if s[0] < 0:
            shard(s)
    # far arm (sleeve) and hand
    def arm(dx, bias):
        sh = (top[0] + dx, top[1] - 1)
        if release:
            hand = (sh[0] + 11, sh[1] + 1)
        else:
            hand = (sh[0] + 7 - raise_ * 4 + dx * 0.3, sh[1] + 10 - raise_ * 21)
        el = ((sh[0] + hand[0]) / 2 + (1 - raise_) * 1.5 + 1, (sh[1] + hand[1]) / 2 + 2 - raise_ * 1)
        r.part(union(limb(sh[0], sh[1], el[0], el[1], 2.4, 2.6), limb(el[0], el[1], hand[0], hand[1], 2.6, 3.0)), VOID_ROBE, bias=bias)
        r.part(ell(hand[0] + 0.5, hand[1], 1.3, 1.2), BONE, bias=bias)
        for k in range(3):                                                   # long fingers
            a = (-1.2 if raise_ > 0.5 and not release else -0.2) + (k - 1) * 0.45
            tip = polar((hand[0] + 0.8, hand[1]), 2.6, a)
            r.stroke(hand[0] + 0.8, hand[1], tip[0], tip[1], BONE[2] if bias == 0 else BONE[3])
        return hand
    far = arm(-2.5, 0.3)
    # robe: narrow shoulders, long tattered skirt
    hem = []
    for k in range(11):
        t = k / 10
        hem.append((top[0] - 9 + 19 * t + sway * (1 - abs(t - 0.5) * 2), hem_y - (k % 2) * 3 + math.sin(phase * 6.28 + k) * 0.6))
    r.part(poly([(top[0] - 5, top[1] - 3), (top[0] - 1, top[1] - 6), (top[0] + 5, top[1] - 3)] + list(reversed(hem)), 0.45), VOID_ROBE)
    for k, dx in enumerate((-4, 0, 4)):                                       # folds
        r.stroke(top[0] + dx * 0.6, top[1] + 1, top[0] + dx + sway * 0.4, hem_y - 3, VOID_ROBE[3])
    # star motes in the cloth
    rng = random.Random(int(phase * 40) + 11)
    for _ in range(5):
        r.dot(top[0] + rng.uniform(-6, 7), top[1] + rng.uniform(2, hem_y - top[1] - 3), VOID[rng.randint(1, 2)])
    # rune belt
    r.stroke(top[0] - 5, top[1] + 6, top[0] + 5, top[1] + 6.5, VOID[3])
    for k in range(3):
        r.dot(top[0] - 3 + k * 3, top[1] + 6.2, VOID[1])
    # hood with a pointed back, the void face inside
    r.part(union(ell(head[0] - 0.5, head[1], 5.2, 6.0), poly([(head[0] - 4, head[1] - 3), (head[0] - 8, head[1] + 1), (head[0] - 3, head[1] + 4)], 0.3)),
           VOID_ROBE, bias=-0.05)
    r.part(ell(head[0] + 1.6, head[1] + 0.8, 3.0, 4.0), [hexc('#05030c'), hexc('#020107')], edge=False, flat=0.5)
    for ex, ey in ((head[0] + 0.6, head[1]), (head[0] + 3.0, head[1] + 0.2)):
        r.dot(ex, ey, VOID[0])
    r.glow(head[0] + 1.8, head[1], 3.0 + orb * 2, VOID[1], 0.4 + orb * 0.3)
    rng = random.Random(5)
    for _ in range(3):
        r.dot(head[0] + 1.6 + rng.uniform(-2, 2), head[1] + 1 + rng.uniform(0, 3), VOID[2])
    r.stroke(head[0] - 2.5, head[1] - 4.5, head[0] + 3, head[1] - 3.5, VOID_ROBE[0])      # hood rim catching light
    # shards in front
    for s in shards:
        if s[0] >= 0:
            shard(s)
    # near arm
    near = arm(2.5, 0.0)
    # the void orb between the hands
    if not release and orb > 0.05:
        ox, oy = (far[0] + near[0]) / 2 + 1.5, (far[1] + near[1]) / 2 - 1.5 - raise_ * 1.5
        rad = 1.5 + orb * 3.2
        r.part(ell(ox, oy, rad + 0.8, rad + 0.8), [VOID[2], VOID[3]], edge=False, flat=0.3)
        r.part(ell(ox, oy, rad, rad), [hexc('#08040f'), hexc('#020107')], edge=False, flat=0.5)
        for k in range(8):                                                   # matter spiralling in
            a = phase * 6.28 * 2 + k * 0.8
            d = rad + 1.5 - (k % 4) * 0.6
            r.dot(ox + math.cos(a) * d, oy + math.sin(a) * d, VOID[k % 3])
        r.glow(ox, oy, rad + 4, VOID[2], 0.45 + orb * 0.3)
    return r.render()


def caller_anims():
    idle = [draw_caller(bob=math.sin(i / 4 * 6.28) * 0.8, sway=math.sin(i / 4 * 6.28) * 0.6, phase=i / 4) for i in range(4)]
    walk = [draw_caller(bob=math.sin(i / 6 * 6.28) * 0.6 - 0.3, sway=-1.6 - math.sin(i / 6 * 6.28) * 0.6, phase=i / 6)
            for i in range(6)]
    cast = [draw_caller(raise_=min(1, k * 0.27), orb=0.3 + k * 0.17, phase=k / 5, sway=-k * 0.2) for k in range(5)]
    cast.append(draw_caller(release=True, phase=0.9, sway=1.0, bob=-0.5))
    air = [draw_caller(bob=-1, sway=1.5, phase=0.3)]
    return dict(idle=idle, walk=walk, cast=cast, air=air)


def gravity_well_frames():
    """A knot of void with matter spiralling into it, seen from the side (8 frames, loops)."""
    W = H = 64
    cx = cy = 32
    frames = []
    for i in range(8):
        r = Rig(W, H, rim=0)
        spin = i / 8 * 6.28
        # accretion ring behind
        for k in range(48):
            a = k / 48 * 6.28 + spin * 0.5
            if math.sin(a) < 0:
                r.dot(cx + math.cos(a) * 24, cy + math.sin(a) * 6, VOID[2 + (k % 2)])
        # spiral arms of dust and starlight falling in
        for arm_ in range(4):
            for t in range(26):
                d = 21 * (1 - t / 26) + 8
                a = arm_ * 1.571 + t * 0.26 + spin
                c = VOID[min(4, 1 + (26 - t) // 7)] if t % 3 else VOID[0]
                r.dot(cx + math.cos(a) * d, cy + math.sin(a) * d * 0.8, c)
        r.part(ell(cx, cy, 9, 9), [VOID[2], VOID[3]], edge=False, flat=0.4)
        r.part(ell(cx, cy, 7, 7), [hexc('#06030d'), hexc('#010005')], edge=False, flat=0.5)
        # accretion ring in front
        for k in range(48):
            a = k / 48 * 6.28 + spin * 0.5
            if math.sin(a) >= 0:
                r.dot(cx + math.cos(a) * 24, cy + math.sin(a) * 6, VOID[1 + (k % 2)])
        rng = random.Random(i)
        for _ in range(6):
            a, d = rng.uniform(0, 6.28), rng.uniform(10, 27)
            r.dot(cx + math.cos(a) * d, cy + math.sin(a) * d * 0.8, VOID[0])
        r.glow(cx, cy, 14, VOID[2], 0.5)
        frames.append(r.render())
    return frames


# =====================================================================================================
# Mimic: a chest that isn't. Its sleeping frame is the chest itself; awake, the lid is a jaw full of teeth,
# a tongue lolls out and spindly legs push the box up
# =====================================================================================================
MIMIC_W, MIMIC_H = 56, 48
CHEST_X, CHEST_Y = 8, 14                 # where the 40x34 chest frame sits in the mimic frame


def _chest():
    return effects.chest_frames()


def draw_mimic(lift=0, gap=0, tongue=0.0, legs='stand', eyes=0.6, phase=0.0, tilt=0):
    """lift: px the box is raised on its legs; gap: px the lid (upper jaw) is raised; tilt: px the lid's front
    edge rises further than its back (a gaping bite); legs: stand / crouch / spread / tuck."""
    W, H = MIMIC_W, MIMIC_H
    x0, x1 = CHEST_X + 5, CHEST_X + 34                    # the chest's box edges (as in effects.chest_frames)
    base_bot = CHEST_Y + 31 - lift
    hinge = CHEST_Y + 18 - lift
    lid_h = 9
    img = new(W, H)
    px = img.load()

    # spindly legs (drawn first, the box covers their roots)
    rig = Rig(W, H, rim=0.3)
    ground = H - 2
    feet = {'stand': (6, 1.5), 'crouch': (8, 0.5), 'spread': (11, 2.5), 'tuck': (4, -1.0)}[legs]
    for k, (rx, side) in enumerate(((x0 + 5, -1), (x1 - 5, 1), (x0 + 10, -1), (x1 - 10, 1))):
        bias = 0.3 if k >= 2 else 0.0
        root = (rx, base_bot - 1)
        foot_y = ground if legs != 'tuck' else base_bot + 4
        foot = (rx + side * feet[0] * (0.6 if k >= 2 else 1), foot_y)
        knee = ((root[0] + foot[0]) / 2 + side * (3 + feet[1]), min(root[1], foot_y) - 1 - feet[1])
        if lift > 2 or legs == 'tuck':
            rig.part(union(limb(root[0], root[1], knee[0], knee[1], 1.5, 1.1), limb(knee[0], knee[1], foot[0], foot[1], 1.1, 0.6)),
                     MIMIC_SKIN, bias=bias)
            rig.part(chain([foot, (foot[0] + side * 2, foot[1] - 0.5)], 0.6, 0.2), HORN, edge=False, bias=bias)
    legs_img = rig.render()

    # box base (the lower jaw), as the chest draws it
    top = hinge + 3
    for y in range(top, base_bot + 1):
        for x in range(x0, x1 + 1):
            s = 1 if y == top else 4 if y == base_bot else 2
            if (y - top) % 4 == 3 and y != base_bot:
                s = 3
            if x == x0:
                s = max(1, s - 1)
            if x == x1:
                s = min(4, s + 1)
            put(px, W, H, x, y, WOOD[s])
    for bx in (x0 + 4, x1 - 4):
        for y in range(top, base_bot + 1):
            put(px, W, H, bx, y, GOLD[2])
            put(px, W, H, bx + 1, y, GOLD[3])
    for y in range(top, base_bot + 1):
        put(px, W, H, x0, y, GOLD[2])
        put(px, W, H, x1, y, GOLD[3])
    # mouth: the dark inside of the box and the gap up to the lid
    lid_bottom_back = hinge - gap
    lid_bottom_front = hinge - gap - tilt
    for x in range(x0 + 1, x1):
        t = (x - x0) / (x1 - x0)
        lb = int(round(lid_bottom_back + (lid_bottom_front - lid_bottom_back) * t))
        for y in range(lb, top):
            put(px, W, H, x, y, (26, 8, 18, 255) if y > lb + 1 else (60, 16, 32, 255))
    # eyes glowing in the dark of the mouth
    if gap >= 3:
        ey = hinge - gap // 2
        for ex in (x0 + 9, x1 - 9):
            put(px, W, H, ex, ey, (255, 230, 120, 255) if eyes > 0.5 else (230, 140, 60, 255))
            put(px, W, H, ex + 1, ey, (255, 170, 60, 255))
    # lower teeth along the box rim, upper teeth hanging from the lid
    for k, x in enumerate(range(x0 + 2, x1 - 1, 3)):
        h = 2 if k % 2 == 0 else 1
        for j in range(h):
            put(px, W, H, x, top - 1 - j, BONE[1] if j == 0 else BONE[0])
        t = (x - x0) / (x1 - x0)
        lb = int(round(lid_bottom_back + (lid_bottom_front - lid_bottom_back) * t))
        if gap > 0:
            for j in range(min(gap + 1, 2 + (k % 2))):
                put(px, W, H, x + 1, lb + j, BONE[0] if j == 0 else BONE[1])
    # lid (upper jaw): the chest's lid front, lifted and tilted
    for x in range(x0, x1 + 1):
        t = (x - x0) / (x1 - x0)
        lb = int(round(lid_bottom_back + (lid_bottom_front - lid_bottom_back) * t))
        ly0 = lb - lid_h
        for y in range(ly0, lb):
            if y == ly0 and (x - x0 < 2 or x1 - x < 2):
                continue
            s = 0 if y == ly0 else 1 if y == ly0 + 1 else 2
            if y == lb - 1:
                s = 3
            if x == x1:
                s = min(4, s + 1)
            c = GOLD[1] if x in (x0 + 4, x1 - 4) else GOLD[3] if x in (x0 + 5, x1 - 3) else WOOD[s]
            put(px, W, H, x, y, c)
    # the lock plate is now a fanged brow
    lx = (x0 + x1) // 2 - 1
    lbm = int(round(lid_bottom_back + (lid_bottom_front - lid_bottom_back) * 0.5))
    for y in range(lbm - 3, lbm):
        for x in range(lx, lx + 4):
            put(px, W, H, x, y, GOLD[1])
    put(px, W, H, lx + 1, lbm - 2, (40, 24, 12, 255))
    put(px, W, H, lx + 2, lbm - 2, (40, 24, 12, 255))
    # tongue lolling out over the front lip, toward the facing side
    if tongue > 0:
        tl = int(4 + tongue * 9)
        sway = math.sin(phase * 6.28) * 1.5
        for k in range(tl):
            tx = x1 - 8 + k * 0.9
            ty = top - 1 + k * 0.6 + (k / tl) ** 2 * (3 + sway)
            for w in (-1, 0, 1):
                c = TONGUE[1] if w < 0 else TONGUE[2] if w == 0 else TONGUE[3]
                put(px, W, H, int(tx), int(ty + w), c)
        put(px, W, H, int(x1 - 8 + tl * 0.9), int(top + tl * 0.6 + 3 + sway), TONGUE[0])
    body = outline(img)
    legs_img.alpha_composite(body)
    return legs_img


def mimic_anims():
    chest = _chest()
    sleep = new(MIMIC_W, MIMIC_H)
    sleep.alpha_composite(chest[0], (CHEST_X, CHEST_Y))
    # the twitch: the same chest with the lid lifted a hair, teeth and an eye showing
    twitch = draw_mimic(gap=2, tongue=0.0, eyes=1.0)
    dormant = [sleep, twitch]
    wake = [draw_mimic(lift=0, gap=4, tilt=1, eyes=1.0),
            draw_mimic(lift=4, gap=7, tilt=3, tongue=0.6, legs='spread', eyes=1.0, phase=0.2),
            draw_mimic(lift=7, gap=5, tilt=2, tongue=0.8, legs='stand', eyes=1.0, phase=0.4)]
    idle = [draw_mimic(lift=7 - (i % 2), gap=4 + (i == 1), tilt=1 + (i == 2), tongue=0.7 + 0.15 * math.sin(i / 4 * 6.28),
                       phase=i / 4) for i in range(4)]
    hop = [draw_mimic(lift=3, gap=3, tilt=1, tongue=0.5, legs='crouch', phase=0.0),
           draw_mimic(lift=9, gap=6, tilt=3, tongue=0.9, legs='spread', phase=0.3),
           draw_mimic(lift=8, gap=8, tilt=4, tongue=1.0, legs='tuck', phase=0.6),
           draw_mimic(lift=4, gap=4, tilt=1, tongue=0.6, legs='crouch', phase=0.9)]
    bite = [draw_mimic(lift=6, gap=10, tilt=5, tongue=0.4, legs='spread', eyes=1.0),
            draw_mimic(lift=5, gap=1, tilt=0, tongue=0.0, legs='crouch', eyes=1.0),
            draw_mimic(lift=7, gap=4, tilt=1, tongue=0.8, legs='stand', eyes=0.6)]
    return dict(dormant=dormant, wake=wake, idle=idle, hop=hop, bite=bite)


# =====================================================================================================
def all_strips():
    out = {}
    for anim, frames in drake_anims().items():
        out[f'DemonDrake_{anim}'] = frames
    for anim, frames in caller_anims().items():
        out[f'DemonVoidCaller_{anim}'] = frames
    for anim, frames in mimic_anims().items():
        out[f'DemonMimic_{anim}'] = frames
    out['DrakeBomb'] = drake_bomb_frames()
    out['GravityWell'] = gravity_well_frames()
    return out


def generate(out_dir):
    for name, frames in all_strips().items():
        strip(frames).save(f'{out_dir}/{name}.png')


def sheet(path, scale=3):
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
