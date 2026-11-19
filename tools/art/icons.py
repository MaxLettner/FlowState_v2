"""Skill icons for the skill tree and the hotbar: one 24x24 icon per SkillType, intended display x2.

File name: Icon_<SKILL_TYPE>.png (the Java enum constant name), e.g. Icon_FIRE_BALL.png.
Each icon is a framed tile tinted by branch (melee red, ranged green, magic purple) with a subject on top.
Weapons are re-rendered small with the weapons.py part functions (a smaller canvas keeps them bold);
bows, crossbows and spell projectiles reuse their game sprites; the rest is drawn here.
"""
import math
from PIL import Image

from pixel import *
from materials import *
import weapons
import ranged
import projectiles
import effects

S = 24
INNER = 20  # subject box

BRANCH_BG = {
    'MELEE': ramp('#7a2a33', '#5c1d26', '#43141c', '#2c0d13', '#1a070b'),
    'RANGED': ramp('#3f6b33', '#2f5226', '#223c1c', '#162812', '#0c1609'),
    'MAGIC': ramp('#5a3a8f', '#44296f', '#321e53', '#211338', '#130a21'),
}
FRAME = ramp('#e8d9b0', '#bfa978', '#8a774f', '#5c4e33', '#382f1e')
MANA_BLUE = ramp('#d6ecff', '#7fc0ff', '#3f86e6', '#2552a8', '#132b63')
BLOOD = ramp('#ffb3b3', '#ff4d5e', '#c41e3a', '#7d0f24', '#4a0612')
SPARKLE = (255, 255, 230, 255)


# ----- tile -----
def tile(branch):
    """Rounded square with a top-left lit gradient and a 1px bevelled frame."""
    img = new(S, S)
    px = img.load()
    bg = BRANCH_BG[branch]
    for y in range(S):
        for x in range(S):
            corner = (x in (0, S - 1)) and (y in (0, S - 1))
            if corner:
                continue
            edge = x in (0, S - 1) or y in (0, S - 1)
            if edge:
                lit = x == 0 or y == 0
                px[x, y] = FRAME[1] if lit else FRAME[3]
                continue
            inner_edge = x in (1, S - 2) or y in (1, S - 2)
            if inner_edge:
                px[x, y] = bg[4]
                continue
            # radial-ish light from the top-left
            v = math.hypot(x - 6, y - 6) / (S * 1.15)
            px[x, y] = bg[dither_index(min(1, 0.15 + v), x, y, 4)]
    return img


def finish(branch, subject, dx=0, dy=0):
    img = tile(branch)
    w, h = subject.size
    img.alpha_composite(subject, ((S - w) // 2 + dx, (S - h) // 2 + dy))
    return img


# ----- subject helpers -----
def crop(img):
    box = img.getbbox()
    return img.crop(box) if box else img


def fit(img, size=INNER):
    """Crops to content and shrinks (never grows) to fit size x size.
    Shrinking pixel art: area-average the colours, threshold the alpha, then re-outline."""
    img = crop(img)
    w, h = img.size
    k = min(size / w, size / h, 1.0)
    if k >= 1.0:
        n = int(min(size / w, size / h))
        if n >= 2:  # tiny sprite: grow by an integer factor so it stays crisp
            return img.resize((w * n, h * n), Image.Resampling.NEAREST)
        return img
    # drop the old outline first so the shrink doesn't smear it into the colours
    core = strip_outline(img)
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    small = core.resize((nw, nh), Image.Resampling.BOX)
    px = small.load()
    for y in range(nh):
        for x in range(nw):
            r, g, b, a = px[x, y]
            if a < 90:
                px[x, y] = (0, 0, 0, 0)
            else:
                f = 255 / a  # un-premultiply the average towards transparent
                px[x, y] = (min(255, int(r * f)) if a < 255 else r,
                            min(255, int(g * f)) if a < 255 else g,
                            min(255, int(b * f)) if a < 255 else b, 255)
    padded = new(nw + 2, nh + 2)
    padded.paste(small, (1, 1), small)
    return crop(outline(padded))


def strip_outline(img):
    out = img.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if px[x, y][:3] == OUTLINE[:3]:
                px[x, y] = (0, 0, 0, 0)
    return out


def frame(strip_img, count, index=0):
    w = strip_img.width // count
    return strip_img.crop((index * w, 0, (index + 1) * w, strip_img.height))


def trident_icon(shaft, head, gem=None, frost=False, heavy=False):
    """Upright trident: the prongs stay readable at icon size (the diagonal sprite merges them)."""
    img = new(15, 22)
    px = img.load()
    w, h = img.size
    cx = 7
    sw = 2 if heavy else 1
    for y in range(8, 22):  # shaft
        for x in range(cx - sw // 2 - (1 if heavy else 0), cx + sw // 2 + 1):
            px[x, y] = shaft[1] if x <= cx - (1 if heavy else 0) else shaft[3]
        if not heavy and y % 4 == 0:
            px[cx, y] = shaft[2]
    for x in range(2, 13):  # crossbar
        px[x, 8] = head[1]
        px[x, 9] = head[3]
    for px_x in (2, 7, 12):  # prongs
        top = 1 if px_x == 7 else 3
        for y in range(top, 8):
            px[px_x, y] = head[1]
            if px_x != 7 or heavy:
                put(px, w, h, px_x + 1, y, head[3])
        put(px, w, h, px_x, top - 1, head[0])
    for side in (-1, 1):  # barbs
        put(px, w, h, cx + side * 6, 4, head[2])
        put(px, w, h, cx + side * 6 + side, 5, head[2])
    if gem:
        px[cx, 9] = gem[1]
        px[cx, 10] = gem[2]
    if frost:
        for x, y in ((5, 13), (8, 16), (6, 19), (9, 11)):
            put(px, w, h, x, y, ICE[0])
    return outline(img)


def small_weapon(fn, *args, canvas=24, **kw):
    """Re-renders a diagonal weapon on a small canvas (bold, icon-sized)."""
    original = weapons.canvas_for_length
    weapons.canvas_for_length = lambda length: canvas
    try:
        return crop(fn(*args, **kw))
    finally:
        weapons.canvas_for_length = original


def compose(*layers):
    """Pastes (image, x, y) layers onto an S x S canvas (subject space)."""
    img = new(S, S)
    for layer, x, y in layers:
        img.alpha_composite(layer, (x, y))
    return crop(img)


def rotate(img, deg):
    return crop(img.rotate(deg, resample=Image.Resampling.NEAREST, expand=True))


# ----- drawn subjects -----
def drop(mat, W=12, H=16):
    """A shaded droplet (mana / blood)."""
    img = new(W, H)
    px = img.load()
    cx, cy, r = W / 2, H - W / 2, W / 2 - 0.5
    for y in range(H):
        for x in range(W):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            inside = math.hypot(dx, dy) <= r
            if not inside and y + 0.5 < cy:
                # taper to the tip
                half = r * (y + 0.5) / cy
                inside = abs(dx) <= half * (1 - ((cy - y - 0.5) / cy) ** 2) ** 0.5 * 1.0 + 0.2 and abs(dx) <= half
            if inside:
                lit = (-dx - dy * 0.6) / (r + 1)
                k = 0 if lit > 0.55 else 1 if lit > 0.1 else 2 if lit > -0.35 else 3
                px[x, y] = mat[k]
    px[int(cx - r * 0.45), int(cy - r * 0.35)] = mat[0]
    return outline(img)


def heart_drop():
    d = drop(BLOOD)
    return d


def arcane_orb():
    """Arcane: a glowing violet orb with an orbiting ring."""
    img = new(20, 20)
    px = img.load()
    c = 9.5
    for y in range(20):
        for x in range(20):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            if d <= 6.2:
                lit = (-dx - dy) / 9
                k = 0 if lit > 0.45 else 1 if lit > 0.05 else 2 if lit > -0.4 else 3
                px[x, y] = PURPLE[k]
            # tilted ring
            rx, ry = dx * 0.94 + dy * 0.34, -dx * 0.34 + dy * 0.94
            e = (rx / 9.2) ** 2 + (ry / 3.4) ** 2
            if 0.82 <= e <= 1.18 and not (d <= 6.2 and ry < 0):
                px[x, y] = PURPLE[0] if rx < 0 else PURPLE[1]
    px[7, 6] = (255, 255, 255, 255)
    px[6, 7] = PURPLE[0]
    img = outline(img)
    sparkle(img, 17, 3, big=False)
    return img


def lightning():
    """Elemental (Spark): a jagged lightning bolt."""
    pts = [(13, 0), (5, 10), (10, 10), (4, 21), (16, 8), (11, 8), (17, 0)]
    img = Image.new('RGBA', (20, 22), (0, 0, 0, 0))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.polygon(pts, fill=GOLD[1])
    d.line([(13, 1), (7, 9)], fill=GOLD[0])
    d.line([(10, 11), (6, 18)], fill=GOLD[0])
    d.line([(15, 9), (9, 15)], fill=GOLD[3])
    return outline(img)


def dumbbell():
    """Strength: an iron dumbbell, tilted."""
    img = new(22, 12)
    px = img.load()
    w, h = img.size
    for x in range(5, 17):  # bar
        for y in (5, 6):
            px[x, y] = STEEL[1] if y == 5 else STEEL[3]
    for x0 in (1, 16):      # weights
        for x in range(x0, x0 + 5):
            for y in range(0, 12):
                k = 1 if x == x0 else 2 if x < x0 + 3 else 3
                if y in (0, 11):
                    k = min(4, k + 1)
                px[x, y] = BLACK_IRON[k]
        for y in range(2, 10):
            px[x0 + 1, y] = BLACK_IRON[0]
    return rotate(outline(img), 30)


def target():
    """Dexterity: bullseye with an arrow in it."""
    img = new(20, 20)
    px = img.load()
    c = 9.5
    rings = [(9.3, CLOTH_WHITE[1]), (7.4, CRIMSON[1]), (5.4, CLOTH_WHITE[1]), (3.4, CRIMSON[1]), (1.6, GOLD[1])]
    for y in range(20):
        for x in range(20):
            d = math.hypot(x + 0.5 - c - 0.5, y + 0.5 - c - 0.5)
            for r, col in rings:
                if d <= r:
                    shade_col = col
                    if x + y > 22:
                        shade_col = mix(col, (0, 0, 0, 255), 0.25)
                    px[x, y] = shade_col
    img = outline(img)
    arrow = projectiles.arrow(14, WOOD, STEEL, CRIMSON)
    arrow = rotate(arrow, 35)
    out = new(24, 24)
    out.alpha_composite(img, (0, 4))
    out.alpha_composite(arrow, (9, 0))
    return crop(out)


def sparkle(img, x, y, big=True):
    px = img.load()
    w, h = img.size
    pts = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]
    if big:
        pts += [(2, 0), (-2, 0), (0, 2), (0, -2)]
    for dx, dy in pts:
        put(px, w, h, x + dx, y + dy, SPARKLE if (dx, dy) == (0, 0) or abs(dx) + abs(dy) == 1 else GOLD[1])


def wings():
    """Levitation: a white feathered wing pair with an up arrow."""
    img = new(22, 18)
    px = img.load()
    w, h = img.size
    for side in (-1, 1):
        for i in range(4):
            # feathers fan out and down from the centre
            length = 9 - i * 1.5
            ang = math.radians(-20 - i * 18)
            x0, y0 = 11 + side * 2, 6 + i
            for t in range(int(length * 2)):
                tt = t / 2
                x = x0 + side * math.cos(ang) * tt
                y = y0 - math.sin(ang) * tt * 0.6
                col = CLOTH_WHITE[0] if i < 2 else CLOTH_WHITE[1]
                put(px, w, h, int(round(x)), int(round(y)), col)
                put(px, w, h, int(round(x)), int(round(y)) + 1, CLOTH_WHITE[2])
    # up arrow in the middle
    for y in range(2, 16):
        put(px, w, h, 10, y, MANA_BLUE[1])
        put(px, w, h, 11, y, MANA_BLUE[2])
    for i in range(4):
        for x in range(10 - i, 12 + i):
            put(px, w, h, x, 2 + i, MANA_BLUE[1] if x < 11 else MANA_BLUE[2])
    return crop(outline(img))


def aura(img, colors):
    """Puts a 1px glow (cycling through colors along the diagonal) around the subject."""
    w, h = img.size
    out = new(w + 2, h + 2)
    src = img.load()
    px = out.load()
    for y in range(h + 2):
        for x in range(w + 2):
            near = any(0 <= x - 1 + dx < w and 0 <= y - 1 + dy < h and src[x - 1 + dx, y - 1 + dy][3] > 0
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near:
                col = colors[((x + y) // 4) % len(colors)]
                px[x, y] = (col[0], col[1], col[2], 210)
    out.alpha_composite(img, (1, 1))
    return out


def whetstone_sword():
    """Sharpen: the iron sword with a golden glow and edge sparkles."""
    img = aura(small_weapon(weapons.iron_sword, canvas=22), [GOLD[1]])
    sparkle(img, img.width - 6, 5)
    sparkle(img, 5, img.height - 10, big=False)
    return img


def imbue_sword():
    """Imbue: the iron sword wrapped in fire, ice and poison."""
    fire = ramp('#fff3a0', '#ffb347', '#ff6b2d', '#c43a12', '#7a1e08')
    return aura(small_weapon(weapons.iron_sword, canvas=22), [fire[2], ICE[2], ranged.POISON[2]])


def piercing():
    """Piercing: an arrow driven through a ring of steel plates."""
    plate = new(6, 16)
    ppx = plate.load()
    for y in range(16):
        for x in range(6):
            ppx[x, y] = STEEL[1 if x < 2 else 2 if x < 4 else 3]
    plate = outline(plate)
    arrow = projectiles.arrow(20, WOOD, STEEL, CRIMSON, head_len=5)
    img = new(24, 20)
    img.alpha_composite(plate, (5, 2))
    img.alpha_composite(plate, (13, 2))
    a = crop(arrow)
    img.alpha_composite(a, (0, 10 - a.height // 2))
    # the plates are in front of the shaft only where the arrow went through: redraw the plate rims
    return crop(img)


def shield_bubble():
    return fit(effects.magic_shield(S=52)[0], 20)


def mana_potion():
    """Mana stat: blue droplet with a sparkle."""
    d = drop(MANA_BLUE)
    img = new(d.width + 4, d.height + 2)
    img.alpha_composite(d, (0, 2))
    sparkle(img, d.width + 1, 3, big=False)
    return img


def three_darts():
    dart = crop(projectiles.poison_dart())
    img = new(24, 20)
    for i, deg in enumerate((22, 0, -22)):
        r = rotate(dart, deg)
        img.alpha_composite(r, (2 + i, 2 + i * 6))
    return crop(img)


def two_bolts_crossbow():
    return fit(ranged.dual_crossbow(res=1)[0])


def flail_spin():
    return small_weapon(weapons.morningstar)


# ----- the icon table -----
def icons():
    m, r, g = 'MELEE', 'RANGED', 'MAGIC'
    bow = lambda fn: fit(fn(res=1)[2])     # full-draw frame, drawn at icon resolution
    xbow = lambda fn: fit(fn(res=1)[0])    # loaded frame, drawn at icon resolution
    glove = lambda fn: rotate(fn(), 0)
    trident = trident_icon
    sprite = lambda frames, i=0, size=INNER: fit(frames[i], size)
    fire = projectiles.fireball()
    return {
        'RUSTY_SWORD': (m, small_weapon(weapons.rusty_sword)),
        'STRENGTH': (m, dumbbell()),
        'DEXTERITY': (r, target()),
        'MANA': (g, mana_potion()),

        'SWORDS': (m, small_weapon(weapons.iron_sword)),
        'SHORTSWORD': (m, small_weapon(weapons.short_sword, canvas=20)),
        'DUAL_WIELDING': (m, compose((small_weapon(weapons.katana, canvas=20), 0, 2),
                                     (rotate(small_weapon(weapons.katana, canvas=20), 90), 2, 2))),
        'ZWEIHANDER': (m, small_weapon(weapons.zweihander)),

        'FISTICUFFS': (m, glove(projectiles.hand_wraps)),
        'LEATHER_GLOVES': (m, glove(projectiles.leather_glove)),
        'METAL_GLOVES': (m, compose((projectiles.metal_guard(), 2, 0), (projectiles.metal_glove(), 7, 8))),
        'SPIKE_GLOVES': (m, glove(projectiles.spike_glove)),

        'BLUNT': (m, small_weapon(weapons.club)),
        'HAMMER': (m, small_weapon(weapons.big_hammer)),
        'MORNINGSTAR': (m, flail_spin()),
        'SPRING_HAMMER': (m, small_weapon(weapons.spring_hammer)),

        'BOW': (r, bow(ranged.basic_bow)),
        'SHORTBOW': (r, bow(ranged.short_bow)),
        'BONE_BOW': (r, bow(ranged.bone_bow)),
        'WAR_BOW': (r, bow(ranged.war_bow)),

        'CROSSBOW': (r, xbow(ranged.basic_crossbow)),
        'DUAL_CROSSBOW': (r, two_bolts_crossbow()),
        'HEAVY_CROSSBOW': (r, xbow(ranged.heavy_crossbow)),
        'POISON_CROSSBOW': (r, xbow(ranged.poison_crossbow)),

        'TRIDENT': (r, trident(WOOD, STEEL)),
        'RECALL_TRIDENT': (r, trident(TEAL, GOLD, gem=TEAL)),
        'ICE_TRIDENT': (r, trident(ICE, ICE, frost=True)),
        'HEAVY_TRIDENT': (r, trident(BLACK_IRON, DARK_STEEL, heavy=True, gem=CRIMSON)),

        'ARCANE': (g, arcane_orb()),
        'MAGIC_MISSILE': (g, sprite(projectiles.magic_missile())),
        'MANA_SHIELD': (g, shield_bubble()),
        'LEVITATION': (g, wings()),

        'ELEMENTAL': (g, lightning()),
        'FIRE_BALL': (g, sprite(fire)),
        'ICICLE': (g, rotate(fit(projectiles.icicle()), 35)),
        'POISON_DARTS': (g, three_darts()),

        'ENCHANTING': (g, whetstone_sword()),
        'IMBUE': (g, imbue_sword()),
        'LIFE_STEAL': (g, heart_drop()),
        'PIERCING': (g, piercing()),
    }


def build():
    out = {}
    for name, (branch, subject) in icons().items():
        subject = crop(subject)
        limit = INNER + 2 if name in ('ENCHANTING', 'IMBUE') else INNER  # glow may touch the frame
        if subject.width > limit or subject.height > limit:
            subject = fit(subject, limit)
        out[name] = finish(branch, subject)
    return out


def generate(out_dir):
    items = build()
    for name, img in items.items():
        img.save(f'{out_dir}/Icon_{name}.png')
    return items


def preview(path, scale=4, cols=8):
    items = build()
    names = list(items)
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * (S + 4) * scale, rows * (S + 4) * scale), (30, 30, 34, 255))
    for i, n in enumerate(names):
        big = items[n].resize((S * scale, S * scale), Image.Resampling.NEAREST)
        sheet.alpha_composite(big, ((i % cols) * (S + 4) * scale + 2 * scale, (i // cols) * (S + 4) * scale + 2 * scale))
    sheet.save(path)


if __name__ == '__main__':
    import sys
    preview(sys.argv[1] if len(sys.argv) > 1 else 'icons_preview.png')
