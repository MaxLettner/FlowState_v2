"""In-game montage of the Tar Hydra: the necks strung from HydraNeck / HydraScute / HydraFin pieces the way Java
would do it, head frames on the neck ends, over the tar lake with basalt pillars. Preview only.

    python tools/art/hydra.py ingame out.png
"""
import math
from PIL import Image, ImageDraw
from pixel import new
import hydra as h
from slimes import PLAYER, draw_slime

SW_, SH_ = 1920, 720
LEDGE = 640
LAKE_FROM, LAKE_TO = 360, 1560
BG = (26, 10, 12, 255)


def cubic(p0, p1, p2, p3, t):
    a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
    return a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]


def neck_curve(base, end, pitch, bend=0.0):
    ln = math.hypot(end[0] - base[0], end[1] - base[1])
    p1 = (base[0] + bend * ln * 0.3, base[1] - ln * 0.45)
    p2 = (end[0] - math.cos(pitch) * ln * 0.45, end[1] - math.sin(pitch) * ln * 0.45)
    pts = [cubic(base, p1, p2, end, i / 200) for i in range(201)]
    return pts


def resample(pts, step_fn):
    """Points along the curve spaced by step_fn(t)."""
    out = [(pts[0], 0.0)]
    acc = 0.0
    for i in range(1, len(pts)):
        acc += math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
        t = i / (len(pts) - 1)
        if acc >= step_fn(t):
            out.append((pts[i], t))
            acc = 0.0
    if out[-1][1] < 1:
        out.append((pts[-1], 1.0))
    return out


def tangent(pts, t):
    i = min(len(pts) - 2, int(t * (len(pts) - 1)))
    a, b = pts[max(0, i - 2)], pts[min(len(pts) - 1, i + 2)]
    ln = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
    return (b[0] - a[0]) / ln, (b[1] - a[1]) / ln


def paste_center(img, sprite, x, y):
    img.alpha_composite(sprite, (int(round(x - sprite.width / 2)), int(round(y - sprite.height / 2))))


def fin_rotated(fin, direction):
    """The fin (points up, base at (20, 58)) turned to point along direction, base at the canvas centre."""
    c = new(120, 120)
    c.alpha_composite(fin, (40, 2))
    ang = math.degrees(math.atan2(direction[0], -direction[1]))
    return c.rotate(-ang, resample=Image.NEAREST)


def draw_neck(img, S, base, end, pitch, r0, r1, bend=0.0, fins=True):
    pts = neck_curve(base, end, pitch, bend)
    rad = lambda t: r0 + (r1 - r0) * t
    if fins:
        for k, (p, t) in enumerate(resample(pts, lambda t: rad(t) * 0.8)[1:-1]):
            tx, ty = tangent(pts, t)
            back = (ty, -tx)
            f = fin_rotated(S['HydraFin'][k % 2], back)
            paste_center(img, f, p[0] + back[0] * rad(t) * 0.55, p[1] + back[1] * rad(t) * 0.55)
    discs = resample(pts, lambda t: max(4, rad(t) * 0.4))
    for name in ('HydraNeckEdge', 'HydraNeck'):            # all outlines first, then the fills
        for p, t in discs:
            i = max(0, min(20, round((rad(t) - 20) / 2)))
            paste_center(img, S[name][i], *p)
    for p, t in resample(pts, lambda t: rad(t) * 0.5)[1:-1]:
        tx, ty = tangent(pts, t)
        belly = (-ty, tx)
        i = max(0, min(10, round((rad(t) * 0.34 - 8) / 2)))
        paste_center(img, S['HydraScute'][i], p[0] + belly[0] * rad(t) * 0.55, p[1] + belly[1] * rad(t) * 0.55)
    return pts


def head_at(img, frame, x, y, kind):
    img.alpha_composite(frame, (int(x - h.neck_x(kind)), int(y - h.AY)))


def player(img, x, ground, flip=True):
    s = draw_slime(PLAYER, 40, 38)
    if flip:
        s = s.transpose(Image.FLIP_LEFT_RIGHT)
    s = s.resize((128, 128), Image.NEAREST)
    img.alpha_composite(s, (int(x - 64), int(ground - 124)))


def ledges(img, S, top):
    for x0 in list(range(-40, LAKE_FROM, 112)) + list(range(LAKE_TO, SW_, 112)):
        img.alpha_composite(S['BasaltPillar_top'][0], (x0, top))
        for y in range(top + 40, SH_, 32):
            img.alpha_composite(S['BasaltPillar_shaft'][0], (x0, y))


def pillar(img, S, x, top, bottom=SH_):
    img.alpha_composite(S['BasaltPillar_top'][0], (x - 56, top))
    for y in range(top + 40, bottom, 32):
        img.alpha_composite(S['BasaltPillar_shaft'][0], (x - 56, y))


def lake(img, S, surface, x0=0, x1=SW_, frame=0):
    tile = S['TarLake'][frame]
    for x in range(x0, x1, 128):
        piece = tile.crop((0, 0, min(128, x1 - x), 24))
        img.alpha_composite(piece, (x, surface))
    d = ImageDraw.Draw(img)
    d.rectangle((x0, surface + 24, x1 - 1, SH_), fill=h.TAR_SKIN[3])


def phase1(S):
    img = Image.new('RGBA', (SW_, SH_), BG)
    ledges(img, S, LEDGE)
    surface = 660
    img.alpha_composite(S['HydraBody_idle'][0], (960 - 280, surface + 30 - 146))
    heads = [
        ('Spitter', 'rear', 5, (800, surface + 30), (660, 300), -0.45, -0.6),
        ('Biter', 'bite', 0, (960, surface + 30), (1110, 360), -0.5, 0.2),
        ('Lure', 'sweep', 3, (1110, surface + 30), (1210, 500), 0.08, 0.6),
    ]
    for kind, anim, i, base, end, pitch, bend in heads:
        info = h.HEADS[kind]
        draw_neck(img, S, base, end, pitch, info['base'], info['neck'], bend)
        head_at(img, S[f'Hydra{kind}_{anim}'][i], *end, kind)
    for x, top in ((520, 500), (1380, 520)):
        pillar(img, S, x, top)
    lake(img, S, surface, LAKE_FROM, LAKE_TO)
    for k in range(4):                                     # burning tar on the right pillar
        img.alpha_composite(S['TarFirePool'][k % 4], (1330 + k * 32 - 32, 520 - 8))
    paste_center(img, S['TarGlob'][1], 980, 230)
    paste_center(img, S['TarGlob'][2], 1110, 200)
    player(img, 1420, 524)
    return img


def phase3(S):
    img = Image.new('RGBA', (SW_, SH_), BG)
    surface = 560                                          # the lake has risen over the ledges
    img.alpha_composite(S['HydraBody_idle'][2], (960 - 280, surface + 30 - 146))
    for kind, state, base, end in (('Spitter', 'seared', (790, surface + 30), (740, 470)),
                                   ('Biter', 'frozen', (1150, surface + 30), (1200, 450)),
                                   ('Lure', 'rotted', (1290, surface + 30), (1330, 500))):
        info = h.HEADS[kind]
        draw_neck(img, S, base, end, -math.pi / 2, info['base'], info['neck'], 0.0, fins=False)
        st = S[f'Hydra{kind}Stump_{state}'][0]
        img.alpha_composite(st, (int(end[0] - h.SAX), int(end[1] - h.SAY)))
    info = h.HEADS['Crowned']
    draw_neck(img, S, (960, surface + 30), (900, 250), -0.6, info['base'], info['neck'], -0.4)
    head_at(img, S['HydraCrowned_roar'][4], 900, 250, 'Crowned')
    pillar(img, S, 520, 430)
    pillar(img, S, 1560, 470)
    lake(img, S, surface)
    img.alpha_composite(S['TarWave'][1], (260, surface + 6 - 118))
    player(img, 1560, 474)
    return img


def ingame(path):
    # the strips are stored at half size (shown at x2 in game): blown up here so the montage is in world px
    S = {k: [f.resize((f.width * 2, f.height * 2), Image.NEAREST) for f in v] for k, v in h.all_strips().items()}
    a, b = phase1(S), phase3(S)
    out = Image.new('RGBA', (SW_, SH_ * 2 + 40), (14, 6, 8, 255))
    d = ImageDraw.Draw(out)
    d.text((12, 4), 'phase 1: spitter rearing, biter lunging, lure sweeping (Java strings the necks from discs, scutes and fins)', fill=(240, 210, 190, 255))
    out.alpha_composite(a, (0, 20))
    d.text((12, SH_ + 24), 'phase 3: lake risen, stumps seared / frozen / rotted, the Crowned Head roars, a tar wave rolls in', fill=(240, 210, 190, 255))
    out.alpha_composite(b, (0, SH_ + 40))
    out.save(path)
