"""Bows, crossbows, thrown weapons."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def bow_draw():
    dur = 0.9
    creak = bandpass(tone(dur, sweep(35, 75, dur), 'saw'), 900, 3)
    creak = amp(creak, lambda t: 0.3 + 0.7 * (0.5 + 0.5 * math.sin(TAU * 7 * t)) * (t / dur))
    stretch = bandpass(noise(dur, seed=60), sweep(300, 800, dur), 2)
    return amp(mix((creak, 0.7), (stretch, 0.3)), lambda t: min(1.0, t / 0.1))


@sound
def bow_release():
    twang = env(pluck(0.4, 150, 0.993, 0.6, seed=61), 0.001, 0.35)
    return mix((twang, 1.0), (whoosh(0.15, 1200, 4000, 1.5, 0.2, seed=62), 0.5))


@sound
def arrow_thunk():
    return mix((thump(0.12, 260, 140), 1.0), (squelch(0.06, 900, 600, 3, seed=63), 0.6),
               (env(pluck(0.15, 380, 0.98, 0.4, seed=64), 0.001, 0.12), 0.4))


@sound
def bone_shatter():
    return mix((clicks(0.16, 70, 0.006, (2500, 6500), seed=65), 1.0), (thump(0.05, 400, 200), 0.3))


@loop
def crank():
    # a loop of ratchet ticks: plays while a crossbow is cranked
    tick = lambda f: mix((bell(0.03, f, ((1, 1.0), (2.4, 0.5)), 0.012), 1.0),
                         (highpass(env(noise(0.008, seed=int(f)), 0.0002, 0.006), 2000), 0.6))
    return mix(*[(tick(1700 + (k % 2) * 300), 1.0, k / 12) for k in range(6)], (silence_pad(0.5), 0))


def silence_pad(dur):
    return [0.0] * n(dur)


@sound
def crossbow_loaded():
    clack = lambda f, s: mix((bell(0.06, f, ((1, 1.0), (2.3, 0.6)), 0.03), 1.0),
                             (highpass(env(noise(0.01, seed=s), 0.0002, 0.008), 1500), 0.8))
    return mix((clack(1300, 66), 0.8), (clack(900, 67), 1.0, 0.07), (thump(0.08, 180, 100), 0.5, 0.07))


@sound
def crossbow_fire():
    thwack = env(pluck(0.25, 95, 0.985, 0.8, seed=68), 0.0005, 0.2)
    snap = highpass(env(noise(0.02, seed=69), 0.0002, 0.015), 1500)
    return drive(mix((thwack, 1.0), (snap, 0.8), (whoosh(0.15, 1500, 4500, 1.5, 0.2, seed=70), 0.4)), 1.4)


@sound
def throw():
    return mix((whoosh(0.32, 300, 1800, 1.3, 0.35, seed=71), 1.0), (thump(0.05, 140, 90), 0.2))


@sound
def trident_stick():
    twang = env(pluck(0.5, 290, 0.995, 0.5, seed=72), 0.001, 0.45)
    return mix((thump(0.1, 260, 120), 1.0), (twang, 0.6), (squelch(0.05, 1200, 700, 3, seed=73), 0.5))


@sound
def recall():
    rise = env(tone(0.55, sweep(500, 1300, 0.55), vibrato=0.4, vib_rate=9), 0.2, 0.55, curve='lin')
    return mix((whoosh(0.55, 300, 2200, 1.2, 0.85, seed=74), 1.0), (rise, 0.2), (shimmer(0.5, 2000, 5000, seed=75), 0.3))


@sound
def pickup():
    return chord(0.25, (1500, 2250), 0.14, ((1, 1.0), (2.7, 0.3)), spread=0.04)


@sound
def enemy_throw():
    return whoosh(0.22, 300, 1500, 1.4, 0.4, seed=76)
