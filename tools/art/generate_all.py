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
import ruins
import icons
import skilltree
import menu
import cursor
import hudbars
import pixfont

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'textures'))


def main():
    os.makedirs(OUT, exist_ok=True)
    slimes.generate(OUT)
    weapons.generate(OUT)
    ranged.generate(OUT)
    projectiles.generate(OUT)
    effects.generate(OUT)
    icons.generate(OUT)
    terrain.generate(OUT)
    ruins.generate(OUT)
    skilltree.generate(OUT)
    menu.generate(OUT)
    cursor.generate(OUT)
    hudbars.generate(OUT)
    pixfont.generate(OUT)
    print('textures written to', OUT)


if __name__ == '__main__':
    main()
