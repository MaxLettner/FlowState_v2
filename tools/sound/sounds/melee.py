"""Swings, hits on flesh, blocking."""
import math
import random

from synth import *  # noqa: F401,F403  (the DSP toolbox: tone, noise, reverb, cavern, lowpass2, sub, toll, ...)
from common import *  # noqa: F401,F403  (sound, loop, whoosh, thump, squelch, chord, shimmer, impact)

PITCH = (1.0, 0.93, 1.07)   # per-variant pitch of the repeating sounds


def dark_whoosh(dur, lo, hi, cutoff, seed, q=1.0, peak=0.45):
    """Heavy air: pink noise swept through a band, then darkened with a 12 dB low-pass."""
    return lowpass2(whoosh(dur, lo, hi, q, peak, seed=seed, color='pink'), cutoff)


def sizzle(dur, seed, rate=60, freq=(1200, 3500), level=1.0):
    """Molten ember crackle: sparse ticks that thin out."""
    return amp(clicks(dur, rate, 0.004, freq, 0.6, seed), lambda t: level * math.exp(-t / (dur * 0.5)))


def flesh(dur, seed, body=130):
    """A wet thud: a low punch plus dark noise."""
    wet = lowpass2(env(noise(dur, 'pink', seed), 0.002, dur * 0.8), sweep(1100, 250, dur), 1.5)
    return mix((thump(dur, body * 1.3, body * 0.5), 1.0), (wet, 0.8))


def iron(dur, freq, decay, seed=0, ring=0.6):
    """Dark inharmonic iron: low struck partials."""
    return bell(dur, freq, ((1, 1.0), (1.59, 0.7 * ring), (2.14, 0.5 * ring), (3.02, 0.3 * ring), (4.2, 0.15 * ring)), decay)


@sound(variants=3)
def swing_light(v):
    s = PITCH[v]
    air = dark_whoosh(0.24, 140 * s, 900 * s, 1200, 20 + v, 1.0)
    edge = highpass2(lowpass2(whoosh(0.1, 800, 2500, 1.5, 0.5, seed=21 + v), 3000), 600)
    low = env(lowpass2(tone(0.2, sweep(110 * s, 60, 0.2)), 300), 0.05, 0.2, curve='lin')
    return mix((air, 1.0), (edge, 0.08, 0.03), (low, 0.15))


@sound(variants=3)
def swing_heavy(v):
    s = PITCH[v]
    air = dark_whoosh(0.5, 110 * s, 700 * s, 1100, 22 + v, 1.0, 0.5)
    low = env(lowpass2(stack(0.45, sweep(90 * s, 50, 0.45), 'saw', 3, 20, 23 + v), 350), 0.12, 0.45, curve='lin')
    return cavern(mix((air, 1.2), (low, 0.8), (sub(0.4, 60, 36, 0.3, 0.1), 0.5, 0.05)), 0.15, 0.7)


@sound
def punch():
    air = dark_whoosh(0.12, 200, 1000, 1500, 24, 1.2, 0.5)
    return mix((air, 1.0), (thump(0.08, 110, 60), 0.5, 0.03))


@sound
def stab():
    air = dark_whoosh(0.18, 250, 1100, 1500, 25, 1.4, 0.35)
    shing = env(iron(0.15, 330, 0.08, ring=0.5), 0.003, 0.12)
    return mix((air, 1.0), (shing, 0.15, 0.06))


@sound
def spin():
    dur = 0.4
    air = dark_whoosh(dur, 150, 900, 1300, 26, 1.0, 0.55)
    chain = amp(lowpass2(clicks(dur, 70, 0.012, (500, 1600), 0.3, 27), 2500), lambda t: math.sin(math.pi * t / dur))
    return cavern(mix((air, 1.2), (chain, 0.35), (tremolo(env(tone(dur, 70), 0.1, dur, curve='lin'), 14, 0.5), 0.4)), 0.12, 0.6)


@sound(variants=3)
def hit_slash(v):
    s = PITCH[v]
    crack = highpass2(env(noise(0.025, seed=30 + v), 0.0003, 0.02), 2200)
    ring = env(iron(0.4, 380 * s, 0.14, ring=0.5), 0.001, 0.3)
    sig = mix((crack, 0.35), (ring, 0.22), (flesh(0.16, 31 + v, 140 * s), 1.0),
              (sub(0.2, 80, 40, 0.15), 0.5))
    return cavern(saturate(sig, 1.7), 0.15, 0.6)


@sound(variants=3)
def hit_blunt(v):
    s = PITCH[v]
    return cavern(impact(0.45, 1.0, 100 * s, 0.35, 32 + v, 0), 0.15, 0.7)


@sound(variants=3)
def hit_punch(v):
    s = PITCH[v]
    body = mix((flesh(0.12, 34 + v, 150 * s), 1.0), (sub(0.15, 80, 45, 0.12), 0.5),
               (lowpass2(env(noise(0.02, seed=35 + v), 0.0004, 0.015), 2800), 0.4))
    return saturate(body, 2.0)


@sound(variants=3)
def hit_arrow(v):
    s = PITCH[v]
    thuck = mix((thump(0.1, 240 * s, 100), 1.0), (flesh(0.1, 36 + v, 180 * s), 0.8),
                (env(bandpass(noise(0.05, seed=37 + v), 900 * s, 3), 0.001, 0.04), 0.8))
    return saturate(thuck, 1.6)


@sound(variants=3)
def hit_magic(v):
    s = PITCH[v]
    burst = lowpass2(env(noise(0.3, seed=38 + v), 0.001, 0.25), sweep(2200, 300, 0.3), 2)
    hum = env(mix(tone(0.3, 110 * s), tone(0.3, 165 * s)), 0.002, 0.25)
    return cavern(saturate(mix((burst, 0.9), (hum, 0.7), (thump(0.12, 150, 60), 0.8),
                               (sizzle(0.25, 39 + v, 80, (1200, 3000), 0.3), 0.3)), 1.6), 0.18, 0.7)


@sound
def crit():
    # heavy iron toll with a hard crack; the game plays it a third lower or higher
    sig = mix((toll(0.9, 150, 0.45), 1.0), (sub(0.5, 90, 40, 0.4), 0.9),
              (highpass2(env(noise(0.02, seed=40), 0.0003, 0.015), 2500), 0.35),
              (flesh(0.15, 41, 120), 0.6))
    return cavern(saturate(sig, 1.8), 0.25, 1.0)


@sound(variants=3)
def enemy_death(v):
    s = PITCH[v]
    dur = 0.6
    burst = lowpass2(env(noise(dur, seed=42 + v), 0.002, dur * 0.6), sweep(2200, 200, dur), 1.6)
    gloop = resample(gulp_tone(0.4, 200 * s, 70), 0.9)
    rng = random.Random(43 + v)
    drops = mix(*[(env(tone(0.08, sweep(rng.uniform(150, 300), 70, 0.08)), 0.003, 0.07), 0.5, 0.15 + k * 0.07)
                  for k in range(4)])
    sig = mix((burst, 0.9), (thump(0.25, 160 * s, 45), 1.0), (sub(0.5, 70, 32, 0.45), 0.9), (gloop, 0.7), (drops, 0.6))
    return cavern(saturate(sig, 1.7), 0.25, 1.0)


def gulp_tone(dur, f0, f1):
    """A sinking wet throat tone for deaths."""
    return env(formant(stack(dur, sweep(f0, f1, dur), 'saw', 3, 30, 5), 'o', 0.8), 0.01, dur)


@sound
def block_raise():
    latch = env(iron(0.3, 180, 0.12, ring=0.4), 0.002, 0.25)
    scrape = lowpass2(env(noise(0.1, seed=50), 0.003, 0.08), sweep(500, 1600, 0.1), 2)
    return mix((latch, 0.8), (scrape, 0.7), (thump(0.08, 100, 60), 0.6, 0.02))


@sound(variants=3)
def block_hit(v):
    s = PITCH[v]
    clang = env(iron(0.9, 240 * s, 0.4), 0.001, 0.7)
    crack = highpass2(env(noise(0.03, seed=51 + v), 0.0003, 0.025), 1500)
    sig = mix((clang, 0.9), (crack, 0.35), (thump(0.12, 130, 55), 1.0), (sub(0.25, 70, 38, 0.2), 0.6))
    return cavern(saturate(sig, 1.5), 0.25, 1.0)


@sound
def deflect():
    ting = env(iron(0.6, 560, 0.3, ring=0.8), 0.001, 0.5)
    scrape = lowpass2(env(noise(0.06, seed=52), 0.001, 0.05), sweep(3000, 800, 0.06), 1.5)
    return cavern(lowpass2(mix((ting, 0.8), (scrape, 0.6), (thump(0.06, 160, 80), 0.4)), 4500), 0.2, 0.9)
