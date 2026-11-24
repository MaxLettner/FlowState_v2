"""The Tar & Ember slime: movement, hurt and death, flow, charges, the slam."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)


@sound
def jump():
    blob = env(tone(0.16, sweep(170, 520, 0.12), vibrato=1.5, vib_rate=28), 0.003, 0.16)
    wet = squelch(0.08, 900, 2200, 4, seed=1)
    return mix((blob, 1.0), (wet, 0.35))


@sound
def land():
    return mix((thump(0.16, 140, 55), 1.0), (squelch(0.14, 1400, 400, 3, seed=2), 0.6),
               (lowpass(env(noise(0.08, seed=3), 0.001, 0.06), 900), 0.4))


@sound
def step():
    return mix((squelch(0.07, 700, 1600, 3, seed=4), 1.0), (thump(0.06, 210, 110), 0.5))


@sound
def dash():
    return mix((whoosh(0.28, 400, 2600, 1.2, 0.35, seed=5), 1.0), (squelch(0.06, 2000, 900, 3, seed=6), 0.3))


@sound
def levitate():
    air = whoosh(0.9, 300, 1400, 1.0, 0.3, seed=7, color='pink')
    rise = env(tone(0.9, sweep(330, 880, 0.9), vibrato=0.3, vib_rate=5), 0.15, 0.9, curve='lin')
    return mix((air, 1.0), (rise, 0.25), (shimmer(0.8, 1800, 4200, seed=8), 0.35))


@sound
def player_hurt():
    hit = mix((thump(0.14, 180, 60), 1.0), (lowpass(env(noise(0.06, seed=9), 0.001, 0.05), 2500), 0.6))
    blorp = env(tone(0.28, sweep(480, 160, 0.25), vibrato=2.0, vib_rate=22), 0.01, 0.28)
    return drive(mix((hit, 1.0), (blorp, 0.55, 0.02), (squelch(0.18, 1800, 500, 3, seed=10), 0.4)), 1.6)


@sound
def player_death():
    melt = env(tone(1.3, sweep(320, 55, 1.2), vibrato=2.5, vib_rate=7), 0.02, 1.3, curve='lin')
    rng = random.Random(11)
    bubbles = mix(*[(env(tone(0.05, sweep(f, f * 1.8, 0.05)), 0.002, 0.05), rng.uniform(0.2, 0.5), rng.uniform(0.05, 1.1))
                    for f in (rng.uniform(250, 700) for _ in range(16))])
    goo = lowpass(squelch(1.1, 1200, 200, 2, seed=12), 1500)
    return mix((melt, 0.8), (bubbles, 0.6), (goo, 0.5))


@sound
def fall():
    whistle = env(tone(1.1, sweep(1300, 180, 1.1), vibrato=0.4, vib_rate=6), 0.05, 1.1, curve='lin')
    return mix((whistle, 0.5), (whoosh(1.1, 200, 900, 1.0, 0.2, seed=13, color='pink'), 0.6))


@sound
def bounce():
    return env(tone(0.35, sweep(180, 620, 0.3), 'tri', vibrato=3, vib_rate=26), 0.002, 0.35)


@sound
def slam_dive():
    return whoosh(0.45, 500, 2600, 1.2, 0.75, seed=57)


@sound
def slam_impact():
    boom = drive(mix((thump(0.6, 85, 28), 1.0), (lowpass(env(noise(0.5, seed=58), 0.001, 0.4), sweep(1500, 150, 0.4)), 0.9)), 2.2)
    debris = lowpass(clicks(0.6, 40, 0.006, (600, 2500), seed=59), 4000)
    return mix((boom, 1.0), (amp(debris, lambda t: math.exp(-t / 0.2)), 0.5, 0.03))


@sound
def flow_up():
    return chord(0.4, (880, 1320), 0.28, ((1, 1.0), (2.0, 0.3), (4.0, 0.08)), spread=0.03)


@sound
def flow_max():
    arp = chord(0.9, (880, 1109, 1319, 1760), 0.45, ((1, 1.0), (2.0, 0.3)), spread=0.045)
    return mix((arp, 0.8), (shimmer(0.6, 2500, 6000, seed=53), 0.4, 0.1))


@sound
def flow_ready():
    return bell(0.05, 2600, ((1, 1.0),), 0.02)


@sound
def charge_weapon():
    dur = 1.5
    hum = lowpass(tone(dur, sweep(80, 170, dur), 'saw'), sweep(300, 1600, dur))
    hum = tremolo(hum, sweep(5, 22, dur), 0.4)
    hiss = bandpass(noise(dur, seed=54), sweep(600, 3000, dur), 2)
    return amp(mix((hum, 0.7), (hiss, 0.4)), lambda t: min(1.0, t / dur) ** 1.2 * 0.9 + 0.1)


@sound
def charge_ready():
    return mix((chord(0.5, (1319, 1976), 0.35, ((1, 1.0), (2.0, 0.2))), 0.7), (shimmer(0.3, 3000, 6000, seed=55), 0.4))


@sound
def perfect():
    shing = highpass(env(noise(0.5, seed=56), 0.001, 0.45), sweep(2000, 6000, 0.4))
    ch = chord(0.8, (1319, 1661, 1976, 2637), 0.5, ((1, 1.0), (2.0, 0.25)), spread=0.02)
    return mix((shing, 0.4), (ch, 0.6))
