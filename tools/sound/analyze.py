"""Numbers for sounds nobody can listen to: length, loudness and where the energy sits.

    python tools/sound/analyze.py jump land        these files from the game's sound folder
    python tools/sound/analyze.py --group player   every sound of a group
    python tools/sound/analyze.py path/to/x.wav    any WAV

sub < 80 Hz, low 80-300, mid 300-2k, high > 2k (shares of the energy, %). centroid is a rough brightness in Hz.
For the dark palette, aim for high <~15% on anything that isn't meant to hiss, and some sub on anything heavy.
"""
import glob
import math
import os
import struct
import sys
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'sounds'))

from synth import SR, lowpass2

SOUND_DIR = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'sounds'))


def read(path):
    with wave.open(path) as w:
        raw = w.readframes(w.getnframes())
    return [s / 32768 for s in struct.unpack(f'<{len(raw) // 2}h', raw)]


def energy(sig):
    return sum(x * x for x in sig) / max(1, len(sig))


def stats(sig):
    total = energy(sig) or 1e-12
    below80 = lowpass2(sig, 80)
    below300 = lowpass2(sig, 300)
    below2k = lowpass2(sig, 2000)
    e80, e300, e2k = energy(below80), energy(below300), energy(below2k)
    bands = [e80, max(0.0, e300 - e80), max(0.0, e2k - e300), max(0.0, total - e2k)]
    s = sum(bands) or 1e-12
    shares = [100 * b / s for b in bands]
    centroid = sum(sh * f for sh, f in zip(shares, (50, 160, 800, 4500))) / 100
    rms_db = 10 * math.log10(total)
    return len(sig) / SR, rms_db, shares, centroid


def paths(args):
    if args[:1] == ['--group']:
        import generate_all
        owner = generate_all.load(args[1:2])
        names = sorted(owner)
        args = []
        for nm in names:
            args += sorted(glob.glob(os.path.join(SOUND_DIR, nm + '.wav')) + glob.glob(os.path.join(SOUND_DIR, nm + '_[0-9]*.wav')))
        return args
    return [a if a.endswith('.wav') else os.path.join(SOUND_DIR, a + '.wav') for a in args] or sorted(glob.glob(os.path.join(SOUND_DIR, '*.wav')))


def main():
    print(f'{"sound":26s} {"len":>5s} {"rms":>6s} {"sub":>4s} {"low":>4s} {"mid":>4s} {"high":>4s} {"centroid":>8s}')
    for p in paths(sys.argv[1:]):
        length, rms, sh, c = stats(read(p))
        print(f'{os.path.basename(p):26s} {length:5.2f} {rms:6.1f} {sh[0]:4.0f} {sh[1]:4.0f} {sh[2]:4.0f} {sh[3]:4.0f} {c:8.0f}')


if __name__ == '__main__':
    main()
