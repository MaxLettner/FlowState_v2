"""The 4-slot hotbar at the bottom of the HUD (`Menu/Hotbar.java`), drawn x2, in the forged iron of the HUD bars.

- `Hotbar.png`: an iron beam with pointed ends holding four square sockets; each socket is an iron frame with
  riveted corner brackets around a dark, empty PIT x PIT recess (drop shadow included).
- `HotbarRim.png`: 3 frames of SOCK x SOCK, laid over a socket's frame: 0 a gold bezel (the active attack),
  1 a glowing arcane bezel (a toggle that is on), 2 a white-hot bezel (the skill just became ready; Java fades it).
- `HotbarIcon_<TYPE>.png`: PIT x PIT, every skill icon on a dark, branch-tinted recess, without the parchment
  frame of the skill tree icons.
- `HudBar_COMBO.png` / `HudFill_COMBO.png`: the Fisticuffs combo meter above the sockets, a thin iron trough with
  pointed ends filled with fire; same format as the HUD bars (hudbars.py), drawn by HudBar.java with its trough at
  (COMBO_TX, COMBO_TY). It spans the sockets and sits COMBO_GAP px above the hotbar.
Socket i's frame starts at (sock_x(i), MARGIN) and its recess at (sock_x(i) + RIM, MARGIN + RIM).
Keep these numbers in sync with Hotbar.java. `python tools/art/hotbar.py out.png [screenshot]` renders a preview.
"""
import math
import random
import sys
from PIL import Image

from pixel import *
from materials import GOLD
from menu import IRON
from hudbars import OUT, PIT as PIT_DARK, GLASS_SHADOW, MANA, TROUGH_LEN, add_shadow, rivet, fill_frames
import icons

SCALE = 2
SLOTS = 4
PIT = 24                     # the icon recess
RIM = 4                      # iron around the recess: outline, bevel, bezel, lip
SOCK = PIT + 2 * RIM         # 32
GAP = 6                      # beam between two sockets
END = 12                     # beam beyond the outer sockets, including its point
MARGIN = 1                   # the corner brackets stand out of the frame by this much
BRACKET = 7                  # length of a bracket's arms, measured from the outside
W = 2 * END + SLOTS * SOCK + (SLOTS - 1) * GAP
H = SOCK + 2 * MARGIN
BEAM_TOP, BEAM_BOT = 11, 22  # rows of the beam (full height), centred on the sockets
CY = (BEAM_TOP + BEAM_BOT) / 2

WHITE_HOT = ramp('#ffffff', '#fff6d8', '#ffe39a', '#f5b94a', '#c47a22')

# combo meter
COMBO_W = SLOTS * SOCK + (SLOTS - 1) * GAP     # as wide as the row of sockets
COMBO_TH = 3                                   # trough height
COMBO_HALF = COMBO_TH // 2 + 3                 # body half-height, like the HUD bars
COMBO_CY = COMBO_HALF + 1                      # the collars stand out by one row
COMBO_TX = (COMBO_W - TROUGH_LEN) // 2         # 9
COMBO_TY = COMBO_CY - COMBO_TH // 2            # 4
COMBO_GAP = 1                                  # art px between the meter (with its shadow) and the hotbar
FIRE = ramp('#ffe9a8', '#ffa53a', '#f26a1b', '#c23a17', '#6e1a10')


def sock_x(i):
    return END + i * (SOCK + GAP)


def socket_dist(x, y):
    """Ring of a socket pixel (x, y relative to the frame's top-left): 0 outline .. RIM-1 lip, >= RIM the recess,
    None outside (the cut corners); and the side it is on (0 top, 1 left, 2 bottom, 3 right)."""
    dl, dr, dt, db = x, SOCK - 1 - x, y, SOCK - 1 - y
    if min(dl, dr, dt, db) < 0:
        return None, None
    d = min(dl, dr, dt, db, min(dl, dr) + min(dt, db) - 2)
    if d < 0:
        return None, None
    side = min(range(4), key=lambda k: (dt, dl, db, dr)[k])
    return d, side


def lit(side):
    return side in (0, 1)


def bracket_mask():
    """The four L-shaped corner brackets, relative to the frame's top-left. They cover the frame's outer three
    rings and stand MARGIN px out of it."""
    mask = set()
    for x in range(-MARGIN, SOCK + MARGIN):
        for y in range(-MARGIN, SOCK + MARGIN):
            ex = min(x + MARGIN, SOCK - 1 + MARGIN - x)     # distance from the bracket's outer edge
            ey = min(y + MARGIN, SOCK - 1 + MARGIN - y)
            if ex + ey == 0:
                continue                                    # round the very corner
            if max(ex, ey) < BRACKET and min(ex, ey) < 3 + MARGIN:
                mask.add((x, y))
    return mask


BRACKETS = bracket_mask()


# ---------------------------------------------------------------- the iron beam
COLLARS = set()
for _i in range(SLOTS - 1):
    _c = sock_x(_i) + SOCK + GAP // 2
    COLLARS |= {_c - 1, _c}
COLLARS |= {END - 4, END - 3, W - END + 2, W - END + 3}


def beam_mask():
    mask = set()
    taper = (BEAM_BOT - BEAM_TOP + 1) // 2 - 1      # 5
    for x in range(W):
        k = max(0, taper - x, x - (W - 1 - taper))     # columns into the point
        grow = 1 if x in COLLARS else 0
        for y in range(BEAM_TOP + k - grow, BEAM_BOT - k + grow + 1):
            mask.add((x, y))
    return mask


def shade_body(px, mask, rng, collars=frozenset(), pits=0.08, cy=CY):
    """Forged iron like the HUD bars: outline, lit top, shaded bottom, a little pitting."""
    for (x, y) in mask:
        if any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = OUT
            continue
        top = min(yy for (xx, yy) in mask if xx == x)
        bot = max(yy for (xx, yy) in mask if xx == x)
        dt, db = y - top, bot - y
        collar = x in collars
        if dt == 1:
            c = IRON[0] if collar else IRON[1]
        elif db == 1:
            c = IRON[3]
        elif dt == 2:
            c = IRON[1] if collar else IRON[2]
        elif db == 2:
            c = IRON[2] if collar else IRON[3]
        else:
            c = IRON[1] if collar else IRON[2] if y < cy else IRON[3]
        if collar and x == max(collars & {x - 1, x, x + 1}):
            c = IRON[min(4, IRON.index(c) + 1)]     # shaded right side of a collar
        elif c not in (IRON[0], IRON[1]) and rng.random() < pits:
            c = IRON[min(4, IRON.index(c) + 1)]
        px[x, y] = c


def draw_beam(px, rng):
    shade_body(px, beam_mask(), rng, COLLARS)
    for x in (END - 8, W - END + 6):                 # a rivet on each end
        rivet(px, x, BEAM_TOP + 2)
        rivet(px, x, BEAM_BOT - 3)


# ---------------------------------------------------------------- sockets
def recess(x, y):
    """The empty recess: shadowed along the top and left, dark glass below."""
    if x == 0 or y == 0:
        return GLASS_SHADOW
    t = (x + y) / (2 * PIT)
    return mix(PIT_DARK, IRON[4], 0.25 + 0.35 * t)


def draw_socket(px, ox, oy, rng):
    for y in range(SOCK):
        for x in range(SOCK):
            d, side = socket_dist(x, y)
            if d is None:
                continue
            if d == 0:
                c = OUT
            elif d == 1:
                c = IRON[1] if lit(side) else IRON[3]
            elif d == 2:
                c = IRON[2] if lit(side) else IRON[3]
                if rng.random() < 0.12:
                    c = IRON[min(4, IRON.index(c) + 1)]
            elif d == 3:
                c = OUT if lit(side) else IRON[1]    # deep shadow top-left, lit lip bottom-right
            else:
                c = recess(x - RIM, y - RIM)
            px[ox + x, oy + y] = c
    # riveted corner brackets on top: lit where the outline is above or left of a pixel, shaded below or right
    def edge(x, y):
        return (x, y) not in BRACKETS or any((x + dx, y + dy) not in BRACKETS
                                             for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    for (x, y) in BRACKETS:
        if edge(x, y):
            c = OUT
        elif edge(x, y - 1) or edge(x - 1, y):
            c = IRON[1]
        elif edge(x, y + 1) or edge(x + 1, y):
            c = IRON[3]
        else:
            c = IRON[2]
        px[ox + x, oy + y] = c
    for rx, ry in ((0, 0), (SOCK - 2, 0), (0, SOCK - 2), (SOCK - 2, SOCK - 2)):
        px[ox + rx, oy + ry] = IRON[0]                # rivet heads where the arms meet
        px[ox + rx + 1, oy + ry + 1] = IRON[3]


def hotbar():
    img = new(W, H)
    px = img.load()
    rng = random.Random(5)
    draw_beam(px, rng)
    for i in range(SLOTS):
        draw_socket(px, sock_x(i), MARGIN, rng)
    return add_shadow(img)


# ---------------------------------------------------------------- overlays
def rim(ramp_, glow=None):
    """The bezel (rings 2 and 3, not under the brackets) in another material; glow (alphas) tints the
    outermost rings of the recess."""
    img = new(SOCK, SOCK)
    px = img.load()
    for y in range(SOCK):
        for x in range(SOCK):
            if (x, y) in BRACKETS:
                continue
            d, side = socket_dist(x, y)
            if d == 2:
                px[x, y] = ramp_[1] if lit(side) else ramp_[2]
            elif d == 3:
                px[x, y] = ramp_[3] if lit(side) else ramp_[1]
            elif glow is not None and d is not None and d - RIM < len(glow) and d >= RIM:
                c = ramp_[0]
                px[x, y] = (c[0], c[1], c[2], glow[d - RIM])
    return img


RIMS = [
    lambda: rim(GOLD),
    lambda: rim(MANA, glow=(110, 40)),
    lambda: rim(WHITE_HOT, glow=(220, 120, 45)),
]


def rims():
    return strip([f() for f in RIMS])


# ---------------------------------------------------------------- icons
def recess_tile(branch):
    bg = icons.BRANCH_BG[branch]
    img = new(PIT, PIT)
    px = img.load()
    for y in range(PIT):
        for x in range(PIT):
            if x == 0 or y == 0:
                px[x, y] = mix(bg[4], GLASS_SHADOW, 0.6)
                continue
            v = math.hypot(x - 7, y - 7) / (PIT * 1.1)
            px[x, y] = bg[1 + dither_index(min(1, 0.25 + v), x, y, 4)]
    return img


def hotbar_icons(limit=icons.INNER):
    out = {}
    for name, (branch, subject) in icons.icons().items():
        subject = icons.crop(subject)
        lim = limit + 1 if name in ('ENCHANTING', 'IMBUE') else limit
        if subject.width > lim or subject.height > lim:
            subject = icons.fit(subject, lim)
        img = recess_tile(branch)
        img.alpha_composite(subject, ((PIT - subject.width + 1) // 2, (PIT - subject.height + 1) // 2))
        out[name] = img
    return out


# ---------------------------------------------------------------- combo meter
def combo_bar():
    """The empty combo meter: a HUD bar body without the gem, pointed at both ends."""
    th, cy, half, tx, ty = COMBO_TH, COMBO_CY, COMBO_HALF, COMBO_TX, COMBO_TY
    collars = {tx - 3, tx - 2, tx + TROUGH_LEN + 1, tx + TROUGH_LEN + 2}
    mask = set()
    for x in range(COMBO_W):
        k = max(0, half - x, x - (COMBO_W - 1 - half))
        grow = 1 if x in collars else 0
        for y in range(cy - half + k - grow, cy + half - k + grow + 1):
            mask.add((x, y))
    img = new(COMBO_W, 2 * cy + 1)
    px = img.load()
    shade_body(px, mask, random.Random(13), collars, cy=cy)
    for x in range(tx, tx + TROUGH_LEN):
        px[x, ty + th] = IRON[1]                    # lit lower lip under the glass
    for k in (1, 2, 3):
        x = tx + TROUGH_LEN * k // 4 - 1
        for y0 in (cy - half + 1, cy + half - 2):
            rivet(px, x, y0)
    tint = mix(GLASS_SHADOW, FIRE[4], 0.35)
    floor = mix(GLASS_SHADOW, FIRE[4], 0.6)
    for x in range(tx, tx + TROUGH_LEN):
        for y in range(ty, ty + th):
            px[x, y] = GLASS_SHADOW if y == ty or x == tx else floor if y == ty + th - 1 else tint
    return add_shadow(img)


def combo_fill():
    return strip(fill_frames(COMBO_TH, FIRE, 17))


def generate(out_dir):
    hotbar().save(f'{out_dir}/Hotbar.png')
    rims().save(f'{out_dir}/HotbarRim.png')
    for name, img in hotbar_icons().items():
        img.save(f'{out_dir}/HotbarIcon_{name}.png')
    combo_bar().save(f'{out_dir}/HudBar_COMBO.png')
    combo_fill().save(f'{out_dir}/HudFill_COMBO.png')


# ---------------------------------------------------------------- preview
def dimmed(icon):
    """Roughly what Hotbar.java's ColorAdjust does to an icon on cooldown."""
    out = icon.copy()
    px = out.load()
    for y in range(icon.height):
        for x in range(icon.width):
            r, g, b, a = px[x, y]
            lum = r * 0.3 + g * 0.59 + b * 0.11
            px[x, y] = tuple(int((c * 0.3 + lum * 0.7) * 0.4) for c in (r, g, b)) + (a,)
    return out


def compose(slots, active=None, on=(), cooldown=None, flash=None, combo=None):
    """The hotbar as the game draws it (art px). slots: icon names or None; cooldown: (slot, part left 0..1);
    combo: the combo meter's fill 0..1 (None hides it)."""
    img = hotbar()
    ic = hotbar_icons()
    frames = [f() for f in RIMS]
    for i, name in enumerate(slots):
        x, y = sock_x(i) + RIM, MARGIN + RIM
        if name:
            icon = ic[name]
            if cooldown and cooldown[0] == i:
                img.alpha_composite(dimmed(icon), (x, y))
                h = round(PIT * (1 - cooldown[1]))
                if h:
                    img.alpha_composite(icon.crop((0, PIT - h, PIT, PIT)), (x, y + PIT - h))
                    for xx in range(PIT):
                        p = img.getpixel((x + xx, y + PIT - h))
                        img.putpixel((x + xx, y + PIT - h), mix(p, WHITE_HOT[1], 0.6))
            else:
                img.alpha_composite(icon, (x, y))
        for k, hit in ((0, i == active), (1, i in on), (2, i == flash)):
            if hit:
                img.alpha_composite(frames[k], (sock_x(i), MARGIN))
    bar = combo_bar()
    out = new(W + 1, bar.height + COMBO_GAP + img.height)
    out.alpha_composite(img, (0, bar.height + COMBO_GAP))
    if combo is not None:
        fill, drain, edge = fill_frames(COMBO_TH, FIRE, 17)
        n = round(TROUGH_LEN * combo)
        if n:
            bar.alpha_composite(fill.crop((0, 0, n, COMBO_TH)), (COMBO_TX, COMBO_TY))
            bar.alpha_composite(edge.crop((0, 0, 1, COMBO_TH)), (COMBO_TX + n - 1, COMBO_TY))
        out.alpha_composite(bar, (END, 0))
    return out


def preview(path, bg=None):
    zoom = 5
    scene_w, scene_h = 960, 200
    if bg:
        scene = Image.open(bg).convert('RGBA').crop((480, 1080 - scene_h, 1440, 1080))
    else:
        scene = Image.new('RGBA', (scene_w, scene_h), (40, 14, 14, 255))
    a = compose(['FISTICUFFS', 'BOW', 'MANA_SHIELD', None], active=0, on=(2,), cooldown=(1, 0.4), combo=0.6)
    b = compose(['SHORTSWORD', 'FIRE_BALL', 'LEVITATION', 'IMBUE'], active=1, flash=3)
    big = a.resize((a.width * SCALE, a.height * SCALE), Image.Resampling.NEAREST)
    scene.alpha_composite(big, ((scene_w - big.width) // 2, scene_h - 24 - big.height))
    out = Image.new('RGBA', (max(scene_w, a.width * zoom + 20), scene_h + 2 * (a.height * zoom + 10) + 10),
                    (20, 18, 24, 255))
    out.paste(scene, (0, 0))
    y = scene_h + 10
    for img in (a, b):
        z = img.resize((img.width * zoom, img.height * zoom), Image.Resampling.NEAREST)
        out.alpha_composite(z, (10, y))
        y += z.height + 10
    out.save(path)


if __name__ == '__main__':
    preview(sys.argv[1] if len(sys.argv) > 1 else 'hotbar_preview.png', sys.argv[2] if len(sys.argv) > 2 else None)
