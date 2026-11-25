"""The Tar & Ember slime: movement, hurt and death, flow, charges, the slam."""
import math
import random

from synth import (
    amp, bell, cavern, clicks, env, formant, highpass2, lowpass2, mix, noise, saturate, stack, sub, sweep,
    swell, toll, tone, tremolo)
from common import impact, sound, thump, whoosh

PITCH = (1.0, 0.92, 1.08)   # per-variant pitch of the repeating sounds


def gulp(dur, f0, f1, seed, cutoff=500):
    """A viscous tar gulp: a bent low sine plus resonant noise, both darkened."""
    body = env(tone(dur, sweep(f0, f1, dur)), 0.006, dur * 0.8)
    wet = lowpass2(env(noise(dur, seed=seed), 0.004, dur * 0.7), sweep(cutoff * 2, cutoff * 0.5, dur), 2.5)
    return saturate(mix((body, 0.8), (wet, 0.7)), 1.8)


def sizzle(dur, seed, rate=60, freq=(1200, 3500), level=1.0):
    """Molten ember crackle: sparse ticks that thin out."""
    return amp(clicks(dur, rate, 0.004, freq, 0.6, seed), lambda t: level * math.exp(-t / (dur * 0.5)))


def choir(dur, freqs, vowel='o', shift=0.8, seed=0, peak=0.6):
    """A low choir swell: detuned saws through a vowel, one voice per note."""
    voices = [formant(stack(dur, f, 'saw', 4, 18, seed + i), vowel, shift) for i, f in enumerate(freqs)]
    return swell(mix(*voices), peak)


@sound
def jump():
    push = gulp(0.2, 70, 150, 1, 450)
    stretch = lowpass2(env(noise(0.18, 'pink', 2), 0.01, 0.15), sweep(250, 900, 0.18), 2)
    return cavern(mix((push, 1.0), (stretch, 0.5), (thump(0.1, 120, 60), 0.6)), 0.12, 0.6)


@sound(variants=3)
def land(v):
    s = PITCH[v]
    slap = lowpass2(env(noise(0.14, 'pink', 3 + v), 0.001, 0.1), 900 * s, 1.5)
    body = mix((sub(0.3, 90 * s, 36, 0.28), 1.0), (thump(0.14, 150 * s, 62), 0.9), (slap, 0.8),
               (sizzle(0.2, 9 + v, 70, (900, 2500), 0.2), 0.3))
    return cavern(saturate(body, 2.0), 0.15, 0.6)


@sound(variants=3)
def step(v):
    s = PITCH[v]
    squish = lowpass2(env(noise(0.08, 'pink', 4 + v), 0.003, 0.06), sweep(500 * s, 1100 * s, 0.08), 2)
    return saturate(mix((squish, 1.0), (thump(0.08, 130 * s, 65), 0.9)), 1.6)


@sound
def dash():
    air = lowpass2(whoosh(0.32, 120, 1100, 1.0, 0.35, seed=5, color='pink'), 1400)
    drag = gulp(0.2, 140, 60, 6, 400)
    return cavern(mix((air, 1.2), (drag, 0.7), (sizzle(0.3, 7, 90, (1500, 3500), 0.4), 0.3)), 0.15, 0.7)


@sound
def levitate():
    air = lowpass2(whoosh(0.9, 120, 600, 1.0, 0.4, seed=7, color='pink'), 900)
    hum = lowpass2(env(stack(0.9, sweep(55, 110, 0.9), 'saw', 3, 20, 8), 0.2, 0.9, curve='lin'), 450)
    embers = amp(clicks(0.9, 40, 0.004, (900, 2500), 0.7, 9), lambda t: 0.4 * math.sin(math.pi * t / 0.9))
    return cavern(mix((air, 1.0), (hum, 0.8), (embers, 0.4)), 0.2, 1.0)


@sound
def player_hurt():
    hit = mix((thump(0.16, 150, 50), 1.0), (lowpass2(env(noise(0.1, seed=10), 0.001, 0.07), 1800), 0.8))
    groan = lowpass2(formant(stack(0.35, sweep(150, 70, 0.35), 'saw', 3, 25, 11), 'o', 0.7), 900)
    groan = env(groan, 0.01, 0.33)
    wet = gulp(0.2, 200, 70, 12, 700)
    return cavern(saturate(mix((hit, 1.0), (groan, 2.2), (wet, 0.6)), 2.0), 0.2, 0.8)


@sound
def player_death():
    dur = 1.8
    groan = formant(stack(dur, sweep(170, 38, dur), 'saw', 4, 30, 14), 'o', 0.7)
    groan = env(groan, 0.03, dur, curve='lin')
    rng = random.Random(11)
    bubbles = mix(*[(gulp(0.12, f, f * 0.6, 20 + k, 350), rng.uniform(0.3, 0.7), rng.uniform(0.05, 1.5))
                    for k, f in enumerate(rng.uniform(70, 220) for _ in range(9))])
    goo = lowpass2(env(noise(dur, 'brown', 12), 0.02, dur, curve='lin'), sweep(1200, 120, dur), 1.5)
    sig = mix((groan, 2.0), (bubbles, 1.0), (goo, 0.8), (sub(1.6, 70, 30, 1.5), 1.2),
              (sizzle(dur, 13, 50, (1000, 3000), 0.3), 0.4))
    return cavern(saturate(sig, 1.8), 0.4, 2.0)


@sound
def fall():
    dur = 1.1
    howl = lowpass2(whoosh(dur, 120, 800, 1.2, 0.35, seed=13, color='pink'), sweep(1400, 250, dur))
    moan = env(formant(stack(dur, sweep(210, 80, dur), 'saw', 3, 30, 15), 'u', 0.8), 0.1, dur, curve='lin')
    return cavern(mix((howl, 1.0), (moan, 1.4)), 0.25, 1.0)


@sound
def bounce():
    slap = gulp(0.3, 60, 140, 16, 600)
    rebound = lowpass2(env(noise(0.2, 'pink', 17), 0.002, 0.15), sweep(300, 1000, 0.2), 2)
    return cavern(saturate(mix((slap, 1.0), (rebound, 0.6), (sub(0.35, 80, 40, 0.3), 0.9)), 1.8), 0.15, 0.7)


@sound
def slam_dive():
    dur = 0.45
    air = lowpass2(whoosh(dur, 150, 1400, 1.2, 0.8, seed=57, color='pink'), 1800)
    drone = env(lowpass2(stack(dur, sweep(110, 60, dur), 'saw', 3, 20, 58), 500), 0.1, dur, curve='lin')
    return mix((air, 1.2), (drone, 0.9))


@sound
def slam_impact():
    boom = cavern(impact(1.0, 1.5, 85, 0.5, 59, 0), 0.3, 1.8)
    debris = lowpass2(clicks(0.7, 45, 0.006, (500, 2000), 0.5, 60), 3000)
    splash = lowpass2(env(noise(0.5, seed=61), 0.002, 0.4), sweep(1800, 200, 0.5), 1.5)
    return mix((boom, 1.0), (amp(debris, lambda t: math.exp(-t / 0.2)), 0.5, 0.03), (splash, 0.6, 0.01))


@sound
def flow_up():
    # a muted toll and its fifth: rate is raised by the game for each flow step
    sig = mix((toll(0.7, 110, 0.4), 1.0), (bell(0.5, 165, ((1, 1.0), (2.0, 0.2)), 0.25), 0.5, 0.03),
              (sub(0.4, 70, 45, 0.35), 0.6))
    return cavern(saturate(sig, 1.5), 0.2, 1.0)


@sound
def flow_max():
    sig = mix((toll(1.6, 82, 0.9), 1.0), (toll(1.2, 123, 0.6), 0.5, 0.05), (sub(1.2, 75, 38, 1.0), 1.0),
              (choir(1.5, (110, 131, 165), 'o', 0.9, 20, 0.5), 1.2, 0.05))
    return cavern(saturate(sig, 1.6), 0.45, 2.0)


@sound
def flow_ready():
    # a tiny dark tick, no room: it repeats fast and the game pitches it up
    tick = env(tone(0.06, sweep(300, 220, 0.05)), 0.001, 0.05)
    click = lowpass2(env(noise(0.012, seed=53), 0.0003, 0.01), 2500)
    return saturate(mix((tick, 1.0), (click, 0.6)), 1.5)


@sound
def charge_weapon():
    dur = 1.5
    rise = sweep(42, 110, dur)
    drone = lowpass2(stack(dur, rise, 'saw', 4, 25, 54), sweep(250, 1400, dur), 1.5)
    fifth = lowpass2(stack(dur, lambda t: rise(t) * 1.5, 'saw', 3, 20, 55), sweep(200, 900, dur))
    drone = tremolo(mix((drone, 1.0), (fifth, 0.5)), sweep(4, 18, dur), 0.35)
    embers = clicks(dur, 25, 0.004, (900, 3000), 0.6, 56, accel=3.0)
    heat = lowpass2(noise(dur, 'pink', 57), sweep(300, 1800, dur))
    sig = saturate(mix((drone, 1.3), (embers, 0.3), (heat, 0.5)), 1.8)
    return amp(sig, lambda t: min(1.0, t / dur) ** 1.2 * 0.9 + 0.1)


@sound
def charge_ready():
    sig = mix((toll(1.0, 98, 0.55), 1.0), (sub(0.8, 80, 40, 0.6), 1.0),
              (lowpass2(env(noise(0.05, seed=62), 0.0005, 0.04), 2500), 0.4))
    return cavern(saturate(sig, 1.7), 0.3, 1.4)


@sound
def perfect():
    sig = mix((toll(1.6, 73.4, 0.8), 1.2), (toll(1.2, 110, 0.55), 0.6, 0.04), (sub(1.2, 80, 34, 1.0), 1.2),
              (choir(1.6, (98, 116.5, 147), 'a', 0.8, 30, 0.35), 0.9, 0.1),
              (highpass2(env(noise(0.08, seed=63), 0.0005, 0.06), 1200), 0.25),
              (sizzle(0.8, 64, 70, (1200, 3000), 0.3), 0.3))
    return cavern(saturate(sig, 1.7), 0.4, 1.8)
