"""Extra animations for the Tar & Ember player slime (slimes.PLAYER), 64x64 frames like slimes.py.

slimes.py writes the base strips (Player_idle/walk/air/land); this module writes the rest:
  takeoff (3)  run (8)  dash (4)  hurt (4)  death (10)  attack (5)  shoot (3)  cast (6)
  levitate (6)  glide (4)  dive (2)  slam (6)  block (4)  charge (4, picked by charge progress)  fidget (16)
SpriteComponent reads the frame count from the strip width (64 px per frame).

Review sheet:  python tools/art/player_slime.py sheet out.png
"""
import math
import sys

from pixel import new, put, ellipse, mix, strip
import slimes
from slimes import PLAYER, TAR, EMBER, SIZE, CX, BASE_Y, draw_slime

FRAME = SIZE


# ---------- frame helper ----------
def frame(w, h, lift=0, lean=0, eye_dy=0, blink=False, phase=0.0, **mood):
    """One player frame. mood keys: eyes, look, heat, cool (see slimes.eyes_ember / ember_cracks),
    pre = [fn(img, px, info)] drawn before the outline (tar bits), post = [fn(img)] drawn after (glow, sparks)."""
    img = draw_slime(PLAYER, w, h, lift=lift, lean=lean, eye_dy=eye_dy, blink=blink, phase=phase, mood=mood)
    for fx in mood.get('post', []):
        fx(img)
    return img


# ---------- tar bits (pre: they get the black outline) ----------
def droplets(*pts):
    """Flying tar droplets at (x, y, r); r >= 1.5 gets a molten core so they read on dark backgrounds."""
    def fx(img, px, info):
        for x, y, r in pts:
            ellipse(px, SIZE, SIZE, x, y, r, r * 1.1, TAR[1])
            put(px, SIZE, SIZE, int(x - r * 0.4), int(y - r * 0.4), TAR[0])
            if r >= 1.5:
                put(px, SIZE, SIZE, int(x + 0.3), int(y + 0.3), EMBER[3])
    return fx


def strands(*items):
    """Tar strands hanging from the bottom of a floating body: (nx, length, drop) - drop > 0 adds a
    detached droplet that far below the strand."""
    def fx(img, px, info):
        bottom = info['bottom']
        for nx, length, drop in items:
            x = int(CX + nx * info['w'] * 0.5)
            for k in range(int(length)):                 # 1 px strand thinning into a teardrop
                put(px, SIZE, SIZE, x, int(bottom + k), TAR[2])
            ellipse(px, SIZE, SIZE, x + 0.5, bottom + length + 0.5, 1.4, 1.6, TAR[1])
            put(px, SIZE, SIZE, x, int(bottom + length), TAR[0])
            if drop > 0:
                ellipse(px, SIZE, SIZE, x + 0.5, bottom + length + drop + 2, 0.9, 1.0, TAR[1])
    return fx


def bubble(r):
    """A tar bubble swelling on top of the head (fidget)."""
    def fx(img, px, info):
        x, y = slimes.rel(info, -0.15, 1.0)
        y += r * 0.6
        ellipse(px, SIZE, SIZE, x, y - r, r, r, TAR[1])
        ellipse(px, SIZE, SIZE, x, y - r * 0.8, r * 0.6, r * 0.5, TAR[2])
        put(px, SIZE, SIZE, int(x - r * 0.5), int(y - r * 1.5), TAR[0])
        if r >= 3:
            put(px, SIZE, SIZE, int(x), int(y - r * 0.7), EMBER[3])   # glow showing through the thin skin
    return fx


# ---------- glow (post: no outline) ----------
def sparks(*pts):
    """Ember sparks: (x, y, size, heat 0 white .. 4 deep red)."""
    def fx(img):
        px = img.load()
        for x, y, size, t in pts:
            c = EMBER[min(4, max(0, int(t)))]
            put(px, SIZE, SIZE, int(x), int(y), c)
            if size >= 2:
                for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                    if px[int(x) + dx, int(y) + dy][3] == 0 if 0 <= int(x) + dx < SIZE and 0 <= int(y) + dy < SIZE else False:
                        put(px, SIZE, SIZE, int(x) + dx, int(y) + dy, EMBER[min(4, int(t) + 2)])
    return fx


def streaks(*lines):
    """Horizontal motion streaks behind a dash: (x0, x1, y, colour index); fades out to the back."""
    def fx(img):
        px = img.load()
        for x0, x1, y, ci in lines:
            for x in range(int(x0), int(x1)):
                if not (0 <= x < SIZE and 0 <= y < SIZE) or px[x, y][3] > 0:
                    continue
                t = (x - x0) / max(1, x1 - x0)          # 0 at the far back .. 1 next to the body
                if t < 0.35 and (x % 2):
                    continue                             # broken up at the tail
                c = EMBER[ci]
                px[x, y] = (c[0], c[1], c[2], int(90 + 165 * t))
    return fx


def vlines(*lines):
    """Vertical speed lines above a dive: (x, y0, y1, colour index)."""
    def fx(img):
        px = img.load()
        for x, y0, y1, ci in lines:
            for y in range(int(y0), int(y1)):
                if 0 <= y < SIZE and px[x, y][3] == 0:
                    t = (y - y0) / max(1, y1 - y0)
                    c = EMBER[ci]
                    px[x, y] = (c[0], c[1], c[2], int(70 + 170 * t))
    return fx


# ---------- animations ----------
def anim_takeoff():
    """Jump launch (played once when leaving the ground): stretched, tar splashing off the base."""
    return [
        frame(34, 46, eyes='wide', heat=0.5, pre=[droplets((CX - 15, 59, 1.6), (CX + 15, 59, 1.4), (CX - 8, 61, 1))]),
        frame(32, 48, heat=0.4, pre=[droplets((CX - 19, 58, 1.4), (CX + 19, 57, 1.6), (CX - 11, 62, 1))]),
        frame(33, 47, heat=0.3, pre=[droplets((CX - 22, 60, 1.1), (CX + 22, 60, 1.1))]),
    ]


def anim_run(n=8):
    """Full-speed run: longer hops, leaning hard into it, sparks kicked up behind."""
    frames = []
    for i in range(n):
        p = i / n
        hop = max(0.0, math.sin(p * 2 * math.pi))
        squash = max(0.0, -math.sin(p * 2 * math.pi))
        w = 42 + squash * 8 - hop * 5
        h = 37 - squash * 6 + hop * 5
        post = []
        if squash > 0.5:                     # foot strike: kick sparks back
            post.append(sparks((CX - w / 2 - 3, 58, 1, 2), (CX - w / 2 - 6, 55, 1, 3), (CX - w / 2 - 2, 53, 1, 1)))
        elif hop > 0.6:
            post.append(sparks((CX - w / 2 - 8, 52 - hop * 4, 1, 3)))
        frames.append(frame(w, h, lift=hop * 7, lean=5 + hop * 3, phase=p, eyes='fierce',
                            heat=0.45 + 0.25 * squash, post=post))
    return frames


def anim_dash(n=4):
    """Leather Gloves dodge: smeared flat and fast with molten streaks behind."""
    frames = []
    for i in range(n):
        wob = math.sin(i / n * 2 * math.pi)
        back = CX - 28
        lines = [(back - 22 + 3 * (i % 2), back + 2, 44, 3), (back - 28 + 2 * i, back + 4, 50, 2),
                 (back - 18 - 2 * (i % 2), back + 4, 56, 3), (back - 12, back + 2, 60, 4)]
        frames.append(frame(56 + wob, 26 - wob, lift=2, lean=9, phase=i / n, eyes='squint', heat=0.7,
                            post=[streaks(*lines), sparks((back - 4 - 3 * i, 47 + i, 1, 1))]))
    return frames


def anim_hurt():
    """Taking a hit: knocked flat and back, eyes squeezed shut, tar flung off, cracks flare."""
    return [
        frame(47, 31, lean=-8, eyes='shut', heat=1.0,
              pre=[droplets((CX - 25, 36, 1.6), (CX - 21, 30, 1.2), (CX + 12, 30, 1))],
              post=[sparks((CX + 6, 26, 2, 0), (CX - 2, 24, 1, 1))]),
        frame(36, 42, lean=-4, eyes='shut', heat=0.75,
              pre=[droplets((CX - 28, 33, 1.4), (CX - 24, 25, 1.1), (CX + 15, 27, 1))]),
        frame(43, 35, lean=2, eyes='squint', heat=0.45, pre=[droplets((CX - 30, 34, 1))]),
        frame(40, 38, eyes='normal', heat=0.3),
    ]


def anim_death():
    """Knocked out, the glow goes out of the eyes and the slime melts into a cooling tar puddle."""
    rise = lambda k: [sparks((CX - 6 + 4 * k, 40 - 6 * k, 1, 2 + k % 2), (CX + 8 - 2 * k, 46 - 5 * k, 1, 3))]
    return [
        frame(47, 31, lean=-8, eyes='shut', heat=1.0, pre=[droplets((CX - 25, 36, 1.6), (CX - 21, 30, 1.2))],
              post=[sparks((CX + 6, 26, 2, 0))]),
        frame(36, 42, lean=-3, eyes='dim', heat=0.6),
        frame(44, 34, lean=1, eyes='dim', heat=0.4, cool=0.2),
        frame(48, 28, eyes='dim', heat=0.2, cool=0.4),
        frame(52, 22, eyes='off', heat=0.0, cool=0.55, post=rise(0)),
        frame(55, 16, eyes='off', heat=0.0, cool=0.7, post=rise(1)),
        frame(58, 11, eyes='off', heat=0.0, cool=0.8, post=rise(2)),
        frame(60, 8, eyes='off', heat=0.0, cool=0.9, post=[sparks((CX + 2, 30, 1, 4))]),
        frame(61, 6, eyes='off', heat=0.0, cool=1.0),
        frame(61, 6, eyes='off', heat=0.0, cool=1.0),
    ]


def anim_attack():
    """Melee swing: wind back, throw the whole body forward, follow through."""
    return [
        frame(44, 34, lean=-5, eyes='fierce', heat=0.5),
        frame(46, 32, lean=-7, eyes='fierce', heat=0.7),
        frame(34, 44, lean=8, eyes='fierce', heat=1.0, pre=[droplets((CX - 20, 34, 1.2))]),
        frame(38, 40, lean=5, eyes='fierce', heat=0.6),
        frame(41, 38, lean=2, eyes='normal', heat=0.35),
    ]


def anim_shoot():
    """Bow / crossbow / trident throw: a short recoil."""
    return [
        frame(44, 35, lean=-4, eyes='squint', heat=0.5),
        frame(42, 37, lean=-2, eyes='normal', heat=0.4),
        frame(40, 38, eyes='normal', heat=0.3),
    ]


def anim_cast():
    """Spell: the body rises, eyes and cracks go white-hot and embers boil up off the top."""
    top = lambda h: BASE_Y - h
    return [
        frame(44, 34, eyes='squint', heat=0.6),
        frame(38, 42, eyes='wide', heat=0.9, post=[sparks((CX - 4, top(42) - 2, 1, 1), (CX + 6, top(42) - 1, 1, 2))]),
        frame(35, 46, eyes='wide', heat=1.0, post=[sparks((CX - 6, top(46) - 5, 2, 0), (CX + 7, top(46) - 3, 1, 1),
                                                          (CX + 1, top(46) - 8, 1, 2), (CX - 12, top(46) + 2, 1, 2))]),
        frame(37, 44, eyes='wide', heat=1.0, post=[sparks((CX - 8, top(44) - 10, 1, 1), (CX + 9, top(44) - 8, 2, 1),
                                                          (CX + 2, top(44) - 13, 1, 2), (CX - 14, top(44) - 3, 1, 3))]),
        frame(40, 39, eyes='normal', heat=0.6, post=[sparks((CX - 9, top(39) - 14, 1, 3), (CX + 10, top(39) - 12, 1, 3))]),
        frame(40, 38, eyes='normal', heat=0.4),
    ]


def anim_levitate(n=6):
    """Levitation: floating off the ground with tar strands dripping and embers drifting down."""
    frames = []
    for i in range(n):
        p = i / n
        s = math.sin(p * 2 * math.pi)
        lift = 9 + s * 1.5
        drop = (i % 3) * 2
        frames.append(frame(38 - s, 40 + s, lift=lift, phase=p, heat=0.45 + 0.15 * s,
                            pre=[strands((-0.3, 2 + (i % 2), 0), (0.25, 2 + ((i + 1) % 3), drop))],
                            post=[sparks((CX - 6 + (i % 3), BASE_Y - 1 - (i % 2), 1, 3), (CX + 9, BASE_Y - 4 + (i % 3), 1, 4))]))
    return frames


def anim_glide(n=4):
    """Slow fall: spread wide and flat like a canopy, edges rippling."""
    frames = []
    for i in range(n):
        p = i / n
        s = math.sin(p * 2 * math.pi)
        frames.append(frame(54 + s, 24 - s, lift=8, phase=p, look=(0, -0.6), heat=0.3,
                            pre=[strands((-0.9, 2 + (i % 2), 0), (0.9, 3 - (i % 2), 0))]))
    return frames


def anim_dive():
    """Hammer slam fall: stretched into a falling spike with speed lines above."""
    frames = []
    for i in range(2):
        top = BASE_Y - 54
        lines = [(CX - 7, max(0, top - 14 + 3 * i), top - 1, 3), (CX + 6, max(0, top - 10 - 3 * i), top + 1, 2),
                 (CX - 1, max(0, top - 6), top - 2, 1)]
        frames.append(frame(26 + i, 54 - i, eyes='squint', look=(0, 1), heat=0.85, phase=i / 2,
                            post=[vlines(*lines)]))
    return frames


def anim_slam():
    """Hammer slam landing: a huge splat with tar thrown both ways, then the rebound."""
    return [
        frame(62, 14, eyes='squint', heat=1.0,
              pre=[droplets((CX - 27, 44, 1.6), (CX + 27, 45, 1.6), (CX - 19, 38, 1.2), (CX + 21, 39, 1.2))],
              post=[sparks((CX - 12, 40, 2, 0), (CX + 14, 41, 2, 0), (CX, 37, 1, 1))]),
        frame(58, 20, eyes='squint', heat=0.9,
              pre=[droplets((CX - 30, 34, 1.4), (CX + 30, 35, 1.4), (CX - 22, 27, 1.1), (CX + 24, 28, 1.1))],
              post=[sparks((CX - 15, 30, 1, 1), (CX + 17, 31, 1, 1))]),
        frame(36, 46, eyes='wide', heat=0.7, pre=[droplets((CX - 31, 40, 1.1), (CX + 31, 41, 1.1))]),
        frame(44, 34, eyes='normal', heat=0.5, pre=[droplets((CX - 30, 56, 1), (CX + 30, 57, 1))]),
        frame(41, 39, eyes='normal', heat=0.35),
        frame(40, 38, eyes='normal', heat=0.3),
    ]


def anim_block(n=4):
    """Metal Gloves block: hunkered down, braced back, the tar hardened to stone (cracks go cold)."""
    frames = []
    for i in range(n):
        t = 0.5 if i % 2 else 0
        frames.append(frame(48 + t, 32 - t, lean=-2, eyes='squint', heat=0.0, cool=0.75, phase=i / n))
    return frames


def anim_charge(n=4):
    """Charging an attack (bow draw, zweihander, fireball): picked by progress, not played in time."""
    frames = []
    for i in range(n):
        p = i / (n - 1)
        ring = []
        for k in range(int(p * 6)):
            a = k / 6 * 2 * math.pi + p * 1.3
            ring.append((CX + math.cos(a) * 24, BASE_Y - 20 + math.sin(a) * 19, 2 if k % 3 == 0 and p > 0.9 else 1,
                         0 if p > 0.9 else 1 + k % 2))
        frames.append(frame(41 + 3 * p, 38 - 3 * p, lean=-1 - 3 * p, eyes='squint' if p < 0.5 else 'fierce',
                            heat=0.2 + 0.8 * p, phase=0.25, post=[sparks(*ring)]))
    return frames


def anim_fidget(n=16):
    """Idle variant after standing still a while: glance back and forth, then a tar bubble swells
    on top and pops with a spark."""
    frames = []
    for i in range(n):
        s = math.sin(i / n * 4 * math.pi)
        w, h = 40 - s * 1.5, 38 + s * 1.5
        mood = dict(heat=0.3)
        blink = False
        if 2 <= i <= 4:
            mood['look'] = (-1.3, 0)
        elif 6 <= i <= 7:
            mood['look'] = (1.0, 0)
        elif 8 <= i <= 11:
            mood['look'] = (0, -1)
            mood['pre'] = [bubble([1.2, 2.0, 2.8, 3.5][i - 8])]
        elif i == 12:
            mood['eyes'] = 'wide'
            mood['heat'] = 0.6
            x, y = CX - 3, BASE_Y - h - 3
            mood['pre'] = [droplets((x - 5, y - 1, 1), (x + 5, y - 2, 1))]
            mood['post'] = [sparks((x, y - 3, 2, 0))]
        elif i == 13:
            mood['eyes'] = 'wide'
            x, y = CX - 3, BASE_Y - h - 3
            mood['post'] = [sparks((x + 1, y - 8, 1, 2), (x - 3, y - 6, 1, 3))]
        blink = i in (5, 14)
        frames.append(frame(w, h, blink=blink, phase=i / n, **mood))
    return frames


ANIMS = {
    'takeoff': anim_takeoff, 'run': anim_run, 'dash': anim_dash, 'hurt': anim_hurt, 'death': anim_death,
    'attack': anim_attack, 'shoot': anim_shoot, 'cast': anim_cast, 'levitate': anim_levitate,
    'glide': anim_glide, 'dive': anim_dive, 'slam': anim_slam, 'block': anim_block, 'charge': anim_charge,
    'fidget': anim_fidget,
}


def generate(out_dir):
    for name, fn in ANIMS.items():
        strip(fn()).save(f'{out_dir}/Player_{name}.png')


def sheet(out):
    """All player strips (base + extra) on a dark backdrop, x3, one row per animation."""
    from PIL import Image, ImageDraw, ImageFont
    rows = [('idle', slimes.anim_idle(PLAYER)), ('walk', slimes.anim_walk(PLAYER)),
            ('air', slimes.anim_air(PLAYER)), ('land', slimes.anim_land(PLAYER))]
    rows += [(name, fn()) for name, fn in ANIMS.items()]
    s = 3
    cell = FRAME * s
    label = 120
    cols = 8
    lines = sum((len(f) + cols - 1) // cols for _, f in rows)
    img = Image.new('RGBA', (label + cell * cols, cell * lines), (24, 10, 14, 255))
    d = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=20)
    y = 0
    for name, frames in rows:
        d.text((12, y + cell // 2 - 10), f'{name} ({len(frames)})', font=font, fill=(240, 232, 220))
        for i, f in enumerate(frames):
            r, c = divmod(i, cols)
            x0, y0 = label + c * cell, y + r * cell
            d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=(48, 18, 22) if (i + r) % 2 else (40, 14, 18))
            img.alpha_composite(f.resize((cell, cell), Image.NEAREST), (x0, y0))
        y += cell * ((len(frames) + cols - 1) // cols)
        d.line([(0, y - 1), (img.width, y - 1)], fill=(90, 40, 30), width=2)
    img.save(out)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2])
    else:
        print(__doc__)
