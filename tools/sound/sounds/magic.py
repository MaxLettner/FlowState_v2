"""Spells, shields, enchantments."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def cast_arcane():
    pew = env(tone(0.2, sweep(1300, 420, 0.18), vibrato=0.8, vib_rate=30), 0.002, 0.2)
    return mix((pew, 0.6), (shimmer(0.15, 2500, 5000, seed=80), 0.4), (whoosh(0.15, 800, 3000, 1.5, 0.2, seed=81), 0.3))


@sound
def cast_spark():
    rng = random.Random(82)
    zap = env(tone(0.16, lambda t: 2400 * math.exp(-t * 9) + 500 + rng.uniform(-300, 300), 'square'), 0.001, 0.15)
    crackle = highpass(clicks(0.14, 120, 0.003, (3000, 8000), seed=83), 2000)
    return mix((lowpass(zap, 5000), 0.4), (crackle, 0.6))


@sound
def cast_icicle():
    shing = highpass(env(noise(0.25, seed=84), 0.002, 0.22), sweep(3000, 7000, 0.2))
    return mix((shing, 0.5), (bell(0.35, 2637, ((1, 1.0), (1.5, 0.5), (2.0, 0.3)), 0.22), 0.4))


@sound
def cast_poison():
    puff = lambda s: env(bandpass(noise(0.07, seed=s), 1300, 1.5), 0.003, 0.06)
    return mix((puff(85), 1.0), (puff(86), 0.8, 0.025), (puff(87), 0.7, 0.05), (squelch(0.1, 600, 300, 2, seed=88), 0.3))


@sound
def cast_fireball():
    roar = lowpass(noise(0.5, 'pink', seed=89), sweep(300, 1600, 0.4))
    roar = swell(roar, 0.3)
    crackle = lowpass(clicks(0.45, 60, 0.004, (800, 3000), seed=90), 5000)
    return mix((roar, 1.0), (amp(crackle, lambda t: 1 - t / 0.45), 0.4))


@sound
def charge_magic():
    dur = 1.5
    core = env(tone(dur, sweep(180, 620, dur), vibrato=0.6, vib_rate=8), 0.3, dur, hold=dur - 0.35, curve='lin')
    roar = lowpass(noise(dur, 'pink', seed=91), sweep(200, 1400, dur))
    crackle = clicks(dur, 25, 0.004, (800, 3500), seed=92, accel=2.0)
    return amp(mix((core, 0.4), (roar, 0.7), (crackle, 0.3)), lambda t: 0.2 + 0.8 * min(1.0, t / dur))


@sound
def explosion():
    boom = drive(mix((thump(0.7, 75, 26), 1.0), (lowpass(env(noise(0.8, seed=93), 0.002, 0.7), sweep(2500, 180, 0.6)), 1.0)), 2.6)
    crackle = lowpass(clicks(0.8, 50, 0.005, (700, 3000), seed=94), 4500)
    return mix((boom, 1.0), (amp(crackle, lambda t: math.exp(-t / 0.3)), 0.5, 0.05))


@sound
def freeze():
    crackle = highpass(clicks(0.45, 90, 0.004, (3000, 9000), seed=95), 2500)
    frost = highpass(env(noise(0.5, seed=96), 0.02, 0.45), 5000)
    return mix((amp(crackle, lambda t: math.exp(-t / 0.25)), 0.8), (frost, 0.3), (shimmer(0.35, 2500, 5000, seed=97), 0.5))


@sound
def shatter():
    rng = random.Random(98)
    shards = mix(*[(bell(0.5, rng.uniform(2200, 7000), ((1, 1.0), (1.7, 0.4)), rng.uniform(0.08, 0.3)),
                    rng.uniform(0.2, 0.6), rng.uniform(0, 0.08)) for _ in range(14)])
    burst = highpass(env(noise(0.15, seed=99), 0.0005, 0.12), 2500)
    return mix((shards, 0.6), (burst, 0.7), (thump(0.06, 300, 150), 0.3))


@sound
def crackle():
    roar = env(lowpass(noise(0.6, 'pink', seed=100), 700), 0.15, 0.45)
    pops = lowpass(clicks(0.6, 14, 0.006, (500, 2200), seed=101), 4000)
    return mix((roar, 0.5), (pops, 0.9))


@sound
def no_mana():
    buzz = lowpass(env(tone(0.18, 110, 'square'), 0.003, 0.18, hold=0.08, curve='lin'), 900)
    fizz = bandpass(env(noise(0.15, seed=102), 0.002, 0.12), 2500, 2)
    return mix((buzz, 0.6), (fizz, 0.3))


@sound
def shield_on():
    hum = mix(*[(env(tone(0.5, sweep(f, f * 2, 0.35)), 0.05, 0.5, curve='lin'), 0.4) for f in (330, 333, 495)])
    return mix((hum, 1.0), (shimmer(0.4, 2000, 4500, seed=103), 0.4))


@sound
def shield_off():
    return mix(*[(env(tone(0.35, sweep(f, f / 2, 0.3)), 0.01, 0.35, curve='lin'), 0.4) for f in (660, 663, 990)])


@sound
def shield_hit():
    bwom = env(tone(0.3, sweep(380, 260, 0.3), vibrato=1.2, vib_rate=14), 0.002, 0.3)
    return mix((bwom, 0.7), (shimmer(0.2, 2000, 4000, seed=104), 0.4), (thump(0.06, 160, 90), 0.4))


@sound
def shield_break():
    drop = env(tone(0.5, sweep(700, 120, 0.45)), 0.002, 0.5)
    return mix((shatter(), 0.8), (drop, 0.4))


@sound
def enchant_on():
    arp = chord(0.6, (659, 880, 1319), 0.3, ((1, 1.0), (2.0, 0.35)), spread=0.06)
    return mix((arp, 0.7), (shimmer(0.5, 2500, 5500, seed=105), 0.35))


@sound
def toggle_off():
    return chord(0.35, (880, 587), 0.18, ((1, 1.0), (2.0, 0.2)), spread=0.08)
