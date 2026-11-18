"""Writes every generated sprite into src/main/resources/assets/textures.

Run from anywhere:  python tools/art/generate_all.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import slimes
import weapons
import ranged
import projectiles
import effects
import terrain

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'textures'))
LAVA_ROWS = 80  # only the bottom of the lava layer is visible; keeps the x5 images small


def main():
    os.makedirs(OUT, exist_ok=True)
    slimes.generate(OUT)
    weapons.generate(OUT)
    ranged.generate(OUT)
    projectiles.generate(OUT)
    effects.generate(OUT)
    items = terrain.generate(OUT)
    # crop the lava frames to the visible band at the bottom
    frames = [f.crop((0, f.height - LAVA_ROWS, f.width, f.height)) for f in items['bg_lava']]
    from pixel import strip
    strip(frames).save(os.path.join(OUT, 'bg_lava.png'))
    print('textures written to', OUT)


if __name__ == '__main__':
    main()
