"""Writes every generated sprite into src/main/resources/assets/textures.

Run from anywhere:  python tools/art/generate_all.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import slimes
import demons
import demons2
import demons3
import lord2d
import matriarch2d
import hydra
import player_slime
import weapons
import ranged
import projectiles
import effects
import terrain
import foreground
import ruins
import icons
import skilltree
import menu
import cursor
import hudbars
import hotbar
import pixfont

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'textures'))


def main():
    os.makedirs(OUT, exist_ok=True)
    slimes.generate(OUT)
    player_slime.generate(OUT)             # the player's extra animations (run, dash, hurt, death, ...)
    demons.generate(OUT)                   # the enemies: demons, their projectiles and effects
    demons2.generate(OUT)                  # wave 2 demons and their effects (portal, hex bolt, boss gate)
    lord2d.generate(OUT)                   # the Pit Lord boss: the goat-skull executioner (2D, lord3d.py stays for the Matriarch)
    demons3.generate(OUT)                  # wave 3: Ash Drake, Void Caller, Mimic and their effects
    matriarch2d.generate(OUT)              # the Void Matriarch boss (2D), her mites, egg sacs, strands, web gates, acid
    hydra.generate(OUT)                    # the Tar Hydra boss: heads, stumps, neck pieces, tar glob/pool/wave/lake, basalt pillars
    weapons.generate(OUT)
    ranged.generate(OUT)
    projectiles.generate(OUT)
    effects.generate(OUT)
    icons.generate(OUT)
    terrain.generate(OUT)
    foreground.generate(OUT)              # cave silhouettes in front of the world (parallax)
    ruins.generate(OUT)
    skilltree.generate(OUT)
    menu.generate(OUT)
    cursor.generate(OUT)
    hudbars.generate(OUT)
    hotbar.generate(OUT)
    pixfont.generate(OUT)
    print('textures written to', OUT)


if __name__ == '__main__':
    main()
