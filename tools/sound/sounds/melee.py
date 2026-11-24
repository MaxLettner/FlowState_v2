"""Swings, hits on flesh, blocking."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def swing_light():
    return mix((whoosh(0.2, 700, 3600, 1.4, 0.4, seed=20), 1.0), (highpass(whoosh(0.12, 3000, 7000, 2, 0.5, seed=21), 2000), 0.3, 0.03))


@sound
def swing_heavy():
    return mix((whoosh(0.4, 180, 1300, 1.2, 0.5, seed=22, color='pink'), 1.0), (whoosh(0.3, 600, 2200, 1.5, 0.5, seed=23), 0.4, 0.06))


@sound
def punch():
    return mix((whoosh(0.1, 600, 2400, 1.5, 0.5, seed=24), 0.8), (thump(0.05, 150, 90), 0.2))


@sound
def stab():
    return mix((whoosh(0.15, 1200, 4500, 1.8, 0.3, seed=25), 1.0), (bell(0.12, 2100, ((1, 1), (2.3, 0.4)), 0.06), 0.15, 0.06))


@sound
def spin():
    return whoosh(0.38, 250, 1600, 1.3, 0.55, seed=26, color='pink')


@sound
def hit_slash():
    slice_ = highpass(env(noise(0.05, seed=30), 0.0005, 0.04), 2500)
    ring = bell(0.2, 2300, ((1, 1.0), (1.47, 0.6), (2.09, 0.3)), 0.1)
    flesh = mix((thump(0.09, 170, 70), 1.0), (squelch(0.1, 1500, 600, 2.5, seed=31), 0.6))
    return mix((slice_, 0.9), (ring, 0.18), (flesh, 0.8))


@sound
def hit_blunt():
    body = drive(mix((thump(0.22, 120, 40), 1.0), (lowpass(env(noise(0.1, seed=32), 0.001, 0.08), 1200), 0.8)), 2.5)
    return mix((body, 1.0), (squelch(0.12, 900, 300, 2, seed=33), 0.5))


@sound
def hit_punch():
    return drive(mix((thump(0.09, 190, 75), 1.0), (lowpass(env(noise(0.03, seed=34), 0.0005, 0.025), 3000), 0.7),
                     (squelch(0.07, 1200, 500, 2, seed=35), 0.4)), 1.8)


@sound
def hit_arrow():
    return mix((thump(0.07, 320, 130), 1.0), (squelch(0.06, 1600, 800, 3, seed=36), 0.6),
               (lowpass(env(noise(0.015, seed=37), 0.0005, 0.012), 5000), 0.5))


@sound
def hit_magic():
    pop = env(tone(0.15, sweep(900, 1500, 0.08), vibrato=0.5, vib_rate=40), 0.001, 0.15)
    fizz = bandpass(env(noise(0.18, seed=38), 0.001, 0.16), 3500, 1.5)
    return mix((pop, 0.5), (fizz, 0.6), (shimmer(0.15, 2500, 5000, seed=39), 0.4), (thump(0.06, 180, 90), 0.4))


@sound
def crit():
    ding = chord(0.45, (1760, 2637), 0.3, ((1, 1.0), (2.0, 0.25)))
    click = highpass(env(noise(0.01, seed=40), 0.0002, 0.008), 3000)
    return mix((ding, 0.6), (click, 0.6))


@sound
def enemy_death():
    splat = drive(mix((squelch(0.32, 2400, 250, 1.8, seed=41), 1.0), (thump(0.2, 220, 50), 0.9)), 1.8)
    gloop = env(tone(0.3, sweep(300, 90, 0.3), vibrato=3, vib_rate=18), 0.01, 0.3)
    rng = random.Random(42)
    drops = mix(*[(squelch(0.04, rng.uniform(1500, 3000), 600, 4, seed=43 + k), 0.4, 0.12 + k * 0.05) for k in range(4)])
    return mix((splat, 1.0), (gloop, 0.4), (drops, 0.5))


@sound
def block_raise():
    clank = bell(0.18, 420, ((1, 1.0), (2.69, 0.6), (4.45, 0.35)), 0.1)
    return mix((clank, 0.7), (lowpass(env(noise(0.03, seed=50), 0.0005, 0.025), 3000), 0.5))


@sound
def block_hit():
    clang = bell(0.55, 760, ((1, 1.0), (1.78, 0.7), (2.95, 0.5), (4.1, 0.3)), 0.4)
    return mix((clang, 0.7), (highpass(env(noise(0.04, seed=51), 0.0005, 0.03), 1500), 0.7), (thump(0.08, 200, 90), 0.5))


@sound
def deflect():
    ting = bell(0.4, 2500, ((1, 1.0), (1.56, 0.6), (2.08, 0.4)), 0.3)
    return mix((ting, 0.7), (highpass(env(noise(0.02, seed=52), 0.0005, 0.015), 3000), 0.6))
