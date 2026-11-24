"""The sound registry and the building blocks every sound group shares.

Register a sound with @sound (one file), @sound(variants=3) (the function takes the variant index v and is written
to name.wav, name_2.wav, name_3.wav; the game picks one at random each time) or @loop (a seamless loop, not trimmed).
"""
import math
import random

from synth import (SR, TAU, sweep, tone, noise, bell, bandpass, env, swell, mix, lowpass, lowpass2, highpass2, sub,
                   saturate, cavern)

SOUNDS = {}     # name -> (fn, variants)
LOOPS = set()


def sound(fn=None, *, variants=1):
    def register(f):
        SOUNDS[f.__name__] = (f, variants)
        return f
    return register(fn) if fn else register


def loop(fn):
    SOUNDS[fn.__name__] = (fn, 1)
    LOOPS.add(fn.__name__)
    return fn


#-----Shared layers-----
def whoosh(dur, lo, hi, q=1.5, peak=0.45, seed=None, color='white'):
    """Filtered noise rising to hi and back to lo: anything moving through air."""
    centre = lambda t: lo + (hi - lo) * math.sin(math.pi * min(1.0, t / dur)) ** 1.5
    return swell(bandpass(noise(dur, color, seed), centre, q), peak)


def thump(dur, f0, f1, decay=None):
    return env(tone(dur, sweep(f0, f1, dur * 0.7)), 0.001, decay or dur)


def squelch(dur, f0, f1, q=3, seed=None):
    return env(bandpass(noise(dur, seed=seed), sweep(f0, f1, dur), q), 0.002, dur)


def chord(dur, freqs, decay, partials=((1, 1.0), (2.0, 0.3), (3.0, 0.12)), spread=0.0):
    return mix(*[(bell(dur, f, partials, decay), 1.0, i * spread) for i, f in enumerate(freqs)])


def shimmer(dur, lo, hi, seed=None):
    """Sparkly high glints. Too bright for the new palette: use sparingly, low in the mix, or not at all."""
    rng = random.Random(seed)
    parts = []
    for _ in range(int(dur * 22)):
        f = rng.uniform(lo, hi)
        parts.append((bell(0.25, f, ((1, 1.0),), 0.12), rng.uniform(0.2, 0.6), rng.uniform(0, dur)))
    return mix(*parts)


def impact(dur=0.6, weight=1.0, body=110, crack=0.5, seed=None, room=0.25):
    """A heavy hit in the cavern: sub drop + dark noise body + a short crack, saturated, with a stone-hall tail.
    weight 0..1.5 scales the sub, body is the punch frequency, crack 0..1 the transient."""
    rng = random.Random(seed)
    low = sub(dur, body * 0.55, 32, dur * 0.9)
    punch = env(tone(dur * 0.4, sweep(body * 1.6, body * 0.7, dur * 0.25)), 0.0008, dur * 0.35)
    thud = lowpass2(env(noise(dur, 'brown', seed=rng.randrange(1 << 30)), 0.001, dur * 0.7), 500)
    snap = highpass2(env(noise(0.03, seed=rng.randrange(1 << 30)), 0.0003, 0.025), 1800)
    dry = saturate(mix((low, weight), (punch, 0.8), (thud, 0.9), (snap, crack * 0.5)), 2.2)
    return cavern(dry, room, 1.6 + weight) if room else dry
