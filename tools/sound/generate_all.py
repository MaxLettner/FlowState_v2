"""Writes every sound effect into src/main/resources/assets/sounds as 16-bit mono WAV.

Run from anywhere:
    python tools/sound/generate_all.py                  every sound
    python tools/sound/generate_all.py jump land        just these
    python tools/sound/generate_all.py --group player   one group (a module in sounds/)
Every file is peak-normalized; how loud each one plays in game is set in Sfx.java.
The sounds live in sounds/<group>.py, the DSP in synth.py, shared layers and the registry in common.py.
"""
import glob
import importlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'sounds'))

from synth import SR, write
from common import SOUNDS, LOOPS

OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'sounds'))
GROUPS = ['player', 'melee', 'ranged', 'magic', 'demons', 'bosses', 'ui']


def load(groups):
    """Imports the group modules; returns name -> group for every sound they register."""
    owner = {}
    for g in groups:
        before = set(SOUNDS)
        importlib.import_module(g)
        for name in set(SOUNDS) - before:
            owner[name] = g
    return owner


def file_names(name, variants):
    return [name + '.wav'] + [f'{name}_{v + 1}.wav' for v in range(1, variants)]


def main():
    args = sys.argv[1:]
    groups = GROUPS
    if args[:1] == ['--group']:
        groups, args = args[1:2], args[2:]
    owner = load(groups)
    only = set(args)
    os.makedirs(OUT, exist_ok=True)
    for name, (fn, variants) in SOUNDS.items():
        if only and name not in only:
            continue
        # old variant files of this sound go first, so lowering the count never leaves stale ones behind
        for old in glob.glob(os.path.join(OUT, name + '_[0-9]*.wav')):
            os.remove(old)
        for v, file in enumerate(file_names(name, variants)):
            sig = fn(v) if variants > 1 else fn()
            out = write(os.path.join(OUT, file), sig, trim=name not in LOOPS)
            print(f'{owner[name]:7s} {file:26s} {len(out) / SR:5.2f}s')


if __name__ == '__main__':
    main()
