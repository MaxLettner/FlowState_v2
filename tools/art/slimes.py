"""Slime characters: player + enemy types, all animation strips (64x64 frames)."""
import math
from pixel import OUTLINE, dither_index, ellipse, hexc, mix, new, outline, put, ramp, strip

SIZE = 64
CX = 32
BASE_Y = 62   # bottom of the slime (anchor used by SpriteComponent)
SCALE = 1.0   # draw_slime renders at SIZE * SCALE native pixels; all geometry stays in 64 px units (1.5 for the Hollow Ember)


class ScaledPx:
    """Pixel access in 64 px units on a SCALE times bigger image: a write fills the block it covers,
    so the drawing helpers (put, ellipse, ...) work unchanged."""

    def __init__(self, px, size, k):
        self.px, self.size, self.k = px, size, k

    def __getitem__(self, p):
        x = min(self.size - 1, int((p[0] + 0.5) * self.k))
        y = min(self.size - 1, int((p[1] + 0.5) * self.k))
        return self.px[x, y]

    def __setitem__(self, p, c):
        for y in range(int(p[1] * self.k), max(int(p[1] * self.k) + 1, int((p[1] + 1) * self.k))):
            for x in range(int(p[0] * self.k), max(int(p[0] * self.k) + 1, int((p[0] + 1) * self.k))):
                if 0 <= x < self.size and 0 <= y < self.size:
                    self.px[x, y] = c


class ScaledImage:
    """An image seen in 64 px units: load() gives a ScaledPx (for the glow / spark effects that only use load())."""

    def __init__(self, img, k):
        self.img, self.k = img, k

    def load(self):
        return ScaledPx(self.img.load(), self.img.width, self.k)

    def getpixel(self, p):
        return self.load()[p]


def native_size():
    return int(SIZE * SCALE + 0.5)


def scaled_view(img):
    return img if SCALE == 1.0 else ScaledImage(img, SCALE)

LAVA_RIM = hexc('#ff8a3d')


class Look:
    def __init__(self, body, outline_c, eye_style, mouth, extras, bubbles=True, rim=0.55):
        self.body = body          # ramp: highlight .. shadow (5 colours)
        self.outline = outline_c
        self.eye_style = eye_style
        self.mouth = mouth
        self.extras = extras      # list of callables(px, frame_info)
        self.bubbles = bubbles
        self.rim = rim


ROUND_FROM = 0.45   # with round > 0 the body below this height curves in like the top half
DROP_M = 0.8        # teardrop tail sharpness (0 = ellipse, 1 = classic drop, higher = thinner tail)
_DROP_MAX = {}


def drop_profile(v, m=None):
    """Teardrop half width (0..1) at v: round end at v=0, tail tip at v=1  (x = sin t * sin(t/2)^m)."""
    m = DROP_M if m is None else m
    if m not in _DROP_MAX:
        _DROP_MAX[m] = max(math.sin(t) * math.sin(t / 2) ** m for t in (i * math.pi / 2000 for i in range(2001)))
    t = math.acos(max(-1.0, min(1.0, 2 * v - 1)))
    return math.sin(t) * math.sin(t / 2) ** m / _DROP_MAX[m]


def half_width(v, w, round=0.0, tip_top=0.0, tip_bot=0.0, tip_from=0.0, ripple=None, **_):
    """Half width of the slime at relative height v (0 bottom .. 1 top).
    round 0: sat on the ground, flat bottom spreading into a foot; 1: airborne, an egg with a round bottom.
    tip_top / tip_bot (0..1): airflow pulls that end out into a water-drop tail (falling: tail up, rising: down).
    tip_from (0..1): the top tail only pulls above this height (a body sat on the ground keeps its foot).
    ripple = (amplitude, waves over the height, phase): a surface wave running over the skin (impacts, pops)."""
    if v < 0 or v > 1:
        return -1
    hw = (w / 2) * (1 - v ** 2.4) ** 0.5
    if v < 0.18:
        hw *= 1 + 0.10 * (1 - v / 0.18) * (1 - round)
    if round > 0 and v < ROUND_FROM:
        t = (ROUND_FROM - v) / ROUND_FROM
        ball = (w / 2) * (1 - ROUND_FROM ** 2.4) ** 0.5 * max(0.0, 1 - t ** 2.2) ** (1 / 2.2)
        hw += (ball - hw) * round
    if tip_top > 0:
        k = tip_top
        if tip_from > 0:
            k *= min(1.0, max(0.0, (v - tip_from) / (1 - tip_from))) ** 0.7
        hw += ((w / 2) * drop_profile(v) - hw) * k
    if tip_bot > 0:
        hw += ((w / 2) * drop_profile(1 - v) - hw) * tip_bot
    if ripple:
        amp, waves, ph = ripple
        hw *= 1 + amp * math.sin(2 * math.pi * waves * v - ph) * math.sin(math.pi * v)
    return hw


def side_scale(v, bulge=0.0, **_):
    """(back, front) half-width factors: bulge > 0 fills the front low down (the mass surging forward over
    the foot) and thins the back by as much, < 0 the other way. 1, 1 without a bulge."""
    if not bulge:
        return 1.0, 1.0
    vv = min(1.0, max(0.0, v))
    g = math.sin(math.pi * vv) * (1 - vv) * 1.6           # peaks ~0.3 up the body
    return 1 - bulge * g, 1 + bulge * g


def warp_dy(x, w, cup=0.0, tilt=0.0, **_):
    """px column x is lifted (+ = up): cup lifts both edges (a canopy in the updraft), tilt the front (+x) edge."""
    if not cup and not tilt:
        return 0.0
    n = (x - CX) / max(1.0, w / 2)
    return cup * n * n + tilt * n


def row_cx(v, lean, sweep_top=0.0, sweep_bot=0.0, **_):
    """Body centre x at height v: lean tilts the whole body forward (+x), the sweeps bend the top / bottom
    end sideways (px at the tip), so a drop tail trails behind when it moves sideways."""
    return CX + lean * v + sweep_top * max(0.0, v) ** 2.5 + sweep_bot * max(0.0, 1 - v) ** 2.5


def body_mask(w, h, lift, lean, round=0.0, form=None):
    """lean shifts the top of the body forward (+x) for motion; form = airflow shape (see draw_slime)."""
    form = dict(form or {})
    form.setdefault('round', round)
    mask = {}
    bottom = BASE_Y - lift
    warped = form.get('cup') or form.get('tilt')
    k = SCALE
    for y in range(native_size()):
        yc = (y + 0.5) / k                            # pixel centre in 64 px units
        if warped:                                    # the height depends on the column: test every pixel
            for x in range(native_size()):
                xc = (x + 0.5) / k
                v = (bottom - (yc + warp_dy(xc, w, **form))) / h
                hw = half_width(v, w, **form)
                if hw <= 0:
                    continue
                back, front = side_scale(v, **form)
                d = xc - row_cx(v, lean, **form)
                lim = hw * (front if d >= 0 else back)
                if abs(d) <= lim:
                    mask[(x, y)] = (d / max(lim, 1e-3), v)
            continue
        v = (bottom - yc) / h
        hw = half_width(v, w, **form)
        if hw <= 0:
            continue
        cx = row_cx(v, lean, **form)
        back, front = side_scale(v, **form)
        for x in range(native_size()):
            d = (x + 0.5) / k - cx
            lim = hw * (front if d >= 0 else back)
            if abs(d) <= lim:
                mask[(x, y)] = (d / max(lim, 1e-3), v)
    return mask


def draw_slime(look, w, h, lift=0, lean=0, eye_dy=0, blink=False, phase=0.0, mood=None, round=0.0, form=None):
    """round (0..1) rounds off the flat bottom for airborne frames (see half_width).
    form: airflow shape {tip_top, tip_bot (0..1 drop tail), sweep_top, sweep_bot (px the tail bends sideways)}."""
    form = dict(form or {}, round=round)
    img = new(native_size(), native_size())
    px = img.load()
    mask = body_mask(w, h, lift, lean, form=form)
    bottom = BASE_Y - lift
    light = (-0.55, -0.7, 0.45)
    ll = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ll for c in light)

    for (x, y), (nx, v) in mask.items():
        ny = -(v - 0.45) / 0.55
        nz2 = max(0.0, 1 - nx * nx * 0.85 - ny * ny * 0.6)
        nz = math.sqrt(nz2)
        lam = nx * light[0] + ny * light[1] + nz * light[2]
        value = 1 - max(0.0, min(1.0, (lam + 0.25) / 1.15))
        idx = dither_index(value, x, y, len(look.body))
        c = look.body[idx]
        px[x, y] = c

    # warm lava bounce light: a thin rim on the lower edge only
    for (x, y), (nx, v) in mask.items():
        if v < 0.3 and look.rim > 0:
            edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1)))
            if edge:
                px[x, y] = mix(px[x, y], LAVA_RIM, look.rim)

    px = scaled_view(img).load() if SCALE != 1.0 else px   # from here on in 64 px units
    # inner bubbles
    if look.bubbles:
        for i, (bx, bv, r) in enumerate([(-0.35, 0.25, 1.6), (0.25, 0.18, 1.1), (0.05, 0.4, 0.9)]):
            hw = half_width(bv, w, **form)
            cx = row_cx(bv, lean, **form) + bx * hw
            cy = bottom - bv * h - math.sin(phase * 2 * math.pi + i) * 1.2
            ellipse(px, SIZE, SIZE, cx, cy, r, r, look.body[1])
            put(px, SIZE, SIZE, int(cx - 0.5), int(cy - 0.8), look.body[0])

    # specular highlight
    hv = 0.78 - 0.3 * form.get('tip_top', 0.0)          # slides down off a thin drop tail, into the bulb
    hhw = half_width(hv, w, **form) * side_scale(hv, **form)[0]
    hx = row_cx(hv, lean, **form) - hhw * 0.42
    hy = bottom - hv * h - warp_dy(hx, w, **form)
    shine = mix(look.body[0], (255, 255, 255, 255), 0.75)
    ellipse(px, SIZE, SIZE, hx, hy, max(1.5, min(w * 0.07, hhw * 0.3)), max(1.2, h * 0.06), shine)
    put(px, SIZE, SIZE, int(hx - 1), int(hy - 1), (255, 255, 255, 255))
    put(px, SIZE, SIZE, int(hx + w * 0.11), int(hy - 1), shine)

    info = dict(w=w, h=h, bottom=bottom, lean=lean, phase=phase, px=px, mood=mood, round=round, form=form)

    # face
    ev = 0.55 + eye_dy / max(h, 1)
    ey = bottom - ev * h
    ecx = row_cx(ev, lean, **form) + min(w * 0.12, half_width(ev, w, **form) * side_scale(ev, **form)[1] * 0.6)   # face looks to the right (+x)
    ey -= warp_dy(ecx, w, **form)
    look.eye_style(px, ecx, ey, w, h, blink, info)
    look.mouth(px, ecx, ey, w, h, info)

    for extra in look.extras:
        extra(img, px, info)
    for extra in (mood or {}).get('pre', []):   # per-frame extras that get the outline (tar droplets, bubbles)
        extra(img, px, info)

    return outline(img, look.outline)


# ---------- faces ----------
def eyes_hero(px, ecx, ey, w, h, blink, info):
    gap = max(5, w * 0.17)
    for sx in (-gap / 2, gap / 2):
        x = ecx + sx
        if blink:
            for dx in range(-2, 2):
                put(px, SIZE, SIZE, int(x + dx), int(ey + 1), OUTLINE)
            continue
        ellipse(px, SIZE, SIZE, x, ey, 2.6, 3.4, (255, 255, 255, 255))
        ellipse(px, SIZE, SIZE, x + 0.8, ey + 0.6, 1.6, 2.4, hexc('#14213d'))
        put(px, SIZE, SIZE, int(x), int(ey - 1), (255, 255, 255, 255))
        put(px, SIZE, SIZE, int(x + 1), int(ey + 2), hexc('#5aa9ff'))


def mouth_smile(px, ecx, ey, w, h, info):
    my = ey + max(5, h * 0.14)
    for dx, dy in [(-2, 0), (-1, 1), (0, 1), (1, 1), (2, 0)]:
        put(px, SIZE, SIZE, int(ecx + dx), int(my + dy), OUTLINE)


def eyes_angry(px, ecx, ey, w, h, blink, info):
    gap = max(5, w * 0.18)
    for i, sx in enumerate((-gap / 2, gap / 2)):
        x = ecx + sx
        ellipse(px, SIZE, SIZE, x, ey + 0.5, 2.3, 2.6, hexc('#fff3b0'))
        ellipse(px, SIZE, SIZE, x + 0.8, ey + 1, 1.2, 1.6, hexc('#b3001b'))
        # slanted brow
        d = 1 if i == 0 else -1
        for k in range(-3, 4):
            put(px, SIZE, SIZE, int(x + k), int(ey - 3 + d * k * 0.45), OUTLINE)
            put(px, SIZE, SIZE, int(x + k), int(ey - 4 + d * k * 0.45), OUTLINE)


def mouth_teeth(px, ecx, ey, w, h, info):
    my = int(ey + max(6, h * 0.16))
    for dx in range(-4, 5):
        put(px, SIZE, SIZE, int(ecx + dx), my, OUTLINE)
        put(px, SIZE, SIZE, int(ecx + dx), my + 2, OUTLINE)
        put(px, SIZE, SIZE, int(ecx + dx), my + 1, hexc('#3a0a14'))
    for dx in (-3, -1, 1, 3):
        put(px, SIZE, SIZE, int(ecx + dx), my + 1, hexc('#f4ecd8'))


def eyes_hunter(px, ecx, ey, w, h, blink, info):
    gap = max(5, w * 0.17)
    for sx in (-gap / 2, gap / 2):
        x = ecx + sx
        ellipse(px, SIZE, SIZE, x, ey, 2.2, 2.0, hexc('#fffbe6'))
        put(px, SIZE, SIZE, int(x + 1), int(ey), OUTLINE)
        put(px, SIZE, SIZE, int(x + 1), int(ey - 1), OUTLINE)
        for k in range(-2, 3):  # narrow, focused lids
            put(px, SIZE, SIZE, int(x + k), int(ey - 2), OUTLINE)
    # war paint stripes under the eyes
    for sx in (-gap / 2, gap / 2):
        for k in range(0, 3):
            put(px, SIZE, SIZE, int(ecx + sx - 1 + k), int(ey + 3), hexc('#ffe066'))


def mouth_flat(px, ecx, ey, w, h, info):
    my = ey + max(6, h * 0.15)
    for dx in range(-2, 3):
        put(px, SIZE, SIZE, int(ecx + dx), int(my), OUTLINE)


# ---------- extras ----------
def headband(img, px, info):
    """Player: red bandana around the head with two fluttering tails."""
    w, h, bottom, lean, phase = info['w'], info['h'], info['bottom'], info['lean'], info['phase']
    band_v = 0.74
    by = bottom - band_v * h
    red = ramp('#ff6b6b', '#e63946', '#b51f2e', '#7a1020')
    for y in range(int(by) - 1, int(by) + 2):
        v = (bottom - (y + 0.5)) / h
        hw = half_width(v, w)
        if hw <= 0:
            continue
        cx = CX + lean * v
        for x in range(int(cx - hw), int(cx + hw) + 1):
            if (x + 0.5 - cx) ** 2 <= hw * hw and img.getpixel((x, y))[3] > 0:
                shade = 0 if y == int(by) - 1 else (1 if x > cx - hw * 0.3 else 2)
                if x > cx + hw * 0.6:
                    shade = 2
                px[x, y] = red[shade]
    # knot + tails behind (left)
    hw = half_width(band_v, w)
    kx = CX + lean * band_v - hw
    ellipse(px, SIZE, SIZE, kx, by, 1.6, 1.6, red[1])
    for t_i, (amp, length, off) in enumerate([(2.2, 11, 0.0), (1.6, 8, 1.3)]):
        for k in range(1, length):
            tx = kx - k
            ty = by + k * 0.35 + math.sin(phase * 2 * math.pi + k * 0.55 + off) * amp * (k / length)
            c = red[2] if k % 3 == 0 else red[1]
            put(px, SIZE, SIZE, int(tx), int(ty), c)
            put(px, SIZE, SIZE, int(tx), int(ty) + 1, red[3])


def horns(img, px, info):
    """Brute: two small bone horns on top."""
    w, h, bottom, lean = info['w'], info['h'], info['bottom'], info['lean']
    top = bottom - h
    bone = ramp('#fff8e1', '#e8dcb8', '#bfae84', '#8a7a55')
    for side in (-1, 1):
        bx = CX + lean + side * w * 0.2
        for k in range(6):
            x = bx + side * k * 0.5
            y = top + 3 - k
            width = max(1, 3 - k // 2)
            for dx in range(width):
                put(px, SIZE, SIZE, int(x + dx * side * -1), int(y), bone[0 if dx == 0 and k > 1 else 1 if k < 4 else 2])


def feather(img, px, info):
    """Hunter: headband with a feather sticking up at the back."""
    w, h, bottom, lean, phase = info['w'], info['h'], info['bottom'], info['lean'], info['phase']
    band_v = 0.72
    by = int(bottom - band_v * h)
    band = ramp('#8d5524', '#6b3e1a', '#4a2a10')
    for y in (by, by + 1):
        v = (bottom - (y + 0.5)) / h
        hw = half_width(v, w)
        cx = CX + lean * v
        for x in range(int(cx - hw), int(cx + hw) + 1):
            if img.getpixel((x, y))[3] > 0:
                px[x, y] = band[0 if y == by else 1]
    # beads
    hw = half_width(band_v, w)
    cx = CX + lean * band_v
    for k, col in enumerate(['#ffe066', '#3ec1d3', '#ffe066']):
        put(px, SIZE, SIZE, int(cx - 3 + k * 3), by + 1, hexc(col))
    # feather (back = left)
    fx = cx - hw * 0.55
    sway = math.sin(phase * 2 * math.pi) * 1.0
    shaft = hexc('#4a2a10')
    vane = ramp('#fff1e6', '#3ec1d3', '#1d7f8c', '#0f4c55')
    length = 13
    for k in range(length):
        x = fx - k * 0.6 + sway * (k / length)
        y = by - k
        half = 0 if k < 2 else (2 if k < length - 3 else 1)
        for d in range(-half, half + 1):
            col = vane[1] if d <= 0 else vane[2]
            if k > length - 4:
                col = vane[0]          # white tip
            elif k % 4 == 0 and d != 0:
                col = vane[3]          # notches
            put(px, SIZE, SIZE, int(x) + d, int(y) + (1 if abs(d) == 2 else 0), col)
        put(px, SIZE, SIZE, int(x), int(y), shaft if k < length - 3 else vane[0])


# ---------- player: Tar & Ember (2026-11-19, replaces the cyan bandana slime) ----------
EMBER = ramp('#fff6c0', '#ffd25a', '#ff8a2a', '#ff5a1a', '#c2300f')   # white-hot .. deep red


def rel(info, nx, v):
    """Body-relative point (nx -1 back .. 1 front, v 0 bottom .. 1 top) -> pixel coords."""
    form = info.get('form') or {'round': info.get('round', 0.0)}
    hw = half_width(v, info['w'], **form)
    back, front = side_scale(v, **form)
    x = row_cx(v, info['lean'], **form) + nx * hw * (front if nx > 0 else back)
    return x, info['bottom'] - v * info['h'] - warp_dy(x, info['w'], **form)


def inside(px, x, y):
    return 0 <= x < SIZE and 0 <= y < SIZE and px[x, y][3] > 0


def halo(px, cx, cy, r, col, strength):
    """Tints body pixels around (cx, cy) toward col in 4 steps (a glow that stays inside the body)."""
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d < 1 and inside(px, x, y):
                t = round(strength * (1 - d) ** 1.3 * 4) / 4
                if t > 0:
                    px[x, y] = mix(px[x, y], col, t)


def mood_of(info, key, default=None):
    """Per-frame expression settings passed to draw_slime(mood={...}) by the player animations."""
    return (info.get('mood') or {}).get(key, default)


def heat(info):
    """0 at rest .. 1 when squashed flat (landing impact): the cracks and eyes flare up.
    mood 'heat' overrides it."""
    forced = mood_of(info, 'heat')
    if forced is not None:
        return forced
    squash = max(0.0, min(1.0, (info['w'] / info['h'] - 1.15) / 0.8))
    pulse = (math.sin(info['phase'] * 2 * math.pi) + 1) / 2
    return max(squash, 0.35 * pulse)


def eyes_ember(px, ecx, ey, w, h, blink, info):
    """Molten eyes, no pupils; a heavy tar lid sags over the top so they look tired-dangerous.
    mood 'eyes': normal | fierce (lid slants down to the front) | squint | shut (>  <) | wide | dim | off;
    mood 'look': (dx, dy) shift of the hot core (glancing back / up)."""
    gap = max(5, w * 0.17)
    hot = heat(info)
    style = mood_of(info, 'eyes', 'normal')
    lx, ly = mood_of(info, 'look', (0, 0))
    if blink:
        style = 'blink'
    glow = {'blink': 0.2, 'off': 0.0, 'dim': 0.2, 'squint': 0.35, 'shut': 0.4}.get(style, 0.5 + 0.25 * hot)
    if style == 'wide':
        glow = 0.85
    for sx in (-gap / 2, gap / 2):
        if glow > 0:
            halo(px, ecx + sx, ey, 6.5 if style == 'wide' else 5.5, EMBER[3], glow)
    for i, sx in enumerate((-gap / 2, gap / 2)):
        x = ecx + sx
        if style == 'blink':
            for dx in range(-2, 2):
                put(px, SIZE, SIZE, int(x + dx), int(ey + 1), EMBER[2])
            continue
        if style == 'off':
            for dx in range(-1, 1):
                put(px, SIZE, SIZE, int(x + dx), int(ey + 1), TAR[0])
            continue
        if style == 'dim':
            ellipse(px, SIZE, SIZE, x, ey + 1, 1.4, 1.6, EMBER[4])
            put(px, SIZE, SIZE, int(x), int(ey + 1), EMBER[3])
            continue
        if style == 'squint':
            for dx in range(-2, 3):
                put(px, SIZE, SIZE, int(x + dx), int(ey), EMBER[2])
                put(px, SIZE, SIZE, int(x + dx), int(ey + 1), EMBER[1] if -1 <= dx <= 1 else EMBER[3])
            for dx in range(-3, 4):
                put(px, SIZE, SIZE, int(x + dx), int(ey - 1), TAR[3])
            continue
        if style == 'shut':                           # > <  squeezed shut
            d = -1 if i == 0 else 1
            for k in range(-2, 3):
                put(px, SIZE, SIZE, int(x + d * (abs(k) - 1)), int(ey + k), EMBER[1])
            continue
        rx, ry = (2.6, 3.4) if style == 'wide' else (2.2, 3.0)
        ellipse(px, SIZE, SIZE, x, ey, rx, ry, EMBER[2])
        ellipse(px, SIZE, SIZE, x + 0.4 + lx, ey + 0.4 + ly, 1.4, 2.0, EMBER[1])
        ellipse(px, SIZE, SIZE, x + 0.7 + lx, ey + 0.2 + ly, 0.7, 1.0, EMBER[0])
        if style == 'wide':
            continue                                  # lids pulled back
        for dx in range(-3, 3):                       # tar lid
            slant = 0
            if style == 'fierce':                     # angry lids: lower toward the gap between the eyes
                slant = (1 if dx >= 0 else 0) if i == 0 else (1 if dx <= -2 else 0)
                if slant:
                    put(px, SIZE, SIZE, int(x + dx), int(ey - 3), TAR[3])
            put(px, SIZE, SIZE, int(x + dx), int(ey - 3 + slant), TAR[3])
            if -2 <= dx <= 1:
                put(px, SIZE, SIZE, int(x + dx), int(ey - 2 + slant), TAR[3] if dx < 1 and not slant else EMBER[4] if not slant else TAR[3])


def no_mouth(px, ecx, ey, w, h, info):
    pass


CRACKS = [[(-0.62, 0.14), (-0.32, 0.3), (-0.48, 0.5), (-0.22, 0.63)],
          [(-0.32, 0.3), (-0.1, 0.36)],
          [(0.18, 0.06), (0.34, 0.22), (0.6, 0.28)],
          [(-0.08, 0.84), (0.12, 0.95)]]


def _crack_pixels(info, path):
    pts = []
    for a, b in zip(path, path[1:]):
        x0, y0 = rel(info, *a)
        x1, y1 = rel(info, *b)
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for k in range(n + 1):
            t = k / n
            p = (int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t))
            if not pts or pts[-1] != p:
                pts.append(p)
    return pts


def ember_cracks(img, px, info):
    """Molten cracks in the tar that follow the squash, pulse while idle and flare white-hot on landing."""
    hot = heat(info)
    cool = mood_of(info, 'cool', 0.0)              # 0 molten .. 1 cold, dead stone (blocking, dying)
    paths = [_crack_pixels(info, p) for p in CRACKS]
    for pts in paths:
        for x, y in pts:
            if inside(px, x, y) and cool < 0.5:
                halo(px, x + 0.5, y + 0.5, 2.4 + 1.2 * hot, EMBER[4] if hot < 0.6 else EMBER[3], 0.3 + 0.35 * hot)
    for pts in paths:
        for k, (x, y) in enumerate(pts):
            if not inside(px, x, y):
                continue
            c = EMBER[3]
            if hot > 0.6:                              # landing impact: white-hot cracks
                c = EMBER[0] if k % 3 == 1 else EMBER[1]
            elif k % 4 == 1:
                c = EMBER[1]
            if cool > 0:
                c = mix(c, COLD_CRACK, cool)
            px[x, y] = c


COLD_CRACK = hexc('#3a1a14')
TAR = ramp('#5e5552', '#342d2b', '#211b1a', '#141010', '#090707')
PLAYER = Look(TAR, hexc('#000000'), eyes_ember, no_mouth, [ember_cracks], bubbles=False, rim=0.75)
# the previous player (cyan slime with the red bandana), kept for a possible skin
CYAN_PLAYER = Look(ramp('#c8fbff', '#6fe3ff', '#2fb4f5', '#1f7ad1', '#16479a'), hexc('#0b1a3a'),
                   eyes_hero, mouth_smile, [headband], rim=0.45)
BRUTE = Look(ramp('#d8ff9a', '#8fd94a', '#4fa52a', '#2f6e1f', '#1b4016'), hexc('#0e1f0b'),
             eyes_angry, mouth_teeth, [horns], rim=0.6)
HUNTER = Look(ramp('#ffd6a8', '#f3964a', '#c9562a', '#8e3220', '#561a17'), hexc('#2a0b0b'),
              eyes_hunter, mouth_flat, [feather], rim=0.35)


# ---------- animations ----------
def anim_idle(look, n=6):
    frames = []
    for i in range(n):
        s = math.sin(i / n * 2 * math.pi)
        frames.append(draw_slime(look, 40 - s * 1.5, 38 + s * 1.5, phase=i / n, blink=(i == 3)))
    return frames


def anim_walk(look, n=8):
    frames = []
    for i in range(n):
        p = i / n
        hop = max(0.0, math.sin(p * 2 * math.pi))           # in the air half the cycle
        squash = max(0.0, -math.sin(p * 2 * math.pi))
        w = 40 + squash * 6 - hop * 3
        h = 38 - squash * 5 + hop * 4
        frames.append(draw_slime(look, w, h, lift=hop * 5, lean=2 + hop * 2, phase=p))
    return frames


# rising fast (stretched teardrop), rising, apex (rounded out into a ball), falling (stretched again);
# off the ground the bottom is round, it only flattens when it touches down
AIR_PARAMS = [(33, 47, 1.5, 0.9), (36, 43, 1, 1.0), (42, 39, 0, 1.0), (35, 45, -1.5, 1.0)]


def anim_air(look):
    return [draw_slime(look, w, h, lean=l, eye_dy=-1 if i < 2 else 1, phase=i / 4, round=r)
            for i, (w, h, l, r) in enumerate(AIR_PARAMS)]


def anim_land(look):
    # touch-down: the round bottom flattens and the body splats wide, then wobbles back up to rest.
    # SpriteComponent starts later in the strip for small drops (only a light squash).
    params = [(54, 26), (50, 31), (44, 37), (38, 41), (41, 39)]
    return [draw_slime(look, w, h, phase=i / len(params)) for i, (w, h) in enumerate(params)]


def anim_attack(look):
    # wind up (lean back), throw (lean forward), recover
    params = [(42, 36, -3), (44, 34, -5), (36, 44, 6), (40, 38, 2)]
    return [draw_slime(look, w, h, lean=l, phase=i / 4) for i, (w, h, l) in enumerate(params)]


def frozen_overlay(look):
    """Ice shell drawn over a slime (enemy freeze effect)."""
    img = new(SIZE, SIZE)
    px = img.load()
    mask = body_mask(46, 44, 0, 0)
    ice = ramp('#ffffff', '#d6f6ff', '#a8e4f7', '#6cc3e8')
    for (x, y), (nx, v) in mask.items():
        idx = dither_index(0.3 + 0.5 * (1 - v) * 0.6 + (0.2 if nx > 0.4 else 0), x, y, len(ice))
        c = ice[idx]
        px[x, y] = (c[0], c[1], c[2], 150)
    # crystal shards on top
    for sx, height in [(-12, 9), (-3, 13), (7, 8), (14, 6)]:
        for k in range(height):
            wdt = max(0, (height - k) // 3)
            for d in range(-wdt, wdt + 1):
                put(px, SIZE, SIZE, CX + sx + d, BASE_Y - 40 - k + abs(sx) // 3, (230, 250, 255, 210))
    # shine lines
    for k in range(6):
        put(px, SIZE, SIZE, CX - 10 + k, BASE_Y - 30 + k, (255, 255, 255, 230))
    return outline(img, (60, 130, 180, 200))


def generate(out_dir):
    # the slime enemies (BRUTE, HUNTER) were replaced by the demons in demons.py; their looks stay for reference
    # the player's idle / walk / land are physics-driven now (player_slime.py); air stays as the fallback strip
    strip(anim_air(PLAYER)).save(f'{out_dir}/Player_air.png')
    frozen_overlay(BRUTE).save(f'{out_dir}/FrozenOverlay.png')
