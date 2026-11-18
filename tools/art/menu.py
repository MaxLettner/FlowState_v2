"""Game menu art (Menu/GameMenu.java), carved into the same ancient rune wall as the skill tree.

  MenuBg            480x270 opaque, shown x4 (1920x1080): rune wall, a lintel with the molten FLOWSTATE title, a big
                    carved seal (rune circle) in the middle that holds the buttons, two wall torches.
  MenuGlow          480x270, only the molten pixels of MenuBg: blurred and added on top in Java (pulsing).
  MenuFlame         6 frames 12x20 torch flame; its bottom centre sits on a torch bowl (TORCHES), shown x4.
  MenuButton_<ID>   2 frames (normal / hover), 104x15 (main) or 52x15 (small), shown x4.
  MenuControls      the controls tablet (key caps and labels), TABLET_W x TABLET_H plus a 3 px shadow, shown x4.
  MenuText_<ID>     two carved lines for the confirm prompts (transparent), shown x4.

Java places everything by the art coordinates below times 4; keep GameMenu in sync.
`python tools/art/menu.py out.png [controls|confirm]` renders a full-screen mock.
"""
import math
import random
import sys
from PIL import Image, ImageChops, ImageFilter

from pixel import *
from effects import Noise
from pixfont import text_cells, text_width
import skilltree as st
from skilltree import W, H, WALL, MORTAR, EMBER, LIGHT, groove_stamp, carve_glyph, rune_glyphs

SCALE = 4
CENTER = (240, 146)          # seal centre (screen 960, 584)
R_EDGE = 95                  # seal slab radius
R_OUTER, R_INNER = 91, 77.5  # molten / dormant ring grooves
R_DISC = 75                  # recessed inner disc that holds the buttons
LINTEL = (140, 340, 3, 37)
TORCHES = ((62, 128), (418, 128))   # bowl top centre
FLAME_W, FLAME_H, FLAME_FRAMES = 12, 20, 6

BUTTON_H = 15
MAIN_W, SMALL_W = 104, 52
BUTTONS = {  # id: (label, width)
    'NEW_GAME': ('START NEW GAME', MAIN_W),
    'SAVE': ('SAVE GAME', MAIN_W),
    'LOAD': ('LOAD GAME', MAIN_W),
    'CONTROLS': ('CONTROLS', MAIN_W),
    'EXIT': ('EXIT GAME', MAIN_W),
    'SOON': ('COMING SOON', MAIN_W),
    'BACK': ('BACK', SMALL_W),
    'START': ('START', SMALL_W),
    'QUIT': ('EXIT', SMALL_W),
    'CANCEL': ('CANCEL', SMALL_W),
}
MAIN_ORDER = ('NEW_GAME', 'SAVE', 'LOAD', 'CONTROLS', 'EXIT')
BUTTON_TOP, BUTTON_STEP = 101, 19   # main column, centred on CENTER

PROMPTS = {
    'NEW_GAME': ('START A NEW GAME?', 'PROGRESS WILL BE LOST'),
    'EXIT': ('EXIT THE GAME?', 'PROGRESS WILL BE LOST'),
}

TABLET_W, TABLET_H = 220, 212
CONTROLS = [
    (('A', 'D'), 'MOVE LEFT / RIGHT'),
    (('W',), 'JUMP / FLY UP'),
    (('S',), 'SINK WHILE LEVITATING'),
    (('LMB',), 'ATTACK'),
    (('RMB',), 'WEAPON SPECIAL'),
    (('1', '-', '4'), 'SELECT WEAPON / USE SKILL'),
    (('I',), 'SKILL TREE'),
    (('ESC',), 'MENU / BACK'),
    (('F11',), 'FULLSCREEN'),
    None,  # section: in the skill tree
    (('LMB',), 'LEARN OR EQUIP A SKILL'),
    (('1', '-', '4'), 'ASSIGN THE HOVERED SKILL'),
]

IRON = ramp('#77717d', '#55505c', '#3a3641', '#25222b', '#16141a')
PARCHMENT = ramp('#eadcb6', '#cbb88d', '#9c8a66')
SHADOW = (11, 10, 14, 150)
MOLTEN = [(0.9, EMBER[3]), (1.6, EMBER[2]), (2.3, EMBER[1])]
DORMANT = [(1.0, EMBER[5]), (2.0, EMBER[4])]


def clamp(v):
    return max(0.0, min(1.0, v))


def circle_pts(r, step=0.4):
    n = int(2 * math.pi * r / step)
    return [(CENTER[0] + r * math.cos(2 * math.pi * i / n), CENTER[1] + r * math.sin(2 * math.pi * i / n))
            for i in range(n + 1)]


# ---------------------------------------------------------------- the seal
def seal(img, rng):
    px = img.load()
    nz, nz2 = Noise(111, n=16), Noise(112, n=16)
    cx, cy = CENTER
    for y in range(cy - R_EDGE - 4, cy + R_EDGE + 5):
        for x in range(cx - R_EDGE - 4, cx + R_EDGE + 5):
            if not (0 <= x < W and 0 <= y < H):
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > R_EDGE + 3:
                continue
            nx, ny = dx / max(d, 0.01), dy / max(d, 0.01)
            lit = -(nx * LIGHT[0] + ny * LIGHT[1])  # 1 = this side of the circle faces the light
            if d > R_EDGE:  # the slab is raised: shadow cast to the lower right
                k = 0.25 + 0.45 * clamp(-lit + 0.3)
                px[x, y] = mix(px[x, y], MORTAR, k * (1 - (d - R_EDGE) / 3.2))
                continue
            if d > R_EDGE - 1:
                px[x, y] = MORTAR
                continue
            v = 0.4 + 0.14 * (nz(x / 6, y / 6) - 0.5) + 0.06 * (nz2(x / 2, y / 2) - 0.5)
            if d > R_EDGE - 2.2:  # rim bevel
                v -= 0.3 * lit
            elif d > R_EDGE - 3.2:
                v -= 0.1 * lit
            elif 80 <= d <= 88.5:  # smooth rune band
                v = 0.3 + 0.08 * (nz(x / 6, y / 6) - 0.5)
                if d < 81:
                    v += 0.12 * lit
                elif d > 87.5:
                    v -= 0.12 * lit
            elif d < R_DISC:  # recessed disc: darker, its inner wall shades the top left
                v = 0.6 + 0.1 * (nz(x / 8, y / 8) - 0.5)
                if d > R_DISC - 5:
                    v += 0.22 * lit * (d - (R_DISC - 5)) / 5
                v -= 0.08 * max(0.0, 1 - d / 40)
            px[x, y] = WALL[dither_index(clamp(v), x, y, len(WALL), band=0.1)]

    # rune band: upright glyphs around the circle, some of them glowing
    glyphs = rune_glyphs(random.Random(5), 20)
    n = 34
    for i in range(n):
        a = 2 * math.pi * i / n - math.pi / 2
        gx, gy = cx + 84.2 * math.cos(a), cy + 84.2 * math.sin(a)
        fill = EMBER[3] if i % 4 == 1 else EMBER[4] if i % 4 == 3 else WALL[7]
        carve_glyph(px, rng.choice(glyphs), int(round(gx - 2.5)), int(round(gy - 2.5)), fill, lip=WALL[1])

    groove_stamp(px, W, H, circle_pts(R_OUTER), lambda t: 2.5)
    groove_stamp(px, W, H, circle_pts(R_INNER), lambda t: 2.0)
    groove_stamp(px, W, H, circle_pts(R_OUTER), lambda t: 2.5, fill=MOLTEN, lip=False, walls=False)
    groove_stamp(px, W, H, circle_pts(R_INNER), lambda t: 2.0, fill=DORMANT, lip=False, walls=False)

    # faint spokes in the disc, visible beside the button column
    for a in (0, math.pi):
        pts = [(cx + r * math.cos(a), cy + r * math.sin(a)) for r in [58 + i * 0.4 for i in range(41)]]
        groove_stamp(px, W, H, pts, lambda t: 1.5 - t * 0.5, lip_col=WALL[4])
        groove_stamp(px, W, H, pts, lambda t: 1.5 - t * 0.5, fill=[(0.6, EMBER[5]), (1.1, EMBER[4])], lip=False,
                     walls=False)

    # bosses where the seal meets the lintel, the roots and the torch grooves
    for a in (-math.pi / 2, math.pi / 2, 0, math.pi):
        boss(px, cx + R_OUTER * math.cos(a), cy + R_OUTER * math.sin(a), 5)


def boss(px, bx, by, r):
    """Raised round stud with a molten core."""
    for y in range(int(by - r) - 2, int(by + r) + 3):
        for x in range(int(bx - r) - 2, int(bx + r) + 3):
            dx, dy = x + 0.5 - bx, y + 0.5 - by
            d = math.hypot(dx, dy)
            if d > r + 1.5:
                continue
            if d > r:
                if dx + dy > 0:
                    put(px, W, H, x, y, mix(px[x, y], MORTAR, 0.55))
                continue
            if d > r - 1:
                put(px, W, H, x, y, MORTAR)
                continue
            lit = -(dx * LIGHT[0] + dy * LIGHT[1]) / max(d, 0.01)
            v = 0.34 - 0.25 * lit * (d / r)
            c = WALL[dither_index(clamp(v), x, y, len(WALL), band=0.1)]
            if d < r * 0.45:
                c = EMBER[1] if d < r * 0.2 else EMBER[2]
            elif d < r * 0.45 + 1:
                c = EMBER[4]
            put(px, W, H, x, y, c)


# ---------------------------------------------------------------- torches & grooves to them
def torch_grooves(img):
    px = img.load()
    cy = CENTER[1]
    paths = []
    for tx, ty in TORCHES:
        s = 1 if tx < CENTER[0] else -1
        x0 = CENTER[0] - s * (R_EDGE - 2)
        x1 = tx + s * 4
        paths.append(st.bezier((x0, cy), (x0 - s * 30, cy), (x1 + s * 26, ty + 22), (x1, ty + 16)))
    for pts in paths:
        groove_stamp(px, W, H, pts, lambda t: 2.0, lip_col=WALL[3])
    for pts in paths:
        groove_stamp(px, W, H, pts, lambda t: 2.0, fill=DORMANT, lip=False, walls=False)


def torches(img):
    px = img.load()
    for tx, ty in TORCHES:
        shapes = []  # (x, y, colour) of the sconce, shadow drawn first
        for y in range(ty + 7, ty + 24):  # wall plate
            for x in range(tx - 3, tx + 4):
                if (y in (ty + 7, ty + 23)) and abs(x - tx) == 3:
                    continue
                edge = abs(x - tx) == 3 or y in (ty + 7, ty + 23)
                c = IRON[1] if (x == tx - 3 or y == ty + 7) else IRON[3] if edge else IRON[2]
                shapes.append((x, y, c))
        for y in (ty + 10, ty + 20):  # rivets
            shapes.append((tx, y, IRON[0]))
            shapes.append((tx + 1, y + 1, IRON[4]))
        for y in range(ty + 3, ty + 8):  # neck
            for x in range(tx - 1, tx + 2):
                shapes.append((x, y, IRON[1] if x == tx - 1 else IRON[3] if x == tx + 1 else IRON[2]))
        for y in range(ty, ty + 4):  # bowl
            half = 6 - (y - ty) * 1.2
            for x in range(int(tx - half), int(tx + half) + 1):
                c = IRON[0] if y == ty else IRON[3] if x > tx + half - 1.5 or y == ty + 3 else IRON[2]
                if y == ty + 1 and x < tx:
                    c = IRON[1]
                shapes.append((x, y, c))
        for x, y, c in shapes:
            if not any((x2, y2) == (x + 2, y + 2) for x2, y2, _ in shapes):
                put(px, W, H, x + 2, y + 2, mix(px[x + 2, y + 2], MORTAR, 0.55))
        for x, y, c in shapes:
            put(px, W, H, x, y, c)
        for x in range(tx - 4, tx + 5):  # embers in the bowl
            put(px, W, H, x, ty - 1, EMBER[3] if (x + tx) % 3 else EMBER[2])
        for x in range(tx - 2, tx + 3):
            put(px, W, H, x, ty - 2, EMBER[2])


def connectors(img):
    """Molten grooves from the lintel down to the seal and from the seal into the bottom frame."""
    px = img.load()
    cx, cy = CENTER
    top = [(cx, LINTEL[3] + i * 0.4) for i in range(int((cy - R_EDGE - LINTEL[3] + 3) / 0.4))]
    bottom = [(cx, cy + R_EDGE - 3 + i * 0.4) for i in range(int((H - st.BORDER - cy - R_EDGE + 5) / 0.4))]
    for pts in (top, bottom):
        groove_stamp(px, W, H, pts, lambda t: 2.5)
    for pts in (top, bottom):
        groove_stamp(px, W, H, pts, lambda t: 2.5, fill=MOLTEN, lip=False, walls=False)


def menu_bg():
    cx, cy = CENTER

    def avoid(sx, sy):
        return (math.hypot(sx - cx, sy - cy) < R_EDGE + 16
                or any(abs(sx - tx) < 26 and -30 < sy - ty < 34 for tx, ty in TORCHES)
                or (sy < 52 and LINTEL[0] - 10 < sx < LINTEL[1] + 10))

    def finish(img, rng):
        st.border(img, rng, skip=(LINTEL[0] - 8, LINTEL[1] + 8))
        torch_grooves(img)
        connectors(img)
        seal(img, rng)
        torches(img)
        boss(img.load(), CENTER[0], H - 5, 4)
        st.lintel(img, 'FLOWSTATE', LINTEL, scale=3, text_dy=7)

    warm = tuple((tx, ty - 4, 78, 70) for tx, ty in TORCHES) + ((cx, cy, 150, 128),)
    return st.stone_bg(seed=19, warm=warm, canopy=(cx, cy, 230, 170), avoid=avoid, finish=finish)


def glow_of(img):
    """Only the molten pixels (meant to be blurred and added)."""
    hot = set(EMBER[:4])
    out = new(W, H)
    src, dst = img.load(), out.load()
    for y in range(H):
        for x in range(W):
            if src[x, y] in hot:
                dst[x, y] = src[x, y]
    return out


# ---------------------------------------------------------------- flame
def flame_frames(seed=4):
    nz = Noise(seed, n=16)
    frames = []
    for f in range(FLAME_FRAMES):
        img = new(FLAME_W, FLAME_H)
        px = img.load()
        ph = 2 * math.pi * f / FLAME_FRAMES
        for y in range(FLAME_H):
            hgt = (FLAME_H - 1 - y) / (FLAME_H - 1)  # 0 bottom .. 1 tip
            cx = FLAME_W / 2 + math.sin(ph + hgt * 3.2) * hgt * 1.7
            half = 5.6 * (1 - hgt) ** 0.6 + 0.5
            for x in range(FLAME_W):
                dx = abs(x + 0.5 - cx) / half
                n = nz(x / 2.4, (y + f * 40 / FLAME_FRAMES) / 2.4)
                v = 1 - dx - 0.45 * n * (0.3 + hgt) - 0.3 * hgt * hgt
                if v > 0.6:
                    c = EMBER[0]
                elif v > 0.4:
                    c = EMBER[1]
                elif v > 0.2:
                    c = EMBER[2]
                elif v > 0.04:
                    c = EMBER[3]
                else:
                    continue
                px[x, y] = c
        # a spark leaving the tip
        sy = 4 - (f % 3) * 2
        sx = int(FLAME_W / 2 + math.sin(ph * 2) * 3)
        if px[sx, max(0, sy)][3] == 0:
            px[sx, max(0, sy)] = EMBER[2]
        frames.append(img)
    return strip(frames)


# ---------------------------------------------------------------- buttons, text
def molten_text(px, w, h, cells, ox, oy, height=7):
    for x, y in cells:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (1, 1)):
            if (x + dx, y + dy) not in cells:
                put(px, w, h, ox + x + dx, oy + y + dy, WALL[7] if (dx, dy) != (1, 1) else EMBER[5])
    for x, y in cells:
        t = y / (height - 1)
        put(px, w, h, ox + x, oy + y, EMBER[1] if t < 0.3 else EMBER[2] if t < 0.75 else EMBER[3])


def carved_text(px, w, h, cells, ox, oy, fill=WALL[7], lip=WALL[1]):
    for x, y in cells:
        if (x + 1, y + 1) not in cells:
            put(px, w, h, ox + x + 1, oy + y + 1, lip)
    for x, y in cells:
        put(px, w, h, ox + x, oy + y, fill)


def inlaid_text(px, w, h, cells, ox, oy, pal=PARCHMENT):
    """Pale letters set into the stone with a dark shadow (readable body text)."""
    for x, y in cells:
        if (x + 1, y + 1) not in cells:
            put(px, w, h, ox + x + 1, oy + y + 1, WALL[7])
    for x, y in cells:
        put(px, w, h, ox + x, oy + y, pal[0] if y < 3 else pal[1])


def slab(w, h, hover, seed, base=0.36):
    img = new(w, h)
    px = img.load()
    nz = Noise(seed, n=16)
    for y in range(h):
        for x in range(w):
            dl, dr, dt, db = x, w - 1 - x, y, h - 1 - y
            d = min(dl, dr, dt, db)
            if min(dl, dr) + min(dt, db) == 0:
                continue  # cut corners
            if d == 0:
                px[x, y] = MORTAR
                continue
            v = (base - (0.1 if hover else 0)) + 0.12 * (nz(x / 5, y / 5) - 0.5)
            if d == 1:
                v += -0.22 if (dl == 1 or dt == 1) else 0.28
            elif d == 2:
                v += -0.06 if (dl == 2 or dt == 2) else 0.1
            c = WALL[dither_index(clamp(v), x, y, len(WALL), band=0.1)]
            if hover:
                c = mix(c, st.WARM, 0.1)
                if d == 2:
                    c = EMBER[2] if (dl == 2 or dt == 2) else EMBER[3]
            px[x, y] = c
    return img


def stud(px, w, h, sx, sy, hover):
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            k = abs(dx) + abs(dy)
            if k == 2:
                put(px, w, h, sx + dx, sy + dy, WALL[7])
            elif k == 1:
                put(px, w, h, sx + dx, sy + dy, EMBER[2] if hover else EMBER[4])
            elif k == 0:
                put(px, w, h, sx, sy, EMBER[0] if hover else EMBER[3])


def button(label, w, hover):
    img = slab(w, BUTTON_H, hover, 101 + len(label))
    px = img.load()
    cells = text_cells(label)
    tw = text_width(label)
    ox, oy = (w - tw) // 2, 4
    if hover:
        molten_text(px, w, BUTTON_H, cells, ox, oy)
    else:
        carved_text(px, w, BUTTON_H, cells, ox, oy)
    if (w - tw) // 2 >= 11:
        stud(px, w, BUTTON_H, 6, 7, hover)
        stud(px, w, BUTTON_H, w - 7, 7, hover)
    return img


def prompt(lines):
    w = max(text_width(t) for t in lines) + 4
    h = 7 * len(lines) + 5 * (len(lines) - 1) + 4
    img = new(w, h)
    px = img.load()
    for i, t in enumerate(lines):
        ox, oy = (w - text_width(t)) // 2, 1 + i * 12
        if i == 0:
            molten_text(px, w, h, text_cells(t), ox, oy)
        else:
            inlaid_text(px, w, h, text_cells(t), ox, oy)
    return img


# ---------------------------------------------------------------- controls tablet
def keycap(text):
    tw = text_width(text)
    w, h = tw + 6, 11
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            dl, dr, dt, db = x, w - 1 - x, y, h - 1 - y
            d = min(dl, dr, dt, db)
            if min(dl, dr) + min(dt, db) == 0:
                continue
            if d == 0:
                px[x, y] = MORTAR
            elif db == 1:
                px[x, y] = WALL[4]  # the key's front side
            elif dl == 1 or dt == 1:
                px[x, y] = WALL[0]
            elif dr == 1:
                px[x, y] = WALL[3]
            else:
                px[x, y] = WALL[1] if (x * 7 + y * 3) % 11 else WALL[2]
    for x, y in text_cells(text):
        px[3 + x, 2 + y] = WALL[7]
    return img


def mouse_icon(right):
    w, h = 9, 12
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            dx, dy = (x + 0.5 - w / 2) / (w / 2), (y + 0.5 - h / 2) / (h / 2)
            if dx * dx * 1.1 + dy * dy > 1.05 and not (dy < 0 and abs(dx) < 0.9 and dy > -0.75):
                continue
            px[x, y] = WALL[1]
    out = img.copy()
    op = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0:
                continue
            edge = any(not (0 <= x + a < w and 0 <= y + b < h) or px[x + a, y + b][3] == 0
                       for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                op[x, y] = MORTAR
            elif y == 5 or x == w // 2 and y < 5:
                op[x, y] = WALL[4]
            elif y < 5 and ((x > w // 2) == right):
                op[x, y] = EMBER[1] if y < 2 else EMBER[2]
            elif x < 3 and y > 5:
                op[x, y] = WALL[0]
            else:
                op[x, y] = WALL[1] if y < 5 else WALL[2]
    return out


def tablet(w, h, seed=121):
    img = new(w + 3, h + 3)
    px = img.load()
    nz = Noise(seed, n=16)
    for y in range(h + 3):
        for x in range(w + 3):
            if (x >= w or y >= h) and x >= 3 and y >= 3:
                px[x, y] = SHADOW
    for y in range(h):
        for x in range(w):
            dl, dr, dt, db = x, w - 1 - x, y, h - 1 - y
            d = min(dl, dr, dt, db)
            if d == 0:
                px[x, y] = MORTAR
                continue
            v = 0.46 + 0.12 * (nz(x / 7, y / 7) - 0.5) + 0.05 * (nz(x / 2.1, y / 2.1) - 0.5)
            if d == 1:
                v += -0.25 if (dl == 1 or dt == 1) else 0.3
            elif d == 2:
                v += -0.1 if (dl == 2 or dt == 2) else 0.14
            elif d == 5:
                v = 0.85 if (dl == 5 or dt == 5) else 0.12  # carved inner line
            px[x, y] = WALL[dither_index(clamp(v), x, y, len(WALL), band=0.1)]
    for sx, sy in ((5, 5), (w - 6, 5), (5, h - 6), (w - 6, h - 6)):
        stud(px, w + 3, h + 3, sx, sy, False)
    return img


def separator(px, w, h, x0, x1, y):
    for x in range(x0, x1 + 1):
        put(px, w, h, x, y, WALL[7])
        put(px, w, h, x, y + 1, WALL[1])


def controls_tablet():
    img = tablet(TABLET_W, TABLET_H)
    px = img.load()
    w, h = img.size
    title = 'CONTROLS'
    molten_text(px, w, h, text_cells(title, 2), (TABLET_W - text_width(title, 2)) // 2, 9, height=14)
    separator(px, w, h, 16, TABLET_W - 17, 28)
    key_right, label_x = 56, 64
    y = 34
    for row in CONTROLS:
        if row is None:  # section header
            y += 3
            head = 'IN THE SKILL TREE'
            hw = text_width(head)
            hx = (TABLET_W - hw) // 2
            molten_text(px, w, h, text_cells(head), hx, y + 2)
            separator(px, w, h, 16, hx - 6, y + 5)
            separator(px, w, h, hx + hw + 6, TABLET_W - 17, y + 5)
            y += 15
            continue
        keys, label = row
        parts = []
        for k in keys:
            if k == '-':
                part = new(text_width('-'), 11)
                inlaid_text(part.load(), part.width, 11, text_cells('-'), 0, 2)
            elif k in ('LMB', 'RMB'):
                part = mouse_icon(k == 'RMB')
            else:
                part = keycap(k)
            parts.append(part)
        total = sum(p.width for p in parts) + 2 * (len(parts) - 1)
        x = key_right - total
        for p in parts:
            img.alpha_composite(p, (x, y + (11 - p.height) // 2))
            x += p.width + 2
        inlaid_text(px, w, h, text_cells(label), label_x, y + 2)
        y += 12
    return img


# ---------------------------------------------------------------- output
def layout():
    """Art-pixel positions the Java view uses (x4 for the screen)."""
    cx, cy = CENTER
    return {
        'main_buttons': [(cx - MAIN_W // 2, BUTTON_TOP + i * BUTTON_STEP) for i in range(len(MAIN_ORDER))],
        'tablet': (cx - TABLET_W // 2, cy - TABLET_H // 2),
        'back': (cx - SMALL_W // 2, cy - TABLET_H // 2 + TABLET_H - BUTTON_H - 8),
        'prompt_y': cy - 30,
        'confirm_buttons': [(cx - SMALL_W - 4, cy + 2), (cx + 4, cy + 2)],
        'flames': [(tx - FLAME_W // 2, ty + 2 - FLAME_H) for tx, ty in TORCHES],
    }


def generate(out_dir):
    bg = menu_bg()
    bg.save(f'{out_dir}/MenuBg.png')
    glow_of(bg).save(f'{out_dir}/MenuGlow.png')
    flame_frames().save(f'{out_dir}/MenuFlame.png')
    for bid, (label, w) in BUTTONS.items():
        strip([button(label, w, False), button(label, w, True)]).save(f'{out_dir}/MenuButton_{bid}.png')
    for pid, lines in PROMPTS.items():
        prompt(lines).save(f'{out_dir}/MenuText_{pid}.png')
    controls_tablet().save(f'{out_dir}/MenuControls.png')
    # left / right mouse button, also used by the skill tree's controls
    strip([mouse_icon(False), mouse_icon(True)]).save(f'{out_dir}/MouseIcon.png')


def mock(path, mode='main'):
    bg = menu_bg().convert('RGBA')
    L = layout()

    def paste(img, x, y):
        bg.alpha_composite(img, (x, y))

    flame = flame_frames().crop((0, 0, FLAME_W, FLAME_H))
    for x, y in L['flames']:
        paste(flame, x, y)
    if mode == 'main':
        for i, (bid, (x, y)) in enumerate(zip(MAIN_ORDER, L['main_buttons'])):
            label, w = BUTTONS[bid]
            paste(button(label, w, i == 0), x, y)
    elif mode == 'controls':
        paste(controls_tablet(), *L['tablet'])
        paste(button('BACK', SMALL_W, True), *L['back'])
    else:
        p = prompt(PROMPTS['NEW_GAME'])
        paste(p, CENTER[0] - p.width // 2, L['prompt_y'])
        paste(button('START', SMALL_W, True), *L['confirm_buttons'][0])
        paste(button('CANCEL', SMALL_W, False), *L['confirm_buttons'][1])
    big = bg.resize((W * SCALE, H * SCALE), Image.Resampling.NEAREST)
    g = glow_of(bg).resize((W * SCALE, H * SCALE), Image.Resampling.NEAREST).filter(ImageFilter.GaussianBlur(14))
    g = Image.eval(g, lambda v: int(v * 0.55))
    big = ImageChops.add(big, g)
    big.convert('RGB').save(path)


if __name__ == '__main__':
    mock(sys.argv[1] if len(sys.argv) > 1 else 'menu_mock.png', sys.argv[2] if len(sys.argv) > 2 else 'main')
