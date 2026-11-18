"""Weapons drawn along the diagonal from the handle (bottom-left) to the tip (top-right).

The Java side relies on this convention: native canvas S x S, 2px margin, handle end at
(2, S-3) and tip at (S-3, 2). Display length = (S - 4) * sqrt(2) * scale.
"""
import math
import random
from pixel import *
from materials import *

MARGIN = 2
SCALE = 2  # intended display scale (pixels look crisp around this size)


def canvas_for_length(display_length):
    return int(round(display_length / (SCALE * math.sqrt(2)))) + 2 * MARGIN


# ----- part primitives in (u, d) space: u = distance along the axis from the handle end,
# d = signed distance from the axis (positive = up-left = lit side) -----
def shade_round(d, r):
    q = d / max(r, 0.5)
    if q > 0.45:
        return 0 if q > 0.8 and r >= 2 else 1
    if q > -0.2:
        return 2
    if q > -0.7:
        return 3
    return 4


def cyl(u0, u1, r, mat, bands=None, band_mat=None):
    """Round shaft/handle. r may be a number or a function of the local fraction (0..1)."""
    def f(u, d, L):
        if not (u0 <= u <= u1):
            return None
        rr = r((u - u0) / max(u1 - u0, 1e-6)) if callable(r) else r
        if abs(d) > rr:
            return None
        if bands and int((u - u0) // bands) % 2 == 1:
            m = band_mat or mat
            return m[min(4, shade_round(d, rr) + 1)]
        return mat[shade_round(d, rr)]
    return f


def wrap(u0, u1, r, mat, period=3):
    """Diagonally wrapped grip (leather/cloth)."""
    def f(u, d, L):
        if not (u0 <= u <= u1) or abs(d) > r:
            return None
        s = shade_round(d, r)
        if int(u + d * 0.9) % period == 0:
            s = min(4, s + 2)
        return mat[s]
    return f


def blade(u0, u1, hw, mat, tip=0.18, fuller=None, curve=0.0, edge_mat=None, spots=None):
    """Flat blade. hw: half width at the base; tapers to a point over the last `tip` fraction.
    curve bends the blade towards the lit side (katana)."""
    def f(u, d, L):
        if not (u0 <= u <= u1):
            return None
        t = (u - u0) / max(u1 - u0, 1e-6)
        dd = d - curve * t * t
        w = hw if t < 1 - tip else hw * (1 - t) / tip
        if abs(dd) > w + 0.25:
            return None
        q = dd / max(w, 0.5)
        if q > 0.55:
            c = (edge_mat or mat)[0]
        elif fuller and abs(dd) < fuller and t < 0.85:
            c = mat[3]
        elif q > 0:
            c = mat[1]
        elif q > -0.6:
            c = mat[2]
        else:
            c = mat[3]
        if spots and spots(u, d):
            c = RUST[2] if c in (mat[1], mat[0]) else RUST[3]
        return c
    return f


def box(u0, u1, d0, d1, mat, rivets=None):
    """Axis-aligned box in (u, d): guards, hammer heads."""
    def f(u, d, L):
        if not (u0 <= u <= u1 and d0 <= d <= d1):
            return None
        if rivets and any(abs(u - ru) < 0.8 and abs(d - rd) < 0.8 for ru, rd in rivets):
            return GOLD[1]
        if d > d1 - 1.2:
            return mat[0]
        if u > u1 - 1.0:
            return mat[1]
        if d < d0 + 1.2 or u < u0 + 1.0:
            return mat[3]
        return mat[2]
    return f


def ball(uc, dc, r, mat):
    def f(u, d, L):
        du, dd = u - uc, d - dc
        if du * du + dd * dd > r * r:
            return None
        # light from the lit side (+d) and slightly towards the tip
        lit = (dd * 0.8 + du * 0.3) / r
        if lit > 0.55 and (du * du + dd * dd) < (r * 0.55) ** 2 + 4:
            return mat[0]
        if lit > 0.1:
            return mat[1]
        if lit > -0.45:
            return mat[2]
        return mat[3]
    return f


def tri(ub, uc_tip, hw, dc, mat):
    """Triangle pointing along the axis: base at ub (half width hw), apex at uc_tip, centred on d=dc."""
    def f(u, d, L):
        lo, hi = min(ub, uc_tip), max(ub, uc_tip)
        if not (lo <= u <= hi):
            return None
        t = abs(u - ub) / max(abs(uc_tip - ub), 1e-6)
        w = hw * (1 - t)
        if abs(d - dc) > w + 0.3:
            return None
        return mat[1] if d - dc > 0 else mat[2]
    return f


def spike(uc, dc, angle_deg, length, mat):
    """Spike starting at (uc, dc) pointing in a direction (in u/d space)."""
    a = math.radians(angle_deg)
    du, dd = math.cos(a), math.sin(a)

    def f(u, d, L):
        pu, pd = u - uc, d - dc
        along = pu * du + pd * dd
        if along < 0 or along > length:
            return None
        across = abs(-pu * dd + pd * du)
        w = 1.4 * (1 - along / length) + 0.2
        if across > w:
            return None
        return mat[0] if along > length * 0.6 else mat[1]
    return f


def render(S, parts, outline_color=OUTLINE, post=None):
    img = new(S, S)
    px = img.load()
    hx, hy = MARGIN + 0.5, S - MARGIN - 0.5
    tx, ty = S - MARGIN - 0.5, MARGIN + 0.5
    L = math.hypot(tx - hx, ty - hy)
    ax, ay = (tx - hx) / L, (ty - hy) / L
    pxx, pyy = -ay * -1, ax * -1  # perpendicular
    # make +d point up-left
    if pxx > 0 or pyy > 0:
        pxx, pyy = -pxx, -pyy
    pxx, pyy = (-1 / math.sqrt(2), -1 / math.sqrt(2))
    for y in range(S):
        for x in range(S):
            vx, vy = x + 0.5 - hx, y + 0.5 - hy
            u = vx * ax + vy * ay
            d = vx * pxx + vy * pyy
            for part in reversed(parts):
                c = part(u, d, L)
                if c is not None:
                    px[x, y] = c
                    break
    if post:
        post(img, px, S, L)
    return outline(img, outline_color)


def axis_len(S):
    return (S - 2 * MARGIN) * math.sqrt(2) - 1


# ----- weapons -----
def rusty_sword():
    S = canvas_for_length(95)
    L = axis_len(S)
    rng = random.Random(3)
    spots = {(int(rng.uniform(L * 0.3, L)), int(rng.uniform(-2, 2))) for _ in range(9)}
    return render(S, [
        ball(1.6, 0, 2.2, RUST),
        wrap(2, L * 0.24, 1.6, LEATHER),
        box(L * 0.24, L * 0.29, -5, 5, RUST),
        blade(L * 0.29, L, 2.7, RUST, spots=lambda u, d: (int(u), int(round(d))) in spots),
    ])


def iron_sword():
    S = canvas_for_length(105)
    L = axis_len(S)
    return render(S, [
        ball(1.8, 0, 2.4, GOLD),
        ball(1.8, 0, 1.0, TEAL),
        wrap(2.5, L * 0.23, 1.6, LEATHER),
        box(L * 0.23, L * 0.28, -6, 6, GOLD),
        ball(L * 0.255, 0, 1.4, CRIMSON),
        blade(L * 0.28, L, 2.8, STEEL, fuller=0.7),
    ])


def short_sword():
    S = canvas_for_length(95)
    L = axis_len(S)
    return render(S, [
        ball(1.6, 0, 2.0, STEEL),
        wrap(2, L * 0.25, 1.6, DARK_WOOD, period=2),
        box(L * 0.25, L * 0.3, -4.5, 4.5, DARK_STEEL),
        blade(L * 0.3, L, 3.4, STEEL, tip=0.3, fuller=0.6),
    ])


def katana():
    S = canvas_for_length(112)
    L = axis_len(S)
    return render(S, [
        cyl(1, 3, 1.8, GOLD),
        wrap(3, L * 0.27, 1.7, CLOTH_WHITE, period=3),
        cyl(L * 0.27, L * 0.3, 3.4, BLACK_IRON),
        blade(L * 0.3, L, 1.9, STEEL, tip=0.12, curve=2.2, edge_mat=CLOTH_WHITE),
    ])


def zweihander():
    S = canvas_for_length(178)
    L = axis_len(S)
    return render(S, [
        ball(2.2, 0, 3.0, DARK_STEEL),
        wrap(3, L * 0.2, 2.0, LEATHER),
        box(L * 0.2, L * 0.235, -10, 10, DARK_STEEL),
        ball(L * 0.217, 9.5, 2.0, DARK_STEEL),
        ball(L * 0.217, -9.5, 2.0, DARK_STEEL),
        ball(L * 0.217, 0, 1.6, CRIMSON),
        cyl(L * 0.235, L * 0.29, 2.6, STEEL),
        tri(L * 0.29, L * 0.255, 3.6, 3.2, STEEL),
        tri(L * 0.29, L * 0.255, 3.6, -3.2, STEEL),
        blade(L * 0.29, L, 4.0, STEEL, tip=0.14, fuller=1.0),
    ])


def club():
    S = canvas_for_length(100)
    L = axis_len(S)
    rng = random.Random(7)
    knots = [(rng.uniform(L * 0.45, L * 0.95), rng.uniform(-3, 3)) for _ in range(5)]

    def knotty(u, d, Ll):
        for ku, kd in knots:
            if (u - ku) ** 2 + (d - kd) ** 2 < 1.3:
                return WOOD[4]
        return None
    return render(S, [
        cyl(1, L * 0.3, 1.8, LEATHER, bands=2, band_mat=LEATHER),
        cyl(L * 0.3, L * 0.34, 2.6, DARK_STEEL),
        cyl(L * 0.34, L, lambda t: 2.4 + 3.4 * min(1, t * 1.3) - (2.8 * max(0, t - 0.88) / 0.12), WOOD),
        knotty,
    ])


def big_hammer():
    S = canvas_for_length(122)
    L = axis_len(S)
    return render(S, [
        cyl(1, L * 0.2, 1.8, LEATHER, bands=2),
        cyl(L * 0.2, L * 0.8, 1.6, WOOD),
        box(L * 0.74, L, -11, 11, STEEL, rivets=[(L * 0.87, 6), (L * 0.87, -6)]),
        box(L * 0.84, L * 0.9, -11.5, 11.5, DARK_STEEL),
    ])


def morningstar():
    S = canvas_for_length(162)
    L = axis_len(S)
    head = L * 0.86
    r = min(8.0, L * 0.11)
    parts = [
        cyl(1, L * 0.2, 1.9, LEATHER, bands=2),
        cyl(L * 0.2, head - r + 1, 1.7, DARK_WOOD),
        cyl(head - r - 2, head - r + 1, 2.8, DARK_STEEL),
    ]
    for k in range(8):
        a = k * 45 + 22.5
        parts.append(spike(head + math.cos(math.radians(a)) * (r - 1), math.sin(math.radians(a)) * (r - 1), a, 4.5, STEEL))
    parts.append(ball(head, 0, r, DARK_STEEL))
    return render(S, parts)


def spring_hammer():
    S = canvas_for_length(112)
    L = axis_len(S)
    coil = lambda u, d, Ll: (GOLD[1] if int(u) % 2 == 0 else GOLD[3]) if (L * 0.6 <= u <= L * 0.78 and abs(d) <= 3.2) else None
    return render(S, [
        cyl(1, L * 0.22, 1.8, CRIMSON, bands=2),
        cyl(L * 0.22, L * 0.6, 1.5, STEEL),
        coil,
        box(L * 0.78, L, -8, 8, STEEL),
        box(L * 0.78, L * 0.84, -8.5, 8.5, CRIMSON),
        box(L * 0.94, L, -8.5, 8.5, CRIMSON),
    ])


def trident(shaft_mat, head_mat, display_length=132, heavy=False, gem=None, frost=False):
    S = canvas_for_length(display_length)
    L = axis_len(S)
    base = L * 0.74
    pr = 1.3 if not heavy else 1.9
    side = 5.5 if not heavy else 7
    parts = [
        cyl(1, base, 1.4 if not heavy else 2.0, shaft_mat, bands=6 if not heavy else None),
        cyl(1, 4, 2.2, head_mat),
        box(base - 1, base + 2.5, -side - 1.5, side + 1.5, head_mat),
        # prongs
        cyl(base, L - 3, pr, head_mat),
        tri(L - 4, L, pr + 1.4, 0, head_mat),
        box(base + 1, L * 0.93, side - pr, side + pr, head_mat),
        tri(L * 0.93, L * 0.995, pr + 1.2, side, head_mat),
        box(base + 1, L * 0.93, -side - pr, -side + pr, head_mat),
        tri(L * 0.93, L * 0.995, pr + 1.2, -side, head_mat),
        # barbs
        tri(L * 0.86, L * 0.82, 1.6, side + 2.2, head_mat),
        tri(L * 0.86, L * 0.82, 1.6, -side - 2.2, head_mat),
    ]
    if gem:
        parts.append(ball(base + 0.8, 0, 2.2, gem))
    if frost:
        rng = random.Random(11)
        flakes = {(int(rng.uniform(L * 0.3, base)), int(rng.uniform(-2, 3))) for _ in range(10)}
        parts.append(lambda u, d, Ll: ICE[0] if (int(u), int(round(d))) in flakes else None)
    return render(S, parts)


def spear():
    S = canvas_for_length(84)
    L = axis_len(S)
    return render(S, [
        tri(L * 0.0, L * 0.12, 3.2, 1.6, CRIMSON),
        tri(L * 0.0, L * 0.12, 3.2, -1.6, CLOTH_WHITE),
        cyl(1, L * 0.8, 1.2, WOOD, bands=8),
        cyl(L * 0.74, L * 0.79, 1.8, CRIMSON),
        tri(L * 0.79, L, 3.0, 0, BONE),
    ])


def generate(out_dir):
    items = {
        'RustySword': rusty_sword(),
        'BasicSword': iron_sword(),
        'ShortSword': short_sword(),
        'DualWieldingSword': katana(),
        'Zweihander': zweihander(),
        'Knuppel': club(),
        'BigHammer': big_hammer(),
        'BigMace': morningstar(),
        'SpringHammer': spring_hammer(),
        'BasicTrident': trident(WOOD, STEEL),
        'RecallTrident': trident(TEAL, GOLD, gem=TEAL),
        'IceTrident': trident(ICE, ICE, frost=True),
        'HeavyTrident': trident(BLACK_IRON, DARK_STEEL, display_length=150, heavy=True, gem=CRIMSON),
        'Spear': spear(),
    }
    for name, img in items.items():
        img.save(f'{out_dir}/{name}.png')
    return items
