"""Golden mouse cursor that matches the rune wall menus.

`Cursor.png` is a 2-frame strip (normal / hover over something clickable), 21x21 each, drawn x2 by the game
(`Menu/GoldCursor.java`). The hotspot is the arrow tip at (1, 1).
"""
import sys
from PIL import Image

from pixel import *
from materials import GOLD
from skilltree import EMBER

PAD = 1                     # room for the hover halo
SIZE = 21
HOTSPOT = (PAD, PAD)        # the tip of the outline
OUT = hexc('#1a0e07')       # warm near-black outline: reads on the dark wall and on bright effects

# hand-pixelled: o outline, 1-5 gold ramp (1 = highlight), e molten groove along the arrow's axis
ARROW = [
    'o.............',
    'oo............',
    'o1o...........',
    'o12o..........',
    'o1e2o.........',
    'o1e22o........',
    'o12e22o.......',
    'o12e223o......',
    'o122e233o.....',
    'o122e2333o....',
    'o1222e3334o...',
    'o1223e33344o..',
    'o12233ooooooo.',
    'o123o34o......',
    'o12oo34o......',
    'o1o..o34o.....',
    'oo...o34o.....',
    '......o45o....',
    '.......oo.....',
]


def palette(hover):
    """Normal: an engraved groove. Hover: the groove turns molten, like the grooves of the menu wall."""
    pal = {'o': OUT, 'e': GOLD[4] if not hover else EMBER[2]}
    pal.update({str(i + 1): GOLD[i] for i in range(5)})
    return pal


def frame(hover):
    img = new(SIZE, SIZE)
    px = img.load()
    pal = palette(hover)
    for y, row in enumerate(ARROW):
        for x, ch in enumerate(row):
            if ch in pal:
                px[x + PAD, y + PAD] = pal[ch]
    if hover:
        # soft ember halo just outside the outline
        halo = (EMBER[2][0], EMBER[2][1], EMBER[2][2], 150)
        solid = {(x, y) for x in range(SIZE) for y in range(SIZE) if px[x, y][3] == 255}
        for x, y in [(x, y) for x in range(SIZE) for y in range(SIZE) if (x, y) not in solid]:
            if any((x + dx, y + dy) in solid for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = halo
    return img


def generate(out_dir):
    strip([frame(False), frame(True)]).save(f'{out_dir}/Cursor.png')


def preview(path):
    """Both frames at x2 (game size) and x12 on dark wall, sky and ember backgrounds."""
    bgs = [(27, 26, 32, 255), (120, 150, 190, 255), (209, 54, 26, 255), (230, 225, 210, 255)]
    big = 12
    out = Image.new('RGBA', (len(bgs) * (SIZE * big + 16) + 16, 2 * (SIZE * big + 16) + 16 + 60), (0, 0, 0, 255))
    for r, hover in enumerate((False, True)):
        f = frame(hover)
        for c, bg in enumerate(bgs):
            x, y = 16 + c * (SIZE * big + 16), 16 + r * (SIZE * big + 16)
            tile = Image.new('RGBA', (SIZE * big, SIZE * big), bg)
            tile.alpha_composite(f.resize((SIZE * big, SIZE * big), Image.Resampling.NEAREST))
            out.paste(tile, (x, y))
            small = Image.new('RGBA', (SIZE * 2 + 8, SIZE * 2 + 8), bg)
            small.alpha_composite(f.resize((SIZE * 2, SIZE * 2), Image.Resampling.NEAREST), (4, 4))
            out.paste(small, (x + r * 48, 2 * (SIZE * big + 16) + 16))
    out.save(path)


if __name__ == '__main__':
    preview(sys.argv[1] if len(sys.argv) > 1 else 'cursor_preview.png')
