"""The Hollow Ember (boss): the player's void double, 1.5x the player at the same pixel size.

Frame table (name: frames x frame size), same frames and order as Player_<name>.png:
    idle       12 x 96x96
    walk       30 x 96x96
    land       72 x 96x96
    takeoff     3 x 96x96
    run        30 x 96x96
    dash        6 x 96x96
    hurt        6 x 96x96
    death      14 x 96x96
    attack      8 x 96x96
    shoot       5 x 96x96
    cast        8 x 96x96
    levitate   54 x 96x96
    glide      18 x 96x96
    dive       12 x 96x96
    slam       10 x 96x96
    block       6 x 96x96
    charge      6 x 96x96
    fidget     24 x 96x96
    airflow    35 x 96x96
    air         4 x 96x96

Other textures: HollowShard 4f 12x12 (violet, orbits the boss), HollowShardLoose 4f 12x12 (warm ember, the pickup),
HollowGate 4f 24x64 (tile, repeats every 64 px upward, like BossGate), HollowGlassTop 1024x12 (like GroundTop).

How: player_slime.py's own animations are run with slimes.SCALE = 1.5, so the body is rasterised natively at
96 px (the geometry stays in 64 px units: feet at the same relative anchor, shown at x2 like everything else).
player_slime.frame is wrapped so every frame gets the void recolour, a see-through hollow low in the body ringed
in violet, and a violet flame crown that follows the squash and stretch.

Preview:  python tools/art/hollow.py preview      (writes preview_hollow_anims.png and preview_hollow_ingame.png)
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

from pixel import new, ramp, hexc, mix, strip
import slimes
import player_slime
from slimes import EMBER, BASE_Y, rel
from effects import WrapNoise

HERE = os.path.dirname(os.path.abspath(__file__))
TEXTURES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'textures'))

SCALE = 1.5
VOID_BODY = ramp('#9484b4', '#4c3c6a', '#2c2044', '#1a1228', '#0c0814')
VOID_GLOW = ramp('#f6eaff', '#d8b4ff', '#a86cff', '#7a3ae0', '#4a1a9a')

HOLLOW_V = 0.24                  # height of the hollow in the body (0 bottom .. 1 top)
HOLLOW_R = 4.6                   # its radius in 64 px units
CROWN = ((-0.62, 7), (-0.31, 13), (0.0, 20), (0.31, 15), (0.62, 10))   # (nx across the top, tongue height)


def clamp(v):
    return max(0.0, min(1.0, v))


# ---------- recolour ----------
def void_recolor(img):
    """Warm pixels (embers, cracks, sparks) -> violet glow ramp, everything else by brightness -> void body ramp."""
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            lum = (c[0] + c[1] + c[2]) / 765
            if c[0] - c[2] > 60 and c[0] > 110:
                px[x, y] = VOID_GLOW[int(clamp(1 - lum * 1.3) * 4.99)][:3] + (c[3],)
            elif c[3] == 255:
                px[x, y] = VOID_BODY[int(clamp(1 - lum / 0.4) * 4.99)]
            else:
                px[x, y] = mix(c, VOID_BODY[2], 0.6)[:3] + (c[3],)
    return img


# ---------- native drawing in 64 px units ----------
def disc(px, n, cx, cy, rx, ry, c):
    k = SCALE
    for y in range(max(0, int((cy - ry) * k) - 1), min(n, int((cy + ry) * k) + 2)):
        for x in range(max(0, int((cx - rx) * k) - 1), min(n, int((cx + rx) * k) + 2)):
            if ((((x + 0.5) / k - cx) / rx) ** 2 + (((y + 0.5) / k - cy) / ry) ** 2) <= 1:
                px[x, y] = c


def hollow_spot(info):
    """Centre and radius (64 px units) of the hollow for this body, or None when it is squashed too flat."""
    if info['h'] < 17:
        return None
    cx, cy = rel(info, 0.0, HOLLOW_V)
    hw = slimes.half_width(HOLLOW_V, info['w'], **info['form'])
    r = min(HOLLOW_R, info['w'] * 0.115, info['h'] * 0.13, hw * 0.5)
    return (cx, cy, r) if r >= 2.2 else None


def carve(img, info):
    """pre fx (before the outline): cuts the hole out of the body."""
    spot = hollow_spot(info)
    if spot:
        cx, cy, r = spot
        disc(img.load(), img.width, cx, cy, r, r, (0, 0, 0, 0))


def ring(img, spot, tick):
    cx, cy, r = spot
    px, k = img.load(), SCALE
    pulse = 0.05 * math.sin(tick * 1.3)
    for y in range(int((cy - r * 1.6) * k), int((cy + r * 1.6) * k) + 1):
        for x in range(int((cx - r * 1.6) * k), int((cx + r * 1.6) * k) + 1):
            if not (0 <= x < img.width and 0 <= y < img.height) or px[x, y][3] == 0:
                continue
            d = math.hypot((x + 0.5) / k - cx, (y + 0.5) / k - cy) / r
            if d <= 1.0:
                px[x, y] = VOID_GLOW[4]                    # the outline lining the hole
            elif d < 1.16 + pulse:
                px[x, y] = VOID_GLOW[0]
            elif d < 1.32 + pulse:
                px[x, y] = VOID_GLOW[1]
            elif d < 1.5:
                px[x, y] = VOID_GLOW[3] if d < 1.42 else mix(px[x, y], VOID_GLOW[4], 0.6)


def crown(img, info, tick):
    """A violet flame crown of 5 tongues rooted in the top of the body, bent back by the lean."""
    n, k = img.width, SCALE
    top = rel(info, 0.0, 1.0)[1]
    scale = clamp((info['h'] - 12) / 14) * min(1.0, max(0.0, (top - 1) / 20))
    if scale < 0.3:
        return
    px = img.load()
    tongues = []
    for i, (nx, height) in enumerate(CROWN):
        v = 0.94 - 0.2 * abs(nx)
        bx, by = rel(info, nx * 0.8, v)
        flick = math.sin(tick * 1.1 + i * 1.7)
        ht = height * scale * (1 + 0.12 * flick)
        sway = -info['lean'] * 0.5 + 1.8 * math.sin(tick * 0.8 + i * 2.3) + nx * 3
        wid = (2.9 if i == 2 else 2.3) * (0.7 + 0.3 * scale)
        tongues.append((bx, by, ht, sway, wid))
    for layer in (0, 1):                                    # all outer glow first, then the hot cores on top
        for bx, by, ht, sway, wid in tongues:
            steps = int(ht * k * 1.3) + 1
            for s in range(steps + 1):
                t = s / steps
                x = bx + sway * t * t
                y = by - ht * t
                r = wid * (1 - t) ** 0.8 * (1.0 if layer == 0 else 0.5) + 0.35
                if layer == 0:
                    c = VOID_GLOW[3] if t < 0.7 else VOID_GLOW[2]
                elif t < 0.75:
                    c = VOID_GLOW[1] if t > 0.15 else VOID_GLOW[0]
                else:
                    continue
                disc(px, n, x, y, r, r * 1.1, c)


# ---------- the boss frame ----------
_tick = [0]
_orig_frame = player_slime.frame


def hollow_frame(w, h, **kw):
    """player_slime.frame with the void recolour, hollow and crown. Same arguments, a 96 px frame."""
    mood = dict(kw)
    seen = {}

    def pre(img, px, info):
        seen['info'] = info
        carve(img, info)

    mood['pre'] = list(mood.get('pre', [])) + [pre]
    img = _orig_frame(w, h, **mood)
    void_recolor(img)
    info = seen['info']
    _tick[0] += 1
    spot = hollow_spot(info)
    if spot:
        ring(img, spot, _tick[0])
    crown(img, info, _tick[0])
    return img


def boss_anims():
    """name -> list of 96 px frames, the player's animations driven at 1.5x."""
    slimes.SCALE = SCALE
    player_slime.frame = hollow_frame
    try:
        _tick[0] = 0
        anims = {name: fn() for name, fn in player_slime.ANIMS.items()}
        anims['air'] = [player_slime.frame(w, h, lean=l, eye_dy=-1 if i < 2 else 1, phase=i / 4, round=r)
                        for i, (w, h, l, r) in enumerate(slimes.AIR_PARAMS)]
        return anims
    finally:
        slimes.SCALE = 1.0
        player_slime.frame = _orig_frame


# ---------- shards ----------
def shard_frames(pal):
    """A floating ember shard, 4 frames 12x12: it turns about its long axis and its core pulses."""
    out = []
    for i, hw in enumerate((4.4, 3.3, 1.9, 3.3)):
        img = new(12, 12)
        px = img.load()
        for y in range(12):
            for x in range(12):
                v = (y - 1.0) / 9.5
                if not 0 <= v <= 1:
                    continue
                half = hw * (v / 0.45 if v < 0.45 else (1 - v) / 0.55)
                d = x + 0.5 - 6
                if abs(d) <= half:
                    c = pal[1] if d < -half * 0.25 else pal[2] if d < half * 0.4 else pal[3]
                    if abs(d) < 0.7:
                        c = pal[0]
                    px[x, y] = c
        px[6, 4 + (i % 2)] = pal[0]
        px[5, 5 + (i % 2)] = pal[0]
        out.append(slimes.outline(img, pal[4]))
    return out


# ---------- gate and floor ----------
def gate_frames():
    """A wall of black glass shards with violet glints running up it; 24 wide, tiles every 64 px (4 frames)."""
    out = []
    for i in range(4):
        img = new(24, 64)
        px = img.load()
        for y in range(64):
            zig = abs(y % 16 - 8) / 8.0                     # period 16 divides 64: tiles upward
            left = 2 + round(2.5 * zig) + (1 if (y // 16) % 2 else 0)
            right = 21 - round(2.5 * (1 - zig)) - (1 if (y // 32) % 2 else 0)
            for x in range(left, right + 1):
                u = (y + 4 * x) % 16                        # diagonal facets
                c = VOID_BODY[3 if u < 8 else 4]
                if (x * 3 + y // 8) % 5 == 0:
                    c = VOID_BODY[2]
                if u % 8 == 0:
                    c = VOID_GLOW[4] if (x + y) % 3 else VOID_GLOW[3]
                if x in (left, right):
                    c = VOID_GLOW[3] if (y + i) % 3 else VOID_BODY[2]
                glint = (y - 16 * i + x * 2) % 64
                if glint < 3 and u % 8 < 5:
                    c = VOID_GLOW[1 if glint else 0]
                px[x, y] = c[:3] + (238,)
        out.append(img)
    return out


def glass_top(width=1024, height=12, seed=4):
    """Black glass edge for the arena floor: smooth, a faint violet sheen, seamless sideways."""
    img = new(width, height)
    px = img.load()
    lump = WrapNoise(seed, width, 1, 0.12, 1)
    slow = WrapNoise(seed + 3, width, 1, 1 / 60, 1)
    speck = WrapNoise(seed + 7, width, 1, 0.8, 1)
    for x in range(width):
        sheen = 0.5 + 0.5 * math.sin(2 * math.pi * 9 * x / width + 3 * slow(x)) * (0.4 + 0.6 * slow(x))
        top = 1 + round(1.4 * lump(x))
        bottom = 8 + round(2.5 * (1 - slow(x)))
        for y in range(top, min(height, bottom + 1)):
            if y == top:
                c = VOID_GLOW[3] if sheen > 0.72 else VOID_BODY[2] if sheen > 0.35 else VOID_BODY[3]
            elif y == top + 1:
                c = VOID_BODY[2] if sheen > 0.6 else VOID_BODY[3]
            elif y >= bottom - 1:
                c = VOID_BODY[4]
            else:
                c = VOID_BODY[3] if (x + y * 2) % 13 else VOID_BODY[4]
                if sheen > 0.8 and (x + y) % 6 == 0:
                    c = VOID_BODY[2]
            px[x, y] = c
        if speck(x) > 0.86:
            px[x, top + 3] = VOID_GLOW[2]
    return img


# ---------- output ----------
def table(anims):
    return '\n'.join(f'{name:<9} {len(f):>3} x {f[0].width}x{f[0].height}' for name, f in anims.items())


def generate(out_dir):
    anims = boss_anims()
    for name, frames in anims.items():
        strip(frames).save(f'{out_dir}/Hollow_{name}.png')
    strip(shard_frames(VOID_GLOW)).save(f'{out_dir}/HollowShard.png')
    strip(shard_frames(EMBER)).save(f'{out_dir}/HollowShardLoose.png')
    strip(gate_frames()).save(f'{out_dir}/HollowGate.png')
    glass_top().save(f'{out_dir}/HollowGlassTop.png')
    print(table(anims))


# ---------- previews (untracked) ----------
def preview_anims(out, per_row=12, s=2):
    anims = boss_anims()
    cell, label = 96 * s, 170
    rows = [(name, frames[k:k + per_row], len(frames)) for name, frames in anims.items()
            for k in range(0, len(frames), per_row)]
    img = Image.new('RGBA', (label + cell * per_row, cell * len(rows)), (22, 12, 24, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=22)
    seen = set()
    for r, (name, frames, total) in enumerate(rows):
        y = r * cell
        if name not in seen:
            seen.add(name)
            d.text((10, y + cell // 2 - 12), f'{name} ({total})', font=font, fill=(236, 226, 255))
            d.line([(0, y), (img.width, y)], fill=(90, 50, 130), width=2)
        for i, f in enumerate(frames):
            x0 = label + i * cell
            d.rectangle([x0, y, x0 + cell - 1, y + cell - 1], fill=(34, 18, 40) if (i + r) % 2 else (28, 14, 34))
            d.line([(x0, y + 93 * s), (x0 + cell, y + 93 * s)], fill=(60, 40, 80))      # the feet line
            img.alpha_composite(f.resize((cell, cell), Image.NEAREST), (x0, y))
    img.save(out)


def preview_ingame(out, s=2):
    """The boss next to the player on the ground (everything x2), shards, the gate and the glass floor."""
    anims = boss_anims()
    load = lambda n: Image.open(f'{TEXTURES}/{n}.png').convert('RGBA')
    big = lambda im: im.resize((im.width * s, im.height * s), Image.NEAREST)
    w, h, ground = 780, 330, 250
    img = Image.new('RGBA', (w, h), (26, 10, 12, 255))
    img.alpha_composite(big(load('GroundTop')).crop((0, 0, 450, 12 * s)), (0, ground - 2 * s))
    img.alpha_composite(load('Ground').crop((0, 0, 450, 70)), (0, ground + 10 * s))
    glass = big(load('HollowGlassTop')).crop((0, 0, w - 450, 12 * s))
    img.alpha_composite(glass, (450, ground - 2 * s))
    img.alpha_composite(Image.new('RGBA', (w - 450, 70), (10, 7, 16, 255)), (450, ground + 10 * s))
    img.alpha_composite(big(load('Player_idle').crop((0, 0, 64, 64))), (40, ground - 62 * s))
    for frame, x in ((anims['idle'][3], 150), (anims['attack'][4], 330)):
        img.alpha_composite(big(frame), (x, ground - 93 * s))
    shards = load('HollowShard')
    for i, (x, y) in enumerate(((140, 110), (310, 70), (290, 180), (230, 30))):
        img.alpha_composite(big(strip_frame(shards, i)), (x, y))
    img.alpha_composite(big(strip_frame(load('HollowShardLoose'), 1)), (110, ground - 44))
    gate = load('HollowGate').crop((0, 0, 24, 64))
    img.alpha_composite(big(gate), (640, ground - 64 * s + 4 * s))
    img.alpha_composite(big(gate), (640, ground - 128 * s + 4 * s))
    img.save(out)


def strip_frame(sheet, i, size=12):
    return sheet.crop((i * size, 0, (i + 1) * size, size))


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'preview':
        preview_anims(os.path.join(HERE, 'preview_hollow_anims.png'))
        preview_ingame(os.path.join(HERE, 'preview_hollow_ingame.png'))
    else:
        generate(TEXTURES)
