"""Spells, shields, enchantments: dark power, furnace roars, groaning glaciers, deep occult hums."""
import math
import random

from synth import (
    TAU, amp, bandpass, bell, cavern, clicks, env, formant, lowpass2, mix, noise, reverse, saturate, stack,
    sub, sweep, swell, toll, tone)
from common import sound, squelch, thump


def pitch(v):
    """Per-variant pitch factor: the same sound, slightly different each time."""
    return (1.0, 0.93, 1.08)[v % 3]


def hum(dur, freq, voices=3, cents=16, seed=None, cutoff=500):
    """A thick detuned saw voice darkened with a 12 dB low-pass: the body of every ward and ritual."""
    return lowpass2(stack(dur, freq, 'saw', voices, cents, seed), cutoff, 1.2)


@sound(variants=3)
def cast_arcane(v):
    """A deep occult cast: falling choir-like saw hum, an inward reversed whisper and low crackle."""
    p = pitch(v)
    voice = formant(stack(0.35, sweep(150 * p, 80 * p, 0.3), 'saw', 4, 20, seed=80 + v), 'o', 0.7)
    voice = env(voice, 0.03, 0.35)
    inward = lowpass2(reverse(env(bandpass(noise(0.3, seed=81 + v), sweep(500, 1500, 0.3), 3), 0.001, 0.25)), 2000)
    crackle = lowpass2(clicks(0.25, 40, 0.005, (250, 1200), seed=82 + v), 2000)
    body = mix((sub(0.35, 70 * p, 38, 0.3), 0.7), (thump(0.15, 140 * p, 70, 0.12), 0.5))
    sig = saturate(mix((voice, 1.2), (inward, 0.6), (crackle, 0.4), (body, 0.8)), 2.0)
    return cavern(sig, 0.2, 0.7)


@sound(variants=3)
def cast_spark(v):
    """Crackling occult energy: a buzzing low square with random flutter, jagged low clicks and a thud."""
    p = pitch(v)
    rng = random.Random(82 + v)
    buzz = env(tone(0.2, lambda t: (90 + 200 * math.exp(-t * 12) + rng.uniform(-30, 30)) * p, 'square'), 0.001, 0.18)
    buzz = lowpass2(buzz, 1500)
    crackle = lowpass2(clicks(0.18, 110, 0.003, (500, 2500), seed=83 + v), 3500)
    crackle = amp(crackle, lambda t: math.exp(-t / 0.08))
    body = mix((thump(0.1, 180 * p, 70, 0.08), 0.8), (sub(0.15, 80, 40, 0.1), 0.5))
    return cavern(saturate(mix((buzz, 0.6), (crackle, 0.9), (body, 0.8)), 2.0), 0.15, 0.5)


@sound
def cast_icicle():
    """A shard of glacier: a low groan, a hard mid crack and a muted iron ring, not tinkling glass."""
    groan = lowpass2(env(stack(0.4, sweep(120, 60, 0.4), 'saw', 3, 25, seed=84), 0.02, 0.4), 450)
    crack = lowpass2(env(noise(0.1, seed=85), 0.0005, 0.06), 3500)
    ticks = lowpass2(clicks(0.15, 90, 0.004, (800, 3000), seed=86), 4000)
    ring = lowpass2(toll(0.5, 220, 0.25), 1500)
    body = mix((thump(0.12, 200, 80, 0.1), 0.8), (sub(0.2, 80, 40, 0.15), 0.5))
    return cavern(saturate(mix((groan, 0.9), (crack, 0.7), (ticks, 0.5), (ring, 0.3), (body, 0.8)), 1.8), 0.25, 0.9)


@sound
def cast_poison():
    """A thick bubbling spit: low sloshing brown noise with soft rising bubble pops."""
    slosh = lowpass2(env(noise(0.35, 'pink', seed=85), 0.01, 0.3), 650)
    pops = mix(*[(env(lowpass2(tone(0.07, sweep(110 + 40 * k, 260 + 60 * k, 0.05)), 700), 0.003, 0.06),
                  0.9 - 0.15 * k, 0.04 + 0.06 * k) for k in range(4)])
    gurgle = lowpass2(squelch(0.25, 500, 200, 3, seed=88), 900)
    body = mix((thump(0.12, 130, 60, 0.1), 0.7), (sub(0.2, 70, 38, 0.15), 0.4))
    return cavern(saturate(mix((slosh, 0.8), (pops, 0.9), (gurgle, 0.6), (body, 0.7)), 1.8), 0.2, 0.7)


@sound(variants=3)
def cast_fireball(v):
    """A furnace roar: swelling dark pink noise over a deep sub, with low ember crackle."""
    p = pitch(v)
    roar = lowpass2(noise(0.6, 'pink', seed=89 + v), sweep(350, 1400 * p, 0.5), 1.3)
    roar = swell(roar, 0.3)
    rumble = env(lowpass2(noise(0.6, 'brown', seed=95 + v), 200), 0.08, 0.5)
    deep = amp(sub(0.6, 80 * p, 36, 0.5, 0.05), lambda t: min(1.0, t / 0.08))
    crackle = amp(lowpass2(clicks(0.5, 55, 0.004, (300, 1800), seed=90 + v), 3000), lambda t: 1 - t / 0.5)
    sig = saturate(mix((roar, 1.6), (rumble, 0.2), (deep, 0.3), (crackle, 0.35)), 2.2)
    return cavern(sig, 0.2, 0.8)


@sound
def charge_magic():
    """A build-up over 1.5 s: a growing sub drone and choir hum, a swelling furnace roar and accelerating crackle."""
    dur = 1.5
    drone = tone(dur, sweep(45, 90, dur))
    choir = formant(stack(dur, sweep(55, 110, dur), 'saw', 5, 25, seed=91), 'o', 0.7)
    roar = lowpass2(noise(dur, 'pink', seed=92), sweep(200, 1200, dur))
    crackle = lowpass2(clicks(dur, 20, 0.004, (300, 2000), seed=93, accel=3.0), 3000)
    sig = saturate(mix((drone, 0.9), (choir, 1.0), (roar, 0.9), (crackle, 0.4)), 1.8)
    return amp(sig, lambda t: 0.15 + 0.85 * min(1.0, t / dur) ** 1.5)


@sound(variants=3)
def explosion(v):
    """A cavern-shaking blast: huge sub drop, saturated brown-noise body, falling debris crackle and a long hall tail."""
    p = pitch(v)
    boom = mix((sub(1.0, 80 * p, 30, 0.8), 1.2), (thump(0.5, 130 * p, 50, 0.4), 1.0))
    body = lowpass2(env(noise(1.0, 'brown', seed=93 + v), 0.002, 0.8), sweep(1400, 140, 0.8))
    blast = lowpass2(env(noise(0.3, seed=96 + v), 0.001, 0.15), sweep(3000, 400, 0.2))
    debris = amp(lowpass2(clicks(1.0, 45, 0.005, (250, 1800), seed=94 + v), 3000), lambda t: math.exp(-t / 0.35))
    sig = saturate(mix((boom, 1.0), (body, 1.2), (blast, 0.6), (debris, 0.5, 0.06)), 2.6)
    return cavern(sig, 0.4, 1.8)


@sound
def freeze():
    """Ice seizing: a long groaning glacier, cracking pops and a deep frozen thud, no sparkle."""
    groan = lowpass2(env(stack(0.5, sweep(100, 55, 0.5), 'saw', 4, 30, seed=95), 0.05, 0.5), 400)
    groan = amp(groan, lambda t: 0.6 + 0.4 * math.sin(TAU * 9 * t))
    cracks = amp(lowpass2(clicks(0.5, 40, 0.005, (500, 2500), seed=96, jitter=0.7), 3500), lambda t: math.exp(-t / 0.2))
    frost = env(lowpass2(noise(0.5, 'pink', seed=97), 1800), 0.05, 0.4)
    body = mix((thump(0.2, 150, 60, 0.15), 0.7), (sub(0.3, 70, 36, 0.25), 0.6))
    return cavern(saturate(mix((groan, 1.0), (cracks, 0.9), (frost, 0.4), (body, 0.8)), 1.8), 0.3, 1.0)


@sound
def shatter():
    """Ice breaking heavily: a booming crack, low falling shards of iron-dark glass and a sub thud."""
    rng = random.Random(98)
    shards = mix(*[(lowpass2(bell(0.4, rng.uniform(350, 1600), ((1, 1.0), (1.7, 0.4)), rng.uniform(0.06, 0.2)), 2500),
                    rng.uniform(0.3, 0.7), rng.uniform(0, 0.1)) for _ in range(12)])
    burst = lowpass2(env(noise(0.2, seed=99), 0.0005, 0.12), sweep(4000, 500, 0.15))
    body = mix((thump(0.2, 200, 60, 0.15), 1.0), (sub(0.35, 85, 34, 0.3), 0.9))
    return cavern(saturate(mix((shards, 0.7), (burst, 0.9), (body, 1.0)), 2.0), 0.28, 0.9)


@sound(variants=3)
def crackle(v):
    """Low fire crackle: a dark furnace bed with slow popping embers (it is pitched down 20-40% in game)."""
    roar = env(lowpass2(noise(0.6, 'pink', seed=100 + v), 500), 0.15, 0.45)
    pops = lowpass2(clicks(0.6, 14, 0.006, (350, 1600), seed=101 + v), 2500)
    return cavern(saturate(mix((roar, 0.6), (pops, 1.0)), 1.6), 0.12, 0.5)


@sound
def no_mana():
    """A dull refusal: a short dissonant low buzz (tritone) with a dry thud, nothing sparkling."""
    buzz = lowpass2(env(mix((tone(0.2, 70, 'square'), 1.0), (tone(0.2, 99, 'square'), 0.7)), 0.003, 0.2, 0.08, 'lin'), 600)
    return saturate(mix((buzz, 0.8), (thump(0.1, 140, 60, 0.07), 0.7)), 1.8)


@sound
def shield_on():
    """A ward rising: a deep resonant hum on a fifth that swells up, a sub below and a soft shimmering overtone."""
    dur = 0.6
    low = hum(dur, sweep(82, 98, dur), 3, 12, 103, 600)
    fifth = hum(dur, sweep(123, 147, dur), 2, 10, 104, 700)
    swell_ = lambda s: amp(s, lambda t: min(1.0, t / 0.2) * math.exp(-max(0.0, t - 0.3) / 0.15))
    body = sub(dur, 60, 41, 0.5, 0.05)
    sig = saturate(mix((swell_(low), 1.0), (swell_(fifth), 0.6), (body, 0.8)), 1.8)
    return cavern(sig, 0.3, 1.5)


@sound
def shield_off():
    """A ward fading: the hum sinking an octave into the sub, with a soft low thump."""
    dur = 0.45
    low = hum(dur, sweep(110, 55, dur), 3, 12, 106, 500)
    fifth = hum(dur, sweep(165, 82, dur), 2, 10, 107, 500)
    sig = env(mix((low, 1.0), (fifth, 0.5)), 0.01, dur)
    return cavern(saturate(mix((sig, 1.0), (thump(0.15, 120, 50, 0.1), 0.6)), 1.6), 0.2, 0.6)


@sound(variants=3)
def shield_hit(v):
    """A blow on the ward: a deep resonant gong (low toll), a sub thump and a muted crackle."""
    p = pitch(v)
    gong = lowpass2(toll(0.55, 110 * p, 0.3), 1800)
    body = mix((thump(0.15, 160 * p, 70, 0.12), 0.9), (sub(0.25, 80, 38, 0.2), 0.7))
    zap = lowpass2(clicks(0.1, 80, 0.003, (400, 1800), seed=104 + v), 2500)
    return cavern(saturate(mix((gong, 1.0), (body, 1.0), (zap, 0.4)), 2.0), 0.2, 0.6)


@sound
def shield_break():
    """The ward collapsing: a heavy shatter over a falling sub drop and a dying hum."""
    drop = env(tone(0.7, sweep(200, 35, 0.6)), 0.002, 0.65)
    return mix((shatter(), 0.8), (drop, 0.6))


@sound
def enchant_on():
    """A ritual binding: a swelling choir on a low minor chord, a deep toll on the fifth and a sub pulse."""
    dur = 0.9
    choir = mix(*[(formant(stack(dur, f, 'saw', 4, 20, seed=105 + int(f)), 'o', 0.8), g) for f, g in
                  ((98, 1.0), (117, 0.7), (147, 0.6))])
    choir = amp(choir, lambda t: min(1.0, t / 0.4) * math.exp(-max(0.0, t - 0.5) / 0.25))
    ritual = mix((toll(1.0, 98, 0.8), 0.8), (toll(1.0, 147, 0.5), 0.4, 0.05))
    body = sub(0.5, 70, 40, 0.4, 0.02)
    return cavern(saturate(mix((choir, 0.9), (lowpass2(ritual, 2000), 0.9), (body, 0.8)), 1.6), 0.4, 2.0)


@sound
def toggle_off():
    """An enchantment released: a low toll falling a semitone and a fading murmur of hum."""
    ring = lowpass2(mix((toll(0.6, 110, 0.4), 1.0), (toll(0.6, 104, 0.3), 0.4, 0.05)), 1800)
    murmur = env(hum(0.4, sweep(82, 62, 0.4), 3, 14, 108, 400), 0.02, 0.4)
    return cavern(saturate(mix((ring, 1.0), (murmur, 0.6), (thump(0.1, 100, 50, 0.07), 0.5)), 1.6), 0.2, 0.6)
